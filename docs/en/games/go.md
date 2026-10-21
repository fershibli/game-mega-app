# Go

🌐 **English** · [Português](../../pt-BR/games/go.md) · [Español](../../es/games/go.md) · [Français](../../fr/games/go.md) · [Italiano](../../it/games/go.md) · [Deutsch](../../de/games/go.md) · [Русский](../../ru/games/go.md) · [日本語](../../ja/games/go.md) · [中文](../../zh-CN/games/go.md) · [العربية](../../ar/games/go.md)

A board game from East Asia, thousands of years old, for two players: you play the black stones and the computer the white ones. Stones go on the crossings of the lines and never move. The aim is to surround more of the board than your opponent.

**Take turns on this phone:** two people can also play against each other, without the computer. The board never turns around and the screen says whose turn it is. At the end, you agree together which stones are dead.

## How to play

- Tap a crossing to place a stone. On the 13×13 and 19×19 boards the crossings are small, so the first tap shows the stone faintly and a second tap on the same crossing places it.
- Stones that touch along the lines form a group. A group needs at least one empty crossing next to it (a **liberty**). When its last liberty is filled, the group is **captured** and leaves the board.
- You cannot play where your stone would have no liberty, unless it captures something.
- **Ko**: when a single stone has just been captured, you cannot capture back at once in the same place. Play somewhere else first.
- Tap **Pass** when you see nothing useful to play. When both players pass one after the other, the game ends.
- The last stone played has a small ring. The captured stones are counted at the top.
- **Undo** takes back your last move (and the computer's answer). **Hint** marks a good crossing.

## Counting the points

- We use area scoring (Chinese rules): each side gets one point for each of its stones on the board and for each empty crossing surrounded only by its stones.
- White gets 6.5 extra points (**komi**) for playing second. The half point means there is never a tie.
- After the two passes the computer proposes which groups are **dead** (they could not escape capture): they are crossed out, and the territory is shown with small squares. Tap a group to switch it between dead and alive, then tap **Count**. If you do not agree, tap **Keep playing** and settle it on the board.

## The boards you can choose

- **9×9**: small and quick, the best way to start.
- **13×13**: more room for plans.
- **19×19**: the full classic board. Pinch with two fingers to zoom in.

## Levels

- **Easy**: plays almost at random, but captures stones that have one liberty left, often saves its own and avoids putting itself in danger. Good for learning the rules.
- **Medium**: plays thousands of quick imaginary games to the end from the current position and chooses the move that wins most often (a Monte Carlo search), for about a third of a second.
- **Hard**: the same search for about two thirds of a second. On the bigger boards that time covers fewer games, so the computer is weaker there.

The computer never fills its own eyes. There are no handicap stones: every level uses the same 6.5 komi.

## Tips

- Start in the corners, then the sides, then the centre: corners need the fewest stones to surround.
- A group with two separate eyes (empty points inside it) can never be captured.
- When one of your groups has a single liberty left, save it at once or let it go; do not keep adding stones to a lost group.
