--!strict
-- Geometry constants for the first floor slice. Units are studs; floor top is y = 0,
-- the corridor runs along +X starting at the elevator wall (x = 0).

local Layout = {
	CorridorWidth = 13,
	CorridorHeight = 11,
	CorridorLength = 50,
	Wall = 1,

	WainscotTop = 3.6, -- chair rail height
	BaseboardHeight = 0.7,

	DoorWidth = 5,
	DoorHeight = 8.0,
	DoorRecess = 0.3,

	Elevator = {
		InnerMinX = -11,
		InnerMaxX = -1,
		InnerHalfZ = 5,
		CeilingY = 9.5,
		OpeningHalfWidth = 3,
		OpeningHeight = 8.5,
	},

	-- side: "north" wall is z = +6.5, "south" wall is z = -6.5
	Apartments = {
		{ id = "101", x = 15, side = "north", variant = "two" },
		{ id = "102", x = 27, side = "south", variant = "four" },
		{ id = "103", x = 39, side = "north", variant = "old" },
	},

	-- Ceiling fixtures along the corridor center line.
	LightX = { 6, 16, 26, 36, 46 },
	LightBrightness = { 1.0, 1.0, 1.0, 1.0, 0.8 },
	LightColor = { 255, 231, 196 },
	LightRange = 18,

	Pickup = { x = 4.2, z = -5.15 },

	-- Upper-wall pilasters (repeating wall module), kept clear of doors and props.
	PilasterX = { 10.5, 21, 32, 44.5 },

	-- Walk lane that must stay free of colliding props (checked by tests).
	WalkLaneHalfZ = 4.0,
}

return Layout
