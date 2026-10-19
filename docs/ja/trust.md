# 信頼・プライバシー・セキュリティ

🌐 [English](../en/trust.md) · [Português](../pt-BR/trust.md) · [Español](../es/trust.md) · [Français](../fr/trust.md) · [Italiano](../it/trust.md) · [Deutsch](../de/trust.md) · [Русский](../ru/trust.md) · **日本語** · [中文](../zh-CN/trust.md) · [العربية](../ar/trust.md)

Mili Games は、アプリのよしあしをご自分で判断するのがむずかしい方のスマホに入れていただくことを考えて作りました。このページでは、アプリがすること・しないことを説明します。そして何より、作者を信じなくても **だれでも確かめられる方法** をご紹介します。

## 目次

1. [まとめ](#まとめ)
2. [プライバシー](#プライバシー)
3. [権限](#権限)
4. [アプリの中に入っているもの](#アプリの中に入っているもの)
5. [プログラムの確かめ方](#プログラムの確かめ方)
6. [ダウンロードした APK の確かめ方](#ダウンロードした-apk-の確かめ方)
7. [部品の入手経路を守るしくみ](#部品の入手経路を守るしくみ)
8. [AI の手助けについて正直に](#ai-の手助けについて正直に)
9. [セキュリティの問題を見つけたら](#セキュリティの問題を見つけたら)

## まとめ

| | |
|---|---|
| インターネットへの接続 | **なし**（`INTERNET` の権限なし） |
| 集めるデータ | **なし** |
| 広告、利用状況の分析、エラー報告、追跡 | **なし** |
| アカウント、ログイン、課金 | **なし** |
| 求める Android の権限 | **なし** |
| ほかの会社のプログラム | AndroidX（Google）と Kotlin（JetBrains）の公式ライブラリだけ |
| ライセンス | MIT、ソースコードは公開 |

## プライバシー

アプリが保存するのは、次の2つだけです。どちらも、ほかのアプリからは読めない **アプリ専用** の場所に保存されます。

1. `settings.xml` — 設定で選んだ内容（言語、大きさ、色など）。
2. `current_game.json` — 遊んでいる途中のゲーム。アプリを閉じた後でも続きから遊べるようにするためです。ゲームが終わったとき、またはやめたときに消えます。

Android のバックアップをオンにしている場合は、新しいスマホでも設定が引きつがれるように、Android がこの2つのファイルを *あなた自身の* Google のバックアップ（と、スマホからスマホへの引っ越し）にふくめることがあります。これは [`backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) と [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml) で決めています。ほかには何も保存せず、何も送りません。

設定 → このアプリについて にある「プロジェクトのページを開く」ボタンは、ブラウザで GitHub のページを開くよう Android にお願いするだけです。アプリ自体は、そのときも一切通信しません。

## 権限

[マニフェスト](../../app/src/main/AndroidManifest.xml) には `<uses-permission>` が **1つもありません**。Android SDK があれば、どの APK でも確かめられます。

```bash
aapt2 dump permissions MiliGames-v1.0.0.apk
```

表示されるのは、`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` で終わるもの1つだけです。これは利用者に求める権限ではありません。アプリの中だけで使うしくみをほかのアプリから守るために、AndroidX ライブラリが自動で加える、署名レベルの非公開の権限です。だれにも何の許可もあたえません。

`INTERNET` の権限がないので、Android はこのアプリにどんな通信もさせません。

## アプリの中に入っているもの

使っている部品（ライブラリ）の一覧は [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) にすべて書いてあります。

| ライブラリ | 提供元 | 使う理由 |
|---|---|---|
| `androidx.activity:activity-compose` | Google（AndroidX） | Compose の画面をアプリの中に表示する |
| `androidx.compose.ui`、`foundation`、`material3` | Google（AndroidX） | 画面を作るための道具 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JetBrains | 途中のゲームを JSON 形式で保存する |
| Kotlin 標準ライブラリ | JetBrains | プログラム言語を動かすための部品 |

テストのときだけ使うライブラリ（アプリには入りません）：JUnit、Robolectric、Compose UI test。

アプリには **画像、音、フォント、ネイティブライブラリは入っていません**。絵はすべてプログラムでえがいていて、そのプログラムは [`ui/art`](../../app/src/main/java/io/github/fershibli/megagames/ui/art) で読めます。データのファイルは、ふつうのテキストで書かれたクロスワードの辞書だけです（[`assets/crossword`](../../app/src/main/assets/crossword)）。

公開版では、Google の「依存関係情報」のブロック（`dependenciesInfo { includeInApk = false }`）も入れていません。これは、そのままだと APK の中に入る暗号化されたデータです。そのため、APK の中身はすべて確かめられます。

## プログラムの確かめ方

変更が送られるたび、またプルリクエストのたびに、GitHub Actions で [CI](../../.github/workflows/ci.yml) が動きます。

1. **Gradle wrapper の確認** — `gradle-wrapper.jar` が公式のファイルであることを確かめます。
2. **50 の自動テスト**。たとえば次のようなものです。
   - すべてのゲームのルール（打てる手、得点、上がり、ブラコの組とジョーカーなど）
   - 問題作り：どの数独も答えが1つだけ、どの麻雀ソリティアも解ける、どのパイプつなぎと色つなぎにも完全な答えがある、クロスワードの文字が食いちがわない
   - コンピューターの相手がすばやくゲームを終え、カードや駒をなくしたり増やしたりしない
   - すべての言語ですべての文が翻訳され、差しこむ部分（数字や名前など）が同じであること
   - 自動保存の保存と読みこみ、こわれた保存データのあつかい、設定の保存
   - 実際の画面：2本指の拡大のオンとオフ、片手でのダブルタップと押して回すでの拡大、ゲームを選ぶ、設定からゲームをやめる、再起動の後の「続きから遊ぶ？」の確認
3. **Android Lint**。*警告もエラーとしてあつかいます*。
4. デバッグ版と公開版の APK を **組み立て**、**SHA-256 のチェックサム** といっしょに公開します。

同じチェックをご自分で行うには：[building.md（英語）](../en/building.md) をご覧ください。

## ダウンロードした APK の確かめ方

1. 同じ [公開ページ](https://github.com/fershibli/game-mega-app/releases) から、`MiliGames-vX.Y.Z.apk` と `SHA256SUMS.txt` をダウンロードします。
2. チェックサムを計算して、くらべます。

   ```bash
   sha256sum MiliGames-vX.Y.Z.apk      # Linux / macOS: shasum -a 256
   certutil -hashfile MiliGames-vX.Y.Z.apk SHA256   # Windows
   ```

3. 署名の証明書がバージョンが変わっても同じであることを確かめます（Android も、ちがう鍵で署名されたアプリへの更新はこばみます）。

   ```bash
   apksigner verify --print-certs MiliGames-vX.Y.Z.apk
   ```

4. 完全に確かめたいときは、タグのついたソースコードからご自分で組み立てて、中身をくらべてください（`unzip -l`、`aapt2 dump badging`）。

## 部品の入手経路を守るしくみ

- [`gradle-wrapper.properties`](../../gradle/wrapper/gradle-wrapper.properties) の `distributionSha256Sum` で、使う Gradle を正確に固定しています。
- 部品は Google の Maven リポジトリと Maven Central からだけ取りこみます（`repositoriesMode = FAIL_ON_PROJECT_REPOS`）。
- [Dependabot](../../.github/dependabot.yml) が、部品と GitHub Actions の更新を毎月提案します。
- CI は読み取りだけの権限で動きます。書きこめるのはバージョン管理と公開の作業だけで、それもバージョンのタグを付けることと、公開版を出すことに限られます。署名の鍵を作る作業が書きこむのは署名用の秘密情報だけで、このリポジトリの秘密情報にしか使えない鍵を使います。
- 公開するすべての APK は、アップロードの前に自動で確かめます：署名（`apksigner`）、バージョン名、そして権限を何も求めていないこと（`aapt2`）。
- 公開版の署名の鍵は GitHub の暗号化された秘密情報の中にあり、リポジトリには決して入れません（`*.jks` と `keystore.properties` は git で無視する設定です）。

## AI の手助けについて正直に

プログラム、絵、説明書は、管理者の指示のもと、Anthropic 社の AI アシスタント **Claude** の助けを借りて作りました。AI が書いたプログラムにも、ほかのプログラムと同じようにまちがいがあるかもしれません。だからこのプロジェクトでは、約束ではなく、確かめられることを大切にしています：読めるソースコード、幅広いテスト、Lint、同じ結果になる組み立て、公開されたチェックサムです。問題を見つけたら、ぜひお知らせください。

## セキュリティの問題を見つけたら

[SECURITY.md](../../SECURITY.md) をご覧ください。セキュリティの弱点は、だれでも見られる issue には書かないでください。GitHub の非公開の報告機能（private vulnerability reporting）をお使いください。
