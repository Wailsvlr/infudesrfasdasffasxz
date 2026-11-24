#include "Win32Support.h"

#include <windows.h>

#include <cstdarg>
#include <cstdio>
#include <sstream>
#include <vector>

namespace jiggle::win {

bool SelfMemory::Read(uintptr_t address, void* destination, size_t size) const {
  if (size == 0 || !IsPlausibleUserPointer(address)) return false;
  SIZE_T transferred = 0;
  const BOOL ok = ReadProcessMemory(GetCurrentProcess(), reinterpret_cast<LPCVOID>(address),
                                    destination, size, &transferred);
  return ok != FALSE && transferred == size;
}

bool SelfMemory::Write(uintptr_t address, const void* source, size_t size) {
  if (size == 0 || !IsPlausibleUserPointer(address)) return false;
  SIZE_T transferred = 0;
  const BOOL ok = WriteProcessMemory(GetCurrentProcess(), reinterpret_cast<LPVOID>(address),
                                     source, size, &transferred);
  return ok != FALSE && transferred == size;
}

std::optional<MainModuleInfo> ReadMainModuleInfo(const IMemory& memory) {
  const HMODULE module = GetModuleHandleW(nullptr);
  if (module == nullptr) return std::nullopt;
  const uintptr_t base = reinterpret_cast<uintptr_t>(module);

  IMAGE_DOS_HEADER dos{};
  if (!memory.Read(base, &dos, sizeof(dos)) || dos.e_magic != IMAGE_DOS_SIGNATURE) return std::nullopt;
  if (dos.e_lfanew < static_cast<LONG>(sizeof(dos)) || dos.e_lfanew > 0x1000) return std::nullopt;

  IMAGE_NT_HEADERS64 nt{};
  if (!memory.Read(base + static_cast<uintptr_t>(dos.e_lfanew), &nt, sizeof(nt))) return std::nullopt;
  if (nt.Signature != IMAGE_NT_SIGNATURE || nt.OptionalHeader.Magic != IMAGE_NT_OPTIONAL_HDR64_MAGIC) {
    return std::nullopt;
  }
  if (nt.OptionalHeader.SizeOfImage == 0) return std::nullopt;

  MainModuleInfo info;
  info.base = base;
  info.fingerprint.imageSize = nt.OptionalHeader.SizeOfImage;
  info.fingerprint.timeDateStamp = nt.FileHeader.TimeDateStamp;

  std::vector<wchar_t> path(32768);
  const DWORD len = GetModuleFileNameW(module, path.data(), static_cast<DWORD>(path.size()));
  if (len > 0 && len < path.size()) info.path.assign(path.data(), len);
  return info;
}

bool IsOwnProcessForeground() {
  const HWND foreground = GetForegroundWindow();
  if (foreground == nullptr) return false;
  DWORD pid = 0;
  GetWindowThreadProcessId(foreground, &pid);
  return pid == GetCurrentProcessId();
}

std::filesystem::path ModuleDirectory(void* module) {
  std::vector<wchar_t> path(32768);
  const DWORD len =
      GetModuleFileNameW(static_cast<HMODULE>(module), path.data(), static_cast<DWORD>(path.size()));
  if (len == 0 || len >= path.size()) return {};
  return std::filesystem::path(std::wstring(path.data(), len)).parent_path();
}

std::optional<std::string> ReadTextFile(const std::filesystem::path& path) {
  std::ifstream in(path, std::ios::binary);
  if (!in) return std::nullopt;
  std::ostringstream buffer;
  buffer << in.rdbuf();
  if (in.bad()) return std::nullopt;
  return buffer.str();
}

Logger::Logger(const std::filesystem::path& file) : stream_(file, std::ios::out | std::ios::trunc) {}

void Logger::Line(std::string_view text) {
  if (!stream_) return;
  SYSTEMTIME t;
  GetLocalTime(&t);
  char stamp[32];
  std::snprintf(stamp, sizeof(stamp), "%02u:%02u:%02u.%03u ", t.wHour, t.wMinute, t.wSecond,
                t.wMilliseconds);
  stream_ << stamp << text << '\n';
  stream_.flush();
}

void Logger::Linef(const char* format, ...) {
  char buffer[1024];
  va_list args;
  va_start(args, format);
  std::vsnprintf(buffer, sizeof(buffer), format, args);
  va_end(args);
  Line(buffer);
}

}  // namespace jiggle::win
