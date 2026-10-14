# Technical design

🌐 **English** · [Português](../pt-BR/technical.md) · [Español](../es/technical.md) · [Français](../fr/technical.md) · [中文](../zh-CN/technical.md)

## Contents

1. [Technology choice](#technology-choice)
2. [Architecture](#architecture)
3. [Project structure](#project-structure)
4. [Navigation, settings and autosave](#navigation-settings-and-autosave)
5. [Zoom and one-hand gestures](#zoom-and-one-hand-gestures)
6. [Art without images](#art-without-images)
7. [Puzzle generators](#puzzle-generators)
8. [Computer opponents](#computer-opponents)
9. [Internationalization](#internationalization)
10. [Testing](#testing)

## Technology choice

The request was "React Native if possible, but choose whatever is lightest in size, RAM and CPU; Android only". The options were compared:

| | React Native (Hermes) | Flutter | **Kotlin + Jetpack Compose** |
|---|---|---|---|
| Release APK for this app | ~15–25 MB (JS engine + RN core per ABI) | ~8–12 MB (engine per ABI) | **~1.3 MB** (measured) |
| Extra runtime in memory | JS engine + bridge | Dart VM + Skia/Impeller | none (runs on ART) |
| Drawing boards / pinch zoom | needs extra libraries (Skia, gesture handler, reanimated) | built in | built in (`Canvas`, pointer input) |
| Native Android accessibility (TalkBack, font scale) | through a bridge | own implementation | direct |

*Sizes for React Native and Flutter are typical figures for small apps; only the Compose size was measured on this project.*

**Decision:** native **Kotlin + Jetpack Compose**, single activity, no game engine, no image assets. Compose is part of the Android platform libraries (AndroidX), so the APK only contains the parts actually used after R8 shrinking. The measured release APK is about **1.3 MB**.

Other choices that keep the app small and auditable:

- No navigation library: navigation is a four-state sealed interface.
- No DataStore/Room: settings use `SharedPreferences`; the single saved game is one JSON file written atomically.
- No icon fonts/images: icons and all game art are drawn in `DrawScope`.
- Minimum Android 6.0 (API 23) to support older phones that older adults often inherit.

## Architecture

```
MainActivity
 └─ MegaGamesApp (state machine: Home → ChooseDifficulty → Playing, + Settings)
     ├─ MegaGamesTheme ── palette (6 variants), language, font scale, settings as CompositionLocals
     ├─ HomeScreen / DifficultyScreen / SettingsScreen
     └─ GameScreen ── TopBar + gear, in-game settings, "continue?" and end-of-game dialogs
          └─ GameContent(game) ── one composable per game
               └─ LoadGame(host, serializer, create)   ← restores or generates the state, autosaves every change
                    └─ GameLayout(status, ZoomableBoard(board), controls)
```

Each game is split into:

- an **engine** — plain Kotlin, no Android dependency, immutable `@Serializable` state, fully unit-tested;
- a **screen** — Compose UI that draws the state on a `Canvas` and turns taps into engine calls.

The computer opponents run on `Dispatchers.Default` so the UI never freezes, and show their moves step by step.

## Project structure

```
app/src/main/java/io/github/fershibli/megagames/
├── MainActivity.kt
├── core/            Settings, SettingsStore, SaveStore + GameHost (autosave), GameId, Difficulty
├── ui/
│   ├── MegaGamesApp.kt   navigation
│   ├── theme/            Palette.kt (6 palettes), Theme.kt (language, font scale)
│   ├── components/       buttons, top bar, floating gear, wheel picker, ZoomableBoard, GameLayout
│   ├── art/              cards, suits, mahjong tiles, 3D blocks, glossy discs, home thumbnails
│   └── screens/          Home, Difficulty, Settings, Game
└── games/
    ├── cards/  sudoku/  solitaire/  buraco/  backgammon/  mahjong/
    └── reversi/  crossword/  pipes/  connect/  dots/
app/src/main/assets/crossword/   en.txt pt.txt es.txt fr.txt zh.txt   (word|clue)
app/src/main/res/values*/        strings in 5 languages
app/src/test/                     unit, UI-flow, translation, screenshot and benchmark tests
```

## Navigation, settings and autosave

- **Navigation** is a `sealed interface Screen`. The phone's Back button goes Settings → Home, Difficulty → Home, and inside a game toggles the in-game settings (so a game is never left by accident).
- **Settings** are an immutable `data class` saved to `SharedPreferences` on every change and provided to the whole UI through `CompositionLocal`s.
- **Autosave**: `LoadGame` wraps a game's state in a `MutableState` and observes it with `snapshotFlow`; every new state is serialized to JSON and handed to `SaveStore`, which writes on a single background thread, *newest write wins*, through a temporary file + rename (a crash never leaves half a file). `GameHost.finish()` deletes the save so finished games are not offered again.
- **"Continue?"**: on a cold start (`savedInstanceState == null`) `MegaGamesApp` loads the save and opens the game directly with the prompt. A save that cannot be decoded (corrupted, or from an incompatible version) is ignored and a new game is generated.
- The activity handles configuration changes itself (`android:configChanges`), so rotating, folding or switching dark mode never recreates the screen or loses state.

## Zoom and one-hand gestures

`ZoomableBoard` wraps every board. It listens on the **Initial** pointer pass, before the game sees the events:

- Two or more fingers (when *pinch to zoom* is on) → zoom around the fingers' centroid and pan; those events are consumed so the game never sees a stray tap.
- One finger → passed through untouched; games use ordinary local coordinates because the board is transformed with `graphicsLayer` (Compose maps the touch coordinates back automatically).
- One-hand *double tap*: the second tap of a double tap is consumed; tap = zoom step (1× → 2× → 3× → 1×), tap-and-drag = pan.
- One-hand *press and rotate*: after the long-press timeout (with haptic feedback) the angle of the finger around the press point drives an exponential zoom (clockwise = in). Double tap resets, double-tap-and-drag pans.

Offsets are clamped so the board can never be dragged out of view, and a reset button appears while zoomed.

## Art without images

All art is drawn at runtime with Compose `DrawScope`, so it is sharp at any zoom and recolors itself for each of the six palettes:

- **Depth**: `raisedBlock()` draws stacked side layers from light to dark plus a gradient face (mahjong tiles, dice); `glossyDisc()` draws a shadow, a radial gradient, a ridge and a highlight (checkers, reversi discs, dots); cards get a soft multi-layer shadow.
- **Cards**: suit symbols are vector paths; ranks are large in both top corners so overlapped cards stay readable; face cards get a crown; the color-blind palette uses a four-color deck; French uses R/D/V for King/Queen/Jack.
- **Mahjong**: dots, bamboo (with the traditional bird on the 1), characters (Chinese numerals + 萬), winds, dragons, flowers and seasons, plus a Western index in the corner.
- **Palettes**: Normal, High contrast and Color-blind (Okabe–Ito), each light and dark. In the accessible palettes every colored piece also carries a shape (`drawSymbol`).

## Puzzle generators

| Game | Method | Guarantee (tested) |
|---|---|---|
| Sudoku | Random full grid by backtracking; remove clues in random order, keeping each removal only if a solution counter (most-constrained-cell backtracking, stops at 2) still finds exactly one solution. | Unique solution; clue count per level |
| Mahjong | Fixed layouts (tower 48, fortress 96, turtle 144 turned upright). Faces are assigned by *simulating the game backwards*: repeatedly remove two currently free positions and give them a matching pair. Playing in that order wins. Shuffle re-runs this on the remaining tiles. | Every deal and every reshuffle is solvable |
| Pipes | Random spanning tree grown from the central source (randomized Prim, avoiding 4-way crosses), then every piece rotated randomly. | Connected, loop-free, solvable; never already solved |
| Connect | A Hamiltonian path (serpentine) randomized by thousands of "backbite" moves, then cut into colored segments of ≥3 cells whose ends are not adjacent. | Solvable with the whole board filled |
| Crossword | Words (longest first) placed only where they cross existing letters, never touching other words sideways; best of up to 60 attempts; numbered like a printed crossword. | No letter clashes; target word count per level |
| Dots | Random colors; refill after gravity; the board is reshuffled if no move exists. | A move always exists |
| Solitaire / Buraco | Fair shuffle of one / two decks (`kotlin.random`). | — |

## Computer opponents

A ready-made engine was considered first, as requested:

- **GNU Backgammon** is the reference open-source backgammon engine, but it is written in C under the GPL and would have to be compiled with the NDK for every CPU type, adding megabytes and forcing the whole app under the GPL.
- For **Buraco**, no established open-source engine exists (only hobby projects and forum discussions).

So the opponents are written specifically for this app, in plain Kotlin:

**Reversi** — minimax with alpha-beta pruning. Evaluation: a positional weight table (corners +100, the squares next to corners −20/−50), mobility, and disc count near the end. Easy: depth 1 with 35 % random moves. Medium: depth 3 (8 in the endgame). Hard: depth 6, and a full search of the last 12 empty squares (perfect endgame).

**Backgammon** — complete move generation (enter from the bar, hitting, bearing off with the higher-die rule, "use both dice / the larger die" rule) on a side-relative view. Evaluation: pip-count difference, made points weighted by importance, prime length, anchors, blot exposure estimated from the number of the 36 rolls that hit each blot, checkers on the bar, and a separate race evaluation once contact is broken. Easy: random play 45 % of the time. Medium: best evaluation after its own move. Hard: takes the 5 best candidates and looks one roll ahead, averaging the opponent's best reply over all 21 possible rolls (expectimax).

**Buraco** — heuristic player: decides whether to take the discard pile (top card immediately usable, value of the whole pile), lays down natural sequences first, adds cards to existing sequences, places wild cards according to level (Hard keeps clean canastas clean), and discards the card with the lowest "keep value" (neighbours in hand, fit with own sequences) plus a "danger" cost for cards that would extend your sequences or match cards you picked up.

### Benchmarks

Computer vs. computer, produced by `AiBenchmarkTest` (opt-in):

| Game | Hard vs Easy | Medium vs Easy | Hard vs Medium |
|---|---|---|---|
| Buraco (100 games) | 92 % wins, +903 pts avg | 92 %, +925 pts | 52 %, +77 pts |
| Backgammon (60 games) | 98 % | 100 % | 48 % |
| Reversi (20 games) | 100 % | 95 % | 95 % |

Medium and Hard are close when two computers play Backgammon or Buraco — both are far stronger than Easy. Against people, Hard's extra knowledge (look-ahead in Backgammon, memory of your pickups and defensive discards in Buraco) matters more than against another program.

## Internationalization

- UI strings live in `res/values/strings.xml` (English) and `values-pt`, `values-es`, `values-fr`, `values-zh`.
- The in-app language override wraps the Compose tree in a context created with `createConfigurationContext`, so it works on every Android version without AppCompat. Android 13+ also lists the languages in the system's per-app language settings (`locales_config.xml`).
- Only these five languages are packaged (`localeFilters`), and language splits are disabled for app bundles so switching never needs a download.
- Crossword dictionaries are plain text: `ANSWER|clue`. Answers are normalized (upper case, accents removed, Spanish Ñ kept, Œ → OE); in Chinese every character is one square.

## Testing

| Test class | What it covers |
|---|---|
| `SudokuEngineTest`, `SolitaireTest`, `ReversiTest`, `MahjongTest`, `PuzzleGeneratorsTest`, `CrosswordTest`, `BackgammonTest`, `BuracoTest` | Rules and generator guarantees, AI speed and correctness |
| `TranslationsTest` | Every key translated in all languages, identical placeholders, no unescaped apostrophes |
| `AppFlowTest` (Robolectric) | Autosave round trip, corrupted save, settings persistence, pinch zoom on/off, double-tap and press-and-rotate zoom, choosing and abandoning a game, the "continue?" prompt |
| `ScreenshotTest` (opt-in) | Renders every screen and game to `docs/screenshots` |
| `AiBenchmarkTest` (opt-in) | The table above |
