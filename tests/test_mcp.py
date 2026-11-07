import importlib.util, pathlib, unittest, json, subprocess, sys, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
class ProtocolTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  spec=importlib.util.spec_from_file_location('mcpmcp_bridge',ROOT/'bridge/mcp_server.py');assert spec is not None and spec.loader is not None;cls.module=importlib.util.module_from_spec(spec);spec.loader.exec_module(cls.module)
 def setUp(self):
  self.calls=[]
  def invoke(method,args):self.calls.append((method,args));return {'data':{},'observed_at':123}
  self.server=self.module.Server(invoke)
 def call(self,method,params=None):return self.server.handle({'jsonrpc':'2.0','id':1,'method':method,'params':params or {}})
 def test_init(self):self.assertEqual(self.call('initialize',{'protocolVersion':'2025-03-26'})['result']['serverInfo']['name'],'mcpmcp')
 def test_notification(self):self.assertIsNone(self.server.handle({'jsonrpc':'2.0','method':'notifications/initialized'}))
 def test_tools_closed_schema(self):
  tools=self.call('tools/list')['result']['tools'];self.assertGreaterEqual(len(tools),12);self.assertTrue(all(t['inputSchema'].get('additionalProperties') is False for t in tools))
 def test_observe(self):
  r=self.call('tools/call',{'name':'get_player_state','arguments':{}});self.assertFalse(r['result']['isError']);self.assertEqual(self.calls[0],('observe',{'kind':'player','arguments':{}}))
 def test_recipe_cursor_forwarding(self):
  pages=iter([{'data':{'recipes':[{'id':'first'}],'next_cursor':'recipes:opaque-2'}},{'data':{'recipes':[{'id':'second'}],'next_cursor':None}}])
  def provider(method,args):self.calls.append((method,args));return next(pages)
  self.server.invoke=provider
  first=self.call('tools/call',{'name':'get_recipes','arguments':{'query':'stone','limit':1}})
  cursor=json.loads(first['result']['content'][0]['text'])['data']['next_cursor']
  second=self.call('tools/call',{'name':'get_recipes','arguments':{'query':'stone','limit':1,'cursor':cursor}})
  self.assertNotIn('error',second)
  self.assertEqual(json.loads(second['result']['content'][0]['text'])['data']['recipes'][0]['id'],'second')
  self.assertEqual(self.calls[-1],('observe',{'kind':'recipes','arguments':{'query':'stone','limit':1,'cursor':'recipes:opaque-2'}}))
 def test_recipe_offset_rejected(self):
  response=self.call('tools/call',{'name':'get_recipes','arguments':{'offset':1}})
  self.assertIn('error',response)
  self.assertEqual(response['error']['code'],-32602)
  self.assertEqual(self.calls,[])
 def test_action_id_preserved(self):
  def fail(*x):raise self.module.BridgeError('offline')
  self.server.invoke=fail;r=self.call('tools/call',{'name':'execute_action','arguments':{'action':'player.look','arguments':{'yaw':1}}})['result'];self.assertTrue(r['isError']);d=json.loads(r['content'][0]['text']);self.assertTrue(d['action_id']);self.assertTrue(d['delivery_unknown'])
 def test_unknown_rejected(self):self.assertEqual(self.call('tools/call',{'name':'execute_action','arguments':{'action':'x','unexpected':1}})['error']['code'],-32602)
 def test_invalid_nested(self):
  for a in ({'action':'x','arguments':[]},{'action':'x','arguments':{'n':float('nan')}},{'action':'x','action_id':'../../x'}):self.assertEqual(self.call('tools/call',{'name':'execute_action','arguments':a})['error']['code'],-32602)
 def test_no_external_file_tool(self):
  names={x['name'] for x in self.module.TOOLS};self.assertNotIn('write_file',names);self.assertIn('write_plugin_file',names)
 def test_inventory_detail_contract(self):
  r=self.call('tools/call',{'name':'get_inventory','arguments':{'detail':'detailed'}});self.assertFalse(r['result']['isError']);self.assertEqual(self.calls[0][1]['arguments']['detail'],'detailed')
 def test_invalid_detail_enum(self):
  self.assertEqual(self.call('tools/call',{'name':'get_inventory','arguments':{'detail':'full'}})['error']['code'],-32602)
 def test_stdio(self):
  with tempfile.TemporaryDirectory() as d:
   p=subprocess.run([sys.executable,str(ROOT/'bridge/mcp_server.py'),'--config',str(pathlib.Path(d)/'missing')],input=b'{bad}\n{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}\n',capture_output=True,timeout=8)
   self.assertEqual(p.returncode,0);self.assertEqual(p.stderr,b'');lines=[json.loads(x) for x in p.stdout.splitlines()];self.assertEqual(lines[0]['error']['code'],-32700);self.assertIn('result',lines[1])
if __name__=='__main__':unittest.main()
