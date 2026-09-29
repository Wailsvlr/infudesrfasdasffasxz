--!strict
-- Assembles the whole place (DataModel) in memory. build.luau writes it to disk;
-- tests call it directly.

local roblox = require("@lune/roblox")

local B = require("./lib/Builder")
local Corridor = require("./map/Corridor")
local Elevator = require("./map/Elevator")
local Apartments = require("./map/Apartments")
local Props = require("./map/Props")
local Environment = require("./Environment")
local Assets = require("./Assets")
local Hud = require("./ui/Hud")
local Scripts = require("./Scripts")

local Place = {}

function Place.make(): any
	B.records = {}
	local game = roblox.Instance.new("DataModel")
	local workspace = game:GetService("Workspace")

	local root = B.folder(workspace, "MidnightDelivery")
	local map = B.folder(root, "Map")
	Corridor.build(map)
	Elevator.build(map)
	Apartments.build(map)
	local devOnly = B.folder(root, "_DevOnly")
	Props.build(map, devOnly)

	Environment.build(game)
	local sharedRoot = Assets.build(game)
	Hud.build(game, sharedRoot)
	Scripts.install(game)
	return game
end

return Place
