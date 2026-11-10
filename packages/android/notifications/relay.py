"""Read one OpenCode server's live events and send generic FCM notifications."""

from dataclasses import dataclass
import json
import logging
import os
from pathlib import Path
import sys
import threading
import time
from urllib.parse import quote


EVENT_TYPES = (
    "session.execution.succeeded",
    "session.execution.failed",
    "permission.asked",
    "form.created",
)
STATUS = Path("/tmp/opencode-notifications-status.json")


@dataclass(frozen=True)
class Notice:
    event_id: str
    session_id: str
    kind: str


def main():
    import requests

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    config = Path(os.environ.get("NOTIFICATION_CONFIG", "/config"))
    source = os.environ.get("OPENCODE_URL", "http://devbox:4096").rstrip("/")
    public_url = os.environ["OPENCODE_PUBLIC_URL"].rstrip("/")
    label = os.environ.get("OPENCODE_LABEL", "NAS")
    password_file = Path(os.environ.get("OPENCODE_PASSWORD_FILE", "/run/secrets/opencode-password"))
    state = {"source_connected": False, "observed": 0, "sent": 0, "failed": 0}
    write_status(state, delivery=delivery_configuration(config)[1])
    logging.info("Starting OpenCode notification relay; no public listener is opened")
    logging.info("Delivery state: %s", state["delivery"])

    with requests.Session() as client:
        # The source is the existing Docker-internal server, not a public proxy.
        client.trust_env = False
        while True:
            try:
                client.auth = ("opencode", password_file.read_text().strip())
                with client.get(
                    source + "/api/event",
                    params=[("types", event_type) for event_type in EVENT_TYPES],
                    headers={"Accept": "text/event-stream"},
                    stream=True,
                    timeout=(10, 90),
                    allow_redirects=False,
                ) as response:
                    response.raise_for_status()
                    if response.status_code != 200:
                        raise ValueError("Unexpected source response")
                    response.encoding = "utf-8"
                    write_status(state, source_connected=True)
                    logging.info("Connected to OpenCode event stream")
                    for event in parse_events(heartbeat_lines(response, state, config)):
                        notice = notice_from_event(event)
                        if notice is None:
                            continue
                        state["observed"] += 1
                        tokens, delivery = delivery_configuration(config)
                        write_status(state, delivery=delivery)
                        if delivery != "configured":
                            continue
                        try:
                            session_id = notification_session(client, source, notice)
                            if session_id is not None:
                                deliver(config, tokens, notice, session_id, label, public_url, state)
                        except Exception as error:
                            # Exception text can contain provider responses, tokens, or session data.
                            state["failed"] += 1
                            write_status(state, last_error=type(error).__name__)
                            logging.error("Notification delivery failed (%s); contents omitted", type(error).__name__)
                raise ConnectionError("Source stream closed")
            except (OSError, ValueError, requests.RequestException) as error:
                write_status(state, source_connected=False, last_error=type(error).__name__)
                logging.warning("Event stream unavailable (%s); retrying in 10 seconds", type(error).__name__)
                threading.Event().wait(10)


def parse_events(lines):
    data = []
    for line in lines:
        if line.startswith("data:"):
            data.append(line[5:].removeprefix(" "))
        if line or not data:
            continue
        event = json.loads("\n".join(data))
        data = []
        if not isinstance(event, dict) or not isinstance(event.get("type"), str) or not isinstance(event.get("data"), dict):
            raise ValueError("Invalid event envelope")
        yield event


def notice_from_event(event):
    kind = {
        "session.execution.succeeded": "complete",
        "session.execution.failed": "error",
        "permission.asked": "permission",
    }.get(event["type"])
    payload = event["data"]
    if event["type"] == "form.created":
        form = payload.get("form")
        if not isinstance(form, dict):
            raise ValueError("Invalid form")
        metadata = form.get("metadata") or {}
        if not isinstance(metadata, dict):
            raise ValueError("Invalid form metadata")
        if metadata.get("kind") not in ("question", "mcp-elicitation", "websearch.provider"):
            return None
        kind = "question"
        payload = form
    if kind is None:
        return None
    session_id = payload.get("sessionID")
    event_id = event.get("id")
    if not isinstance(session_id, str) or not session_id or not isinstance(event_id, str) or not event_id:
        raise ValueError("Invalid notification event identifiers")
    return Notice(event_id, session_id, kind)


