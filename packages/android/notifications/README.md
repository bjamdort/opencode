# NAS notification relay

A sidecar for an existing OpenCode V2 server. It subscribes to `/api/event` and forwards completion, failure, permission, and question notices through Firebase Cloud Messaging. It does not change, restart, or run an OpenCode server, and it does not approve requests or run models.

The Android wrapper supports Firebase-enabled builds and explicitly shared phone-registration files. Deploying this container alone does not enable notifications: install that APK, enable push, and import the phone's registration below.

## NAS layout

- Compose stack: `/home/bjamdort/compose/opencode/compose.yaml`, service `notification-relay`.
- Build context: `/home/bjamdort/compose/opencode/notifications` (these source files).
- Persistent configuration: `/home/bjamdort/opencode/notifications`, owned by UID/GID 1000, mode 0700.
- Existing server password: `/home/bjamdort/opencode/server-password`, mounted read-only.

The relay joins the existing Compose network and connects to `http://devbox:4096`. It runs as UID/GID 1000 with a read-only root filesystem, dropped capabilities, a 192 MiB limit, and no published ports or Docker socket. Configuration is mounted read-only; status is written only to its temporary filesystem.

`compose.example.yaml` contains the service to merge into the existing stack. Set `OPENCODE_PUBLIC_URL` to the same URL you add in the Android app. Do not replace the existing Compose file with the example.

Start only the new service, without recreating OpenCode:

```sh
cd /home/bjamdort/compose/opencode
docker compose build notification-relay
docker compose up -d --no-deps --wait notification-relay
docker compose exec -T notification-relay python relay.py --status
```

## Finish Firebase configuration

Use the same Firebase project for the Android app (`dev.bjamdort.opencode.remote`) and sender. Enable the FCM HTTP v1 API and use a service account permitted to send messages (the Firebase Cloud Messaging API Admin role); do not grant broad project ownership.

Store its private key JSON **on the NAS only**, as:

```text
/home/bjamdort/opencode/notifications/firebase-service-account.json
```

Keep that file mode 0600. Do not put it in the APK, repository, Compose environment, or chat. Outbound OAuth access uses only the `firebase.messaging` scope; the NAS needs outbound HTTPS to Google, not inbound internet access.

In the Firebase-enabled APK, open **Push notifications → Enable**, grant the Android permission, then use **Share registration → LocalSend** to send `opencode-phone-registration.json` to your computer. Copy that private registration file to the NAS and import it:

```sh
python3 /home/bjamdort/compose/opencode/notifications/register_device.py \
  /path/to/opencode-phone-registration.json \
  --config /home/bjamdort/opencode/notifications
```

The importer checks the Firebase project, preserves existing devices, avoids duplicate tokens, and atomically writes `devices.json` with mode 0600. It does not print the token or require a public registration endpoint. Keep registration files private, and share a new one if the phone's token changes.

For manual administration, the registry has this shape:

```json
{"tokens": ["YOUR_ANDROID_FCM_REGISTRATION_TOKEN"]}
```

Until a phone is registered, leave `{"tokens": []}`. No Google requests are made by the running relay without both a credentials file and at least one configured device. Config files are reread on incoming events; only the relay needs restarting if configuration has changed outside its mounted directory. A changed server password file may require recreating the relay's read-only file mount.

`--status` distinguishes `waiting-for-firebase`, `waiting-for-device`, `invalid-device-configuration`, and `configured`. "Configured" means the files are present, not that a message has been delivered. The `sent` counter means FCM accepted a message, not that the handset displayed it. Container health means the OpenCode stream is connected; it does not imply push delivery is configured.

## Privacy and delivery limits

- Notifications use generic titles/bodies, a server URL, session ID, event ID, and event kind. They do not send session titles, prompts, model output, commands, errors, or permission contents to Google.
- Subagent completions/errors are suppressed. Requests from subagents target their root session.
- Notices cover all eligible activity on the NAS, not only sessions open on the phone. There are no per-phone/session preferences yet.
- OpenCode's event feed is volatile. Events missed during downtime/reconnection are not replayed, and no backlog is queued before Firebase is configured. FCM failure is reported without logging tokens or provider responses; failed sends are not retried or durably queued. This initial sender is best-effort, not an exactly-once notification system.
- Invalid/expired FCM tokens are not removed automatically; update `devices.json` when registration changes.
- Messages are data-only and high priority. The native service posts the visible notification after checking the phone's opt-in and notification permission. This prevents Firebase's automatic background display from bypassing the app's Disable action. Force-stopping Android prevents receipt until the app is reopened.

Run the parser/privacy/configuration tests without connecting to a server or Google:

```sh
python3 -m unittest -v
```
