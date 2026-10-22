# Go

🌐 [English](../../en/games/go.md) · [Português](../../pt-BR/games/go.md) · [Español](../../es/games/go.md) · [Français](../../fr/games/go.md) · [Italiano](../../it/games/go.md) · **Deutsch** · [Русский](../../ru/games/go.md) · [日本語](../../ja/games/go.md) · [中文](../../zh-CN/games/go.md) · [العربية](../../ar/games/go.md)

Ein Brettspiel aus Ostasien, Tausende Jahre alt, für zwei: Du spielst die schwarzen Steine, der Computer die weißen. Die Steine kommen auf die Kreuzungspunkte der Linien und bewegen sich nie. Ziel ist es, mehr vom Brett zu umschließen als der Gegner.

**Abwechselnd an diesem Telefon:** Zwei Personen können auch gegeneinander spielen, ohne den Computer. Das Brett dreht sich nie, und der Bildschirm zeigt, wer am Zug ist. Am Ende entscheidet ihr gemeinsam, welche Steine tot sind.

## So wird gespielt

- Tippe auf einen Kreuzungspunkt, um einen Stein zu setzen. Auf den Brettern 13×13 und 19×19 sind die Punkte klein: Der erste Tipp zeigt den Stein blass an, ein zweiter Tipp auf denselben Punkt setzt ihn.
- Steine, die sich entlang der Linien berühren, bilden eine Gruppe. Eine Gruppe braucht mindestens einen leeren Nachbarpunkt (eine **Freiheit**). Wird ihre letzte Freiheit besetzt, ist die Gruppe **geschlagen** und verlässt das Brett.
- Du darfst nicht dorthin setzen, wo dein Stein keine Freiheit hätte, außer er schlägt dabei etwas.
- **Ko**: Wurde gerade ein einzelner Stein geschlagen, darf man an derselben Stelle nicht sofort zurückschlagen. Spiele zuerst woanders.
- Tippe auf **Passen**, wenn du nichts Nützliches mehr siehst. Passen beide nacheinander, ist das Spiel zu Ende.
- Der zuletzt gesetzte Stein hat einen kleinen Ring. Die geschlagenen Steine werden oben gezählt.
- **Rückgängig** nimmt deinen letzten Zug zurück (und die Antwort des Computers). **Tipp** markiert einen guten Punkt.

## Die Zählung

- Wir zählen nach Fläche (chinesische Regeln): Jede Seite bekommt einen Punkt für jeden eigenen Stein auf dem Brett und für jeden leeren Punkt, der nur von ihren Steinen umschlossen ist.
- Weiß bekommt 6,5 Punkte dazu (**Komi**), weil es als Zweites spielt. Der halbe Punkt verhindert ein Unentschieden.
- Nach dem zweimaligen Passen schlägt der Computer vor, welche Gruppen **tot** sind (sie könnten dem Schlagen nicht entkommen): Sie werden durchgestrichen, und das Gebiet wird mit kleinen Quadraten gezeigt. Tippe auf eine Gruppe, um sie zwischen tot und lebendig umzuschalten, dann auf **Zählen**. Bist du nicht einverstanden, tippe auf **Weiterspielen** und kläre es auf dem Brett.

## Die Bretter

- **9×9**: klein und schnell, ideal für den Anfang.
- **13×13**: mehr Platz für Pläne.
- **19×19**: das große klassische Brett. Mit zwei Fingern vergrößern.

## Stufen

- **Leicht**: spielt fast zufällig, schlägt aber Steine mit nur noch einer Freiheit, rettet oft die eigenen und bringt sich selten in Gefahr. Gut zum Lernen der Regeln.
- **Mittel**: spielt von der aktuellen Stellung aus Tausende schnelle gedachte Partien zu Ende und wählt den Zug, der am häufigsten gewinnt (eine Monte-Carlo-Suche), etwa eine Drittelsekunde lang.
- **Schwer**: dieselbe Suche etwa zwei Drittelsekunden lang. Auf den größeren Brettern reicht diese Zeit für weniger Partien, dort ist der Computer schwächer.

Der Computer füllt nie seine eigenen Augen. Es gibt keine Vorgabesteine: Alle Stufen verwenden dasselbe Komi von 6,5.

## Tipps

- Beginne in den Ecken, dann an den Rändern, zuletzt in der Mitte: In den Ecken braucht man die wenigsten Steine zum Umschließen.
- Eine Gruppe mit zwei getrennten Augen (leeren Punkten in ihr) kann nie geschlagen werden.
- Hat eine deiner Gruppen nur noch eine Freiheit, rette sie sofort oder gib sie auf; setze keine weiteren Steine in eine verlorene Gruppe.
