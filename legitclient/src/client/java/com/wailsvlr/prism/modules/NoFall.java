package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.ModeSetting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class NoFall extends Module {
    private final ModeSetting mode;

    public NoFall() {
        super("NoFall", "Prevent fall damage in singleplayer and multiplayer", Category.MISC);
        mode = add(new ModeSetting("Mode", "Bypass technique", "Spoof", "Spoof", "Packet"));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.connection == null) return;

        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null && !server.isPublished()) {
            java.util.UUID id = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp != null) sp.resetFallDistance();
            });
            return;
        }

        if (mc.player.isPassenger() || mc.player.isFallFlying()
                || mc.player.isInWater() || mc.player.isInLava()
                || mc.player.getAbilities().flying) return;

        if (mode.is("Spoof")) {
            if (mc.player.fallDistance > 1.5) {
                mc.player.connection.send(
                        new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
                mc.player.resetFallDistance();
            }
        } else {
            if (mc.player.fallDistance > 1.5 && mc.player.getDeltaMovement().y < 0) {
                double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
                mc.player.connection.send(
                        new ServerboundMovePlayerPacket.Pos(x, y - 0.04, z, true, mc.player.horizontalCollision));
                mc.player.connection.send(
                        new ServerboundMovePlayerPacket.Pos(x, y, z, false, mc.player.horizontalCollision));
                mc.player.resetFallDistance();
            }
        }
    }

    @Override
    public String getInfo() { return mode.get(); }
}
