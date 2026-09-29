-- Re-opens the saved MidnightDelivery.rbxlx from disk and checks it: every property the
-- build assigned survived serialization, the file matches the current sources, and the
-- scene meets the brief (scale, walk lane, lights, prompts, UI paths, English text).

local fs = require("@lune/fs")
local luau = require("@lune/luau")
local roblox = require("@lune/roblox")

local H = require("./harness")
local Config = require("../src/shared/Config")
local Strings = require("../src/shared/Strings")
local Layout = require("../build/map/Layout")
local B = require("../build/lib/Builder")
local Place = require("../build/Place")
local Scripts = require("../build/Scripts")

local FILE = "MidnightDelivery.rbxlx"

local function allBaseParts(root)
	local out = {}
	for _, d in root:GetDescendants() do
		if d:IsA("BasePart") then
			table.insert(out, d)
		end
	end
	return out
end

local function tagged(root, tag)
	local out = {}
	for _, d in root:GetDescendants() do
		if d:HasTag(tag) then
			table.insert(out, d)
		end
	end
	return out
end

local function worldAabb(part)
	local c = { part.CFrame:GetComponents() }
	local s = part.Size
	local hx = math.abs(c[4]) * s.X / 2 + math.abs(c[5]) * s.Y / 2 + math.abs(c[6]) * s.Z / 2
	local hy = math.abs(c[7]) * s.X / 2 + math.abs(c[8]) * s.Y / 2 + math.abs(c[9]) * s.Z / 2
	local hz = math.abs(c[10]) * s.X / 2 + math.abs(c[11]) * s.Y / 2 + math.abs(c[12]) * s.Z / 2
	return c[1] - hx, c[1] + hx, c[2] - hy, c[2] + hy, c[3] - hz, c[3] + hz
end

local function lightWorldPos(light)
	local host = light.Parent
	if host:IsA("Attachment") then
		return (host.Parent.CFrame * host.CFrame).Position
	end
	return host.CFrame.Position
end

local function near(a: number, b: number, tol: number): boolean
	return math.abs(a - b) <= tol
end

local function sameValue(a, b, map): (boolean, string?)
	local t = typeof(a)
	if t ~= typeof(b) then
		return false, "type " .. t .. " vs " .. typeof(b)
	end
	if t == "number" then
		return near(a, b, math.max(1e-4, math.abs(a) * 1e-5))
	elseif t == "Vector3" then
		return near(a.X, b.X, 1e-4) and near(a.Y, b.Y, 1e-4) and near(a.Z, b.Z, 1e-4)
	elseif t == "Vector2" then
		return near(a.X, b.X, 1e-4) and near(a.Y, b.Y, 1e-4)
	elseif t == "CFrame" then
		local x, y = { a:GetComponents() }, { b:GetComponents() }
		for i = 1, 12 do
			if not near(x[i], y[i], 1e-4) then
				return false
			end
		end
		return true
	elseif t == "Color3" then
		return near(a.R, b.R, 0.003) and near(a.G, b.G, 0.003) and near(a.B, b.B, 0.003)
	elseif t == "UDim" then
		return near(a.Scale, b.Scale, 1e-5) and a.Offset == b.Offset
	elseif t == "UDim2" then
		return near(a.X.Scale, b.X.Scale, 1e-5) and a.X.Offset == b.X.Offset and near(a.Y.Scale, b.Y.Scale, 1e-5) and a.Y.Offset == b.Y.Offset
	elseif t == "Font" then
		return a.Family == b.Family and a.Weight == b.Weight and a.Style == b.Style
	elseif t == "Instance" then
		return map[a] == b, "reference"
	end
	return a == b
end

