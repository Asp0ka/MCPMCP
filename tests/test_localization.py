import json,re,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'versions/1.20.1-forge/src/main'
class LocalizationTest(unittest.TestCase):
 def test_locales_cover_ui(self):
  folder=BASE/'resources/assets/mcpmcp/lang'
  en=json.loads((folder/'en_us.json').read_text(encoding='utf-8'))
  zh=json.loads((folder/'zh_cn.json').read_text(encoding='utf-8'))
  self.assertEqual(set(en),set(zh))
  self.assertTrue(all(isinstance(v,str) and v.strip() for v in en.values()))
  self.assertTrue(all(isinstance(v,str) and v.strip() for v in zh.values()))
  for filename in ('SettingsScreen.java','ServerSettingsScreen.java','PluginListScreen.java','PluginForceScreen.java'):
   source=(BASE/'java/dev/mcpmcp/forge'/filename).read_text(encoding='utf-8')
   for key in re.findall(r'"(mcpmcp\.[a-z_.]+)"',source):
    if not key.endswith('.'):
     self.assertIn(key,en,filename)
  self.assertIn('mcpmcp.plugins.version_mismatch',en)
  self.assertIn('mcpmcp.plugins.force_confirm',en)
 def test_settings_protection(self):
  source=(BASE/'java/dev/mcpmcp/forge/UiActions.java').read_text(encoding='utf-8')
  self.assertIn('instanceof McpmcpSettingsScreen',source)
if __name__=='__main__':unittest.main()
