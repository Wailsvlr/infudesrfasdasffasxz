package net.feathermc.client.module.hud;

import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.TickEvent;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CPSDisplay extends HudModule {
    private final List<Long> leftClicks = new ArrayList<>();
    private final List<Long> rightClicks = new ArrayList<>();
    private final BooleanSetting showRight = addSetting(new BooleanSetting("Show Right Click", true));
    private boolean leftWasDown, rightWasDown;

    public CPSDisplay() {
        super("CPS Display", "Shows clicks per second", 5, 18);
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.screen != null) return;
        long now = System.currentTimeMillis();
        boolean leftDown = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (leftDown && !leftWasDown) leftClicks.add(now);
        if (rightDown && !rightWasDown) rightClicks.add(now);
        leftWasDown = leftDown;
        rightWasDown = rightDown;
        leftClicks.removeIf(t -> now - t > 1000);
        rightClicks.removeIf(t -> now - t > 1000);
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        String text = showRight.getValue()
                ? leftClicks.size() + " | " + rightClicks.size() + " CPS"
                : leftClicks.size() + " CPS";
        drawText(graphics, text, getX(), getY());
    }
}
