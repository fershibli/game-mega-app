# Sudoku des sommes

🌐 [English](../../en/games/killer.md) · [Português](../../pt-BR/games/killer.md) · [Español](../../es/games/killer.md) · **Français** · [Italiano](../../it/games/killer.md) · [Deutsch](../../de/games/killer.md) · [Русский](../../ru/games/killer.md) · [日本語](../../ja/games/killer.md) · [中文](../../zh-CN/games/killer.md) · [العربية](../../ar/games/killer.md)

Un sudoku sans aucun chiffre donné. Comme d’habitude, chaque ligne, colonne et carré 3×3 contient les chiffres de 1 à 9 une seule fois. La grille est aussi divisée en zones aux contours en pointillés : les chiffres d’une zone ont pour somme le petit nombre dans son coin, et une zone ne répète jamais un chiffre. (On l’appelle aussi « killer sudoku ».)

## Comment jouer

- Comme au Sudoku : touchez une case, puis un chiffre ; **Notes** pour de petits pense-bêtes ; **Annuler** pour revenir en arrière.
- Les cases de la zone choisie sont teintées.
- **Indice** montre d’abord les combinaisons qui donnent la somme de la zone de la case choisie ; touchez-le encore pour remplir la case.
- Pincez pour zoomer.

## Niveaux

- **Facile** : une dizaine de zones d’une seule case (elles valent des chiffres donnés) et de petites zones ; un raisonnement simple suffit. Les erreurs sont en rouge.
- **Moyen** : quelques cases seules et des zones de 4 ou 5 cases ; il faut parfois des chiffres « bloqués » (un chiffre qui doit être sur une ligne d’un carré ou dans une partie d’une zone). Les erreurs sont en rouge.
- **Difficile** : aucune case seule et de plus grandes zones ; la règle du 45 peut servir. Les mauvais chiffres ne sont pas montrés, mais les répétitions et les sommes fausses le sont.

Chaque grille est créée par l’application et a une seule solution, trouvée par le raisonnement, sans deviner.

## Astuces

- Une zone d’une case donne tout de suite son chiffre.
- Deux cases qui font 3 contiennent 1 et 2 ; 17, c’est 8 et 9 ; trois cases qui font 6 contiennent 1, 2 et 3.
- La règle du 45 : chaque ligne, colonne et carré fait 45. Si les zones à l’intérieur d’un carré le couvrent sauf une case, cette case vaut 45 moins leur total.
