package com.wailsvlr.prism.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Free text typed in the ClickGUI (click the box, type, Enter). Used for comma separated block / item ids. */
public class TextSetting extends Setting<String> {
	private final String placeholder;

	public TextSetting(String name, String description, String defaultValue, String placeholder) {
		super(name, description, defaultValue);
		this.placeholder = placeholder;
	}

	public String getPlaceholder() { return placeholder; }

	@Override
	public void set(String v) { value = v == null ? "" : v; }

	/** The comma (or space) separated entries, lower case, "minecraft:" added when no namespace is given. */
	public List<String> ids() {
		List<String> out = new ArrayList<>();
		for (String part : value.split("[,;\\s]+")) {
			String id = part.trim().toLowerCase(Locale.ROOT);
			if (id.isEmpty()) continue;
			out.add(id.contains(":") ? id : "minecraft:" + id);
		}
		return out;
	}

	@Override
	public JsonElement toJson() { return new JsonPrimitive(value); }

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive()) value = json.getAsString();
	}
}
