# Hand-off — como trabalhar neste projeto

Para quem for pegar um item do [backlog](specs.md). Leia inteiro antes de começar: as regras abaixo vieram de erros reais.

## O projeto em uma frase

**Mili Games** é um app Android (Kotlin + Jetpack Compose, sem XML de layout) de jogos clássicos para pessoas idosas: letras grandes, toques grandes, nada de pressa, nada de castigo. Tudo é desenhado em `Canvas`, não há imagens nem bibliotecas de jogos. São 10 idiomas: en, pt, es, fr, it, de, ru, ja, zh e ar (o árabe é RTL).

## Regras da casa (obrigatórias)

1. **Uma branch e um PR por item, sempre contra a `stag`.** Nunca empilhe PRs (um PR apontando para a branch de outro). Os PRs são independentes e entram com *squash merge*.
2. **Todo commit e todo PR terminam com** `Co-authored-by: isabelmaito <maitoisabel@gmail.com>`.
3. **Autoria limpa:** nada de rodapé "Generated with…", nada de `Co-Authored-By` de ferramenta, nada de links de sessão, nada de prefixo de ferramenta no nome da branch. O nome da branch descreve o trabalho (`feat/…`, `fix/…`, `docs/…`).
4. **Nunca coloque o número 1000 no app** (em textos, níveis, pontuações etc.).
5. **Mostre capturas de tela** de toda tela nova ou mudada, no PR e para a dona do produto.
6. **Faça push com frequência** para branches temporárias `wip/<nome>`, para não perder trabalho.
7. **Não peça nem guarde segredos** (chaves de assinatura, tokens).
8. **Nada de reescrever histórico** de branch que já está no remoto: nada de rebase, amend ou force-push. Para atualizar, faça merge da `stag` na branch.

## Comandos

Os comandos Gradle precisam destas opções (sem elas o daemon estoura a memória do ambiente):

```bash
G="./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process -q"

$G :app:compileDebugKotlin                                    # compila
$G :app:testDebugUnitTest --tests '*MeuJogoTest'              # testes de um jogo
$G :app:lintDebug :app:testDebugUnitTest; echo "EXIT=$?"      # o mesmo que o CI roda
$G :app:testDebugUnitTest -Pscreenshots=true --tests '*ScreenshotTest.meuJogo*'   # gera capturas em docs/screenshots
python3 scripts/optimize_screenshots.py                       # reduz os PNGs (mexe em TODOS)
python3 scripts/check_dictionary.py                           # valida os dicionários de palavras
```

**Atenção:**
- **Sempre confira o código de saída** (`echo EXIT=$?`). Um `| tail` no fim engole a falha. Já passou lint vermelho assim.
- O `optimize_screenshots.py` reescreve todos os PNGs. Depois dele, descarte as capturas que não são do seu item: `git checkout -- docs/screenshots/<outros>.png`.
- A suíte completa leva de 6 a 10 minutos. Para trabalhar em dois itens ao mesmo tempo, use um `git worktree` e copie o `local.properties` (o caminho do SDK) para ele.

## Onde um jogo novo encosta (checklist)

| O quê | Onde |
|---|---|
| Regras (Kotlin puro, `@Serializable`) | `app/src/main/java/.../games/<jogo>/<Jogo>Engine.kt` |
| Tela | `.../games/<jogo>/<Jogo>Game.kt` (use `LoadGame`, `GameLayout`, `StatusPill`, `ActionBar`, `BigButton`) |
| Desenho e miniatura | `.../games/<jogo>/<Jogo>Art.kt`, com uma função `<jogo>Thumbnail(scope, p)` |
| Registro | `core/Model.kt` (enum `GameId`; a `key` nunca muda depois de publicada), `games/GameContent.kt` e `ui/art/Thumbnails.kt` |
| Textos | `res/values/strings_<jogo>.xml` mais os 9 `values-xx/` (pt, es, fr, it, de, ru, ja, zh, ar) |
| "Aprenda a jogar" | `docs/<lang>/games/<jogo>.md` nos 10 idiomas (`en`, `pt-BR`, `es`, `fr`, `it`, `de`, `ru`, `ja`, `zh-CN`, `ar`). O Gradle copia esses arquivos para o app, então eles são o texto da tela. O árabe vai dentro de `<div dir="rtl">` |
| Testes | `app/src/test/.../games/<Jogo>Test.kt` (regras) e `.../<Jogo>UiTest.kt` (Robolectric) |
| Capturas | entradas no `ScreenshotTest.kt` e os PNGs em `docs/screenshots/` |
| Novidades | uma linha em `CHANGELOG.md`, em "Added" |

