# 08 — Lições do tutorial interativo para outros jogos

**Hoje** só a Paciência tem lição interativa (`games/solitaire/SolitaireTutorial.kt`, com `TutorialGuide`, `LocalTutorial`, `TutorialScreen` e `games/Tutorials.kt`). Os outros jogos têm só o texto "Aprenda a jogar".

**Aguardando decisão:** a dona do produto escolhe os próximos jogos, em lotes de 2 ou 3. Sugestão de ordem: Buraco, Sudoku, Mahjong, Damas, Palavras cruzadas.

## Como fazer cada lição
- Seguir a Paciência: frases curtas, uma por passo; uma mão mostra o movimento e depois é a vez da pessoa; só o alvo responde; depois de dois toques fora do alvo, a mão mostra onde tocar.
- Cada lição tem um tabuleiro preparado (estado fixo), entre 8 e 12 passos, e textos nos 10 idiomas (`strings_tutorial.xml`).
- Testes como o `TutorialTest`, inclusive o duplo toque em "Próximo", que não deve pular passos (um bug já corrigido).
