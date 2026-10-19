# Vertrauen, Datenschutz und Sicherheit

🌐 [English](../en/trust.md) · [Português](../pt-BR/trust.md) · [Español](../es/trust.md) · [Français](../fr/trust.md) · [Italiano](../it/trust.md) · **Deutsch** · [Русский](../ru/trust.md) · [日本語](../ja/trust.md) · [中文](../zh-CN/trust.md) · [العربية](../ar/trust.md)

Mili Games soll auf den Telefonen von Menschen installiert werden, die eine App vielleicht nicht selbst beurteilen können. Diese Seite erklärt, was die App tut und was nicht — und, noch wichtiger, **wie jeder das nachprüfen kann**, ohne dem Autor einfach glauben zu müssen.

## Inhalt

1. [Zusammenfassung](#zusammenfassung)
2. [Datenschutz](#datenschutz)
3. [Berechtigungen](#berechtigungen)
4. [Was in der App steckt](#was-in-der-app-steckt)
5. [Wie der Code geprüft wird](#wie-der-code-geprüft-wird)
6. [Eine heruntergeladene APK prüfen](#eine-heruntergeladene-apk-prüfen)
7. [Schutz der Lieferkette](#schutz-der-lieferkette)
8. [Hilfe durch KI, ganz offen](#hilfe-durch-ki-ganz-offen)
9. [Ein Sicherheitsproblem melden](#ein-sicherheitsproblem-melden)

## Zusammenfassung

| | |
|---|---|
| Internetzugang | **Keiner** (keine Berechtigung `INTERNET`) |
| Gesammelte Daten | **Keine** |
| Werbung, Statistiken, Absturzberichte, Tracker | **Keine** |
| Konten, Anmeldungen, Käufe | **Keine** |
| Angeforderte Android-Berechtigungen | **Keine** |
| Code von Dritten | Nur offizielle Bibliotheken von AndroidX (Google) und Kotlin (JetBrains) |
| Lizenz | MIT, Quellcode öffentlich |

## Datenschutz

Die App speichert genau zwei Dinge, beide in ihrem **privaten** Speicher, den andere Apps nicht lesen können:

1. `settings.xml` — deine Auswahl in den Einstellungen (Sprache, Größen, Farben…).
2. `current_game.json` — das laufende Spiel, damit es nach dem Schließen der App fortgesetzt werden kann. Diese Datei wird gelöscht, wenn das Spiel endet oder abgebrochen wird.

Wenn du die Sicherung von Android selbst eingeschaltet hast, kann Android diese beiden Dateien in *deine* Google-Sicherung (und in die Übertragung von Gerät zu Gerät) aufnehmen, damit ein neues Telefon deine Einstellungen behält. Das ist in [`backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) und [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml) festgelegt. Sonst wird niemals etwas gespeichert oder gesendet.

Die Taste „Projektseite öffnen“ unter Einstellungen → Über bittet Android nur, die GitHub-Seite in deinem Browser zu öffnen; die App selbst baut weiterhin keine Verbindung auf.

## Berechtigungen

Das [Manifest](../../app/src/main/AndroidManifest.xml) enthält **kein einziges** `<uses-permission>`. Das kannst du bei jeder APK mit dem Android SDK bestätigen:

```bash
aapt2 dump permissions MiliGames-v1.0.0.apk
```

Du siehst einen einzigen Eintrag, der auf `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` endet. Das ist keine Berechtigung für den Benutzer: Es ist eine private Berechtigung auf Signaturebene, die die AndroidX-Bibliothek automatisch hinzufügt, um die internen Broadcast-Receiver der App vor anderen Apps zu schützen. Sie gewährt niemandem irgendetwas.

Ohne die Berechtigung `INTERNET` lässt Android die App überhaupt keine Netzwerkverbindung öffnen.

## Was in der App steckt

Die vollständige Liste der Abhängigkeiten steht in [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml):

| Bibliothek | Herausgeber | Wofür |
|---|---|---|
| `androidx.activity:activity-compose` | Google (AndroidX) | Zeigt die Compose-Oberfläche in der Activity an |
| `androidx.compose.ui`, `foundation`, `material3` | Google (AndroidX) | Das Werkzeug für die Oberfläche |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JetBrains | Speichert das laufende Spiel als JSON |
| Kotlin-Standardbibliothek | JetBrains | Die Laufzeitumgebung der Sprache |

Nur für Tests (nie in der App enthalten): JUnit, Robolectric, Compose UI test.

In der App gibt es **keine Bilder, Töne, Schriftarten oder nativen Bibliotheken**: Jedes Bild wird von Code gezeichnet, den du in [`ui/art`](../../app/src/main/java/io/github/fershibli/megagames/ui/art) lesen kannst. Die einzigen Datendateien sind die Wörterbücher für das Kreuzworträtsel als reiner Text ([`assets/crossword`](../../app/src/main/assets/crossword)).

Der Release-Build schaltet außerdem den „Dependency Info“-Block von Google ab (`dependenciesInfo { includeInApk = false }`), einen verschlüsselten Datenblock, der sonst in die APK eingebettet würde. So lässt sich jedes Byte der APK untersuchen.

## Wie der Code geprüft wird

Bei jedem Push und jedem Pull Request läuft [CI](../../.github/workflows/ci.yml) auf GitHub Actions:

1. **Prüfung des Gradle-Wrappers** — bestätigt, dass `gradle-wrapper.jar` die offizielle Datei ist.
2. **50 automatische Tests**, unter anderem:
   - die Regeln jedes Spiels (erlaubte Züge, Wertung, Hinauswürfeln, Folgen und Joker beim Buraco…);
   - Generatoren: Jedes Sudoku hat genau eine Lösung, jede Mahjong-Verteilung ist lösbar, jedes Rätsel bei Rohre und Farben verbinden hat eine vollständige Lösung, Kreuzworträtsel haben nie Widersprüche;
   - die Computergegner beenden ihre Spiele zügig und verlieren oder verdoppeln nie eine Karte oder einen Stein;
   - alle Sprachen übersetzen jeden Text und behalten dieselben Platzhalter;
   - Speichern und Laden des laufenden Spiels, Umgang mit beschädigten Spielständen, dauerhafte Einstellungen;
   - die echte Oberfläche: Zoom mit zwei Fingern ein/aus, Doppeltippen und Drücken und kreisen im Einhand-Modus, ein Spiel wählen, es über die Einstellungen verlassen und die Frage „Weiterspielen?“ nach einem Neustart.
3. **Android Lint**, bei dem *Warnungen als Fehler gelten*.
4. **Bauen** der Debug- und Release-APKs, veröffentlicht als Artefakte mit **SHA-256-Prüfsummen**.

Dieselben Prüfungen kannst du selbst ausführen: siehe [building.md](../en/building.md) (auf Englisch).

## Eine heruntergeladene APK prüfen

1. Lade `MiliGames-vX.Y.Z.apk` und `SHA256SUMS.txt` aus demselben [Release](https://github.com/fershibli/game-mega-app/releases) herunter.
2. Berechne die Prüfsumme und vergleiche sie:

   ```bash
   sha256sum MiliGames-vX.Y.Z.apk      # Linux / macOS: shasum -a 256
   certutil -hashfile MiliGames-vX.Y.Z.apk SHA256   # Windows
   ```

3. Prüfe, ob das Signaturzertifikat über alle Versionen gleich bleibt (Android weigert sich ohnehin, eine App zu aktualisieren, die mit einem anderen Schlüssel signiert ist):

   ```bash
   apksigner verify --print-certs MiliGames-vX.Y.Z.apk
   ```

4. Für volle Gewissheit baue die App selbst aus dem markierten Quellcode (Tag) und vergleiche den Inhalt (`unzip -l`, `aapt2 dump badging`).

## Schutz der Lieferkette

- `distributionSha256Sum` legt in [`gradle-wrapper.properties`](../../gradle/wrapper/gradle-wrapper.properties) genau fest, welche Gradle-Version benutzt wird.
- Abhängigkeiten kommen nur aus dem Maven-Repository von Google und aus Maven Central (`repositoriesMode = FAIL_ON_PROJECT_REPOS`).
- [Dependabot](../../.github/dependabot.yml) schlägt jeden Monat Aktualisierungen für Abhängigkeiten und GitHub Actions vor.
- CI läuft nur mit Leserechten; nur die Workflows für Versionierung und Veröffentlichung dürfen schreiben, und auch nur, um den Versions-Tag zu setzen und das Release zu veröffentlichen. Der Workflow für den Signaturschlüssel schreibt nur die Signatur-Secrets, mit einem Token, das auf die Secrets dieses Repositorys beschränkt ist.
- Jede veröffentlichte APK wird vor dem Hochladen in der Pipeline geprüft: Signatur (`apksigner`), Versionsname und dass sie keine Berechtigungen anfordert (`aapt2`).
- Die Signaturschlüssel für Releases liegen in verschlüsselten GitHub-Secrets und werden nie eingecheckt (`*.jks` und `keystore.properties` werden von Git ignoriert).

## Hilfe durch KI, ganz offen

Der Code, die Grafiken und die Dokumentation wurden mit Hilfe von **Claude** erstellt, dem KI-Assistenten von Anthropic, unter der Leitung des Maintainers. Von einer KI geschriebener Code kann Fehler enthalten wie jeder andere Code auch. Deshalb setzt dieses Projekt auf Dinge, die du nachprüfen kannst, statt auf Versprechen: lesbarer Quellcode, eine breite Testsuite, Lint, nachvollziehbare Builds und veröffentlichte Prüfsummen. Wenn dir ein Problem auffällt, melde es bitte.

## Ein Sicherheitsproblem melden

Siehe [SECURITY.md](../../SECURITY.md). Bitte eröffne für eine Sicherheitslücke kein öffentliches Issue, sondern nutze die private Meldung von Sicherheitslücken auf GitHub.
