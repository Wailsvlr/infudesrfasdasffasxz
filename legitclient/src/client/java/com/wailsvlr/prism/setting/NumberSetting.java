package com.wailsvlr.prism.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A slider value between {@code min} and {@code max}, snapped to {@code step}. */
public class NumberSetting extends Setting<Double> {
	private final double min, max, step;
	private final String suffix;

	public NumberSetting(String name, String description, double defaultValue, double min, double max, double step, String suffix) {
		super(name, description, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.suffix = suffix;
	}

	public double getMin() { return min; }
	public double getMax() { return max; }
	public double getStep() { return step; }
	public float getFloat() { return value.floatValue(); }
	public int getInt() { return (int) Math.round(value); }

	@Override
	public void set(Double v) {
		double snapped = Math.round((v - min) / step) * step + min;
		this.value = Math.max(min, Math.min(max, snapped));
	}

	/** Position of the value on the slider, 0..1. */
	public double fraction() { return (value - min) / (max - min); }

	public void setFraction(double f) { set(min + Math.max(0, Math.min(1, f)) * (max - min)); }

	public String display() {
		boolean whole = step >= 1 && Math.abs(step - Math.rint(step)) < 1e-9;
		String num = whole ? String.valueOf(Math.round(value)) : String.format(java.util.Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", value);
		return num + suffix;
	}

	@Override
	public JsonElement toJson() { return new JsonPrimitive(value); }

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsDouble());
	}
}
