package net.feathermc.client.module.hud;

import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.TickEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ReachDisplay extends HudModule {
    private double lastReach;
    private long lastHitTime;
    private final NumberSetting displayTime = addSetting(new NumberSetting("Display Time (s)", 3.0, 0.5, 10.0, 0.5));

    public ReachDisplay() {
        super("Reach Display", "Shows distance to last hit entity", 5, 313);
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult ehr) {
            double dist = mc.player.distanceTo(ehr.getEntity());
            if (mc.player.swinging && dist != lastReach) {
                lastReach = dist;
                lastHitTime = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        long elapsed = System.currentTimeMillis() - lastHitTime;
        if (elapsed > displayTime.getValue() * 1000) return;
        drawText(graphics, String.format("Reach: %.2f blocks", lastReach), getX(), getY());
    }
}
