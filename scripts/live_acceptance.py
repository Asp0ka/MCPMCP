"""Bounded real-client regression checks. Only the dedicated test instance.
Never prints the bearer token. Writes request/result evidence under .local/.
"""
import argparse
import importlib.util
import json
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class Client:
    def __init__(self, instance):
        self.instance = Path(instance)
        if self.instance.name != '1.20.1-Forge_47.4.28-test':
            raise ValueError('dedicated_test_instance_required')
        spec = importlib.util.spec_from_file_location('mcpmcp_bridge', ROOT / 'bridge/mcp_server.py')
        assert spec and spec.loader
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        self.bridge = mod.Bridge(self.instance / 'config/mcpmcp.json')
        self.evidence = ROOT / '.local' / 'regression-events.jsonl'
        self.evidence.parent.mkdir(parents=True, exist_ok=True)

    def call(self, method, args=None):
        result = self.bridge(method, args or {})
        with self.evidence.open('a', encoding='utf-8') as out:
            out.write(json.dumps({'method': method, 'args': args or {}, 'result': result}, ensure_ascii=False) + '\n')
        return result

    def observe(self, kind, **args):
        result = self.call('observe', {'kind': kind, 'arguments': args})
        if 'error' in result:
            raise AssertionError(result)
        return result.get('data', result)

    def action(self, action, args=None, timeout=30):
        aid = 'regression-' + uuid.uuid4().hex
        result = self.call('execute', {'action': action, 'arguments': args or {}, 'action_id': aid})
        deadline = time.monotonic() + timeout
        while result.get('status') in ('queued', 'running') and time.monotonic() < deadline:
            time.sleep(0.1)
            result = self.call('job', {'action_id': aid})
        return result

    def brewing_hit(self):
        self.action('command', {'command': '/setblock 0 101 2 minecraft:brewing_stand'})
        self.action('command', {'command': '/tp @s 0.5 101 0.5'})
        result = self.action('container.open', {'x': 0, 'y': 101, 'z': 2, 'face': 'north', 'hand': 'off'})
        assert result.get('status') != 'failed', result
        deadline = time.monotonic() + 3
        menu = self.observe('container')
        while time.monotonic() < deadline:
            menu = self.observe('container')
            if menu['menu_id'] != 0:
                break
            time.sleep(0.1)
        assert menu['class'].endswith('BrewingStandMenu'), menu
        self.action('container.close', {'menu_id': menu['menu_id']})
        assert self.observe('container')['menu_id'] == 0
        return {'brewing_shape_hit': 'passed'}

    def shield(self):
        self.action('command', {'command': '/item replace entity @s weapon.offhand with minecraft:air'})
        self.action('command', {'command': '/item replace entity @s hotbar.5 with minecraft:shield 1'})
        result = self.action('container.click', {'slot': 41, 'button': 40, 'click': 'swap'})
        assert result.get('status') == 'observed_success', result
        assert self.observe('equipment')['offhand']['id'] == 'minecraft:shield'
        return {'shield_swap': 'passed'}

    def hold_schema(self):
        spec = self.call('describe', {'action': 'player.hold'})
        assert spec['input_schema']['properties']['button']['type'] == 'string', spec
        result = self.action('player.hold', {'button': 'right', 'ticks': 1})
        assert 'error' not in result and result.get('status') == 'submitted', result
        return {'hold_schema': 'passed'}

    def packet_policy(self):
        status = self.call('status')
        assert status['configuration']['mode'] == 'NORMAL', 'run this rejection scenario in NORMAL'
        manifest = {'api': 1, 'id': 'acceptance.policy', 'mod_id': 'minecraft', 'actions': [{
            'name': 'acceptance.unsafe', 'transport': 'packet', 'permission': 'normal',
            'input_schema': {'type': 'object', 'additionalProperties': False},
            'packet': {'packet_class': 'net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket',
                'signature': ['int', 'net.minecraft.world.item.ItemStack'], 'values': [36, {'item_id': 'minecraft:oak_log', 'count': 7}]}}]}
        self.call('plugins.write', {'path': 'minecraft-policy/plugin.json', 'text': json.dumps(manifest)})
        self.call('plugins.reload')
        result = self.action('acceptance.unsafe')
        assert result.get('status') == 'failed' and result['result'].get('error') == 'packet_policy_denied', result
        return {'packet_policy': 'passed'}

    def consume(self):
        self.action('command', {'command': '/gamemode survival'})
        self.action('command', {'command': '/item replace entity @s hotbar.1 with minecraft:golden_apple 2'})
        self.action('player.select', {'slot': 1})
        try:
            result = self.action('player.hold', {'button': 'right', 'ticks': 45})
            assert result.get('status') in ('submitted', 'observed_success'), result
            time.sleep(0.2)
            apples = [s['item']['count'] for s in self.observe('inventory')['slots'] if s['item'].get('id') == 'minecraft:golden_apple']
            assert sum(apples) == 1, {'apple_counts': apples, 'effects': self.observe('player')['effects']}
        finally:
            self.action('stop')
            self.action('command', {'command': '/gamemode creative'})
        return {'consume': 'passed'}

    def cancel_container(self):
        target = {'x': -2, 'y': 101, 'z': 0, 'face': 'east', 'hand': 'off'}
        self.action('command', {'command': '/item replace block -2 101 0 container.0 with minecraft:cobblestone 8'})
        aid = 'cancel-container-' + uuid.uuid4().hex
        self.call('execute', {'action': 'container.sequence', 'arguments': {'target': target, 'steps': [{'slot': 0, 'click': 'pickup', 'button': 0}, {'op': 'wait', 'ticks': 200}]}, 'action_id': aid})
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            state = self.observe('container')
            if state['carried_count'] == 8:
                break
            time.sleep(0.05)
        self.call('cancel', {'action_id': aid})
        time.sleep(0.3)
        state = self.observe('container')
        assert state['menu_id'] == 0 and state['carried_count'] == 0, state
        return {'cancel_container': 'passed'}

    def queued_cancel(self):
        self.action('command', {'command': '/item replace entity @s hotbar.1 with minecraft:bow 1'})
        self.action('command', {'command': '/give @s minecraft:arrow 1'})
        self.action('player.select', {'slot': 1})
        a, b = 'hold-a-' + uuid.uuid4().hex, 'hold-b-' + uuid.uuid4().hex
        self.action('packet.capture_start', {'query': 'PlayerAction'})
        self.call('execute', {'action': 'player.hold', 'arguments': {'button': 'right', 'ticks': 200}, 'action_id': a})
        time.sleep(0.2)
        self.call('execute', {'action': 'player.hold', 'arguments': {'button': 'right', 'ticks': 200}, 'action_id': b})
        before = self.observe('packet_capture')['entries']
        self.call('cancel', {'action_id': b})
        time.sleep(0.1)
        after = self.observe('packet_capture')['entries']
        assert before == after, 'queued_cancel_sent_release_for_running_hold'
        self.call('execute', {'action': 'packet.capture_stop', 'arguments': {}, 'action_id': 'capture-stop-' + uuid.uuid4().hex})
        current = self.call('job', {'action_id': a})
        assert current.get('status') == 'running', current
        self.call('cancel', {'action_id': a})
        return {'queued_cancel': 'passed'}

    def move_cancel(self):
        self.action('command', {'command': '/tp @s 0.5 101 0.5'})
        aid = 'move-cancel-' + uuid.uuid4().hex
        self.call('execute', {'action': 'move.to', 'arguments': {'x': 9, 'y': 101, 'z': 9}, 'action_id': aid})
        time.sleep(0.15)
        result = self.call('job', {'action_id': aid})
        assert result.get('status') == 'running', result
        self.call('cancel', {'action_id': aid})
        time.sleep(0.3)
        a = self.observe('player')['position']
        time.sleep(0.4)
        b = self.observe('player')['position']
        assert sum((a[k] - b[k]) ** 2 for k in ('x', 'y', 'z')) < 0.05, (a, b)
        return {'move_cancel': 'passed'}

    def all_regressions(self):
        results = {}
        for scenario in ('sneak', 'brewing_hit', 'shield', 'hold_schema', 'consume', 'cancel_container', 'queued_cancel', 'move_cancel'):
            results.update(getattr(self, scenario)())
        return results

    def sneak(self):
        try:
            result = self.action('player.sneak', {'value': True})
            assert result['status'] == 'observed_success', result
            time.sleep(0.3)
            assert self.observe('player')['sneaking'], 'sneak_not_retained_across_ticks'
        finally:
            self.action('stop')
        time.sleep(0.1)
        assert not self.observe('player')['sneaking'], 'sneak_not_released'
        return {'sneak': 'passed'}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--instance', default='D:/Games/mc/Cheat/versions/1.20.1-Forge_47.4.28-test')
    parser.add_argument('--scenario', choices=['sneak', 'brewing_hit', 'shield', 'hold_schema', 'packet_policy', 'consume', 'cancel_container', 'queued_cancel', 'move_cancel', 'all_regressions'], default='sneak')
    args = parser.parse_args()
    client = Client(args.instance)
    print(json.dumps(getattr(client, args.scenario)(), ensure_ascii=False))

if __name__ == '__main__':
    main()
