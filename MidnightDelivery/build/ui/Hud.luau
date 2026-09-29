--!strict
-- HUD built as editable instances (StarterGui) plus billboard templates
-- (ReplicatedStorage.MidnightDelivery.UI). Layout follows docs/ART_DIRECTION.md §5-6.
-- Client scripts only change text, visibility, colours and scale; they never rebuild it.

local B = require("../lib/Builder")
local Palette = require("../../src/shared/Palette")

local Enum = B.Enum
local UDim2 = B.UDim2
local UDim = B.UDim

local Hud = {}

local FAMILY = "rbxasset://fonts/families/BuilderSans.json"
local FONT = {
	Regular = B.Font.new(FAMILY, Enum.FontWeight.Regular),
	Medium = B.Font.new(FAMILY, Enum.FontWeight.Medium),
	Bold = B.Font.new(FAMILY, Enum.FontWeight.Bold),
}

local function frame(parent: any, name: string, props: { [string]: any })
	local base: { [string]: any } = { Name = name, BorderSizePixel = 0, BackgroundTransparency = 1 }
	for k, v in props do
		base[k] = v
	end
	return B.new("Frame", base, parent)
end

local function text(parent: any, name: string, value: string, size: number, weight: string, color: string, props: { [string]: any }?)
	local base: { [string]: any } = {
		Name = name,
		BorderSizePixel = 0,
		BackgroundTransparency = 1,
		Text = value,
		TextSize = size,
		FontFace = (FONT :: any)[weight],
		TextColor3 = color,
		TextXAlignment = Enum.TextXAlignment.Left,
		TextYAlignment = Enum.TextYAlignment.Center,
		RichText = false,
	}
	for k, v in props or {} do
		base[k] = v
	end
	return B.new("TextLabel", base, parent)
end

local function corner(parent: any, px: number)
	return B.new("UICorner", { CornerRadius = UDim.new(0, px) }, parent)
end

local function stroke(parent: any, color: string, transparency: number?)
	return B.new("UIStroke", {
		Color = B.Color3.fromHex(color),
		Thickness = 1,
		Transparency = transparency or 0,
		ApplyStrokeMode = Enum.ApplyStrokeMode.Border,
	}, parent)
end

local function list(parent: any, direction: any, padding: number, props: { [string]: any }?)
	local base: { [string]: any } = {
		FillDirection = direction,
		SortOrder = Enum.SortOrder.LayoutOrder,
		Padding = UDim.new(0, padding),
	}
	for k, v in props or {} do
		base[k] = v
	end
	return B.new("UIListLayout", base, parent)
end

local function padding(parent: any, t: number, r: number, b: number, l: number)
	return B.new("UIPadding", {
		PaddingTop = UDim.new(0, t),
		PaddingRight = UDim.new(0, r),
		PaddingBottom = UDim.new(0, b),
		PaddingLeft = UDim.new(0, l),
	}, parent)
end

local function chip(parent: any, name: string, props: { [string]: any })
	local c = frame(parent, name, props)
	B.set(c, { BackgroundColor3 = Palette.UiSurface, BackgroundTransparency = 0.12 })
	corner(c, 6)
	stroke(c, Palette.UiLine, 0.2)
	return c
end

