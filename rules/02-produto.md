# 02 — Produto: para quem é o app

O **Mili Games** é para pessoas idosas, a começar pela mãe da dona do produto. Cada decisão passa por esta pergunta: **uma pessoa de 75 anos, sem pressa e com a vista cansada, consegue e gosta de fazer isso?**

## Sempre
- **Calma:** nada de reflexo rápido nem tempo correndo, a não ser que a pessoa escolha (o "contra o relógio" é opcional). Os jogos de ação são lentos no Fácil.
- **Ritmo e animações:** toda pausa e toda animação seguem as configurações de Ritmo e Animações (`currentPace`, `currentMotion`). Nenhum `delay` fixo.
- **Nada de castigo pesado:** ninguém volta ao início, ninguém "morre" de forma violenta, ninguém perde o que construiu. Perder no RPG leva à estalagem. Na Trilha, ninguém volta à partida.
- **Mundos duradouros** (Pet, Fazenda e, depois, o RPG) **nunca se perdem** com "Novo jogo". Ficam no `WorldStore`.
- **Jogos em movimento começam pausados** e pausam sozinhos quando a pessoa sai do app ou abre as configurações.
- **O computador nunca olha informação escondida** (cartas na mão, frota, peças viradas). A IA só usa o que um jogador veria.
- **Uma pergunta por tela** antes de jogar (apresentação, regras, quem joga, nível, mesa), e só as perguntas que o jogo tem.
- **Linguagem gentil e simples.** Na forca virou uma flor que perde pétalas; nunca imagem de enforcamento, de violência ou de morte. No RPG, os inimigos "fogem" ou "desmaiam".

## Nunca
- **Nunca o número 1000** em lugar nenhum do app (textos, níveis, pontuação, metas). Pedido explícito da dona do produto.
- **Nunca nomes ou visual de jogos comerciais.** Use nomes genéricos ("Quatro em linha", "Pinball", "Estacionamento"), e nada de personagens, arte, música ou fases copiados. Uma ideia genérica pode, uma cópia não.
- **Nunca os jogos recusados:** Pôquer, Damagol, visual de Tetris, Corrida, Bingo.
- **Nunca peça nem grave segredos** (chaves de assinatura, tokens) no código, nos commits, nas issues ou nos PRs.
- **Nunca dados pessoais** da família ou de endereços no repositório.

## Decisões
- **Mudanças de comportamento que a pessoa percebe** (o que aparece, o que se perde, o que muda de lugar) e escolhas de produto passam pela dona do produto. Registre a pergunta em `backlog/items/17-decisoes.md` e não implemente antes da resposta.
- **O retorno da dona do produto vira item do backlog**, com as palavras dela citadas: por exemplo, "esperava um visual mais pixel art".
