package dev.donut.client.gui.hud;

import dev.donut.client.DonutClient;
import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.RenderEvent;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.*;

public class HUDRenderer {
    private static final int ACCENT = 0xFF00C8F8;
    private static final int BG_COLOR = 0x80101010;

    private final Map<Module, Float> slideAnimations = new HashMap<>();
    private final Set<Module> lastEnabled = new HashSet<>();

    @EventBus.Subscribe
    public void onHudRender(RenderEvent.Hud event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.options.hudHidden) return;

        DrawContext ctx = event.getContext();
        TextRenderer tr = mc.textRenderer;

        drawLogo(ctx, tr, 4, 4);
        drawArrayList(ctx, tr, 4, 30);

        String fps = mc.getCurrentFps() + " FPS";
        int fpsWidth = tr.getWidth(fps);
        int screenW = mc.getWindow().getScaledWidth();
        ctx.drawTextWithShadow(tr, fps, screenW / 2 - fpsWidth / 2, 4, 0xFFE0E0E0);
    }

    private void drawLogo(DrawContext ctx, TextRenderer tr, int x, int y) {
        String name = "DONUT";
        String ver = "v1";
        ctx.drawTextWithShadow(tr, name, x, y, ACCENT);
        int nameW = tr.getWidth(name);
        int vx = x + nameW + 2;
        int vw = tr.getWidth(ver) + 6;
        ctx.fill(vx, y, vx + vw, y + 10, ACCENT);
        ctx.drawText(tr, ver, vx + 3, y + 1, 0xFF000000, false);
    }

    private void drawArrayList(DrawContext ctx, TextRenderer tr, int x, int startY) {
        List<Module> enabled = DonutClient.INSTANCE.getModules().getModules().stream()
                .filter(Module::isEnabled)
                .toList();

        Set<Module> currentEnabled = new HashSet<>(enabled);

        for (Module m : currentEnabled) {
            slideAnimations.putIfAbsent(m, 0f);
        }
        for (Module m : currentEnabled) {
            float anim = slideAnimations.getOrDefault(m, 0f);
            slideAnimations.put(m, anim + (1f - anim) * 0.2f);
        }
        for (Module m : lastEnabled) {
            if (!currentEnabled.contains(m)) {
                float anim = slideAnimations.getOrDefault(m, 1f);
                float next = anim - anim * 0.3f;
                if (next < 0.05f) {
                    slideAnimations.remove(m);
                } else {
                    slideAnimations.put(m, next);
                }
            }
        }
        lastEnabled.clear();
        lastEnabled.addAll(currentEnabled);

        List<Module> toDraw = new ArrayList<>(enabled);
        for (var entry : slideAnimations.entrySet()) {
            if (!currentEnabled.contains(entry.getKey()) && entry.getValue() > 0.05f) {
                toDraw.add(entry.getKey());
            }
        }
        toDraw.sort((a, b) -> Integer.compare(
                tr.getWidth(getDisplayText(b)),
                tr.getWidth(getDisplayText(a))
        ));

        int y = startY;
        for (Module module : toDraw) {
            float anim = slideAnimations.getOrDefault(module, 0f);
            if (anim < 0.02f) continue;

            String text = getDisplayText(module);
            int textWidth = tr.getWidth(text);
            int alpha = (int)(anim * 255f);
            int offsetX = x + (int)((1f - anim) * -textWidth);
            int bgAlpha = (int)(anim * 128f);

            ctx.fill(offsetX - 2, y - 1, offsetX + textWidth + 4, y + 10,
                    (bgAlpha << 24) | 0x101010);
            ctx.fill(offsetX + textWidth + 2, y - 1, offsetX + textWidth + 4, y + 10,
                    (alpha << 24) | 0x00C8F8);

            int charX = offsetX;
            for (int i = 0; i < text.length(); i++) {
                float hueShift = (float)i / text.length() * 0.08f;
                int color = shiftHue(ACCENT, hueShift);
                color = (color & 0x00FFFFFF) | (alpha << 24);
                String ch = String.valueOf(text.charAt(i));
                ctx.drawTextWithShadow(tr, ch, charX, y, color);
                charX += tr.getWidth(ch);
            }
            y += 12;
        }
    }

    private String getDisplayText(Module module) {
        StringBuilder sb = new StringBuilder(module.getName());
        module.getSettings().stream()
                .filter(s -> s instanceof NumberSetting)
                .findFirst()
                .ifPresent(s -> {
                    double val = ((NumberSetting) s).getValue();
                    sb.append(" ").append(String.format("%.1f", val));
                });
        return sb.toString();
    }

    private int shiftHue(int color, float shift) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float[] hsb = Color.RGBtoHSB((int)(r * 255), (int)(g * 255), (int)(b * 255), null);
        hsb[0] = (hsb[0] + shift) % 1f;
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        return rgb | 0xFF000000;
    }
}
