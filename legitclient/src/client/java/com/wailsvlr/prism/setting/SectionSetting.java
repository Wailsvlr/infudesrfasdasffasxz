package com.wailsvlr.prism.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A heading that groups the settings below it in the GUI. It stores nothing. */
public class SectionSetting extends Setting<Boolean> {
	public SectionSetting(String name) {
		super(name, "", true);
	}

	@Override
	public JsonElement toJson() { return JsonNull.INSTANCE; }

	@Override
	public void fromJson(JsonElement json) { }
}
