from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from urllib.parse import urlsplit,parse_qs
from pathlib import Path
import json,time,os,datetime,hashlib,re
repo=Path(__file__).resolve().parents[4]
root=Path(os.environ.get('ANDROID_UI_OUTPUT',str(repo/'app/build/reports/compact-layout')))
root.mkdir(parents=True,exist_ok=True)
def uid(n):return f'10000000-0000-4000-8000-{n:012d}'
user={'id':uid(1),'email':'layout@example.test','displayName':'本地验收同学','role':'user','active':True,'status':'active','schoolEmailVerified':True,'followingCount':12,'followerCount':8}
hall={'id':uid(10),'name':'本地验收食堂','coverImageUrl':'','active':True,'status':'active'}
stalls=[{'id':uid(100+i),'diningHallId':hall['id'],'name':n,'active':True,'status':'active','averageScore':4.2,'reviewCount':3} for i,n in enumerate(['现做面食','今天想吃的盖浇饭和特色小炒','早餐粥铺','空档口','川味麻辣烫','饺子馄饨','煲仔饭','甜品饮料'])]
dishes={s['id']:[{'id':uid(1000+i*10+j),'stallId':s['id'],'name':('香菇鸡腿面' if j==0 else ['番茄牛肉面','清汤馄饨面','青椒肉丝面','手工水饺'][j-1]),'price':None if j==4 else 12+j,'priceUnit':'bowl' if i==0 else 'serving','description':'热乎的一餐，现点现做。' if j%2==0 else '', 'coverImageUrl':'/api/v1/media/'+uid(999),'averageScore':None if j==4 else 4.2,'reviewCount':3,'revision':1,'active':True,'status':'active'} for j in range(5)] if i!=3 else [] for i,s in enumerate(stalls)}
empty={'items':[],'total':0,'page':1,'size':20,'hasMore':False,'nextCursor':None}
state={'sessionDelay':0,'hallDelay':0,'sessionFailure':False}
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def log(self,status):
  with (root/'requests.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps({'method':self.command,'path':self.path,'status':status})+'\n')
 def raw(self,status,payload,kind,headers=None):
  self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(payload)))
  for key,value in (headers or {'Cache-Control':'no-store'}).items():self.send_header(key,value)
  self.end_headers()
  try:self.wfile.write(payload)
  except (BrokenPipeError,ConnectionResetError):pass
  self.log(status)
 def send(self,data,status=200):
  payload=json.dumps({'code':'OK' if status<400 else 'UNAVAILABLE','message':'' if status<400 else '本地模拟加载失败','data':data,'requestId':'layout-fixture'},ensure_ascii=False).encode()
  path=urlsplit(self.path).path
  public=status==200 and re.fullmatch(r'/api/v1/(?:dining-halls|dining-halls/[0-9a-f-]{36}/stalls|stalls/[0-9a-f-]{36}(?:/dishes)?|dishes/search|dishes/[0-9a-f-]{36}(?:/images)?|search/(?:stalls|dishes))',path)
  if public:
   etag='"catalog-v1-'+hashlib.sha256(json.dumps(data,sort_keys=True).encode()).hexdigest()+'"'
   headers={'Cache-Control':'private, no-cache','X-Foodtime-Public-Catalog':'1','ETag':etag}
   return self.raw(304,b'','application/json',headers) if self.headers.get('If-None-Match')==etag else self.raw(status,payload,'application/json',headers)
  self.raw(status,payload,'application/json')
 def do_GET(self):
  url=urlsplit(self.path);p=url.path;q=parse_qs(url.query)
  if p in ['/api/v1/search/stalls','/api/v1/search/dishes']:
   query={key:(values if key=='diningHallIds' else int(values[0]) if key in ['page','size','previewSize'] else values[0]) for key,values in q.items()}
   return self.search(query,p+'/query')
  if p.startswith('/api/v1/media/'):
   image=repo/'app/src/main/assets/images/hall_1.webp'
   etag='\"fixture-catalog-image\"'
   headers={'Cache-Control':'public, max-age=31536000, immutable','ETag':etag}
   return self.raw(304,b'','image/webp',headers) if self.headers.get('If-None-Match')==etag else self.raw(200,image.read_bytes(),'image/webp',headers)
  if p=='/api/v1/search/capabilities':return self.send({'distanceHallIds':[],'coordinateSystem':'WGS84','distanceKind':'straight-line'})
  if p in ['/api/v1/auth/me','/api/v1/users/me/profile']:
   time.sleep(state['sessionDelay']);return self.send(None,503) if state['sessionFailure'] else self.send(user)
  if p=='/api/v1/auth/csrf':return self.send({'token':'fixture','headerName':'X-CSRF-TOKEN'})
  if p.endswith('/capabilities'):return self.send({'revision':'fixture','capabilities':[]})
  if p=='/api/v1/dining-halls':
   time.sleep(state['hallDelay']);return self.send([hall])
  if p=='/api/v1/dining-halls/'+hall['id']:return self.send(hall)
  if p.endswith('/stalls'):return self.send(stalls)
  if p.startswith('/api/v1/stalls/') and p.endswith('/dishes'):
   time.sleep(.1);return self.send(dishes.get(p.split('/')[-2],[]))
  if p.startswith('/api/v1/stalls/'):return self.send(next((s for s in stalls if s['id']==p.split('/')[-1]),{}))
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
  if p.endswith('/users/me/community-favorites'):
   indices=[0] if q.get('unfiled')==['true'] else [1,2] if q.get('groupId') else [0,1,2]
   posts=[{'id':uid(9010+i),'userId':user['id'],'userDisplayName':user['displayName'],'title':'收藏食记'+str(i),'content':'本地收藏夹验收','status':'approved','imageUrls':['/api/v1/media/'+uid(999)],'images':[{'id':uid(9110+i),'url':'/api/v1/media/'+uid(999),'width':800,'height':600,'sortOrder':0}],'createdAt':'2026-11-20T00:00:00Z','likeCount':0,'commentCount':0,'favoriteCount':1,'viewCount':0,'contentRevision':1} for i in indices]
   return self.send({**empty,'items':posts,'total':len(indices) if q.get('groupId') or q.get('unfiled') else 52})
  if p.endswith('/users/me/community-posts'):return self.send({**empty,'total':65})
  if p.endswith('/users/me/venue-reviews'):return self.send({**empty,'total':43})
  if p.endswith('/unread-count'):return self.send({'unreadCount':0})
  if p.endswith('/notices/current'):return self.send(None)
  if p.endswith('/favorite-groups'):return self.send([{'id':uid(9000),'kind':'post','name':'晚饭收藏','sortOrder':0,'revision':1,'itemCount':2}] if q.get('kind')==['post'] else [])
  if p.endswith('/notices/current-set'):return self.send([])
  if p.endswith('/community/pins'):return self.send({'revision':0,'items':[],'canManage':False})
  if p.endswith('/recommendations/daily'):
   items=[{'stallId':s['id'],'stallName':s['name'],'diningHallId':hall['id'],'diningHallName':hall['name'],'averageRating':4.2,'reviewCount':3,'imageUrl':'/api/v1/media/'+uid(999),'recommendationType':'QUALITY','recommendationScore':.9,'reasonCode':'GOOD_REPUTATION','reason':'同学好评推荐'} for s in stalls[:2]]
   today=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).date().isoformat()
   return self.send({'date':today,'algorithmVersion':'fixture','total':len(items),'items':items})
  return self.send(empty)
 def search(self,query,path):
  term=query.get('q','');page=query.get('page',1);size=query.get('size',10);preview=query.get('previewSize',4)
  if path=='/api/v1/search/notes/query':
   posts=[{'id':uid(8000+i),'userId':user['id'],'userDisplayName':user['displayName'],'title':term+'笔记'+str(i),'content':'本地界面验收笔记','imageUrls':['/api/v1/media/'+uid(999)],'images':[{'id':uid(8500+i),'url':'/api/v1/media/'+uid(999),'width':800,'height':600,'sortOrder':0}],'status':'approved','createdAt':'2026-11-20T00:00:00Z','likeCount':3,'commentCount':0,'favoriteCount':0,'viewCount':0,'contentRevision':1} for i in range(4)]
   rows=[{'post':post,'distanceMeters':None,'diningHallName':hall['name']} for post in posts]
  elif path=='/api/v1/search/dishes/query':
   rows=[{**d,'imageUrl':d['coverImageUrl']} for d in dishes.get(query.get('stallId'),[]) if term in d['name']]
  else:
   rows=[]
   for stall in stalls:
    matches=[{**d,'imageUrl':d['coverImageUrl']} for d in dishes[stall['id']] if term in d['name']]
    if matches:rows.append({**stall,'diningHallId':hall['id'],'diningHallName':hall['name'],'distanceMeters':None,'matchCount':len(matches),'imageUrl':'/api/v1/media/'+uid(999),'dishes':matches[:preview]})
  return self.send({'items':rows[(page-1)*size:page*size],'total':len(rows),'page':page,'size':size})
 def do_POST(self):
  body=self.rfile.read(int(self.headers.get('Content-Length',0)))
  if self.path.startswith('/api/v1/search/'):return self.search(json.loads(body or '{}'),self.path)
  if self.path=='/fixture/control':state.update(json.loads(body or '{}'));return self.send(True)
  if self.path.endswith('/auth/login'):return self.send({'user':user,'sessionMode':'normal'})
  return self.send({})
ThreadingHTTPServer(('127.0.0.1',18530),Handler).serve_forever()
