--!strict
-- Corridor props: mailboxes, notice board, electrical panel, the parcel cart (pickup
-- point) and an edit-only R15-sized scale reference.

local B = require("../lib/Builder")
local Layout = require("./Layout")
local Palette = require("../../src/shared/Palette")
local Corridor = require("./Corridor")

local V3 = B.Vector3
local CF = B.CFrame
local Enum = B.Enum

local Props = {}

local REGULAR = B.Font.new("rbxasset://fonts/families/BuilderSans.json", Enum.FontWeight.Regular)
local BOLD = B.Font.new("rbxasset://fonts/families/BuilderSans.json", Enum.FontWeight.Bold)

local function surfaceText(part: any, lines: { { text: string, size: number, bold: boolean? } }, color: string, pps: number)
	local gui = B.new("SurfaceGui", {
		Name = "Text",
		Face = Enum.NormalId.Front,
		SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud,
		PixelsPerStud = pps,
		LightInfluence = 1,
		ClipsDescendants = true,
	}, part)
	B.new("UIPadding", {
		PaddingTop = B.UDim.new(0, 6),
		PaddingLeft = B.UDim.new(0, 8),
		PaddingRight = B.UDim.new(0, 8),
	}, gui)
	B.new("UIListLayout", {
		SortOrder = Enum.SortOrder.LayoutOrder,
		Padding = B.UDim.new(0, 3),
		HorizontalAlignment = Enum.HorizontalAlignment.Left,
	}, gui)
	for i, line in lines do
		B.new("TextLabel", {
			Name = "Line" .. i,
			LayoutOrder = i,
			Size = B.UDim2.new(1, 0, 0, 0),
			AutomaticSize = Enum.AutomaticSize.Y,
			BackgroundTransparency = 1,
			BorderSizePixel = 0,
			Text = line.text,
			TextSize = line.size,
			TextWrapped = true,
			TextXAlignment = Enum.TextXAlignment.Left,
			TextColor3 = color,
			FontFace = if line.bold then BOLD else REGULAR,
		}, gui)
	end
	return gui
end

local function mailboxes(parent: any)
	local f = Corridor.frames.north() -- u = -x
	local m = B.model(parent, "Mailboxes")
	f:box(m, "Body", -7.2, 3.9, -0.55, -3.4, 6.3, 0, Palette.Metal, "Metal")
	f:box(m, "TopLip", -7.28, 6.3, -0.6, -3.32, 6.38, 0, Palette.MetalDark, "Metal", { shadow = false })
	local shades = { Palette.MetalLight, B.lerpHex(Palette.MetalLight, Palette.Metal, 0.35), B.lerpHex(Palette.MetalLight, Palette.Rust, 0.12) }
	for i, id in { "101", "102", "103" } do
		local u = -6.55 + (i - 1) * 1.25
		f:box(m, "Door" .. id, u - 0.56, 4.05, -0.58, u + 0.56, 6.15, -0.55, shades[i], "Metal", { shadow = false })
		f:box(m, "Slot" .. id, u - 0.38, 5.72, -0.6, u + 0.38, 5.84, -0.58, Palette.Brass, "Metal", { shadow = false, collide = false })
		local label = f:box(m, "Label" .. id, u - 0.3, 5.28, -0.6, u + 0.3, 5.52, -0.58, "E4DDCB", "SmoothPlastic", { shadow = false, collide = false })
		surfaceText(label, { { text = id, size = 13, bold = true } }, "2B2620", 60)
		B.part(m, "Keyhole" .. id, V3.new(0.03, 0.1, 0.1), f:at(u + 0.35, 4.6, -0.595) * CF.Angles(0, math.pi / 2, 0), "1F1D1B", "Metal", { shape = "Cylinder", shadow = false, collide = false })
	end
	-- a rust bloom at the bottom corner of 103's box
	f:box(m, "Rust", -3.75, 4.05, -0.585, -3.5, 4.3, -0.58, Palette.Rust, "CorrodedMetal", { shadow = false, collide = false })
	return m
end

