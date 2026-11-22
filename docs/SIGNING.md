# Signing and updating

All builds are signed with `app/atracker-debug.keystore` (alias `atracker`, password `android`).
Android only lets an APK update an installed app when both were signed with the same key, so keeping this key
fixed is what lets you install new versions without uninstalling (which would delete your data).

## Security status

The repository is currently **public**, so this key is public. Consequences:
- Nobody can read your library: data lives only on your phone.
- Someone could build their own APK signed with this key. It would only replace your app if you installed it,
  so install APKs only from your own repository's Releases page.

## Hardening (optional, takes the owner about 5 minutes, needs the GitHub website)
1. Make the repository private (Settings > General > Danger Zone > Change visibility).
2. Keep using the same key: nothing else to do, builds and updates keep working.
Moving to a secret-held key would REQUIRE a one-time uninstall/reinstall of the app (different signature), so
only do that after exporting a backup from the app, and only if you want it.
