"""Contracts for the localized widget picker and model specifications."""
from pathlib import Path
import re
import unittest
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'app/src/main/res'
LOCALES = ['values', 'values-en', 'values-de', 'values-es', 'values-it']


def strings_from(locale):
    result = {}
    for xml in (RES / locale).glob('strings*.xml'):
        for element in ET.parse(xml).getroot():
            if element.tag == 'string':
                key = element.get('name')
                if key in result:
                    raise AssertionError(f'Duplicate string {locale}/{key}')
                result[key] = ''.join(element.itertext())
    return result


class ModelAndWidgetDescriptionsTest(unittest.TestCase):
    def test_picker_descriptions_explain_resizing_in_five_languages(self):
        for locale in LOCALES:
            values = strings_from(locale)
            for key in ('widget_description', 'widget_description_insight'):
                self.assertGreater(len(values[key]), 25, (locale, key))
                self.assertNotRegex(values[key], r'\b(?:2|4|5)[×x](?:1|2)\b')
            self.assertNotRegex(values['widget_config_forecast_mode_note'], r'\b(?:2|4|5)[×x](?:1|2)\b')

    def test_models_metadata_specifications_are_localized(self):
        required = [
            'model_metadata_explanation', 'model_metadata_provider_resolution',
            'model_metadata_temporal', 'model_step_1_hour', 'model_step_3_hours',
            'model_step_6_hours', 'model_step_15_minutes',
            'model_step_hourly_three', 'model_step_hourly_three_six',
        ]
        for locale in LOCALES:
            values = strings_from(locale)
            for key in required:
                self.assertTrue(values.get(key), (locale, key))
            self.assertIn('%3$d', values['model_metadata_temporal'])
            self.assertIn('%1$s', values['model_metadata_provider_resolution'])

    def test_widget_typography_has_consistent_heading_levels(self):
        text = (ROOT / 'app/src/main/java/com/meteocompare/app/widget/MeteoWidgetConfigActivity.kt').read_text()
        for key in ('widget_config_pick_city', 'widget_config_forecast_mode', 'widget_config_appearance'):
            self.assertRegex(text, rf'R.string.{key}\),\s*style = MaterialTheme.typography.titleLarge')
        for key in ('widget_config_corner_style', 'widget_config_spacing', 'widget_config_colors_section', 'widget_config_opacity', 'widget_config_preview_title'):
            self.assertRegex(text, rf'R.string.{key}\),\s*style = MaterialTheme.typography.titleMedium')


if __name__ == '__main__':
    unittest.main()
