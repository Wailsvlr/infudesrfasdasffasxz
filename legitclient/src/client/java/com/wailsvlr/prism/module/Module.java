package com.wailsvlr.prism.module;

import com.google.gson.JsonObject;
import com.wailsvlr.prism.setting.Setting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
	protected static final Minecraft mc = Minecraft.getInstance();

	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;
	/** GLFW key that toggles the module, or {@link GLFW#GLFW_KEY_UNKNOWN} for none. */
	private int key = GLFW.GLFW_KEY_UNKNOWN;
	/** 0..1 animation value used by the GUI and HUD (eases towards enabled/disabled). */
	public float anim;

	protected Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public String getName() { return name; }
	public String getDescription() { return description; }
	public Category getCategory() { return category; }
	public List<Setting<?>> getSettings() { return settings; }
	public boolean isEnabled() { return enabled; }
	public int getKey() { return key; }
	public void setKey(int key) { this.key = key; }

	public void toggle() { setEnabled(!enabled); }

	/** False for settings-only entries (like the ClickGUI theme): clicking them opens their settings instead. */
	public boolean isToggleable() { return true; }

	public void setEnabled(boolean on) {
		if (on == enabled) return;
		enabled = on;
		if (on) onEnable(); else onDisable();
		com.wailsvlr.prism.gui.Notifications.toggled(this);
	}

	protected void onEnable() { }
	protected void onDisable() { }
	public void onTick() { }

	/** Short text shown next to the name in the HUD module list (e.g. the current mode). */
	public String getInfo() { return null; }

	public JsonObject save() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		o.addProperty("key", key);
		JsonObject s = new JsonObject();
		for (Setting<?> setting : settings) s.add(setting.getName(), setting.toJson());
		o.add("settings", s);
		return o;
	}

	public void load(JsonObject o) {
		if (o.has("key")) key = o.get("key").getAsInt();
		if (o.has("settings") && o.get("settings").isJsonObject()) {
			JsonObject s = o.getAsJsonObject("settings");
			for (Setting<?> setting : settings) setting.fromJson(s.get(setting.getName()));
		}
		if (o.has("enabled")) setEnabled(o.get("enabled").getAsBoolean());
	}
}
