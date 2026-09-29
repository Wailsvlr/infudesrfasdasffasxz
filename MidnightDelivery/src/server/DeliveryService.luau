-- Server-authoritative delivery service. Owns every player's delivery state and validates
-- each request (state, parcel ownership, distance, rate limit) before applying it.
-- Roblox specifics (characters, parcels, remotes) are injected so the rules can be tested
-- outside Studio; DeliveryServer wires the real ones.

local DeliveryService = {}
DeliveryService.__index = DeliveryService

export type Deps = {
	config: any,
	logic: any,
	now: () -> number,
	rootPosition: (player: any) -> any?, -- Vector3 of a living character, else nil
	attachParcel: (player: any, apartmentId: string) -> boolean,
	removeParcel: (player: any) -> (),
	publish: (player: any, snapshot: { [string]: any }) -> (),
	feedback: (player: any, payload: { [string]: any }) -> (),
	scare: (player: any, apartmentId: string) -> (),
}

function DeliveryService.new(deps: Deps)
	local self = setmetatable({}, DeliveryService)
	self.deps = deps
	self.states = {} :: { [any]: any }
	self.lastRequest = {} :: { [any]: number }
	return self
end

function DeliveryService:getState(player)
	return self.states[player]
end

function DeliveryService:_publish(player)
	local state = self.states[player]
	if state then
		self.deps.publish(player, self.deps.logic.snapshot(state, self.deps.config))
	end
end

function DeliveryService:addPlayer(player)
	if self.states[player] then
		return
	end
	self.states[player] = self.deps.logic.newState(self.deps.config)
	self:_publish(player)
end

function DeliveryService:removePlayer(player)
	self.deps.removeParcel(player)
	self.states[player] = nil
	self.lastRequest[player] = nil
end

-- Called on death, respawn and character removal. Safe to call repeatedly.
function DeliveryService:characterReset(player)
	local state = self.states[player]
	self.deps.removeParcel(player)
	if not state then
		return
	end
	if self.deps.logic.dropParcel(state) then
		self.deps.feedback(player, { kind = "parcel_lost" })
	end
	self:_publish(player)
end

-- Shared request gate: known player, living character, in range, not spamming.
function DeliveryService:_gate(player, position): (any?, string)
	local state = self.states[player]
	if not state then
		return nil, "unknown_player"
	end
	local root = self.deps.rootPosition(player)
	if root == nil then
		return nil, "no_character"
	end
	if typeof(position) ~= "Vector3" or (root - position).Magnitude > self.deps.config.InteractMaxDistance then
		return nil, "too_far"
	end
	local now = self.deps.now()
	local last = self.lastRequest[player]
	if last and now - last < self.deps.config.ActionCooldown then
		return nil, "cooldown"
	end
	self.lastRequest[player] = now
	return state, "ok"
end

function DeliveryService:requestPickup(player, pointPosition): string
	local state, reason = self:_gate(player, pointPosition)
	if not state then
		return reason
	end
	local logic, config = self.deps.logic, self.deps.config

	if state.stage == "complete" then
		local ok, code = logic.restart(state, config)
		if ok then
			self:_publish(player)
			self.deps.feedback(player, { kind = "restarted" })
		end
		return code
	end

	local ok, code = logic.pickup(state, config)
	if not ok then
		return code
	end
	local target = logic.target(state, config)
	if not self.deps.attachParcel(player, target) then
		logic.dropParcel(state) -- no hand to hold it: undo
		self:_publish(player)
		return "attach_failed"
	end
	self:_publish(player)
	return code
end

function DeliveryService:requestDeliver(player, apartmentId, doorPosition): string
	local state, reason = self:_gate(player, doorPosition)
	if not state then
		return reason
	end
	local logic, config = self.deps.logic, self.deps.config
	local result = logic.deliver(state, config, apartmentId)

	if not result.ok then
		if result.code == "wrong_door" then
			self.deps.feedback(player, { kind = "wrong_door", door = result.door, expected = result.expected })
		elseif result.code == "no_parcel" then
			self.deps.feedback(player, { kind = "no_parcel" })
		end
		return result.code
	end

	self.deps.removeParcel(player)
	self:_publish(player)
	self.deps.feedback(player, {
		kind = "delivered",
		door = result.door,
		reward = result.reward,
		earnings = result.earnings,
		complete = result.complete,
	})
	if result.scare then
		self.deps.scare(player, result.door)
	end
	return result.code
end

return DeliveryService
