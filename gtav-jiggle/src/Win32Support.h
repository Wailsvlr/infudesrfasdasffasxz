#pragma once

#include <cstdint>
#include <filesystem>
#include <fstream>
#include <optional>
#include <string>
#include <string_view>

#include "Memory.h"
#include "Settings.h"

namespace jiggle::win {

// Reads and writes this process through ReadProcessMemory / WriteProcessMemory on the
// pseudo-handle, so a bad address fails with an error instead of faulting the game.
class SelfMemory final : public IMemory {
 public:
  bool Read(uintptr_t address, void* destination, size_t size) const override;
  bool Write(uintptr_t address, const void* source, size_t size) override;
};

struct MainModuleInfo {
  uintptr_t base{0};
  std::wstring path;
  ModuleFingerprint fingerprint;
};

// PE headers of the process's main executable, validated before use.
std::optional<MainModuleInfo> ReadMainModuleInfo(const IMemory& memory);

bool IsOwnProcessForeground();

// Directory containing the module handle (an HMODULE passed as void* to keep windows.h out).
std::filesystem::path ModuleDirectory(void* module);

std::optional<std::string> ReadTextFile(const std::filesystem::path& path);

class Logger {
 public:
  explicit Logger(const std::filesystem::path& file);
  void Line(std::string_view text);
  void Linef(const char* format, ...);

 private:
  std::ofstream stream_;
};

}  // namespace jiggle::win
