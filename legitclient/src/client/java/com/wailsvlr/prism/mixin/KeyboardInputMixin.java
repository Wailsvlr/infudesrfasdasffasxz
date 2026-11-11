package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Freecam;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: the player gets no movement input (WASD, jump, sneak, sprint move the camera instead). Printer: forced sneak. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void prism$freecamBlockInput(CallbackInfo ci) {
		if (com.wailsvlr.prism.modules.Printer.forceSneak && Freecam.active() == null) {
			// Printer: sneak for a click against a chest or hopper (sent to the server like a held shift key)
			Input k = this.keyPresses;
			this.keyPresses = new Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), true, false);
			return;
		}
		if (Freecam.active() == null) return;
		this.keyPresses = Input.EMPTY;
		this.moveVector = Vec2.ZERO;
	}
}
