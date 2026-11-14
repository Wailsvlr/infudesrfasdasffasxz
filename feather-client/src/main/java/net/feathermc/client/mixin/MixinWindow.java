package net.feathermc.client.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Window.class)
public class MixinWindow {
    @ModifyArg(method = "setTitle", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowTitle(JLjava/lang/CharSequence;)V"), index = 1)
    private CharSequence modifyTitle(CharSequence title) {
        return "Feather Client 1.0.0 | " + title;
    }
}
