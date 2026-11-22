# AGENTS.md — LastLight Reborn

## Project Overview

**LastLight Reborn** (`dev.lastlight.reborn`) is a standalone native Android port/launcher for **Metro: Last Light Redux** (Nintendo Switch 1, base `v0`, Title ID `0100F0400E850000`, 4A Engine, ARM64, SDK `8.2.0`, NVN → Vulkan).

- **App layer (`app/`):** Kotlin + Jetpack Compose (`minSdk 33`, `targetSdk 36`, `arm64-v8a`), locked to `sensorLandscape`.
- **Native core (`peanuts-native/`):** Used strictly as the Horizon HLE / ARM64 execution / NVN-to-Vulkan core. Built directly into `app/` via `app/src/main/cpp/CMakeLists.txt` with `GAME=0100F0400E850000` (`NX_GENERIC_HOST=OFF`), producing `libnxhost.so` and our thin JNI bridge `liblastlight_bridge.so` (`app/src/main/cpp/lastlight_jni.c`).
- **Process model:** `GameActivity` **must** run in a dedicated process (`android:process=":game"`). `nxhost_main` cannot be started twice in one process; `GameActivity.onDestroy()` calls `NativeBridge.terminateProcess()` (`_exit(0)`) whenever finishing.
- **Cross-process settings:** Stored in `filesDir/lastlight_config.json` (`data/AppPrefs.kt`), never in `SharedPreferences`, so live adjustments inside `:game` persist and sync immediately with `MainActivity`.
- **Manifest requirements:** `android:allowNativeHeapPointerTagging="false"` is mandatory (Android 11+ Scudo pointer tagging breaks guest addresses). `MANAGE_EXTERNAL_STORAGE` is required to read `.nup` / `android-pack` on shared storage.

---

## Repository Layout

- `app/src/main/cpp/CMakeLists.txt` — builds `peanuts-native/android/host` (`libnxhost.so`) + `liblastlight_bridge.so`.
- `app/src/main/cpp/lastlight_jni.c` — JNI shim for `dev.lastlight.reborn.nativecore.NativeBridge` (log redirection to file + logcat, 8 MiB stack thread for `nxhost_main`, `ANativeWindow`, lifecycle, input, FPS query, `_exit`).
- `app/src/main/java/dev/lastlight/reborn/`
  - `MainActivity.kt` — Compose launcher entry, SAF pickers (`OpenDocumentTree` for game folder, `OpenDocument` for Turnip `.zip` & save import, `CreateDocument` for save export).
  - `GameActivity.kt` — `:game` process hosting `SurfaceView`, physical gamepad forwarding, `OnBackPressedCallback` pause drawer, `ShooterHudOverlay`, and `PerfOverlay`.
  - `nativecore/NativeBridge.kt` — JNI declarations (`VIRTUAL_PAD_DEVICE_ID = 0x70000001`).
  - `data/AppPrefs.kt` — process-safe JSON config (`PerfPreset`, `GyroMode`, `LauncherConfig`).
  - `data/GamePaths.kt` — locates `.nup`, `android-pack` (`main.patched.bin` / `pack.tsv`), `dmsh_vk.lib`, extracts Turnip `.so` from AdrenoTools `.zip` into `filesDir/gpu_driver/`, builds `DM_*` environment variables.
  - `data/SaveBackupManager.kt` — `.zip` export/import of `saves/0100F0400E850000/`.
  - `ui/theme/MetroTheme.kt`, `ui/Strings.kt`, `ui/LauncherScreen.kt` — Metro-styled Compose UI (RU + EN).
  - `ui/hud/ShooterHudOverlay.kt` — floating left stick (auto-sprint `L3`), right-half free Touch-Look + hardware `Sensor.TYPE_GYROSCOPE` (`OFF` / `ADS` / `ALWAYS`), Nintendo Switch-labelled buttons (`ZR`, `ZL`, `A`, `B`, `X`, `Y`, `R3`, `L`, `R`, `−`, `+`, D-pad).
  - `ui/hud/PerfOverlay.kt` — live FPS (`nxhost_fps`) + battery temperature (`BatteryManager.EXTRA_TEMPERATURE`).
