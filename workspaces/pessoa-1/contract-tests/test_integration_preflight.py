import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "check-integration.py"
spec = importlib.util.spec_from_file_location("preflight", SCRIPT)
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)


class InventoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def put(self, path, content="example"):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)

    def run_script(self):
        return subprocess.run([sys.executable, str(SCRIPT), "--root", str(self.root)],
                              capture_output=True, text=True, check=False)

    def test_all_missing(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 1)
        self.assertEqual(result.stdout.count("MISSING"), 12)
        self.assertIn("Inventory only", result.stdout)

    def test_empty_placeholders_are_missing(self):
        for owner, modules in preflight.MODULES.items():
            base = f"workspaces/{owner}"
            self.put(f"{base}/frontend/src/README.md")
            for module in modules:
                self.put(f"{base}/backend/src/main/java/logistics/{module}/.gitkeep")
                self.put(f"{base}/backend/src/main/resources/db/migration/{module}/README.md")
        self.assertTrue(all(not present for _, present in preflight.inventory(self.root)))
        self.assertEqual(self.run_script().returncode, 1)

    def test_source_present_is_inventory_only(self):
        for owner, modules in preflight.MODULES.items():
            base = f"workspaces/{owner}"
            self.put(f"{base}/frontend/src/App.vue")
            for module in modules:
                self.put(f"{base}/backend/src/main/java/logistics/{module}/Service.java")
                self.put(f"{base}/backend/src/main/resources/db/migration/{module}/V1__init.sql")
        result = self.run_script()
        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stdout.count("PRESENT"), 12)
        self.assertIn("real adapters remain required", result.stdout)


if __name__ == "__main__":
    unittest.main()