local function noticeBoard(parent: any)
	local f = Corridor.frames.north()
	local m = B.model(parent, "NoticeBoard")
	local u0, u1 = -28.7, -25.3
	f:box(m, "Cork", u0, 4.3, -0.1, u1, 6.8, 0, "8F7250", "Cardboard")
	f:box(m, "FrameTop", u0 - 0.12, 6.8, -0.14, u1 + 0.12, 6.92, 0, Palette.Trim, "Wood", { shadow = false })
	f:box(m, "FrameBottom", u0 - 0.12, 4.18, -0.14, u1 + 0.12, 4.3, 0, Palette.Trim, "Wood", { shadow = false })
	f:box(m, "FrameL", u0 - 0.12, 4.3, -0.14, u0, 6.8, 0, Palette.Trim, "Wood", { shadow = false })
	f:box(m, "FrameR", u1, 4.3, -0.14, u1 + 0.12, 6.8, 0, Palette.Trim, "Wood", { shadow = false })

	local p1 = f:box(m, "PaperRules", -28.45, 4.55, -0.12, -27.05, 6.55, -0.1, "E9E4D8", "SmoothPlastic", { shadow = false, collide = false })
	surfaceText(p1, {
		{ text = "NOTICE", size = 15, bold = true },
		{ text = "Quiet hours", size = 12 },
		{ text = "22:00 - 07:00", size = 12, bold = true },
		{ text = "Keep the hallway clear.", size = 11 },
	}, "2B2620", 80)
	local p2cf = f:at(-26.2, 5.75, -0.115) * CF.Angles(0, 0, math.rad(-3))
	local p2 = B.part(m, "PaperNote", V3.new(1.25, 1.2, 0.02), p2cf, "DCD6C6", "SmoothPlastic", { shadow = false, collide = false })
	surfaceText(p2, {
		{ text = "The light by 103 keeps flickering.", size = 11 },
		{ text = "Reported twice.  - 2B", size = 11 },
	}, "3A332C", 80)
	local p3 = f:box(m, "PaperParcels", -26.75, 4.45, -0.12, -25.6, 5.0, -0.1, "E4DDCB", "SmoothPlastic", { shadow = false, collide = false })
	surfaceText(p3, { { text = "Parcels: leave at the door.", size = 11 } }, "2B2620", 80)
	for i, pin in { { -27.75, 6.45, "8A3B32" }, { -26.2, 6.25, "3B4E6A" }, { -26.2, 4.9, "8A3B32" } } do
		B.part(m, "Pin" .. i, V3.new(0.09, 0.09, 0.09), f:at(pin[1], pin[2], -0.16), pin[3], "SmoothPlastic", { shape = "Ball", shadow = false, collide = false, query = false })
	end
	return m
end

local function electricalPanel(parent: any)
	local f = Corridor.frames.south() -- u = x
	local m = B.model(parent, "ElectricalPanel")
	f:box(m, "Box", 33.2, 4.2, -0.5, 35.4, 7.4, 0, "6A6E70", "Metal")
	f:box(m, "DoorFace", 33.28, 4.28, -0.53, 35.32, 7.32, -0.5, "71767A", "Metal", { shadow = false })
	f:box(m, "Hinge", 33.3, 4.5, -0.56, 33.38, 7.1, -0.53, Palette.MetalDark, "Metal", { shadow = false, collide = false })
	f:box(m, "Handle", 35.05, 5.5, -0.62, 35.15, 6.1, -0.53, Palette.MetalDark, "Metal", { shadow = false, collide = false })
	local label = f:box(m, "Warning", 33.5, 6.68, -0.545, 35.1, 7.06, -0.53, "C7AE5A", "SmoothPlastic", { shadow = false, collide = false })
	surfaceText(label, { { text = "ELECTRICAL", size = 11, bold = true } }, "1F1D1B", 60)
	f:box(m, "RustEdge", 33.28, 4.28, -0.545, 35.32, 4.42, -0.53, Palette.Rust, "CorrodedMetal", { shadow = false, collide = false })
	f:box(m, "RustDrip", 34.6, 4.42, -0.54, 34.72, 4.9, -0.53, Palette.Rust, "CorrodedMetal", { shadow = false, collide = false, transparency = 0.2 })
	-- conduit to the ceiling
	B.part(m, "Conduit", V3.new(3.6, 0.28, 0.28), f:at(34.3, 7.4 + 1.8, -0.25) * CF.Angles(0, 0, math.pi / 2), "8B8F92", "Metal", { shape = "Cylinder", shadow = false })
	f:box(m, "Clamp", 34.12, 9.2, -0.42, 34.48, 9.32, 0, Palette.MetalDark, "Metal", { shadow = false, collide = false })
	return m
end

local function parcelBox(parent: any, name: string, size: any, cf: any, color: string)
	local box = B.part(parent, name, size, cf, color, "Cardboard")
	B.part(parent, name .. "Tape", V3.new(size.X + 0.02, 0.02, 0.26), cf * CF.new(0, size.Y / 2 + 0.005, 0), Palette.Tape, "SmoothPlastic", { shadow = false, collide = false, query = false })
	return box
end

