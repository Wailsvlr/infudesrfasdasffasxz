package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.modules.AutoSell;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AutoSell's and AuctionHelper's slot frames, drawn with the slot (so tooltips stay on top). 26.1.2 build. */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void prism$sellFrame(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		PrismClient.modules().get(AutoSell.class).renderMark(g, slot);
		PrismClient.modules().get(com.wailsvlr.prism.modules.AuctionHelper.class).renderMark(g, slot);
	}
}
