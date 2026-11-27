#!/usr/bin/env python3
"""Validate the integrated P2 flow against a disposable database with demo seeds."""
from concurrent.futures import ThreadPoolExecutor
import json
import os
from threading import Barrier
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen
from uuid import uuid4

BASE = os.environ.get("P2_SMOKE_URL", "http://localhost:8080").rstrip("/") + "/api/v1"
ORIGIN = "11111111-1111-4111-8111-111111111111"
STORE = "22222222-2222-4222-8222-222222222222"
OWN_DRIVER = "55555555-5555-4555-8555-555555555555"
OWN_VEHICLE = "66666666-6666-4666-8666-666666666666"


def request(path, *, token=None, body=None, statuses=(200,), code=None, headers=None, method=None):
    headers = dict(headers or {})
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    try:
        response = urlopen(Request(BASE + path, data=data, headers=headers, method=method), timeout=15)
    except HTTPError as error:
        response = error
    with response:
        status = response.code
        result = json.load(response)
    if status not in statuses or (code and result.get("code") != code):
        result_code = result.get("code", "sem código")
        raise AssertionError(f"{path} retornou HTTP {status} ({result_code})")
    return status, result


def main():
    password = os.environ["P2_SMOKE_PASSWORD"]
    _, admin_login = request("/auth/login", body={
        "email": "sprint4-operator@example.invalid", "password": password,
    })
    _, store_login = request("/auth/login", body={
        "email": "sprint4-other-store@example.invalid", "password": password,
    })
    admin = admin_login["accessToken"]
    other_store = store_login["accessToken"]
    origin_path = f"/origins/{ORIGIN}"
    request(origin_path, headers={"X-Dev-Role": "ADMIN"}, statuses=(401,), code="UNAUTHENTICATED")
    request(origin_path, token="invalid-token", statuses=(401,), code="UNAUTHENTICATED")
    request(origin_path, token=other_store, statuses=(404,), code="NOT_FOUND")
    request("/vehicles", statuses=(401,), code="UNAUTHENTICATED")
    request("/deliveries/my", statuses=(401,), code="UNAUTHENTICATED")
    request("/vehicles", token=other_store, statuses=(403,), code="FORBIDDEN")
    _, origin = request(origin_path, token=admin)
    assert origin["storeId"] == STORE and origin["active"]

    _, new_store = request("/stores", token=admin, statuses=(201,), body={
        "name": f"Loja smoke {uuid4()}", "active": True,
    })
    _, new_origin = request("/origins", token=admin, statuses=(201,), body={
        "storeId": new_store["id"], "type": "DISTRIBUTION_CENTER",
        "address": origin["address"], "active": True,
    })

    def make_route(weight):
        _, order = request("/orders", token=admin, statuses=(201,), body={
            "storeId": new_store["id"],
            "originId": new_origin["id"],
            "destination": {
                "line1": "Rua Destino, 12", "city": "Cidade Fictícia",
                "state": "SC", "country": "BR", "latitude": -26.49, "longitude": -49.08,
            },
            "recipientName": "Teste de integração",
            "items": [{"description": "Carga fictícia", "quantity": 1, "unitWeightKg": weight}],
        })
        order_id = order["orderId"]
        _, confirmed = request(f"/orders/{order_id}/confirm", token=admin, body={})
        assert confirmed["status"] == "CONFIRMED"
        tracking_path = f"/tracking/{quote(order['trackingCode'], safe='')}"
        _, tracking = request(tracking_path)
        assert set(tracking) == {"status", "milestones"}
        assert tracking["status"] == "PENDING"
        assert tracking["milestones"]
        assert all(set(event) == {"status", "at"} for event in tracking["milestones"])
        request(tracking_path, method="POST", body={}, statuses=(401,), code="UNAUTHENTICATED")
        _, route = request("/routes", token=admin, statuses=(201,), body={
            "originId": new_origin["id"], "orderIds": [order_id],
        })
        assert route["status"] == "PLANNED"
        return route["routeId"], tracking_path

    def assign(route_id, statuses=(200,), token=admin):
        return request(f"/routes/{route_id}/assign", token=token, body={}, statuses=statuses)

    def get_assignment(route_id, **kwargs):
        return request(f"/fleet/assignments/{route_id}", token=admin, **kwargs)[1]

    request(f"/routes/{uuid4()}/assign", token=admin, body={}, statuses=(404,), code="NOT_FOUND")
    request(f"/tracking/TRK-{uuid4().hex}", statuses=(404,), code="NOT_FOUND")
    request("/tracking/extra/segments", statuses=(401,), code="UNAUTHENTICATED")
    route, tracking_path = make_route("40.000")
    assign(route, (403,), token=other_store)
    _, assigned = assign(route)
    assert assigned["status"] == "ASSIGNED"
    own = get_assignment(route)
    assert own["fleetType"] == "OWN"
    assert own["driverId"] == assigned["driverId"] == OWN_DRIVER
    assert own["vehicleId"] == assigned["vehicleId"] == OWN_VEHICLE
    assign(route)
    assert own == get_assignment(route)
    _, started = request(f"/routes/{route}/start", token=admin, body={})
    assert started["status"] == "IN_PROGRESS"
    _, public = request(tracking_path)
    assert set(public) == {"status", "milestones"} and public["status"] == "ASSIGNED"
    assert all(set(event) == {"status", "at"} for event in public["milestones"])

    # Only one of the demo vehicles can carry 1,500 kg.
    contenders = [make_route("1500.000")[0] for _ in range(2)]
    barrier = Barrier(len(contenders), timeout=10)

    def assign_together(route_id):
        barrier.wait()
        return assign(route_id, (200, 409))

    with ThreadPoolExecutor(max_workers=len(contenders)) as executor:
        outcomes = list(executor.map(assign_together, contenders))
    assert sorted(status for status, _ in outcomes) == [200, 409], outcomes
    for route_id, (status, body) in zip(contenders, outcomes):
        if status == 200:
            reserved = get_assignment(route_id)
            assert reserved["fleetType"] == "THIRD_PARTY"
            assert reserved["driverId"] == body["driverId"]
            assert reserved["vehicleId"] == body["vehicleId"]
        else:
            assert body["code"] == "NO_ELIGIBLE_RESOURCE"
            get_assignment(route_id, statuses=(404,), code="NOT_FOUND")
            _, planned = request(f"/routes/{route_id}", token=admin)
            assert planned["status"] == "PLANNED"
    print(f"Smoke OK. Rota própria {route} persistida; concorrência sem reserva dupla.")


if __name__ == "__main__":
    main()
