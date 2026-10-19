# Publicar na Google Play

🌐 [English](../en/google-play.md) · **Português**

Tudo o que o repositório consegue fazer sozinho está automatizado: a chave de assinatura, o AAB, os textos e imagens da loja e (opcionalmente) o envio de cada nova versão. Sobram os passos que só o dono da conta pode fazer: pagar, comprovar a identidade, convidar testadores e preencher os formulários do Google. Esta página lista esses passos em ordem, com as respostas para este app.

## Conteúdo

1. [Chave de assinatura](#1-chave-de-assinatura)
2. [Conta de desenvolvedor da Google Play](#2-conta-de-desenvolvedor-da-google-play)
3. [Criar o app](#3-criar-o-app)
4. [Formulários de conteúdo do app](#4-formulários-de-conteúdo-do-app)
5. [Página da loja](#5-página-da-loja)
6. [A mesma chave no GitHub e na Play](#6-a-mesma-chave-no-github-e-na-play)
7. [Teste fechado: 12 testadores por 14 dias](#7-teste-fechado-12-testadores-por-14-dias)
8. [Envio automático (opcional)](#8-envio-automático-opcional)
9. [Verificação de desenvolvedor e instalação por APK](#9-verificação-de-desenvolvedor-e-instalação-por-apk)

## 1. Chave de assinatura

O Android só instala uma atualização assinada com a **mesma chave** do app instalado. O workflow [`signing-key.yml`](../../.github/workflows/signing-key.yml) cria essa chave e a guarda nos secrets do repositório; ele nunca substitui uma chave existente.

1. Crie um token com permissão de gravar os secrets deste repositório:
   [novo token (já preenchido)](https://github.com/settings/personal-access-tokens/new?name=Mega+Games+signing+key&description=Lets+the+Signing+key+workflow+store+the+release+key+in+the+repository+secrets&expires_in=366&secrets=write).
   Em **Repository access**, escolha *Only select repositories* → `game-mega-app`, depois **Generate token** e copie o valor.
2. Salve-o como o secret `MEGAGAMES_SECRETS_TOKEN`: [novo secret do repositório](https://github.com/fershibli/game-mega-app/settings/secrets/actions/new).
3. Execute [Signing key → Run workflow](https://github.com/fershibli/game-mega-app/actions/workflows/signing-key.yml) (ação `sync`). Ele cria a chave com uma senha temporária aleatória e a guarda em `MEGAGAMES_SIGNING_PASSWORD`. O resumo da execução mostra a impressão digital SHA-256 do certificado.
4. Quando quiser, troque `MEGAGAMES_SIGNING_PASSWORD` pela sua própria senha (16 caracteres ou mais; 20 ou mais para fazer backups). O workflow recriptografa **a mesma chave** com ela: em até uma hora (a verificação roda de hora em hora no branch padrão) ou na hora, com *Run workflow*.
5. Execute o workflow com a ação `backup` e guarde o arquivo baixado e a senha num gerenciador de senhas. Sem uma cópia, perder o secret obrigaria todos os celulares a desinstalar o app para receber atualizações.

A partir daí toda versão, inclusive as candidatas, sai assinada com essa chave. O token vence em um ano: crie outro quando isso acontecer (ele só é necessário para criar ou recriptografar a chave).

## 2. Conta de desenvolvedor da Google Play

- [Criar a conta](https://play.google.com/console/signup): conta **pessoal**, taxa única de US$ 25, verificação de identidade com documento oficial e verificação em duas etapas.

Em outubro de 2026 o Google anunciou que apps criados no Google AI Studio podem ser publicados sem conta de desenvolvedor, mas só de forma privada, no celular de quem criou. Isso não vale para este app.

## 3. Criar o app

[Play Console](https://play.google.com/console) → **Criar app**:

| Campo | Valor |
|---|---|
| Nome do app | `Mili Games` |
| Idioma padrão | à sua escolha (os textos estão prontos para en-US, pt-BR, es-419, fr-FR e zh-CN) |
| App ou jogo | Jogo |
| Gratuito ou pago | Gratuito |

## 4. Formulários de conteúdo do app

*Política e programas → Conteúdo do app*. Respostas para este app:

| Formulário | Resposta |
|---|---|
| Política de privacidade | `https://github.com/fershibli/game-mega-app/blob/main/PRIVACY.md` |
| Anúncios | Não, o app não tem anúncios |
| Acesso ao app | Todas as funções estão disponíveis sem acesso especial |
| Classificação do conteúdo (IARC) | Categoria *Jogo*; responda **Não** a todas as perguntas (sem violência, sem apostas, sem interação entre usuários, sem compras). Classificação esperada: Livre |
| Público-alvo | 18 anos ou mais (o app é feito para idosos; idades menores trazem a política de famílias) |
| Segurança dos dados | **Não**, o app não coleta nem compartilha nenhum dos tipos de dados pedidos (o backup do próprio Android não conta como coleta pelo desenvolvedor) |
| App governamental, recursos financeiros, saúde, notícias | Não |
| ID de publicidade | O app não usa |

## 5. Página da loja

*Crescimento → Presença na loja → Página principal da loja* (adicione os outros idiomas em *Gerenciar traduções*). Tudo está em [`fastlane/metadata/android`](../../fastlane/metadata/android):

| Campo do Play Console | Arquivo |
|---|---|
| Nome do app | `<idioma>/title.txt` |
| Descrição curta | `<idioma>/short_description.txt` |
| Descrição completa | `<idioma>/full_description.txt` |
| Ícone do app (512×512) | `en-US/images/icon.png` |
| Imagem de destaque (1024×500) | `en-US/images/featureGraphic.jpg` |
| Capturas de tela do celular | `<idioma>/images/phoneScreenshots/` |

Categoria: *Tabuleiro*. E-mail de contato: obrigatório pelo Google e exibido publicamente.

Para redesenhar as imagens depois de mudar a interface: `./gradlew :app:testDebugUnitTest -PplayAssets=true --tests '*PlayAssetsTest*' && python3 scripts/optimize_screenshots.py`.

## 6. A mesma chave no GitHub e na Play

Por padrão a Play reassina os apps com uma chave gerada pelo Google. Aí o APK do GitHub e o app da Play não se atualizam um pelo outro (trocar exige desinstalar e perder o jogo salvo). Para entregar à Play **a nossa** chave:

1. No Play Console, ao criar a primeira versão, abra as opções de assinatura do app e escolha usar **a sua própria chave**, *Exportar e enviar uma chave de um keystore Java*. Baixe `encryption_public_key.pem` e abra-o num editor de texto.
2. Execute [Signing key](https://github.com/fershibli/game-mega-app/actions/workflows/signing-key.yml) com a ação `export-for-play`, colando o conteúdo do arquivo em `play_encryption_key`.
3. Baixe o artefato `miligames-play-signing-key`, descompacte-o uma vez e envie o `miligames-play-signing-key.zip` de dentro no Play Console. Ele vem criptografado para o Google: só a Google Play consegue lê-lo.

## 7. Teste fechado: 12 testadores por 14 dias

Contas pessoais criadas depois de novembro de 2023 precisam [fazer um teste fechado](https://support.google.com/googleplay/android-developer/answer/14151465) com **pelo menos 12 testadores inscritos por 14 dias seguidos** antes de publicar para todos.

1. *Testar e lançar → Teste fechado* → a faixa padrão *Alpha* → **Criar versão**.
2. Envie o `MiliGames-vX.Y.Z.aab` da [última versão no GitHub](https://github.com/fershibli/game-mega-app/releases) (o primeiro pacote precisa ser enviado à mão).
3. *Testadores*: crie uma lista de e-mails com 12 ou mais contas Google (família e amigos com Android), copie o link de participação e envie para eles. Cada um precisa abrir o link, aceitar e instalar o app.
4. Envie a versão para revisão. Depois de 14 dias com 12 ou mais testadores, vá em *Painel → Solicitar acesso à produção* e responda às perguntas sobre o teste.

## 8. Envio automático (opcional)

Com uma conta de serviço, o [`deploy.yml`](../../.github/workflows/deploy.yml) envia sozinho cada novo AAB (script: [`scripts/play-upload.sh`](../../scripts/play-upload.sh), só `curl` e `openssl`).

1. [Ative a Google Play Android Developer API](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com) num projeto do Google Cloud.
2. [Crie uma conta de serviço](https://console.cloud.google.com/iam-admin/serviceaccounts) e uma **chave JSON** para ela.
3. Play Console → *Usuários e permissões* → **Convidar novos usuários** → o e-mail da conta de serviço, com permissão para lançar em faixas de teste (e em produção, mais tarde).
4. Salve o JSON inteiro como o secret `PLAY_SERVICE_ACCOUNT_JSON`: [novo secret do repositório](https://github.com/fershibli/game-mega-app/settings/secrets/actions/new).

Assim as versões candidatas vão para a faixa de teste, e as estáveis seguem estas [variáveis do repositório](https://github.com/fershibli/game-mega-app/settings/variables/actions):

| Variável | Padrão | Significado |
|---|---|---|
| `PLAY_TESTING_TRACK` | `alpha` | faixa das versões candidatas (e das estáveis até liberar a produção) |
| `PLAY_RELEASE_STATUS` | `draft` | troque para `completed` depois que o app passar pela primeira revisão (antes disso o Google só aceita rascunhos) |
| `PLAY_PRODUCTION` | — | coloque `true` quando o Google liberar a produção: as versões estáveis passam a ir para produção |

Nunca há envio com a chave de debug.

## 9. Verificação de desenvolvedor e instalação por APK

Desde 30 de setembro de 2026, o Android no Brasil, Indonésia, Singapura e Tailândia exige que os apps das lojas participantes (inclusive a Google Play) sejam registrados por um desenvolvedor verificado; quem publica na Play é verificado pelo Play Console. Por enquanto isso não vale para APKs instalados diretamente (como os das versões do GitHub), mas o Google pretende estender a regra a todos os apps em 2027 ([detalhes](https://developer.android.com/developer-verification)).

Para manter o APK do GitHub instalável pela família sem pagar nada, uma [conta de distribuição limitada](https://developer.android.com/developer-verification/guides/limited-distribution), gratuita, registra o pacote `io.github.fershibli.miligames` para até 20 aparelhos, sem documento e sem taxa.
