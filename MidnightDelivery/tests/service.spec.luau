-- DeliveryService: server validation with fake players (no Roblox runtime).
-- Covers distance checks, parcel ownership, rate limiting, double-payout attempts,
-- respawn/leave cleanup and two players not affecting each other.

local roblox = require("@lune/roblox")
local H = require("./harness")
local Config = require("../src/shared/Config")
local Logic = require("../src/shared/DeliveryLogic")
local DeliveryService = require("../src/server/DeliveryService")

local V3 = roblox.Vector3

local CART = V3.new(4.4, 2.3, -5.2)
local DOORS = {
	["101"] = V3.new(15, 3.6, 5.6),
	["102"] = V3.new(27, 3.6, -5.6),
	["103"] = V3.new(39, 3.6, 5.6),
}

local function makeWorld()
	local w = {
		clock = 0,
		positions = {} :: { [any]: any },
		hands = {} :: { [any]: boolean },
		carried = {} :: { [any]: string? },
		published = {} :: { [any]: any },
		feedback = {} :: { [any]: { any } },
		scares = {} :: { { any } },
	}
	w.service = DeliveryService.new({
		config = Config,
		logic = Logic,
		now = function()
			return w.clock
		end,
		rootPosition = function(player)
			return w.positions[player]
		end,
		attachParcel = function(player, id)
			if w.hands[player] == false then
				return false
			end
			w.carried[player] = id
			return true
		end,
		removeParcel = function(player)
			w.carried[player] = nil
		end,
		publish = function(player, snap)
			w.published[player] = snap
		end,
		feedback = function(player, payload)
			w.feedback[player] = w.feedback[player] or {}
			table.insert(w.feedback[player], payload)
		end,
		scare = function(player, id)
			table.insert(w.scares, { player, id })
		end,
	})
	function w.player(name: string)
		local p = { Name = name }
		w.service:addPlayer(p)
		w.positions[p] = V3.new(-4.5, 3, 0)
		return p
	end
	function w.walk(p, target)
		w.positions[p] = target + V3.new(0, -0.6, 2.5 * math.sign(-target.Z + 0.001))
		w.clock += 1
	end
	function w.lastFeedback(p)
		local list = w.feedback[p]
		return list and list[#list]
	end
	return w
end

return function()
	H.section("DeliveryService (server validation, fake players)")

	H.test("pickup from the elevator (too far) is rejected", function()
		local w = makeWorld()
		local a = w.player("A")
		H.eq(w.service:requestPickup(a, CART), "too_far")
		H.eq(w.carried[a], nil)
		H.eq(w.published[a].MD_HasParcel, false)
	end)

	H.test("pickup at the cart attaches the parcel for the first address", function()
		local w = makeWorld()
		local a = w.player("A")
		w.walk(a, CART)
		H.eq(w.service:requestPickup(a, CART), "picked_up")
		H.eq(w.carried[a], "102")
		H.eq(w.published[a].MD_Stage, "deliver")
	end)

	H.test("delivering at a door without a parcel does nothing", function()
		local w = makeWorld()
		local a = w.player("A")
		w.walk(a, DOORS["102"])
		H.eq(w.service:requestDeliver(a, "102", DOORS["102"]), "no_parcel")
		H.eq(w.lastFeedback(a).kind, "no_parcel")
		H.eq(w.published[a].MD_Earnings, 0)
	end)

	H.test("wrong door: feedback names the right apartment, parcel stays", function()
		local w = makeWorld()
		local a = w.player("A")
		w.walk(a, CART)
		w.service:requestPickup(a, CART)
		w.walk(a, DOORS["101"])
		H.eq(w.service:requestDeliver(a, "101", DOORS["101"]), "wrong_door")
		local fb = w.lastFeedback(a)
		H.eq(fb.kind, "wrong_door")
		H.eq(fb.expected, "102")
		H.eq(w.carried[a], "102")
	end)

	H.test("spamming the correct door pays once (cooldown + state guard)", function()
		local w = makeWorld()
		local a = w.player("A")
		w.walk(a, CART)
		w.service:requestPickup(a, CART)
		w.walk(a, DOORS["102"])
		local codes = {}
		for i = 1, 10 do
			table.insert(codes, w.service:requestDeliver(a, "102", DOORS["102"]))
			w.clock += if i % 2 == 0 then 1 else 0.01 -- mix of rapid and spaced repeats
		end
		H.eq(codes[1], "delivered")
		local delivered = 0
		for _, c in codes do
			if c == "delivered" then
				delivered += 1
			end
		end
		H.eq(delivered, 1)
		H.eq(w.published[a].MD_Earnings, Config.RewardPerDelivery)
		H.eq(w.carried[a], nil)
	end)

	H.test("claiming from far away (spoofed request) is rejected", function()
		local w = makeWorld()
		local a = w.player("A")
		w.walk(a, CART)
		w.service:requestPickup(a, CART)
		w.clock += 1 -- player still at the cart
		H.eq(w.service:requestDeliver(a, "102", DOORS["102"]), "too_far")
		H.eq(w.service:requestDeliver(a, "102", "not a position"), "too_far")
		H.eq(w.published[a].MD_Earnings, 0)
	end)

	H.test("dead or missing character cannot interact", function()
		local w = makeWorld()
		local a = w.player("A")
		w.positions[a] = nil
		H.eq(w.service:requestPickup(a, CART), "no_character")
	end)

	H.test("death while carrying clears the parcel; a new one can be picked up", function()
		local w = makeWorld()
		local a = w.player("A")
		w.walk(a, CART)
		w.service:requestPickup(a, CART)
		w.service:characterReset(a) -- Humanoid.Died
		w.service:characterReset(a) -- CharacterAdded after respawn (idempotent)
		H.eq(w.carried[a], nil)
		H.eq(w.published[a].MD_Stage, "pickup")
		H.eq(w.published[a].MD_Target, "102")
		local lostCount = 0
		for _, fb in w.feedback[a] do
			if fb.kind == "parcel_lost" then
				lostCount += 1
			end
		end
		H.eq(lostCount, 1)
		w.walk(a, CART)
		H.eq(w.service:requestPickup(a, CART), "picked_up")
	end)

	H.test("leaving while carrying removes the parcel and the state", function()
		local w = makeWorld()
		local a = w.player("A")
		w.walk(a, CART)
		w.service:requestPickup(a, CART)
		w.service:removePlayer(a)
		H.eq(w.carried[a], nil)
		H.eq(w.service:getState(a), nil)
		H.eq(w.service:requestPickup(a, CART), "unknown_player")
	end)

	H.test("no hand to hold the parcel: pickup is undone", function()
		local w = makeWorld()
		local a = w.player("A")
		w.hands[a] = false
		w.walk(a, CART)
		H.eq(w.service:requestPickup(a, CART), "attach_failed")
		H.eq(w.published[a].MD_Stage, "pickup")
		H.eq(w.published[a].MD_HasParcel, false)
	end)

	H.test("full shift through the service: 3 payouts, one scare, restart at the cart", function()
		local w = makeWorld()
		local a = w.player("A")
		for _, id in Config.Route do
			w.walk(a, CART)
			H.eq(w.service:requestPickup(a, CART), "picked_up")
			w.walk(a, DOORS[id])
			H.eq(w.service:requestDeliver(a, id, DOORS[id]), "delivered")
		end
		H.eq(w.published[a].MD_Stage, "complete")
		H.eq(w.published[a].MD_Earnings, 75)
		H.eq(#w.scares, 1)
		H.eq(w.scares[1][2], Config.Route[Config.ScareDeliveryIndex])
		w.walk(a, CART)
		H.eq(w.service:requestPickup(a, CART), "restarted")
		H.eq(w.published[a].MD_Earnings, 0)
		H.eq(w.published[a].MD_Stage, "pickup")
	end)

	H.test("two players: parcels, targets and earnings stay separate", function()
		local w = makeWorld()
		local a, b = w.player("A"), w.player("B")
		w.walk(a, CART)
		w.walk(b, CART)
		w.service:requestPickup(a, CART)
		-- B has no parcel: cannot deliver A's address
		w.walk(b, DOORS["102"])
		w.walk(a, DOORS["102"])
		H.eq(w.service:requestDeliver(b, "102", DOORS["102"]), "no_parcel")
		H.eq(w.service:requestDeliver(a, "102", DOORS["102"]), "delivered")
		H.eq(w.published[a].MD_Earnings, 25)
		H.eq(w.published[b].MD_Earnings, 0)
		H.eq(w.published[a].MD_Target, "103")
		H.eq(w.published[b].MD_Target, "102")
		-- A's pickup and B's death/pickup at the same instant: no cross effects
		w.walk(a, CART)
		w.positions[b] = CART + V3.new(0, -0.6, 2.5)
		w.service:requestPickup(a, CART)
		w.service:characterReset(b)
		H.eq(w.carried[a], "103")
		H.eq(w.published[a].MD_HasParcel, true)
		H.eq(w.service:requestPickup(b, CART), "picked_up") -- A's cooldown is not B's
		H.eq(w.carried[b], "102")
		-- the scare goes only to the player whose delivery triggered it
		w.walk(a, DOORS["103"])
		w.service:requestDeliver(a, "103", DOORS["103"])
		H.eq(#w.scares, 1)
		H.eq(w.scares[1][1], a)
	end)
end
