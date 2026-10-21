# Go

🌐 [English](../../en/games/go.md) · [Português](../../pt-BR/games/go.md) · [Español](../../es/games/go.md) · [Français](../../fr/games/go.md) · **Italiano** · [Deutsch](../../de/games/go.md) · [Русский](../../ru/games/go.md) · [日本語](../../ja/games/go.md) · [中文](../../zh-CN/games/go.md) · [العربية](../../ar/games/go.md)

Un gioco da tavolo dell’Asia orientale, vecchio di migliaia di anni, per due giocatori: tu giochi con le pietre nere e il computer con le bianche. Le pietre si posano sulle intersezioni delle linee e non si muovono mai. Lo scopo è circondare una parte del tavoliere più grande di quella dell’avversario.

## Come si gioca

- Tocca un’intersezione per posare una pietra. Sui tavolieri 13×13 e 19×19 le intersezioni sono piccole: il primo tocco mostra la pietra in trasparenza e un secondo tocco sulla stessa intersezione la posa.
- Le pietre che si toccano lungo le linee formano un gruppo. Un gruppo ha bisogno di almeno un’intersezione vuota accanto (una **libertà**). Quando viene occupata la sua ultima libertà, il gruppo è **catturato** ed esce dal tavoliere.
- Non puoi giocare dove la tua pietra resterebbe senza libertà, a meno che catturi qualcosa.
- **Ko**: quando è appena stata catturata una sola pietra, non si può ricatturare subito nello stesso punto. Gioca prima altrove.
- Tocca **Passa** quando non vedi niente di utile da giocare. Quando i due giocatori passano uno dopo l’altro, la partita finisce.
- L’ultima pietra giocata ha un piccolo anello. Le pietre catturate sono contate in alto.
- **Annulla mossa** riprende la tua ultima mossa (e la risposta del computer). **Suggerimento** indica una buona intersezione.

## Il conteggio

- Contiamo per area (regole cinesi): ogni parte prende un punto per ogni sua pietra sul tavoliere e per ogni intersezione vuota circondata solo dalle sue pietre.
- Il bianco riceve 6,5 punti in più (**komi**) perché gioca per secondo. Il mezzo punto evita i pareggi.
- Dopo i due passaggi il computer propone quali gruppi sono **morti** (non potrebbero sfuggire alla cattura): appaiono barrati, e il territorio è indicato da piccoli quadrati. Tocca un gruppo per passarlo da morto a vivo o viceversa, poi tocca **Conta**. Se non sei d’accordo, tocca **Continua a giocare** e risolvi sul tavoliere.

## I tavolieri

- **9×9**: piccolo e veloce, il modo migliore per iniziare.
- **13×13**: più spazio per i piani.
- **19×19**: il grande tavoliere classico. Allarga due dita per ingrandire.

## Livelli

- **Facile**: gioca quasi a caso, ma cattura le pietre rimaste con una sola libertà, spesso salva le sue ed evita di mettersi in pericolo. Adatto per imparare le regole.
- **Medio**: gioca migliaia di rapide partite immaginarie fino in fondo dalla posizione attuale e sceglie la mossa che vince più spesso (una ricerca Monte Carlo), per circa un terzo di secondo.
- **Difficile**: la stessa ricerca per circa due terzi di secondo. Sui tavolieri grandi in quel tempo si giocano meno partite, quindi lì il computer è più debole.

Il computer non riempie mai i propri occhi. Non ci sono pietre di handicap: tutti i livelli usano lo stesso komi di 6,5.

## Consigli

- Comincia dagli angoli, poi i lati e infine il centro: negli angoli servono meno pietre per circondare.
- Un gruppo con due occhi separati (punti vuoti al suo interno) non può mai essere catturato.
- Quando un tuo gruppo ha una sola libertà, salvalo subito o lascialo andare; non aggiungere pietre a un gruppo perso.
