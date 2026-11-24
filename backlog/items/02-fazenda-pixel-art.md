# 02 — Fazenda em pixel art isométrico, com cenário

**Feedback da dona do produto:** "esperava um visual mais pixel art e isométrico, com cenário".

**Hoje** (`games/farm/`), as abas Horta, Animais, Pedidos e Celeiro mostram grades de cartões planos, desenhados com formas vetoriais.

## O que fazer
- Uma **cena isométrica única** da fazenda substitui as grades da Horta e dos Animais:
  - canteiros em ladrilhos de terra;
  - cercado dos animais, celeiro, casa, árvores, cerca e caminho;
  - céu ou grama de fundo.
- Usar a base do item 01: sprites em pixel art para cada planta nos 3 estágios, para os 9 produtos e para os 3 animais (com 2 quadros de animação calma: piscar, ciscar).
- **Interação continua por toque:**
  - tocar num canteiro planta, rega ou colhe, como hoje;
  - tocar num animal recolhe;
  - os botões grandes de baixo continuam ("Colher tudo", "Regar tudo", "Novo canteiro", "Comprar animal").
- **Tamanho e zoom:** os alvos de toque têm pelo menos 48 dp. A cena cabe num celular em pé sem rolar com 6 canteiros e 3 animais. Com 12 canteiros e 6 animais, usar o `ZoomableBoard` (pinça e arraste) e um botão "centralizar".
- Pedidos e Celeiro podem continuar em cartões, mas com ícones em pixel art.
- O "dia e noite" é opcional, pelo relógio do celular.
- **Regras e salvamento não mudam:** `FarmEngine.kt` e o formato de `FarmWorld` ficam como estão. Os mundos salvos continuam funcionando.
- **Acessibilidade:** cada canteiro e cada animal mantém `contentDescription` e `testTag` (`farm-plot-i`, `farm-pen-i`). O `FarmUiTest` deve passar sem mudanças, ou só com mudanças de toque.

## Pronto quando
- Há capturas novas em claro, escuro e pt, mostrando uma fazenda cheia.
- O lint e a suíte passam.
