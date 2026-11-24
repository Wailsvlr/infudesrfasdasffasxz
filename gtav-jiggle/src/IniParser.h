#pragma once

#include <map>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace jiggle {

// Section and key names are stored lowercase; values keep their case.
struct IniSection {
  std::string name;
  std::map<std::string, std::string> values;
};

struct IniDocument {
  std::vector<IniSection> sections;  // file order

  const IniSection* Find(std::string_view lowerName) const;
  std::optional<std::string_view> Get(std::string_view lowerSection, std::string_view lowerKey) const;
};

// ';' and '#' start a comment anywhere on a line. A leading UTF-8 BOM is skipped.
IniDocument ParseIni(std::string_view text);

// Decimal or 0x-prefixed hex, optional leading '-'. Whole string must be consumed.
std::optional<long long> ParseInteger(std::string_view text);
std::optional<double> ParseFloat(std::string_view text);  // finite values only
std::optional<bool> ParseBool(std::string_view text);     // 0/1/true/false/yes/no/on/off

std::vector<std::string> SplitList(std::string_view text, char separator);
std::string ToLower(std::string_view text);
std::string_view Trim(std::string_view text);

}  // namespace jiggle
