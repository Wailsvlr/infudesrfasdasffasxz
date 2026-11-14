package net.feathermc.client.module.visual;

import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.RenderHudEvent;
import net.feathermc.client.module.Module;
import net.minecraft.client.gui.GuiGraphics;

public class Crosshair extends Module {
    private final ModeSetting style = addSetting(new ModeSetting("Style", "Cross", "Default", "Circle", "Cross", "Dot"));
    private final ColorSetting color = addSetting(new ColorSetting("Color", 0xFFFFFFFF));
    private final NumberSetting size = addSetting(new NumberSetting("Size", 6, 1, 20, 1));
    private final NumberSetting gap = addSetting(new NumberSetting("Gap", 2, 0, 10, 1));
    private final NumberSetting thickness = addSetting(new NumberSetting("Thickness", 1, 1, 5, 1));
    private final BooleanSetting dynamic = addSetting(new BooleanSetting("Dynamic", true));

    public Crosshair() {
        super("Crosshair", "Custom crosshair rendering", Category.VISUAL);
    }

    @Subscribe
    public void onRenderHud(RenderHudEvent event) {
        if (mc.options.hideGui) return;

        GuiGraphics g = event.graphics;
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int col = color.getValue();
        int sz = size.getValue().intValue();
        int gp = gap.getValue().intValue();
        int th = thickness.getValue().intValue();

        if (dynamic.getValue() && mc.player != null) {
            double speed = mc.player.getDeltaMovement().horizontalDistance();
            gp += (int) (speed * 10);
        }

        switch (style.getValue()) {
            case "Cross" -> {
                g.fill(cx - th / 2, cy - sz - gp, cx + (th + 1) / 2, cy - gp, col);
                g.fill(cx - th / 2, cy + gp + 1, cx + (th + 1) / 2, cy + sz + gp + 1, col);
                g.fill(cx - sz - gp, cy - th / 2, cx - gp, cy + (th + 1) / 2, col);
                g.fill(cx + gp + 1, cy - th / 2, cx + sz + gp + 1, cy + (th + 1) / 2, col);
            }
            case "Dot" -> {
                int r = Math.max(th, 2);
                g.fill(cx - r, cy - r, cx + r, cy + r, col);
            }
            case "Circle" -> {
                int segments = 32;
                for (int i = 0; i < segments; i++) {
                    double a1 = 2 * Math.PI * i / segments;
                    double a2 = 2 * Math.PI * (i + 1) / segments;
                    int x1 = cx + (int) (Math.cos(a1) * sz);
                    int y1 = cy + (int) (Math.sin(a1) * sz);
                    int x2 = cx + (int) (Math.cos(a2) * sz);
                    int y2 = cy + (int) (Math.sin(a2) * sz);
                    int minX = Math.min(x1, x2);
                    int minY = Math.min(y1, y2);
                    int maxX = Math.max(x1, x2) + 1;
                    int maxY = Math.max(y1, y2) + 1;
                    g.fill(minX, minY, maxX, maxY, col);
                }
            }
            default -> {}
        }
    }
}
