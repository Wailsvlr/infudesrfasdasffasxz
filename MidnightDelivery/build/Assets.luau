--!strict
-- ReplicatedStorage content: the carried parcel template and the remote events.

local B = require("./lib/Builder")
local Palette = require("../src/shared/Palette")

local V3 = B.Vector3
local CF = B.CFrame
local Enum = B.Enum

local Assets = {}

local BOLD = B.Font.new("rbxasset://fonts/families/BuilderSans.json", Enum.FontWeight.Bold)

-- Parcel carried by its twine loop: X = sideways, Y = up, Z = forward. The loop top is at
-- local (0, 0.7, 0) relative to the box centre; the server welds that point to the hand.
local function parcel(parent: any)
	local m = B.model(parent, "Parcel")
	local origin = CF.new(0, 50, 0)
	local detail = { collide = false, query = false, shadow = false }
	local box = B.part(m, "Box", V3.new(0.8, 0.85, 1.2), origin, Palette.Cardboard, "Cardboard", { collide = false, query = false })
	local parts = {
		B.part(m, "TwineAround", V3.new(0.82, 0.87, 0.05), origin, "C9B48A", "Fabric", detail),
		B.part(m, "TwineAlong", V3.new(0.05, 0.87, 1.22), origin, "C9B48A", "Fabric", detail),
		B.part(m, "LoopFront", V3.new(0.04, 0.28, 0.04), origin * CF.new(0, 0.565, -0.12), "C9B48A", "Fabric", detail),
		B.part(m, "LoopBack", V3.new(0.04, 0.28, 0.04), origin * CF.new(0, 0.565, 0.12), "C9B48A", "Fabric", detail),
		B.part(m, "LoopTop", V3.new(0.04, 0.04, 0.28), origin * CF.new(0, 0.7, 0), "C9B48A", "Fabric", detail),
	}
	local label = B.part(m, "Label", V3.new(0.02, 0.36, 0.56), origin * CF.new(0.41, 0.05, 0.1), "E9E4D8", "SmoothPlastic", detail)
	table.insert(parts, label)
	local gui = B.new("SurfaceGui", {
		Name = "Address",
		Face = Enum.NormalId.Right,
		SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud,
		PixelsPerStud = 100,
		LightInfluence = 1,
	}, label)
	B.new("TextLabel", {
		Name = "Text",
		Size = B.UDim2.fromScale(1, 1),
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		Text = "APT ---",
		TextScaled = true,
		TextColor3 = "2B2620",
		FontFace = BOLD,
	}, gui)

	for _, p in { box, table.unpack(parts) } do
		B.set(p, { Anchored = false, Massless = true, CanTouch = false })
	end
	for _, p in parts do
		B.new("Weld", { Name = "Weld", Part0 = box, Part1 = p, C0 = box.CFrame:Inverse() * p.CFrame, C1 = CF.new() }, box)
	end
	B.set(m, { PrimaryPart = box })
	B.attachment(box, "GripAttachment", (origin * CF.new(0, 0.7, 0)).Position)
	return m
end

function Assets.build(game: any)
	local rs = game:GetService("ReplicatedStorage")
	local root = B.folder(rs, "MidnightDelivery")
	local assets = B.folder(root, "Assets")
	parcel(assets)
	local remotes = B.folder(root, "Remotes")
	B.new("RemoteEvent", { Name = "DeliveryFeedback" }, remotes) -- server -> client result toasts
	B.new("RemoteEvent", { Name = "ScareEvent" }, remotes) -- server -> client one-off scare
	return root
end

return Assets
