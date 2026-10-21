# Building, releasing and contributing

🌐 **English** · [Português](../pt-BR/building.md) · [Español](../es/building.md) · [Français](../fr/building.md) · [中文](../zh-CN/building.md)

## Contents

1. [Requirements](#requirements)
2. [Build and run](#build-and-run)
3. [Tests and lint](#tests-and-lint)
4. [Screenshots](#screenshots)
5. [AI benchmarks](#ai-benchmarks)
6. [Signed releases](#signed-releases)
7. [Versioning and deploy](#versioning-and-deploy)
8. [Contributing](#contributing)
9. [Adding crossword words](#adding-crossword-words)
10. [Adding or improving a translation](#adding-or-improving-a-translation)
11. [Adding a game](#adding-a-game)

## Requirements

- **JDK 17 or newer** (CI uses Temurin 21).
- **Android SDK** with platform **API 37** (Android Studio installs it; or `sdkmanager "platforms;android-37"`).
- Internet access on the first build (Gradle downloads itself and the dependencies).

Point Gradle to the SDK with `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=/path/to/Android/sdk`.

## Build and run

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # install on a connected phone/emulator (USB debugging on)
./gradlew assembleRelease    # shrunk with R8; unsigned unless a key is configured
```

Or open the folder in **Android Studio** and press *Run*.

## Tests and lint

```bash
./gradlew testDebugUnitTest   # 50 tests: rules, generators, AI, translations, UI flow
./gradlew lintDebug           # Android Lint, warnings are errors
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`.

The UI tests use **Robolectric**, so they run on the JVM without an emulator.

## Screenshots

Every image in the README is rendered from the real code:

```bash
./gradlew :app:testDebugUnitTest -Pscreenshots=true --tests '*ScreenshotTest*'
# PNG files are written to docs/screenshots/
```

Then shrink them before committing: `python3 scripts/optimize_screenshots.py` (needs Pillow).

To add one, add a `@Test` to `app/src/test/.../ScreenshotTest.kt` using `shot(...)` or `gameShot(...)`.

## AI benchmarks

```bash
./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*AiBenchmarkTest*' -i | grep BENCHMARK
```

Plays the computer opponents against each other (Buraco, Backgammon, Reversi) and prints win rates. Results are recorded in [technical.md](technical.md#benchmarks).

## Signed releases

Android only installs signed APKs, and updates must be signed with the **same key**. If the key is lost, users must uninstall to install a new version.

**On GitHub (recommended)**: the [`signing-key.yml`](../../.github/workflows/signing-key.yml) workflow creates the key, stores it in the repository secrets and re-encrypts it whenever you change the password in the `MEGAGAMES_SIGNING_PASSWORD` secret. It never replaces an existing key, and it can make an encrypted backup. Setup (one token, two clicks): [google-play.md → Signing key](google-play.md#1-signing-key).

The [deploy pipeline](#versioning-and-deploy) signs every APK and AAB with that key. Without it, release candidates (`stag`) are still published, signed with a temporary debug key and marked as such in the release notes, but a stable version (`main`) is never published: the run fails before creating the tag, so you can create the key and simply re-run it.

**Locally**: with the key file (from a backup), create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=miligames-release.p12
storePassword=...
keyAlias=megagames
keyPassword=...
```

Then `./gradlew assembleRelease bundleRelease` produces `app/build/outputs/apk/release/app-release.apk` and `app/build/outputs/bundle/release/app-release.aab`.

**Google Play**: step-by-step guide, with the answers to every Play Console form: [google-play.md](google-play.md).

## Versioning and deploy

Versions follow [Semantic Versioning](https://semver.org) (`MAJOR.MINOR.PATCH`) and are computed **automatically** from [Conventional Commits](https://www.conventionalcommits.org) by our own workflow, [`versioning.yml`](../../.github/workflows/versioning.yml) (plain bash, no third-party release tool). It then calls [`deploy.yml`](../../.github/workflows/deploy.yml), which builds and publishes the APK.

| Commit message | Effect | Example |
|---|---|---|
| `feat!: …` / `fix!: …` or `BREAKING CHANGE:` in the body | **MAJOR** | 1.4.2 → 2.0.0 |
| `feat: …` / `feat(sudoku): …` | **MINOR** | 1.4.2 → 1.5.0 |
| `fix:`, `perf:`, `revert:`, `refactor:`, `build:` | **PATCH** | 1.4.2 → 1.4.3 |
| `docs:`, `chore:`, `ci:`, `test:`, `style:` or no prefix | no release | — |

The first release is `1.0.0`. Branches:

| Push to | Result |
|---|---|
| `main` (or `master`) | stable release `v1.5.0`, marked *Latest* |
| `stag` | release candidate `v1.5.0-rc.1`, `-rc.2`… marked *Pre-release* (a new rc only when something releasable arrived) |

What happens on each release:

1. `versioning.yml` reads the commits since the last stable tag, picks the bump, writes grouped release notes (breaking changes, features, fixes, performance, others) and pushes an annotated tag `vX.Y.Z`.
2. `deploy.yml` checks out that tag, runs the tests and lint, builds the release APK with `-PappVersion=X.Y.Z`, signs it, **verifies** it (signature, `versionName`, no permissions requested), and publishes a GitHub Release with `MiliGames-vX.Y.Z.apk`, `SHA256SUMS.txt` and the notes.

The Android `versionCode` is derived from the version so it always increases: `MAJOR×1,000,000 + MINOR×10,000 + PATCH×100 + n` where `n` is the rc number, or 99 for the final release (so `1.5.0-rc.2` < `1.5.0`). Minor and patch must stay below 100.

Other ways to deploy:

- **Actions → Semantic versioning → Run workflow** and choose `patch`, `minor` or `major` to force a release.
- **Actions → Deploy APK → Run workflow** with a version number to (re)build a specific version.
- Push a tag by hand: `git tag v1.2.3 && git push origin v1.2.3` (release notes are then generated by GitHub).

Locally, `./gradlew assembleRelease -PappVersion=1.2.3` builds that version; without `-PappVersion` the version is `1.0.0`.

## Contributing

Contributions are welcome — especially from people who play these games with older relatives.

1. Open an issue describing the bug or idea (screenshots help).
2. Fork, create a branch, make the change.
3. Run `./gradlew testDebugUnitTest lintDebug` — CI runs the same.
4. Open a pull request explaining *why* the change helps players.

Guidelines:

- **Accessibility first**: touch targets ≥ 56 dp, main actions in the lower half of the screen and mirrored for left-handed mode (`ActionBar`), never color alone (use `drawSymbol` in accessible palettes), no timers or time pressure.
- **Stay tiny and private**: no new permissions, no network, no analytics, no image assets (draw in code), no new dependency without a strong reason.
- **Engines are pure Kotlin** with unit tests; the UI only draws state and calls the engine.
- Every user-visible text goes to `strings.xml` (or the game's own `strings_<key>.xml`) **in all ten languages** (`TranslationsTest` enforces it).
- Write commit messages as [Conventional Commits](https://www.conventionalcommits.org) (`feat: …`, `fix: …`, `docs: …`): they decide the next version (see [Versioning and deploy](#versioning-and-deploy)).
- Code comments and identifiers in English; follow the existing style (`.editorconfig`, Kotlin official style).

## Adding crossword words

Dictionaries are plain text in `app/src/main/assets/crossword/<lang>.txt`, one entry per line:

```
ANSWER|A short, friendly clue
```

- Everyday words, 2–13 letters (Chinese: 2–4 characters; Japanese: 2–6 kana, in katakana), no proper names that need specific knowledge.
- Clues should be gentle and unambiguous — think of someone in their eighties.
- Accents are fine in answers; they are removed automatically (Spanish keeps Ñ).
- Lines starting with `#` are comments. `CrosswordTest` checks every dictionary still has at least 120 usable words.

## Adding or improving a translation

1. Copy `app/src/main/res/values/strings.xml` to `values-<code>/strings.xml` and translate every `<string>` and `<plurals>` (keep `%1$d`-style placeholders; escape apostrophes as `\'` or use the typographic `’`).
2. Add the language to `AppLanguage` in `core/Settings.kt`, to `localeFilters` in `app/build.gradle.kts` and to `res/xml/locales_config.xml`.
3. Add a crossword dictionary `assets/crossword/<code>.txt` and map it in `dictionaryFile()`.
4. Translate the README and the four documents in `docs/<code>/`, and add them to [docs/README.md](../README.md).
5. Run the tests.

## Adding a game

Each game lives in its own files, so several games can be developed in parallel branches and only a few one-line additions meet in shared files.

1. **Register it** with one line in `GameId` (`core/Model.kt`): a `key` that is stored in save files and must never change, title and how-to strings, a `GameCategory` (cards, board games, puzzles, calm action, pastimes) and, when it has regional rules, its `variants`. A `GameVariant` has a stable `key`, a name and a one-line description; the rules screen shows them before the difficulty and preselects the last choice. Set `hasDifficulty = false` for games without levels (a pet, a farm). A game that cannot work in every language (a cryptogram has no alphabet to swap in Japanese or Chinese) lists the ones it can with `languages = setOf(AppLanguage.ENGLISH, AppLanguage.PORTUGUESE, ...)`: it stays on the home screen, and when the app language is not in the set the player first picks one of them; the game then reads it from `LocalGameLanguage` (and its own strings come in that language).
2. **Engine** in `games/<name>/`: pure Kotlin, a `@Serializable` state and unit tests. Computer opponents run on the phone, without network, and must answer in well under a second on a slow phone (`AiBenchmarkTest` style).
3. **Screen**: `LoadGame(host, State.serializer(), create = { ... })` and `GameLayout(status, controls) { board }`; read the rules from `host.variant` and the level from `host.difficulty`; call `host.finish(GameOutcome(...))` at the end. Boards are drawn in left-to-right coordinates even in Arabic: draw everything in code (no image files) and keep touch targets big.
4. **Lasting worlds** (a pet, a farm) keep their state with `rememberWorldStore()` instead of the autosave, which only holds the one game in progress and is erased when another game starts. Never punish absence: no deaths, no withering, no notifications.
5. **Route** it in `GameContent.kt` and draw its text-free thumbnail in `ui/art/Thumbnails.kt` (one line each; the drawing itself goes in the game's own file).
6. **Texts** go to `res/values*/strings_<key>.xml`, one file per game, **in all ten languages** (`TranslationsTest` checks every key and placeholder in every language).
7. **Rules for players** go to `docs/<lang>/games/<key>.md` in the ten languages.
8. **Tests**: engine tests, at least one UI test that plays a few moves by tapping the board like a person would, and a screenshot in `ScreenshotTest`. `CatalogTest` already opens every game with every set of rules.
9. **Names and looks**: only generic, descriptive names, and pictures of our own. Game rules are free to use, but names, characters, artwork and the look of a commercial game are not (see [Trust](trust.md)). When in doubt, leave it out: a single complaint can take the whole app off the store.

When a pull request to `stag` is merged, the **Sync open PRs** workflow (`sync-prs.yml`) merges `stag` into every other open pull request that now conflicts. If the conflicts are only in the lists above (`GameId`, `GameContent.kt`, `Thumbnails.kt`, `ScreenshotTest.kt` and the changelog), it keeps both sides (`scripts/merge_game_lists.py`), checks that everything compiles, pushes the merge and starts CI. Any other conflict is listed in the run summary for a person to solve.
