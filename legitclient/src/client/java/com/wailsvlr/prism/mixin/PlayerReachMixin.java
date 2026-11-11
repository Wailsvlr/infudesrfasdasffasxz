package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Reach;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerReachMixin {
	@Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
	private void prism$reach(CallbackInfoReturnable<Double> cir) {
		if ((Object) this == Minecraft.getInstance().player) cir.setReturnValue(Reach.distance(cir.getReturnValue()));
	}
}
