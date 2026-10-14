# Compilar, publicar y contribuir

🌐 [English](../en/building.md) · [Português](../pt-BR/building.md) · **Español** · [Français](../fr/building.md) · [中文](../zh-CN/building.md)

## Contenido

1. [Requisitos](#requisitos)
2. [Compilar y ejecutar](#compilar-y-ejecutar)
3. [Pruebas y lint](#pruebas-y-lint)
4. [Capturas](#capturas)
5. [Benchmarks de la IA](#benchmarks-de-la-ia)
6. [Versiones firmadas](#versiones-firmadas)
7. [Cómo contribuir](#cómo-contribuir)
8. [Añadir palabras al crucigrama](#añadir-palabras-al-crucigrama)
9. [Añadir o mejorar una traducción](#añadir-o-mejorar-una-traducción)
10. [Añadir un juego](#añadir-un-juego)

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

   Sube `versionCode` y `versionName` en `app/build.gradle.kts`, actualiza el [CHANGELOG.md](../../CHANGELOG.md) y luego:

   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```

   El [workflow de versiones](../../.github/workflows/release.yml) prueba, compila, firma y publica `MegaGames-v1.0.0.apk` con `SHA256SUMS.txt`.

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
