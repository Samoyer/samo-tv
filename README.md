# Samo TV

面向电视遥控器操作的轻量原生 Android 启动器，提供离线海岸壁纸、常用应用和统一设置入口。

项目使用 Java 与 Android 系统组件实现，不依赖第三方运行时库。应用不申请网络权限，不包含广告或统计 SDK，壁纸与应用偏好均保存在本地。

## 功能

- **首页与常用应用**：随焦点显示应用名称和介绍，支持添加、移除常用应用，保存列表及顺序。
- **应用列表**：五列网格，兼容电视与普通 Android 应用入口，支持方向键导航。
- **统一设置**：集中提供电视设置、网络、应用管理和默认启动器入口。
- **离线界面**：内置海岸壁纸，保留已安装应用的原始图标；焦点过渡遵循系统动画设置。
- **可选主页适配**：在匹配的原桌面窗口出现时返回 Samo，需用户手动授权，可在应用内暂停。

## 版本与兼容性

| 项目 | 当前配置 |
| --- | --- |
| 应用版本 | `1.3.3`（versionCode `7`） |
| 包名 | `cn.samo.tv` |
| 最低系统版本 | Android 6.0（API 23） |
| 目标 SDK | API 28 |
| 编译 SDK | API 35 |
| Android Build Tools | `35.0.1` |

最低系统版本表示安装门槛。默认桌面选择、无障碍服务和开机行为取决于设备系统，需在目标设备上检查兼容性。

主页适配仅支持代码中匹配的厂商桌面窗口，不是通用的主页键拦截方案，也不会自行更改系统默认桌面。

## 构建

### 环境要求

- JDK 17。
- Python 3.9 或更新版本。
- Bash 与 `shasum`。
- Android SDK：`platforms;android-35`、`build-tools;35.0.1`；安装到设备还需 `platform-tools`。

仓库不包含 JDK、Android SDK 或预编译 APK，也不使用 Gradle。构建脚本使用 Android SDK 工具直接编译、打包和签名。

### 准备源码与 SDK

```sh
git clone https://github.com/Samoyer/samo-tv.git
cd samo-tv
```

从 [Android 官方网站](https://developer.android.com/studio#command-tools) 获取 SDK Command-line Tools，并安装到所选 SDK 目录。

下面以仓库内的 `.tooling/android-sdk` 为例；如已有 SDK，可将 `ANDROID_SDK_ROOT` 指向其实际目录：

```sh
export JAVA_HOME="/absolute/path/to/jdk-17"
export ANDROID_SDK_ROOT="$PWD/.tooling/android-sdk"

"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-35" \
  "build-tools;35.0.1" \
  "platform-tools"
```

将 `JAVA_HOME` 替换为本机 JDK 的实际目录，并按 `sdkmanager` 提示接受 SDK 许可。构建和测试均使用该 JDK。

### 生成 APK

```sh
bash tools/build-apk.sh
```

脚本完成资源编译、Java 编译、D8 转换、APK 打包、对齐、签名和签名校验，输出：

```text
dist/samo-tv-debug.apk
dist/samo-tv-debug.apk.sha256
```

首次构建会创建 `.tooling/debug.keystore`。后续覆盖安装需沿用同一签名，请妥善保存该文件。此密钥用于本地调试构建；密钥、构建产物与工具缓存均不纳入版本控制。

## 安装与设置

### 安装应用

在电视上开启 ADB 调试，并允许当前电脑连接。将 Android SDK 的 `platform-tools` 加入 `PATH`：

```sh
export PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"
adb devices
adb install -r dist/samo-tv-debug.apk
adb shell am start -n cn.samo.tv/.MainActivity
```

确认设备状态为 `device` 后安装。若同时连接多台设备，请为命令添加 `-s <设备序列号>`。

### 设置默认桌面

进入 Samo 的「设置 → 默认启动器」，按系统提示选择 Samo TV。部分系统不提供该入口或不允许选择第三方桌面。

### 启用可选主页适配

对于受支持的厂商桌面，可进入「设置 → 主页键接管 → 系统无障碍设置」，手动启用 Samo TV 服务，并确认应用内接管开关已开启。

授权后，请实际检查主页键返回、应用启动和系统设置入口。系统允许开启服务，并不代表当前桌面符合适配条件。

接管逻辑包含开机补偿：仅在开机后 120 秒内、启动编号可读、服务已授权且应用开关开启时尝试启动；每种触发来源每次开机最多尝试一次，实际进入 Samo 后停止补偿。系统仍可能限制后台启动或撤销授权。

### 恢复原桌面

在「设置 → 主页键接管」中暂停接管，或在系统无障碍设置中关闭 Samo TV 服务。如果已将 Samo 设为默认桌面，再到系统默认启动器设置中选择原桌面。

## 遥控器操作

| 操作 | 行为 |
| --- | --- |
| 方向键 | 在首页、应用网格和设置间移动焦点 |
| 确认键 | 打开选中应用或设置项 |
| 长按确认键 / 菜单键 | 打开应用菜单，添加或移出常用、查看应用信息 |
| 返回键 | 关闭弹层或返回首页 |
| 主页键 | 由系统默认桌面或已授权的主页适配决定返回位置 |

首页「全部应用」可进入应用网格，网格首行向上可返回顶部导航。常用应用允许保存为空，升级会保留已保存的列表和顺序。

## 开发与测试

在已设置 `JAVA_HOME` 的终端中运行：

```sh
bash tools/test-startup-policy.sh
bash -n tools/build-apk.sh
bash -n tools/test-startup-policy.sh
```

启动策略测试覆盖开机窗口、授权、暂停、重复触发，以及启动请求与实际进入应用的区分。上述检查不连接设备；遥控器导航、视觉效果、主页返回与开机行为需在目标设备上验证。

```text
app/src/main/
├── AndroidManifest.xml
├── java/cn/samo/tv/       # 启动器、主页适配与原生界面组件
└── res/                  # 图标、壁纸、主题与服务声明
tools/
├── build-apk.sh          # APK 构建与签名
├── test-startup-policy.sh
└── tests/cn/samo/tv/      # 启动策略测试
```

## 隐私与权限

- 普通应用权限仅有 `RECEIVE_BOOT_COMPLETED`，用于接收开机通知。
- 可选主页适配服务由系统通过 `BIND_ACCESSIBILITY_SERVICE` 绑定，仅监听指定厂商桌面的窗口状态事件。
- 服务不读取窗口内容、不过滤按键、不执行手势。
- 不申请网络或悬浮窗权限，不上传应用列表与使用记录。

## 设计与许可

界面布局参考 [SunnyTV](https://github.com/xudong7587/SunnyTV) 与 Apple TV / tvOS 的视觉方向，采用独立原生实现，未引入参考项目的代码或演示素材。内置海岸壁纸由 AI 生成，随应用离线提供。

本项目采用 [MIT 许可证](LICENSE)。
