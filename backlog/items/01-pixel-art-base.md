# 01 — Base de pixel art compartilhada

**Por quê:** a dona do produto pediu um visual em pixel art para a Fazenda, a Trilha, o Pinball e o Estacionamento, e o RPG também vai usar. O motor existe só dentro do Pet.

## O que fazer
- Mover `PixelArt`, `pixelArt(rows, palette)`, `palette(spec)` e `drawFitted` de `games/pet/PetArt.kt` para `ui/art/PixelArt.kt` (pacote `ui.art`), sem mudar o comportamento. O Pet passa a importar de lá.
- Acrescentar o que os jogos novos vão precisar:
  - `PixelArt.draw(..., flipX = false)`, para espelhar sprites;
  - `tiles(...)`, que desenha uma grade de ladrilhos a partir de um mapa de caracteres;
  - um utilitário isométrico: `isoToScreen(col, row, tileW, tileH)` e o inverso, para converter um toque em coordenadas do mapa;
  - desenho com `FilterQuality.None` / sem antialias quando for bitmap (hoje são retângulos, então já sai nítido).
- Limite atual: os sprites têm menos de 64×64 (os campos são de 6 bits). Se precisar de mais, aumente o empacotamento.
- Uma paleta-base acolhedora (ver as cores do Pet), documentada no KDoc, com variantes para modo escuro e alto contraste. Os contornos ficam mais fortes quando `p.strongOutlines`.

## Pronto quando
- O Pet fica idêntico. Compare as capturas `game_pet*.png` antes e depois.
- Há testes de unidade de `pixelArt` (runs, transparência, espelho) e de `isoToScreen` ida e volta.
- O PR é pequeno e não mexe em nenhum outro jogo.
