<div align="center">

# Mili Games

**昔ながらのゲーム10種類を、小さくて安心なAndroidアプリ1つに。ご年配の方のために作りました。**

ナンプレ · ソリティア · ブラコ · バックギャモン · 麻雀ソリティア · リバーシ · クロスワード · パイプつなぎ · 色つなぎ · ドットつなぎ

🌐 [English](README.md) · [Português](README.pt-BR.md) · [Español](README.es.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Deutsch](README.de.md) · [Русский](README.ru.md) · **日本語** · [中文](README.zh-CN.md) · [العربية](README.ar.md)

[![CI](https://github.com/fershibli/game-mega-app/actions/workflows/ci.yml/badge.svg)](https://github.com/fershibli/game-mega-app/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/fershibli/game-mega-app?include_prereleases&sort=semver)](https://github.com/fershibli/game-mega-app/releases)
![ライセンス：MIT](https://img.shields.io/badge/license-MIT-green)
![Android 6.0以上](https://img.shields.io/badge/Android-6.0%2B-3DDC84)
![APK 約1.3 MB](https://img.shields.io/badge/APK-~1.3%20MB-blue)
![権限なし](https://img.shields.io/badge/permissions-none-brightgreen)

<img src="docs/screenshots/home.png" width="220" alt="ゲームの絵がならぶホーム画面"> <img src="docs/screenshots/game_mahjong_easy.png" width="220" alt="麻雀ソリティア"> <img src="docs/screenshots/game_backgammon.png" width="220" alt="バックギャモン">

</div>

> このアプリは、これらのゲームが大好きな私の母のために作りました。どなたでも、ずっと無料でお使いいただけます。
> 広告なし、アカウントなし、アプリ内課金なし、インターネット接続なし、データ収集なしです。

## 目次

1. [主な特長](#主な特長)
2. [ゲームの紹介](#ゲームの紹介)
3. [ご年配の方のための工夫](#ご年配の方のための工夫)
4. [インストール](#インストール)
5. [安心して使える理由](#安心して使える理由)
6. [画面の例](#画面の例)
7. [説明書](#説明書)
8. [ソースコードからの組み立て](#ソースコードからの組み立て)
9. [AIの助けを借りて作りました](#aiの助けを借りて作りました)
10. [ライセンス](#ライセンス)

## 主な特長

- **10種類のゲーム**。どれも **かんたん／ふつう／むずかしい** から選べます。
- **問題はその場で新しく作られます**（ナンプレ、麻雀ソリティア、パイプつなぎ、色つなぎ、クロスワードなど）。同じ問題がくり返されることはなく、作られた問題は**必ず解ける**ようになっています。
- バックギャモン、ブラコ、リバーシには、それぞれ専用に作った**本格的なコンピューターの相手**がいます。
- **すべてのゲームで2本指の拡大**ができます。片手モードでは1本指でも拡大できます。
- **自動で保存**：うっかりアプリを閉じてしまっても、やめたところからそのまま続けられます。
- **10の言語**：英語、ポルトガル語（ブラジル）、スペイン語、フランス語、イタリア語、ドイツ語、ロシア語、日本語、中国語（簡体字）、アラビア語。画面の表示だけでなく、クロスワードの辞書もそれぞれの言語で用意しています。
- **小さくて軽い**：約 **1.3 MB**。Kotlin と Jetpack Compose で作ったネイティブアプリで、ゲームエンジンも画像ファイルも使っていません。絵はすべてプログラムでえがいています。

## ゲームの紹介

| | ゲーム | どんなゲーム？ | むずかしさによるちがい |
|---|---|---|---|
| <img src="docs/screenshots/game_sudoku.png" width="70"> | **ナンプレ** | おなじみの9×9の数字パズル。メモ、ヒント、一手戻すが使えます | 最初の数字が 40 → 32 → 26 個。かんたん・ふつうではまちがいが赤で表示されます |
| <img src="docs/screenshots/game_solitaire.png" width="70"> | **ソリティア** | クロンダイク。カードをタップすると、いちばんよい場所に動きます | 1枚めくり → 3枚めくり → 3枚めくりで山札は3回まで |
| <img src="docs/screenshots/game_buraco.png" width="70"> | **ブラコ** | コンピューターと対戦するブラジルのラミー（トランプ2組、2はジョーカー、予備の手札、カナスタ） | コンピューターがかしこくなります：捨て札を上手に取り、ジョーカーを大切にし、あなたに役立つカードを捨てません |
| <img src="docs/screenshots/game_backgammon.png" width="70"> | **バックギャモン** | 正式なルール（バー、ヒット、上がり、ギャモン） | コンピューターが「気軽」から、次のサイコロまで読む強さ（エクスペクティマックス）まで |
| <img src="docs/screenshots/game_mahjong_easy.png" width="70"> | **麻雀ソリティア** | 立体的な牌を使う一人遊び。取れない牌はうす暗く表示されます | 牌の数 48 → 96 → 144（定番の「亀」の形） |
| <img src="docs/screenshots/game_reversi.png" width="70"> | **リバーシ** | コンピューターと対戦するオセロ。置ける場所が表示されます | アルファベータ探索の深さ 1 → 3 → 6（終盤は完ぺき） |
| <img src="docs/screenshots/game_crossword_easy.png" width="70"> | **クロスワード** | 毎回新しいクロスワードを、お使いの言語で | 文字ボタン → キーボード全体。むずかしいではまちがいの表示なし |
| <img src="docs/screenshots/game_pipes.png" width="70"> | **パイプつなぎ** | パイプの部品を回して、すべてに水を通します | 5×5 → 7×7 → 9×9 |
| <img src="docs/screenshots/game_connect.png" width="70"> | **色つなぎ** | 同じ色の点どうしを、線を交差させずにつなぎます | 5×5・4色 → 7×7・6色 → 9×9・8色。ふつうとむずかしいでは盤面をすべてうめます |
| <img src="docs/screenshots/game_dots.png" width="70"> | **ドットつなぎ** | 同じ色の点をつなぎます。四角を作るとその色が全部消えます | 盤面が広く、色が多く、手数が少なくなります |

くわしいルールとコツ：**[使い方ガイド](docs/ja/user-guide.md)**

## ご年配の方のための工夫

- **とてもかんたんな操作**：ゲームを選ぶ（絵だけなので、文字を読まなくても大丈夫）→ 回転式の選択でむずかしさを選ぶ → 遊ぶ。歯車（⚙）はいつも同じすみにあります。
- **ボタンは親指の届くところに**：ほとんどの操作ボタンは画面の下半分にあります。**左利きモード**では左右が入れかわります。
- **片手モード**：歯車が画面のはしにかくれる浮かぶボタンになり、**ダブルタップ**や**押して回す**で拡大できます。
- **文字の大きさと、駒やボタンの大きさを調整できます**（80 %〜160 %）。
- **3つの色モード、それぞれにダークモード付き**：ふつう（中くらいのコントラスト）、ハイコントラスト（太いふちどり）、色覚サポート（Okabe–Ito の配色、色のついた駒に形の目印、4色のトランプ）。
- 大きなタッチ範囲（56 dp）、大きな文字、ヒント、何度でも使える「一手戻す」。時間制限はどこにもありません。

くわしくは：**[使い方ガイドの「アクセシビリティ」](docs/ja/user-guide.md#アクセシビリティ)**

## インストール

1. Android のスマホで **[Releases（公開ページ）](https://github.com/fershibli/game-mega-app/releases)** を開き、いちばん新しい `MiliGames-vX.Y.Z.apk` をダウンロードします。
2. ファイルを開きます。Android にたずねられたら、ブラウザやファイルアプリに「不明なアプリのインストール」を許可してください。
3. 必要なら：ファイルの SHA-256 を、同じページにある `SHA256SUMS.txt` とくらべてください。

Android 6.0 以降が必要です。絵つきの手順はこちら：[使い方ガイド → インストール](docs/ja/user-guide.md#インストール)

## 安心して使える理由

| お約束 | ご自分で確かめる方法 |
|---|---|
| インターネットを使わず、追跡もしない | マニフェストで**権限を1つも宣言していません**（[AndroidManifest.xml](app/src/main/AndroidManifest.xml)）。`INTERNET` の権限がないと、Android はすべての通信をさえぎります。 |
| かくれたプログラムはない | 100 % オープンソースです。使っているのは AndroidX と Kotlin の公式ライブラリだけです（[libs.versions.toml](gradle/libs.versions.toml)）。広告や分析のための部品は入っていません。 |
| CI が作ったものが、そのまま届く | 変更のたびに GitHub Actions で組み立て、テスト、チェックを行い、APK は SHA-256 のチェックサムといっしょに公開されます。Google の「依存関係情報」のデータは入れていないので、APK の中身はすべて確かめられます。 |
| きちんと動く | 組み立てのたびに 50 の自動テストを行います：ルール、問題作り（どの問題も解けること）、コンピューターの相手、翻訳、自動保存、画面の操作（2本指の拡大、片手での拡大、画面の移動）。Lint の警告もエラーとしてあつかいます。 |
| 組み立ての道具も安全 | Gradle wrapper のチェックサムを固定し、CI で確かめています。Dependabot が依存ライブラリを最新に保ちます。 |

くわしくは：**[信頼・プライバシー・セキュリティ](docs/ja/trust.md)** · [SECURITY.md](SECURITY.md)

## 画面の例

| ホーム（ダーク） | ハイコントラスト | 色覚サポート＋ダーク | 片手モード |
|---|---|---|---|
| <img src="docs/screenshots/home_dark.png" width="180"> | <img src="docs/screenshots/home_high_contrast.png" width="180"> | <img src="docs/screenshots/home_colorblind_dark.png" width="180"> | <img src="docs/screenshots/home_one_hand.png" width="180"> |

| むずかしさ | 設定 | 続きから遊ぶ？ | ブラコ（ポルトガル語） |
|---|---|---|---|
| <img src="docs/screenshots/difficulty.png" width="180"> | <img src="docs/screenshots/settings.png" width="180"> | <img src="docs/screenshots/resume_dialog.png" width="180"> | <img src="docs/screenshots/game_buraco_pt.png" width="180"> |

画面の例はすべて、実際のプログラムから自動で作られています（[Building（英語）](docs/en/building.md#screenshots)をご覧ください）。

## 説明書

| 言語 | 使い方ガイド | 信頼とプライバシー | 技術的な設計 | 組み立てと参加のしかた |
|---|---|---|---|---|
| English | [user-guide](docs/en/user-guide.md) | [trust](docs/en/trust.md) | [technical](docs/en/technical.md) | [building](docs/en/building.md) |
| Português | [guia](docs/pt-BR/user-guide.md) | [confiança](docs/pt-BR/trust.md) | [técnico](docs/pt-BR/technical.md) | [compilar](docs/pt-BR/building.md) |
| Español | [guía](docs/es/user-guide.md) | [confianza](docs/es/trust.md) | [técnico](docs/es/technical.md) | [compilar](docs/es/building.md) |
| Français | [guide](docs/fr/user-guide.md) | [confiance](docs/fr/trust.md) | [technique](docs/fr/technical.md) | [compiler](docs/fr/building.md) |
| Italiano | [guida](docs/it/user-guide.md) | [fiducia](docs/it/trust.md) | [technical（英語）](docs/en/technical.md) | [building（英語）](docs/en/building.md) |
| Deutsch | [Anleitung](docs/de/user-guide.md) | [Vertrauen](docs/de/trust.md) | [technical（英語）](docs/en/technical.md) | [building（英語）](docs/en/building.md) |
| Русский | [руководство](docs/ru/user-guide.md) | [доверие](docs/ru/trust.md) | [technical（英語）](docs/en/technical.md) | [building（英語）](docs/en/building.md) |
| 日本語 | [使い方ガイド](docs/ja/user-guide.md) | [信頼とプライバシー](docs/ja/trust.md) | [technical（英語）](docs/en/technical.md) | [building（英語）](docs/en/building.md) |
| 中文 | [用户指南](docs/zh-CN/user-guide.md) | [信任与隐私](docs/zh-CN/trust.md) | [技术设计](docs/zh-CN/technical.md) | [构建与贡献](docs/zh-CN/building.md) |
| العربية | [دليل الاستخدام](docs/ar/user-guide.md) | [الثقة والخصوصية](docs/ar/trust.md) | [technical (EN)](docs/en/technical.md) | [building (EN)](docs/en/building.md) |

すべての一覧：[docs/README.md](docs/README.md)

## ソースコードからの組み立て

```bash
git clone https://github.com/fershibli/game-mega-app.git
cd game-mega-app
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

JDK 17 以降と Android SDK（API 37）が必要です。[docs/en/building.md](docs/en/building.md)（英語）をご覧ください。

## AIの助けを借りて作りました

このリポジトリにあるものはすべて、つまりプログラム、絵（カード、牌、盤、アイコンはすべてプログラムでえがいています）、そして10の言語で書かれたこの説明書は、管理者の指示のもと、**Anthropic** 社の AI アシスタント **Claude** の助けを借りて作りました。設計の考え方は [docs/en/technical.md](docs/en/technical.md)（英語）で説明しています。また、プログラムをただ信じてもらうのではなく確かめてもらえるように、テストを用意しています。

## ライセンス

[MIT](LICENSE) — 自由に使い、コピーし、変更し、共有できます。
