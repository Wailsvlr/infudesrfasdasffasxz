package net.feathermc.client.mixin;

import net.feathermc.client.FeatherClient;
import net.feathermc.client.module.movement.ToggleSprint;
import net.feathermc.client.module.movement.ToggleSneak;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onAiStep(CallbackInfo ci) {
        if (FeatherClient.getInstance() == null) return;
        LocalPlayer player = (LocalPlayer) (Object) this;

        ToggleSprint sprint = FeatherClient.getInstance().getModuleManager().getModule(ToggleSprint.class);
        if (sprint != null && sprint.isEnabled() && player.input.hasForwardImpulse() && !player.isCrouching()
                && !player.isUsingItem() && player.getFoodData().getFoodLevel() > 6) {
            player.setSprinting(true);
        }

        ToggleSneak sneak = FeatherClient.getInstance().getModuleManager().getModule(ToggleSneak.class);
        if (sneak != null && sneak.isEnabled() && sneak.isSneaking()) {
            player.setShiftKeyDown(true);
        }
    }
}
