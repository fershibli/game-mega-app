# Compilar, publicar y contribuir

🌐 [English](../en/building.md) · [Português](../pt-BR/building.md) · **Español** · [Français](../fr/building.md) · [中文](../zh-CN/building.md)

## Contenido

1. [Requisitos](#requisitos)
2. [Compilar y ejecutar](#compilar-y-ejecutar)
3. [Pruebas y lint](#pruebas-y-lint)
4. [Capturas](#capturas)
5. [Benchmarks de la IA](#benchmarks-de-la-ia)
6. [Versiones firmadas](#versiones-firmadas)
7. [Versionado y despliegue](#versionado-y-despliegue)
8. [Cómo contribuir](#cómo-contribuir)
9. [Añadir palabras al crucigrama](#añadir-palabras-al-crucigrama)
10. [Añadir o mejorar una traducción](#añadir-o-mejorar-una-traducción)
11. [Añadir un juego](#añadir-un-juego)

## Requisitos

- **JDK 17 o posterior** (el CI usa Temurin 21).
- **Android SDK** con la plataforma **API 37** (Android Studio la instala; o `sdkmanager "platforms;android-37"`).
- Internet en la primera compilación (Gradle se descarga a sí mismo y las dependencias).

Indica el SDK con `ANDROID_HOME` o con un archivo `local.properties` que contenga `sdk.dir=/ruta/al/Android/sdk`.

## Compilar y ejecutar

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # instala en un teléfono/emulador conectado (depuración USB activada)
./gradlew assembleRelease    # reducido con R8; sin firmar si no hay clave configurada
```

O abre la carpeta en **Android Studio** y pulsa *Run*.

## Pruebas y lint

```bash
./gradlew testDebugUnitTest   # 50 pruebas: reglas, generadores, IA, traducciones, flujo de interfaz
./gradlew lintDebug           # Android Lint, las advertencias son errores
```

Informes: `app/build/reports/tests/testDebugUnitTest/index.html` y `app/build/reports/lint-results-debug.html`.

Las pruebas de interfaz usan **Robolectric**, así que se ejecutan en la JVM, sin emulador.

## Capturas

Todas las imágenes del README se dibujan desde el código real:

```bash
./gradlew :app:testDebugUnitTest -Pscreenshots=true --tests '*ScreenshotTest*'
# los PNG se guardan en docs/screenshots/
```

Después, redúcelas antes del commit: `python3 scripts/optimize_screenshots.py` (necesita Pillow).

Para añadir una, añade un `@Test` en `app/src/test/.../ScreenshotTest.kt` usando `shot(...)` o `gameShot(...)`.

## Benchmarks de la IA

```bash
./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*AiBenchmarkTest*' -i | grep BENCHMARK
```

Enfrenta a los rivales del ordenador entre sí (Buraco, Backgammon, Reversi) y muestra los porcentajes de victoria. Los resultados están en [technical.md](technical.md#benchmarks).

## Versiones firmadas

Android solo instala APK firmados, y las actualizaciones deben firmarse con la **misma clave**. Guarda bien la clave: si se pierde, quienes usen la aplicación tendrán que desinstalarla para instalar una versión nueva.

1. **Crea la clave una sola vez** (guarda el archivo y las contraseñas en un gestor de contraseñas):

   ```bash
   keytool -genkeypair -v -keystore megagames-release.jks -alias megagames \
     -keyalg RSA -keysize 4096 -validity 36500
   ```

2. **Firma local**: crea `keystore.properties` en la raíz del proyecto (git lo ignora):

   ```properties
   storeFile=megagames-release.jks
   storePassword=...
   keyAlias=megagames
   keyPassword=...
   ```

   Después `./gradlew assembleRelease` genera `app/build/outputs/apk/release/app-release.apk`.

3. **Versiones desde GitHub**: crea cuatro secretos en el repositorio (*Settings → Secrets and variables → Actions*):

   | Secreto | Valor |
   |---|---|
   | `MEGAGAMES_KEYSTORE_BASE64` | salida de `base64 -w0 megagames-release.jks` |
   | `MEGAGAMES_KEYSTORE_PASSWORD` | contraseña del almacén |
   | `MEGAGAMES_KEY_ALIAS` | `megagames` |
   | `MEGAGAMES_KEY_PASSWORD` | contraseña de la clave |

   Con estos secretos, el [pipeline de despliegue](#versionado-y-despliegue) firma cada APK con la clave real. Sin ellos, las versiones candidatas (`stag`) se siguen publicando, firmadas con una clave de depuración temporal y señaladas así en las notas de la versión, pero una versión estable (`main`) no se publica: la ejecución falla antes de crear la etiqueta, y basta con añadir los secretos y volver a ejecutarla.

## Versionado y despliegue

Las versiones siguen el [Versionado Semántico](https://semver.org/lang/es/) (`MAYOR.MENOR.PARCHE`) y se calculan **automáticamente** a partir de los [Conventional Commits](https://www.conventionalcommits.org/es/) con nuestro propio workflow, [`versioning.yml`](../../.github/workflows/versioning.yml) (bash puro, sin herramientas de terceros). Después llama a [`deploy.yml`](../../.github/workflows/deploy.yml), que genera y publica el APK.

| Mensaje del commit | Efecto | Ejemplo |
|---|---|---|
| `feat!: …` / `fix!: …` o `BREAKING CHANGE:` en el cuerpo | **MAYOR** | 1.4.2 → 2.0.0 |
| `feat: …` / `feat(sudoku): …` | **MENOR** | 1.4.2 → 1.5.0 |
| `fix:`, `perf:`, `revert:`, `refactor:`, `build:` | **PARCHE** | 1.4.2 → 1.4.3 |
| `docs:`, `chore:`, `ci:`, `test:`, `style:` o sin prefijo | sin versión nueva | — |

La primera versión es `1.0.0`. Ramas:

| Push a | Resultado |
|---|---|
| `main` (o `master`) | versión estable `v1.5.0`, marcada como *Latest* |
| `stag` | candidata `v1.5.0-rc.1`, `-rc.2`… marcada como *Pre-release* (solo se crea una rc nueva si llega algo publicable) |

Qué ocurre en cada versión:

1. `versioning.yml` lee los commits desde la última etiqueta estable, elige el incremento, redacta notas agrupadas (cambios incompatibles, novedades, correcciones, rendimiento, otros) y sube una etiqueta anotada `vX.Y.Z`.
2. `deploy.yml` hace checkout de esa etiqueta, ejecuta las pruebas y el lint, compila el APK final con `-PappVersion=X.Y.Z`, lo firma, lo **verifica** (firma, `versionName`, ningún permiso solicitado) y publica una Release en GitHub con `MegaGames-vX.Y.Z.apk`, `SHA256SUMS.txt` y las notas.

El `versionCode` de Android se deriva de la versión para que siempre aumente: `MAYOR×1.000.000 + MENOR×10.000 + PARCHE×100 + n`, donde `n` es el número de la rc, o 99 en la versión final (así `1.5.0-rc.2` < `1.5.0`). MENOR y PARCHE deben ser menores que 100.

Otras formas de desplegar:

- **Actions → Semantic versioning → Run workflow** y elige `patch`, `minor` o `major` para forzar una versión.
- **Actions → Deploy APK → Run workflow** con un número de versión para (re)generar una versión concreta.
- Sube una etiqueta a mano: `git tag v1.2.3 && git push origin v1.2.3` (GitHub genera entonces las notas).

En local, `./gradlew assembleRelease -PappVersion=1.2.3` compila esa versión; sin `-PappVersion` la versión es `1.0.0`.

## Cómo contribuir

Las contribuciones son bienvenidas, sobre todo de quienes juegan a estos juegos con familiares mayores.

1. Abre una incidencia describiendo el problema o la idea (las capturas ayudan).
2. Haz un fork, crea una rama y haz el cambio.
3. Ejecuta `./gradlew testDebugUnitTest lintDebug`; el CI ejecuta lo mismo.
4. Abre un pull request explicando *por qué* el cambio ayuda a quien juega.

Pautas:

- **La accesibilidad primero**: zonas táctiles ≥ 56 dp, acciones principales en la mitad inferior de la pantalla y reflejadas en el modo zurdo (`ActionBar`), nunca solo el color (usa `drawSymbol` en las paletas accesibles), sin cronómetros ni prisas.
- **Seguir siendo pequeña y privada**: ningún permiso nuevo, nada de red, nada de analítica, ninguna imagen (dibuja con código), ninguna dependencia nueva sin un muy buen motivo.
- **Los motores son Kotlin puro** con pruebas; la interfaz solo dibuja el estado y llama al motor.
- Todo texto visible va a `strings.xml` **en los cinco idiomas** (`TranslationsTest` lo exige).
- Escribe los mensajes de commit como [Conventional Commits](https://www.conventionalcommits.org/es/) (`feat: …`, `fix: …`, `docs: …`): deciden la próxima versión (ver [Versionado y despliegue](#versionado-y-despliegue)).
- Comentarios y nombres en el código en inglés; sigue el estilo existente (`.editorconfig`, estilo oficial de Kotlin).

## Añadir palabras al crucigrama

Los diccionarios son texto plano en `app/src/main/assets/crossword/<idioma>.txt`, una entrada por línea:

```
RESPUESTA|Una pista corta y amable
```

- Palabras cotidianas de 2 a 13 letras (chino: 2 a 4 caracteres), sin nombres propios que exijan conocimientos específicos.
- Las pistas deben ser amables y sin ambigüedad: piensa en alguien de ochenta años.
- Se pueden usar tildes en las respuestas; se quitan automáticamente (el español conserva la Ñ).
- Las líneas que empiezan por `#` son comentarios. `CrosswordTest` comprueba que cada diccionario siga teniendo al menos 120 palabras utilizables.

## Añadir o mejorar una traducción

1. Copia `app/src/main/res/values/strings.xml` a `values-<código>/strings.xml` y traduce todos los `<string>` y `<plurals>` (conserva los marcadores tipo `%1$d`; escapa los apóstrofos como `\'` o usa el tipográfico `’`).
2. Añade el idioma a `AppLanguage` (`core/Settings.kt`), a `localeFilters` (`app/build.gradle.kts`) y a `res/xml/locales_config.xml`.
3. Crea un diccionario `assets/crossword/<código>.txt` y conéctalo en `dictionaryFile()`.
4. Traduce el README y los cuatro documentos de `docs/<código>/`, y añádelos a [docs/README.md](../README.md).
5. Ejecuta las pruebas.

## Añadir un juego

1. Crea una entrada en `GameId` (su `key` se guarda en los archivos de partida: no la cambies nunca después) con los textos de título y de cómo jugar.
2. Crea `games/<nombre>/` con un motor en Kotlin puro (estado `@Serializable`) y pruebas.
3. Escribe la pantalla con `LoadGame(host, State.serializer(), create = { ... })` y `GameLayout(status, controls) { tablero }`; llama a `host.finish(GameOutcome(...))` al final.
4. Conéctalo en `GameContent.kt` y dibuja su miniatura en `ui/art/Thumbnails.kt`.
5. Añade una prueba de captura y documenta las reglas en las cinco guías de uso.
