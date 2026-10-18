# 构建与开发

[返回项目首页](../README.md)

Samo TV 使用 Java 与 Android 系统组件实现，不依赖第三方运行时库，也不使用 Gradle。构建脚本直接调用 Android SDK 工具，完成资源编译、Java 编译、D8 转换、APK 打包、对齐、签名与校验。

## 环境要求

| 工具 | 版本或要求 |
| --- | --- |
| JDK | 17 |
| Python | 3.9 或更新版本 |
| Shell | Bash |
| 校验工具 | `shasum` |
| Android SDK Platform | `platforms;android-35` |
| Android SDK Build Tools | `build-tools;35.0.1` |
| Android SDK Platform-Tools | 使用 ADB 安装与调试时需要 |

应用最低系统版本为 API 23，目标 SDK 为 API 28，编译 SDK 为 API 35。JDK、Android SDK、签名密钥和构建产物不纳入版本控制。

## 获取源码

```sh
git clone https://github.com/Samoyer/samo-tv.git
cd samo-tv
```

需要构建与发布包对应的源码时，可在克隆后检出对应标签，例如 `git checkout v1.3.3`。

## 配置 JDK 与 Android SDK

安装 JDK 17，并将 `JAVA_HOME` 指向其实际目录。构建与启动策略测试均通过该变量选择 JDK。

从 [Android 官方网站](https://developer.android.com/studio#command-tools) 获取 SDK Command-line Tools，解压到所选 SDK 目录。下面采用仓库内的 `.tooling/android-sdk`：

```text
.tooling/android-sdk/
└── cmdline-tools/latest/
    ├── bin/sdkmanager
    └── lib/
```

设置环境变量并安装组件：

```sh
export JAVA_HOME="/absolute/path/to/jdk-17"
export ANDROID_SDK_ROOT="$PWD/.tooling/android-sdk"

"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-35" \
  "build-tools;35.0.1" \
  "platform-tools"
```

按 `sdkmanager` 提示接受 SDK 许可。若使用已有 SDK，将 `ANDROID_SDK_ROOT` 改为其实际目录即可；无需复制或建立符号链接。

未设置环境变量时，脚本默认使用 `.tooling/jdk/Contents/Home` 与 `.tooling/android-sdk`。

## 构建 APK

在仓库根目录运行：

```sh
bash tools/build-apk.sh
```

构建脚本会检查必需工具与 Android Platform，校验 APK 签名和对齐结果，并生成：

```text
dist/samo-tv-debug.apk
dist/samo-tv-debug.apk.sha256
```

构建中间文件位于 `.tooling/build/`，每次构建都会重新生成。

### 签名与覆盖安装

首次构建会创建 `.tooling/debug.keystore`，后续构建复用该密钥。这是用于本地开发的调试签名；请妥善保存密钥，勿提交到仓库。

Android 覆盖安装要求包名与签名一致。若需要持续更新已安装的自建版本，应使用同一份密钥；自建 APK 与 GitHub Release APK 的签名可能不同。

## 离线测试

在已设置 `JAVA_HOME` 的终端中运行：

```sh
bash tools/test-startup-policy.sh
bash -n tools/build-apk.sh
bash -n tools/test-startup-policy.sh
```

启动策略测试覆盖开机窗口、授权、暂停、重复触发，以及启动请求与实际进入应用的区分。测试脚本将 Java 类编译到临时目录，并在退出时清理。

上述检查不连接设备。遥控器导航、视觉效果、主页返回与开机行为需在目标设备上验证。

可选主页适配的开机补偿仅在开机后 120 秒内、启动编号可读、服务已授权且应用开关开启时尝试启动；每种触发来源每次开机最多尝试一次，实际进入 Samo 后停止补偿。设备系统仍可能限制后台启动或撤销授权。

## 安装自建版本

将 Android SDK 的 `platform-tools` 加入 `PATH`，在设备开启 ADB 调试并授权电脑后运行：

```sh
export PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"
adb devices
adb install -r dist/samo-tv-debug.apk
adb shell am start -n cn.samo.tv/.MainActivity
```

同时连接多台设备时，在 `adb` 后添加 `-s <设备序列号>`。默认桌面与可选主页适配的设置见 [安装与设置](../README.md#安装与设置)。

## 目录结构

```text
app/src/main/
├── AndroidManifest.xml
├── java/cn/samo/tv/       # 启动器、主页适配与原生界面组件
└── res/                  # 图标、壁纸、主题与服务声明
docs/
├── BUILD.md
└── images/               # README 界面示例
tools/
├── build-apk.sh          # APK 构建与签名
├── test-startup-policy.sh
└── tests/cn/samo/tv/      # 启动策略测试
```
