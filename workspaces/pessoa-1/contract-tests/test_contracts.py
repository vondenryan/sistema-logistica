"""Workspace-local checks; canonical shared contracts are never modified."""
import copy
import json
import pathlib
import unittest
from jsonschema import Draft202012Validator, FormatChecker

ROOT = pathlib.Path(__file__).parent

def fixture(name):
    return json.loads((ROOT / 'fixtures' / f'{name}.json').read_text())

def validator(name):
    schema = json.loads((ROOT / 'schemas' / f'{name}.json').read_text())
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema, format_checker=FormatChecker())

class ContractTests(unittest.TestCase):
    def test_canonical_document_fixtures(self):
        for name in ('address', 'origin', 'create-order', 'problem'):
            with self.subTest(name=name):
                validator(name).validate(fixture(name))

    def test_draft_can_omit_coordinates(self):
        data = fixture('create-order')
        del data['destination']['latitude']
        del data['destination']['longitude']
        validator('create-order').validate(data)

    def test_invalid_requests(self):
        mutations = [lambda x: x.update(storeId='bad-id'),
                     lambda x: x.update(extra=True),
                     lambda x: x.update(items=[]),
                     lambda x: x['items'][0].update(unitWeightKg=20.0),
                     lambda x: x['items'][0].update(unitWeightKg='-20.000'),
                     lambda x: x['items'][0].update(unitWeightKg='0.000'),
                     lambda x: x['items'][0].update(unitWeightKg='20.0001'),
                     lambda x: x['items'][0].update(quantity=0),
                     lambda x: x['destination'].update(latitude=91),
                     lambda x: x.update(recipientEmail='invalid')]
        for index, mutate in enumerate(mutations):
            with self.subTest(case=index):
                data = copy.deepcopy(fixture('create-order'))
                mutate(data)
                self.assertTrue(list(validator('create-order').iter_errors(data)))

    def test_error_details_require_field_and_reason(self):
        data = fixture('problem')
        data['details'] = {}
        self.assertTrue(list(validator('problem').iter_errors(data)))
        data['details'] = [{'field': 'requiredCapacityKg', 'message': 'Must be positive.'}]
        self.assertTrue(list(validator('problem').iter_errors(data)))
        data['details'] = [
            {'field': 'requiredCapacityKg', 'reason': 'MUST_BE_POSITIVE', 'message': 'Must be positive.'}
        ]
        self.assertTrue(list(validator('problem').iter_errors(data)))
        data['details'] = [{'field': 'requiredCapacityKg', 'reason': 'MUST_BE_POSITIVE'}]
        validator('problem').validate(data)

    def test_error_details_require_field_and_reason(self):
        for item in ('invalid', {}, {'field': 'name', 'message': 'required'},
                     {'field': 'name', 'reason': 42}, {'reason': 'required'}):
            with self.subTest(item=item):
                data = fixture('problem')
                data['details'] = [item]
                self.assertFalse(validator('problem').is_valid(data))

    def test_origin_requires_store_even_distribution_center(self):
        data = fixture('origin')
        data['type'] = 'DISTRIBUTION_CENTER'
        validator('origin').validate(data)
        del data['storeId']
        self.assertTrue(list(validator('origin').iter_errors(data)))

if __name__ == '__main__':
    unittest.main()
