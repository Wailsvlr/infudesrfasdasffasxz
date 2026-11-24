#include "LayoutProbe.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <deque>
#include <unordered_set>

namespace jiggle {

static_assert(sizeof(Vec3) == 3 * sizeof(float), "Vec3 must be three packed floats");

namespace {

bool LoadVec3(std::span<const uint8_t> buffer, int64_t offset, Vec3& out) {
  if (offset < 0 || (offset & 3) != 0) return false;
  if (static_cast<uint64_t>(offset) + sizeof(Vec3) > buffer.size()) return false;
  std::memcpy(&out, buffer.data() + offset, sizeof(Vec3));
  return IsFinite(out) && std::fabs(out.x) <= kMaxObjectSpaceMagnitude &&
         std::fabs(out.y) <= kMaxObjectSpaceMagnitude && std::fabs(out.z) <= kMaxObjectSpaceMagnitude;
}

bool DistancesAgree(const std::array<Vec3, kMaxProbeBones>& observed,
                    std::span<const ProbeBone> bones, size_t count, float tolerance) {
  for (size_t a = 0; a < count; ++a) {
    for (size_t b = a + 1; b < count; ++b) {
      const float seen = Length(observed[b] - observed[a]);
      const float want = Length(bones[b].expected - bones[a].expected);
      if (std::fabs(seen - want) > tolerance) return false;
    }
  }
  return true;
}

bool ReadWindow(const IMemory& memory, uintptr_t address, size_t wanted, std::vector<uint8_t>& out) {
  for (size_t bytes = wanted & ~static_cast<size_t>(7); bytes >= 0x100; bytes = (bytes / 2) & ~static_cast<size_t>(7)) {
    out.resize(bytes);
    if (memory.Read(address, out.data(), bytes)) return true;
  }
  out.clear();
  return false;
}

}  // namespace

std::string ValidateProbeBones(std::span<const ProbeBone> bones, float tolerance, MatchMode mode) {
  if (bones.size() < 2) return "need at least two probe bones";
  if (bones.size() > kMaxProbeBones) return "too many probe bones";
  if (mode == MatchMode::Distances && bones.size() < 3) return "distance matching needs at least three bones";
  if (!(tolerance > 0.f) || !std::isfinite(tolerance)) return "tolerance must be positive";

  for (size_t a = 0; a < bones.size(); ++a) {
    if (bones[a].boneIndex < 0) return "negative bone index";
    if (!IsFinite(bones[a].expected)) return "non-finite expected position";
    for (size_t b = a + 1; b < bones.size(); ++b) {
      if (bones[a].boneIndex == bones[b].boneIndex) return "two probe bones resolve to the same bone index";
      if (Length(bones[b].expected - bones[a].expected) <= 5.f * tolerance) {
        return "two probe bones are closer together than 5x the tolerance; pick bones that are further apart";
      }
    }
  }
  return {};
}

std::vector<MatrixCandidate> ScanBufferForMatrixArray(std::span<const uint8_t> buffer,
                                                      std::span<const ProbeBone> bones,
                                                      std::span<const uint32_t> strides,
                                                      float tolerance, MatchMode mode,
                                                      size_t maxResults) {
  std::vector<MatrixCandidate> results;
  if (!ValidateProbeBones(bones, tolerance, mode).empty() || maxResults == 0) return results;

  const size_t count = bones.size();
  std::array<Vec3, kMaxProbeBones> expectedDelta{};
  for (size_t j = 1; j < count; ++j) expectedDelta[j] = bones[j].expected - bones[0].expected;

  for (size_t offset = 0; offset + sizeof(Vec3) <= buffer.size(); offset += 4) {
    Vec3 first;
    if (!LoadVec3(buffer, static_cast<int64_t>(offset), first)) continue;

    for (const uint32_t stride : strides) {
      if (stride == 0) continue;
      MatrixCandidate candidate;
      candidate.observed[0] = first;
      bool ok = true;
      for (size_t j = 1; j < count && ok; ++j) {
        const int64_t delta = static_cast<int64_t>(bones[j].boneIndex - bones[0].boneIndex) * stride;
        ok = LoadVec3(buffer, static_cast<int64_t>(offset) + delta, candidate.observed[j]);
        if (ok && mode == MatchMode::Components) {
          ok = NearlyEqual(candidate.observed[j] - first, expectedDelta[j], tolerance);
        }
      }
      if (ok && mode == MatchMode::Distances) {
        ok = DistancesAgree(candidate.observed, bones, count, tolerance);
      }
      if (!ok) continue;

      candidate.bufferOffset = offset;
      candidate.stride = stride;
      candidate.count = count;
      results.push_back(candidate);
      if (results.size() >= maxResults) return results;
    }
  }
  return results;
}

ProbeReport RunLayoutProbe(const IMemory& memory, uintptr_t root, std::span<const ProbeBone> bones,
                           const ProbeOptions& options) {
  ProbeReport report;
  report.error = ValidateProbeBones(bones, options.tolerance, options.mode);
  if (!report.error.empty()) return report;
  if (!IsPlausibleUserPointer(root)) {
    report.error = "root address is not a plausible user-mode pointer";
    return report;
  }
  if (options.strides.empty()) {
    report.error = "no candidate strides";
    return report;
  }

  struct Node {
    uintptr_t address;
    std::vector<uint32_t> chain;
  };
  std::deque<Node> queue;
  std::unordered_set<uintptr_t> seen;
  queue.push_back({root, {}});
  seen.insert(root);
  const size_t queueCap = static_cast<size_t>(options.maxWindows) * 4;

  std::vector<uint8_t> buffer;
  while (!queue.empty()) {
    if (report.windowsScanned >= options.maxWindows) {
      report.windowCapReached = true;
      break;
    }
    Node node = std::move(queue.front());
    queue.pop_front();

    const size_t wanted = node.chain.empty() ? options.rootWindowBytes : options.windowBytes;
    if (!ReadWindow(memory, node.address, wanted, buffer)) {
      ++report.unreadableWindows;
      continue;
    }
    ++report.windowsScanned;

    const size_t room = options.maxHits - report.hits.size();
    for (const MatrixCandidate& c : ScanBufferForMatrixArray(buffer, bones, options.strides,
                                                             options.tolerance, options.mode, room)) {
      ProbeHit hit;
      hit.chain = node.chain;
      hit.bone0TranslationOffset = static_cast<uint32_t>(c.bufferOffset);
      hit.stride = c.stride;
      hit.absoluteAddress = node.address + c.bufferOffset;
      hit.observed = c.observed;
      hit.count = c.count;
      report.hits.push_back(std::move(hit));
    }
    if (report.hits.size() >= options.maxHits) break;

    if (node.chain.size() >= options.maxDepth) continue;
    for (size_t slot = 0; slot + sizeof(uint64_t) <= buffer.size(); slot += sizeof(uint64_t)) {
      uint64_t value;
      std::memcpy(&value, buffer.data() + slot, sizeof(value));
      if (!IsPlausibleUserPointer(value) || (value & 7u) != 0) continue;
      if (queue.size() >= queueCap) break;
      if (!seen.insert(static_cast<uintptr_t>(value)).second) continue;
      Node next{static_cast<uintptr_t>(value), node.chain};
      next.chain.push_back(static_cast<uint32_t>(slot));
      queue.push_back(std::move(next));
    }
  }
  return report;
}

std::optional<uint32_t> LayoutOffsetForBoneZero(const ProbeHit& hit, int32_t firstProbeBoneIndex) {
  if (firstProbeBoneIndex < 0) return std::nullopt;
  const int64_t back = static_cast<int64_t>(firstProbeBoneIndex) * hit.stride;
  const int64_t offset = static_cast<int64_t>(hit.bone0TranslationOffset) - back;
  if (offset < 0 || offset > 0xFFFFFFFFll) return std::nullopt;
  return static_cast<uint32_t>(offset);
}

}  // namespace jiggle
