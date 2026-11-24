#include "Settings.h"

#include <cmath>
#include <cstdio>

#include "IniParser.h"

namespace jiggle {

namespace {

constexpr long long kMaxOffset = 0x100000;
constexpr long long kMaxStride = 0x1000;
constexpr long long kMaxBoneIndexLimit = 4095;

std::string Hex(uint32_t v) {
  char buf[16];
  std::snprintf(buf, sizeof(buf), "0x%X", v);
  return buf;
}

class Loader {
 public:
  explicit Loader(const IniDocument& doc, std::vector<std::string>& warnings)
      : doc_(doc), warnings_(warnings) {}

  void Warn(const std::string& message) { warnings_.push_back(message); }

  std::optional<std::string_view> Raw(std::string_view section, std::string_view key) const {
    const auto v = doc_.Get(section, key);
    if (!v || v->empty()) return std::nullopt;
    return v;
  }

  std::optional<long long> Int(std::string_view section, std::string_view key, long long lo,
                               long long hi) {
    const auto raw = Raw(section, key);
    if (!raw) return std::nullopt;
    const auto v = ParseInteger(*raw);
    if (!v || *v < lo || *v > hi) {
      Warn("[" + std::string(section) + "] " + std::string(key) + " ignored: '" +
           std::string(*raw) + "' is not an integer in range");
      return std::nullopt;
    }
    return v;
  }

  std::optional<float> Float(std::string_view section, std::string_view key, double lo, double hi) {
    const auto raw = Raw(section, key);
    if (!raw) return std::nullopt;
    const auto v = ParseFloat(*raw);
    if (!v || *v < lo || *v > hi) {
      Warn("[" + std::string(section) + "] " + std::string(key) + " ignored: '" +
           std::string(*raw) + "' is not a number in range");
      return std::nullopt;
    }
    return static_cast<float>(*v);
  }

  std::optional<bool> Bool(std::string_view section, std::string_view key) {
    const auto raw = Raw(section, key);
    if (!raw) return std::nullopt;
    const auto v = ParseBool(*raw);
    if (!v) {
      Warn("[" + std::string(section) + "] " + std::string(key) + " ignored: '" +
           std::string(*raw) + "' is not a boolean");
    }
    return v;
  }

  std::string Text(std::string_view section, std::string_view key) const {
    const auto raw = Raw(section, key);
    return raw ? std::string(*raw) : std::string();
  }

  // Physics keys readable from a section; unset keys keep the incoming values.
  void ReadPhysics(std::string_view section, SpringParams& spring, ShapeParams& shape) {
    if (auto v = Float(section, "frequencyhz", 0.5, 20.0)) spring.frequencyHz = *v;
    if (auto v = Float(section, "dampingratio", 0.02, 1.0)) spring.dampingRatio = *v;
    if (auto v = Float(section, "teleportdistance", 0.25, 100.0)) spring.teleportDistance = *v;
    if (auto v = Float(section, "gain", 0.0, 100.0)) shape.gain = *v;
    if (auto v = Float(section, "maxdisplacement", 0.0, 1.0)) shape.maxDisplacement = *v;
    if (auto v = Float(section, "axisright", -10.0, 10.0)) shape.axisGain.x = *v;
    if (auto v = Float(section, "axisforward", -10.0, 10.0)) shape.axisGain.y = *v;
    if (auto v = Float(section, "axisup", -10.0, 10.0)) shape.axisGain.z = *v;
  }

