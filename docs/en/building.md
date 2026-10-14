# Building, releasing and contributing

🌐 **English** · [Português](../pt-BR/building.md) · [Español](../es/building.md) · [Français](../fr/building.md) · [中文](../zh-CN/building.md)

## Contents

1. [Requirements](#requirements)
2. [Build and run](#build-and-run)
3. [Tests and lint](#tests-and-lint)
4. [Screenshots](#screenshots)
5. [AI benchmarks](#ai-benchmarks)
6. [Signed releases](#signed-releases)
7. [Contributing](#contributing)
8. [Adding crossword words](#adding-crossword-words)
9. [Adding or improving a translation](#adding-or-improving-a-translation)
10. [Adding a game](#adding-a-game)

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

To add one, add a `@Test` to `app/src/test/.../ScreenshotTest.kt` using `shot(...)` or `gameShot(...)`.

## AI benchmarks

```bash
./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*AiBenchmarkTest*' -i | grep BENCHMARK
```

Plays the computer opponents against each other (Buraco, Backgammon, Reversi) and prints win rates. Results are recorded in [technical.md](technical.md#benchmarks).

## Signed releases

Android only installs signed APKs, and updates must be signed with the **same key**. Keep the key safe: if it is lost, users must uninstall to install a new version.

1. **Create a key once** (keep the file and the passwords in a password manager):

   ```bash
   keytool -genkeypair -v -keystore megagames-release.jks -alias megagames \
     -keyalg RSA -keysize 4096 -validity 36500
   ```

2. **Local signing**: create `keystore.properties` in the project root (it is git-ignored):

   ```properties
   storeFile=megagames-release.jks
   storePassword=...
   keyAlias=megagames
   keyPassword=...
   ```

   Then `./gradlew assembleRelease` produces `app/build/outputs/apk/release/app-release.apk`.

3. **Releases from GitHub**: add four repository secrets (*Settings → Secrets and variables → Actions*):

   | Secret | Value |
   |---|---|
   | `MEGAGAMES_KEYSTORE_BASE64` | output of `base64 -w0 megagames-release.jks` |
   | `MEGAGAMES_KEYSTORE_PASSWORD` | store password |
   | `MEGAGAMES_KEY_ALIAS` | `megagames` |
   | `MEGAGAMES_KEY_PASSWORD` | key password |

   Bump `versionCode` and `versionName` in `app/build.gradle.kts`, update [CHANGELOG.md](../../CHANGELOG.md), then:

   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```

   The [release workflow](../../.github/workflows/release.yml) tests, builds, signs and publishes `MegaGames-v1.0.0.apk` with `SHA256SUMS.txt`.

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
- Every user-visible text goes to `strings.xml` **in all five languages** (`TranslationsTest` enforces it).
- Code comments and identifiers in English; follow the existing style (`.editorconfig`, Kotlin official style).

## Adding crossword words

Dictionaries are plain text in `app/src/main/assets/crossword/<lang>.txt`, one entry per line:

```
ANSWER|A short, friendly clue
```

- Everyday words, 2–13 letters (Chinese: 2–4 characters), no proper names that need specific knowledge.
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

1. Add an entry to `GameId` (its `key` is stored in save files: never change it later) with title and how-to strings.
2. Create `games/<name>/` with a pure-Kotlin engine (`@Serializable` state) and unit tests.
3. Write the screen with `LoadGame(host, State.serializer(), create = { ... })` and `GameLayout(status, controls) { board }`; call `host.finish(GameOutcome(...))` at the end.
4. Route it in `GameContent.kt` and draw its thumbnail in `ui/art/Thumbnails.kt`.
5. Add a screenshot test and document the rules in all five user guides.
