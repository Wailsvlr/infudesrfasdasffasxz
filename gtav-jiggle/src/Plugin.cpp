// Script Hook V plugin (.asi). Single-threaded: everything runs on the script fiber.
//
// No inline hooks and no code patches: the only writes are 12-byte translation
// stores through SkeletonAccess, and the game rewrites that memory on its own
// schedule, so unloading cannot leave anything dangling.

#ifndef NOMINMAX
#define NOMINMAX  // windows.h min/max macros break std::min/std::max
#endif
#include <windows.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <cstdio>
#include <exception>
#include <memory>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "main.h"
#include "natives.h"
#include "types.h"

#include "JiggleMath.h"
#include "LayoutProbe.h"
#include "Settings.h"
#include "SkeletonAccess.h"
#include "Win32Support.h"

namespace jiggle {
namespace {

constexpr int kMaxWorldPeds = 256;
constexpr size_t kMaxModelCache = 512;
constexpr size_t kMaxLoggedHits = 16;
constexpr const char* kIniName = "Jiggle.ini";
constexpr const char* kLogName = "Jiggle.log";

Vec3 ToVec3(const Vector3& v) { return {v.x, v.y, v.z}; }

uintptr_t BaseAddressOf(Ped ped) {
  return reinterpret_cast<uintptr_t>(getScriptHandleBaseAddress(static_cast<int>(ped)));
}

unsigned long long U64(uintptr_t v) { return static_cast<unsigned long long>(v); }

std::string ChainToString(const std::vector<uint32_t>& chain) {
  if (chain.empty()) return "none";
  std::string out;
  for (size_t i = 0; i < chain.size(); ++i) {
    char buf[16];
    std::snprintf(buf, sizeof(buf), "%s0x%X", i == 0 ? "" : ",", chain[i]);
    out += buf;
  }
  return out;
}

struct BoneRuntime {
  size_t configIndex{0};
  int32_t index{-1};  // skeleton bone index on this ped model, -1 = bone not present
  SpringState spring;
  AppliedState applied;
};

struct PedRuntime {
  uintptr_t base{0};
  uint32_t model{0};
  uint64_t seenFrame{0};
  std::vector<BoneRuntime> bones;
};

class Plugin {
 public:
  explicit Plugin(HMODULE module)
      : directory_(win::ModuleDirectory(module)), log_(directory_ / kLogName) {
    QueryPerformanceFrequency(&frequency_);
    LoadConfiguration();
  }

  [[noreturn]] void Run() {
    for (;;) {
      try {
        Tick();
      } catch (const std::exception& e) {
        log_.Linef("tick threw '%s'; jiggle disabled until toggled or reloaded", e.what());
        enabled_ = false;
        peds_.clear();
      }
      scriptWait(0);
    }
  }

 private:
  // ---- configuration -------------------------------------------------------

  void LoadConfiguration() {
    RestoreAll();

    const auto text = win::ReadTextFile(directory_ / kIniName);
    if (!text) log_.Linef("%s not found next to the .asi; defaults only, feature stays gated", kIniName);
    LoadResult loaded = LoadSettings(text.value_or(std::string()));
    for (const std::string& warning : loaded.warnings) log_.Linef("config: %s", warning.c_str());
    settings_ = std::move(loaded.settings);

    mainModule_ = win::ReadMainModuleInfo(memory_);
    if (mainModule_) {
      log_.Linef("main module: %ls  base=0x%llX  SizeOfImage=0x%X  TimeDateStamp=0x%X",
                 mainModule_->path.c_str(), U64(mainModule_->base), mainModule_->fingerprint.imageSize,
                 mainModule_->fingerprint.timeDateStamp);
    } else {
      log_.Line("main module: PE headers unreadable");
    }

    gate_ = EvaluateGate(settings_, mainModule_ ? mainModule_->fingerprint : ModuleFingerprint{});
    if (!mainModule_) {
      gate_.open = false;
      gate_.reasons.emplace_back("main module PE headers unreadable");
    }
    ReportGate();

    skeleton_ = std::make_unique<SkeletonAccess>(memory_, settings_.layout);
    peds_.clear();
    modelBones_.clear();
    enabled_ = settings_.startEnabled;
  }

