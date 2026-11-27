# ATracker

Private, single-user, offline-first Android app for tracking anime, manga, manhwa, manhua, light novels and web novels.
All personal data (progress, ratings, notes, history, franchises) lives in a local Room/SQLite database on the phone.
Online catalogue metadata (AniList, MangaDex, Wikidata) is stored separately and can never overwrite personal data.
Anything the providers lack can be added by hand.

Requirements: `docs/REQUIREMENTS.md` · Status and honest verification notes: `docs/DEVELOPMENT_CHECKLIST.md` · Signing: `docs/SIGNING.md`

## Install

Always-current APK (rebuilt on every push to `main`):
**https://github.com/Altriors/ATracker/releases/download/latest/ATracker.apk**

1. Open that link on the phone (or download and copy the file). Allow "Install unknown apps" for the browser/file manager when asked.
2. **Update** by installing a newer APK over the old one. Never uninstall first: uninstalling deletes your data.
   Every build is signed with the same key and has an increasing version code, so in-place updates work.
3. Before anything risky, use **Settings → Save a full backup** and keep the file outside the app.

## Features

- Home, Discover, My Library, Statistics, Settings; dark/light/system theme.
- Discover: search and browse with genre/year/status/score filters; nothing is added to the library without an explicit action.
- Library: status, progress (episodes / chapters / volumes, per format), rating 0–10, favourites, notes, re-reads, filters and sorting.
- Franchises: link related adaptations manually (never by name); each keeps its own progress; combined per-format statistics.
- Statistics and activity history (day/week/month/year); units are never mixed; history events can be voided without changing progress.
- Release calendar for titles in your library (only dates a provider reports; can be switched off).
- Backup/restore (full database), automatic daily backup to a folder you choose, JSON and CSV export, MyAnimeList XML import.

## Build and test

JDK 17 + Android SDK: `./gradlew testDebugUnitTest assembleDebug`.
CI runs unit tests and builds on every meaningful push; the emulator UI test runs on `workflow_dispatch` or a commit message containing `[emulator]`.


## More
Install, MyAnimeList setup, source coverage and known limitations: [docs/SETUP_AND_LIMITATIONS.md](docs/SETUP_AND_LIMITATIONS.md)
