# Go

🌐 [English](../../en/games/go.md) · **Português** · [Español](../../es/games/go.md) · [Français](../../fr/games/go.md) · [Italiano](../../it/games/go.md) · [Deutsch](../../de/games/go.md) · [Русский](../../ru/games/go.md) · [日本語](../../ja/games/go.md) · [中文](../../zh-CN/games/go.md) · [العربية](../../ar/games/go.md)

Um jogo de tabuleiro do Leste Asiático, com milhares de anos, para dois jogadores: você joga com as pedras pretas e o computador com as brancas. As pedras vão nos cruzamentos das linhas e nunca se movem. O objetivo é cercar uma parte maior do tabuleiro do que o adversário.

## Como jogar

- Toque em um cruzamento para colocar uma pedra. Nos tabuleiros 13×13 e 19×19 os cruzamentos são pequenos: o primeiro toque mostra a pedra clarinha e um segundo toque no mesmo cruzamento a coloca.
- Pedras que se tocam pelas linhas formam um grupo. Um grupo precisa de pelo menos um cruzamento vazio ao lado (uma **liberdade**). Quando a última liberdade é ocupada, o grupo é **capturado** e sai do tabuleiro.
- Não é permitido jogar onde sua pedra ficaria sem liberdade, a não ser que ela capture alguma coisa.
- **Ko**: quando uma única pedra acaba de ser capturada, não dá para capturar de volta no mesmo lugar logo em seguida. Jogue em outro lugar antes.
- Toque em **Passar** quando não houver nada útil a jogar. Quando os dois jogadores passam um depois do outro, o jogo termina.
- A última pedra jogada tem um pequeno anel. As pedras capturadas aparecem contadas no alto.
- **Desfazer** volta a sua última jogada (e a resposta do computador). **Dica** marca um bom cruzamento.

## A contagem

- Usamos a contagem por área (regras chinesas): cada lado ganha um ponto por pedra sua no tabuleiro e por cruzamento vazio cercado só por pedras suas.
- As brancas recebem 6,5 pontos a mais (**komi**) por jogarem em segundo. O meio ponto evita empates.
- Depois dos dois passes, o computador propõe quais grupos estão **mortos** (não teriam como escapar da captura): eles aparecem riscados, e o território aparece com quadradinhos. Toque em um grupo para trocá-lo entre morto e vivo e depois em **Contar**. Se não concordar, toque em **Continuar jogando** e resolva no tabuleiro.

## Os tabuleiros

- **9×9**: pequeno e rápido, o melhor para começar.
- **13×13**: mais espaço para planos.
- **19×19**: o tabuleiro clássico completo. Afaste dois dedos para ampliar.

## Níveis

- **Fácil**: joga quase ao acaso, mas captura pedras que só têm uma liberdade, muitas vezes salva as suas e evita se colocar em perigo. Bom para aprender as regras.
- **Médio**: joga milhares de partidas imaginárias rápidas até o fim a partir da posição atual e escolhe a jogada que mais vence (uma busca de Monte Carlo), por cerca de um terço de segundo.
- **Difícil**: a mesma busca por cerca de dois terços de segundo. Nos tabuleiros maiores esse tempo cobre menos partidas, então o computador joga pior neles.

O computador nunca preenche os próprios olhos. Não há pedras de handicap: todos os níveis usam o mesmo komi de 6,5.

## Dicas

- Comece pelos cantos, depois pelas bordas e por fim pelo centro: nos cantos são precisas menos pedras para cercar.
- Um grupo com dois olhos separados (pontos vazios dentro dele) nunca pode ser capturado.
- Quando um grupo seu tiver uma só liberdade, salve-o na hora ou desista dele; não continue colocando pedras em um grupo perdido.
