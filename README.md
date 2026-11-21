# ATracker

Private, offline-first Android app for tracking anime, manga, manhwa, manhua, light novels and web novels.
All personal data (progress, ratings, notes, history) lives in a local Room/SQLite database.
Online catalogue metadata is stored separately and can never overwrite personal data.

Requirements and plan: `docs/REQUIREMENTS.md`, `docs/DEVELOPMENT_CHECKLIST.md`.

## Getting the APK

Every push to `main` builds the app on GitHub Actions.
1. Open the repository's **Releases** page and download the newest `ATracker-debug-N.apk`
   (or open **Actions**, pick the latest green run, download the `ATracker-apk` artifact and unzip it).
2. Copy it to the phone and open it. Allow "Install unknown apps" for your browser/file manager when asked.
3. To update, install a newer APK over the old one. Never uninstall first: uninstalling deletes your data.
   All builds use the same signing key, so in-place updates work.

## Build locally (optional)

Needs JDK 17 and the Android SDK: `./gradlew assembleDebug`. Tests: `./gradlew testDebugUnitTest`.
