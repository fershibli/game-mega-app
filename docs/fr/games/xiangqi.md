# Échecs chinois (Xiangqi)

🌐 [English](../../en/games/xiangqi.md) · [Português](../../pt-BR/games/xiangqi.md) · [Español](../../es/games/xiangqi.md) · **Français** · [Italiano](../../it/games/xiangqi.md) · [Deutsch](../../de/games/xiangqi.md) · [Русский](../../ru/games/xiangqi.md) · [日本語](../../ja/games/xiangqi.md) · [中文](../../zh-CN/games/xiangqi.md) · [العربية](../../ar/games/xiangqi.md)

Les échecs chinois sont le jeu de plateau le plus joué en Chine et au Vietnam. Deux armées se font face de part et d’autre d’une rivière ; celui qui enferme le **général** adverse gagne.

## Le plateau

- 9 lignes horizontales et 10 verticales. Les pièces se placent **sur les croisements**, pas dans les cases.
- La **rivière** traverse le milieu. Certaines pièces changent une fois qu’elles l’ont traversée.
- Chaque camp a un **palais** : les 3 × 3 croisements marqués d’un X. Le général et ses deux conseillers n’en sortent jamais.
- Vous jouez les **rouges**, en bas, et commencez. L’ordinateur joue les noirs.

## Les pièces et leurs marques

Chaque pièce porte son caractère traditionnel et, autour, de petites marques qui montrent comment elle se déplace, pour jouer sans savoir lire les caractères :

- un **point** = un pas dans cette direction ; **deux points** à la suite = deux pas ;
- une **flèche** = n’importe quelle distance dans cette direction ;
- une **flèche barrée** = elle saute par-dessus une pièce pour prendre ;
- un **point creux** = permis seulement après avoir traversé la rivière.

| Pièce | Marques | Déplacement |
|---|---|---|
| Général 帥 / 將 | 4 points droits | Un pas le long d’une ligne, dans le palais. |
| Conseiller 仕 / 士 | 4 points en diagonale | Un pas en diagonale, dans le palais. |
| Éléphant 相 / 象 | 4 paires de points en diagonale | Exactement deux points en diagonale. Il ne traverse pas la rivière et il est bloqué si le point du milieu (« l’œil de l’éléphant ») est occupé. |
| Cheval 馬 | 8 points | Un point tout droit, puis un en diagonale vers l’extérieur. Si le point voisin dans cette direction est occupé, il ne peut pas passer (« cheval entravé »). |
| Char 車 | 4 flèches | N’importe quelle distance le long d’une ligne. La pièce la plus forte. |
| Canon 炮 / 砲 | 4 flèches barrées | Se déplace comme le char, mais ne **prend** qu’en sautant par-dessus exactement une pièce (de n’importe quel camp). |
| Soldat 兵 / 卒 | 1 point devant, 2 points creux sur les côtés | Un pas en avant ; après la rivière, aussi un pas sur le côté. Il ne recule jamais. |

Touchez **Pièces** à tout moment pour voir cette liste avec de grands dessins.

## Règles particulières

- **Les généraux ne se font jamais face** sur une ligne sans rien entre eux. Un coup qui les laisserait ainsi est interdit.
- **Échec** : votre général est attaqué. Vous devez parer l’échec.
- L’**échec et mat** gagne. Aux échecs chinois, celui qui **n’a plus aucun coup possible** (même sans être en échec) **perd** aussi.

## Jouer dans l’application

1. Touchez une de vos pièces rouges : des points verts montrent où elle peut aller (un anneau vert autour d’une pièce ennemie veut dire que vous pouvez la prendre). La ligne d’état dit quelle pièce c’est et comment elle se déplace.
2. Touchez un point vert pour jouer.
3. **Annuler** reprend votre dernier coup et la réponse de l’ordinateur. **Indice** montre un bon coup avec une flèche.
4. Les pièces prises par chaque camp sont affichées au-dessus et au-dessous du plateau.

## Niveaux

- **Facile** : regarde un coup à l’avance et fait beaucoup d’erreurs. Idéal pour apprendre.
- **Moyen** : regarde quelques coups à l’avance et ne laisse pas de pièces en prise.
- **Difficile** : réfléchit aussi loin que possible en moins d’une seconde.

## Ce qui est simplifié

Les règles officielles interdisent l’échec perpétuel et la poursuite perpétuelle ; ici, une répétition est simplement **nulle**. La partie est aussi nulle quand la même position revient **trois fois**, après **60 coups chacun sans prise**, ou quand aucun camp n’a plus de pièce capable d’attaquer (cheval, char, canon ou soldat).

## Conseils

- Sortez vos chars tôt : chacun vaut environ deux chevaux.
- Le canon a besoin d’un « écran » pour prendre ; il est le plus fort au début, quand le plateau est plein.
- Gardez les conseillers et les éléphants près du général : ce sont ses gardes du corps.
