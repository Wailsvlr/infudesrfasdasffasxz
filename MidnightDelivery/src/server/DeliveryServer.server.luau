-- Wires the delivery rules (DeliveryService) to the place: prompts, characters, the
-- carried parcel and the remotes. All payouts and state changes happen here, on the server.

local CollectionService = game:GetService("CollectionService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local shared = ReplicatedStorage:WaitForChild("MidnightDelivery")
local Config = require(shared:WaitForChild("Config"))
local Logic = require(shared:WaitForChild("DeliveryLogic"))
local DeliveryService = require(script.Parent:WaitForChild("DeliveryService"))

local remotes = shared:WaitForChild("Remotes")
local feedbackRemote = remotes:WaitForChild("DeliveryFeedback")
local scareRemote = remotes:WaitForChild("ScareEvent")
local parcelTemplate = shared:WaitForChild("Assets"):WaitForChild("Parcel")

-- Edit-mode helpers (the R15 scale reference) are not part of the game.
local sceneRoot = workspace:FindFirstChild("MidnightDelivery")
local devOnly = sceneRoot and sceneRoot:FindFirstChild("_DevOnly")
if devOnly then
	devOnly:Destroy()
end

local carried: { [Player]: Model } = {}

local function rootPosition(player: Player): Vector3?
	local character = player.Character
	if not character then
		return nil
	end
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	local root = character:FindFirstChild("HumanoidRootPart")
	if not humanoid or not root or humanoid.Health <= 0 then
		return nil
	end
	return (root :: BasePart).Position
end

local function removeParcel(player: Player)
	local parcel = carried[player]
	carried[player] = nil
	if parcel then
		parcel:Destroy()
	end
end

local function attachParcel(player: Player, apartmentId: string): boolean
	removeParcel(player)
	local character = player.Character
	if not character then
		return false
	end
	local hand = character:FindFirstChild("RightHand") or character:FindFirstChild("Right Arm")
	if not hand or not hand:IsA("BasePart") then
		return false
	end

	local parcel = parcelTemplate:Clone()
	parcel.Name = "CarriedParcel"
	local box = parcel.PrimaryPart :: BasePart
	local label = parcel:FindFirstChild("Label")
	local address = label and label:FindFirstChild("Address")
	local text = address and address:FindFirstChild("Text")
	if text then
		text.Text = "APT " .. apartmentId
	end

	-- Put the twine loop (GripAttachment) in the hand, then weld. Parts are massless and
	-- non-colliding, so carrying never changes how the character moves.
	local g = if hand.Name == "RightHand" then Config.ParcelGrip.R15 else Config.ParcelGrip.R6
	local gripInHand = hand.CFrame * CFrame.new(g[1], g[2], g[3])
	local grip = box:FindFirstChild("GripAttachment") :: Attachment
	parcel:PivotTo(gripInHand * grip.CFrame:Inverse())

	local weld = Instance.new("Weld")
	weld.Name = "HandWeld"
	weld.Part0 = hand
	weld.Part1 = box
	weld.C0 = hand.CFrame:Inverse() * box.CFrame
	weld.Parent = box

	parcel.Parent = character
	carried[player] = parcel
	return true
end

local service = DeliveryService.new({
	config = Config,
	logic = Logic,
	now = os.clock,
	rootPosition = rootPosition,
	attachParcel = attachParcel,
	removeParcel = removeParcel,
	publish = function(player, snapshot)
		for name, value in snapshot do
			player:SetAttribute(name, value)
		end
	end,
	feedback = function(player, payload)
		feedbackRemote:FireClient(player, payload)
	end,
	scare = function(player, apartmentId)
		scareRemote:FireClient(player, apartmentId)
	end,
})

local function promptAt(part: Instance): (ProximityPrompt?, Attachment?)
	local attachment = part:FindFirstChild("PromptAttachment")
	if not attachment then
		return nil, nil
	end
	return attachment:FindFirstChildOfClass("ProximityPrompt"), attachment :: Attachment
end

for _, point in CollectionService:GetTagged("MD_PickupPoint") do
	local prompt, attachment = promptAt(point)
	if prompt and attachment then
		prompt.Triggered:Connect(function(player)
			service:requestPickup(player, attachment.WorldPosition)
		end)
	end
end

for _, door in CollectionService:GetTagged("MD_ApartmentDoor") do
	local apartmentId = door:GetAttribute("ApartmentId")
	local prompt, attachment = promptAt(door)
	if prompt and attachment and type(apartmentId) == "string" then
		prompt.Triggered:Connect(function(player)
			service:requestDeliver(player, apartmentId, attachment.WorldPosition)
		end)
	end
end

local function onCharacter(player: Player, character: Model)
	service:characterReset(player)
	local humanoid = character:WaitForChild("Humanoid", 10)
	if humanoid then
		(humanoid :: Humanoid).Died:Connect(function()
			service:characterReset(player)
		end)
	end
end

local function onPlayer(player: Player)
	service:addPlayer(player)
	player.CharacterAdded:Connect(function(character)
		onCharacter(player, character)
	end)
	player.CharacterRemoving:Connect(function()
		service:characterReset(player)
	end)
	if player.Character then
		onCharacter(player, player.Character)
	end
end

Players.PlayerAdded:Connect(onPlayer)
Players.PlayerRemoving:Connect(function(player)
	service:removePlayer(player)
end)
for _, player in Players:GetPlayers() do
	onPlayer(player)
end
