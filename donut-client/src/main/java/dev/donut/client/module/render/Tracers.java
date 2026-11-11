package dev.donut.client.module.render;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.RenderEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;
import dev.donut.client.util.RenderUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class Tracers extends Module {
    private final NumberSetting range = addSetting(new NumberSetting("Range", 128.0, 16.0, 256.0, 8.0));

    public Tracers() {
        super("Tracers", "Draws lines to players", Category.RENDER);
    }

    @EventBus.Subscribe
    public void onWorldRender(RenderEvent.World event) {
        if (nullCheck()) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (mc.player.distanceTo(player) > range.getValue()) continue;

            Vec3d target = RenderUtil.getInterpolatedPos(player, event.getTickDelta());
            target = target.add(0, player.getStandingEyeHeight() / 2.0, 0);

            float dist = (float) mc.player.distanceTo(player);
            float maxDist = range.getFloat();
            float r = Math.min(1f, dist / maxDist);
            float g = Math.min(1f, 1f - dist / maxDist);

            RenderUtil.drawLine(event.getMatrices(), cam, target, r, g, 0.2f, 1f);
        }
    }
}
