package com.wailsvlr.prism.modules;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.player.LocalPlayer;

/** Creative-style flight (double-tap jump), with a local-player path for remote servers. */
public class Fly extends SingleplayerModule {
	private LocalPlayer remotePlayer;
	private boolean previousMayfly;
	private boolean previousFlying;

	public Fly() {
		super("Fly", "Fly like in creative: double-tap jump. Multiplayer flight is subject to server movement checks");
	}

	@Override
	protected void onEnable() { onTick(); }

	@Override
	public String getInfo() { return mc.player != null && world() == null ? "MP" : null; }

	@Override
	public void onTick() {
		// re-applied every tick: dying, changing dimension or game mode resets the abilities
		if (onPlayer(p -> {
			if (!p.getAbilities().mayfly) {
				p.getAbilities().mayfly = true;
				p.onUpdateAbilities();
			}
		})) return;
		LocalPlayer p = mc.player;
		if (p == null) { remotePlayer = null; return; }
		if (remotePlayer != p) {
			remotePlayer = p;
			previousMayfly = p.getAbilities().mayfly;
			previousFlying = p.getAbilities().flying;
		}
		p.getAbilities().mayfly = true;
	}

	@Override
	protected void onDisable() {
		onPlayer(Fly::land);
		LocalPlayer p = mc.player;
		if (p != null && p == remotePlayer && !p.isCreative() && !p.isSpectator()) {
			p.getAbilities().mayfly = previousMayfly;
			p.getAbilities().flying = previousMayfly && previousFlying;
			p.onUpdateAbilities();
		}
		remotePlayer = null;
	}

	private static void land(ServerPlayer p) {
		if (p.isCreative() || p.isSpectator()) return;
		p.getAbilities().mayfly = false;
		p.getAbilities().flying = false;
		p.onUpdateAbilities();
	}
}
