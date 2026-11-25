# 06 — Batalha naval: silhuetas de navios e papel cartográfico

**Feedback:** "está muito bom, mas se conseguir melhorar as silhuetas dos navios fica ainda melhor; tenta dar uma cara de papel cartográfico pro cenário e tabuleiro".

**Pré-requisito:** o PR #65 já estar na `stag`.

## O que fazer
**Papel cartográfico** (fundo do tabuleiro):
- cor de papel envelhecido, com textura sutil;
- linhas de grade finas em azul-acinzentado;
- coordenadas A–J e 1–10 nas bordas (letras latinas em todos os idiomas, como no jogo de papel);
- uma rosa dos ventos num canto e o mar em aquarela clara.
- A água "tiro na água" vira um círculo de tinta; o "acerto" vira um X de tinta vermelha com uma chama pequena.

**Silhuetas dos navios**, vistas de cima, uma por tamanho:
- 5: porta-aviões, com pista;
- 4: encouraçado, com torres;
- 3: cruzador ou submarino, alternando entre os dois navios de 3;
- 2: lancha patrulha.
- Desenho em `Path` (não precisa ser pixel art), com proa pontuda e popa reta, orientado na horizontal ou vertical.
- O navio afundado fica mais escuro, inclinado ou com fumaça.

**Modos de cor:** manter a legibilidade no escuro, no alto contraste e para quem não distingue cores (o X e o círculo já diferem pela forma).

**O que não muda:** `BattleshipEngine.kt` e os testes.

## Pronto quando
- Há capturas novas (en e pt).
- O lint e a suíte passam.
