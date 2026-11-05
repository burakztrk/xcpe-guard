# AGENTS.md

Instructions for coding agents (Claude Code, Codex, Gemini CLI, Cursor and others) working with this repository.

## Which job are you doing?

| You were asked to | Do this |
|---|---|
| Check or clean an Android TV box for the XCPE botnet, without installing the app | Read [skills/xcpe-guard/SKILL.md](skills/xcpe-guard/SKILL.md) and follow it exactly, on the real box, over adb. Use `skills/xcpe-guard/scripts/` instead of writing your own. |
| Change this project: code, strings, docs, the blocklist | Read [Working on the code](#working-on-the-code) below. |

The box-cleaning procedure has hard safety rules: disable packages, never uninstall; never start a factory or test package;
never touch Android's core packages; back up first; close adb at the end. They are at the top of the skill and are not
negotiable.

## Working on the code

- Kotlin, Jetpack Compose for TV, `minSdk` 26, package `com.booapps.xcpeguard`. Build with JDK 17 and Android SDK 34:
  `./gradlew assembleDebug`. A debug build needs a higher `VERSION_CODE` than the one on the device to install over it
  (`VERSION_CODE=20 ./gradlew assembleDebug`).
- Do not write comments in code. Names carry the meaning.
- Every user-facing string lives in both `app/src/main/res/values/strings.xml` and `values-tr/strings.xml`. Findings are
  stored as message keys and translated when drawn, so a language change retranslates old reports: never store a
  translated sentence.
- The indicators are in `scan/Iocs.kt`; the checks are in `scan/Scanner.kt`. The XCPE source of truth is Google's report.
  Google's packages are malware. Anything else, like `com.oem.qa`, is only ever flagged as suspicious, with the reason.
- Fixing means `pm disable-user`. The app never uninstalls and never starts a package it is switching off.
- The release workflow signs with four repository secrets and publishes `v1.0.<run number>` on every push to `main` that
  changes code. Docs, `skills/`, `LICENSE` and `.github/` changes do not release. Running the workflow by hand on any
  other branch publishes a pre-release `v1.0.<run number>-test` instead; it never becomes the latest release.
- Never commit keystores, passwords, tokens, device serial numbers, IP addresses or Wi-Fi names, in code, docs or
  screenshots.
- Licence: GPL-3.0. New code is released under it.
