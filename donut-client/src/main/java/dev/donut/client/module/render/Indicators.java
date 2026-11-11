package dev.donut.client.module.render;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.RenderEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class Indicators extends Module {
    public Indicators() {
        super("Indicators", "Shows offscreen player indicators", Category.RENDER);
    }

    @EventBus.Subscribe
    public void onHud(RenderEvent.Hud event) {
        if (nullCheck()) return;

        DrawContext ctx = event.getContext();
        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (mc.player.distanceTo(player) > 64) continue;

            Vec3d dir = player.getPos().subtract(mc.player.getPos()).normalize();
            double angle = Math.atan2(dir.z, dir.x) - Math.toRadians(mc.player.getYaw() + 90);
            int ix = cx + (int)(Math.cos(angle) * 60);
            int iy = cy + (int)(Math.sin(angle) * 60);

            ctx.fill(ix - 2, iy - 2, ix + 2, iy + 2, 0xFFFF0000);
        }
    }
}
