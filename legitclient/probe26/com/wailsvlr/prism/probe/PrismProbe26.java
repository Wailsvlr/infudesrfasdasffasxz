package com.wailsvlr.prism.probe;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.gui.ClickGuiScreen;
import com.wailsvlr.prism.modules.ChunkFinder;
import com.wailsvlr.prism.modules.Freecam;
import com.wailsvlr.prism.modules.Fullbright;
import com.wailsvlr.prism.modules.HudModule;
import com.wailsvlr.prism.modules.OreFinder;
import com.wailsvlr.prism.modules.StorageFinder;
import com.wailsvlr.prism.render.EspRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Test driver for the Prism Client 26.1.2 build, in a real 26.1.2 client: creates a fresh world, places test blocks,
 * switches modules on and checks what they find, exercises Freecam (camera mixin), Fullbright (lightmap mixin) and
 * the ClickGUI, takes screenshots, writes prism26-report.txt and closes the game. Not part of Prism itself.
 */
public class PrismProbe26 implements ClientModInitializer {
	private final List<String> report = new ArrayList<>();
	private int pass, fail;
	private int tick, phaseTick;
	private String phase = "title";
	private Vec3 cameraBefore, playerBefore;
	private net.minecraft.core.BlockPos goldAt;
	/** The player's block position when the scene was built; the later phases place things relative to it. */
	private int bx, by, bz;
	private boolean plankBeforeStone;
	private float fovNormal;
	private net.minecraft.core.BlockPos diedAt;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void check(boolean ok, String what) {
		if (ok) pass++;
		else fail++;
		report.add((ok ? "PASS  " : "FAIL  ") + what);
	}

