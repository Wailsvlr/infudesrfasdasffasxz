--!strict
-- Instance construction helpers for the Lune build.
-- Every property assignment goes through `B.new`/`B.set`, which records it so the
-- test suite can re-open the saved .rbxlx and prove each value survived serialization
-- (Lune silently drops non-serialized alias properties such as Attachment.Position).

local roblox = require("@lune/roblox")

local Instance = roblox.Instance
local Enum = roblox.Enum
local Vector3 = roblox.Vector3
local CFrame = roblox.CFrame
local Color3 = roblox.Color3

local B = {}

B.records = {} :: { { inst: any, prop: string, value: any } }

local SMOOTH_FACES = { "TopSurface", "BottomSurface", "LeftSurface", "RightSurface", "FrontSurface", "BackSurface" }

local function coerce(prop: string, value: any): any
	if prop == "Color" or prop == "BackgroundColor3" or prop == "TextColor3" or prop == "ImageColor3" then
		if type(value) == "string" then
			return Color3.fromHex(value)
		end
	elseif prop == "Material" and type(value) == "string" then
		return (Enum.Material :: any)[value]
	elseif prop == "CFrame" and typeof(value) == "Vector3" then
		return CFrame.new(value)
	end
	return value
end

function B.set(inst: any, props: { [string]: any }?)
	if not props then
		return inst
	end
	-- Name first so recorded paths are readable when a check fails.
	if props.Name then
		inst.Name = props.Name
	end
	for prop, value in props do
		if prop ~= "Parent" and prop ~= "Name" then
			local v = coerce(prop, value)
			inst[prop] = v
			table.insert(B.records, { inst = inst, prop = prop, value = v })
		end
	end
	return inst
end

function B.new(className: string, props: { [string]: any }?, parent: any?)
	local inst = Instance.new(className)
	B.set(inst, props)
	if parent then
		inst.Parent = parent
	end
	return inst
end

function B.folder(parent: any, name: string)
	return B.new("Folder", { Name = name }, parent)
end

function B.model(parent: any, name: string)
	return B.new("Model", { Name = name }, parent)
end

export type PartOpts = {
	collide: boolean?,
	shadow: boolean?,
	transparency: number?,
	shape: string?,
	reflectance: number?,
	query: boolean?,
	class: string?,
}

-- Anchored, smooth-surfaced part. `cf` may be a CFrame or a Vector3 position.
function B.part(parent: any, name: string, size: any, cf: any, color: string, material: string, opts: PartOpts?)
	local o: PartOpts = opts or {}
	local props: { [string]: any } = {
		Name = name,
		Anchored = true,
		Size = size,
		CFrame = cf,
		Color = color,
		Material = material,
		CanCollide = if o.collide == nil then true else o.collide,
		CastShadow = if o.shadow == nil then true else o.shadow,
		CanTouch = false,
		CanQuery = if o.query == nil then true else o.query,
		Transparency = o.transparency or 0,
		Reflectance = o.reflectance or 0,
	}
	for _, face in SMOOTH_FACES do
		props[face] = Enum.SurfaceType.Smooth
	end
	if o.shape then
		props.Shape = (Enum.PartType :: any)[o.shape]
	end
	return B.new(o.class or "Part", props, parent)
end

-- Axis-aligned part from two corners (world space).
function B.box(parent: any, name: string, min: any, max: any, color: string, material: string, opts: PartOpts?)
	local size = Vector3.new(math.abs(max.X - min.X), math.abs(max.Y - min.Y), math.abs(max.Z - min.Z))
	local center = (min + max) / 2
	return B.part(parent, name, size, CFrame.new(center), color, material, opts)
end

-- A local building frame. `u` is the viewer's right, `v` is up, `w` is depth into the
-- surface (negative w sticks out toward the viewer). Parts built through a frame face
-- the viewer with their Front (-Z) face, so SurfaceGuis use Face = Front.
export type Frame = {
	cf: any,
	box: (self: Frame, parent: any, name: string, u0: number, v0: number, w0: number, u1: number, v1: number, w1: number, color: string, material: string, opts: PartOpts?) -> any,
	at: (self: Frame, u: number, v: number, w: number) -> any,
}

local FrameMeta = {}
FrameMeta.__index = FrameMeta

function FrameMeta.at(self: any, u: number, v: number, w: number)
	return self.cf * CFrame.new(-u, v, w)
end

function FrameMeta.box(self: any, parent: any, name: string, u0: number, v0: number, w0: number, u1: number, v1: number, w1: number, color: string, material: string, opts: PartOpts?)
	local size = Vector3.new(math.abs(u1 - u0), math.abs(v1 - v0), math.abs(w1 - w0))
	local cf = self:at((u0 + u1) / 2, (v0 + v1) / 2, (w0 + w1) / 2)
	return B.part(parent, name, size, cf, color, material, opts)
end

-- origin: point on the wall face at floor level; yaw: rotation so local -Z faces the viewer.
function B.frame(origin: any, yaw: number): Frame
	return (setmetatable({ cf = CFrame.new(origin) * CFrame.Angles(0, yaw, 0) }, FrameMeta) :: any) :: Frame
end

-- Attachment placed at a world-space point inside `part` (Attachment.CFrame is the
-- serialized property; Position is not saved by the file format).
function B.attachment(part: any, name: string, worldPos: any)
	local localCf = part.CFrame:Inverse() * CFrame.new(worldPos)
	return B.new("Attachment", { Name = name, CFrame = CFrame.new(localCf.Position) }, part)
end

function B.lerpHex(a: string, b: string, t: number): string
	return Color3.fromHex(a):Lerp(Color3.fromHex(b), t):ToHex()
end

B.Vector3 = Vector3
B.CFrame = CFrame
B.UDim2 = roblox.UDim2
B.Vector2 = roblox.Vector2
B.UDim = roblox.UDim
B.Font = roblox.Font
B.Color3 = Color3
B.Enum = Enum

return B
