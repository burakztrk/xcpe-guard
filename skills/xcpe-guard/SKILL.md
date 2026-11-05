---
name: xcpe-guard
description: Check an Android TV box for the XCPE botnet that Google Threat Intelligence exposed in 2026 (the firmware backdoor com.oemservice / x.cpe found on Mecool and other Videostrong-made boxes) and switch it off over adb, with no app and no root. Use when the user mentions XCPE, com.oemservice, a Mecool KM2 or similar TV box, Google's XCPE report, or asks to check or clean an Android TV box for malware.
---

# XCPE botnet: check and clean an Android TV box over adb

Source of every indicator below: Google Threat Intelligence Group,
[Shutting the door on the XCPE botnet](https://cloud.google.com/blog/topics/threat-intelligence/shutting-the-door-on-the-xcpe-botnet).
This guide does by hand, over adb, what the XCPE Guard app does on the box. Tested on a Mecool KM2 (Android TV 14,
build `UKG3.240613.001.C1.4.1_20251218`).

The person may write in any language: answer in theirs.

## What the problem is

The box's maker preinstalled a backdoor in the firmware. It runs with system rights as `com.oemservice` (classes under
`x.cpe.*`, system app `/system/priv-app/Xcpe`). It installs apps silently, runs commands, records the screen, presses keys
through an accessibility service, switches Play Protect off and rents the connection out as a proxy. It keeps a spare
copy in the read-only `/oem` partition and its boot receiver reinstalls from there when the package is removed.

That is why the fix is to **disable** the package, never to uninstall it. A disabled package cannot run, start at boot or
reach the network, and its boot receiver cannot reinstall anything.

## Rules that are not negotiable

- Use `pm disable-user --user 0 <package>`, one package per shell call. Never `pm uninstall`, never root, never flash.
- Back up the package and settings lists before the first change.
- Never start a factory or test package (`com.oem.qa`, anything named factory, fac, production). On some boards that flips
  a factory-mode flag the shell cannot undo. Read it with `dumpsys package <name>` instead.
- Never disable Android's core packages (`android`, `com.android.systemui`, `com.android.shell`, `com.android.settings`,
  `com.android.tv.settings`, `com.android.providers.*`, permission controller, package installer, `com.google.android.gms`,
  Play Store) or the launcher without a replacement.
- Ask before disabling anything that is not in the lists below, and before the firmware updater (see "Optional").
- Do not paste or store passwords, tokens or keys. Do not put the box's IP address, serial number or Wi-Fi name in anything
  that will be published.
- Stop and tell the person if the box is not Android TV (no adb port, Tizen, webOS, Roku, Fire TV), if `pm disable-user`
  is refused for every package, or if adb asks for something you cannot give. Do not retry in a loop.

## 1. Connect

The person must turn on **Developer options** (Settings → System → About → press the build entry 7 times), then either
**USB debugging**, or **Wireless debugging**.

```bash
adb connect <box ip>:5555
adb devices -l
```

If port 5555 is closed, use Wireless debugging. Ask the person for the pairing code and port shown under
Developer options → Wireless debugging → Pair device with pairing code:

```bash
adb pair <box ip>:<pairing port> <code>
adb mdns services
adb connect <box ip>:<connect port from mdns>
```

If `adb` fails with "No route to host" on macOS while the port is open, that is macOS Local Network privacy: use a
terminal that has the permission, or run adb from another machine. Do not debug it further.

Set `ANDROID_SERIAL=<serial from adb devices>` so every command targets the right device.

## 2. Back up

```bash
mkdir -p backup
adb shell pm list packages -f -u > backup/packages-all.txt
adb shell pm list packages -d > backup/packages-disabled.txt
adb shell getprop > backup/getprop.txt
for n in global secure system; do adb shell settings list $n > backup/settings-$n.txt; done
```

Keep `backup/` out of version control.

## 3. Is this box affected?

```bash
adb shell getprop | grep -iE 'mecool|ro.product.(manufacturer|model)|ro.build.fingerprint'
adb shell pm list packages -u | grep -E 'oemservice|x\.cpe|abc\.nexus'
adb shell ls /oem
```

