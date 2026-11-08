import importlib.util
import json
import os
import uuid
import hmac
import hashlib
import urllib.parse
from pathlib import Path
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('mcp_discovery_bridge',ROOT/'bridge/mcp_server.py')
assert spec is not None and spec.loader is not None
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class DiscoveryTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory()
  self.directory=Path(self.temp.name)
  self.config=self.directory/'config/mcpmcp.json'
  self.config.parent.mkdir()
  self.config.write_text(json.dumps({'port':5210,'token':'a'*64}))
  self.received=[]
  self.identity= str(uuid.uuid4())
  self.identity_changes={}
  self.signing_key=b"a"*64
  self.identity_close=False
  self.identity_redirect=False
  self.sent_authorizations=[]
  outer=self
  class Handler(BaseHTTPRequestHandler):
   protocol_version="HTTP/1.1"
   def do_GET(self):
    if outer.identity_redirect:
     self.send_response(302);self.send_header("Location","/call");self.send_header("Content-Length","0");self.end_headers();return
    nonce=urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query).get("challenge",[""])[0]
    data={"instance":outer.identity,"pid":os.getpid(),"port":outer.port,"challenge":nonce};data.update(outer.identity_changes)
    message="mcpmcp-identity-v1\n"+data["instance"]+"\n"+str(data["pid"])+"\n"+str(data["port"])+"\n"+data["challenge"]
    data["proof"]=hmac.new(outer.signing_key,message.encode(),hashlib.sha256).hexdigest()
    raw=json.dumps(data).encode();self.send_response(200);self.send_header("Content-Type","application/json");self.send_header("Content-Length",str(len(raw)))
    if outer.identity_close:self.send_header("Connection","close")
    self.end_headers();self.wfile.write(raw)
   def log_message(self,format,*args):pass
   def do_POST(self):
    outer.received.append(self.path)
    outer.sent_authorizations.append(self.headers.get("Authorization"))
    length=int(self.headers['Content-Length']);body=json.loads(self.rfile.read(length))
    data=json.dumps({'data':{'echo_method':body['method']}}).encode()
    self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
  self.server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
  self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
  self.port=self.server.server_address[1]
 def tearDown(self):
  self.server.shutdown();self.server.server_close();self.thread.join();self.temp.cleanup()
 def runtime(self,**changes):
  data={'running':True,'port':self.port,'game_directory':str(self.directory),'pid':os.getpid(),'instance':self.identity};data.update(changes)
  self.config.with_name('mcpmcp-runtime.json').write_text(json.dumps(data))
 def test_discovered_port_used_without_overwriting_config(self):
  self.runtime();before=self.config.read_bytes()
  result=module.Bridge(self.config)('status',{})
  self.assertEqual(result['data']['echo_method'],'status')
  self.assertEqual(self.config.read_bytes(),before)
  self.assertEqual(self.received,['/call'])
 def test_stopped_server_does_not_use_config_port(self):
  self.runtime(running=False)
  with self.assertRaisesRegex(module.BridgeError,'server_not_running'):module.Bridge(self.config)('status',{})
  self.assertEqual(self.received,[])
 def test_mismatched_instance_rejected(self):
  self.runtime(game_directory=str(self.directory/'other'))
  with self.assertRaisesRegex(module.BridgeError,'invalid_runtime'):module.Bridge(self.config)('status',{})
  self.assertEqual(self.received,[])
 def test_invalid_discovery_fails_closed(self):
  self.runtime(port=True)
  with self.assertRaisesRegex(module.BridgeError,'invalid_runtime'):module.Bridge(self.config)('status',{})
 def test_stale_pid_rejected_before_credential_or_game_request(self):
  self.runtime(pid=2147483647)
  with self.assertRaisesRegex(module.BridgeError,'runtime_identity'):module.Bridge(self.config)('status',{})
  self.assertEqual(self.received,[])
  self.assertEqual(self.sent_authorizations,[])
 def test_stale_instance_rejected_before_credential_or_game_request(self):
  self.runtime(instance=str(uuid.uuid4()))
  with self.assertRaisesRegex(module.BridgeError,'runtime_identity'):module.Bridge(self.config)('status',{})
  self.assertEqual(self.sent_authorizations,[])
 def test_forged_listener_rejected_without_bearer(self):
  self.signing_key=b"b"*64
  self.runtime()
  with self.assertRaisesRegex(module.BridgeError,'runtime_identity'):module.Bridge(self.config)('status',{})
  self.assertEqual(self.sent_authorizations,[])
 def test_identity_redirect_never_receives_bearer(self):
  self.runtime();self.identity_redirect=True
  with self.assertRaises(module.BridgeError):module.Bridge(self.config)('status',{})
  self.assertEqual(self.received,[])
 def test_verified_connection_closing_never_reconnects_with_bearer(self):
  self.runtime();self.identity_close=True
  with self.assertRaisesRegex(module.BridgeError,'runtime_identity'):module.Bridge(self.config)('status',{})
  self.assertEqual(self.sent_authorizations,[])
 def test_legacy_config_without_discovery_still_connects(self):
  self.config.write_text(json.dumps({'port':self.port,'token':'a'*64}))
  self.assertEqual(module.Bridge(self.config)('status',{})['data']['echo_method'],'status')

if __name__=='__main__':unittest.main()
