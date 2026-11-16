"""Internal-network component check, explicitly using P1 dev fixtures."""
import importlib.util,json,os,re,smtplib,sys,time,urllib.request,urllib.error
from email.message import EmailMessage
web='http://frontend';mailweb='http://mailpit:8025'
def request(url,body=None,token=None,extra_headers=None):
 headers={'Content-Type':'application/json',**(extra_headers or {})}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(url,data=json.dumps(body).encode() if body is not None else None,headers=headers)
 try:
  with urllib.request.urlopen(req,timeout=3) as r:return r.status,r.read(),r.headers
 except urllib.error.HTTPError as e:return e.code,e.read(),e.headers

def ready(url,body=None):
 deadline=time.monotonic()+50
 while time.monotonic()<deadline:
  try:
   status,data,h=request(url,body)
   if status==200:return data
  except OSError:pass
  time.sleep(.25)
 raise AssertionError('Local component readiness timed out')

def component_flow():
 ready(web+'/healthz');ready(mailweb+'/api/v1/messages')
 login=ready(web+'/api/v1/auth/login',{'email':'admin@example.invalid','password':os.environ['DEMO_PASSWORD']});token=json.loads(login)['accessToken']
 code,data,h=request(web+'/api/v1/auth/me?probe=gateway',token=token)
 assert code==200 and json.loads(data)['role']=='ADMIN' and h.get('Cache-Control')=='no-store'
 code,data,h=request(web+'/api/v1/auth/me?probe=anonymous');assert code==401 and json.loads(data)['code']=='UNAUTHENTICATED'
 status,index,index_headers=request(web+'/');assert status==200 and index_headers.get('Cache-Control')=='no-cache'
 asset=re.search(rb'src="(/assets/[^"]+)"',index);assert asset
 status,contents,asset_headers=request(web+asset.group(1).decode());assert status==200 and contents and 'immutable' in asset_headers.get('Cache-Control','')
 assert request(web+'/rotas/consulta')[1]==index
 assert request(web+'/assets/missing.js')[0]==404
 spec=importlib.util.spec_from_file_location('smoke','/checks/smoke-sprint2.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 module.load_env=lambda _:None
 os.environ['SMOKE_BASE_URL']=web+'/api/v1';module.run()
 status,contents,_=request(web+'/api/v1/orders?page=0&size=1',token=token);page=json.loads(contents)
 assert status==200 and page['size']==1 and len(page['items'])==1 and page['totalItems']==2
 print('PASS: internal Docker gateway preserves URI/query/auth, SPA fallback and cache rules; P1-only dev/fake workflow',flush=True)
 message=EmailMessage();message['From']='demo@example.invalid';message['To']='recipient@example.invalid';message['Subject']='Synthetic SMTP component check';message.set_content('Local capture only; not a P3 application event.')
 with smtplib.SMTP('mailpit',1025,timeout=5) as smtp:smtp.send_message(message)
 data=json.loads(ready(mailweb+'/api/v1/messages'));assert data['total']>=1
 print('PASS: synthetic SMTP message captured in Mailpit, not a P3 application notification',flush=True)

def login_rate(mode):
 ready(web+'/healthz')
 credentials={'email':'admin@example.invalid','password':os.environ['DEMO_PASSWORD']}
 if mode=='--rate-abuse':
  limited=False
  for n in range(35):
   spoof='203.0.113.'+str(n+1)
   code,data,_=request(web+'/api/v1/auth/login',{'email':'admin@example.invalid','password':'incorrect'},extra_headers={'X-Real-IP':spoof,'X-Forwarded-For':spoof,'Forwarded':'for='+spoof})
   if code==429:
    assert json.loads(data)['code']=='RATE_LIMITED';limited=True;break
   assert code==401
  assert limited,'Forged gateway headers bypassed the limit'
  code,_,_=request(web+'/api/v1/auth/login',credentials,extra_headers={'X-Real-IP':'198.51.100.77'})
  assert code==429,'Blocked client bypassed gateway rate limit with a forged header'
  print('PASS: gateway client A is limited despite rotating forged forwarding headers',flush=True)
 elif mode=='--rate-control':
  blocked=os.environ['TEST_BLOCKED_IP']
  code,data,_=request(web+'/api/v1/auth/login',credentials,extra_headers={'X-Real-IP':blocked,'X-Forwarded-For':blocked,'Forwarded':'for='+blocked})
  assert code==200,'Client B was blocked by client A exhausting the gateway window'
  assert json.loads(data)['actor']['role']=='ADMIN'
  print('PASS: distinct gateway client B can log in despite exhausted client A and forged headers',flush=True)
 else:
  code,data,_=request('http://backend:8080/api/v1/auth/login',credentials,extra_headers={'X-Real-IP':'198.51.100.88','X-Forwarded-For':'198.51.100.88','Forwarded':'for=198.51.100.88'})
  assert code==429 and json.loads(data)['code']=='RATE_LIMITED','Forged direct-backend headers bypassed client A rate limit'
  print('PASS: direct backend access ignores forged forwarding headers from an untrusted peer',flush=True)

if __name__=='__main__':
 if len(sys.argv)==1:component_flow()
 else:login_rate(sys.argv[1])
