# Diseño técnico

🌐 [English](../en/technical.md) · [Português](../pt-BR/technical.md) · **Español** · [Français](../fr/technical.md) · [中文](../zh-CN/technical.md)

## Contenido

1. [Elección de la tecnología](#elección-de-la-tecnología)
2. [Arquitectura](#arquitectura)
3. [Estructura del proyecto](#estructura-del-proyecto)
4. [Navegación, ajustes y guardado automático](#navegación-ajustes-y-guardado-automático)
5. [Zoom y gestos de una mano](#zoom-y-gestos-de-una-mano)
6. [Arte sin imágenes](#arte-sin-imágenes)
7. [Generadores de retos](#generadores-de-retos)
8. [Rivales del ordenador](#rivales-del-ordenador)
9. [Internacionalización](#internacionalización)
10. [Pruebas](#pruebas)

## Elección de la tecnología

La petición fue "React Native si es posible, pero elige lo más ligero en tamaño, RAM y procesador; solo Android". Se compararon las opciones:

| | React Native (Hermes) | Flutter | **Kotlin + Jetpack Compose** |
|---|---|---|---|
| APK final de esta aplicación | ~15–25 MB (motor JS + núcleo RN por ABI) | ~8–12 MB (motor por ABI) | **~1,3 MB** (medido) |
| Entorno extra en memoria | motor JS + puente | VM de Dart + Skia/Impeller | ninguno (se ejecuta en ART) |
| Dibujar tableros / zoom con dos dedos | requiere bibliotecas extra (Skia, gesture handler, reanimated) | incluido | incluido (`Canvas`, entrada táctil) |
| Accesibilidad nativa de Android (TalkBack, tamaño de letra) | a través de un puente | implementación propia | directa |

*Los tamaños de React Native y Flutter son cifras típicas de aplicaciones pequeñas; solo se midió el tamaño con Compose en este proyecto.*

**Decisión:** **Kotlin + Jetpack Compose** nativo, una sola activity, sin motor de juego y sin imágenes. Compose forma parte de las bibliotecas de la plataforma Android (AndroidX), así que el APK solo contiene lo que realmente se usa tras la reducción de R8. El APK final medido ocupa unos **1,3 MB**.

Otras decisiones que mantienen la aplicación pequeña y auditable:

- Sin biblioteca de navegación: la navegación es una interfaz sellada con cuatro estados.
- Sin DataStore/Room: los ajustes usan `SharedPreferences`; la única partida guardada es un archivo JSON escrito de forma atómica.
- Sin fuentes de iconos ni imágenes: los iconos y todo el arte de los juegos se dibujan con `DrawScope`.
- Android mínimo 6.0 (API 23), para funcionar en los teléfonos antiguos que a menudo heredan las personas mayores.

## Arquitectura

```
MainActivity
 └─ MegaGamesApp (máquina de estados: Inicio → Dificultad → Jugando, + Ajustes)
     ├─ MegaGamesTheme ── paleta (6 variantes), idioma, escala de letra, ajustes como CompositionLocals
     ├─ HomeScreen / DifficultyScreen / SettingsScreen
     └─ GameScreen ── barra superior + engranaje, ajustes en partida, diálogos "¿continuar?" y de fin de partida
          └─ GameContent(juego) ── un composable por juego
               └─ LoadGame(host, serializer, create)   ← restaura o genera el estado, guarda cada cambio
                    └─ GameLayout(estado, ZoomableBoard(tablero), controles)
```

Cada juego se divide en:

- un **motor**: Kotlin puro, sin dependencias de Android, estado inmutable `@Serializable`, totalmente probado;
- una **pantalla**: interfaz Compose que dibuja el estado en un `Canvas` y convierte los toques en llamadas al motor.

Los rivales del ordenador se ejecutan en `Dispatchers.Default` para que la pantalla nunca se congele, y muestran sus jugadas paso a paso.

## Estructura del proyecto

```
app/src/main/java/io/github/fershibli/megagames/
├── MainActivity.kt
├── core/            Settings, SettingsStore, SaveStore + GameHost (guardado), GameId, Difficulty
├── ui/
│   ├── MegaGamesApp.kt   navegación
│   ├── theme/            Palette.kt (6 paletas), Theme.kt (idioma, escala de letra)
│   ├── components/       botones, barra superior, engranaje flotante, rueda, ZoomableBoard, GameLayout
│   ├── art/              cartas, palos, fichas de mahjong, bloques 3D, discos brillantes, miniaturas
│   └── screens/          Inicio, Dificultad, Ajustes, Juego
└── games/
    ├── cards/  sudoku/  solitaire/  buraco/  backgammon/  mahjong/
    └── reversi/  crossword/  pipes/  connect/  dots/
app/src/main/assets/crossword/   en.txt pt.txt es.txt fr.txt zh.txt   (palabra|pista)
app/src/main/res/values*/        textos en 5 idiomas
app/src/test/                     pruebas unitarias, de flujo de interfaz, traducciones, capturas y benchmarks
```

## Navegación, ajustes y guardado automático

- La **navegación** es una `sealed interface Screen`. El botón Atrás del teléfono va de Ajustes → Inicio, de Dificultad → Inicio y, dentro de un juego, abre/cierra los ajustes de la partida (así nadie abandona una partida sin querer).
- Los **ajustes** son una `data class` inmutable que se guarda en `SharedPreferences` en cada cambio y se entrega a toda la interfaz mediante `CompositionLocal`s.
- **Guardado automático**: `LoadGame` guarda el estado del juego en un `MutableState` y lo observa con `snapshotFlow`; cada estado nuevo se convierte en JSON y se entrega a `SaveStore`, que escribe en un único hilo en segundo plano, *gana la escritura más reciente*, mediante un archivo temporal + renombrado (un cierre inesperado nunca deja medio archivo). `GameHost.finish()` borra el guardado, de modo que las partidas terminadas no se vuelven a ofrecer.
- **"¿Continuar?"**: en un arranque en frío (`savedInstanceState == null`), `MegaGamesApp` carga el guardado y abre directamente el juego con la pregunta. Un guardado que no se puede leer (dañado o de una versión incompatible) se ignora y se genera una partida nueva.
- La activity gestiona ella misma los cambios de configuración (`android:configChanges`), así que girar, plegar el dispositivo o cambiar el modo oscuro nunca recrea la pantalla ni pierde el estado.

## Zoom y gestos de una mano

`ZoomableBoard` envuelve todos los tableros. Escucha en la pasada **Initial** de los eventos táctiles, antes que el juego:

- Dos o más dedos (con el *zoom con dos dedos* activado) → zoom alrededor del centro de los dedos y desplazamiento; esos eventos se consumen para que el juego nunca reciba un toque suelto.
- Un dedo → pasa sin cambios; los juegos usan coordenadas locales normales porque el tablero se transforma con `graphicsLayer` (Compose convierte las coordenadas del toque automáticamente).
- Una mano, *doble toque*: el segundo toque de un doble toque se consume; toque = paso de zoom (1× → 2× → 3× → 1×), tocar y arrastrar = desplazar.
- Una mano, *mantener y girar*: tras el tiempo de pulsación larga (con vibración), el ángulo del dedo alrededor del punto pulsado controla un zoom exponencial (horario = acercar). El doble toque vuelve a lo normal y doble toque y arrastrar desplaza.

El desplazamiento está limitado para que el tablero nunca salga de la pantalla, y aparece un botón para restablecer mientras hay zoom.

## Arte sin imágenes

Todo el arte se dibuja al momento con `DrawScope` de Compose, así que es nítido con cualquier zoom y cambia de color solo en cada una de las seis paletas:

- **Profundidad**: `raisedBlock()` dibuja capas laterales de claro a oscuro y una cara con degradado (fichas de mahjong, dados); `glossyDisc()` dibuja sombra, degradado radial, un reborde y un brillo (fichas, discos de reversi, puntos); las cartas llevan una sombra suave en capas.
- **Cartas**: los palos son trazados vectoriales; los valores aparecen grandes en las dos esquinas superiores para que se lean aunque las cartas se solapen; las figuras llevan una corona; la paleta para daltonismo usa una baraja de cuatro colores; el francés usa R/D/V para Rey/Dama/Sota.
- **Mahjong**: círculos, bambúes (con el pájaro tradicional en el 1), caracteres (numerales chinos + 萬), vientos, dragones, flores y estaciones, más un índice occidental en la esquina.
- **Paletas**: Normal, Alto contraste y Daltonismo (Okabe–Ito), cada una clara y oscura. En las paletas accesibles, cada pieza de color lleva además una forma (`drawSymbol`).

## Generadores de retos

| Juego | Método | Garantía (probada) |
|---|---|---|
| Sudoku | Cuadrícula completa aleatoria por vuelta atrás; quita números en orden aleatorio y mantiene cada eliminación solo si un contador de soluciones (vuelta atrás por la casilla más restringida, se detiene en 2) sigue encontrando exactamente una solución. | Solución única; número de pistas por nivel |
| Mahjong | Diseños fijos (torre 48, fortaleza 96, tortuga 144 puesta de pie). Las caras se reparten *simulando la partida al revés*: se quitan una y otra vez dos posiciones libres en ese momento y reciben una pareja igual. Jugar en ese orden gana. Mezclar repite el proceso con las fichas que quedan. | Todo reparto y toda mezcla tienen solución |
| Tuberías | Árbol generador aleatorio que crece desde la fuente central (Prim aleatorio, evitando cruces de 4 salidas) y luego cada pieza girada al azar. | Conectado, sin circuitos, con solución; nunca empieza resuelto |
| Une los colores | Un camino hamiltoniano (serpentina) aleatorizado con miles de movimientos "backbite" y luego cortado en tramos de colores de 3+ casillas cuyos extremos no se tocan. | Tiene solución con el tablero entero lleno |
| Crucigrama | Palabras (las más largas primero) colocadas solo donde cruzan letras existentes, sin rozar de lado otras palabras; la mejor de hasta 60 intentos; numeradas como en un crucigrama impreso. | Sin choques de letras; número de palabras por nivel |
| Puntos | Colores aleatorios; reposición tras la "gravedad"; el tablero se baraja si no hay jugada. | Siempre hay una jugada |
| Solitario / Buraco | Barajado justo de una / dos barajas (`kotlin.random`). | — |

## Rivales del ordenador

Tal como se pidió, primero se consideró un motor ya hecho:

- **GNU Backgammon** es el motor de backgammon de código abierto de referencia, pero está escrito en C bajo GPL y habría que compilarlo con el NDK para cada tipo de procesador, lo que añadiría megas y obligaría a toda la aplicación a usar la GPL.
- Para el **Buraco** no existe ningún motor de código abierto consolidado (solo proyectos personales y debates en foros).

Por eso los rivales se escribieron específicamente para esta aplicación, en Kotlin puro:

**Reversi**: minimax con poda alfa-beta. Evaluación: tabla de pesos por posición (esquinas +100, casillas junto a las esquinas −20/−50), movilidad y recuento de fichas al final. Fácil: profundidad 1 con un 35 % de jugadas al azar. Medio: profundidad 3 (8 en el final). Difícil: profundidad 6 y búsqueda completa de las 12 últimas casillas vacías (final perfecto).

**Backgammon**: generación completa de jugadas (entrar desde la barra, capturar, sacar fichas con la regla del dado mayor, regla de "usar los dos dados / el mayor") sobre una vista relativa a cada jugador. Evaluación: diferencia en el recuento de puntos (pips), puntos hechos ponderados por su importancia, longitud del bloqueo, anclas, exposición de las fichas solas estimada por cuántas de las 36 tiradas las alcanzan, fichas en la barra y una evaluación de carrera aparte cuando ya no hay contacto. Fácil: juega al azar el 45 % de las veces. Medio: la mejor evaluación tras su propia jugada. Difícil: toma los 5 mejores candidatos y mira una tirada por delante, promediando la mejor respuesta del rival en las 21 tiradas posibles (expectimax).

**Buraco**: jugador heurístico: decide si toma el pozo (carta superior utilizable al momento, valor de todo el pozo), baja primero las escaleras naturales, añade cartas a sus escaleras, usa los comodines según el nivel (el Difícil mantiene limpias sus canastas limpias) y descarta la carta con menor "valor de conservar" (vecinas en la mano, encaje con sus escaleras) más un "coste de peligro" para las cartas que completarían tus escaleras o coinciden con cartas que has recogido.

### Benchmarks

Ordenador contra ordenador, generado por `AiBenchmarkTest` (opcional):

| Juego | Difícil vs Fácil | Medio vs Fácil | Difícil vs Medio |
|---|---|---|---|
| Buraco (100 partidas) | 92 % de victorias, +903 pts de media | 92 %, +925 pts | 52 %, +77 pts |
| Backgammon (60 partidas) | 98 % | 100 % | 48 % |
| Reversi (20 partidas) | 100 % | 95 % | 95 % |

Medio y Difícil quedan cerca cuando dos ordenadores juegan al Backgammon o al Buraco; ambos son mucho más fuertes que Fácil. Contra personas, el conocimiento extra del Difícil (mirar por delante en Backgammon, memoria de lo que recoges y descartes defensivos en Buraco) pesa más que contra otro programa.

## Internacionalización

- Los textos de la interfaz están en `res/values/strings.xml` (inglés) y en `values-pt`, `values-es`, `values-fr`, `values-zh`.
- El cambio de idioma dentro de la aplicación envuelve el árbol de Compose en un contexto creado con `createConfigurationContext`, así que funciona en todas las versiones de Android sin AppCompat. En Android 13+ los idiomas también aparecen en los ajustes de idioma por aplicación del sistema (`locales_config.xml`).
- Solo se empaquetan estos cinco idiomas (`localeFilters`), y la división por idioma está desactivada en los app bundles, así que cambiar de idioma nunca necesita descargas.
- Los diccionarios del crucigrama son texto plano: `RESPUESTA|pista`. Las respuestas se normalizan (mayúsculas, sin tildes, Ñ conservada en español, Œ → OE); en chino, cada carácter es una casilla.

## Pruebas

| Clase de prueba | Qué cubre |
|---|---|
| `SudokuEngineTest`, `SolitaireTest`, `ReversiTest`, `MahjongTest`, `PuzzleGeneratorsTest`, `CrosswordTest`, `BackgammonTest`, `BuracoTest` | Reglas y garantías de los generadores, velocidad y corrección de la IA |
| `TranslationsTest` | Todas las claves traducidas en todos los idiomas, marcadores idénticos, ningún apóstrofo sin escapar |
| `AppFlowTest` (Robolectric) | Guardar y recuperar, guardado dañado, persistencia de ajustes, zoom con dos dedos activado/desactivado, zoom por doble toque y por mantener y girar, elegir y abandonar un juego, la pregunta "¿continuar?" |
| `ScreenshotTest` (opcional) | Dibuja todas las pantallas y juegos en `docs/screenshots` |
| `AiBenchmarkTest` (opcional) | La tabla de arriba |
