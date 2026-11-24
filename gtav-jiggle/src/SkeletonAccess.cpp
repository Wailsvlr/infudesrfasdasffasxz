#include "SkeletonAccess.h"

#include <cmath>
#include <utility>

#include "LayoutProbe.h"

namespace jiggle {

namespace {
bool PlausibleTranslation(Vec3 v) {
  return IsFinite(v) && std::fabs(v.x) <= kMaxObjectSpaceMagnitude &&
         std::fabs(v.y) <= kMaxObjectSpaceMagnitude && std::fabs(v.z) <= kMaxObjectSpaceMagnitude;
}
}  // namespace

SkeletonAccess::SkeletonAccess(IMemory& memory, SkeletonLayout layout)
    : memory_(memory), layout_(std::move(layout)) {}

std::optional<uintptr_t> SkeletonAccess::ResolveTranslation(uintptr_t pedBase, int32_t boneIndex) const {
  if (!Ready() || boneIndex < 0 || static_cast<uint32_t>(boneIndex) > *layout_.maxBoneIndex) {
    return std::nullopt;
  }
  if (!IsPlausibleUserPointer(pedBase)) return std::nullopt;

  uintptr_t current = pedBase;
  for (const uint32_t offset : *layout_.chain) {
    uint64_t next = 0;
    if (!ReadValue(memory_, current + offset, next)) return std::nullopt;
    if (!IsPlausibleUserPointer(next)) return std::nullopt;
    current = static_cast<uintptr_t>(next);
  }

  const uint64_t address = static_cast<uint64_t>(current) + *layout_.bone0TranslationOffset +
                           static_cast<uint64_t>(boneIndex) * *layout_.matrixStride;
  if (!IsPlausibleUserPointer(address) || (address & 3u) != 0) return std::nullopt;
  return static_cast<uintptr_t>(address);
}

bool SkeletonAccess::Read(uintptr_t pedBase, int32_t boneIndex, Vec3& out) const {
  const auto address = ResolveTranslation(pedBase, boneIndex);
  if (!address) return false;
  Vec3 value;
  if (!memory_.Read(*address, &value, sizeof(value)) || !PlausibleTranslation(value)) return false;
  out = value;
  return true;
}

bool SkeletonAccess::Write(uintptr_t pedBase, int32_t boneIndex, Vec3 value) {
  if (!PlausibleTranslation(value)) return false;
  const auto address = ResolveTranslation(pedBase, boneIndex);
  if (!address) return false;
  return memory_.Write(*address, &value, sizeof(value));
}

}  // namespace jiggle
