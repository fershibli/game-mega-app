# 05 — Testes e CI

## Comandos
```bash
G="./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process -q"
$G :app:lintDebug :app:testDebugUnitTest; echo "EXIT=$?"
```
- **Antes de todo push para um PR:** lint e a suíte completa com `EXIT=0`. Rodar só os testes do jogo novo não basta: um jogo novo já fez falhar testes antigos (as listas comuns, o tempo total da suíte).
- **Confira o código de saída.** `comando | tail` devolve o código do `tail`, e um lint vermelho já passou despercebido assim. Grave a saída num arquivo e veja o `EXIT`.
- **Falhas:** `grep -l "<failure" app/build/test-results/testDebugUnitTest/*.xml` mostra quais testes quebraram. O motivo fica dentro de cada XML.

## O que testar
- **Regras:** um `<Jogo>Test` com os casos de cada regra, partidas inteiras simuladas (por exemplo, "30 partidas sempre terminam"), níveis do computador diferentes de verdade ("o Difícil precisa de menos tiros que o Médio") e geradores (faixa de dificuldade e tempo).
- **Tela:** um `<Jogo>UiTest` (Robolectric) que joga como uma pessoa: toques e arrastes reais, e confere o estado salvo ou o texto da tela.
- **Capturas:** entradas no `ScreenshotTest` para cada tela nova, em inglês e pelo menos um outro idioma (ou modo escuro), mostrando um **momento interessante** montado com estado restaurado, não a tela vazia.

## Testes de tela que não falham por acaso
Todas as regras abaixo vieram de testes que passavam sozinhos e falhavam na suíte completa ou no CI.
- **`LoadGame` cria o jogo fora da thread principal e `waitForIdle()` não espera por isso.** Antes do primeiro toque ou da primeira verificação, use `rule.waitUntil(10_000) { rule.onAllNodes(hasText("...")).fetchSemanticsNodes().isNotEmpty() }`, ou espere pela `testTag` do tabuleiro.
- **Para ler o salvamento, use a mesma instância de `SaveStore` que o jogo** e chame `store.flush()` antes de `load()`. Cada instância tem a sua thread de escrita.
- **Fim de jogo com pausa:** avance o relógio (`rule.mainClock.advanceTimeBy(...)`) antes de esperar o resultado.
- **Testes novos usam `androidx.compose.ui.test.junit4.v2.createComposeRule`.** Na regra antiga (v1), a atualização que vem depois de `withContext(Dispatchers.Default)` pode rodar em outra thread durante o desenho da tela, e o Compose para o teste com "performMeasureAndLayout called during measure layout". Migre as classes antigas aos poucos (backlog, item 16).
- **Relógio do app nos testes:** injete o relógio (`LocalFarmClock`) em vez de usar a hora real.
- **Repita um teste novo de tela 3 vezes seguidas** antes do push.

## Capturas de tela
- Gere com `-Pscreenshots=true --tests '*ScreenshotTest.<nome>*'`. A anotação `@GraphicsMode(NATIVE)` é obrigatória; sem ela a imagem sai preta.
- `python3 scripts/optimize_screenshots.py` reescreve **todos** os PNGs. Depois dele, reverta os que não são do seu PR.
- **Olhe cada captura antes de publicar:** textos cortados, sobreposições e o idioma certo. Foi olhando que se descobriu o status cortado da Batalha naval e a aba miúda da Fazenda.

## CI
- **Roda lint, a suíte completa e o build dos APKs** em cada push e PR. O tamanho do APK é vigiado.
- **Erro 429 ou de download** (`MavenArtifactFetcher`, `Could not GET … 429`) é externo: rode o job de novo uma vez (veja `01-git-e-prs.md`).
- **Erro de teste é nosso, mesmo que o teste seja antigo:** corrija a causa no PR atual se for pequena, ou abra um PR próprio de `test:` antes.
