# AirPlay Receiver for Windows

[简体中文](README.zh-CN.md)

AirPlay Receiver for Windows is a modern Windows 10/11 x64 desktop receiver
for mirroring an iPhone, iPad, or Mac display with synchronized system audio.
It combines a native Swing control center, a dedicated GStreamer playback
window, and a Windows tray workflow in one lightweight application.

> This is an independent, community-maintained project. It is not affiliated
> with or endorsed by Apple Inc.

## Highlights

- Mirror iPhone, iPad, and Mac screens over a trusted local network.
- Play the audio that accompanies a mirroring session through the PC speakers.
- Keep the receiver available from the Windows notification area.
- Use a dedicated playback window with native maximize, aspect-ratio-preserving
  resize, rotation-aware layout, volume, mute, and always-on-top controls.
- Choose Chinese or English, light or dark theme, receiver name, display
  capability, 60 Hz or 120 Hz frame rate, startup behavior, and tray behavior.
- Prefer Windows D3D11 H.264 hardware decoding when the installed driver and
  private GStreamer runtime support it, with an automatic software fallback.
  The Windows package validates that the D3D11 plug-in is present before it is
  published.
- Keep the high-rate video path bounded and reference-counted: network payloads
  stay in Netty ByteBufs through decryption/NALU preparation and are copied only
  once into a native GStreamer buffer.
- Bundle a trimmed Java 21 runtime and GStreamer runtime in the Windows
  installer; no separate Java or GStreamer installation is required.

## Install

