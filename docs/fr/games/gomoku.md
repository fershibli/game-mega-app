# Gomoku (cinq en ligne)

🌐 [English](../../en/games/gomoku.md) · [Português](../../pt-BR/games/gomoku.md) · [Español](../../es/games/gomoku.md) · **Français** · [Italiano](../../it/games/gomoku.md) · [Deutsch](../../de/games/gomoku.md) · [Русский](../../ru/games/gomoku.md) · [日本語](../../ja/games/gomoku.md) · [中文](../../zh-CN/games/gomoku.md) · [العربية](../../ar/games/gomoku.md)

Un jeu pour deux sur les intersections d’un plateau 15×15. Vous jouez les noirs et commencez ; l’ordinateur joue les blancs. Le premier qui forme une ligne continue de cinq pierres ou plus, horizontalement, verticalement ou en diagonale, gagne.

## Comment jouer

- Touchez une intersection : une pierre transparente montre où elle ira. Touchez à nouveau la même intersection pour la poser, ou une autre pour changer d’avis.
- La dernière pierre jouée porte un petit anneau, et la ligne gagnante est mise en évidence.
- **Annuler** reprend votre dernier coup (et la réponse de l’ordinateur). **Indice** marque une bonne intersection.

## Règles

- Style libre : cinq pierres alignées ou plus gagnent, six ou plus aussi.
- Les restrictions de tournoi pour les noirs (pas de « double trois », pas de « double quatre », pas de ligne de plus de cinq) ne sont **pas** utilisées : les noirs, qui commencent, ont donc un avantage.
- Si le plateau est plein, la partie est nulle.

## Niveaux

- **Facile** : choisit l’intersection la plus prometteuse, mais parfois une moins bonne exprès, et peut même oublier de bloquer un quatre.
- **Moyen** : complète toujours ses quatre et bloque les vôtres, et prévoit quelques coups à l’avance.
- **Difficile** : regarde plus loin et cherche des gains par une suite de quatre, le tout en moins d’une seconde.

## Conseils

- Un **trois ouvert** (trois alignées avec les deux bouts libres) doit être bloqué tout de suite, sinon il devient un quatre ouvert qu’on ne peut plus arrêter.
- Deux menaces à la fois gagnent : essayez de faire un quatre et un trois ouvert avec une seule pierre.
