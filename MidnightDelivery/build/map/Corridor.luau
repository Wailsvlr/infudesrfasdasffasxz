--!strict
-- Corridor shell: floor, ceiling, four walls with openings, trims, runner carpet,
-- repeating ceiling light modules, end window, radiator and a few wear details.

local B = require("../lib/Builder")
local Layout = require("./Layout")
local Palette = require("../../src/shared/Palette")

local V3 = B.Vector3
local CF = B.CFrame
local Enum = B.Enum

local H = Layout.CorridorHeight
local L = Layout.CorridorLength
local HALF_W = Layout.CorridorWidth / 2
local WT = Layout.WainscotTop
local T = Layout.Wall

local Corridor = {}

type Opening = { u0: number, u1: number, v0: number, v1: number, trimGap: number }

-- Builds one wall (body + baseboard + chair rail + crown) in a local frame.
-- u range is the interior span; `extend` widens only the body to close corners.
local function buildWall(parent: any, name: string, frame: any, uMin: number, uMax: number, extend: number, trimInset: number, openings: { Opening })
	local wall = B.model(parent, name)
	table.sort(openings, function(a, b)
		return a.u0 < b.u0
	end)

	local function piece(u0: number, u1: number, v0: number, v1: number)
		if u1 - u0 < 0.01 or v1 - v0 < 0.01 then
			return
		end
		if v0 < WT then
			frame:box(wall, "WallLower", u0, v0, 0, u1, math.min(v1, WT), T, Palette.WallLower, "Plaster")
		end
		if v1 > WT then
			frame:box(wall, "WallUpper", u0, math.max(v0, WT), 0, u1, v1, T, Palette.WallUpper, "Plaster")
		end
	end

	local cursor = uMin - extend
	for _, o in openings do
		piece(cursor, o.u0, 0, H)
		piece(o.u0, o.u1, 0, o.v0)
		piece(o.u0, o.u1, o.v1, H)
		cursor = o.u1
	end
	piece(cursor, uMax + extend, 0, H)

	-- Trims are interrupted where a door/elevator casing sits (trimGap > 0).
	local function trimRun(trimName: string, v0: number, v1: number, depth: number, color: string, material: string, useGaps: boolean)
		local c = uMin + trimInset
		local stop = uMax - trimInset
		for _, o in openings do
			if useGaps and o.trimGap > 0 and o.v0 <= v0 then
				local g0, g1 = o.u0 - o.trimGap, o.u1 + o.trimGap
				if g0 - c > 0.05 then
					frame:box(wall, trimName, c, v0, -depth, g0, v1, 0, color, material, { shadow = false })
				end
				c = g1
			end
		end
		if stop - c > 0.05 then
			frame:box(wall, trimName, c, v0, -depth, stop, v1, 0, color, material, { shadow = false })
		end
	end

	trimRun("Baseboard", 0, Layout.BaseboardHeight, 0.12, Palette.Trim, "Wood", true)
	trimRun("ChairRail", WT - 0.1, WT + 0.12, 0.12, Palette.Trim, "Wood", true)
	trimRun("Crown", H - 0.28, H, 0.2, Palette.LampHousing, "Plaster", false)
	return wall
end

-- Wall frames: u is the viewer's right when standing in the corridor facing the wall.
Corridor.frames = {
	north = function() return B.frame(V3.new(0, 0, HALF_W), 0) end, -- u = -x
	south = function() return B.frame(V3.new(0, 0, -HALF_W), math.pi) end, -- u = x
	east = function() return B.frame(V3.new(L, 0, 0), math.pi / 2) end, -- u = z
	west = function() return B.frame(V3.new(0, 0, 0), -math.pi / 2) end, -- u = -z
}

function Corridor.uForX(side: string, x: number): number
	return if side == "north" then -x else x
end

