package dev.donut.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class RotationUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static float[] getRotations(Entity target) {
        if (mc.player == null) return new float[]{0, 0};
        Vec3d eyes = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() / 2.0, 0);
        double dx = targetPos.x - eyes.x;
        double dy = targetPos.y - eyes.y;
        double dz = targetPos.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitch};
    }

    public static float[] smoothRotations(float[] current, float[] target, float speed) {
        float yawDiff = wrapDegrees(target[0] - current[0]);
        float pitchDiff = target[1] - current[1];
        float yaw = current[0] + yawDiff * Math.min(1f, speed);
        float pitch = current[1] + pitchDiff * Math.min(1f, speed);
        pitch = MathHelper.clamp(pitch, -90f, 90f);
        return new float[]{yaw, pitch};
    }

    public static float wrapDegrees(float degrees) {
        degrees = degrees % 360f;
        if (degrees >= 180f) degrees -= 360f;
        if (degrees < -180f) degrees += 360f;
        return degrees;
    }
}
