"""Checks for the public Karoo application-manifest integration."""

import json
import re
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
MANIFEST_URL = "https://github.com/Danybs/powerclimb/releases/latest/download/manifest.json"


class KarooManifestTest(unittest.TestCase):
    def test_project_release_references_target_original_repository(self):
        for relative in ("README.md", "ci/README.md", "app/src/main/AndroidManifest.xml"):
            with self.subTest(file=relative):
                text = (ROOT / relative).read_text()
                projects = re.findall(r"https://github\.com/([^/]+/powerclimb)/", text)
                if relative != "ci/README.md":
                    self.assertTrue(projects)
                for project in projects:
                    self.assertEqual(project, "Danybs/powerclimb")
                self.assertNotIn("(../../releases/", text)
        self.assertIn("\n  Danybs/powerclimb manifest.json\n", (ROOT / "ci/README.md").read_text())

    def test_application_exposes_manifest_without_changing_extension_discovery(self):
        app = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
        metadata = app.find("meta-data[@" + ANDROID + "name='io.hammerhead.karooext.MANIFEST_URL']")
        self.assertIsNotNone(metadata)
        self.assertEqual(metadata.get(ANDROID + "value"), MANIFEST_URL)

        service = app.find("service")
        self.assertEqual(service.get(ANDROID + "name"), ".PowerClimbExtension")
        self.assertEqual(service.get(ANDROID + "exported"), "true")
        self.assertEqual(
            service.find("intent-filter/action").get(ANDROID + "name"),
            "io.hammerhead.karooext.KAROO_EXTENSION",
        )
        self.assertEqual(service.find("meta-data").get(ANDROID + "resource"), "@xml/extension_info")

    def test_launcher_activity_is_registered(self):
        app = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
        activity = app.find("activity[@" + ANDROID + "name='.LauncherActivity']")
        self.assertIsNotNone(activity)
        self.assertEqual(activity.get(ANDROID + "exported"), "true")
        intent = activity.find("intent-filter")
        self.assertEqual(intent.find("action").get(ANDROID + "name"), "android.intent.action.MAIN")
        self.assertEqual(intent.find("category").get(ANDROID + "name"), "android.intent.category.LAUNCHER")

    def test_launcher_reuses_single_task_when_reopened(self):
        app = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
        activity = app.find("activity[@" + ANDROID + "name='.LauncherActivity']")
        self.assertEqual(activity.get(ANDROID + "launchMode"), "singleTask")

    def test_launcher_summary_is_visible_localized_and_scrollable(self):
        app = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
        activity = app.find("activity[@" + ANDROID + "name='.LauncherActivity']")
        self.assertEqual(activity.get(ANDROID + "theme"), "@style/Theme.PowerClimb")
        layout = ET.parse(ROOT / "app/src/main/res/layout/launcher_summary.xml").getroot()
        scroll = layout if layout.tag == "ScrollView" else layout.find("ScrollView")
        self.assertIsNotNone(scroll)
        self.assertEqual(scroll.get(ANDROID + "fillViewport"), "true")
        text_refs = {node.get(ANDROID + "text") for node in layout.iter("TextView")}
        expected = {"app_name", "about_summary", "about_fields", "about_usage", "about_notes"}
        self.assertEqual(text_refs, {"@string/" + name for name in expected})
        for locale in ("values", "values-es"):
            with self.subTest(locale=locale):
                strings = ET.parse(ROOT / "app/src/main/res" / locale / "strings.xml").getroot()
                values = {node.get("name"): node.text for node in strings.findall("string")}
                for name in expected:
                    self.assertTrue(values.get(name))
                self.assertIn(values["datatype_name_numeric"], values["about_fields"])
                self.assertIn(values["datatype_name_zone"], values["about_fields"])

    def test_visual_back_is_compact_accessible_and_outside_content(self):
        layout = ET.parse(ROOT / "app/src/main/res/layout/launcher_summary.xml").getroot()
        self.assertEqual([node.tag for node in layout], ["ScrollView", "ImageButton"])
        scroll, button = list(layout)
        self.assertEqual(button.get(ANDROID + "id"), "@+id/button_back")
        self.assertEqual(button.get(ANDROID + "layout_width"), "54dp")
        self.assertEqual(button.get(ANDROID + "layout_height"), "54dp")
        self.assertEqual(button.get(ANDROID + "enabled"), "true")
        self.assertEqual(button.get(ANDROID + "contentDescription"), "@string/about_back")
        self.assertEqual(button.get(ANDROID + "src"), "@drawable/ic_back")
        self.assertEqual(button.get(ANDROID + "background"), "@drawable/back_navigation_background")
        self.assertFalse(list(layout.iter("Button")))
        for locale, expected in (("values", "Back"), ("values-es", "Volver")):
            strings = ET.parse(ROOT / "app/src/main/res" / locale / "strings.xml").getroot()
            values = {node.get("name"): node.text for node in strings.findall("string")}
            self.assertEqual(values.get("about_back"), expected)

    def test_back_overlays_full_height_scroll_without_reserved_row(self):
        layout = ET.parse(ROOT / "app/src/main/res/layout/launcher_summary.xml").getroot()
        self.assertEqual(layout.tag, "FrameLayout")
        scroll, button = list(layout)
        self.assertEqual(scroll.get(ANDROID + "layout_width"), "match_parent")
        self.assertEqual(scroll.get(ANDROID + "layout_height"), "match_parent")
        self.assertIsNone(scroll.get(ANDROID + "layout_weight"))
        self.assertEqual(button.get(ANDROID + "layout_gravity"), "bottom|start")
        content = scroll.find("LinearLayout")
        self.assertEqual(content.get(ANDROID + "paddingBottom"), "70dp")
        self.assertIsNone(content.get(ANDROID + "padding"))
        for edge in ("Start", "Top", "End"):
            self.assertEqual(content.get(ANDROID + "padding" + edge), "20dp")

    def test_generation_uses_built_apk_identity_and_version(self):
        with tempfile.TemporaryDirectory() as directory:
            metadata = Path(directory) / "output-metadata.json"
            output = Path(directory) / "manifest.json"
            metadata.write_text(json.dumps({
                "applicationId": "com.powerclimb",
                "elements": [{"versionName": "0.3.2", "versionCode": 302}],
            }))
            result = subprocess.run(
                [sys.executable, str(ROOT / "ci/generate_manifest.py"),
                 str(metadata), "Danybs/powerclimb", str(output)],
                capture_output=True, text=True,
            )
            self.assertEqual((result.returncode, result.stdout, result.stderr), (0, "", ""))
            self.assertEqual(json.loads(output.read_text()), {
                "label": "PowerClimb",
                "packageName": "com.powerclimb",
                "latestApkUrl": "https://github.com/Danybs/powerclimb/releases/download/v0.3.2/powerclimb.apk",
                "latestVersion": "0.3.2",
                "latestVersionCode": 302,
            })

    def test_release_generates_and_publishes_both_assets(self):
        workflow = (ROOT / ".github/workflows/release.yml").read_text()
        self.assertIn('python3 -m unittest discover -s ci -p "test_*.py"', workflow)
        self.assertIn('python3 ci/generate_manifest.py', workflow)
        self.assertIn('app/build/outputs/apk/release/output-metadata.json', workflow)
        self.assertIn('"$GITHUB_REPOSITORY" manifest.json', workflow)
        publication = workflow.split("- name: Publish GitHub release", 1)[1]
        self.assertRegex(publication, r"files: \|\s+powerclimb\.apk\s+manifest\.json")
        # The existing APK remains available for USB/ADB installation.
        self.assertIn("cp app/build/outputs/apk/release/app-release.apk powerclimb.apk", workflow)
        self.assertIn("apksigner\" verify --verbose powerclimb.apk", workflow)


if __name__ == "__main__":
    unittest.main()
