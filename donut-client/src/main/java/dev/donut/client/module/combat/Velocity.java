package dev.donut.client.module.combat;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.PacketEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

public class Velocity extends Module {
    private final NumberSetting horizontal = addSetting(new NumberSetting("Horizontal", 0.0, 0.0, 100.0, 1.0));
    private final NumberSetting vertical = addSetting(new NumberSetting("Vertical", 0.0, 0.0, 100.0, 1.0));

    public Velocity() {
        super("Velocity", "Reduces knockback", Category.COMBAT);
    }

    @EventBus.Subscribe
    public void onPacket(PacketEvent.Receive event) {
        if (nullCheck()) return;

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
            if (packet.getEntityId() == mc.player.getId()) {
                if (horizontal.getValue() == 0 && vertical.getValue() == 0) {
                    event.cancel();
                }
            }
        }
    }
}
