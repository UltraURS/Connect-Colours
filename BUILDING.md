# 构建说明

本仓库包含模块源码、资源、测试和构建脚本。已构建的测试版 APK 位于 [Releases](https://github.com/UltraURS/Connect-Colours/releases)。

## 工具与依赖

- JDK 21、Gradle 8.9。
- Android SDK Platform 35、Build Tools 35.0.0；应用运行目标为 Android 13。
- Android Gradle Plugin 8.7.3、Xposed API 82、JUnit 4.13.2、Robolectric 4.16.1，声明见 Gradle 文件。
- Android 13 Robolectric 运行库：`org.robolectric:android-all-instrumented:13-robolectric-9030017-i7`。

使用你自己的 SDK、JDK 和 Gradle 安装路径。仓库不包含 Gradle Wrapper、SDK、Gradle 二进制或依赖缓存。

## 首次准备与构建（PowerShell）

```powershell
$env:JAVA_HOME = 'C:/path/to/jdk-21'
$env:ANDROID_HOME = 'C:/path/to/android-sdk'
$env:GRADLE_USER_HOME = Join-Path $PWD '.tooling/gradle-cache'
$env:ANDROID_USER_HOME = Join-Path $PWD '.tooling/android-user'
New-Item -ItemType Directory -Force '.tooling/robolectric' | Out-Null
Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/13-robolectric-9030017-i7/android-all-instrumented-13-robolectric-9030017-i7.jar' -OutFile '.tooling/robolectric/android-all-instrumented-13-robolectric-9030017-i7.jar'
& 'C:/path/to/gradle-8.9/bin/gradle.bat' :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --max-workers=2
```

首次构建需要联网，从 `settings.gradle` 声明的仓库下载依赖。Xposed API 使用其官方 Maven 仓库；如果该仓库无法访问，需要自行准备同版本依赖。

依赖已缓存后，可使用现有离线检查脚本：

```powershell
./build.ps1 -SdkRoot 'C:/path/to/android-sdk' -JdkHome 'C:/path/to/jdk-21' -GradleHome 'C:/path/to/gradle-8.9'
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。这是使用本机调试密钥签名的构建；新环境生成的密钥可能不同于已发布 APK，签名不同的包无法直接覆盖安装。不要公开上传自己的签名私钥。

## 适配核验与发布记录

`tools/verify-apk-hooks.py` 用于核验指定系统 APK/JAR 中的 Hook 目标，输入由设备所有者自行提供；参见 `tools/adaptation.md`、`tools/screenshot-adaptation.md` 和 `tools/sidebar-adaptation.md`。厂商系统文件、原始日志及私人参考截图不包含在公开仓库中，也不参与模块编译。

`tools/package.py` 是维护者本地发布脚本，依赖适配核验记录、测试报告及本地 Build Tools；其默认路径沿用开发工作区布局，其他环境需要调整。它不是普通源码编译的必需步骤。

v0.2.4 构建号 17 的测试、签名、对齐和 SHA-256 记录随 Release 提供。自动化测试及静态适配核验不能替代设备上的 LSPosed 验证。