  void ReportGate() {
    if (gate_.open) {
      log_.Line("gate: open");
      return;
    }
    for (const std::string& reason : gate_.reasons) log_.Linef("gate: closed - %s", reason.c_str());
  }

  // ---- per tick ------------------------------------------------------------

  float ConsumeFrameSeconds() {
    LARGE_INTEGER now;
    QueryPerformanceCounter(&now);
    float dt = 1.f / 60.f;
    if (lastCounter_.QuadPart != 0 && frequency_.QuadPart > 0) {
      dt = static_cast<float>(static_cast<double>(now.QuadPart - lastCounter_.QuadPart) /
                              static_cast<double>(frequency_.QuadPart));
    }
    lastCounter_ = now;
    return dt;
  }

  static bool IsDown(int vk) { return (GetAsyncKeyState(vk) & 0x8000) != 0; }

  static bool Edge(int vk, bool focused, bool& wasDown) {
    const bool down = focused && IsDown(vk);
    const bool edge = down && !wasDown;
    wasDown = down;
    return edge;
  }

  void Tick() {
    const float dt = ConsumeFrameSeconds();
    const bool focused = win::IsOwnProcessForeground();
    const Hotkeys& keys = settings_.hotkeys;

    const bool toggle = Edge(keys.toggle, focused, wasDown_[0]);
    const bool probe = Edge(keys.probe, focused, wasDown_[1]);
    const bool reload = Edge(keys.reload, focused, wasDown_[2]);
    nudgeHeld_ = focused && IsDown(keys.nudge);

    if (reload) {
      LoadConfiguration();
      log_.Line("configuration reloaded");
      MessageBeep(MB_OK);
      return;
    }
    if (toggle) ToggleEnabled();
    if (probe) RunProbe();

    if (!enabled_ || !gate_.open) return;
    Simulate(dt);
  }

  void ToggleEnabled() {
    enabled_ = !enabled_;
    if (!enabled_) {
      RestoreAll();
      log_.Line("jiggle: off");
      MessageBeep(MB_ICONASTERISK);
      return;
    }
    if (!gate_.open) {
      log_.Line("jiggle: on, but the gate is closed (nothing is written):");
      ReportGate();
      MessageBeep(MB_ICONHAND);
      return;
    }
    log_.Line("jiggle: on");
    MessageBeep(MB_OK);
  }

  void Simulate(float dt) {
    const Ped player = PLAYER::PLAYER_PED_ID();
    if (!ENTITY::DOES_ENTITY_EXIST(player)) {
      peds_.clear();
      return;
    }
    ++frame_;
    for (const Ped ped : CollectPeds(player)) ProcessPed(ped, dt);
    ReleaseStale();
  }

  std::vector<Ped> CollectPeds(Ped player) {
    std::vector<Ped> result{player};
    if (!settings_.scope.includeNearbyPeds || settings_.scope.maxPeds <= 1) return result;

    const Vec3 origin = ToVec3(ENTITY::GET_ENTITY_COORDS(player, TRUE));
    if (!IsFinite(origin)) return result;

    std::array<int, kMaxWorldPeds> handles{};
    const int count = std::clamp(worldGetAllPeds(handles.data(), kMaxWorldPeds), 0, kMaxWorldPeds);

    std::vector<std::pair<float, Ped>> candidates;
    for (int i = 0; i < count; ++i) {
      const Ped ped = static_cast<Ped>(handles[static_cast<size_t>(i)]);
      if (ped == player || !ENTITY::DOES_ENTITY_EXIST(ped)) continue;
      const Vec3 position = ToVec3(ENTITY::GET_ENTITY_COORDS(ped, TRUE));
      if (!IsFinite(position)) continue;
      const float distance = Length(position - origin);
      if (distance <= settings_.scope.maxDistance) candidates.emplace_back(distance, ped);
    }
    std::sort(candidates.begin(), candidates.end());
    const size_t room = std::min<size_t>(candidates.size(), settings_.scope.maxPeds - 1);
    for (size_t i = 0; i < room; ++i) result.push_back(candidates[i].second);
    return result;
  }

