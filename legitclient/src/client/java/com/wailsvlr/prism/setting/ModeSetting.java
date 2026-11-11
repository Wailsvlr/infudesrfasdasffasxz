package com.wailsvlr.prism.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** One choice out of a fixed list (cycled with left/right click in the GUI). */
public class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String name, String description, String defaultValue, String... modes) {
		super(name, description, defaultValue);
		this.modes = List.of(modes);
	}

	public List<String> getModes() { return modes; }
	public boolean is(String mode) { return value.equals(mode); }
	public int index() { return Math.max(0, modes.indexOf(value)); }

	public void cycle(int dir) {
		int i = Math.floorMod(index() + dir, modes.size());
		value = modes.get(i);
	}

	@Override
	public void set(String v) { if (modes.contains(v)) value = v; }

	@Override
	public JsonElement toJson() { return new JsonPrimitive(value); }

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive()) set(json.getAsString());
	}
}
