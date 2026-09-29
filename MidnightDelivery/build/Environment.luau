--!strict
-- Lighting and service settings. Interior-only scene: no sun/moon contribution, low warm
-- ambient so shadows are readable, no fog/atmosphere/bloom, a gentle colour grade.

local B = require("./lib/Builder")

local Enum = B.Enum
local C3 = B.Color3

local Environment = {}

function Environment.build(game: any)
	local lighting = game:GetService("Lighting")
	B.set(lighting, {
		Technology = Enum.Technology.Future,
		ClockTime = 0,
		Brightness = 0, -- enclosed interior: no sky light may leak in
		Ambient = C3.fromRGB(34, 31, 28),
		OutdoorAmbient = C3.fromRGB(20, 22, 28),
		EnvironmentDiffuseScale = 0,
		EnvironmentSpecularScale = 0.25,
		ExposureCompensation = 0,
		GlobalShadows = true,
		FogStart = 0,
		FogEnd = 100000,
		ShadowSoftness = 0.2,
	})
	B.new("ColorCorrectionEffect", {
		Name = "Grade",
		Brightness = 0,
		Contrast = 0.04,
		Saturation = -0.06,
		TintColor = C3.fromRGB(255, 250, 245),
		Enabled = true,
	}, lighting)

	B.set(game:GetService("Workspace"), { StreamingEnabled = false })

	B.set(game:GetService("StarterPlayer"), {
		CameraMaxZoomDistance = 14, -- keeps the camera under the 11-stud ceiling
		CameraMinZoomDistance = 0.5,
		CharacterWalkSpeed = 12, -- a walking pace suits a quiet night shift
		LoadCharacterAppearance = true,
	})

	B.set(game:GetService("TextChatService"), {
		ChatVersion = Enum.ChatVersion.TextChatService,
	})
end

return Environment
