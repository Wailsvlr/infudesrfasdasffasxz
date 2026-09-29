-- Per-player delivery state machine. Pure Luau (no Roblox APIs) so it can be unit-tested.
--
--   pickup --(pickup)--> deliver --(deliver to target)--> pickup ... --> complete
--   complete --(restart)--> pickup
--   deliver --(dropParcel: death/respawn)--> pickup
--
-- Every transition checks its preconditions, so repeated or out-of-order requests are
-- rejected instead of paying out twice.

export type Stage = "pickup" | "deliver" | "complete"

export type State = {
	night: number,
	index: number,
	total: number,
	stage: Stage,
	hasParcel: boolean,
	earnings: number,
	scarePlayed: boolean,
}

export type DeliverResult = {
	ok: boolean,
	code: string,
	door: string?,
	expected: string?,
	reward: number?,
	earnings: number?,
	scare: boolean?,
	complete: boolean?,
}

local Logic = {}

function Logic.newState(config): State
	return {
		night = config.Night,
		index = 1,
		total = #config.Route,
		stage = "pickup",
		hasParcel = false,
		earnings = 0,
		scarePlayed = false,
	}
end

function Logic.target(state: State, config): string?
	if state.stage == "complete" then
		return nil
	end
	return config.Route[state.index]
end

function Logic.isApartment(config, id: any): boolean
	if type(id) ~= "string" then
		return false
	end
	return table.find(config.Apartments, id) ~= nil
end

function Logic.pickup(state: State, config): (boolean, string)
	if state.stage == "complete" then
		return false, "shift_complete"
	end
	if state.hasParcel then
		return false, "already_carrying"
	end
	if Logic.target(state, config) == nil then
		return false, "no_target"
	end
	state.hasParcel = true
	state.stage = "deliver"
	return true, "picked_up"
end

function Logic.restart(state: State, config): (boolean, string)
	if state.stage ~= "complete" then
		return false, "shift_in_progress"
	end
	local fresh = Logic.newState(config)
	for k, v in fresh do
		(state :: any)[k] = v
	end
	return true, "restarted"
end

function Logic.deliver(state: State, config, apartmentId: any): DeliverResult
	if not Logic.isApartment(config, apartmentId) then
		return { ok = false, code = "unknown_door" }
	end
	if not state.hasParcel or state.stage ~= "deliver" then
		return { ok = false, code = "no_parcel", door = apartmentId }
	end
	local target = Logic.target(state, config)
	if apartmentId ~= target then
		return { ok = false, code = "wrong_door", door = apartmentId, expected = target }
	end

	local reward = config.RewardPerDelivery
	state.hasParcel = false
	state.earnings += reward

	local scare = config.ScareDeliveryIndex == state.index and not state.scarePlayed
	if scare then
		state.scarePlayed = true
	end

	state.index += 1
	local complete = state.index > state.total
	state.stage = if complete then "complete" else "pickup"

	return {
		ok = true,
		code = "delivered",
		door = apartmentId,
		reward = reward,
		earnings = state.earnings,
		scare = scare,
		complete = complete,
	}
end

-- Death, respawn or leaving: the carried parcel is gone; the current delivery stays.
function Logic.dropParcel(state: State): boolean
	if not state.hasParcel then
		return false
	end
	state.hasParcel = false
	state.stage = "pickup"
	return true
end

-- Flat snapshot mirrored to the client as Player attributes (MD_*).
function Logic.snapshot(state: State, config): { [string]: any }
	return {
		MD_Night = state.night,
		MD_Stage = state.stage,
		MD_Target = Logic.target(state, config) or "",
		MD_Index = state.index,
		MD_Total = state.total,
		MD_HasParcel = state.hasParcel,
		MD_Earnings = state.earnings,
	}
end

return Logic
