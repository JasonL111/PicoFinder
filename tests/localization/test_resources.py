"""Resource contracts for both shipped languages; runs without an Android SDK."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
RES=ROOT/'android/app/src/main/res'


class ResourceTests(unittest.TestCase):
    def test_english_and_chinese_keys_and_format_arguments_match(self):
        english=ET.parse(RES/'values/strings.xml').getroot()
        chinese=ET.parse(RES/'values-zh-rCN/strings.xml').getroot()
        e={x.attrib['name']:x for x in english if x.attrib.get('translatable')!='false'}
        c={x.attrib['name']:x for x in chinese}
        self.assertEqual(e.keys(),c.keys())
        pattern=r'%(?:\d+\$)?[-0-9.]*[a-zA-Z]'
        for key in e:
            with self.subTest(key=key):
                # Plural categories differ by language (Chinese uses only "other").
                if e[key].tag == 'plurals':
                    english_args = {tuple(sorted(re.findall(pattern, item.text or ''))) for item in e[key]}
                    chinese_args = {tuple(sorted(re.findall(pattern, item.text or ''))) for item in c[key]}
                    self.assertEqual(english_args, chinese_args)
                else:
                    self.assertEqual(sorted(re.findall(pattern,''.join(e[key].itertext()))), sorted(re.findall(pattern,''.join(c[key].itertext()))))
                self.assertTrue(''.join(c[key].itertext()).strip())
                self.assertFalse(re.search(r'[\u4e00-\u9fff]',''.join(e[key].itertext())))

    def test_production_kotlin_has_no_hardcoded_chinese_ui_copy(self):
        for source in ('main','debug','release'):
            for file in (ROOT/'android/app/src'/source).rglob('*.kt'):
                self.assertIsNone(re.search(r'[\u4e00-\u9fff]',file.read_text()),str(file))

    def test_supported_locales_and_icon_references_are_declared(self):
        ns='{http://schemas.android.com/apk/res/android}'
        locales=ET.parse(RES/'xml/locales_config.xml').getroot()
        self.assertEqual({'en','zh-CN'},{x.attrib[ns+'name'] for x in locales})
        app=ET.parse(ROOT/'android/app/src/main/AndroidManifest.xml').getroot().find('application')
        self.assertEqual('@xml/locales_config',app.attrib[ns+'localeConfig'])
        for attr in ('icon','roundIcon'):
            stem=app.attrib[ns+attr].split('/')[-1]
            adaptive=ET.parse(RES/'mipmap-anydpi'/f'{stem}.xml').getroot()
            self.assertEqual('adaptive-icon',adaptive.tag)
            self.assertEqual({'background','foreground','monochrome'},{child.tag for child in adaptive})


if __name__=='__main__':
    unittest.main()
