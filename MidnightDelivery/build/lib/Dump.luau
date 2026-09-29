--!strict
-- Exports scene geometry, lights, surface text and UI trees to JSON for the approximate
-- preview renderer in tools/preview. This is a checking aid only, not a Roblox render.

local serde = require("@lune/serde")

local Dump = {}

local function hex(c: any): string
	return c:ToHex()
end

local function cframe(cf: any): { number }
	return { cf:GetComponents() }
end

local function worldCFrame(inst: any): any?
	if inst:IsA("BasePart") then
		return inst.CFrame
	elseif inst:IsA("Attachment") and inst.Parent and inst.Parent:IsA("BasePart") then
		return inst.Parent.CFrame * inst.CFrame
	end
	return nil
end

local function textLabels(gui: any): { any }
	local out = {}
	for _, d in gui:GetDescendants() do
		if d:IsA("TextLabel") then
			table.insert(out, {
				text = d.Text,
				size = d.TextSize,
				scaled = d.TextScaled,
				color = hex(d.TextColor3),
				bold = d.FontFace.Weight.Name,
				order = d.LayoutOrder,
			})
		end
	end
	table.sort(out, function(a, b)
		return a.order < b.order
	end)
	return out
end

function Dump.scene(workspace: any): string
	local parts, lights, guis = {}, {}, {}
	local index = {}
	for _, d in workspace:GetDescendants() do
		if d:IsA("BasePart") then
			table.insert(parts, {
				name = d.Name,
				path = d:GetFullName(),
				shape = if d:IsA("Part") then d.Shape.Name else "Block",
				size = { d.Size.X, d.Size.Y, d.Size.Z },
				cf = cframe(d.CFrame),
				color = hex(d.Color),
				material = d.Material.Name,
				transparency = d.Transparency,
				reflectance = d.Reflectance,
				shadow = d.CastShadow,
				collide = d.CanCollide,
			})
			index[d] = #parts
		end
	end
	for _, d in workspace:GetDescendants() do
		if d:IsA("Light") then
			local host = d.Parent
			local cf = host and worldCFrame(host)
			if cf then
				local entry: { [string]: any } = {
					class = d.ClassName,
					pos = { cf.Position.X, cf.Position.Y, cf.Position.Z },
					color = hex(d.Color),
					brightness = d.Brightness,
					range = d.Range,
					shadows = d.Shadows,
				}
				if d:IsA("SurfaceLight") or d:IsA("SpotLight") then
					entry.face = d.Face.Name
					entry.angle = d.Angle
					entry.cf = cframe(cf)
					if host:IsA("BasePart") then
						entry.size = { host.Size.X, host.Size.Y, host.Size.Z }
					end
				end
				table.insert(lights, entry)
			end
		elseif d:IsA("SurfaceGui") and d.Parent and index[d.Parent] then
			table.insert(guis, {
				part = index[d.Parent] - 1,
				face = d.Face.Name,
				lightInfluence = d.LightInfluence,
				labels = textLabels(d),
			})
		end
	end
	return serde.encode("json", { parts = parts, lights = lights, surfaceGuis = guis }, true)
end

local UI_PROPS = {
	"Size", "Position", "AnchorPoint", "AutomaticSize", "BackgroundColor3", "BackgroundTransparency",
	"Visible", "Text", "TextSize", "TextColor3", "TextTransparency", "TextXAlignment", "TextYAlignment",
	"TextWrapped", "TextTruncate", "Rotation", "LayoutOrder", "FillDirection", "Padding",
	"HorizontalAlignment", "VerticalAlignment", "PaddingTop", "PaddingBottom", "PaddingLeft",
	"PaddingRight", "CornerRadius", "Color", "Thickness", "Transparency", "ScreenInsets",
	"Scale", "GroupTransparency", "TextStrokeTransparency", "TextStrokeColor3",
}

local function encodeValue(v: any): any
	local t = typeof(v)
	if t == "UDim2" then
		return { v.X.Scale, v.X.Offset, v.Y.Scale, v.Y.Offset }
	elseif t == "UDim" then
		return { v.Scale, v.Offset }
	elseif t == "Vector2" then
		return { v.X, v.Y }
	elseif t == "Color3" then
		return v:ToHex()
	elseif t == "EnumItem" then
		return v.Name
	elseif t == "number" or t == "string" or t == "boolean" then
		return v
	end
	return nil
end

local function uiNode(inst: any): any
	local node: { [string]: any } = { class = inst.ClassName, name = inst.Name, props = {}, children = {} }
	for _, prop in UI_PROPS do
		local ok, value = pcall(function()
			return inst[prop]
		end)
		if ok and value ~= nil then
			node.props[prop] = encodeValue(value)
		end
	end
	local okFont, font = pcall(function()
		return inst.FontFace
	end)
	if okFont and font then
		node.props.FontWeight = font.Weight.Name
	end
	for _, child in inst:GetChildren() do
		table.insert(node.children, uiNode(child))
	end
	return node
end

function Dump.ui(starterGui: any, templates: any): string
	local screens = {}
	for _, gui in starterGui:GetChildren() do
		table.insert(screens, uiNode(gui))
	end
	local tpl = {}
	for _, t in templates:GetChildren() do
		table.insert(tpl, uiNode(t))
	end
	return serde.encode("json", { screens = screens, templates = tpl }, true)
end

return Dump
