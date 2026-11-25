# 03 — Código e arquitetura

## Estrutura de um jogo
- **Regras em Kotlin puro** (`<Jogo>Engine.kt`): sem Compose, sem Android e determinísticas quando recebem uma semente (`Random(seed)`), para que os testes de unidade cubram tudo.
- **Tela separada** (`<Jogo>Game.kt`), feita com os componentes comuns (`GameLayout`, `StatusPill`, `ActionBar`, `BigButton`, `BigDialog`, `Handoff`). **Não crie botões ou diálogos próprios** quando já existe um.
- **Desenho separado** (`<Jogo>Art.kt`), com a função de miniatura `<jogo>Thumbnail(scope, p)`. A miniatura funciona em **qualquer proporção** (centralize um quadrado ou use largura e altura): ela aparece em telas de tamanhos diferentes.
- **Pixel art** usa sempre `ui/art/PixelArt.kt` (`pixelArt`, `TileMap`, `IsoGrid`, `PixelColors`). Não copie o motor.

## Estado e salvamento
- **O estado salvo é `@Serializable` e pequeno.** O `SaveSizeTest` vigia o tamanho. Não salve histórico infinito nem dados que dá para recalcular.
- **As chaves salvas nunca mudam depois de publicadas:** `GameId.key`, os `key` dos enums salvos e as chaves do `WorldStore`. Ao mudar um formato, aceite o antigo (com valores padrão ou migração).
- **Um salvamento por jogo** (`SaveStore`). Jogos com nível usam `LoadGame` (salvamento automático a cada mudança). Mundos duradouros usam `WorldStore`, com `hasDifficulty = false`.
- **Não salve 60 vezes por segundo.** Em jogos com animação contínua, o movimento fica fora do estado salvo; só os resultados entram nele (pontos, bolas, estrelas). Veja o Pinball.
- **Relógio injetável** (`LocalFarmClock`, `LocalPetClock`) em tudo que depende do tempo real, para os testes avançarem o tempo.

## Desempenho
- **Nada de alocar objetos por quadro** em loops de animação: reaproveite arrays e objetos.
- **Trabalho pesado fora da thread principal** (`withContext(Dispatchers.Default)`): IA, geradores de quebra-cabeça. Gere uma vez, salve o resultado e mostre um indicador de carregamento enquanto isso.
- **A IA responde em menos de 1 segundo** em qualquer nível, num celular simples.
- **Geradores têm teste de tempo** (por exemplo, "cada nível gera um desafio em menos de 8 s") e de qualidade (uma solução, ou o mínimo de movimentos certo).

## Listas comuns
Todo jogo novo acrescenta uma linha a `GameId` (`core/Model.kt`), ao `when` de `GameContent.kt`, ao `when` de `Thumbnails.kt`, ao `ScreenshotTest.kt` e ao `CHANGELOG.md`, sempre **no fim de cada lista**. Isso facilita o merge automático do robô.

## Dependências
- **Atualização de dependência é um PR à parte** (`build(deps): …`). Nunca misture com funcionalidade.
- **Ao atualizar o AGP, o Kotlin ou o Compose, rode o lint completo.** No AGP 9.5 foi preciso fixar o `lifecycle-runtime` em 2.11.0 para o lint carregar.
- **Não adicione bibliotecas de jogo, de imagem ou de rede.** Tudo é desenhado em Canvas e o app funciona sem internet.
