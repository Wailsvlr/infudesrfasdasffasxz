package com.wailsvlr.prism.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A group of on/off targets drawn as chips, each with its colour dot: click a chip to switch the target, right click
 * it to change its colour. The switches and colours stay ordinary settings (saved as before); this only draws them.
 */
public class ChipsSetting extends Setting<Boolean> {
	/** {@code colorWhen} false: the colour does not apply right now (e.g. shulkers in their own dye colours). */
	public record Chip(BoolSetting on, ColorSetting color, BooleanSupplier colorWhen) {
		public String label() { return on.getName(); }
		public boolean colorActive() { return color != null && colorWhen.getAsBoolean(); }
	}

	private final List<Chip> chips = new ArrayList<>();

	public ChipsSetting(String name, String description) {
		super(name, description, true);
	}

	public ChipsSetting chip(BoolSetting on, ColorSetting color) { return chip(on, color, () -> true); }

	public ChipsSetting chip(BoolSetting on, ColorSetting color, BooleanSupplier colorWhen) {
		on.group();
		if (color != null) color.group();
		chips.add(new Chip(on, color, colorWhen));
		return this;
	}

	public List<Chip> chips() { return chips; }

	@Override
	public JsonElement toJson() { return JsonNull.INSTANCE; }

	@Override
	public void fromJson(JsonElement json) { }
}
