package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class Fly extends Module {
    private final ModeSetting mode;
    private final NumberSetting speed;
    private boolean wasMayfly, wasFlying;
    private int antiKickTicks;

    public Fly() {
        super("Fly", "Flight in singleplayer and multiplayer", Category.MISC);
        mode = add(new ModeSetting("Mode", "Flight technique", "Motion", "Vanilla", "Motion", "Glide"));
        speed = add(new NumberSetting("Speed", "Flight speed multiplier", 1.0, 0.2, 5.0, 0.1, "x"));
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        wasMayfly = mc.player.getAbilities().mayfly;
        wasFlying = mc.player.getAbilities().flying;
        antiKickTicks = 0;
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

        } else if (mode.is("Motion")) {
            double spd = 0.5 * speed.get();
            double vy = 0;

            if (mc.options.keyJump.isDown()) {
                vy = spd;
            } else if (mc.options.keyShift.isDown()) {
                vy = -spd;
            }

            // Horizontal movement based on look direction
            float yaw = mc.player.getYRot();
            double rad = Math.toRadians(yaw);
            double mx = 0, mz = 0;

            if (mc.options.keyUp.isDown()) {
                mx -= Math.sin(rad) * spd;
                mz += Math.cos(rad) * spd;
            }
            if (mc.options.keyDown.isDown()) {
                mx += Math.sin(rad) * spd;
                mz -= Math.cos(rad) * spd;
            }
            if (mc.options.keyLeft.isDown()) {
                mx += Math.cos(rad) * spd;
                mz += Math.sin(rad) * spd;
            }
            if (mc.options.keyRight.isDown()) {
                mx -= Math.cos(rad) * spd;
                mz -= Math.sin(rad) * spd;
            }

            // Anti-kick: every 40 ticks drop slightly so server doesn't kick for flying
            antiKickTicks++;
            if (antiKickTicks >= 40) {
                antiKickTicks = 0;
                vy = -0.04;
            }

            mc.player.setDeltaMovement(mx, vy, mz);
            mc.player.fallDistance = 0;

            // Spoof onGround periodically to reduce anticheat flags
            if (antiKickTicks % 4 == 0 && mc.player.connection != null) {
                mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(
                        true, mc.player.horizontalCollision));
            }

        } else if (mode.is("Glide")) {
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
        antiKickTicks = 0;
    }

    @Override
    public String getInfo() { return mode.get(); }
}
