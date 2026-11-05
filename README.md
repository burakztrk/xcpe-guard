<p align="center">
  <img src="docs/images/banner.png" width="420" alt="XCPE Guard icon">
</p>

<h1 align="center">XCPE Guard</h1>

<p align="center">
  <b>Find and switch off the XCPE botnet on Android TV boxes, from the box itself. No computer, no root.</b>
</p>

<p align="center">
  <a href="https://github.com/burakztrk/xcpe-guard/releases/latest"><img src="https://img.shields.io/github/v/release/burakztrk/xcpe-guard?label=latest%20APK&color=81C995&labelColor=151A22" alt="Latest release"></a>
  <a href="https://github.com/burakztrk/xcpe-guard/actions/workflows/release.yml"><img src="https://img.shields.io/github/actions/workflow/status/burakztrk/xcpe-guard/release.yml?branch=main&label=build&labelColor=151A22" alt="Build"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-81C995?labelColor=151A22" alt="Android 8.0+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/licence-GPL--3.0-8AB4F8?labelColor=151A22" alt="Licence"></a>
</p>

<p align="center">
  <a href="https://github.com/burakztrk/xcpe-guard/releases/latest/download/xcpe-guard.apk"><b>⬇ Download the latest APK</b></a>
  · <a href="README.tr.md">Türkçe</a>
</p>

---

