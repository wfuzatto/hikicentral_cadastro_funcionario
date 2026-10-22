# HikCentral Android QA

Run `./qa/run-all.ps1` from the repository root in PowerShell. It runs JVM tests/build, Windows and Android DNS/TCP/TLS diagnostics, safe update install, basic UI/settings checks, camera open-and-cancel, and log collection on exactly one authorized Android device. Set Java, Android SDK, Gradle and platform-tools in `PATH` first. If PowerShell blocks scripts, start it with `-ExecutionPolicy Bypass` for this invocation.

`adb install -r` preserves app data. The suite never runs `pm clear` or uninstalls the app. If the APK signing key differs, installation fails without removing the existing app; recover the original signing key to update in place.

Connectivity diagnostics use no credentials and do not authenticate to OpenAPI. They report Android and Windows network paths separately. `test-ui.ps1` verifies launch, the accessible settings control, server/port fields without saving changes, and empty-name validation. `test-camera.ps1` opens Camera and cancels without taking a photo. Rotation and other keyboard flows need additional testing. `test-api.ps1` intentionally performs no calls: configure OpenAPI AK/SK securely on-device and use the app's read-only Test OpenAPI control. No Person is created by this suite.

Never place AppKey/AppSecret, face photos, or personal data in reports. Generated device artifacts are timestamped under `qa/reports/` and are ignored by Git by default; review any artifacts before sharing. Do not use production data for end-to-end tests.
