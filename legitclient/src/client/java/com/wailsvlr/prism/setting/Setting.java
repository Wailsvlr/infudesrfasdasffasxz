package com.wailsvlr.prism.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/**
 * A module option shown in the ClickGUI and saved to the config file.
 * {@link #visibleWhen} hides options that do not apply (e.g. fill opacity while the mode is "Outline").
 */
public abstract class Setting<T> {
	private final String name;
	private final String description;
	protected T value;
	private final T defaultValue;
	private BooleanSupplier visible = () -> true;
	private boolean grouped;

	protected Setting(String name, String description, T defaultValue) {
		this.name = name;
		this.description = description;
		this.value = defaultValue;
		this.defaultValue = defaultValue;
	}

	public String getName() { return name; }
	public String getDescription() { return description; }
	public T get() { return value; }
	public void set(T value) { this.value = value; }
	public T getDefault() { return defaultValue; }
	public void reset() { this.value = defaultValue; }
	public boolean isVisible() { return !grouped && visible.getAsBoolean(); }

	/** Shown inside a {@link ChipsSetting} instead of on a row of its own (still saved as usual). */
	public void group() { grouped = true; }

	public boolean isGrouped() { return grouped; }

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
		this.visible = condition;
		return (S) this;
	}

	public abstract JsonElement toJson();

	/** Reads a saved value; anything invalid keeps the current value. */
	public abstract void fromJson(JsonElement json);
}