In 2026 Google Threat Intelligence published
[Shutting the door on the XCPE botnet](https://cloud.google.com/blog/topics/threat-intelligence/shutting-the-door-on-the-xcpe-botnet).
The maker of many Android TV set-top boxes had placed a backdoor (`x.cpe` / `com.oemservice`) in their firmware. It runs
with system rights, silently installs apps, runs commands, records the screen, presses keys and rents your connection
out as a proxy. More than **1.3 million** boxes joined the botnet.

Google took the servers down, but the backdoor is still in the firmware of every one of those boxes. XCPE Guard checks a
box against the indicators in that report, switches the backdoor off, and checks again every day.

## Screenshots

| Choose a language | Give the permission once |
|---|---|
| ![Language](docs/images/language.png) | ![Setup](docs/images/setup.png) |
| **Pair with a code, on top of Settings** | **Problems fixed automatically** |
| ![Pairing](docs/images/pairing.png) | ![Report](docs/images/report-fixed.png) |
| **Every item explains itself** | **Share with a friend** |
| ![Details](docs/images/report-expanded.png) | ![About](docs/images/about.png) |

## How it works

Android lets an app talk to the device's own debugging service (adbd) once the owner allows it. The botnet abused that
same service; here it works for you, from inside the box, and nothing leaves your network.

1. **Language**: English or Türkçe.
2. **Developer options**: the app shows where to press seven times.
3. **Debugging permission**, either way:
   - **Wireless debugging (Android 11+)**: the app keeps a small keypad on top of Settings. Open
     "Pair device with pairing code" and type the code into the keypad; it pairs as soon as the sixth digit is in.
     Some boxes never announce their pairing screen on the network, so the keypad cannot open by itself. Once wireless
     debugging is on, the panel counts down and then asks for the port shown after the IP address, then the code.
   - **USB / network debugging**: when the box already listens on port 5555 (many of these boxes do), the TV asks
     "Allow debugging?" once.
4. After the first connection the app grants itself `WRITE_SECURE_SETTINGS`, so for each scan it **switches debugging on,
   scans, and switches it off again**.

Anything that came back is switched off at once (`pm disable-user`), the backdoor's accessibility hook is removed and
Play Protect is turned back on. Nothing is uninstalled, so everything can be undone.

## What it checks

| Area | Check |
|---|---|
| Malware packages | The 10 package names Google lists: `x.cpe`, `com.oemservice`, `com.abc.nexus`, droppers, proxy SDKs |
| Suspicious maker apps | `com.oem.qa`, a factory test app with a silent installer, updated together with the backdoor |
| Persistence | `/oem` APK fingerprints (the backdoor's spare copy), new system apps without a firmware update, new `/system/priv-app` folders, new privapp permission files, `persist.networkstack.*`, malicious native libraries, switched-off apps coming back on |
| Settings | Accessibility services, Play Protect, running processes, debugging state |
| Network | Live TCP connections against the C2 addresses and MQTT ports 3885–3887, whether your DNS blocks the C2 names, Private DNS bypassing a home filter |

## Full list: what it switches off

Nothing is uninstalled. Each package is switched off with `pm disable-user` and force-stopped, so it can be switched
back on at any time.

| Package | What it does |
|---|---|
| `x.cpe`, `com.oemservice` | The backdoor itself: silently installs apps, runs commands, records the screen, presses keys |
| `com.abc.nexus` | ABCNexus loader: downloads the ad-fraud and proxy modules |
| `com.vs.grass` | Listed by Google as part of the same toolkit as ABCNexus |
| `com.android.system.ntp` | Dropper posing as a clock service; reinstalls the botnet as a system app |
| `com.android.sso.auth.handler` | Dropper that installs the AppSalt proxy SDK |
| `com.ota.service` | HiddenView: clicks ads in the background |
| `com.newtv.cboxtv` | Fission dynamic-code backdoor |
| `com.gaganode`, `com.gaganode.sdk` | GaGaNode / IPCola proxy: routes strangers' traffic through your connection |
| `com.oem.qa` | The maker's factory test app (BoxFactoryTest) with a silent APK installer. Not in Google's list, so it is marked suspicious. Never launched |

## Full list: what else it fixes

1. **Accessibility hook**: removes the backdoor's `AccessibilityKeyDetector` entry and leaves your other services alone.
2. **Play Protect**: turns it back on if it was switched off (`package_verifier_user_consent=-1`).
3. **Private DNS "Automatic"**: sets it to `off`, because it can bypass a home DNS filter such as AdGuard.
4. **Packages that come back or appear**: switches off again anything that was off in the first scan and got re-enabled,
   and any new system app that shows up without a firmware update (the sign of a dropper).

## Full list: what it only reports

- **Processes**: `oemservice`, `x.cpe`, `abc.nexus`, `vs.grass`, `system.ntp`, `sso.auth.handler`, `gaganode`,
  `lw_android_agent`
- **Native libraries**: `libseccomp_guard.so`, `libexploit.so`, `libnative_keys.so`, `libfleetshare.so`, `libjaguar.so`,
  `libtraceroute.so`, `lw_android_agent`
- **Properties**: `persist.networkstack.uninstall`, `persist.networkstack.skip_system_install`,
  `persist.networkstack.install_time`
- **`/oem` fingerprints**: the spare copy `xcpe_signed.apk` and Google's seven SHA-256 values. It cannot be deleted
  without root and is harmless while its package stays off
- **System folders**: a new `/system/priv-app` folder or a new `privapp-permissions` file raises an alarm
- **Network**: live TCP connections to the C2 addresses `219.134.163.210`, `219.134.163.211`, `23.94.49.57`,
  `38.145.197.135` and to MQTT ports 3885–3887
- **DNS**: whether these seven C2 names are blocked: `mqtt.szbboys.com`, `ott.szbboys.com`, `mcstb.anyevonline.com`,
  `intermedia-arts.tv`, `yokatvonline.com`, `linkfog.cn`, `hiddeninplainsight.top`
- **New user apps**: listed once, for information
- **Firmware fingerprint**: if it changes, the app stores a new reference instead of raising an alarm

The app itself switches debugging on for each scan and off again, scans once a day (and 2 minutes after boot), and its
only network traffic is the DNS lookup of the seven names above.

## Install

1. Download [`xcpe-guard.apk`](https://github.com/burakztrk/xcpe-guard/releases/latest/download/xcpe-guard.apk), or scan
   the QR code on the app's About screen with a phone.
2. Sideload it, for example with the Downloader app, or from a computer:
   ```bash
   adb install xcpe-guard.apk
   ```
3. Open **XCPE Guard** from the apps row and follow the three steps.

Each release also has a `.sha256` file to check the download.

## Building

You need JDK 17 and the Android SDK (platform 34).

```bash
git clone https://github.com/burakztrk/xcpe-guard.git
cd xcpe-guard
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
```

Without a keystore the release build is signed with your local debug key. To sign with your own key:

```bash
export KEYSTORE_FILE=/path/to/release.jks
export KEYSTORE_PASSWORD=…  KEY_ALIAS=…  KEY_PASSWORD=…
export VERSION_CODE=42         # optional; becomes versionCode 42 and versionName 1.0.42
./gradlew assembleRelease
```

### Releases

Every push to `main` runs [`.github/workflows/release.yml`](.github/workflows/release.yml): it builds the release APK,
signs it, and publishes it as release `v1.0.<run number>`. The download link and the version badge above always point
to the latest one. The workflow reads four repository secrets (Settings → Secrets and variables → Actions):
`KEYSTORE_BASE64` (the keystore, base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Without them it only
uploads an unsigned build artifact, because updates have to be signed with the same key every time.

## Block it on your network

XCPE Guard works on the box. To keep every device on your network away from the botnet's servers, add the
[AdGuard blocklist](docs/adguard-blocklist.md) (154 rules from the same Google report) to AdGuard Home, or to any
DNS filter that understands AdGuard syntax. The app's "C2 names (DNS)" row then turns green.

## No app? Use an AI agent

If you would rather not install anything on the box, an AI coding agent with a shell can do the same checks and fixes
over adb from a computer on the same network. The step-by-step procedure, with the safety rules, is in
[skills/xcpe-guard/SKILL.md](skills/xcpe-guard/SKILL.md).

**Claude Code** (as a skill):

```bash
git clone --depth 1 https://github.com/burakztrk/xcpe-guard.git /tmp/xcpe-guard
mkdir -p ~/.claude/skills && cp -r /tmp/xcpe-guard/skills/xcpe-guard ~/.claude/skills/
```

Then ask: *"Check my Android TV box at 192.168.1.50 for the XCPE botnet and clean it."*

**Any other agent** (Codex, Gemini CLI, Cursor…): clone the repo and open it in the agent; [AGENTS.md](AGENTS.md) points it
to the same procedure. Or paste: *"Read https://raw.githubusercontent.com/burakztrk/xcpe-guard/main/skills/xcpe-guard/SKILL.md
and follow it to check my Android TV box for the XCPE botnet."*

You need `adb` and Developer options with USB or Wireless debugging on the box. The agent shows you what it found and
waits for your yes before it disables anything. It disables, never uninstalls, so everything can be undone.

## Limits

- **No root.** The backdoor's spare copy on the read-only `/oem` partition cannot be deleted. It cannot run either while
  its package stays off, which is what the app makes sure of.
- **A factory reset** brings the box back to the factory state: run the app again afterwards.
- **IP addresses** cannot be blocked from an app without root. Block the C2 names on your router or DNS filter
  (AdGuard Home, Pi-hole); the app shows whether they still resolve.
- Android forgets an unused debugging key after **7 days**. The daily scan keeps it fresh; with scheduled scans off,
  or a box unplugged for longer, open the app and connect again.

## Privacy

No accounts, no analytics, no servers of its own. The only network traffic is a DNS lookup of seven C2 names, to see
whether your network blocks them.

## Credits

- Indicators of compromise: Google Threat Intelligence Group.
- ADB pairing and TLS: [libadb-android](https://github.com/MuntashirAkon/libadb-android) by Muntashir Al-Islam;
  see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for every bundled library.
- Started from a clean-up done with [Clean Your TV](https://github.com/Gathin23/cleanyourtv).

## Licence

[GPL-3.0](LICENSE). Free and open source: use it, study it, change it and share it, for any purpose. If you
distribute a modified version, it has to stay open source under the same licence.
