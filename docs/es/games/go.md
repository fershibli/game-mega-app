# Go

🌐 [English](../../en/games/go.md) · [Português](../../pt-BR/games/go.md) · **Español** · [Français](../../fr/games/go.md) · [Italiano](../../it/games/go.md) · [Deutsch](../../de/games/go.md) · [Русский](../../ru/games/go.md) · [日本語](../../ja/games/go.md) · [中文](../../zh-CN/games/go.md) · [العربية](../../ar/games/go.md)

Un juego de tablero de Asia oriental, con miles de años, para dos jugadores: tú juegas con las piedras negras y el ordenador con las blancas. Las piedras se ponen en los cruces de las líneas y nunca se mueven. El objetivo es rodear más parte del tablero que el rival.

**Por turnos en este teléfono:** dos personas también pueden jugar una contra otra, sin el ordenador. El tablero nunca gira y la pantalla dice a quién le toca. Al final, deciden juntos qué piedras están muertas.

## Cómo se juega

- Toca un cruce para poner una piedra. En los tableros de 13×13 y 19×19 los cruces son pequeños: el primer toque muestra la piedra en tono claro y un segundo toque en el mismo cruce la coloca.
- Las piedras que se tocan por las líneas forman un grupo. Un grupo necesita al menos un cruce vacío al lado (una **libertad**). Cuando se ocupa su última libertad, el grupo queda **capturado** y sale del tablero.
- No puedes jugar donde tu piedra se quedaría sin libertades, salvo que capture algo.
- **Ko**: cuando se acaba de capturar una sola piedra, no se puede recapturar enseguida en el mismo sitio. Juega antes en otro lugar.
- Toca **Pasar** cuando no veas nada útil que jugar. Cuando los dos jugadores pasan uno tras otro, la partida termina.
- La última piedra jugada lleva un pequeño anillo. Las piedras capturadas se cuentan arriba.
- **Deshacer** anula tu última jugada (y la respuesta del ordenador). **Pista** marca un buen cruce.

## El recuento

- Usamos el recuento por área (reglas chinas): cada bando suma un punto por cada piedra suya en el tablero y por cada cruce vacío rodeado solo por sus piedras.
- Las blancas reciben 6,5 puntos extra (**komi**) por jugar en segundo lugar. El medio punto evita los empates.
- Tras los dos pases, el ordenador propone qué grupos están **muertos** (no podrían escapar de la captura): aparecen tachados, y el territorio se muestra con cuadraditos. Toca un grupo para cambiarlo entre muerto y vivo y luego toca **Contar**. Si no estás de acuerdo, toca **Seguir jugando** y resuélvelo en el tablero.

## Los tableros

- **9×9**: pequeño y rápido, lo mejor para empezar.
- **13×13**: más espacio para planes.
- **19×19**: el tablero clásico completo. Separa dos dedos para ampliar.

## Niveles

- **Fácil**: juega casi al azar, pero captura las piedras que solo tienen una libertad, a menudo salva las suyas y evita ponerse en peligro. Bueno para aprender las reglas.
- **Medio**: juega miles de partidas imaginarias rápidas hasta el final desde la posición actual y elige la jugada que más gana (una búsqueda de Montecarlo), durante un tercio de segundo.
- **Difícil**: la misma búsqueda durante unos dos tercios de segundo. En los tableros grandes ese tiempo da para menos partidas, así que ahí el ordenador juega peor.

El ordenador nunca rellena sus propios ojos. No hay piedras de hándicap: todos los niveles usan el mismo komi de 6,5.

## Consejos

- Empieza por las esquinas, luego los lados y al final el centro: en las esquinas hacen falta menos piedras para rodear.
- Un grupo con dos ojos separados (puntos vacíos dentro de él) no se puede capturar nunca.
- Cuando a un grupo tuyo le quede una sola libertad, sálvalo enseguida o déjalo; no sigas añadiendo piedras a un grupo perdido.
