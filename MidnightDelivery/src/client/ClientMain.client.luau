-- Client entry point: mirrors the server state (Player attributes MD_*) into the HUD,
-- device, prompts and target marker, and plays local feedback for server results.

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local StarterGui = game:GetService("StarterGui")
local ContextActionService = game:GetService("ContextActionService")
local TweenService = game:GetService("TweenService")

if not game:IsLoaded() then
	game.Loaded:Wait() -- the map must be replicated before tagged parts are looked up
end

local player = Players.LocalPlayer
local shared = ReplicatedStorage:WaitForChild("MidnightDelivery")
local Config = require(shared:WaitForChild("Config"))
local Strings = require(shared:WaitForChild("Strings"))
local Palette = require(shared:WaitForChild("Palette"))
local remotes = shared:WaitForChild("Remotes")
local uiTemplates = shared:WaitForChild("UI")

local here = script.Parent
local DeviceUI = require(here:WaitForChild("DeviceUI"))
local Toast = require(here:WaitForChild("Toast"))
local PromptUI = require(here:WaitForChild("PromptUI"))
local TargetMarker = require(here:WaitForChild("TargetMarker"))
local WorldFx = require(here:WaitForChild("WorldFx"))
local ScareFx = require(here:WaitForChild("ScareFx"))

-- Roblox UI that would overlap the device (player list) or has no use here
-- (backpack: no tools; health: no damage). Chat stays on, in its default place.
task.spawn(function()
	for _ = 1, 10 do
		local ok = pcall(function()
			StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.PlayerList, false)
			StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.Backpack, false)
			StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.Health, false)
		end)
		if ok then
			break
		end
		task.wait(0.5)
	end
end)

do
	local missing = {}
	for key, id in Config.Sounds do
		if id == "" then
			table.insert(missing, key)
		end
	end
	if #missing > 0 then
		table.sort(missing)
		print("[MidnightDelivery] No audio assigned yet: " .. table.concat(missing, ", ") .. " (see docs/ASSETS.md)")
	end
end

local playerGui = player:WaitForChild("PlayerGui")
local hud = playerGui:WaitForChild("MidnightHUD") :: ScreenGui
local topbar = playerGui:WaitForChild("TopbarHUD") :: ScreenGui
local earningsValue = topbar:WaitForChild("Earnings"):WaitForChild("Value") :: TextLabel
local nightLabel = topbar:WaitForChild("Title"):WaitForChild("Night") :: TextLabel

local device = DeviceUI.new(hud, Strings, Palette, Config)
local toast = Toast.new(hud:WaitForChild("Toast") :: CanvasGroup, Palette, Config)
local marker = TargetMarker.new(playerGui, uiTemplates:WaitForChild("TargetMarker"), Strings, Config)
local world = WorldFx.new(Strings, Config, shared:WaitForChild("Assets"):WaitForChild("Parcel"))
PromptUI.start(playerGui, uiTemplates:WaitForChild("PromptBillboard"))

-- Layout follows the viewport (desktop, phone, window resize).
local function applyLayout()
	local camera = workspace.CurrentCamera
	if not camera then
		return
	end
	local viewport = camera.ViewportSize
	local scale = device:layout(viewport)
	toast:layout(viewport, scale)
end
local viewportConnection
local function watchCamera()
	if viewportConnection then
		viewportConnection:Disconnect()
	end
	local camera = workspace.CurrentCamera
	if camera then
		viewportConnection = camera:GetPropertyChangedSignal("ViewportSize"):Connect(applyLayout)
	end
	applyLayout()
end
workspace:GetPropertyChangedSignal("CurrentCamera"):Connect(watchCamera)
watchCamera()

local function readState()
	return {
		night = player:GetAttribute("MD_Night") or Config.Night,
		stage = player:GetAttribute("MD_Stage") or "pickup",
		target = player:GetAttribute("MD_Target") or Config.Route[1],
		index = player:GetAttribute("MD_Index") or 1,
		total = player:GetAttribute("MD_Total") or #Config.Route,
		hasParcel = player:GetAttribute("MD_HasParcel") == true,
		earnings = player:GetAttribute("MD_Earnings") or 0,
	}
end

local lastObjective: string? = nil
local hadParcel = false
local refreshQueued = false
local function refresh()
	refreshQueued = false
	local state = readState()
	device:render(state)
	marker:setState(state)
	world:applyPrompts(state)
	earningsValue.Text = Strings.money(state.earnings)
	nightLabel.Text = Strings.night(state.night)

	if state.hasParcel and not hadParcel then
		world:playSound("Pickup", world.pickupPoint)
	end
	hadParcel = state.hasParcel

	local objective = state.stage .. "|" .. tostring(state.target) .. "|" .. tostring(state.hasParcel)
	if lastObjective ~= nil and objective ~= lastObjective then
		device:objectiveChanged()
	end
	lastObjective = objective
end

-- The server sets several attributes in a row; coalesce them into one refresh.
player.AttributeChanged:Connect(function(name)
	if string.sub(name, 1, 3) == "MD_" and not refreshQueued then
		refreshQueued = true
		task.defer(refresh)
	end
end)
refresh()
device:objectiveChanged() -- show the first delivery on arrival

local function flashEarnings()
	earningsValue.TextColor3 = Color3.fromHex(Palette.UiAccent)
	TweenService:Create(earningsValue, TweenInfo.new(0.8, Enum.EasingStyle.Quad, Enum.EasingDirection.In), {
		TextColor3 = Color3.fromHex(Palette.UiText),
	}):Play()
end

remotes:WaitForChild("DeliveryFeedback").OnClientEvent:Connect(function(payload)
	if type(payload) ~= "table" then
		return
	end
	local T = Strings.Toast
	local kind = payload.kind
	if kind == "delivered" then
		world:dropParcel(payload.door)
		flashEarnings()
		if payload.complete then
			toast:show(T.ShiftComplete, Strings.ShiftEarnings .. " " .. Strings.money(payload.earnings), "success")
		else
			toast:show(T.Delivered, Strings.deliveredDetail(payload.reward, payload.door), "success")
		end
	elseif kind == "wrong_door" then
		toast:show(T.WrongDoor, Strings.wrongDoorDetail(payload.expected), "warn")
	elseif kind == "no_parcel" then
		toast:show(T.NoParcel, T.NoParcelDetail, "warn")
	elseif kind == "parcel_lost" then
		toast:show(T.ParcelLost, T.ParcelLostDetail, "warn")
	elseif kind == "restarted" then
		toast:show(T.Restarted, T.RestartedDetail, "info")
	end
end)

remotes:WaitForChild("ScareEvent").OnClientEvent:Connect(function(apartmentId)
	if type(apartmentId) == "string" then
		ScareFx.play(apartmentId, Config)
	end
end)

-- Tab toggles the device. ContextActionService ignores keys typed into chat/text boxes,
-- and the high priority keeps Tab ours now that the player list is disabled.
ContextActionService:BindActionAtPriority("MidnightDeliveryDevice", function(_, inputState)
	if inputState == Enum.UserInputState.Begin then
		device:toggle()
	end
	return Enum.ContextActionResult.Sink
end, false, Enum.ContextActionPriority.High.Value, Enum.KeyCode.Tab)
