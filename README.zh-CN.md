# AirPlay Receiver for Windows

[English](README.md)

AirPlay Receiver for Windows 是面向 Windows 10/11 x64 的现代 AirPlay 接收器，
可以接收 iPhone、iPad 和 Mac 的屏幕镜像，并同步播放投屏时传输的系统音频。
项目将桌面控制中心、独立 GStreamer 播放窗口和 Windows 托盘体验整合在一个轻量应用中。

> 本项目由社区独立维护，与 Apple Inc. 没有隶属、合作或官方授权关系。

## 主要功能

- 在可信局域网中接收 iPhone、iPad 和 Mac 的屏幕镜像。
- 播放随镜像传输的系统音频。
- 应用关闭主窗口后继续驻留 Windows 通知区域。
- 独立投屏窗口支持最大化、锁定宽高比缩放、横竖屏布局、音量、静音和窗口置顶。
- 支持简体中文/英文、浅色/深色主题、接收器名称、分辨率能力、60 Hz/120 Hz 帧率和托盘行为设置。
- Windows 上优先使用 D3D11 硬件 H.264 解码；驱动不支持时自动回退到软件解码，安装包构建会校验 D3D11 插件存在。
- 视频网络包使用引用计数的 Netty `ByteBuf`，在原缓冲区内完成解密和 NALU 处理，再只复制一次到 GStreamer 原生缓冲区。
- 安装包内置精简 Java 21 和 GStreamer 运行时，无需单独安装 Java 或 GStreamer。

## 安装

从 [Releases](https://github.com/pavpea/airplay-receiver-windows/releases) 下载：

`AirPlay-Receiver-<version>-windows-x64-setup.exe`

安装器按当前用户安装，不要求管理员权限，提供安装目录、桌面快捷方式、开始菜单快捷方式和安装完成后启动选项。

## 开始投屏

1. 安装并启动 **AirPlay Receiver**。接收服务默认随应用启动，并可在托盘中管理。
2. 确保 Windows 电脑与 Apple 设备连接到同一个局域网。
3. 在 Apple 设备打开控制中心，点击“屏幕镜像”，选择主页面显示的接收器名称。
4. 投屏窗口会在当前显示器一侧打开；窗口缩放和设备横竖屏切换时都会保持源画面比例。

首次启动时 Windows Defender 防火墙可能会询问网络权限，请仅在可信网络配置文件中允许访问。

## 播放控制

投屏窗口标题栏提供静音、音量、窗口置顶、最小化、最大化和关闭按钮。
关闭投屏窗口会结束当前会话；关闭主窗口是否进入托盘由设置决定。同一时间只允许一个活动投屏会话。

## 设置与数据

设置位置：

`%APPDATA%\AirPlay Receiver for Windows\settings.json`

日志位置：

`%LOCALAPPDATA%\AirPlay Receiver for Windows\logs`

支持系统/浅色/深色主题、中文/英文语言、接收器名称、显示能力、最高帧率、
随 Windows 启动、连接时前置和关闭到托盘。卸载默认保留个人数据，也可以在卸载流程中删除本项目的设置和日志。

## 功能范围

当前桌面产品专注于屏幕镜像及配套音频，不包含：

- 从 Windows 向 Apple 设备发送内容；
- HLS 或媒体 URL 直投；
- 录屏、截图和自动更新；
- AirPlay PIN 配对或密码管理。

仓库中的协议和播放器实验模块仍用于开发与测试，除非发行说明特别注明，否则不会打入桌面安装包。

## 常见问题

- **找不到接收器：** 检查两台设备是否在同一局域网，确认 Windows 防火墙允许应用访问，并关闭访客网络隔离。
- **服务未就绪：** 从应用打开日志目录，检查端口冲突和网络接口状态。
- **音画无法启动：** 确认安装目录中的私有 GStreamer 运行时完整，并运行打包应用的 `--self-test`。
- **画面发软或 CPU 占用较高：** 避免请求明显高于显示器能力的源分辨率；日志会记录是否选择 D3D11 硬件解码以及 `renderedFps` 实际渲染帧率。`maxFPS` 只是向 Apple 设备请求的上限，不能强制发送端输出 120fps。
- **升级或卸载被旧进程阻塞：** 安装器会请求确认，先正常关闭应用并最多等待 15 秒，随后使用隐藏的兜底终止逻辑。

## 从源码构建

要求：

- JDK 21；
- Windows 10/11 x64（生成桌面安装包时）；
- Windows 打包需要 GStreamer 1.28.5 MSVC x86_64 运行时；
- 本地使用 `jpackage --type exe` 生成安装包还需要 WiX Toolset 3.14+，并将
  `candle.exe` 和 `light.exe` 加入 `PATH`。GitHub Windows Runner 已预装 WiX。

运行单元测试：

```bash
./gradlew test
```

Windows 打包前设置 `GSTREAMER_RUNTIME_DIR`，然后执行：

```powershell
.\gradlew.bat test :player:app:stageGStreamerRuntime --console=plain
.\gradlew.bat :player:app:packageWindows --console=plain
```

### Windows 本地打包的一次性准备

WiX Toolset 是 `jpackage` 用来把应用镜像编译为 MSI/EXE 安装包的
Windows Installer 编译和链接工具。它不随 JDK 提供，只有 GitHub Actions
的 Windows Runner 预装了它。本机不需要管理员权限即可使用便携版：从
[WiX 3.14 官方二进制包](https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip)
下载并解压到用户可写目录，然后在 PowerShell 中设置：

```powershell
$wix = Join-Path $env:LOCALAPPDATA 'Programs\WiX Toolset v3.14'
$env:WIX = $wix
$env:PATH = "$wix;$env:PATH"
$env:GSTREAMER_RUNTIME_DIR = 'C:\path\to\gstreamer-runtime'
```

如果希望以后打开终端也自动生效，可以使用
`[Environment]::SetEnvironmentVariable(<名称>, <值>, 'User')` 保存这些变量。
完整 GStreamer SDK 之所以有几个 GB，是因为它还包含头文件、开发库、调试
文件和全部插件；它只是构建时的来源。`stageGStreamerRuntime` 会复制本应用
实际需要的精简私有运行时，完成复制后可以卸载完整 SDK。不要在 staging 或
打包完成前删除 `GSTREAMER_RUNTIME_DIR` 指向的目录。

安装包输出位置：

`player/app/build/package/installer/AirPlay-Receiver-<version>-windows-x64-setup.exe`

代码签名环境变量和 CI 流程见 `.github/workflows/build.yaml`。

## 模块

- `lib`：AirPlay 协议结构、加密和公共工具。
- `server`：RTSP/Netty 接收服务和会话生命周期。
- `player:gstreamer`：桌面 GStreamer 播放器和私有运行时初始化。
- `player:app`：Windows 桌面应用、托盘、设置和安装器。
- `client`、`player:ffmpeg`、`player:vlc`、`player:h264-dump`：实验或开发模块，不打入桌面安装包。

## 致谢与许可证

本项目参考并包含源自
[serezhka/java-airplay](https://github.com/serezhka/java-airplay) 的工作。
上游归属和范围见 [ACKNOWLEDGEMENTS.md](ACKNOWLEDGEMENTS.md)，运行时与依赖声明见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

项目继续采用 [MIT License](LICENSE)。qiuspace 是本 Windows 项目的维护团队，
当前团队成员为 qiuxtao 和 qiuxiaoxuan。
