# Confiança, privacidade e segurança

🌐 [English](../en/trust.md) · **Português** · [Español](../es/trust.md) · [Français](../fr/trust.md) · [中文](../zh-CN/trust.md)

O Mega Games foi feito para ser instalado no celular de pessoas que talvez não consigam avaliar um app sozinhas. Esta página explica o que o app faz e o que não faz — e, mais importante, **como qualquer pessoa pode conferir** sem precisar confiar no autor.

## Sumário

1. [Resumo](#resumo)
2. [Privacidade](#privacidade)
3. [Permissões](#permissões)
4. [O que há dentro do app](#o-que-há-dentro-do-app)
5. [Como o código é verificado](#como-o-código-é-verificado)
6. [Conferindo um APK baixado](#conferindo-um-apk-baixado)
7. [Proteções da cadeia de suprimentos](#proteções-da-cadeia-de-suprimentos)
8. [Ajuda de IA, com transparência](#ajuda-de-ia-com-transparência)
9. [Relatando um problema de segurança](#relatando-um-problema-de-segurança)

## Resumo

| | |
|---|---|
| Acesso à internet | **Nenhum** (não há permissão `INTERNET`) |
| Dados coletados | **Nenhum** |
| Anúncios, estatísticas, relatórios de falha, rastreadores | **Nenhum** |
| Contas, login, compras | **Nenhum** |
| Permissões do Android pedidas | **Nenhuma** |
| Código de terceiros | Só bibliotecas oficiais AndroidX (Google) e Kotlin (JetBrains) |
| Licença | MIT, código-fonte público |

## Privacidade

O app guarda exatamente duas coisas, ambas na sua área **privada**, que outros apps não conseguem ler:

1. `settings.xml` — suas escolhas nas Configurações (idioma, tamanhos, cores…).
2. `current_game.json` — a partida em andamento, para poder ser retomada depois que o app fecha. Ele é apagado quando a partida termina ou é abandonada.

Se você ativou o backup do próprio Android, ele pode incluir esses dois arquivos no *seu* backup do Google (e na transferência entre aparelhos), para que um celular novo mantenha as configurações. Isso está definido em [`backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) e [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml). Nada além disso é guardado ou enviado.

O botão "Abrir a página do projeto", em Configurações → Sobre, apenas pede ao Android para abrir a página do GitHub no seu navegador; o app em si continua sem fazer nenhuma conexão.

## Permissões

O [manifesto](../../app/src/main/AndroidManifest.xml) **não** declara nenhum `<uses-permission>`. Dá para confirmar em qualquer APK com o Android SDK:

```bash
aapt2 dump permissions MegaGames-v1.0.0.apk
```

Aparece uma única entrada terminando em `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Ela não é uma permissão de usuário: é uma permissão privada, de nível de assinatura, que a biblioteca AndroidX adiciona automaticamente para proteger os receptores internos do próprio app contra outros apps. Ela não concede nada a ninguém.

Sem a permissão `INTERNET`, o Android não deixa o app abrir nenhuma conexão de rede.

## O que há dentro do app

A lista completa de dependências está em [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml):

| Biblioteca | Quem publica | Para quê |
|---|---|---|
| `androidx.activity:activity-compose` | Google (AndroidX) | Exibe a interface Compose na activity |
| `androidx.compose.ui`, `foundation`, `material3` | Google (AndroidX) | O kit de interface |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JetBrains | Salvar a partida em andamento em JSON |
| Biblioteca padrão do Kotlin | JetBrains | O runtime da linguagem |

Bibliotecas só de teste (nunca vão para o app): JUnit, Robolectric, Compose UI test.

**Não há imagens, sons, fontes nem bibliotecas nativas** no app: cada desenho é feito por código que você pode ler em [`ui/art`](../../app/src/main/java/io/github/fershibli/megagames/ui/art). Os únicos arquivos de dados são os dicionários das palavras cruzadas, em texto puro ([`assets/crossword`](../../app/src/main/assets/crossword)).

A versão final também desativa o bloco de "informações de dependências" do Google (`dependenciesInfo { includeInApk = false }`), um conteúdo criptografado que seria embutido no APK. Assim, cada byte do APK pode ser inspecionado.

## Como o código é verificado

Todo push e pull request roda o [CI](../../.github/workflows/ci.yml) no GitHub Actions:

1. **Validação do Gradle wrapper** — confirma que o `gradle-wrapper.jar` é o binário oficial.
2. **50 testes automáticos**, incluindo:
   - as regras de todos os jogos (jogadas válidas, pontuação, retirada de pedras, jogos e curingas do Buraco…);
   - os geradores: todo Sudoku tem solução única, toda distribuição do Mahjong tem solução, todo Encanamento e todo Ligue as cores têm solução completa, as palavras cruzadas nunca se contradizem;
   - os adversários do computador terminam as partidas rápido e nunca perdem nem duplicam uma carta ou pedra;
   - os cinco idiomas traduzem todos os textos e mantêm os mesmos marcadores;
   - salvar e recarregar a partida, arquivo de salvamento corrompido, persistência das configurações;
   - a interface de verdade: zoom com pinça ligado/desligado, zoom de uma mão por toque duplo e por pressionar e girar, escolher um jogo, sair dele pelas Configurações e a pergunta "continuar?" depois de reabrir.
3. **Android Lint** tratando *avisos como erros*.
4. **Compilação** dos APKs de depuração e final, publicados como artefatos com **somas SHA-256**.

Rode as mesmas verificações você mesmo: veja [building.md](building.md).

## Conferindo um APK baixado

1. Baixe o `MegaGames-vX.Y.Z.apk` e o `SHA256SUMS.txt` da mesma [versão](https://github.com/fershibli/game-mega-app/releases).
2. Calcule o checksum e compare:

   ```bash
   sha256sum MegaGames-vX.Y.Z.apk      # Linux / macOS: shasum -a 256
   certutil -hashfile MegaGames-vX.Y.Z.apk SHA256   # Windows
   ```

3. Confira que o certificado de assinatura é o mesmo entre versões (o Android também se recusa a atualizar um app assinado com outra chave):

   ```bash
   apksigner verify --print-certs MegaGames-vX.Y.Z.apk
   ```

4. Para garantia total, compile você mesmo a partir do código da tag e compare o conteúdo (`unzip -l`, `aapt2 dump badging`).

## Proteções da cadeia de suprimentos

- `distributionSha256Sum` fixa a distribuição exata do Gradle em [`gradle-wrapper.properties`](../../gradle/wrapper/gradle-wrapper.properties).
- As dependências vêm só do repositório Maven do Google e do Maven Central (`repositoriesMode = FAIL_ON_PROJECT_REPOS`).
- O [Dependabot](../../.github/dependabot.yml) propõe todo mês atualizações das dependências e das GitHub Actions.
- O CI roda com permissão só de leitura; apenas os workflows de versionamento e de deploy podem escrever, e só para enviar a tag da versão e publicar a Release.
- Todo APK publicado é verificado no pipeline antes do envio: assinatura (`apksigner`), número da versão e ausência de permissões (`aapt2`).
- As chaves de assinatura ficam nos segredos criptografados do GitHub e nunca vão para o repositório (`*.jks` e `keystore.properties` estão no `.gitignore`).

## Ajuda de IA, com transparência

O código, a arte e a documentação foram criados com a ajuda do **Claude**, o assistente de IA da Anthropic, sob a direção do mantenedor. Código escrito com IA pode ter erros como qualquer outro código; por isso este projeto se apoia em coisas que você pode conferir, e não em promessas: código legível, uma bateria ampla de testes, lint, compilações reproduzíveis e checksums publicados. Se encontrar um problema, por favor avise.

## Relatando um problema de segurança

Veja o [SECURITY.md](../../SECURITY.md). Por favor, não abra uma issue pública para uma vulnerabilidade; use o relato privado de vulnerabilidades do GitHub.
