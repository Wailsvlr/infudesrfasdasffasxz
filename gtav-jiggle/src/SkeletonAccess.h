#pragma once

#include <cstdint>
#include <optional>

#include "JiggleMath.h"
#include "Memory.h"
#include "Settings.h"

namespace jiggle {

// Reads and writes one bone's object-space translation through a SkeletonLayout.
// Every pointer on the way is checked; any failed or partial access is a failure.
class SkeletonAccess {
 public:
  SkeletonAccess(IMemory& memory, SkeletonLayout layout);

  bool Ready() const { return layout_.Complete(); }

  std::optional<uintptr_t> ResolveTranslation(uintptr_t pedBase, int32_t boneIndex) const;
  bool Read(uintptr_t pedBase, int32_t boneIndex, Vec3& out) const;
  bool Write(uintptr_t pedBase, int32_t boneIndex, Vec3 value);

 private:
  IMemory& memory_;
  SkeletonLayout layout_;
};

}  // namespace jiggle
