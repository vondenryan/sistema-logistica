import copy
import unittest
from test_contracts import fixture, validator

class Sprint2Contracts(unittest.TestCase):
    def test_fixtures(self):
        for name in ('freight-quote', 'route-request', 'route-suggestion', 'route-snapshot', 'delivery-snapshot', 'assignment-request', 'assignment-snapshot', 'store-snapshot'):
            with self.subTest(name=name): validator(name).validate(fixture(name))

    def test_quote_source_is_not_a_frozen_enum(self):
        for source in ("OSRM", "FAKE"):
            with self.subTest(source=source):
                data = fixture("freight-quote"); data["source"] = source
                validator("freight-quote").validate(data)
        data["source"] = ""
        self.assertFalse(validator("freight-quote").is_valid(data))

    def test_invalid_matrix(self):
        cases = [
            ('freight-quote', 'amount', 30.0), ('freight-quote', 'amount', '30.000'), ('freight-quote', 'distanceKm', '-1'), ('freight-quote', 'currency', 'USD'),
            ('route-request', 'orderIds', []), ('route-request', 'orderIds', ['44444444-4444-4444-8444-444444444444']*2), ('route-request', 'unexpected', True),
            ('route-snapshot', 'status', 'UNKNOWN'), ('route-snapshot', 'startedAt', '2026-11-14T12:00:00.000Z'), ('route-snapshot', 'driverId', None),
            ('delivery-snapshot', 'status', 'UNKNOWN'), ('delivery-snapshot', 'updatedAt', None),
            ('assignment-request', 'requiredCapacityKg', 40), ('assignment-request', 'requiredCapacityKg', '0.000'), ('assignment-request', 'requiredCapacityKg', '1.0001'),
            ('assignment-snapshot', 'fleetType', 'OTHER'), ('store-snapshot', 'active', None)]
        for name, field, value in cases:
            with self.subTest(name=name, field=field, value=value):
                data = fixture(name); data[field] = value
                self.assertFalse(validator(name).is_valid(data))
        data = fixture('delivery-snapshot'); data.update(status='DELIVERED', receiverName=None)
        self.assertFalse(validator('delivery-snapshot').is_valid(data))
        data['receiverName'] = 'Recebedor'; validator('delivery-snapshot').validate(data)
        data = fixture('route-snapshot'); data.update(status='IN_PROGRESS', startedAt='2026-11-14T12:00:00.000Z'); validator('route-snapshot').validate(data)
        data = fixture('route-snapshot'); data.update(status='PLANNED', driverId=None, vehicleId=None); validator('route-snapshot').validate(data)
        data = fixture('freight-quote'); data['futureOptionalField'] = True; validator('freight-quote').validate(data)
