package com.wailsvlr.prism.test;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.modules.BaseValue;
import com.wailsvlr.prism.modules.BlockFinder;
import com.wailsvlr.prism.modules.ChunkFinder;
import com.wailsvlr.prism.modules.HudModule;
import com.wailsvlr.prism.modules.ItemFinder;
import com.wailsvlr.prism.modules.OreFinder;
import com.wailsvlr.prism.modules.PlayerFinder;
import com.wailsvlr.prism.modules.SpawnerFinder;
import com.wailsvlr.prism.modules.StorageFinder;
import com.wailsvlr.prism.modules.TrailFinder;
import com.wailsvlr.prism.util.Perf;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Frame rate with each module on in a dense "city" (a server lobby full of built blocks, lights, chests and signs),
 * plus the profiler's per-module times. Run: -PprismOnly=perf. Writes perf-report.txt.
 */
public class PerfGameTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();

	private int fps(ClientGameTestContext ctx) {
		ctx.waitTicks(60);
		int a = ctx.computeOnClient(Minecraft::getFps);
		ctx.waitTicks(30);
		int b = ctx.computeOnClient(Minecraft::getFps);
		ctx.waitTicks(30);
		return (a + b + ctx.computeOnClient(Minecraft::getFps)) / 3;
	}

	@SafeVarargs
	private void scenario(ClientGameTestContext ctx, String name, Class<? extends Module>... on) {
		ctx.runOnClient(mc -> {
			for (Module m : PrismClient.modules().all()) if (m.isToggleable() && !(m instanceof HudModule)) m.setEnabled(false);
			for (Class<? extends Module> c : on) PrismClient.modules().get(c).setEnabled(true);
		});
		ctx.waitTicks(100);   // let the scanners do their first pass
		ctx.runOnClient(mc -> Perf.reset());
		int f = fps(ctx);
		log.add(String.format("%-34s %4d fps", name, f));
		for (String l : ctx.computeOnClient(mc -> Perf.report()).stream().limit(9).toList()) log.add("      " + l);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!System.getProperty("prism.only", "").equals("perf")) return;
		ctx.getInput().resizeWindow(1600, 900);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var server = sp.getServer();
			ctx.runOnClient(mc -> {
				mc.options.renderDistance().set(12);
				mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
				mc.options.framerateLimit().set(260);
				mc.options.enableVsync().set(false);
				mc.getWindow().updateVsync(false);
			});
			server.runCommand("time set noon");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamemode creative @a");
			// a city: 14 x 14 towers, each with walls, windows, lights, a chest, a sign and some redstone
			long t0 = System.currentTimeMillis();
			for (int gx = -7; gx < 7; gx++) {
				for (int gz = -7; gz < 7; gz++) {
					int x = gx * 12, z = gz * 12, y = -60;
					if (Math.abs(gx) < 1 && Math.abs(gz) < 1) continue;   // a free square where the player stands
					server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks hollow", x, y, z, x + 8, y + 24, z + 8));
					server.runCommand(String.format("fill %d %d %d %d %d %d glass", x, y + 6, z, x + 8, y + 7, z + 8));
					server.runCommand(String.format("fill %d %d %d %d %d %d light_blue_stained_glass", x, y + 16, z, x + 8, y + 17, z + 8));
					server.runCommand(String.format("fill %d %d %d %d %d %d sea_lantern", x + 1, y + 24, z + 1, x + 7, y + 24, z + 7));
					server.runCommand(String.format("setblock %d %d %d chest", x + 2, y + 1, z + 2));
					server.runCommand(String.format("setblock %d %d %d torch", x + 4, y + 1, z + 4));
					server.runCommand(String.format("setblock %d %d %d oak_sign", x + 6, y + 1, z + 6));
					server.runCommand(String.format("setblock %d %d %d red_bed", x + 3, y + 1, z + 6));
					server.runCommand(String.format("fill %d %d %d %d %d %d redstone_wire", x + 2, y + 1, z + 4, x + 3, y + 1, z + 4));
				}
			}
			server.runCommand("tp @p 4.5 -59 4.5 0 10");
			log.add("built the city in " + (System.currentTimeMillis() - t0) + " ms");
			ctx.waitTicks(300);   // chunks load and render

			scenario(ctx, "nothing (HUD only)");
			scenario(ctx, "StorageFinder", StorageFinder.class);
			scenario(ctx, "SpawnerFinder", SpawnerFinder.class);
			scenario(ctx, "OreFinder", OreFinder.class);
			scenario(ctx, "BlockFinder", BlockFinder.class);
			scenario(ctx, "BaseValue (drives BlockFinder)", BaseValue.class);
			scenario(ctx, "ChunkFinder", ChunkFinder.class);
			scenario(ctx, "TrailFinder", TrailFinder.class);
			scenario(ctx, "ItemFinder", ItemFinder.class);
			scenario(ctx, "PlayerFinder", PlayerFinder.class);
			scenario(ctx, "all finders", StorageFinder.class, SpawnerFinder.class, OreFinder.class, BlockFinder.class, BaseValue.class,
				ChunkFinder.class, TrailFinder.class, ItemFinder.class, PlayerFinder.class);
			// walking through the city (new chunks come into range all the time)
			ctx.runOnClient(mc -> Perf.reset());
			int[] walk = new int[8];
			for (int i = 0; i < 8; i++) {
				server.runCommand("tp @p " + (4.5 + i * 24) + " -59 " + (4.5 + i * 16) + " 0 10");
				ctx.waitTicks(20);
				walk[i] = ctx.computeOnClient(Minecraft::getFps);
			}
			log.add("all finders while moving: fps " + java.util.Arrays.toString(walk));
			for (String l : ctx.computeOnClient(mc -> Perf.report()).stream().limit(8).toList()) log.add("      " + l);
		} finally {
			try { Files.write(Path.of("perf-report.txt"), log); } catch (Exception ignored) { }
		}
	}
}
