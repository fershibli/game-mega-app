# Fiducia, privacy e sicurezza

🌐 [English](../en/trust.md) · [Português](../pt-BR/trust.md) · [Español](../es/trust.md) · [Français](../fr/trust.md) · **Italiano** · [Deutsch](../de/trust.md) · [Русский](../ru/trust.md) · [日本語](../ja/trust.md) · [中文](../zh-CN/trust.md) · [العربية](../ar/trust.md)

Mili Games è pensato per essere installato sui telefoni di persone che forse non sono in grado di valutare un’app da sole. Questa pagina spiega che cosa fa l’app e che cosa non fa e, soprattutto, **come chiunque può verificarlo** senza doversi fidare dell’autore.

## Indice

1. [Riepilogo](#riepilogo)
2. [Privacy](#privacy)
3. [Autorizzazioni](#autorizzazioni)
4. [Che cosa c’è dentro l’app](#che-cosa-cè-dentro-lapp)
5. [Come viene controllato il codice](#come-viene-controllato-il-codice)
6. [Verificare un APK scaricato](#verificare-un-apk-scaricato)
7. [Protezioni della catena di fornitura](#protezioni-della-catena-di-fornitura)
8. [L’aiuto dell’IA, in tutta onestà](#laiuto-dellia-in-tutta-onestà)
9. [Segnalare un problema di sicurezza](#segnalare-un-problema-di-sicurezza)

## Riepilogo

| | |
|---|---|
| Accesso a internet | **Nessuno** (nessuna autorizzazione `INTERNET`) |
| Dati raccolti | **Nessuno** |
| Pubblicità, statistiche, segnalazione dei crash, tracciamento | **Nessuno** |
| Account, accessi, acquisti | **Nessuno** |
| Autorizzazioni Android richieste | **Nessuna** |
| Codice di terze parti | Solo le librerie ufficiali AndroidX (Google) e Kotlin (JetBrains) |
| Licenza | MIT, codice sorgente pubblico |

## Privacy

L’app salva esattamente due cose, entrambe nella sua memoria **privata**, che le altre app non possono leggere:

1. `settings.xml`: le tue scelte nelle Impostazioni (lingua, dimensioni, colori…).
2. `current_game.json`: la partita in corso, così si può riprendere dopo la chiusura dell’app. Viene eliminato quando la partita finisce o viene abbandonata.

Se hai attivato il backup di Android, Android può includere questi due file nel *tuo* backup Google (e nel trasferimento da un telefono all’altro), così un telefono nuovo mantiene le tue impostazioni. Questo è configurato in [`backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) e [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml). Nient’altro viene mai salvato o inviato.

Il pulsante “Apri la pagina del progetto” in Impostazioni → Informazioni chiede semplicemente ad Android di aprire la pagina GitHub nel tuo browser; l’app in sé continua a non fare nessuna connessione.

## Autorizzazioni

Il [manifest](../../app/src/main/AndroidManifest.xml) **non** dichiara nessun `<uses-permission>`. Puoi verificarlo su qualsiasi APK con l’Android SDK:

```bash
aapt2 dump permissions MiliGames-v1.0.0.apk
```

Vedrai una sola voce che finisce con `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Non è un’autorizzazione per l’utente: è un’autorizzazione privata, a livello di firma, che la libreria AndroidX aggiunge automaticamente per proteggere dalle altre app i “ricevitori di messaggi” interni dell’app stessa. Non concede niente a nessuno.

Senza l’autorizzazione `INTERNET`, Android non permette all’app di aprire nessuna connessione di rete.

## Che cosa c’è dentro l’app

L’elenco completo delle dipendenze si trova in [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml):

| Libreria | Editore | A che cosa serve |
|---|---|---|
| `androidx.activity:activity-compose` | Google (AndroidX) | Ospita l’interfaccia Compose nell’activity |
| `androidx.compose.ui`, `foundation`, `material3` | Google (AndroidX) | Gli strumenti per l’interfaccia |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JetBrains | Salvare la partita in corso in formato JSON |
| Libreria standard di Kotlin | JetBrains | Il supporto di base del linguaggio |

Librerie usate solo per i test (mai incluse nell’app): JUnit, Robolectric, Compose UI test.

Nell’app **non ci sono immagini, suoni, caratteri né librerie native**: ogni disegno è fatto dal codice, che puoi leggere in [`ui/art`](../../app/src/main/java/io/github/fershibli/megagames/ui/art). Gli unici file di dati sono i dizionari dei cruciverba, in testo semplice ([`assets/crossword`](../../app/src/main/assets/crossword)).

La versione pubblicata disattiva anche il blocco “dependency info” di Google (`dependenciesInfo { includeInApk = false }`), un blocco cifrato che altrimenti verrebbe inserito nell’APK. Così ogni byte dell’APK si può ispezionare.

## Come viene controllato il codice

Ogni modifica (push) e ogni pull request avvia la [CI](../../.github/workflows/ci.yml) su GitHub Actions:

1. **Verifica del Gradle wrapper**: conferma che `gradle-wrapper.jar` sia il file ufficiale.
2. **50 test automatici**, tra cui:
   - le regole di ogni gioco (mosse valide, punteggio, uscita delle pedine, scale e jolly del Buraco…);
   - i generatori: ogni Sudoku ha una sola soluzione, ogni partita di Mahjong si può risolvere, ogni rompicapo di Tubi e di Unisci i colori ha una soluzione completa, le parole dei cruciverba non si scontrano mai;
   - gli avversari al computer finiscono le partite in fretta e non perdono né duplicano mai una carta o una pedina;
   - tutte le lingue traducono ogni testo e mantengono gli stessi segnaposto;
   - salvataggio e ripresa automatici, gestione dei salvataggi danneggiati, conservazione delle impostazioni;
   - la vera interfaccia: zoom con due dita attivo/disattivo, zoom con una mano a doppio tocco e con “premi e gira”, scelta di un gioco, uscita dalle Impostazioni e la domanda “Continuare?” dopo un riavvio.
3. **Android Lint**, con gli *avvisi trattati come errori*.
4. **Compilazione** degli APK di debug e di release, pubblicati come artifact con i **checksum SHA-256**.

Puoi fare tu stesso gli stessi controlli: vedi [building.md](../en/building.md) (in inglese).

## Verificare un APK scaricato

1. Scarica `MiliGames-vX.Y.Z.apk` e `SHA256SUMS.txt` dalla stessa [release](https://github.com/fershibli/game-mega-app/releases).
2. Calcola il checksum e confrontalo:

   ```bash
   sha256sum MiliGames-vX.Y.Z.apk      # Linux / macOS: shasum -a 256
   certutil -hashfile MiliGames-vX.Y.Z.apk SHA256   # Windows
   ```

3. Controlla che il certificato di firma resti lo stesso da una versione all’altra (del resto Android rifiuta di aggiornare un’app firmata con una chiave diversa):

   ```bash
   apksigner verify --print-certs MiliGames-vX.Y.Z.apk
   ```

4. Per la massima sicurezza, compila tu stesso l’app dal codice sorgente della versione (tag) e confronta il contenuto (`unzip -l`, `aapt2 dump badging`).

## Protezioni della catena di fornitura

- `distributionSha256Sum` fissa la distribuzione esatta di Gradle in [`gradle-wrapper.properties`](../../gradle/wrapper/gradle-wrapper.properties).
- Le dipendenze arrivano solo dal repository Maven di Google e da Maven Central (`repositoriesMode = FAIL_ON_PROJECT_REPOS`).
- [Dependabot](../../.github/dependabot.yml) propone ogni mese gli aggiornamenti delle dipendenze e di GitHub Actions.
- La CI gira con autorizzazioni di sola lettura; solo i workflow di versione e di pubblicazione possono scrivere, e solo per creare il tag della versione e pubblicare la release. Il workflow della chiave di firma scrive solo i segreti di firma, con un token limitato ai segreti di questo repository.
- Ogni APK pubblicato viene verificato nella pipeline prima del caricamento: firma (`apksigner`), nome della versione e assenza di autorizzazioni richieste (`aapt2`).
- Le chiavi di firma delle release sono conservate nei segreti cifrati di GitHub e non vengono mai inserite nel repository (`*.jks` e `keystore.properties` sono esclusi da git).

## L’aiuto dell’IA, in tutta onestà

Il codice, la grafica e la documentazione sono stati creati con l’aiuto di **Claude**, l’assistente di intelligenza artificiale di Anthropic, sotto la guida del responsabile del progetto. Il codice scritto da un’IA può contenere errori, come qualsiasi altro codice. Per questo il progetto si basa su cose che puoi verificare, non su promesse: codice sorgente leggibile, un’ampia serie di test, lint, compilazioni riproducibili e checksum pubblicati. Se trovi un problema, segnalalo.

## Segnalare un problema di sicurezza

Vedi [SECURITY.md](../../SECURITY.md). Per favore non aprire una segnalazione pubblica per una vulnerabilità: usa la segnalazione privata delle vulnerabilità di GitHub.