local function lightModule(parent: any, index: number, x: number)
	local m = B.model(parent, "CorridorLight_" .. index)
	m:AddTag("MD_CorridorLight")
	local brightness = Layout.LightBrightness[index]
	m:SetAttribute("BaseBrightness", brightness)
	local housing = B.box(m, "Housing", V3.new(x - 0.9, H - 0.2, -1.7), V3.new(x + 0.9, H, 1.7), Palette.LampHousing, "SmoothPlastic", { shadow = false, collide = false })
	B.box(m, "Diffuser", V3.new(x - 0.7, H - 0.36, -1.5), V3.new(x + 0.7, H - 0.2, 1.5), Palette.LampDiffuser, "Neon", { shadow = false, collide = false, transparency = 0.25 })
	-- thin end caps so the diffuser reads as a fitted lamp, not a floating slab
	B.box(m, "CapA", V3.new(x - 0.75, H - 0.38, -1.62), V3.new(x + 0.75, H - 0.2, -1.5), Palette.MetalLight, "Metal", { shadow = false, collide = false })
	B.box(m, "CapB", V3.new(x - 0.75, H - 0.38, 1.5), V3.new(x + 0.75, H - 0.2, 1.62), Palette.MetalLight, "Metal", { shadow = false, collide = false })
	local anchor = B.attachment(housing, "LightAnchor", V3.new(x, H - 0.75, 0))
	local c = Layout.LightColor
	B.new("PointLight", {
		Name = "Light",
		Color = B.Color3.fromRGB(c[1], c[2], c[3]),
		Brightness = brightness,
		Range = Layout.LightRange,
		Shadows = true,
	}, anchor)
	return m
end

