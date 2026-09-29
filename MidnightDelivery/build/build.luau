--!strict
-- Builds MidnightDelivery.rbxlx from the map builders, UI builder and src/ scripts.
-- Run from the MidnightDelivery folder:
--
--   lune run build/build.luau                 writes MidnightDelivery.rbxlx
--   lune run build/build.luau --dump <dir>    also writes scene.json / ui.json for previews

local fs = require("@lune/fs")
local process = require("@lune/process")
local roblox = require("@lune/roblox")

local B = require("./lib/Builder")
local Dump = require("./lib/Dump")
local Place = require("./Place")

local OUTPUT = "MidnightDelivery.rbxlx"

-- The bundled API database (0.728) writes UICorner as four per-corner radii. Older Studio
-- builds only know the classic single CornerRadius, which every Studio still reads, so the
-- file keeps the classic form for compatibility.
local function classicCorners(xml: string): string
	return (string.gsub(xml, '(<Item class="UICorner" referent="[^"]*">.-</Item>)', function(item)
		local radii = {}
		for name, scale, offset in string.gmatch(item, '<UDim name="(%a+)Radius">%s*<S>([^<]*)</S>%s*<O>([^<]*)</O>%s*</UDim>') do
			radii[name] = scale .. "|" .. offset
		end
		local value = radii.TopLeft
		assert(value and radii.TopRight == value and radii.BottomLeft == value and radii.BottomRight == value, "UICorner radii differ")
		local scale, offset = string.match(value, "([^|]*)|(.*)")
		local first = true
		return (string.gsub(item, '(%s*)<UDim name="%a+Radius">%s*<S>[^<]*</S>%s*<O>[^<]*</O>%s*</UDim>', function(indent)
			if first then
				first = false
				return string.format('%s<UDim name="CornerRadius">%s<S>%s</S>%s<O>%s</O>%s</UDim>', indent, indent .. "  ", scale, indent .. "  ", offset, indent)
			end
			return ""
		end))
	end))
end

local game = Place.make()
fs.writeFile(OUTPUT, classicCorners(roblox.serializePlace(game, true)))
print(string.format("wrote %s (%d property assignments recorded)", OUTPUT, #B.records))

local dumpIndex = table.find(process.args, "--dump")
if dumpIndex then
	local dir = process.args[dumpIndex + 1]
	fs.writeDir(dir)
	fs.writeFile(dir .. "/scene.json", Dump.scene(game:GetService("Workspace")))
	local templates = game:GetService("ReplicatedStorage"):FindFirstChild("MidnightDelivery"):FindFirstChild("UI")
	fs.writeFile(dir .. "/ui.json", Dump.ui(game:GetService("StarterGui"), templates))
	print("wrote preview data to " .. dir)
end
