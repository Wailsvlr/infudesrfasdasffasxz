package com.wailsvlr.prism.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** BaseValue reads spawner stack holograms ("64x Skeleton Spawner") that servers draw with text displays. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
	@Accessor("DATA_TEXT_ID")
	static EntityDataAccessor<Component> prism$textId() { throw new AssertionError(); }
}