- `peanuts-native/` — upstream engine core + local fixes (see below).
- `work/` — local, uncommitted workspace (packer conda env, MinGW toolchain, keys, `.nup`, `android-pack`, pulled device logs).

---

## Local Modifications in `peanuts-native/`

Keep changes in `peanuts-native/` minimal, general, and well-tracked:

1. **`dm/gfx/vk_android/vka_exec.c`** — Implemented `color_masked(...)` (fullscreen triangle + push-constant SPIR-V fragment shaders `vka_cmc_frag_f` / `vka_cmc_frag_u` with `colorWriteMask`) for `nvnCommandBufferClearColor` when `(c->mask & 0xf) != 0xf`. Metro clears a post-process/refraction buffer with mask `0x3`; skipping it caused smeared droplet artifacts in the center of the screen.
2. **`dm/host/hle_audio.c`** — Added `HLE_FORCE` hook `h_au_BufCap` for `_ZN2nn5audio27GetAudioOutBufferBufferSizeEPKNS0_14AudioOutBufferE` returning `b->cap`. Without it, the call fell through to `sdk` and read the wrong struct offset, causing severe audio stuttering/regression.
3. **`dm/games/0100F0400E850000/`** — Added Metro: Last Light Redux profile (`game.tsv`, `README.md`, `shader_map.tsv`, `shader_facts.tsv`, `shader_pairs.tsv`, `pso_pairs.tsv`, `pso_manifest.tsv`, `bf_want.tsv`):
   - `v0` (1.0.0): `main=85c362cc9790f0edf23749b041953c51`, `sdk=3ae34e689d7a35f13cbf42bfd672af8b` (identical `sdk` build to Diablo III).
   - `depth_mode zero`, `fps_limit_present 1`, `fps_limit_max 60`, `aspect_plan 85c362cc9790f0edf23749b041953c51 ratio_f32 m0x12fc198 398ee33f`.
4. **`android/app/build.gradle`** — Parameterized `ndkVersion` via `-PnxNdkVersion` (fallback `28.2.13676358`).

---

## Metro: Last Light Redux Technical Facts (4A Engine / Switch 1)

- **Modules:** `main` (13.3 MB text), `rtld`, `sdk` (`nnSdk 8.2.0`). No `subsdk`, no runtime GLSLC.
- **Shaders:** Uncompressed in `romfs/shaders.nx` (16.4 MB); `shader_split.py` extracts **854** unique shaders (463 pixel, 391 vertex, 913 variation pairs) cleanly into 880 `spirv-val` Vulkan 1.3-valid modules (`dmsh_vk.lib`, 2.4 MB).
- **Audio:** Uses `nn::audio::AudioOut` (stereo 48 kHz) + SDK software resampler; no `AudioRenderer`.
- **Movies:** `.ogv` decoded in software by 4A Engine (no `nn::movie` / MediaCodec needed).
- **30 FPS cap:** Hardcoded in `main+0xa83f00` (`mov w1, #2` → `nvnWindowSetPresentInterval`). Unlocked via `DM_PRESENT_INTERVAL=display` + `DM_PRESENT_HZ=<45|60>` (4A Engine is delta-time driven).
- **Aspect ratio:** Single `16/9` float (`0x3fe38e39`) at `main+0x12fc198`, patched cleanly via `DM_VK_PICTURE_ASPECT=<w>:<h>` + `DM_ASPECT_PLAN=85c362cc9790f0edf23749b041953c51,ratio_f32,m0x12fc198,398ee33f`.
- **Dirty page tracking:** Metro generates ~2.3M `write-protection faults` with 0 re-uploads when tracked page-by-page; `DM_DIRTY_BLOCK_AT=0` + `DM_DIRTY_BLOCK_KB=64` mitigates fault overhead.
- **PSO compilation:** `DM_VK_PSO_ASYNC=0` prevents skipped draws / flicker on uncached scenes (pipelines cache to `temp/0100F0400E850000/vk_pipelines-*.bin`).
- **Virtual pad mapping:** Register virtual touch pad with `labelled = 1` in `inputDeviceAdded` so `KEYCODE_BUTTON_A/B/X/Y` map 1:1 to Nintendo Switch A/B/X/Y labels.