  std::vector<int32_t> BoneIndicesFor(Ped ped, uint32_t model) {
    const auto cached = modelBones_.find(model);
    if (cached != modelBones_.end()) return cached->second;
    if (modelBones_.size() >= kMaxModelCache) modelBones_.clear();

    std::vector<int32_t> indices;
    indices.reserve(settings_.bones.size());
    for (const BoneSettings& bone : settings_.bones) {
      std::string name = bone.boneName;
      const int index = ENTITY::GET_ENTITY_BONE_INDEX_BY_NAME(ped, name.data());
      const bool usable = index >= 0 && static_cast<uint32_t>(index) <= *settings_.layout.maxBoneIndex;
      indices.push_back(usable ? static_cast<int32_t>(index) : -1);
    }
    modelBones_[model] = indices;
    return indices;
  }

  PedRuntime BuildRuntime(Ped ped, uintptr_t base, uint32_t model) {
    PedRuntime rt;
    rt.base = base;
    rt.model = model;
    const std::vector<int32_t> indices = BoneIndicesFor(ped, model);
    for (size_t i = 0; i < indices.size(); ++i) {
      if (indices[i] < 0) continue;
      BoneRuntime bone;
      bone.configIndex = i;
      bone.index = indices[i];
      rt.bones.push_back(bone);
    }
    return rt;
  }

  void ProcessPed(Ped ped, float dt) {
    if (PED::IS_PED_RAGDOLL(ped)) {
      ReleasePed(ped);
      return;
    }
    const uintptr_t base = BaseAddressOf(ped);
    if (!IsPlausibleUserPointer(base)) return;
    const uint32_t model = static_cast<uint32_t>(ENTITY::GET_ENTITY_MODEL(ped));

    PedRuntime& rt = peds_[ped];
    if (rt.base != base || rt.model != model) rt = BuildRuntime(ped, base, model);
    rt.seenFrame = frame_;

    Frame frame;
    if (!MakeUprightFrame(ToVec3(ENTITY::GET_ENTITY_FORWARD_VECTOR(ped)), frame)) return;
    for (BoneRuntime& bone : rt.bones) ProcessBone(ped, rt, bone, frame, dt);
  }

  void ProcessBone(Ped ped, const PedRuntime& rt, BoneRuntime& bone, const Frame& frame, float dt) {
    const BoneSettings& config = settings_.bones[bone.configIndex];

    Vec3 current;
    if (!skeleton_->Read(rt.base, bone.index, current)) {
      bone.applied.valid = false;
      bone.spring = SpringState{};
      return;
    }

    Vec3 anchor = ToVec3(ENTITY::GET_WORLD_POSITION_OF_ENTITY_BONE(ped, bone.index));
    if (!IsFinite(anchor)) return;

    // If our previous write is still in memory, both the stored translation and the
    // native's world position include our offset; strip it to get the animated pose.
    const bool ours = IsOurWritePresent(current, bone.applied);
    const Vec3 rest = RestFromCurrent(current, bone.applied);
    if (ours) anchor = anchor - LocalToWorld(frame, bone.applied.lastOffset);

    Vec3 offset;
    if (nudgeHeld_) {
      offset = {0.f, 0.f, settings_.nudgeMeters};
    } else {
      const Vec3 lag = AdvanceSpring(bone.spring, config.spring, anchor, dt);
      offset = ShapeDisplacement(frame, lag, config.shape);
    }

    const Vec3 target = rest + offset;
    if (skeleton_->Write(rt.base, bone.index, target)) {
      bone.applied = AppliedState{target, offset, true};
    } else {
      bone.applied.valid = false;
    }
  }

