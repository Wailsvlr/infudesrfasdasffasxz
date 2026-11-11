package com.wailsvlr.prism.test;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.modules.Printer;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.Setting;
import com.wailsvlr.prism.setting.TextSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Printer test in a real client: writes a small .litematic with a redstone line and oriented blocks into
 * schematics/, lets the Printer build it in creative, and compares every block with the schematic.
 * Writes printer-test-report.txt in the test run folder.
 */
public class PrinterGameTest implements FabricClientGameTest {
	private final List<String> log = new ArrayList<>();
	private int fails;

	private void check(boolean ok, String what) {
		log.add((ok ? "PASS  " : "FAIL  ") + what);
		if (!ok) fails++;
	}

	static final String[][] PARTS = {
		{"0 0 1", "minecraft:stone"}, {"0 1 1", "minecraft:lever[face=floor,facing=north,powered=true]"},
		{"1 0 1", "minecraft:redstone_wire"}, {"2 0 1", "minecraft:repeater[facing=west,delay=3]"},
		{"3 0 1", "minecraft:comparator[facing=west,mode=subtract]"}, {"4 0 1", "minecraft:redstone_wire"},
		{"5 0 1", "minecraft:sticky_piston[facing=up]"}, {"6 0 1", "minecraft:note_block[note=5]"},
		{"0 0 3", "minecraft:oak_stairs[facing=east,half=top]"}, {"1 0 3", "minecraft:stone_slab[type=top]"},
		{"2 0 3", "minecraft:stone_slab[type=double]"}, {"3 0 3", "minecraft:hopper[facing=east]"}, {"4 0 3", "minecraft:stone"},
		{"5 0 3", "minecraft:observer[facing=south]"}, {"6 0 3", "minecraft:stone"}, {"6 1 3", "minecraft:stone"},
		{"6 1 2", "minecraft:redstone_wall_torch[facing=north]"},
		{"2 0 5", "minecraft:oak_log[axis=x]"}, {"3 0 5", "minecraft:oak_log[axis=z]"}, {"4 0 5", "minecraft:furnace[facing=west]"},
		{"5 0 5", "minecraft:oak_trapdoor[facing=north,half=top,open=true]"},
	};

	/** Properties that follow from neighbours or power, not from how the block was placed. */
	static final Set<String> DYNAMIC = Set.of("power", "north", "south", "east", "west", "instrument", "lit", "extended", "enabled",
		"waterlogged", "shape", "locked", "triggered", "powered");

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String only = System.getProperty("prism.only", "");
		if (!only.isEmpty() && !only.equals("printer")) return;
		ctx.getInput().resizeWindow(1600, 900);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamemode creative @a");
			server.runCommand("tp @p 23.5 -57 2.5 0 50");
			ctx.waitTicks(40);
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
			Path file = ctx.computeOnClient(mc -> {
				try {
					Path dir = mc.gameDirectory.toPath().resolve("schematics");
					Files.createDirectories(dir);
					Path f = dir.resolve("prism_test.litematic");
					NbtIo.writeCompressed(litematic(want, 7, 2, 6), f);
					return f;
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			log.add("wrote " + file);
			ctx.runOnClient(mc -> {
				mc.player.getAbilities().flying = true;
				Printer pr = PrismClient.modules().get(Printer.class);
				for (Setting<?> s : pr.getSettings()) {
					if (s.getName().equals("File")) ((TextSetting) s).set("prism_test.litematic");
					if (s.getName().equals("Origin")) ((TextSetting) s).set("20 -60 0");
					if (s.getName().equals("Range")) ((NumberSetting) s).set(5.5);
					if (s.getName().equals("Blocks Per Tick")) ((NumberSetting) s).set(2.0);
					if (s.getName().equals("Delay")) ((NumberSetting) s).set(1.0);
				}
				pr.setEnabled(true);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("printer_start");
			int t = 0;
			for (; t < 1200; t += 20) {
				ctx.waitTicks(20);
				boolean done = ctx.computeOnClient(mc -> {
					Printer pr = PrismClient.modules().get(Printer.class);
					return pr.total() > 0 && pr.missing() == 0 && pr.wrong() == 0;
				});
				if (done) break;
			}
			log.add("printer stopped changing after about " + t + " ticks: " + ctx.computeOnClient(mc -> PrismClient.modules().get(Printer.class).getInfo()));
			ctx.waitTicks(20);
			ctx.takeScreenshot("printer_done");
			BlockPos o = new BlockPos(20, -60, 0);
			for (var e : want.entrySet()) {
				BlockPos at = o.offset(e.getKey());
				BlockState w = e.getValue();
				BlockState h = ctx.computeOnClient(mc -> mc.level.getBlockState(at));
				boolean ok = h.getBlock() == w.getBlock();
				StringBuilder diff = new StringBuilder();
				if (ok) {
					for (var prop : w.getProperties()) {
						String n = prop.getName();
						if (DYNAMIC.contains(n) && !(n.equals("powered") && w.is(Blocks.LEVER))) continue;
						if (!h.getValue(prop).equals(w.getValue(prop))) {
							ok = false;
							diff.append(' ').append(n).append('=').append(h.getValue(prop)).append(" (want ").append(w.getValue(prop)).append(')');
						}
					}
				}
				check(ok, "printed " + BlockStateParser.serialize(w) + (ok ? "" : " -> got " + BlockStateParser.serialize(h) + diff));
			}
			ctx.runOnClient(mc -> PrismClient.modules().get(Printer.class).setEnabled(false));
		} finally {
			log.add(0, "Printer test: " + log.stream().filter(l -> l.startsWith("PASS")).count() + " passed, " + fails + " failed");
			try {
				Files.write(Path.of("printer-test-report.txt"), log);
			} catch (Exception ignored) { }
		}
	}

	/** A one-region Litematica file for the given blocks (palette entry 0 is air). */
	static CompoundTag litematic(Map<BlockPos, BlockState> blocks, int sx, int sy, int sz) {
		List<BlockState> palette = new ArrayList<>();
		palette.add(Blocks.AIR.defaultBlockState());
		for (BlockState s : blocks.values()) if (!palette.contains(s)) palette.add(s);
		int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
		long volume = (long) sx * sy * sz;
		long[] data = new long[(int) ((volume * bits + 63) / 64)];
		for (long i = 0; i < volume; i++) {
			int x = (int) (i % sx), z = (int) ((i / sx) % sz), y = (int) (i / ((long) sx * sz));
			long v = palette.indexOf(blocks.getOrDefault(new BlockPos(x, y, z), palette.get(0)));
			long start = i * bits;
			int si = (int) (start >> 6), ei = (int) (((i + 1) * bits - 1) >> 6), off = (int) (start & 63);
			data[si] |= v << off;
			if (si != ei) data[ei] |= v >>> (64 - off);
		}
		CompoundTag region = new CompoundTag();
		CompoundTag pos = new CompoundTag();
		pos.putInt("x", 0);
		pos.putInt("y", 0);
		pos.putInt("z", 0);
		CompoundTag size = new CompoundTag();
		size.putInt("x", sx);
		size.putInt("y", sy);
		size.putInt("z", sz);
		region.put("Position", pos);
		region.put("Size", size);
		ListTag pal = new ListTag();
		for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
		region.put("BlockStatePalette", pal);
		region.put("BlockStates", new LongArrayTag(data));
		CompoundTag regions = new CompoundTag();
		regions.put("test", region);
		CompoundTag root = new CompoundTag();
		root.putInt("Version", 6);
		root.put("Regions", regions);
		return root;
	}
}
