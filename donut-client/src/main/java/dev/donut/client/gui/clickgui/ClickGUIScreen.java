package dev.donut.client.gui.clickgui;

import dev.donut.client.DonutClient;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.BooleanSetting;
import dev.donut.client.setting.ModeSetting;
import dev.donut.client.setting.NumberSetting;
import dev.donut.client.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClickGUIScreen extends Screen {
    private static final int HEADER_HEIGHT = 22;
    private static final int MODULE_HEIGHT = 16;
    private static final int SETTING_HEIGHT = 14;
    private static final int PANEL_WIDTH = 110;
    private static final int ACCENT = 0xFF00C8F8;
    private static final int BG = 0xE0141414;
    private static final int BG_HOVER = 0xE0202020;
    private static final int BG_SETTING = 0xE0181818;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int TEXT_DIM = 0xFF909090;

    private Category selectedCategory = Category.COMBAT;
    private Module expandedModule = null;
    private Module bindingModule = null;
    private int scrollOffset = 0;
    private final Map<Module, Float> toggleAnimations = new HashMap<>();
    private boolean draggingSlider = false;
    private NumberSetting draggingSetting = null;

    public ClickGUIScreen() {
        super(Text.literal("Donut Client"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);

        int centerX = width / 2;
        int centerY = height / 2;
        int totalW = PANEL_WIDTH + 10 + 160;
        int totalH = 300;
        int startX = centerX - totalW / 2;
        int startY = centerY - totalH / 2;

        drawCategoryTabs(ctx, startX, startY, mouseX, mouseY);
        drawModuleList(ctx, startX, startY + HEADER_HEIGHT + 2, mouseX, mouseY);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawCategoryTabs(DrawContext ctx, int x, int y, int mx, int my) {
        int tabW = PANEL_WIDTH + 10 + 160;
        ctx.fill(x, y, x + tabW, y + HEADER_HEIGHT, BG);

        int tabX = x + 4;
        for (Category cat : Category.values()) {
            String name = cat.name().substring(0, 1) + cat.name().substring(1).toLowerCase();
            int tw = textRenderer.getWidth(name) + 8;
            boolean hovered = mx >= tabX && mx <= tabX + tw && my >= y && my <= y + HEADER_HEIGHT;
            boolean selected = cat == selectedCategory;

            if (selected) {
                ctx.fill(tabX, y + HEADER_HEIGHT - 2, tabX + tw, y + HEADER_HEIGHT, ACCENT);
            }
            ctx.drawTextWithShadow(textRenderer, name, tabX + 4, y + 7,
                    selected ? ACCENT : (hovered ? TEXT_COLOR : TEXT_DIM));
            tabX += tw + 2;
        }
    }

    private void drawModuleList(DrawContext ctx, int x, int y, int mx, int my) {
        List<Module> modules = DonutClient.INSTANCE.getModules().getByCategory(selectedCategory);
        int panelH = 300 - HEADER_HEIGHT - 2;

        ctx.fill(x, y, x + PANEL_WIDTH, y + panelH, BG);

        int listX = x + 10 + PANEL_WIDTH;
        ctx.fill(listX, y, listX + 160, y + panelH, BG);

        int drawY = y + 2 - scrollOffset;
        for (Module module : modules) {
            if (drawY + MODULE_HEIGHT < y || drawY > y + panelH) {
                drawY += MODULE_HEIGHT;
                if (module == expandedModule) {
                    drawY += module.getSettings().size() * SETTING_HEIGHT + 18;
                }
                continue;
            }

            boolean hovered = mx >= x && mx <= x + PANEL_WIDTH && my >= drawY && my <= drawY + MODULE_HEIGHT && my >= y && my <= y + panelH;
            if (hovered) {
                ctx.fill(x, drawY, x + PANEL_WIDTH, drawY + MODULE_HEIGHT, BG_HOVER);
            }

            float anim = toggleAnimations.getOrDefault(module, module.isEnabled() ? 1f : 0f);
            float target = module.isEnabled() ? 1f : 0f;
            anim += (target - anim) * 0.15f;
            toggleAnimations.put(module, anim);

            int dotColor = lerpColor(0xFF404040, ACCENT, anim);
            ctx.fill(x + 3, drawY + 5, x + 7, drawY + MODULE_HEIGHT - 5, dotColor);
            ctx.drawTextWithShadow(textRenderer, module.getName(), x + 10, drawY + 4,
                    module.isEnabled() ? ACCENT : TEXT_COLOR);

            if (module.getKeyBind() != 0) {
                String keyName = getKeyName(module.getKeyBind());
                int kw = textRenderer.getWidth(keyName);
                ctx.drawTextWithShadow(textRenderer, keyName, x + PANEL_WIDTH - kw - 4, drawY + 4, TEXT_DIM);
            }

            drawY += MODULE_HEIGHT;

            if (module == expandedModule) {
                drawY = drawSettings(ctx, module, listX, y, drawY, mx, my, panelH + y);
            }
        }
    }

    private int drawSettings(DrawContext ctx, Module module, int x, int panelTop, int y, int mx, int my, int maxY) {
        for (Setting<?> setting : module.getSettings()) {
            if (y + SETTING_HEIGHT < panelTop || y > maxY) {
                y += SETTING_HEIGHT;
                continue;
            }

            ctx.fill(x, y, x + 160, y + SETTING_HEIGHT, BG_SETTING);

            if (setting instanceof BooleanSetting bs) {
                boolean hov = mx >= x && mx <= x + 160 && my >= y && my <= y + SETTING_HEIGHT;
                ctx.drawTextWithShadow(textRenderer, bs.getName(), x + 4, y + 3,
                        bs.getValue() ? ACCENT : TEXT_DIM);
                int checkX = x + 160 - 14;
                ctx.fill(checkX, y + 2, checkX + 10, y + 12, bs.getValue() ? ACCENT : 0xFF404040);
            } else if (setting instanceof NumberSetting ns) {
                ctx.drawTextWithShadow(textRenderer, ns.getName(), x + 4, y + 3, TEXT_COLOR);
                String val = String.format("%.1f", ns.getValue());
                int vw = textRenderer.getWidth(val);
                ctx.drawTextWithShadow(textRenderer, val, x + 160 - vw - 4, y + 3, ACCENT);

                int barX = x + 4;
                int barW = 152;
                int barY2 = y + SETTING_HEIGHT - 2;
                double pct = (ns.getValue() - ns.getMin()) / (ns.getMax() - ns.getMin());
                ctx.fill(barX, barY2 - 1, barX + barW, barY2, 0xFF303030);
                ctx.fill(barX, barY2 - 1, barX + (int)(barW * pct), barY2, ACCENT);
            } else if (setting instanceof ModeSetting ms) {
                ctx.drawTextWithShadow(textRenderer, ms.getName(), x + 4, y + 3, TEXT_COLOR);
                String mode = ms.getValue();
                int mw = textRenderer.getWidth(mode);
                ctx.drawTextWithShadow(textRenderer, mode, x + 160 - mw - 4, y + 3, ACCENT);
            }

            y += SETTING_HEIGHT;
        }

        int bindY = y;
        ctx.fill(x, bindY, x + 160, bindY + 16, BG_SETTING);
        String bindText = bindingModule == module ? "Press a key..." :
                "Bind: " + (module.getKeyBind() == 0 ? "NONE" : getKeyName(module.getKeyBind()));
        ctx.drawTextWithShadow(textRenderer, bindText, x + 4, bindY + 4,
                bindingModule == module ? ACCENT : TEXT_DIM);
        return bindY + 18;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = width / 2;
        int centerY = height / 2;
        int totalW = PANEL_WIDTH + 10 + 160;
        int startX = centerX - totalW / 2;
        int startY = centerY - 150;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (my >= startY && my <= startY + HEADER_HEIGHT) {
            int tabX = startX + 4;
            for (Category cat : Category.values()) {
                String name = cat.name().substring(0, 1) + cat.name().substring(1).toLowerCase();
                int tw = textRenderer.getWidth(name) + 8;
                if (mx >= tabX && mx <= tabX + tw) {
                    selectedCategory = cat;
                    scrollOffset = 0;
                    expandedModule = null;
                    bindingModule = null;
                    return true;
                }
                tabX += tw + 2;
            }
        }

        int panelY = startY + HEADER_HEIGHT + 2;
        int panelH = 300 - HEADER_HEIGHT - 2;

        if (mx >= startX && mx <= startX + PANEL_WIDTH && my >= panelY && my <= panelY + panelH) {
            int drawY = panelY + 2 - scrollOffset;
            for (Module module : DonutClient.INSTANCE.getModules().getByCategory(selectedCategory)) {
                if (my >= drawY && my <= drawY + MODULE_HEIGHT && my >= panelY && my <= panelY + panelH) {
                    if (button == 0) {
                        module.toggle();
                    } else if (button == 1) {
                        expandedModule = expandedModule == module ? null : module;
                        bindingModule = null;
                    }
                    return true;
                }
                drawY += MODULE_HEIGHT;
                if (module == expandedModule) {
                    drawY += module.getSettings().size() * SETTING_HEIGHT + 18;
                }
            }
        }

        int listX = startX + 10 + PANEL_WIDTH;
        if (expandedModule != null && mx >= listX && mx <= listX + 160) {
            int drawY = panelY + 2 - scrollOffset;
            for (Module module : DonutClient.INSTANCE.getModules().getByCategory(selectedCategory)) {
                drawY += MODULE_HEIGHT;
                if (module == expandedModule) {
                    for (Setting<?> setting : module.getSettings()) {
                        if (my >= drawY && my <= drawY + SETTING_HEIGHT) {
                            if (setting instanceof BooleanSetting bs) {
                                bs.setValue(!bs.getValue());
                                return true;
                            } else if (setting instanceof NumberSetting ns) {
                                draggingSlider = true;
                                draggingSetting = ns;
                                updateSlider(mx, listX);
                                return true;
                            } else if (setting instanceof ModeSetting ms) {
                                ms.cycle();
                                return true;
                            }
                        }
                        drawY += SETTING_HEIGHT;
                    }
                    if (my >= drawY && my <= drawY + 16) {
                        bindingModule = bindingModule == module ? null : module;
                        return true;
                    }
                    break;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingSlider && draggingSetting != null) {
            int centerX = width / 2;
            int totalW = PANEL_WIDTH + 10 + 160;
            int startX = centerX - totalW / 2;
            int listX = startX + 10 + PANEL_WIDTH;
            updateSlider((int) mouseX, listX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = false;
        draggingSetting = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int)(verticalAmount * 10);
        if (scrollOffset < 0) scrollOffset = 0;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingModule != null) {
            if (keyCode == 256) {
                bindingModule.setKeyBind(0);
            } else {
                bindingModule.setKeyBind(keyCode);
            }
            bindingModule = null;
            return true;
        }
        if (keyCode == 256) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateSlider(int mouseX, int listX) {
        if (draggingSetting == null) return;
        int barX = listX + 4;
        int barW = 152;
        double pct = Math.max(0, Math.min(1, (mouseX - barX) / (double) barW));
        double val = draggingSetting.getMin() + pct * (draggingSetting.getMax() - draggingSetting.getMin());
        double step = draggingSetting.getStep();
        val = Math.round(val / step) * step;
        draggingSetting.setValue(val);
    }

    private int lerpColor(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int)(((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
        int g = (int)(((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
        int b = (int)((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
        int a = (int)(((c1 >> 24) & 0xFF) * (1 - t) + ((c2 >> 24) & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String getKeyName(int keyCode) {
        return switch (keyCode) {
            case 65 -> "A"; case 66 -> "B"; case 67 -> "C"; case 68 -> "D";
            case 69 -> "E"; case 70 -> "F"; case 71 -> "G"; case 72 -> "H";
            case 73 -> "I"; case 74 -> "J"; case 75 -> "K"; case 76 -> "L";
            case 77 -> "M"; case 78 -> "N"; case 79 -> "O"; case 80 -> "P";
            case 81 -> "Q"; case 82 -> "R"; case 83 -> "S"; case 84 -> "T";
            case 85 -> "U"; case 86 -> "V"; case 87 -> "W"; case 88 -> "X";
            case 89 -> "Y"; case 90 -> "Z";
            case 256 -> "ESC"; case 290 -> "F1"; case 291 -> "F2";
            case 292 -> "F3"; case 293 -> "F4"; case 340 -> "LSHIFT";
            case 341 -> "LCTRL"; case 342 -> "LALT";
            default -> "KEY" + keyCode;
        };
    }
}
