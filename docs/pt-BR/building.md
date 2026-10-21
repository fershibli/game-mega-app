# Compilar, publicar e contribuir

🌐 [English](../en/building.md) · **Português** · [Español](../es/building.md) · [Français](../fr/building.md) · [中文](../zh-CN/building.md)

## Sumário

1. [Requisitos](#requisitos)
2. [Compilar e rodar](#compilar-e-rodar)
3. [Testes e lint](#testes-e-lint)
4. [Capturas de tela](#capturas-de-tela)
5. [Benchmarks da IA](#benchmarks-da-ia)
6. [Versões assinadas](#versões-assinadas)
7. [Versionamento e deploy](#versionamento-e-deploy)
8. [Como contribuir](#como-contribuir)
9. [Adicionando palavras às cruzadas](#adicionando-palavras-às-cruzadas)
10. [Adicionando ou melhorando uma tradução](#adicionando-ou-melhorando-uma-tradução)
11. [Adicionando um jogo](#adicionando-um-jogo)

## Requisitos

- **JDK 17 ou mais novo** (o CI usa Temurin 21).
- **Android SDK** com a plataforma **API 37** (o Android Studio instala; ou `sdkmanager "platforms;android-37"`).
- Internet na primeira compilação (o Gradle baixa a si mesmo e as dependências).

Indique o SDK com `ANDROID_HOME` ou com um arquivo `local.properties` contendo `sdk.dir=/caminho/para/Android/sdk`.

## Compilar e rodar

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # instala num celular/emulador conectado (depuração USB ligada)
./gradlew assembleRelease    # reduzido com R8; sem assinatura se não houver chave configurada
```

Ou abra a pasta no **Android Studio** e aperte *Run*.

## Testes e lint

```bash
./gradlew testDebugUnitTest   # 50 testes: regras, geradores, IA, traduções, fluxo da interface
./gradlew lintDebug           # Android Lint, avisos são erros
```

Relatórios: `app/build/reports/tests/testDebugUnitTest/index.html` e `app/build/reports/lint-results-debug.html`.

Os testes de interface usam **Robolectric**, então rodam na JVM, sem emulador.

## Capturas de tela

Todas as imagens do README são desenhadas a partir do código real:

```bash
./gradlew :app:testDebugUnitTest -Pscreenshots=true --tests '*ScreenshotTest*'
# os PNGs são gravados em docs/screenshots/
```

Depois, reduza o tamanho antes do commit: `python3 scripts/optimize_screenshots.py` (precisa do Pillow).

Para incluir uma nova, adicione um `@Test` em `app/src/test/.../ScreenshotTest.kt` usando `shot(...)` ou `gameShot(...)`.

## Benchmarks da IA

```bash
./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*AiBenchmarkTest*' -i | grep BENCHMARK
```

Coloca os adversários do computador para jogar entre si (Buraco, Gamão, Reversi) e mostra as taxas de vitória. Os resultados estão em [technical.md](technical.md#benchmarks).

## Versões assinadas

O Android só instala APKs assinados, e as atualizações precisam ser assinadas com a **mesma chave**. Se a chave se perder, quem usa o app terá de desinstalar para instalar uma nova versão.

**No GitHub (recomendado)**: o workflow [`signing-key.yml`](../../.github/workflows/signing-key.yml) cria a chave, guarda nos secrets do repositório e a recriptografa sempre que você troca a senha no secret `MEGAGAMES_SIGNING_PASSWORD`. Ele nunca substitui uma chave existente e consegue fazer um backup criptografado. Configuração (um token e dois cliques): [google-play.md → Chave de assinatura](google-play.md#1-chave-de-assinatura).

O [pipeline de deploy](#versionamento-e-deploy) assina todo APK e AAB com essa chave. Sem ela, as versões candidatas (`stag`) continuam sendo publicadas, assinadas com uma chave de depuração temporária e identificadas assim nas notas da versão, mas uma versão estável (`main`) nunca é publicada: a execução falha antes de criar a tag, e basta criar a chave e executá-la de novo.

**Localmente**: com o arquivo da chave (de um backup), crie `keystore.properties` na raiz do projeto (ele é ignorado pelo git):

```properties
storeFile=miligames-release.p12
storePassword=...
keyAlias=megagames
keyPassword=...
```

Depois, `./gradlew assembleRelease bundleRelease` gera `app/build/outputs/apk/release/app-release.apk` e `app/build/outputs/bundle/release/app-release.aab`.

**Google Play**: guia passo a passo, com as respostas de cada formulário do Play Console: [google-play.md](google-play.md).

## Versionamento e deploy

As versões seguem o [Versionamento Semântico](https://semver.org/lang/pt-BR/) (`MAIOR.MENOR.CORREÇÃO`) e são calculadas **automaticamente** a partir dos [Conventional Commits](https://www.conventionalcommits.org/pt-br/) pelo nosso próprio workflow, [`versioning.yml`](../../.github/workflows/versioning.yml) (bash puro, sem ferramenta de release de terceiros). Em seguida ele chama o [`deploy.yml`](../../.github/workflows/deploy.yml), que gera e publica o APK.

| Mensagem do commit | Efeito | Exemplo |
|---|---|---|
| `feat!: …` / `fix!: …` ou `BREAKING CHANGE:` no corpo | **MAIOR** | 1.4.2 → 2.0.0 |
| `feat: …` / `feat(sudoku): …` | **MENOR** | 1.4.2 → 1.5.0 |
| `fix:`, `perf:`, `revert:`, `refactor:`, `build:` | **CORREÇÃO** | 1.4.2 → 1.4.3 |
| `docs:`, `chore:`, `ci:`, `test:`, `style:` ou sem prefixo | sem versão nova | — |

A primeira versão é `1.0.0`. Branches:

| Push em | Resultado |
|---|---|
| `main` (ou `master`) | versão estável `v1.5.0`, marcada como *Latest* |
| `stag` | candidata `v1.5.0-rc.1`, `-rc.2`… marcada como *Pre-release* (só gera rc nova quando chega algo publicável) |

O que acontece em cada versão:

1. O `versioning.yml` lê os commits desde a última tag estável, escolhe o incremento, monta as notas agrupadas (mudanças incompatíveis, novidades, correções, desempenho, outras) e envia uma tag anotada `vX.Y.Z`.
2. O `deploy.yml` faz checkout dessa tag, roda os testes e o lint, compila o APK final com `-PappVersion=X.Y.Z`, assina, **verifica** (assinatura, `versionName`, nenhuma permissão pedida) e publica uma Release no GitHub com `MiliGames-vX.Y.Z.apk`, `SHA256SUMS.txt` e as notas.

O `versionCode` do Android é derivado da versão para sempre crescer: `MAIOR×1.000.000 + MENOR×10.000 + CORREÇÃO×100 + n`, em que `n` é o número da rc, ou 99 na versão final (assim `1.5.0-rc.2` < `1.5.0`). MENOR e CORREÇÃO devem ficar abaixo de 100.

Outras formas de publicar:

- **Actions → Semantic versioning → Run workflow** e escolha `patch`, `minor` ou `major` para forçar uma versão.
- **Actions → Deploy APK → Run workflow** com um número de versão para (re)gerar uma versão específica.
- Envie uma tag manualmente: `git tag v1.2.3 && git push origin v1.2.3` (as notas são geradas pelo GitHub).

Localmente, `./gradlew assembleRelease -PappVersion=1.2.3` gera essa versão; sem `-PappVersion` a versão é `1.0.0`.

## Como contribuir

Contribuições são bem-vindas — principalmente de quem joga esses jogos com parentes mais velhos.

1. Abra uma issue descrevendo o problema ou a ideia (capturas de tela ajudam).
2. Faça um fork, crie um branch e faça a mudança.
3. Rode `./gradlew testDebugUnitTest lintDebug` — o CI roda o mesmo.
4. Abra um pull request explicando *por que* a mudança ajuda quem joga.

Diretrizes:

- **Acessibilidade em primeiro lugar**: alvos de toque ≥ 56 dp, ações principais na metade de baixo da tela e espelhadas no modo canhoto (`ActionBar`), nunca só a cor (use `drawSymbol` nas paletas acessíveis), nada de cronômetro ou pressa.
- **Continuar pequeno e privado**: nenhuma permissão nova, nada de rede, nada de estatísticas, nenhuma imagem (desenhe em código), nenhuma dependência nova sem um ótimo motivo.
- **Os motores são Kotlin puro** com testes; a interface só desenha o estado e chama o motor.
- Todo texto visível vai para o `strings.xml` **nos cinco idiomas** (o `TranslationsTest` exige isso).
- Escreva as mensagens de commit no padrão [Conventional Commits](https://www.conventionalcommits.org/pt-br/) (`feat: …`, `fix: …`, `docs: …`): elas decidem a próxima versão (veja [Versionamento e deploy](#versionamento-e-deploy)).
- Comentários e nomes no código em inglês; siga o estilo existente (`.editorconfig`, estilo oficial do Kotlin).

## Adicionando palavras às cruzadas

Os dicionários são texto puro em `app/src/main/assets/crossword/<idioma>.txt`, uma entrada por linha:

```
RESPOSTA|Uma dica curta e simpática
```

- Palavras do dia a dia, de 2 a 13 letras (chinês: 2 a 4 caracteres; japonês: 2 a 6 kana, em katakana), sem nomes próprios que exijam conhecimento específico.
- As dicas devem ser gentis e sem ambiguidade — pense em alguém de oitenta anos.
- Pode usar acentos nas respostas; eles são removidos automaticamente (o espanhol mantém o Ñ).
- Linhas que começam com `#` são comentários. O `CrosswordTest` confere se cada dicionário continua com pelo menos 120 palavras utilizáveis.

## Adicionando ou melhorando uma tradução

1. Copie `app/src/main/res/values/strings.xml` para `values-<código>/strings.xml` e traduza todos os `<string>` e `<plurals>` (mantenha os marcadores do tipo `%1$d`; escape apóstrofos como `\'` ou use o tipográfico `’`).
2. Acrescente o idioma em `AppLanguage` (`core/Settings.kt`), em `localeFilters` (`app/build.gradle.kts`) e em `res/xml/locales_config.xml`.
3. Crie um dicionário `assets/crossword/<código>.txt` e ligue-o em `dictionaryFile()`.
4. Traduza o README e os quatro documentos em `docs/<código>/` e inclua-os no [docs/README.md](../README.md).
5. Rode os testes.

## Adicionando um jogo

1. Crie uma entrada em `GameId` (a `key` dela fica gravada nos salvamentos: nunca a mude depois) com os textos de título e de como jogar.
2. Crie `games/<nome>/` com um motor em Kotlin puro (estado `@Serializable`) e testes.
3. Escreva a tela com `LoadGame(host, State.serializer(), create = { ... })` e `GameLayout(status, controls) { tabuleiro }`; chame `host.finish(GameOutcome(...))` no fim.
4. Ligue-o em `GameContent.kt` e desenhe a miniatura em `ui/art/Thumbnails.kt`.
5. Adicione um teste de captura de tela e documente as regras nos cinco guias do usuário.

Quando um pull request para `stag` é mesclado, o workflow **Sync open PRs** (`sync-prs.yml`) mescla `stag` em cada outro pull request aberto que passou a ter conflito. Se os conflitos estão só nas listas de jogos (`GameId`, `GameContent.kt`, `Thumbnails.kt`, `ScreenshotTest.kt` e o changelog), ele mantém os dois lados (`scripts/merge_game_lists.py`), confere se tudo compila, envia a mesclagem e roda o CI. Qualquer outro conflito aparece no resumo da execução para uma pessoa resolver.
