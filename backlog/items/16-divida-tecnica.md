# 16 — Dívida técnica e CI

| Tema | O que fazer |
|---|---|
| CI cai com 429 no Maven Central | O build às vezes para antes de compilar com `429 Too Many Requests` (por exemplo em `org.robolectric:nativeruntime-dist-compat`). Opções: deixar o cache do Gradle gravável nos PRs (`cache-read-only: false`, ou cache por branch), adicionar um espelho (`https://maven-central.storage-download.googleapis.com/maven2/`) em `settings.gradle.kts` antes do `mavenCentral()`, e evitar rodar o build duas vezes por push (hoje roda em `push` **e** em `pull_request`) |
| Testes de tela sem espera | Procurar testes que verificam a tela logo depois de `setContent` num jogo que carrega em segundo plano (`LoadGame`) e trocar por `waitUntil`. Já corrigidos: `CheckersChessUiTest`, `BoardTakeTurnsTest`, `BattleshipUiTest`, `PinballUiTest` e `ParkingUiTest` |
| API de teste obsoleta | `createComposeRule` (v1) está obsoleto; migrar para `androidx.compose.ui.test.junit4.v2.createComposeRule`. A v2 usa `StandardTestDispatcher`, então muitos testes vão precisar de sincronização explícita: fazer em lotes, sem ser num PR de jogo |
| Avisos do compilador nos testes | `!!` desnecessários em `TrickCardsTest` e opt-in de `ExperimentalCoroutinesApi` em `TakeTurnsTest` |
| Branches `wip/*` antigas no GitHub | **A dona do produto apaga, pela página de branches.** O ambiente dos agentes recebe 403 ao apagar. Lista atual: `wip/battleship`, `wip/deps-bump`, `wip/dict-ar`, `wip/dict-de`, `wip/dict-en`, `wip/dict-es`, `wip/dict-fr`, `wip/dict-it`, `wip/dict-ja`, `wip/dict-pt`, `wip/dict-ru`, `wip/dict-zh`, `wip/dictionaries`, `wip/farm`, `wip/life-trail`, `wip/parking`, `wip/pinball` |
