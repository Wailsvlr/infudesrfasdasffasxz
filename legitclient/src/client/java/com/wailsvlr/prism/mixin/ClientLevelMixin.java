package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.modules.BlockFinder;
import com.wailsvlr.prism.modules.OreFinder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells OreFinder and BlockFinder about every block change on the client (server updates and your own placing), so new ores show at once. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "setBlocksDirty", at = @At("HEAD"))
	private void prism$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		PrismClient.modules().get(OreFinder.class).onBlockChanged(pos, oldState, newState);
		PrismClient.modules().get(BlockFinder.class).onBlockChanged(pos, oldState, newState);
		PrismClient.modules().get(com.wailsvlr.prism.modules.TrailFinder.class).onBlockChanged(pos, oldState, newState);
		PrismClient.modules().get(com.wailsvlr.prism.modules.ChunkFinder.class).onBlockChanged(pos, oldState, newState);
	}
}
