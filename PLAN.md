# PLAN.md — Дорожная карта LastLight Reborn

Актуальный план разработки самостоятельного нативного Android-порта **Metro: Last Light Redux** (`0100F0400E850000`).

---

## Базовый bring-up ядра — Готово ✅

- [x] Импорт `.nsp` → `.nup` и анализ бинарников (`survey.md`: ARM64, SDK `8.2.0`, NVN).
- [x] Разбор шейдеров `romfs/shaders.nx` (854/854 валидных SPIR-V модулей, 0 ошибок `spirv-val`).
- [x] Профиль игры в `peanuts-native/dm/games/0100F0400E850000/`.
- [x] Фикс артефактов/капель: реализован masked color clear (`color_masked`) в `dm/gfx/vk_android/vka_exec.c`.
- [x] Фикс звука: добавлен хук `nn::audio::GetAudioOutBufferBufferSize` в `dm/host/hle_audio.c`.
- [x] Проверено 25+ минут стабильного геймплея на Snapdragon 8 Gen 3 (Turnip `v26.0.0_R8`) без артефактов и проблем со звуком.

---

## Этап 1 — Самостоятельный лаунчер и сенсорное управление — Готово ✅

- [x] Чистый Android-проект `LastLight Reborn` (`dev.lastlight.reborn`, Kotlin + Jetpack Compose, строго `sensorLandscape`).
- [x] Нативный JNI-мост (`lastlight_jni.c`) поверх `libnxhost.so` с выделенным процессом `:game`.
- [x] Редизайн UI в стиле Metro (AAA верхний бар, стеклянные карточки, обложка, чистая типографика без КАПС, RU + EN).
- [x] Системные SAF-пикеры для папки игры, драйвера Turnip `.zip` (с чтением `meta.json`) и экспорта/импорта сохранений `.zip`.
- [x] Процесс-безопасное сохранение настроек в JSON-файл (`lastlight_config.json`).
- [x] Сенсорный шутер-HUD: плавающий левый стик (авто-бег `L3`), правая половина — свободный Touch-Look, реальные подписи кнопок Nintendo Switch (`A`, `B`, `X`, `Y`, `ZL`, `ZR`, `L`, `R`, `R3`, `−`, `+`, D-pad).
- [x] Компактный оверлей телеметрии (`FPS` + температура батареи `°C`).
- [x] Фикс выхода жестом «Назад» (`OnBackPressedCallback` + `terminateProcess()`).

---

## Этап 2 — Оптимизации движка, 60 FPS и доводка управления — В тестировании 🔄

- [x] Анлок 45 / 60 FPS (`DM_PRESENT_INTERVAL=display` + `DM_PRESENT_HZ`, `fps_limit_present 1` в `game.tsv`, переключатель `30 / 45 / 60 FPS` в Графике).
- [x] Полноэкранный режим 20:9 / без полос (`aspect_plan` на `m0x12fc198` + `DM_VK_PICTURE_ASPECT`).
- [x] Аппаратный гироскоп (`Sensor.TYPE_GYROSCOPE`: Выкл / В прицеле `ZL` / Всегда + слайдер чувствительности).
- [x] Снижение нагрузки от page-faults (`DM_DIRTY_BLOCK_AT=0`, `DM_DIRTY_BLOCK_KB=64`).
- [ ] **Проверка на устройстве:** замерить реальный FPS на 45/60, проверить тайминги/физику 4A Engine, работу 20:9, гироскопа и температуру батареи по логу.
- [ ] Собрать и вшить `vk_prewarm.bin` из `.keys` реальных запусков (чтобы убрать компиляцию пайплайнов при загрузке).
- [ ] Доработка эргономики сенсорного HUD (по результатам теста: при необходимости редактор позиций/размера кнопок и настройка кривой отклика Touch-Look / гироскопа).
- [ ] Полировка визуала лаунчера (кастомный широкоформатный арт/анимации переходов/просмотр логов).

---

## Этап 3 — Независимость от ПК (On-device сборка из `.nsp`) — В очереди ⏳

- [ ] Подключить `libnximport.so` (`peanuts-native/dm/peanuts/import`) в `app/src/main/cpp/CMakeLists.txt` для распаковки `.nsp` / `.xci` / `.nsz` → `.nup` прямо на смартфоне.
- [ ] Настроить сборку on-device `stage` (`libpython3.14.so`, `libnxpack.so`, `libdmsh_xlate.so`, `libSPIRV-Tools-shared.so`, `libzbicdec.so`, `python.zip`) через Linux / GitHub Actions.
- [ ] Реализовать `PackService` и пошаговый мастер установки в UI: выбор `.nsp` + `prod.keys` + `firmware.zip` → прогресс сборки (1–2 мин для 854 шейдеров) → «Играть».
- [ ] Сквозная проверка чистой установки APK на телефоне без участия ПК.
