-- Small amber marker over the current target (parcel cart or apartment door).
-- Hidden once the player is close enough for the interaction prompt to take over.

local CollectionService = game:GetService("CollectionService")
local Players = game:GetService("Players")
local RunService = game:GetService("RunService")

local TargetMarker = {}
TargetMarker.__index = TargetMarker

local function markerAttachment(part: Instance?): Attachment?
	local a = part and part:FindFirstChild("MarkerAttachment")
	return if a and a:IsA("Attachment") then a else nil
end

function TargetMarker.new(playerGui: PlayerGui, template: BillboardGui, Strings, Config)
	local self = setmetatable({}, TargetMarker)
	self.Strings = Strings
	self.Config = Config
	self.gui = template:Clone()
	self.gui.Enabled = false
	self.gui.Parent = playerGui
	self.label = self.gui:WaitForChild("Label") :: TextLabel
	self.anchor = nil :: Attachment?

	RunService.RenderStepped:Connect(function()
		self:_update()
	end)
	return self
end

function TargetMarker:_find(state): (Attachment?, string)
	if state.stage == "deliver" then
		for _, door in CollectionService:GetTagged("MD_ApartmentDoor") do
			if door:GetAttribute("ApartmentId") == state.target then
				return markerAttachment(door), state.target
			end
		end
		return nil, ""
	end
	return markerAttachment(CollectionService:GetTagged("MD_PickupPoint")[1]), self.Strings.MarkerParcels
end

function TargetMarker:setState(state)
	local anchor, text = self:_find(state)
	self.anchor = anchor
	self.gui.Adornee = anchor
	self.label.Text = text
end

function TargetMarker:_update()
	local character = Players.LocalPlayer.Character
	local root = character and character:FindFirstChild("HumanoidRootPart")
	local anchor = self.anchor
	if not anchor or not root or not root:IsA("BasePart") then
		self.gui.Enabled = false
		return
	end
	local distance = (root.Position - anchor.WorldPosition).Magnitude
	self.gui.Enabled = distance > self.Config.UI.MarkerHideDistance
end

return TargetMarker
