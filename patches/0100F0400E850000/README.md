<!-- PEANUTS: Metro: Last Light Redux bring-up profile -->
# Metro: Last Light Redux (`0100F0400E850000`)

- **Status:** bring-up on Android (Snapdragon 8 Gen 3, Adreno / Turnip)
- **Builds:** `v0` (1.0.0): `main=85c362cc9790f0edf23749b041953c51`, `sdk=3ae34e689d7a35f13cbf42bfd672af8b` (SDK 8.2.0, same `sdk` build as Diablo III)
- **Engine / Graphics:** 4A Engine, NVN
- **Shaders:** `romfs/shaders.nx` — 854 shaders (463 pixel, 391 vertex), 913 variation pairs, split automatically by `shader_split.py`.
- **Memory:** NPDM declares 0 MB system resource (`mem_reserve=0x100000000`, `mem_alias=0x10000000`, `mem_stack=0x10000000`, `app_pool=0xCD500000`).

## Found on this game

- Initial profile generated from base v0 survey and shader split.

## Open

- First boot and rendering verification on device.
