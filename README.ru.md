<div align="center">

# Mili Games

**Десять классических игр в одном маленьком приложении для Android, которое бережёт вашу частную жизнь. Создано для пожилых людей.**

Судоку · Пасьянс «Косынка» · Бурако · Нарды · Маджонг · Реверси · Кроссворд · Трубы · Соедини цвета · Точки

🌐 [English](README.md) · [Português](README.pt-BR.md) · [Español](README.es.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Deutsch](README.de.md) · **Русский** · [日本語](README.ja.md) · [中文](README.zh-CN.md)

[![CI](https://github.com/fershibli/game-mega-app/actions/workflows/ci.yml/badge.svg)](https://github.com/fershibli/game-mega-app/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/fershibli/game-mega-app?include_prereleases&sort=semver)](https://github.com/fershibli/game-mega-app/releases)
![Лицензия: MIT](https://img.shields.io/badge/%D0%BB%D0%B8%D1%86%D0%B5%D0%BD%D0%B7%D0%B8%D1%8F-MIT-green)
![Android 6.0+](https://img.shields.io/badge/Android-6.0%2B-3DDC84)
![APK около 1,3 МБ](https://img.shields.io/badge/APK-~1%2C3%20%D0%9C%D0%91-blue)
![Без разрешений](https://img.shields.io/badge/%D1%80%D0%B0%D0%B7%D1%80%D0%B5%D1%88%D0%B5%D0%BD%D0%B8%D1%8F-%D0%BD%D0%B5%D1%82-brightgreen)

<img src="docs/screenshots/home.png" width="220" alt="Главный экран с картинками игр"> <img src="docs/screenshots/game_mahjong_easy.png" width="220" alt="Маджонг"> <img src="docs/screenshots/game_backgammon.png" width="220" alt="Нарды">

</div>

> Я сделал это приложение для своей мамы, которая очень любит эти игры. Оно бесплатно для всех и навсегда:
> без рекламы, без учётных записей, без покупок внутри приложения, без доступа к интернету и без сбора данных.

## Содержание

1. [Главное](#главное)
2. [Игры](#игры)
3. [Сделано для пожилых людей](#сделано-для-пожилых-людей)
4. [Установка](#установка)
5. [Почему ему можно доверять](#почему-ему-можно-доверять)
6. [Снимки экрана](#снимки-экрана)
7. [Документация](#документация)
8. [Сборка из исходного кода](#сборка-из-исходного-кода)
9. [Создано с помощью ИИ](#создано-с-помощью-ии)
10. [Лицензия](#лицензия)

## Главное

- **10 игр**, в каждой три уровня: **Легко / Средне / Сложно**.
- **Головоломки создаются заново каждый раз** (Судоку, Маджонг, Трубы, Соедини цвета, Кроссворд…), поэтому никогда не повторяются — и каждая из них **гарантированно решаема**.
- **Настоящие соперники-компьютеры**, написанные специально для Нард, Бурако и Реверси.
- **Масштаб двумя пальцами в каждой игре**, а в режиме одной руки — масштаб одним пальцем.
- **Автоматическое сохранение**: если вы случайно закрыли приложение, оно предложит продолжить ровно с того места, где вы остановились.
- **9 языков**: английский, португальский, испанский, французский, итальянский, немецкий, русский, японский и китайский — и интерфейс, *и* слова для кроссворда.
- **Маленькое и лёгкое**: около **1,3 МБ**, написано на Kotlin + Jetpack Compose, без игрового движка и без файлов с картинками — все рисунки нарисованы прямо в коде.

## Игры

| | Игра | Что это | Что меняется со сложностью |
|---|---|---|---|
| <img src="docs/screenshots/game_sudoku.png" width="70"> | **Судоку** | Классическая числовая головоломка 9×9 с заметками, подсказками и отменой хода | 40 → 32 → 26 открытых чисел; на уровнях «Легко» и «Средне» ошибки видны красным |
| <img src="docs/screenshots/game_solitaire.png" width="70"> | **Пасьянс «Косынка»** | Клондайк; коснитесь карты, и она сама перейдёт на лучшее место | Сдача по 1 карте → по 3 карты → по 3 карты и только 3 прохода колоды |
| <img src="docs/screenshots/game_buraco.png" width="70"> | **Бурако** | Бразильская карточная игра вроде рамми против компьютера (две колоды, двойки — джокеры, прикупы, канасты) | Компьютер умнее: с умом забирает сброс, бережёт джокеров, не подкидывает вам нужные карты |
| <img src="docs/screenshots/game_backgammon.png" width="70"> | **Нарды** | Короткие нарды (бэкгаммон) по полным правилам (бар, взятие шашек, выбрасывание, марс) | Компьютер от «играет наугад» до просчёта на один бросок вперёд (expectimax) |
| <img src="docs/screenshots/game_mahjong_easy.png" width="70"> | **Маджонг** | Пасьянс маджонг с объёмными костями; занятые кости затемнены | 48 → 96 → 144 кости (классическая «черепаха») |
| <img src="docs/screenshots/game_reversi.png" width="70"> | **Реверси** | «Отелло» против компьютера; возможные ходы показаны | Альфа-бета поиск на глубину 1 → 3 → 6 (идеальный эндшпиль) |
| <img src="docs/screenshots/game_crossword_easy.png" width="70"> | **Кроссворд** | Каждый раз новый кроссворд, на вашем языке | Набор букв → полная клавиатура; на уровне «Сложно» ошибки не подсвечиваются |
| <img src="docs/screenshots/game_pipes.png" width="70"> | **Трубы** | Поворачивайте детали, пока вода не дойдёт до каждой трубы | 5×5 → 7×7 → 9×9 |
| <img src="docs/screenshots/game_connect.png" width="70"> | **Соедини цвета** | Соединяйте точки одного цвета так, чтобы линии не пересекались | 5×5 и 4 цвета → 7×7 и 6 → 9×9 и 8; на уровнях «Средне» и «Сложно» нужно заполнить всё поле |
| <img src="docs/screenshots/game_dots.png" width="70"> | **Точки** | Соединяйте точки одного цвета; замкните квадрат, чтобы убрать весь цвет | Поле больше, цветов больше, ходов меньше |

Полные правила и советы: **[Руководство пользователя](docs/ru/user-guide.md)**.

## Сделано для пожилых людей

- **Очень простая навигация**: выберите игру (только картинка, читать ничего не нужно) → выберите сложность на прокручиваемом колесе → играйте. Шестерёнка (⚙) всегда в одном и том же углу.
- **Кнопки там, где большой палец**: почти все кнопки действий находятся в нижней половине экрана. **Режим для левшей** переносит их на другую сторону.
- **Режим одной руки**: шестерёнка превращается в плавающую кнопку, которая прячется у края экрана; масштаб меняется **двойным касанием** или **нажатием с вращением**.
- **Настраиваемый размер текста и размер фишек и кнопок** (от 80 % до 160 %).
- **Три цветовых режима, у каждого своя тёмная тема**: обычный (средний контраст), высокий контраст (толстые контуры) и для дальтоников (палитра Окабе–Ито, значки на цветных фишках, колода карт четырёх цветов).
- Крупные кнопки (56 dp), крупный шрифт, подсказки, неограниченная отмена хода и никаких таймеров.

Подробнее: **[раздел о специальных возможностях в руководстве](docs/ru/user-guide.md#специальные-возможности)**.

## Установка

1. Откройте на своём телефоне Android страницу **[Releases](https://github.com/fershibli/game-mega-app/releases)** и скачайте последний файл `MiliGames-vX.Y.Z.apk`.
2. Откройте этот файл. Если Android спросит, разрешите браузеру или приложению «Файлы» «устанавливать неизвестные приложения».
3. По желанию: сравните контрольную сумму SHA-256 файла с файлом `SHA256SUMS.txt` из того же выпуска.

Нужен Android 6.0 или новее. Пошаговая инструкция: [Руководство → Установка](docs/ru/user-guide.md#установка).

## Почему ему можно доверять

| Обещание | Как проверить самому |
|---|---|
| Нет интернета, нет слежки | В манифесте **нет ни одного разрешения** ([AndroidManifest.xml](app/src/main/AndroidManifest.xml)); без разрешения `INTERNET` Android блокирует любой доступ к сети. |
| Нет скрытого кода | 100 % открытый исходный код; только официальные библиотеки AndroidX и Kotlin ([libs.versions.toml](gradle/libs.versions.toml)). Никаких SDK для рекламы или аналитики. |
| Вы получаете именно то, что собрано в CI | Каждое изменение собирается, тестируется и проверяется линтером в GitHub Actions; APK публикуются с контрольными суммами SHA-256. Блок Google «dependency info» отключён, так что содержимое APK можно полностью изучить. |
| Оно работает | 50 автоматических тестов при каждой сборке: правила, генераторы (каждая головоломка решаема), соперники-компьютеры, переводы, автосохранение и работа интерфейса (масштаб двумя пальцами, масштаб одной рукой, навигация). Линтер считает предупреждения ошибками. |
| Надёжные инструменты сборки | Контрольная сумма Gradle wrapper закреплена и проверяется в CI; Dependabot поддерживает зависимости в актуальном состоянии. |

Подробнее: **[Доверие, конфиденциальность и безопасность](docs/ru/trust.md)** · [SECURITY.md](SECURITY.md)

## Снимки экрана

| Главный экран (тёмная тема) | Высокий контраст | Для дальтоников + тёмная тема | Режим одной руки |
|---|---|---|---|
| <img src="docs/screenshots/home_dark.png" width="180"> | <img src="docs/screenshots/home_high_contrast.png" width="180"> | <img src="docs/screenshots/home_colorblind_dark.png" width="180"> | <img src="docs/screenshots/home_one_hand.png" width="180"> |

| Сложность | Настройки | Продолжить? | Бурако (на португальском) |
|---|---|---|---|
| <img src="docs/screenshots/difficulty.png" width="180"> | <img src="docs/screenshots/settings.png" width="180"> | <img src="docs/screenshots/resume_dialog.png" width="180"> | <img src="docs/screenshots/game_buraco_pt.png" width="180"> |

Все снимки экрана создаются автоматически из настоящего кода (см. [Building](docs/en/building.md#screenshots), на английском).

## Документация

| Язык | Руководство пользователя | Доверие и конфиденциальность | Техническое устройство | Сборка и участие |
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

Полный указатель: [docs/README.md](docs/README.md). Публикация в Google Play: [google-play.md](docs/en/google-play.md) (на английском).

## Сборка из исходного кода

```bash
git clone https://github.com/fershibli/game-mega-app.git
cd game-mega-app
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Нужны JDK 17+ и Android SDK (API 37). Подробнее: [docs/en/building.md](docs/en/building.md) (на английском).

## Создано с помощью ИИ

Всё в этом репозитории — код, рисунки (каждая карта, кость, доска и значок нарисованы в коде) и эта документация на девяти языках — создано с помощью **Claude**, ИИ-ассистента компании **Anthropic**, под руководством автора проекта. Почему всё устроено именно так, объясняется в [docs/en/technical.md](docs/en/technical.md) (на английском), а набор тестов существует для того, чтобы никому не приходилось верить коду на слово.

## Лицензия

[MIT](LICENSE) — можно бесплатно использовать, копировать, изменять и распространять.
