package net.feathermc.client.module.render;

import net.feathermc.client.module.Module;

public class ItemPhysics extends Module {
    private final NumberSetting rotationSpeed = addSetting(new NumberSetting("Rotation Speed", 1.0, 0.1, 5.0, 0.1));
    private final BooleanSetting flatItems = addSetting(new BooleanSetting("Flat on Ground", true));

    public ItemPhysics() {
        super("Item Physics", "Realistic dropped item physics and rotation", Category.RENDER);
    }

    public float getRotationSpeed() {
        return rotationSpeed.getValue().floatValue();
    }

    public boolean isFlatOnGround() {
        return flatItems.getValue();
    }
}
