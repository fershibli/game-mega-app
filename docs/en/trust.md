# Trust, privacy and security

🌐 **English** · [Português](../pt-BR/trust.md) · [Español](../es/trust.md) · [Français](../fr/trust.md) · [中文](../zh-CN/trust.md)

Mega Games is meant to be installed on the phones of people who may not be able to judge an app themselves. This page explains what the app does and does not do, and — more importantly — **how anyone can verify it** without trusting the author.

## Contents

1. [Summary](#summary)
2. [Privacy](#privacy)
3. [Permissions](#permissions)
4. [What is inside the app](#what-is-inside-the-app)
5. [How the code is checked](#how-the-code-is-checked)
6. [Verifying a downloaded APK](#verifying-a-downloaded-apk)
7. [Supply-chain protections](#supply-chain-protections)
8. [AI assistance, honestly](#ai-assistance-honestly)
9. [Reporting a security problem](#reporting-a-security-problem)

## Summary

| | |
|---|---|
| Internet access | **None** (no `INTERNET` permission) |
| Data collected | **None** |
| Ads, analytics, crash reporters, trackers | **None** |
| Accounts, logins, purchases | **None** |
| Android permissions requested | **None** |
| Third-party code | Only official AndroidX (Google) and Kotlin (JetBrains) libraries |
| License | MIT, source code public |

## Privacy

The app stores exactly two things, both in its **private** storage, which other apps cannot read:

1. `settings.xml` — your choices in Settings (language, sizes, colors…).
2. `current_game.json` — the game in progress, so it can be resumed after the app is closed. It is deleted when the game ends or is abandoned.

If you turned on Android's own backup, Android may include these two files in *your* Google backup (and device-to-device transfer), so a new phone keeps your settings. This is configured in [`backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) and [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml). Nothing else is ever stored or sent.

The "Open the project page" button in Settings → About simply asks Android to open the GitHub page in your browser; the app itself still makes no connection.

## Permissions

The [manifest](../../app/src/main/AndroidManifest.xml) declares **no** `<uses-permission>`. You can confirm this on any APK with the Android SDK:

```bash
aapt2 dump permissions MegaGames-v1.0.0.apk
```

You will see a single entry ending in `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. This is not a user permission: it is a private, signature-level permission that the AndroidX library adds automatically to protect the app's own internal broadcast receivers from other apps. It grants nothing to anyone.

Without the `INTERNET` permission, Android does not let the app open any network connection at all.

## What is inside the app

The complete dependency list is in [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml):

| Library | Publisher | Why |
|---|---|---|
| `androidx.activity:activity-compose` | Google (AndroidX) | Hosts the Compose UI in the activity |
| `androidx.compose.ui`, `foundation`, `material3` | Google (AndroidX) | The UI toolkit |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JetBrains | Saving the game in progress as JSON |
| Kotlin standard library | JetBrains | The language runtime |

Test-only libraries (never shipped): JUnit, Robolectric, Compose UI test.

There are **no images, sounds, fonts or native libraries** in the app: every picture is drawn by code that you can read in [`ui/art`](../../app/src/main/java/io/github/fershibli/megagames/ui/art). The only data files are the crossword dictionaries in plain text ([`assets/crossword`](../../app/src/main/assets/crossword)).

The release build also disables Google's "dependency info" block (`dependenciesInfo { includeInApk = false }`), an encrypted blob that would otherwise be embedded in the APK. Every byte of the APK can therefore be inspected.

## How the code is checked

Every push and pull request runs [CI](../../.github/workflows/ci.yml) on GitHub Actions:

1. **Gradle wrapper validation** — confirms `gradle-wrapper.jar` is the official binary.
2. **50 automated tests**, including:
   - rules of every game (legal moves, scoring, bearing off, Buraco sequences and wild cards…);
   - generators: every Sudoku has a unique solution, every Mahjong deal is solvable, every Pipes and Connect puzzle has a full solution, crosswords never clash;
   - computer opponents finish their games quickly and never lose or duplicate a card or checker;
   - all five languages translate every text and keep the same placeholders;
   - autosave round-trip, corrupted-save handling, settings persistence;
   - the real UI: pinch zoom on/off, one-hand double-tap and press-and-rotate zoom, choosing a game, leaving it from Settings, and the "continue?" prompt after a restart.
3. **Android Lint** with *warnings treated as errors*.
4. **Build** of debug and release APKs, published as artifacts with **SHA-256 checksums**.

Run the same checks yourself: see [building.md](building.md).

## Verifying a downloaded APK

1. Download `MegaGames-vX.Y.Z.apk` and `SHA256SUMS.txt` from the same [release](https://github.com/fershibli/game-mega-app/releases).
2. Compute the checksum and compare:

   ```bash
   sha256sum MegaGames-vX.Y.Z.apk      # Linux / macOS: shasum -a 256
   certutil -hashfile MegaGames-vX.Y.Z.apk SHA256   # Windows
   ```

3. Check the signing certificate stays the same across versions (Android also refuses to update an app signed with a different key):

   ```bash
   apksigner verify --print-certs MegaGames-vX.Y.Z.apk
   ```

4. For full assurance, build it yourself from the tagged source and compare the contents (`unzip -l`, `aapt2 dump badging`).

## Supply-chain protections

- `distributionSha256Sum` pins the exact Gradle distribution in [`gradle-wrapper.properties`](../../gradle/wrapper/gradle-wrapper.properties).
- Dependencies come only from Google's Maven repository and Maven Central (`repositoriesMode = FAIL_ON_PROJECT_REPOS`).
- [Dependabot](../../.github/dependabot.yml) proposes dependency and GitHub Actions updates every month.
- CI runs with read-only permissions; only the versioning and deploy workflows can write, and only to push the version tag and publish the release. The signing key workflow writes only the signing secrets, with a token limited to this repository's secrets.
- Every published APK is verified in the pipeline before upload: signature (`apksigner`), version name, and that it requests no permissions (`aapt2`).
- Release signing keys live in GitHub encrypted secrets and are never committed (`*.jks`, `keystore.properties` are git-ignored).

## AI assistance, honestly

The code, the artwork and the documentation were created with the assistance of **Claude**, Anthropic's AI assistant, under the maintainer's direction. AI-written code can contain mistakes like any other code, which is why this project relies on things you can check rather than on promises: readable source, a broad test suite, lint, reproducible builds and published checksums. If you spot a problem, please report it.

## Reporting a security problem

See [SECURITY.md](../../SECURITY.md). Please do not open a public issue for a vulnerability; use GitHub's private vulnerability reporting.
