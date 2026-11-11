package dev.donut.client.module.utility;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;

public class PearlCatch extends Module {
    public PearlCatch() {
        super("PearlCatch", "Alerts on incoming pearls", Category.UTILITY);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        for (var entity : mc.world.getEntities()) {
            if (entity instanceof EnderPearlEntity pearl) {
                if (pearl.squaredDistanceTo(mc.player) < 25 && pearl.getOwner() != mc.player) {
                    mc.player.sendMessage(net.minecraft.text.Text.literal("[Donut] Pearl nearby!"), true);
                }
            }
        }
    }
}
