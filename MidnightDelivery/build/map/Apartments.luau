--!strict
-- Apartment doors 101-103: recessed door slab, jamb linings, wood casings, number plate,
-- doorbell, doormat, prompt + marker attachments and the (empty) scare sound slots.
-- Doors share one construction so they belong to the same building; panel layout,
-- hardware, mat and wear differ per door.

local B = require("../lib/Builder")
local Layout = require("./Layout")
local Palette = require("../../src/shared/Palette")

local V3 = B.Vector3
local CF = B.CFrame
local Enum = B.Enum

local Apartments = {}

local HALF_W = Layout.CorridorWidth / 2
local DW = Layout.DoorWidth / 2 -- 2.5
local DH = Layout.DoorHeight -- 8.5
local RECESS = Layout.DoorRecess -- 0.3
local SLAB_T = 0.25

local BUILDER_BOLD = B.Font.new("rbxasset://fonts/families/BuilderSans.json", Enum.FontWeight.Bold)

local function numberPlate(parent: any, frame: any, id: string, u: number, v: number, tarnished: boolean)
	local color = if tarnished then Palette.BrassTarnished else Palette.Brass
	local tilt = if tarnished then CF.Angles(0, 0, math.rad(3)) else CF.new()
	local backing = B.part(parent, "PlateBacking", V3.new(1.42, 0.66, 0.03), frame:at(u, v, RECESS - 0.055) * tilt, "2B2620", "SmoothPlastic", { shadow = false, collide = false })
	local plate = B.part(parent, "NumberPlate", V3.new(1.3, 0.55, 0.04), frame:at(u, v, RECESS - 0.08) * tilt, color, "Metal", { shadow = false, collide = false })
	local gui = B.new("SurfaceGui", {
		Name = "Number",
		Face = Enum.NormalId.Front,
		SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud,
		PixelsPerStud = 80,
		LightInfluence = 1,
		ClipsDescendants = true,
	}, plate)
	B.new("TextLabel", {
		Name = "Text",
		Size = B.UDim2.fromScale(1, 1),
		BorderSizePixel = 0,
		BackgroundTransparency = 1,
		Text = id,
		TextScaled = true,
		TextColor3 = "2B2620",
		FontFace = BUILDER_BOLD,
	}, gui)
	return plate, backing
end

local function lever(parent: any, frame: any, u: number, color: string, material: string)
	frame:box(parent, "HandleRose", u - 0.13, 3.35, RECESS - 0.03, u + 0.13, 4.35, RECESS, color, material, { shadow = false })
	local dir = if u > 0 then -1 else 1
	frame:box(parent, "HandleLever", math.min(u, u + dir * 0.62), 3.92, RECESS - 0.22, math.max(u, u + dir * 0.62), 4.05, RECESS - 0.1, color, material, { shadow = false, collide = false })
	frame:box(parent, "HandleNeck", u - 0.05, 3.93, RECESS - 0.1, u + 0.05, 4.04, RECESS - 0.03, color, material, { shadow = false, collide = false })
end

local function deadbolt(parent: any, frame: any, u: number, v: number, color: string)
	B.part(parent, "Deadbolt", V3.new(0.05, 0.24, 0.24), frame:at(u, v, RECESS - 0.025) * CF.Angles(0, math.pi / 2, 0), color, "Metal", { shape = "Cylinder", shadow = false, collide = false })
end

local function panels(parent: any, frame: any, variant: string, doorColor: string)
	local raised = B.lerpHex(doorColor, "FFFFFF", 0.05)
	local w0, w1 = RECESS - 0.04, RECESS
	if variant == "four" then
		for _, uu in { { -1.75, -0.25 }, { 0.25, 1.75 } } do
			frame:box(parent, "Panel", uu[1], 4.55, w0, uu[2], 7.3, w1, raised, "SmoothPlastic", { shadow = false })
			frame:box(parent, "Panel", uu[1], 0.9, w0, uu[2], 3.85, w1, raised, "SmoothPlastic", { shadow = false })
		end
	elseif variant == "old" then
		frame:box(parent, "Panel", -1.6, 5.2, w0, 1.6, 7.3, w1, raised, "SmoothPlastic", { shadow = false })
		frame:box(parent, "Panel", -1.6, 0.9, w0, 1.6, 4.4, w1, raised, "SmoothPlastic", { shadow = false })
	else
		frame:box(parent, "Panel", -1.6, 4.6, w0, 1.6, 7.3, w1, raised, "SmoothPlastic", { shadow = false })
		frame:box(parent, "Panel", -1.6, 0.9, w0, 1.6, 3.9, w1, raised, "SmoothPlastic", { shadow = false })
	end
