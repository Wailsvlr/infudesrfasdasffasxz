package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.ModeSetting;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.ArrayList;
import java.util.List;

public final class Blink extends Module {

    private static Blink instance;
    private final ModeSetting mode;
    private final List<Packet<?>> packets = new ArrayList<>();
    private double startX, startY, startZ;

    public Blink() {
        super("Blink", "Holds movement packets then releases them", Category.MISC);
        instance = this;
        mode = add(new ModeSetting("Mode", "What happens on disable", "Send", "Send", "Cancel"));
    }

    public static boolean shouldCapture(Packet<?> packet) {
        if (instance == null || !instance.isEnabled()) return false;
        return packet instanceof ServerboundMovePlayerPacket;
    }

    public static void capture(Packet<?> packet) {
        if (instance != null && instance.isEnabled()) {
            instance.packets.add(packet);
        }
    }

    @Override
    protected void onEnable() {
        packets.clear();
        if (mc.player != null) {
            startX = mc.player.getX();
            startY = mc.player.getY();
            startZ = mc.player.getZ();
        }
    }

    @Override
    protected void onDisable() {
        if (mc.player == null || mc.player.connection == null) {
            packets.clear();
            return;
        }

        if (mode.is("Send")) {
            for (Packet<?> p : packets) {
                mc.player.connection.send(p);
            }
        } else if (mode.is("Cancel")) {
            mc.player.setPos(startX, startY, startZ);
        }
        packets.clear();
    }

    @Override
    public String getInfo() {
        return mode.get() + " (" + packets.size() + ")";
    }
}