	private void cmd(Minecraft mc, String c) {
		MinecraftServer s = mc.getSingleplayerServer();
		if (s != null) s.execute(() -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), c));
	}

	private void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1, msg -> { });
		report.add("screenshot " + name);
	}

	private void go(String next) {
		phase = next;
		phaseTick = 0;
	}

	private void tick(Minecraft mc) {
		tick++;
		phaseTick++;
		try {
			switch (phase) {
				case "title" -> {
					if (mc.screen instanceof TitleScreen && phaseTick > 40) {
						CreateWorldScreen.openFresh(mc, () -> mc.setScreen(new TitleScreen()));
						go("create");
					}
				}
				case "create" -> {
					if (mc.screen instanceof CreateWorldScreen cws && phaseTick > 40) {
						var m = CreateWorldScreen.class.getDeclaredMethod("onCreate");
						m.setAccessible(true);
						m.invoke(cws);
						go("loading");
					}
				}
				case "loading" -> {
					if (mc.level != null && mc.player != null && mc.screen == null && phaseTick > 20) {
						report.add("joined a fresh 26.1.2 world (" + mc.level.dimension().identifier() + ")");
						go("scene");
					}
					if (phaseTick > 20 * 120) throw new IllegalStateException("world did not load");
				}
				case "scene" -> {
					if (phaseTick == 40) {
						cmd(mc, "gamemode creative @a");
						cmd(mc, "time set noon");
						cmd(mc, "weather clear");
						cmd(mc, "effect give @a speed 600 1");
						cmd(mc, "effect give @a strength 300");
						cmd(mc, "item replace entity @a armor.head with diamond_helmet[damage=200]");
						cmd(mc, "item replace entity @a armor.chest with netherite_chestplate");
						cmd(mc, "item replace entity @a weapon.mainhand with diamond_sword[damage=900]");
						int x = mc.player.getBlockX(), y = mc.player.getBlockY(), z = mc.player.getBlockZ();
						bx = x; by = y; bz = z;
						cmd(mc, "fill " + (x - 6) + " " + (y + 6) + " " + (z + 2) + " " + (x + 10) + " " + (y + 14) + " " + (z + 14) + " air");
						cmd(mc, "fill " + (x - 6) + " " + (y + 5) + " " + (z + 2) + " " + (x + 10) + " " + (y + 5) + " " + (z + 14) + " stone");
						cmd(mc, "setblock " + (x - 2) + " " + (y + 6) + " " + (z + 6) + " chest");
						cmd(mc, "setblock " + (x + 2) + " " + (y + 6) + " " + (z + 6) + " chest");
						cmd(mc, "setblock " + x + " " + (y + 6) + " " + (z + 9) + " diamond_ore");
						cmd(mc, "setblock " + (x + 1) + " " + (y + 6) + " " + (z + 9) + " gold_ore");
						goldAt = new net.minecraft.core.BlockPos(x + 1, y + 6, z + 9);
						// a geode that kept growing: 6 budding blocks with a cluster on each
						for (int i = 0; i < 6; i++) {
							cmd(mc, "setblock " + (x + 4 + i) + " " + (y + 6) + " " + (z + 12) + " budding_amethyst");
							cmd(mc, "setblock " + (x + 4 + i) + " " + (y + 7) + " " + (z + 12) + " amethyst_cluster[facing=up]");
						}
						cmd(mc, "tp @p " + (x + 0.5) + " " + (y + 6) + " " + (z + 3.5) + " 0 35");
					}
					if (phaseTick == 80) {
						for (var mod : new Class<?>[] {StorageFinder.class, OreFinder.class, ChunkFinder.class, HudModule.class}) {
							PrismClient.modules().get(mod.asSubclass(com.wailsvlr.prism.module.Module.class)).setEnabled(true);
						}
					}
					if (phaseTick == 240) {
						StorageFinder sf = PrismClient.modules().get(StorageFinder.class);
						OreFinder of = PrismClient.modules().get(OreFinder.class);
						ChunkFinder cf = PrismClient.modules().get(ChunkFinder.class);
						int chests = sf.counts().getOrDefault(StorageFinder.Kind.CHEST, 0);
						int diamonds = of.counts().getOrDefault(OreFinder.Ore.DIAMOND, 0), gold = of.counts().getOrDefault(OreFinder.Ore.GOLD, 0);
						report.add("block at the gold position: " + mc.level.getBlockState(goldAt));
						report.add("found: " + chests + " chests, " + diamonds + " diamond, " + gold + " gold, " + cf.active().size() + " active chunks, "
							+ EspRenderer.lastDrawn + " highlights drawn last frame");
						check(chests >= 2, "StorageFinder finds the 2 chests");
						check(diamonds >= 1 && gold >= 1, "OreFinder finds the diamond and gold ore");
						check(cf.active().stream().anyMatch(a -> a.sign() == ChunkFinder.Sign.AMETHYST), "ChunkFinder marks the chunk with the grown geode");
						check(EspRenderer.lastDrawn > 0, "highlights are drawn (level render event + pipelines work in 26.1.2)");
						check(com.wailsvlr.prism.gui.PrismHud.BOUNDS.containsKey("potions") && com.wailsvlr.prism.gui.PrismHud.BOUNDS.containsKey("armor"),
							"the Cards HUD draws the effects and armour cards (" + com.wailsvlr.prism.gui.PrismHud.BOUNDS.keySet() + ")");
						shot(mc, "prism26_1_esp_and_hud");
						go("freecam");
					}
				}
				case "freecam" -> {
					if (phaseTick == 1) {
						cameraBefore = mc.gameRenderer.getMainCamera().position();
						playerBefore = mc.player.position();
						PrismClient.modules().get(Freecam.class).setEnabled(true);
					}
					if (phaseTick == 5) mc.options.keyUp.setDown(true);
					if (phaseTick == 40) mc.options.keyUp.setDown(false);
					if (phaseTick == 50) {
						Vec3 now = mc.gameRenderer.getMainCamera().position();
						double moved = now.distanceTo(cameraBefore), player = mc.player.position().distanceTo(playerBefore);
						report.add(String.format(java.util.Locale.ROOT, "freecam: camera moved %.2f blocks, player moved %.2f", moved, player));
						check(Freecam.active() != null && moved > 1.0 && player < 0.5, "Freecam moves the camera and leaves the player in place (camera mixin)");
						shot(mc, "prism26_2_freecam");
						PrismClient.modules().get(Freecam.class).setEnabled(false);
						go("fullbright");
					}
				}
				case "fullbright" -> {
					if (phaseTick == 1) {
						cmd(mc, "time set midnight");
						PrismClient.modules().get(Fullbright.class).setEnabled(true);
					}
					if (phaseTick == 60) {
						check(PrismClient.modules().get(Fullbright.class).amount() > 0, "Fullbright is on (lightmap mixin applied without errors)");
						shot(mc, "prism26_3_fullbright_night");
						PrismClient.modules().get(Fullbright.class).setEnabled(false);
						cmd(mc, "time set noon");
						go("printer");
					}
				}
				case "printer" -> {
					if (phaseTick == 1) {
						// 3x3 stone with 3x3 planks on top, built in Layers order
						java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> want = new java.util.LinkedHashMap<>();
						for (int a = 0; a < 3; a++) for (int c = 0; c < 3; c++) {
							want.put(new net.minecraft.core.BlockPos(a, 0, c), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
							want.put(new net.minecraft.core.BlockPos(a, 1, c), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
						}
						Path dir = mc.gameDirectory.toPath().resolve("schematics");
						Files.createDirectories(dir);
						net.minecraft.nbt.NbtIo.writeCompressed(litematic(want, 3, 2, 3), dir.resolve("probe_layers.litematic"));
						cmd(mc, "tp @p " + (bx + 6.5) + " " + (by + 6) + " " + (bz + 6.5) + " 180 45");
					}
					if (phaseTick == 10) {
						com.wailsvlr.prism.modules.Printer pr = PrismClient.modules().get(com.wailsvlr.prism.modules.Printer.class);
						set(pr, "File", "probe_layers.litematic");
						set(pr, "Origin", (bx + 5) + " " + (by + 6) + " " + (bz + 2));
						set(pr, "Range", 5.5);
						set(pr, "Delay", 1.0);
						set(pr, "Build Order", "Layers");
						pr.setEnabled(true);
					}
					if (phaseTick == 13) {
						var mats = PrismClient.modules().get(com.wailsvlr.prism.modules.Printer.class).materials();
						report.add("printer materials: " + mats);
						check(mats.size() == 2, "Printer lists the two materials still needed");
					}
					if (phaseTick > 13 && phaseTick < 400) {
						int stone = 0, planks = 0;
						for (int a = 0; a < 3; a++) for (int c = 0; c < 3; c++) {
							if (mc.level.getBlockState(new net.minecraft.core.BlockPos(bx + 5 + a, by + 6, bz + 2 + c)).is(net.minecraft.world.level.block.Blocks.STONE)) stone++;
							if (mc.level.getBlockState(new net.minecraft.core.BlockPos(bx + 5 + a, by + 7, bz + 2 + c)).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS)) planks++;
						}
						if (planks > 0 && stone < 9) plankBeforeStone = true;
						if (phaseTick == 30) shot(mc, "prism26_5_printer_panel");
						if (PrismClient.modules().get(com.wailsvlr.prism.modules.Printer.class).placed() == 18 && phaseTick > 30) phaseTick = 399;
					}
					if (phaseTick == 400) {
						var pr = PrismClient.modules().get(com.wailsvlr.prism.modules.Printer.class);
						check(pr.placed() == 18, "Printer builds the whole schematic (" + pr.placed() + "/18)");
						check(!plankBeforeStone, "Layers order: no plank before the stone layer was done");
						check(com.wailsvlr.prism.gui.PrismHud.BOUNDS.containsKey("printer"), "the printer panel is drawn in the HUD");
						check(cards("Finished"), "the 'Finished' card appears");
						pr.setEnabled(false);
						go("easy");
					}
				}
				case "easy" -> {
					// Easy Place: a furnace facing south, placed only when you right-click its box
					if (phaseTick == 1) {
						java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> want = new java.util.LinkedHashMap<>();
						want.put(new net.minecraft.core.BlockPos(0, 0, 0), net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState()
							.setValue(net.minecraft.world.level.block.FurnaceBlock.FACING, net.minecraft.core.Direction.SOUTH));
						net.minecraft.nbt.NbtIo.writeCompressed(litematic(want, 1, 1, 1), mc.gameDirectory.toPath().resolve("schematics").resolve("probe_easy.litematic"));
						cmd(mc, "tp @p " + (bx + 2.5) + " " + (by + 6) + " " + (bz + 8.5));
					}
					if (phaseTick == 10) {
						var pr = PrismClient.modules().get(com.wailsvlr.prism.modules.Printer.class);
						set(pr, "File", "probe_easy.litematic");
						set(pr, "Origin", (bx + 2) + " " + (by + 6) + " " + (bz + 11));
						set(pr, "Easy Place", true);
						pr.setEnabled(true);
					}
					if (phaseTick == 40) {
						check(mc.level.getBlockState(new net.minecraft.core.BlockPos(bx + 2, by + 6, bz + 11)).isAir(), "Easy Place: nothing without a click");
						var eye = mc.player.getEyePosition();
						double dx = bx + 2.5 - eye.x, dy = by + 6.5 - eye.y, dz = bz + 11.5 - eye.z;
						mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
						mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
					}
					if (phaseTick == 44) mc.options.keyUse.setDown(true);
					if (phaseTick == 47) mc.options.keyUse.setDown(false);
					if (phaseTick == 60) {
						var st = mc.level.getBlockState(new net.minecraft.core.BlockPos(bx + 2, by + 6, bz + 11));
						check(st.is(net.minecraft.world.level.block.Blocks.FURNACE)
							&& st.getValue(net.minecraft.world.level.block.FurnaceBlock.FACING) == net.minecraft.core.Direction.SOUTH,
							"Easy Place: a right click puts the furnace there, facing south (" + st + ")");
						var pr = PrismClient.modules().get(com.wailsvlr.prism.modules.Printer.class);
						set(pr, "Easy Place", false);
						pr.setEnabled(false);
						go("guide");
					}
				}
				case "guide" -> {
					// the setup guide: a crafter of the schematic with only the middle slot open, opened (CrafterScreen mixin)
					var crafter = new net.minecraft.core.BlockPos(bx + 2, by + 6, bz + 11);
					if (phaseTick == 1) {
						java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> want = new java.util.LinkedHashMap<>();
						want.put(new net.minecraft.core.BlockPos(0, 0, 0), net.minecraft.world.level.block.Blocks.CRAFTER.defaultBlockState());
						var root = litematic(want, 1, 1, 1);
						var te = new net.minecraft.nbt.CompoundTag();
						te.putInt("x", 0); te.putInt("y", 0); te.putInt("z", 0);
						te.putString("id", "minecraft:crafter");
						te.putIntArray("disabled_slots", new int[]{0, 1, 2, 3, 5, 6, 7, 8});
						var tes = new net.minecraft.nbt.ListTag();
						tes.add(te);
						root.getCompoundOrEmpty("Regions").getCompoundOrEmpty("test").put("TileEntities", tes);
						net.minecraft.nbt.NbtIo.writeCompressed(root, mc.gameDirectory.toPath().resolve("schematics").resolve("probe_guide.litematic"));
						cmd(mc, "setblock " + crafter.getX() + " " + crafter.getY() + " " + crafter.getZ() + " air");
					}
					// set wrong on purpose: the "Set slots" button fixes it
					if (phaseTick == 3) cmd(mc, "setblock " + crafter.getX() + " " + crafter.getY() + " " + crafter.getZ() + " crafter{disabled_slots:[I;0,4]}");
					if (phaseTick == 10) {
						var pr = PrismClient.modules().get(com.wailsvlr.prism.modules.Printer.class);
						set(pr, "File", "probe_guide.litematic");
						set(pr, "Origin", crafter.getX() + " " + crafter.getY() + " " + crafter.getZ());
						pr.setEnabled(true);
					}
					if (phaseTick == 30) {
						var gd = PrismClient.modules().get(com.wailsvlr.prism.modules.Printer.class).guide();
						check(gd != null && gd.crafters().size() == 1, "Setup guide reads the crafter's locked slots from the schematic");
						var eye = mc.player.getEyePosition();
						double dx = crafter.getX() + 0.5 - eye.x, dy = crafter.getY() + 0.5 - eye.y, dz = crafter.getZ() + 0.5 - eye.z;
						mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
						mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
					}
					if (phaseTick == 34) mc.options.keyUse.setDown(true);
					if (phaseTick == 36) mc.options.keyUse.setDown(false);
					if (phaseTick == 50) {
						check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CrafterScreen, "the crafter opens with a right click");
						var g0 = PrismClient.modules().get(com.wailsvlr.prism.modules.Printer.class).guide0();
						check(g0.applyButtonForTest() != null, "the crafter screen shows the Set slots button");
						shot(mc, "prism26_6a_guide_crafter_wrong");
						g0.applyForTest();
					}
					if (phaseTick == 75) shot(mc, "prism26_6b_guide_crafter");
					if (phaseTick == 80) {
						var gd = PrismClient.modules().get(com.wailsvlr.prism.modules.Printer.class).guide();
						check(gd != null && gd.crafterOk(crafter), "Set slots sets the crafter like the schematic (CrafterScreen mixin)");
						mc.setScreen(null);
						PrismClient.modules().get(com.wailsvlr.prism.modules.Printer.class).setEnabled(false);
						go("finders");
					}
				}
				case "finders" -> {
					if (phaseTick == 1) {
						cmd(mc, "setblock " + (bx + 8) + " " + (by + 6) + " " + (bz + 12) + " spawner");
						cmd(mc, "setblock " + (bx - 4) + " " + (by + 6) + " " + (bz + 10) + " shulker_box");
						cmd(mc, "tp @p " + (bx + 0.5) + " " + (by + 6) + " " + (bz + 3.5) + " 0 25");
					}
					if (phaseTick == 15) {
						com.wailsvlr.prism.modules.SpawnerFinder sp = PrismClient.modules().get(com.wailsvlr.prism.modules.SpawnerFinder.class);
						set(sp, "Pointer", true);
						sp.setEnabled(true);
						sp.rescan();
						StorageFinder sf = PrismClient.modules().get(StorageFinder.class);
						set(sf, "Shulker Alert", true);
						set(sf, "Labels", true);
						sf.rescan();
					}
					if (phaseTick == 35) {
						check(cards("spawner at " + (bx + 8)), "SpawnerFinder alert card");
						check(cards("Shulker box at " + (bx - 4)), "StorageFinder Shulker Alert card");
						check(PrismClient.modules().get(com.wailsvlr.prism.modules.SpawnerFinder.class).pointerTarget() != null, "SpawnerFinder pointer has a target");
						shot(mc, "prism26_6_finder_labels_cards_pointer");
						go("autotool");
					}
				}
				case "autotool" -> {
					if (phaseTick == 1) {
						cmd(mc, "gamemode survival @a");
						cmd(mc, "effect give @a resistance infinite 255 true");
						cmd(mc, "clear @a");
						cmd(mc, "item replace entity @a hotbar.0 with stick");
						cmd(mc, "item replace entity @a hotbar.4 with stone_pickaxe");
						cmd(mc, "setblock " + bx + " " + (by + 6) + " " + (bz + 13) + " stone");
						cmd(mc, "setblock " + bx + " " + (by + 7) + " " + (bz + 13) + " stone");
						cmd(mc, "tp @p " + (bx + 0.5) + " " + (by + 6) + " " + (bz + 11.5) + " facing " + (bx + 0.5) + " " + (by + 6.5) + " " + (bz + 13.5));
					}
					if (phaseTick == 15) {
						mc.player.getInventory().setSelectedSlot(0);
						PrismClient.modules().get(com.wailsvlr.prism.modules.AutoTool.class).setEnabled(true);
					}
					if (phaseTick == 18) mc.options.keyAttack.setDown(true);
					if (phaseTick == 24) {
						int slot = mc.player.getInventory().getSelectedSlot();
						check(slot == 4, "AutoTool picks the pickaxe for stone (slot " + slot + ")");
						mc.options.keyAttack.setDown(false);
					}
					if (phaseTick == 34) {
						int slot = mc.player.getInventory().getSelectedSlot();
						check(slot == 0, "AutoTool goes back to the stick (slot " + slot + ")");
						PrismClient.modules().get(com.wailsvlr.prism.modules.AutoTool.class).setEnabled(false);
						go("zoom");
					}
				}
				case "zoom" -> {
					if (phaseTick == 5) fovNormal = mc.gameRenderer.getMainCamera().getFov();
					if (phaseTick == 6) PrismClient.zoomKey().setDown(true);
					if (phaseTick == 30) {
						float fov = mc.gameRenderer.getMainCamera().getFov();
						report.add("fov " + fovNormal + " -> " + fov + " while zooming");
						check(fov > 0 && fovNormal / fov > 3.5f, "Zoom narrows the camera's field of view about 4x (camera mixin)");
						shot(mc, "prism26_7_zoom");
						PrismClient.zoomKey().setDown(false);
					}
					if (phaseTick == 50) {
						float fov = mc.gameRenderer.getMainCamera().getFov();
						check(Math.abs(fov - fovNormal) < 0.5f, "letting go of Zoom gives the normal view back (" + fov + ")");
						go("death");
					}
				}
				case "death" -> {
					if (phaseTick == 1) {
						cmd(mc, "effect clear @a");
						cmd(mc, "tp @p " + (bx + 6.5) + " " + (by + 6) + " " + (bz + 12.5));
						PrismClient.modules().get(com.wailsvlr.prism.modules.DeathPoint.class).setEnabled(true);
					}
					if (phaseTick == 10) { diedAt = mc.player.blockPosition(); cmd(mc, "kill @a"); }
					if (phaseTick == 30) {
						var at = PrismClient.modules().get(com.wailsvlr.prism.modules.DeathPoint.class).last();
						check(at != null && at.equals(diedAt), "DeathPoint remembers where you died (" + at + ", stood at " + diedAt + ")");
						mc.player.respawn();
						mc.setScreen(null);
					}
					if (phaseTick == 60) {
						cmd(mc, "gamemode creative @a");
						cmd(mc, "tp @p " + (bx - 3.5) + " " + (by + 6) + " " + (bz + 4.5) + " -45 20");
					}
					if (phaseTick == 80) {
						check(PrismClient.modules().get(com.wailsvlr.prism.modules.DeathPoint.class).pointerTarget() != null, "DeathPoint points back after respawning");
						shot(mc, "prism26_8_deathpoint");
						go("gui");
					}
				}
				case "gui" -> {
					if (phaseTick == 1) mc.setScreen(new ClickGuiScreen());
					if (phaseTick == 5 && mc.screen instanceof ClickGuiScreen gui) gui.expandForTest(PrismClient.modules().get(StorageFinder.class), null);
					if (phaseTick == 40) {
						check(mc.screen instanceof ClickGuiScreen, "the ClickGUI opens");
						shot(mc, "prism26_4_clickgui");
					}
					if (phaseTick == 50) {
						mc.setScreen(null);
						go("done");
					}
				}
				case "done" -> {
					if (phaseTick == 20) {
						finish(mc);
					}
				}
				default -> { }
			}
		} catch (Throwable t) {
			check(false, "the probe stopped in phase " + phase + ": " + t);
			finish(mc);
		}
	}

	private boolean cards(String part) {
		return com.wailsvlr.prism.gui.Notifications.texts().stream().anyMatch(t -> t.contains(part));
	}

	private static void set(com.wailsvlr.prism.module.Module m, String name, Object v) {
		for (com.wailsvlr.prism.setting.Setting<?> st : m.getSettings()) {
			if (!st.getName().equals(name)) continue;
			if (st instanceof com.wailsvlr.prism.setting.BoolSetting b) b.set((Boolean) v);
			else if (st instanceof com.wailsvlr.prism.setting.NumberSetting n) n.set(((Number) v).doubleValue());
			else if (st instanceof com.wailsvlr.prism.setting.ModeSetting md) md.set((String) v);
			else if (st instanceof com.wailsvlr.prism.setting.TextSetting t) t.set((String) v);
			return;
		}
		throw new IllegalArgumentException(m.getName() + " has no option " + name);
	}

	/** A one-region Litematica file (the same as the 1.21.11 tests' helper). */
	static net.minecraft.nbt.CompoundTag litematic(java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> blocks, int sx, int sy, int sz) {
		List<net.minecraft.world.level.block.state.BlockState> palette = new ArrayList<>();
		palette.add(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		for (var st : blocks.values()) if (!palette.contains(st)) palette.add(st);
		int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
		long volume = (long) sx * sy * sz;
		long[] data = new long[(int) ((volume * bits + 63) / 64)];
		for (long i = 0; i < volume; i++) {
			int x = (int) (i % sx), z = (int) ((i / sx) % sz), y = (int) (i / ((long) sx * sz));
			long v = palette.indexOf(blocks.getOrDefault(new net.minecraft.core.BlockPos(x, y, z), palette.get(0)));
			long start = i * bits;
			int si = (int) (start >> 6), ei = (int) (((i + 1) * bits - 1) >> 6), off = (int) (start & 63);
			data[si] |= v << off;
			if (si != ei) data[ei] |= v >>> (64 - off);
		}
		var region = new net.minecraft.nbt.CompoundTag();
		var pos = new net.minecraft.nbt.CompoundTag();
		pos.putInt("x", 0); pos.putInt("y", 0); pos.putInt("z", 0);
		var size = new net.minecraft.nbt.CompoundTag();
		size.putInt("x", sx); size.putInt("y", sy); size.putInt("z", sz);
		region.put("Position", pos);
		region.put("Size", size);
		var pal = new net.minecraft.nbt.ListTag();
		for (var st : palette) pal.add(net.minecraft.nbt.NbtUtils.writeBlockState(st));
		region.put("BlockStatePalette", pal);
		region.put("BlockStates", new net.minecraft.nbt.LongArrayTag(data));
		var regions = new net.minecraft.nbt.CompoundTag();
		regions.put("test", region);
		var root = new net.minecraft.nbt.CompoundTag();
		root.putInt("Version", 6);
		root.put("Regions", regions);
		return root;
	}

	private void finish(Minecraft mc) {
		report.add(0, "Prism Client 26.1.2 probe: " + pass + " passed, " + fail + " failed");
		try {
			Files.write(Path.of(mc.gameDirectory.getPath(), "prism26-report.txt"), report, StandardCharsets.UTF_8);
		} catch (IOException ignored) { }
		phase = "closing";
		mc.stop();
	}
}
