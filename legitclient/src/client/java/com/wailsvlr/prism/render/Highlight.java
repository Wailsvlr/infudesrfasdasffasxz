package com.wailsvlr.prism.render;

import net.minecraft.world.phys.AABB;

/**
 * One thing to highlight this frame. {@code rgb} is the base colour; the style decides how it is drawn.
 * {@code label} is optional text shown above it (projected to the screen by the HUD), {@code labelLift} blocks above
 * the top of the box. {@code health} (0..1, or -1 for none) adds a small health bar to the label and the 2D frame.
 */
public record Highlight(AABB box, int rgb, HighlightStyle style, String label, double labelLift, float health) {
	public Highlight(AABB box, int rgb, HighlightStyle style, String label) {
		this(box, rgb, style, label, 0.25, -1f);
	}

	public Highlight(AABB box, int rgb, HighlightStyle style, String label, double labelLift) {
		this(box, rgb, style, label, labelLift, -1f);
	}
}
