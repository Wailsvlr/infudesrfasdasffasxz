package com.wailsvlr.prism.util;

public final class ColorUtil {
	private ColorUtil() { }

	public static int argb(int a, int rgb) { return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF); }

	public static int alpha(int argb, float alpha01) { return argb(Math.round(alpha01 * 255f), argb); }

	public static int withAlphaMul(int argb, float mul) {
		int a = (argb >>> 24) & 0xFF;
		return argb(Math.round(a * mul), argb);
	}

	/** Linear blend of two ARGB colours. */
	public static int lerp(int c1, int c2, float t) {
		t = Math.max(0, Math.min(1, t));
		int a = Math.round(((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
		int r = Math.round(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
		int g = Math.round(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
		int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int hsbToRgb(float h, float s, float b) {
		return java.awt.Color.HSBtoRGB(h, s, b) & 0xFFFFFF;
	}

	public static float[] rgbToHsb(int rgb) {
		return java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
	}

	/** Brighten (amount &gt; 0) or darken (amount &lt; 0) keeping alpha. */
	public static int shade(int argb, float amount) {
		int target = amount >= 0 ? 0xFFFFFFFF : 0xFF000000;
		int mixed = lerp(argb, target, Math.abs(amount));
		return (argb & 0xFF000000) | (mixed & 0xFFFFFF);
	}
}
