package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.render.HighlightStyle;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.SectionSetting;

/** Shared appearance options of the finder modules ("Look" and "Extras" sections). */
public abstract class FinderModule extends Module {
	public static final String[] MODES = {"Outline", "Fill", "Both", "Corners", "Corners + Fill"};

	protected ModeSetting mode;
	protected NumberSetting outlineOpacity, fillOpacity, lineWidth, expand, pulseSpeed;
	protected BoolSetting throughWalls, pulse, glow, softFill;
	protected NumberSetting range, tracerOpacity, scanInterval;
	protected BoolSetting tracers, distanceFade, labels, labelDistance;
	protected ModeSetting tracerOrigin;
	protected BoolSetting pointer, cards;
	/** The nearest target, for the pointer arrow (set by each finder when it scans). */
	private volatile com.wailsvlr.prism.gui.Pointers.Target nearest;

	protected FinderModule(String name, String description) {
		super(name, description, Category.RENDER);
	}

	/** Adds the "Look" section. Call from the subclass constructor at the place the section should appear. */
	protected void addLookSettings(String defaultMode, double defaultFill) {
		addLookSettings(defaultMode, defaultFill, MODES);
	}

	protected void addLookSettings(String defaultMode, double defaultFill, String... modes) {
		add(new SectionSetting("Look"));
		mode = add(new ModeSetting("Mode", "How targets are drawn: box outline, filled box, both, or only the corners"
			+ (java.util.Arrays.asList(modes).contains("2D") ? ". 2D: a frame on the screen with a health bar" : ""), defaultMode, modes));
		outlineOpacity = add(new NumberSetting("Outline Opacity", "Opacity of the outline / corner lines", 100, 0, 100, 1, "%"))
			.visibleWhen(() -> !mode.is("Fill"));
		fillOpacity = add(new NumberSetting("Fill Opacity", "Opacity of the filled faces", defaultFill, 0, 100, 1, "%"))
			.visibleWhen(() -> mode.is("Fill") || mode.is("Both") || mode.is("Corners + Fill"));
		lineWidth = add(new NumberSetting("Line Width", "Line thickness in pixels (at 1080p)", 2.0, 0.5, 8, 0.5, "px"))
			.visibleWhen(() -> !mode.is("Fill") || tracers.isOn());
		throughWalls = add(new BoolSetting("Through Walls", "Draw highlights over blocks that are in front of them", true));
		expand = add(new NumberSetting("Box Size", "Grow or shrink the boxes", 0, -0.1, 0.25, 0.01, ""));
		glow = add(new BoolSetting("Glow", "A soft glow around the lines", true)).visibleWhen(() -> !mode.is("Fill"));
		softFill = add(new BoolSetting("Soft Fill", "The fill fades out towards the top of the box", true))
			.visibleWhen(() -> mode.is("Fill") || mode.is("Both") || mode.is("Corners + Fill"));
		pulse = add(new BoolSetting("Pulse", "Slowly pulse the opacity", false));
		pulseSpeed = add(new NumberSetting("Pulse Speed", "Pulses per second", 1.0, 0.2, 4, 0.1, "/s")).visibleWhen(() -> pulse.isOn());
	}

	/** Adds range, tracers, fade and labels (part of the "Extras" section). */
	protected void addExtraSettings(double defaultRange, boolean defaultTracers, boolean defaultLabels) {
		add(new SectionSetting("Extras"));
		range = add(new NumberSetting("Range", "How far to look, in blocks. The client only knows the chunks inside your render distance, so that is the real limit", defaultRange, 8, 10250, 2, "m"));
		tracers = add(new BoolSetting("Tracers", "Lines from your crosshair to each target", defaultTracers));
		tracerOpacity = add(new NumberSetting("Tracer Opacity", "Opacity of the tracer lines", 70, 5, 100, 1, "%")).visibleWhen(() -> tracers.isOn());
		tracerOrigin = add(new ModeSetting("Tracer Origin", "Where tracers start", "Crosshair", "Crosshair", "Feet")).visibleWhen(() -> tracers.isOn());
		distanceFade = add(new BoolSetting("Distance Fade", "Fade targets towards the end of the range", false));
		labels = add(new BoolSetting("Labels", "Show a name tag above each target", defaultLabels));
		labelDistance = add(new BoolSetting("Label Distance", "Add the distance to the label", true)).visibleWhen(() -> labels.isOn());
		scanInterval = add(new NumberSetting("Scan Interval", "How often to look for new targets", 10, 2, 40, 1, " ticks"));
		pointer = add(new BoolSetting("Pointer", "An arrow around the crosshair pointing at the nearest target, with its distance", false));
		cards = add(new BoolSetting("Alert Cards", "Also show this finder's alerts as a card in the corner (not only in chat)", true)).visibleWhen(this::hasAlerts);
	}

	/** True when this finder has an alert switched on (shows the "Alert Cards" option). */
	protected boolean hasAlerts() { return false; }

	/** Called by the finders after a scan: the nearest target (null: none). */
	protected void nearest(net.minecraft.world.phys.Vec3 pos, String name, int rgb) {
		nearest = pos == null ? null : new com.wailsvlr.prism.gui.Pointers.Target(pos, name, rgb);
	}

	/** The pointer arrow's target, or null when the pointer is off. */
	public com.wailsvlr.prism.gui.Pointers.Target pointerTarget() {
		return isEnabled() && pointer != null && pointer.isOn() ? nearest : null;
	}

	/** An alert card in the corner (when "Alert Cards" is on). A card with the same key replaces the older one. */
	protected void card(Object key, String text) {
		if (cards != null && cards.isOn()) com.wailsvlr.prism.gui.Notifications.post(key, "render", getName(), text);
	}

	protected HighlightStyle style() {
		return new HighlightStyle(mode.get(), outlineOpacity.getFloat() / 100f, fillOpacity.getFloat() / 100f, lineWidth.getFloat(),
			throughWalls.isOn(), tracers.isOn(), tracerOpacity.getFloat() / 100f, distanceFade.isOn(), range.get(),
			pulse.isOn(), pulseSpeed.getFloat(), expand.get(), tracerOrigin.is("Feet"), glow.isOn(), softFill.isOn());
	}

	@Override
	public String getInfo() { return mode.get(); }
}
