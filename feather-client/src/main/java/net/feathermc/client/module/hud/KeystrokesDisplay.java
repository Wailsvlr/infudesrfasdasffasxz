package net.feathermc.client.module.hud;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public class KeystrokesDisplay extends HudModule {
    private static final int KEY_SIZE = 22;
    private static final int GAP = 2;

    private final BooleanSetting showMouse = addSetting(new BooleanSetting("Show Mouse Buttons", true));
    private final BooleanSetting showSpace = addSetting(new BooleanSetting("Show Spacebar", true));
    private final ColorSetting pressedColor = addSetting(new ColorSetting("Pressed Color", 0xFFFFFFFF));
    private final ColorSetting releasedColor = addSetting(new ColorSetting("Released Color", 0xFF404040));
    private final ColorSetting pressedTextColor = addSetting(new ColorSetting("Pressed Text", 0xFF000000));
    private final ColorSetting releasedTextColor = addSetting(new ColorSetting("Released Text", 0xFFCCCCCC));

    public KeystrokesDisplay() {
        super("Keystrokes", "Shows WASD, mouse buttons, and spacebar input", 5, 92);
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        if (mc.player == null) return;
        int bx = getX();
        int by = getY();

        // Row 1: W
        renderKey(graphics, "W", bx + KEY_SIZE + GAP, by, mc.options.keyUp);

        // Row 2: A S D
        int row2y = by + KEY_SIZE + GAP;
        renderKey(graphics, "A", bx, row2y, mc.options.keyLeft);
        renderKey(graphics, "S", bx + KEY_SIZE + GAP, row2y, mc.options.keyDown);
        renderKey(graphics, "D", bx + (KEY_SIZE + GAP) * 2, row2y, mc.options.keyRight);

        int nextY = row2y + KEY_SIZE + GAP;

        // Row 3: Mouse buttons
        if (showMouse.getValue()) {
            int halfW = (KEY_SIZE * 3 + GAP * 2 - GAP) / 2;
            boolean lmb = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean rmb = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            renderBox(graphics, "LMB", bx, nextY, halfW, KEY_SIZE, lmb);
            renderBox(graphics, "RMB", bx + halfW + GAP, nextY, halfW, KEY_SIZE, rmb);
            nextY += KEY_SIZE + GAP;
        }

        // Row 4: Spacebar
        if (showSpace.getValue()) {
            int totalW = KEY_SIZE * 3 + GAP * 2;
            boolean spaceDown = mc.options.keyJump.isDown();
            renderBox(graphics, "---", bx, nextY, totalW, KEY_SIZE / 2, spaceDown);
        }
    }

    private void renderKey(GuiGraphics graphics, String label, int x, int y, KeyMapping key) {
        renderBox(graphics, label, x, y, KEY_SIZE, KEY_SIZE, key.isDown());
    }

    private void renderBox(GuiGraphics graphics, String label, int x, int y, int w, int h, boolean pressed) {
        int bg = pressed ? pressedColor.getValue() : releasedColor.getValue();
        int fg = pressed ? pressedTextColor.getValue() : releasedTextColor.getValue();
        graphics.fill(x, y, x + w, y + h, bg);
        int tx = x + (w - mc.font.width(label)) / 2;
        int ty = y + (h - 8) / 2;
        graphics.drawString(mc.font, label, tx, ty, fg, false);
    }
}