-- Top bar row: title (left) and shift earnings (right), inside Roblox's topbar safe area.
local function topbar(starterGui: any)
	local gui = B.new("ScreenGui", {
		Name = "TopbarHUD",
		ResetOnSpawn = false,
		ScreenInsets = Enum.ScreenInsets.TopbarSafeInsets,
		ZIndexBehavior = Enum.ZIndexBehavior.Sibling,
		DisplayOrder = 5,
	}, starterGui)

	local title = chip(gui, "Title", {
		AnchorPoint = B.Vector2.new(0, 0.5),
		Position = UDim2.new(0, 8, 0.5, 0),
		Size = UDim2.fromOffset(0, 38),
		AutomaticSize = Enum.AutomaticSize.X,
	})
	padding(title, 0, 12, 0, 12)
	list(title, Enum.FillDirection.Vertical, 0, { VerticalAlignment = Enum.VerticalAlignment.Center })
	text(title, "Brand", "MIDNIGHT DELIVERY", 14, "Bold", Palette.UiText, {
		LayoutOrder = 1,
		Size = UDim2.fromOffset(0, 16),
		AutomaticSize = Enum.AutomaticSize.X,
	})
	text(title, "Night", "NIGHT 01", 12, "Bold", Palette.UiAccent, {
		LayoutOrder = 2,
		Size = UDim2.fromOffset(0, 14),
		AutomaticSize = Enum.AutomaticSize.X,
	})

	local earnings = chip(gui, "Earnings", {
		AnchorPoint = B.Vector2.new(1, 0.5),
		Position = UDim2.new(1, -8, 0.5, 0),
		Size = UDim2.fromOffset(0, 38),
		AutomaticSize = Enum.AutomaticSize.X,
	})
	padding(earnings, 0, 14, 0, 12)
	list(earnings, Enum.FillDirection.Horizontal, 10, { VerticalAlignment = Enum.VerticalAlignment.Center })
	text(earnings, "Label", "SHIFT EARNINGS", 11, "Bold", Palette.UiText, {
		LayoutOrder = 1,
		TextTransparency = 0.4,
		Size = UDim2.fromOffset(0, 16),
		AutomaticSize = Enum.AutomaticSize.X,
	})
	text(earnings, "Value", "$0", 17, "Bold", Palette.UiText, {
		LayoutOrder = 2,
		Size = UDim2.fromOffset(0, 20),
		AutomaticSize = Enum.AutomaticSize.X,
	})
	return gui
end

local function device(gui: any)
	local d = frame(gui, "Device", {
		AnchorPoint = B.Vector2.new(1, 0),
		Position = UDim2.new(1, -16, 0, 12),
		Size = UDim2.fromOffset(320, 0),
		AutomaticSize = Enum.AutomaticSize.Y,
		BackgroundColor3 = Palette.UiSurface,
		BackgroundTransparency = 0.06,
	})
	corner(d, 6)
	stroke(d, Palette.UiLine, 0)
	list(d, Enum.FillDirection.Vertical, 0)

	-- Header doubles as the open/close button (mouse and touch). Desktop also uses Tab.
	local header = B.new("TextButton", {
		Name = "Header",
		LayoutOrder = 1,
		Size = UDim2.new(1, 0, 0, 36),
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		Text = "",
		FontFace = FONT.Bold,
		AutoButtonColor = false,
		Selectable = false,
	}, d)
	frame(header, "Dot", {
		AnchorPoint = B.Vector2.new(0, 0.5),
		Position = UDim2.new(0, 14, 0.5, 0),
		Size = UDim2.fromOffset(6, 6),
		BackgroundColor3 = Palette.UiAccent,
		BackgroundTransparency = 0,
	})
	corner(header:FindFirstChild("Dot"), 3)
	text(header, "Title", "COURIER DEVICE", 12, "Bold", Palette.UiText, {
		Position = UDim2.fromOffset(28, 0),
		Size = UDim2.new(1, -150, 1, 0),
		TextTransparency = 0.35,
		TextTruncate = Enum.TextTruncate.AtEnd,
	})
	text(header, "Summary", "Apt 102", 12, "Bold", Palette.UiAccent, {
		AnchorPoint = B.Vector2.new(1, 0.5),
		Position = UDim2.new(1, -62, 0.5, 0),
		Size = UDim2.fromOffset(110, 20),
		TextXAlignment = Enum.TextXAlignment.Right,
		TextTruncate = Enum.TextTruncate.AtEnd,
		Visible = false,
	})
	local key = frame(header, "Key", {
		AnchorPoint = B.Vector2.new(1, 0.5),
		Position = UDim2.new(1, -12, 0.5, 0),
		Size = UDim2.fromOffset(42, 20),
		BackgroundColor3 = Palette.UiSurfaceRaised,
		BackgroundTransparency = 0,
	})
	corner(key, 4)
	stroke(key, Palette.UiLine, 0)
	text(key, "Label", "TAB", 11, "Bold", Palette.UiText, {
		Size = UDim2.fromScale(1, 1),
		TextXAlignment = Enum.TextXAlignment.Center,
		TextTransparency = 0.25,
	})

	frame(d, "Divider", {
		LayoutOrder = 2,
		Size = UDim2.new(1, 0, 0, 1),
		BackgroundColor3 = Palette.UiLine,
		BackgroundTransparency = 0,
	})

	local body = frame(d, "Body", {
		LayoutOrder = 3,
		Size = UDim2.new(1, 0, 0, 0),
		AutomaticSize = Enum.AutomaticSize.Y,
	})
	padding(body, 12, 16, 14, 16)
	list(body, Enum.FillDirection.Vertical, 4)

	local meta = frame(body, "Meta", { LayoutOrder = 1, Size = UDim2.new(1, 0, 0, 16) })
	text(meta, "Section", "CURRENT DELIVERY", 12, "Bold", Palette.UiAccent, { Size = UDim2.fromScale(1, 1) })
	text(meta, "Counter", "1 / 3", 12, "Medium", Palette.UiText, {
		Size = UDim2.fromScale(1, 1),
		TextXAlignment = Enum.TextXAlignment.Right,
		TextTransparency = 0.45,
	})
	text(body, "Target", "Apartment 102", 22, "Bold", Palette.UiText, {
		LayoutOrder = 2,
		Size = UDim2.new(1, 0, 0, 28),
		TextTruncate = Enum.TextTruncate.AtEnd,
	})
	text(body, "Instruction", "Pick up the parcel.", 15, "Regular", Palette.UiText, {
		LayoutOrder = 3,
		Size = UDim2.new(1, 0, 0, 0),
		AutomaticSize = Enum.AutomaticSize.Y,
		TextWrapped = true,
		TextTransparency = 0.12,
		TextYAlignment = Enum.TextYAlignment.Top,
	})
	frame(body, "Spacer", { LayoutOrder = 4, Size = UDim2.new(1, 0, 0, 6) })

	local steps = frame(body, "Steps", { LayoutOrder = 5, Size = UDim2.new(1, 0, 0, 16) })
	list(steps, Enum.FillDirection.Horizontal, 16, { VerticalAlignment = Enum.VerticalAlignment.Center })
	text(steps, "StepPickup", "1  PICK UP", 11, "Bold", Palette.UiAccent, {
		LayoutOrder = 1,
		Size = UDim2.fromOffset(0, 16),
		AutomaticSize = Enum.AutomaticSize.X,
	})
	text(steps, "StepDeliver", "2  DELIVER", 11, "Bold", Palette.UiText, {
		LayoutOrder = 2,
		Size = UDim2.fromOffset(0, 16),
		AutomaticSize = Enum.AutomaticSize.X,
		TextTransparency = 0.6,
	})

	local earn = frame(body, "Earnings", { LayoutOrder = 6, Size = UDim2.new(1, 0, 0, 20), Visible = false })
	text(earn, "Label", "Shift earnings", 13, "Regular", Palette.UiText, { Size = UDim2.fromScale(1, 1), TextTransparency = 0.35 })
	text(earn, "Value", "$0", 15, "Bold", Palette.UiAccent, {
		Size = UDim2.fromScale(1, 1),
		TextXAlignment = Enum.TextXAlignment.Right,
	})
	return d