Download the latest `AirPlay-Receiver-<version>-windows-x64-setup.exe` from
[Releases](https://github.com/pavpea/airplay-receiver-windows/releases).
The installer is per-user and does not require administrator privileges. It
offers a directory chooser, desktop and Start menu shortcut options, and an
option to launch the application after installation.

## Start mirroring

1. Install and start **AirPlay Receiver**. The receiver service starts with
   the application by default and remains available in the tray.
2. Connect the Windows PC and Apple device to the same local network.
3. Open Control Center on the Apple device, tap **Screen Mirroring**, and
   choose the receiver name shown on the AirPlay Receiver home page.
4. The playback window opens at the side of the active display. It keeps the
   source aspect ratio while the window is resized or the device rotates.

The product is designed for a trusted LAN. Windows Defender Firewall may ask
for permission the first time the receiver is started; allow it on the network
profile you trust.

## Playback controls

The playback title bar provides mute, volume, always-on-top, minimize,
maximize, and close controls. Closing the playback window ends the active
session. Closing the main window follows the configured **close to tray**
behavior. A second device is rejected while another mirroring session is
active.

## Settings and data

Settings are saved atomically in:

`%APPDATA%\AirPlay Receiver for Windows\settings.json`

Logs are written to:

`%LOCALAPPDATA%\AirPlay Receiver for Windows\logs`

The application supports Chinese and English, system/light/dark themes,
receiver name, display capability, frame rate, launch at Windows sign-in,
connection foregrounding, and tray behavior. Uninstalling keeps user data by
default; the uninstall flow can remove all project-specific settings and logs.

## Scope

The Windows desktop product currently focuses on screen mirroring and its
associated audio. It does not include:

- sending content from Windows to an Apple device;
- HLS or direct media URL playback;
- recording, screenshots, or automatic updates;
- AirPlay PIN pairing or password management.

Experimental protocol and player modules remain in the repository for
development and testing, but are not included in the desktop installer unless
explicitly stated by a release.

## Troubleshooting

- **The receiver is not visible:** verify that both devices use the same LAN,
  Windows Firewall permits the application, and no guest-network isolation is
  enabled.
- **The service is not ready:** open the log directory from the application
  and check for a port conflict or a blocked network interface.
- **Video or audio does not start:** confirm that the private GStreamer runtime
  is present in the installed application directory and run the packaged
  `--self-test` command.
- **Video is soft or the PC is under load:** keep the playback window near the
  source aspect ratio and avoid requesting a stream far above the display
  capability. On supported Windows systems the log records whether D3D11
  decoding was selected; otherwise the receiver uses the bounded software
  decoder path. `renderedFps` in the periodic performance entry is the actual
  decoded/rendered rate; `maxFPS` is only an upper bound requested from the
  Apple sender and cannot force a 120 fps source.
- **A previous process blocks an upgrade/uninstall:** the installer asks for
  confirmation, sends a normal close request, waits up to 15 seconds, and then
  uses a hidden fallback termination if needed.

## Build from source

Requirements:

- JDK 21;
- Windows 10/11 x64 for the packaged application;
- GStreamer 1.28.5 MSVC x86_64 runtime for Windows packaging;
- WiX Toolset 3.14+ (`candle.exe` and `light.exe`) on `PATH` for local
  `jpackage --type exe` builds. GitHub-hosted Windows runners provide WiX.

Run the unit tests on any supported build host:

```bash
./gradlew test
```

On Windows, set `GSTREAMER_RUNTIME_DIR` to the extracted GStreamer runtime and
build the installer (the GStreamer runtime is pruned to the codecs and sinks
used by the desktop receiver):

```powershell
.\gradlew.bat test :player:app:stageGStreamerRuntime --console=plain
.\gradlew.bat :player:app:packageWindows --console=plain
```

### One-time Windows packaging setup

WiX Toolset is the Windows Installer compiler/linker used by `jpackage` to
turn the application image into the MSI/EXE installer. It is not bundled with
the JDK, and GitHub Actions provides it only on the hosted Windows runner. A
non-admin portable installation is sufficient for local builds: download the
[WiX 3.14 binaries](https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip),
extract them to a user-writable directory, and expose that directory in the
current PowerShell session:

```powershell
$wix = Join-Path $env:LOCALAPPDATA 'Programs\WiX Toolset v3.14'
$env:WIX = $wix
$env:PATH = "$wix;$env:PATH"
$env:GSTREAMER_RUNTIME_DIR = 'C:\path\to\gstreamer-runtime'
```

To keep these values for future terminals, set them once with
`[Environment]::SetEnvironmentVariable(<name>, <value>, 'User')`. The full
GStreamer SDK can occupy several gigabytes because it includes headers,
development libraries, debug files, and every plugin. It is only a staging
source; `stageGStreamerRuntime` copies the small private runtime needed by this
application. After that copy has been preserved, the full SDK may be
uninstalled. Do not remove the directory selected by
`GSTREAMER_RUNTIME_DIR` until staging or packaging is complete.

The installer is generated at:

`player/app/build/package/installer/AirPlay-Receiver-<version>-windows-x64-setup.exe`

Optional code-signing variables are documented in
`.github/workflows/build.yaml`. The CI workflow runs shared JVM tests, Windows
runtime validation, packaging checks, installer lifecycle tests, and the
installed `--self-test` smoke test.

## Modules

- `lib` — AirPlay protocol structures, cryptography, and shared utilities.
- `server` — RTSP/Netty receiver services and session lifecycle.
- `player:gstreamer` — the desktop GStreamer player and private runtime setup.
- `player:app` — the Windows desktop application, tray, settings, and
  installer.
- `client`, `player:ffmpeg`, `player:vlc`, and `player:h264-dump` — experimental
  or development modules not shipped in the desktop installer.

## Acknowledgements and license

This project is inspired by and includes work derived from
[serezhka/java-airplay](https://github.com/serezhka/java-airplay). See
[ACKNOWLEDGEMENTS.md](ACKNOWLEDGEMENTS.md) for the upstream attribution and
scope, and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for runtime and
dependency notices.

The project is released under the [MIT License](LICENSE). qiuspace is the
maintainer team for this Windows product; qiuxtao and qiuxiaoxuan are the
current team members.
