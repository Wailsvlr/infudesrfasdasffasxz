package com.wailsvlr.prism.test;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.modules.Printer;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.Setting;
import com.wailsvlr.prism.setting.TextSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Printer on a real Paper server with GrimAC (verity-dev/printer-grim-server, port 25610), in survival with
 * items from the inventory, Legit Aim + Look At Block on: hoppers into chests, a hopper chain, a double chest, two
 * single chests side by side, a furnace fed from the top and the side, and plain stone rows to measure speed.
 * Records the time, blocks per second, every Grim alert and refused placement, and checks every block.
 * Run: gradlew runClientGameTest -PprismOnly=printerserver
 */
public class PrinterServerGameTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();
	private final List<String> chat = Collections.synchronizedList(new ArrayList<>());
	private int fails;

	private void check(boolean ok, String what) {
		log.add((ok ? "PASS  " : "FAIL  ") + what);
		if (!ok) fails++;
	}

	static final String[][] PARTS = {
		// a double chest fed by two hoppers from above, and a hopper chain into the right hopper
		{"0 0 0", "minecraft:chest[facing=south,type=right]"}, {"1 0 0", "minecraft:chest[facing=south,type=left]"},
		{"0 1 0", "minecraft:hopper[facing=down]"}, {"1 1 0", "minecraft:hopper[facing=down]"},
		{"2 1 0", "minecraft:hopper[facing=west]"}, {"3 1 0", "minecraft:hopper[facing=west]"}, {"2 0 0", "minecraft:stone"}, {"3 0 0", "minecraft:stone"},
		// two single chests side by side (they must not join)
		{"5 0 0", "minecraft:chest[facing=south,type=single]"}, {"6 0 0", "minecraft:chest[facing=south,type=single]"},
		// a furnace fed from the top and from the side
		{"0 0 2", "minecraft:furnace[facing=south]"}, {"0 1 2", "minecraft:hopper[facing=down]"}, {"1 0 2", "minecraft:hopper[facing=west]"},
		// plain rows for the speed measurement
		{"0 0 4", "minecraft:stone"}, {"1 0 4", "minecraft:stone"}, {"2 0 4", "minecraft:stone"}, {"3 0 4", "minecraft:stone"},
		{"4 0 4", "minecraft:stone"}, {"5 0 4", "minecraft:stone"}, {"6 0 4", "minecraft:stone"},
		{"0 0 5", "minecraft:stone"}, {"1 0 5", "minecraft:stone"}, {"2 0 5", "minecraft:stone"}, {"3 0 5", "minecraft:stone"},
		{"4 0 5", "minecraft:stone"}, {"5 0 5", "minecraft:stone"}, {"6 0 5", "minecraft:stone"},
	};

	static final Set<String> DYNAMIC = PrinterGameTest.DYNAMIC;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!System.getProperty("prism.only", "").equals("printerserver")) return;
		ctx.getInput().resizeWindow(1600, 900);
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((m, overlay) -> { if (!overlay) chat.add(m.getString()); });
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register((m, s, p, params, t) -> chat.add(m.getString()));
		try {
			ctx.runOnClient(mc -> {
				ServerData data = new ServerData("grim-test", "127.0.0.1:25610", ServerData.Type.OTHER);
				ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString("127.0.0.1:25610"), data, false, null);
			});
			for (int i = 0; i < 300 && !ctx.computeOnClient(mc -> mc.player != null && mc.level != null && mc.screen == null); i++) ctx.waitTicks(5);
			check(ctx.computeOnClient(mc -> mc.player != null), "joined the Paper + GrimAC test server");
			ctx.waitTicks(60);
			String[] setup = {"gamemode survival", "clear", "effect clear", "time set noon", "gamerule doDaylightCycle false",
				"fill -2 -60 -2 9 -55 8 air", "tp @s 3.5 -60 3.5 180 30",
				"give @s chest 4", "give @s hopper 6", "give @s furnace 1", "give @s stone 16"};
			for (String c : setup) {
				ctx.runOnClient(mc -> mc.player.connection.sendCommand(c));
				ctx.waitTicks(5);
			}
			ctx.waitTicks(60);
			log.add("grim reply: " + String.join(" | ", chat));
			Map<BlockPos, BlockState> want = new LinkedHashMap<>();
			for (String[] p : PARTS) {
				String[] c = p[0].split(" ");
				try {
					want.put(new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])),
						BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, p[1], false).blockState());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			ctx.runOnClient(mc -> {
				try {
					Path dir = mc.gameDirectory.toPath().resolve("schematics");
					Files.createDirectories(dir);
					NbtIo.writeCompressed(PrinterGameTest.litematic(want, 7, 2, 6), dir.resolve("grim_test.litematic"));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				Printer pr = PrismClient.modules().get(Printer.class);
				for (Setting<?> s : pr.getSettings()) {
					switch (s.getName()) {
						case "File" -> ((TextSetting) s).set("grim_test.litematic");
						case "Origin" -> ((TextSetting) s).set("0 -60 0");
						case "Range" -> ((NumberSetting) s).set(5.0);
						case "Delay" -> ((NumberSetting) s).set(0.0);
						case "Blocks Per Tick" -> ((NumberSetting) s).set(1.0);
						case "Legit Aim", "Look At Block" -> ((BoolSetting) s).set(true);
						case "Air Place" -> ((BoolSetting) s).set(false);
						case "Mode" -> ((com.wailsvlr.prism.setting.ModeSetting) s).set("Auto");
						default -> { }
					}
				}
			});
			int before = chat.size();
			long start = ctx.computeOnClient(mc -> mc.level.getGameTime());
			ctx.runOnClient(mc -> PrismClient.modules().get(Printer.class).setEnabled(true));
			int t = 0, lastPlaced = -1, still = 0;
			boolean moved = false, movedEast = false;
			String info = "";
			for (; t < 2400; t += 10) {
				ctx.waitTicks(10);
				info = ctx.computeOnClient(mc -> String.valueOf(PrismClient.modules().get(Printer.class).getInfo()));
				if (t % 100 == 0 || t <= 120) log.add("t=" + t + " " + info);
				int nowPlaced = Integer.parseInt(info.split("/")[0].trim());
				still = nowPlaced == lastPlaced ? still + 10 : 0;
				lastPlaced = nowPlaced;
				if (still >= 100 && !moved && info.contains("stand south of the furnace")) {
					// like a player reading the hint: walk round to the south side of the furnace
					moved = true;
					log.add("t=" + t + " the printer asks: " + info + " -> moving south of the furnace (0.5, -59, 4.5), facing north");
					ctx.runOnClient(mc -> mc.player.connection.sendCommand("tp @s 0.5 -59 4.5 180 30"));
					ctx.waitTicks(20);
				}
				if (still >= 100 && info.contains("stand east of the hopper") && !movedEast) {
					movedEast = true;
					log.add("t=" + t + " the printer asks: " + info + " -> moving east of the hopper (3.5, -60, 2.5), facing west");
					ctx.runOnClient(mc -> mc.player.connection.sendCommand("tp @s 3.5 -60 2.5 90 30"));
					ctx.waitTicks(20);
				}
				boolean done = ctx.computeOnClient(mc -> {
					Printer pr = PrismClient.modules().get(Printer.class);
					return pr.total() > 0 && pr.missing() == 0 && pr.wrong() == 0;
				});
				if (done) break;
			}
			long ticks = ctx.computeOnClient(mc -> mc.level.getGameTime()) - start;
			ctx.takeScreenshot("printer_grim_done");
			log.add(String.format(java.util.Locale.ROOT, "finished in %d ticks (%.1f s): %s; %.2f blocks per second", ticks, ticks / 20.0, info,
				PARTS.length / (ticks / 20.0)));
			List<String> alerts = new ArrayList<>();
			synchronized (chat) {
				for (int i = before; i < chat.size(); i++) {
					String c = chat.get(i);
					if (c.toLowerCase(java.util.Locale.ROOT).contains("failed") || c.contains("Grim") || c.contains("[Printer]")) alerts.add(c);
				}
			}
			log.add("grim alerts / printer messages during the print (" + alerts.size() + "):");
			for (String a : alerts) log.add("  " + a);
			check(alerts.stream().noneMatch(a -> a.toLowerCase(java.util.Locale.ROOT).contains("failed")), "no Grim alerts while printing");
			check(alerts.stream().noneMatch(a -> a.contains("took back")), "the server took back no placement");
			for (var e : want.entrySet()) {
				BlockPos at = new BlockPos(0, -60, 0).offset(e.getKey());
				BlockState w = e.getValue();
				BlockState h = ctx.computeOnClient(mc -> mc.level.getBlockState(at));
				boolean ok = h.getBlock() == w.getBlock();
				StringBuilder diff = new StringBuilder();
				if (ok) {
					for (var prop : w.getProperties()) {
						if (DYNAMIC.contains(prop.getName())) continue;
						if (!h.getValue(prop).equals(w.getValue(prop))) {
							ok = false;
							diff.append(' ').append(prop.getName()).append('=').append(h.getValue(prop)).append(" (want ").append(w.getValue(prop)).append(')');
						}
					}
				}
				check(ok, "printed " + BlockStateParser.serialize(w) + (ok ? "" : " -> got " + BlockStateParser.serialize(h) + diff));
				if (!ok) log.add("    why: " + ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).explain(at)));
			}
			ctx.runOnClient(mc -> PrismClient.modules().get(Printer.class).setEnabled(false));

			// ---------------------------------------------------------- Easy Place: you click, it puts the schematic's block there
			Map<BlockPos, BlockState> easy = new LinkedHashMap<>();
			String[][] easyParts = {{"0 0 0", "minecraft:stone"}, {"1 0 0", "minecraft:furnace[facing=south]"},
				{"2 0 0", "minecraft:chest[facing=south,type=single]"}, {"2 1 0", "minecraft:hopper[facing=down]"}};
			for (String[] p : easyParts) {
				String[] c = p[0].split(" ");
				try {
					easy.put(new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])),
						BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, p[1], false).blockState());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			for (String c : new String[]{"fill 10 -60 -2 16 -55 5 air", "clear", "item replace entity @s hotbar.0 with stick",
				"give @s stone 1", "give @s furnace 1", "give @s chest 1", "give @s hopper 1", "tp @s 12.5 -60 3.5 180 30"}) {
				ctx.runOnClient(mc -> mc.player.connection.sendCommand(c));
				ctx.waitTicks(5);
			}
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> {
				try {
					NbtIo.writeCompressed(PrinterGameTest.litematic(easy, 3, 2, 1), mc.gameDirectory.toPath().resolve("schematics").resolve("grim_easy.litematic"));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				mc.player.getInventory().setSelectedSlot(0);
				Printer pr = PrismClient.modules().get(Printer.class);
				for (Setting<?> s : pr.getSettings()) {
					switch (s.getName()) {
						case "File" -> ((TextSetting) s).set("grim_easy.litematic");
						case "Origin" -> ((TextSetting) s).set("11 -60 0");
						case "Easy Place" -> ((BoolSetting) s).set(true);
						default -> { }
					}
				}
				pr.setEnabled(true);
			});
			ctx.waitTicks(30);
			boolean none = ctx.computeOnClient(mc -> {
				for (BlockPos k : easy.keySet()) if (!mc.level.getBlockState(new BlockPos(11, -60, 0).offset(k)).isAir()) return false;
				return true;
			});
			check(none, "Easy Place on the server: nothing placed without a click");
			int easyBefore = chat.size();
			long easyStart = ctx.computeOnClient(mc -> mc.level.getGameTime());
			for (BlockPos k : easy.keySet()) {
				BlockPos at = new BlockPos(11, -60, 0).offset(k);
				for (int attempt = 0; attempt < 3; attempt++) {
					ctx.runOnClient(mc -> {
						var eye = mc.player.getEyePosition();
						double dx = at.getX() + 0.5 - eye.x, dy = at.getY() + 0.5 - eye.y, dz = at.getZ() + 0.5 - eye.z;
						mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
						mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
					});
					ctx.waitTicks(3);
					ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
					ctx.waitTicks(12);
					if (ctx.computeOnClient(mc -> mc.level.getBlockState(at).getBlock() == easy.get(k).getBlock())) break;
					log.add("  easy place retry for " + at.toShortString() + ": " + ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).reason()));
				}
			}
			ctx.waitTicks(20);
			log.add(String.format(java.util.Locale.ROOT, "easy place: 4 clicks done in %d ticks", ctx.computeOnClient(mc -> mc.level.getGameTime()) - easyStart));
			for (var e : easy.entrySet()) {
				BlockPos at = new BlockPos(11, -60, 0).offset(e.getKey());
				BlockState w = e.getValue(), h = ctx.computeOnClient(mc -> mc.level.getBlockState(at));
				boolean ok = h.getBlock() == w.getBlock();
				for (var prop : w.getProperties()) if (ok && !DYNAMIC.contains(prop.getName()) && !h.getValue(prop).equals(w.getValue(prop))) ok = false;
				check(ok, "Easy Place on the server: " + BlockStateParser.serialize(w) + (ok ? "" : " -> got " + BlockStateParser.serialize(h)));
			}
			List<String> easyAlerts = new ArrayList<>();
			synchronized (chat) {
				for (int i = easyBefore; i < chat.size(); i++) if (chat.get(i).toLowerCase(java.util.Locale.ROOT).contains("failed") || chat.get(i).contains("took back")) easyAlerts.add(chat.get(i));
			}
			for (String al : easyAlerts) log.add("  " + al);
			check(easyAlerts.isEmpty(), "Easy Place: no Grim alerts and nothing taken back (" + easyAlerts.size() + ")");
			ctx.takeScreenshot("printer_grim_easy_place");
			ctx.runOnClient(mc -> {
				Printer pr = PrismClient.modules().get(Printer.class);
				for (Setting<?> s : pr.getSettings()) if (s.getName().equals("Easy Place")) ((BoolSetting) s).set(false);
				pr.setEnabled(false);
			});
		} finally {
			log.add(0, "Printer on Paper + GrimAC: " + log.stream().filter(l -> l.startsWith("PASS")).count() + " passed, " + fails + " failed");
			try {
				Files.write(Path.of("printer-server-report.txt"), log);
			} catch (Exception ignored) { }
			ctx.runOnClient(mc -> {
				if (mc.getConnection() != null) mc.disconnect(new TitleScreen(), false);
			});
			ctx.waitTicks(40);
		}
	}
}
