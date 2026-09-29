-- DeliveryLogic: the per-player state machine.

local H = require("./harness")
local Config = require("../src/shared/Config")
local Logic = require("../src/shared/DeliveryLogic")

return function()
	H.section("DeliveryLogic")

	H.test("new state starts at the first route stop without a parcel", function()
		local s = Logic.newState(Config)
		H.eq(s.stage, "pickup")
		H.eq(s.hasParcel, false)
		H.eq(s.earnings, 0)
		H.eq(Logic.target(s, Config), Config.Route[1])
		H.eq(s.total, 3)
	end)

	H.test("route uses all three doors exactly once", function()
		local seen = {}
		for _, id in Config.Route do
			H.truthy(Logic.isApartment(Config, id), id .. " is not an apartment")
			H.truthy(not seen[id], id .. " repeats")
			seen[id] = true
		end
		H.eq(#Config.Route, #Config.Apartments)
	end)

	H.test("delivery without the parcel is rejected and pays nothing", function()
		local s = Logic.newState(Config)
		local r = Logic.deliver(s, Config, Config.Route[1])
		H.eq(r.ok, false)
		H.eq(r.code, "no_parcel")
		H.eq(s.earnings, 0)
		H.eq(s.index, 1)
	end)

	H.test("wrong door explains the expected apartment and keeps the parcel", function()
		local s = Logic.newState(Config)
		Logic.pickup(s, Config)
		local r = Logic.deliver(s, Config, "101")
		H.eq(r.ok, false)
		H.eq(r.code, "wrong_door")
		H.eq(r.expected, "102")
		H.eq(s.hasParcel, true)
		H.eq(s.stage, "deliver")
		H.eq(s.earnings, 0)
	end)

	H.test("correct door pays exactly once; repeating the request pays nothing", function()
		local s = Logic.newState(Config)
		Logic.pickup(s, Config)
		local r1 = Logic.deliver(s, Config, "102")
		H.eq(r1.ok, true)
		H.eq(r1.reward, Config.RewardPerDelivery)
		H.eq(s.earnings, Config.RewardPerDelivery)
		for _ = 1, 5 do
			local again = Logic.deliver(s, Config, "102")
			H.eq(again.ok, false)
		end
		H.eq(s.earnings, Config.RewardPerDelivery)
		H.eq(s.stage, "pickup")
		H.eq(Logic.target(s, Config), "103")
	end)

	H.test("unknown or malformed door ids are rejected", function()
		local s = Logic.newState(Config)
		Logic.pickup(s, Config)
		for _, bad in { "104", "", 102, nil, {} } do
			H.eq(Logic.deliver(s, Config, bad).code, "unknown_door")
		end
		H.eq(s.hasParcel, true)
	end)

	H.test("second pickup while carrying is rejected", function()
		local s = Logic.newState(Config)
		H.eq((Logic.pickup(s, Config)), true)
		local ok, code = Logic.pickup(s, Config)
		H.eq(ok, false)
		H.eq(code, "already_carrying")
	end)

	H.test("full shift: three deliveries, scare exactly once on the configured one", function()
		local s = Logic.newState(Config)
		local scares = {}
		for i, id in Config.Route do
			H.eq((Logic.pickup(s, Config)), true)
			local r = Logic.deliver(s, Config, id)
			H.eq(r.ok, true, "delivery " .. i)
			if r.scare then
				table.insert(scares, i)
			end
			H.eq(r.complete, i == #Config.Route)
		end
		H.eq(#scares, 1)
		H.eq(scares[1], Config.ScareDeliveryIndex)
		H.eq(s.stage, "complete")
		H.eq(s.earnings, Config.RewardPerDelivery * #Config.Route)
		H.eq(Logic.target(s, Config), nil)
		local ok, code = Logic.pickup(s, Config)
		H.eq(ok, false)
		H.eq(code, "shift_complete")
	end)

	H.test("restart only after the shift is complete, then starts fresh", function()
		local s = Logic.newState(Config)
		H.eq((Logic.restart(s, Config)), false)
		for _, id in Config.Route do
			Logic.pickup(s, Config)
			Logic.deliver(s, Config, id)
		end
		H.eq((Logic.restart(s, Config)), true)
		H.eq(s.stage, "pickup")
		H.eq(s.index, 1)
		H.eq(s.earnings, 0)
		H.eq(s.scarePlayed, false)
	end)

	H.test("dropping the parcel (death/respawn) returns to pickup for the same delivery", function()
		local s = Logic.newState(Config)
		Logic.pickup(s, Config)
		H.eq(Logic.dropParcel(s), true)
		H.eq(s.stage, "pickup")
		H.eq(s.hasParcel, false)
		H.eq(Logic.target(s, Config), "102")
		H.eq(Logic.dropParcel(s), false)
		H.eq((Logic.pickup(s, Config)), true)
	end)

	H.test("snapshot mirrors the state for the client", function()
		local s = Logic.newState(Config)
		Logic.pickup(s, Config)
		local snap = Logic.snapshot(s, Config)
		H.eq(snap.MD_Stage, "deliver")
		H.eq(snap.MD_Target, "102")
		H.eq(snap.MD_HasParcel, true)
		H.eq(snap.MD_Index, 1)
		H.eq(snap.MD_Total, 3)
		H.eq(snap.MD_Earnings, 0)
		H.eq(snap.MD_Night, 1)
	end)
end
