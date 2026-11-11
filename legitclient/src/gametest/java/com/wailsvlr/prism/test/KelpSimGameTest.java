package com.wailsvlr.prism.test;

import com.wailsvlr.prism.modules.Printer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds kelp farm schematics in a flat world and runs them fast (tick sprint), counting kelp, dried kelp, bone meal and fuel
 * in every container over time. Schematics: one path per line in verity-dev/kelp-sim/list.txt; a line may add
 * "fuel=<item>x<count>@<dx>,<dy>,<dz>" (put that into the container at that schematic position first) and
 * "ticks=<n>". Run: -PprismOnly=kelp. Writes kelp-sim-report.txt and screenshots.
 */
public class KelpSimGameTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();
	/** Photo mode: labels in the world, the smoker and a fuel hopper opened (for the user's how-to pictures). */
	private boolean pics;
	/** Blocks to watch: every state they go through during the run, and how often they changed. */
	private final List<int[]> watches = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!System.getProperty("prism.only", "").equals("kelp")) return;
		ctx.getInput().resizeWindow(1600, 900);
		List<String> lines;
		try {
			lines = Files.readAllLines(Path.of(System.getProperty("user.home"), "verity-dev", "kelp-sim", "list.txt"));
		} catch (Exception e) {
			log.add("no list: " + e);
			write();
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("difficulty peaceful");
			server.runCommand("gamemode spectator @a");
			int run = 0;
			for (String line : lines) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] parts = line.split("\\s*\\|\\s*");
				Path file = Path.of(parts[0]);
				int ticks = 12000;
				List<String[]> fuels = new ArrayList<>();
				List<int[]> pulls = new ArrayList<>();
				for (int i = 1; i < parts.length; i++) {
					if (parts[i].startsWith("lever=")) {
						String[] c = parts[i].substring(6).split(",");
						pulls.add(new int[]{Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])});
					}
					if (parts[i].equals("pics")) pics = true;
					if (parts[i].startsWith("watch=")) {
						String[] c = parts[i].substring(6).split(",");
						watches.add(new int[]{Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])});
					}
					if (parts[i].startsWith("ticks=")) ticks = Integer.parseInt(parts[i].substring(6));
					if (parts[i].startsWith("fuel=")) fuels.add(parts[i].substring(5).split("[x@]"));
					if (parts[i].startsWith("fill=")) fuels.add(("ALL@" + parts[i].substring(5)).split("@"));
				}
				BlockPos o = new BlockPos(400 + run * 64, -50, 400);
				run++;
				log.add("=== " + file.getFileName() + " at " + o.toShortString() + ", " + ticks + " ticks");
				try {
					simulate(ctx, server, file, o, ticks, fuels, pulls);
				} catch (Exception e) {
					log.add("FAIL  " + e);
				}
			}
		}
		write();
	}

	private void write() {
		try {
			Files.write(Path.of("kelp-sim-report.txt"), log);
		} catch (Exception ignored) { }
	}

	private void simulate(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, Path file, BlockPos o,
						  int ticks, List<String[]> fuels, List<int[]> pulls) throws Exception {
		CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		Printer.read(root, o, blocks);
		// block entities and entities, by the same corner maths as the Printer
		List<CompoundTag> tes = new ArrayList<>();
		List<CompoundTag> ents = new ArrayList<>();
		int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		CompoundTag regions = root.getCompoundOrEmpty("Regions");
		for (String key : regions.keySet()) {
			CompoundTag r = regions.getCompoundOrEmpty(key);
			CompoundTag pos = r.getCompoundOrEmpty("Position"), size = r.getCompoundOrEmpty("Size");
			int px = pos.getIntOr("x", 0), py = pos.getIntOr("y", 0), pz = pos.getIntOr("z", 0);
			int sx = size.getIntOr("x", 0), sy = size.getIntOr("y", 0), sz = size.getIntOr("z", 0);
			int mx = sx < 0 ? px + sx + 1 : px, my = sy < 0 ? py + sy + 1 : py, mz = sz < 0 ? pz + sz + 1 : pz;
			for (Tag t : r.getListOrEmpty("TileEntities")) if (t instanceof CompoundTag te) {
				CompoundTag c = te.copy();
				c.putInt("x", o.getX() + mx + te.getIntOr("x", 0));
				c.putInt("y", o.getY() + my + te.getIntOr("y", 0));
				c.putInt("z", o.getZ() + mz + te.getIntOr("z", 0));
				tes.add(c);
			}
			for (Tag t : r.getListOrEmpty("Entities")) if (t instanceof CompoundTag e) {
				CompoundTag c = e.copy();
				var p = e.getListOrEmpty("Pos");
				var np = new net.minecraft.nbt.ListTag();
				np.add(net.minecraft.nbt.DoubleTag.valueOf(o.getX() + px + p.getDoubleOr(0, 0)));
				np.add(net.minecraft.nbt.DoubleTag.valueOf(o.getY() + py + p.getDoubleOr(1, 0)));
				np.add(net.minecraft.nbt.DoubleTag.valueOf(o.getZ() + pz + p.getDoubleOr(2, 0)));
				c.put("Pos", np);
				ents.add(c);
			}
			lo = new int[]{Math.min(lo[0], o.getX() + mx), Math.min(lo[1], o.getY() + my), Math.min(lo[2], o.getZ() + mz)};
			hi = new int[]{Math.max(hi[0], o.getX() + mx + Math.abs(sx) - 1), Math.max(hi[1], o.getY() + my + Math.abs(sy) - 1), Math.max(hi[2], o.getZ() + mz + Math.abs(sz) - 1)};
		}
		final int[] LO = lo, HI = hi;
		server.runCommand("forceload add " + (LO[0] - 16) + " " + (LO[2] - 16) + " " + (HI[0] + 16) + " " + (HI[2] + 16));
		ctx.waitTicks(20);
		String placed = server.computeOnServer(sv -> {
			ServerLevel level = sv.overworld();
			var lookup = level.holderLookup(net.minecraft.core.registries.Registries.BLOCK);
			Map<BlockPos, BlockState> fixed = new LinkedHashMap<>(blocks);
			int moving = 0;
			// a piston caught mid-move: put the block it was moving (a moving piston head is left out)
			for (CompoundTag te : tes) {
				if (!te.getStringOr("id", "").equals("minecraft:piston")) continue;
				BlockPos at = new BlockPos(te.getIntOr("x", 0), te.getIntOr("y", 0), te.getIntOr("z", 0));
				BlockState st = NbtUtils.readBlockState(lookup, te.getCompoundOrEmpty("blockState"));
				fixed.put(at, st.is(Blocks.PISTON_HEAD) ? Blocks.AIR.defaultBlockState() : st);
				moving++;
			}
			// clear the box first, then place without neighbour updates, then wake everything up
			for (int x = LO[0] - 1; x <= HI[0] + 1; x++)
				for (int y = LO[1] - 2; y <= HI[1] + 1; y++)
					for (int z = LO[2] - 1; z <= HI[2] + 1; z++)
						level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2 | 16);
			for (var e : fixed.entrySet()) level.setBlock(e.getKey(), e.getValue(), 2 | 16);
			for (CompoundTag te : tes) {
				if (te.getStringOr("id", "").equals("minecraft:piston")) continue;
				BlockPos at = new BlockPos(te.getIntOr("x", 0), te.getIntOr("y", 0), te.getIntOr("z", 0));
				BlockEntity be = level.getBlockEntity(at);
				if (be != null) {
					try {
						be.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), te));
					} catch (Exception ignored) { }
					be.setChanged();
				}
			}
			// catch what falls out of the bottom: a chest under every downward hopper on the lowest layer
			int catches = 0;
			for (var e : fixed.entrySet()) {
				BlockState st = e.getValue();
				BlockPos below = e.getKey().below();
				if (st.is(Blocks.HOPPER) && st.getValue(net.minecraft.world.level.block.HopperBlock.FACING) == net.minecraft.core.Direction.DOWN
					&& level.getBlockState(below).isAir() && below.getY() < LO[1]) {
					level.setBlock(below, Blocks.CHEST.defaultBlockState(), 3);
					catches++;
				}
			}
			for (var e : fixed.entrySet()) level.updateNeighborsAt(e.getKey(), e.getValue().getBlock());
			// observer clocks start when an observer is placed next to its partner, as when built by hand
			for (var e : fixed.entrySet()) if (e.getValue().is(Blocks.OBSERVER)) {
				level.setBlock(e.getKey(), Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(e.getKey(), e.getValue(), 3);
			}
			for (var e : fixed.entrySet()) {
				BlockState now = level.getBlockState(e.getKey());
				level.neighborChanged(now, e.getKey(), now.getBlock(), null, false);
			}
			return fixed.size() + " blocks, " + moving + " mid-move pistons fixed, " + catches + " catch chests";
		});
		log.add("placed " + placed);
		// pistons check their power when placed: place them again once the redstone around has settled, as a player would
		ctx.waitTicks(10);
		server.runOnServer(sv -> {
			ServerLevel level = sv.overworld();
			for (var e : blocks.entrySet()) {
				BlockState now = level.getBlockState(e.getKey());
				if (now.getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock && !now.getValue(net.minecraft.world.level.block.piston.PistonBaseBlock.EXTENDED)) {
					level.setBlock(e.getKey(), Blocks.AIR.defaultBlockState(), 3);
					level.setBlock(e.getKey(), now, 3);
				}
			}
		});
		for (CompoundTag e : ents) {
			String id = e.getStringOr("id", "");
			var p = e.getListOrEmpty("Pos");
			server.runCommand(String.format(java.util.Locale.ROOT, "summon %s %.3f %.3f %.3f", id, p.getDoubleOr(0, 0), p.getDoubleOr(1, 0), p.getDoubleOr(2, 0)));
		}
		// bone meal: four full shulker boxes in the highest chest
		BlockPos topChest = server.computeOnServer(sv -> {
			BlockPos best = null;
			for (var e : blocks.entrySet()) if (e.getValue().is(Blocks.CHEST) && (best == null || e.getKey().getY() > best.getY())) best = e.getKey();
			return best;
		});
		StringBuilder box = new StringBuilder("white_shulker_box[container=[");
		for (int i = 0; i < 27; i++) box.append(i == 0 ? "" : ",").append("{slot:").append(i).append(",item:{id:\"minecraft:bone_meal\",count:64}}");
		box.append("]]");
		if (topChest != null) for (int i = 0; i < 4; i++)
			server.runCommand("item replace block " + topChest.getX() + " " + topChest.getY() + " " + topChest.getZ() + " container." + i + " with " + box);
		log.add("bone meal: 4 shulkers (6912) into the chest at " + (topChest == null ? "none" : rel(topChest, o)));
		for (String[] f : fuels) {
			if (f[0].equals("ALL")) {
				// fill=<item>@x,y,z: every slot, full stacks
				String[] c = f[2].split(",");
				BlockPos at = o.offset(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
				int n = server.computeOnServer(sv -> sv.overworld().getBlockEntity(at) instanceof Container cc ? cc.getContainerSize() : 0);
				for (int i = 0; i < n; i++) server.runCommand("item replace block " + at.getX() + " " + at.getY() + " " + at.getZ() + " container." + i + " with " + f[1] + " 64");
				log.add("filled " + n + " slots with " + f[1] + " at " + rel(at, o));
				continue;
			}
			BlockPos at = o.offset(Integer.parseInt(f[2].split(",")[0]), Integer.parseInt(f[2].split(",")[1]), Integer.parseInt(f[2].split(",")[2]));
			server.runCommand("item replace block " + at.getX() + " " + at.getY() + " " + at.getZ() + " container.0 with " + f[0] + " " + f[1]);
			log.add("fuel: " + f[1] + " " + f[0] + " at " + rel(at, o));
		}
		for (int[] l : pulls) {
			BlockPos at = o.offset(l[0], l[1], l[2]);
			String r = server.computeOnServer(sv -> {
				ServerLevel level = sv.overworld();
				BlockState st = level.getBlockState(at);
				if (!(st.getBlock() instanceof net.minecraft.world.level.block.LeverBlock lb)) return "no lever at " + rel(at, o);
				lb.pull(st, level, at, null);
				return "lever at " + rel(at, o) + " now " + level.getBlockState(at).getValue(net.minecraft.world.level.block.LeverBlock.POWERED);
			});
			log.add(r);
		}
		if (pics) { photos(ctx, server, o); return; }
		server.runCommand("tp @p " + (HI[0] + 6) + " " + (HI[1] + 2) + " " + (LO[2] - 6) + " 45 25");
		ctx.waitTicks(40);
		ctx.takeScreenshot("kelp_" + file.getFileName().toString().replace(' ', '_') + "_start");
		long start = server.computeOnServer(sv -> sv.overworld().getGameTime());
		server.runCommand("tick sprint " + ticks);
		long lastReport = start;
		Map<String, java.util.Set<String>> seen = new TreeMap<>();
		Map<String, Integer> changes = new TreeMap<>();
		Map<String, String> last = new java.util.HashMap<>();
		while (true) {
			if (watches.isEmpty()) ctx.waitTicks(20);
			else for (int k = 0; k < 20; k++) {
				ctx.waitTicks(1);
				for (int[] w : watches) {
					BlockPos at = o.offset(w[0], w[1], w[2]);
					String key = rel(at, o);
					String st = server.computeOnServer(sv -> {
						BlockState b = sv.overworld().getBlockState(at);
						String n = b.toString().replace("Block{minecraft:", "").replace("}", "");
						int sig = sv.overworld().getBestNeighborSignal(at);
						return n + " sig" + sig;
					});
					seen.computeIfAbsent(key, kk -> new java.util.TreeSet<>()).add(st);
					if (last.containsKey(key) && !last.get(key).equals(st)) changes.merge(key, 1, Integer::sum);
					last.put(key, st);
				}
			}
			long now = server.computeOnServer(sv -> sv.overworld().getGameTime());
			if (now - lastReport >= 2400 || now - start >= ticks) {
				lastReport = now;
				log.add("t=" + (now - start) + "  " + census(server, LO, HI));
			}
			if (now - start >= ticks) break;
		}
		server.runCommand("tick sprint stop");
		for (var e : seen.entrySet()) log.add("watch " + e.getKey() + " states " + e.getValue() + ", changed " + changes.getOrDefault(e.getKey(), 0) + " times");
		ctx.waitTicks(20);
		ctx.takeScreenshot("kelp_" + file.getFileName().toString().replace(' ', '_') + "_end");
		// where everything sits, container by container
		log.add("containers: " + server.computeOnServer(sv -> {
			StringBuilder sb = new StringBuilder();
			ServerLevel level = sv.overworld();
			for (int x = LO[0] - 1; x <= HI[0] + 1; x++)
				for (int y = LO[1] - 2; y <= HI[1] + 1; y++)
					for (int z = LO[2] - 1; z <= HI[2] + 1; z++) {
						BlockPos p = new BlockPos(x, y, z);
						if (!(level.getBlockEntity(p) instanceof Container c)) continue;
						Map<String, Integer> what = new TreeMap<>();
						for (int i = 0; i < c.getContainerSize(); i++) {
							ItemStack s = c.getItem(i);
							if (!s.isEmpty()) what.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
						}
						if (!what.isEmpty()) sb.append("\n    ").append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath())
							.append(' ').append(rel(p, o)).append(' ').append(what);
					}
			return sb.toString();
		}));
	}

	private void label(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, BlockPos o, double x, double y, double z, String text, String color) {
		server.runCommand(String.format(java.util.Locale.ROOT,
			"summon text_display %.2f %.2f %.2f {text:{text:\"%s\",color:\"%s\",bold:1b},billboard:\"center\",see_through:1b,background:-1442840576,"
				+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1.4f,1.4f,1.4f]}}",
			o.getX() + x, o.getY() + y, o.getZ() + z, text, color));
	}

	/** Stands on a glass block at (x, y-1, z) relative to o, looks at the centre of block (bx,by,bz) and right-clicks it. */
	private void openAt(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, BlockPos o,
						double x, double y, double z, int bx, int by, int bz) {
		server.runCommand("setblock " + (o.getX() + (int) Math.floor(x)) + " " + (o.getY() + (int) y - 1) + " " + (o.getZ() + (int) Math.floor(z)) + " glass");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f", o.getX() + x, o.getY() + y, o.getZ() + z));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> {
			var eye = mc.player.getEyePosition();
			double dx = o.getX() + bx + 0.5 - eye.x, dy = o.getY() + by + 0.5 - eye.y, dz = o.getZ() + bz + 0.5 - eye.z;
			mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
			mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
		});
		ctx.waitTicks(5);
		ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(15);
		ctx.getInput().setCursorPos(5, 5);
		ctx.waitTicks(5);
	}

	/** Where the open container's slots are on the screenshot (pixels): "index:x,y" for the first n menu slots. */
	private String slotPixels(ClientGameTestContext ctx, int n) {
		return ctx.computeOnClient(mc -> {
			if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) return "no screen";
			var acc = (com.wailsvlr.prism.mixin.AbstractContainerScreenAccessor) cs;
			double s = mc.getWindow().getGuiScale();
			StringBuilder sb = new StringBuilder("scale=" + s + " left=" + acc.prism$leftPos() + " top=" + acc.prism$topPos());
			for (int i = 0; i < n; i++) {
				var sl = cs.getMenu().slots.get(i);
				sb.append(" ").append(i).append(':').append((int) ((acc.prism$leftPos() + sl.x + 8) * s)).append(',').append((int) ((acc.prism$topPos() + sl.y + 8) * s));
			}
			return sb.toString();
		});
	}

	private void photos(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, BlockPos o) {
		server.runCommand("gamemode creative @a");
		ctx.runOnClient(mc -> mc.options.hideGui = true);
		// a little life in the machine: kelp in the smokers, a block on the fuel line, blaze rods where they go
		for (int x = 2; x <= 5; x++) {
			server.runCommand("item replace block " + (o.getX() + x) + " " + (o.getY() - 3) + " " + (o.getZ() + 2) + " container.0 with kelp 64");
			server.runCommand("item replace block " + (o.getX() + x) + " " + (o.getY() - 3) + " " + (o.getZ() + 2) + " container.1 with blaze_rod 40");
		}
		label(server, o, 4.0, -2.5, 3.6, "4 SMOKER  (blaze rod iceri)", "gold");
		label(server, o, 4.0, -4.6, 3.4, "pismis yosun buradan cikar", "gray");
		label(server, o, 0.5, -3.5, 2.9, "ASANSOR (2 dropper)", "aqua");
		label(server, o, 0.5, -1.6, 2.6, "CRAFTER (blok yapar)", "aqua");
		label(server, o, 7.4, -1.5, 0.5, "DEPO: yosun bloklari", "green");
		label(server, o, 7.4, -0.5, 2.0, "FAZLA CIG YOSUN", "yellow");
		label(server, o, 3.5, -0.3, 3.4, "yosun hatti (ciftlikten gelir)", "white");
		label(server, o, 0.5, 0.6, 3.4, "ciftligin taban hopper'lari", "white");
		ctx.waitTicks(20);
		server.runCommand("setblock " + (o.getX() + 12) + " " + (o.getY() - 3) + " " + (o.getZ() + 11) + " glass");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %d %d %d facing %.1f %.1f %.1f", o.getX() + 12, o.getY() - 2, o.getZ() + 11,
			o.getX() + 2.5, o.getY() - 2.5, o.getZ() + 1.5));
		ctx.waitTicks(40);
		ctx.takeScreenshot("pic_overview");
		// the back side: fuel hoppers with their cobblestone
		server.runCommand("setblock " + (o.getX() + 9) + " " + (o.getY() - 3) + " " + (o.getZ() - 6) + " glass");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %d %d %d facing %.1f %.1f %.1f", o.getX() + 9, o.getY() - 2, o.getZ() - 6,
			o.getX() + 3.5, o.getY() - 2.5, o.getZ() + 1.5));
		label(server, o, 3.5, -3.5, -0.6, "YAKIT HOPPER'LARI (4 cobblestone)", "light_purple");
		label(server, o, 3.5, -0.9, -1.2, "BLAZE ROD BURAYA (2 sandik)", "gold");
		ctx.waitTicks(40);
		ctx.takeScreenshot("pic_back");
		// the smoker, opened
		openAt(ctx, server, o, 2.5, -3, 4.5, 2, -3, 2);
		log.add("smoker slots " + slotPixels(ctx, 3));
		ctx.takeScreenshot("pic_smoker");
		ctx.runOnClient(mc -> mc.setScreen(null));
		ctx.waitTicks(5);
		// a fuel hopper, opened from behind
		openAt(ctx, server, o, 2.5, -3, -1.5, 2, -3, 1);
		log.add("hopper slots " + slotPixels(ctx, 5));
		ctx.takeScreenshot("pic_hopper");
		ctx.runOnClient(mc -> { mc.setScreen(null); mc.options.hideGui = false; });
	}

	private static String rel(BlockPos p, BlockPos o) { return "(" + (p.getX() - o.getX()) + "," + (p.getY() - o.getY()) + "," + (p.getZ() - o.getZ()) + ")"; }

	/** Totals of the interesting items in every container and on the ground in the box (shulker contents counted too). */
	private String census(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, int[] lo, int[] hi) {
		return server.computeOnServer(sv -> {
			ServerLevel level = sv.overworld();
			Map<String, Integer> total = new TreeMap<>();
			for (int x = lo[0] - 1; x <= hi[0] + 1; x++)
				for (int y = lo[1] - 2; y <= hi[1] + 1; y++)
					for (int z = lo[2] - 1; z <= hi[2] + 1; z++) {
						if (!(level.getBlockEntity(new BlockPos(x, y, z)) instanceof Container c)) continue;
						for (int i = 0; i < c.getContainerSize(); i++) count(total, c.getItem(i));
					}
			AABB box = new AABB(lo[0] - 2, lo[1] - 3, lo[2] - 2, hi[0] + 3, hi[1] + 3, hi[2] + 3);
			for (var e : level.getEntities((net.minecraft.world.entity.Entity) null, box, en -> true)) {
				if (e instanceof ItemEntity ie) count(total, ie.getItem());
				else if (e instanceof Container c) for (int i = 0; i < c.getContainerSize(); i++) count(total, c.getItem(i));
			}
			StringBuilder sb = new StringBuilder();
			for (String k : new String[]{"kelp", "dried_kelp", "dried_kelp_block", "bone_meal", "coal", "blaze_rod", "white_shulker_box"})
				sb.append(k).append('=').append(total.getOrDefault(k, 0)).append("  ");
			return sb.toString();
		});
	}

	private static void count(Map<String, Integer> total, ItemStack s) {
		if (s.isEmpty()) return;
		String k = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		total.merge(k, s.getCount(), Integer::sum);
		var cont = s.get(net.minecraft.core.component.DataComponents.CONTAINER);
		if (cont != null) for (ItemStack in : cont.nonEmptyItemsCopy()) total.merge(BuiltInRegistries.ITEM.getKey(in.getItem()).getPath(), in.getCount(), Integer::sum);
	}
}
