# OpenCode Remote for Android

A small native Android WebView wrapper for OpenCode's web UI, with saved **Laptop** and **Desktop** servers. It loads the UI from the selected computer rather than bundling a second copy, so updating OpenCode on that computer also updates the UI in the app.

The Android app does not run the OpenCode server or include a VPN. Install and connect Tailscale separately on your phone and both computers. Requires Android 8.0 or newer and an up-to-date Android System WebView.

## Connect your computers

On each computer, update OpenCode, start its background service, and print its address:

```sh
opencode service start
opencode service status
```

The default port is `49374` on macOS/Linux and `28894` on Windows. Use the actual port reported by `service status` in the examples below.

### Recommended: private HTTPS with Tailscale Serve

Keep OpenCode listening on localhost. On each computer, proxy that local port through [Tailscale Serve](https://tailscale.com/docs/reference/tailscale-cli/serve):

```sh
tailscale serve --bg http://127.0.0.1:49374
tailscale serve status
```

Serve requires MagicDNS and HTTPS certificates enabled in your tailnet. Follow Tailscale's setup prompt if needed. If you already use Serve on this computer, inspect its configuration before changing it. Use **Serve**, not **Funnel**: Serve stays private to your tailnet, subject to your tailnet access rules.

Generate a one-time OpenCode pairing link using the HTTPS address printed by Serve:

```sh
opencode pair --url https://laptop.your-tailnet.ts.net
```

In the Android app, open **Servers → Edit servers**, paste the entire link into **Laptop**, and tap **Save**. Do not open it in your browser first: the link works once, and the app needs its own sign-in cookie. Repeat for **Desktop**, then select **Desktop** from the server menu to finish pairing it.

Pairing links expire after a few minutes. Only the server origin is saved; unused pairing links are temporary and are lost when the activity is recreated or closed. Pair one computer at a time, immediately after generating its link. Authentication cookies persist in the app's private WebView storage. Signing in through your phone's browser does not sign in this app.

### Alternative: HTTP over Tailscale

If you do not use Serve, bind OpenCode to the computer's Tailscale IP, not every network interface:

```sh
tailscale ip -4
opencode service set hostname 100.101.102.103
opencode service start
opencode pair --url http://100.101.102.103:49374
```

Replace the example IP and port with your own. Changing a service setting stops the running service; do this when it is safe to interrupt it. Restrict access with Tailscale's access rules and your host firewall. Do not expose the port through your router.

HTTP is permitted for this workflow because Tailscale encrypts transport between peers. That protection does **not** apply to arbitrary LAN or public HTTP addresses. HTTPS through Serve is preferred, including for browser features that require a secure context. TLS certificate errors are never bypassed.

## Use the app

- Tap **Laptop** or **Desktop** in the top bar to switch servers or edit addresses.
- Tap **Reload** after reconnecting Tailscale or restarting a server.
- Android Back navigates within the selected server. Switching servers clears navigation history, but retains each origin's web storage and cookies.
- Web file inputs open Android's document picker for attachments. External HTTP(S) links open in your browser.
- If pairing expires, paste a fresh pairing link into that server's field. Alternatively, use the web UI's sign-in form; HTTP Basic authentication prompts are also supported without saving a password in app preferences.

There is no native JavaScript bridge, background server, notification integration, camera/microphone permission, or bundled web UI. The web UI controls its own features. Keep the app foregrounded for a reliable live connection; Android may suspend it in the background. Clearing app data signs you out and removes saved addresses. App storage is excluded from cloud backup and device transfer.

## Build an APK

Install JDK 17 and Android SDK platform 36/build-tools 36.0.0. Set `JAVA_HOME` and `ANDROID_HOME` to your installations, then run from this directory:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The Gradle wrapper downloads Gradle with checksum verification. No Bun install or OpenCode web build is needed for this package.

Install the signed development APK:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy that APK to your phone and allow APK installation from your file manager. `assembleDebug` uses your local Android debug key. Keep using the same key for updates; a build signed by another key cannot replace it without uninstalling, which clears app data. Do not commit signing keys. Production distribution needs a separately managed release signing key.

The **Android wrapper** GitHub Actions workflow builds and uploads a development APK and unit-test reports. Builds on fresh CI runners use different debug keys, so those artifacts are for testing, not a stable update channel.

## Keep the fork current

This wrapper starts from upstream `dev` at `8de2338c48` (OpenCode 2.2.7). Keep the Android changes on `android-wrapper`; leave the upstream web and server packages unchanged. Merge newer upstream `dev` commits into that branch as needed. Updating the APK is only necessary for native wrapper changes; each computer supplies its currently installed OpenCode web UI.
