package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.util.SelfDestruct;
import org.lwjgl.glfw.GLFW;

/**
 * Panic button: when toggled, instantly disables every module,
 * deletes the mod JAR + config files, and disables itself.
 * Default keybind: DELETE key.
 * After panic, no trace of the mod remains on disk.
 */
public final class Panic extends Module {

    private final BoolSetting deleteJar;
    private final BoolSetting deleteConfig;

    public Panic() {
        super("Panic", "Emergency kill switch - disables all and deletes traces", Category.CLIENT);
        setKey(GLFW.GLFW_KEY_DELETE);
        deleteJar = add(new BoolSetting("Delete JAR", "Remove the mod file from mods folder", true));
        deleteConfig = add(new BoolSetting("Delete Config", "Remove all config/settings files", true));
    }

    @Override
    protected void onEnable() {
        // Disable every other module first
        for (Module m : PrismClient.modules().all()) {
            if (m == this) continue;
            if (m.isEnabled()) m.setEnabled(false);
        }

        // Delete traces
        if (deleteJar.isOn()) SelfDestruct.deleteJar();
        if (deleteConfig.isOn()) SelfDestruct.deleteConfig();

        // Disable self - no modules running, no files on disk
        setEnabled(false);

        if (mc.player != null) {
            mc.gui.getChat().addMessage(
                    net.minecraft.network.chat.Component.literal("Client cleaned.")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
