"""MCPMCP stdio bridge. All game effects go through the authenticated local mod."""
import argparse,json,math,os,re,sys,uuid,urllib.request,urllib.error
from pathlib import Path
from typing import Any
MAX_MESSAGE=1024*1024
class BridgeError(Exception):pass
class Bridge:
 def __init__(self,path):self.path=Path(path);self.opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
 def __call__(self,method,args):
  try:
   if self.path.stat().st_size>65536:raise BridgeError('config_too_large')
   c=json.loads(self.path.read_text(encoding='utf-8-sig'));port=c.get('port',5007);token=c.get('token','')
   if type(port)is not int or not 1024<=port<=65535 or not isinstance(token,str)or not 32<=len(token)<=256:raise BridgeError('invalid_local_config')
   req=urllib.request.Request(f'http://127.0.0.1:{port}/call',data=json.dumps({'method':method,'args':args},ensure_ascii=False,allow_nan=False).encode('utf-8'),headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
   with self.opener.open(req,timeout=20)as response:raw=response.read(12*1024*1024+1)
   if len(raw)>12*1024*1024:raise BridgeError('response_too_large')
   result=json.loads(raw)
   if not isinstance(result,dict):raise BridgeError('invalid_bridge_response')
   return result
  except BridgeError:raise
  except (OSError,ValueError,TypeError)as e:raise BridgeError('bridge_unavailable_or_invalid_response')from e

def string(maximum=128):return {'type':'string','minLength':1,'maxLength':maximum}
def declaration(name,description,properties=None,required=None,write=False):return {'name':name,'description':description,'inputSchema':{'type':'object','properties':properties or {},'required':required or [],'additionalProperties':False},'annotations':{'readOnlyHint':not write,'destructiveHint':write,'openWorldHint':False}}
OBJECT={'type':'object','additionalProperties':True}
TOOLS=[
 declaration('get_status','读取主菜单/世界连接、模式、插件和任务状态。'),
 declaration('list_capabilities','列出可调用动作与查询能力。'),
 declaration('describe_action','读取动作参数、执行方式、前置条件和验证说明。',{'action':string()},['action']),
 declaration('execute_action','执行一项明确指定的动作，返回 action_id；必须查询任务并读回验证。',{'action':string(),'arguments':OBJECT,'action_id':dict(string(96),pattern='^[A-Za-z0-9_.-]{1,96}$')},['action'],True),
 declaration('get_job','读取动作进度、已交送或观察成功状态。',{'action_id':string(96)},['action_id']),
 declaration('cancel_job','取消任务并清理自己拥有的输入或容器。',{'action_id':string(96)},['action_id'],True),
 declaration('observe','读取指定能力，缺失客户端信息明确为 unknown。',{'kind':string(),'arguments':OBJECT},['kind']),
 declaration('get_player_state','读取生命、经验、饥饿、装备与玩家状态。'),
 declaration('get_inventory','读取背包清单。',{'detail':dict(string(),enum=['coarse','detailed'])}),
 declaration('get_screen','读取当前屏幕、控件、焦点和菜单。'),
 declaration('get_recipes','通过原生或插件提供者查询配方。',{'query':string(),'limit':{'type':'integer','minimum':1,'maximum':100},'cursor':string(256)}),
 declaration('get_chat_messages','读取本客户端已接收的有界聊天记录。',{'after':{'type':'integer','minimum':0,'maximum':9007199254740991},'limit':{'type':'integer','minimum':1,'maximum':200}}),
 declaration('get_mods','返回加载模组、版本与兼容状态。'),
 declaration('get_paths','返回当前实例目录；远程存档目录不会猜测。'),
 declaration('get_plugins','读取插件、内容revision、热/冷加载状态。'),
 declaration('reload_plugins','原子重载声明式插件，失败保留上一版本。',write=True),
 declaration('read_plugin_file','只读取 MCPMCP-Plugin 内声明和文档，不读取其他客户端文件。',{'path':string(512)},['path']),
 declaration('write_plugin_file','只写 MCPMCP-Plugin 内文本文件；不能安装或覆盖二进制代码。',{'path':string(512),'text':{'type':'string','maxLength':262144,'minLength':0}},['path','text'],True),
]
ALIASES={'get_player_state':'player','get_inventory':'inventory','get_screen':'screen','get_recipes':'recipes','get_chat_messages':'chat','get_mods':'mods','get_paths':'paths'}
def validate(v,s,depth=0,budget=None):
 if budget is None:budget=[8192]
 budget[0]-=1
 if depth>16 or budget[0]<0:raise ValueError('json_budget_exceeded')
 if 'enum'in s and v not in s['enum']:raise ValueError('invalid_enum')
 t=s.get('type')
 if t=='object':
  if not isinstance(v,dict)or len(v)>128:raise ValueError('object_required')
  props=s.get('properties',{});extra=s.get('additionalProperties',False)
  if any(k not in v for k in s.get('required',[])):raise ValueError('missing_argument')
  for k,x in v.items():
   if not isinstance(k,str)or len(k)>256:raise ValueError('invalid_key')
   if k in props:validate(x,props[k],depth+1,budget)
   elif extra is True:bounded(x,depth+1,budget)
   else:raise ValueError('unknown_argument')
 elif t=='string':
  if not isinstance(v,str)or not s.get('minLength',0)<=len(v)<=s.get('maxLength',8192):raise ValueError('invalid_string')
  if s.get('minLength',0)>0 and not v.strip():raise ValueError('empty_string')
  if 'pattern'in s and not re.fullmatch(s['pattern'],v):raise ValueError('invalid_pattern')
 elif t=='boolean':
  if type(v)is not bool:raise ValueError('boolean_required')
 elif t in ('integer','number'):
  if type(v)not in ((int,)if t=='integer'else(int,float))or not s.get('minimum',-1e30)<=v<=s.get('maximum',1e30)or not math.isfinite(v):raise ValueError('invalid_number')
 else:bounded(v,depth,budget)
def bounded(v,depth,budget):
 if depth>16:raise ValueError('json_too_deep')
 budget[0]-=1
 if budget[0]<0:raise ValueError('json_budget_exceeded')
 if isinstance(v,dict):
  if len(v)>128:raise ValueError('object_too_large')
  for k,x in v.items():
   if len(k)>256:raise ValueError('key_too_long')
   bounded(x,depth+1,budget)
 elif isinstance(v,list):
  if len(v)>512:raise ValueError('array_too_large')
  for x in v:bounded(x,depth+1,budget)
 elif isinstance(v,str):
  if len(v)>262144:raise ValueError('string_too_large')
 elif type(v)in(int,float):
  if not -1e30<=v<=1e30 or not math.isfinite(v):raise ValueError('invalid_number')
 elif v is not None and type(v)is not bool:raise ValueError('invalid_json')
class Server:
 def __init__(self,invoke):self.invoke=invoke
 @staticmethod
 def error(i,c,m):return {'jsonrpc':'2.0','id':i,'error':{'code':c,'message':m}}
 def handle(self,m):
  if not isinstance(m,dict)or m.get('jsonrpc')!='2.0'or not isinstance(m.get('method'),str):return self.error(None,-32600,'Invalid request')
  if 'id'not in m:return None
  i=m['id'];method=m['method'];p=m.get('params',{})
  if not isinstance(p,dict):return self.error(i,-32602,'params must be object')
  if method=='initialize':r={'protocolVersion':p.get('protocolVersion')if p.get('protocolVersion')in('2025-11-25','2025-06-18','2025-03-26','2024-11-05')else'2025-03-26','serverInfo':{'name':'mcpmcp','version':'0.1.1'},'capabilities':{'tools':{'listChanged':False}},'instructions':'先读能力与状态。submitted不代表成功。禁止未经用户同意修改其他模组、配方、脚本或客户端文件；本工具只允许自身插件文本目录。'}
  elif method=='ping':r={}
  elif method=='tools/list':r={'tools':TOOLS}
  elif method=='tools/call':
   name=p.get('name');tool=next((x for x in TOOLS if x['name']==name),None)
   if not isinstance(name,str):return self.error(i,-32602,'Tool name must be a string')
   if tool is None:return self.error(i,-32602,'Unknown tool')
   a=p.get('arguments',{})
   try:validate(a,tool['inputSchema'])
   except (ValueError,TypeError,OverflowError)as e:return self.error(i,-32602,str(e))
   a=dict(a)
   data: Any
   if name=='execute_action':a.setdefault('action_id',uuid.uuid4().hex)
   try:
    if name in ALIASES:data=self.invoke('observe',{'kind':ALIASES[name],'arguments':a})
    else:data=self.invoke({'get_status':'status','list_capabilities':'capabilities','describe_action':'describe','execute_action':'execute','get_job':'job','cancel_job':'cancel','get_plugins':'plugins','reload_plugins':'plugins.reload','read_plugin_file':'plugins.read','write_plugin_file':'plugins.write'}.get(name,name),a)
   except BridgeError as e:
    data={'error':str(e)}
    if name=='execute_action':data.update(action_id=a['action_id'],delivery_unknown=True,retry_note='Query this action_id before retrying; reuse it.')
   if name=='observe'and a.get('kind')=='screenshot'and data.get('data',data).get('base64'):
    pic=data.get('data',data);r={'content':[{'type':'image','data':pic['base64'],'mimeType':pic.get('mime','image/png')}],'isError':False}
   else:r={'content':[{'type':'text','text':json.dumps(data,ensure_ascii=False,allow_nan=False,separators=(',',':'))}],'isError':'error'in data}
  else:return self.error(i,-32601,'Method not found')
  return {'jsonrpc':'2.0','id':i,'result':r}
def main():
 p=argparse.ArgumentParser();p.add_argument('--config',required=True);a=p.parse_args();s=Server(Bridge(a.config));inp=sys.stdin.buffer;out=sys.stdout.buffer
 while True:
  line=inp.readline(MAX_MESSAGE+1)
  if not line:break
  if len(line)>MAX_MESSAGE:
   while line and not line.endswith(b'\n'):line=inp.readline(MAX_MESSAGE+1)
   r=s.error(None,-32600,'Message too large')
  else:
   try:r=s.handle(json.loads(line,parse_constant=lambda x:(_ for _ in ()).throw(ValueError('nonfinite'))))
   except (ValueError,UnicodeError):r=s.error(None,-32700,'Parse error')
   except Exception:r=s.error(None,-32603,'Internal error')
  if r is not None:out.write(json.dumps(r,ensure_ascii=False,allow_nan=False).encode('utf-8')+b'\n');out.flush()
if __name__=='__main__':main()
