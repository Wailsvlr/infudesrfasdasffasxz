package com.wailsvlr.prism.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** AutoSell reads the slot under the mouse (for marking) and the menu's position (for drawing the marks). */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("hoveredSlot")
	Slot prism$hoveredSlot();

	@Accessor("leftPos")
	int prism$leftPos();

	@Accessor("topPos")
	int prism$topPos();
}
