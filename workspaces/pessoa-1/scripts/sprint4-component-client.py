"""Internal-network component check, explicitly using P1 dev fixtures."""
import importlib.util,json,os,re,smtplib,time,urllib.request,urllib.error
from email.message import EmailMessage
web='http://frontend';mailweb='http://mailpit:8025'
def request(url,body=None,token=None):
 headers={'Content-Type':'application/json'}
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
