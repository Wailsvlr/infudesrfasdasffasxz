package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.modules.Printer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Printer setup guide's slot frames on crafters (locked slots skip the regular slot drawing, so this hooks every return). */
@Mixin(CrafterScreen.class)
public class CrafterScreenMixin {
	@Inject(method = "renderSlot", at = @At("RETURN"))
	private void prism$guideFrame(GuiGraphics g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		PrismClient.modules().get(Printer.class).renderGuideSlot(g, slot);
	}
}
