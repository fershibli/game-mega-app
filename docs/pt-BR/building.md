# Compilar, publicar e contribuir

🌐 [English](../en/building.md) · **Português** · [Español](../es/building.md) · [Français](../fr/building.md) · [中文](../zh-CN/building.md)

## Sumário

1. [Requisitos](#requisitos)
2. [Compilar e rodar](#compilar-e-rodar)
3. [Testes e lint](#testes-e-lint)
4. [Capturas de tela](#capturas-de-tela)
5. [Benchmarks da IA](#benchmarks-da-ia)
6. [Versões assinadas](#versões-assinadas)
7. [Como contribuir](#como-contribuir)
8. [Adicionando palavras às cruzadas](#adicionando-palavras-às-cruzadas)
9. [Adicionando ou melhorando uma tradução](#adicionando-ou-melhorando-uma-tradução)
10. [Adicionando um jogo](#adicionando-um-jogo)

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

Para incluir uma nova, adicione um `@Test` em `app/src/test/.../ScreenshotTest.kt` usando `shot(...)` ou `gameShot(...)`.

## Benchmarks da IA

```bash
./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*AiBenchmarkTest*' -i | grep BENCHMARK
```

Coloca os adversários do computador para jogar entre si (Buraco, Gamão, Reversi) e mostra as taxas de vitória. Os resultados estão em [technical.md](technical.md#benchmarks).

## Versões assinadas

O Android só instala APKs assinados, e as atualizações precisam ser assinadas com a **mesma chave**. Guarde bem a chave: se ela se perder, quem usa o app terá de desinstalar para instalar uma versão nova.

1. **Crie a chave uma única vez** (guarde o arquivo e as senhas num gerenciador de senhas):

   ```bash
   keytool -genkeypair -v -keystore megagames-release.jks -alias megagames \
     -keyalg RSA -keysize 4096 -validity 36500
   ```

2. **Assinatura local**: crie `keystore.properties` na raiz do projeto (ele é ignorado pelo git):

   ```properties
   storeFile=megagames-release.jks
   storePassword=...
   keyAlias=megagames
   keyPassword=...
   ```

   Então `./gradlew assembleRelease` gera `app/build/outputs/apk/release/app-release.apk`.

3. **Versões pelo GitHub**: crie quatro segredos no repositório (*Settings → Secrets and variables → Actions*):

   | Segredo | Valor |
   |---|---|
   | `MEGAGAMES_KEYSTORE_BASE64` | saída de `base64 -w0 megagames-release.jks` |
   | `MEGAGAMES_KEYSTORE_PASSWORD` | senha do keystore |
   | `MEGAGAMES_KEY_ALIAS` | `megagames` |
   | `MEGAGAMES_KEY_PASSWORD` | senha da chave |

   Aumente `versionCode` e `versionName` em `app/build.gradle.kts`, atualize o [CHANGELOG.md](../../CHANGELOG.md) e então:

   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```

   O [workflow de versão](../../.github/workflows/release.yml) testa, compila, assina e publica o `MegaGames-v1.0.0.apk` com o `SHA256SUMS.txt`.

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
- Comentários e nomes no código em inglês; siga o estilo existente (`.editorconfig`, estilo oficial do Kotlin).

## Adicionando palavras às cruzadas

Os dicionários são texto puro em `app/src/main/assets/crossword/<idioma>.txt`, uma entrada por linha:

```
RESPOSTA|Uma dica curta e simpática
```

- Palavras do dia a dia, de 2 a 13 letras (chinês: 2 a 4 caracteres), sem nomes próprios que exijam conhecimento específico.
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
