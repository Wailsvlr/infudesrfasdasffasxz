--!strict
-- Elevator cab (start area): brushed metal cab, centre-opening doors, call button,
-- floor indicator, spawn point and an occupancy zone used by the server door logic.

local B = require("../lib/Builder")
local Layout = require("./Layout")
local Palette = require("../../src/shared/Palette")

local V3 = B.Vector3
local CF = B.CFrame
local Enum = B.Enum

local Elevator = {}

local E = Layout.Elevator
local HZ = E.InnerHalfZ -- 5
local X0, X1 = E.InnerMinX, E.InnerMaxX -- -11, -1
local TOP = E.CeilingY -- 9.5
local OW = E.OpeningHalfWidth -- 3
local OH = E.OpeningHeight -- 8.5

local BOLD = B.Font.new("rbxasset://fonts/families/BuilderSans.json", Enum.FontWeight.Bold)

local function display(part: any, face: any, text: string)
	local gui = B.new("SurfaceGui", {
		Name = "Display",
		Face = face,
		SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud,
		PixelsPerStud = 60,
		LightInfluence = 0,
		Brightness = 1.2,
	}, part)
	B.new("TextLabel", {
		Name = "Text",
		Size = B.UDim2.fromScale(1, 1),
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		Text = text,
		TextScaled = true,
		TextColor3 = Palette.UiAccent,
		FontFace = BOLD,
	}, gui)
end