end

local function toast(gui: any)
	-- CanvasGroup so the whole card can fade with GroupTransparency.
	local t = B.new("CanvasGroup", {
		Name = "Toast",
		AnchorPoint = B.Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(0.5, 0.8),
		Size = UDim2.fromOffset(302, 0),
		AutomaticSize = Enum.AutomaticSize.Y,
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		GroupTransparency = 0,
		Visible = false,
	}, gui)
	padding(t, 1, 1, 1, 1) -- keeps the 1px border stroke inside the canvas
	local panel = frame(t, "Panel", {
		Size = UDim2.new(1, 0, 0, 0),
		AutomaticSize = Enum.AutomaticSize.Y,
		BackgroundColor3 = Palette.UiSurface,
		BackgroundTransparency = 0.06,
	})
	corner(panel, 6)
	stroke(panel, Palette.UiLine, 0)
	frame(panel, "Accent", {
		Position = UDim2.fromOffset(12, 12),
		Size = UDim2.new(0, 3, 1, -24),
		BackgroundColor3 = Palette.UiAccent,
		BackgroundTransparency = 0,
	})
	local content = frame(panel, "Content", { Size = UDim2.new(1, 0, 0, 0), AutomaticSize = Enum.AutomaticSize.Y })
	padding(content, 11, 16, 12, 26)
	list(content, Enum.FillDirection.Vertical, 2)
	text(content, "Title", "Delivery complete", 17, "Bold", Palette.UiText, { LayoutOrder = 1, Size = UDim2.new(1, 0, 0, 22) })
	text(content, "Detail", "+$25 · Apartment 102", 14, "Medium", Palette.UiAccent, {
		LayoutOrder = 2,
		Size = UDim2.new(1, 0, 0, 0),
		AutomaticSize = Enum.AutomaticSize.Y,
		TextWrapped = true,
	})
	return t
