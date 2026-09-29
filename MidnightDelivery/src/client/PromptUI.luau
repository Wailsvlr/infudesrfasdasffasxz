-- Custom ProximityPrompt look (prompts use Style = Custom, so Roblox draws nothing and the
-- interaction text is never shown twice). The pill sits on the prompt's attachment.
-- Touch/click on the pill works like pressing the key.

local ProximityPromptService = game:GetService("ProximityPromptService")
local TweenService = game:GetService("TweenService")
local UserInputService = game:GetService("UserInputService")

local PromptUI = {}

local EMPTY_BAR = UDim2.new(0, 0, 0, 2)
local FULL_BAR = UDim2.new(1, -16, 0, 2)

local function keyText(prompt: ProximityPrompt, inputType: Enum.ProximityPromptInputType): string
	if inputType == Enum.ProximityPromptInputType.Touch then
		return "TAP"
	elseif inputType == Enum.ProximityPromptInputType.Gamepad then
		return (string.gsub(prompt.GamepadKeyCode.Name, "^Button", ""))
	end
	local ok, s = pcall(function()
		return UserInputService:GetStringForKeyCode(prompt.KeyboardKeyCode)
	end)
	if not ok or s == nil or s == "" then
		s = prompt.KeyboardKeyCode.Name
	end
	return string.upper(s)
end

local function isPress(input: InputObject): boolean
	return input.UserInputType == Enum.UserInputType.Touch or input.UserInputType == Enum.UserInputType.MouseButton1
end

function PromptUI.start(playerGui: PlayerGui, template: BillboardGui)
	ProximityPromptService.PromptShown:Connect(function(prompt: ProximityPrompt, inputType)
		if prompt.Style ~= Enum.ProximityPromptStyle.Custom then
			return
		end
		local billboard = template:Clone()
		local button = billboard:WaitForChild("Button") :: TextButton
		local keyLabel = button:WaitForChild("Key"):WaitForChild("Label") :: TextLabel
		local objectLabel = button:WaitForChild("Object") :: TextLabel
		local actionLabel = button:WaitForChild("Action") :: TextLabel
		local progress = button:WaitForChild("Progress") :: Frame

		keyLabel.Text = keyText(prompt, inputType)
		keyLabel.TextSize = if #keyLabel.Text > 1 then 11 else 15

		local function sync()
			objectLabel.Text = prompt.ObjectText
			objectLabel.Visible = prompt.ObjectText ~= ""
			actionLabel.Text = prompt.ActionText
		end
		sync()

		billboard.Adornee = prompt.Parent
		billboard.Parent = playerGui

		local connections = {}
		local holdTween: Tween? = nil
		table.insert(connections, prompt:GetPropertyChangedSignal("ActionText"):Connect(sync))
		table.insert(connections, prompt:GetPropertyChangedSignal("ObjectText"):Connect(sync))
		table.insert(connections, prompt.PromptButtonHoldBegan:Connect(function()
			if prompt.HoldDuration <= 0 then
				return
			end
			progress.Size = EMPTY_BAR
			holdTween = TweenService:Create(progress, TweenInfo.new(prompt.HoldDuration, Enum.EasingStyle.Linear), { Size = FULL_BAR })
			holdTween:Play()
		end))
		table.insert(connections, prompt.PromptButtonHoldEnded:Connect(function()
			if holdTween then
				holdTween:Cancel()
				holdTween = nil
			end
			progress.Size = EMPTY_BAR
		end))
		table.insert(connections, button.InputBegan:Connect(function(input)
			if isPress(input) then
				prompt:InputHoldBegin()
			end
		end))
		table.insert(connections, button.InputEnded:Connect(function(input)
			if isPress(input) then
				prompt:InputHoldEnd()
			end
		end))

		prompt.PromptHidden:Once(function()
			for _, connection in connections do
				connection:Disconnect()
			end
			billboard:Destroy()
		end)
	end)
end

return PromptUI
