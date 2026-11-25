# 04 — Textos, idiomas e documentação

## Os 10 idiomas
`en` (padrão, em `values/`), `pt`, `es`, `fr`, `it`, `de`, `ru`, `ja`, `zh` e `ar` (direita para a esquerda). **Toda string nova existe nos 10.** O lint falha com `MissingTranslation`.

- **Placeholders iguais em todos os idiomas** (`%1$d`, `%2$s`). Confira com um script antes do commit, como o `i18n.py` usado até aqui, que compara chaves e placeholders com o inglês.
- **Lint `PluralsCandidate`:** em inglês, um número seguido de palavra quebra o build ("%1$d coins", "%1$d squares"). Escreva com dois-pontos: "Coins: %1$d", "Squares ahead: %1$d". Isso já derrubou o CI duas vezes.
- **Concordância:** frases genéricas com o nome de um item precisam funcionar para todos os itens. "Lã pronto" saiu errado; o certo foi "Lã para recolher: 1".
- **Textos curtos nos botões e abas:** "Encomendas" ficou miúdo numa aba, e "Pedidos" coube. O `StatusPill` mostra até 3 linhas: se passar disso, mude o texto ou leve parte da informação para outro lugar da tela.
- **Os nomes nos textos e na documentação são os do app.** Antes de citar um botão ("Nuevo juego", "Annulla mossa"), confira a string real em `values-xx/strings.xml`.
- **O árabe é direita para a esquerda.** Uma `Row` com lado físico (esquerda e direita, as palhetas do Pinball) precisa de `LayoutDirection.Ltr`. O Canvas não espelha sozinho.
- **Escrita própria de cada idioma:** o japonês e o chinês têm regras próprias de caracteres nos jogos de palavras (veja o `WordKit`). Um jogo que só funciona em alguns idiomas declara `languages = setOf(...)`.

## "Aprenda a jogar"
- **`docs/<lang>/games/<jogo>.md` nos 10 idiomas**, com a barra de idiomas no topo. Esses arquivos **são o texto da tela do app** (o Gradle copia). O árabe vai dentro de `<div dir="rtl">`.
- **Ao mudar uma regra ou opção de um jogo** (por exemplo, o Buraco passou a aceitar de 3 a 6 jogadores), **atualize os 10 idiomas no mesmo PR.** O resumo em `user-guide.md` também.
- **Tabelas com números** (tempos, pontos, níveis) vêm do código. Gere-as a partir das constantes, sem digitar à mão.

## Dicionários de palavras
- `app/src/main/assets/crossword/<lang>.txt`, uma linha por palavra no formato `RESPOSTA|dica|nível`.
- **Valide com** `python3 scripts/check_dictionary.py`, e o `DictionarySizeTest` também confere. Cada idioma tem **mais de mil** palavras e as proporções de cerca de 45/35/20 entre os níveis.
- **Um idioma por pessoa ou agente.** Quando vários trabalharam em paralelo numa pasta temporária comum, palavras em português foram parar no italiano. Separe as pastas.

## CHANGELOG
- **Uma linha por funcionalidade**, em "Added", "Changed" ou "Fixed", escrita para o usuário final (o que muda para quem joga), em inglês.
