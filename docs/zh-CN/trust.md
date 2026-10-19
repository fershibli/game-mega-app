# 信任、隐私与安全

🌐 [English](../en/trust.md) · [Português](../pt-BR/trust.md) · [Español](../es/trust.md) · [Français](../fr/trust.md) · [Italiano](../it/trust.md) · [Deutsch](../de/trust.md) · [Русский](../ru/trust.md) · [日本語](../ja/trust.md) · **中文** · [العربية](../ar/trust.md)

Mili Games 的用户可能无法自己判断一个应用是否可靠。本页说明这个应用做什么、不做什么——更重要的是，**任何人都可以自己验证**，不需要相信作者。

## 目录

1. [概要](#概要)
2. [隐私](#隐私)
3. [权限](#权限)
4. [应用里有什么](#应用里有什么)
5. [代码如何被检查](#代码如何被检查)
6. [验证下载的 APK](#验证下载的-apk)
7. [供应链保护](#供应链保护)
8. [坦诚说明 AI 的参与](#坦诚说明-ai-的参与)
9. [报告安全问题](#报告安全问题)

## 概要

| | |
|---|---|
| 联网 | **无**（没有 `INTERNET` 权限） |
| 收集的数据 | **无** |
| 广告、统计、崩溃上报、追踪器 | **无** |
| 账号、登录、购买 | **无** |
| 申请的安卓权限 | **无** |
| 第三方代码 | 只有官方的 AndroidX（Google）和 Kotlin（JetBrains）库 |
| 许可证 | MIT，源代码公开 |

## 隐私

应用只保存两样东西，都在其他应用无法读取的**私有**存储中：

1. `settings.xml`——你在设置中的选择（语言、大小、颜色……）。
2. `current_game.json`——正在进行的游戏，以便应用关闭后可以继续。游戏结束或被放弃时会删除。

如果你开启了安卓系统自带的备份，系统可能会把这两个文件包含在*你自己的*谷歌备份（以及设备间迁移）中，让新手机保留你的设置。这一点在 [`backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) 和 [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml) 中配置。除此之外，不会保存或发送任何东西。

设置 → 关于 中的“打开项目主页”按钮只是请安卓用你的浏览器打开 GitHub 页面；应用本身仍然不建立任何连接。

## 权限

[清单文件](../../app/src/main/AndroidManifest.xml)**没有**声明任何 `<uses-permission>`。可以用 Android SDK 在任意 APK 上确认：

```bash
aapt2 dump permissions MiliGames-v1.0.0.apk
```

你会看到唯一一条以 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 结尾的条目。它不是用户权限，而是 AndroidX 库自动添加的签名级私有权限，用来防止其他应用调用本应用的内部广播接收器。它不会授予任何人任何东西。

没有 `INTERNET` 权限，安卓根本不允许应用建立网络连接。

## 应用里有什么

完整的依赖列表在 [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml)：

| 库 | 发布者 | 用途 |
|---|---|---|
| `androidx.activity:activity-compose` | Google（AndroidX） | 在 Activity 中承载 Compose 界面 |
| `androidx.compose.ui`、`foundation`、`material3` | Google（AndroidX） | 界面工具包 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JetBrains | 把进行中的游戏保存为 JSON |
| Kotlin 标准库 | JetBrains | 语言运行时 |

只在测试中使用（不会打包进应用）：JUnit、Robolectric、Compose UI test。

应用里**没有图片、声音、字体或原生库**：每一个画面都由代码绘制，代码可在 [`ui/art`](../../app/src/main/java/io/github/fershibli/megagames/ui/art) 中阅读。唯一的数据文件是纯文本的填字游戏词库（[`assets/crossword`](../../app/src/main/assets/crossword)）。

正式版还关闭了 Google 的“依赖信息”区块（`dependenciesInfo { includeInApk = false }`），否则它会以加密形式嵌入 APK。因此 APK 的每一个字节都可以检查。

## 代码如何被检查

每次推送和拉取请求都会在 GitHub Actions 上运行 [CI](../../.github/workflows/ci.yml)：

1. **Gradle wrapper 校验**——确认 `gradle-wrapper.jar` 是官方二进制文件。
2. **50 个自动化测试**，包括：
   - 所有游戏的规则（合法走法、计分、双陆棋移出、巴西拉米的顺子和百搭牌……）；
   - 题目生成：每道数独都有唯一解，每局麻将都能消完，每道接水管和颜色连线都有完整解，填字游戏绝不冲突；
   - 电脑对手能快速下完一局，且从不丢失或复制任何一张牌或一颗棋子；
   - 十种语言翻译了所有文字，并保留相同的占位符；
   - 游戏的保存与读取、损坏的存档、设置的持久化；
   - 真实界面：双指缩放的开/关、单手双击缩放和按住旋转缩放、选择游戏、从设置中离开游戏，以及重新打开后的“继续吗？”提示。
3. **Android Lint**，*警告视为错误*。
4. **构建** 调试版和正式版 APK，并附带 **SHA-256 校验和** 发布为构建产物。

你也可以自己运行同样的检查：见 [building.md](building.md)。

## 验证下载的 APK

1. 从同一个[版本](https://github.com/fershibli/game-mega-app/releases)下载 `MiliGames-vX.Y.Z.apk` 和 `SHA256SUMS.txt`。
2. 计算校验和并对比：

   ```bash
   sha256sum MiliGames-vX.Y.Z.apk      # Linux / macOS：shasum -a 256
   certutil -hashfile MiliGames-vX.Y.Z.apk SHA256   # Windows
   ```

3. 确认各版本的签名证书相同（安卓也会拒绝用不同密钥签名的更新）：

   ```bash
   apksigner verify --print-certs MiliGames-vX.Y.Z.apk
   ```

4. 如需完全确认，可以从对应标签的源代码自行构建并比较内容（`unzip -l`、`aapt2 dump badging`）。

## 供应链保护

- `distributionSha256Sum` 在 [`gradle-wrapper.properties`](../../gradle/wrapper/gradle-wrapper.properties) 中固定了确切的 Gradle 发行版。
- 依赖只来自 Google 的 Maven 仓库和 Maven Central（`repositoriesMode = FAIL_ON_PROJECT_REPOS`）。
- [Dependabot](../../.github/dependabot.yml) 每月提出依赖和 GitHub Actions 的更新。
- CI 只有只读权限；只有版本管理和部署工作流可以写入，而且只用于推送版本标签和发布 Release。签名密钥工作流只写入签名相关的 Secrets，使用的令牌仅限于本仓库的 Secrets。
- 每个发布的 APK 在上传前都会在流水线中验证：签名（`apksigner`）、版本号，以及没有申请任何权限（`aapt2`）。
- 签名密钥保存在 GitHub 加密的 Secrets 中，从不提交到仓库（`*.jks` 和 `keystore.properties` 已在 `.gitignore` 中）。

## 坦诚说明 AI 的参与

代码、美术和文档都是在 Anthropic 公司的 AI 助手 **Claude** 的帮助下，在维护者的指导下完成的。AI 写的代码和其他代码一样可能有错误，因此本项目依靠的是你可以亲自验证的东西，而不是承诺：可读的源代码、全面的测试、Lint、可复现的构建和公开的校验和。如果你发现问题，请告诉我们。

## 报告安全问题

见 [SECURITY.md](../../SECURITY.md)。请不要为安全漏洞开公开 issue，请使用 GitHub 的私密漏洞报告功能。
