<div align="center">

# Mega Games

**Ten classic games in one tiny, private Android app — designed for older adults.**

Sudoku · Solitaire · Buraco · Backgammon · Mahjong · Reversi · Crossword · Pipes · Connect · Dots

🌐 **English** · [Português](README.pt-BR.md) · [Español](README.es.md) · [Français](README.fr.md) · [中文](README.zh-CN.md)

[![CI](https://github.com/fershibli/game-mega-app/actions/workflows/ci.yml/badge.svg)](https://github.com/fershibli/game-mega-app/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/fershibli/game-mega-app?include_prereleases&sort=semver)](https://github.com/fershibli/game-mega-app/releases)
![License: MIT](https://img.shields.io/badge/license-MIT-green)
![Android 6.0+](https://img.shields.io/badge/Android-6.0%2B-3DDC84)
![APK about 1.3 MB](https://img.shields.io/badge/APK-~1.3%20MB-blue)
![No permissions](https://img.shields.io/badge/permissions-none-brightgreen)

<img src="docs/screenshots/home.png" width="220" alt="Home screen with game thumbnails"> <img src="docs/screenshots/game_mahjong_easy.png" width="220" alt="Mahjong"> <img src="docs/screenshots/game_backgammon.png" width="220" alt="Backgammon">

</div>

> This app was made for my mother, who loves these games. It is free for everyone, forever:
> no ads, no accounts, no in-app purchases, no internet access, no data collection.

## Contents

1. [Highlights](#highlights)
2. [The games](#the-games)
3. [Made for older adults](#made-for-older-adults)
4. [Install](#install)
5. [Why you can trust it](#why-you-can-trust-it)
6. [Screenshots](#screenshots)
7. [Documentation](#documentation)
8. [Build from source](#build-from-source)
9. [Made with AI assistance](#made-with-ai-assistance)
10. [License](#license)

## Highlights

- **10 games**, each with **Easy / Medium / Hard**.
- **Puzzles are generated on the fly** (Sudoku, Mahjong, Pipes, Connect, Crossword…), so they never repeat — and every generated puzzle is **guaranteed to be solvable**.
- **Real computer opponents** written specifically for Backgammon, Buraco and Reversi.
- **Pinch to zoom on every game**, plus one-finger zoom for one-hand mode.
- **Automatic save**: close the app by accident and it offers to pick up exactly where you left off.
- **5 languages**: English, Portuguese (Brazil), Spanish, French and Simplified Chinese — the interface *and* the crossword dictionaries.
- **Tiny and light**: about **1.3 MB**, native Kotlin + Jetpack Compose, no game engine, no images — all art is vector-drawn in code.

## The games

| | Game | What it is | How difficulty changes |
|---|---|---|---|
| <img src="docs/screenshots/game_sudoku.png" width="70"> | **Sudoku** | Classic 9×9 number puzzle with notes, hints and undo | 40 → 32 → 26 starting numbers; mistakes shown in red on Easy/Medium |
| <img src="docs/screenshots/game_solitaire.png" width="70"> | **Solitaire** | Klondike; tap a card and it moves to the best place | Draw 1 → draw 3 → draw 3 with only 3 passes |
| <img src="docs/screenshots/game_buraco.png" width="70"> | **Buraco** | Brazilian rummy against the computer (two decks, 2s are wild, mortos, canastas) | Smarter computer: takes the pile wisely, guards its wild cards, avoids feeding you |
| <img src="docs/screenshots/game_backgammon.png" width="70"> | **Backgammon** | Full rules (bar, hitting, bearing off, gammons) | Computer from "casual" to a one-roll look-ahead (expectimax) |
| <img src="docs/screenshots/game_mahjong_easy.png" width="70"> | **Mahjong** | Mahjong solitaire with 3D tiles; blocked tiles are dimmed | 48 → 96 → 144 tiles (classic turtle) |
| <img src="docs/screenshots/game_reversi.png" width="70"> | **Reversi** | Othello against the computer; legal moves are shown | Alpha-beta search depth 1 → 3 → 6 (perfect endgame) |
| <img src="docs/screenshots/game_crossword_easy.png" width="70"> | **Crossword** | New crossword every time, in your language | Letter bank → full keyboard; error highlighting off on Hard |
| <img src="docs/screenshots/game_pipes.png" width="70"> | **Pipes** | Turn pipe pieces until water reaches everything | 5×5 → 7×7 → 9×9 |
| <img src="docs/screenshots/game_connect.png" width="70"> | **Connect** | Join dots of the same color without crossing lines | 5×5/4 colors → 7×7/6 → 9×9/8, must fill the board on Medium/Hard |
| <img src="docs/screenshots/game_dots.png" width="70"> | **Dots** | Connect same-colored dots; close a square to clear a color | Bigger board, more colors, fewer moves |

Full rules and tips: **[User guide](docs/en/user-guide.md)**.

## Made for older adults

- **Very simple navigation**: pick a game (picture only, no reading needed) → pick a difficulty on a scroll wheel → play. The gear (⚙) is always in the same corner.
- **Buttons where the thumb is**: almost every action button sits in the lower half of the screen. **Left-handed mode** mirrors them.
- **One-hand mode**: the gear becomes a floating bubble that tucks into the screen edge; zoom works with **double tap** or **press-and-rotate**.
- **Adjustable text size and piece/button size** (80 % to 160 %).
- **Three color modes, each with its own dark mode**: Normal (medium contrast), High contrast (thick outlines), Color-blind friendly (Okabe–Ito palette, shapes drawn on colored pieces, four-color card deck).
- Big touch targets (56 dp), large type, hints and unlimited undo, and no timers anywhere.

More: **[Accessibility section of the user guide](docs/en/user-guide.md#accessibility)**.

## Install

1. Open **[Releases](https://github.com/fershibli/game-mega-app/releases)** on your Android phone and download the latest `MegaGames-vX.Y.Z.apk`.
2. Open the file. If Android asks, allow your browser/files app to "install unknown apps".
3. Optional: compare the file's SHA-256 with `SHA256SUMS.txt` in the same release.

Requires Android 6.0 or newer. Step-by-step instructions with pictures: [User guide → Installing](docs/en/user-guide.md#installing).

## Why you can trust it

| Claim | How to check it yourself |
|---|---|
| No internet, no tracking | The manifest declares **no permissions at all** ([AndroidManifest.xml](app/src/main/AndroidManifest.xml)); without `INTERNET` Android blocks all network access. |
| No hidden code | 100 % open source; only official AndroidX/Kotlin libraries ([libs.versions.toml](gradle/libs.versions.toml)). No ads/analytics SDKs. |
| What CI builds is what you get | Every push is built, tested and linted on GitHub Actions; APKs are published with SHA-256 checksums. The Google "dependency info" blob is disabled so the APK contents are fully inspectable. |
| It works | 50 automated tests on every build: rules, generators (every puzzle solvable), AI, translations, autosave and the UI flow (pinch, one-hand zoom, navigation). Lint runs with warnings as errors. |
| Safe build tooling | Gradle wrapper checksum is pinned and validated in CI; Dependabot keeps dependencies current. |

Details: **[Trust, privacy and security](docs/en/trust.md)** · [SECURITY.md](SECURITY.md)

## Screenshots

| Home (dark) | High contrast | Color-blind + dark | One-hand mode |
|---|---|---|---|
| <img src="docs/screenshots/home_dark.png" width="180"> | <img src="docs/screenshots/home_high_contrast.png" width="180"> | <img src="docs/screenshots/home_colorblind_dark.png" width="180"> | <img src="docs/screenshots/home_one_hand.png" width="180"> |

| Difficulty | Settings | Continue? | Buraco (Português) |
|---|---|---|---|
| <img src="docs/screenshots/difficulty.png" width="180"> | <img src="docs/screenshots/settings.png" width="180"> | <img src="docs/screenshots/resume_dialog.png" width="180"> | <img src="docs/screenshots/game_buraco_pt.png" width="180"> |

All screenshots are generated automatically from the real code (see [Building](docs/en/building.md#screenshots)).

## Documentation

| Language | User guide | Trust & privacy | Technical design | Building & contributing |
|---|---|---|---|---|
| English | [user-guide](docs/en/user-guide.md) | [trust](docs/en/trust.md) | [technical](docs/en/technical.md) | [building](docs/en/building.md) |
| Português | [guia](docs/pt-BR/user-guide.md) | [confiança](docs/pt-BR/trust.md) | [técnico](docs/pt-BR/technical.md) | [compilar](docs/pt-BR/building.md) |
| Español | [guía](docs/es/user-guide.md) | [confianza](docs/es/trust.md) | [técnico](docs/es/technical.md) | [compilar](docs/es/building.md) |
| Français | [guide](docs/fr/user-guide.md) | [confiance](docs/fr/trust.md) | [technique](docs/fr/technical.md) | [compiler](docs/fr/building.md) |
| 中文 | [用户指南](docs/zh-CN/user-guide.md) | [信任与隐私](docs/zh-CN/trust.md) | [技术设计](docs/zh-CN/technical.md) | [构建与贡献](docs/zh-CN/building.md) |

Full index: [docs/README.md](docs/README.md).

## Build from source

```bash
git clone https://github.com/fershibli/game-mega-app.git
cd game-mega-app
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17+ and the Android SDK (API 37). See [docs/en/building.md](docs/en/building.md).

## Made with AI assistance

Everything in this repository — the code, the artwork (every card, tile, board and icon is drawn in code) and this documentation in five languages — was created with the assistance of **Claude**, the AI assistant made by **Anthropic**, under the direction of the maintainer. The design choices are explained in [docs/en/technical.md](docs/en/technical.md), and the test suite exists so that nobody has to take the code on faith.

## License

[MIT](LICENSE) — free to use, copy, modify and share.
