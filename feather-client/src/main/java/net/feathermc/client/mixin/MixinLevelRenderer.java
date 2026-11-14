package net.feathermc.client.mixin;

import net.feathermc.client.FeatherClient;
import net.feathermc.client.module.render.TimeChanger;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    // Hook point for block overlay rendering and time changer
}
