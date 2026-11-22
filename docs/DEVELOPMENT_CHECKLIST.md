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

## Provider findings (verified live from CI on 2026-11-21, see scripts/probe-providers.sh)

| Need | Source | Verified result |
|---|---|---|
| Anime, manga, manhwa, manhua, Japanese light novels | AniList GraphQL | Found Lord of the Mysteries (manhua + anime ONA), Omniscient Reader, Tower of God, Eleceed, Solo Leveling, Frieren, Mushoku Tensei, Kino no Tabi, Spice and Wolf. Relations and next-airing episode included. Rate limit 30/min. |
| Manga/manhwa/manhua, covers | MangaDex | Found Reverend Insanity (manhua, cancelled, 96 chapters) and many obscure titles. lastChapter only trusted for finished series. Links AniList id for cross-reference. |
| Web novels (Lord of the Mysteries, Reverend Insanity) | Wikidata | Both found with title, aliases, author, year. NO chapter counts, status or covers: stays unknown, user enters them. |
| Chinese/Korean web novels in general | none | AniList has no web novels (searches returned nothing). Manual entry is first-class. |
| Jikan (MAL) | Jikan | NOT reachable from the CI runner (timeouts), so not used for now. |
| Open Library / Google Books | - | Poor for web novels (returned fan-fiction and unrelated books); Google Books quota-limited. Not used. |

Coverage limits: only what these sources hold can be discovered. Web novels beyond the Wikidata list must be added by hand.

## Status (only items that are implemented AND covered by passing CI are ticked)

- [x] Environment inspected; build via GitHub Actions; push access verified
- [x] Phase 1: Room schema v1, repositories, ownership tests
- [x] Phase 2: provider layer (AniList, MangaDex, Wikidata), cache + offline fallback, cross-reference merge, manual entry data layer. 29 tests on real captured responses.
- [~] Phase 3: library + progress. Data layer tested. UI written (Home, Discover, Detail with progress/rating/status/notes/favourite/rewatch, Library grid/list with filters+sort+search). Filtering logic tested (14 tests). UI has NOT yet been run on a device or emulator.
- [ ] Phase 4: franchises (linking UI, franchise page, combined stats)
- [ ] Phase 5: activity history views + statistics dashboard
- [ ] Phase 6: advanced search (basic search/browse/filters done in Phase 2/3)
- [ ] Phase 7: calendar / release tracking (data stored from AniList next-airing; no screen yet)
- [ ] Phase 8: backup/restore, JSON/CSV export, MAL import, auto-backup, settings screen
- [ ] Phase 9: emulator UI smoke tests, polish, performance

Statistics and Settings tabs currently show "not built yet".

## Signing key

app/atracker-debug.keystore is committed so every build is signed identically (in-place updates
keep your data). Limitation: the repository is public, so the key is public too. For a personal sideloaded
app this only means someone could build an APK that would install over yours IF you installed it
yourself, so only install APKs from your own repository's Releases. The sandbox is not permitted to set GitHub
secrets or make the repo private; doing either in the GitHub website would let us move the key out of the repo
(see docs/SIGNING.md).

## Rule

Nothing is marked done until it has compiled and its tests have passed in CI.
