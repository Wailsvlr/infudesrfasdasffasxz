package com.wailsvlr.prism.modules;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** Resets integrated-server fall distance, or reports ground contact to a remote server. */
public class NoFall extends SingleplayerModule {
	public NoFall() {
		super("NoFall", "Prevent fall damage. Multiplayer ground reports are subject to server validation");
	}

	@Override
	protected void onEnable() { onTick(); }

	@Override
	public String getInfo() { return mc.player != null && world() == null ? "MP" : null; }

	@Override
	public void onTick() {
		if (onPlayer(p -> p.resetFallDistance())) return;
		var p = mc.player;
		if (p == null || p.connection == null || p.isPassenger() || p.isFallFlying()
				|| p.isInWater() || p.isInLava() || p.getAbilities().flying) return;
		if (p.fallDistance > 2.0 && p.getDeltaMovement().y < 0) {
			p.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, p.horizontalCollision));
			p.resetFallDistance();
		}
	}
}
