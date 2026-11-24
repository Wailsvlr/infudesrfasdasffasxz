# GTA V jiggle physics (Script Hook V `.asi`)

Secondary-motion (spring) physics for chosen ped bones. Single-player, Script Hook V, x64, C++20.

## Status

| Part | State |
|---|---|
| Spring solver, shaping, config parser, gate, layout probe, skeleton access | Built and run here with g++ 13 on Linux, ASan + UBSan on: `2113 checks, 0 failures` |
| `Plugin.cpp`, `Win32Support.cpp` | Compiled and linked here with mingw-w64 against **stub** Script Hook V headers I wrote. Not built with MSVC, not built against the real SDK, never run in the game. |
| Skeleton memory layout (`Chain`, `Bone0TranslationOffset`, `MatrixStride`, `MaxBoneIndex`) | **Unknown.** No value is shipped. The feature is gated until you capture them with the probe. |
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

## First run (what unblocks the feature)

1. In `Jiggle.ini` `[Probe] Bones=` put two to six bone names that exist on the player ped model (read them off the model's skeleton in CodeWalker). Pick bones at least about 0.1 m apart.
2. Start single-player, stand still and upright, press **F7**. Read `Jiggle.log` (next to the `.asi`).
3. The `main module:` line gives `SizeOfImage` and `TimeDateStamp`. Put them in `[Build]` and say in `Provenance=` where they came from.
4. A `probe: hit N` line gives `chain`, `Bone0TranslationOffset` and `stride`. Copy them into `[Skeleton]` as `Chain`, `Bone0TranslationOffset`, `MatrixStride`. Set `MaxBoneIndex` to the highest bone index of the ped skeleton.
   Each hit prints, per probe bone, the memory delta next to the natives delta. They must agree.
5. Add the bones to jiggle as `[Bone.Something]` sections with `Name=`. Use leaf bones: only the bone's own matrix is written, children do not follow.
6. **F8** reloads the INI. Hold **F9**: every configured bone on the player should jump up by `NudgeMeters`. If nothing moves, the hit was a stale copy; try the next hit. If it moves, **F6** toggles the physics.

Feedback is `Jiggle.log` plus system beeps: a plain beep means on / ok, the asterisk sound means off, the hand sound means gated or failed (reasons are in the log).

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
