package com.wailsvlr.prism.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.MultiBufferSource;
import com.wailsvlr.prism.render.EspPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class Tracers extends Module {

    private static Tracers instance;
    private final NumberSetting range;
    private final BoolSetting players;
    private final BoolSetting mobs;
    private final BoolSetting invisibles;

    public Tracers() {
        super("Tracers", "Draws lines to entities", Category.RENDER);
        instance = this;
        range = add(new NumberSetting("Range", "Maximum trace distance", 64, 16, 256, 16, " blocks"));
        players = add(new BoolSetting("Players", "Trace to players", true));
        mobs = add(new BoolSetting("Mobs", "Trace to mobs", false));
        invisibles = add(new BoolSetting("Invisibles", "Trace invisible entities", false));
    }

    public static void render(WorldRenderContext ctx) {
        if (instance == null || !instance.isEnabled()) return;
        if (mc.player == null || mc.level == null) return;

        Vec3 cam = ctx.worldState().cameraRenderState.pos;
        PoseStack.Pose pose = ctx.matrices().last();
        MultiBufferSource consumers = ctx.consumers();
        Matrix4f matrix = pose.pose();

        VertexConsumer lines = consumers.getBuffer(EspPipelines.lines(true));

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player) continue;
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            if (!instance.invisibles.isOn() && le.isInvisible()) continue;

            boolean isPlayer = le instanceof Player;
            boolean isMob = le instanceof Monster;
            if (isPlayer && !instance.players.isOn()) continue;
            if (!isPlayer && !isMob) continue;
            if (isMob && !instance.mobs.isOn()) continue;

            double dist = mc.player.distanceTo(le);
            if (dist > instance.range.get()) continue;

            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float ex = (float) (lerp(partialTick, le.xo, le.getX()) - cam.x);
            float ey = (float) (lerp(partialTick, le.yo, le.getY()) - cam.y + le.getBbHeight() / 2.0);
            float ez = (float) (lerp(partialTick, le.zo, le.getZ()) - cam.z);

            float r, g, b;
            if (isPlayer) { r = 1; g = 0.2f; b = 0.2f; }
            else { r = 1; g = 0.5f; b = 0; }

            float len = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
            if (len == 0) continue;
            float nx = ex / len, ny = ey / len, nz = ez / len;

            lines.addVertex(matrix, 0, 0, 0).setColor(r, g, b, 0.8f).setNormal(pose, nx, ny, nz);
            lines.addVertex(matrix, ex, ey, ez).setColor(r, g, b, 0.8f).setNormal(pose, nx, ny, nz);
        }

        if (consumers instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(EspPipelines.lines(true));
        }
    }

    private static double lerp(float delta, double start, double end) {
        return start + (end - start) * delta;
    }

    @Override
    public String getInfo() { return null; }
}
