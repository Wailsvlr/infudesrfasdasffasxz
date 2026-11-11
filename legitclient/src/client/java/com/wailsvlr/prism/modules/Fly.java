package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class Fly extends Module {
    private final ModeSetting mode;
    private final NumberSetting speed;
    private boolean wasMayfly, wasFlying;

    public Fly() {
        super("Fly", "Flight in singleplayer and multiplayer", Category.MISC);
        mode = add(new ModeSetting("Mode", "Flight technique", "Vanilla", "Vanilla", "Glide"));
        speed = add(new NumberSetting("Speed", "Flight speed multiplier", 1.0, 0.2, 5.0, 0.1, "x"));
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        wasMayfly = mc.player.getAbilities().mayfly;
        wasFlying = mc.player.getAbilities().flying;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null && !server.isPublished()) {
            java.util.UUID id = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp != null && !sp.getAbilities().mayfly) {
                    sp.getAbilities().mayfly = true;
                    sp.onUpdateAbilities();
                }
            });
            return;
        }

        if (mode.is("Vanilla")) {
            mc.player.getAbilities().mayfly = true;
            mc.player.getAbilities().setFlyingSpeed((float) (0.05 * speed.get()));
        } else {
            Vec3 v = mc.player.getDeltaMovement();
            double glideSpeed = -0.04 / speed.get();
            if (v.y < glideSpeed) {
                mc.player.setDeltaMovement(v.x, glideSpeed, v.z);
            }
            mc.player.fallDistance = 0;
        }
    }

    @Override
    protected void onDisable() {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null && !server.isPublished() && mc.player != null) {
            java.util.UUID id = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp != null && !sp.isCreative() && !sp.isSpectator()) {
                    sp.getAbilities().mayfly = false;
                    sp.getAbilities().flying = false;
                    sp.onUpdateAbilities();
                }
            });
        }
        if (mc.player != null && !mc.player.isCreative() && !mc.player.isSpectator()) {
            mc.player.getAbilities().mayfly = wasMayfly;
            mc.player.getAbilities().flying = wasMayfly && wasFlying;
            mc.player.getAbilities().setFlyingSpeed(0.05f);
            mc.player.onUpdateAbilities();
        }
    }

    @Override
    public String getInfo() { return mode.get(); }
}
