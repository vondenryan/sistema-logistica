"""Run the full delivery and fleet lifecycle through the shared gateway."""
from concurrent.futures import ThreadPoolExecutor
import json
import os
from html.parser import HTMLParser
from pathlib import Path
import runpy
import sys
from urllib.error import HTTPError
from urllib.parse import urljoin
from urllib.request import urlopen
from uuid import uuid4

root = Path(__file__).resolve().parents[1]
gateway = os.environ.get("P2_SMOKE_URL", "http://127.0.0.1:18000").rstrip("/")
os.environ["P2_SMOKE_URL"] = gateway
p2 = runpy.run_path(str(root / "workspaces/pessoa-2/scripts/smoke-integration.py"))
p3 = runpy.run_path(str(root / "workspaces/pessoa-3/scripts/smoke-integration.py"))
request = p2["request"]


class Assets(HTMLParser):
    def __init__(self):
        super().__init__()
        self.scripts = []

    def handle_starttag(self, tag, attributes):
        if tag == "script" and dict(attributes).get("src"):
            self.scripts.append(dict(attributes)["src"])


def check_gateway():
    with urlopen(gateway + "/ready", timeout=10) as response:
        assert response.status == 200
    with urlopen(gateway + "/", timeout=10) as response:
        assert b"/entregas/#rastreamento" in response.read()
    for path, prefix in (("/pedidos/", "/pedidos/"), ("/frota/vehicles", "/frota/"),
                         ("/entregas/", "/entregas/")):
        parser = Assets()
        with urlopen(gateway + path, timeout=10) as response:
            parser.feed(response.read().decode())
        assert parser.scripts and all(src.startswith(prefix) for src in parser.scripts), path
        for src in parser.scripts:
            with urlopen(urljoin(gateway, src), timeout=10) as response:
                assert response.status == 200 and "javascript" in response.headers.get_content_type()
        try:
            urlopen(gateway + prefix + "assets/missing.js", timeout=10).close()
        except HTTPError as error:
            assert error.code == 404
        else:
            raise AssertionError(f"Asset inexistente em {prefix} deve retornar 404")


def register():
    check_gateway()
    operator = p3["login"]("operator")
    other_store = p3["login"]("other-store")
    request("/vehicles", statuses=(401,), code="UNAUTHENTICATED")
    request("/vehicles", token=other_store, statuses=(403,), code="FORBIDDEN")
    request("/deliveries/my", statuses=(401,), code="UNAUTHENTICATED")

    def create(path, body):
        return request(path, token=operator, statuses=(201,), body=body)[1]

    store = create("/stores", {"name": f"Loja integrada {uuid4()}", "active": True})
    _, reference = request(f"/origins/{p2['ORIGIN']}", token=operator)
    origin = create("/origins", {
        "storeId": store["id"], "type": "STORE", "address": reference["address"], "active": True,
    })
    created_driver = create("/drivers", {"name": "Motorista cadastrado no smoke", "active": True})
    vehicle = create("/vehicles", {
        "identifier": f"INT-{uuid4().hex[:10]}", "capacityKg": "3100.000",
        "fleetType": "OWN", "active": True,
    })
    _, drivers = request("/drivers", token=operator)
    for item in drivers["items"]:
        if item["id"] != created_driver["id"] and item["active"]:
            request(f"/drivers/{item['id']}", token=operator, method="PATCH", body={"active": False})
    Path(os.environ["INTEGRATION_FIXTURE_FILE"]).write_text(json.dumps({
        "store": store["id"], "origin": origin["id"], "driver": created_driver["id"],
        "vehicle": vehicle["id"],
    }))


