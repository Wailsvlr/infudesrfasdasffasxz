package dev.donut.client.module.render;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.RenderEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.BooleanSetting;
import dev.donut.client.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;

public class NameTags extends Module {
    private final BooleanSetting showHealth = addSetting(new BooleanSetting("Show Health", true));
    private final NumberSetting scale = addSetting(new NumberSetting("Scale", 1.5, 0.5, 3.0, 0.1));

    public NameTags() {
        super("NameTags", "Better nametags with health", Category.RENDER);
    }

    @EventBus.Subscribe
    public void onWorldRender(RenderEvent.World event) {
        if (nullCheck()) return;
    }
}
