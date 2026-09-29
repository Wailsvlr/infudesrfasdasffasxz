--!strict
-- Single source for Midnight Delivery colors (hex strings, no leading #).
-- Used by the place build (map + UI) and by client scripts at runtime.

local Palette = {
	-- Environment
	WallUpper = "C8BDA8",
	WallLower = "AFA28A",
	Ceiling = "BEB4A2",
	Trim = "5B4A3C",
	Floor = "4E4036",
	Runner = "4A3431",
	Door = "30493E",
	DoorFaded = "384B42",
	Metal = "62666A",
	MetalDark = "4C5054",
	MetalLight = "7E8387",
	Brass = "A88A55",
	BrassTarnished = "8E7A52",
	Rust = "7A4A2E",
	Cardboard = "A9835A",
	Tape = "C7B08A",
	LampDiffuser = "E3D3B3",
	LampHousing = "D6CDBA",
	Grime = "3B342C",

	-- UI
	UiSurface = "202622",
	UiSurfaceRaised = "2A312C",
	UiLine = "3A433D",
	UiText = "EEE9DC",
	UiAccent = "D6AD62",
	UiWarn = "C9826B",
}

return table.freeze(Palette)
