# Go

🌐 [English](../../en/games/go.md) · [Português](../../pt-BR/games/go.md) · [Español](../../es/games/go.md) · **Français** · [Italiano](../../it/games/go.md) · [Deutsch](../../de/games/go.md) · [Русский](../../ru/games/go.md) · [日本語](../../ja/games/go.md) · [中文](../../zh-CN/games/go.md) · [العربية](../../ar/games/go.md)

Un jeu de plateau d’Asie de l’Est vieux de plusieurs milliers d’années, pour deux joueurs : vous jouez les pierres noires et l’ordinateur les blanches. Les pierres se posent sur les intersections des lignes et ne bougent jamais. Le but est d’entourer une plus grande partie du plateau que l’adversaire.

## Comment jouer

- Touchez une intersection pour poser une pierre. Sur les plateaux 13×13 et 19×19 les intersections sont petites : le premier toucher montre la pierre en transparence et un second toucher sur la même intersection la pose.
- Des pierres qui se touchent le long des lignes forment un groupe. Un groupe a besoin d’au moins une intersection vide à côté de lui (une **liberté**). Quand sa dernière liberté est occupée, le groupe est **capturé** et quitte le plateau.
- Vous ne pouvez pas jouer là où votre pierre n’aurait aucune liberté, sauf si elle capture quelque chose.
- **Ko** : quand une seule pierre vient d’être capturée, on ne peut pas la reprendre tout de suite au même endroit. Jouez d’abord ailleurs.
- Touchez **Passer** quand vous ne voyez rien d’utile à jouer. Quand les deux joueurs passent l’un après l’autre, la partie se termine.
- La dernière pierre jouée porte un petit anneau. Les pierres capturées sont comptées en haut.
- **Annuler** reprend votre dernier coup (et la réponse de l’ordinateur). **Indice** marque une bonne intersection.

## Le décompte

- Nous comptons par zone (règles chinoises) : chaque camp gagne un point par pierre à lui sur le plateau et par intersection vide entourée seulement par ses pierres.
- Les blancs reçoivent 6,5 points de plus (**komi**) parce qu’ils jouent en second. Le demi-point évite les égalités.
- Après les deux passes, l’ordinateur propose les groupes **morts** (ils ne pourraient pas échapper à la capture) : ils sont barrés, et le territoire est montré par de petits carrés. Touchez un groupe pour le faire passer de mort à vivant ou l’inverse, puis touchez **Compter**. Si vous n’êtes pas d’accord, touchez **Continuer** et réglez la question sur le plateau.

## Les plateaux

- **9×9** : petit et rapide, idéal pour commencer.
- **13×13** : plus de place pour les plans.
- **19×19** : le grand plateau classique. Écartez deux doigts pour zoomer.

## Niveaux

- **Facile** : joue presque au hasard, mais capture les pierres qui n’ont plus qu’une liberté, sauve souvent les siennes et évite de se mettre en danger. Bien pour apprendre les règles.
- **Moyen** : joue des milliers de parties imaginaires rapides jusqu’au bout depuis la position actuelle et choisit le coup qui gagne le plus souvent (une recherche de Monte-Carlo), pendant environ un tiers de seconde.
- **Difficile** : la même recherche pendant environ deux tiers de seconde. Sur les grands plateaux ce temps couvre moins de parties : l’ordinateur y est plus faible.

L’ordinateur ne remplit jamais ses propres yeux. Il n’y a pas de pierres de handicap : tous les niveaux utilisent le même komi de 6,5.

## Conseils

- Commencez par les coins, puis les bords, puis le centre : dans les coins il faut moins de pierres pour entourer.
- Un groupe avec deux yeux séparés (des points vides à l’intérieur) ne peut jamais être capturé.
- Quand un de vos groupes n’a plus qu’une liberté, sauvez-le tout de suite ou abandonnez-le ; n’ajoutez pas de pierres à un groupe perdu.