end

local function doormat(parent: any, frame: any, variant: string)
	local mat = B.model(parent, "Doormat")
	if variant == "two" then
		-- coir mat with a darker border
		frame:box(mat, "Border", -1.75, 0, -2.45, 1.75, 0.07, -0.4, "5E4630", "Fabric", { shadow = false })
		frame:box(mat, "Coir", -1.55, 0, -2.25, 1.55, 0.09, -0.6, "8A6B45", "Fabric", { shadow = false })
	elseif variant == "four" then
		-- ribbed grey rubber mat
		frame:box(mat, "Base", -1.6, 0, -2.3, 1.6, 0.06, -0.45, "3E403F", "Rubber", { shadow = false })
		for i = 0, 5 do
			local w = -2.1 + i * 0.3
			frame:box(mat, "Rib", -1.45, 0.06, w, 1.45, 0.09, w + 0.12, "474A48", "Rubber", { shadow = false, collide = false })
		end
	else
		-- worn, slightly crooked mat in front of 103
		local cf = frame:at(0.25, 0.035, -1.35) * CF.Angles(0, math.rad(5), 0)
		B.part(mat, "Base", V3.new(3.2, 0.07, 1.75), cf, "6A3A30", "Fabric", { shadow = false })
		B.part(mat, "WornPatch", V3.new(1.4, 0.02, 0.8), cf * CF.new(0.2, 0.045, 0.1), "7D5044", "Fabric", { shadow = false, collide = false })
	end
	return mat
end

