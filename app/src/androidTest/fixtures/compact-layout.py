from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from urllib.parse import urlsplit,parse_qs
from pathlib import Path
import json,time,os
repo=Path(__file__).resolve().parents[4]
root=Path(os.environ.get('ANDROID_UI_OUTPUT',str(repo/'app/build/reports/compact-layout')))
root.mkdir(parents=True,exist_ok=True)
def uid(n):return f'10000000-0000-4000-8000-{n:012d}'
user={'id':uid(1),'email':'layout@example.test','displayName':'本地验收同学','role':'user','active':True,'status':'active','schoolEmailVerified':True,'followingCount':12,'followerCount':8}
hall={'id':uid(10),'name':'本地验收食堂','coverImageUrl':'','active':True,'status':'active'}
stalls=[{'id':uid(100+i),'diningHallId':hall['id'],'name':n,'active':True,'status':'active','averageScore':4.2,'reviewCount':3} for i,n in enumerate(['现做面食','今天想吃的盖浇饭和特色小炒','早餐粥铺','空档口','川味麻辣烫','饺子馄饨','煲仔饭','甜品饮料'])]
dishes={s['id']:[{'id':uid(1000+i*10+j),'stallId':s['id'],'name':('香菇鸡腿面' if j==0 else ['番茄牛肉面','清汤馄饨','青椒肉丝饭','手工水饺'][j-1]),'price':None if j==4 else 12+j,'priceUnit':'bowl' if i==0 else 'serving','description':'热乎的一餐，现点现做。' if j%2==0 else '', 'coverImageUrl':'/api/v1/media/'+uid(999),'averageScore':None if j==4 else 4.2,'reviewCount':3,'revision':1,'active':True,'status':'active'} for j in range(5)] if i!=3 else [] for i,s in enumerate(stalls)}
empty={'items':[],'total':0,'page':1,'size':20,'hasMore':False,'nextCursor':None}
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def log(self,status):
  with (root/'requests.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps({'method':self.command,'path':self.path,'status':status})+'\n')
 def raw(self,status,payload,kind):
  self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(payload)));self.send_header('Cache-Control','no-store');self.end_headers()
  try:self.wfile.write(payload)
  except (BrokenPipeError,ConnectionResetError):pass
  self.log(status)
 def send(self,data,status=200):self.raw(status,json.dumps({'code':'OK' if status<400 else 'UNAVAILABLE','message':'' if status<400 else '本地模拟加载失败','data':data,'requestId':'layout-fixture'},ensure_ascii=False).encode(),'application/json')
 def do_GET(self):
  url=urlsplit(self.path);p=url.path;q=parse_qs(url.query)
  if p.startswith('/api/v1/media/'):
   image=repo/'app/src/main/assets/images/hall_1.webp'
   return self.raw(200,image.read_bytes(),'image/webp')
  if p in ['/api/v1/auth/me','/api/v1/users/me/profile']:return self.send(user)
  if p=='/api/v1/auth/csrf':return self.send({'token':'fixture','headerName':'X-CSRF-TOKEN'})
  if p.endswith('/capabilities'):return self.send({'revision':'fixture','capabilities':[]})
  if p=='/api/v1/dining-halls':return self.send([hall])
  if p.endswith('/stalls'):return self.send(stalls)
  if p.startswith('/api/v1/stalls/') and p.endswith('/dishes'):
   time.sleep(.1);return self.send(dishes.get(p.split('/')[-2],[]))
  if p=='/api/v1/dishes/search':
   rows=[{**d,'diningHallId':hall['id'],'diningHallName':hall['name'],'stallName':s['name']} for s in stalls for d in dishes[s['id']] if q.get('q',[''])[0] in d['name']]
   page=int(q.get('page',['1'])[0]);return self.send({**empty,'items':rows[(page-1)*20:page*20],'total':len(rows),'page':page})
  if p.startswith('/api/v1/dishes/'):
   ident=p.split('/')[4];d=next((d for rows in dishes.values() for d in rows if d['id']==ident),None)
   if p.endswith('/images'):return self.send({**empty,'items':[{'id':uid(998),'imageUrl':'/api/v1/media/'+uid(999)}],'total':1})
   if p.endswith('/rating'):return self.send({'averageScore':4.2,'reviewCount':3,'starCounts':[0,0,0,2,1]})
   if p.endswith('/reviews'):return self.send(empty)
   if d:return self.send({**d,'diningHallId':hall['id'],'diningHallName':hall['name'],'stallName':next(s['name'] for s in stalls if s['id']==d['stallId'])})
  if p.endswith('/users/me/dish-favorites'):return self.send({**empty,'total':31})
  if p.endswith('/users/me/community-favorites'):return self.send({**empty,'total':52})
  if p.endswith('/users/me/community-posts'):return self.send({**empty,'total':65})
  if p.endswith('/users/me/venue-reviews'):return self.send({**empty,'total':43})
  if p.endswith('/unread-count'):return self.send({'unreadCount':0})
  if p.endswith('/notices/current'):return self.send(None)
  if p.endswith('/notices/current-set') or p.endswith('/favorite-groups'):return self.send([])
  if p.endswith('/community/pins'):return self.send({'revision':0,'items':[],'canManage':False})
  if p.endswith('/recommendations/daily'):return self.send({'date':'2026-11-23','algorithmVersion':'fixture','items':[]})
  return self.send(empty)
 def do_POST(self):
  self.rfile.read(int(self.headers.get('Content-Length',0)))
  if self.path.endswith('/auth/login'):return self.send({'user':user,'sessionMode':'normal'})
  return self.send({})
ThreadingHTTPServer(('127.0.0.1',18530),Handler).serve_forever()
