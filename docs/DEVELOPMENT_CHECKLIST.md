# Development Checklist – Personal Media Library (Android)

Resume point for future sessions. Update after every working increment.

## Environment findings (2026-11-21, session 1)

- Sandbox: Ubuntu 24.04, JDK 21, Gradle 8.14.3, Node 22, Python 3.13, git 2.43.
- No Android SDK installed, and it cannot be downloaded: the egress proxy returns 403 for
  dl.google.com, repo.maven.apache.org and plugins.gradle.org (free plan allows package managers only).
- Result: the app CANNOT be compiled or APK-built inside the sandbox.
- api.github.com is reachable. GH_TOKEN is invalid and no GitHub account is linked to Claude yet,
  so nothing can be pushed until the user connects GitHub.

## Chosen delivery route

GitHub Actions builds the APK (cloud runner has full network + Android SDK):
1. User connects GitHub to Claude and creates an empty repo (private is fine).
2. Claude pushes the project; the workflow `.github/workflows/build-apk.yml` compiles, runs unit tests
   and publishes `app-debug.apk` as a workflow artifact and a GitHub Release asset.
3. Claude reads the workflow logs through the REST API, fixes compile errors, and repeats.
4. User downloads the APK to the vivo T3 Ultra and installs it (allow "install unknown apps").

Fallback if GitHub cannot be linked: Claude writes the full project, user uploads the folder to a
new GitHub repo through the web UI, and Actions builds the APK. Errors then have to be pasted back.

## Status

- [x] Inspect build environment
- [x] GitHub linked, repo Altriors/ATracker created, push access verified
- [ ] Phase 0: Architecture, provider feasibility and DB schema docs
- [x] Phase 1: Project foundation, Room database v1, repositories, ownership tests (CI green, APK published)
- [ ] Phase 2: Providers (AniList, Jikan, MangaDex, manual entry) and catalogue cache
- [ ] Phase 3: Library and progress tracking
- [ ] Phase 4: Franchises
- [ ] Phase 5: Activity history and statistics
- [ ] Phase 6: Discover, search, filters
- [ ] Phase 7: Calendar and release tracking
- [ ] Phase 8: Backup, restore, import/export (auto-backup early)
- [ ] Phase 9: Polish, performance, tests, release APK

## Rule

Also: debug and release builds are signed with the committed key app/atracker-debug.keystore so APKs
update in place without losing data.


Nothing is marked done until it has compiled and its tests have passed in CI.
