-- Local-only world feedback: which prompts this player can use, the parcel set down on
-- the doormat after a delivery, and small sounds. Nothing here changes game state.

local CollectionService = game:GetService("CollectionService")
local SoundService = game:GetService("SoundService")
local TweenService = game:GetService("TweenService")

local WorldFx = {}
WorldFx.__index = WorldFx

local function promptIn(part: Instance?): ProximityPrompt?
	local a = part and part:FindFirstChild("PromptAttachment")
	return a and a:FindFirstChildOfClass("ProximityPrompt")
end

function WorldFx.new(Strings, Config, parcelTemplate: Model)
	local self = setmetatable({}, WorldFx)
	self.Strings = Strings
	self.Config = Config
	self.parcelTemplate = parcelTemplate
	self.pickupPoint = CollectionService:GetTagged("MD_PickupPoint")[1]
	self.pickupPrompt = promptIn(self.pickupPoint)
	self.callPrompt = promptIn(CollectionService:GetTagged("MD_ElevatorCallButton")[1])
	self.elevator = CollectionService:GetTagged("MD_Elevator")[1]
	self.doors = {} :: { [string]: BasePart }
	self.doorPrompts = {} :: { [string]: ProximityPrompt }
	for _, door in CollectionService:GetTagged("MD_ApartmentDoor") do
		local id = door:GetAttribute("ApartmentId")
		if type(id) == "string" and door:IsA("BasePart") then
			self.doors[id] = door
			self.doorPrompts[id] = promptIn(door) :: ProximityPrompt
		end
	end

	if self.elevator then
		self.elevator:GetAttributeChangedSignal("DoorsOpen"):Connect(function()
			self:_applyCall()
		end)
	end
	self:_applyCall()
	return self
end

function WorldFx:_applyCall()
	if self.callPrompt and self.elevator then
		self.callPrompt.Enabled = self.elevator:GetAttribute("DoorsOpen") ~= true
	end
end

-- Only the interactions that make sense right now are shown to this player.
-- The server still validates every trigger.
function WorldFx:applyPrompts(state)
	local S = self.Strings
	if self.pickupPrompt then
		self.pickupPrompt.Enabled = state.stage ~= "deliver"
		self.pickupPrompt.ActionText = if state.stage == "complete" then S.Prompt.Restart else S.Prompt.Pickup
	end
	for _, prompt in self.doorPrompts do
		prompt.Enabled = state.stage == "deliver" and state.hasParcel
	end
end

function WorldFx:playSound(key: string, parent: Instance?)
	local id = self.Config.Sounds[key]
	if type(id) ~= "string" or id == "" then
		return
	end
	local sound = Instance.new("Sound")
	sound.SoundId = id
	sound.Volume = 0.5
	if parent then
		sound.Parent = parent
		sound:Play()
		sound.Ended:Once(function()
			sound:Destroy()
		end)
	else
		SoundService:PlayLocalSound(sound)
		sound:Destroy()
	end
end

-- The delivered parcel is set down on the doormat, then fades away.
function WorldFx:dropParcel(apartmentId: string)
	local door = self.doors[apartmentId]
	local drop = door and door:FindFirstChild("DropAttachment")
	if not door or not drop or not drop:IsA("Attachment") then
		return
	end
	self:playSound("Delivered", door)

	local ghost = self.parcelTemplate:Clone()
	ghost.Name = "DeliveredParcel"
	local parts = {}
	for _, d in ghost:GetDescendants() do
		if d:IsA("BasePart") then
			d.Anchored = true
			d.CanCollide = false
			d.CanQuery = false
			table.insert(parts, d)
		elseif d:IsA("TextLabel") then
			d.Text = "APT " .. apartmentId
		end
	end
	local yaw = math.rad(math.random(-12, 12))
	ghost:PivotTo(CFrame.new(drop.WorldPosition) * door.CFrame.Rotation * CFrame.Angles(0, yaw, 0))
	ghost.Parent = workspace

	task.delay(2.2, function()
		for _, d in ghost:GetDescendants() do
			if d:IsA("SurfaceGui") then
				d.Enabled = false
			end
		end
		local info = TweenInfo.new(0.6, Enum.EasingStyle.Quad, Enum.EasingDirection.In)
		for _, p in parts do
			TweenService:Create(p, info, { Transparency = 1 }):Play()
		end
		task.delay(0.7, function()
			ghost:Destroy()
		end)
	end)
end

return WorldFx
