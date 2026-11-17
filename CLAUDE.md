# LiftLog — Claude Code Context

**Inherits:** All rules from `~/CLAUDE.md` and `~/Code/CLAUDE.md`

## What This Is

A personal Android workout tracker (working name LiftLog), sideloaded onto Ryan's Galaxy S22
and never published. Native Kotlin with Jetpack Compose and Material 3, Room for on-device
storage, DataStore for settings, Vico for charts. Single Gradle module, MVVM, manual
dependency wiring through an `AppContainer`. This is a personal project, not QVC work.

The approved build plan and milestones are in `~/.claude/plans/floofy-finding-sutton.md`.

## Key Files

| Component | File | Notes |
|-----------|------|-------|
| Versions | `gradle/libs.versions.toml` | Every plugin and library version lives here |
| App build | `app/build.gradle.kts` | SDK levels, signing, dependencies |
| Entry point | `app/src/main/java/app/liftlog/MainActivity.kt` | |
| Navigation | `app/src/main/java/app/liftlog/ui/LiftLogApp.kt` | Bottom bar and nav graph |

## Build and Install

There is no system Java and `adb` is not on PATH. Use Android Studio's bundled JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installRelease        # build and install on the connected phone
./gradlew test                  # unit tests
~/Library/Android/sdk/platform-tools/adb devices
```

## Important Notes

- **No `INTERNET` permission, by design.** Data leaves the phone only through a user-triggered
  export. Do not add network libraries or the permission.
- **Signing key lives outside the repo** at `~/.config/liftlog/liftlog.jks`; its password is in
  the macOS Keychain (service `liftlog-signing`, account `store_password`). Debug and release
  builds both use it, so either installs over the other without wiping data. Losing the key
  means an uninstall, which deletes all workout data.
- **Weights are stored in kg** and converted for display. Never rewrite stored values when the
  unit setting changes.
- **Room migrations are always real migrations.** Never use destructive fallback: the database
  is the user's training history.
- **Install the release build for daily use.** Compose debug builds are noticeably slower.
- Same functionality as MyFitCoach, but no copied branding, artwork or exercise media.

## Branches

- `main` — the version installed on the phone
- one feature branch per milestone
