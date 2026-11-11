package dev.donut.client.module.render;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.RenderEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;
import dev.donut.client.util.RenderUtil;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ItemESP extends Module {
    private final NumberSetting range = addSetting(new NumberSetting("Range", 32.0, 8.0, 128.0, 4.0));

    public ItemESP() {
        super("ItemESP", "Highlights dropped items", Category.RENDER);
    }

    @EventBus.Subscribe
    public void onWorldRender(RenderEvent.World event) {
        if (nullCheck()) return;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity item)) continue;
            if (mc.player.distanceTo(item) > range.getValue()) continue;

            Vec3d pos = RenderUtil.getInterpolatedPos(item, event.getTickDelta());
            Vec3d d = pos.subtract(item.getPos());
            Box box = item.getBoundingBox().offset(d.x, d.y, d.z);
            RenderUtil.drawBox(event.getMatrices(), box, 1f, 1f, 0f, 0.8f);
        }
    }
}
