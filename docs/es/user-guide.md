# Mega Games — Guía de uso

🌐 [English](../en/user-guide.md) · [Português](../pt-BR/user-guide.md) · **Español** · [Français](../fr/user-guide.md) · [中文](../zh-CN/user-guide.md)

Esta guía está escrita para quienes juegan y para los familiares que les preparan el teléfono.

## Contenido

1. [Instalación](#instalación)
2. [Cómo moverse por la aplicación](#cómo-moverse-por-la-aplicación)
3. [Guardado automático y "¿continuar?"](#guardado-automático-y-continuar)
4. [Zoom](#zoom)
5. [Ajustes](#ajustes)
6. [Accesibilidad](#accesibilidad)
7. [Los juegos](#los-juegos)
   - [Sudoku](#sudoku) · [Solitario](#solitario) · [Buraco](#buraco) · [Backgammon](#backgammon) · [Mahjong](#mahjong)
   - [Reversi](#reversi) · [Crucigrama](#crucigrama) · [Tuberías](#tuberías) · [Une los colores](#une-los-colores) · [Puntos](#puntos)
8. [Preguntas frecuentes](#preguntas-frecuentes)

---

## Instalación

Mega Games no está en una tienda de aplicaciones; se instala desde su página de GitHub. Es seguro siempre que lo descargues de la página oficial indicada abajo.

1. En el teléfono Android, abre el navegador y entra en **https://github.com/fershibli/game-mega-app/releases**.
2. En la versión más reciente, toca **`MegaGames-vX.Y.Z.apk`** para descargarlo.
3. Abre el archivo descargado (desde la notificación o desde la aplicación *Archivos / Descargas*).
4. Puede que Android diga que el navegador "no tiene permiso para instalar aplicaciones desconocidas". Toca **Ajustes**, activa **Permitir de esta fuente**, vuelve y toca **Instalar**.
5. Listo: el icono de **Mega Games** (una carta, un dado, un sudoku y dos fichas) aparece junto a tus otras aplicaciones.

**Para actualizar:** descarga el APK más nuevo e instálalo encima del anterior. Los ajustes se conservan.

**Comprobación opcional:** cada versión incluye un `SHA256SUMS.txt`. Un familiar que sepa de informática puede compararlo con el archivo (`sha256sum MegaGames-*.apk`) para asegurarse de que no se ha modificado.

Requisitos: Android 6.0 o posterior, unos 5 MB libres. La aplicación nunca necesita internet.

## Cómo moverse por la aplicación

La aplicación solo tiene tres tipos de pantalla.

1. **Elige tu juego.** Una cuadrícula de imágenes, dos por fila. No hay texto a propósito: cada imagen *es* el juego (un sudoku, cartas, un tablero de backgammon…). La lista se desplaza sin fin en ambos sentidos: si sigues bajando, los juegos vuelven a aparecer. Toca una imagen para elegirla.
2. **Elige la dificultad.** Una breve explicación del juego y, debajo, una rueda con **Fácil / Medio / Difícil**. Tu dificultad predeterminada ya está seleccionada. Gira la rueda (o toca un nivel) y pulsa **Confirmar**. **Volver** regresa a los juegos.
3. **El juego.** El nombre del juego está arriba y el **engranaje ⚙** en la esquina superior derecha, exactamente donde estaba en la primera pantalla. Los botones del juego (deshacer, pista…) están abajo, cerca del pulgar.

El **engranaje** abre los ajustes. Dentro de un juego, los ajustes muestran además tres botones grandes abajo:

- **Volver al juego**: cierra los ajustes.
- **Nuevo juego**: empieza otra partida del mismo juego (pide confirmación).
- **Menú principal**: sale del juego y vuelve a la lista. *Esto abandona la partida*: antes pregunta "¿Salir de este juego?", para que no ocurra sin querer.

El botón o gesto **Atrás** del teléfono, dentro de un juego, abre (o cierra) los ajustes, así nadie sale de una partida por error.

## Guardado automático y "¿continuar?"

Cada jugada se guarda al instante en el teléfono. Si la aplicación se cierra —sin querer, por una llamada, porque se acabó la batería— la próxima vez que se abra irá directamente a esa partida y preguntará:

> **¡Qué bueno verte de nuevo!** Tienes una partida de *Sudoku* sin terminar. ¿Quieres seguir donde lo dejaste o volver al menú principal?

- **Seguir jugando**: todo queda exactamente como estaba, incluido el turno del ordenador si estaba jugando.
- **Menú principal**: la partida guardada se descarta.

Solo se guarda una partida a la vez. Una partida terminada (ganada o perdida) no se guarda.

## Zoom

Todos los tableros se pueden ampliar.

- **Zoom con dos dedos** (activado de serie): pon dos dedos sobre el tablero y sepáralos para ampliar; júntalos para reducir. Mueve el tablero ampliado con dos dedos. Un dedo siempre juega con normalidad.
- Mientras hay zoom aparece en la esquina inferior un botón (cuatro esquinas) para **volver al tamaño normal**.
- En el **modo una mano** no hacen falta dos dedos; ver más abajo.

## Ajustes

| Ajuste | Qué hace |
|---|---|
| **Idioma** | English, Português, Español, Français, 中文, o "El mismo del teléfono". Cambia toda la aplicación, también las palabras del crucigrama. |
| **Tamaño del texto** | Del 80 % al 160 % del tamaño normal. |
| **Tamaño de piezas y botones** | Del 80 % al 160 %. Agranda teclados numéricos, teclados de letras, las cartas de tu mano y otros controles. Los tableros siempre ocupan la pantalla; usa el zoom para agrandarlos más. |
| **Colores** | *Normal* (suave, contraste medio) · *Alto contraste* (colores fuertes, bordes gruesos) · *Para daltonismo* (colores que se siguen distinguiendo, con formas dibujadas en las piezas y baraja de cuatro colores). |
| **Modo oscuro** | Fondo oscuro. Cada modo de color tiene su propia versión oscura. |
| **Zoom con dos dedos** | Activa o desactiva el zoom y el desplazamiento con dos dedos. |
| **Modo zurdo** | Lleva los botones principales, el engranaje y el mazo del Solitario al lado izquierdo. |
| **Modo una mano** | El engranaje se convierte en una burbuja flotante cerca del pulgar que, tras unos segundos, se esconde a medias en el borde (tócala una vez para sacarla y otra para abrir los ajustes; arrástrala arriba o abajo). El zoom funciona con un dedo: |
| ↳ *Doble toque* | Toca dos veces para acercar: 2×, luego 3×, luego vuelve a lo normal. Toca dos veces **y arrastra** para moverte. |
| ↳ *Mantener y girar* | Mantén el dedo hasta que el teléfono vibre y dibuja círculos: en sentido horario acerca, en sentido antihorario aleja. Toca dos veces y arrastra para moverte; toca dos veces para volver a lo normal. |
| **Dificultad predeterminada** | El nivel que aparece ya marcado en la rueda de dificultad. |
| **Acerca de** | Versión, aviso de privacidad, enlace a este proyecto y **Restablecer ajustes predeterminados**. |

La lista de ajustes empieza bastante abajo en la pantalla, y cualquier opción se puede desplazar hacia el pulgar.

## Accesibilidad

Pensada para personas mayores:

- **Objetivos grandes**: los botones miden al menos 56 dp (unos 9 mm) de alto, más de lo que recomienda Android.
- **Letra grande de serie**, ajustable hasta el 160 %, además del tamaño de letra del propio teléfono.
- **No hace falta leer para elegir un juego**, pero cada imagen tiene un nombre hablado para **TalkBack**.
- **Pensada para el pulgar**: acciones abajo, reflejadas en el modo zurdo, engranaje flotante en el modo una mano.
- **Nunca solo el color**: en los modos Alto contraste y Para daltonismo, las piezas llevan formas (círculo, cuadrado, triángulo…), las cartas usan cuatro colores de palo y los bordes son más gruesos.
- **Indulgente**: deshacer ilimitado en la mayoría de los juegos, pistas en todos, sin cronómetros, fichas bloqueadas del Mahjong oscurecidas, jugadas posibles resaltadas en Reversi y Backgammon.
- **Tranquila**: sin sonidos, sin parpadeos, sin anuncios, sin ventanas inesperadas.

---

## Los juegos

### Sudoku

**Objetivo:** completar la cuadrícula de 9×9 para que cada fila, columna y cuadro de 3×3 tenga los números del 1 al 9 una sola vez.

**Cómo se juega:** toca una casilla y luego un número de abajo. La goma borra. **Notas** convierte el teclado en recordatorios pequeños (anotaciones a lápiz); al colocar un número, esa nota desaparece sola de la misma fila, columna y cuadro. **Pista** rellena la casilla seleccionada (o una cualquiera) con el número correcto. **Deshacer** vuelve atrás.

Ayudas: la fila, la columna y el cuadro de la casilla seleccionada se sombrean, los números iguales se resaltan y el botón de un número se apaga cuando ya están los nueve.

| Nivel | Números iniciales | Errores en rojo |
|---|---|---|
| Fácil | 40 | sí |
| Medio | 32 | sí |
| Difícil | 26 | solo conflictos de reglas |

Cada sudoku se genera nuevo y tiene exactamente **una** solución.

### Solitario

**Objetivo:** Klondike: llevar todas las cartas a las cuatro pilas de arriba, del As al Rey, una pila por palo.

**Cómo se juega:** basta con **tocar una carta**. Va al mejor sitio: a una pila de arriba si puede; si no, a una columna (colores alternos, un valor menos); si no, a una columna vacía (solo Reyes). Si no puede moverse, parpadea en rojo. Tocar una carta descubierta dentro de una columna la mueve junto con las que tiene encima. Toca el **mazo** (arriba a la derecha, o a la izquierda en el modo zurdo) para robar; cuando se acaba, tócalo para volver a darle la vuelta. **Pista** marca una jugada útil; **Terminar** aparece cuando todas las cartas están descubiertas y completa la partida por ti.

| Nivel | Robo | Vueltas al mazo |
|---|---|---|
| Fácil | 1 carta | ilimitadas |
| Medio | 3 cartas | ilimitadas |
| Difícil | 3 cartas | 3 |

### Buraco

Buraco brasileño para dos: tú contra el ordenador.

**Cartas:** dos barajas de 52 cartas (104 cartas). **Todos los 2 son comodines.**

**Reparto:** 11 cartas para cada uno; dos montones extra de 11 cartas, los **muertos**, esperan a un lado; una carta empieza el **pozo** de descartes.

**Tu turno:**

1. **Roba** una carta del mazo **o toma todo el pozo** (botones de abajo, o toca el mazo o el pozo).
2. Si quieres, **baja escaleras**: selecciona 3 o más cartas del **mismo palo seguidas** (p. ej. 5♥ 6♥ 7♥) y pulsa **Nueva escalera**. El As puede ir abajo (A-2-3) o arriba (Q-K-A). Cada escalera puede llevar **un** comodín; un 2 del mismo palo en su propio sitio (A-2-3♥) cuenta como carta normal.
3. Si quieres, **añade cartas** a tus escaleras: selecciónalas y **toca la escalera**.
4. **Descarta** una carta (selecciónala y pulsa **Descartar**). El turno pasa al ordenador.

**Canasta:** una escalera de 7 cartas o más. **Limpia** (sin comodín) tiene borde dorado y vale 200; **sucia** (con comodín) tiene borde plateado y vale 100.

**Muerto:** la primera vez que te quedas sin cartas, recoges un muerto y sigues jugando.

**Cerrar ("bater"):** solo puedes quedarte sin cartas después de tener tu muerto (o si ya no queda ninguno) **y** con al menos una **canasta limpia**. La aplicación te avisa cuando una jugada no está permitida y por qué ("guarda una carta para descartar", "necesitas una canasta limpia").

**Final y puntuación:** la partida termina cuando alguien cierra o cuando se acaba el mazo.

| Concepto | Puntos |
|---|---|
| As en una escalera | 15 |
| 2 | 10 |
| 8, 9, 10, J, Q, K | 10 |
| del 3 al 7 | 5 |
| Canasta limpia | +200 |
| Canasta sucia | +100 |
| Cerrar | +100 |
| Cartas que quedan en la mano | se resta su valor |
| No haber recogido el muerto | −100 |

**Deshacer** retira todo lo que has bajado desde que robaste. Niveles del ordenador: *Fácil* juega sin mucho cuidado; *Medio* toma el pozo cuando le conviene, protege sus comodines y evita descartar cartas que encajan en tus escaleras; *Difícil* además valora el pozo entero, mantiene limpias sus canastas limpias y recuerda lo que has recogido.

### Backgammon

**Objetivo:** mover tus 15 fichas claras por el tablero hasta tu casa (puntos 1–6, abajo a la izquierda) y sacarlas todas antes que el ordenador (fichas oscuras).

El tablero se dibuja **de pie** para que las fichas sean grandes: los puntos 12→1 bajan por la columna izquierda, 13→24 por la derecha, la **barra** cruza el centro y la **bandeja** de fichas sacadas está abajo (la tuya a la izquierda).

**Cómo se juega:** toca **Tirar los dados**. Las fichas que pueden moverse se iluminan en **amarillo**; toca una y sus destinos posibles se iluminan en **verde**; toca el destino. Si una ficha solo tiene un destino posible, se mueve directamente. Cuando hayas usado todos los dados, toca **Listo**. **Deshacer** reinicia tu turno. Para sacar una ficha, toca la bandeja.

Se aplican todas las reglas habituales: hay que usar los dos dados si se puede (el mayor si solo se puede usar uno), los dobles se juegan cuatro veces, una ficha sola del rival puede ser capturada y va a la barra, y las fichas de la barra deben entrar primero. La victoria es un *gammon* (doble) si el perdedor no sacó ninguna ficha, y un *backgammon* (triple) si al perdedor aún le quedaba una ficha en la barra o en la casa del ganador. No hay cubo de doblaje.

Niveles del ordenador: *Fácil* a menudo hace una jugada al azar; *Medio* valora todas las jugadas posibles (carrera, puntos seguros, fichas expuestas, bloqueos, anclas); *Difícil* además mira una tirada por delante, promediando las 21 respuestas posibles.

### Mahjong

**Objetivo:** quitar todas las fichas en parejas iguales.

Una ficha está **libre** cuando no tiene ninguna ficha encima y su lado izquierdo **o** derecho está despejado. Toca dos fichas libres e iguales para quitarlas. Dos **flores** cualesquiera forman pareja, y dos **estaciones** cualesquiera también. Cada ficha de palo muestra en la esquina un pequeño número occidental (o E/S/W/N en los vientos), así que no hace falta leer chino.

En Fácil y Medio, las fichas **que no están libres se oscurecen**. **Pista** marca una pareja, **Deshacer** devuelve la última pareja, **Mezclar** reparte de nuevo las fichas que quedan cuando te atascas.

| Nivel | Fichas | Mezclas |
|---|---|---|
| Fácil | 48 (torre) | ilimitadas |
| Medio | 96 (fortaleza) | 3 |
| Difícil | 144 (tortuga clásica) | 1 |

Cada reparto se construye para que **siempre tenga solución**.

### Reversi

**Objetivo:** tener más fichas que el ordenador cuando el tablero se llene. Juegas con las fichas oscuras y empiezas tú.

Coloca una ficha de modo que una o más fichas del ordenador queden encerradas en línea recta entre tu ficha nueva y otra tuya: cambian a tu color. Las jugadas posibles se muestran como puntitos; la última jugada del ordenador lleva un punto rojo. Si alguien no puede jugar, pasa (la aplicación lo avisa).

**Pista** marca una buena jugada. **Deshacer** retira tu última jugada. Niveles: profundidad 1 con errores al azar / búsqueda de profundidad 3 / búsqueda de profundidad 6 que juega perfectamente los 12 últimos movimientos.

### Crucigrama

En cada partida se **genera un crucigrama nuevo** a partir de un diccionario de palabras cotidianas con pistas sencillas, en el idioma de la aplicación.

**Cómo se juega:** toca una casilla para elegir su palabra; vuelve a tocar la misma casilla para cambiar entre horizontal y vertical. La pista aparece abajo, entre ◀ y ▶ (palabra anterior / siguiente). Escribe con las letras de abajo: tras cada letra el cursor avanza. La goma borra una letra. **Pista** rellena la casilla seleccionada.

Las tildes se ignoran, como en los crucigramas impresos (É = E…); el español conserva la Ñ. En chino, cada casilla lleva un carácter.

| Nivel | Cuadrícula | Cómo se escribe | Errores en rojo |
|---|---|---|---|
| Fácil | 9×9 (8×8 en chino), ~7 palabras (~6 en chino) | banco de letras (las de la palabra + 2 más) | sí |
| Medio | 11×11 (10×10 en chino), ~11 palabras (~9) | teclado completo (chino: banco con 5 más) | sí |
| Difícil | 13×13 (12×12 en chino), ~16 palabras (~12) | teclado completo (chino: banco con 8 más) | no |

El teclado español incluye la Ñ; el francés usa AZERTY y los demás QWERTY.

### Tuberías

**Objetivo:** girar las piezas para que el agua de la fuente redonda del centro llegue a **todas** las piezas, sin ninguna tubería apuntando a la pared o a un lado cerrado.

Toca una pieza para girarla un cuarto de vuelta en sentido horario. Las piezas con agua se ponen azules; los extremos son pequeños depósitos que se llenan. **Pista** coloca bien una pieza equivocada. **Deshacer** revierte los giros. Cuadrícula: 5×5 / 7×7 / 9×9. El reto es un "árbol" de tuberías aleatorio, sin circuitos cerrados, y siempre tiene solución.

### Une los colores

**Objetivo:** dibujar líneas que unan cada pareja de puntos del mismo color. Las líneas no pueden cruzarse.

Pon el dedo en un punto y dibuja por las casillas (arriba, abajo, a los lados). Dibujar encima de otra línea la corta. Empieza sobre una línea para acortarla. **Pista** dibuja una línea correcta; **Borrar** elimina todas.

| Nivel | Tablero | Colores | Para ganar |
|---|---|---|---|
| Fácil | 5×5 | 4 | unir todos los colores |
| Medio | 7×7 | 6 | unir todos los colores y llenar todas las casillas |
| Difícil | 9×9 | 8 | unir todos los colores y llenar todas las casillas |

Los retos se generan cortando un camino aleatorio que recorre todo el tablero, así que todos se pueden llenar por completo.

### Puntos

**Objetivo:** cumplir los objetivos de arriba (por ejemplo, reunir 15 rojos, 15 azules y 15 verdes) antes de quedarte sin movimientos.

Desliza el dedo por **puntos vecinos del mismo color** (arriba, abajo, a los lados; al menos dos). Al levantar el dedo desaparecen y caen puntos nuevos desde arriba. **Cierra un cuadrado** (vuelve a un punto que ya está en tu línea) para eliminar **todos los puntos de ese color**: el tablero se ilumina cuando tienes un cuadrado. Los movimientos se ponen en rojo cuando quedan 3 o menos. Fácil: 6×6, 4 colores, 22 movimientos. Medio: 7×7, 5 colores, 20 movimientos. Difícil: 8×8, 5 colores, 18 movimientos, cuatro objetivos.

---

## Preguntas frecuentes

**¿Usa datos móviles o wifi?** No. La aplicación no tiene ningún permiso de internet; Android bloquea cualquier conexión.

**¿Recoge algún dato?** No. Los ajustes y la partida actual se guardan solo en el teléfono, en el almacenamiento privado de la aplicación. La copia de seguridad del propio Android (si la activaste) puede copiarlos a *tu* cuenta de Google.

**¿Por qué no está en Play Store?** Quizá lo esté en el futuro. De momento, las versiones se publican en GitHub con checksums.

**El texto es demasiado pequeño / grande.** Ajustes → Tamaño → Tamaño del texto. También se respeta el tamaño de letra del propio teléfono.

**¿Puedo jugar a dos juegos a la vez?** Solo se guarda una partida. Empezar una nueva sustituye a la guardada.

**¿El ordenador hace trampas?** No. El ordenador solo ve lo que vería una persona (su propia mano, la mesa, el pozo) y usa los mismos dados y barajados. Su código es abierto: ver [technical.md](technical.md#rivales-del-ordenador).

**He encontrado un problema o tengo una idea.** Abre una incidencia: https://github.com/fershibli/game-mega-app/issues
