package com.wailsvlr.prism.render;

/**
 * How a finder module draws its highlights.
 *
 * @param mode           "Outline", "Fill", "Both", "Corners" or "Corners + Fill"
 * @param outlineAlpha   0..1 opacity of the outline / corner lines
 * @param fillAlpha      0..1 opacity of the filled faces
 * @param lineWidth      line width in pixels at 1080p (scaled with the window)
 * @param throughWalls   draw over blocks in front instead of being hidden by them
 * @param tracers        draw a line from the crosshair to the target
 * @param tracerAlpha    0..1 tracer opacity
 * @param distanceFade   fade targets out towards the end of the range
 * @param range          range in blocks (for the fade)
 * @param pulse          animate the opacity
 * @param pulseSpeed     pulses per second
 * @param expand         grow (+) or shrink (-) the box, in blocks
 * @param tracerFromFeet start tracers at the player's feet instead of the crosshair
 * @param glow           a wide, faint line under every line: a soft glow
 * @param softFill       the filled faces fade out towards the top
 */
public record HighlightStyle(String mode, float outlineAlpha, float fillAlpha, float lineWidth, boolean throughWalls,
							 boolean tracers, float tracerAlpha, boolean distanceFade, double range,
							 boolean pulse, float pulseSpeed, double expand, boolean tracerFromFeet, boolean glow, boolean softFill) {

	/** Without the glow / soft fill switches: both on. */
	public HighlightStyle(String mode, float outlineAlpha, float fillAlpha, float lineWidth, boolean throughWalls,
						  boolean tracers, float tracerAlpha, boolean distanceFade, double range,
						  boolean pulse, float pulseSpeed, double expand, boolean tracerFromFeet) {
		this(mode, outlineAlpha, fillAlpha, lineWidth, throughWalls, tracers, tracerAlpha, distanceFade, range, pulse, pulseSpeed, expand,
			tracerFromFeet, true, true);
	}

	/** "2D": a frame drawn on the screen around the target (the HUD draws it), nothing in the world but the tracer. */
	public boolean screen2D() { return mode.equals("2D"); }

	public boolean drawsOutline() { return mode.equals("Outline") || mode.equals("Both"); }
	public boolean drawsCorners() { return mode.startsWith("Corners"); }
	public boolean drawsFill() { return mode.equals("Fill") || mode.equals("Both") || mode.equals("Corners + Fill"); }
}
