# 06 — Visual e acessibilidade

## Toque e leitura
- **Alvos de toque com pelo menos 48 dp.** O padrão dos botões é 56 dp (`TouchTarget`). Peças de tabuleiro pequenas ficam num `ZoomableBoard`.
- **Texto do tema, nunca tamanhos fixos.** O app tem uma configuração de tamanho de texto, e a escala de peças (`LocalSettings.pieceScale`) vale para peças e cartas.
- **Ações principais embaixo**, onde o polegar alcança (`ActionBar`), e o lado da mão escolhido nas configurações é respeitado.
- **Toda área tocável tem `contentDescription`** (para leitores de tela) **e `testTag`** (para os testes).
- **Prefira toque a arraste.** Quando houver arraste (Estacionamento), ele deve ser tolerante: encaixar no lugar mais próximo e permitir desfazer.

## Cores e modos
- **Cor nunca é a única pista:** cada cor tem também um símbolo ou uma forma (os peões do Ludo e da Trilha, os acertos da Batalha naval).
- **Todo jogo funciona nos 4 modos:** claro, escuro, alto contraste (contornos fortes, `p.strongOutlines`) e daltonismo (paleta própria). Use as cores da `Palette` e `PixelColors.of(p)`, nunca cores fixas para coisas que mudam de significado.
- **Tamanho da miniatura:** desenhe para qualquer proporção.

## Estilo
- **Estilo pedido pela dona do produto:** pixel art com cenário e apelo visual nos jogos de passatempo e de ação (Fazenda isométrica, tabuleiro de verdade na Trilha, Pinball temático). Quebra-cabeças e tabuleiros clássicos podem continuar em vetor, desde que caprichados (o papel cartográfico da Batalha naval).
- **Nada de visual "painel de botões"** em jogos de tabuleiro: precisa parecer um tabuleiro.
- **Animações curtas e calmas**, que respeitam a configuração de movimento reduzido.
- **Visual original:** nada de imitar a arte de jogos comerciais.
