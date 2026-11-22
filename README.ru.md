# LastLight Reborn

[English](README.md) | **Русский**

> Самостоятельный нативный Android-порт и лаунчер для **Metro: Last Light Redux** (Nintendo Switch `v1.0.0`, ARM64 / Vulkan 1.3).

[![Platform](https://img.shields.io/badge/Android-13%2B%20(arm64--v8a)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Graphics](https://img.shields.io/badge/Graphics-NVN%20%E2%86%92%20Vulkan%201.3-AC162C?style=flat-square&logo=vulkan&logoColor=white)](https://www.vulkan.org/)
[![UI](https://img.shields.io/badge/UI-Kotlin%20%2B%20Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Release](https://img.shields.io/badge/Release-v0.1.0--alpha-F6B747?style=flat-square)](https://github.com/Meller2/LastLight-reborn/releases/tag/v0.1.0-alpha)

---

## О проекте

**LastLight Reborn** (`dev.lastlight.reborn`) запускает **Metro: Last Light Redux** (Switch `v0`, Title ID `0100F0400E850000`, 4A Engine, `nnSdk 8.2.0`) нативно на ARM64-смартфонах через слой Horizon HLE и трансляцию NVN → Vulkan (`peanuts-native` / `libnxhost.so`), обёрнутые в JNI-мост (`liblastlight_bridge.so`) и шутерный лаунчер на Jetpack Compose.

## Возможности

- **Нативный ARM64 + Vulkan 1.3** — без JIT-оверхеда CPU; все 854 шейдера игры (`romfs/shaders.nx`) предтранслированы в `dmsh_vk.lib` без ошибок `spirv-val`.
- **Анлок 45 / 60 FPS** — снимает зашитый лимит 30 FPS (`DM_PRESENT_INTERVAL=display`) с сохранением корректной дельта-физики 4A Engine.
- **Честный 20:9 / Ultrawide** — 5-точечный патч аспекта (`DM_ASPECT_PLAN`) для инициализации дисплея и тиков динамического разрешения: честное расширение FOV без растягивания картинки.
- **Лаунчер в стиле Metro** — тёмный стеклянный альбомный интерфейс, карточка реального железа и живой температуры батареи, быстрые профили (`Холодный 30` / `Баланс 45` / `Максимум 60`), апскейлеры `SGSR1` / `FSR1`, импорт драйверов **Turnip** `.zip` и бэкап сохранений в `.zip`.
- **Шутерный HUD и гироскоп** — плавающий левый стик с авто-спринтом (`L3`), 120 Гц аккумулятор углового импульса для Touch-Look (быстрые флики сохраняют угол поворота), гироскоп с режимом «только в прицеле ZL», подлинные подписи кнопок Switch и автоматическое переключение на физический геймпад.
- **Изолированный процесс `:game`** — чистый жизненный цикл `nxhost_main`, процесс-безопасный JSON-конфиг (`lastlight_config.json`) и живая подстройка управления в меню паузы.

## Системные требования

| Компонент | Минимум | Рекомендуется (проверено) |
|---|---|---|
| **ОС** | Android 13 (API 33), `arm64-v8a` | Android 14–16 (API 34–36) |
| **Процессор / GPU** | Snapdragon 8 Gen 2 / Adreno 740 (Vulkan 1.3) | Snapdragon 8 Gen 3 (`SM8650`, Adreno 750) |
| **Драйвер** | Системный Vulkan 1.3 | Mesa Turnip (`v26.0.0_R8` AdrenoTools `.zip`) |
| **ОЗУ / Память** | 8 ГБ ОЗУ / ~9 ГБ свободно | 12+ ГБ ОЗУ / UFS 3.1+ |

## Быстрый старт (Android)

1. Установите свежий APK из [GitHub Releases](https://github.com/Meller2/LastLight-reborn/releases).
2. Поместите легально полученную копию **Metro: Last Light Redux** (`0100F0400E850000`, `v0`) в формате `.nup` и `android-pack` (`main.patched.bin`, `sdk.patched.bin`, `pack.tsv`, `shaders/.../dmsh_vk.lib`) в папку на смартфоне.
3. Откройте **LastLight Reborn**, выдайте доступ к хранилищу и выберите папку с игрой (индикаторы `NUP`, `Pack`, `Shaders` должны загореться зелёным).
4. *(Рекомендуется для Adreno)* Импортируйте `.zip` драйвера Turnip во вкладке **Файлы → Драйвер Turnip**.
5. Выберите профиль (`30` / `45` / `60`) и нажмите **Запуск**.

## Сборка из исходников

Требуются **JDK 21**, **Android SDK** (`compileSdk 36`, `cmake 3.22.1`, **`ndk 29.0.14206865`**) и **Python 3** (`python3` в `PATH`).

```powershell
git clone --recursive https://github.com/Meller2/LastLight-reborn.git
cd LastLight-reborn

# Применить локальные патчи peanuts-native и профиль Metro
git -C peanuts-native apply ..\patches\peanuts-native.patch
New-Item -ItemType Directory -Force -Path "peanuts-native\dm\games\0100F0400E850000" | Out-Null
Copy-Item -Path "patches\0100F0400E850000\*" -Destination "peanuts-native\dm\games\0100F0400E850000\" -Force

# Собрать и установить Release APK
.\gradlew.bat :app:assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

## Ключевые технические фиксы

| Область | Решение |
|---|---|
| **Masked Color Clear** | Реализован SPIR-V проход `color_masked` в `vka_exec.c` для очистки с маской `0x3` (убирает артефакты-«капли» в центре экрана). |
| **Заикания звука** | Добавлен `HLE_FORCE`-хук `GetAudioOutBufferBufferSize` в `hle_audio.c`, возвращающий `b->cap`. |
| **Честный 20:9** | 5-точечный патч (`0x5a504`, `0x5a5c8`, `0x5a5cc`, `0x5a5f0`, `0x5a6c4`) в `DM_ASPECT_PLAN`, не дающий динамическому разрешению сбрасывать аспект в `16:9`. |
| **Нагрев / Page Faults** | Включены 64 KiB dirty-блоки (`DM_DIRTY_BLOCK_AT=0`, `DM_DIRTY_BLOCK_KB=64`), убирающие ~2.3M лишних page faults. |
| **Touch-Look и гироскоп** | 120 Гц буфер углового смещения + 2D-радиальная компенсация мёртвой зоны и кривой стика; хостовый `DM_MOTION=0` для строгого контроля гироскопа по `ZL`. |

## Структура проекта

- `app/src/main/cpp/` — `CMakeLists.txt` (`libnxhost.so` + `liblastlight_bridge.so`) и `lastlight_jni.c`.
- `app/src/main/java/dev/lastlight/reborn/` — `MainActivity`, `GameActivity` (`:game`), `data/` (`AppPrefs`, `GamePaths`, `DeviceInfo`, `SaveBackupManager`), `ui/` (`LauncherScreen`, `ShooterHudOverlay`, `PerfOverlay`).
- `patches/` — дифф правок `peanuts-native` (`peanuts-native.patch`) и профиль `0100F0400E850000/`.
- `peanuts-native/` — ядро Horizon HLE + NVN → Vulkan (git submodule).

## Логи и диагностика

Логи каждого запуска сохраняются в `/sdcard/Android/data/dev.lastlight.reborn/files/logs/` и дублируются в logcat (`LastLight`). Для поиска ошибок ищите строки с тегом `[RED]`.

## Дорожная карта

Подробный список задач — в [PLAN.md](PLAN.md):
- [x] Базовый bring-up, шейдеры, фиксы звука и `color_masked`
- [x] Самостоятельный Compose-лаунчер, SAF-пикеры, бэкап сейвов, шутер-HUD
- [x] Анлок 45/60 FPS, честный 20:9, ADS-гироскоп, стабильные флики Touch-Look
- [ ] Вшитый кэш пайплайнов (`vk_prewarm.bin`) и редактор раскладки HUD
- [ ] Сборка `.nsp` → `.nup` и шейдеров прямо на смартфоне (без ПК)

## Правовая информация

Репозиторий **не содержит** игровых файлов, ROM-образов, ключей, прошивки или шейдеров. Пользователь обязан использовать собственную легально приобретённую копию _Metro: Last Light Redux_ для Nintendo Switch. Все товарные знаки принадлежат их правообладателям.