def notification_session(client, source, notice):
    session_id = notice.session_id
    while True:
        with client.get(source + "/api/session/" + quote(session_id, safe=""), timeout=(10, 15), allow_redirects=False) as response:
            response.raise_for_status()
            session = response.json()
        if not isinstance(session, dict):
            raise ValueError("Invalid session response")
        parent = session.get("parentID")
        if parent is None:
            return session_id
        # Subagent completions are not completion of the user's session. Requests target their root.
        if notice.kind in ("complete", "error"):
            return None
        if not isinstance(parent, str) or not parent:
            raise ValueError("Invalid parent session")
        session_id = parent


def fcm_message(notice, session_id, label, public_url):
    title, body = {
        "complete": ("OpenCode finished", "A session finished and is ready to review."),
        "error": ("OpenCode needs attention", "A session could not finish."),
        "permission": ("OpenCode needs permission", "A session is waiting for your approval."),
        "question": ("OpenCode has a question", "A session is waiting for your answer."),
    }[notice.kind]
    return {
        "data": {
            # Data-only delivery lets Android honor the app's opt-out in every app state.
            "notification_title": f"{label}: {title}",
            "notification_body": body,
            "server_url": public_url,
            "session_id": session_id,
            "event_id": notice.event_id,
            "kind": notice.kind,
        },
        "android": {
            "priority": "HIGH",
            "ttl": "300s",
        },
    }


def delivery_configuration(config):
    if not (config / "firebase-service-account.json").is_file():
        return [], "waiting-for-firebase"
    try:
        data = json.loads((config / "devices.json").read_text())
        tokens = data.get("tokens") if isinstance(data, dict) else None
        if not isinstance(tokens, list) or any(not isinstance(token, str) or not token.strip() for token in tokens):
            return [], "invalid-device-configuration"
        return tokens, "configured" if tokens else "waiting-for-device"
    except (OSError, ValueError):
        return [], "invalid-device-configuration"


def deliver(config, tokens, notice, session_id, label, public_url, state):
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account

    credentials = service_account.Credentials.from_service_account_file(
        str(config / "firebase-service-account.json"),
        scopes=["https://www.googleapis.com/auth/firebase.messaging"],
    )
    message = fcm_message(notice, session_id, label, public_url)
    with AuthorizedSession(credentials, refresh_timeout=15) as sender:
        for index, token in enumerate(tokens):
            with sender.post(
                f"https://fcm.googleapis.com/v1/projects/{credentials.project_id}/messages:send",
                json={"message": {**message, "token": token}},
                timeout=(10, 20),
                max_allowed_time=45,
            ) as response:
                if response.status_code == 200:
                    state["sent"] += 1
                    logging.info("FCM accepted %s notification for device %s", notice.kind, index + 1)
                    continue
                state["failed"] += 1
                state["last_error"] = "FCM HTTP " + str(response.status_code)
                logging.error("FCM rejected a notification (HTTP %s); response omitted", response.status_code)
    write_status(state)


def heartbeat_lines(response, state, config):
    for line in response.iter_lines(chunk_size=1, decode_unicode=True):
        write_status(state, delivery=delivery_configuration(config)[1])
        yield line


def write_status(state, **updates):
    state.update(updates, updated_at=time.time())
    temporary = STATUS.with_suffix(".tmp")
    temporary.write_text(json.dumps(state))
    temporary.replace(STATUS)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--healthcheck":
        try:
            status = json.loads(STATUS.read_text())
            sys.exit(0 if status["source_connected"] and time.time() - status["updated_at"] < 180 else 1)
        except (OSError, ValueError, KeyError):
            sys.exit(1)
    if len(sys.argv) > 1 and sys.argv[1] == "--status":
        print(STATUS.read_text())
        sys.exit(0)
    main()
