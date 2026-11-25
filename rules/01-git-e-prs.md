# 01 — Git, PRs e autoria

## Branches e PRs
- **Todo PR aponta para a `stag`.** A `main` só recebe a `stag` quando a dona do produto decide promover uma versão.
- **Nunca empilhe PRs** (um PR apontando para a branch de outro, ou cadeias como "17 → 18 → 21"). Cada PR sai da `stag` e é independente. Se um item depende de outro, espere o primeiro entrar na `stag`.
- **Um assunto por PR.** Um jogo novo, uma correção, uma migração. Uma correção encontrada no caminho que afeta outros PRs (por exemplo, um teste instável) pode ir no PR atual se for pequena; diga isso na descrição.
- **Nome da branch descreve o trabalho:** `feat/<jogo>`, `fix/<assunto>`, `docs/<assunto>`, `test/<assunto>`. Nada de nome de ferramenta nem código de sessão.
- **Push frequente para `wip/<nome>`** enquanto trabalha, para não perder nada. Elas são apagadas pela dona do produto depois.
- **O merge é sempre squash**, com o título `tipo(escopo): descrição (#N)`.

## Commits
- **Mensagem no padrão Conventional Commits** (`feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci`), em inglês. O corpo explica o **porquê**.
- **Todo commit e todo PR terminam com** `Co-authored-by: isabelmaito <maitoisabel@gmail.com>`.
- **Autoria limpa:** nada de `Co-Authored-By` de ferramenta, de "Generated with…", de links para sessões ou chats. Confira antes de cada push:
  `git log origin/stag..HEAD --format=%B | grep -iE "generated with|claude|anthropic|noreply@"`
- Fecha uma issue: `Closes #N` no corpo do commit. O workflow `label-on-stag` marca a issue como "em avaliação (stag)".

## Histórico
- **Nunca reescreva o histórico de uma branch que já está no GitHub:** nada de rebase, amend ou force-push. Para atualizar, faça `git merge` da `stag`.
- **O robô `sync-prs` faz merge da `stag` nos PRs abertos sozinho.** Antes de enviar, faça `git fetch` e `git merge` da sua branch remota. Se o push for recusado por "fetch first", é quase sempre o robô: faça o merge, não force.
- **Num merge com conflito nas listas comuns** (`GameId`, `GameContent`, `Thumbnails`, `ScreenshotTest`, `CHANGELOG`), mantenha os dois lados **e compile**. Já aconteceu duas vezes de a união dos lados apagar a `}` que fechava uma função do `ScreenshotTest`.

## PRs: descrição
- **Estrutura:** "What changed" (o que mudou, em tópicos), uma tabela de capturas de tela e "Testing" (quais testes, e que o lint e a suíte passaram localmente).
- **Imagens:** use a sintaxe Markdown `![legenda](docs/screenshots/x.png)`. Pela API, a tag `<img>` às vezes chega escapada e aparece como texto.

## Pedir o merge
- **Só com o CI verde no commit atual do PR** e sem conflito.
- **CI vermelho por causa externa** (Maven Central respondendo 429, download que falhou): rode o job de novo **uma vez**. Se falhar de novo pela mesma causa externa e outro job do mesmo commit passou inteiro, deixe um comentário no PR explicando e siga. Nunca faça commit vazio nem feche e reabra o PR para disparar o CI.
- **CI vermelho num teste:** ache a causa e corrija (ver `05-testes-e-ci.md`). "Instável" não é causa. Nunca desligue, pule ou coloque um teste em quarentena para ficar verde.