 private:
  const IniDocument& doc_;
  std::vector<std::string>& warnings_;
};

}  // namespace

std::vector<std::string> SkeletonLayout::MissingFields() const {
  std::vector<std::string> missing;
  if (!chain) missing.emplace_back("Skeleton.Chain");
  if (!bone0TranslationOffset) missing.emplace_back("Skeleton.Bone0TranslationOffset");
  if (!matrixStride) missing.emplace_back("Skeleton.MatrixStride");
  if (!maxBoneIndex) missing.emplace_back("Skeleton.MaxBoneIndex");
  return missing;
}

LoadResult LoadSettings(std::string_view iniText) {
  LoadResult result;
  Settings& s = result.settings;
  const IniDocument doc = ParseIni(iniText);
  Loader in(doc, result.warnings);

  for (uint32_t stride = 16; stride <= 256; stride += 16) s.probe.strides.push_back(stride);

  // [General]
  if (auto v = in.Int("general", "togglekey", 1, 0xFE)) s.hotkeys.toggle = static_cast<int>(*v);
  if (auto v = in.Int("general", "probekey", 1, 0xFE)) s.hotkeys.probe = static_cast<int>(*v);
  if (auto v = in.Int("general", "reloadkey", 1, 0xFE)) s.hotkeys.reload = static_cast<int>(*v);
  if (auto v = in.Int("general", "nudgekey", 1, 0xFE)) s.hotkeys.nudge = static_cast<int>(*v);
  if (auto v = in.Bool("general", "startenabled")) s.startEnabled = *v;
  if (auto v = in.Float("general", "nudgemeters", 0.0, 1.0)) s.nudgeMeters = *v;
  if (auto v = in.Bool("general", "includenearbypeds")) s.scope.includeNearbyPeds = *v;
  if (auto v = in.Int("general", "maxpeds", 1, 64)) s.scope.maxPeds = static_cast<uint32_t>(*v);
  if (auto v = in.Float("general", "maxdistance", 1.0, 500.0)) s.scope.maxDistance = *v;

  // [Build]
  if (auto v = in.Int("build", "expectedimagesize", 1, 0xFFFFFFFFll))
    s.build.imageSize = static_cast<uint32_t>(*v);
  if (auto v = in.Int("build", "expectedtimedatestamp", 1, 0xFFFFFFFFll))
    s.build.timeDateStamp = static_cast<uint32_t>(*v);
  s.build.provenance = in.Text("build", "provenance");

  // [Skeleton]
  if (const auto raw = in.Raw("skeleton", "chain")) {
    if (ToLower(Trim(*raw)) == "none") {
      s.layout.chain = std::vector<uint32_t>{};
    } else {
      std::vector<uint32_t> chain;
      bool ok = true;
      for (const std::string& item : SplitList(*raw, ',')) {
        const auto v = ParseInteger(item);
        if (!v || *v < 0 || *v > kMaxOffset || (*v % 4) != 0) {
          ok = false;
          break;
        }
        chain.push_back(static_cast<uint32_t>(*v));
      }
      if (ok && !chain.empty() && chain.size() <= 8) {
        s.layout.chain = std::move(chain);
      } else {
        in.Warn("[Skeleton] Chain ignored: expected 'none' or 1-8 comma-separated byte offsets "
                "(multiples of 4, <= 0x100000)");
      }
    }
  }
  if (auto v = in.Int("skeleton", "bone0translationoffset", 0, kMaxOffset)) {
    if (*v % 4 == 0) {
      s.layout.bone0TranslationOffset = static_cast<uint32_t>(*v);
    } else {
      in.Warn("[Skeleton] Bone0TranslationOffset ignored: must be a multiple of 4");
    }
  }
  if (auto v = in.Int("skeleton", "matrixstride", 4, kMaxStride)) {
    if (*v % 4 == 0) {
      s.layout.matrixStride = static_cast<uint32_t>(*v);
    } else {
      in.Warn("[Skeleton] MatrixStride ignored: must be a multiple of 4");
    }
  }
  if (auto v = in.Int("skeleton", "maxboneindex", 0, kMaxBoneIndexLimit))
    s.layout.maxBoneIndex = static_cast<uint32_t>(*v);
  s.layout.provenance = in.Text("skeleton", "provenance");

  // [Physics] defaults, then per-bone overrides
  SpringParams defaultSpring;
  ShapeParams defaultShape;
  in.ReadPhysics("physics", defaultSpring, defaultShape);

  for (const IniSection& section : doc.sections) {
    constexpr std::string_view kPrefix = "bone.";
    if (section.name.compare(0, kPrefix.size(), kPrefix) != 0) continue;

    BoneSettings bone;
    bone.label = section.name.substr(kPrefix.size());
    bone.boneName = in.Text(section.name, "name");
    bone.spring = defaultSpring;
    bone.shape = defaultShape;
    in.ReadPhysics(section.name, bone.spring, bone.shape);

    if (bone.boneName.empty()) {
      in.Warn("[" + section.name + "] skipped: Name is not set (bone stays disabled)");
      continue;
    }
    if (s.bones.size() >= 16) {
      in.Warn("[" + section.name + "] skipped: at most 16 bones are supported");
      continue;
    }
    s.bones.push_back(std::move(bone));
  }

  // [Probe]
  s.probe.boneNames = SplitList(in.Text("probe", "bones"), ',');
  if (s.probe.boneNames.size() > 6) {
    in.Warn("[Probe] Bones: only the first 6 names are used");
    s.probe.boneNames.resize(6);
  }
  if (auto v = in.Int("probe", "rootwindowbytes", 0x100, 0x10000))
    s.probe.rootWindowBytes = static_cast<uint32_t>(*v & ~7ll);
  if (auto v = in.Int("probe", "windowbytes", 0x100, 0x10000))
    s.probe.windowBytes = static_cast<uint32_t>(*v & ~7ll);
  if (auto v = in.Int("probe", "maxdepth", 0, 3)) s.probe.maxDepth = static_cast<uint32_t>(*v);
  if (auto v = in.Int("probe", "maxwindows", 1, 200000)) s.probe.maxWindows = static_cast<uint32_t>(*v);
  if (auto v = in.Float("probe", "tolerance", 0.0005, 0.5)) s.probe.tolerance = *v;
  if (const auto raw = in.Raw("probe", "strides")) {
    std::vector<uint32_t> strides;
    bool ok = true;
    for (const std::string& item : SplitList(*raw, ',')) {
      const auto v = ParseInteger(item);
      if (!v || *v < 4 || *v > kMaxStride || (*v % 4) != 0) {
        ok = false;
        break;
      }
      strides.push_back(static_cast<uint32_t>(*v));
    }
    if (ok && !strides.empty() && strides.size() <= 64) {
      s.probe.strides = std::move(strides);
    } else {
      in.Warn("[Probe] Strides ignored: expected up to 64 comma-separated multiples of 4 (<= 0x1000)");
    }
  }

  return result;
}

GateResult EvaluateGate(const Settings& settings, ModuleFingerprint actual) {
  GateResult gate;

  for (const std::string& missing : settings.layout.MissingFields()) {
    gate.reasons.push_back("unset: " + missing);
  }

  if (!settings.build.imageSize) gate.reasons.push_back("unset: Build.ExpectedImageSize");
  if (!settings.build.timeDateStamp) gate.reasons.push_back("unset: Build.ExpectedTimeDateStamp");
  if (settings.build.imageSize && *settings.build.imageSize != actual.imageSize) {
    gate.reasons.push_back("main module SizeOfImage is " + Hex(actual.imageSize) + ", config expects " +
                           Hex(*settings.build.imageSize));
  }
  if (settings.build.timeDateStamp && *settings.build.timeDateStamp != actual.timeDateStamp) {
    gate.reasons.push_back("main module TimeDateStamp is " + Hex(actual.timeDateStamp) +
                           ", config expects " + Hex(*settings.build.timeDateStamp));
  }

  if (settings.bones.empty()) {
    gate.reasons.push_back("no [Bone.*] section has a Name");
  }

  gate.open = gate.reasons.empty();
  return gate;
}

}  // namespace jiggle