local function parcelCart(parent: any)
	local m = B.model(parent, "ParcelCart")
	local p = Layout.Pickup
	local base = CF.new(p.x, 0, p.z) * CF.Angles(0, math.rad(-8), 0)
	local deck = B.part(m, "Deck", V3.new(3.0, 0.15, 1.8), base * CF.new(0, 0.625, 0), "4A3F35", "Wood")
	B.part(m, "Rim", V3.new(3.1, 0.08, 1.9), base * CF.new(0, 0.52, 0), Palette.MetalLight, "Metal", { shadow = false, collide = false })
	for _, sx in { -1.25, 1.25 } do
		for _, sz in { -0.7, 0.7 } do
			B.part(m, "Wheel", V3.new(0.16, 0.42, 0.42), base * CF.new(sx, 0.21, sz) * CF.Angles(0, math.pi / 2, 0), "232323", "Rubber", { shape = "Cylinder", shadow = false, collide = false })
			B.part(m, "Fork", V3.new(0.26, 0.2, 0.08), base * CF.new(sx, 0.4, sz), Palette.MetalLight, "Metal", { shadow = false, collide = false })
		end
	end
	for _, sz in { -0.8, 0.8 } do
		B.part(m, "HandlePost", V3.new(2.4, 0.13, 0.13), base * CF.new(-1.42, 1.85, sz) * CF.Angles(0, 0, math.pi / 2), Palette.MetalLight, "Metal", { shape = "Cylinder" })
	end
	B.part(m, "HandleBar", V3.new(1.74, 0.13, 0.13), base * CF.new(-1.42, 3.05, 0) * CF.Angles(0, math.pi / 2, 0), Palette.MetalLight, "Metal", { shape = "Cylinder" })

	parcelBox(m, "ParcelA", V3.new(1.3, 0.9, 1.1), base * CF.new(0.55, 0.7 + 0.45, -0.25) * CF.Angles(0, math.rad(4), 0), Palette.Cardboard)
	parcelBox(m, "ParcelB", V3.new(1.1, 0.8, 1.0), base * CF.new(-0.6, 0.7 + 0.4, 0.28) * CF.Angles(0, math.rad(-6), 0), "9C774F")
	parcelBox(m, "ParcelC", V3.new(0.9, 0.6, 0.8), base * CF.new(0.45, 0.7 + 0.9 + 0.3, -0.18) * CF.Angles(0, math.rad(12), 0), "B38F63")
	B.part(m, "Sticker", V3.new(0.5, 0.32, 0.02), base * CF.new(0.55, 1.2, -0.25) * CF.Angles(0, math.rad(4), 0) * CF.new(0, 0, 0.56), "E9E4D8", "SmoothPlastic", { shadow = false, collide = false, query = false })

	deck:AddTag("MD_PickupPoint")
	local top = base * CF.new(0.2, 2.3, 0)
	local pa = B.attachment(deck, "PromptAttachment", top.Position)
	B.new("ProximityPrompt", {
		Name = "PickupPrompt",
		ActionText = "Pick up parcel",
		ObjectText = "Parcel cart",
		Style = Enum.ProximityPromptStyle.Custom,
		HoldDuration = 0,
		MaxActivationDistance = 7,
		RequiresLineOfSight = false,
		KeyboardKeyCode = Enum.KeyCode.E,
		GamepadKeyCode = Enum.KeyCode.ButtonX,
		Exclusivity = Enum.ProximityPromptExclusivity.OnePerButton,
	}, pa)
	B.attachment(deck, "MarkerAttachment", (base * CF.new(0.2, 3.9, 0)).Position)
	B.new("Sound", { Name = "PickupSound", Volume = 0.4, RollOffMaxDistance = 30, RollOffMinDistance = 5 }, deck)
	return m
end

-- Blocky R15 proportions (≈5.2 studs). Visible in Studio edit mode only; the server
-- removes the _DevOnly folder when the game starts.
local function scaleReference(parent: any)
	local m = B.model(parent, "ScaleReference_R15")
	local base = CF.new(15, 0, 3.6) * CF.Angles(0, math.pi, 0) -- facing door 101 (+Z)
	local col, opts = "6C8EBF", { collide = false, query = false, shadow = false, transparency = 0.25 }
	B.part(m, "LeftLeg", V3.new(0.95, 2.0, 1.0), base * CF.new(-0.5, 1.0, 0), col, "SmoothPlastic", opts)
	B.part(m, "RightLeg", V3.new(0.95, 2.0, 1.0), base * CF.new(0.5, 1.0, 0), col, "SmoothPlastic", opts)
	B.part(m, "Torso", V3.new(2.0, 2.0, 1.0), base * CF.new(0, 3.0, 0), col, "SmoothPlastic", opts)
	B.part(m, "LeftArm", V3.new(1.0, 2.0, 1.0), base * CF.new(-1.5, 2.95, 0), col, "SmoothPlastic", opts)
	B.part(m, "RightArm", V3.new(1.0, 2.0, 1.0), base * CF.new(1.5, 2.95, 0), col, "SmoothPlastic", opts)
	B.part(m, "Head", V3.new(1.2, 1.2, 1.2), base * CF.new(0, 4.6, 0), col, "SmoothPlastic", opts)
	return m
end

function Props.build(root: any, devRoot: any)
	local props = B.model(root, "Props")
	mailboxes(props)
	noticeBoard(props)
	electricalPanel(props)
	parcelCart(props)
	scaleReference(devRoot)
	return props
end

return Props
