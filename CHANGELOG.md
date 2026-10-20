# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/) and versions follow [Semantic Versioning](https://semver.org/).

## [2.0.0] - Unreleased

### Added
- **Checkers** with five sets of rules (Brazilian, International 10×10, English, Italian, Russian): mandatory and majority captures, flying kings where they apply, multi-captures by tapping the final square, a draw after long king-only endgames, Undo and Hint, and a computer opponent on three levels.
- **Chess** against the computer with every rule (castling, en passant, promotion, all the draws), Undo and Hint, plus **Famous games**: seven historic games, from the Immortal Game (1851) to Kasparov–Topalov (1999), replayed move by move with their story.
- **Cacheta** (cards): the Brazilian rummy against 1 to 3 computer players, with Classic, With wild card (vira and curinga) and Pife (sequences turn the corner, grab a discard to go out) rules; a fanned hand with groups underlined, sort by suit or number, a Hint and a Bater! button that lights up when every card fits.
- **Truco** (cards): Paulista (vira and manilhas) and Mineiro (fixed manilhas) rules, 1 vs 1 or 2 vs 2 with a computer partner; Truco!/Seis!/Nove!/Doze! in big speech balloons with big answer buttons, hand of eleven, and a computer that weighs its chances without seeing your cards and bluffs now and then.
- **Magic cube** (puzzles): the color cube puzzle in 2×2, 3×3, 4×4 and 5×5, drawn in 3D in code. Swipe a sticker or use big face buttons with ↺/↻ to turn, arrows to see every side, unlimited Undo, a step-by-step Hint that teaches the beginner's layer-by-layer method on the 2×2 and 3×3 (and walks back to the start on the bigger cubes), and a symbol on every color in the color-blind palette.
- Home screen grouped by category (cards, board games, puzzles, calm action, pastimes), ready for many more games. Settings → Game list: **Group by category** (or one single list) and **Recently played first** (the game you open moves to the top of its group and its group to the top; both layouts keep their own order).
- A rules screen for games with regional variants, shown before the difficulty; the rules picked last time come preselected.
- Lasting storage for pastime games (a pet, a farm), kept apart from the game in progress and included in Android's own backup.
- Settings → **Why Mili Games exists** (at the very end): why the app is and always will be free and ad-free, how it was made (with the help of Claude, with no AI inside the app) and a button to the source code and documentation. The same text is in the store descriptions.
- **Arabic** (Modern Standard Arabic): screens read right to left, while game boards keep their layout and left-handed mode still means the physical left; the crossword reads right to left with a letter bank. Western digits are kept, as on the boards.
- CI reports the release APK size, warns past 10 MB and fails past 20 MB.
- Four new languages: **Italian, German, Russian and Japanese** — the interface, crossword dictionaries (with QWERTZ and ЙЦУКЕН keyboards; Japanese in katakana with a kana bank), store listings, README, user guide, trust page and privacy policy.

### Changed
- The app is now called **Mili Games** and its package is `io.github.fershibli.miligames`. Android sees it as a different app: uninstall the previous version (the saved game is lost once) and install this one; later versions update normally. The release key is the same.

## [1.0.0] - 2026-10-19

### Added
- Ten games with Easy / Medium / Hard: Sudoku, Solitaire (Klondike), Buraco, Backgammon, Mahjong solitaire, Reversi, Crossword, Pipes, Connect and Dots.
- Procedural, always-solvable puzzles (Sudoku with unique solution, Mahjong, Pipes, Connect, Crossword).
- Computer opponents for Reversi (alpha-beta), Backgammon (heuristic + one-roll expectimax) and Buraco (heuristic).
- Home screen with endless two-column picture grid; difficulty scroll wheel with the default preselected.
- Settings: language (EN, PT-BR, ES, FR, ZH), text size, piece/button size, Normal / High-contrast / Color-blind palettes each with a dark mode, pinch zoom, left-handed mode, one-hand mode (floating gear, double-tap or press-and-rotate zoom), default difficulty.
- Automatic save with "continue where you left off?" on the next start.
- Backgammon on the traditional horizontal board, with pinch zoom to get closer; a Rotate button stands it up (bigger checkers) and the choice is remembered.
- Documentation in five languages, CI, Dependabot.
- Automatic semantic versioning from Conventional Commits (`versioning.yml`) and an APK deploy pipeline (`deploy.yml`) that tests, signs, verifies and publishes each release.
- Google Play: AAB in every release, optional upload to Play (`scripts/play-upload.sh`), store listing texts and graphics in five languages (`fastlane/metadata/android`), privacy policy (`PRIVACY.md`) and a step-by-step guide (`docs/en/google-play.md`).
- `signing-key.yml`: creates the release key, keeps it in the repository secrets, re-encrypts it when `MEGAGAMES_SIGNING_PASSWORD` changes, makes encrypted backups and exports it for Google Play.

### Fixed
- Sudoku: after the first tap on the board, selecting another cell could erase the numbers placed since then.

> From now on, the notes of every version are generated automatically from the commits and published on the [GitHub Releases](https://github.com/fershibli/game-mega-app/releases) page.
