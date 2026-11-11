package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.ModeSetting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public final class Criticals extends Module {

    private static Criticals instance;
    private final ModeSetting mode;
    private boolean doPacket;

    public Criticals() {
        super("Criticals", "Ensures critical hits on every attack", Category.COMBAT);
        instance = this;
        mode = add(new ModeSetting("Mode", "Critical hit method", "Packet", "Packet", "MiniJump"));
    }

    public static void onAttack() {
        if (instance == null || !instance.isEnabled()) return;
        if (mc.player == null || !mc.player.onGround()) return;

        if (instance.mode.is("Packet")) {
            instance.doPacket = true;
        } else if (instance.mode.is("MiniJump")) {
            Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(v.x, 0.1, v.z);
            mc.player.setOnGround(false);
        }
    }

    @Override
    public void onTick() {
        if (!doPacket || mc.player == null) return;
        doPacket = false;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        var conn = mc.player.connection;

        conn.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, false, mc.player.horizontalCollision));
        conn.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, mc.player.horizontalCollision));
        conn.send(new ServerboundMovePlayerPacket.Pos(x, y + 1.1e-5, z, false, mc.player.horizontalCollision));
        conn.send(new ServerboundMovePlayerPacket.Pos(x, y, z, true, mc.player.horizontalCollision));
    }

    @Override
    protected void onDisable() { doPacket = false; }

    @Override
    public String getInfo() { return mode.get(); }
}
