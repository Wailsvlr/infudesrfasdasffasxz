package dev.donut.client.module.world;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.PacketEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;

public class AutoFish extends Module {
    public AutoFish() {
        super("AutoFish", "Automatically reels and recasts", Category.WORLD);
    }

    @EventBus.Subscribe
    public void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;
        if (event.getPacket() instanceof PlaySoundS2CPacket sound) {
            if (sound.getSound().value() == SoundEvents.ENTITY_FISHING_BOBBER_SPLASH) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                new Thread(() -> {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ignored) {}
                    mc.execute(() -> mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND));
                }).start();
            }
        }
    }
}
