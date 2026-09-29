-- Short result message (delivery complete / wrong apartment / ...). One at a time;
-- a new message replaces the current one.

local TweenService = game:GetService("TweenService")

local Toast = {}
Toast.__index = Toast

local FADE_IN = TweenInfo.new(0.15, Enum.EasingStyle.Quad, Enum.EasingDirection.Out)
local FADE_OUT = TweenInfo.new(0.25, Enum.EasingStyle.Quad, Enum.EasingDirection.In)
local REST = UDim2.fromScale(0.5, 0.8)
local START = UDim2.fromScale(0.5, 0.815)

function Toast.new(group: CanvasGroup, Palette, Config)
	local self = setmetatable({}, Toast)
	self.Config = Config
	self.group = group
	local panel = group:WaitForChild("Panel")
	local content = panel:WaitForChild("Content")
	self.accent = panel:WaitForChild("Accent") :: Frame
	self.title = content:WaitForChild("Title") :: TextLabel
	self.detail = content:WaitForChild("Detail") :: TextLabel
	self.colors = {
		accent = Color3.fromHex(Palette.UiAccent),
		warn = Color3.fromHex(Palette.UiWarn),
		text = Color3.fromHex(Palette.UiText),
	}
	self.token = 0
	return self
end

-- tone: "success" (amber detail) | "warn" (terracotta bar, plain detail) | "info"
function Toast:show(title: string, detail: string, tone: string)
	self.token += 1
	local token = self.token
	local c = self.colors
	self.title.Text = title
	self.detail.Text = detail
	self.accent.BackgroundColor3 = if tone == "warn" then c.warn else c.accent
	self.detail.TextColor3 = if tone == "success" then c.accent else c.text
	self.detail.TextTransparency = if tone == "success" then 0 else 0.2

	self.group.GroupTransparency = 1
	self.group.Position = START
	self.group.Visible = true
	TweenService:Create(self.group, FADE_IN, { GroupTransparency = 0, Position = REST }):Play()

	task.delay(self.Config.UI.ToastSeconds, function()
		if self.token ~= token then
			return
		end
		local out = TweenService:Create(self.group, FADE_OUT, { GroupTransparency = 1 })
		out.Completed:Once(function()
			if self.token == token then
				self.group.Visible = false
			end
		end)
		out:Play()
	end)
end

function Toast:layout(viewport: Vector2, scale: number)
	self.group.Size = UDim2.fromOffset(math.min(302, math.floor(viewport.X * 0.8 / scale)), 0)
end

return Toast
