# ATracker: install, MyAnimeList setup, limitations

## Install / update
1. On the phone open https://github.com/Altriors/ATracker/releases/download/latest/ATracker.apk and install it (allow "install unknown apps" for your browser once).
2. Installing a newer APK over the old one keeps your data. **Uninstalling deletes it**; make a backup first (Settings → Backup).

## Importing from MyAnimeList
**Option 1, export file (no setup, recommended first):** on myanimelist.net open Profile → Export My List, download the anime and manga files, then in ATracker Settings → MyAnimeList pick each file (.xml or .xml.gz). You get a preview, choose how existing titles are handled, then confirm.

**Option 2, connect your account:** MyAnimeList requires every app to have its own free Client ID.
1. Go to https://myanimelist.net/apiconfig and create an app (type: other).
2. Redirect URL must be exactly `atracker://mal-auth`.
3. Paste the Client ID in ATracker (Settings → MyAnimeList), tap Connect, approve in the browser, then "Import my lists".
The sign-in token is stored encrypted with an Android Keystore key.

Matching uses the MAL id (via AniList's `idMal`), never title similarity. Re-running an import never duplicates titles. Defaults never overwrite your own progress, scores, notes or statuses.

## Sources and what they cover
| Source | Anime | Manga | Manhwa/Manhua | Light novels | Web novels |
|---|---|---|---|---|---|
| AniList | yes | yes | yes | yes | no |
| MangaDex | no | yes | yes | no | no |
| Kitsu | no | yes | yes | yes | yes |
| MangaUpdates | no | yes | yes | no | yes (novels) |
| Wikidata | partial | partial | partial | partial | partial |
No API keys are needed for any search source.

## Known limitations (not hidden)
- **MAL account connection is untested against the live MAL service** (needs your own Client ID). Parsing, matching, preview, conflict handling and history are covered by automated tests; the export-file route is the verified one. MAL's documentation does not confirm that the API returns start/finish dates, tags and comments; the app asks for them and, if MAL rejects the request, falls back to status/progress/score and tells you.
- Titles that exist only on MAL (no match on AniList) are imported with MAL's own data and marked "no online match"; they have no cover until matched. There is no Kitsu/MangaUpdates matching by MAL id for them yet.
- Kitsu has one "novel" type; light vs web novel is inferred from the original-language title Kitsu returns. MangaUpdates lists all novels as web novels.
- Release alerts use only dates a source reported (AniList airing schedule); chapter release dates are generally not available.
- Search results from different sources for the same title are listed separately (they are not merged by name on purpose).
- Bulk editing in the library and per-source selection in search are not implemented.
- Statistics are computed from the database; the activity heat-map and trend charts follow the existing Statistics screen, not the prototype's exact charts.
- Adult-rated titles are excluded from search.
