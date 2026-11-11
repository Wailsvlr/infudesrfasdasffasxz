package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.NumberSetting;

/**
 * Hold the Zoom key (C by default, in Options > Controls) to look closer. Only the field of view changes (see
 * GameRendererMixin), the mouse slows down with it and the wheel changes how far you zoom while the key is held
 * (MouseHandlerMixin). Always available: this entry only holds the options.
 */
public class Zoom extends Module {
	private static Zoom instance;
	public final NumberSetting factor, smoothness;
	public final BoolSetting slowMouse, scrollAdjust, hideHand;
	/** The zoom right now (1 = none); eased towards the target. */
	private float current = 1f;
	/** The wheel's zoom while the key is held (-1: use the Factor setting). */
	private float wheel = -1f;
	private long last;

	public Zoom() {
		super("Zoom", "Hold the Zoom key (C, change it in Options > Controls) to look closer, like a spyglass", Category.RENDER);
		instance = this;
		factor = add(new NumberSetting("Factor", "How much closer you see", 4, 1.5, 15, 0.5, "x"));
		smoothness = add(new NumberSetting("Smoothness", "How long zooming in and out takes (0 = instantly)", 0.15, 0, 0.6, 0.05, " s"));
		slowMouse = add(new BoolSetting("Slow Mouse", "Turn the mouse down by the same amount, so aiming stays steady", true));
		scrollAdjust = add(new BoolSetting("Scroll Adjusts", "While zooming, the mouse wheel zooms further in or out (it goes back on release)", true));
		hideHand = add(new BoolSetting("Hide Hand", "Hide your hand and item while zooming", true));
	}

	@Override
	public boolean isToggleable() { return false; }

	private static boolean held() {
		return PrismClient.zoomKey() != null && PrismClient.zoomKey().isDown() && mc.screen == null && mc.player != null;
	}

	/** How much the view is zoomed this frame (1 = not at all). Called while rendering. */
	public static float factor() {
		Zoom z = instance;
		if (z == null) return 1f;
		long now = System.nanoTime();
		float dt = z.last == 0 ? 0 : Math.min(0.1f, (now - z.last) / 1e9f);
		z.last = now;
		boolean h = held();
		if (!h) z.wheel = -1f;
		float target = h ? (z.wheel > 0 ? z.wheel : z.factor.getFloat()) : 1f;
		float t = z.smoothness.getFloat();
		if (t <= 0) z.current = target;
		else {
			// ease in log space so 2x -> 4x feels the same as 4x -> 8x
			double lc = Math.log(z.current), lt = Math.log(target);
			lc += (lt - lc) * (1 - Math.exp(-dt * 4.6 / t));
			z.current = (float) Math.exp(lc);
			if (Math.abs(z.current - target) < 0.002f) z.current = target;
		}
		return z.current;
	}

	/** Mouse wheel while the key is held: zoom further in or out. True when the scroll was used here. */
	public static boolean scroll(double amount) {
		Zoom z = instance;
		if (z == null || !held() || !z.scrollAdjust.isOn() || amount == 0) return false;
		float base = z.wheel > 0 ? z.wheel : z.factor.getFloat();
		z.wheel = (float) Math.max(1.5, Math.min(40, base * (amount > 0 ? 1.25 : 0.8)));
		return true;
	}

	public static boolean slowsMouse() { return instance != null && instance.slowMouse.isOn(); }

	public static boolean hidesHand() { return instance != null && instance.hideHand.isOn() && instance.current > 1.05f; }
}
