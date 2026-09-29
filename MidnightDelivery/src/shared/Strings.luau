-- All player-facing text (English). Keep wording short; the device is a small panel.

local Strings = {
	DeviceTitle = "COURIER DEVICE",
	CurrentDelivery = "CURRENT DELIVERY",
	ShiftCompleteSection = "SHIFT COMPLETE",
	AllDeliveredTarget = "All parcels delivered",
	PickUp = "Pick up the parcel.",
	ReturnToCart = "Return to the parcel cart to start again.",
	StepPickup = "1  PICK UP",
	StepDeliver = "2  DELIVER",
	ShiftEarnings = "Shift earnings",
	KeyDesktop = "TAB",
	KeyShow = "SHOW",
	KeyHide = "HIDE",
	MarkerParcels = "PARCELS",

	Prompt = {
		Pickup = "Pick up parcel",
		Restart = "Restart shift",
		Deliver = "Deliver parcel",
		Call = "Call elevator",
	},

	Toast = {
		Delivered = "Delivery complete",
		WrongDoor = "Wrong apartment",
		NoParcel = "No parcel",
		NoParcelDetail = "Pick up the parcel first.",
		ShiftComplete = "Shift complete",
		ParcelLost = "Parcel dropped",
		ParcelLostDetail = "Pick up a new one at the parcel cart.",
		Restarted = "New shift",
		RestartedDetail = "Three parcels are waiting.",
	},
}

function Strings.night(n: number): string
	return string.format("NIGHT %02d", n)
end

function Strings.apartment(id: string): string
	return "Apartment " .. id
end

function Strings.apartmentShort(id: string): string
	return "Apt " .. id
end

function Strings.deliverTo(id: string): string
	return "Deliver to Apartment " .. id .. "."
end

function Strings.wrongDoorDetail(expected: string): string
	return "This parcel is for Apartment " .. expected .. "."
end

function Strings.money(amount: number): string
	return "$" .. tostring(math.floor(amount))
end

function Strings.deliveredDetail(reward: number, id: string): string
	return "+" .. Strings.money(reward) .. " · Apartment " .. id
end

function Strings.counter(index: number, total: number): string
	return string.format("%d / %d", math.min(index, total), total)
end

return Strings