local function buildApartment(parent: any, spec: any)
	local yaw = if spec.side == "north" then 0 else math.pi
	local zFace = if spec.side == "north" then HALF_W else -HALF_W
	local frame = B.frame(V3.new(spec.x, 0, zFace), yaw)
	local model = B.model(parent, "Apartment" .. spec.id)
	model:SetAttribute("ApartmentId", spec.id)

	local doorColor = Palette.Door
	if spec.variant == "four" then
		doorColor = B.lerpHex(Palette.Door, "000000", 0.05)
	elseif spec.variant == "old" then
		doorColor = Palette.DoorFaded
	end

	-- Jamb linings fill the reveal between the wall face and the recessed door.
	frame:box(model, "JambL", -DW, 0, 0, -DW + 0.12, DH, 1, Palette.Trim, "Wood")
	frame:box(model, "JambR", DW - 0.12, 0, 0, DW, DH, 1, Palette.Trim, "Wood")
	frame:box(model, "JambHead", -DW, DH - 0.12, 0, DW, DH, 1, Palette.Trim, "Wood")
	frame:box(model, "Backing", -DW + 0.1, 0, 0.9, DW - 0.1, DH - 0.1, 1, "2A2622", "SmoothPlastic")
	frame:box(model, "Threshold", -DW + 0.12, 0, -0.05, DW - 0.12, 0.05, RECESS + SLAB_T, "6B5846", "Wood", { shadow = false })

	-- Casings (architrave) around the opening
	frame:box(model, "CasingL", -DW - 0.45, 0, -0.16, -DW + 0.05, DH + 0.45, 0, Palette.Trim, "Wood", { shadow = false })
	frame:box(model, "CasingR", DW - 0.05, 0, -0.16, DW + 0.45, DH + 0.45, 0, Palette.Trim, "Wood", { shadow = false })
	frame:box(model, "CasingHead", -DW - 0.5, DH - 0.05, -0.2, DW + 0.5, DH + 0.5, 0, Palette.Trim, "Wood", { shadow = false })

	-- Door leaf (recessed): slab + everything mounted on it, grouped so the client can
	-- rattle it as one piece during the scare.
	local leaf = B.model(model, "Leaf")
	local door = frame:box(leaf, "Door", -DW + 0.12, 0.05, RECESS, DW - 0.12, DH - 0.12, RECESS + SLAB_T, doorColor, "SmoothPlastic")
	door:AddTag("MD_ApartmentDoor")
	door:SetAttribute("ApartmentId", spec.id)
	panels(leaf, frame, spec.variant, doorColor)

	-- Hardware per variant
	if spec.variant == "two" then
		lever(leaf, frame, 1.85, Palette.Brass, "Metal")
		B.part(leaf, "Peephole", V3.new(0.05, 0.16, 0.16), frame:at(0, 5.6, RECESS - 0.065) * CF.Angles(0, math.pi / 2, 0), Palette.Brass, "Metal", { shape = "Cylinder", shadow = false, collide = false })
		frame:box(leaf, "KickPlate", -2.1, 0.15, RECESS - 0.02, 2.1, 0.8, RECESS, "8C7448", "Metal", { shadow = false })
		numberPlate(leaf, frame, spec.id, 0, 6.45, false)
	elseif spec.variant == "four" then
		B.part(leaf, "Knob", V3.new(0.3, 0.3, 0.3), frame:at(-1.85, 3.95, RECESS - 0.2), Palette.Brass, "Metal", { shape = "Ball", shadow = false, collide = false })
		frame:box(leaf, "KnobRose", -1.97, 3.83, RECESS - 0.03, -1.73, 4.07, RECESS, Palette.Brass, "Metal", { shadow = false })
		deadbolt(leaf, frame, -1.85, 4.75, Palette.Brass)
		frame:box(leaf, "MailSlot", -0.8, 4.03, RECESS - 0.03, 0.8, 4.37, RECESS, Palette.Brass, "Metal", { shadow = false })
		frame:box(leaf, "MailSlotGap", -0.66, 4.15, RECESS - 0.05, 0.66, 4.25, RECESS - 0.03, "1A1714", "SmoothPlastic", { shadow = false, collide = false })
		numberPlate(leaf, frame, spec.id, 0, 6.45, false)
	else
		lever(leaf, frame, 1.85, "3C3A38", "Metal")
		deadbolt(leaf, frame, 1.85, 4.8, "4A4744")
		deadbolt(leaf, frame, 1.85, 5.35, "4A4744")
		-- scratched paint around the locks
		for i, s in { { 1.65, 4.3, 1.95, 4.36 }, { 1.7, 4.62, 2.0, 4.67 }, { 1.62, 5.05, 1.8, 5.1 } } do
			frame:box(leaf, "Scratch" .. i, s[1], s[2], RECESS - 0.01, s[3], s[4], RECESS, "5E7166", "SmoothPlastic", { shadow = false, collide = false, query = false })
		end
		numberPlate(leaf, frame, spec.id, 0, 6.45, true)
	end

	-- Doorbell on the wall, right of the casing
	frame:box(model, "Doorbell", DW + 0.65, 4.1, -0.05, DW + 1.0, 4.6, 0, "D9D0BC", "SmoothPlastic", { shadow = false, collide = false })
	B.part(model, "DoorbellButton", V3.new(0.04, 0.14, 0.14), frame:at(DW + 0.825, 4.35, -0.07) * CF.Angles(0, math.pi / 2, 0), "8C7448", "Metal", { shape = "Cylinder", shadow = false, collide = false })

	doormat(model, frame, spec.variant)

	-- Gameplay anchors: prompt in front of the door at chest height, marker above the casing.
	local prompt = B.attachment(door, "PromptAttachment", frame:at(0, 3.6, -0.9).Position)
	B.new("ProximityPrompt", {
		Name = "DeliverPrompt",
		ActionText = "Deliver parcel",
		ObjectText = "Apartment " .. spec.id,
		Style = Enum.ProximityPromptStyle.Custom,
		HoldDuration = 0.35,
		MaxActivationDistance = 7,
		RequiresLineOfSight = false,
		KeyboardKeyCode = Enum.KeyCode.E,
		GamepadKeyCode = Enum.KeyCode.ButtonX,
		Exclusivity = Enum.ProximityPromptExclusivity.OnePerButton,
	}, prompt)
	B.attachment(door, "MarkerAttachment", frame:at(0, DH + 1.2, -0.4).Position)
	B.attachment(door, "DropAttachment", frame:at(-0.6, 0.52, -1.1).Position) -- where the delivered parcel is set down

	-- Scare sound slots (no audio asset is bundled; see docs/ASSETS.md)
	B.new("Sound", { Name = "KnockSound", Volume = 0.6, RollOffMaxDistance = 45, RollOffMinDistance = 6 }, door)
	B.new("Sound", { Name = "ThudSound", Volume = 0.5, RollOffMaxDistance = 45, RollOffMinDistance = 6 }, door)

	return model
end

function Apartments.build(root: any)
	local folder = B.model(root, "Apartments")
	for _, spec in Layout.Apartments do
		buildApartment(folder, spec)
	end
	return folder
end

return Apartments
