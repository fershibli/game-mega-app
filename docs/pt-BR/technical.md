# Design técnico

🌐 [English](../en/technical.md) · **Português** · [Español](../es/technical.md) · [Français](../fr/technical.md) · [中文](../zh-CN/technical.md)

## Sumário

1. [Escolha da tecnologia](#escolha-da-tecnologia)
2. [Arquitetura](#arquitetura)
3. [Estrutura do projeto](#estrutura-do-projeto)
4. [Navegação, configurações e salvamento automático](#navegação-configurações-e-salvamento-automático)
5. [Zoom e gestos de uma mão](#zoom-e-gestos-de-uma-mão)
6. [Arte sem imagens](#arte-sem-imagens)
7. [Geradores de desafios](#geradores-de-desafios)
8. [Adversários do computador](#adversários-do-computador)
9. [Internacionalização](#internacionalização)
10. [Testes](#testes)

## Escolha da tecnologia

O pedido foi "React Native se possível, mas escolha o que for mais leve em tamanho, RAM e processamento; só Android". As opções foram comparadas:

| | React Native (Hermes) | Flutter | **Kotlin + Jetpack Compose** |
|---|---|---|---|
| APK final deste app | ~15–25 MB (motor JS + núcleo RN por ABI) | ~8–12 MB (motor por ABI) | **~1,3 MB** (medido) |
| Runtime extra na memória | motor JS + ponte | VM Dart + Skia/Impeller | nenhum (roda no ART) |
| Desenhar tabuleiros / zoom com pinça | exige bibliotecas extras (Skia, gesture handler, reanimated) | embutido | embutido (`Canvas`, entrada de toque) |
| Acessibilidade nativa do Android (TalkBack, tamanho de fonte) | através de uma ponte | implementação própria | direta |

*Os tamanhos de React Native e Flutter são valores típicos para apps pequenos; só o tamanho com Compose foi medido neste projeto.*

**Decisão:** **Kotlin + Jetpack Compose** nativo, uma única activity, sem motor de jogo e sem imagens. O Compose faz parte das bibliotecas da plataforma Android (AndroidX), então o APK só leva as partes realmente usadas depois da redução do R8. O APK final medido tem cerca de **1,3 MB**.

Outras escolhas que mantêm o app pequeno e auditável:

- Sem biblioteca de navegação: a navegação é uma interface selada com quatro estados.
- Sem DataStore/Room: as configurações usam `SharedPreferences`; a única partida salva é um arquivo JSON gravado de forma atômica.
- Sem fontes de ícones/imagens: ícones e toda a arte dos jogos são desenhados com `DrawScope`.
- Android mínimo 6.0 (API 23), para funcionar nos celulares antigos que muitas vezes ficam com as pessoas idosas.

## Arquitetura

```
MainActivity
 └─ MegaGamesApp (máquina de estados: Início → Dificuldade → Jogando, + Configurações)
     ├─ MegaGamesTheme ── paleta (6 variações), idioma, escala de fonte, configurações como CompositionLocals
     ├─ HomeScreen / DifficultyScreen / SettingsScreen
     └─ GameScreen ── barra superior + engrenagem, configurações no jogo, diálogos "continuar?" e de fim de jogo
          └─ GameContent(jogo) ── um composable por jogo
               └─ LoadGame(host, serializer, create)   ← restaura ou gera o estado, salva cada mudança
                    └─ GameLayout(status, ZoomableBoard(tabuleiro), controles)
```

Cada jogo é dividido em:

- um **motor** — Kotlin puro, sem dependência do Android, estado imutável `@Serializable`, totalmente testado;
- uma **tela** — interface Compose que desenha o estado num `Canvas` e transforma toques em chamadas ao motor.

Os adversários do computador rodam em `Dispatchers.Default`, para a tela nunca travar, e mostram as jogadas passo a passo.

## Estrutura do projeto

```
app/src/main/java/io/github/fershibli/megagames/
├── MainActivity.kt
├── core/            Settings, SettingsStore, SaveStore + GameHost (salvamento), GameId, Difficulty
├── ui/
│   ├── MegaGamesApp.kt   navegação
│   ├── theme/            Palette.kt (6 paletas), Theme.kt (idioma, escala de fonte)
│   ├── components/       botões, barra superior, engrenagem flutuante, roleta, ZoomableBoard, GameLayout
│   ├── art/              cartas, naipes, peças de mahjong, blocos 3D, discos brilhantes, miniaturas
│   └── screens/          Início, Dificuldade, Configurações, Jogo
└── games/
    ├── cards/  sudoku/  solitaire/  buraco/  backgammon/  mahjong/
    └── reversi/  crossword/  pipes/  connect/  dots/
app/src/main/assets/crossword/   en.txt pt.txt es.txt fr.txt zh.txt   (palavra|dica)
app/src/main/res/values*/        textos em 5 idiomas
app/src/test/                     testes de unidade, fluxo da interface, traduções, telas e benchmarks
```

## Navegação, configurações e salvamento automático

- A **navegação** é uma `sealed interface Screen`. O botão Voltar do celular vai de Configurações → Início, de Dificuldade → Início e, dentro de um jogo, abre/fecha as configurações do jogo (assim ninguém sai de uma partida sem querer).
- As **configurações** são uma `data class` imutável, gravada em `SharedPreferences` a cada mudança e entregue à interface inteira por `CompositionLocal`s.
- **Salvamento automático**: `LoadGame` guarda o estado do jogo num `MutableState` e o observa com `snapshotFlow`; cada estado novo é convertido em JSON e entregue ao `SaveStore`, que grava numa única thread em segundo plano, *a gravação mais nova vence*, usando um arquivo temporário + renomeação (uma queda nunca deixa meio arquivo). `GameHost.finish()` apaga o salvamento, então partidas terminadas não são oferecidas de novo.
- **"Continuar?"**: numa abertura "do zero" (`savedInstanceState == null`), o `MegaGamesApp` carrega o salvamento e abre o jogo direto com a pergunta. Um salvamento que não pode ser lido (corrompido ou de versão incompatível) é ignorado e um jogo novo é gerado.
- A activity trata sozinha as mudanças de configuração (`android:configChanges`), então girar, dobrar o aparelho ou mudar o modo escuro nunca recria a tela nem perde o estado.

## Zoom e gestos de uma mão

`ZoomableBoard` envolve todos os tabuleiros. Ele escuta na passagem **Initial** dos eventos de toque, antes do jogo:

- Dois dedos ou mais (com o *zoom com pinça* ligado) → zoom em volta do centro dos dedos e movimento; esses eventos são consumidos para o jogo nunca receber um toque perdido.
- Um dedo → passa direto; os jogos usam coordenadas locais normais porque o tabuleiro é transformado com `graphicsLayer` (o Compose converte as coordenadas do toque automaticamente).
- Uma mão, *toque duplo*: o segundo toque de um toque duplo é consumido; toque = passo de zoom (1× → 2× → 3× → 1×), tocar e arrastar = mover.
- Uma mão, *pressionar e girar*: depois do tempo de toque longo (com vibração), o ângulo do dedo em volta do ponto pressionado controla um zoom exponencial (horário = ampliar). Toque duplo volta ao normal, toque duplo e arrastar move.

O deslocamento é limitado para o tabuleiro nunca sair da tela, e um botão de restaurar aparece enquanto há zoom.

## Arte sem imagens

Toda a arte é desenhada na hora com o `DrawScope` do Compose, então fica nítida em qualquer zoom e muda de cor sozinha em cada uma das seis paletas:

- **Profundidade**: `raisedBlock()` desenha camadas laterais do claro ao escuro mais uma face com degradê (peças de mahjong, dados); `glossyDisc()` desenha sombra, degradê radial, um friso e um brilho (pedras, discos do reversi, bolinhas); as cartas ganham uma sombra suave em camadas.
- **Cartas**: os naipes são caminhos vetoriais; os valores aparecem grandes nos dois cantos de cima, para continuarem legíveis com cartas sobrepostas; figuras ganham uma coroa; a paleta para daltônicos usa um baralho de quatro cores; o francês usa R/D/V para Rei/Dama/Valete.
- **Mahjong**: círculos, bambus (com o pássaro tradicional no 1), caracteres (numerais chineses + 萬), ventos, dragões, flores e estações, mais um índice ocidental no canto.
- **Paletas**: Normal, Alto contraste e Daltonismo (Okabe–Ito), cada uma clara e escura. Nas paletas acessíveis, cada peça colorida também tem uma forma (`drawSymbol`).

## Geradores de desafios

| Jogo | Método | Garantia (testada) |
|---|---|---|
| Sudoku | Grade completa aleatória por retrocesso; retira números em ordem aleatória, mantendo cada retirada só se um contador de soluções (retrocesso pela casa mais restrita, para em 2) ainda encontra exatamente uma solução. | Solução única; quantidade de números por nível |
| Mahjong | Layouts fixos (torre 48, fortaleza 96, tartaruga 144 em pé). As faces são distribuídas *simulando o jogo de trás para a frente*: retiram-se repetidamente duas posições livres naquele momento e elas recebem um par igual. Jogar nessa ordem vence. Embaralhar repete isso com as peças que sobraram. | Toda distribuição e todo embaralhamento têm solução |
| Encanamento | Árvore geradora aleatória crescendo a partir da fonte central (Prim aleatório, evitando cruzamentos de 4 saídas) e depois cada peça girada ao acaso. | Conectado, sem circuitos, com solução; nunca começa resolvido |
| Ligue as cores | Um caminho hamiltoniano (serpentina) embaralhado com milhares de movimentos "backbite" e depois cortado em trechos coloridos de 3+ casas cujas pontas não se tocam. | Tem solução com o tabuleiro inteiro preenchido |
| Palavras cruzadas | Palavras (mais longas primeiro) colocadas só onde cruzam letras existentes, sem encostar de lado em outras palavras; a melhor de até 60 tentativas; numeradas como numa revista. | Sem conflito de letras; quantidade de palavras por nível |
| Pontinhos | Cores aleatórias; reposição depois da "gravidade"; o tabuleiro é embaralhado se não houver jogada. | Sempre existe jogada |
| Paciência / Buraco | Embaralhamento justo de um / dois baralhos (`kotlin.random`). | — |

## Adversários do computador

Como pedido, primeiro foi considerado um motor pronto:

- O **GNU Backgammon** é o motor de gamão de código aberto de referência, mas é escrito em C sob a GPL e teria de ser compilado com o NDK para cada tipo de processador, somando megabytes e obrigando o app inteiro a usar a GPL.
- Para o **Buraco** não existe um motor de código aberto consolidado (só projetos pessoais e discussões em fóruns).

Por isso os adversários foram escritos especialmente para este app, em Kotlin puro:

**Reversi** — minimax com poda alfa-beta. Avaliação: tabela de pesos por posição (cantos +100, casas vizinhas dos cantos −20/−50), mobilidade e contagem de pedras no fim. Fácil: profundidade 1 com 35 % de jogadas aleatórias. Médio: profundidade 3 (8 no final). Difícil: profundidade 6 e busca completa nas últimas 12 casas vazias (final perfeito).

**Gamão** — geração completa das jogadas (entrar pela barra, capturar, retirar com a regra do dado maior, regra de "usar os dois dados / o maior") sobre uma visão relativa a cada lado. Avaliação: diferença na contagem de pontos (pips), pontos feitos pesados pela importância, tamanho do bloqueio, âncoras, exposição das pedras sozinhas estimada pelo número das 36 jogadas de dados que as acertam, pedras na barra, e uma avaliação de corrida separada quando não há mais contato. Fácil: joga ao acaso 45 % das vezes. Médio: a melhor avaliação depois da própria jogada. Difícil: pega as 5 melhores candidatas e prevê uma jogada à frente, fazendo a média da melhor resposta do adversário nas 21 jogadas de dados possíveis (expectimax).

**Buraco** — jogador heurístico: decide se pega o lixo (carta de cima usável na hora, valor do lixo inteiro), baixa primeiro as sequências naturais, encaixa cartas nos jogos existentes, usa curingas conforme o nível (o Difícil mantém as canastras limpas limpas) e descarta a carta com o menor "valor de guardar" (vizinhas na mão, encaixe nos próprios jogos) somado a um "custo de perigo" para cartas que completariam os seus jogos ou combinam com cartas que você pegou.

### Benchmarks

Computador contra computador, gerado pelo `AiBenchmarkTest` (opcional):

| Jogo | Difícil × Fácil | Médio × Fácil | Difícil × Médio |
|---|---|---|---|
| Buraco (100 partidas) | 92 % de vitórias, +903 pts em média | 92 %, +925 pts | 52 %, +77 pts |
| Gamão (60 partidas) | 98 % | 100 % | 48 % |
| Reversi (20 partidas) | 100 % | 95 % | 95 % |

Médio e Difícil ficam próximos quando dois computadores jogam Gamão ou Buraco — os dois são muito mais fortes que o Fácil. Contra pessoas, o conhecimento extra do Difícil (previsão no Gamão, memória do que você pegou e descartes defensivos no Buraco) pesa mais do que contra outro programa.

## Internacionalização

- Os textos da interface ficam em `res/values/strings.xml` (inglês) e em `values-pt`, `values-es`, `values-fr`, `values-zh`.
- A troca de idioma dentro do app envolve a árvore do Compose num contexto criado com `createConfigurationContext`, então funciona em qualquer versão do Android sem AppCompat. No Android 13+ os idiomas também aparecem nas configurações de idioma por app do sistema (`locales_config.xml`).
- Só esses cinco idiomas são empacotados (`localeFilters`), e a divisão por idioma está desativada para app bundles, então trocar de idioma nunca exige download.
- Os dicionários das palavras cruzadas são texto puro: `RESPOSTA|dica`. As respostas são normalizadas (maiúsculas, sem acentos, Ñ mantido no espanhol, Œ → OE); em chinês, cada caractere é uma casa.

## Testes

| Classe de teste | O que cobre |
|---|---|
| `SudokuEngineTest`, `SolitaireTest`, `ReversiTest`, `MahjongTest`, `PuzzleGeneratorsTest`, `CrosswordTest`, `BackgammonTest`, `BuracoTest` | Regras e garantias dos geradores, velocidade e correção da IA |
| `TranslationsTest` | Todas as chaves traduzidas em todos os idiomas, marcadores idênticos, nenhum apóstrofo sem escape |
| `AppFlowTest` (Robolectric) | Salvar e carregar, salvamento corrompido, persistência das configurações, zoom com pinça ligado/desligado, zoom por toque duplo e por pressionar e girar, escolher e abandonar um jogo, a pergunta "continuar?" |
| `ScreenshotTest` (opcional) | Desenha todas as telas e jogos em `docs/screenshots` |
| `AiBenchmarkTest` (opcional) | A tabela acima |
