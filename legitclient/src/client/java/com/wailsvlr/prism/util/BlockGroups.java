package com.wailsvlr.prism.util;

import com.wailsvlr.prism.PrismClient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Where the game generates each block by itself, read from block_groups.txt (made from Minecraft's own structure and
 * worldgen files). A block that is not listed generates everywhere (terrain, trees, ores); a listed block generates
 * only in the named structures, or never ("-": player-only).
 */
public final class BlockGroups {
	private static final Map<Block, Set<String>> GROUPS = new IdentityHashMap<>();

	static {
		try (InputStream in = BlockGroups.class.getResourceAsStream("/assets/prismclient/block_groups.txt")) {
			if (in == null) throw new IllegalStateException("block_groups.txt missing");
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			for (String line; (line = r.readLine()) != null; ) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] parts = line.split(" ");
				BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(parts[0])).ifPresent(b ->
					GROUPS.put(b, parts.length < 2 || parts[1].equals("-") ? Set.of() : Set.of(parts[1].split(","))));
			}
		} catch (Exception e) {
			PrismClient.LOG.warn("Could not read block_groups.txt: {}", e.toString());
		}
	}

	private BlockGroups() { }

	/** Structures that generate this block (empty: never generated), or null when it generates everywhere. */
	public static Set<String> of(Block b) { return GROUPS.get(b); }

	public static Set<Block> blocks() { return GROUPS.keySet(); }
}
