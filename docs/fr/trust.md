# Confiance, vie privée et sécurité

🌐 [English](../en/trust.md) · [Português](../pt-BR/trust.md) · [Español](../es/trust.md) · **Français** · [中文](../zh-CN/trust.md)

Mega Games est conçue pour être installée sur le téléphone de personnes qui ne peuvent pas toujours juger une application elles-mêmes. Cette page explique ce que fait l’application et ce qu’elle ne fait pas — et surtout **comment chacun peut le vérifier** sans avoir à faire confiance à l’auteur.

## Sommaire

1. [En bref](#en-bref)
2. [Vie privée](#vie-privée)
3. [Autorisations](#autorisations)
4. [Ce que contient l’application](#ce-que-contient-lapplication)
5. [Comment le code est vérifié](#comment-le-code-est-vérifié)
6. [Vérifier un APK téléchargé](#vérifier-un-apk-téléchargé)
7. [Protection de la chaîne d’approvisionnement](#protection-de-la-chaîne-dapprovisionnement)
8. [L’aide d’une IA, en toute transparence](#laide-dune-ia-en-toute-transparence)
9. [Signaler un problème de sécurité](#signaler-un-problème-de-sécurité)

## En bref

| | |
|---|---|
| Accès à Internet | **Aucun** (pas d’autorisation `INTERNET`) |
| Données collectées | **Aucune** |
| Publicité, statistiques, rapports de plantage, traceurs | **Aucun** |
| Comptes, connexions, achats | **Aucun** |
| Autorisations Android demandées | **Aucune** |
| Code tiers | Uniquement les bibliothèques officielles AndroidX (Google) et Kotlin (JetBrains) |
| Licence | MIT, code source public |

## Vie privée

L’application enregistre exactement deux choses, toutes deux dans son espace **privé**, que les autres applications ne peuvent pas lire :

1. `settings.xml` — vos choix dans les Réglages (langue, tailles, couleurs…).
2. `current_game.json` — la partie en cours, pour pouvoir la reprendre après une fermeture. Il est supprimé quand la partie se termine ou est abandonnée.

Si vous avez activé la sauvegarde d’Android, celle-ci peut inclure ces deux fichiers dans *votre* sauvegarde Google (et dans le transfert d’un appareil à l’autre), pour qu’un nouveau téléphone garde vos réglages. C’est configuré dans [`backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) et [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml). Rien d’autre n’est jamais enregistré ni envoyé.

Le bouton « Ouvrir la page du projet » (Réglages → À propos) demande simplement à Android d’ouvrir la page GitHub dans votre navigateur ; l’application elle-même n’établit toujours aucune connexion.

## Autorisations

Le [manifeste](../../app/src/main/AndroidManifest.xml) ne déclare **aucun** `<uses-permission>`. Vous pouvez le vérifier sur n’importe quel APK avec le SDK Android :

```bash
aapt2 dump permissions MegaGames-v1.0.0.apk
```

Une seule entrée apparaît, se terminant par `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Ce n’est pas une autorisation utilisateur : c’est une autorisation privée, au niveau de la signature, que la bibliothèque AndroidX ajoute automatiquement pour protéger les récepteurs internes de l’application contre les autres applications. Elle n’accorde rien à personne.

Sans l’autorisation `INTERNET`, Android ne laisse l’application ouvrir aucune connexion réseau.

## Ce que contient l’application

La liste complète des dépendances se trouve dans [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) :

| Bibliothèque | Éditeur | Rôle |
|---|---|---|
| `androidx.activity:activity-compose` | Google (AndroidX) | Affiche l’interface Compose dans l’activité |
| `androidx.compose.ui`, `foundation`, `material3` | Google (AndroidX) | La boîte à outils d’interface |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JetBrains | Enregistrer la partie en cours en JSON |
| Bibliothèque standard Kotlin | JetBrains | L’environnement d’exécution du langage |

Bibliothèques de test uniquement (jamais livrées) : JUnit, Robolectric, Compose UI test.

L’application ne contient **ni images, ni sons, ni polices, ni bibliothèques natives** : chaque dessin est produit par du code lisible dans [`ui/art`](../../app/src/main/java/io/github/fershibli/megagames/ui/art). Les seuls fichiers de données sont les dictionnaires des mots croisés, en texte brut ([`assets/crossword`](../../app/src/main/assets/crossword)).

La version finale désactive aussi le bloc « informations de dépendances » de Google (`dependenciesInfo { includeInApk = false }`), un contenu chiffré qui serait sinon intégré à l’APK. Chaque octet de l’APK peut donc être inspecté.

## Comment le code est vérifié

Chaque push et chaque pull request lancent la [CI](../../.github/workflows/ci.yml) sur GitHub Actions :

1. **Validation du Gradle wrapper** — vérifie que `gradle-wrapper.jar` est le binaire officiel.
2. **50 tests automatiques**, notamment :
   - les règles de tous les jeux (coups valides, décompte, sortie des pions, suites et jokers du Buraco…) ;
   - les générateurs : chaque Sudoku a une solution unique, chaque donne de Mahjong a une solution, chaque grille de Tuyaux et de Relie les couleurs a une solution complète, les mots croisés ne se contredisent jamais ;
   - les adversaires de l’ordinateur terminent rapidement leurs parties et ne perdent ni ne dupliquent jamais une carte ou un pion ;
   - les cinq langues traduisent tous les textes et conservent les mêmes marqueurs ;
   - enregistrement et relecture de la partie, fichier de sauvegarde corrompu, persistance des réglages ;
   - l’interface réelle : zoom à deux doigts activé/désactivé, zoom à une main par double toucher et par appuyer-tourner, choix d’un jeu, sortie par les Réglages et question « continuer ? » après un redémarrage.
3. **Android Lint** avec *les avertissements traités comme des erreurs*.
4. **Compilation** des APK de débogage et finaux, publiés comme artefacts avec leurs **sommes SHA-256**.

Lancez vous-même les mêmes vérifications : voir [building.md](building.md).

## Vérifier un APK téléchargé

1. Téléchargez `MegaGames-vX.Y.Z.apk` et `SHA256SUMS.txt` depuis la même [version](https://github.com/fershibli/game-mega-app/releases).
2. Calculez la somme de contrôle et comparez :

   ```bash
   sha256sum MegaGames-vX.Y.Z.apk      # Linux / macOS : shasum -a 256
   certutil -hashfile MegaGames-vX.Y.Z.apk SHA256   # Windows
   ```

3. Vérifiez que le certificat de signature reste le même d’une version à l’autre (Android refuse d’ailleurs de mettre à jour une application signée avec une autre clé) :

   ```bash
   apksigner verify --print-certs MegaGames-vX.Y.Z.apk
   ```

4. Pour une garantie totale, compilez-la vous-même à partir du code de l’étiquette et comparez le contenu (`unzip -l`, `aapt2 dump badging`).

## Protection de la chaîne d’approvisionnement

- `distributionSha256Sum` fige la distribution exacte de Gradle dans [`gradle-wrapper.properties`](../../gradle/wrapper/gradle-wrapper.properties).
- Les dépendances proviennent uniquement du dépôt Maven de Google et de Maven Central (`repositoriesMode = FAIL_ON_PROJECT_REPOS`).
- [Dependabot](../../.github/dependabot.yml) propose chaque mois les mises à jour des dépendances et des GitHub Actions.
- La CI s’exécute en lecture seule ; seuls les workflows de versionnage et de déploiement peuvent écrire, et uniquement pour pousser l’étiquette de version et publier la Release.
- Chaque APK publié est vérifié dans le pipeline avant l’envoi : signature (`apksigner`), numéro de version et absence d’autorisations (`aapt2`).
- Les clés de signature sont stockées dans les secrets chiffrés de GitHub et ne sont jamais versionnées (`*.jks` et `keystore.properties` sont dans `.gitignore`).

## L’aide d’une IA, en toute transparence

Le code, les illustrations et la documentation ont été créés avec l’aide de **Claude**, l’assistant d’IA d’Anthropic, sous la direction du mainteneur. Du code écrit avec une IA peut contenir des erreurs comme n’importe quel code ; c’est pourquoi ce projet repose sur ce que vous pouvez vérifier plutôt que sur des promesses : un code lisible, une large batterie de tests, lint, des compilations reproductibles et des sommes de contrôle publiées. Si vous trouvez un problème, merci de le signaler.

## Signaler un problème de sécurité

Voir [SECURITY.md](../../SECURITY.md). Merci de ne pas ouvrir d’issue publique pour une vulnérabilité ; utilisez le signalement privé de vulnérabilités de GitHub.
