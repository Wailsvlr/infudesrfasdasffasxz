package net.feathermc.client.module.misc;

import net.feathermc.client.module.Module;

public class NickHider extends Module {
    private String fakeName = "FeatherPlayer";

    public NickHider() {
        super("Nick Hider", "Hides your real name with a custom one", Category.MISC);
    }

    public String getFakeName() {
        return fakeName;
    }

    public void setFakeName(String fakeName) {
        this.fakeName = fakeName;
    }

    public String processName(String original) {
        if (!isEnabled() || mc.player == null) return original;
        String realName = mc.player.getGameProfile().name();
        if (original.contains(realName)) {
            return original.replace(realName, fakeName);
        }
        return original;
    }
}
