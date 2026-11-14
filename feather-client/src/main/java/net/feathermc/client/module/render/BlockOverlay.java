package net.feathermc.client.module.render;

import net.feathermc.client.module.Module;

public class BlockOverlay extends Module {
    private final ColorSetting outlineColor = addSetting(new ColorSetting("Outline Color", 0xFF000000));
    private final ColorSetting fillColor = addSetting(new ColorSetting("Fill Color", 0x40FFFFFF));
    private final NumberSetting outlineWidth = addSetting(new NumberSetting("Outline Width", 2.0, 0.5, 5.0, 0.5));
    private final BooleanSetting persistentOutline = addSetting(new BooleanSetting("Persistent", false));

    public BlockOverlay() {
        super("Block Overlay", "Custom block selection highlight", Category.RENDER);
    }

    public int getOutlineColor() { return outlineColor.getValue(); }
    public int getFillColor() { return fillColor.getValue(); }
    public float getOutlineWidth() { return outlineWidth.getValue().floatValue(); }
    public boolean isPersistent() { return persistentOutline.getValue(); }
}
