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

local game = Place.make()
fs.writeFile(OUTPUT, roblox.serializePlace(game, true))
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
