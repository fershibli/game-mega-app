# Backlog — Mili Games

Tudo o que está pendente, com uma especificação por arquivo em [`items/`](items/). Antes de pegar um item, leia o [hand-off](HANDOFF.md): ele explica o fluxo de trabalho, as armadilhas conhecidas e quais itens podem andar em paralelo sem conflito.

Status: `livre` (ninguém pegou), `em andamento` (diga a branch), `aguardando decisão` (só a dona do produto resolve), `feito`.

## Itens

| # | Item | Área | Depende de | Status |
|---|---|---|---|---|
| 01 | [Base de pixel art compartilhada](items/01-pixel-art-base.md) | visual | — | feito |
| 02 | [Fazenda em pixel art isométrico](items/02-fazenda-pixel-art.md) | visual | 01 | feito |
| 03 | [Trilha da vida com cara de tabuleiro](items/03-trilha-tabuleiro.md) | visual | 01 | livre |
| 04 | [Pinball: pixel art e mais elementos](items/04-pinball-visual.md) | visual + jogo | 01 | livre |
| 05 | [Estacionamento em pixel art](items/05-estacionamento-pixel-art.md) | visual | 01 | livre |
| 06 | [Batalha naval: navios e papel cartográfico](items/06-batalha-naval-carta.md) | visual | — | livre |
| 07 | [RPG "Jornada"](items/07-rpg-jornada.md) | jogo novo (grande) | 01 | livre |
| 08 | [Lições do tutorial interativo para outros jogos](items/08-tutoriais.md) | tutorial | — | aguardando decisão (quais jogos) |
| 09 | [Jogos novos de cartas](items/09-jogos-cartas.md) | jogos novos | — | livre |
| 10 | [Jogos novos de tabuleiro](items/10-jogos-tabuleiro.md) | jogos novos | — | livre |
| 11 | [Quebra-cabeças novos](items/11-quebra-cabecas.md) | jogos novos | — | livre |
| 12 | [Ação calma: jogos novos](items/12-acao-calma.md) | jogos novos | 01 (visual) | livre |
| 13 | [Passatempos novos](items/13-passatempos.md) | jogos novos | 01 | livre |
| 14 | [Documentação técnica nos outros idiomas](items/14-docs-tecnicas.md) | docs | — | livre |
| 15 | [Revisão dos dicionários](items/15-dicionarios.md) | conteúdo | — | aguardando decisão |
| 16 | [Dívida técnica e CI](items/16-divida-tecnica.md) | engenharia | — | livre |
| 17 | [Decisões que esperam a dona do produto](items/17-decisoes.md) | produto | — | aguardando decisão |

## Em andamento agora (fora deste backlog)

| PR | Jogo | Estado |
|---|---|---|
| #65 | Batalha naval | esperando o CI; entra na `stag` quando verde |

A Trilha (#63) e o Estacionamento (#66) já estão na `stag`. O item 06 mexe na Batalha naval: só comece depois que o #65 entrar.

## Recusados (não fazer)

Pôquer, Damagol, visual de Tetris, Corrida, Bingo.
