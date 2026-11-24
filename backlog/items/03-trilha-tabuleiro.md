# 03 — Trilha da vida com cara de tabuleiro

**Feedback:** "esperava mais pixel art; precisa ter cara de tabuleiro, atualmente parece um painel de botões".

**Pré-requisito:** o PR #63 já estar na `stag`, além do item 01.

**Hoje** (`games/trail/TrailArt.kt`), são 48 quadrados coloridos numa grade de 8×6, ligados por uma linha marrom.

## O que fazer
- **Parecer um tabuleiro de verdade:**
  - um caminho sinuoso de casas redondas ou ovais, de tamanhos levemente diferentes, sobre uma paisagem em pixel art;
  - cada faixa é um cenário da fase da vida: parquinho e escola, cidade e universidade, escritório e oficina, casa e jardim, porto e montanhas, varanda e pomar;
  - uma moldura de madeira em volta.
- **Casas especiais com ícones em pixel art:** coração, livro, placa de encruzilhada, bandeira de saída, casinha de chegada. O caminho deixa de ser uma grade de 8×6: as posições vêm de uma lista fixa de pontos (`Trail.SQUARES` continua 48).
- **Peões:** figurinhas em pixel art, uma pessoa por cor, com o símbolo da cor para quem não distingue cores. Animar o andar casa a casa (não teletransportar), num ritmo calmo (`currentPace`).
- **Dado:** um dado de pixel art que rola por um instante.
- **Cartão de história:** um cartão de papel com ilustração da fase.
- **O que não muda:** `TrailEngine.kt` (regras e salvamento) e `TrailUiTest`; a descrição de acessibilidade do tabuleiro continua.

## Pronto quando
- Há capturas novas (história e encruzilhada, en e pt).
- O lint e a suíte passam.
