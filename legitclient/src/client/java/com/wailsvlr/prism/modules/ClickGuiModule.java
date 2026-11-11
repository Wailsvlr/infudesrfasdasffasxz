package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ColorSetting;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.SectionSetting;

/** Look of the ClickGUI. The GUI itself opens with the "Open ClickGUI" key (Right Shift by default). */
public class ClickGuiModule extends Module {
	public final ColorSetting accent, accent2;
	public final BoolSetting gradient, blur, sounds, descriptions, middleClickFriends;
	public final NumberSetting dim, panelOpacity, radius, animationSpeed, panelWidth;
	public final ModeSetting style, colors, font;

	public ClickGuiModule() {
		super("ClickGUI", "Theme of this menu. Open it with Right Shift (change the key in Options > Controls)", Category.CLIENT);
		add(new SectionSetting("Theme"));
		style = add(new ModeSetting("Style", "Sidebar: a category list on the left opens the panels, enabled modules fill their row with "
			+ "colour, the dots open the settings. Blocks: solid coloured headers, centred names. Cards: a switch and a gear on every module. "
			+ "Classic: the original look", "Sidebar", "Sidebar", "Blocks", "Cards", "Classic"));
		font = add(new ModeSetting("Font", "Inter: a smooth font for the menu, the module list and labels. Minecraft: the game's own pixel font",
			"Inter", "Inter", "Minecraft"));
		colors = add(new ModeSetting("Colors", "Mono: black and white (white to grey along the panels). Accent: the colours below",
			"Mono", "Mono", "Accent"));
		accent = add(new ColorSetting("Accent", "Main accent colour", 0xFF8B5CF6));
		gradient = add(new BoolSetting("Gradient", "Blend the accent into a second colour", true));
		accent2 = add(new ColorSetting("Accent 2", "Second accent colour of the gradient", 0xFF22D3EE)).visibleWhen(gradient::isOn);
		panelOpacity = add(new NumberSetting("Panel Opacity", "Opacity of the panels", 92, 40, 100, 1, "%"));
		radius = add(new NumberSetting("Corner Radius", "Roundness of panels and controls", 6, 0, 12, 1, "px"));
		panelWidth = add(new NumberSetting("Panel Width", "Width of each category panel", 112, 100, 180, 2, ""));
		add(new SectionSetting("Background"));
		blur = add(new BoolSetting("Blur", "Blur the game behind the menu", true));
		dim = add(new NumberSetting("Dim", "Darken the game behind the menu", 35, 0, 90, 1, "%"));
		add(new SectionSetting("Behaviour"));
		animationSpeed = add(new NumberSetting("Animation Speed", "Speed of opening, toggles and expanding", 1.0, 0.25, 3, 0.25, "x"));
		descriptions = add(new BoolSetting("Descriptions", "Show what a module or option does when hovering it", true));
		sounds = add(new BoolSetting("Click Sounds", "Play a soft click on toggles", true));
		middleClickFriends = add(new BoolSetting("Middle Click Friends", "Middle click a player in game to add or remove them as a friend", true));
	}

	@Override
	public void setEnabled(boolean on) { /* not a toggle: the theme always applies */ }

	@Override
	public boolean isToggleable() { return false; }

	public boolean cards() { return style.is("Cards"); }

	public boolean blocks() { return style.is("Blocks"); }

	public boolean sidebar() { return style.is("Sidebar"); }

	public boolean mono() { return colors.is("Mono"); }

	public int accentAt(float t) {
		if (mono()) return 0xFF000000 | com.wailsvlr.prism.util.ColorUtil.lerp(0xFFFFFFFF, 0xFF8C8C8C, Math.max(0, Math.min(1, t)));
		int a = accent.rgb();
		if (!gradient.isOn()) return 0xFF000000 | a;
		return 0xFF000000 | com.wailsvlr.prism.util.ColorUtil.lerp(0xFF000000 | a, 0xFF000000 | accent2.rgb(), t);
	}
}
