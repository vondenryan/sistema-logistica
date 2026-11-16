"""Additional REST/schema and authorization checks against the local dev backend."""
import json
import os
from pathlib import Path
import urllib.request
import urllib.error
from test_contracts import fixture, validator

for line in (Path(__file__).parent.parent / '.env').read_text().splitlines():
    if '=' in line:
        key, value = line.split('=', 1)
        os.environ.setdefault(key, value)
base = os.environ.get('SMOKE_BASE_URL', 'http://localhost:8080/api/v1')

def request(method, path, body=None, token=None, expected=200):
    headers = {'Content-Type':'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as result:
            status, payload = result.status, json.load(result)
    except urllib.error.HTTPError as error:
        status, payload = error.code, json.load(error)
    assert status == expected, (method, path, status, expected, payload.get('code'))
    if expected >= 400:
        validator('problem').validate(payload)
    return payload

def login(email):
    return request('POST','/auth/login',{'email':email,'password':os.environ['DEMO_PASSWORD']})['accessToken']

store=login('store@example.invalid')
other=login('other-store@example.invalid')
driver=login('driver@example.invalid')
request('GET','/orders',expected=401)
request('POST','/auth/login',{'email':'store@example.invalid','password':'incorrect'},expected=401)
request('GET','/orders',token=driver,expected=403)
payload=fixture('create-order')
order=request('POST','/orders',payload,store,201)
assert isinstance(order['totalWeightKg'],str)
assert order['freightAmount'] is None
assert len(order['trackingCode'])>=16
path='/orders/'+order['orderId']
request('GET',path,token=other,expected=404)
request('POST',path+'/confirm',{},other,404)
payload['recipientName']='Nome corrigido'
updated=request('PATCH',path,payload,store)
assert updated['recipientName']=='Nome corrigido'
request('POST',path+'/confirm',{},store)
request('POST',path+'/confirm',{},store)
request('PATCH',path,payload,store,409)
# Cancellation after confirmation requires a business decision, so stays blocked.
request('POST',path+'/cancel',{'reason':'Teste'},store,409)
invalid=fixture('create-order'); invalid['unknown']=True
request('POST','/orders',invalid,store,400)
invalid=fixture('create-order'); invalid['items'][0]['unitWeightKg']=20.0
request('POST','/orders',invalid,store,400)
invalid=fixture('create-order'); invalid['destination']['latitude']=91
request('POST','/orders',invalid,store,400)
draft=request('POST','/orders',fixture('create-order'),store,201)
cancelled=request('POST','/orders/'+draft['orderId']+'/cancel',{'reason':'Teste local'},store)
assert cancelled['status']=='CANCELLED'
print('PASS: live schemas, authentication, DRIVER denial, cross-store hiding, draft edit/cancel, confirmation idempotency, immutable confirmed order and invalid inputs')
