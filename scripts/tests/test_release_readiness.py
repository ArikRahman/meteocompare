"""Cheap release guards for contracts not covered by JVM unit tests.

The workflow must also compile Android instrumentation code: JVM unit tests
alone cannot detect missing Hilt bindings introduced by a new repository.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'app'
LOCALES = ('fr-FR', 'en-US', 'de-DE', 'es-ES', 'it-IT')


class ReleaseReadinessTests(unittest.TestCase):
    def test_all_production_repositories_have_instrumentation_bindings(self):
        production = (APP / 'src/main/java/com/meteocompare/app/di/RepositoryModule.kt').read_text(encoding='utf-8')
        testing = (APP / 'src/androidTest/java/com/meteocompare/app/testutil/TestRepositoryModule.kt').read_text(encoding='utf-8')
        self.assertIn('replaces = [RepositoryModule::class]', testing)
        repository_types = set(re.findall(r'\)\s*:\s*(\w+Repository)\b', production))
        testing_types = set(re.findall(r'\)\s*:\s*(\w+Repository)\b', testing))
        self.assertGreaterEqual(len(repository_types), 9)
        self.assertEqual(repository_types, testing_types)
        fake = APP / 'src/androidTest/java/com/meteocompare/app/testutil/FakeUvIndexRepository.kt'
        self.assertTrue(fake.is_file())
        self.assertIn('override suspend fun get(city: City)', fake.read_text(encoding='utf-8'))

    def test_release_workflow_compiles_android_instrumentation(self):
        workflow = (ROOT / '.github/workflows/release-fdroid.yml').read_text(encoding='utf-8')
        self.assertIn(':app:assembleDebugAndroidTest', workflow)
        self.assertIn(':app:testDebugUnitTest', workflow)
        self.assertIn(':app:testReleaseUnitTest', workflow)
        self.assertIn(':app:lintDebug', workflow)
        self.assertIn(':app:assembleRelease', workflow)
        self.assertIn(':app:bundleRelease', workflow)

    def test_version_1171_has_five_localized_release_notes(self):
        build = (APP / 'build.gradle.kts').read_text(encoding='utf-8')
        self.assertRegex(build, r'versionCode\s*=\s*42\b')
        self.assertRegex(build, r'versionName\s*=\s*"1\.17\.1"')
        for locale in LOCALES:
            with self.subTest(locale=locale):
                note = (ROOT / f'fastlane/metadata/android/{locale}/changelogs/42.txt').read_text(encoding='utf-8').strip()
                self.assertTrue(note)
                self.assertLessEqual(len(note), 500)

    def test_home_uv_avoids_refetch_for_renames_but_reloads_on_move(self):
        home = (APP / 'src/main/java/com/meteocompare/app/ui/citylist/CityListViewModel.kt').read_text(encoding='utf-8')
        self.assertIn('requestedUvLocations', home)
        self.assertIn('city.latitude to city.longitude', home)
        self.assertIn('uvById.update { it - city.id }', home)
        self.assertIn('current.latitude == city.latitude && current.longitude == city.longitude', home)
        self.assertNotIn('requestedUvIds', home)


if __name__ == '__main__':
    unittest.main()
