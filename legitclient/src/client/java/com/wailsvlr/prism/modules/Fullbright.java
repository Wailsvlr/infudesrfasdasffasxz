package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;

/**
 * See in the dark. Changes only the client's lightmap (see LightTextureMixin), so nothing is sent to the server:
 * "Full" gives every block full white light (like daylight everywhere), "Night Vision" works like the potion
 * (keeps the night's blue tint) and "Gamma" pushes the brightness slider past its maximum.
 */
public class Fullbright extends Module {
	public final ModeSetting mode;
	public final NumberSetting strength, fadeSpeed;
	/** 0..1, eases in and out when toggling. */
	private float amount;

	public Fullbright() {
		super("Fullbright", "See in the dark: caves, nights and the nether become fully lit (client side only)", Category.RENDER);
		mode = add(new ModeSetting("Mode", "Full: everything lit like daylight. Night Vision: like the potion (bluish at night). Gamma: brightness far past 100 %",
			"Full", "Full", "Night Vision", "Gamma"));
		strength = add(new NumberSetting("Strength", "How bright it gets", 100, 10, 100, 5, "%"));
		fadeSpeed = add(new NumberSetting("Fade", "How fast it fades in and out (0 = instantly)", 0.5, 0, 2, 0.1, " s"));
	}

	/** Called every client tick, also while disabled, so it can fade out. */
	public void animate() {
		float target = isEnabled() ? strength.getFloat() / 100f : 0f;
		float step = fadeSpeed.getFloat() <= 0 ? 1f : 1f / (fadeSpeed.getFloat() * 20f);
		amount = amount < target ? Math.min(target, amount + step) : Math.max(target, amount - step);
	}

	public float amount() { return amount; }

	/** Ambient light factor: 1 means every block gets the ambient colour as its light. */
	public float ambient(float vanilla) { return mode.is("Full") ? Math.max(vanilla, amount) : vanilla; }

	/** Ambient colour, moved towards white. */
	public org.joml.Vector3fc ambientColor(org.joml.Vector3fc vanilla) {
		if (!mode.is("Full") || amount <= 0) return vanilla;
		return new org.joml.Vector3f(vanilla).lerp(new org.joml.Vector3f(1, 1, 1), amount);
	}

	/** Night vision factor for the lightmap. */
	public float nightVision(float vanilla) { return mode.is("Night Vision") ? Math.max(vanilla, amount) : vanilla; }

	/** Gamma for the lightmap (vanilla's slider tops out at 1). */
	public float gamma(float vanilla) { return mode.is("Gamma") ? vanilla + amount * 14f : vanilla; }

	@Override
	public String getInfo() { return mode.get(); }
}
