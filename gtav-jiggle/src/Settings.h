#pragma once

// Every build-dependent value lives here, in Settings, and is loaded from
// Jiggle.ini. A std::optional that is empty means "unknown"; a real 0 is
// std::optional<T>{0}. Nothing in this project ships a guessed game value.

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "JiggleMath.h"

namespace jiggle {

struct Hotkeys {
  int toggle{0x75};  // VK_F6
  int probe{0x76};   // VK_F7
  int reload{0x77};  // VK_F8
  int nudge{0x78};   // VK_F9 (hold)
};

struct ScopeSettings {
  bool includeNearbyPeds{true};
  uint32_t maxPeds{16};
  float maxDistance{25.f};  // metres from the player ped
};

// Which executable build the SkeletonLayout below was captured on.
struct BuildExpectation {
  std::optional<uint32_t> imageSize;       // PE OptionalHeader.SizeOfImage of the main module
  std::optional<uint32_t> timeDateStamp;   // PE FileHeader.TimeDateStamp of the main module
  std::string provenance;                  // free text: where the values came from
};

// How to reach the object-space matrix translations of a ped's skeleton.
//   cur = pedBase; for each offset in chain: cur = *(uintptr_t*)(cur + offset)
//   translation(bone i) = cur + bone0TranslationOffset + i * matrixStride
// All offsets are byte offsets in the *running process* (runtime layout, not file RVAs).
struct SkeletonLayout {
  std::optional<std::vector<uint32_t>> chain;  // empty vector = zero dereferences ("Chain=none")
  std::optional<uint32_t> bone0TranslationOffset;
  std::optional<uint32_t> matrixStride;
  std::optional<uint32_t> maxBoneIndex;  // inclusive upper bound for bone indices we may touch
  std::string provenance;

  std::vector<std::string> MissingFields() const;
  bool Complete() const { return MissingFields().empty(); }
};

struct BoneSettings {
  std::string label;
  std::string boneName;  // as the game's bone-name lookup expects it
  SpringParams spring;
  ShapeParams shape;
};

struct ProbeSettings {
  std::vector<std::string> boneNames;  // at least two bones that exist on the player ped model
  uint32_t rootWindowBytes{0x2000};
  uint32_t windowBytes{0x800};
  uint32_t maxDepth{2};
  uint32_t maxWindows{20000};
  float tolerance{0.02f};
  std::vector<uint32_t> strides;  // candidate byte strides between consecutive bone matrices
};

struct Settings {
  Hotkeys hotkeys;
  bool startEnabled{true};
  float nudgeMeters{0.10f};
  ScopeSettings scope;
  BuildExpectation build;
  SkeletonLayout layout;
  std::vector<BoneSettings> bones;  // only entries with a bone name
  ProbeSettings probe;
};

struct LoadResult {
  Settings settings;
  std::vector<std::string> warnings;
};

LoadResult LoadSettings(std::string_view iniText);

// ---------------------------------------------------------------------------
// Gate: decides whether the memory-writing feature may run at all.
// ---------------------------------------------------------------------------

struct ModuleFingerprint {
  uint32_t imageSize{0};
  uint32_t timeDateStamp{0};
};

struct GateResult {
  bool open{false};
  std::vector<std::string> reasons;  // why it is closed
};

GateResult EvaluateGate(const Settings& settings, ModuleFingerprint actual);

}  // namespace jiggle
