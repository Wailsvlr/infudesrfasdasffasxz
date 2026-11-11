package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Freecam;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: mouse movement turns the camera, not the player (so no rotation change reaches the server). */
@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void prism$freecamTurn(double dx, double dy, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer)) return;
		Freecam fc = Freecam.active();
		if (fc == null) return;
		fc.turn(dx, dy);
		ci.cancel();
	}
}
