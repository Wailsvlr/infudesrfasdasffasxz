package dev.donut.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Comparator;
import java.util.List;

public class PlayerUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static PlayerEntity getClosestPlayer(double range) {
        if (mc.player == null || mc.world == null) return null;
        return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player)
                .filter(p -> mc.player.distanceTo(p) <= range)
                .min(Comparator.comparingDouble(mc.player::distanceTo))
                .orElse(null);
    }

    public static List<PlayerEntity> getPlayers(double range) {
        if (mc.player == null || mc.world == null) return List.of();
        return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player)
                .filter(p -> mc.player.distanceTo(p) <= range)
                .sorted(Comparator.comparingDouble(mc.player::distanceTo))
                .toList();
    }
}