end

local function promptTemplate(parent: any)
	local bb = B.new("BillboardGui", {
		Name = "PromptBillboard",
		Size = UDim2.fromOffset(236, 52),
		AlwaysOnTop = true,
		LightInfluence = 0,
		Active = true,
		ClipsDescendants = false,
		MaxDistance = 40,
		ResetOnSpawn = false,
	}, parent)
	local btn = B.new("TextButton", {
		Name = "Button",
		Size = UDim2.fromScale(1, 1),
		BackgroundColor3 = Palette.UiSurface,
		BackgroundTransparency = 0.06,
		BorderSizePixel = 0,
		Text = "",
		FontFace = FONT.Bold,
		AutoButtonColor = false,
	}, bb)
	corner(btn, 6)
	stroke(btn, Palette.UiLine, 0)
	local key = frame(btn, "Key", {
		AnchorPoint = B.Vector2.new(0, 0.5),
		Position = UDim2.new(0, 10, 0.5, 0),
		Size = UDim2.fromOffset(32, 32),
		BackgroundColor3 = Palette.UiSurfaceRaised,
		BackgroundTransparency = 0,
	})
	corner(key, 4)
	stroke(key, Palette.UiAccent, 0.25)
	text(key, "Label", "E", 15, "Bold", Palette.UiText, {
		Size = UDim2.fromScale(1, 1),
		TextXAlignment = Enum.TextXAlignment.Center,
	})
	text(btn, "Object", "Apartment 102", 12, "Medium", Palette.UiText, {
		Position = UDim2.fromOffset(52, 8),
		Size = UDim2.new(1, -62, 0, 14),
		TextTransparency = 0.4,
		TextTruncate = Enum.TextTruncate.AtEnd,
	})
	text(btn, "Action", "Deliver parcel", 16, "Bold", Palette.UiText, {
		Position = UDim2.fromOffset(52, 23),
		Size = UDim2.new(1, -62, 0, 20),
		TextTruncate = Enum.TextTruncate.AtEnd,
	})
	frame(btn, "Progress", {
		AnchorPoint = B.Vector2.new(0, 1),
		Position = UDim2.new(0, 8, 1, -3),
		Size = UDim2.new(0, 0, 0, 2),
		BackgroundColor3 = Palette.UiAccent,
		BackgroundTransparency = 0,
	})
	return bb
end

local function markerTemplate(parent: any)
	local bb = B.new("BillboardGui", {
		Name = "TargetMarker",
		Size = UDim2.fromOffset(64, 40),
		AlwaysOnTop = true,
		LightInfluence = 0,
		MaxDistance = 120,
		ResetOnSpawn = false,
	}, parent)
	local diamond = frame(bb, "Diamond", {
		AnchorPoint = B.Vector2.new(0.5, 0),
		Position = UDim2.new(0.5, 0, 0, 3),
		Size = UDim2.fromOffset(11, 11),
		Rotation = 45,
		BackgroundColor3 = Palette.UiAccent,
		BackgroundTransparency = 0,
	})
	B.new("UIStroke", {
		Color = B.Color3.fromHex(Palette.UiSurface),
		Thickness = 2,
		Transparency = 0.2,
		ApplyStrokeMode = Enum.ApplyStrokeMode.Border,
	}, diamond)
	text(bb, "Label", "102", 14, "Bold", Palette.UiAccent, {
		Position = UDim2.fromOffset(0, 20),
		Size = UDim2.new(1, 0, 0, 18),
		TextXAlignment = Enum.TextXAlignment.Center,
		TextStrokeColor3 = B.Color3.fromHex(Palette.UiSurface),
		TextStrokeTransparency = 0.35,
	})
	return bb
end

function Hud.build(game: any, sharedRoot: any)
	local starterGui = game:GetService("StarterGui")
	topbar(starterGui)
	local gui = B.new("ScreenGui", {
		Name = "MidnightHUD",
		ResetOnSpawn = false,
		ScreenInsets = Enum.ScreenInsets.CoreUISafeInsets,
		ZIndexBehavior = Enum.ZIndexBehavior.Sibling,
		DisplayOrder = 4,
	}, starterGui)
	B.new("UIScale", { Name = "Scale", Scale = 1 }, gui)
	device(gui)
	toast(gui)

	local templates = B.folder(sharedRoot, "UI")
	promptTemplate(templates)
	markerTemplate(templates)
end

return Hud
