package net.feathermc.client.module.visual;

import net.feathermc.client.module.Module;

public class TabList extends Module {
    private final BooleanSetting showPing = addSetting(new BooleanSetting("Show Ping Number", true));
    private final BooleanSetting headerFooter = addSetting(new BooleanSetting("Show Header/Footer", true));

    public TabList() {
        super("Tab List", "Tab list display tweaks", Category.VISUAL);
    }

    public boolean shouldShowPing() {
        return showPing.getValue();
    }

    public boolean shouldShowHeaderFooter() {
        return headerFooter.getValue();
    }
}
