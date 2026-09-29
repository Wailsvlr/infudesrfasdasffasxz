-- Elevator doors (start area only; no floor travel in this slice).
-- Opens when a character spawns in the cab or someone presses the call button,
-- closes after nobody has been in the cab/doorway for a while, and reopens if someone
-- steps into the doorway while it is closing.

local CollectionService = game:GetService("CollectionService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local shared = ReplicatedStorage:WaitForChild("MidnightDelivery")
local Config = require(shared:WaitForChild("Config"))
local E = Config.Elevator

local elevator = CollectionService:GetTagged("MD_Elevator")[1]
local zone = CollectionService:GetTagged("MD_ElevatorZone")[1] :: BasePart?
local callPlate = CollectionService:GetTagged("MD_ElevatorCallButton")[1] :: BasePart?
if not elevator or not zone then
	warn("[MidnightDelivery] Elevator parts missing; elevator disabled.")
	return
end

type Door = { part: BasePart, closed: CFrame, open: CFrame }
local doors: { Door } = {}
for _, part in CollectionService:GetTagged("MD_ElevatorDoor") do
	if part:IsA("BasePart") then
		local offset = part:GetAttribute("OpenOffset")
		if typeof(offset) == "Vector3" then
			table.insert(doors, { part = part, closed = part.CFrame, open = part.CFrame + offset })
		end
	end
end

local indicator = elevator:FindFirstChild("FloorIndicator", true)
local ding = indicator and indicator:FindFirstChild("DingSound")
local doorSound: Instance? = nil
for _, door in doors do
	doorSound = doorSound or door.part:FindFirstChild("DoorSound")
end
local ring = callPlate and callPlate.Parent and callPlate.Parent:FindFirstChild("Ring") :: BasePart?
local ringIdle = ring and { material = ring.Material, color = ring.Color }

local function play(sound: Instance?, id: string)
	if not sound or not sound:IsA("Sound") then
		return
	end
	if id ~= "" then
		sound.SoundId = id
	end
	if sound.SoundId ~= "" then
		sound:Play()
	end
end

local status = "closed" -- closed | opening | open | closing
local lastOccupied = os.clock()

local function occupied(): boolean
	local zoneCf, half = zone.CFrame, zone.Size / 2
	for _, player in Players:GetPlayers() do
		local character = player.Character
		local root = character and character:FindFirstChild("HumanoidRootPart")
		if root and root:IsA("BasePart") then
			local p = zoneCf:PointToObjectSpace(root.Position)
			if math.abs(p.X) <= half.X and math.abs(p.Y) <= half.Y + 3 and math.abs(p.Z) <= half.Z then
				return true
			end
		end
	end
	return false
end

local function tweenDoors(toOpen: boolean): Tween?
	local info = TweenInfo.new(E.DoorTweenTime, Enum.EasingStyle.Quad, Enum.EasingDirection.InOut)
	local last
	for _, door in doors do
		last = TweenService:Create(door.part, info, { CFrame = if toOpen then door.open else door.closed })
		last:Play()
	end
	return last
end

local function setRing(active: boolean)
	if not ring or not ringIdle then
		return
	end
	ring.Material = if active then Enum.Material.Neon else ringIdle.material
	ring.Color = if active then Color3.fromHex("D6AD62") else ringIdle.color
end

local function open()
	if status == "open" or status == "opening" then
		return
	end
	status = "opening"
	elevator:SetAttribute("DoorsOpen", true)
	play(ding, Config.Sounds.Ding)
	play(doorSound, Config.Sounds.ElevatorDoor)
	local tween = tweenDoors(true)
	local function finished()
		if status == "opening" then
			status = "open"
			lastOccupied = os.clock()
			setRing(false)
		end
	end
	if tween then
		tween.Completed:Once(finished)
	else
		finished()
	end
end

local function close()
	if status ~= "open" then
		return
	end
	status = "closing"
	elevator:SetAttribute("DoorsOpen", false)
	play(doorSound, Config.Sounds.ElevatorDoor)
	local tween = tweenDoors(false)
	if tween then
		tween.Completed:Once(function(playbackState)
			if status == "closing" and playbackState == Enum.PlaybackState.Completed then
				status = "closed"
			end
		end)
	else
		status = "closed"
	end
end

elevator:SetAttribute("DoorsOpen", false)

-- Arrival: doors open shortly after a character appears.
local function onPlayer(player: Player)
	player.CharacterAdded:Connect(function()
		task.delay(E.OpenDelay, open)
	end)
	if player.Character then
		task.delay(E.OpenDelay, open)
	end
end
Players.PlayerAdded:Connect(onPlayer)
for _, player in Players:GetPlayers() do
	onPlayer(player)
end

-- Call button (validated on the server like every other interaction).
if callPlate then
	local attachment = callPlate:FindFirstChild("PromptAttachment")
	local prompt = attachment and attachment:FindFirstChildOfClass("ProximityPrompt")
	if prompt and attachment and attachment:IsA("Attachment") then
		prompt.Triggered:Connect(function(player)
			local character = player.Character
			local root = character and character:FindFirstChild("HumanoidRootPart")
			if not root or not root:IsA("BasePart") then
				return
			end
			if (root.Position - attachment.WorldPosition).Magnitude > E.CallMaxDistance then
				return
			end
			if status == "closed" or status == "closing" then
				setRing(true)
				open()
			end
		end)
	end
end

-- Auto-close / safety reopen.
task.spawn(function()
	while true do
		task.wait(0.2)
		local isOccupied = occupied()
		if status == "open" then
			if isOccupied then
				lastOccupied = os.clock()
			elseif os.clock() - lastOccupied > E.AutoCloseAfter then
				close()
			end
		elseif status == "closing" and isOccupied then
			open()
		end
	end
end)
