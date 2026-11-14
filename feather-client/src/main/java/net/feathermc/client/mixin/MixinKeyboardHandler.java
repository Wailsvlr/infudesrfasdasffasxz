package net.feathermc.client.mixin;

import net.feathermc.client.FeatherClient;
import net.feathermc.client.event.events.KeyEvent;
import net.feathermc.client.gui.FeatherSettingsScreen;
import net.feathermc.client.module.Module;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (window != Minecraft.getInstance().getWindow().getWindow()) return;
        if (Minecraft.getInstance().screen != null) return;

        if (FeatherClient.getInstance() != null) {
            FeatherClient.getInstance().getEventBus().post(new KeyEvent(key, action));

            if (action == GLFW.GLFW_PRESS) {
                if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                    Minecraft.getInstance().setScreen(new FeatherSettingsScreen());
                    return;
                }

                for (Module module : FeatherClient.getInstance().getModuleManager().getModules()) {
                    if (module.getKeyBind() == key) {
                        module.toggle();
                    }
                }
            }
        }
    }
}
