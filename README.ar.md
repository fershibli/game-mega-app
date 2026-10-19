<div dir="rtl">

<div align="center">

# Mili Games

**عشر ألعاب كلاسيكية في تطبيق أندرويد صغير يحترم خصوصيتك، صُمم لكبار السن.**

سودوكو · سوليتير · بوراكو · طاولة الزهر · ماهجونغ · ريفيرسي · الكلمات المتقاطعة · الأنابيب · صِل الألوان · النقاط

🌐 [English](README.md) · [Português](README.pt-BR.md) · [Español](README.es.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Deutsch](README.de.md) · [Русский](README.ru.md) · [日本語](README.ja.md) · [中文](README.zh-CN.md) · **العربية**

[![CI](https://github.com/fershibli/game-mega-app/actions/workflows/ci.yml/badge.svg)](https://github.com/fershibli/game-mega-app/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/fershibli/game-mega-app?include_prereleases&sort=semver)](https://github.com/fershibli/game-mega-app/releases)
![License: MIT](https://img.shields.io/badge/license-MIT-green)
![Android 6.0+](https://img.shields.io/badge/Android-6.0%2B-3DDC84)
![APK about 1.3 MB](https://img.shields.io/badge/APK-~1.3%20MB-blue)
![No permissions](https://img.shields.io/badge/permissions-none-brightgreen)

<img src="docs/screenshots/home.png" width="220" alt="الشاشة الرئيسية مع صور الألعاب"> <img src="docs/screenshots/game_mahjong_easy.png" width="220" alt="ماهجونغ"> <img src="docs/screenshots/game_backgammon.png" width="220" alt="طاولة الزهر">

</div>

> صنعتُ هذا التطبيق لأمي، فهي تحب هذه الألعاب. وهو مجاني للجميع، وإلى الأبد:
> لا إعلانات، ولا حسابات، ولا مشتريات داخل التطبيق، ولا اتصال بالإنترنت، ولا جمع لأي بيانات.

## المحتويات

1. [أبرز المزايا](#أبرز-المزايا)
2. [الألعاب](#الألعاب)
3. [مصمم لكبار السن](#مصمم-لكبار-السن)
4. [التثبيت](#التثبيت)
5. [لماذا يمكنك الوثوق به](#لماذا-يمكنك-الوثوق-به)
6. [لقطات الشاشة](#لقطات-الشاشة)
7. [التوثيق](#التوثيق)
8. [البناء من الشيفرة المصدرية](#البناء-من-الشيفرة-المصدرية)
9. [صنع بمساعدة الذكاء الاصطناعي](#صنع-بمساعدة-الذكاء-الاصطناعي)
10. [الترخيص](#الترخيص)

## أبرز المزايا

- **10 ألعاب**، ولكل منها ثلاثة مستويات: **سهل / متوسط / صعب**.
- **الألغاز تُصنع في لحظتها** (سودوكو، ماهجونغ، الأنابيب، صِل الألوان، الكلمات المتقاطعة…)، فلا تتكرر أبدًا، وكل لغز منها **يمكن حله دائمًا**.
- **منافسون حقيقيون من الكمبيوتر** كُتبوا خصيصًا لطاولة الزهر وبوراكو وريفيرسي.
- **التكبير بإصبعين في كل لعبة**، والتكبير بإصبع واحد في وضع اليد الواحدة.
- **حفظ تلقائي**: إذا أغلقت التطبيق عن طريق الخطأ، يعرض عليك المتابعة من النقطة نفسها التي توقفت عندها.
- **10 لغات**: الإنجليزية، والبرتغالية (البرازيل)، والإسبانية، والفرنسية، والإيطالية، والألمانية، والروسية، واليابانية، والصينية المبسطة، والعربية. الترجمة تشمل الواجهة *و*قواميس الكلمات المتقاطعة.
- **صغير وخفيف**: نحو **1.3 ميغابايت**، مكتوب بلغة Kotlin الأصلية مع Jetpack Compose، بلا محرك ألعاب وبلا صور: كل الرسوم مرسومة بالشيفرة.

## الألعاب

| | اللعبة | ما هي | كيف تتغير الصعوبة |
|---|---|---|---|
| <img src="docs/screenshots/game_sudoku.png" width="70"> | **سودوكو** | لغز الأرقام الكلاسيكي 9×9، مع ملاحظات وتلميحات وتراجع | 40 → 32 → 26 رقمًا في البداية؛ الأخطاء تظهر بالأحمر في السهل والمتوسط |
| <img src="docs/screenshots/game_solitaire.png" width="70"> | **سوليتير** | كلوندايك؛ اضغط على ورقة فتنتقل إلى أفضل مكان | سحب ورقة واحدة → سحب 3 أوراق → سحب 3 أوراق مع 3 دورات فقط للرزمة |
| <img src="docs/screenshots/game_buraco.png" width="70"> | **بوراكو** | لعبة الورق البرازيلية ضد الكمبيوتر (رزمتان، ورقة 2 جوكر، أوراق احتياطية، كاناستا) | كمبيوتر أذكى: يأخذ الكومة بحكمة، ويحافظ على الجوكر، ويتجنب أن يعطيك أوراقًا مفيدة |
| <img src="docs/screenshots/game_backgammon.png" width="70"> | **طاولة الزهر** | القواعد الكاملة (الحاجز، ضرب القطع، إخراج القطع، الغامون) | من كمبيوتر «عفوي» إلى كمبيوتر يتوقع الرمية التالية (expectimax) |
| <img src="docs/screenshots/game_mahjong_easy.png" width="70"> | **ماهجونغ** | ماهجونغ سوليتير بقطع ثلاثية الأبعاد؛ القطع المحجوزة باهتة | 48 → 96 → 144 قطعة (السلحفاة الكلاسيكية) |
| <img src="docs/screenshots/game_reversi.png" width="70"> | **ريفيرسي** | عُطيل ضد الكمبيوتر؛ الحركات الممكنة معروضة | بحث ألفا-بيتا بعمق 1 → 3 → 6 (نهاية مثالية) |
| <img src="docs/screenshots/game_crossword_easy.png" width="70"> | **الكلمات المتقاطعة** | شبكة جديدة في كل مرة، بلغتك | أزرار الحروف → لوحة مفاتيح كاملة؛ لا يُظهر الأخطاء في المستوى الصعب (بالعربية: أزرار الحروف دائمًا) |
| <img src="docs/screenshots/game_pipes.png" width="70"> | **الأنابيب** | أدِر قطع الأنابيب حتى يصل الماء إلى كل مكان | 5×5 → 7×7 → 9×9 |
| <img src="docs/screenshots/game_connect.png" width="70"> | **صِل الألوان** | صِل النقاط التي لها اللون نفسه دون أن تتقاطع الخطوط | 5×5/4 ألوان → 7×7/6 → 9×9/8؛ في المتوسط والصعب يجب ملء اللوحة كلها |
| <img src="docs/screenshots/game_dots.png" width="70"> | **النقاط** | صِل النقاط التي لها اللون نفسه؛ أغلق مربعًا لتزيل لونًا كاملًا | لوحة أكبر، وألوان أكثر، وحركات أقل |

القواعد الكاملة والنصائح: **[دليل الاستخدام](docs/ar/user-guide.md)**.

## مصمم لكبار السن

- **تنقل بسيط جدًّا**: اختر لعبة (بالصور فقط، دون حاجة إلى القراءة) ← اختر الصعوبة على عجلة دوّارة ← العب. الترس (⚙) دائمًا في الزاوية نفسها.
- **الأزرار حيث يصل الإبهام**: معظم أزرار الأفعال في النصف السفلي من الشاشة. **وضع اليد اليسرى** ينقلها إلى الجهة الأخرى.
- **وضع اليد الواحدة**: يصبح الترس فقاعة عائمة تختبئ نصفها في حافة الشاشة؛ ويعمل التكبير **بالنقر مرتين** أو **بالضغط والتدوير**.
- **حجم النص وحجم القطع والأزرار قابلان للتعديل** (من 80 % إلى 160 %).
- **ثلاثة أوضاع للألوان، لكل منها نسخته الداكنة**: عادي (تباين متوسط)، تباين عالٍ (حواف سميكة)، مناسب لعمى الألوان (ألوان Okabe–Ito، وأشكال مرسومة على القطع الملونة، وأوراق لعب بأربعة ألوان).
- أهداف لمس كبيرة (56 dp)، وخط كبير، وتلميحات، وتراجع بلا حدود، ولا مؤقتات في أي مكان.

المزيد: **[قسم إمكانية الوصول في دليل الاستخدام](docs/ar/user-guide.md#إمكانية-الوصول)**.

## التثبيت

1. افتح صفحة **[Releases](https://github.com/fershibli/game-mega-app/releases)** على هاتفك الأندرويد، ونزّل أحدث ملف `MiliGames-vX.Y.Z.apk`.
2. افتح الملف. إذا طلب أندرويد ذلك، فاسمح للمتصفح أو لتطبيق الملفات بـ«تثبيت التطبيقات غير المعروفة».
3. اختياري: قارن بصمة SHA-256 للملف مع الملف `SHA256SUMS.txt` في الإصدار نفسه.

يحتاج إلى أندرويد 6.0 أو أحدث. تعليمات خطوة بخطوة مع الصور: [دليل الاستخدام ← التثبيت](docs/ar/user-guide.md#التثبيت).

## لماذا يمكنك الوثوق به

| الادعاء | كيف تتحقق منه بنفسك |
|---|---|
| لا إنترنت ولا تتبع | ملف manifest **لا يطلب أي إذن على الإطلاق** ([AndroidManifest.xml](app/src/main/AndroidManifest.xml))؛ ومن دون إذن `INTERNET` يمنع أندرويد أي اتصال بالشبكة. |
| لا شيفرة مخفية | مفتوح المصدر 100 %؛ يستخدم فقط مكتبات AndroidX وKotlin الرسمية ([libs.versions.toml](gradle/libs.versions.toml)). لا أدوات للإعلانات أو الإحصاءات. |
| ما تبنيه CI هو ما تحصل عليه | كل تعديل يُبنى ويُختبر ويُفحص بـ lint على GitHub Actions؛ وتُنشر ملفات APK مع بصمات SHA-256. كتلة «dependency info» من Google معطلة، لذلك يمكن فحص محتوى ملف APK بالكامل. |
| إنه يعمل | 50 اختبارًا آليًّا في كل بناء: القواعد، والمولّدات (كل لغز قابل للحل)، ومنافسو الكمبيوتر، والترجمات، والحفظ التلقائي، ومسار الواجهة (التكبير بإصبعين، والتكبير بيد واحدة، والتنقل). ويعامل lint التحذيرات كأخطاء. |
| أدوات بناء آمنة | بصمة Gradle wrapper مثبتة ويُتحقق منها في CI؛ وDependabot يُبقي المكتبات محدّثة. |

التفاصيل: **[الثقة والخصوصية والأمان](docs/ar/trust.md)** · [SECURITY.md](SECURITY.md)

## لقطات الشاشة

| الشاشة الرئيسية (داكن) | تباين عالٍ | عمى الألوان + داكن | وضع اليد الواحدة |
|---|---|---|---|
| <img src="docs/screenshots/home_dark.png" width="180"> | <img src="docs/screenshots/home_high_contrast.png" width="180"> | <img src="docs/screenshots/home_colorblind_dark.png" width="180"> | <img src="docs/screenshots/home_one_hand.png" width="180"> |

| الصعوبة | الإعدادات | هل تتابع؟ | بوراكو (Português) |
|---|---|---|---|
| <img src="docs/screenshots/difficulty.png" width="180"> | <img src="docs/screenshots/settings.png" width="180"> | <img src="docs/screenshots/resume_dialog.png" width="180"> | <img src="docs/screenshots/game_buraco_pt.png" width="180"> |

كل لقطات الشاشة تُنشأ تلقائيًّا من الشيفرة الحقيقية (انظر [Building](docs/en/building.md#screenshots)، بالإنجليزية).

## التوثيق

| اللغة | دليل الاستخدام | الثقة والخصوصية | التصميم التقني | البناء والمساهمة |
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
| العربية | [دليل الاستخدام](docs/ar/user-guide.md) | [الثقة والخصوصية](docs/ar/trust.md) | [التصميم التقني (بالإنجليزية)](docs/en/technical.md) | [البناء (بالإنجليزية)](docs/en/building.md) |

النشر على Google Play مشروح في [google-play.md](docs/en/google-play.md) (بالإنجليزية). الفهرس الكامل: [docs/README.md](docs/README.md).

## البناء من الشيفرة المصدرية

```bash
git clone https://github.com/fershibli/game-mega-app.git
cd game-mega-app
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

يحتاج إلى JDK 17 أو أحدث وAndroid SDK (API 37). انظر [docs/en/building.md](docs/en/building.md) (بالإنجليزية).

## صنع بمساعدة الذكاء الاصطناعي

كل ما في هذا المستودع، أي الشيفرة والرسوم (كل ورقة وقطعة ولوحة وأيقونة مرسومة بالشيفرة) وهذا التوثيق بعشر لغات، صُنع بمساعدة **Claude**، مساعد الذكاء الاصطناعي من شركة **Anthropic**، وبتوجيه من القائم على المشروع. خيارات التصميم مشروحة في [docs/en/technical.md](docs/en/technical.md) (بالإنجليزية)، ومجموعة الاختبارات موجودة حتى لا يضطر أحد إلى الوثوق بالشيفرة دون دليل.

## الترخيص

[MIT](LICENSE): يمكنك استخدامه ونسخه وتعديله ومشاركته بحرية.

</div>
