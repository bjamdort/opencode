"""Import a phone's explicitly shared registration file; never print its token."""

import argparse
import json
import os
from pathlib import Path
import tempfile


def register(config, registration):
    if not isinstance(registration, dict) or registration.get("format") != "opencode-fcm-registration-v1":
        raise ValueError("Not an OpenCode phone registration file")
    project = json.loads((config / "firebase-service-account.json").read_text())["project_id"]
    if registration.get("project_id") != project:
        raise ValueError("The phone and relay use different Firebase projects")
    token = registration.get("token")
    if not isinstance(token, str) or not token.strip():
        raise ValueError("Registration has no device token")
    path = config / "devices.json"
    devices = json.loads(path.read_text()) if path.exists() else {"tokens": []}
    tokens = devices.get("tokens") if isinstance(devices, dict) else None
    if not isinstance(tokens, list) or any(not isinstance(item, str) or not item.strip() for item in tokens):
        raise ValueError("Existing device registry is invalid; no changes made")
    if token in tokens:
        return False
    devices["tokens"] = [*tokens, token]
    with tempfile.NamedTemporaryFile(mode="w", dir=config, prefix=".devices-", delete=False) as temporary:
        json.dump(devices, temporary)
        temporary.write("\n")
        os.fchmod(temporary.fileno(), 0o600)
    Path(temporary.name).replace(path)
    return True


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("registration", type=Path)
    parser.add_argument("--config", type=Path, default=Path("/config"))
    args = parser.parse_args()
    try:
        added = register(args.config, json.loads(args.registration.read_text()))
    except (OSError, ValueError, KeyError) as error:
        # Do not print JSON parser errors, paths, or token-bearing source text.
        raise SystemExit("Registration failed (" + type(error).__name__ + "); check the file and Firebase project.")
    print("Device registered." if added else "Device was already registered.")
