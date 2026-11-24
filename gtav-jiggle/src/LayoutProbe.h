#pragma once

// Finds where a ped's object-space bone matrices live, using the game's own
// bone-position natives as the oracle. Pure logic over IMemory, so it is
// testable without the game.
//
// Hypothesis the probe tests (it does not assume it): the skeleton keeps one
// matrix per bone, consecutive bones are `stride` bytes apart, and each
// matrix holds the bone's translation as three contiguous floats. For every
// pair of probe bones the translation difference in memory must equal the
// difference of the positions the natives report, expressed in the ped's
// right/forward/up axes. The common origin offset cancels out.

#include <array>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

#include "JiggleMath.h"
#include "Memory.h"

namespace jiggle {

inline constexpr size_t kMaxProbeBones = 6;
inline constexpr float kMaxObjectSpaceMagnitude = 100.f;

struct ProbeBone {
  int32_t boneIndex{0};
  Vec3 expected{};  // position from the natives, in (right, forward, up) coordinates
};

enum class MatchMode {
  Components,  // translation differences must match component-wise (axes agree)
  Distances,   // only pairwise distances must match (axes may differ); needs >= 3 bones
};

struct MatrixCandidate {
  size_t bufferOffset{0};  // offset of bone 0's translation inside the scanned buffer
  uint32_t stride{0};
  std::array<Vec3, kMaxProbeBones> observed{};
  size_t count{0};
};

// Returns an empty string when the bones are usable, else the reason they are not.
std::string ValidateProbeBones(std::span<const ProbeBone> bones, float tolerance, MatchMode mode);

std::vector<MatrixCandidate> ScanBufferForMatrixArray(std::span<const uint8_t> buffer,
                                                      std::span<const ProbeBone> bones,
                                                      std::span<const uint32_t> strides,
                                                      float tolerance, MatchMode mode,
                                                      size_t maxResults);

struct ProbeOptions {
  uint32_t rootWindowBytes{0x2000};
  uint32_t windowBytes{0x800};
  uint32_t maxDepth{2};
  uint32_t maxWindows{20000};
  float tolerance{0.02f};
  std::vector<uint32_t> strides;
  size_t maxHits{32};
  MatchMode mode{MatchMode::Components};
};

struct ProbeHit {
  std::vector<uint32_t> chain;      // pointer-slot offsets to dereference, starting at the ped base
  uint32_t bone0TranslationOffset{0};
  uint32_t stride{0};
  uintptr_t absoluteAddress{0};     // where bone 0's translation was seen in the live process
  std::array<Vec3, kMaxProbeBones> observed{};
  size_t count{0};
};

struct ProbeReport {
  std::vector<ProbeHit> hits;  // shortest chains first
  size_t windowsScanned{0};
  size_t unreadableWindows{0};
  bool windowCapReached{false};
  std::string error;           // non-empty when the probe could not run
};

ProbeReport RunLayoutProbe(const IMemory& memory, uintptr_t root, std::span<const ProbeBone> bones,
                           const ProbeOptions& options);

// SkeletonLayout::bone0TranslationOffset is the translation of bone *index 0*; a hit reports the
// translation of the first probe bone. Empty when the array would start before the pointed-to block.
std::optional<uint32_t> LayoutOffsetForBoneZero(const ProbeHit& hit, int32_t firstProbeBoneIndex);

}  // namespace jiggle
