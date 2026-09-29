-- Courier device panel: one header row (also the open/close button) and one delivery card.
-- Instances live in StarterGui.MidnightHUD; this module only updates them.

local UserInputService = game:GetService("UserInputService")

local DeviceUI = {}
DeviceUI.__index = DeviceUI

function DeviceUI.new(hud: ScreenGui, Strings, Palette, Config)
	local self = setmetatable({}, DeviceUI)
	self.Strings = Strings
	self.Config = Config
	self.scale = hud:WaitForChild("Scale") :: UIScale
	self.frame = hud:WaitForChild("Device") :: Frame
	self.header = self.frame:WaitForChild("Header") :: TextButton
	self.divider = self.frame:WaitForChild("Divider") :: Frame
	self.body = self.frame:WaitForChild("Body") :: Frame
	self.summary = self.header:WaitForChild("Summary") :: TextLabel
	self.keyLabel = self.header:WaitForChild("Key"):WaitForChild("Label") :: TextLabel
	self.accent = Color3.fromHex(Palette.UiAccent)
	self.text = Color3.fromHex(Palette.UiText)
	self.isOpen = true
	self.small = false
	self.collapseToken = 0

	self.header.Activated:Connect(function()
		self:toggle()
	end)
	UserInputService.LastInputTypeChanged:Connect(function()
		self:_updateKey()
	end)
	self:_updateKey()
	return self
end

local function touchOnly(): boolean
	return UserInputService.TouchEnabled and not UserInputService.KeyboardEnabled
end

function DeviceUI:_updateKey()
	local S = self.Strings
	if touchOnly() then
		self.keyLabel.Text = if self.isOpen then S.KeyHide else S.KeyShow
	else
		self.keyLabel.Text = S.KeyDesktop
	end
end

function DeviceUI:setOpen(open: boolean)
	self.isOpen = open
	self.body.Visible = open
	self.divider.Visible = open
	self.summary.Visible = not open
	self.collapseToken += 1
	self:_updateKey()
end

function DeviceUI:toggle()
	self:setOpen(not self.isOpen)
end

-- New objective: show the card. On small screens it folds back to the header after a
-- few seconds so it never sits over the view for long.
function DeviceUI:objectiveChanged()
	self:setOpen(true)
	if self.small then
		self.collapseToken += 1
		local token = self.collapseToken
		task.delay(self.Config.UI.AutoCollapseSeconds, function()
			if self.collapseToken == token then
				self:setOpen(false)
			end
		end)
	end
end

-- Returns the UI scale so other HUD pieces can size themselves consistently.
function DeviceUI:layout(viewport: Vector2): number
	local scale = math.clamp(viewport.Y / 720, 0.85, 1)
	self.scale.Scale = scale
	local width = math.min(320, math.floor(viewport.X * 0.42 / scale))
	self.frame.Size = UDim2.fromOffset(width, 0)
	self.small = viewport.Y < self.Config.UI.SmallScreenHeight
	return scale
end

function DeviceUI:render(state)
	local S = self.Strings
	local body = self.body
	local meta = body:FindFirstChild("Meta") :: Frame
	local section = meta:FindFirstChild("Section") :: TextLabel
	local counter = meta:FindFirstChild("Counter") :: TextLabel
	local target = body:FindFirstChild("Target") :: TextLabel
	local instruction = body:FindFirstChild("Instruction") :: TextLabel
	local steps = body:FindFirstChild("Steps") :: Frame
	local earnings = body:FindFirstChild("Earnings") :: Frame

	if state.stage == "complete" then
		section.Text = S.ShiftCompleteSection
		counter.Text = S.counter(state.total, state.total)
		target.Text = S.AllDeliveredTarget
		instruction.Text = S.ReturnToCart
		steps.Visible = false
		earnings.Visible = true
		local value = earnings:FindFirstChild("Value") :: TextLabel
		value.Text = S.money(state.earnings)
		self.summary.Text = S.ShiftCompleteSection
		return
	end

	section.Text = S.CurrentDelivery
	counter.Text = S.counter(state.index, state.total)
	target.Text = S.apartment(state.target)
	instruction.Text = if state.hasParcel then S.deliverTo(state.target) else S.PickUp
	steps.Visible = true
	earnings.Visible = false
	self.summary.Text = S.apartmentShort(state.target)

	local pick = steps:FindFirstChild("StepPickup") :: TextLabel
	local deliver = steps:FindFirstChild("StepDeliver") :: TextLabel
	if state.hasParcel then
		pick.TextColor3, pick.TextTransparency = self.text, 0.55
		deliver.TextColor3, deliver.TextTransparency = self.accent, 0
	else
		pick.TextColor3, pick.TextTransparency = self.accent, 0
		deliver.TextColor3, deliver.TextTransparency = self.text, 0.6
	end
end

return DeviceUI
