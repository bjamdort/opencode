import json
from pathlib import Path
import stat
import tempfile
import unittest

from register_device import register


class RegistrationTests(unittest.TestCase):
    def test_import_preserves_devices_is_idempotent_and_keeps_the_registry_private(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory)
            (config / "firebase-service-account.json").write_text('{"project_id":"project-a"}')
            (config / "devices.json").write_text('{"tokens":["existing-token"],"note":"preserve"}')
            registration = {"format": "opencode-fcm-registration-v1", "project_id": "project-a", "token": "phone-token"}
            self.assertTrue(register(config, registration))
            self.assertFalse(register(config, registration))
            path = config / "devices.json"
            self.assertEqual(json.loads(path.read_text()), {"tokens": ["existing-token", "phone-token"], "note": "preserve"})
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)

    def test_foreign_projects_and_invalid_inputs_leave_existing_devices_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory)
            (config / "firebase-service-account.json").write_text('{"project_id":"project-a"}')
            path = config / "devices.json"
            path.write_text('{"tokens":["existing-token"]}')
            original = path.read_bytes()
            for registration in [
                None,
                {"format": "other"},
                {"format": "opencode-fcm-registration-v1", "project_id": "project-b", "token": "phone-token"},
                {"format": "opencode-fcm-registration-v1", "project_id": "project-a", "token": ""},
            ]:
                with self.subTest(registration=registration):
                    with self.assertRaises(ValueError):
                        register(config, registration)
                    self.assertEqual(path.read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
