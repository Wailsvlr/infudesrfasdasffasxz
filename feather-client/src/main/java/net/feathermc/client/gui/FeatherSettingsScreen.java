package net.feathermc.client.gui;

import net.feathermc.client.FeatherClient;
import net.feathermc.client.module.Module;
import net.feathermc.client.module.Module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

public class FeatherSettingsScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 140;
    private static final int HEADER_HEIGHT = 40;
    private static final int ITEM_HEIGHT = 28;

    private Category selectedCategory = Category.HUD;
    private Module selectedModule = null;
    private int scrollOffset = 0;

    public FeatherSettingsScreen() {
        super(Component.literal("Feather Client Settings"));
    }

    @Override
    protected void init() {
        int y = HEADER_HEIGHT + 5;
        for (Category cat : Category.values()) {
            Category captured = cat;
            addRenderableWidget(Button.builder(Component.literal(cat.getDisplayName()), btn -> {
                selectedCategory = captured;
                selectedModule = null;
                scrollOffset = 0;
            }).bounds(5, y, SIDEBAR_WIDTH - 10, 20).build());
            y += 24;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);

        // Sidebar background
        graphics.fill(0, 0, SIDEBAR_WIDTH, height, 0xE0101018);

        // Header
        graphics.fill(0, 0, width, HEADER_HEIGHT, 0xE0181820);
        graphics.drawCenteredString(font, "Feather Client", width / 2, 14, 0xFFDDDDDD);

        // Module list area
        int listX = SIDEBAR_WIDTH + 10;
        int listY = HEADER_HEIGHT + 10;
        int listWidth = selectedModule == null ? width - SIDEBAR_WIDTH - 20 : (width - SIDEBAR_WIDTH) / 2 - 15;

        List<Module> modules = FeatherClient.getInstance().getModuleManager().getByCategory(selectedCategory);
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int my = listY + i * ITEM_HEIGHT - scrollOffset;
            if (my < HEADER_HEIGHT || my > height - 10) continue;

            boolean hovered = mouseX >= listX && mouseX <= listX + listWidth && mouseY >= my && mouseY <= my + ITEM_HEIGHT - 2;
            boolean selected = module == selectedModule;

            int bg = selected ? 0xFF2A2A3A : (hovered ? 0xFF222230 : 0xFF1A1A24);
            graphics.fill(listX, my, listX + listWidth, my + ITEM_HEIGHT - 2, bg);

            graphics.drawString(font, module.getName(), listX + 8, my + 5, module.isEnabled() ? 0xFF55FF55 : 0xFFAAAAAA, false);
            graphics.drawString(font, module.getDescription(), listX + 8, my + 16, 0xFF666666, false);

            // Toggle indicator
            int toggleX = listX + listWidth - 30;
            int toggleY = my + 6;
            graphics.fill(toggleX, toggleY, toggleX + 24, toggleY + 12, module.isEnabled() ? 0xFF00AA00 : 0xFF444444);
            int knobX = module.isEnabled() ? toggleX + 13 : toggleX + 1;
            graphics.fill(knobX, toggleY + 1, knobX + 10, toggleY + 11, 0xFFDDDDDD);
        }

        // Settings panel
        if (selectedModule != null) {
            int settX = listX + listWidth + 10;
            int settY = listY;
            int settWidth = width - settX - 10;

            graphics.fill(settX, settY, settX + settWidth, height - 10, 0xFF1A1A24);
            graphics.drawString(font, selectedModule.getName() + " Settings", settX + 8, settY + 8, 0xFFDDDDDD, false);

            int sy = settY + 28;
            for (Setting<?> setting : selectedModule.getSettings()) {
                if (sy > height - 20) break;

                graphics.drawString(font, setting.getName(), settX + 8, sy, 0xFFAAAAAA, false);

                if (setting instanceof BooleanSetting bs) {
                    int bx = settX + settWidth - 38;
                    graphics.fill(bx, sy - 1, bx + 24, sy + 11, bs.getValue() ? 0xFF00AA00 : 0xFF444444);
                    int kx = bs.getValue() ? bx + 13 : bx + 1;
                    graphics.fill(kx, sy, kx + 10, sy + 10, 0xFFDDDDDD);
                } else if (setting instanceof NumberSetting ns) {
                    int barX = settX + settWidth / 2;
                    int barW = settWidth / 2 - 16;
                    double pct = (ns.getValue() - ns.getMin()) / (ns.getMax() - ns.getMin());
                    graphics.fill(barX, sy + 3, barX + barW, sy + 7, 0xFF444444);
                    graphics.fill(barX, sy + 3, barX + (int)(barW * pct), sy + 7, 0xFF00AAFF);
                    String val = String.format("%.1f", ns.getValue());
                    graphics.drawString(font, val, barX + barW + 4, sy, 0xFF888888, false);
                } else if (setting instanceof ModeSetting ms) {
                    String val = ms.getValue();
                    graphics.drawString(font, val, settX + settWidth - font.width(val) - 8, sy, 0xFF00AAFF, false);
                }

                sy += 22;
            }
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = SIDEBAR_WIDTH + 10;
        int listY = HEADER_HEIGHT + 10;
        int listWidth = selectedModule == null ? width - SIDEBAR_WIDTH - 20 : (width - SIDEBAR_WIDTH) / 2 - 15;

        List<Module> modules = FeatherClient.getInstance().getModuleManager().getByCategory(selectedCategory);
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int my = listY + i * ITEM_HEIGHT - scrollOffset;
            if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= my && mouseY <= my + ITEM_HEIGHT - 2) {
                int toggleX = listX + listWidth - 30;
                if (mouseX >= toggleX) {
                    module.toggle();
                } else {
                    selectedModule = (selectedModule == module) ? null : module;
                }
                return true;
            }
        }

        // Settings interactions
        if (selectedModule != null) {
            int settX = listX + listWidth + 10;
            int settWidth = width - settX - 10;
            int sy = listY + 28;
            for (Setting<?> setting : selectedModule.getSettings()) {
                if (mouseY >= sy - 2 && mouseY <= sy + 12) {
                    if (setting instanceof BooleanSetting bs) {
                        bs.setValue(!bs.getValue());
                        return true;
                    } else if (setting instanceof ModeSetting ms) {
                        ms.cycle();
                        return true;
                    } else if (setting instanceof NumberSetting ns) {
                        int barX = settX + settWidth / 2;
                        int barW = settWidth / 2 - 16;
                        if (mouseX >= barX && mouseX <= barX + barW) {
                            double pct = (mouseX - barX) / barW;
                            double val = ns.getMin() + pct * (ns.getMax() - ns.getMin());
                            val = Math.round(val / ns.getStep()) * ns.getStep();
                            ns.setValue(Math.max(ns.getMin(), Math.min(ns.getMax(), val)));
                            return true;
                        }
                    }
                }
                sy += 22;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, scrollOffset - (int)(scrollY * 20));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        FeatherClient.getInstance().getConfigManager().save();
        super.onClose();
    }
}
