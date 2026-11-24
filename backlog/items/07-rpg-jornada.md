# 07 — RPG "Jornada"

Um RPG tranquilo, em turnos, sem reflexo e sem tempo correndo. O desenho foi aprovado pela dona do produto com estas decisões:

| Decisão | Resposta |
|---|---|
| Tema | **Misto, predominantemente medieval**: reino, castelos e cavaleiros, com toques do folclore brasileiro em alguns personagens e criaturas amigáveis (por exemplo um Saci brincalhão que dá dicas, uma Iara no rio, o Curupira guardião da floresta) |
| Grupo | **Até 3 companheiros ativos** ao lado do herói. Mais companheiros podem ser encontrados no caminho e **trocados na vila**, cada um com jogabilidade diferente |
| Perder | **Sem castigo**: acorda na estalagem da vila com tudo o que tinha |
| Extra | **Sistema de magia** obrigatório, para variar a jogabilidade |

## Mundo e exploração
- **Mapa de nós:** uma vila e 5 regiões (Floresta, Rio, Colinas, Ruínas, Castelo). Toca-se num ponto do mapa para viajar, sem joystick.
- **Pontos de interesse:** baús, moradores com pequenas missões, portões com um mini-desafio (reaproveitar jogos existentes: memória, palavras, luzes), batalhas e um chefe por região.
- **Vila:** estalagem (recupera vida e mana, salva), loja (equipamentos e itens), taverna (trocar companheiros) e quadro de missões.

## Personagens
- **Herói:** 4 classes (Cavaleiro, Arqueira, Mago, Curandeira), com nome escolhido pelo jogador.
- **Companheiros recrutáveis** (6 a 8 ao todo), cada um com um papel claro:
  - tanque, que protege os aliados;
  - dano em área, com magia de fogo;
  - suporte, com cura e escudo;
  - controle, que deixa o inimigo lento ou adormecido;
  - ladino, que rouba itens e ataca primeiro;
  - bardo, que dá bônus ao grupo.
- **Atributos simples:** Vida, Mana, Ataque, Defesa e Velocidade (a velocidade define a ordem dos turnos). Nível de 1 a 20. Cada nível dá uma escolha simples de habilidade, entre duas opções.

## Magia
- **Escolas:** Fogo, Gelo, Luz (cura), Natureza (veneno e plantas) e Arcana (escudo e controle).
- **Custo:** cada magia custa mana. A mana volta na estalagem, com poções e um pouco a cada turno de "Defender".
- **Fraquezas:** cada inimigo tem fraqueza e resistência a escolas, mostradas com ícone grande depois do primeiro acerto. É o principal elemento tático.
- **Combos simples entre companheiros**, por exemplo "Molhado + Gelo = Congelado", "Óleo + Fogo = Queimadura forte". São opcionais, só para quem quiser explorar.
- **Livro de magias:** aprendidas por nível e em pergaminhos achados no mapa.

## Batalha
- Em turnos e sem limite de tempo. A ordem de quem joga aparece numa faixa no topo.
- **Ações:** Atacar, Magia, Defender, Item e Trocar companheiro (gasta o turno). Os botões são grandes.
- **"Melhor ação"** sugere uma jogada, como a Dica dos outros jogos.
- **Opção "Automático"** para batalhas fáceis.
- Os turnos dos inimigos são mostrados devagar (`currentPace`). O dano aparece em números grandes, e os inimigos têm cara simpática.
- **Dificuldade:** Fácil, Médio e Difícil, só nos números dos inimigos. Pode mudar a qualquer momento na estalagem.

## Visual
Pixel art (item 01): sprites de 16×16 a 32×32 para personagens e inimigos, cenários por região e retratos nos diálogos.

## Técnica
- **Mundo duradouro:** `hasDifficulty = false` e o mundo salvo no `WorldStore` (como Pet e Fazenda). A dificuldade é uma configuração interna.
- **Motor em Kotlin puro e testável:** regras de dano, ordem de turno, magias e fraquezas, e a IA dos inimigos (simples e determinística com semente).
- **Conteúdo** (regiões, inimigos, magias, diálogos) em dados declarativos (listas Kotlin), com textos em `strings_rpg*.xml` nos 10 idiomas.
- **Textos curtos e frases simples.** Nada de violência gráfica: os inimigos "fogem" ou "desmaiam".

## Entrega em 3 PRs independentes e sequenciais

Cada um entra na `stag` antes do próximo começar; nunca empilhe.

1. **Motor, vila, Floresta e batalhas:** herói, 2 companheiros, 3 escolas de magia, 1 chefe. Já é jogável do começo ao fim de um capítulo.
2. **Regiões 2 a 5:** loja e equipamentos, taverna com troca de companheiros, as outras escolas de magia e combos.
3. **História completa (5 capítulos), tutorial interativo e polimento.**

Cada PR traz textos nos 10 idiomas, "Aprenda a jogar", capturas e testes.
