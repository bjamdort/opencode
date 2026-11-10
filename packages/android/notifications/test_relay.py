import json
from pathlib import Path
import tempfile
import unittest

from relay import delivery_configuration, fcm_message, notice_from_event, parse_events


class RelayTests(unittest.TestCase):
    def test_sse_comments_multiline_frames_and_incomplete_frames(self):
        lines = [
            ": heartbeat", "", "event: message", "id: transport-id",
            'data: {"id":"event-1","type":"permission.asked",',
            'data: "data":{"sessionID":"session-1"}}', "",
            'data: {"type":"server.connected","data":{}}', "",
            'data: {"type":"incomplete"',
        ]
        events = list(parse_events(lines))
        self.assertEqual([event["type"] for event in events], ["permission.asked", "server.connected"])
        self.assertEqual(notice_from_event(events[0]).session_id, "session-1")
        self.assertIsNone(notice_from_event(events[1]))

    def test_only_user_attention_events_produce_notices_without_private_content(self):
        for event_type, kind in [
            ("session.execution.succeeded", "complete"),
            ("session.execution.failed", "error"),
            ("permission.asked", "permission"),
            ("session.text.delta", None),
            ("session.idle", None),
        ]:
            with self.subTest(event_type=event_type):
                event = {"id": "event-1", "type": event_type, "data": {
                    "sessionID": "session-1", "title": "PRIVATE TITLE", "error": "PRIVATE ERROR", "input": "PRIVATE PROMPT"
                }}
                notice = notice_from_event(event)
                if kind is None:
                    self.assertIsNone(notice)
                    continue
                message = fcm_message(notice, "root-session", "NAS", "https://opencode.example.com")
                self.assertEqual(message["data"]["kind"], kind)
                self.assertEqual(message["data"]["session_id"], "root-session")
                self.assertNotIn("PRIVATE", json.dumps(message))
                self.assertNotIn("token", message)
                self.assertNotIn("notification", message)
                self.assertNotIn("notification", message["android"])

    def test_form_requests_use_the_nested_session_and_only_known_question_kinds(self):
        for kind in ["question", "mcp-elicitation", "websearch.provider", "other", None]:
            with self.subTest(kind=kind):
                event = {"id": "event-1", "type": "form.created", "data": {"form": {
                    "sessionID": "child-session", "metadata": {"kind": kind}
                }}}
                notice = notice_from_event(event)
                if kind in ("other", None):
                    self.assertIsNone(notice)
                    continue
                self.assertEqual(notice.kind, "question")
                self.assertEqual(notice.session_id, "child-session")

    def test_delivery_stays_disabled_until_both_credentials_and_device_exist(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory)
            self.assertEqual(delivery_configuration(config), ([], "waiting-for-firebase"))
            (config / "firebase-service-account.json").write_text("{}")
            (config / "devices.json").write_text('{"tokens": []}')
            self.assertEqual(delivery_configuration(config), ([], "waiting-for-device"))
            (config / "devices.json").write_text('{"tokens": ["device-token"]}')
            self.assertEqual(delivery_configuration(config), (["device-token"], "configured"))
            (config / "devices.json").write_text('{"tokens": [null]}')
            self.assertEqual(delivery_configuration(config), ([], "invalid-device-configuration"))


if __name__ == "__main__":
    unittest.main()
