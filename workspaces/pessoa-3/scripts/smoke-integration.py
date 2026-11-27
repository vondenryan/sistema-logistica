#!/usr/bin/env python3
"""Exercise delivery ownership, public tracking, academic PDFs and Mailpit."""
from collections import Counter
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import runpy
import subprocess
from threading import Thread
from time import monotonic, sleep
from urllib.error import URLError
from urllib.request import Request, urlopen
from uuid import uuid4

P2_SMOKE = Path(__file__).resolve().parents[2] / "pessoa-2/scripts/smoke-integration.py"
core = runpy.run_path(str(P2_SMOKE))
request = core["request"]


class OsrmFixture(BaseHTTPRequestHandler):
    def do_GET(self):
        if not self.path.startswith("/route/v1/driving/"):
            self.send_error(404)
            return
        payload = b'{"code":"Ok","routes":[{"distance":2500}]}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


def login(account):
    _, session = request("/auth/login", body={
        "email": f"sprint4-{account}@example.invalid", "password": os.environ["P2_SMOKE_PASSWORD"],
    })
    return session["accessToken"]


def verify_documents(order_id, operator, other_store, freight):
    for kind in ("NFE_SIMULATED", "CTE_SIMULATED"):
        payload = {"orderId": order_id, "documentType": kind}
        _, doc = request("/documents", token=operator, body=payload, statuses=(201,))
        assert doc["orderId"] == order_id and doc["documentType"] == kind
        _, repeated = request("/documents", token=operator, body=payload)
        assert repeated["documentId"] == doc["documentId"]
        path = f"/documents/{doc['documentId']}/pdf"
        request(path, statuses=(401,), code="UNAUTHENTICATED")
        request(path, token=other_store, statuses=(404,), code="NOT_FOUND")
        with urlopen(Request(core["BASE"] + path, headers={"Authorization": f"Bearer {operator}"}),
                     timeout=10) as response:
            assert response.headers.get_content_type() == "application/pdf"
            text = subprocess.run(
                ["pdftotext", "-", "-"], input=response.read(), capture_output=True, check=True,
            ).stdout.decode()
        assert "SIMULAÇÃO ACADÊMICA — SEM VALIDADE FISCAL" in text
        assert order_id in text and f"Valor do Frete (BRL): {freight}" in text


def wait_for_mail(recipient):
    base = os.environ.get("P3_SMOKE_MAILPIT_URL", "http://127.0.0.1:8025").rstrip("/")
    deadline = monotonic() + 75
    while monotonic() < deadline:
        try:
            with urlopen(f"{base}/api/v1/messages", timeout=5) as response:
                listing = json.load(response)
        except URLError:
            sleep(1)
            continue
        matching = [message for message in listing["messages"]
                    if any(to["Address"] == recipient for to in message["To"])]
        if len(matching) >= 5:
            assert Counter(message["Subject"] for message in matching) == {
                "Pedido confirmado": 1,
                "Entrega em trânsito": 2,
                "Tentativa de entrega sem sucesso": 1,
                "Entrega concluída": 1,
            }
            return
        sleep(1)
    raise AssertionError("Mailpit não recebeu as cinco mensagens do pedido dentro do prazo.")


def smoke():
    operator = login("operator")
    driver = login("driver")
    other_driver = login("other-driver")
    other_store = login("other-store")
    recipient = f"cliente-{uuid4().hex}@example.invalid"
    _, order = request("/orders", token=operator, statuses=(201,), body={
        "storeId": core["STORE"], "originId": core["ORIGIN"],
        "destination": {
            "line1": "Rua Destino, 12", "city": "Cidade Fictícia", "state": "SC",
            "country": "BR", "latitude": -26.49, "longitude": -49.08,
        },
        "recipientName": "Cliente de teste", "recipientEmail": recipient,
        "items": [{"description": "Carga fictícia", "quantity": 1, "unitWeightKg": "40.000"}],
    })
    order_id = order["orderId"]
    _, confirmed = request(f"/orders/{order_id}/confirm", token=operator, body={})
    assert confirmed["status"] == "CONFIRMED"
    _, quote = request(f"/orders/{order_id}/freight-quote", token=operator, body={})
    _, route = request("/routes", token=operator, statuses=(201,), body={
        "originId": core["ORIGIN"], "orderIds": [order_id],
    })
    route_path = f"/routes/{route['routeId']}"
    _, assigned = request(route_path + "/assign", token=operator, body={})
    assert assigned["driverId"] == core["OWN_DRIVER"]
    request(route_path + "/start", token=operator, body={})

    _, own_page = request("/deliveries/my?size=1", token=driver)
    assert own_page["totalItems"] == 1 and own_page["items"][0]["orderId"] == order_id
    _, other_page = request("/deliveries/my", token=other_driver)
    assert other_page["totalItems"] == 0 and other_page["items"] == []
    delivery_path = f"/deliveries/{own_page['items'][0]['deliveryId']}"
    patch = {"method": "PATCH"}
    request(delivery_path + "/status", token=other_driver, body={"status": "IN_TRANSIT"},
            statuses=(403, 404), **patch)
    request(delivery_path + "/status", token=driver, body={"status": "IN_TRANSIT"}, **patch)
    request(delivery_path + "/status", token=driver,
            body={"status": "FAILED_ATTEMPT", "reason": "Local fechado"}, **patch)
    request(delivery_path + "/status", token=driver, body={"status": "ASSIGNED"}, **patch)
    request(delivery_path + "/status", token=driver, body={"status": "IN_TRANSIT"}, **patch)
    _, completed = request(delivery_path + "/complete", token=driver, body={"receiverName": "Recebedor fictício"})
    assert completed["status"] == "DELIVERED"

    tracking = order["trackingCode"]
    _, public = request(f"/tracking/{tracking}")
    assert set(public) == {"status", "milestones"} and public["status"] == "DELIVERED"
    assert all(set(event) == {"status", "at"} for event in public["milestones"])
    request(f"/tracking/TRK-{uuid4().hex}", statuses=(404,), code="NOT_FOUND")
    verify_documents(order_id, operator, other_store, quote["amount"])
    print("Entrega e PDFs persistidos. Aguardando notificações no Mailpit.", flush=True)
    wait_for_mail(recipient)
    print("Smoke P3 OK: autorização, rastreamento, PDFs e notificações.")


if __name__ == "__main__":
    with ThreadingHTTPServer(("127.0.0.1", int(os.environ.get("P3_SMOKE_OSRM_PORT", "19090"))),
                             OsrmFixture) as osrm:
        Thread(target=osrm.serve_forever, daemon=True).start()
        try:
            smoke()
        finally:
            osrm.shutdown()
