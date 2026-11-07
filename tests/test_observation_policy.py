import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class ObservationPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('mcpmcp_observation_policy', ROOT / 'bridge/mcp_server.py')
        assert spec is not None and spec.loader is not None
        cls.bridge = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.bridge)

    def setUp(self):
        self.calls = []
        def invoke(method, args):
            self.calls.append((method, args))
            return {'data': {'blocks': [], 'next_offset': 200}, 'complete': False, 'truncated': True}
        self.server = self.bridge.Server(invoke)

    def request(self, method, params=None):
        return self.server.handle({'jsonrpc': '2.0', 'id': 1, 'method': method, 'params': params or {}})['result']

    def test_initialize_teaches_structured_first_and_visual_last(self):
        guidance = self.request('initialize')['instructions']
        for concept in ('结构化', '截图识图仅作最后手段', 'next_offset', 'dig_target_occluded'):
            with self.subTest(concept=concept):
                self.assertIn(concept, guidance)

    def test_tool_discovery_explains_query_and_visual_fallback(self):
        tools = {tool['name']: tool for tool in self.request('tools/list')['tools']}
        desc = tools['observe']['description']
        for concept in ('blocks', 'entities', 'block', 'radius', 'offset', 'next_offset', 'next_cursor', '截图识图仅作最后手段'):
            with self.subTest(concept=concept):
                self.assertIn(concept, desc)
        self.assertIn('截图', tools['get_screen']['description'])
        self.assertIn('优先', tools['get_screen']['description'])
        self.assertNotIn('cursor', tools['get_screen']['inputSchema']['properties'])
        self.assertIn('observe(kind=screen', tools['get_screen']['description'])

    def test_empty_filtered_page_is_preserved_for_continuation(self):
        args = {'kind': 'blocks', 'arguments': {'radius': 3, 'block': 'minecraft:oak_log', 'limit': 200, 'offset': 200}}
        result = self.request('tools/call', {'name': 'observe', 'arguments': args})
        data = json.loads(result['content'][0]['text'])
        self.assertEqual(self.calls, [('observe', args)])
        self.assertFalse(data['complete'])
        self.assertTrue(data['truncated'])
        self.assertEqual(data['data']['blocks'], [])
        self.assertEqual(data['data']['next_offset'], 200)

    def test_explicit_visual_fallback_still_returns_an_image(self):
        self.server.invoke = lambda method, args: {'data': {'base64': 'cG5n', 'mime': 'image/png'}}
        result = self.request('tools/call', {'name': 'observe', 'arguments': {'kind': 'screenshot', 'arguments': {'x': 0, 'y': 0, 'width': 100, 'height': 80}}})
        self.assertEqual(result['content'], [{'type': 'image', 'data': 'cG5n', 'mimeType': 'image/png'}])
        self.assertFalse(result['isError'])

if __name__ == '__main__':
    unittest.main()
