# GTA V jiggle physics (Script Hook V `.asi`)

Secondary-motion (spring) physics for chosen ped bones. Single-player, Script Hook V, x64, C++20.

## Status

| Part | State |
|---|---|
| Spring solver, shaping, config parser, gate, layout probe, skeleton access | Built and run here with g++ 13 on Linux, ASan + UBSan on: `2113 checks, 0 failures` |
| `Plugin.cpp`, `Win32Support.cpp` | Compiled and linked here with mingw-w64 against **stub** Script Hook V headers I wrote. Not built with MSVC, not built against the real SDK, never run in the game. |
| Skeleton memory layout (`Chain`, `Bone0TranslationOffset`, `MatrixStride`, `MaxBoneIndex`) | **Unknown.** No value is shipped. The feature is gated until the in-game probe (F7) captures them into `Jiggle.auto.ini`. |
| Executable fingerprint (`ExpectedImageSize`, `ExpectedTimeDateStamp`) | **Unknown.** Gated until you copy them from `Jiggle.log`. |

## Layout

```
CMakeLists.txt        core lib + tests everywhere; Jiggle.asi on Windows when the SDK is found
Jiggle.ini            every build-dependent value, all blank
src/JiggleMath.h      spring solver, frame math, write bookkeeping (pure)
src/IniParser.*       INI parsing (pure)
src/Settings.*        config model, loader, gate (pure)
src/Memory.h          IMemory interface, pointer plausibility
src/LayoutProbe.*     finds the bone-matrix array using the game's own bone natives as oracle (pure)
src/SkeletonAccess.*  validated read/write of one bone translation through the layout (pure)
src/Win32Support.*    ReadProcessMemory/WriteProcessMemory wrapper, PE fingerprint, logger
src/Plugin.cpp        Script Hook V entry point and tick loop
tests/TestMain.cpp    everything above that does not need the game
```

## Build

Requirements: Visual Studio 2022 (MSVC, x64), CMake 3.20+, the Script Hook V SDK (`inc/` and `lib/ScriptHookV.lib`).

```bash
cmake -S gtav-jiggle -B gtav-jiggle/build -G "Visual Studio 17 2022" -A x64 -DSHV_SDK_DIR=C:/path/to/ScriptHookV_SDK
cmake --build gtav-jiggle/build --config Release
ctest --test-dir gtav-jiggle/build -C Release --output-on-failure
```

Output: `gtav-jiggle/build/Release/Jiggle.asi` plus a copy of `Jiggle.ini`. Put both next to `GTA5.exe` (or where your ASI loader reads them) with Script Hook V installed.
If your SDK zip uses another layout, pass `-DSHV_INC_DIR=...` and `-DSHV_LIB_FILE=...` instead.

Logic tests only, any compiler: `cmake -S gtav-jiggle -B b && cmake --build b && ctest --test-dir b`.

## First run (automatic setup)

1. Put `Jiggle.asi` and `Jiggle.ini` next to the game executable (Script Hook V installed). Start single-player.
2. Play as a ped that has the bones you want to move (see "Which ped").
3. Stand still and upright, press **F7**. The plugin finds the bone-matrix array in memory using the game's own bone natives, writes `Jiggle.auto.ini` (build fingerprint and layout, values from the running game), reloads, and for about 2.5 seconds pushes every configured bone up by `NudgeMeters` as a visible test.
4. If the bones jumped, the setup is right: **F6** toggles the physics. If nothing moved, press **F7** again to try the next hit; the log lists them all.
5. `Jiggle.log` lists every step. Values you type into `Jiggle.ini` always win over `Jiggle.auto.ini`. Delete `Jiggle.auto.ini` to start over. If the game executable changes (update), the fingerprint no longer matches, the feature closes by itself, and F7 sets it up again.

Hotkeys: F6 toggle, F7 probe and setup, F8 reload the INI, F9 hold for the nudge test.

## Which ped

The default story characters have no breast bones. The female upper-body models in the pack that prompted this project skin about 1000-1600 vertices each to skeleton bone indices 125 and 126, mirrored at chest height; names for those bones are not stored in the files. `Jiggle.ini` therefore ships `SPR_L_Breast` and `SPR_R_Breast` as **unverified candidate names**; after the first F7, `Jiggle.log` prints `jiggle bone '<name>' -> index N`. If the indices are 125 and 126 the names are right. If a name says "not on this ped model", change it in `Jiggle.ini`. If the game's own skeleton already springs those bones, this plugin only adds a small extra offset on top.

## If the probe finds nothing

1. Check the log: are all probe bones found, is `cap REACHED`? Raise `MaxDepth` (max 3), `MaxWindows`, `WindowBytes`.
2. If a hit shows up only under `distances` mode, the object-space axes are not right/forward/up. The physics offsets would then point the wrong way; tell me the printed deltas and I will add an axis map.
3. Find the layout by hand: look up the native hash of `GET_WORLD_POSITION_OF_ENTITY_BONE` in your `natives.h`, break on its handler in x64dbg, and step to where it loads the bone matrix. The displacements on the way from the entity pointer give `Chain`, and the index multiplication gives `MatrixStride`. Confirm the structure in ReClass.NET against the probe's printed positions.

## Unverified, check these

- Every Script Hook V name used was written from memory of the SDK headers, not from your copy: `scriptRegister`, `scriptUnregister`, `scriptWait`, `getScriptHandleBaseAddress`, `worldGetAllPeds`, `PLAYER::PLAYER_PED_ID`, `ENTITY::DOES_ENTITY_EXIST`, `ENTITY::GET_ENTITY_COORDS`, `ENTITY::GET_ENTITY_FORWARD_VECTOR`, `ENTITY::GET_ENTITY_MODEL`, `ENTITY::GET_ENTITY_BONE_INDEX_BY_NAME`, `ENTITY::GET_WORLD_POSITION_OF_ENTITY_BONE`, `PED::IS_PED_RAGDOLL`. A mismatch shows up at compile time.
- Whether Script Hook V runs on your game edition and build is unknown to me.
- Hypotheses the probe tests rather than assumes: matrices per bone, contiguous float triple for the translation, object-space axes equal to the ped's right/forward/up.
- Not known yet, needs the game: whether a write from the script tick survives to render, and whether the native's world position reads the same memory the plugin writes. The plugin handles both "game refreshed the matrix" and "game did not" for the stored value, but only the nudge test shows what the screen does.
- I do not know if the vanilla ped skeleton has any bone worth jiggling (for example breast or butt bones). Confirm on your ped in CodeWalker. Custom add-on peds with extra leaf bones are the intended target.
- Physics defaults (`FrequencyHz`, `DampingRatio`, `MaxDisplacement`, `Gain`) are uncalibrated starting values.

## Behaviour notes

- No hooks, no code patches. The only writes are 12-byte translation stores through `WriteProcessMemory` on the current process, each validated first (chain pointers, bone index bound, finite and bounded values). A failed or partial read or write counts as failure and skips that bone.
- Ragdolling peds are skipped and restored. Peds that leave range or are replaced are restored or dropped. Turning the feature off restores every bone whose memory still holds our last write.
- Unloading needs no special handling: the game rewrites the matrices itself, and nothing in the process points into the plugin.