  // ---- restore / release ---------------------------------------------------

  void RestorePed(Ped ped, PedRuntime& rt) {
    if (!skeleton_ || !ENTITY::DOES_ENTITY_EXIST(ped) || BaseAddressOf(ped) != rt.base) return;
    for (BoneRuntime& bone : rt.bones) {
      Vec3 current;
      if (bone.applied.valid && skeleton_->Read(rt.base, bone.index, current) &&
          IsOurWritePresent(current, bone.applied)) {
        skeleton_->Write(rt.base, bone.index, current - bone.applied.lastOffset);
      }
      bone.applied.valid = false;
      bone.spring = SpringState{};
    }
  }

  void ReleasePed(Ped ped) {
    const auto it = peds_.find(ped);
    if (it == peds_.end()) return;
    RestorePed(ped, it->second);
    peds_.erase(it);
  }

  void ReleaseStale() {
    for (auto it = peds_.begin(); it != peds_.end();) {
      if (it->second.seenFrame != frame_) {
        RestorePed(it->first, it->second);
        it = peds_.erase(it);
      } else {
        ++it;
      }
    }
  }

  void RestoreAll() {
    for (auto& [ped, rt] : peds_) RestorePed(ped, rt);
    peds_.clear();
  }

  // ---- layout probe --------------------------------------------------------

  void ProbeFailed(const char* why) {
    log_.Linef("probe: %s", why);
    MessageBeep(MB_ICONHAND);
  }

