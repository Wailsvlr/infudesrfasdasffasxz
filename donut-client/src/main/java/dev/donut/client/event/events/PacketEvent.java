package dev.donut.client.event.events;

import dev.donut.client.event.Event;
import net.minecraft.network.packet.Packet;

public class PacketEvent {
    public static class Send extends Event {
        private final Packet<?> packet;
        public Send(Packet<?> packet) { this.packet = packet; }
        public Packet<?> getPacket() { return packet; }
    }

    public static class Receive extends Event {
        private final Packet<?> packet;
        public Receive(Packet<?> packet) { this.packet = packet; }
        public Packet<?> getPacket() { return packet; }
    }
}
