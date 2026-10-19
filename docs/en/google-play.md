# Publishing on Google Play

🌐 **English** · [Português](../pt-BR/google-play.md)

Everything the repository can do on its own is automated: the release key, the AAB, the store texts and graphics, and (optionally) uploading every new version. What remains are the steps only the account owner can do: pay, prove their identity, invite testers and fill in Google's forms. This page lists them in order, with the answers for this app.

## Contents

1. [Signing key](#1-signing-key)
2. [Google Play developer account](#2-google-play-developer-account)
3. [Create the app](#3-create-the-app)
4. [App content forms](#4-app-content-forms)
5. [Store listing](#5-store-listing)
6. [Same key on GitHub and on Play](#6-same-key-on-github-and-on-play)
7. [Closed test: 12 testers for 14 days](#7-closed-test-12-testers-for-14-days)
8. [Automatic uploads (optional)](#8-automatic-uploads-optional)
9. [Developer verification and sideloading](#9-developer-verification-and-sideloading)

## 1. Signing key

Android only installs an update signed with the **same key** as the installed app. The [`signing-key.yml`](../../.github/workflows/signing-key.yml) workflow creates that key and keeps it in the repository secrets; it never replaces an existing key.

1. Create a fine-grained token that can write this repository's secrets:
   [new token (pre-filled)](https://github.com/settings/personal-access-tokens/new?name=Mega+Games+signing+key&description=Lets+the+Signing+key+workflow+store+the+release+key+in+the+repository+secrets&expires_in=366&secrets=write).
   Under **Repository access** choose *Only select repositories* → `game-mega-app`, then **Generate token** and copy it.
2. Save it as the secret `MEGAGAMES_SECRETS_TOKEN`: [new repository secret](https://github.com/fershibli/game-mega-app/settings/secrets/actions/new).
3. Run [Signing key → Run workflow](https://github.com/fershibli/game-mega-app/actions/workflows/signing-key.yml) (action `sync`). It creates the key with a random temporary password and stores it in `MEGAGAMES_SIGNING_PASSWORD`. The run summary shows the certificate's SHA-256 fingerprint.
4. Whenever you want, replace `MEGAGAMES_SIGNING_PASSWORD` with your own password (16 characters or more; 20 or more to make backups). The workflow re-encrypts **the same key** with it: within an hour (it checks hourly on the default branch) or right away with *Run workflow*.
5. Run the workflow with action `backup` and keep the downloaded file and your password in a password manager. Without a copy, losing the secret would mean every phone has to uninstall the app to get updates.

From then on every release, including release candidates, is signed with this key. The token expires after a year: create a new one then (only needed to create or re-encrypt the key).

## 2. Google Play developer account

- [Create the account](https://play.google.com/console/signup) — a **personal** account, one-time US$25 fee, identity check with a government ID, 2-step verification.

Google announced in October 2026 that apps built in Google AI Studio can be published without a developer account, but only privately on the creator's own phone. It does not apply to this app.

## 3. Create the app

[Play Console](https://play.google.com/console) → **Create app**:

| Field | Value |
|---|---|
| App name | `Mili Games` |
| Default language | your choice (texts are ready for en-US, pt-BR, es-419, fr-FR, zh-CN) |
| App or game | Game |
| Free or paid | Free |

## 4. App content forms

*Policy and programs → App content*. Answers for this app:

| Form | Answer |
|---|---|
| Privacy policy | `https://github.com/fershibli/game-mega-app/blob/main/PRIVACY.md` |
| Ads | No, the app does not contain ads |
| App access | All functionality is available without special access |
| Content rating (IARC) | Category *Game*; answer **No** to every question (no violence, no gambling, no user interaction, no purchases). Expected rating: Everyone / L |
| Target audience | 18 and over (the app is made for older adults; younger ages bring the Families policy) |
| Data safety | **No**, the app does not collect or share any of the required user data types (Android's own backup is not collection by the developer) |
| Government app, financial features, health, news | No |
| Advertising ID | The app does not use it |

## 5. Store listing

*Grow → Store presence → Main store listing* (add the other languages with *Manage translations*). Everything is in [`fastlane/metadata/android`](../../fastlane/metadata/android):

| Play Console field | File |
|---|---|
| App name | `<language>/title.txt` |
| Short description | `<language>/short_description.txt` |
| Full description | `<language>/full_description.txt` |
| App icon (512×512) | `en-US/images/icon.png` |
| Feature graphic (1024×500) | `en-US/images/featureGraphic.jpg` |
| Phone screenshots | `<language>/images/phoneScreenshots/` |

Category: *Board*. Contact e-mail: required by Google, shown publicly.

To redraw the graphics after a UI change: `./gradlew :app:testDebugUnitTest -PplayAssets=true --tests '*PlayAssetsTest*' && python3 scripts/optimize_screenshots.py`.

## 6. Same key on GitHub and on Play

By default Play re-signs apps with a key generated by Google. Then the APK from GitHub and the app from Play cannot update each other (switching means uninstalling and losing the saved game). To give Play **our** key instead:

1. In Play Console, when creating the first release, open the app signing options and choose to use **your own key**, *Export and upload a key from Java keystore*. Download `encryption_public_key.pem` and open it in a text editor.
2. Run [Signing key](https://github.com/fershibli/game-mega-app/actions/workflows/signing-key.yml) with action `export-for-play`, pasting the file content in `play_encryption_key`.
3. Download the `miligames-play-signing-key` artifact, unzip it once and upload the inner `miligames-play-signing-key.zip` in Play Console. It is encrypted for Google: only Google Play can read it.

## 7. Closed test: 12 testers for 14 days

Personal accounts created after November 2023 must [run a closed test](https://support.google.com/googleplay/android-developer/answer/14151465) with **at least 12 testers opted in for 14 days in a row** before publishing to everyone.

1. *Test and release → Closed testing* → the default *Alpha* track → **Create release**.
2. Upload `MiliGames-vX.Y.Z.aab` from the [latest GitHub release](https://github.com/fershibli/game-mega-app/releases) (the very first bundle must be uploaded by hand).
3. *Testers*: create an e-mail list with 12+ Google accounts (family and friends with Android), copy the opt-in link and send it to them. Each one must open it, accept and install the app.
4. Send the release for review. After 14 days with 12+ testers, *Dashboard → Apply for production* and answer the questions about the test.

## 8. Automatic uploads (optional)

With a service account, [`deploy.yml`](../../.github/workflows/deploy.yml) uploads every new AAB itself (script: [`scripts/play-upload.sh`](../../scripts/play-upload.sh), plain `curl` + `openssl`).

1. [Enable the Google Play Android Developer API](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com) in a Google Cloud project.
2. [Create a service account](https://console.cloud.google.com/iam-admin/serviceaccounts) and a **JSON key** for it.
3. Play Console → *Users and permissions* → **Invite new users** → the service account's e-mail, with permission to release to testing tracks (and production, later).
4. Save the whole JSON as the secret `PLAY_SERVICE_ACCOUNT_JSON`: [new repository secret](https://github.com/fershibli/game-mega-app/settings/secrets/actions/new).

Release candidates then go to the testing track and stable versions follow these [repository variables](https://github.com/fershibli/game-mega-app/settings/variables/actions):

| Variable | Default | Meaning |
|---|---|---|
| `PLAY_TESTING_TRACK` | `alpha` | track for release candidates (and stable versions until production is enabled) |
| `PLAY_RELEASE_STATUS` | `draft` | set to `completed` once the app has passed its first review (Google only accepts drafts before that) |
| `PLAY_PRODUCTION` | — | set to `true` after Google grants production access: stable versions then go to production |

Uploads never happen with the debug key.

## 9. Developer verification and sideloading

Since 30 September 2026, Android in Brazil, Indonesia, Singapore and Thailand requires apps from participating stores (Google Play included) to be registered by a verified developer; Play developers are verified through Play Console. For now this does not apply to APKs installed directly (like the ones on GitHub Releases), but Google plans to extend it to every app in 2027 ([details](https://developer.android.com/developer-verification)).

To keep the GitHub APK installable for family without paying, a free [limited distribution account](https://developer.android.com/developer-verification/guides/limited-distribution) can register the package `io.github.fershibli.miligames` for up to 20 devices, with no ID and no fee.
