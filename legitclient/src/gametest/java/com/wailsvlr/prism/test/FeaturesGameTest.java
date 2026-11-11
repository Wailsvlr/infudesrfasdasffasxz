package com.wailsvlr.prism.test;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.gui.Notifications;
import com.wailsvlr.prism.gui.Pointers;
import com.wailsvlr.prism.gui.PrismHud;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.modules.AutoTool;
import com.wailsvlr.prism.modules.DeathPoint;
import com.wailsvlr.prism.modules.Printer;
import com.wailsvlr.prism.modules.SpawnerFinder;
import com.wailsvlr.prism.modules.StorageFinder;
import com.wailsvlr.prism.modules.Zoom;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.Setting;
import com.wailsvlr.prism.setting.TextSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The newer features in a real client (survival, flat world): the Printer's material list, layer order and alerts,
 * the finders' alert cards and pointer, AutoTool, Zoom and DeathPoint. Run: -PprismOnly=features
 * Writes features-test-report.txt; screenshots in screenshots/.
 */
public class FeaturesGameTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();

	private void check(boolean ok, String what) { log.add((ok ? "PASS  " : "FAIL  ") + what); }

	/** Sets a module's option by its name (Bool / Number / Mode / Text). */
	private static void set(Module m, String name, Object v) {
		for (Setting<?> s : m.getSettings()) {
			if (!s.getName().equals(name)) continue;
			if (s instanceof BoolSetting b) b.set((Boolean) v);
			else if (s instanceof NumberSetting n) n.set(((Number) v).doubleValue());
			else if (s instanceof ModeSetting md) md.set((String) v);
			else if (s instanceof TextSetting t) t.set((String) v);
			return;
		}
		throw new IllegalArgumentException(m.getName() + " has no option " + name);
	}

	private static boolean cardWith(ClientGameTestContext ctx, String part) {
		return ctx.computeOnClient(mc -> Notifications.texts().stream().anyMatch(t -> t.contains(part)));
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!System.getProperty("prism.only", "").equals("features")) return;
		ctx.getInput().resizeWindow(1600, 900);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 255 true");
			server.runCommand("tp @p 41.5 -60 5.5 180 40");
			ctx.waitTicks(30);
			ctx.runOnClient(mc -> mc.options.guiScale().set(3));

			// ---------------------------------------------------------- Printer: 3x3 stone, then 3x3 planks on top
			Map<BlockPos, BlockState> want = new LinkedHashMap<>();
			for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
				want.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
				want.put(new BlockPos(x, 1, z), Blocks.OAK_PLANKS.defaultBlockState());
			}
			ctx.runOnClient(mc -> {
				try {
					Path dir = mc.gameDirectory.toPath().resolve("schematics");
					Files.createDirectories(dir);
					NbtIo.writeCompressed(PrinterGameTest.litematic(want, 3, 2, 3), dir.resolve("prism_layers.litematic"));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			server.runCommand("clear @a");
			server.runCommand("give @a stone 9");
			server.runCommand("give @a oak_planks 4");
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> {
				Printer pr = PrismClient.modules().get(Printer.class);
				set(pr, "File", "prism_layers.litematic");
				set(pr, "Origin", "40 -60 0");
				set(pr, "Range", 5.5);
				set(pr, "Delay", 1);
				set(pr, "Build Order", "Layers");
				pr.setEnabled(true);
			});
			ctx.waitTicks(3);
			List<Printer.Material> mats = ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).materials());
			check(mats.size() == 2 && mats.get(0).item() == Blocks.OAK_PLANKS.asItem() && mats.get(0).need() == 9 && mats.get(0).have() == 4
				&& mats.get(1).need() >= 8 && mats.get(1).need() == mats.get(1).have(),
				"material list: planks short first (9 needed, 4 carried), then stone, as many as carried (" + mats + ")");
			int layer0 = ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).layer());
			check(layer0 == -60, "Layers order starts at the lowest height (Y " + layer0 + ")");
			boolean early = false;
			for (int t = 0; t < 400; t += 2) {
				ctx.waitTicks(2);
				boolean bad = ctx.computeOnClient(mc -> {
					int stone = 0, planks = 0;
					for (int x = 40; x < 43; x++) for (int z = 0; z < 3; z++) {
						if (mc.level.getBlockState(new BlockPos(x, -60, z)).is(Blocks.STONE)) stone++;
						if (mc.level.getBlockState(new BlockPos(x, -59, z)).is(Blocks.OAK_PLANKS)) planks++;
					}
					return planks > 0 && stone < 9;
				});
				if (bad) early = true;
				int placed = ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).placed());
				if (placed >= 13) break;
			}
			check(!early, "no plank was placed before the stone layer was complete");
			ctx.waitTicks(10);
			int placed = ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).placed());
			check(placed == 13, "with 4 planks it gets to 13 of 18 (" + placed + ")");
			check(cardWith(ctx, "Out of Oak Planks"), "a card says the planks ran out (" + ctx.computeOnClient(mc -> Notifications.texts()) + ")");
			boolean panel = ctx.computeOnClient(mc -> PrismHud.BOUNDS.containsKey("printer"));
			check(panel, "the printer panel is in the HUD (and can be moved in Edit HUD)");
			ctx.takeScreenshot("printer_panel_short");
			server.runCommand("give @a oak_planks 5");
			for (int t = 0; t < 200; t += 5) {
				ctx.waitTicks(5);
				if (ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).placed()) == 18) break;
			}
			ctx.waitTicks(2);
			check(ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).placed()) == 18, "with more planks the build is finished");
			check(cardWith(ctx, "Finished"), "a card says the build is finished");
			ctx.takeScreenshot("printer_finished");
			ctx.runOnClient(mc -> PrismClient.modules().get(Printer.class).setEnabled(false));

			// ---------------------------------------------------------- Easy Place: nothing by itself, right click places the right block
			Map<BlockPos, BlockState> easy = new LinkedHashMap<>();
			easy.put(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
			easy.put(new BlockPos(1, 0, 0), Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X));
			easy.put(new BlockPos(2, 0, 0), Blocks.FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.FurnaceBlock.FACING, net.minecraft.core.Direction.SOUTH));
			ctx.runOnClient(mc -> {
				try {
					NbtIo.writeCompressed(PrinterGameTest.litematic(easy, 3, 1, 1), mc.gameDirectory.toPath().resolve("schematics").resolve("prism_easy.litematic"));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a hotbar.0 with stick");
			server.runCommand("give @a stone 1");
			server.runCommand("give @a oak_log 1");
			server.runCommand("give @a furnace 1");
			server.runCommand("tp @p 71.5 -60 3.5 facing 70.5 -59.5 0.5");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				mc.player.getInventory().setSelectedSlot(0);
				Printer pr = PrismClient.modules().get(Printer.class);
				set(pr, "File", "prism_easy.litematic");
				set(pr, "Origin", "70 -60 0");
				set(pr, "Easy Place", true);
				pr.setEnabled(true);
			});
			ctx.waitTicks(30);
			boolean nothing = ctx.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(70, -60, 0)).isAir()
				&& mc.level.getBlockState(new BlockPos(71, -60, 0)).isAir() && mc.level.getBlockState(new BlockPos(72, -60, 0)).isAir());
			check(nothing, "Easy Place: nothing is placed without a click");
			ctx.takeScreenshot("easy_place_aim");
			String[][] spots = {{"70.5 -59.5 0.5", "70 -60 0"}, {"71.5 -59.5 0.5", "71 -60 0"}, {"72.5 -59.5 0.5", "72 -60 0"}};
			for (String[] spot : spots) {
				server.runCommand("tp @p 71.5 -60 3.5");
				ctx.waitTicks(3);
				String[] c = spot[0].split(" ");
				double tx = Double.parseDouble(c[0]), ty = Double.parseDouble(c[1]), tz = Double.parseDouble(c[2]);
				ctx.runOnClient(mc -> {
					// look from the eyes, as a player aiming at the box would
					var eye = mc.player.getEyePosition();
					double dx = tx - eye.x, dy = ty - eye.y, dz = tz - eye.z;
					mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
					mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
				});
				ctx.waitTicks(3);
				log.add("aim " + spot[1] + " -> " + ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).aimedForTest())
					+ "  hit " + ctx.computeOnClient(mc -> String.valueOf(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b ? b.getBlockPos() : mc.hitResult)));
				ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				ctx.waitTicks(8);
				log.add("  after: " + ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).getInfo() + " / " + PrismClient.modules().get(Printer.class).reason()));
			}
			BlockState s0 = ctx.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(70, -60, 0)));
			BlockState s1 = ctx.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(71, -60, 0)));
			BlockState s2 = ctx.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(72, -60, 0)));
			check(s0.is(Blocks.STONE), "Easy Place: a right click on the first box places stone (" + s0 + ")");
			check(s1.is(Blocks.OAK_LOG) && s1.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) == net.minecraft.core.Direction.Axis.X,
				"Easy Place: the log lies along X like the schematic (" + s1 + ")");
			check(s2.is(Blocks.FURNACE) && s2.getValue(net.minecraft.world.level.block.FurnaceBlock.FACING) == net.minecraft.core.Direction.SOUTH,
				"Easy Place: the furnace faces south like the schematic (" + s2 + ")");
			ctx.takeScreenshot("easy_place_done");
			ctx.runOnClient(mc -> {
				Printer pr = PrismClient.modules().get(Printer.class);
				set(pr, "Easy Place", false);
				pr.setEnabled(false);
			});

			// ---------------------------------------------------------- finders: alert cards and the pointer
			server.runCommand("setblock 46 -60 14 spawner");
			server.runCommand("setblock 34 -60 10 shulker_box");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				SpawnerFinder sf = PrismClient.modules().get(SpawnerFinder.class);
				set(sf, "Pointer", true);
				sf.setEnabled(true);
				sf.rescan();
				StorageFinder st = PrismClient.modules().get(StorageFinder.class);
				set(st, "Shulker Alert", true);
				st.setEnabled(true);
				st.rescan();
			});
			ctx.waitTicks(5);
			check(cardWith(ctx, "spawner at 46 -60 14"), "SpawnerFinder: a card for the new spawner");
			check(cardWith(ctx, "Shulker box at 34 -60 10"), "StorageFinder: Shulker Alert card");
			Pointers.Target pt = ctx.computeOnClient(mc -> PrismClient.modules().get(SpawnerFinder.class).pointerTarget());
			check(pt != null && Math.abs(pt.pos().x - 46.5) < 0.01 && Math.abs(pt.pos().z - 14.5) < 0.01, "SpawnerFinder pointer aims at the spawner (" + pt + ")");
			ctx.waitTicks(10);
			ctx.takeScreenshot("finder_cards_pointer");
			int before = ctx.computeOnClient(mc -> Notifications.count());
			ctx.runOnClient(mc -> PrismClient.modules().get(SpawnerFinder.class).rescan());
			ctx.waitTicks(2);
			int after = ctx.computeOnClient(mc -> Notifications.count());
			check(after <= before, "a spawner already reported is not reported again");
			ctx.runOnClient(mc -> {
				PrismClient.modules().get(SpawnerFinder.class).setEnabled(false);
				PrismClient.modules().get(StorageFinder.class).setEnabled(false);
			});

			// ---------------------------------------------------------- AutoTool
			server.runCommand("setblock 50 -60 22 stone");
			server.runCommand("setblock 50 -59 22 stone");
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a hotbar.0 with stick");
			server.runCommand("item replace entity @a hotbar.4 with stone_pickaxe");
			server.runCommand("tp @p 50.5 -60 20.5 facing 50.5 -59.5 22.5");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				mc.player.getInventory().setSelectedSlot(0);
				PrismClient.modules().get(AutoTool.class).setEnabled(true);
			});
			ctx.waitTicks(3);
			ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			ctx.waitTicks(6);
			int during = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
			ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			ctx.waitTicks(10);
			int afterMining = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
			check(during == 4, "AutoTool: mining stone picks the pickaxe (slot " + during + ")");
			check(afterMining == 0, "AutoTool: back to the stick after mining (slot " + afterMining + ")");
			ctx.runOnClient(mc -> PrismClient.modules().get(AutoTool.class).setEnabled(false));

			// ---------------------------------------------------------- Zoom
			server.runCommand("tp @p 41.5 -59 8.5 180 10");
			ctx.waitTicks(10);
			ctx.getInput().holdKey(GLFW.GLFW_KEY_C);
			ctx.waitTicks(20);
			float z = ctx.computeOnClient(mc -> Zoom.factor());
			ctx.takeScreenshot("zoom");
			ctx.getInput().releaseKey(GLFW.GLFW_KEY_C);
			ctx.waitTicks(20);
			float z0 = ctx.computeOnClient(mc -> Zoom.factor());
			check(z > 3.8f && z < 4.2f, "Zoom: holding C zooms 4x (" + z + ")");
			check(Math.abs(z0 - 1f) < 0.01f, "Zoom: letting go goes back (" + z0 + ")");

			// ---------------------------------------------------------- DeathPoint
			server.runCommand("effect clear @a");
			server.runCommand("tp @p 60.5 -60 30.5");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> PrismClient.modules().get(DeathPoint.class).setEnabled(true));
			server.runCommand("kill @a");
			ctx.waitTicks(20);
			BlockPos died = ctx.computeOnClient(mc -> PrismClient.modules().get(DeathPoint.class).last());
			check(died != null && died.getX() == 60 && died.getZ() == 30, "DeathPoint remembers the spot (" + died + ")");
			check(cardWith(ctx, "Died at 60"), "DeathPoint card");
			ctx.runOnClient(mc -> { mc.player.respawn(); mc.setScreen(null); });
			ctx.waitTicks(20);
			server.runCommand("tp @p 40.5 -60 30.5 -90 20");
			ctx.waitTicks(20);
			Pointers.Target dp = ctx.computeOnClient(mc -> PrismClient.modules().get(DeathPoint.class).pointerTarget());
			check(dp != null, "after respawning the pointer leads back");
			ctx.takeScreenshot("deathpoint_pointer");
			server.runCommand("tp @p 60.5 -60 30.5");
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> PrismClient.modules().get(DeathPoint.class).last()) == null, "reaching the spot clears it");
			// ---------------------------------------------------------- setup guide, with a real farm schematic (when it is on this PC)
			Path farm = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "ModrinthApp", "profiles", "C3rvette_s Modpack 1.0.0 (1)",
				"schematics", "McDs 240 Smokers Kelp Farm (Without Unloader).litematic");
			if (Files.exists(farm)) {
				server.runCommand("gamemode creative @a");
				server.runCommand("tp @p 230.5 -59 214.5 90 30");
				ctx.waitTicks(40);
				ctx.runOnClient(mc -> {
					try {
						Files.copy(farm, mc.gameDirectory.toPath().resolve("schematics").resolve("guide_farm.litematic"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					Printer pr = PrismClient.modules().get(Printer.class);
					set(pr, "File", "guide_farm.litematic");
					set(pr, "Origin", "200 -60 200");
					set(pr, "Easy Place", true);   // nothing is built by itself: only the guide is looked at
					set(pr, "Setup Guide", true);
					pr.setEnabled(true);
				});
				ctx.waitTicks(60);
				List<String> rows = ctx.computeOnClient(mc -> {
					var gd = PrismClient.modules().get(Printer.class).guide();
					return gd == null ? List.<String>of() : gd.rows().stream().map(r -> r[1]).toList();
				});
				log.add("guide rows: " + rows);
				check(rows.stream().anyMatch(r -> r.startsWith("Crafters set: 0/21")), "guide: 21 crafters to set");
				check(rows.stream().anyMatch(r -> r.startsWith("Place by hand: 0/17")), "guide: 17 entities to place (minecarts, boats, frames)");
				check(rows.stream().anyMatch(r -> r.startsWith("Fill by hand:") && r.contains("water") && r.contains("kelp")), "guide: water and kelp to add by hand");
				// a crafter of the schematic, set up right on the server, then opened: the guide checks it off
				BlockPos crafter = ctx.computeOnClient(mc -> {
					var gd = PrismClient.modules().get(Printer.class).guide();
					return gd.crafters().entrySet().stream().filter(e -> e.getValue().size() == 8 && !e.getValue().contains(4)).map(Map.Entry::getKey).findFirst().orElse(null);
				});
				check(crafter != null, "guide: found a crafter that keeps only the middle slot open (" + crafter + ")");
				if (crafter != null) {
					server.runCommand("setblock " + crafter.getX() + " " + crafter.getY() + " " + crafter.getZ() + " crafter{disabled_slots:[I;0,1,2,3,5,6,7,8]}");
					server.runCommand("setblock " + (crafter.getX() + 1) + " " + (crafter.getY() - 1) + " " + crafter.getZ() + " stone");
					// a small floor two blocks in front of the crafter to stand on
					server.runCommand("fill " + (crafter.getX() - 1) + " " + (crafter.getY() - 1) + " " + (crafter.getZ() + 2) + " " + (crafter.getX() + 1) + " "
						+ (crafter.getY() - 1) + " " + (crafter.getZ() + 3) + " stone");
					server.runCommand("tp @p " + (crafter.getX() + 0.5) + " " + crafter.getY() + " " + (crafter.getZ() + 2.5) + " 180 20");
					ctx.waitTicks(10);
					ctx.runOnClient(mc -> {
						var eye = mc.player.getEyePosition();
						double dx = crafter.getX() + 0.5 - eye.x, dy = crafter.getY() + 0.5 - eye.y, dz = crafter.getZ() + 0.5 - eye.z;
						mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
						mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
					});
					ctx.waitTicks(5);
					ctx.takeScreenshot("guide_crafter_tip");
					// first set wrong (top-left locked, middle locked): the guide frames those slots
					server.runCommand("setblock " + crafter.getX() + " " + crafter.getY() + " " + crafter.getZ() + " air");
					server.runCommand("setblock " + crafter.getX() + " " + crafter.getY() + " " + crafter.getZ() + " crafter{disabled_slots:[I;0,4]}");
					ctx.waitTicks(5);
					ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
					ctx.waitTicks(15);
					boolean wrongOpen = ctx.computeOnClient(mc -> mc.screen instanceof net.minecraft.client.gui.screens.inventory.CrafterScreen);
					boolean wrongOk = ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).guide().crafterOk(crafter));
					check(wrongOpen && !wrongOk, "guide: a crafter set wrong is not checked off");
					ctx.takeScreenshot("guide_crafter_wrong");
					// "Set slots" clicks the slots like a player would; the server keeps the result
					int[] btn = ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).guide0().applyButtonForTest());
					check(btn != null, "guide: the crafter screen shows a Set slots button");
					if (btn != null) {
						double gs = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
						ctx.getInput().setCursorPos(btn[0] * gs, btn[1] * gs);
						ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
					}
					ctx.waitTicks(40);
					ctx.takeScreenshot("guide_crafter_applied");
					boolean applied = ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).guide().crafterOk(crafter));
					check(applied, "guide: Set slots sets the crafter like the schematic");
					ctx.runOnClient(mc -> mc.setScreen(null));
					ctx.waitTicks(10);
					String serverSlots = server.computeOnServer(sv -> {
						var be = sv.overworld().getBlockEntity(crafter);
						if (!(be instanceof net.minecraft.world.level.block.entity.CrafterBlockEntity c)) return "no crafter";
						StringBuilder sb = new StringBuilder();
						for (int k = 0; k < 9; k++) sb.append(c.isSlotDisabled(k) ? 'x' : 'o');
						return sb.toString();
					});
					check(serverSlots.equals("xxxxoxxxx"), "guide: the server has the crafter set too (" + serverSlots + ")");
					// opened again: checked off
					ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
					ctx.waitTicks(15);
					boolean open = ctx.computeOnClient(mc -> mc.screen instanceof net.minecraft.client.gui.screens.inventory.CrafterScreen);
					check(open, "the crafter opened");
					ctx.takeScreenshot("guide_crafter_screen");
					boolean ok = ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).guide().crafterOk(crafter));
					check(ok, "guide: a crafter set like the schematic is checked off when opened");
					ctx.runOnClient(mc -> mc.setScreen(null));
					ctx.waitTicks(5);
				}
				// a hopper minecart where the schematic has one: placed
				var spot = ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).guide().spots().stream()
					.filter(spt -> spt.type() == net.minecraft.world.entity.EntityType.HOPPER_MINECART).findFirst().orElse(null));
				if (spot != null) {
					server.runCommand(String.format(java.util.Locale.ROOT, "summon hopper_minecart %.2f %.2f %.2f {NoGravity:1b}", spot.pos().x, spot.pos().y, spot.pos().z));
					ctx.waitTicks(30);
					int missing = ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).guide().spotsMissing().size());
					check(missing == 16, "guide: a hopper minecart in its place is checked off (" + missing + " still to place)");
				}
				server.runCommand("tp @p 230.5 -50 214.5 90 25");
				ctx.waitTicks(30);
				ctx.takeScreenshot("guide_world");
				ctx.runOnClient(mc -> {
					Printer pr = PrismClient.modules().get(Printer.class);
					set(pr, "Easy Place", false);
					pr.setEnabled(false);
				});
			} else log.add("(setup guide test skipped: the farm schematic is not on this PC)");
		} catch (RuntimeException e) {
			log.add("FAIL  exception: " + e);
			throw e;
		} finally {
			try { Files.write(Path.of("features-test-report.txt"), log); } catch (Exception ignored) { }
		}
	}
}
