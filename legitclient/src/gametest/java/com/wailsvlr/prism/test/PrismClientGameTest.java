package com.wailsvlr.prism.test;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.gui.ClickGuiScreen;
import com.wailsvlr.prism.modules.ClickGuiModule;
import com.wailsvlr.prism.modules.HudModule;
import com.wailsvlr.prism.modules.SpawnerFinder;
import com.wailsvlr.prism.modules.StorageFinder;
import com.wailsvlr.prism.render.EspRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Real client test: creates a flat world, places containers and spawners in front of the player, checks what the
 * finders detect, and takes screenshots of every highlight mode and of the ClickGUI.
 */
public class PrismClientGameTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();
	private int failures;

	private void check(boolean ok, String what) {
		log.add((ok ? "PASS  " : "FAIL  ") + what);
		if (!ok) failures++;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void runTest(ClientGameTestContext ctx) {
		String only = System.getProperty("prism.only", "");
		if (!only.isEmpty() && !only.equals("main")) return;
		ctx.getInput().resizeWindow(1600, 900);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("difficulty peaceful");   // the night test would otherwise let the spawner's skeletons shoot the player
			server.runCommand("gamerule doDaylightCycle false");
			// player looks south (+Z) and a bit down at the test blocks
			server.runCommand("tp @p 0.5 -60 0.5 0 22");
			String[] blocks = {
				// z = 5: single chest, double chest (left half connects east), trapped chest
				"setblock -3 -60 5 chest[facing=north]",
				"setblock 0 -60 5 chest[facing=north,type=left]",
				"setblock 1 -60 5 chest[facing=north,type=right]",
				"setblock 4 -60 5 trapped_chest[facing=north]",
				// z = 8: shulker boxes (red, blue, undyed), dropper, dispenser (not highlighted by default)
				"setblock -3 -60 8 red_shulker_box",
				"setblock -1 -60 8 blue_shulker_box",
				"setblock 1 -60 8 shulker_box",
				"setblock 3 -60 8 dropper",
				"setblock 5 -60 8 dispenser",
				// z = 12: spawners
				"setblock -2 -60 12 spawner{SpawnData:{entity:{id:\"minecraft:zombie\"}}}",
				"setblock 2 -60 12 spawner{SpawnData:{entity:{id:\"minecraft:skeleton\"}}}",
				// a chest hidden behind a stone wall (through-walls check)
				"fill -7 -60 3 -5 -58 3 stone",
				"setblock -6 -60 5 chest[facing=north]",
				// z = 16: ores on the surface (exposed) and one diamond buried in the dirt
				"setblock -4 -60 16 diamond_ore",
				"setblock -2 -60 16 deepslate_diamond_ore",
				"setblock 0 -60 16 gold_ore",
				"setblock 2 -60 16 ancient_debris",
				"setblock 4 -60 16 iron_ore",
				"setblock 6 -60 16 coal_ore",
				"setblock 6 -63 6 diamond_ore",
			};
			for (String b : blocks) server.runCommand(b);
			ctx.waitTicks(40);
			sp.getClientWorld().waitForChunksRender();

			StorageFinder storage = PrismClient.modules().get(StorageFinder.class);
			SpawnerFinder spawners = PrismClient.modules().get(SpawnerFinder.class);
			ctx.runOnClient(mc -> {
				mc.options.hideGui = false;
				storage.setEnabled(true);
				spawners.setEnabled(true);
				storage.rescan();
				spawners.rescan();
			});
			ctx.waitTicks(10);

			// ---------------------------------------------------------------- detection
			Map<StorageFinder.Kind, Integer> counts = ctx.computeOnClient(mc -> storage.counts());
			int targets = ctx.computeOnClient(mc -> storage.targets().size());
			log.add("storage counts: " + counts + ", boxes: " + targets);
			check(counts.getOrDefault(StorageFinder.Kind.CHEST, 0) == 3, "3 chests found (single, double, behind the wall)");
			check(counts.getOrDefault(StorageFinder.Kind.TRAPPED_CHEST, 0) == 1, "1 trapped chest found");
			check(counts.getOrDefault(StorageFinder.Kind.SHULKER, 0) == 3, "3 shulker boxes found");
			check(counts.getOrDefault(StorageFinder.Kind.DROPPER, 0) == 1, "1 dropper found");
			check(!counts.containsKey(StorageFinder.Kind.DISPENSER), "dispenser ignored while 'Dispensers' is off");
			check(targets == 8, "double chest drawn as one box: 8 boxes for 9 storage blocks");
			boolean doubleMerged = ctx.computeOnClient(mc -> storage.targets().stream().anyMatch(t -> t.box().getXsize() > 1.5));
			check(doubleMerged, "the double chest box spans both halves");
			List<Integer> shulkerColors = ctx.computeOnClient(mc -> storage.targets().stream()
				.filter(t -> t.kind() == StorageFinder.Kind.SHULKER).map(StorageFinder.Target::rgb).distinct().toList());
			check(shulkerColors.size() == 3, "shulker boxes use their own dye colours: " + shulkerColors);

			List<String> mobs = ctx.computeOnClient(mc -> spawners.targets().stream().map(SpawnerFinder.Target::mob).sorted().toList());
			log.add("spawners: " + mobs);
			check(mobs.equals(List.of("Skeleton", "Zombie")), "both spawners found with their mob types");

			ctx.runOnClient(mc -> storage.targetSetting(StorageFinder.Kind.DISPENSER).set(true));
			ctx.runOnClient(mc -> storage.rescan());
			check(ctx.computeOnClient(mc -> storage.counts().getOrDefault(StorageFinder.Kind.DISPENSER, 0)) == 1, "turning 'Dispensers' on finds the dispenser");
			ctx.runOnClient(mc -> { storage.targetSetting(StorageFinder.Kind.DISPENSER).set(false); storage.rescan(); });

			ctx.waitTicks(5);
			check(EspRenderer.lastDrawn >= 10, "highlights are being drawn (" + EspRenderer.lastDrawn + " this frame)");

			// ---------------------------------------------------------------- screenshots of the modes
			shot(ctx, "01_both_default");
			setMode(ctx, storage, spawners, "Outline");
			shot(ctx, "02_outline");
			setMode(ctx, storage, spawners, "Corners + Fill");
			shot(ctx, "03_corners_fill");
			setMode(ctx, storage, spawners, "Fill");
			shot(ctx, "04_fill");
			setMode(ctx, storage, spawners, "Both");
			ctx.runOnClient(mc -> {
				setNumber(storage, "Fill Opacity", 45);
				setNumber(storage, "Line Width", 4);
				setBool(storage, "Tracers", true);
				setBool(storage, "Labels", true);
				storage.colorSetting(StorageFinder.Kind.CHEST).setRainbow(true);
			});
			ctx.waitTicks(3);
			shot(ctx, "05_tracers_labels_rainbow_thick");
			ctx.runOnClient(mc -> {
				setBool(storage, "Through Walls", false);
				storage.colorSetting(StorageFinder.Kind.CHEST).setRainbow(false);
				setBool(storage, "Tracers", false);
			});
			ctx.waitTicks(3);
			shot(ctx, "06_depth_tested_chest_behind_wall_hidden");
			ctx.runOnClient(mc -> setBool(storage, "Through Walls", true));
			ctx.runOnClient(mc -> setBool(spawners, "Activation Range", true));
			ctx.waitTicks(3);
			shot(ctx, "07_spawner_activation_range");
			ctx.runOnClient(mc -> setBool(spawners, "Activation Range", false));
			ctx.runOnClient(mc -> {
				for (var st : spawners.getSettings()) if (st.getName().equals("Tracer Origin")) ((com.wailsvlr.prism.setting.Setting<String>) st).set("Feet");
			});
			ctx.waitTicks(3);
			shot(ctx, "08_tracers_from_feet");
			ctx.runOnClient(mc -> {
				for (var st : spawners.getSettings()) if (st.getName().equals("Tracer Origin")) ((com.wailsvlr.prism.setting.Setting<String>) st).set("Crosshair");
			});
			// (one chat alert per spawner is checked in the game log: "[CHAT] [Legit] ... spawner found")

			// ---------------------------------------------------------------- ClickGUI
			ctx.setScreen(ClickGuiScreen::new);
			ctx.waitTicks(20);
			shot(ctx, "10_clickgui");
			// real mouse click on the StorageFinder row (first module of the Render panel) toggles it
			boolean before = ctx.computeOnClient(mc -> storage.isEnabled());
			double s = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
			float[] rp = ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen gui ? gui.catPosForTest(com.wailsvlr.prism.module.Category.RENDER) : new float[2]);
			ctx.getInput().setCursorPos((rp[0] + 40) * s, (rp[1] + 20 + 2 + 10) * s);   // first module of the Render panel
			ctx.waitTicks(2);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(2);
			boolean after = ctx.computeOnClient(mc -> storage.isEnabled());
			check(before != after, "clicking the StorageFinder row in the GUI toggles it (" + before + " -> " + after + ")");
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(2);
			check(ctx.computeOnClient(mc -> storage.isEnabled()) == before, "clicking again toggles it back");
			// right click opens the settings
			ctx.getInput().pressMouse(1);
			ctx.waitTicks(15);
			shot(ctx, "11_storagefinder_settings");
			ctx.runOnClient(mc -> {
				if (mc.screen instanceof ClickGuiScreen gui) gui.expandForTest(spawners, null);
			});
			ctx.getInput().scroll(-6);
			ctx.waitTicks(10);
			shot(ctx, "12_scrolled_and_spawnerfinder");
			ctx.setScreen(ClickGuiScreen::new);
			ctx.runOnClient(mc -> {
				if (mc.screen instanceof ClickGuiScreen gui) {
					gui.expandForTest(PrismClient.modules().get(ClickGuiModule.class), PrismClient.modules().get(ClickGuiModule.class).accent);
				}
			});
			ctx.waitTicks(20);
			shot(ctx, "13_color_picker");
			ctx.getInput().typeChars("spawn");
			ctx.waitTicks(8);
			shot(ctx, "14_search");
			ctx.setScreen(() -> null);
			ctx.waitTicks(5);
			shot(ctx, "15_hud");

			// ---------------------------------------------------------------- ore finder
			com.wailsvlr.prism.modules.OreFinder ores = PrismClient.modules().get(com.wailsvlr.prism.modules.OreFinder.class);
			ctx.runOnClient(mc -> { storage.setEnabled(false); spawners.setEnabled(false); ores.setEnabled(true); });
			ctx.waitTicks(30);
			var oreCounts = ctx.computeOnClient(mc -> ores.counts());
			log.add("ores: " + oreCounts);
			check(oreCounts.getOrDefault(com.wailsvlr.prism.modules.OreFinder.Ore.DIAMOND, 0) == 3, "3 diamond ores (normal, deepslate, buried)");
			check(oreCounts.getOrDefault(com.wailsvlr.prism.modules.OreFinder.Ore.GOLD, 0) == 1, "gold ore found");
			check(oreCounts.getOrDefault(com.wailsvlr.prism.modules.OreFinder.Ore.ANCIENT_DEBRIS, 0) == 1, "ancient debris found");
			check(!oreCounts.containsKey(com.wailsvlr.prism.modules.OreFinder.Ore.IRON) && !oreCounts.containsKey(com.wailsvlr.prism.modules.OreFinder.Ore.COAL), "iron and coal ignored while switched off");
			ctx.runOnClient(mc -> { ores.oreSetting(com.wailsvlr.prism.modules.OreFinder.Ore.IRON).set(true); ores.oreSetting(com.wailsvlr.prism.modules.OreFinder.Ore.COAL).set(true); });
			ctx.waitTicks(30);
			var oreCounts2 = ctx.computeOnClient(mc -> ores.counts());
			check(oreCounts2.getOrDefault(com.wailsvlr.prism.modules.OreFinder.Ore.IRON, 0) == 1 && oreCounts2.getOrDefault(com.wailsvlr.prism.modules.OreFinder.Ore.COAL, 0) == 1, "switching iron and coal on finds them: " + oreCounts2);
			ctx.runOnClient(mc -> setBool(ores, "Labels", true));
			ctx.waitTicks(3);
			shot(ctx, "09_orefinder");
			ctx.runOnClient(mc -> setBool(ores, "Exposed Only", true));
			ctx.waitTicks(30);
			check(ctx.computeOnClient(mc -> ores.counts().getOrDefault(com.wailsvlr.prism.modules.OreFinder.Ore.DIAMOND, 0)) == 2, "'Exposed Only' skips the buried diamond");
			ctx.runOnClient(mc -> setBool(ores, "Exposed Only", false));
			server.runCommand("setblock 0 -60 16 air");   // "mine" the gold
			ctx.waitTicks(15);
			check(!ctx.computeOnClient(mc -> ores.counts()).containsKey(com.wailsvlr.prism.modules.OreFinder.Ore.GOLD), "a mined ore disappears");
			// a placed ore shows up at once, without waiting for the chunk refresh
			ctx.waitTicks(40);
			server.runCommand("setblock 3 -60 3 ancient_debris");
			ctx.waitTicks(5);
			int debris = ctx.computeOnClient(mc -> ores.counts().getOrDefault(com.wailsvlr.prism.modules.OreFinder.Ore.ANCIENT_DEBRIS, 0));
			check(debris == 2, "a placed ancient debris shows up within 5 ticks (" + debris + " debris)");
			// with Exposed Only, digging next to a buried ore reveals it at once
			ctx.runOnClient(mc -> setBool(ores, "Exposed Only", true));
			ctx.waitTicks(40);
			server.runCommand("setblock 6 -62 6 air");
			ctx.waitTicks(5);
			int dia = ctx.computeOnClient(mc -> ores.counts().getOrDefault(com.wailsvlr.prism.modules.OreFinder.Ore.DIAMOND, 0));
			check(dia == 3, "digging next to the buried diamond reveals it within 5 ticks (" + dia + " diamonds)");
			ctx.runOnClient(mc -> setBool(ores, "Exposed Only", false));
			ctx.runOnClient(mc -> { ores.setEnabled(false); setBool(ores, "Labels", false); });

			// ---------------------------------------------------------------- player finder
			// no second client in a gametest, so two players are added on the client only
			com.wailsvlr.prism.modules.PlayerFinder playerFinder = PrismClient.modules().get(com.wailsvlr.prism.modules.PlayerFinder.class);
			ctx.runOnClient(mc -> {
				ores.setEnabled(false);
				storage.setEnabled(false);
				spawners.setEnabled(false);
				String[][] who = {{"Notch", "-3", "9"}, {"jeb_", "4", "14"}};
				for (String[] w : who) {
					var fake = new net.minecraft.client.player.RemotePlayer(mc.level,
						new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(w[0].getBytes(StandardCharsets.UTF_8)), w[0]));
					fake.snapTo(Double.parseDouble(w[1]) + 0.5, -60, Double.parseDouble(w[2]) + 0.5, 180, 0);
					fake.setHealth(w[0].equals("jeb_") ? 7 : 20);
					mc.level.addEntity(fake);
				}
				playerFinder.setEnabled(true);
			});
			ctx.waitTicks(5);
			List<String> playerLabels = ctx.computeOnClient(mc -> EspRenderer.lastFrame().stream().map(com.wailsvlr.prism.render.Highlight::label)
				.filter(java.util.Objects::nonNull).toList());
			log.add("player labels: " + playerLabels);
			check(ctx.computeOnClient(mc -> playerFinder.targets().size()) == 2, "PlayerFinder finds both players");
			check(playerLabels.stream().anyMatch(l -> l.startsWith("Notch 20") && l.endsWith("m"))
				&& playerLabels.stream().anyMatch(l -> l.startsWith("jeb_ 7")), "player labels show name, health and distance");
			check(ctx.computeOnClient(mc -> EspRenderer.lastFrame().stream().noneMatch(h -> h.box().contains(mc.player.position().add(0, 1, 0)))),
				"your own player is not highlighted");
			shot(ctx, "10_playerfinder");
			ctx.runOnClient(mc -> {
				playerFinder.setEnabled(false);
				// the client-only players are not driven by a server, so take them away again
				for (var pl : List.copyOf(mc.level.players())) if (pl != mc.player) mc.level.removeEntity(pl.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
			});

			// ---------------------------------------------------------------- fullbright
			com.wailsvlr.prism.modules.Fullbright fullbright = PrismClient.modules().get(com.wailsvlr.prism.modules.Fullbright.class);
			server.runCommand("time set midnight");
			ctx.waitTicks(10);
			double dark = brightness(ctx.takeScreenshot("11a_night"));
			ctx.runOnClient(mc -> fullbright.setEnabled(true));
			ctx.waitTicks(20);   // fades in over half a second
			double bright = brightness(ctx.takeScreenshot("11b_night_fullbright"));
			log.add(String.format("fullbright: night brightness %.1f -> %.1f", dark, bright));
			check(bright > dark * 2.5, "Fullbright lights up the night");
			ctx.runOnClient(mc -> fullbright.setEnabled(false));
			ctx.waitTicks(20);
			double darkAgain = brightness(ctx.takeScreenshot("11c_night_after"));
			check(darkAgain < bright * 0.8, "turning Fullbright off makes it dark again");
			server.runCommand("time set noon");

			// ---------------------------------------------------------------- block finder
			com.wailsvlr.prism.modules.BlockFinder blockFinder = PrismClient.modules().get(com.wailsvlr.prism.modules.BlockFinder.class);
			for (String b : new String[]{"setblock -6 -60 4 crafting_table", "setblock -7 -60 4 red_bed", "setblock -8 -60 4 piston",
				"setblock -9 -60 4 oak_sign", "setblock -10 -60 4 sponge"}) server.runCommand(b);
			// type the custom block into the GUI text box like a player would
			ctx.setScreen(ClickGuiScreen::new);
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> { if (mc.screen instanceof ClickGuiScreen gui) gui.editForTest(blockFinder.customList()); });
			ctx.getInput().typeChars("sponge, beacon");
			ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
			ctx.waitTicks(2);
			check(ctx.computeOnClient(mc -> blockFinder.customList().get()).equals("sponge, beacon"), "typing into the Block List box stores the text");
			check(ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen), "typing in the box does not close the menu");
			ctx.setScreen(() -> null);
			// a small stash a few chunks away: 3 shulkers, 5 chests, a crafting table and a furnace (24 + 5 + 1 + 1 points)
			for (String b : new String[]{"fill 40 -60 40 42 -60 40 white_shulker_box", "fill 40 -60 42 44 -60 42 chest[type=single]",
				"setblock 44 -60 40 crafting_table", "setblock 44 -60 41 furnace"}) server.runCommand(b);
			// a raided base three chunks west: beds, workstations and pistons but no storage left, a raid sign,
			// loot on the ground, a lava cast and fires
			for (String b : new String[]{"setblock -40 -60 40 red_bed", "setblock -40 -60 42 blue_bed", "setblock -39 -60 40 enchanting_table",
				"setblock -38 -60 40 anvil", "setblock -37 -60 40 crafting_table", "setblock -36 -60 40 smithing_table",
				"fill -40 -60 44 -38 -60 44 piston", "setblock -36 -60 42 oak_sign{front_text:{messages:[\"RAIDED\",\"gg ez\",\"\",\"\"]}}",
				"fill -44 -61 38 -42 -61 38 netherrack", "fill -44 -60 38 -42 -60 38 fire", "setblock -37 -60 46 lava"}) server.runCommand(b);
			for (int i = 0; i < 6; i++) server.runCommand("summon item " + (-38.5 + i * 0.4) + " -60 41.5 {Item:{id:\"minecraft:cobblestone\",count:4},PickupDelay:32767}");
			// a village: bell, beds, job blocks, a few chests and torches (26 points, nothing only players make)
			for (String b : new String[]{"setblock -72 -60 -72 bell", "fill -78 -60 -78 -71 -60 -78 white_bed", "fill -78 -60 -76 -71 -60 -76 crafting_table",
				"fill -78 -60 -74 -76 -60 -74 chest[type=single]", "fill -78 -60 -70 -69 -60 -70 torch"}) server.runCommand(b);
			// a cobblestone generator farm: pistons and observers, flowing lava, a "Raid Farm" sign, no storage
			for (String b : new String[]{"fill -80 -60 80 -77 -60 80 piston", "fill -80 -60 82 -77 -60 82 observer", "setblock -75 -60 84 lava",
				"setblock -79 -60 85 oak_sign{front_text:{messages:[\"Raid\",\"Farm\",\"\",\"\"]}}"}) server.runCommand(b);
			// a lit 5x5 nether portal next to village houses, with fire and lava around it (like a relit ruined portal)
			// (all inside chunk 4,-5: loaded on the test server and away from the other scenes)
			for (String b : new String[]{"fill 66 -60 -72 72 -54 -72 obsidian", "fill 67 -59 -72 71 -55 -72 air", "setblock 69 -59 -72 fire",
				"fill 66 -60 -68 69 -60 -68 white_bed", "fill 66 -60 -66 69 -60 -66 crafting_table",
				"fill 73 -61 -69 75 -61 -69 netherrack", "fill 73 -60 -69 75 -60 -69 fire", "setblock 70 -60 -76 lava"}) server.runCommand(b);
			// a line of torches running east from the stash through six chunks
			for (int x = 50; x <= 130; x += 16) server.runCommand("fill " + x + " -60 40 " + (x + 2) + " -60 40 torch");
			ctx.runOnClient(mc -> blockFinder.setEnabled(true));
			ctx.waitTicks(20);
			// let it scan every loaded chunk first (4 a tick, nearest first), or far scenes are checked before they are seen
			for (int i = 0; i < 60 && ctx.computeOnClient(mc -> blockFinder.scanPending()) > 0; i++) ctx.waitTicks(5);
			ctx.waitTicks(10);
			log.add("block finder scan left: " + ctx.computeOnClient(mc -> blockFinder.scanPending()) + " chunks");
			var blockCounts = ctx.computeOnClient(mc -> blockFinder.counts());
			log.add("block finder counts: " + blockCounts + ", bases: " + ctx.computeOnClient(mc -> blockFinder.bases()));
			check(blockCounts.getOrDefault(com.wailsvlr.prism.modules.BlockFinder.Group.WORKSTATIONS, 0) >= 2
				&& blockCounts.getOrDefault(com.wailsvlr.prism.modules.BlockFinder.Group.BEDS, 0) >= 1
				&& blockCounts.getOrDefault(com.wailsvlr.prism.modules.BlockFinder.Group.REDSTONE, 0) >= 1
				&& blockCounts.getOrDefault(com.wailsvlr.prism.modules.BlockFinder.Group.SIGNS, 0) >= 1, "BlockFinder finds workstations, beds, redstone and signs");
			check(blockCounts.getOrDefault(com.wailsvlr.prism.modules.BlockFinder.Group.CUSTOM, 0) == 1, "BlockFinder finds the typed custom block (sponge)");
			check(ctx.computeOnClient(mc -> blockFinder.bases().stream().anyMatch(b -> b.box().contains(42.5, -59.5, 41.5) && b.score() >= 20)),
				"Base Alert reports the stash as a possible base");
			var raided = ctx.computeOnClient(mc -> blockFinder.bases().stream().filter(b -> b.box().contains(-38.5, -59.5, 41.5)).findFirst().orElse(null));
			log.add("raided base: " + raided);
			check(raided != null && raided.status().equals("Raided") && raided.evidence().stream().anyMatch(e -> e.startsWith("sign says"))
				&& raided.evidence().contains("no chests or shulkers left"), "Raid Check marks the looted base as Raided (sign, no storage, ...)");
			check(ctx.computeOnClient(mc -> blockFinder.bases().stream().anyMatch(b -> b.box().contains(42.5, -59.5, 41.5) && b.status().equals("Intact"))),
				"Raid Check leaves the full stash Intact");
			check(ctx.computeOnClient(mc -> blockFinder.bases().stream().noneMatch(b -> b.box().contains(-74.5, -59.5, -74.5))),
				"a village (beds, job blocks, chests, torches, a bell) is not reported as a base");
			check(blockCounts.getOrDefault(com.wailsvlr.prism.modules.BlockFinder.Group.PORTALS, 0) >= 25
				&& blockCounts.getOrDefault(com.wailsvlr.prism.modules.BlockFinder.Group.BEDS, 0) >= 15, "the village and portal scenes are scanned (" + blockCounts + ")");
			check(ctx.computeOnClient(mc -> blockFinder.bases().stream().noneMatch(b -> b.box().contains(69.5, -57.5, -71.5))),
				"a lit nether portal by a village is not reported as a base");
			var farm = ctx.computeOnClient(mc -> blockFinder.bases().stream().filter(b -> b.box().contains(-78.5, -59.5, 81.5)).findFirst().orElse(null));
			log.add("farm: " + farm);
			check(farm != null && farm.status().equals("Intact"), "a cobblestone farm with flowing lava and a 'Raid Farm' sign is not marked raided");
			double stashEdge = ctx.computeOnClient(mc -> blockFinder.bases().stream().filter(b -> b.box().contains(42.5, -59.5, 41.5)).mapToDouble(b -> b.box().maxX).max().orElse(0));
			check(stashEdge > 0 && stashEdge <= 64, "a row of torches does not stretch the stash's box across the map (box ends at x " + stashEdge + ")");
			// the Inspect key flies the freecam camera over a base and explains it in chat
			var inspected = ctx.computeOnClient(mc -> blockFinder.inspectNext());
			ctx.waitTicks(3);
			check(inspected != null && ctx.computeOnClient(mc -> com.wailsvlr.prism.modules.Freecam.active() != null
				&& mc.gameRenderer.getMainCamera().position().distanceTo(inspected.box().getCenter()) < 70), "Inspect flies the Freecam camera over the base");
			shot(ctx, "12b_inspect_base");
			ctx.runOnClient(mc -> PrismClient.modules().get(com.wailsvlr.prism.modules.Freecam.class).setEnabled(false));
			ctx.waitTicks(3);
			server.runCommand("setblock -11 -60 4 beacon");
			ctx.waitTicks(5);
			check(ctx.computeOnClient(mc -> blockFinder.counts().getOrDefault(com.wailsvlr.prism.modules.BlockFinder.Group.CUSTOM, 0)) == 2,
				"a placed custom block shows up within 5 ticks");
			shot(ctx, "12_blockfinder");
			ctx.runOnClient(mc -> blockFinder.setEnabled(false));

			// ---------------------------------------------------------------- item finder
			com.wailsvlr.prism.modules.ItemFinder itemFinder = PrismClient.modules().get(com.wailsvlr.prism.modules.ItemFinder.class);
			server.runCommand("summon item 2.5 -60 5.5 {Item:{id:\"minecraft:diamond\",count:3},PickupDelay:32767}");
			server.runCommand("summon item -1.5 -60 5.5 {Item:{id:\"minecraft:dirt\",count:5},PickupDelay:32767}");
			server.runCommand("summon item_frame 4.5 -60.5 7.5 {Facing:1b,Item:{id:\"minecraft:elytra\",count:1}}");
			ctx.runOnClient(mc -> itemFinder.setEnabled(true));
			ctx.waitTicks(20);
			List<String> itemLabels = ctx.computeOnClient(mc -> EspRenderer.lastFrame().stream().map(com.wailsvlr.prism.render.Highlight::label)
				.filter(java.util.Objects::nonNull).toList());
			log.add("item labels: " + itemLabels);
			check(itemLabels.stream().anyMatch(l -> l.startsWith("Diamond ×3")), "ItemFinder shows the dropped diamonds with their amount");
			check(itemLabels.stream().anyMatch(l -> l.startsWith("Elytra")), "ItemFinder shows the elytra in the item frame");
			check(itemLabels.stream().noneMatch(l -> l.startsWith("Dirt")), "'Valuable' leaves the dirt out");
			shot(ctx, "13_itemfinder");
			ctx.runOnClient(mc -> { for (var st : itemFinder.getSettings()) if (st.getName().equals("Show")) ((com.wailsvlr.prism.setting.ModeSetting) st).set("All"); });
			ctx.waitTicks(3);
			check(ctx.computeOnClient(mc -> itemFinder.targets().stream().anyMatch(t -> t.name().equals("Dirt") && t.count() == 5)), "'All' shows the dirt too");
			ctx.runOnClient(mc -> itemFinder.setEnabled(false));

			// ---------------------------------------------------------------- freecam
			com.wailsvlr.prism.modules.Freecam freecam = PrismClient.modules().get(com.wailsvlr.prism.modules.Freecam.class);
			ctx.runOnClient(mc -> { storage.setEnabled(false); spawners.setEnabled(false); });
			net.minecraft.world.phys.Vec3 clientStart = ctx.computeOnClient(mc -> mc.player.position());
			net.minecraft.world.phys.Vec3 serverStart = server.computeOnServer(srv -> srv.getPlayerList().getPlayers().get(0).position());
			float yawStart = ctx.computeOnClient(mc -> mc.player.getYRot());
			ctx.runOnClient(mc -> freecam.setEnabled(true));
			ctx.waitTicks(2);
			net.minecraft.world.phys.Vec3 camStart = ctx.computeOnClient(mc -> mc.gameRenderer.getMainCamera().position());
			ctx.getInput().holdKeyFor(opts -> opts.keyUp, 30);
			ctx.waitTicks(10);
			double yBeforeJump = ctx.computeOnClient(mc -> mc.gameRenderer.getMainCamera().position().y);
			ctx.getInput().holdKeyFor(opts -> opts.keyJump, 12);
			ctx.waitTicks(10);
			net.minecraft.world.phys.Vec3 camAfter = ctx.computeOnClient(mc -> mc.gameRenderer.getMainCamera().position());
			net.minecraft.world.phys.Vec3 clientAfter = ctx.computeOnClient(mc -> mc.player.position());
			net.minecraft.world.phys.Vec3 serverAfter = server.computeOnServer(srv -> srv.getPlayerList().getPlayers().get(0).position());
			log.add(String.format("freecam: camera %s -> %s, player client %s -> %s, server %s -> %s", camStart, camAfter, clientStart, clientAfter, serverStart, serverAfter));
			check(camAfter.distanceTo(camStart) > 5, String.format("W + Jump fly the camera (%.1f blocks)", camAfter.distanceTo(camStart)));
			check(camAfter.y > yBeforeJump + 3, String.format("Jump raises the camera (%.1f blocks)", camAfter.y - yBeforeJump));
			check(clientAfter.distanceTo(clientStart) < 0.01, String.format("player did not move on the client (%.3f)", clientAfter.distanceTo(clientStart)));
			check(serverAfter.distanceTo(serverStart) < 0.01, String.format("player did not move on the server (%.3f)", serverAfter.distanceTo(serverStart)));
			ctx.runOnClient(mc -> mc.player.turn(400, -150));   // what the mouse handler calls
			ctx.waitTicks(3);
			float yawPlayer = ctx.computeOnClient(mc -> mc.player.getYRot());
			float yawServer = server.computeOnServer(srv -> srv.getPlayerList().getPlayers().get(0).getYRot());
			float yawCam = ctx.computeOnClient(mc -> mc.gameRenderer.getMainCamera().yRot());
			check(yawPlayer == yawStart && Math.abs(yawServer - yawStart) < 0.01, "mouse look does not turn the player (client " + yawPlayer + ", server " + yawServer + ")");
			check(Math.abs(yawCam - (yawStart + 60)) < 0.01, "mouse look turns the camera (" + yawCam + ")");
			// turn 120 more degrees (180 in total) to look back north at the player and the test blocks
			ctx.runOnClient(mc -> mc.player.turn(800, 0));
			ctx.waitTicks(3);
			shot(ctx, "16_freecam_looking_back_at_player");
			// inside the dirt layer under the test blocks, looking up at them: the world must still render (spectator culling)
			ctx.runOnClient(mc -> freecam.moveTo(new net.minecraft.world.phys.Vec3(0.5, -62.5, -2.5), 0, -30));
			ctx.waitTicks(10);
			shot(ctx, "17_freecam_inside_the_ground_looking_up");
			check(ctx.computeOnClient(mc -> mc.gameRenderer.getMainCamera().position().y) < -61, "camera is inside the ground");
			ctx.runOnClient(mc -> freecam.setEnabled(false));
			ctx.waitTicks(3);
			net.minecraft.world.phys.Vec3 camBack = ctx.computeOnClient(mc -> mc.gameRenderer.getMainCamera().position());
			net.minecraft.world.phys.Vec3 eye = ctx.computeOnClient(mc -> mc.player.getEyePosition());
			check(camBack.distanceTo(eye) < 0.2, "after turning freecam off the camera is back at the player's eyes");
			check(server.computeOnServer(srv -> srv.getPlayerList().getPlayers().get(0).position()).distanceTo(serverStart) < 0.01, "server position still unchanged after freecam");

			// ---------------------------------------------------------------- performance
			ctx.runOnClient(mc -> {
				mc.options.framerateLimit().set(260);
				mc.options.enableVsync().set(false);
				mc.getWindow().updateVsync(false);
			});
			HudModule hudMod = PrismClient.modules().get(HudModule.class);
			ctx.runOnClient(mc -> { storage.setEnabled(false); spawners.setEnabled(false); ores.setEnabled(false); hudMod.setEnabled(false); });
			log.add("perf: baseline (nothing on) " + fps(ctx) + " fps");
			ctx.runOnClient(mc -> { storage.setEnabled(true); spawners.setEnabled(true); hudMod.setEnabled(true); });
			log.add("perf: HUD + Storage + Spawner " + fps(ctx) + " fps");
			ctx.runOnClient(mc -> { storage.setEnabled(false); spawners.setEnabled(false); });
			log.add("perf: HUD only " + fps(ctx) + " fps");
			ctx.runOnClient(mc -> { storage.setEnabled(true); hudMod.setEnabled(false); });
			log.add("perf: Storage only (no HUD, no labels) " + fps(ctx) + " fps");
			ctx.runOnClient(mc -> { spawners.setEnabled(true); hudMod.setEnabled(true); });
			ctx.setScreen(ClickGuiScreen::new);
			log.add("perf: ClickGUI open " + fps(ctx) + " fps");
			ctx.runOnClient(mc -> PrismClient.modules().get(ClickGuiModule.class).getSettings().forEach(st -> { if (st.getName().equals("Blur")) ((com.wailsvlr.prism.setting.BoolSetting) st).set(false); }));
			log.add("perf: ClickGUI open, blur off " + fps(ctx) + " fps");
			ctx.runOnClient(mc -> PrismClient.modules().get(ClickGuiModule.class).getSettings().forEach(st -> { if (st.getName().equals("Blur")) ((com.wailsvlr.prism.setting.BoolSetting) st).set(true); }));
			ctx.setScreen(() -> null);
			// a big storage room and an ore wall in front of the player
			server.runCommand("fill -12 -60 24 12 -57 36 chest");
			server.runCommand("fill -20 -63 40 20 -50 41 diamond_ore");
			ctx.runOnClient(mc -> { storage.setEnabled(false); spawners.setEnabled(false); hudMod.setEnabled(false); });
			log.add("perf: stress scene, nothing on " + fps(ctx) + " fps");
			ctx.runOnClient(mc -> { storage.setEnabled(true); hudMod.setEnabled(true); });
			log.add("perf: stress scene, Storage + HUD " + fps(ctx) + " fps (" + ctx.computeOnClient(mc -> storage.counts().values().stream().mapToInt(Integer::intValue).sum()) + " storage)");
			ctx.runOnClient(mc -> hudMod.setEnabled(false));
			log.add("perf: stress scene, Storage, HUD off " + fps(ctx) + " fps");
			ctx.runOnClient(mc -> hudMod.setEnabled(true));
			ctx.runOnClient(mc -> { storage.setEnabled(false); ores.setEnabled(true); });
			log.add("perf: stress scene, Ores + HUD " + fps(ctx) + " fps (" + ctx.computeOnClient(mc -> ores.counts().values().stream().mapToInt(Integer::intValue).sum()) + " ores)");
			ctx.runOnClient(mc -> hudMod.setEnabled(false));
			log.add("perf: stress scene, Ores, HUD off " + fps(ctx) + " fps");
			com.wailsvlr.prism.modules.BlockFinder bfPerf = PrismClient.modules().get(com.wailsvlr.prism.modules.BlockFinder.class);
			ctx.runOnClient(mc -> { ores.setEnabled(false); hudMod.setEnabled(true); bfPerf.setEnabled(true); });
			log.add("perf: stress scene, BlockFinder + HUD " + fps(ctx) + " fps (" + ctx.computeOnClient(mc -> bfPerf.bases().size()) + " bases)");
			ctx.runOnClient(mc -> bfPerf.setEnabled(false));
			shot(ctx, "18_stress_scene");
			// maximum range: still finds everything, and the scan only walks loaded chunks
			ctx.runOnClient(mc -> { ores.setEnabled(false); storage.setEnabled(true); hudMod.setEnabled(true);
				for (var st : storage.getSettings()) if (st.getName().equals("Range")) ((com.wailsvlr.prism.setting.NumberSetting) st).set(10250.0); });
			int maxRangeFps = fps(ctx);
			int maxRangeCount = ctx.computeOnClient(mc -> storage.counts().values().stream().mapToInt(Integer::intValue).sum());
			log.add("perf: stress scene, Storage + HUD, range 10250 " + maxRangeFps + " fps (" + maxRangeCount + " storage)");
			check(maxRangeCount >= 1308, "range 10250 still finds every container (" + maxRangeCount + ")");
			ctx.runOnClient(mc -> { for (var st : storage.getSettings()) if (st.getName().equals("Range")) ((com.wailsvlr.prism.setting.NumberSetting) st).set(128.0); });
			ctx.runOnClient(mc -> { ores.setEnabled(false); hudMod.setEnabled(true); });

			// ---------------------------------------------------------------- base hunter
			com.wailsvlr.prism.modules.BaseHunter hunter = PrismClient.modules().get(com.wailsvlr.prism.modules.BaseHunter.class);
			// a raided base (12 chests, raid sign, loot on the ground, lava) and an intact one (14 chests, 2 shulkers) far away
			server.runCommand("forceload add 3000 3000 3015 3015");
			server.runCommand("forceload add 5000 5000 5015 5015");
			for (String b : new String[]{"fill 3000 -60 3000 3011 -60 3000 chest[type=single]", "fill 3000 -60 3002 3002 -60 3002 enchanting_table",
				"setblock 3005 -60 3003 oak_sign{front_text:{messages:[\"RAIDED\",\"\",\"\",\"\"]}}", "setblock 3010 -60 3004 lava",
				"fill 5000 -60 5000 5013 -60 5000 chest[type=single]", "fill 5000 -60 5002 5001 -60 5002 white_shulker_box"}) server.runCommand(b);
			for (int i = 0; i < 6; i++) server.runCommand("summon item " + (3004.5 + i * 0.4) + " -60 3001.5 {Item:{id:\"minecraft:cobblestone\",count:4},PickupDelay:32767}");
			ctx.waitTicks(10);
			TestServerCommands.plan(List.of(
				"execute in minecraft:overworld run tp @s 2000.5 -60 2000.5",
				"execute in minecraft:overworld run tp @s 3006.5 -60 2995.5",
				"execute in minecraft:overworld run tp @s 5006.5 -60 4995.5"));
			try { Files.deleteIfExists(com.wailsvlr.prism.modules.BaseHunter.logFile()); } catch (IOException ignored) { }
			ctx.runOnClient(mc -> {
				storage.setEnabled(false); spawners.setEnabled(false); ores.setEnabled(false);
				itemFinder.setEnabled(true);   // an ESP that showing the found base has to switch off and back on
				com.wailsvlr.prism.util.HeatMap.get().clearWorld();
				// a long delay, so moving on early from the empty spot shows
				for (var st : hunter.getSettings()) {
					if (st.getName().equals("Min Delay")) ((com.wailsvlr.prism.setting.NumberSetting) st).set(10.0);
					if (st.getName().equals("Max Delay")) ((com.wailsvlr.prism.setting.NumberSetting) st).set(10.0);
				}
				hunter.setEnabled(true);
			});
			for (int i = 0; i < 160 && ctx.computeOnClient(mc -> hunter.isEnabled()); i++) ctx.waitTicks(10);
			log.add("base hunter: " + ctx.computeOnClient(mc -> hunter.status()) + ", teleports " + ctx.computeOnClient(mc -> hunter.teleports())
				+ ", server rtp " + TestServerCommands.teleports + " ok / " + TestServerCommands.refused + " refused");
			check(ctx.computeOnClient(mc -> hunter.found()) == 1 && ctx.computeOnClient(mc -> hunter.teleports()) == 3,
				"BaseHunter skips the empty spot and the raided base, and stops at the intact one (3rd teleport)");
			check(TestServerCommands.refused >= 1, "BaseHunter waits out the server's 'You must wait N seconds' cooldown");
			long firstStay = TestServerCommands.TIMES.size() >= 2 ? TestServerCommands.TIMES.get(1) - TestServerCommands.TIMES.get(0) : -1;
			check(firstStay > 0 && firstStay < 8000, "Skip Empty Areas leaves the empty spot early (" + firstStay + " ms with a 10 s delay)");
			var marks = ctx.computeOnClient(mc -> List.copyOf(com.wailsvlr.prism.util.HeatMap.get().markers()));
			log.add("heat map: " + ctx.computeOnClient(mc -> com.wailsvlr.prism.util.HeatMap.get().cells().keySet()) + ", markers "
				+ marks.stream().map(m -> m.type + "@" + m.x + "," + m.z + ":" + m.status).toList());
			check(ctx.computeOnClient(mc -> com.wailsvlr.prism.util.HeatMap.get().cells().size()) >= 3
				&& marks.stream().anyMatch(m -> m.status.equals("Raided") && Math.abs(m.x - 3006) < 20)
				&& marks.stream().anyMatch(m -> m.status.equals("Intact") && Math.abs(m.x - 5007) < 20), "the heat map keeps the landings and the raided and intact bases");
			String baseLog = "";
			try { baseLog = Files.readString(com.wailsvlr.prism.modules.BaseHunter.logFile(), StandardCharsets.UTF_8); } catch (IOException ignored) { }
			log.add("base log: " + baseLog.trim());
			check(baseLog.contains("14 chests, 2 shulkers") && baseLog.contains("Intact") && !baseLog.contains("Raided"), "the found base is written to prismclient-bases.txt");
			check(ctx.computeOnClient(mc -> com.wailsvlr.prism.modules.Freecam.active() != null && !itemFinder.isEnabled()
				&& PrismClient.modules().get(com.wailsvlr.prism.modules.BlockFinder.class).focused() != null
				&& mc.gameRenderer.getMainCamera().position().distanceTo(new net.minecraft.world.phys.Vec3(5007, -60, 5001)) < 70),
				"the found base is shown alone: other ESPs off, Freecam camera over it");
			shot(ctx, "19_basehunter_found");
			ctx.runOnClient(mc -> PrismClient.modules().get(com.wailsvlr.prism.modules.Freecam.class).setEnabled(false));
			ctx.waitTicks(3);
			check(ctx.computeOnClient(mc -> itemFinder.isEnabled() && PrismClient.modules().get(com.wailsvlr.prism.modules.BlockFinder.class).focused() == null),
				"turning Freecam off brings the other ESPs back");
			ctx.runOnClient(mc -> itemFinder.setEnabled(false));
			// the heat map screen: clicking the intact base makes it the target, shown in the HUD
			ctx.setScreen(com.wailsvlr.prism.gui.HeatMapScreen::new);
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> {
				if (!(mc.screen instanceof com.wailsvlr.prism.gui.HeatMapScreen mapScreen)) return;
				for (var m : com.wailsvlr.prism.util.HeatMap.get().markers()) {
					if (m.status.equals("Intact") && Math.abs(m.x - 5007) < 20) { float[] p = mapScreen.screenPos(m.x, m.z); mapScreen.select(p[0], p[1]); }
				}
			});
			shot(ctx, "19b_heat_map");
			check(ctx.computeOnClient(mc -> { var t = com.wailsvlr.prism.util.HeatMap.get().target(); return t != null && "Intact".equals(t.status); }),
				"clicking a base on the heat map makes it the target");
			ctx.setScreen(() -> null);
			ctx.waitTicks(3);
			shot(ctx, "19c_target_in_hud");
			ctx.runOnClient(mc -> com.wailsvlr.prism.util.HeatMap.get().setTarget(null));
			// safety: taking damage stops it
			ctx.runOnClient(mc -> hunter.setEnabled(true));
			ctx.waitTicks(5);
			server.runCommand("damage @p 2");
			ctx.waitTicks(5);
			check(!ctx.computeOnClient(mc -> hunter.isEnabled()) && ctx.computeOnClient(mc -> hunter.status()).contains("damage"), "BaseHunter stops when you take damage");
			// nether and end use their own commands
			server.runCommand("gamemode creative @p");
			for (String[] d : new String[][]{{"Nether", "nether", "minecraft:the_nether"}, {"End", "end", "minecraft:the_end"}}) {
				ctx.runOnClient(mc -> {
					for (var st : hunter.getSettings()) if (st.getName().equals("Dimension")) ((com.wailsvlr.prism.setting.ModeSetting) st).set(d[0]);
					hunter.setEnabled(true);
				});
				for (int i = 0; i < 120 && !ctx.computeOnClient(mc -> mc.level.dimension().identifier().toString()).equals(d[2]); i++) ctx.waitTicks(5);
				ctx.runOnClient(mc -> hunter.setEnabled(false));
				log.add("hunter " + d[0] + ": " + ctx.computeOnClient(mc -> mc.level.dimension().identifier() + " " + hunter.status() + " " + hunter.lastCommand())
					+ ", server got " + TestServerCommands.lastRequest);
				check(TestServerCommands.lastRequest.equals(d[1]) && ctx.computeOnClient(mc -> mc.level.dimension().identifier().toString()).equals(d[2])
					&& ctx.computeOnClient(mc -> hunter.lastCommand()).equals("/rtp " + d[1]), "Dimension " + d[0] + " sends /rtp " + d[1] + " and lands in " + d[2]);
			}
			// the Quick RTP key: one teleport with hunting off, and the old area's bases are dropped
			ctx.runOnClient(mc -> { for (var st : hunter.getSettings()) if (st.getName().equals("Dimension")) ((com.wailsvlr.prism.setting.ModeSetting) st).set("Overworld"); });
			ctx.waitTicks(100);   // the test server's rtp cooldown
			TestServerCommands.plan(List.of("execute in minecraft:overworld run tp @s 7000.5 -60 7000.5"));
			ctx.runOnClient(mc -> hunter.quickRtp());
			for (int i = 0; i < 60 && ctx.computeOnClient(mc -> mc.player.getX()) < 6000; i++) ctx.waitTicks(5);
			ctx.waitTicks(5);
			check(!ctx.computeOnClient(mc -> hunter.isEnabled()) && ctx.computeOnClient(mc -> mc.player.getX()) > 6000 && TestServerCommands.lastRequest.equals("overworld")
				&& ctx.computeOnClient(mc -> PrismClient.modules().get(com.wailsvlr.prism.modules.BlockFinder.class).bases().stream().noneMatch(b -> b.center().getX() < 6000)),
				"Quick RTP teleports once (hunting stays off) and forgets the old area's bases");
			server.runCommand("execute in minecraft:overworld run tp @p 0.5 -60 0.5");
			server.runCommand("gamemode survival @p");
			ctx.runOnClient(mc -> PrismClient.modules().get(com.wailsvlr.prism.modules.BlockFinder.class).setEnabled(false));
			ctx.waitTicks(20);

			// ---------------------------------------------------------------- trail finder
			com.wailsvlr.prism.modules.TrailFinder trails = PrismClient.modules().get(com.wailsvlr.prism.modules.TrailFinder.class);
			ctx.runOnClient(mc -> trails.setEnabled(true));
			// someone was here before: 3x3 chunks whose water has long finished flowing when we arrive
			server.runCommand("forceload add 12000 12000 12047 12047");
			for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) server.runCommand("setblock " + (12008 + 16 * i) + " -60 " + (12008 + 16 * j) + " water");
			server.runCommand("execute in minecraft:overworld run tp @p 9000.5 -60 9000.5");
			ctx.waitTicks(60);
			// a liquid starting to flow in front of us, like in a chunk that was just generated
			server.runCommand("setblock 9000 -60 9004 lava");
			ctx.waitTicks(40);
			check(ctx.computeOnClient(mc -> trails.age(562, 562)) == 1, "TrailFinder marks a chunk whose liquid starts flowing as new");
			// BaseHunter lands next to the old chunks and stops on the trail
			ctx.waitTicks(80);   // the test server's rtp cooldown
			TestServerCommands.plan(List.of("execute in minecraft:overworld run tp @s 12024.5 -60 12024.5"));
			try { Files.deleteIfExists(com.wailsvlr.prism.modules.BaseHunter.logFile()); } catch (IOException ignored) { }
			ctx.runOnClient(mc -> {
				for (var st : hunter.getSettings()) if (st.getName().equals("Min New Chunks")) ((com.wailsvlr.prism.setting.NumberSetting) st).set(0.0);
				hunter.setEnabled(true);
			});
			for (int i = 0; i < 60 && ctx.computeOnClient(mc -> hunter.isEnabled()); i++) ctx.waitTicks(10);
			int[] around = ctx.computeOnClient(mc -> trails.around(mc.options.getEffectiveRenderDistance()));
			log.add("trail: " + ctx.computeOnClient(mc -> hunter.status()) + ", " + around[0] + " old / " + around[1] + " new");
			check(around[0] >= 9, "the 9 chunks that arrive with flowing water are marked old (" + around[0] + ")");
			String trailLog = "";
			try { trailLog = Files.readString(com.wailsvlr.prism.modules.BaseHunter.logFile(), StandardCharsets.UTF_8); } catch (IOException ignored) { }
			check(ctx.computeOnClient(mc -> hunter.status()).equals("found a trail") && trailLog.contains("TRAIL"), "BaseHunter stops on the trail and logs it");
			check(ctx.computeOnClient(mc -> com.wailsvlr.prism.modules.Freecam.active() != null), "a found trail is shown from above with Freecam");
			shot(ctx, "20_trailfinder");
			ctx.runOnClient(mc -> PrismClient.modules().get(com.wailsvlr.prism.modules.Freecam.class).setEnabled(false));
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> {
				for (var st : hunter.getSettings()) if (st.getName().equals("Min New Chunks")) ((com.wailsvlr.prism.setting.NumberSetting) st).set(10.0);
				trails.setEnabled(false);
			});
			server.runCommand("execute in minecraft:overworld run tp @p 0.5 -60 0.5");
			ctx.waitTicks(20);

			// ---------------------------------------------------------------- chunk finder
			com.wailsvlr.prism.modules.ChunkFinder cfm = PrismClient.modules().get(com.wailsvlr.prism.modules.ChunkFinder.class);
			server.runCommand("forceload add 15000 15000 15100 15100");
			ctx.waitTicks(60);   // the forced chunks load in the background; commands into unloaded chunks fail
			// a geode that kept growing (8 budding blocks, 2 clusters each: 2.0 per budding), one that grew a little
			// (0.875) and a fresh one (2 small buds: 0.0), each in its own chunk; and a chunk with 16 player-only blocks
			int[][] geodes = {{15008, 2}, {15040, 1}, {15072, 0}};
			for (int[] gd : geodes) {
				int gx = gd[0];
				server.runCommand("fill " + (gx - 3) + " -50 15005 " + (gx + 3) + " -44 15011 calcite");
				server.runCommand("fill " + (gx - 2) + " -49 15006 " + (gx + 2) + " -45 15010 air");
				for (int k = 0; k < 8; k++) {
					int bx = gx - 2 + (k % 4), bz = k < 4 ? 15006 : 15010;
					server.runCommand("setblock " + bx + " -49 " + bz + " budding_amethyst");
					if (gd[1] == 2) {
						server.runCommand("setblock " + bx + " -48 " + bz + " amethyst_cluster[facing=up]");
						server.runCommand("setblock " + bx + " -49 " + (bz == 15006 ? 15007 : 15009) + " amethyst_cluster[facing=" + (bz == 15006 ? "south" : "north") + "]");
					} else if (gd[1] == 1 && k < 7) {
						server.runCommand("setblock " + bx + " -48 " + bz + " amethyst_cluster[facing=up]");
					}
				}
				if (gd[1] == 0) {
					server.runCommand("setblock " + (gx - 2) + " -48 15006 small_amethyst_bud[facing=up]");
					server.runCommand("setblock " + (gx - 1) + " -48 15006 small_amethyst_bud[facing=up]");
				}
			}
			server.runCommand("fill 15024 -60 15040 15027 -60 15043 lime_concrete");
			// a geode made by the game itself, to see how a fresh natural one scores
			server.runCommand("fill 15078 -60 15078 15102 -30 15102 stone");
			server.runCommand("place feature minecraft:amethyst_geode 15086 -55 15086");
			server.runCommand("execute in minecraft:overworld run tp @p 15040.5 -60 15024.5 0 60");
			ctx.runOnClient(mc -> {
				for (var st : cfm.getSettings()) if (st.getName().equals("Sensitivity")) ((com.wailsvlr.prism.setting.NumberSetting) st).set(5.0);
				cfm.setEnabled(true);
			});
			ctx.waitTicks(200);
			ctx.runOnClient(mc -> cfm.evaluate(mc.level));
			// each test geode straddles a chunk border on purpose (x 15006..15009 and z 15006..15010): judged as one geode
			java.util.function.Function<Integer, String> signAt = cx -> ctx.computeOnClient(mc -> cfm.active().stream()
				.filter(a -> a.x() == cx && a.z() == (15010 >> 4) && a.sign() == com.wailsvlr.prism.modules.ChunkFinder.Sign.AMETHYST)
				.map(a -> a.sign() + " " + a.detail()).findFirst().orElse("none"));
			String grownSign = signAt.apply(15008 >> 4), mediumSign = signAt.apply(15040 >> 4), freshSign = signAt.apply(15072 >> 4);
			log.add("chunk finder (sensitivity 5, threshold " + ctx.computeOnClient(mc -> cfm.amethystThreshold()) + "): grown " + grownSign + ", medium " + mediumSign + ", fresh " + freshSign);
			check(grownSign.startsWith("AMETHYST"), "ChunkFinder marks the chunk whose geode kept growing");
			check(mediumSign.startsWith("AMETHYST"), "at sensitivity 5 the half-grown geode (0.88 per budding) is marked too");
			check(freshSign.equals("none"), "the fresh geode is not marked");
			boolean playerChunk = ctx.computeOnClient(mc -> cfm.active().stream().anyMatch(a -> a.sign() == com.wailsvlr.prism.modules.ChunkFinder.Sign.PLAYER
				&& a.x() == (15024 >> 4) && a.z() == (15040 >> 4)));
			check(playerChunk, "ChunkFinder marks the chunk with 16 player-only blocks");
			String natural = ctx.computeOnClient(mc -> {
				int budding = 0; double grown = 0;
				for (BlockPos p : BlockPos.betweenClosed(15078, -55, 15078, 15102, -25, 15102)) {
					var b = mc.level.getBlockState(p).getBlock();
					if (b == net.minecraft.world.level.block.Blocks.BUDDING_AMETHYST) budding++;
					else if (b == net.minecraft.world.level.block.Blocks.AMETHYST_CLUSTER) grown += 1;
					else if (b == net.minecraft.world.level.block.Blocks.LARGE_AMETHYST_BUD) grown += 0.5;
				}
				return budding + " budding, " + grown + " grown (" + (budding == 0 ? "-" : String.format(java.util.Locale.ROOT, "%.2f", grown / budding)) + " per budding)";
			});
			log.add("chunk finder: a geode the game just generated has " + natural);
			// lower sensitivity: only clear cases
			ctx.runOnClient(mc -> {
				for (var st : cfm.getSettings()) if (st.getName().equals("Sensitivity")) ((com.wailsvlr.prism.setting.NumberSetting) st).set(1.0);
				cfm.evaluate(mc.level);
			});
			check(signAt.apply(15008 >> 4).startsWith("AMETHYST") && signAt.apply(15040 >> 4).equals("none"),
				"at sensitivity 1 only the fully grown geode stays marked (threshold " + ctx.computeOnClient(mc -> cfm.amethystThreshold()) + ")");
			List<com.wailsvlr.prism.modules.ChunkFinder.Spot> cspots = ctx.computeOnClient(mc -> cfm.spots());
			log.add("chunk finder spots: " + cspots);
			check(!cspots.isEmpty(), "a probable spot is estimated from the active chunks");
			ctx.runOnClient(mc -> {
				for (var st : cfm.getSettings()) if (st.getName().equals("Sensitivity")) ((com.wailsvlr.prism.setting.NumberSetting) st).set(5.0);
				cfm.evaluate(mc.level);
				mc.player.setXRot(55);
			});
			ctx.waitTicks(10);
			shot(ctx, "26_chunkfinder");
			ctx.runOnClient(mc -> cfm.setEnabled(false));
			server.runCommand("execute in minecraft:overworld run tp @p 0.5 -60 0.5");
			ctx.waitTicks(20);

			// ---------------------------------------------------------------- auto sell
			com.wailsvlr.prism.modules.AutoSell autoSell = PrismClient.modules().get(com.wailsvlr.prism.modules.AutoSell.class);
			com.wailsvlr.prism.modules.MoneyTracker money = PrismClient.modules().get(com.wailsvlr.prism.modules.MoneyTracker.class);
			// a money sidebar like survival servers have; /sell pays $10 per item into it
			server.runCommand("scoreboard objectives add bal dummy \"Stats\"");
			server.runCommand("scoreboard objectives setdisplay sidebar bal");
			server.runCommand("scoreboard players set money bal 3");
			server.runCommand("scoreboard players set kills bal 2");
			server.runCommand("scoreboard players display name money bal \"Money: $12,345\"");
			ctx.runOnClient(mc -> money.setEnabled(true));
			ctx.waitTicks(25);
			Double startBalance = ctx.computeOnClient(mc -> money.balance());
			check(startBalance != null && startBalance == 12345, "MoneyTracker reads the balance from the sidebar (" + startBalance + ")");
			server.runCommand("gamemode survival @p");
			server.runCommand("clear @p");
			for (int i = 0; i < 30; i++) server.runCommand("give @p golden_carrot 64");
			server.runCommand("give @p dirt 16");
			server.runCommand("give @p paper[custom_name=\"Mystery Note\"] 5");
			server.runCommand("give @p paper 7");
			server.runCommand("give @p diamond_sword[enchantments={sharpness:5}] 1");
			ctx.waitTicks(10);
			// mark with the real key: open the inventory, put the mouse over a stack, press K
			ctx.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
			ctx.waitTicks(5);
			for (String what : new String[]{"minecraft:golden_carrot", "minecraft:paper=Mystery Note"}) {
				double[] at = ctx.computeOnClient(mc -> {
					var scr = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.screen;
					var acc = (com.wailsvlr.prism.mixin.AbstractContainerScreenAccessor) scr;
					for (var slot : scr.getMenu().slots)
						if (slot.hasItem() && com.wailsvlr.prism.modules.AutoSell.markOf(slot.getItem()).equals(what)) {
							double k = mc.getWindow().getGuiScale();
							return new double[]{(acc.prism$leftPos() + slot.x + 8) * k, (acc.prism$topPos() + slot.y + 8) * k};
						}
					return null;
				});
				check(at != null, "the item to mark is in the inventory: " + what);
				if (at == null) continue;
				ctx.getInput().setCursorPos(at[0], at[1]);
				ctx.waitTicks(3);
				ctx.getInput().pressKey(PrismClient.markKey());
				ctx.waitTicks(3);
			}
			String sellMarks = ctx.computeOnClient(mc -> autoSell.markedList().get());
			log.add("marked: " + sellMarks);
			check(sellMarks.contains("minecraft:golden_carrot") && sellMarks.contains("minecraft:paper=Mystery Note") && !sellMarks.contains("dirt"),
				"the mark key marks the hovered item, and a renamed item by its name");
			shot(ctx, "21_autosell_marks");
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(5);
			// the sell key: /sell, fill, close; the test menu holds 27 stacks, so the last 3 + the notes need a second round
			ctx.getInput().pressKey(PrismClient.sellKey());
			for (int i = 0; i < 100 && (TestServerCommands.sellMenus == 0 || ctx.computeOnClient(mc -> autoSell.busy())); i++) ctx.waitTicks(5);
			ctx.waitTicks(10);
			int carrots = TestServerCommands.SOLD.getOrDefault("minecraft:golden_carrot", 0), notes = TestServerCommands.SOLD.getOrDefault("minecraft:paper=Mystery Note", 0);
			int[] inv = ctx.computeOnClient(mc -> {
				int c = 0, plainPaper = 0, dirt = 0, swords = 0, named = 0;
				for (var st : mc.player.getInventory().getNonEquipmentItems()) {
					if (st.is(net.minecraft.world.item.Items.GOLDEN_CARROT)) c += st.getCount();
					if (st.is(net.minecraft.world.item.Items.PAPER)) { if (st.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) named += st.getCount(); else plainPaper += st.getCount(); }
					if (st.is(net.minecraft.world.item.Items.DIRT)) dirt += st.getCount();
					if (st.is(net.minecraft.world.item.Items.DIAMOND_SWORD)) swords++;
				}
				return new int[]{c, named, plainPaper, dirt, swords};
			});
			log.add("sold " + TestServerCommands.SOLD + " in " + TestServerCommands.sellMenus + " menus; left: carrots " + inv[0] + ", notes " + inv[1]
				+ ", paper " + inv[2] + ", dirt " + inv[3] + ", swords " + inv[4] + "; " + ctx.computeOnClient(mc -> autoSell.lastResult()));
			check(carrots == 30 * 64 && notes == 5 && inv[0] == 0 && inv[1] == 0, "the sell key sells every marked stack (1920 carrots + 5 notes)");
			check(TestServerCommands.sellMenus == 2, "a full sell menu is closed and /sell is sent again for the rest (" + TestServerCommands.sellMenus + " menus)");
			check(inv[2] == 7 && inv[3] == 16 && inv[4] == 1 && !TestServerCommands.SOLD.containsKey("minecraft:paper") && !TestServerCommands.SOLD.containsKey("minecraft:dirt"),
				"unmarked items stay: plain paper (only the renamed one was marked), dirt and the sword");
			check(ctx.computeOnClient(mc -> mc.screen == null), "the sell menu is closed afterwards");
			for (int i = 0; i < 60 && ctx.computeOnClient(mc -> money.lastSale()) == 0; i++) ctx.waitTicks(5);
			double sale = ctx.computeOnClient(mc -> money.lastSale());
			log.add("money: " + ctx.computeOnClient(mc -> money.balance()) + ", " + ctx.computeOnClient(mc -> money.hudDetail()));
			check(sale == 19250 && ctx.computeOnClient(mc -> money.balance()) == 12345 + 19250, "MoneyTracker reports what the sale brought in (+$19,250 for 1925 items, " + sale + ")");
			check(ctx.computeOnClient(mc -> money.session()) == 19250, "the session gain counts from the first balance seen");
			shot(ctx, "22_money_hud");
			// typing /sell yourself: the menu fills and closes on its own
			ctx.runOnClient(mc -> autoSell.setEnabled(true));
			server.runCommand("give @p golden_carrot 64");
			server.runCommand("give @p golden_carrot 64");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.player.connection.sendCommand("sell"));
			for (int i = 0; i < 40 && TestServerCommands.sellMenus < 3; i++) ctx.waitTicks(5);
			for (int i = 0; i < 40 && ctx.computeOnClient(mc -> mc.screen != null); i++) ctx.waitTicks(5);
			ctx.waitTicks(10);
			check(TestServerCommands.SOLD.getOrDefault("minecraft:golden_carrot", 0) == 32 * 64, "a /sell typed by the player is filled and closed too (module on)");
			// Sell When Full: a full inventory sells on its own, no key
			ctx.runOnClient(mc -> setBool(autoSell, "Sell When Full", true));
			int menusBefore = TestServerCommands.sellMenus;
			for (int i = 0; i < 33; i++) server.runCommand("give @p golden_carrot 64");   // + dirt, paper and the sword = 36 slots
			ctx.waitTicks(5);
			int freeNow = ctx.computeOnClient(mc -> { int n = 0; for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) n++; return n; });
			for (int i = 0; i < 100 && (TestServerCommands.sellMenus == menusBefore || ctx.computeOnClient(mc -> autoSell.busy())); i++) ctx.waitTicks(5);
			ctx.waitTicks(10);
			// (the free slot count above is read after selling may already have begun, so it is only logged)
			log.add("sell when full: free slots 5 ticks after filling " + freeNow + ", carrots sold " + TestServerCommands.SOLD.getOrDefault("minecraft:golden_carrot", 0));
			check(TestServerCommands.SOLD.getOrDefault("minecraft:golden_carrot", 0) == (32 + 33) * 64
				&& ctx.computeOnClient(mc -> mc.player.getInventory().countItem(net.minecraft.world.item.Items.GOLDEN_CARROT)) == 0,
				"Sell When Full sells by itself once the inventory is full (" + (TestServerCommands.sellMenus - menusBefore) + " menus)");
			ctx.waitTicks(40);
			check(TestServerCommands.sellMenus - menusBefore == 2, "and stops when nothing is left to sell (dirt, paper and the sword stay)");
			ctx.runOnClient(mc -> { setBool(autoSell, "Sell When Full", false); autoSell.setEnabled(false); });
			server.runCommand("clear @p");

			// ---------------------------------------------------------------- auction helper
			com.wailsvlr.prism.modules.AuctionHelper auction = PrismClient.modules().get(com.wailsvlr.prism.modules.AuctionHelper.class);
			ctx.runOnClient(mc -> {
				auction.setEnabled(true);
				for (var st : auction.getSettings()) if (st.getName().equals("Watch List")) ((com.wailsvlr.prism.setting.TextSetting) st).set("elytra<=5m, beacon<=1k");
			});
			ctx.runOnClient(mc -> mc.player.connection.sendCommand("ah"));
			for (int i = 0; i < 40 && ctx.computeOnClient(mc -> mc.screen == null); i++) ctx.waitTicks(5);
			ctx.waitTicks(10);
			java.util.Set<Integer> cheap = ctx.computeOnClient(mc -> new java.util.TreeSet<>(auction.cheapSlots()));
			java.util.Set<Integer> watched = ctx.computeOnClient(mc -> new java.util.TreeSet<>(auction.watchSlots()));
			log.add("auction: cheapest slots " + cheap + ", watched " + watched + ", pings " + ctx.computeOnClient(mc -> auction.pings()));
			// listings sit in slots 0,2,4..: elytra $5.5M, elytra $4.2M, 64 diamonds $6,400 ($100 each), 32 diamonds $4,800 ($150 each), totem, carrots
			check(cheap.equals(new java.util.TreeSet<>(List.of(2, 4, 8, 10))), "AuctionHelper frames the cheapest listing of each item, per item price (" + cheap + ")");
			check(watched.equals(java.util.Set.of(2)) && ctx.computeOnClient(mc -> auction.pings()) == 1, "the watch list pings once, only for the elytra under $5M");
			shot(ctx, "23_auction_helper");
			ctx.runOnClient(mc -> mc.player.closeContainer());
			ctx.waitTicks(5);
			List<String> tip = ctx.computeOnClient(mc -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 10)
				.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL)
				.stream().map(c -> c.getString()).toList());
			log.add("diamond tooltip: " + tip);
			check(tip.stream().anyMatch(l -> l.startsWith("AH: lowest $100 each") && l.contains("stack ~$1K")), "item tooltips show the remembered auction price");
			check(Files.exists(com.wailsvlr.prism.modules.AuctionHelper.file()), "prices are saved when the auction menu closes");
			ctx.runOnClient(mc -> { auction.setEnabled(false); money.setEnabled(false); });
			server.runCommand("scoreboard objectives remove bal");

			// ---------------------------------------------------------------- base value
			com.wailsvlr.prism.modules.BaseValue baseValue = PrismClient.modules().get(com.wailsvlr.prism.modules.BaseValue.class);
			com.wailsvlr.prism.modules.BlockFinder bfv = PrismClient.modules().get(com.wailsvlr.prism.modules.BlockFinder.class);
			ctx.runOnClient(mc -> {
				// diamonds are already known from the auction test ($100 and $150 each); one more $100 makes the median $100
				auction.remember("minecraft:diamond", 100);
				auction.remember("minecraft:spawner=Skeleton Spawner", 50000);
				auction.remember("minecraft:beacon", 20000);
				auction.remember("minecraft:shulker_box", 3000);
			});
			String[] vault = {
				"fill 57 -60 -63 69 -57 -53 air",
				"setblock 60 -60 -60 shulker_box", "setblock 61 -60 -60 shulker_box", "setblock 62 -60 -60 shulker_box", "setblock 63 -60 -60 shulker_box",
				"setblock 60 -60 -58 spawner{SpawnData:{entity:{id:\"minecraft:skeleton\"}}}",
				"summon armor_stand 60.5 -59 -57.5 {CustomName:\"5x Skeleton Spawner\",CustomNameVisible:1b,Invisible:1b,Marker:1b,NoGravity:1b}",
				"setblock 62 -60 -58 diamond_block", "setblock 63 -60 -58 diamond_block", "setblock 64 -60 -58 beacon", "setblock 65 -60 -58 netherite_block",
				"setblock 66 -60 -58 stone",
				"setblock 60 -60 -56 chest[facing=south]{Items:[{Slot:0b,id:\"minecraft:diamond\",count:64}]}",
				"setblock 62 -60 -56 chest[facing=south]{Items:[{Slot:0b,id:\"minecraft:diamond\",count:64}]}",
				"setblock 64 -60 -56 chest[facing=south]{Items:[{Slot:0b,id:\"minecraft:diamond\",count:64}]}",
				"summon item 66.5 -60 -55.5 {Item:{id:\"minecraft:diamond\",count:10},PickupDelay:32767,Age:-32768}",
				"setblock 66 -60 -60 stone",
				"summon item_frame 66 -59 -60 {Facing:1b,Item:{id:\"minecraft:diamond\",count:1}}",
			};
			for (String cmd : vault) server.runCommand(cmd);
			// entities (holograms, items) only reach the client within their tracking range: stand next to the base like a player would
			server.runCommand("tp @p 62.5 -60 -53.5 180 30");
			// only BaseValue: it runs the base search itself, BlockFinder stays off (no boxes, labels or chat from it)
			ctx.runOnClient(mc -> { bfv.setEnabled(false); baseValue.setEnabled(true); });
			ctx.waitTicks(40);
			for (int i = 0; i < 60 && ctx.computeOnClient(mc -> bfv.scanPending()) > 0; i++) ctx.waitTicks(5);
			ctx.waitTicks(20);
			java.util.function.Function<net.minecraft.client.Minecraft, com.wailsvlr.prism.modules.BlockFinder.Base> vaultBase = mc -> bfv.bases().stream()
				.filter(b -> b.box().inflate(1).contains(61.5, -59.5, -59.5)).findFirst().orElse(null);
			com.wailsvlr.prism.modules.BaseValue.Value worth = ctx.computeOnClient(mc -> { var b = vaultBase.apply(mc); return b == null ? null : baseValue.valueOf(b); });
			log.add("base value: " + (worth == null ? "no base found" : worth));
			check(worth != null && Math.abs(worth.known() - 284900) < 1 && worth.unpriced() == 1,
				"BaseValue adds up a 5-stack of skeleton spawners, 4 shulkers, 2 diamond blocks (9 diamonds each), a beacon and visible diamonds: $284,900, the netherite block has no price, stone does not count");
			// open the 3 chests (64 diamonds = $6,400 each): the estimate for unopened chests learns from them
			server.runCommand("tp @p 62.5 -60 -53.5 180 30");
			ctx.waitTicks(10);
			for (int x : new int[]{60, 62, 64}) {
				// aim at the chest and right click, like a player (the use key goes through the normal game loop)
				server.runCommand("tp @p " + (x + 0.5) + " -60 -53.5 180 35");   // eyes 1.6 up: 35 degrees down lands on the chest front
				ctx.waitTicks(5);
				ctx.getInput().pressKey(o -> o.keyUse);
				ctx.waitTicks(12);
				log.add("chest " + x + ": screen " + ctx.computeOnClient(mc -> mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) + ", aiming at " + ctx.computeOnClient(mc -> String.valueOf(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh ? bh.getBlockPos() : mc.hitResult)));
				ctx.runOnClient(mc -> { if (mc.screen != null) mc.player.closeContainer(); });
				ctx.waitTicks(5);
			}
			double[] chestAvg = ctx.computeOnClient(mc -> baseValue.learned("chest"));
			String vaultLabel = ctx.computeOnClient(mc -> { var b = vaultBase.apply(mc); return b == null ? "" : baseValue.labelFor(b); });
			log.add("learned chests " + (chestAvg == null ? "none" : chestAvg[0] + " / " + chestAvg[1]) + ", label \"" + vaultLabel + "\"");
			check(chestAvg != null && chestAvg[1] == 3 && Math.abs(chestAvg[0] - 19200) < 1, "opening base chests teaches what they hold ($6,400 each)");
			check(vaultLabel.equals(" - ~$285K +~$19.2K?"), "the base label shows the value and, apart, the learned estimate for its chests");
			var looked = ctx.computeOnClient(mc -> baseValue.current());
			check(!ctx.computeOnClient(mc -> bfv.isEnabled()) && looked != null && looked.box().inflate(1).contains(61.5, -59.5, -59.5)
				&& ctx.computeOnClient(mc -> baseValue.worthText(looked)).equals("~$285K +~$19.2K?"),
				"with only BaseValue on, the base you look at is picked and its worth shown (BlockFinder stays off)");
			var vaultCheck = ctx.computeOnClient(mc -> baseValue.check(looked));
			log.add("vault check: " + vaultCheck);
			check(vaultCheck.real() && vaultCheck.verdict().startsWith("Real base") && vaultCheck.kinds() >= 2 && vaultCheck.structure().isEmpty(),
				"the real base passes the strict check (dense core, storage + utility, no natural structure)");
			var drawn = ctx.computeOnClient(mc -> baseValue.drawBox(looked, baseValue.check(looked)));
			log.add("vault: BlockFinder area " + looked.box() + ", drawn box " + drawn);
			check(drawn.getXsize() <= 14 && drawn.getZsize() <= 14 && drawn.contains(61.5, -59.5, -59.5) && drawn.contains(64.5, -59.5, -57.5),
				"the box is drawn tight around the base itself (" + Math.round(drawn.getXsize()) + "x" + Math.round(drawn.getZsize()) + "), not BlockFinder's whole area");
			check(Math.abs(ctx.computeOnClient(mc -> baseValue.valueOf(looked)).known() - 284900) < 1, "the value still counts everything in and around the base ($284,900)");
			server.runCommand("tp @p 62.5 -55 -48.5 180 35");
			ctx.waitTicks(30);
			shot(ctx, "24_base_value");
			// turn away and walk off: no base in front, the value goes away after a moment
			server.runCommand("tp @p 62.5 -60 -30.5 0 0");
			ctx.waitTicks(60);
			check(ctx.computeOnClient(mc -> baseValue.current()) == null, "looking away from the base clears it");
			// a mineshaft people mined through: 22 hoppers spread along 88 blocks of tunnel with rails, cobwebs and a chest minecart.
			// BlockFinder calls it a base (66 points, all player-made); the new check must see the mineshaft and the missing core
			server.runCommand("fill 38 -60 -23 130 -57 -17 air");
			server.runCommand("fill 40 -60 -19 128 -60 -19 rail");
			for (int x = 40; x <= 128; x += 4) server.runCommand("setblock " + x + " -60 -20 hopper");
			for (int x = 42; x <= 128; x += 9) server.runCommand("setblock " + x + " -59 -21 cobweb");
			server.runCommand("summon chest_minecart 66.5 -60 -18.5");
			ctx.waitTicks(80);
			var shaft = ctx.computeOnClient(mc -> bfv.bases().stream().filter(b -> b.box().inflate(1).contains(84.5, -59.5, -19.5)).findFirst().orElse(null));
			String shaftVerdict = ctx.computeOnClient(mc -> baseValue.rejected());
			log.add("mineshaft: " + (shaft == null ? "not a base for BlockFinder" : shaft.score() + " pts, player-made " + shaft.playerScore()) + "; BaseValue: "
				+ (ctx.computeOnClient(mc -> baseValue.current()) == null ? "" : "PICKED ") + shaftVerdict);
			check(shaft != null && shaft.playerScore() >= 30 && ctx.computeOnClient(mc -> baseValue.current()) == null
				&& shaftVerdict != null && shaftVerdict.startsWith("Not a base: mineshaft"),
				"a mineshaft full of spread-out hoppers (enough player points for the old rule) is rejected as a mineshaft");
			server.runCommand("kill @e[type=chest_minecart]");
			server.runCommand("fill 38 -60 -23 130 -57 -17 air");
			// pixel art: 196 concrete blocks, player-made but nothing anyone lives in or stores things in
			server.runCommand("fill 138 -61 -62 156 -57 -44 air");
			server.runCommand("fill 140 -61 -60 146 -61 -54 white_concrete");
			server.runCommand("fill 147 -61 -60 153 -61 -54 black_concrete");
			server.runCommand("fill 140 -61 -53 146 -61 -47 red_concrete");
			server.runCommand("fill 147 -61 -53 153 -61 -47 yellow_concrete");
			server.runCommand("tp @p 146.5 -60 -40.5 180 25");
			ctx.waitTicks(80);
			var art = ctx.computeOnClient(mc -> bfv.bases().stream().filter(b -> b.box().inflate(1).contains(146.5, -60.5, -53.5)).findFirst().orElse(null));
			var artCheck = art == null ? null : ctx.computeOnClient(mc -> baseValue.check(art));
			log.add("pixel art: " + (art == null ? "not a base for BlockFinder" : art.score() + " pts; " + artCheck));
			check(art != null && !artCheck.real() && artCheck.verdict().startsWith("Not a base: only building blocks") && ctx.computeOnClient(mc -> baseValue.current()) == null,
				"pixel art (196 concrete) is player-made but not a base: only building blocks");
			server.runCommand("fill 140 -61 -60 153 -61 -47 grass_block");
			// a lived-in house with no shulkers, right on a corner of four chunks (split four ways): plank walls, 6 signs with text, an enchanting table, anvils, a bed, item frames
			for (String cmd : new String[]{"fill 138 -60 -24 152 -55 -10 air", "fill 140 -60 -22 148 -57 -14 oak_planks hollow", "fill 141 -60 -21 147 -58 -15 air",
				"setblock 142 -60 -20 enchanting_table", "setblock 144 -60 -20 anvil", "setblock 145 -60 -20 anvil", "setblock 146 -60 -20 anvil",
				"setblock 142 -60 -16 red_bed[facing=north,part=foot]", "setblock 142 -60 -17 red_bed[facing=north,part=head]",
				"fill 143 -60 -16 147 -60 -16 white_concrete"}) server.runCommand(cmd);
			for (int i = 0; i < 6; i++) server.runCommand("setblock " + (141 + i) + " -60 -15 oak_sign[rotation=8]{front_text:{messages:[\"Storage " + i + "\",\"\",\"\",\"\"]}}");
			for (int i = 0; i < 3; i++) server.runCommand("summon item_frame " + (143 + i) + " -58 -21 {Facing:3b,Item:{id:\"minecraft:clock\",count:1}}");
			server.runCommand("tp @p 144.5 -59 -5.5 180 20");
			ctx.waitTicks(80);
			var house = ctx.computeOnClient(mc -> bfv.bases().stream().filter(b -> b.box().inflate(1).contains(144.5, -59.5, -18.5)).findFirst().orElse(null));
			var houseCheck = house == null ? null : ctx.computeOnClient(mc -> baseValue.check(house));
			log.add("house: " + (house == null ? "not a base for BlockFinder" : house.score() + " pts; " + houseCheck));
			check(house != null && houseCheck.real() && houseCheck.verdict().startsWith("Real base"),
				"a lived-in house with signs, an enchanting table, anvils and item frames is a real base without any shulker");
			server.runCommand("kill @e[type=item_frame]");
			server.runCommand("fill 138 -60 -24 152 -55 -10 air");
			server.runCommand("tp @p 62.5 -60 -30.5 0 0");
			server.runCommand("kill @e[type=armor_stand]");
			server.runCommand("kill @e[type=item]");
			server.runCommand("kill @e[type=item_frame]");
			server.runCommand("fill 57 -60 -63 69 -57 -53 air");
			ctx.runOnClient(mc -> { bfv.setEnabled(false); baseValue.setEnabled(false); });
			server.runCommand("tp @p 0.5 -60 0.5");

			// ---------------------------------------------------------------- scan cap
			// 2048 cobwebs in one chunk (like vines in a jungle): evidence-only blocks are kept at most 600 times per chunk
			server.runCommand("tp @p 0.5 -60 -12.5 0 0");
			server.runCommand("forceload add -64 -64 -49 -49");
			ctx.waitTicks(20);
			server.runCommand("fill -64 -60 -64 -49 -53 -49 cobweb");
			ctx.runOnClient(mc -> bfv.setEnabled(true));
			ctx.waitTicks(60);
			for (int i = 0; i < 60 && ctx.computeOnClient(mc -> bfv.scanPending()) > 0; i++) ctx.waitTicks(5);
			int kept = ctx.computeOnClient(mc -> bfv.foundInChunk(-4, -4));
			log.add("scan cap: " + kept + " of 2048 cobwebs kept");
			check(kept > 0 && kept <= 600, "a chunk full of one natural block cannot flood the scan (" + kept + " of 2048 kept)");
			server.runCommand("fill -64 -60 -64 -49 -53 -49 air");
			server.runCommand("forceload remove -64 -64 -49 -49");
			ctx.runOnClient(mc -> bfv.setEnabled(false));

			// ---------------------------------------------------------------- armor view
			com.wailsvlr.prism.modules.ArmorView armorView = PrismClient.modules().get(com.wailsvlr.prism.modules.ArmorView.class);
			server.runCommand("fill -3 -60 -14 4 -57 -5 air");
			server.runCommand("tp @p 0.5 -60 -12.5 0 0");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				var steve = new net.minecraft.client.player.RemotePlayer(mc.level,
					new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("Steve".getBytes(StandardCharsets.UTF_8)), "Steve"));
				steve.snapTo(0.5, -60, -6.5, 180, 0);
				var chest = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE);
				chest.setDamageValue(355);   // 237 of 592 left: 40 %
				var legs = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS);
				legs.setDamageValue(198);    // 27 of 225 left: 12 %
				steve.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
				steve.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
				steve.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, legs);
				steve.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
				steve.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
				mc.level.addEntity(steve);
				armorView.setEnabled(true);
			});
			ctx.waitTicks(10);
			List<String> worn = ctx.computeOnClient(mc -> armorView.describe());
			log.add("armor view: " + ctx.computeOnClient(mc -> armorView.target() == null ? "no target" : armorView.target().getName().getString()) + " " + worn);
			check(ctx.computeOnClient(mc -> armorView.target() != null && armorView.target().getName().getString().equals("Steve"))
				&& worn.equals(List.of("HEAD 100%", "CHEST 40%", "LEGS 12%", "FEET -")),
				"ArmorView shows the armour of the player you look at with each piece's durability");
			shot(ctx, "25_armor_view");
			server.runCommand("tp @p 0.5 -60 -12.5 180 0");   // turn away
			ctx.waitTicks(80);
			check(ctx.computeOnClient(mc -> armorView.target()) == null, "looking away clears the target after a few seconds");
			ctx.runOnClient(mc -> {
				armorView.setEnabled(false);
				for (var pl : List.copyOf(mc.level.players())) if (pl != mc.player) mc.level.removeEntity(pl.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
			});

			// ---------------------------------------------------------------- config
			ctx.runOnClient(mc -> PrismClient.config().save());
			Path cfg = FabricLoader.getInstance().getConfigDir().resolve("prismclient.json");
			try {
				String json = Files.readString(cfg, StandardCharsets.UTF_8);
				check(json.contains("\"StorageFinder\"") && json.contains("\"Fill Opacity\": 45.0") && json.contains("\"RENDER\""),
					"config file holds modules, settings and panel positions");
			} catch (IOException e) {
				check(false, "config file readable: " + e);
			}
		}
		log.add(failures == 0 ? "== ALL PASSED" : "== " + failures + " FAILED");
		try {
			Path out = FabricLoader.getInstance().getGameDir().resolve("prism-test-report.txt");
			Files.write(out, log, StandardCharsets.UTF_8);
		} catch (IOException ignored) { }
		log.forEach(l -> PrismClient.LOG.info("[test] {}", l));
		if (failures > 0) throw new AssertionError(failures + " checks failed: " + log);
	}

	private static void setMode(ClientGameTestContext ctx, StorageFinder a, SpawnerFinder b, String mode) {
		ctx.runOnClient(mc -> {
			setModeOf(a, mode);
			setModeOf(b, mode);
		});
		ctx.waitTicks(3);
	}

	@SuppressWarnings("unchecked")
	private static void setModeOf(com.wailsvlr.prism.module.Module m, String mode) {
		for (var s : m.getSettings()) if (s.getName().equals("Mode")) ((com.wailsvlr.prism.setting.Setting<String>) s).set(mode);
	}

	@SuppressWarnings("unchecked")
	private static void setNumber(com.wailsvlr.prism.module.Module m, String name, double v) {
		for (var s : m.getSettings()) if (s.getName().equals(name)) ((com.wailsvlr.prism.setting.Setting<Double>) s).set(v);
	}

	@SuppressWarnings("unchecked")
	private static void setBool(com.wailsvlr.prism.module.Module m, String name, boolean v) {
		for (var s : m.getSettings()) if (s.getName().equals(name)) ((com.wailsvlr.prism.setting.Setting<Boolean>) s).set(v);
	}

	/** Frames per second after things settle: the mean of two one-second readings. */
	private int fps(ClientGameTestContext ctx) {
		ctx.waitTicks(50);
		int a = ctx.computeOnClient(Minecraft::getFps);
		ctx.waitTicks(25);
		return (a + ctx.computeOnClient(Minecraft::getFps)) / 2;
	}

	/** Mean brightness (0..255) of the middle of a screenshot. */
	private static double brightness(Path png) {
		try {
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png.toFile());
			long sum = 0, n = 0;
			for (int y = img.getHeight() / 4; y < img.getHeight() * 3 / 4; y += 4)
				for (int x = img.getWidth() / 4; x < img.getWidth() * 3 / 4; x += 4) {
					int c = img.getRGB(x, y);
					sum += ((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114;
					n++;
				}
			return sum / 1000.0 / n;
		} catch (IOException e) {
			return -1;
		}
	}

	private void shot(ClientGameTestContext ctx, String name) {
		Path p = ctx.takeScreenshot(name);
		log.add("screenshot " + p);
	}
}
