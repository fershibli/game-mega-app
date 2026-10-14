# Mega Games — Guide d’utilisation

🌐 [English](../en/user-guide.md) · [Português](../pt-BR/user-guide.md) · [Español](../es/user-guide.md) · **Français** · [中文](../zh-CN/user-guide.md)

Ce guide s’adresse aux joueurs et aux proches qui préparent le téléphone pour eux.

## Sommaire

1. [Installation](#installation)
2. [Se déplacer dans l’application](#se-déplacer-dans-lapplication)
3. [Sauvegarde automatique et « continuer ? »](#sauvegarde-automatique-et--continuer-)
4. [Zoom](#zoom)
5. [Réglages](#réglages)
6. [Accessibilité](#accessibilité)
7. [Les jeux](#les-jeux)
   - [Sudoku](#sudoku) · [Solitaire](#solitaire) · [Buraco](#buraco) · [Backgammon](#backgammon) · [Mahjong](#mahjong)
   - [Reversi](#reversi) · [Mots croisés](#mots-croisés) · [Tuyaux](#tuyaux) · [Relie les couleurs](#relie-les-couleurs) · [Points](#points)
8. [Questions fréquentes](#questions-fréquentes)

---

## Installation

Mega Games n’est pas dans une boutique d’applications ; on l’installe depuis sa page GitHub. C’est sûr, à condition de le télécharger depuis la page officielle ci-dessous.

1. Sur le téléphone Android, ouvrez le navigateur et allez sur **https://github.com/fershibli/game-mega-app/releases**.
2. Dans la version la plus récente, touchez **`MegaGames-vX.Y.Z.apk`** pour le télécharger.
3. Ouvrez le fichier téléchargé (depuis la notification ou l’application *Fichiers / Téléchargements*).
4. Android peut indiquer que le navigateur « n’est pas autorisé à installer des applications inconnues ». Touchez **Paramètres**, activez **Autoriser cette source**, revenez et touchez **Installer**.
5. C’est prêt : l’icône **Mega Games** (une carte, un dé, une grille de sudoku et deux pions) apparaît avec vos autres applications.

**Mettre à jour :** téléchargez le nouvel APK et installez-le par-dessus l’ancien. Les réglages sont conservés.

**Vérification facultative :** chaque version contient un `SHA256SUMS.txt`. Un proche à l’aise avec l’informatique peut le comparer au fichier (`sha256sum MegaGames-*.apk`) pour s’assurer qu’il n’a pas été modifié.

Configuration requise : Android 6.0 ou plus récent, environ 5 Mo libres. L’application n’a jamais besoin d’Internet.

## Se déplacer dans l’application

L’application n’a que trois sortes d’écrans.

1. **Choisissez votre jeu.** Une grille d’images, deux par ligne. Il n’y a volontairement pas de texte : chaque image *est* le jeu (une grille de sudoku, des cartes, un plateau de backgammon…). La liste défile sans fin dans les deux sens : continuez et les jeux reviennent. Touchez une image pour la choisir.
2. **Choisissez la difficulté.** Une courte explication du jeu et, en dessous, une roue **Facile / Moyen / Difficile**. Votre difficulté par défaut est déjà sélectionnée. Tournez la roue (ou touchez un niveau) puis appuyez sur **Confirmer**. **Retour** revient aux jeux.
3. **Le jeu.** Le nom du jeu est en haut et la **roue dentée ⚙** dans le coin supérieur droit, exactement comme sur le premier écran. Les boutons du jeu (annuler, indice…) sont en bas, près du pouce.

La **roue dentée** ouvre les réglages. Dans un jeu, les réglages affichent aussi trois gros boutons en bas :

- **Retour au jeu** — ferme les réglages.
- **Nouvelle partie** — recommence une partie du même jeu (avec confirmation).
- **Menu principal** — quitte le jeu et revient à la liste. *Cela abandonne la partie* : l’application demande d’abord « Quitter cette partie ? », pour éviter toute fausse manœuvre.

Le bouton ou geste **Retour** du téléphone, dans un jeu, ouvre (ou ferme) les réglages : personne ne quitte une partie par erreur.

## Sauvegarde automatique et « continuer ? »

Chaque coup est enregistré immédiatement sur le téléphone. Si l’application se ferme — par erreur, à cause d’un appel, parce que la batterie est vide — à la prochaine ouverture elle va directement à cette partie et demande :

> **Bon retour !** Vous avez une partie de *Sudoku* en cours. Voulez-vous reprendre là où vous vous étiez arrêté ou revenir au menu principal ?

- **Continuer la partie** — tout est exactement comme avant, y compris le tour de l’ordinateur s’il était en train de jouer.
- **Menu principal** — la partie enregistrée est supprimée.

Une seule partie est conservée à la fois. Une partie terminée (gagnée ou perdue) n’est pas conservée.

## Zoom

Tous les plateaux peuvent être agrandis.

- **Zoom à deux doigts** (activé par défaut) : posez deux doigts sur le plateau et écartez-les pour agrandir, rapprochez-les pour réduire. Déplacez le plateau agrandi avec deux doigts. Un seul doigt joue toujours normalement.
- Pendant le zoom, un bouton (quatre coins) apparaît dans le coin inférieur pour **revenir à la taille normale**.
- En **mode une main**, deux doigts ne sont pas nécessaires ; voir plus bas.

## Réglages

| Réglage | Effet |
|---|---|
| **Langue** | English, Português, Español, Français, 中文, ou « Comme le téléphone ». Change toute l’application, y compris les mots croisés. |
| **Taille du texte** | De 80 % à 160 % de la taille normale. |
| **Taille des pièces et des boutons** | De 80 % à 160 %. Agrandit pavés numériques, claviers, cartes en main et autres commandes. Les plateaux occupent toujours l’écran ; utilisez le zoom pour les agrandir davantage. |
| **Couleurs** | *Normal* (doux, contraste moyen) · *Contraste élevé* (couleurs vives, contours épais) · *Adapté au daltonisme* (couleurs qui restent distinctes, formes dessinées sur les pièces, jeu de cartes à quatre couleurs). |
| **Mode sombre** | Fond sombre. Chaque mode de couleur a sa propre version sombre. |
| **Zoom à deux doigts** | Active ou désactive le zoom et le déplacement à deux doigts. |
| **Mode gaucher** | Place les boutons principaux, la roue dentée et le talon du Solitaire à gauche. |
| **Mode une main** | La roue dentée devient une bulle flottante près du pouce qui, après quelques secondes, se cache à moitié dans le bord (touchez une fois pour la faire revenir, une seconde fois pour ouvrir les réglages ; faites-la glisser vers le haut ou le bas). Le zoom fonctionne avec un seul doigt : |
| ↳ *Double toucher* | Touchez deux fois pour agrandir : 2×, puis 3×, puis retour à la normale. Touchez deux fois **et glissez** pour vous déplacer. |
| ↳ *Appuyer et tourner* | Gardez le doigt appuyé jusqu’à la vibration, puis faites des cercles : dans le sens des aiguilles d’une montre pour agrandir, dans l’autre sens pour réduire. Touchez deux fois et glissez pour vous déplacer ; touchez deux fois pour revenir à la normale. |
| **Difficulté par défaut** | Le niveau présélectionné sur la roue de difficulté. |
| **À propos** | Version, déclaration de confidentialité, lien vers ce projet et **Rétablir les réglages par défaut**. |

La liste des réglages commence assez bas sur l’écran, et chaque option peut être amenée près du pouce en faisant défiler.

## Accessibilité

Pensée pour les personnes âgées :

- **Grandes cibles** — les boutons font au moins 56 dp (environ 9 mm) de haut, plus que la recommandation d’Android.
- **Gros caractères par défaut**, réglables jusqu’à 160 %, en plus de la taille de police du téléphone.
- **Pas besoin de lire pour choisir un jeu**, mais chaque image a un nom lu par **TalkBack**.
- **Pensée pour le pouce** — actions en bas, inversées en mode gaucher, roue dentée flottante en mode une main.
- **Jamais la couleur seule** — en Contraste élevé et en mode Daltonisme, les pièces portent des formes (cercle, carré, triangle…), les cartes ont quatre couleurs et les contours sont plus épais.
- **Indulgente** — annulation illimitée dans la plupart des jeux, indices partout, aucun chronomètre, tuiles bloquées du Mahjong assombries, coups possibles mis en évidence au Reversi et au Backgammon.
- **Calme** — pas de sons, pas de clignotements, pas de publicité, pas de fenêtres surprises.

---

## Les jeux

### Sudoku

**But :** remplir la grille 9×9 pour que chaque ligne, colonne et carré 3×3 contienne les chiffres de 1 à 9 une seule fois.

**Jouer :** touchez une case, puis un chiffre en bas. La gomme efface. **Notes** transforme le pavé en petits pense-bêtes (annotations au crayon) ; quand vous posez un chiffre, cette note disparaît automatiquement de la même ligne, colonne et carré. **Indice** remplit la case choisie (ou une case au hasard) avec le bon chiffre. **Annuler** revient en arrière.

Aides : la ligne, la colonne et le carré de la case choisie sont ombrés, les chiffres identiques sont mis en évidence, et le bouton d’un chiffre s’estompe quand les neuf sont posés.

| Niveau | Chiffres de départ | Erreurs en rouge |
|---|---|---|
| Facile | 40 | oui |
| Moyen | 32 | oui |
| Difficile | 26 | seulement les conflits de règles |

Chaque grille est générée à neuf et a exactement **une** solution.

### Solitaire

**But :** Klondike — monter toutes les cartes sur les quatre piles du haut, de l’As au Roi, une pile par couleur.

**Jouer :** il suffit de **toucher une carte**. Elle va au meilleur endroit : sur une pile du haut si possible, sinon sur une colonne (couleurs alternées, valeur inférieure d’un cran), sinon dans une colonne vide (Rois seulement). Si elle ne peut pas bouger, elle clignote en rouge. Toucher une carte retournée au milieu d’une colonne la déplace avec celles qui sont dessus. Touchez le **talon** (en haut à droite, ou à gauche en mode gaucher) pour piocher ; quand il est vide, touchez-le pour retourner la défausse. **Indice** entoure un coup utile ; **Terminer** apparaît quand toutes les cartes sont retournées et finit la partie pour vous.

| Niveau | Pioche | Tours de talon |
|---|---|---|
| Facile | 1 carte | illimités |
| Moyen | 3 cartes | illimités |
| Difficile | 3 cartes | 3 |

### Buraco

Le Buraco brésilien à deux : vous contre l’ordinateur.

**Cartes :** deux jeux de 52 cartes (104 cartes). **Tous les 2 sont des jokers.**

**Distribution :** 11 cartes chacun ; deux paquets supplémentaires de 11 cartes, les **morts**, attendent sur le côté ; une carte commence la **défausse**.

**Votre tour :**

1. **Piochez** une carte **ou prenez toute la défausse** (boutons en bas, ou touchez la pioche/la défausse).
2. Si vous voulez, **posez des suites** : sélectionnez au moins 3 cartes **de la même couleur qui se suivent** (ex. 5♥ 6♥ 7♥) et appuyez sur **Nouvelle suite**. L’As peut être bas (A-2-3) ou haut (D-R-A). Une suite peut contenir **un** joker ; un 2 de la même couleur à sa place (A-2-3♥) compte comme une carte normale.
3. Si vous voulez, **ajoutez des cartes** à vos suites : sélectionnez-les et **touchez la suite**.
4. **Défaussez** une carte (sélectionnez-la, appuyez sur **Défausser**). C’est au tour de l’ordinateur.

**Canasta :** une suite de 7 cartes ou plus. **Propre** (sans joker) : bordure dorée, 200 points ; **sale** (avec joker) : bordure argentée, 100 points.

**Mort :** la première fois que votre main est vide, vous ramassez un mort et continuez à jouer.

**Terminer (« bater ») :** vous ne pouvez vider votre main qu’après avoir ramassé votre mort (ou s’il n’en reste plus) **et** avec au moins une **canasta propre**. L’application explique quand un coup est interdit et pourquoi (« gardez une carte à défausser », « il faut une canasta propre »).

**Fin et décompte :** la partie se termine quand quelqu’un termine ou quand la pioche est épuisée.

| Élément | Points |
|---|---|
| As dans une suite | 15 |
| 2 | 10 |
| 8, 9, 10, V, D, R | 10 |
| 3 à 7 | 5 |
| Canasta propre | +200 |
| Canasta sale | +100 |
| Terminer | +100 |
| Cartes restant en main | moins leur valeur |
| N’avoir jamais ramassé de mort | −100 |

**Annuler** reprend tout ce que vous avez posé depuis votre pioche. Niveaux de l’ordinateur : *Facile* joue sans trop réfléchir ; *Moyen* prend la défausse quand elle est utile, protège ses jokers et évite de défausser des cartes qui prolongent vos suites ; *Difficile* évalue en plus toute la défausse, garde propres ses canastas propres et se souvient de ce que vous avez ramassé.

### Backgammon

**But :** faire le tour du plateau avec vos 15 pions clairs jusqu’à votre jan intérieur (cases 1–6, en bas à gauche) puis les sortir tous avant l’ordinateur (pions foncés).

Le plateau est dessiné **debout** pour que les pions soient grands : les cases 12→1 descendent la colonne de gauche, 13→24 celle de droite, la **barre** traverse le milieu et le **plateau de sortie** est en bas (le vôtre à gauche).

**Jouer :** touchez **Lancer les dés**. Les pions qui peuvent bouger s’allument en **jaune** ; touchez-en un et ses destinations possibles s’allument en **vert** ; touchez la destination. Si un pion n’a qu’une destination possible, il y va directement. Quand tous les dés sont utilisés, touchez **Terminé**. **Annuler** recommence votre tour. Pour sortir un pion, touchez le plateau de sortie.

Toutes les règles habituelles s’appliquent : il faut utiliser les deux dés si possible (le plus grand si un seul peut être joué), les doubles se jouent quatre fois, un pion adverse seul peut être frappé et part sur la barre, et les pions sur la barre doivent rentrer d’abord. La victoire est un *gammon* (double) si le perdant n’a sorti aucun pion, et un *backgammon* (triple) s’il lui reste un pion sur la barre ou dans le jan du gagnant. Il n’y a pas de videau.

Niveaux de l’ordinateur : *Facile* joue souvent un coup au hasard ; *Moyen* évalue tous les coups possibles (course, cases sûres, pions exposés, blocages, ancres) ; *Difficile* anticipe en plus un lancer, en faisant la moyenne des 21 réponses possibles.

### Mahjong

**But :** retirer toutes les tuiles par paires identiques.

Une tuile est **libre** quand aucune tuile n’est posée dessus et que son côté gauche **ou** droit est dégagé. Touchez deux tuiles libres identiques pour les retirer. Deux **fleurs** quelconques forment une paire, de même que deux **saisons**. Chaque tuile de famille porte dans son coin un petit chiffre occidental (ou E/S/W/N pour les vents) : inutile de lire le chinois.

En Facile et Moyen, les tuiles **qui ne sont pas libres sont assombries**. **Indice** entoure une paire, **Annuler** remet la dernière paire, **Mélanger** redistribue les tuiles restantes quand vous êtes bloqué.

| Niveau | Tuiles | Mélanges |
|---|---|---|
| Facile | 48 (tour) | illimités |
| Moyen | 96 (forteresse) | 3 |
| Difficile | 144 (tortue classique) | 1 |

Chaque donne est construite pour **toujours avoir une solution**.

### Reversi

**But :** avoir plus de pions que l’ordinateur quand le plateau est plein. Vous jouez les pions foncés et commencez.

Posez un pion de façon à encadrer en ligne droite un ou plusieurs pions de l’ordinateur entre votre nouveau pion et un autre des vôtres : ils changent de couleur. Les coups possibles sont indiqués par de petits points ; le dernier coup de l’ordinateur porte un point rouge. Si quelqu’un ne peut pas jouer, il passe (l’application le signale).

**Indice** marque un bon coup. **Annuler** reprend votre dernier coup. Niveaux : profondeur 1 avec erreurs aléatoires / recherche de profondeur 3 / recherche de profondeur 6 qui joue parfaitement les 12 derniers coups.

### Mots croisés

Une **nouvelle grille est générée** à chaque partie à partir d’un dictionnaire de mots courants avec des définitions simples, dans la langue de l’application.

**Jouer :** touchez une case pour choisir son mot ; touchez à nouveau la même case pour passer d’horizontal à vertical. La définition s’affiche en bas, entre ◀ et ▶ (mot précédent / suivant). Écrivez avec les lettres du bas : après chaque lettre le curseur avance. La gomme efface une lettre. **Indice** remplit la case choisie.

Les accents sont ignorés comme dans les grilles imprimées (É = E, Ç = C, Œ = OE…) ; l’espagnol garde le Ñ. En chinois, chaque case contient un caractère.

| Niveau | Grille | Saisie | Erreurs en rouge |
|---|---|---|---|
| Facile | 9×9 (8×8 en chinois), ~7 mots (~6 en chinois) | réserve de lettres (celles du mot + 2) | oui |
| Moyen | 11×11 (10×10 en chinois), ~11 mots (~9) | clavier complet AZERTY (chinois : réserve + 5) | oui |
| Difficile | 13×13 (12×12 en chinois), ~16 mots (~12) | clavier complet AZERTY (chinois : réserve + 8) | non |

### Tuyaux

**But :** tourner les pièces pour que l’eau de la source ronde au centre arrive dans **toutes** les pièces, sans aucun tuyau dirigé vers un mur ou un côté fermé.

Touchez une pièce pour la tourner d’un quart de tour dans le sens des aiguilles d’une montre. Les pièces avec de l’eau deviennent bleues ; les extrémités sont de petits réservoirs qui se remplissent. **Indice** remet une mauvaise pièce en place. **Annuler** annule les rotations. Grille : 5×5 / 7×7 / 9×9. Le réseau est un « arbre » de tuyaux aléatoire, sans boucle, qui a toujours une solution.

### Relie les couleurs

**But :** tracer des lignes qui relient chaque paire de points de même couleur. Les lignes ne peuvent pas se croiser.

Posez le doigt sur un point et tracez à travers les cases (haut, bas, gauche, droite). Passer sur une autre ligne la coupe. Commencez sur une ligne pour la raccourcir. **Indice** trace une ligne correcte ; **Effacer** supprime toutes les lignes.

| Niveau | Plateau | Couleurs | Pour gagner |
|---|---|---|---|
| Facile | 5×5 | 4 | relier toutes les couleurs |
| Moyen | 7×7 | 6 | relier toutes les couleurs et remplir toutes les cases |
| Difficile | 9×9 | 8 | relier toutes les couleurs et remplir toutes les cases |

Les grilles sont générées en découpant un chemin aléatoire qui couvre tout le plateau : chacune peut être entièrement remplie.

### Points

**But :** atteindre les objectifs affichés en haut (par exemple ramasser 15 rouges, 15 bleus et 15 verts) avant d’épuiser vos coups.

Glissez le doigt sur des **points voisins de la même couleur** (haut, bas, gauche, droite ; au moins deux). Quand vous levez le doigt, ils disparaissent et de nouveaux points tombent d’en haut. **Fermez un carré** (revenez sur un point déjà dans votre ligne) pour effacer **tous les points de cette couleur** — le plateau s’illumine quand vous avez un carré. Les coups passent au rouge quand il en reste 3 ou moins. Facile : 6×6, 4 couleurs, 22 coups. Moyen : 7×7, 5 couleurs, 20 coups. Difficile : 8×8, 5 couleurs, 18 coups, quatre objectifs.

---

## Questions fréquentes

**Utilise-t-elle les données mobiles ou le Wi-Fi ?** Non. L’application n’a aucune autorisation Internet ; Android bloque toute connexion.

**Collecte-t-elle des données ?** Non. Les réglages et la partie en cours sont enregistrés uniquement sur le téléphone, dans l’espace privé de l’application. La sauvegarde d’Android (si vous l’avez activée) peut les copier sur *votre* compte Google.

**Pourquoi n’est-elle pas sur le Play Store ?** Peut-être plus tard. Pour l’instant, les versions sont publiées sur GitHub avec des sommes de contrôle.

**Le texte est trop petit / trop grand.** Réglages → Taille → Taille du texte. La taille de police du téléphone est aussi respectée.

**Puis-je jouer à deux jeux en même temps ?** Une seule partie est conservée. En commencer une nouvelle remplace celle enregistrée.

**L’ordinateur triche-t-il ?** Non. Il ne voit que ce qu’une personne verrait (sa propre main, la table, la défausse) et utilise les mêmes dés et mélanges. Son code est ouvert : voir [technical.md](technical.md#adversaires-de-lordinateur).

**J’ai trouvé un problème ou j’ai une idée.** Ouvrez une issue : https://github.com/fershibli/game-mega-app/issues
