# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/) and versions follow [Semantic Versioning](https://semver.org/).

## [1.0.0] - Unreleased

### Added
- Ten games with Easy / Medium / Hard: Sudoku, Solitaire (Klondike), Buraco, Backgammon, Mahjong solitaire, Reversi, Crossword, Pipes, Connect and Dots.
- Procedural, always-solvable puzzles (Sudoku with unique solution, Mahjong, Pipes, Connect, Crossword).
- Computer opponents for Reversi (alpha-beta), Backgammon (heuristic + one-roll expectimax) and Buraco (heuristic).
- Home screen with endless two-column picture grid; difficulty scroll wheel with the default preselected.
- Settings: language (EN, PT-BR, ES, FR, ZH), text size, piece/button size, Normal / High-contrast / Color-blind palettes each with a dark mode, pinch zoom, left-handed mode, one-hand mode (floating gear, double-tap or press-and-rotate zoom), default difficulty.
- Automatic save with "continue where you left off?" on the next start.
- Documentation in five languages, CI, Dependabot.
- Automatic semantic versioning from Conventional Commits (`versioning.yml`) and an APK deploy pipeline (`deploy.yml`) that tests, signs, verifies and publishes each release.

### Fixed
- Sudoku: after the first tap on the board, selecting another cell could erase the numbers placed since then.

> From now on, the notes of every version are generated automatically from the commits and published on the [GitHub Releases](https://github.com/fershibli/game-mega-app/releases) page.
