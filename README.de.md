<div align="center">

# Mili Games

**Zehn klassische Spiele in einer kleinen, privaten Android-App — gemacht für ältere Menschen.**

Sudoku · Solitär · Buraco · Backgammon · Mahjong · Reversi · Kreuzworträtsel · Rohre · Farben verbinden · Punkte

🌐 [English](README.md) · [Português](README.pt-BR.md) · [Español](README.es.md) · [Français](README.fr.md) · [Italiano](README.it.md) · **Deutsch** · [Русский](README.ru.md) · [日本語](README.ja.md) · [中文](README.zh-CN.md) · [العربية](README.ar.md)

[![CI](https://github.com/fershibli/game-mega-app/actions/workflows/ci.yml/badge.svg)](https://github.com/fershibli/game-mega-app/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/fershibli/game-mega-app?include_prereleases&sort=semver)](https://github.com/fershibli/game-mega-app/releases)
![Lizenz: MIT](https://img.shields.io/badge/Lizenz-MIT-green)
![Android 6.0+](https://img.shields.io/badge/Android-6.0%2B-3DDC84)
![APK etwa 1,3 MB](https://img.shields.io/badge/APK-~1%2C3%20MB-blue)
![Keine Berechtigungen](https://img.shields.io/badge/Berechtigungen-keine-brightgreen)

<img src="docs/screenshots/home.png" width="220" alt="Startbildschirm mit Bildern der Spiele"> <img src="docs/screenshots/game_mahjong_easy.png" width="220" alt="Mahjong"> <img src="docs/screenshots/game_backgammon.png" width="220" alt="Backgammon">

</div>

> Diese App habe ich für meine Mutter gemacht, die diese Spiele liebt. Sie ist für alle kostenlos, für immer:
> keine Werbung, keine Konten, keine In-App-Käufe, kein Internetzugang, keine Datensammlung.

## Inhalt

1. [Das Wichtigste](#das-wichtigste)
2. [Die Spiele](#die-spiele)
3. [Für ältere Menschen gemacht](#für-ältere-menschen-gemacht)
4. [Installation](#installation)
5. [Warum du der App vertrauen kannst](#warum-du-der-app-vertrauen-kannst)
6. [Bildschirmfotos](#bildschirmfotos)
7. [Dokumentation](#dokumentation)
8. [Aus dem Quellcode bauen](#aus-dem-quellcode-bauen)
9. [Mit Hilfe von KI entstanden](#mit-hilfe-von-ki-entstanden)
10. [Lizenz](#lizenz)

## Das Wichtigste

- **10 Spiele**, jedes in **Leicht / Mittel / Schwer**.
- **Rätsel werden jedes Mal neu erzeugt** (Sudoku, Mahjong, Rohre, Farben verbinden, Kreuzworträtsel…), sie wiederholen sich also nie — und jedes erzeugte Rätsel ist **garantiert lösbar**.
- **Echte Computergegner**, eigens für Backgammon, Buraco und Reversi geschrieben.
- **Zoomen mit zwei Fingern in jedem Spiel**, dazu Zoom mit einem Finger im Einhand-Modus.
- **Automatisches Speichern**: Wird die App aus Versehen geschlossen, bietet sie an, genau dort weiterzuspielen, wo du aufgehört hast.
- **10 Sprachen**: Englisch, Portugiesisch, Spanisch, Französisch, Italienisch, Deutsch, Russisch, Japanisch, Chinesisch und Arabisch — die Oberfläche *und* die Wörter im Kreuzworträtsel.
- **Klein und leicht**: etwa **1,3 MB**, natives Kotlin + Jetpack Compose, keine Spiele-Engine, keine Bilddateien — alle Grafiken werden im Code gezeichnet.

## Die Spiele

| | Spiel | Worum es geht | Was sich mit dem Schwierigkeitsgrad ändert |
|---|---|---|---|
| <img src="docs/screenshots/game_sudoku.png" width="70"> | **Sudoku** | Klassisches 9×9-Zahlenrätsel mit Notizen, Tipps und Rückgängig | 40 → 32 → 26 vorgegebene Zahlen; Fehler werden bei Leicht/Mittel rot angezeigt |
| <img src="docs/screenshots/game_solitaire.png" width="70"> | **Solitär** | Klondike; tippe eine Karte an, und sie wandert an die beste Stelle | 1 Karte ziehen → 3 Karten ziehen → 3 Karten mit nur 3 Durchgängen |
| <img src="docs/screenshots/game_buraco.png" width="70"> | **Buraco** | Brasilianisches Rommé gegen den Computer (zwei Kartenspiele, Zweien sind Joker, Reservestapel, Canastas) | Klügerer Computer: nimmt den Ablagestapel mit Bedacht, hütet seine Joker, gibt dir keine passenden Karten |
| <img src="docs/screenshots/game_backgammon.png" width="70"> | **Backgammon** | Alle Regeln (Bar, Schlagen, Hinauswürfeln, Gammon) | Computer von „locker“ bis zum Vorausdenken über einen Wurf (Expectimax) |
| <img src="docs/screenshots/game_mahjong_easy.png" width="70"> | **Mahjong** | Mahjong-Solitär mit 3D-Steinen; blockierte Steine sind abgedunkelt | 48 → 96 → 144 Steine (klassische Schildkröte) |
| <img src="docs/screenshots/game_reversi.png" width="70"> | **Reversi** | Othello gegen den Computer; mögliche Züge werden angezeigt | Alpha-Beta-Suche mit Tiefe 1 → 3 → 6 (perfektes Endspiel) |
| <img src="docs/screenshots/game_crossword_easy.png" width="70"> | **Kreuzworträtsel** | Jedes Mal ein neues Kreuzworträtsel, in deiner Sprache | Buchstabenauswahl → ganze Tastatur; bei Schwer keine Fehleranzeige |
| <img src="docs/screenshots/game_pipes.png" width="70"> | **Rohre** | Drehe die Rohrstücke, bis das Wasser überall ankommt | 5×5 → 7×7 → 9×9 |
| <img src="docs/screenshots/game_connect.png" width="70"> | **Farben verbinden** | Verbinde Punkte derselben Farbe, ohne dass sich Linien kreuzen | 5×5/4 Farben → 7×7/6 → 9×9/8, bei Mittel/Schwer muss das ganze Feld gefüllt werden |
| <img src="docs/screenshots/game_dots.png" width="70"> | **Punkte** | Verbinde gleichfarbige Punkte; schließe ein Quadrat, um eine ganze Farbe zu entfernen | Größeres Feld, mehr Farben, weniger Züge |

Alle Regeln und Tipps: **[Anleitung](docs/de/user-guide.md)**.

## Für ältere Menschen gemacht

- **Ganz einfache Bedienung**: Spiel wählen (nur ein Bild, kein Lesen nötig) → Schwierigkeitsgrad an einem Drehrad wählen → spielen. Das Zahnrad (⚙) ist immer in derselben Ecke.
- **Tasten dort, wo der Daumen ist**: Fast alle Tasten liegen in der unteren Hälfte des Bildschirms. Der **Linkshänder-Modus** spiegelt sie.
- **Einhand-Modus**: Das Zahnrad wird zu einer schwebenden Blase, die sich an den Bildschirmrand schmiegt; der Zoom funktioniert mit **Doppeltippen** oder **Drücken und kreisen**.
- **Einstellbare Textgröße und Größe von Spielsteinen und Tasten** (80 % bis 160 %).
- **Drei Farbmodi, jeder mit eigenem dunklen Modus**: Normal (mittlerer Kontrast), Hoher Kontrast (dicke Umrisse), Für Farbenblinde (Okabe-Ito-Farben, Formen auf farbigen Spielsteinen, Karten in vier Farben).
- Große Tippflächen (56 dp), große Schrift, Tipps und unbegrenztes Rückgängigmachen, und nirgends eine Zeitbegrenzung.

Mehr dazu: **[Abschnitt Barrierefreiheit in der Anleitung](docs/de/user-guide.md#barrierefreiheit)**.

## Installation

1. Öffne auf deinem Android-Telefon die Seite **[Releases](https://github.com/fershibli/game-mega-app/releases)** und lade die neueste Datei `MiliGames-vX.Y.Z.apk` herunter.
2. Öffne die Datei. Wenn Android fragt, erlaube deinem Browser bzw. deiner Dateien-App, „unbekannte Apps zu installieren“.
3. Freiwillig: Vergleiche die SHA-256-Prüfsumme der Datei mit `SHA256SUMS.txt` aus demselben Release.

Benötigt Android 6.0 oder neuer. Schritt-für-Schritt-Anleitung mit Bildern: [Anleitung → Installation](docs/de/user-guide.md#installation).

## Warum du der App vertrauen kannst

| Aussage | So kannst du es selbst prüfen |
|---|---|
| Kein Internet, keine Überwachung | Das Manifest fordert **überhaupt keine Berechtigungen** an ([AndroidManifest.xml](app/src/main/AndroidManifest.xml)); ohne `INTERNET` sperrt Android jeden Netzwerkzugriff. |
| Kein versteckter Code | 100 % quelloffen; nur offizielle AndroidX-/Kotlin-Bibliotheken ([libs.versions.toml](gradle/libs.versions.toml)). Keine Werbe- oder Analyse-SDKs. |
| Du bekommst genau das, was CI baut | Jeder Push wird auf GitHub Actions gebaut, getestet und mit Lint geprüft; die APKs werden mit SHA-256-Prüfsummen veröffentlicht. Der „Dependency Info“-Block von Google ist abgeschaltet, sodass der Inhalt der APK vollständig prüfbar ist. |
| Es funktioniert | 50 automatische Tests bei jedem Build: Regeln, Generatoren (jedes Rätsel lösbar), Computergegner, Übersetzungen, automatisches Speichern und die Bedienung (Zoom mit zwei Fingern, Einhand-Zoom, Navigation). Lint läuft mit Warnungen als Fehler. |
| Sichere Build-Werkzeuge | Die Prüfsumme des Gradle-Wrappers ist festgelegt und wird in CI geprüft; Dependabot hält die Abhängigkeiten aktuell. |

Einzelheiten: **[Vertrauen, Datenschutz und Sicherheit](docs/de/trust.md)** · [SECURITY.md](SECURITY.md)

## Bildschirmfotos

| Start (dunkel) | Hoher Kontrast | Für Farbenblinde + dunkel | Einhand-Modus |
|---|---|---|---|
| <img src="docs/screenshots/home_dark.png" width="180"> | <img src="docs/screenshots/home_high_contrast.png" width="180"> | <img src="docs/screenshots/home_colorblind_dark.png" width="180"> | <img src="docs/screenshots/home_one_hand.png" width="180"> |

| Schwierigkeitsgrad | Einstellungen | Weiterspielen? | Buraco (Portugiesisch) |
|---|---|---|---|
| <img src="docs/screenshots/difficulty.png" width="180"> | <img src="docs/screenshots/settings.png" width="180"> | <img src="docs/screenshots/resume_dialog.png" width="180"> | <img src="docs/screenshots/game_buraco_pt.png" width="180"> |

Alle Bildschirmfotos werden automatisch aus dem echten Code erzeugt (siehe [Building](docs/en/building.md#screenshots), auf Englisch).

## Dokumentation

| Sprache | Anleitung | Vertrauen & Datenschutz | Technischer Aufbau | Bauen & Mitmachen |
|---|---|---|---|---|
| English | [user-guide](docs/en/user-guide.md) | [trust](docs/en/trust.md) | [technical](docs/en/technical.md) | [building](docs/en/building.md) |
| Português | [guia](docs/pt-BR/user-guide.md) | [confiança](docs/pt-BR/trust.md) | [técnico](docs/pt-BR/technical.md) | [compilar](docs/pt-BR/building.md) |
| Español | [guía](docs/es/user-guide.md) | [confianza](docs/es/trust.md) | [técnico](docs/es/technical.md) | [compilar](docs/es/building.md) |
| Français | [guide](docs/fr/user-guide.md) | [confiance](docs/fr/trust.md) | [technique](docs/fr/technical.md) | [compiler](docs/fr/building.md) |
| Italiano | [guida](docs/it/user-guide.md) | [fiducia](docs/it/trust.md) | [technical (EN)](docs/en/technical.md) | [building (EN)](docs/en/building.md) |
| Deutsch | [Anleitung](docs/de/user-guide.md) | [Vertrauen](docs/de/trust.md) | [technical (EN)](docs/en/technical.md) | [building (EN)](docs/en/building.md) |
| Русский | [руководство](docs/ru/user-guide.md) | [доверие](docs/ru/trust.md) | [technical (EN)](docs/en/technical.md) | [building (EN)](docs/en/building.md) |
| 日本語 | [使い方](docs/ja/user-guide.md) | [信頼](docs/ja/trust.md) | [technical (EN)](docs/en/technical.md) | [building (EN)](docs/en/building.md) |
| 中文 | [用户指南](docs/zh-CN/user-guide.md) | [信任与隐私](docs/zh-CN/trust.md) | [技术设计](docs/zh-CN/technical.md) | [构建与贡献](docs/zh-CN/building.md) |
| العربية | [دليل الاستخدام](docs/ar/user-guide.md) | [الثقة والخصوصية](docs/ar/trust.md) | [technical (EN)](docs/en/technical.md) | [building (EN)](docs/en/building.md) |

Vollständige Übersicht: [docs/README.md](docs/README.md).

## Aus dem Quellcode bauen

```bash
git clone https://github.com/fershibli/game-mega-app.git
cd game-mega-app
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Benötigt JDK 17+ und das Android SDK (API 37). Siehe [docs/en/building.md](docs/en/building.md) (auf Englisch).

## Mit Hilfe von KI entstanden

Alles in diesem Repository — der Code, die Grafiken (jede Karte, jeder Stein, jedes Spielbrett und jedes Symbol wird im Code gezeichnet) und diese Dokumentation in zehn Sprachen — wurde mit Hilfe von **Claude** erstellt, dem KI-Assistenten von **Anthropic**, unter der Leitung des Maintainers. Die Entwurfsentscheidungen sind in [docs/en/technical.md](docs/en/technical.md) erklärt (auf Englisch), und die Testsuite gibt es, damit niemand dem Code blind vertrauen muss.

## Lizenz

[MIT](LICENSE) — frei zu benutzen, zu kopieren, zu verändern und weiterzugeben.
