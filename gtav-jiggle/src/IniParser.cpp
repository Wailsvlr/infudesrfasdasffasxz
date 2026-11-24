#include "IniParser.h"

#include <cctype>
#include <charconv>
#include <cmath>
#include <cstdlib>

namespace jiggle {

std::string_view Trim(std::string_view s) {
  const auto isSpace = [](unsigned char c) { return std::isspace(c) != 0; };
  while (!s.empty() && isSpace(static_cast<unsigned char>(s.front()))) s.remove_prefix(1);
  while (!s.empty() && isSpace(static_cast<unsigned char>(s.back()))) s.remove_suffix(1);
  return s;
}

std::string ToLower(std::string_view s) {
  std::string out(s);
  for (char& c : out) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
  return out;
}

const IniSection* IniDocument::Find(std::string_view lowerName) const {
  for (const IniSection& s : sections) {
    if (s.name == lowerName) return &s;
  }
  return nullptr;
}

std::optional<std::string_view> IniDocument::Get(std::string_view lowerSection,
                                                 std::string_view lowerKey) const {
  const IniSection* s = Find(lowerSection);
  if (s == nullptr) return std::nullopt;
  const auto it = s->values.find(std::string(lowerKey));
  if (it == s->values.end()) return std::nullopt;
  return std::string_view(it->second);
}

IniDocument ParseIni(std::string_view text) {
  IniDocument doc;
  if (text.size() >= 3 && static_cast<unsigned char>(text[0]) == 0xEF &&
      static_cast<unsigned char>(text[1]) == 0xBB && static_cast<unsigned char>(text[2]) == 0xBF) {
    text.remove_prefix(3);
  }

  IniSection* current = nullptr;
  while (!text.empty()) {
    const size_t eol = text.find('\n');
    std::string_view line = text.substr(0, eol);
    text = (eol == std::string_view::npos) ? std::string_view{} : text.substr(eol + 1);

    const size_t comment = line.find_first_of(";#");
    if (comment != std::string_view::npos) line = line.substr(0, comment);
    line = Trim(line);
    if (line.empty()) continue;

    if (line.front() == '[') {
      const size_t close = line.find(']');
      if (close == std::string_view::npos) continue;
      const std::string name = ToLower(Trim(line.substr(1, close - 1)));
      IniSection* existing = nullptr;
      for (IniSection& s : doc.sections) {
        if (s.name == name) existing = &s;
      }
      if (existing == nullptr) {
        doc.sections.push_back(IniSection{name, {}});
        existing = &doc.sections.back();
      }
      current = existing;
      continue;
    }

    const size_t eq = line.find('=');
    if (eq == std::string_view::npos || current == nullptr) continue;
    const std::string key = ToLower(Trim(line.substr(0, eq)));
    if (key.empty()) continue;
    current->values[key] = std::string(Trim(line.substr(eq + 1)));
  }
  return doc;
}

std::optional<long long> ParseInteger(std::string_view text) {
  text = Trim(text);
  if (text.empty()) return std::nullopt;

  bool negative = false;
  if (text.front() == '-') {
    negative = true;
    text.remove_prefix(1);
  } else if (text.front() == '+') {
    text.remove_prefix(1);
  }

  int base = 10;
  if (text.size() > 2 && text[0] == '0' && (text[1] == 'x' || text[1] == 'X')) {
    base = 16;
    text.remove_prefix(2);
  }
  if (text.empty()) return std::nullopt;

  unsigned long long magnitude = 0;
  const auto [ptr, ec] = std::from_chars(text.data(), text.data() + text.size(), magnitude, base);
  if (ec != std::errc{} || ptr != text.data() + text.size()) return std::nullopt;
  if (magnitude > 0x7FFFFFFFFFFFFFFFull) return std::nullopt;

  const long long value = static_cast<long long>(magnitude);
  return negative ? -value : value;
}

std::optional<double> ParseFloat(std::string_view text) {
  text = Trim(text);
  if (text.empty()) return std::nullopt;
  const std::string owned(text);
  char* end = nullptr;
  const double v = std::strtod(owned.c_str(), &end);
  if (end != owned.c_str() + owned.size() || !std::isfinite(v)) return std::nullopt;
  return v;
}

std::optional<bool> ParseBool(std::string_view text) {
  const std::string t = ToLower(Trim(text));
  if (t == "1" || t == "true" || t == "yes" || t == "on") return true;
  if (t == "0" || t == "false" || t == "no" || t == "off") return false;
  return std::nullopt;
}

std::vector<std::string> SplitList(std::string_view text, char separator) {
  std::vector<std::string> out;
  while (true) {
    const size_t pos = text.find(separator);
    const std::string_view item = Trim(text.substr(0, pos));
    if (!item.empty()) out.emplace_back(item);
    if (pos == std::string_view::npos) break;
    text.remove_prefix(pos + 1);
  }
  return out;
}

}  // namespace jiggle
