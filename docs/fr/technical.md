# Conception technique

🌐 [English](../en/technical.md) · [Português](../pt-BR/technical.md) · [Español](../es/technical.md) · **Français** · [中文](../zh-CN/technical.md)

## Sommaire

1. [Choix de la technologie](#choix-de-la-technologie)
2. [Architecture](#architecture)
3. [Structure du projet](#structure-du-projet)
4. [Navigation, réglages et sauvegarde automatique](#navigation-réglages-et-sauvegarde-automatique)
5. [Zoom et gestes à une main](#zoom-et-gestes-à-une-main)
6. [Des illustrations sans images](#des-illustrations-sans-images)
7. [Générateurs de grilles](#générateurs-de-grilles)
8. [Adversaires de l’ordinateur](#adversaires-de-lordinateur)
9. [Internationalisation](#internationalisation)
10. [Tests](#tests)

## Choix de la technologie

La demande était : « React Native si possible, mais choisis ce qui est le plus léger en taille, en mémoire et en processeur ; Android uniquement ». Les options ont été comparées :

| | React Native (Hermes) | Flutter | **Kotlin + Jetpack Compose** |
|---|---|---|---|
| APK final de cette application | ~15–25 Mo (moteur JS + cœur RN par ABI) | ~8–12 Mo (moteur par ABI) | **~1,3 Mo** (mesuré) |
| Environnement supplémentaire en mémoire | moteur JS + pont | VM Dart + Skia/Impeller | aucun (s’exécute sur ART) |
| Dessiner des plateaux / zoom à deux doigts | nécessite des bibliothèques en plus (Skia, gesture handler, reanimated) | intégré | intégré (`Canvas`, entrées tactiles) |
| Accessibilité native d’Android (TalkBack, taille de police) | via un pont | implémentation propre | directe |

*Les tailles pour React Native et Flutter sont des valeurs typiques pour de petites applications ; seule la taille avec Compose a été mesurée sur ce projet.*

**Décision :** **Kotlin + Jetpack Compose** natif, une seule activité, sans moteur de jeu ni images. Compose fait partie des bibliothèques de la plateforme Android (AndroidX) : l’APK ne contient que ce qui est réellement utilisé après la réduction par R8. L’APK final mesuré pèse environ **1,3 Mo**.

D’autres choix gardent l’application petite et vérifiable :

- Pas de bibliothèque de navigation : la navigation est une interface scellée à quatre états.
- Pas de DataStore/Room : les réglages utilisent `SharedPreferences` ; l’unique partie enregistrée est un fichier JSON écrit de façon atomique.
- Pas de police d’icônes ni d’images : les icônes et toutes les illustrations des jeux sont dessinées avec `DrawScope`.
- Android 6.0 minimum (API 23), pour les anciens téléphones dont héritent souvent les personnes âgées.

## Architecture

```
MainActivity
 └─ MegaGamesApp (machine à états : Accueil → Difficulté → En jeu, + Réglages)
     ├─ MegaGamesTheme ── palette (6 variantes), langue, taille du texte, réglages en CompositionLocals
     ├─ HomeScreen / DifficultyScreen / SettingsScreen
     └─ GameScreen ── barre du haut + roue dentée, réglages en jeu, dialogues « continuer ? » et de fin de partie
          └─ GameContent(jeu) ── un composable par jeu
               └─ LoadGame(host, serializer, create)   ← restaure ou génère l’état, enregistre chaque changement
                    └─ GameLayout(statut, ZoomableBoard(plateau), commandes)
```

Chaque jeu est découpé en :

- un **moteur** — Kotlin pur, sans dépendance à Android, état immuable `@Serializable`, entièrement testé ;
- un **écran** — interface Compose qui dessine l’état dans un `Canvas` et transforme les touchers en appels au moteur.

Les adversaires de l’ordinateur s’exécutent sur `Dispatchers.Default` pour que l’écran ne se fige jamais, et montrent leurs coups pas à pas.

## Structure du projet

```
app/src/main/java/io/github/fershibli/megagames/
├── MainActivity.kt
├── core/            Settings, SettingsStore, SaveStore + GameHost (sauvegarde), GameId, Difficulty
├── ui/
│   ├── MegaGamesApp.kt   navigation
│   ├── theme/            Palette.kt (6 palettes), Theme.kt (langue, taille du texte)
│   ├── components/       boutons, barre du haut, roue dentée flottante, roue de choix, ZoomableBoard, GameLayout
│   ├── art/              cartes, couleurs, tuiles de mahjong, blocs en relief, disques brillants, vignettes
│   └── screens/          Accueil, Difficulté, Réglages, Jeu
└── games/
    ├── cards/  sudoku/  solitaire/  buraco/  backgammon/  mahjong/
    └── reversi/  crossword/  pipes/  connect/  dots/
app/src/main/assets/crossword/   en.txt pt.txt es.txt fr.txt zh.txt   (mot|définition)
app/src/main/res/values*/        textes en 5 langues
app/src/test/                     tests unitaires, parcours d’interface, traductions, captures et benchmarks
```

## Navigation, réglages et sauvegarde automatique

- La **navigation** est une `sealed interface Screen`. Le bouton Retour du téléphone va de Réglages → Accueil, de Difficulté → Accueil et, dans un jeu, ouvre/ferme les réglages de la partie (personne ne quitte une partie par erreur).
- Les **réglages** sont une `data class` immuable enregistrée dans `SharedPreferences` à chaque changement et transmise à toute l’interface par des `CompositionLocal`s.
- **Sauvegarde automatique** : `LoadGame` place l’état du jeu dans un `MutableState` et l’observe avec `snapshotFlow` ; chaque nouvel état est converti en JSON et transmis à `SaveStore`, qui écrit sur un unique fil d’arrière-plan, *la dernière écriture l’emporte*, via un fichier temporaire + renommage (un plantage ne laisse jamais un fichier à moitié écrit). `GameHost.finish()` supprime la sauvegarde : une partie terminée n’est plus proposée.
- **« Continuer ? »** : lors d’un démarrage à froid (`savedInstanceState == null`), `MegaGamesApp` charge la sauvegarde et ouvre directement le jeu avec la question. Une sauvegarde illisible (corrompue ou d’une version incompatible) est ignorée et une nouvelle partie est générée.
- L’activité gère elle-même les changements de configuration (`android:configChanges`) : faire pivoter, plier l’appareil ou changer le mode sombre ne recrée jamais l’écran et ne perd jamais l’état.

## Zoom et gestes à une main

`ZoomableBoard` enveloppe tous les plateaux. Il écoute la passe **Initial** des événements tactiles, avant le jeu :

- Deux doigts ou plus (si le *zoom à deux doigts* est activé) → zoom autour du centre des doigts et déplacement ; ces événements sont consommés pour que le jeu ne reçoive jamais un toucher parasite.
- Un doigt → transmis tel quel ; les jeux utilisent des coordonnées locales normales car le plateau est transformé par `graphicsLayer` (Compose convertit automatiquement les coordonnées du toucher).
- Une main, *double toucher* : le second toucher d’un double toucher est consommé ; toucher = palier de zoom (1× → 2× → 3× → 1×), toucher et glisser = déplacement.
- Une main, *appuyer et tourner* : après le délai d’appui long (avec vibration), l’angle du doigt autour du point d’appui pilote un zoom exponentiel (sens horaire = agrandir). Le double toucher revient à la normale ; double toucher et glisser déplace.

Le déplacement est limité pour que le plateau ne sorte jamais de l’écran, et un bouton de retour apparaît pendant le zoom.

## Des illustrations sans images

Toutes les illustrations sont dessinées à l’exécution avec `DrawScope` de Compose : elles restent nettes à tous les niveaux de zoom et changent de couleur d’elles-mêmes dans chacune des six palettes.

- **Relief** : `raisedBlock()` dessine des couches latérales du clair au foncé et une face en dégradé (tuiles de mahjong, dés) ; `glossyDisc()` dessine une ombre, un dégradé radial, une arête et un reflet (pions, disques de reversi, points) ; les cartes ont une ombre douce en plusieurs couches.
- **Cartes** : les couleurs sont des tracés vectoriels ; les valeurs sont grandes dans les deux coins supérieurs pour rester lisibles quand les cartes se chevauchent ; les figures ont une couronne ; la palette daltonisme utilise un jeu à quatre couleurs ; le français utilise R/D/V pour Roi/Dame/Valet.
- **Mahjong** : ronds, bambous (avec l’oiseau traditionnel sur le 1), caractères (chiffres chinois + 萬), vents, dragons, fleurs et saisons, plus un index occidental dans le coin.
- **Palettes** : Normal, Contraste élevé et Daltonisme (Okabe–Ito), chacune claire et sombre. Dans les palettes accessibles, chaque pièce colorée porte aussi une forme (`drawSymbol`).

## Générateurs de grilles

| Jeu | Méthode | Garantie (testée) |
|---|---|---|
| Sudoku | Grille complète aléatoire par retour arrière ; retrait des chiffres dans un ordre aléatoire, chaque retrait n’étant conservé que si un compteur de solutions (retour arrière par la case la plus contrainte, arrêt à 2) trouve encore exactement une solution. | Solution unique ; nombre d’indices par niveau |
| Mahjong | Dispositions fixes (tour 48, forteresse 96, tortue 144 redressée). Les faces sont attribuées en *simulant la partie à l’envers* : on retire à plusieurs reprises deux positions libres à ce moment et on leur donne une paire identique. Jouer dans cet ordre gagne. Mélanger refait la même chose avec les tuiles restantes. | Chaque donne et chaque mélange ont une solution |
| Tuyaux | Arbre couvrant aléatoire qui pousse depuis la source centrale (Prim aléatoire, en évitant les croisements à 4 sorties), puis chaque pièce tournée au hasard. | Connexe, sans boucle, résoluble ; jamais déjà résolu |
| Relie les couleurs | Un chemin hamiltonien (serpentin) rendu aléatoire par des milliers de mouvements « backbite », puis découpé en tronçons colorés de 3 cases ou plus dont les extrémités ne se touchent pas. | Résoluble avec tout le plateau rempli |
| Mots croisés | Mots (les plus longs d’abord) placés uniquement là où ils croisent des lettres existantes, sans jamais toucher d’autres mots de côté ; meilleure de 60 tentatives au plus ; numérotés comme une grille imprimée. | Aucun conflit de lettres ; nombre de mots par niveau |
| Points | Couleurs aléatoires ; remplissage après la « gravité » ; le plateau est mélangé s’il n’y a aucun coup. | Il existe toujours un coup |
| Solitaire / Buraco | Mélange équitable d’un / deux jeux (`kotlin.random`). | — |

## Adversaires de l’ordinateur

Comme demandé, un moteur existant a d’abord été envisagé :

- **GNU Backgammon** est le moteur de backgammon open source de référence, mais il est écrit en C sous GPL et aurait dû être compilé avec le NDK pour chaque type de processeur, ajoutant des mégaoctets et imposant la GPL à toute l’application.
- Pour le **Buraco**, il n’existe aucun moteur open source établi (seulement des projets personnels et des discussions de forum).

Les adversaires ont donc été écrits spécialement pour cette application, en Kotlin pur :

**Reversi** — minimax avec élagage alpha-bêta. Évaluation : table de poids par case (coins +100, cases voisines des coins −20/−50), mobilité, et nombre de pions en fin de partie. Facile : profondeur 1 avec 35 % de coups au hasard. Moyen : profondeur 3 (8 en fin de partie). Difficile : profondeur 6, et recherche complète des 12 dernières cases vides (fin de partie parfaite).

**Backgammon** — génération complète des coups (rentrée depuis la barre, prise, sortie avec la règle du dé supérieur, règle « utiliser les deux dés / le plus grand ») sur une vue relative à chaque camp. Évaluation : écart de compte de points (pips), cases faites pondérées par leur importance, longueur du blocage, ancres, exposition des pions isolés estimée par le nombre des 36 lancers qui les touchent, pions sur la barre, et une évaluation de course séparée quand le contact est rompu. Facile : joue au hasard 45 % du temps. Moyen : meilleure évaluation après son propre coup. Difficile : retient les 5 meilleurs candidats et anticipe un lancer, en faisant la moyenne de la meilleure réponse adverse sur les 21 lancers possibles (expectimax).

**Buraco** — joueur heuristique : décide s’il prend la défausse (carte du dessus utilisable tout de suite, valeur de toute la défausse), pose d’abord les suites naturelles, ajoute des cartes à ses suites, place les jokers selon le niveau (Difficile garde propres ses canastas propres) et défausse la carte à la plus faible « valeur de conservation » (voisines en main, place dans ses suites) augmentée d’un « coût de danger » pour les cartes qui prolongeraient vos suites ou correspondent à des cartes que vous avez ramassées.

### Benchmarks

Ordinateur contre ordinateur, produit par `AiBenchmarkTest` (facultatif) :

| Jeu | Difficile vs Facile | Moyen vs Facile | Difficile vs Moyen |
|---|---|---|---|
| Buraco (100 parties) | 92 % de victoires, +903 pts en moyenne | 92 %, +925 pts | 52 %, +77 pts |
| Backgammon (60 parties) | 98 % | 100 % | 48 % |
| Reversi (20 parties) | 100 % | 95 % | 95 % |

Moyen et Difficile sont proches quand deux ordinateurs jouent au Backgammon ou au Buraco — tous deux sont bien plus forts que Facile. Face à des personnes, les connaissances supplémentaires du niveau Difficile (anticipation au Backgammon, mémoire de vos ramassages et défausses défensives au Buraco) comptent davantage que face à un autre programme.

## Internationalisation

- Les textes de l’interface sont dans `res/values/strings.xml` (anglais) et dans `values-pt`, `values-es`, `values-fr`, `values-zh`.
- Le changement de langue dans l’application enveloppe l’arbre Compose dans un contexte créé avec `createConfigurationContext` : cela fonctionne sur toutes les versions d’Android sans AppCompat. Sur Android 13+, les langues apparaissent aussi dans les réglages de langue par application du système (`locales_config.xml`).
- Seules ces cinq langues sont empaquetées (`localeFilters`), et le découpage par langue est désactivé pour les app bundles : changer de langue ne nécessite jamais de téléchargement.
- Les dictionnaires des mots croisés sont en texte brut : `RÉPONSE|définition`. Les réponses sont normalisées (majuscules, sans accents, Ñ conservé en espagnol et Й en russe, Œ → OE, Ä Ö Ü ß allemands → AE OE UE SS, Ё russe → Е ; le japonais passe en katakana, petits kana en taille normale) ; en chinois et en japonais, chaque caractère occupe une case.

## Tests

| Classe de test | Ce qu’elle couvre |
|---|---|
| `SudokuEngineTest`, `SolitaireTest`, `ReversiTest`, `MahjongTest`, `PuzzleGeneratorsTest`, `CrosswordTest`, `BackgammonTest`, `BuracoTest` | Règles et garanties des générateurs, rapidité et justesse de l’IA |
| `TranslationsTest` | Toutes les clés traduites dans toutes les langues, marqueurs identiques, aucune apostrophe non échappée |
| `AppFlowTest` (Robolectric) | Enregistrement et relecture, sauvegarde corrompue, persistance des réglages, zoom à deux doigts activé/désactivé, zoom par double toucher et par appuyer-tourner, choix et abandon d’un jeu, question « continuer ? » |
| `ScreenshotTest` (facultatif) | Dessine tous les écrans et jeux dans `docs/screenshots` |
| `AiBenchmarkTest` (facultatif) | Le tableau ci-dessus |