def lifecycle():
    check_gateway()
    operator = p3["login"]("operator")
    other_store = p3["login"]("other-store")
    driver = p3["login"]("journey-driver")
    other_driver = p3["login"]("other-driver")
    state = json.loads(Path(os.environ["INTEGRATION_FIXTURE_FILE"]).read_text())
    recipient = f"cliente-{uuid4().hex}@example.invalid"

    def create_order(email=None):
        _, order = request("/orders", token=operator, statuses=(201,), body={
            "storeId": state["store"], "originId": state["origin"],
            "destination": {"line1": "Rua Destino, 12", "city": "Cidade Fictícia",
                            "state": "SC", "country": "BR", "latitude": -26.49, "longitude": -49.08},
            "recipientName": "Cliente de teste", "recipientEmail": email,
            "items": [{"description": "Carga fictícia", "quantity": 1, "unitWeightKg": "3000.000"}],
        })
        for _ in range(2):
            _, confirmed = request(f"/orders/{order['orderId']}/confirm", token=operator, body={})
            assert confirmed["status"] == "CONFIRMED"
        return order

    def make_route(order):
        _, route = request("/routes", token=operator, statuses=(201,), body={
            "originId": state["origin"], "orderIds": [order["orderId"]],
        })
        return f"/routes/{route['routeId']}"

    def assign(path):
        _, route = request(path + "/assign", token=operator, body={})
        assert route["driverId"] == state["driver"] and route["vehicleId"] == state["vehicle"]
        return request(f"/fleet/assignments/{route['routeId']}", token=operator)[1]

    order = create_order(recipient)
    _, freight = request(f"/orders/{order['orderId']}/freight-quote", token=operator, body={})
    route = make_route(order)
    reservation = assign(route)
    assert assign(route) == reservation
    request(route + "/start", token=operator, body={})
    request(route + "/complete", token=operator, body={}, statuses=(422,), code="PRECONDITION_FAILED")
    _, mine = request("/deliveries/my", token=driver)
    assert mine["totalItems"] == 1 and mine["items"][0]["orderId"] == order["orderId"]
    path = f"/deliveries/{mine['items'][0]['deliveryId']}"
    request(path + "/status", token=other_driver, method="PATCH", body={"status": "IN_TRANSIT"},
            statuses=(403, 404))
    for payload in ({"status": "IN_TRANSIT"},
                    {"status": "FAILED_ATTEMPT", "reason": "Local fechado"},
                    {"status": "ASSIGNED"}, {"status": "IN_TRANSIT"}):
        request(path + "/status", token=driver, method="PATCH", body=payload)
    complete = {"receiverName": "Recebedor fictício"}
    _, delivered = request(path + "/complete", token=driver, body=complete)
    assert delivered["status"] == "DELIVERED"
    _, history = request(path + "/events", token=driver)
    assert request(path + "/complete", token=driver, body=complete)[1] == delivered
    assert request(path + "/events", token=driver)[1] == history

    tracking = f"/tracking/{order['trackingCode']}"
    _, public = request(tracking)
    assert set(public) == {"status", "milestones"} and public["status"] == "DELIVERED"
    assert all(set(event) == {"status", "at"} for event in public["milestones"])
    request("/tracking/TRK-inexistente", statuses=(404,), code="NOT_FOUND")
    request(tracking, method="POST", body={}, statuses=(401,), code="UNAUTHENTICATED")
    p3["verify_documents"](order["orderId"], operator, other_store, freight["amount"])
    p3["wait_for_mail"](recipient)

    request(route + "/complete", token=other_store, body={}, statuses=(403,), code="FORBIDDEN")
    _, completed = request(route + "/complete", token=operator, body={})
    assert completed["status"] == "COMPLETED"
    assert request(route + "/complete", token=operator, body={})[1] == completed
    contenders = [make_route(create_order()) for _ in range(2)]

    def reserve(path):
        return request(path + "/assign", token=operator, body={}, statuses=(200, 409))

    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(reserve, contenders))
    assert sorted(status for status, _ in results) == [200, 409]
    for candidate, (status, result) in zip(contenders, results):
        if status == 409:
            assert result["code"] == "NO_ELIGIBLE_RESOURCE"
        else:
            reused = assign(candidate)
            assert reused["assignmentId"] != reservation["assignmentId"]
    print("Jornada integrada aprovada pelo gateway, com liberação e reutilização da frota.")


if __name__ == "__main__":
    if sys.argv[1:] == ["register"]:
        register()
    else:
        lifecycle()
