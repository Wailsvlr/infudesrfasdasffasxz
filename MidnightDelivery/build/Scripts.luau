--!strict
-- Installs the Luau sources from src/ as Script/LocalScript/ModuleScript instances.

local fs = require("@lune/fs")
local B = require("./lib/Builder")

local Scripts = {}

-- { container path, class, name, source file }
Scripts.list = {
	{ "ReplicatedStorage/MidnightDelivery", "ModuleScript", "Config", "src/shared/Config.luau" },
	{ "ReplicatedStorage/MidnightDelivery", "ModuleScript", "Strings", "src/shared/Strings.luau" },
	{ "ReplicatedStorage/MidnightDelivery", "ModuleScript", "Palette", "src/shared/Palette.luau" },
	{ "ReplicatedStorage/MidnightDelivery", "ModuleScript", "DeliveryLogic", "src/shared/DeliveryLogic.luau" },
	{ "ServerScriptService/MidnightDelivery", "ModuleScript", "DeliveryService", "src/server/DeliveryService.luau" },
	{ "ServerScriptService/MidnightDelivery", "Script", "DeliveryServer", "src/server/DeliveryServer.server.luau" },
	{ "ServerScriptService/MidnightDelivery", "Script", "ElevatorServer", "src/server/ElevatorServer.server.luau" },
	{ "StarterPlayer/StarterPlayerScripts/MidnightDelivery", "LocalScript", "ClientMain", "src/client/ClientMain.client.luau" },
	{ "StarterPlayer/StarterPlayerScripts/MidnightDelivery", "ModuleScript", "DeviceUI", "src/client/DeviceUI.luau" },
	{ "StarterPlayer/StarterPlayerScripts/MidnightDelivery", "ModuleScript", "Toast", "src/client/Toast.luau" },
	{ "StarterPlayer/StarterPlayerScripts/MidnightDelivery", "ModuleScript", "PromptUI", "src/client/PromptUI.luau" },
	{ "StarterPlayer/StarterPlayerScripts/MidnightDelivery", "ModuleScript", "TargetMarker", "src/client/TargetMarker.luau" },
	{ "StarterPlayer/StarterPlayerScripts/MidnightDelivery", "ModuleScript", "WorldFx", "src/client/WorldFx.luau" },
	{ "StarterPlayer/StarterPlayerScripts/MidnightDelivery", "ModuleScript", "ScareFx", "src/client/ScareFx.luau" },
}

local function container(game: any, path: string): any
	local segments = string.split(path, "/")
	local current = game:GetService(segments[1])
	for i = 2, #segments do
		local name = segments[i]
		local child = current:FindFirstChild(name)
		if not child then
			local class = if name == "StarterPlayerScripts" or name == "StarterCharacterScripts" then name else "Folder"
			child = B.new(class, { Name = name }, current)
		end
		current = child
	end
	return current
end

function Scripts.install(game: any)
	container(game, "StarterPlayer/StarterCharacterScripts")
	for _, entry in Scripts.list do
		local parent = container(game, entry[1])
		local source = fs.readFile(entry[4])
		B.new(entry[2], { Name = entry[3], Source = source }, parent)
	end
end

return Scripts