The report names the property `ro.mecool.device` (it picks the C2 bootstrap host). Seeing `com.oemservice` or
`/oem/xcpe_signed.apk` confirms the backdoor. Seeing neither does not prove a box is clean: run the checks below.

## 4. Detect

### Packages

Packages Google lists. Check each against `backup/packages-all.txt`:

`x.cpe`, `com.oemservice`, `com.abc.nexus`, `com.vs.grass`, `com.android.system.ntp`,
`com.android.sso.auth.handler`, `com.ota.service`, `com.newtv.cboxtv`, `com.gaganode`, `com.gaganode.sdk`

Not in Google's list, but shipped next to the backdoor and updated at the same moment on the tested box:
`com.oem.qa` (the maker's factory test app, with a silent APK installer). Treat it as suspicious.

For a package that is present, read what it is without starting it:

```bash
adb shell dumpsys package com.oemservice | grep -E 'codePath|versionName|sharedUser|installerPackageName|enabled='
```

`sharedUser=android.uid.system` together with `installerPackageName` equal to the package itself is the signature seen on
the tested box.

### Processes, accessibility, Play Protect, properties

```bash
adb shell 'ps -A -o USER,PID,NAME' | grep -E 'oemservice|x\.cpe|abc\.nexus|vs\.grass|system\.ntp|sso\.auth|gaganode|lw_android_agent'
adb shell settings get secure enabled_accessibility_services
adb shell settings get global package_verifier_user_consent
adb shell settings get global package_verifier_enable
adb shell getprop persist.networkstack.uninstall
adb shell getprop persist.networkstack.skip_system_install
adb shell getprop persist.networkstack.install_time
```

- An accessibility entry `com.oemservice/x.cpe.service.AccessibilityKeyDetector` is the backdoor's key and screen hook.
- `package_verifier_user_consent` of `-1`, or `package_verifier_enable` of `0`, means Play Protect was turned off.
- Any value from the three `persist.networkstack.*` properties is a backdoor marker.

### Files

```bash
adb shell 'sha256sum /oem/*.apk /oem/app/*/*.apk 2>/dev/null'
adb shell 'ls /system/priv-app /product/priv-app /system_ext/priv-app'
adb shell 'ls /system/etc/permissions /product/etc/permissions /system_ext/etc/permissions' | grep -i privapp
adb shell 'find /system /vendor /product /system_ext /oem /data/local/tmp \( -name libseccomp_guard.so -o -name libexploit.so -o -name libnative_keys.so -o -name libfleetshare.so -o -name libjaguar.so -o -name libtraceroute.so -o -name lw_android_agent \) 2>/dev/null'
```

- `/oem/xcpe_signed.apk` is the backdoor's spare copy. It cannot be deleted without root and that is fine while its
  package stays disabled. Record its hash; a change later means someone rewrote the partition.
- On a second scan, a new folder under `priv-app` or a new `privapp-permissions-*.xml` that you did not see before, with
  no firmware update in between, is the dropper installing itself as a system app.
- Google's published SHA-256 values for the malware are in `scripts/hashes.txt`. Hash `pm path <package>` outputs and
  compare.
- Any hit from the `find` command is an alarm.

### Network

```bash
adb shell 'cat /proc/net/tcp /proc/net/tcp6' | python3 -I scripts/tcp_check.py
```

The script flags live connections to the four C2 addresses and to MQTT ports 3885 to 3887. Exit code 1 means a hit.

Then see whether the network blocks the C2 names. From any machine on the same network:

```bash
for d in mqtt.szbboys.com ott.szbboys.com mcstb.anyevonline.com intermedia-arts.tv yokatvonline.com linkfog.cn hiddeninplainsight.top; do
  printf '%s -> ' "$d"; dig +short "$d" | head -1
done
```

Blocked names answer `0.0.0.0` or nothing. If they resolve, tell the person to add
[docs/adguard-xcpe.txt](https://github.com/burakztrk/xcpe-guard/blob/main/docs/adguard-xcpe.txt) to their DNS filter. Also check on the box:

```bash
adb shell settings get global private_dns_mode
```

`opportunistic`, `null` or empty means "Automatic": Android may send every lookup encrypted to a public resolver and skip
the home DNS filter. Offer to set `off` (`adb shell settings put global private_dns_mode off`).

## 5. Fix

Show the person the list of what you found and what you will change. Wait for a yes. Then, one command per call:

```bash
adb shell pm disable-user --user 0 com.oemservice
adb shell am force-stop com.oemservice
adb shell pm disable-user --user 0 com.oem.qa
```

Repeat for every listed package that is present and enabled. Each answers `new state: disabled-user`.

Accessibility hook: if `com.oemservice/...` is the only entry, remove the setting; otherwise rewrite it without that entry.

```bash
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
```

Play Protect, only if it was off:

```bash
adb shell settings put global package_verifier_user_consent 1
adb shell settings put global package_verifier_enable 1
```

### Optional, ask first

The firmware updater (`com.sdt.strong.leaps1.ota.update` on the tested KM2) is the maker's own channel: a later update
could bring the backdoor back. Disabling it also stops genuine security patches. Offer it as a choice and say both sides.

Do not touch the remote-control updater, `com.sdt.globalkey`, `com.droidlogic.*`, the launcher, Play Store or Play Services.

## 6. Verify

Android writes package state to disk about ten seconds after a change. Wait before rebooting.

```bash
adb shell sync
sleep 15
adb reboot
```

After the box is back, reconnect (Wireless debugging port changes after a reboot) and repeat the Detect checks:

```bash
adb shell pm list packages -d
adb shell 'ps -A -o USER,PID,NAME' | grep -E 'oemservice|x\.cpe'
adb shell 'cat /proc/net/tcp /proc/net/tcp6' | python3 -I scripts/tcp_check.py
```

Every package you disabled must still be listed as disabled and no XCPE process may run. Some firmware re-enables packages
at boot: if one came back, say so, disable it again and note it in the report.

## 7. Limits to tell the person

- No root: the spare copy in `/oem` stays. It is harmless while its package is disabled.
- A factory reset restores the factory state, backdoor included. Run this guide again afterwards.
- IP addresses cannot be blocked from the box without root: use the router or the DNS filter.
- Android forgets an unused adb key after 7 days. Re-run this guide about weekly, or install the XCPE Guard app, which
  rechecks daily from the box itself.

## 8. Finish: close adb

Switch off **Wireless debugging** and **USB debugging** in Developer options (and revoke USB debugging authorisations).
Then check that port 5555 refuses connections, with Python (on macOS `nc` can be blocked by Local Network privacy):

```bash
python3 -I -c "import socket; s=socket.socket(); s.settimeout(3); print('OPEN' if s.connect_ex(('<box ip>',5555))==0 else 'closed')"
```

Port 5555 is plaintext: do not leave it open.

## Report back

End with a short report: model and build, what was found (package, process, setting, file, network), what you changed,
what you left alone and why, the state after the reboot, and the limits above. Mention anything you could not verify.

## Indicators

| Kind | Values |
|---|---|
| C2 addresses | `219.134.163.210`, `219.134.163.211`, `23.94.49.57`, `38.145.197.135` |
| MQTT ports | 3885 to 3887 (broker `mqtt.szbboys.com`) |
| Core C2 names | `*.anyevonline.com`, `szbboys.com` (and `ott.`, `mqtt.`), `intermedia-arts.tv`, `yokatvonline.com` |
| All blocked names | [docs/adguard-xcpe.txt](https://github.com/burakztrk/xcpe-guard/blob/main/docs/adguard-xcpe.txt) |
| Files | `/oem/xcpe_signed.apk`, `libseccomp_guard.so`, `libexploit.so`, `libnative_keys.so`, `libfleetshare.so`, `libjaguar.so`, `libtraceroute.so`, `lw_android_agent` |
| Content providers | `content://com.release.debug.provider/configs`, `com.oem.share.provider` |
| Properties | `ro.mecool.device`, `persist.networkstack.uninstall`, `persist.networkstack.skip_system_install`, `persist.networkstack.install_time` |
