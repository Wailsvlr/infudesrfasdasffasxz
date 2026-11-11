package dev.donut.client.mixin;

import dev.donut.client.DonutClient;
import dev.donut.client.gui.clickgui.ClickGUIScreen;
import dev.donut.client.module.Module;
import dev.donut.client.module.player.FastPlace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screen.Screen;

import java.util.HashMap;
import java.util.Map;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient {
    @Shadow private int itemUseCooldown;
    @Shadow public ClientPlayerEntity player;
    @Shadow public Screen currentScreen;

    @Unique
    private final Map<Integer, Boolean> keyStates = new HashMap<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (DonutClient.INSTANCE == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        FastPlace fp = DonutClient.INSTANCE.getModules().get(FastPlace.class);
        if (fp != null && fp.isEnabled()) {
            itemUseCooldown = 0;
        }

        handleKeybinds();
    }

    @Unique
    private void handleKeybinds() {
        if (DonutClient.INSTANCE == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (currentScreen != null) return;

        long window = mc.getWindow().getHandle();

        boolean guiPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean guiWas = keyStates.getOrDefault(GLFW.GLFW_KEY_RIGHT_SHIFT, false);
        if (guiPressed && !guiWas) {
            mc.setScreen(new ClickGUIScreen());
        }
        keyStates.put(GLFW.GLFW_KEY_RIGHT_SHIFT, guiPressed);

        for (Module module : DonutClient.INSTANCE.getModules().getModules()) {
            if (module.getKeyBind() == -1) continue;

            boolean pressed = GLFW.glfwGetKey(window, module.getKeyBind()) == GLFW.GLFW_PRESS;
            boolean wasPressed = keyStates.getOrDefault(module.getKeyBind(), false);

            if (pressed && !wasPressed) {
                module.toggle();
            }
            keyStates.put(module.getKeyBind(), pressed);
        }
    }
}
