# Compiler, publier et contribuer

🌐 [English](../en/building.md) · [Português](../pt-BR/building.md) · [Español](../es/building.md) · **Français** · [中文](../zh-CN/building.md)

## Sommaire

1. [Prérequis](#prérequis)
2. [Compiler et lancer](#compiler-et-lancer)
3. [Tests et lint](#tests-et-lint)
4. [Captures d’écran](#captures-décran)
5. [Benchmarks de l’IA](#benchmarks-de-lia)
6. [Versions signées](#versions-signées)
7. [Contribuer](#contribuer)
8. [Ajouter des mots aux mots croisés](#ajouter-des-mots-aux-mots-croisés)
9. [Ajouter ou améliorer une traduction](#ajouter-ou-améliorer-une-traduction)
10. [Ajouter un jeu](#ajouter-un-jeu)

## Prérequis

- **JDK 17 ou plus récent** (la CI utilise Temurin 21).
- **SDK Android** avec la plateforme **API 37** (Android Studio l’installe ; ou `sdkmanager "platforms;android-37"`).
- Un accès à Internet lors de la première compilation (Gradle se télécharge lui-même ainsi que les dépendances).

Indiquez le SDK avec `ANDROID_HOME` ou avec un fichier `local.properties` contenant `sdk.dir=/chemin/vers/Android/sdk`.

## Compiler et lancer

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # installe sur un téléphone/émulateur connecté (débogage USB activé)
./gradlew assembleRelease    # réduit par R8 ; non signé si aucune clé n’est configurée
```

Ou ouvrez le dossier dans **Android Studio** et appuyez sur *Run*.

## Tests et lint

```bash
./gradlew testDebugUnitTest   # 50 tests : règles, générateurs, IA, traductions, parcours d’interface
./gradlew lintDebug           # Android Lint, les avertissements sont des erreurs
```

Rapports : `app/build/reports/tests/testDebugUnitTest/index.html` et `app/build/reports/lint-results-debug.html`.

Les tests d’interface utilisent **Robolectric** : ils s’exécutent sur la JVM, sans émulateur.

## Captures d’écran

Toutes les images du README sont dessinées à partir du vrai code :

```bash
./gradlew :app:testDebugUnitTest -Pscreenshots=true --tests '*ScreenshotTest*'
# les PNG sont écrits dans docs/screenshots/
```

Réduisez-les ensuite avant le commit : `python3 scripts/optimize_screenshots.py` (nécessite Pillow).

Pour en ajouter une, ajoutez un `@Test` dans `app/src/test/.../ScreenshotTest.kt` avec `shot(...)` ou `gameShot(...)`.

## Benchmarks de l’IA

```bash
./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*AiBenchmarkTest*' -i | grep BENCHMARK
```

Fait jouer les adversaires de l’ordinateur entre eux (Buraco, Backgammon, Reversi) et affiche les taux de victoire. Les résultats figurent dans [technical.md](technical.md#benchmarks).

## Versions signées

Android n’installe que des APK signés, et les mises à jour doivent être signées avec la **même clé**. Conservez bien la clé : si elle est perdue, les utilisateurs devront désinstaller l’application pour installer une nouvelle version.

1. **Créez la clé une seule fois** (gardez le fichier et les mots de passe dans un gestionnaire de mots de passe) :

   ```bash
   keytool -genkeypair -v -keystore megagames-release.jks -alias megagames \
     -keyalg RSA -keysize 4096 -validity 36500
   ```

2. **Signature locale** : créez `keystore.properties` à la racine du projet (ignoré par git) :

   ```properties
   storeFile=megagames-release.jks
   storePassword=...
   keyAlias=megagames
   keyPassword=...
   ```

   Ensuite `./gradlew assembleRelease` produit `app/build/outputs/apk/release/app-release.apk`.

3. **Versions depuis GitHub** : ajoutez quatre secrets au dépôt (*Settings → Secrets and variables → Actions*) :

   | Secret | Valeur |
   |---|---|
   | `MEGAGAMES_KEYSTORE_BASE64` | résultat de `base64 -w0 megagames-release.jks` |
   | `MEGAGAMES_KEYSTORE_PASSWORD` | mot de passe du magasin |
   | `MEGAGAMES_KEY_ALIAS` | `megagames` |
   | `MEGAGAMES_KEY_PASSWORD` | mot de passe de la clé |

   Augmentez `versionCode` et `versionName` dans `app/build.gradle.kts`, mettez à jour le [CHANGELOG.md](../../CHANGELOG.md), puis :

   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```

   Le [workflow de publication](../../.github/workflows/release.yml) teste, compile, signe et publie `MegaGames-v1.0.0.apk` avec `SHA256SUMS.txt`.

## Contribuer

Les contributions sont les bienvenues — surtout de la part de celles et ceux qui jouent à ces jeux avec des proches âgés.

1. Ouvrez une issue décrivant le problème ou l’idée (les captures d’écran aident).
2. Faites un fork, créez une branche, faites la modification.
3. Lancez `./gradlew testDebugUnitTest lintDebug` — la CI lance la même chose.
4. Ouvrez une pull request en expliquant *pourquoi* la modification aide les joueurs.

Principes :

- **L’accessibilité d’abord** : zones tactiles ≥ 56 dp, actions principales dans la moitié basse de l’écran et inversées en mode gaucher (`ActionBar`), jamais la couleur seule (utilisez `drawSymbol` dans les palettes accessibles), pas de chronomètre ni de pression temporelle.
- **Rester petite et respectueuse** : aucune nouvelle autorisation, pas de réseau, pas de statistiques, pas d’images (dessinez en code), pas de nouvelle dépendance sans une très bonne raison.
- **Les moteurs sont en Kotlin pur** avec des tests ; l’interface ne fait que dessiner l’état et appeler le moteur.
- Chaque texte visible va dans `strings.xml` **dans les cinq langues** (`TranslationsTest` l’impose).
- Commentaires et noms dans le code en anglais ; suivez le style existant (`.editorconfig`, style officiel Kotlin).

## Ajouter des mots aux mots croisés

Les dictionnaires sont en texte brut dans `app/src/main/assets/crossword/<langue>.txt`, une entrée par ligne :

```
RÉPONSE|Une définition courte et bienveillante
```

- Des mots du quotidien de 2 à 13 lettres (chinois : 2 à 4 caractères), sans noms propres demandant des connaissances particulières.
- Les définitions doivent être bienveillantes et sans ambiguïté — pensez à une personne de quatre-vingts ans.
- Les accents sont permis dans les réponses ; ils sont retirés automatiquement (l’espagnol garde le Ñ).
- Les lignes commençant par `#` sont des commentaires. `CrosswordTest` vérifie que chaque dictionnaire garde au moins 120 mots utilisables.

## Ajouter ou améliorer une traduction

1. Copiez `app/src/main/res/values/strings.xml` vers `values-<code>/strings.xml` et traduisez chaque `<string>` et `<plurals>` (gardez les marqueurs du type `%1$d` ; échappez les apostrophes en `\'` ou utilisez l’apostrophe typographique `’`).
2. Ajoutez la langue à `AppLanguage` (`core/Settings.kt`), à `localeFilters` (`app/build.gradle.kts`) et à `res/xml/locales_config.xml`.
3. Créez un dictionnaire `assets/crossword/<code>.txt` et branchez-le dans `dictionaryFile()`.
4. Traduisez le README et les quatre documents de `docs/<code>/`, et ajoutez-les à [docs/README.md](../README.md).
5. Lancez les tests.

## Ajouter un jeu

1. Ajoutez une entrée à `GameId` (sa `key` est enregistrée dans les sauvegardes : ne la changez jamais ensuite) avec ses textes de titre et de règles.
2. Créez `games/<nom>/` avec un moteur en Kotlin pur (état `@Serializable`) et des tests.
3. Écrivez l’écran avec `LoadGame(host, State.serializer(), create = { ... })` et `GameLayout(status, controls) { plateau }` ; appelez `host.finish(GameOutcome(...))` à la fin.
4. Branchez-le dans `GameContent.kt` et dessinez sa vignette dans `ui/art/Thumbnails.kt`.
5. Ajoutez un test de capture d’écran et documentez les règles dans les cinq guides d’utilisation.
