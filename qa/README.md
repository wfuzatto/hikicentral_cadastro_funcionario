# HikCentral Android QA

Run `./qa/run-all.ps1` from the repository root in PowerShell. The suite runs local JVM unit tests and builds the debug APK, then attempts a safe update install and basic launch/hierarchy/screenshot capture on exactly one **authorized** Android device. Set Java, Android SDK, Gradle and platform-tools in `PATH` first.

`adb install -r` preserves app data. The suite never runs `pm clear` or uninstalls the app. If the APK signing key differs, installation fails without removing the existing app; use the original signing key for an in-place update.

`test-connectivity.ps1` tests DNS, TCP and TLS from Windows only. It cannot establish the phone's network path. `test-ui.ps1` checks launch and visible labels; it does not automate dialogs, form validation, keyboard, rotation or camera. Camera and API scripts deliberately stop short of capturing biometric data or creating records. API credentials must be configured securely on the device; the app's Test OpenAPI action is read-only.

Never place AppKey/AppSecret, photos, or real personal data in reports. Device artifacts are timestamped under `qa/reports/` and should be reviewed before sharing. Do not use production data for end-to-end tests. No Person is created by this QA suite.
