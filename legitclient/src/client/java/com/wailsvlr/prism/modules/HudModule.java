package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;

public class HudModule extends Module {
	public final ModeSetting style;
	public final BoolSetting armor, potions;
	public final BoolSetting watermark, moduleList, notifications, finderStats, labelBackground, info, infoCoords, infoNether, infoFacing, infoFps, infoPing, infoSpeed;
	public final ModeSetting listSide, listStyle;
	public final NumberSetting labelScale;

	public HudModule() {
		super("HUD", "Watermark, active module list, finder counters and the labels of highlighted blocks", Category.CLIENT);
		style = add(new ModeSetting("Style", "Cards: every part in a dark rounded card, the module list in strips. Text: plain shadowed text",
			"Cards", "Cards", "Text"));
		watermark = add(new BoolSetting("Watermark", "Show the client name in the corner", true));
		moduleList = add(new BoolSetting("Module List", "List the enabled modules", true));
		listSide = add(new ModeSetting("List Side", "Corner of the module list", "Right", "Right", "Left")).visibleWhen(moduleList::isOn);
		listStyle = add(new ModeSetting("List Style", "Text: coloured names without a background, the logo above the list. Boxed: names in dark boxes",
			"Text", "Text", "Boxed")).visibleWhen(() -> moduleList.isOn() && style.is("Text"));
		info = add(new BoolSetting("Info", "A box with coordinates, facing, FPS, ping and speed", true));
		infoCoords = add(new BoolSetting("Coordinates", "Your X Y Z", true)).visibleWhen(info::isOn);
		infoNether = add(new BoolSetting("Other Dimension", "The matching Nether (or Overworld) coordinates", true)).visibleWhen(info::isOn);
		infoFacing = add(new BoolSetting("Facing", "The direction you look and its axis", true)).visibleWhen(info::isOn);
		infoFps = add(new BoolSetting("FPS", "Frames per second", true)).visibleWhen(info::isOn);
		infoPing = add(new BoolSetting("Ping", "Your latency to the server", true)).visibleWhen(info::isOn);
		infoSpeed = add(new BoolSetting("Speed", "How fast you move, in blocks per second", false)).visibleWhen(info::isOn);
		armor = add(new BoolSetting("Armor", "Your armour and the items in your hands, with how much durability is left (next to the hotbar)", true));
		potions = add(new BoolSetting("Potion Effects", "Your effects with their names and time left (in place of the small icons in the corner)", true));
		notifications = add(new BoolSetting("Toggle Notifications", "A small card in the bottom-right corner when a module turns on or off", true));
		finderStats = add(new BoolSetting("Finder Counters", "Show how many targets the finders see", true));
		labelScale = add(new NumberSetting("Label Size", "Size of block labels", 0.8, 0.5, 1.5, 0.05, "x"));
		labelBackground = add(new BoolSetting("Label Background", "Dark box behind block labels", true));
		setEnabled(true);
	}

	public boolean cards() { return style.is("Cards"); }
}
