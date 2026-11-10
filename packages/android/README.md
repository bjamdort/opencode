# OpenCode for Android

A WebView wrapper around the real OpenCode web app. The APK includes the complete built UI: HTML, JavaScript, styles, fonts, workers, and WebAssembly assets. It opens on a fresh install in airplane mode, without a server address, pairing link, Tailscale connection, or previous visit.

The app does **not** run an OpenCode server on Android. A server on your laptop or desktop is needed only when you connect to it to load projects, use sessions, or run tools and models. Install Tailscale separately for those connections.

Requires Android 8.0 or newer and an up-to-date Android System WebView.

## Open without setup

Install the APK and open **OpenCode**. The bundled home screen and settings work without a configured server. There is no mandatory native server setup dialog or full-screen connection gate. Add servers later using the web app's server controls.

Android serves the packaged assets through `WebViewAssetLoader` at a virtual `http://localhost:49373` origin. Nothing listens on that port: there is no local HTTP server or OpenCode background process. This loopback origin also matches OpenCode's existing remote API CORS policy, without requiring a server-side CORS change.

## Connect a computer when you need it

Connect Tailscale on your phone and computer. On the computer, start OpenCode and check its local address:

```sh
opencode service start
opencode service status
```

The usual port is `49374` on macOS/Linux and `28894` on Windows; use the actual port printed above.

For private HTTPS, keep OpenCode listening on localhost and use [Tailscale Serve](https://tailscale.com/docs/reference/tailscale-cli/serve):

```sh
tailscale serve --bg http://127.0.0.1:49374
tailscale serve status
opencode pair --url https://laptop.your-tailnet.ts.net
```

Use your own hostname from Serve. Serve requires MagicDNS and HTTPS certificates enabled for your tailnet. If you already use Serve, inspect its configuration before changing it. Use Serve, not Funnel, to keep access private to your tailnet.

Paste the one-time pairing link into the web app's **Add server** form. Do not open it in your browser first: it works once, and the Android app needs its own authentication. Repeat for your desktop. The built-in web UI manages server names, switching, authentication, and reconnection.

Alternatively, bind OpenCode to its Tailscale IP and connect to its HTTP address. HTTP traffic is encrypted by Tailscale only when it actually travels between Tailscale peers. Do not expose OpenCode through your router, and keep your tailnet access rules restrictive. TLS errors are never bypassed.

## Offline behavior

- All UI assets are installed with the APK, including lazy-loaded screens, so opening the app and settings does not need the network.
- Settings, server configuration, and browser-managed local data persist on the device.
- This is not an offline OpenCode backend or a guaranteed archive of remote sessions. Remote projects, history, models, and tool execution require the corresponding computer.
- Android builds disable the browser service worker: APK assets already provide offline UI availability, without an old worker retaining assets from a previous APK.
- The UI updates when you install a newer APK, not when a remote server updates. Keep the APK and your computers on compatible OpenCode versions.

Native code hosts the assets in WebView and handles system/keyboard insets, Android Back, external links, attachment selection, and optional push delivery. It has no native JavaScript bridge, VPN, camera/microphone permissions, or background OpenCode server. App data is excluded from backup and device transfer.

## Optional Android push notifications

Personal Firebase builds add a native notification service, but do not add an OpenCode server or require notification setup to open the app. The bundled app's **Push notifications** action opens the Android opt-in dialog; Google registration does not start until you enable it. Analytics collection is disabled.

1. Add your Android Firebase app configuration as `app/google-services.json` before building. The file is ignored by Git. **Never use a Firebase service-account private key here**: that key belongs only on the NAS.
2. Install the Firebase-enabled APK, open **Push notifications** on the offline home screen or under **Settings → Notifications**, and enable notifications.
3. Tap **Share registration**, choose LocalSend, and send `opencode-phone-registration.json` to your computer. The file contains a device token, not a server password; keep it private.
4. Import that file into the NAS relay using `notifications/register_device.py`, described in its README. No registration endpoint or new public port is needed.

Firebase builds use high-priority data messages. The app checks its own opt-in and Android permission before posting a notification, including when backgrounded. **Disable** stops local delivery and requests deletion of the FCM token. Remove stale tokens from the NAS registry as needed. Android force-stop prevents delivery until the app is opened again, and device battery policies can delay messages.

Tapping a notification opens the matching server/session in the bundled web app. Add that server to the app first, using the same URL as the relay's `OPENCODE_PUBLIC_URL`; the notification never adds a server or credentials automatically. Repeat registration after reinstalling or if your phone's FCM token changes. The web UI's notification switches do not currently filter NAS pushes.

Generic/CI builds without `google-services.json` remain usable offline, but show that push is unconfigured. Keep using the local build/signing key for updates to the personal APK.

## Build on this machine

Install Bun matching the repository's `package.json`, JDK 17, and Android SDK platform/build-tools 36. Set `JAVA_HOME` and `ANDROID_HOME`, then install the repository dependencies from the repository root:

```sh
bun install --frozen-lockfile
```

From `packages/android`, build and verify the APK:

```sh
./gradlew testDebugUnitTest lintDebug verifyBundledAssets
```

Gradle builds the actual `packages/app` web app in Android mode with `VITE_OPENCODE_SERVER_MODE=none`, synchronizes its assets into the APK, and verifies every packaged asset byte-for-byte. This mode leaves normal web builds unchanged. Generated web assets are not committed.

The signed development APK is `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone to install, or use:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Local builds use your Android debug key. Keep the same signing key for updates: a differently signed APK requires uninstalling, which removes app data. CI artifacts use runner-generated debug keys and are for testing, not a stable update channel. Do not commit signing keys.