-- Pairs every instance of the fresh build with the same instance in the file.
local function pairTrees(fresh, disk)
	local map = { [fresh] = disk }
	for _, service in fresh:GetChildren() do
		local other = disk:FindFirstChild(service.Name)
		assert(other, "service missing in file: " .. service.Name)
		map[service] = other
		local a, b = service:GetDescendants(), other:GetDescendants()
		assert(#a == #b, string.format("%s: %d instances in build, %d in file", service.Name, #a, #b))
		for i = 1, #a do
			assert(a[i].ClassName == b[i].ClassName and a[i].Name == b[i].Name, "tree differs at " .. a[i]:GetFullName())
			map[a[i]] = b[i]
		end
	end
	return map
end

-- Properties the current file format stores under other names.
local MIGRATED = {
	CornerRadius = { "TopLeftRadius", "TopRightRadius", "BottomLeftRadius", "BottomRightRadius" },
}

local function readProp(inst, prop): (boolean, any)
	return pcall(function()
		return inst[prop]
	end)
end

local function isEnglishText(s: string): boolean
	local cleaned = string.gsub(s, "\u{00B7}", "") -- middle dot separator
	return not string.find(cleaned, "[\128-\255]")
end

return function()
	H.section("Saved place file: " .. FILE .. " (re-opened with Lune)")

	local disk = roblox.deserializePlace(fs.readFile(FILE))
	local ws = disk:GetService("Workspace")
	local root = ws:FindFirstChild("MidnightDelivery")
	local map = root and root:FindFirstChild("Map")

	H.test("file uses only long-standing property formats (classic UICorner radius)", function()
		local xml = fs.readFile(FILE)
		H.truthy(not string.find(xml, "TopLeftRadius", 1, true), "per-corner radii written")
		local corners = select(2, string.gsub(xml, '<Item class="UICorner"', ""))
		local classic = select(2, string.gsub(xml, '<UDim name="CornerRadius">', ""))
		H.eq(classic, corners, "every UICorner stores CornerRadius")
		H.truthy(string.sub(xml, 1, 20) == '<roblox version="4">', "XML place format version 4")
	end)

	H.test("file opens and holds the scene in edit mode (not built at runtime)", function()
		H.truthy(map, "Workspace.MidnightDelivery.Map missing")
		H.truthy(#allBaseParts(map) > 250, "expected a full static map in the file")
	end)

	H.test("file matches the current sources and every assigned property survived saving", function()
		local fresh = Place.make()
		local pairs_ = pairTrees(fresh, disk)
		-- later assignments override earlier ones (e.g. a helper default, then the real value)
		local last, order = {}, {}
		for _, r in B.records do
			local byInst = last[r.inst]
			if not byInst then
				byInst = {}
				last[r.inst] = byInst
			end
			if byInst[r.prop] == nil then
				table.insert(order, r)
			end
			byInst[r.prop] = r
		end
		local checked, bad = 0, {}
		for _, first in order do
			local r = last[first.inst][first.prop]
			local other = pairs_[r.inst]
			if not other then
				table.insert(bad, "unmapped " .. r.inst:GetFullName())
			else
				local ok
				if MIGRATED[r.prop] then
					ok = true
					for _, alias in MIGRATED[r.prop] do
						local readOk, value = readProp(other, alias)
						ok = ok and readOk and sameValue(r.value, value, pairs_)
					end
				else
					local readOk, value = readProp(other, r.prop)
					ok = readOk and sameValue(r.value, value, pairs_)
				end
				checked += 1
				if not ok then
					table.insert(bad, r.inst:GetFullName() .. "." .. r.prop)
				end
			end
		end
		H.eq(#bad, 0, "lost/changed properties: " .. table.concat(bad, ", ", 1, math.min(#bad, 8)))
		H.truthy(checked > 5000, "too few properties checked: " .. checked)
	end)

	H.test("script sources in the file equal src/ and compile", function()
		local count = 0
		for _, entry in Scripts.list do
			local path = string.split(entry[1], "/")
			local node = disk:GetService(path[1])
			for i = 2, #path do
				node = node:FindFirstChild(path[i])
				H.truthy(node, "missing container " .. entry[1])
			end
			local s = node:FindFirstChild(entry[3])
			H.truthy(s and s.ClassName == entry[2], "missing " .. entry[2] .. " " .. entry[3])
			H.eq(s.Source, fs.readFile(entry[4]), entry[3] .. " source differs")
			local ok, err = pcall(luau.compile, s.Source)
			H.truthy(ok, entry[3] .. " does not compile: " .. tostring(err))
			count += 1
		end
		H.eq(count, 14)
	end)

	H.test("no stud textures, no default Plastic, everything in the map anchored", function()
		local faces = { "TopSurface", "BottomSurface", "LeftSurface", "RightSurface", "FrontSurface", "BackSurface" }
		for _, p in allBaseParts(disk) do
			for _, f in faces do
				H.eq(p[f], roblox.Enum.SurfaceType.Smooth, p:GetFullName() .. "." .. f)
			end
			H.truthy(p.Material ~= roblox.Enum.Material.Plastic, p:GetFullName() .. " uses default Plastic")
		end
		for _, p in allBaseParts(ws) do
			H.truthy(p.Anchored, p:GetFullName() .. " is not anchored")
			H.truthy(not (p.Transparency >= 1 and p.CanCollide), p:GetFullName() .. " is an invisible wall")
		end
	end)

	H.test("corridor scale: 13 wide, 11 high, 50 long", function()
		local corridor = map:FindFirstChild("Corridor")
		local floor, ceiling = corridor:FindFirstChild("Floor"), corridor:FindFirstChild("Ceiling")
		H.near(floor.CFrame.Y + floor.Size.Y / 2, 0, 1e-4, "floor top")
		H.near(ceiling.CFrame.Y - ceiling.Size.Y / 2, 11, 1e-4, "ceiling bottom")
		local northMin, southMax = math.huge, -math.huge
		for _, p in allBaseParts(corridor:FindFirstChild("Walls"):FindFirstChild("NorthWall")) do
			if string.sub(p.Name, 1, 4) == "Wall" then
				local _, _, _, _, z0 = worldAabb(p)
				northMin = math.min(northMin, z0)
			end
		end
		for _, p in allBaseParts(corridor:FindFirstChild("Walls"):FindFirstChild("SouthWall")) do
			if string.sub(p.Name, 1, 4) == "Wall" then
				local _, _, _, _, _, z1 = worldAabb(p)
				southMax = math.max(southMax, z1)
			end
		end
		H.near(northMin - southMax, 13, 1e-4, "corridor width")
		local east = corridor:FindFirstChild("Walls"):FindFirstChild("EastWall")
		local eastMin = math.huge
		for _, p in allBaseParts(east) do
			if string.sub(p.Name, 1, 4) == "Wall" then
				eastMin = math.min(eastMin, (worldAabb(p)))
			end
		end
		H.near(eastMin, 50, 1e-4, "corridor length (elevator wall at x = 0)")
	end)

	H.test("door openings are 5 wide and 8 high; plates match apartment ids", function()
		local doors = tagged(map, "MD_ApartmentDoor")
		H.eq(#doors, 3)
		local ids = {}
		for _, door in doors do
			local id = door:GetAttribute("ApartmentId")
			ids[id] = true
			local apt = door.Parent.Parent
			H.eq(apt.Name, "Apartment" .. id)
			local jl, jr, head = apt:FindFirstChild("JambL"), apt:FindFirstChild("JambR"), apt:FindFirstChild("JambHead")
			local a0, a1, _, _, c0, c1 = worldAabb(jl)
			local b0, b1, _, _, d0, d1 = worldAabb(jr)
			local width = math.max(a1, b1, c1, d1) - math.min(a0, b0, c0, d0)
			-- linings sit along the wall, so one horizontal extent is the wall depth (1)
			local opening = if math.abs(a1 - a0) < 0.5 then math.max(a1, b1) - math.min(a0, b0) else math.max(c1, d1) - math.min(c0, d0)
			H.near(opening, Layout.DoorWidth, 1e-3, id .. " opening width")
			local _, _, _, top = worldAabb(head)
			H.near(top, Layout.DoorHeight, 1e-3, id .. " opening height")
			H.truthy(Layout.DoorHeight >= 8 and Layout.DoorHeight <= 9 and width > 0)
			local plate = door.Parent:FindFirstChild("NumberPlate")
			H.eq(plate:FindFirstChild("Number"):FindFirstChild("Text").Text, id, "number plate")
			for _, a in { "PromptAttachment", "MarkerAttachment", "DropAttachment" } do
				local att = door:FindFirstChild(a)
				H.truthy(att and att.CFrame.Position.Magnitude > 0.05, id .. " " .. a .. " missing or at origin")
			end
			H.truthy(door:FindFirstChild("KnockSound") and door:FindFirstChild("ThudSound"), id .. " sound slots")
		end
		for _, id in Config.Apartments do
			H.truthy(ids[id], "no door for apartment " .. id)
		end
	end)

	H.test("elevator: ~10x10 cab, two sliding doors, zone, call button, one spawn facing the doors", function()
		local elevator = tagged(map, "MD_Elevator")[1]
		H.truthy(elevator, "elevator")
		H.eq(elevator:GetAttribute("DoorsOpen"), false)
		local back = elevator:FindFirstChild("Cab"):FindFirstChild("BackWall")
		local _, backFace = worldAabb(back)
		H.near(-1 - backFace, 10, 1e-4, "cab depth")
		local doors = tagged(map, "MD_ElevatorDoor")
		H.eq(#doors, 2)
		for _, d in doors do
			H.eq(typeof(d:GetAttribute("OpenOffset")), "Vector3")
		end
		H.eq(#tagged(map, "MD_ElevatorZone"), 1)
		H.eq(#tagged(map, "MD_ElevatorCallButton"), 1)
		local spawns = {}
		for _, d in disk:GetDescendants() do
			if d:IsA("SpawnLocation") then
				table.insert(spawns, d)
			end
		end
		H.eq(#spawns, 1)
		local sp = spawns[1]
		H.truthy(sp.Neutral and sp.Duration == 0 and not sp.CanCollide, "spawn settings")
		H.truthy(sp.CFrame.LookVector.X > 0.99, "spawn faces the doors (+X)")
		H.truthy(sp.CFrame.X > -11 and sp.CFrame.X < -1 and math.abs(sp.CFrame.Z) < 5, "spawn inside the cab")
	end)

	H.test("walk lane (z -4..4, x 1..49) is free of colliding props", function()
		local lane = Layout.WalkLaneHalfZ
		local blocking = {}
		for _, p in allBaseParts(ws) do
			if p.CanCollide then
				local x0, x1, y0, y1, z0, z1 = worldAabb(p)
				if x1 > 1 and x0 < 49 and y1 > 0.3 and y0 < 7.5 and z1 > -lane and z0 < lane then
					table.insert(blocking, p:GetFullName())
				end
			end
		end
		H.eq(#blocking, 0, "blocking: " .. table.concat(blocking, ", "))
	end)

	H.test("every visible lamp has a light and every point light has a visible lamp", function()
		local neon, points, surfaces = {}, {}, {}
		for _, p in allBaseParts(ws) do
			if p.Material == roblox.Enum.Material.Neon then
				table.insert(neon, p)
			end
		end
		for _, d in ws:GetDescendants() do
			if d:IsA("PointLight") then
				table.insert(points, d)
			elseif d:IsA("SurfaceLight") or d:IsA("SpotLight") then
				table.insert(surfaces, d)
			end
		end
		H.eq(#points, #Layout.LightX + 1, "corridor lights + cab light")
		for _, lamp in neon do
			local found = false
			for _, l in points do
				if (lightWorldPos(l) - lamp.CFrame.Position).Magnitude < 1.5 then
					found = true
				end
			end
			H.truthy(found, lamp:GetFullName() .. " glows without a light")
		end
		for _, l in points do
			local found = false
			for _, lamp in neon do
				if (lightWorldPos(l) - lamp.CFrame.Position).Magnitude < 1.5 then
					found = true
				end
			end
			H.truthy(found, l:GetFullName() .. " has no visible lamp")
		end
		H.eq(#surfaces, 1)
		H.eq(surfaces[1].Parent.Name, "Pane", "only the window glow is a surface light")
	end)

	H.test("lighting: Future, interior-only, no fog/atmosphere/bloom", function()
		local lighting = disk:GetService("Lighting")
		H.eq(lighting.Technology, roblox.Enum.Technology.Future)
		H.eq(lighting.Brightness, 0)
		H.truthy(lighting.FogEnd >= 10000)
		for _, d in lighting:GetChildren() do
			H.truthy(not (d:IsA("Atmosphere") or d:IsA("BloomEffect") or d:IsA("SunRaysEffect") or d:IsA("DepthOfFieldEffect")), "unexpected " .. d.ClassName)
		end
		H.eq(ws.StreamingEnabled, false)
	end)

	H.test("prompts: custom style only, E / ButtonX, range inside the server check", function()
		local prompts = {}
		for _, d in ws:GetDescendants() do
			if d:IsA("ProximityPrompt") then
				table.insert(prompts, d)
			end
		end
		H.eq(#prompts, 5, "pickup + 3 doors + call button")
		for _, p in prompts do
			H.eq(p.Style, roblox.Enum.ProximityPromptStyle.Custom, p:GetFullName())
			H.eq(p.KeyboardKeyCode, roblox.Enum.KeyCode.E)
			H.eq(p.GamepadKeyCode, roblox.Enum.KeyCode.ButtonX)
			H.truthy(p.MaxActivationDistance + 2 <= Config.InteractMaxDistance, "prompt range vs server check")
			H.truthy(p.Parent:IsA("Attachment") and p.Parent.CFrame.Position.Magnitude > 0.05, "prompt attachment")
		end
	end)

	H.test("parcel template is massless, non-colliding and welded", function()
		local parcel = disk:GetService("ReplicatedStorage").MidnightDelivery.Assets.Parcel
		H.truthy(parcel.PrimaryPart and parcel.PrimaryPart.Name == "Box", "PrimaryPart")
		local parts, welds = 0, 0
		for _, d in parcel:GetDescendants() do
			if d:IsA("BasePart") then
				parts += 1
				H.truthy(d.Massless and not d.CanCollide and not d.Anchored and not d.CanTouch, d.Name .. " physics")
			elseif d:IsA("Weld") then
				welds += 1
				H.eq(d.Part0, parcel.PrimaryPart)
			end
		end
		H.eq(welds, parts - 1)
		H.truthy(parcel.PrimaryPart.GripAttachment.CFrame.Y > 0.5, "grip above the box")
	end)

	H.test("UI: every element the client scripts look up exists", function()
		local sg = disk:GetService("StarterGui")
		local hud, top = sg:FindFirstChild("MidnightHUD"), sg:FindFirstChild("TopbarHUD")
		H.eq(hud.ScreenInsets, roblox.Enum.ScreenInsets.CoreUISafeInsets)
		H.eq(top.ScreenInsets, roblox.Enum.ScreenInsets.TopbarSafeInsets)
		H.eq(hud.ResetOnSpawn, false)
		H.eq(top.ResetOnSpawn, false)
		local templates = disk:GetService("ReplicatedStorage").MidnightDelivery.UI
		local paths = {
			{ hud, "Scale" }, { hud, "Device/Header/Key/Label" }, { hud, "Device/Header/Summary" },
			{ hud, "Device/Divider" }, { hud, "Device/Body/Meta/Section" }, { hud, "Device/Body/Meta/Counter" },
			{ hud, "Device/Body/Target" }, { hud, "Device/Body/Instruction" }, { hud, "Device/Body/Steps/StepPickup" },
			{ hud, "Device/Body/Steps/StepDeliver" }, { hud, "Device/Body/Earnings/Value" },
			{ hud, "Toast/Panel/Accent" }, { hud, "Toast/Panel/Content/Title" }, { hud, "Toast/Panel/Content/Detail" },
			{ top, "Title/Night" }, { top, "Earnings/Value" },
			{ templates, "PromptBillboard/Button/Key/Label" }, { templates, "PromptBillboard/Button/Object" },
			{ templates, "PromptBillboard/Button/Action" }, { templates, "PromptBillboard/Button/Progress" },
			{ templates, "TargetMarker/Label" },
		}
		for _, entry in paths do
			local node = entry[1]
			for _, name in string.split(entry[2], "/") do
				node = node and node:FindFirstChild(name)
			end
			H.truthy(node, "missing UI element " .. entry[1].Name .. "/" .. entry[2])
		end
		H.eq(hud.Toast.ClassName, "CanvasGroup")
		local width = hud.Device.Size.X.Offset
		H.truthy(width >= 300 and width <= 340, "device design width " .. width)
	end)

	H.test("one font family (Builder Sans) and English-only text everywhere", function()
		local count = 0
		for _, d in disk:GetDescendants() do
			if d:IsA("TextLabel") or d:IsA("TextButton") then
				count += 1
				H.eq(d.FontFace.Family, "rbxasset://fonts/families/BuilderSans.json", d:GetFullName())
				H.truthy(isEnglishText(d.Text), "non-English text in " .. d:GetFullName())
			end
		end
		H.truthy(count > 30)
		local function walk(t)
			for _, v in t do
				if type(v) == "string" then
					H.truthy(isEnglishText(v), "Strings: " .. v)
				elseif type(v) == "table" then
					walk(v)
				end
			end
		end
		walk(Strings)
		H.truthy(isEnglishText(Strings.deliveredDetail(25, "102")))
	end)

	H.test("edit-only helpers are removed at runtime; default UI conflicts handled", function()
		H.truthy(root:FindFirstChild("_DevOnly"):FindFirstChild("ScaleReference_R15"), "scale reference in the file")
		local server = fs.readFile("src/server/DeliveryServer.server.luau")
		H.truthy(string.find(server, 'FindFirstChild("_DevOnly")', 1, true) and string.find(server, "devOnly:Destroy()", 1, true))
		local client = fs.readFile("src/client/ClientMain.client.luau")
		for _, core in { "PlayerList", "Backpack", "Health" } do
			H.truthy(string.find(client, "CoreGuiType." .. core .. ", false", 1, true), core .. " not disabled")
		end
	end)
end
