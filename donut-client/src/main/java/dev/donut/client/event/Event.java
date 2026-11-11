package dev.donut.client.event;

public abstract class Event {
    private boolean cancelled;
    public boolean isCancelled() { return cancelled; }
    public void cancel() { cancelled = true; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
