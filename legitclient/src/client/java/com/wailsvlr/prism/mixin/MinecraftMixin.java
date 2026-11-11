package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Freecam;
import com.wailsvlr.prism.modules.FastPlace;
import com.wailsvlr.prism.modules.FastBreak;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow private int rightClickDelay;

    @Inject(method = "tick", at = @At("HEAD"))
    private void prism$fastPlace(CallbackInfo ci) {
        if (FastPlace.isActive()) rightClickDelay = 0;
    }

	private static boolean prism$blocked() {
		Freecam fc = Freecam.active();
		return fc != null && !fc.allowsInteraction();
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void prism$freecamAttack(CallbackInfoReturnable<Boolean> cir) {
		if (prism$blocked()) cir.setReturnValue(false);
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void prism$freecamUse(CallbackInfo ci) {
		if (prism$blocked()) ci.cancel();
		// Printer's Easy Place: a right click into the schematic places the schematic's block instead
		else if (com.wailsvlr.prism.modules.Printer.easyPlaceClick()) ci.cancel();
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void prism$freecamContinueAttack(boolean leftClick, CallbackInfo ci) {
		if (prism$blocked()) ci.cancel();
	}
}
