# 07 — Pronto: checklist antes do merge

Copie esta lista na descrição do PR (ou confira item a item).

**Código**
- [ ] Regras em Kotlin puro e determinísticas; tela com os componentes comuns.
- [ ] Chaves salvas inalteradas (`GameId.key`, enums salvos, `WorldStore`); formato antigo aceito.
- [ ] Nada de `delay` fixo: pausas pelo `currentPace`, animações pelo `currentMotion`.
- [ ] Nada de alocação por quadro; trabalho pesado fora da thread principal.

**Produto**
- [ ] Nada de 1000, nada de nome ou visual comercial, nada de castigo pesado.
- [ ] O computador não olha informação escondida.
- [ ] Funciona contra o computador e revezando o celular, quando o jogo permite.

**Textos**
- [ ] Strings nos 10 idiomas, com placeholders conferidos.
- [ ] Nada de "%d palavra" em inglês (lint `PluralsCandidate`).
- [ ] "Aprenda a jogar" nos 10 idiomas, com os nomes de botão iguais aos do app.
- [ ] Linha no `CHANGELOG.md`.

**Visual**
- [ ] Testado em claro, escuro, alto contraste e daltonismo.
- [ ] Alvos de toque ≥ 48 dp; `contentDescription` e `testTag`.
- [ ] Capturas novas, conferidas uma a uma e otimizadas; as outras imagens revertidas.

**Testes e CI**
- [ ] Teste de regras e teste de tela; o teste de tela passou 3 vezes seguidas.
- [ ] `lintDebug` e `testDebugUnitTest` com `EXIT=0`.
- [ ] CI verde no commit atual do PR, sem conflito.

**Git**
- [ ] PR para a `stag`, sem empilhar; título `tipo(escopo): …`.
- [ ] `Co-authored-by: isabelmaito <maitoisabel@gmail.com>` no fim.
- [ ] Nenhuma marca de ferramenta nos commits e no PR.
- [ ] Status do item atualizado em `backlog/specs.md`.
