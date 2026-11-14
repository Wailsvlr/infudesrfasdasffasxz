package net.feathermc.client.event.events;

public class MouseScrollEvent {
    public final double delta;
    private boolean cancelled;

    public MouseScrollEvent(double delta) {
        this.delta = delta;
    }

    public boolean isCancelled() { return cancelled; }
    public void cancel() { cancelled = true; }
}
