package com.wailsvlr.prism.module;

public enum Category {
	RENDER("Render"),
	WORLD("World"),
	MISC("Misc"),
	COMBAT("Combat"),
	CLIENT("Client");

	private final String title;

	Category(String title) { this.title = title; }

	public String title() { return title; }
}