function Elevator.build(root: any)
	local m = B.model(root, "Elevator")
	m:AddTag("MD_Elevator")
	m:SetAttribute("DoorsOpen", false)

	-- Cab shell
	local cab = B.model(m, "Cab")
	B.box(cab, "CabFloor", V3.new(X0 - 0.5, -1, -HZ - 0.5), V3.new(X1, 0, HZ + 0.5), "3A3B3C", "Rubber")
	B.box(cab, "BackWall", V3.new(X0 - 0.5, 0, -HZ - 0.5), V3.new(X0, TOP + 0.5, HZ + 0.5), Palette.MetalLight, "Metal")
	B.box(cab, "SideWallN", V3.new(X0 - 0.5, 0, HZ), V3.new(X1, TOP + 0.5, HZ + 0.5), Palette.MetalLight, "Metal")
	B.box(cab, "SideWallS", V3.new(X0 - 0.5, 0, -HZ - 0.5), V3.new(X1, TOP + 0.5, -HZ), Palette.MetalLight, "Metal")
	B.box(cab, "CabCeiling", V3.new(X0 - 0.5, TOP, -HZ - 0.5), V3.new(X1, TOP + 0.5, HZ + 0.5), Palette.MetalDark, "Metal")
	-- metal cladding on the inside of the front wall
	B.box(cab, "FrontCladN", V3.new(X1 - 0.1, 0, OW), V3.new(X1, TOP, HZ), Palette.Metal, "Metal")
	B.box(cab, "FrontCladS", V3.new(X1 - 0.1, 0, -HZ), V3.new(X1, TOP, -OW), Palette.Metal, "Metal")
	B.box(cab, "FrontCladHead", V3.new(X1 - 0.1, OH, -OW), V3.new(X1, TOP, OW), Palette.Metal, "Metal")

	-- Panel seams and kick band
	local detail = B.model(cab, "Detail")
	for _, z in { -2.5, 0, 2.5 } do
		B.box(detail, "Seam", V3.new(X0, 0.8, z - 0.04), V3.new(X0 + 0.03, TOP, z + 0.04), Palette.MetalDark, "Metal", { shadow = false, collide = false })
	end
	for _, x in { -8.5, -6, -3.5 } do
		B.box(detail, "Seam", V3.new(x - 0.04, 0.8, HZ - 0.03), V3.new(x + 0.04, TOP, HZ), Palette.MetalDark, "Metal", { shadow = false, collide = false })
		B.box(detail, "Seam", V3.new(x - 0.04, 0.8, -HZ), V3.new(x + 0.04, TOP, -HZ + 0.03), Palette.MetalDark, "Metal", { shadow = false, collide = false })
	end
	B.box(detail, "KickBack", V3.new(X0, 0, -HZ), V3.new(X0 + 0.04, 0.8, HZ), Palette.MetalDark, "Metal", { shadow = false })
	B.box(detail, "KickN", V3.new(X0, 0, HZ - 0.04), V3.new(X1 - 0.1, 0.8, HZ), Palette.MetalDark, "Metal", { shadow = false })
	B.box(detail, "KickS", V3.new(X0, 0, -HZ), V3.new(X1 - 0.1, 0.8, -HZ + 0.04), Palette.MetalDark, "Metal", { shadow = false })

	-- Handrails (back and sides)
	B.part(detail, "RailBack", V3.new(8, 0.2, 0.2), CF.new(X0 + 0.32, 3.2, 0) * CF.Angles(0, math.pi / 2, 0), Palette.MetalLight, "Metal", { shape = "Cylinder", shadow = false })
	for _, z in { -3.6, 3.6 } do
		B.box(detail, "RailBracket", V3.new(X0, 3.12, z - 0.08), V3.new(X0 + 0.3, 3.28, z + 0.08), Palette.MetalLight, "Metal", { shadow = false, collide = false })
	end
	for _, s in { 1, -1 } do
		B.part(detail, "RailSide", V3.new(6.5, 0.2, 0.2), CF.new(-6.4, 3.2, s * (HZ - 0.32)), Palette.MetalLight, "Metal", { shape = "Cylinder", shadow = false })
		for _, x in { -9, -3.8 } do
			B.box(detail, "RailBracket", V3.new(x - 0.08, 3.12, s > 0 and HZ - 0.3 or -HZ), V3.new(x + 0.08, 3.28, s > 0 and HZ or -HZ + 0.3), Palette.MetalLight, "Metal", { shadow = false, collide = false })
		end
	end

	-- Ceiling light panel + its light
	B.box(detail, "LightFrame", V3.new(-8.1, TOP - 0.04, -2.1), V3.new(-3.9, TOP, 2.1), Palette.MetalLight, "Metal", { shadow = false, collide = false })
	local diffuser = B.box(detail, "LightPanel", V3.new(-8, TOP - 0.08, -2), V3.new(-4, TOP - 0.01, 2), "E8E2D6", "Neon", { shadow = false, collide = false, transparency = 0.3 })
	local la = B.attachment(diffuser, "LightAnchor", V3.new(-6, TOP - 0.5, 0))
	B.new("PointLight", { Name = "Light", Color = B.Color3.fromRGB(255, 240, 222), Brightness = 1.0, Range = 12, Shadows = true }, la)

	-- Inside control panel (decorative)
	B.box(detail, "ControlPanel", V3.new(X1 - 0.16, 3.2, 3.4), V3.new(X1 - 0.1, 6.3, 4.3), Palette.MetalLight, "Metal", { shadow = false, collide = false })
	for i, y in { 5.5, 5.0, 4.5, 4.0 } do
		B.part(detail, "Button" .. i, V3.new(0.04, 0.22, 0.22), CF.new(X1 - 0.18, y, 3.85), "B8B2A4", "Metal", { shape = "Cylinder", shadow = false, collide = false })
	end
	local cpDisplay = B.box(detail, "PanelDisplay", V3.new(X1 - 0.17, 5.85, 3.55), V3.new(X1 - 0.16, 6.12, 4.15), "1C1F1E", "SmoothPlastic", { shadow = false, collide = false })
	display(cpDisplay, Enum.NormalId.Left, "1")

	-- Doorway: linings inside the opening, sill, brushed surround on the corridor side
	local frame = B.model(m, "DoorFrame")
	B.box(frame, "LiningN", V3.new(-1, 0, OW - 0.1), V3.new(0, OH, OW), Palette.MetalLight, "Metal")
	B.box(frame, "LiningS", V3.new(-1, 0, -OW), V3.new(0, OH, -OW + 0.1), Palette.MetalLight, "Metal")
	B.box(frame, "LiningHead", V3.new(-1, OH - 0.1, -OW), V3.new(0, OH, OW), Palette.MetalLight, "Metal")
	B.box(frame, "Sill", V3.new(-1.2, 0, -OW), V3.new(0.35, 0.04, OW), Palette.MetalLight, "Metal", { shadow = false })
	B.box(frame, "SillGroove", V3.new(-0.64, 0.04, -OW + 0.1), V3.new(-0.36, 0.045, OW - 0.1), "2E3032", "Metal", { shadow = false, collide = false })
	B.box(frame, "SurroundN", V3.new(0, 0, OW - 0.05), V3.new(0.15, OH + 0.6, OW + 0.6), Palette.MetalLight, "Metal", { shadow = false })
	B.box(frame, "SurroundS", V3.new(0, 0, -OW - 0.6), V3.new(0.15, OH + 0.6, -OW + 0.05), Palette.MetalLight, "Metal", { shadow = false })
	B.box(frame, "SurroundHead", V3.new(0, OH - 0.05, -OW - 0.6), V3.new(0.15, OH + 0.6, OW + 0.6), Palette.MetalLight, "Metal", { shadow = false })
	-- a little rust where the sill meets the surround
	B.box(frame, "RustN", V3.new(0.02, 0, OW + 0.05), V3.new(0.17, 0.35, OW + 0.5), Palette.Rust, "CorrodedMetal", { shadow = false, collide = false })

	-- Floor indicator above the doors
	local indicator = B.box(frame, "FloorIndicator", V3.new(0, 9.35, -0.8), V3.new(0.2, 10.05, 0.8), "1C1F1E", "SmoothPlastic", { shadow = false })
	display(indicator, Enum.NormalId.Right, "1")
	B.new("Sound", { Name = "DingSound", Volume = 0.5, RollOffMaxDistance = 60, RollOffMinDistance = 8 }, indicator)

	-- Sliding doors (centre-opening). The server tweens them by OpenOffset.
	local doors = B.model(m, "Doors")
	local panelW = OW - 0.1
	local left = B.box(doors, "DoorLeft", V3.new(-0.62, 0.05, -panelW), V3.new(-0.38, OH - 0.1, 0), Palette.Metal, "Metal")
	local right = B.box(doors, "DoorRight", V3.new(-0.62, 0.05, 0), V3.new(-0.38, OH - 0.1, panelW), Palette.Metal, "Metal")
	left:AddTag("MD_ElevatorDoor")
	right:AddTag("MD_ElevatorDoor")
	left:SetAttribute("OpenOffset", V3.new(0, 0, -(panelW - 0.05)))
	right:SetAttribute("OpenOffset", V3.new(0, 0, panelW - 0.05))
	B.new("Sound", { Name = "DoorSound", Volume = 0.35, RollOffMaxDistance = 40, RollOffMinDistance = 6 }, left)

	-- Call button on the corridor side
	local call = B.model(m, "CallButton")
	local plate = B.box(call, "Plate", V3.new(0, 3.7, 4.1), V3.new(0.06, 4.8, 4.7), Palette.MetalLight, "Metal", { shadow = false })
	B.part(call, "Ring", V3.new(0.05, 0.46, 0.46), CF.new(0.075, 4.25, 4.4), Palette.Brass, "Metal", { shape = "Cylinder", shadow = false, collide = false })
	B.part(call, "Button", V3.new(0.08, 0.32, 0.32), CF.new(0.1, 4.25, 4.4), "9A9EA2", "Metal", { shape = "Cylinder", shadow = false, collide = false })
	plate:AddTag("MD_ElevatorCallButton")
	local ca = B.attachment(plate, "PromptAttachment", V3.new(0.9, 4.25, 4.4))
	B.new("ProximityPrompt", {
		Name = "CallPrompt",
		ActionText = "Call elevator",
		ObjectText = "Elevator",
		Style = Enum.ProximityPromptStyle.Custom,
		HoldDuration = 0,
		MaxActivationDistance = 6,
		RequiresLineOfSight = false,
		KeyboardKeyCode = Enum.KeyCode.E,
		GamepadKeyCode = Enum.KeyCode.ButtonX,
		Exclusivity = Enum.ProximityPromptExclusivity.OnePerButton,
		Enabled = true,
	}, ca)

	-- Occupancy zone (cab + doorway), invisible and non-interactive
	local zone = B.box(m, "CabZone", V3.new(X0, 0, -HZ), V3.new(1.5, 9, HZ), "FFFFFF", "SmoothPlastic", { collide = false, shadow = false, transparency = 1, query = false })
	zone:AddTag("MD_ElevatorZone")

	-- Spawn: facing the doors (+X). Invisible, no force field.
	-- Near the doors so the default camera has room behind the character.
	local spawnCf = CF.lookAt(V3.new(-4.5, 0.05, 0), V3.new(10, 0.05, 0))
	B.part(m, "Spawn", V3.new(4, 0.1, 4), spawnCf, "FFFFFF", "SmoothPlastic", {
		class = "SpawnLocation",
		collide = false,
		shadow = false,
		transparency = 1,
		query = false,
	})
	local spawn = m:FindFirstChild("Spawn")
	B.set(spawn, { Neutral = true, Duration = 0, AllowTeamChangeOnTouch = false, Enabled = true })

	return m
end

return Elevator
