-- Midnight Delivery — tunable gameplay settings (first floor slice).
-- Pure data: no Roblox services, so the test suite can load it outside Studio.

local Config = {
	Night = 1,

	-- Earnings added once per successful delivery.
	RewardPerDelivery = 25,

	-- One shift = these deliveries, in order. Uses all three doors.
	Route = { "102", "103", "101" },

	-- Apartments that exist on this floor (must match the door models' ApartmentId).
	Apartments = { "101", "102", "103" },

	-- Which delivery of the shift plays the small scare (index into Route; 0 = never).
	-- It plays at most once per shift.
	ScareDeliveryIndex = 2,

	-- Server-side validation. Prompts activate at 6-7 studs; this adds lag slack.
	InteractMaxDistance = 10,
	ActionCooldown = 0.35, -- seconds between accepted requests per player

	-- Where the parcel's twine loop sits relative to the hand (studs, hand space).
	ParcelGrip = { R15 = { 0, -0.1, 0 }, R6 = { 0, -1.0, 0 } },

	Elevator = {
		OpenDelay = 1.2, -- after a character spawns in the cab
		DoorTweenTime = 1.4,
		AutoCloseAfter = 12, -- seconds with nobody in the cab or doorway
		CallMaxDistance = 9,
	},

	Scare = {
		KnockDelay = 0.9, -- after the delivery is accepted
		SecondSoundDistance = 13, -- player must walk this far from the door
		SecondSoundTimeout = 90, -- give up if they never walk away
	},

	UI = {
		MarkerHideDistance = 9, -- target marker hides when the prompt takes over
		ToastSeconds = 2.4,
		AutoCollapseSeconds = 6, -- small screens only
		SmallScreenHeight = 500,
	},

	-- Audio asset ids ("rbxassetid://..."). None are bundled: every slot is empty and the
	-- game skips silent sounds. See docs/ASSETS.md.
	Sounds = {
		Knock = "",
		Thud = "",
		Ding = "",
		ElevatorDoor = "",
		Delivered = "",
		Pickup = "",
	},
}

return Config
