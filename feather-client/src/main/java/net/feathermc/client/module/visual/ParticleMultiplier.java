package net.feathermc.client.module.visual;

import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.TickEvent;
import net.feathermc.client.module.Module;
import net.minecraft.core.particles.ParticleTypes;

public class ParticleMultiplier extends Module {
    private final NumberSetting multiplier = addSetting(new NumberSetting("Multiplier", 2, 1, 10, 1));

    public ParticleMultiplier() {
        super("Particle Multiplier", "Multiplies attack and crit particle effects", Category.VISUAL);
    }

    public int getMultiplier() {
        return multiplier.getValue().intValue();
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.swinging && mc.player.swingTime == 1) {
            int extra = getMultiplier() - 1;
            for (int i = 0; i < extra; i++) {
                double px = mc.player.getX() + (mc.player.getRandom().nextDouble() - 0.5) * 2;
                double py = mc.player.getY() + mc.player.getRandom().nextDouble() * 2;
                double pz = mc.player.getZ() + (mc.player.getRandom().nextDouble() - 0.5) * 2;
                mc.level.addParticle(ParticleTypes.CRIT, px, py, pz,
                        mc.player.getRandom().nextGaussian() * 0.02,
                        mc.player.getRandom().nextGaussian() * 0.02,
                        mc.player.getRandom().nextGaussian() * 0.02);
            }
        }
    }
}
