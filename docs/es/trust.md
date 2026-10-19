# Confianza, privacidad y seguridad

🌐 [English](../en/trust.md) · [Português](../pt-BR/trust.md) · **Español** · [Français](../fr/trust.md) · [Italiano](../it/trust.md) · [Deutsch](../de/trust.md) · [Русский](../ru/trust.md) · [日本語](../ja/trust.md) · [中文](../zh-CN/trust.md)

Mili Games está pensada para instalarse en los teléfonos de personas que quizá no puedan valorar una aplicación por sí mismas. Esta página explica qué hace y qué no hace la aplicación y, sobre todo, **cómo cualquiera puede comprobarlo** sin tener que fiarse del autor.

## Contenido

1. [Resumen](#resumen)
2. [Privacidad](#privacidad)
3. [Permisos](#permisos)
4. [Qué hay dentro de la aplicación](#qué-hay-dentro-de-la-aplicación)
5. [Cómo se verifica el código](#cómo-se-verifica-el-código)
6. [Comprobar un APK descargado](#comprobar-un-apk-descargado)
7. [Protección de la cadena de suministro](#protección-de-la-cadena-de-suministro)
8. [Ayuda de IA, con transparencia](#ayuda-de-ia-con-transparencia)
9. [Informar de un problema de seguridad](#informar-de-un-problema-de-seguridad)

## Resumen

| | |
|---|---|
| Acceso a internet | **Ninguno** (no hay permiso `INTERNET`) |
| Datos recogidos | **Ninguno** |
| Anuncios, analítica, informes de fallos, rastreadores | **Ninguno** |
| Cuentas, inicios de sesión, compras | **Ninguno** |
| Permisos de Android solicitados | **Ninguno** |
| Código de terceros | Solo bibliotecas oficiales AndroidX (Google) y Kotlin (JetBrains) |
| Licencia | MIT, código fuente público |

## Privacidad

La aplicación guarda exactamente dos cosas, ambas en su almacenamiento **privado**, que otras aplicaciones no pueden leer:

1. `settings.xml`: lo que eliges en Ajustes (idioma, tamaños, colores…).
2. `current_game.json`: la partida en curso, para poder retomarla si la aplicación se cierra. Se borra cuando la partida termina o se abandona.

Si activaste la copia de seguridad del propio Android, este puede incluir esos dos archivos en *tu* copia de Google (y en la transferencia entre dispositivos), para que un teléfono nuevo conserve tus ajustes. Esto se configura en [`backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) y [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml). No se guarda ni se envía nada más.

El botón "Abrir la página del proyecto" de Ajustes → Acerca de solo pide a Android que abra la página de GitHub en tu navegador; la aplicación en sí sigue sin hacer ninguna conexión.

## Permisos

El [manifiesto](../../app/src/main/AndroidManifest.xml) **no** declara ningún `<uses-permission>`. Se puede comprobar en cualquier APK con el Android SDK:

```bash
aapt2 dump permissions MiliGames-v1.0.0.apk
```

Aparece una única entrada que termina en `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No es un permiso de usuario: es un permiso privado, de nivel de firma, que la biblioteca AndroidX añade automáticamente para proteger los receptores internos de la propia aplicación frente a otras aplicaciones. No concede nada a nadie.

Sin el permiso `INTERNET`, Android no deja que la aplicación abra ninguna conexión de red.

## Qué hay dentro de la aplicación

La lista completa de dependencias está en [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml):

| Biblioteca | Editor | Para qué |
|---|---|---|
| `androidx.activity:activity-compose` | Google (AndroidX) | Muestra la interfaz Compose en la activity |
| `androidx.compose.ui`, `foundation`, `material3` | Google (AndroidX) | El kit de interfaz |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JetBrains | Guardar la partida en curso en JSON |
| Biblioteca estándar de Kotlin | JetBrains | El entorno de ejecución del lenguaje |

Bibliotecas solo de pruebas (nunca se incluyen en la aplicación): JUnit, Robolectric, Compose UI test.

**No hay imágenes, sonidos, fuentes ni bibliotecas nativas** en la aplicación: cada dibujo lo hace un código que puedes leer en [`ui/art`](../../app/src/main/java/io/github/fershibli/megagames/ui/art). Los únicos archivos de datos son los diccionarios del crucigrama en texto plano ([`assets/crossword`](../../app/src/main/assets/crossword)).

La versión final también desactiva el bloque de "información de dependencias" de Google (`dependenciesInfo { includeInApk = false }`), un contenido cifrado que, si no, se incrustaría en el APK. Así, cada byte del APK se puede inspeccionar.

## Cómo se verifica el código

Cada push y pull request ejecuta el [CI](../../.github/workflows/ci.yml) en GitHub Actions:

1. **Validación del Gradle wrapper**: confirma que `gradle-wrapper.jar` es el binario oficial.
2. **50 pruebas automáticas**, entre ellas:
   - las reglas de todos los juegos (jugadas válidas, puntuación, sacar fichas, escaleras y comodines del Buraco…);
   - los generadores: todo Sudoku tiene solución única, todo reparto de Mahjong tiene solución, todo Tuberías y todo Une los colores tienen solución completa, los crucigramas nunca se contradicen;
   - los rivales del ordenador terminan sus partidas rápido y nunca pierden ni duplican una carta o ficha;
   - los nueve idiomas traducen todos los textos y conservan los mismos marcadores;
   - guardar y recuperar la partida, archivo de guardado dañado, persistencia de los ajustes;
   - la interfaz real: zoom con dos dedos activado/desactivado, zoom de una mano por doble toque y por mantener y girar, elegir un juego, salir de él desde Ajustes y la pregunta "¿continuar?" tras volver a abrir.
3. **Android Lint** tratando *las advertencias como errores*.
4. **Compilación** de los APK de depuración y final, publicados como artefactos con **sumas SHA-256**.

Ejecuta tú mismo las mismas comprobaciones: ver [building.md](building.md).

## Comprobar un APK descargado

1. Descarga `MiliGames-vX.Y.Z.apk` y `SHA256SUMS.txt` de la misma [versión](https://github.com/fershibli/game-mega-app/releases).
2. Calcula el checksum y compáralo:

   ```bash
   sha256sum MiliGames-vX.Y.Z.apk      # Linux / macOS: shasum -a 256
   certutil -hashfile MiliGames-vX.Y.Z.apk SHA256   # Windows
   ```

3. Comprueba que el certificado de firma es el mismo entre versiones (Android además se niega a actualizar una aplicación firmada con otra clave):

   ```bash
   apksigner verify --print-certs MiliGames-vX.Y.Z.apk
   ```

4. Para tener plena garantía, compílala tú mismo desde el código de la etiqueta y compara el contenido (`unzip -l`, `aapt2 dump badging`).

## Protección de la cadena de suministro

- `distributionSha256Sum` fija la distribución exacta de Gradle en [`gradle-wrapper.properties`](../../gradle/wrapper/gradle-wrapper.properties).
- Las dependencias solo vienen del repositorio Maven de Google y de Maven Central (`repositoriesMode = FAIL_ON_PROJECT_REPOS`).
- [Dependabot](../../.github/dependabot.yml) propone cada mes actualizaciones de dependencias y de GitHub Actions.
- El CI funciona con permisos de solo lectura; solo los workflows de versionado y despliegue pueden escribir, y únicamente para subir la etiqueta de versión y publicar la Release. El workflow de la clave de firma solo escribe los secretos de firma, con un token limitado a los secretos de este repositorio.
- Cada APK publicado se verifica en el pipeline antes de subirlo: firma (`apksigner`), número de versión y ausencia de permisos (`aapt2`).
- Las claves de firma están en los secretos cifrados de GitHub y nunca se suben al repositorio (`*.jks` y `keystore.properties` están en `.gitignore`).

## Ayuda de IA, con transparencia

El código, el arte y la documentación se crearon con la ayuda de **Claude**, el asistente de IA de Anthropic, bajo la dirección del responsable del proyecto. El código escrito con IA puede tener errores como cualquier otro; por eso este proyecto se apoya en cosas que puedes comprobar, no en promesas: código legible, una amplia batería de pruebas, lint, compilaciones reproducibles y checksums publicados. Si encuentras un problema, avísanos.

## Informar de un problema de seguridad

Ver [SECURITY.md](../../SECURITY.md). Por favor, no abras una incidencia pública para una vulnerabilidad; usa el aviso privado de vulnerabilidades de GitHub.
