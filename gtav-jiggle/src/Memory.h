#pragma once

#include <cstddef>
#include <cstdint>

namespace jiggle {

// All-or-nothing memory access: a partial transfer is a failure.
class IMemory {
 public:
  virtual ~IMemory() = default;
  virtual bool Read(uintptr_t address, void* destination, size_t size) const = 0;
  virtual bool Write(uintptr_t address, const void* source, size_t size) = 0;
};

// User-mode x64 canonical range, minus the first 64 KiB.
inline bool IsPlausibleUserPointer(uint64_t p) {
  return p >= 0x10000ull && p <= 0x00007FFFFFFEFFFFull;
}

template <class T>
bool ReadValue(const IMemory& memory, uintptr_t address, T& out) {
  return memory.Read(address, &out, sizeof(T));
}

}  // namespace jiggle
