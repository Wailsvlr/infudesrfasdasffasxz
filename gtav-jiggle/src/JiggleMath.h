#pragma once

// Pure math for the jiggle solver. No game, no Windows.
//
// Coordinate system: GTA world space, +Z up, right-handed, metres.
// Frame: orthonormal basis built from the entity's forward vector with the
// up axis pinned to world +Z (so right x forward = up). Angles are never used.

#include <algorithm>
#include <cmath>

namespace jiggle {

struct Vec3 {
  float x{0.f};
  float y{0.f};
  float z{0.f};
};

constexpr Vec3 operator+(Vec3 a, Vec3 b) { return {a.x + b.x, a.y + b.y, a.z + b.z}; }
constexpr Vec3 operator-(Vec3 a, Vec3 b) { return {a.x - b.x, a.y - b.y, a.z - b.z}; }
constexpr Vec3 operator*(Vec3 a, float s) { return {a.x * s, a.y * s, a.z * s}; }
constexpr float Dot(Vec3 a, Vec3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }

inline float Length(Vec3 a) { return std::sqrt(Dot(a, a)); }

inline bool IsFinite(Vec3 a) {
  return std::isfinite(a.x) && std::isfinite(a.y) && std::isfinite(a.z);
}

inline bool NearlyEqual(Vec3 a, Vec3 b, float eps) {
  return std::fabs(a.x - b.x) <= eps && std::fabs(a.y - b.y) <= eps &&
         std::fabs(a.z - b.z) <= eps;
}

inline Vec3 ClampLength(Vec3 v, float maxLength) {
  if (!(maxLength > 0.f)) return {};
  const float len = Length(v);
  if (len <= maxLength || len <= 0.f) return v;
  return v * (maxLength / len);
}

struct Frame {
  Vec3 right{1.f, 0.f, 0.f};
  Vec3 forward{0.f, 1.f, 0.f};
  Vec3 up{0.f, 0.f, 1.f};
};

// Fails for non-finite input or a (nearly) vertical forward vector.
inline bool MakeUprightFrame(Vec3 forwardWorld, Frame& out) {
  if (!IsFinite(forwardWorld)) return false;
  const float len = std::hypot(forwardWorld.x, forwardWorld.y);
  if (len < 1e-3f) return false;
  const Vec3 f{forwardWorld.x / len, forwardWorld.y / len, 0.f};
  out.forward = f;
  out.right = {f.y, -f.x, 0.f};
  out.up = {0.f, 0.f, 1.f};
  return true;
}

inline Vec3 WorldToLocal(const Frame& f, Vec3 world) {
  return {Dot(world, f.right), Dot(world, f.forward), Dot(world, f.up)};
}

inline Vec3 LocalToWorld(const Frame& f, Vec3 local) {
  return f.right * local.x + f.forward * local.y + f.up * local.z;
}

// ---------------------------------------------------------------------------
// Follow spring with velocity-relative damping.
//
// A virtual mass P is pulled toward the animated anchor A (the bone position
// the game computed this frame). Damping acts on the velocity *relative to the
// anchor's velocity*, so a bone moving at constant speed has zero steady-state
// lag; only acceleration, stops and direction changes make it wobble.
// The returned lag is P - A in world space.
// ---------------------------------------------------------------------------

struct SpringParams {
  float frequencyHz{5.f};
  float dampingRatio{0.25f};
  float teleportDistance{2.5f};  // anchor jump per update that resets the state
};

struct SpringState {
  Vec3 position{};
  Vec3 velocity{};
  Vec3 previousAnchor{};
  bool primed{false};
};

inline constexpr float kMaxFrameDt = 0.1f;
inline constexpr float kMaxSubstep = 1.f / 240.f;
inline constexpr int kMaxSubsteps = 32;

inline SpringParams SanitizeSpring(SpringParams p) {
  const auto clampOr = [](float v, float lo, float hi, float fallback) {
    return std::isfinite(v) ? std::clamp(v, lo, hi) : fallback;
  };
  p.frequencyHz = clampOr(p.frequencyHz, 0.5f, 20.f, 5.f);
  p.dampingRatio = clampOr(p.dampingRatio, 0.02f, 1.f, 0.25f);
  p.teleportDistance = clampOr(p.teleportDistance, 0.25f, 100.f, 2.5f);
  return p;
}

inline Vec3 AdvanceSpring(SpringState& s, const SpringParams& raw, Vec3 anchor, float dt) {
  if (!IsFinite(anchor)) {
    s = SpringState{};
    return {};
  }
  if (!std::isfinite(dt) || dt <= 0.f) {
    return s.primed ? s.position - s.previousAnchor : Vec3{};
  }

  const SpringParams p = SanitizeSpring(raw);
  if (!s.primed || Length(anchor - s.previousAnchor) > p.teleportDistance) {
    s = SpringState{};
    s.position = anchor;
    s.previousAnchor = anchor;
    s.primed = true;
    return {};
  }

  dt = std::min(dt, kMaxFrameDt);
  const Vec3 anchorStart = s.previousAnchor;
  const Vec3 anchorVelocity = (anchor - anchorStart) * (1.f / dt);
  s.previousAnchor = anchor;

  constexpr float kTwoPi = 6.28318530717958647692f;
  const float omega = kTwoPi * p.frequencyHz;
  const float stiffness = omega * omega;
  const float damping = 2.f * p.dampingRatio * omega;

  const int steps = std::clamp(static_cast<int>(std::ceil(dt / kMaxSubstep)), 1, kMaxSubsteps);
  const float h = dt / static_cast<float>(steps);
  for (int i = 0; i < steps; ++i) {
    // Anchor at the start of this substep (linear across the frame). Sampling the end of the
    // substep would leave a constant lag of velocity * h under semi-implicit Euler.
    const Vec3 anchorNow = anchorStart + (anchor - anchorStart) * (static_cast<float>(i) / static_cast<float>(steps));
    const Vec3 accel = (anchorNow - s.position) * stiffness - (s.velocity - anchorVelocity) * damping;
    s.velocity = s.velocity + accel * h;
    s.position = s.position + s.velocity * h;
  }

  const Vec3 lag = s.position - anchor;
  if (!IsFinite(lag)) {
    s = SpringState{};
    return {};
  }
  return lag;
}

// ---------------------------------------------------------------------------
// Shaping: world-space lag -> offset in the entity's local axes.
// ---------------------------------------------------------------------------

struct ShapeParams {
  float gain{1.f};
  Vec3 axisGain{1.f, 1.f, 1.f};  // per local axis: right, forward, up
  float maxDisplacement{0.03f};  // metres
};

inline Vec3 ShapeDisplacement(const Frame& frame, Vec3 lagWorld, const ShapeParams& shape) {
  const Vec3 local = WorldToLocal(frame, lagWorld);
  const Vec3 scaled{local.x * shape.axisGain.x * shape.gain,
                    local.y * shape.axisGain.y * shape.gain,
                    local.z * shape.axisGain.z * shape.gain};
  if (!IsFinite(scaled)) return {};
  return ClampLength(scaled, shape.maxDisplacement);
}

// ---------------------------------------------------------------------------
// Write bookkeeping. The game may or may not refresh the matrix between two of
// our writes. If the value in memory still equals what we last wrote, the
// animated rest value is that value minus our last offset; otherwise the game
// already refreshed it and memory holds the rest value.
// ---------------------------------------------------------------------------

struct AppliedState {
  Vec3 lastWritten{};
  Vec3 lastOffset{};
  bool valid{false};
};

inline constexpr float kPresenceEpsilon = 1e-5f;

inline bool IsOurWritePresent(Vec3 current, const AppliedState& a) {
  return a.valid && NearlyEqual(current, a.lastWritten, kPresenceEpsilon);
}

inline Vec3 RestFromCurrent(Vec3 current, const AppliedState& a) {
  return IsOurWritePresent(current, a) ? current - a.lastOffset : current;
}

}  // namespace jiggle