  void RunProbe() {
    const std::vector<std::string>& names = settings_.probe.boneNames;
    if (names.size() < 2) {
      ProbeFailed("set [Probe] Bones= to at least two bone names that exist on the player ped model");
      return;
    }
    const Ped player = PLAYER::PLAYER_PED_ID();
    if (!ENTITY::DOES_ENTITY_EXIST(player)) return ProbeFailed("no player ped");
    const uintptr_t base = BaseAddressOf(player);
    if (!IsPlausibleUserPointer(base)) return ProbeFailed("player ped base address is not plausible");

    const Vec3 forward = ToVec3(ENTITY::GET_ENTITY_FORWARD_VECTOR(player));
    Frame frame;
    if (!MakeUprightFrame(forward, frame)) return ProbeFailed("player forward vector unusable (stand upright, not ragdolled)");

    log_.Linef("probe: ped=%u base=0x%llX model=0x%X forward=(%.3f, %.3f, %.3f)", static_cast<unsigned>(player), U64(base),
               static_cast<uint32_t>(ENTITY::GET_ENTITY_MODEL(player)), forward.x, forward.y, forward.z);

    std::vector<ProbeBone> bones;
    for (const std::string& name : names) {
      std::string mutableName = name;
      const int index = ENTITY::GET_ENTITY_BONE_INDEX_BY_NAME(player, mutableName.data());
      if (index < 0) {
        log_.Linef("probe: bone '%s' does not exist on the player ped model", name.c_str());
        return ProbeFailed("fix [Probe] Bones=");
      }
      const Vec3 world = ToVec3(ENTITY::GET_WORLD_POSITION_OF_ENTITY_BONE(player, index));
      if (!IsFinite(world)) return ProbeFailed("native returned a non-finite bone position");
      const Vec3 local = WorldToLocal(frame, world);
      log_.Linef("probe: bone '%s' index=%d world=(%.4f, %.4f, %.4f) local=(%.4f, %.4f, %.4f)",
                 name.c_str(), index, world.x, world.y, world.z, local.x, local.y, local.z);
      bones.push_back(ProbeBone{static_cast<int32_t>(index), local});
    }

    ProbeOptions options;
    options.rootWindowBytes = settings_.probe.rootWindowBytes;
    options.windowBytes = settings_.probe.windowBytes;
    options.maxDepth = settings_.probe.maxDepth;
    options.maxWindows = settings_.probe.maxWindows;
    options.tolerance = settings_.probe.tolerance;
    options.strides = settings_.probe.strides;

    const ULONGLONG started = GetTickCount64();
    options.mode = MatchMode::Components;
    ProbeReport report = RunLayoutProbe(memory_, base, bones, options);
    const char* modeName = "components";
    if (report.error.empty() && report.hits.empty() && bones.size() >= 3) {
      options.mode = MatchMode::Distances;
      report = RunLayoutProbe(memory_, base, bones, options);
      modeName = "distances (axes may differ from right/forward/up)";
    }
    const ULONGLONG elapsed = GetTickCount64() - started;

    if (!report.error.empty()) return ProbeFailed(report.error.c_str());

    log_.Linef("probe: %zu hit(s), %zu windows scanned, %zu unreadable, cap %s, mode %s, %llu ms",
               report.hits.size(), report.windowsScanned, report.unreadableWindows,
               report.windowCapReached ? "REACHED" : "not reached", modeName,
               static_cast<unsigned long long>(elapsed));

    for (size_t h = 0; h < report.hits.size() && h < kMaxLoggedHits; ++h) {
      const ProbeHit& hit = report.hits[h];
      const std::optional<uint32_t> zero = LayoutOffsetForBoneZero(hit, bones[0].boneIndex);
      char zeroText[32] = "(negative, not expressible)";
      if (zero) std::snprintf(zeroText, sizeof(zeroText), "0x%X", *zero);
      log_.Linef("probe: hit %zu  chain=%s  seen at 0x%llX  stride=%u  Bone0TranslationOffset=%s", h,
                 ChainToString(hit.chain).c_str(), U64(hit.absoluteAddress), hit.stride, zeroText);
      for (size_t j = 1; j < hit.count; ++j) {
        const Vec3 seen = hit.observed[j] - hit.observed[0];
        const Vec3 want = bones[j].expected - bones[0].expected;
        log_.Linef("probe:   bone '%s' memory delta=(%.4f, %.4f, %.4f)  natives delta=(%.4f, %.4f, %.4f)",
                   names[j].c_str(), seen.x, seen.y, seen.z, want.x, want.y, want.z);
      }
    }

    if (report.hits.empty()) {
      log_.Line("probe: no match. See README 'If the probe finds nothing'.");
      MessageBeep(MB_ICONHAND);
    } else {
      log_.Line("probe: copy a hit into [Skeleton] (Chain, Bone0TranslationOffset, MatrixStride) and set MaxBoneIndex");
      MessageBeep(MB_OK);
    }
  }

  // ---- state ---------------------------------------------------------------

  std::filesystem::path directory_;
  win::Logger log_;
  win::SelfMemory memory_;
  std::optional<win::MainModuleInfo> mainModule_;
  Settings settings_;
  GateResult gate_;
  std::unique_ptr<SkeletonAccess> skeleton_;
  bool enabled_{false};
  bool nudgeHeld_{false};
  std::array<bool, 3> wasDown_{};
  uint64_t frame_{0};
  LARGE_INTEGER frequency_{};
  LARGE_INTEGER lastCounter_{};
  std::unordered_map<Ped, PedRuntime> peds_;
  std::unordered_map<uint32_t, std::vector<int32_t>> modelBones_;
};

HMODULE g_module = nullptr;

}  // namespace
}  // namespace jiggle

void ScriptMain() {
  jiggle::Plugin plugin(jiggle::g_module);
  plugin.Run();
}

BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID) {
  switch (reason) {
    case DLL_PROCESS_ATTACH:
      jiggle::g_module = module;
      DisableThreadLibraryCalls(module);
      scriptRegister(module, &ScriptMain);
      break;
    case DLL_PROCESS_DETACH:
      scriptUnregister(module);
      break;
    default:
      break;
  }
  return TRUE;
}