function Corridor.build(root: any)
	local shell = B.model(root, "Corridor")

	-- Floor, runner, ceiling
	B.box(shell, "Floor", V3.new(-T, -1, -HALF_W - T), V3.new(L + T, 0, HALF_W + T), Palette.Floor, "WoodPlanks")
	B.box(shell, "Runner", V3.new(2.2, 0, -2.4), V3.new(L - 2.2, 0.06, 2.4), Palette.Runner, "Carpet", { shadow = false })
	B.box(shell, "RunnerEdgeA", V3.new(2.2, 0, -2.5), V3.new(L - 2.2, 0.05, -2.4), "3A2A27", "Fabric", { shadow = false })
	B.box(shell, "RunnerEdgeB", V3.new(2.2, 0, 2.4), V3.new(L - 2.2, 0.05, 2.5), "3A2A27", "Fabric", { shadow = false })
	B.box(shell, "Ceiling", V3.new(-T, H, -HALF_W - T), V3.new(L + T, H + 1, HALF_W + T), Palette.Ceiling, "Plaster")

	-- Openings per wall (u coordinates in each wall frame)
	local dw = Layout.DoorWidth / 2
	local openings: { [string]: { Opening } } = { north = {}, south = {}, east = {}, west = {} }
	for _, apt in Layout.Apartments do
		local u = Corridor.uForX(apt.side, apt.x)
		table.insert(openings[apt.side], { u0 = u - dw, u1 = u + dw, v0 = 0, v1 = Layout.DoorHeight, trimGap = 0.45 })
	end
	local eo = Layout.Elevator.OpeningHalfWidth
	table.insert(openings.west, { u0 = -eo, u1 = eo, v0 = 0, v1 = Layout.Elevator.OpeningHeight, trimGap = 0.6 })
	table.insert(openings.east, { u0 = -2, u1 = 2, v0 = 4.2, v1 = 8.4, trimGap = 0 })

	local walls = B.model(shell, "Walls")
	buildWall(walls, "NorthWall", Corridor.frames.north(), -L, 0, T, 0, openings.north)
	buildWall(walls, "SouthWall", Corridor.frames.south(), 0, L, T, 0, openings.south)
	buildWall(walls, "EastWall", Corridor.frames.east(), -HALF_W, HALF_W, 0, 0.12, openings.east)
	buildWall(walls, "WestWall", Corridor.frames.west(), -HALF_W, HALF_W, 0, 0.12, openings.west)

	-- Repeating upper-wall module: slim pilasters between the chair rail and the crown.
	local pil = B.model(shell, "Pilasters")
	for _, x in Layout.PilasterX do
		for _, side in { "north", "south" } do
			local f = Corridor.frames[side]()
			local u = Corridor.uForX(side, x)
			f:box(pil, "Pilaster", u - 0.35, WT + 0.12, -0.08, u + 0.35, H - 0.28, 0, "D2C8B4", "Plaster", { shadow = false })
		end
	end

	-- Repeating light modules
	local lights = B.model(shell, "Lights")
	for i, x in Layout.LightX do
		lightModule(lights, i, x)
	end

	-- End window: frosted pane, wood casing, mullions, sill. A faint cool light suggests
	-- a street lamp outside without letting any sky light in.
	local ef = Corridor.frames.east()
	local win = B.model(shell, "EndWindow")
	ef:box(win, "LiningL", -2, 4.2, 0, -1.9, 8.4, T, Palette.Trim, "Wood")
	ef:box(win, "LiningR", 1.9, 4.2, 0, 2, 8.4, T, Palette.Trim, "Wood")
	ef:box(win, "LiningTop", -2, 8.3, 0, 2, 8.4, T, Palette.Trim, "Wood")
	local pane = ef:box(win, "Pane", -1.9, 4.2, 0.55, 1.9, 8.3, 0.6, "2A3440", "SmoothPlastic", { reflectance = 0.06 })
	ef:box(win, "MullionV", -0.07, 4.2, 0.45, 0.07, 8.3, 0.55, Palette.Trim, "Wood", { shadow = false })
	ef:box(win, "MullionH", -1.9, 6.35, 0.45, 1.9, 6.49, 0.55, Palette.Trim, "Wood", { shadow = false })
	ef:box(win, "CasingL", -2.45, 4.0, -0.14, -1.95, 8.85, 0, Palette.Trim, "Wood", { shadow = false })
	ef:box(win, "CasingR", 1.95, 4.0, -0.14, 2.45, 8.85, 0, Palette.Trim, "Wood", { shadow = false })
	ef:box(win, "CasingTop", -2.45, 8.35, -0.14, 2.45, 8.85, 0, Palette.Trim, "Wood", { shadow = false })
	ef:box(win, "Sill", -2.6, 3.95, -0.4, 2.6, 4.2, 0.3, "D2C8B4", "SmoothPlastic")
	B.new("SurfaceLight", {
		Name = "StreetGlow",
		Face = Enum.NormalId.Front,
		Color = B.Color3.fromRGB(150, 170, 205),
		Brightness = 0.35,
		Range = 10,
		Angle = 100,
		Shadows = false,
	}, pane)

	-- Radiator under the window
	local rad = B.model(shell, "Radiator")
	ef:box(rad, "Base", -1.8, 0.8, -0.5, 1.8, 0.95, -0.12, "CFC7B5", "SmoothPlastic", { shadow = false })
	ef:box(rad, "Top", -1.8, 2.8, -0.5, 1.8, 2.95, -0.12, "CFC7B5", "SmoothPlastic", { shadow = false })
	for i = 0, 9 do
		local u = -1.62 + i * 0.36
		ef:box(rad, "Fin", u - 0.11, 0.95, -0.46, u + 0.11, 2.8, -0.16, "CFC7B5", "SmoothPlastic")
	end
	ef:box(rad, "RustEdge", -1.8, 0.8, -0.52, 1.8, 0.88, -0.5, Palette.Rust, "CorrodedMetal", { shadow = false, collide = false })
	ef:box(rad, "FootL", -1.6, 0, -0.35, -1.4, 0.8, -0.2, Palette.MetalDark, "Metal", { shadow = false })
	ef:box(rad, "FootR", 1.4, 0, -0.35, 1.6, 0.8, -0.2, Palette.MetalDark, "Metal", { shadow = false })
	ef:box(rad, "Valve", 1.85, 1.2, -0.35, 2.05, 1.5, -0.15, Palette.Brass, "Metal", { shadow = false })

	-- Wear: a ceiling water stain near 103, scuffs by the parcel cart.
	local wear = B.model(shell, "Wear")
	B.part(wear, "CeilingStain", V3.new(0.02, 2.4, 2.1), CF.new(41.5, H - 0.03, 3.2) * CF.Angles(0, 0.4, math.pi / 2), "8C7753", "Plaster", { shape = "Cylinder", transparency = 0.6, shadow = false, collide = false, query = false })
	B.part(wear, "CeilingStainRing", V3.new(0.02, 1.2, 1.0), CF.new(42.1, H - 0.06, 3.6) * CF.Angles(0, 1.1, math.pi / 2), "76623F", "Plaster", { shape = "Cylinder", transparency = 0.65, shadow = false, collide = false, query = false })
	local sf = Corridor.frames.south()
	sf:box(wear, "Scuff", 5.2, 0.95, -0.02, 6.6, 1.05, 0, Palette.Grime, "SmoothPlastic", { transparency = 0.55, shadow = false, collide = false, query = false })
	sf:box(wear, "Scuff", 6.1, 1.25, -0.02, 7.0, 1.32, 0, Palette.Grime, "SmoothPlastic", { transparency = 0.6, shadow = false, collide = false, query = false })

	return shell
end

return Corridor
