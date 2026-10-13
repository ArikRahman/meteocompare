"""Tests autonomes du contrôle de ressources, sans SDK ni Gradle."""

import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

CHECKER_FILE = Path(__file__).resolve().parents[1] / "check_android_strings.py"
spec = importlib.util.spec_from_file_location("check_android_strings", CHECKER_FILE)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class AndroidStringAuditTests(unittest.TestCase):
    def test_regex_handles_strings_plurals_and_xml(self):
        content = "R.string.title R.plurals.count @string/label @plurals/choices R.array.values"
        found = {
            (match.group(1) or match.group(3), match.group(2) or match.group(4))
            for match in checker.REFERENCE_RE.finditer(content)
        }
        self.assertEqual(
            found,
            {("string", "title"), ("plurals", "count"), ("string", "label"),
             ("plurals", "choices"), ("array", "values")},
        )

    def test_unused_and_missing_references_detected(self):
        with tempfile.TemporaryDirectory() as temp:
            project = Path(temp)
            res = project / "app/src/main/res"
            (res / "values").mkdir(parents=True)
            (res / "values-fr").mkdir()
            (project / "app/src/main/java").mkdir(parents=True)
            (res / "values/strings.xml").write_text(
                '<resources><string name="used">oui</string><string name="dead">non</string>'
                '<plurals name="items"><item quantity="other">%d</item></plurals></resources>'
            )
            (res / "values-fr/strings.xml").write_text(
                '<resources><string name="used">oui</string><string name="dead">non</string>'
                '<plurals name="items"><item quantity="other">%d</item></plurals></resources>'
            )
            (project / "app/src/main/java/Test.kt").write_text(
                'val label = R.string.used\nval count = R.plurals.items\n'
            )
            with patch.object(checker, "PROJECT_ROOT", project), \
                 patch.object(checker, "ANDROID_APP", project / "app"), \
                 patch.object(checker, "RESOURCES", res):
                definitions, errors = checker.resource_definitions()
                refs = checker.resource_references()
                self.assertEqual(errors, [])
                self.assertEqual(definitions.keys() - refs, {("string", "dead")})
                self.assertNotIn(("plurals", "items"), definitions.keys() - refs)


if __name__ == "__main__":
    unittest.main()
