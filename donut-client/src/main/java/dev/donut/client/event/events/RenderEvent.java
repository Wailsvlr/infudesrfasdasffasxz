package dev.donut.client.event.events;

import dev.donut.client.event.Event;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

public class RenderEvent {
    public static class World extends Event {
        private final MatrixStack matrices;
        private final float tickDelta;

        public World(MatrixStack matrices, float tickDelta) {
            this.matrices = matrices;
            this.tickDelta = tickDelta;
        }

        public MatrixStack getMatrices() { return matrices; }
        public float getTickDelta() { return tickDelta; }
    }

    public static class Hud extends Event {
        private final DrawContext context;
        private final float tickDelta;

        public Hud(DrawContext context, float tickDelta) {
            this.context = context;
            this.tickDelta = tickDelta;
        }

        public DrawContext getContext() { return context; }
        public float getTickDelta() { return tickDelta; }
    }
}
