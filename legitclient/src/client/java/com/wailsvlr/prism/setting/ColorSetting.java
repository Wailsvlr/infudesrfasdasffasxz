package com.wailsvlr.prism.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wailsvlr.prism.util.ColorUtil;

/**
 * A colour stored as hue/saturation/brightness (so the picker does not lose the hue at grey or black) plus alpha,
 * with an optional animated rainbow. {@link #rgb()} returns the current 0xRRGGBB value.
 */
public class ColorSetting extends Setting<Integer> {
	private float hue, sat, bri;
	private boolean rainbow;
	private final boolean hasAlpha;
	private int alpha;

	public ColorSetting(String name, String description, int argb, boolean hasAlpha) {
		super(name, description, argb);
		this.hasAlpha = hasAlpha;
		setArgb(argb);
	}

	public ColorSetting(String name, String description, int argb) {
		this(name, description, argb, false);
	}

	public void setArgb(int argb) {
		float[] hsb = ColorUtil.rgbToHsb(argb);
		hue = hsb[0]; sat = hsb[1]; bri = hsb[2];
		alpha = (argb >>> 24) & 0xFF;
		if (!hasAlpha) alpha = 255;
		this.value = argb;
	}

	public float hue() { return hue; }
	public float saturation() { return sat; }
	public float brightness() { return bri; }
	public int alpha() { return alpha; }
	public boolean hasAlpha() { return hasAlpha; }
	public boolean isRainbow() { return rainbow; }
	public void setRainbow(boolean r) { rainbow = r; }

	public void setHsb(float h, float s, float b) {
		hue = h; sat = s; bri = b;
		value = (alpha << 24) | ColorUtil.hsbToRgb(h, s, b);
	}

	public void setAlpha(int a) {
		alpha = Math.max(0, Math.min(255, a));
		value = (alpha << 24) | (value & 0xFFFFFF);
	}

	/** Current RGB (animated when rainbow is on). {@code offset} shifts the rainbow phase, e.g. per block. */
	public int rgb(float offset) {
		if (rainbow) {
			float h = (System.currentTimeMillis() % 4000L) / 4000f + offset;
			return ColorUtil.hsbToRgb(h - (float) Math.floor(h), Math.max(sat, 0.55f), Math.max(bri, 0.85f));
		}
		return ColorUtil.hsbToRgb(hue, sat, bri);
	}

	public int rgb() { return rgb(0); }

	public int argb() { return (alpha << 24) | rgb(); }

	public String hex() { return String.format("#%06X", rgb()); }

	@Override
	public void reset() {
		setArgb(getDefault());
		rainbow = false;
	}

	@Override
	public JsonElement toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("color", String.format("#%08X", (alpha << 24) | ColorUtil.hsbToRgb(hue, sat, bri)));
		o.addProperty("rainbow", rainbow);
		return o;
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json == null || !json.isJsonObject()) return;
		JsonObject o = json.getAsJsonObject();
		try {
			if (o.has("color")) setArgb((int) Long.parseLong(o.get("color").getAsString().replace("#", ""), 16));
			if (o.has("rainbow")) rainbow = o.get("rainbow").getAsBoolean();
		} catch (RuntimeException ignored) {
			// keep the current colour on a malformed entry
		}
	}
}
