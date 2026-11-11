package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.gui.Notifications;
import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * A module that only works in your own singleplayer world. It sends no packets of its own: it changes your player
 * on the world running inside this game (the integrated server), like a cheat command would. On a multiplayer
 * server, or a world opened to LAN, it stays dormant and does nothing.
 */
public abstract class SingleplayerModule extends Module {
	protected SingleplayerModule(String name, String description) {
		super(name, description, Category.MISC);
	}

	/** The world running in this game, or null on a server or an open-to-LAN world. */
	protected static MinecraftServer world() {
		MinecraftServer s = mc.getSingleplayerServer();
		return s == null || s.isPublished() || mc.player == null ? null : s;
	}

	/** Runs {@code action} on your player in the singleplayer world (on its own thread). False when there is none. */
	protected static boolean onPlayer(Consumer<ServerPlayer> action) {
		MinecraftServer s = world();
		if (s == null) return false;
		java.util.UUID id = mc.player.getUUID();
		s.execute(() -> {
			ServerPlayer p = s.getPlayerList().getPlayer(id);
			if (p != null) action.accept(p);
		});
		return true;
	}

	@Override
	protected void onEnable() {
		if (world() == null && mc.player != null)
			Notifications.post(this, "warning", getName(), "Only works in singleplayer: it waits until you open a world");
	}

	@Override
	public String getInfo() { return world() == null ? "SP only" : null; }
}
