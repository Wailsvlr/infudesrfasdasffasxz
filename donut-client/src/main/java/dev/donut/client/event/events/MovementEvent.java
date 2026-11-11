package dev.donut.client.event.events;

import dev.donut.client.event.Event;

public class MovementEvent {
    public static class Input extends Event {
        public float movementForward;
        public float movementSideways;
        public boolean jumping;
        public boolean sneaking;

        public Input(float forward, float sideways, boolean jumping, boolean sneaking) {
            this.movementForward = forward;
            this.movementSideways = sideways;
            this.jumping = jumping;
            this.sneaking = sneaking;
        }
    }

    public static class Sprint extends Event {
    }
}
