# 构建、发布与贡献

🌐 [English](../en/building.md) · [Português](../pt-BR/building.md) · [Español](../es/building.md) · [Français](../fr/building.md) · **中文**

## 目录

1. [环境要求](#环境要求)
2. [构建与运行](#构建与运行)
3. [测试与 Lint](#测试与-lint)
4. [截图](#截图)
5. [AI 基准测试](#ai-基准测试)
6. [签名发布](#签名发布)
7. [版本管理与部署](#版本管理与部署)
8. [如何贡献](#如何贡献)
9. [添加填字词语](#添加填字词语)
10. [添加或改进翻译](#添加或改进翻译)
11. [添加新游戏](#添加新游戏)

## 环境要求

- **JDK 17 或更高版本**（CI 使用 Temurin 21）。
- 安装了 **API 37** 平台的 **Android SDK**（Android Studio 会自动安装；或运行 `sdkmanager "platforms;android-37"`）。
- 首次构建需要联网（Gradle 会下载自身和依赖）。

通过 `ANDROID_HOME` 环境变量，或在 `local.properties` 中写入 `sdk.dir=/path/to/Android/sdk` 来指定 SDK 位置。

## 构建与运行

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # 安装到已连接的手机/模拟器（需开启 USB 调试）
./gradlew assembleRelease    # 经 R8 精简；未配置密钥时不签名
```

也可以用 **Android Studio** 打开项目目录并点 *Run*。

## 测试与 Lint

```bash
./gradlew testDebugUnitTest   # 50 个测试：规则、生成器、AI、翻译、界面流程
./gradlew lintDebug           # Android Lint，警告视为错误
```

报告：`app/build/reports/tests/testDebugUnitTest/index.html` 和 `app/build/reports/lint-results-debug.html`。

界面测试使用 **Robolectric**，在 JVM 上运行，不需要模拟器。

## 截图

README 中的所有图片都由真实代码渲染：

```bash
./gradlew :app:testDebugUnitTest -Pscreenshots=true --tests '*ScreenshotTest*'
# PNG 文件会写入 docs/screenshots/
```

提交前请先压缩：`python3 scripts/optimize_screenshots.py`（需要 Pillow）。

要添加截图，在 `app/src/test/.../ScreenshotTest.kt` 中用 `shot(...)` 或 `gameShot(...)` 添加一个 `@Test`。

## AI 基准测试

```bash
./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*AiBenchmarkTest*' -i | grep BENCHMARK
```

让电脑对手互相对战（巴西拉米、双陆棋、黑白棋）并输出胜率。结果记录在 [technical.md](technical.md#基准测试)。

## 签名发布

安卓只安装已签名的 APK，而且更新必须使用**同一把密钥**签名。密钥一旦丢失，用户必须卸载后才能安装新版本。

**在 GitHub 上（推荐）**：[`signing-key.yml`](../../.github/workflows/signing-key.yml) 工作流会创建密钥并保存在仓库的 Secrets 中；每当你修改 `MEGAGAMES_SIGNING_PASSWORD` 中的密码，它会用新密码重新加密同一把密钥。它从不替换已有的密钥，还可以生成加密备份。设置方法（一个令牌、点两下）：[google-play.md → Signing key](../en/google-play.md#1-signing-key)（英文）。

[部署流水线](#版本管理与部署) 用这把密钥为每个 APK 和 AAB 签名。没有它时，候选版（`stag`）仍然会发布，但使用临时调试密钥签名，并在版本说明中明确标注；正式版（`main`）则不会发布：流水线会在创建标签之前失败，创建密钥后重新运行即可。

**本地签名**：拿到密钥文件（来自备份）后，在项目根目录创建 `keystore.properties`（已被 git 忽略）：

```properties
storeFile=miligames-release.p12
storePassword=...
keyAlias=megagames
keyPassword=...
```

然后 `./gradlew assembleRelease bundleRelease` 会生成 `app/build/outputs/apk/release/app-release.apk` 和 `app/build/outputs/bundle/release/app-release.aab`。

**Google Play**：包含 Play Console 每个表单答案的分步指南：[google-play.md](../en/google-play.md)（英文）。

## 版本管理与部署

版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)（`主版本.次版本.修订号`），由我们自己编写的工作流 [`versioning.yml`](../../.github/workflows/versioning.yml)（纯 bash，不使用第三方发布工具）根据 [Conventional Commits 约定式提交](https://www.conventionalcommits.org/zh-hans/)**自动**计算。随后它会调用 [`deploy.yml`](../../.github/workflows/deploy.yml) 来构建并发布 APK。

| 提交信息 | 效果 | 示例 |
|---|---|---|
| `feat!: …` / `fix!: …`，或正文中有 `BREAKING CHANGE:` | **主版本** | 1.4.2 → 2.0.0 |
| `feat: …` / `feat(sudoku): …` | **次版本** | 1.4.2 → 1.5.0 |
| `fix:`、`perf:`、`revert:`、`refactor:`、`build:` | **修订号** | 1.4.2 → 1.4.3 |
| `docs:`、`chore:`、`ci:`、`test:`、`style:` 或无前缀 | 不发布新版本 | — |

第一个版本是 `1.0.0`。分支：

| 推送到 | 结果 |
|---|---|
| `main`（或 `master`） | 正式版 `v1.5.0`，标记为 *Latest* |
| `stag` | 候选版 `v1.5.0-rc.1`、`-rc.2`……标记为 *Pre-release*（只有出现可发布的提交时才生成新的 rc） |

每次发布时：

1. `versioning.yml` 读取上一个正式版标签之后的提交，决定版本升级幅度，生成分组的版本说明（不兼容变更、新功能、修复、性能、其他），并推送带注释的标签 `vX.Y.Z`。
2. `deploy.yml` 检出这个标签，运行测试和 Lint，用 `-PappVersion=X.Y.Z` 构建正式版 APK，签名并**验证**（签名、`versionName`、没有申请任何权限），然后在 GitHub 发布 Release，附带 `MiliGames-vX.Y.Z.apk`、`SHA256SUMS.txt` 和版本说明。

安卓的 `versionCode` 由版本号推导，保证始终递增：`主版本×1,000,000 + 次版本×10,000 + 修订号×100 + n`，其中 `n` 是 rc 序号，正式版为 99（因此 `1.5.0-rc.2` < `1.5.0`）。次版本和修订号必须小于 100。

其他发布方式：

- **Actions → Semantic versioning → Run workflow**，选择 `patch`、`minor` 或 `major` 强制发布。
- **Actions → Deploy APK → Run workflow**，填入版本号来（重新）构建指定版本。
- 手动推送标签：`git tag v1.2.3 && git push origin v1.2.3`（版本说明由 GitHub 自动生成）。

本地运行 `./gradlew assembleRelease -PappVersion=1.2.3` 即可构建该版本；不加 `-PappVersion` 时版本为 `1.0.0`。

## 如何贡献

欢迎贡献——尤其欢迎和年长亲人一起玩这些游戏的朋友。

1. 开一个 issue 描述问题或想法（附截图更好）。
2. Fork 仓库，新建分支，进行修改。
3. 运行 `./gradlew testDebugUnitTest lintDebug`——CI 也会运行同样的检查。
4. 提交拉取请求，说明这个修改*为什么*对玩家有帮助。

准则：

- **无障碍优先**：触控区域 ≥ 56 dp，主要操作放在屏幕下半部分，并在左手模式下镜像（`ActionBar`），从不只靠颜色（在无障碍配色中使用 `drawSymbol`），不要计时器或时间压力。
- **保持小巧和私密**：不增加权限，不联网，不做统计，不加图片（用代码绘制），没有充分理由不增加依赖。
- **引擎是纯 Kotlin** 并带测试；界面只负责绘制状态和调用引擎。
- 所有可见文字都放进 `strings.xml`，并且**五种语言都要有**（`TranslationsTest` 会强制检查）。
- 提交信息请使用[约定式提交](https://www.conventionalcommits.org/zh-hans/)（`feat: …`、`fix: …`、`docs: …`）：它们决定下一个版本号（见[版本管理与部署](#版本管理与部署)）。
- 代码注释和命名使用英语；遵循现有风格（`.editorconfig`、Kotlin 官方风格）。

## 添加填字词语

词库是纯文本文件 `app/src/main/assets/crossword/<语言>.txt`，每行一条：

```
答案|简短、友好的提示
```

- 使用日常词语，2 到 13 个字母（中文：2 到 4 个汉字；日语：2 到 6 个假名，用片假名书写），不要需要专门知识的专有名词。
- 提示要温和、不含糊——想象一位八十岁的读者。
- 答案可以带重音符号，会被自动去掉（西班牙语保留 Ñ）。
- 以 `#` 开头的行是注释。`CrosswordTest` 会检查每个词库至少有 120 个可用的词。

## 添加或改进翻译

1. 把 `app/src/main/res/values/strings.xml` 复制到 `values-<代码>/strings.xml`，翻译所有 `<string>` 和 `<plurals>`（保留 `%1$d` 这类占位符；撇号写成 `\'` 或使用印刷体 `’`）。
2. 在 `AppLanguage`（`core/Settings.kt`）、`localeFilters`（`app/build.gradle.kts`）和 `res/xml/locales_config.xml` 中加入该语言。
3. 新建词库 `assets/crossword/<代码>.txt`，并在 `dictionaryFile()` 中关联。
4. 翻译 README 以及 `docs/<代码>/` 下的四篇文档，并加到 [docs/README.md](../README.md)。
5. 运行测试。

## 添加新游戏

1. 在 `GameId` 中添加一项（它的 `key` 会写进存档：以后绝不能修改），并添加标题和玩法说明文字。
2. 创建 `games/<名称>/`，包含纯 Kotlin 引擎（`@Serializable` 状态）和测试。
3. 用 `LoadGame(host, State.serializer(), create = { ... })` 和 `GameLayout(status, controls) { 棋盘 }` 编写界面；游戏结束时调用 `host.finish(GameOutcome(...))`。
4. 在 `GameContent.kt` 中注册，并在 `ui/art/Thumbnails.kt` 中绘制缩略图。
5. 添加截图测试，并在五种语言的用户指南中写明规则。
