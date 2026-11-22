# LastLight Reborn

**English** | [Русский](README.ru.md)

> Standalone native Android port & launcher for **Metro: Last Light Redux** (Nintendo Switch `v1.0.0`, ARM64 / Vulkan 1.3).

[![Platform](https://img.shields.io/badge/Android-13%2B%20(arm64--v8a)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Graphics](https://img.shields.io/badge/Graphics-NVN%20%E2%86%92%20Vulkan%201.3-AC162C?style=flat-square&logo=vulkan&logoColor=white)](https://www.vulkan.org/)
[![UI](https://img.shields.io/badge/UI-Kotlin%20%2B%20Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Release](https://img.shields.io/badge/Release-v0.1.0--alpha-F6B747?style=flat-square)](https://github.com/Meller2/LastLight-reborn/releases/tag/v0.1.0-alpha)

---

## Overview

**LastLight Reborn** (`dev.lastlight.reborn`) runs **Metro: Last Light Redux** (Switch `v0`, Title ID `0100F0400E850000`, 4A Engine, `nnSdk 8.2.0`) natively on ARM64 Android devices via Horizon HLE and NVN-to-Vulkan translation (`peanuts-native` / `libnxhost.so`), wrapped in a dedicated JNI bridge (`liblastlight_bridge.so`) and a shooter-oriented Jetpack Compose launcher.

## Features

- **Native ARM64 + Vulkan 1.3** — No CPU JIT overhead; all 854 game shaders (`romfs/shaders.nx`) pre-translated into `dmsh_vk.lib` with zero `spirv-val` errors.
- **45 / 60 FPS Unlock** — Unlocks the hardcoded 30 FPS present interval (`DM_PRESENT_INTERVAL=display`) with proper 4A Engine delta-time pacing.
- **True 20:9 / Ultrawide Aspect Ratio** — 5-site aspect patch (`DM_ASPECT_PLAN`) covering both display initialization and dynamic-resolution updates so horizontal FOV expands cleanly without stretching.
- **Metro-Styled Launcher** — Glassmorphic landscape UI, real hardware & live battery temperature cards, quick presets (`Cool 30` / `Balanced 45` / `Max 60`), `SGSR1` / `FSR1` upscaling, AdrenoTools **Turnip** `.zip` driver import, and `.zip` save backup/restore.
- **FPS Touch HUD & Gyro** — Floating left stick with auto-sprint (`L3`), 120 Hz angular-accumulator Touch-Look (preserves fast flick turns), ADS-gated hardware gyroscope (`Off` / `ZL` / `Always`), Switch button labels, and automatic physical gamepad switching.
- **Isolated `:game` Process** — Runs `nxhost_main` in its own process with process-safe JSON config (`lastlight_config.json`) and live in-game sensitivity/gyro tuning.

## Requirements

| Component | Minimum | Recommended (Tested) |
|---|---|---|
| **OS** | Android 13 (API 33), `arm64-v8a` | Android 14–16 (API 34–36) |
| **SoC / GPU** | Snapdragon 8 Gen 2 / Adreno 740 (Vulkan 1.3) | Snapdragon 8 Gen 3 (`SM8650`, Adreno 750) |
| **Driver** | System Vulkan 1.3 | Mesa Turnip (`v26.0.0_R8` AdrenoTools `.zip`) |
| **RAM / Storage** | 8 GB RAM / ~9 GB free | 12+ GB RAM / UFS 3.1+ |

## Quick Start (Android)

1. Install the latest APK from [GitHub Releases](https://github.com/Meller2/LastLight-reborn/releases).
2. Place your legally obtained **Metro: Last Light Redux** (`0100F0400E850000`, `v0`) `.nup` and `android-pack` (`main.patched.bin`, `sdk.patched.bin`, `pack.tsv`, `shaders/.../dmsh_vk.lib`) in a folder on your device.
3. Open **LastLight Reborn**, grant storage access, and pick the game folder (all three badges `NUP`, `Pack`, `Shaders` should turn green).
4. *(Recommended on Adreno)* Import a Turnip `.zip` driver under **Files → Turnip driver**.
5. Pick a preset (`30` / `45` / `60`) and press **Launch**.

## Building from Source

Requires **JDK 21**, **Android SDK** (`compileSdk 36`, `cmake 3.22.1`, **`ndk 29.0.14206865`**), and **Python 3** (`python3` in `PATH`).

```powershell
git clone --recursive https://github.com/Meller2/LastLight-reborn.git
cd LastLight-reborn

# Apply local peanuts-native fixes & Metro game profile
git -C peanuts-native apply ..\patches\peanuts-native.patch
New-Item -ItemType Directory -Force -Path "peanuts-native\dm\games\0100F0400E850000" | Out-Null
Copy-Item -Path "patches\0100F0400E850000\*" -Destination "peanuts-native\dm\games\0100F0400E850000\" -Force

# Build & install release APK
.\gradlew.bat :app:assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

## Key Engine & Core Fixes

| Area | Fix |
|---|---|
| **Masked Color Clear** | Implemented `color_masked` SPIR-V pass in `vka_exec.c` for mask `0x3` clears (fixes center-screen smeared droplet artifacts). |
| **Audio Stutter** | Added `HLE_FORCE` hook `GetAudioOutBufferBufferSize` in `hle_audio.c` returning `b->cap`. |
| **True 20:9 Ultrawide** | Patched `0x5a504`, `0x5a5c8`, `0x5a5cc`, `0x5a5f0`, `0x5a6c4` in `main` (`DM_ASPECT_PLAN`) so dynamic-res ticks no longer reset aspect back to `16:9`. |
| **Dirty Page Churn** | Enabled 64 KiB dirty blocks (`DM_DIRTY_BLOCK_AT=0`, `DM_DIRTY_BLOCK_KB=64`) to cut ~2.3M write-protection faults and reduce heat. |
| **Touch-Look & Gyro** | 120 Hz carry-over angular queue + 2D radial deadzone/curve linearization; host `DM_MOTION=0` so ADS-only gyro is strictly controlled by the HUD. |

## Project Layout

- `app/src/main/cpp/` — `CMakeLists.txt` (`libnxhost.so` + `liblastlight_bridge.so`) and `lastlight_jni.c`.
- `app/src/main/java/dev/lastlight/reborn/` — `MainActivity`, `GameActivity` (`:game`), `data/` (`AppPrefs`, `GamePaths`, `DeviceInfo`, `SaveBackupManager`), `ui/` (`LauncherScreen`, `ShooterHudOverlay`, `PerfOverlay`).
- `patches/` — Upstream `peanuts-native` diff (`peanuts-native.patch`) and `0100F0400E850000/` profile.
- `peanuts-native/` — Horizon HLE + NVN → Vulkan core (git submodule).

## Logs & Troubleshooting

Session logs are written to `/sdcard/Android/data/dev.lastlight.reborn/files/logs/` and logcat tag `LastLight`. Search for `[RED]` lines when diagnosing issues.

## Roadmap

See [PLAN.md](PLAN.md) for the detailed tracker:
- [x] Core bring-up, shaders, audio & masked-clear fixes
- [x] Standalone Compose launcher, SAF pickers, save backups, FPS HUD
- [x] 45/60 FPS unlock, true 20:9 aspect ratio, ADS gyro, flick-stable Touch-Look
- [ ] Pre-warmed Vulkan pipeline cache (`vk_prewarm.bin`) & HUD layout editor
- [ ] On-device `.nsp` → `.nup` & shader packer (zero PC dependency)

## Legal Notice

This repository contains **no game files, ROMs, keys, firmware, or shaders**. You must provide your own legally owned copy of _Metro: Last Light Redux_ for Nintendo Switch. All trademarks belong to their respective owners.
