# 15 — Revisão dos dicionários e dos trocadilhos

## Dicionários (`app/src/main/assets/crossword/<lang>.txt`, formato `RESPOSTA|dica|nível`)

Pontos anotados na ampliação para mais de mil palavras por idioma:
- **Espanhol:** algumas palavras puxam para o espanhol da Espanha (COCHE, ZUMO, PATATA). Decidir se fica neutro, aceitando as duas formas na dica, ou se troca.
- **Russo:** algumas respostas só existem no plural (БРЮКИ, ОЧКИ). Avaliar se devem sair.
- **Alemão:** alguns dias da semana foram para o nível médio só para equilibrar as proporções.

Validar com `python3 scripts/check_dictionary.py` e o `DictionarySizeTest`. Cada idioma precisa manter **mais de mil** palavras e as proporções de cerca de 45/35/20 por nível.

## Trocadilhos
Revisão dos trocadilhos e piadas em todos os idiomas. **Não começar** antes de a dona do produto pedir.
