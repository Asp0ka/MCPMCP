import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class NativeChatProtocolTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('native_chat_bridge', ROOT / 'bridge/mcp_server.py')
        assert spec is not None and spec.loader is not None
        cls.bridge = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.bridge)

    def setUp(self):
        self.calls = []
        def invoke(method, args):
            self.calls.append((method, args))
            return {'data': {'mode': 'NORMAL', 'transport': 'MIXED', 'server': {'cheats_enabled': 'unknown'}}}
        self.server = self.bridge.Server(invoke)

    def call(self, method, params=None):
        return self.server.handle({'jsonrpc': '2.0', 'id': 1, 'method': method, 'params': params or {}})

    def test_raw_text_and_action_id_are_preserved(self):
        for text in ('hello', '/time query daytime', '#mine 16 minecraft:iron_ore', '.help', '!custom foo', '  #help  '):
            with self.subTest(text=text):
                args = {'action': 'chat.submit', 'arguments': {'text': text}, 'action_id': 'raw-test'}
                result = self.call('tools/call', {'name': 'execute_action', 'arguments': args})
                self.assertFalse(result['result']['isError'])
                self.assertEqual(self.calls[-1], ('execute', args))

    def test_context_is_a_discoverable_read_only_tool(self):
        tools = {tool['name']: tool for tool in self.call('tools/list')['result']['tools']}
        self.assertIn('get_context', tools)
        self.assertTrue(tools['get_context']['annotations']['readOnlyHint'])
        result = self.call('tools/call', {'name': 'get_context', 'arguments': {}})
        self.assertFalse(result['result']['isError'])
        self.assertEqual(self.calls, [('observe', {'kind': 'context', 'arguments': {}})])
        self.assertEqual(json.loads(result['result']['content'][0]['text'])['data']['server']['cheats_enabled'], 'unknown')

    def test_initialize_discovers_native_chat_without_prefix_rewriting(self):
        guidance = self.call('initialize')['result']['instructions']
        for concept in ('chat.submit', 'get_context', '不补前缀'):
            self.assertIn(concept, guidance)

if __name__ == '__main__':
    unittest.main()