---

## Build & Tooling (Windows Host)

### Local Tools Installed
- **JDK 21:** `C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot`
- **Android SDK:** `%LOCALAPPDATA%\Android\Sdk` (`platforms;android-36`, `build-tools;36.0.0`, `cmake;3.22.1`, `ndk;29.0.14206865`). Note: `ndk\28.2.13676358` is empty on this machine — always use `29.0.14206865`.
- **Packer Python env:** `work\bananative\env` (Python 3.14, `spirv-tools`, `dotnet-runtime 10`, `fonttools`, `capstone`, `lz4`, `zbic`). Includes a `python3.exe` copy of `python.exe` because CMake/Gradle invoke `python3`.
- **Native Importer:** `peanuts-native\peanuts\.build\nximport\nximport_cli.exe` (built with `work\mingw` MinGW-w64 GCC + `-D__get_cpuid_count(l,s,a,b,c,d)=0`).
- **C++ STL:** `-DANDROID_STL=c++_static` is required in `app/build.gradle.kts` (`libadrenotools` and `peanuts_rt` use C++ STL).

### Build & Install Command (PowerShell)
```powershell
$envDir = "D:\GameDev\Ports\LastLight-reborn\work\bananative\env"
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$cmakeBin = "$env:ANDROID_HOME\cmake\3.22.1\bin"
$env:PATH = "$envDir;$envDir\Library\bin;$envDir\Scripts;$env:JAVA_HOME\bin;$cmakeBin;$env:PATH"
Set-Location "D:\GameDev\Ports\LastLight-reborn"
.\gradlew.bat :app:assembleRelease
adb install -r "D:\GameDev\Ports\LastLight-reborn\app\build\outputs\apk\release\app-release.apk"
```

### Target Test Device & Paths
- **Device:** OnePlus `CPH2691` (Snapdragon 8 Gen 3 / `SM8650`, 1080×2376 120 Hz, Turnip `v26.0.0_R8`).
- **Device game folder:** `/sdcard/Download/content/Games/Nintendo/Games/Metro Last Light Redux/` (`.nup`, `.png`, `0100F0400E850000-work/android-pack/`).
- **App external files:** `/sdcard/Android/data/dev.lastlight.reborn/files/` (`logs/`, `saves/0100F0400E850000/`, `temp/0100F0400E850000/`).

---

## Conventions & Rules

- **No game assets or keys in git:** Never commit `.nsp`, `.nup`, `prod.keys`, firmware dumps, `work/`, `.apk`, or logs.
- **UI/UX standards:**
  - Landscape-only (`sensorLandscape`), clean Metro aesthetic (warm obsidian/steel surfaces, subtle amber `#E5953A` accents).
  - Use `FontFamily.SansSerif` with proper casing for UI text (avoid excessive ALL CAPS; reserve `FontFamily.Monospace` strictly for telemetry digits like `FPS / °C`).
  - Always use native Android SAF pickers for folders/files/saves — never require manual path typing.
  - On-screen HUD buttons must display actual Nintendo Switch labels (`A`, `B`, `X`, `Y`, `ZL`, `ZR`, `L`, `R`, `R3`, `−`, `+`, D-pad) rather than action names.
  - Keep RU and EN localization in `ui/Strings.kt` in sync.
- **Debugging workflow:** Always inspect device logs (`/sdcard/Android/data/dev.lastlight.reborn/files/logs/`) for `[RED]` lines and root-cause issues in the host/Vulkan backend rather than adding blind workarounds.
