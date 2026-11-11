package com.wailsvlr.prism.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String name, String description, boolean defaultValue) {
		super(name, description, defaultValue);
	}

	public boolean isOn() { return value; }
	public void toggle() { value = !value; }

	@Override
	public JsonElement toJson() { return new JsonPrimitive(value); }

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) value = json.getAsBoolean();
	}
}
