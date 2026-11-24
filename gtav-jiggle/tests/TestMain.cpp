// Checks for everything that does not need the game. Plain asserts, no framework.

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <deque>
#include <fstream>
#include <sstream>
#include <limits>
#include <random>
#include <string>
#include <vector>

#include "IniParser.h"
#include "JiggleMath.h"
#include "LayoutProbe.h"
#include "Memory.h"
#include "Settings.h"
#include "SkeletonAccess.h"

using namespace jiggle;

namespace {

int g_failures = 0;
int g_checks = 0;

#define CHECK(cond)                                                              \
  do {                                                                           \
    ++g_checks;                                                                  \
    if (!(cond)) {                                                               \
      ++g_failures;                                                              \
      std::printf("FAIL %s:%d  %s\n", __FILE__, __LINE__, #cond);                \
    }                                                                            \
  } while (0)

bool Near(float a, float b, float eps = 1e-4f) { return std::fabs(a - b) <= eps; }

class FakeMemory final : public IMemory {
 public:
  struct Region {
    uintptr_t base;
    std::vector<uint8_t> bytes;
  };

  Region& Add(uintptr_t base, size_t size, uint32_t seed) {
    std::mt19937 rng(seed);
    Region r{base, std::vector<uint8_t>(size)};
    for (uint8_t& b : r.bytes) b = static_cast<uint8_t>(rng());
    regions_.push_back(std::move(r));
    return regions_.back();
  }

  Region& At(size_t i) { return regions_[i]; }

  bool Read(uintptr_t address, void* dst, size_t size) const override {
    const Region* r = Find(address, size);
    if (r == nullptr) return false;
    std::memcpy(dst, r->bytes.data() + (address - r->base), size);
    return true;
  }

  bool Write(uintptr_t address, const void* src, size_t size) override {
    Region* r = const_cast<Region*>(Find(address, size));
    if (r == nullptr) return false;
    std::memcpy(r->bytes.data() + (address - r->base), src, size);
    return true;
  }

 private:
  const Region* Find(uintptr_t address, size_t size) const {
    for (const Region& r : regions_) {
      if (address >= r.base && address + size <= r.base + r.bytes.size()) return &r;
    }
    return nullptr;
  }
  std::deque<Region> regions_;
};

template <class T>
void Put(FakeMemory::Region& r, size_t offset, const T& value) {
  std::memcpy(r.bytes.data() + offset, &value, sizeof(T));
}

// ---------------------------------------------------------------------------

void TestIni() {
  const std::string text =
      "\xEF\xBB\xBF; comment\n"
      "[General]\r\n"
      "ToggleKey = 0x75   ; inline comment\r\n"
      "Name=Some Bone\n"
      "[bone.Chest]\n"
      "Name = Foo\n"
      "# another\n"
      "[GENERAL]\n"
      "Extra=1\n";
  const IniDocument doc = ParseIni(text);
  CHECK(doc.sections.size() == 2);
  CHECK(doc.Get("general", "togglekey").value() == "0x75");
  CHECK(doc.Get("general", "name").value() == "Some Bone");
  CHECK(doc.Get("general", "extra").value() == "1");
  CHECK(doc.Get("bone.chest", "name").value() == "Foo");
  CHECK(!doc.Get("general", "missing").has_value());

  CHECK(ParseInteger("0x75").value() == 0x75);
  CHECK(ParseInteger("  -12 ").value() == -12);
  CHECK(!ParseInteger("12abc").has_value());
  CHECK(!ParseInteger("0x").has_value());
  CHECK(!ParseInteger("").has_value());
  CHECK(Near(static_cast<float>(ParseFloat("0.25").value()), 0.25f));
  CHECK(!ParseFloat("nan").has_value());
  CHECK(!ParseFloat("1.0x").has_value());
  CHECK(ParseBool("Yes").value() == true);
  CHECK(!ParseBool("maybe").has_value());
  CHECK(SplitList(" a, b ,,c ", ',').size() == 3);
}

void TestSettings() {
  {
    const LoadResult r = LoadSettings("");
    CHECK(!r.settings.layout.chain.has_value());
    CHECK(!r.settings.layout.Complete());
    CHECK(r.settings.layout.MissingFields().size() == 4);
    CHECK(!r.settings.build.imageSize.has_value());
    CHECK(r.settings.bones.empty());
    CHECK(r.settings.probe.strides.size() == 16);
    const GateResult gate = EvaluateGate(r.settings, {0x1000, 0x2000});
    CHECK(!gate.open);
    CHECK(gate.reasons.size() >= 6);
  }
  {
    const char* ini =
        "[Build]\nExpectedImageSize=0x4000\nExpectedTimeDateStamp=0x65000000\n"
        "[Skeleton]\nChain=0x10, 0x20\nBone0TranslationOffset=0x30\nMatrixStride=48\nMaxBoneIndex=0\n"
        "[Physics]\nFrequencyHz=7\nGain=2\n"
        "[Bone.A]\nName=SomeBone\nDampingRatio=0.5\n"
        "[Bone.B]\nName=\n"
        "[Probe]\nBones=x, y, z\n";
    const LoadResult r = LoadSettings(ini);
    CHECK(r.settings.layout.Complete());
    CHECK(r.settings.layout.maxBoneIndex.value() == 0);  // a real 0, not "unset"
    CHECK(r.settings.layout.chain.value().size() == 2);
    CHECK(r.settings.layout.matrixStride.value() == 48);
    CHECK(r.settings.bones.size() == 1);
    CHECK(Near(r.settings.bones[0].spring.frequencyHz, 7.f));
    CHECK(Near(r.settings.bones[0].spring.dampingRatio, 0.5f));
    CHECK(Near(r.settings.bones[0].shape.gain, 2.f));
    CHECK(r.settings.probe.boneNames.size() == 3);
    CHECK(!r.warnings.empty());  // [Bone.B] has no name

    CHECK(EvaluateGate(r.settings, {0x4000, 0x65000000}).open);
    CHECK(!EvaluateGate(r.settings, {0x4001, 0x65000000}).open);
    CHECK(!EvaluateGate(r.settings, {0x4000, 0x65000001}).open);
  }
  {
    const LoadResult r = LoadSettings("[Skeleton]\nChain=none\nMatrixStride=7\nBone0TranslationOffset=0x31\n");
    CHECK(r.settings.layout.chain.has_value() && r.settings.layout.chain->empty());
    CHECK(!r.settings.layout.matrixStride.has_value());
    CHECK(!r.settings.layout.bone0TranslationOffset.has_value());
    CHECK(r.warnings.size() == 2);
  }
  {
    const LoadResult r = LoadSettings("[Skeleton]\nChain=0x10,banana\n");
    CHECK(!r.settings.layout.chain.has_value());
    CHECK(r.warnings.size() == 1);
  }
}

// The shipped Jiggle.ini must carry no build-dependent value: every one is unknown until captured.
void TestShippedIniHasNoGameValues() {
#ifdef JIGGLE_SOURCE_DIR
  std::ifstream in(std::string(JIGGLE_SOURCE_DIR) + "/Jiggle.ini", std::ios::binary);
  CHECK(static_cast<bool>(in));
  std::ostringstream text;
  text << in.rdbuf();
  const LoadResult r = LoadSettings(text.str());
  CHECK(!r.settings.build.imageSize.has_value());
  CHECK(!r.settings.build.timeDateStamp.has_value());
  CHECK(!r.settings.layout.chain.has_value());
  CHECK(!r.settings.layout.bone0TranslationOffset.has_value());
  CHECK(!r.settings.layout.matrixStride.has_value());
  CHECK(!r.settings.layout.maxBoneIndex.has_value());
  CHECK(r.settings.bones.size() == 2);  // two unverified candidate names, no game values
  CHECK(r.settings.bones[0].boneName == "SPR_L_Breast" && r.settings.bones[1].boneName == "SPR_R_Breast");
  CHECK(r.settings.probe.boneNames.empty());
  CHECK(!EvaluateGate(r.settings, {0x1234, 0x5678}).open);  // layout and fingerprint still unset
#endif
}

void TestAutoConfig() {
  AutoLayout layout;
  layout.chain = {0x40, 0x10};
  layout.bone0TranslationOffset = 0x2A4;
  layout.matrixStride = 48;
  layout.maxBoneIndex = 127;
  layout.imageSize = 0x0123ABCD;
  layout.timeDateStamp = 0x65432100;
  layout.layoutProvenance = "auto: probe hit 1 of 3; note # with ; comment chars";
  layout.buildProvenance = "auto: runtime";
  const std::string autoText = FormatAutoConfig(layout);

  const std::string main =
      "[Build]\nExpectedImageSize=\nProvenance=mine\n"
      "[Skeleton]\nMatrixStride=64\n"
      "[Bone.A]\nName=Foo\n";
  const LoadResult r = LoadSettings(main, autoText);
  CHECK(r.settings.layout.Complete());
  CHECK(r.settings.layout.chain.value() == (std::vector<uint32_t>{0x40, 0x10}));
  CHECK(r.settings.layout.bone0TranslationOffset.value() == 0x2A4);
  CHECK(r.settings.layout.matrixStride.value() == 64);       // value in Jiggle.ini wins over the auto file
  CHECK(r.settings.layout.maxBoneIndex.value() == 127);
  CHECK(r.settings.build.imageSize.value() == 0x0123ABCD);   // blank in Jiggle.ini, filled by the auto file
  CHECK(r.settings.build.timeDateStamp.value() == 0x65432100);
  CHECK(r.settings.build.provenance == "mine");
  CHECK(EvaluateGate(r.settings, {0x0123ABCD, 0x65432100}).open);
  CHECK(!EvaluateGate(r.settings, {0x0123ABCE, 0x65432100}).open);  // different build: closed again

  // No dereference chain round-trips as "none", not as an unset value.
  layout.chain.clear();
  const LoadResult none = LoadSettings("", FormatAutoConfig(layout));
  CHECK(none.settings.layout.chain.has_value() && none.settings.layout.chain->empty());

  // Auto text never touches other sections.
  const LoadResult other = LoadSettings("[Physics]\nGain=3\n", "[Physics]\nGain=9\n[Skeleton]\nMaxBoneIndex=5\n");
  CHECK(Near(other.settings.bones.empty() ? 0.f : 1.f, 0.f));
  CHECK(other.settings.layout.maxBoneIndex.value() == 5);
}

void TestFrame() {
  Frame f;
  CHECK(MakeUprightFrame({0.f, 2.f, 0.5f}, f));
  CHECK(Near(f.forward.y, 1.f) && Near(f.right.x, 1.f) && Near(f.up.z, 1.f));
  CHECK(Near(Dot(f.right, f.forward), 0.f));

  CHECK(MakeUprightFrame({-1.f, 0.f, 0.f}, f));  // facing -X: right must be +Y (z-up, right-handed)
  CHECK(Near(f.right.x, 0.f) && Near(f.right.y, 1.f));

  CHECK(!MakeUprightFrame({0.f, 0.f, 1.f}, f));
  CHECK(!MakeUprightFrame({std::nanf(""), 1.f, 0.f}, f));

  CHECK(MakeUprightFrame({0.6f, 0.8f, 0.f}, f));
  const Vec3 world{1.f, -2.f, 3.f};
  const Vec3 round = LocalToWorld(f, WorldToLocal(f, world));
  CHECK(NearlyEqual(round, world, 1e-5f));
  const Vec3 l = WorldToLocal(f, f.forward * 2.f);
  CHECK(Near(l.y, 2.f) && Near(l.x, 0.f));
}

void TestSpring() {
  SpringParams p;
  p.frequencyHz = 6.f;
  p.dampingRatio = 0.3f;
  const float dt = 1.f / 60.f;

  {  // static anchor: no lag, ever
    SpringState s;
    Vec3 lag;
    for (int i = 0; i < 300; ++i) lag = AdvanceSpring(s, p, {1.f, 2.f, 3.f}, dt);
    CHECK(Length(lag) < 1e-5f);
  }
  {  // constant velocity: lag settles to ~0
    SpringState s;
    Vec3 anchor{0.f, 0.f, 0.f};
    Vec3 lag;
    for (int i = 0; i < 600; ++i) {
      anchor.y += 5.f * dt;
      lag = AdvanceSpring(s, p, anchor, dt);
    }
    CHECK(Length(lag) < 0.01f);
  }
  {  // sudden stop after running: lag appears, points forward (mass keeps going), then decays
    SpringState s;
    Vec3 anchor{};
    for (int i = 0; i < 300; ++i) {
      anchor.y += 5.f * dt;
      AdvanceSpring(s, p, anchor, dt);
    }
    float peak = 0.f;
    Vec3 lag;
    for (int i = 0; i < 40; ++i) {
      lag = AdvanceSpring(s, p, anchor, dt);
      peak = std::max(peak, lag.y);
    }
    CHECK(peak > 0.02f);
    for (int i = 0; i < 600; ++i) lag = AdvanceSpring(s, p, anchor, dt);
    CHECK(Length(lag) < 1e-3f);
  }
  {  // teleport resets
    SpringState s;
    AdvanceSpring(s, p, {0.f, 0.f, 0.f}, dt);
    AdvanceSpring(s, p, {0.f, 0.1f, 0.f}, dt);
    const Vec3 lag = AdvanceSpring(s, p, {100.f, 0.f, 0.f}, dt);
    CHECK(Length(lag) == 0.f);
    CHECK(NearlyEqual(s.position, {100.f, 0.f, 0.f}, 1e-6f));
  }
  {  // hostile input never produces NaN and recovers
    SpringState s;
    AdvanceSpring(s, p, {0.f, 0.f, 0.f}, dt);
    const float nan = std::numeric_limits<float>::quiet_NaN();
    CHECK(Length(AdvanceSpring(s, p, {nan, 0.f, 0.f}, dt)) == 0.f);
    CHECK(!s.primed);
    CHECK(Length(AdvanceSpring(s, p, {1.f, 1.f, 1.f}, nan)) == 0.f);
    CHECK(Length(AdvanceSpring(s, p, {1.f, 1.f, 1.f}, -1.f)) == 0.f);
    Vec3 lag = AdvanceSpring(s, p, {1.f, 1.f, 1.f}, dt);
    CHECK(IsFinite(lag));
  }
  {  // worst-case parameters and a huge frame time stay finite and bounded
    SpringParams hard;
    hard.frequencyHz = 1000.f;     // sanitized down to 20
    hard.dampingRatio = 100.f;     // sanitized down to 1
    SpringState s;
    Vec3 anchor{};
    float peak = 0.f;
    for (int i = 0; i < 2000; ++i) {
      anchor.x = (i % 2 == 0) ? 0.f : 1.f;
      const Vec3 lag = AdvanceSpring(s, hard, anchor, (i % 7 == 0) ? 5.f : 0.016f);
      CHECK(IsFinite(lag));
      peak = std::max(peak, Length(lag));
    }
    CHECK(peak < 10.f);
  }
}

void TestShape() {
  Frame f;
  MakeUprightFrame({0.f, 1.f, 0.f}, f);
  ShapeParams shape;
  shape.gain = 2.f;
  shape.axisGain = {1.f, 0.f, 1.f};
  shape.maxDisplacement = 0.03f;
  const Vec3 small = ShapeDisplacement(f, {0.005f, 0.5f, 0.f}, shape);
  CHECK(Near(small.x, 0.01f) && Near(small.y, 0.f));
  const Vec3 big = ShapeDisplacement(f, {1.f, 0.f, 1.f}, shape);
  CHECK(Near(Length(big), 0.03f));
  shape.maxDisplacement = 0.f;
  CHECK(Length(ShapeDisplacement(f, {1.f, 0.f, 0.f}, shape)) == 0.f);
}

void TestRest() {
  AppliedState a;
  const Vec3 rest{1.f, 2.f, 3.f};
  CHECK(NearlyEqual(RestFromCurrent(rest, a), rest, 0.f));  // nothing applied yet

  a.valid = true;
  a.lastOffset = {0.f, 0.f, 0.02f};
  a.lastWritten = rest + a.lastOffset;
  CHECK(IsOurWritePresent(a.lastWritten, a));
  CHECK(NearlyEqual(RestFromCurrent(a.lastWritten, a), rest, 1e-6f));  // game did not refresh
  CHECK(!IsOurWritePresent(rest, a));
  CHECK(NearlyEqual(RestFromCurrent(rest, a), rest, 0.f));  // game refreshed
}

// ---------------------------------------------------------------------------

struct SyntheticPed {
  FakeMemory memory;
  uintptr_t pedBase = 0x100000000ull;
  uintptr_t block = 0x200000000ull;
  uint32_t arrayInBlock = 0x180;
  uint32_t stride = 48;
  uint32_t pointerSlot = 0x40;
  Vec3 origin{0.f, 0.f, -0.9f};
  std::vector<Vec3> objectPositions;  // by bone index
};

SyntheticPed MakeSyntheticPed() {
  SyntheticPed p;
  auto& ped = p.memory.Add(p.pedBase, 0x2000, 1);
  auto& block = p.memory.Add(p.block, 0x1000, 2);
  Put<uint64_t>(ped, p.pointerSlot, p.block);

  for (int i = 0; i < 40; ++i) {
    p.objectPositions.push_back({0.01f * static_cast<float>(i * i % 17), 0.05f * static_cast<float>(i),
                                 0.02f * static_cast<float>(i % 9)});
  }
  for (size_t i = 0; i < p.objectPositions.size(); ++i) {
    Put(block, p.arrayInBlock + i * p.stride + 36, p.objectPositions[i] + p.origin);  // translation at +36
  }
  return p;
}

void TestProbeAndAccess() {
  SyntheticPed p = MakeSyntheticPed();

  const int32_t idxA = 5, idxB = 20, idxC = 33;
  // The natives report world-ish positions; the probe uses them in (right, forward, up) coordinates.
  const std::vector<ProbeBone> bones = {{idxA, p.objectPositions[idxA]},
                                        {idxB, p.objectPositions[idxB]},
                                        {idxC, p.objectPositions[idxC]}};
  CHECK(ValidateProbeBones(bones, 0.02f, MatchMode::Components).empty());

  ProbeOptions opt;
  for (uint32_t s = 16; s <= 256; s += 16) opt.strides.push_back(s);
  opt.tolerance = 0.02f;

  const ProbeReport report = RunLayoutProbe(p.memory, p.pedBase, bones, opt);
  CHECK(report.error.empty());
  CHECK(report.hits.size() >= 1);
  bool found = false;
  for (const ProbeHit& h : report.hits) {
    if (h.chain.size() == 1 && h.chain[0] == p.pointerSlot && h.stride == p.stride &&
        h.bone0TranslationOffset == p.arrayInBlock + static_cast<uint32_t>(idxA) * p.stride + 36) {
      found = true;
    }
  }
  CHECK(found);
  for (const ProbeHit& h : report.hits) {
    if (h.chain.size() == 1 && h.stride == p.stride) {
      CHECK(LayoutOffsetForBoneZero(h, idxA).value() == p.arrayInBlock + 36);
    }
  }
  ProbeHit tiny;
  tiny.stride = 48;
  tiny.bone0TranslationOffset = 40;
  CHECK(!LayoutOffsetForBoneZero(tiny, 5).has_value());  // would start before the block
  CHECK(!LayoutOffsetForBoneZero(tiny, -1).has_value());

  // The resolved layout must reproduce the planted values and round-trip writes.
  SkeletonLayout layout;
  layout.chain = std::vector<uint32_t>{p.pointerSlot};
  layout.bone0TranslationOffset = p.arrayInBlock + 36;  // bone 0 of the array
  layout.matrixStride = p.stride;
  layout.maxBoneIndex = 39;
  SkeletonAccess access(p.memory, layout);
  CHECK(access.Ready());

  Vec3 read;
  CHECK(access.Read(p.pedBase, idxB, read));
  CHECK(NearlyEqual(read, p.objectPositions[idxB] + p.origin, 1e-6f));
  CHECK(access.Write(p.pedBase, idxB, read + Vec3{0.f, 0.f, 0.02f}));
  Vec3 after;
  CHECK(access.Read(p.pedBase, idxB, after));
  CHECK(NearlyEqual(after, read + Vec3{0.f, 0.f, 0.02f}, 1e-6f));

  // Guards.
  CHECK(!access.Read(p.pedBase, -1, read));
  CHECK(!access.Read(p.pedBase, 40, read));  // above MaxBoneIndex
  CHECK(!access.Read(0, idxA, read));
  CHECK(!access.Write(p.pedBase, idxA, {std::nanf(""), 0.f, 0.f}));
  CHECK(!access.Write(p.pedBase, idxA, {500.f, 0.f, 0.f}));
  CHECK(!access.Read(p.pedBase + 0xFFFFFF0, idxA, read));  // unmapped ped base
}

void TestProbeDistanceModeAndMisses() {
  SyntheticPed p = MakeSyntheticPed();

  // Axes rotated 90 degrees (x->y, y->-x): component matching must fail, distance matching must succeed.
  const auto rot = [](Vec3 v) { return Vec3{v.y, -v.x, v.z}; };
  const std::vector<ProbeBone> bones = {{5, rot(p.objectPositions[5])},
                                        {20, rot(p.objectPositions[20])},
                                        {33, rot(p.objectPositions[33])},
                                        {11, rot(p.objectPositions[11])}};
  ProbeOptions opt;
  for (uint32_t s = 16; s <= 256; s += 16) opt.strides.push_back(s);
  opt.tolerance = 0.01f;

  opt.mode = MatchMode::Components;
  CHECK(RunLayoutProbe(p.memory, p.pedBase, bones, opt).hits.empty());

  opt.mode = MatchMode::Distances;
  const ProbeReport dist = RunLayoutProbe(p.memory, p.pedBase, bones, opt);
  CHECK(dist.error.empty());
  bool found = false;
  for (const ProbeHit& h : dist.hits) {
    if (h.chain.size() == 1 && h.stride == p.stride) found = true;
  }
  CHECK(found);

  // Depth 0 cannot reach the array behind the pointer.
  opt.mode = MatchMode::Distances;
  opt.maxDepth = 0;
  CHECK(RunLayoutProbe(p.memory, p.pedBase, bones, opt).hits.empty());

  // Invalid probe inputs are rejected with a reason, never scanned.
  const std::vector<ProbeBone> same = {{5, {0.f, 0.f, 0.f}}, {5, {1.f, 0.f, 0.f}}};
  CHECK(!ValidateProbeBones(same, 0.02f, MatchMode::Components).empty());
  const std::vector<ProbeBone> close = {{5, {0.f, 0.f, 0.f}}, {6, {0.01f, 0.f, 0.f}}};
  CHECK(!ValidateProbeBones(close, 0.02f, MatchMode::Components).empty());
  const std::vector<ProbeBone> two = {{5, {0.f, 0.f, 0.f}}, {6, {1.f, 0.f, 0.f}}};
  CHECK(!ValidateProbeBones(two, 0.02f, MatchMode::Distances).empty());

  ProbeOptions bad;
  CHECK(!RunLayoutProbe(p.memory, 0, two, bad).error.empty());
  CHECK(!RunLayoutProbe(p.memory, p.pedBase, two, bad).error.empty());  // no strides

  // Unreadable root window: reported, not scanned as zeros.
  ProbeOptions ok;
  ok.strides = {48};
  const ProbeReport unreadable = RunLayoutProbe(p.memory, 0x300000000ull, two, ok);
  CHECK(unreadable.hits.empty() && unreadable.unreadableWindows == 1);
}

}  // namespace

int main() {
  TestIni();
  TestSettings();
  TestShippedIniHasNoGameValues();
  TestAutoConfig();
  TestFrame();
  TestSpring();
  TestShape();
  TestRest();
  TestProbeAndAccess();
  TestProbeDistanceModeAndMisses();

  std::printf("%d checks, %d failures\n", g_checks, g_failures);
  return g_failures == 0 ? 0 : 1;
}
