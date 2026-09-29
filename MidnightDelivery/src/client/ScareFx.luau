-- The small scare, played only for the player who made the delivery:
--   1. a knock from behind the door (door leaf rattles),
--   2. a brief, irregular dip of the nearest ceiling light (under a second, no strobe),
--   3. once the player has walked away, one heavier thud from the same door.
-- Missing audio does not break it: the rattle and light still play.

local CollectionService = game:GetService("CollectionService")
local Players = game:GetService("Players")

local ScareFx = {}

-- {brightness multiplier, seconds}
local FLICKER = { { 0.35, 0.07 }, { 1, 0.12 }, { 0.55, 0.05 }, { 1, 0.4 }, { 0.25, 0.09 }, { 1, 0 } }

local function findDoor(apartmentId: string): BasePart?
	for _, door in CollectionService:GetTagged("MD_ApartmentDoor") do
		if door:GetAttribute("ApartmentId") == apartmentId and door:IsA("BasePart") then
			return door
		end
	end
	return nil
end

local function nearestLight(position: Vector3): Model?
	local best, bestDistance = nil, math.huge
	for _, model in CollectionService:GetTagged("MD_CorridorLight") do
		local housing = model:FindFirstChild("Housing")
		if housing and housing:IsA("BasePart") then
			local d = (housing.Position - position).Magnitude
			if d < bestDistance then
				best, bestDistance = model, d
			end
		end
	end
	return best
end

local function playSound(door: BasePart, name: string, id: string)
	local sound = door:FindFirstChild(name)
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

-- Moves every part of the door leaf a few hundredths of a stud toward the corridor.
local function rattle(door: BasePart, times: { number }, amplitude: number)
	local leaf = door.Parent
	if not leaf then
		return
	end
	local parts, origins = {}, {}
	for _, d in leaf:GetDescendants() do
		if d:IsA("BasePart") then
			table.insert(parts, d)
			origins[d] = d.CFrame
		end
	end
	local push = door.CFrame.LookVector * amplitude
	local start = os.clock()
	for _, t in times do
		local wait = t - (os.clock() - start)
		if wait > 0 then
			task.wait(wait)
		end
		for _, p in parts do
			p.CFrame = origins[p] + push
		end
		task.wait(0.05)
		for _, p in parts do
			p.CFrame = origins[p]
		end
	end
end

local function flicker(model: Model?)
	if not model then
		return
	end
	local housing = model:FindFirstChild("Housing")
	local anchor = housing and housing:FindFirstChild("LightAnchor")
	local light = anchor and anchor:FindFirstChildOfClass("PointLight")
	local diffuser = model:FindFirstChild("Diffuser")
	if not light then
		return
	end
	local base = model:GetAttribute("BaseBrightness")
	if type(base) ~= "number" then
		base = light.Brightness
	end
	local baseTransparency = if diffuser and diffuser:IsA("BasePart") then diffuser.Transparency else 0
	for _, step in FLICKER do
		light.Brightness = base * step[1]
		if diffuser and diffuser:IsA("BasePart") then
			diffuser.Transparency = baseTransparency + (1 - step[1]) * 0.6
		end
		if step[2] > 0 then
			task.wait(step[2])
		end
	end
	light.Brightness = base
	if diffuser and diffuser:IsA("BasePart") then
		diffuser.Transparency = baseTransparency
	end
end

local function rootPart(): BasePart?
	local character = Players.LocalPlayer.Character
	local root = character and character:FindFirstChild("HumanoidRootPart")
	return if root and root:IsA("BasePart") then root else nil
end

function ScareFx.play(apartmentId: string, Config)
	local door = findDoor(apartmentId)
	if not door then
		return
	end
	local scare, sounds = Config.Scare, Config.Sounds
	task.spawn(function()
		task.wait(scare.KnockDelay)
		playSound(door, "KnockSound", sounds.Knock)
		task.spawn(rattle, door, { 0, 0.2, 0.38 }, 0.03)
		task.wait(0.15)
		flicker(nearestLight(door.Position))

		local started = os.clock()
		local walkedAway = false
		while os.clock() - started < scare.SecondSoundTimeout do
			local root = rootPart()
			if not root then
				return -- died or left: the moment is gone
			end
			if (root.Position - door.Position).Magnitude > scare.SecondSoundDistance then
				walkedAway = true
				break
			end
			task.wait(0.2)
		end
		if walkedAway then
			task.wait(0.35)
			playSound(door, "ThudSound", sounds.Thud)
			rattle(door, { 0 }, 0.06)
		end
	end)
end

return ScareFx