Quatro listas recebem uma linha de quase todo PR de jogo (`GameId`, os `when` de `GameContent` e `Thumbnails`, `ScreenshotTest` e `CHANGELOG`). Por isso dois PRs abertos ao mesmo tempo sempre conflitam ali.
- O workflow `.github/workflows/sync-prs.yml` faz merge da `stag` nos PRs abertos e mantém os dois lados com `scripts/merge_game_lists.py`.
- Se ele não conseguir, faça o merge você mesmo e mantenha as duas linhas.
- Confira se a função de ajuda do `ScreenshotTest` não perdeu a chave `}` de fechamento: isso já aconteceu duas vezes num merge.

Outras características dos jogos:
- **Jogos sem nível** (`hasDifficulty = false`) são "mundos duradouros", como o Pet e a Fazenda. Eles não usam autosave: guardam o mundo no `WorldStore` e o "Novo jogo" nunca apaga nada.
- **Jogos de ação** (Snake, Pinball) usam `rememberRunning()`, `GameTicker` e `Records`/`KeepBest` (`games/arcade/Arcade.kt`).
- **Jogos a dois ou mais** usam `takeTurns = true`, `tables = listOf(TableSize(...))`, `Handoff` e `HandoffOverlay` para revezar o celular.

## Pixel art

Já existe um motor simples em `games/pet/PetArt.kt`:
- `pixelArt(rows, palette)` transforma linhas de texto (um caractere por cor, `.` é transparente) em retângulos.
- `PixelArt.draw(scope, left, top, px)` desenha.
- `palette("o=4A3426 a=F4A259")` monta as cores.

O item 01 move isso para um lugar comum. Os itens visuais (02 a 05, 07, 12 e 13) dependem dele.

## Armadilhas já conhecidas

- **Lint, `PluralsCandidate`:** um `%d` seguido de palavra num texto em inglês ("%1$d coins") quebra o lint. Escreva "Coins: %1$d".
- **Lint, `MissingTranslation`:** toda string nova precisa existir nos 10 idiomas.
- **Testes de tela e carregamento assíncrono:** `LoadGame` cria o jogo fora da thread principal, e `waitForIdle()` não espera por isso. Antes do primeiro clique ou da primeira verificação, use `rule.waitUntil(10_000) { rule.onAllNodes(hasText(...)).fetchSemanticsNodes().isNotEmpty() }`. Sem isso, o teste passa sozinho e falha na suíte.
- **Ler o autosave em teste:** use **a mesma** instância de `SaveStore` que o jogo usa e chame `store.flush()` antes de `load()`.
- **Relógio de teste:** o fim de jogo costuma ter `pace.wait(...)`. Avance o relógio com `rule.mainClock.advanceTimeBy(3_000)` antes de esperar o `outcome`.
- **Árabe (RTL):** uma `Row` de controles com lado físico (esquerda/direita) precisa de `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`.
- **CI:** às vezes o Maven Central responde 429 (muitas requisições) e o build cai antes de compilar. Isso não é o código: rode o job de novo uma vez (veja o item 16).
- **Capturas de tela:** use a cena real do jogo, montando o estado com um `GameHost` restaurado. Mostre um momento interessante, não a tela vazia.

## Passo a passo de um PR

1. `git fetch origin stag && git checkout -b feat/<item> origin/stag`
2. Implemente e faça push frequente para `wip/<item>`.
3. Rode o lint e a suíte completa com `EXIT=0`, gere e otimize as capturas.
4. Faça um commit limpo (um só ou poucos) e `git push -u origin feat/<item>`.
5. Abra o PR contra a `stag`: o que mudou, tabela de capturas, testes, e a linha `Co-authored-by` no fim.
6. Acompanhe o CI. Quando ficar verde, entra com squash.
7. Atualize o status do item em `backlog/specs.md`.

## Paralelismo: o que pode andar junto

- **Podem andar juntos sem medo:** 06, 08, 09, 10, 11, 14 e 16 tocam arquivos diferentes (fora as listas comuns acima).
- **Faça o 01 primeiro (é pequeno).** Depois dele, 02, 03, 04, 05, 12 e 13 podem andar em paralelo, um agente por jogo.
- **O 07 (RPG) é grande:** um agente só, em 3 PRs sequenciais. Cada PR entra na `stag` antes do próximo começar; nunca empilhe.
- **Não mexa nos jogos com PR aberto** (#63 Trilha, #65 Batalha naval, #66 Estacionamento) até eles entrarem na `stag`.
