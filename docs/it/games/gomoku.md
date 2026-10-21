# Gomoku (cinque in fila)

🌐 [English](../../en/games/gomoku.md) · [Português](../../pt-BR/games/gomoku.md) · [Español](../../es/games/gomoku.md) · [Français](../../fr/games/gomoku.md) · **Italiano** · [Deutsch](../../de/games/gomoku.md) · [Русский](../../ru/games/gomoku.md) · [日本語](../../ja/games/gomoku.md) · [中文](../../zh-CN/games/gomoku.md) · [العربية](../../ar/games/gomoku.md)

Un gioco per due sulle intersezioni di un tavoliere 15×15. Tu giochi con il nero e inizi; il computer gioca con il bianco. Vince chi per primo forma una linea continua di cinque o più pietre, in orizzontale, in verticale o in diagonale.

## Come si gioca

- Tocca un’intersezione: una pietra in trasparenza mostra dove andrà. Tocca di nuovo la stessa intersezione per posarla, oppure un’altra se cambi idea.
- L’ultima pietra giocata ha un piccolo anello, e la linea vincente viene evidenziata.
- **Annulla mossa** riprende la tua ultima mossa (e la risposta del computer). **Suggerimento** indica una buona intersezione.

## Regole

- Stile libero: vincono cinque o più pietre in fila, anche sei o più.
- Le restrizioni da torneo per il nero (niente "doppio tre", "doppio quattro" o linee più lunghe di cinque) **non** sono usate, quindi il nero, che inizia, è avvantaggiato.
- Se il tavoliere si riempie, la partita è patta.

## Livelli

- **Facile**: sceglie l’intersezione più promettente, ma a volte una peggiore apposta, e può perfino dimenticare di bloccare un quattro.
- **Medio**: completa sempre i suoi quattro e blocca i tuoi, e guarda qualche mossa avanti.
- **Difficile**: guarda più lontano e cerca vittorie con una serie di quattro, il tutto in meno di un secondo.

## Consigli

- Un **tre aperto** (tre in fila con entrambe le estremità libere) va bloccato subito, altrimenti diventa un quattro aperto che non si può più fermare.
- Due minacce insieme vincono: prova a fare un quattro e un tre aperto con una sola pietra.
