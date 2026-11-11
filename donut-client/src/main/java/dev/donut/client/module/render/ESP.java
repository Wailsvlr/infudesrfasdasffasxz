package dev.donut.client.module.render;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.RenderEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.ModeSetting;
import dev.donut.client.setting.NumberSetting;
import dev.donut.client.util.RenderUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ESP extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Box", "Box", "Outline"));
    private final NumberSetting range = addSetting(new NumberSetting("Range", 64.0, 8.0, 256.0, 8.0));

    public ESP() {
        super("ESP", "Highlights players through walls", Category.RENDER, 88);
    }

    @EventBus.Subscribe
    public void onWorldRender(RenderEvent.World event) {
        if (nullCheck()) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (mc.player.distanceTo(player) > range.getValue()) continue;

            Vec3d interpolated = RenderUtil.getInterpolatedPos(player, event.getTickDelta());
            Vec3d delta = interpolated.subtract(player.getPos());
            Box box = player.getBoundingBox().offset(delta.x, delta.y, delta.z);

            float r = 0f, g = 0.9f, b = 1f;
            if (player.getHealth() < 8f) {
                r = 1f; g = 0.3f; b = 0.3f;
            }
            RenderUtil.drawBox(event.getMatrices(), box, r, g, b, 1f);
        }
    }
}
