package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.SectionSetting;
import com.wailsvlr.prism.setting.TextSetting;
import com.wailsvlr.prism.util.HeatMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hunts for bases with the server's random teleport: sends /rtp (or /rtp nether, /rtp end), waits a random
 * 5-10 seconds while BlockFinder scans the new area, and stops when it finds a base with enough chests or
 * shulkers that does not look raided. Found bases are written to config/prismclient-bases.txt.
 */
public class BaseHunter extends Module {
	private enum State { SEND, TELEPORTING, LOOKING }

	private final ModeSetting dimension;
	private final TextSetting overworldCommand, netherCommand, endCommand;
	private final NumberSetting minDelay, maxDelay, teleportTimeout;
	private final BoolSetting waitForScan, fastScan, skipEmpty;
	private final NumberSetting minChests, minShulkers;
	private final BoolSetting onlyIntact, allowMaybe, stopOnTrail;
	private final NumberSetting minOldChunks, minNewChunks;
	private final BoolSetting stopOnDamage, stopNearPlayers, keepHunting, sound, logToFile, showFound, keepPlayers;
	private final NumberSetting playerDistance, minHealth;

	private State state = State.SEND;
	private long nextSendAt, teleportDeadline, lookUntil, scanCap, arrivedAt;
	private boolean recorded;
	private Vec3 sentFrom;
	private ResourceKey<Level> sentDimension;
	private int rotate, teleports, found;
	private float lastHealth = -1;
	private String status = "";
	private String lastCommand = "";
	private final Set<String> logged = new HashSet<>(), trailLogged = new HashSet<>();
	/** ESP modules switched off while showing a find, to switch back on afterwards. */
	private final List<Module> hidden = new ArrayList<>();
	private boolean showing;
	/** A one-off teleport from the Quick RTP key while hunting is off: until it lands (or times out). */
	private long quickUntil, quickFastUntil;
	private Vec3 quickFrom;
	private ResourceKey<Level> quickDimension;

	private static final Pattern WAIT = Pattern.compile("(\\d+)\\s*(s\\b|sec|second|saniye|sn\\b)", Pattern.CASE_INSENSITIVE);

	public BaseHunter() {
		super("BaseHunter", "Random-teleports until BlockFinder finds a base with enough chests or shulkers that has not been raided", Category.WORLD);
		add(new SectionSetting("Teleport"));
		dimension = add(new ModeSetting("Dimension", "Where to teleport: one world, or take turns between all three", "Overworld", "Overworld", "Nether", "End", "Rotate"));
		overworldCommand = add(new TextSetting("Overworld Command", "Command for a random teleport in the overworld", "/rtp", "/rtp"));
		netherCommand = add(new TextSetting("Nether Command", "Command for a random teleport in the nether", "/rtp nether", "/rtp nether"));
		endCommand = add(new TextSetting("End Command", "Command for a random teleport in the end", "/rtp end", "/rtp end"));
		minDelay = add(new NumberSetting("Min Delay", "Shortest time to look around after a teleport", 5, 2, 60, 1, " s"));
		maxDelay = add(new NumberSetting("Max Delay", "Longest time to look around after a teleport (a random time in between is used)", 10, 2, 60, 1, " s"));
		waitForScan = add(new BoolSetting("Wait For Scan", "Stay up to 5 s longer while loaded chunks are still being scanned", true));
		fastScan = add(new BoolSetting("Fast Scan", "Scan new chunks four times faster while hunting (finds bases sooner, costs some FPS)", true));
		skipEmpty = add(new BoolSetting("Skip Empty Areas", "Teleport on as soon as the area is scanned and shows no sign of players (no old chunks, player blocks or portals) instead of waiting the delay; areas with signs of players get the full delay", true));
		teleportTimeout = add(new NumberSetting("Teleport Timeout", "Send the command again if no teleport happened after this long", 20, 5, 120, 1, " s"));

		add(new SectionSetting("Wanted Base"));
		minChests = add(new NumberSetting("Min Chests", "Chests, trapped chests and barrels the base needs (0 = any)", 10, 0, 200, 1, ""));
		minShulkers = add(new NumberSetting("Min Shulkers", "Placed shulker boxes that are enough on their own (0 = off)", 0, 0, 50, 1, ""));
		onlyIntact = add(new BoolSetting("Only Not Raided", "Skip bases that Raid Check marks as raided", true));
		allowMaybe = add(new BoolSetting("Allow Maybe Raided", "Also stop at bases marked 'Maybe raided'", false)).visibleWhen(onlyIntact::isOn);

		add(new SectionSetting("Trails"));
		stopOnTrail = add(new BoolSetting("Stop On Trail", "Stop when TrailFinder sees old chunks (where players went before) running through fresh land, so you can follow the trail", true));
		minOldChunks = add(new NumberSetting("Min Old Chunks", "Old chunks around you that count as a trail", 6, 1, 100, 1, "")).visibleWhen(stopOnTrail::isOn);
		minNewChunks = add(new NumberSetting("Min New Chunks", "Fresh chunks needed around it too (an area that is all old, like spawn, is not a trail)", 10, 0, 300, 1, "")).visibleWhen(stopOnTrail::isOn);

		add(new SectionSetting("Safety"));
		stopOnDamage = add(new BoolSetting("Stop On Damage", "Stop hunting when you take damage", true));
		stopNearPlayers = add(new BoolSetting("Stop Near Players", "Stop hunting when another player is close", true));
		playerDistance = add(new NumberSetting("Player Distance", "How close a player has to be", 48, 8, 256, 4, "m")).visibleWhen(stopNearPlayers::isOn);
		minHealth = add(new NumberSetting("Min Health", "Stop hunting below this many hearts", 5, 0, 10, 0.5, " hearts"));

		add(new SectionSetting("When Found"));
		keepHunting = add(new BoolSetting("Keep Hunting", "Note the base and teleport on instead of stopping", false));
		sound = add(new BoolSetting("Sound", "Ring when a base is found", true));
		logToFile = add(new BoolSetting("Log To File", "Write found bases to config/prismclient-bases.txt", true));
		showFound = add(new BoolSetting("Show Base", "Turn the other ESPs off, show only the found base (or trail) and fly the Freecam camera over it; turning Freecam off brings everything back", true))
			.visibleWhen(() -> !keepHunting.isOn());
		keepPlayers = add(new BoolSetting("Keep Player ESP", "Leave PlayerFinder on while showing, to see anyone coming to where you really stand", true))
			.visibleWhen(() -> !keepHunting.isOn() && showFound.isOn());
	}

	private static long now() { return System.currentTimeMillis(); }

	private BlockFinder blocks() { return PrismClient.modules().get(BlockFinder.class); }

	private TrailFinder trails() { return PrismClient.modules().get(TrailFinder.class); }

	@Override
	protected void onEnable() {
		endShow();
		state = State.SEND;
		nextSendAt = now();
		teleports = 0;
		found = 0;
		lastHealth = -1;
		status = "starting";
		blocks().enableBaseSearch(onlyIntact.isOn());
		if (stopOnTrail.isOn() && !trails().isEnabled()) trails().setEnabled(true);
		blocks().setFastScan(fastScan.isOn());
	}

	@Override
	protected void onDisable() {
		blocks().setFastScan(false);
	}

	/** Server text (chat and action bar): "wait 7 seconds" style cooldowns push the next teleport back. */
	public void onServerMessage(String text) {
		// only right after sending: a refusal answers our command (and other people's chat about waiting does not count)
		if (!isEnabled() || state != State.TELEPORTING) return;
		String t = text.toLowerCase(Locale.ROOT);
		if (!(t.contains("wait") || t.contains("cooldown") || t.contains("bekle") || t.contains("again in") || t.contains("tekrar"))) return;
		Matcher m = WAIT.matcher(t);
		if (!m.find()) return;
		long wait = Long.parseLong(m.group(1)) * 1000L + 1000L;
		// the command was refused: retry when the cooldown is over (not after the teleport timeout set when sending)
		nextSendAt = now() + wait;
		state = State.SEND;
		status = "server cooldown " + (wait / 1000) + " s";
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.getConnection() == null) { setEnabled(false); return; }
		if (safetyStop()) return;
		long t = now();
		switch (state) {
			case SEND -> {
				if (t < nextSendAt) { status = "next teleport in " + seconds(nextSendAt - t); return; }
				send();
			}
			case TELEPORTING -> {
				boolean moved = mc.level.dimension() != sentDimension || mc.player.position().distanceTo(sentFrom) > 48;
				if (moved) {
					teleports++;
					blocks().resetScan();   // the bases of the place we left must not count here
					long delay = (long) (1000 * ThreadLocalRandom.current().nextDouble(minDelay.get(), Math.max(minDelay.get(), maxDelay.get()) + 1e-9));
					lookUntil = t + delay;
					scanCap = lookUntil + 5000;
					arrivedAt = t;
					recorded = false;
					state = State.LOOKING;
					lastHealth = -1;
				} else if (t > teleportDeadline) {
					status = "no teleport, trying again";
					state = State.SEND;
				} else status = "teleporting...";
			}
			case LOOKING -> {
				BlockFinder.Base hit = wanted();
				if (hit != null) { onFound(hit); return; }
				if (stopOnTrail.isOn() && trails().isEnabled()) {
					int[] c = trails().around(mc.options.getEffectiveRenderDistance());
					if (c[0] >= minOldChunks.getInt() && c[1] >= minNewChunks.getInt() && trailLogged.add(key(mc.player.blockPosition()))) {
						onTrail(c);
						return;
					}
				}
				boolean scanning = waitForScan.isOn() && blocks().scanPending() > 0 && t < scanCap;
				// nobody was ever here: no reason to wait out the delay
				boolean empty = skipEmpty.isOn() && t - arrivedAt >= 2000 && blocks().scanPending() == 0 && activity() < 1;
				if ((t >= lookUntil && !scanning) || empty) {
					record();
					state = State.SEND;
					nextSendAt = t + (empty ? ThreadLocalRandom.current().nextLong(0, 1000) : 0);
					if (empty) status = "empty area, moving on";
					return;
				}
				status = "looking #" + teleports + " (" + seconds(Math.max(0, lookUntil - t)) + (scanning && t >= lookUntil ? ", scanning" : "") + ")";
			}
		}
	}

	/** How much player activity is around: old chunks, player-made blocks, portals, other players. */
	private double activity() {
		BlockFinder.Activity a = blocks().activity();
		int old = trails().isEnabled() ? trails().around(mc.options.getEffectiveRenderDistance())[0] : 0;
		int players = 0;
		for (Player o : mc.level.players()) if (o != mc.player) players++;
		return old + a.playerPoints() + (a.portalBlocks() > 0 ? 5 : 0) + players * 10;
	}

	/** Writes this landing and every base seen into the heat map (once per landing). */
	private void record() {
		if (recorded || mc.player == null) return;
		recorded = true;
		HeatMap map = HeatMap.get();
		for (BlockFinder.Base b : blocks().bases()) {
			BlockPos c = b.center();
			map.mark("base", c.getX(), c.getY(), c.getZ(), b.status().isEmpty() ? "unchecked" : b.status(),
				b.chests() + " chests, " + b.shulkers() + " shulkers, " + b.score() + " points");
		}
		map.visit(mc.player.getBlockX(), mc.player.getBlockZ(), activity());
	}

	private static String seconds(long ms) { return String.format(Locale.ROOT, "%.0f s", Math.ceil(ms / 1000.0)); }

	/** The rtp command for the chosen dimension (Rotate takes turns), without the slash; empty when not set. */
	private String nextCommand() {
		String which = dimension.get();
		if (which.equals("Rotate")) which = new String[]{"Overworld", "Nether", "End"}[rotate++ % 3];
		String cmd = (which.equals("Nether") ? netherCommand : which.equals("End") ? endCommand : overworldCommand).get().trim();
		return cmd.startsWith("/") ? cmd.substring(1) : cmd;
	}

	private void send() {
		String cmd = nextCommand();
		if (cmd.isEmpty()) { status = "no rtp command set"; setEnabled(false); return; }
		lastCommand = "/" + cmd;
		sentFrom = mc.player.position();
		sentDimension = mc.level.dimension();
		mc.getConnection().sendCommand(cmd);
		teleportDeadline = now() + teleportTimeout.getInt() * 1000L;
		nextSendAt = teleportDeadline;   // a missed teleport retries after the timeout
		state = State.TELEPORTING;
		status = "sent " + lastCommand;
	}

	/** The nearest base that matches the settings and was not reported yet. */
	private BlockFinder.Base wanted() {
		// only bases in the area loaded around us (a base list from before a teleport must never match)
		double reach = (mc.options.getEffectiveRenderDistance() + 2) * 16.0;
		for (BlockFinder.Base b : blocks().bases()) {
			if (b.center().distToCenterSqr(mc.player.position()) > reach * reach * 2) continue;
			boolean enough = (minChests.getInt() > 0 && b.chests() >= minChests.getInt()) || (minShulkers.getInt() > 0 && b.shulkers() >= minShulkers.getInt())
				|| (minChests.getInt() == 0 && minShulkers.getInt() == 0);
			if (!enough) continue;
			if (onlyIntact.isOn() && !(b.status().equals("Intact") || (allowMaybe.isOn() && b.status().equals("Maybe raided")))) continue;
			if (logged.contains(key(b))) continue;
			return b;
		}
		return null;
	}

	private String key(BlockFinder.Base b) { return key(b.center()); }

	private String key(BlockPos c) {
		return mc.level.dimension().identifier() + ":" + (c.getX() >> 4) + "," + (c.getZ() >> 4);
	}

	private void onFound(BlockFinder.Base b) {
		record();
		found++;
		logged.add(key(b));
		BlockPos c = b.center();
		String dim = mc.level.dimension().identifier().getPath();
		var msg = Component.literal("[Legit] ").withStyle(ChatFormatting.LIGHT_PURPLE)
			.append(Component.literal("BaseHunter found a base at ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(c.getX() + " " + c.getY() + " " + c.getZ()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
			.append(Component.literal(" (" + dim + ", " + blocks().distance(c) + "m " + blocks().direction(c) + ", " + b.chests() + " chests, "
				+ b.shulkers() + " shulkers, " + b.score() + " points" + (b.status().isEmpty() ? "" : ", " + b.status()) + ", teleport #" + teleports + ")").withStyle(ChatFormatting.GRAY));
		mc.gui.getChat().addMessage(msg);
		if (sound.isOn()) mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1f);
		if (logToFile.isOn()) log(b, dim);
		if (keepHunting.isOn()) {
			status = "found " + found + ", hunting on";
			state = State.SEND;
			nextSendAt = now() + 1000;
		} else {
			status = "found a base";
			setEnabled(false);
			if (showFound.isOn()) {
				hideOthers(BlockFinder.class);
				blocks().focus(b);
				blocks().show(b, "Found base", "Only this base is shown. Turn Freecam off to come back and get the other ESPs back.");
				showing = true;
			}
		}
	}

	/** Old chunks through fresh land: a trail someone made. Reported like a base, then hunting stops (or goes on). */
	private void onTrail(int[] c) {
		record();
		found++;
		BlockPos p = mc.player.blockPosition();
		String dim = mc.level.dimension().identifier().getPath();
		String dir = trails().trailDirection(mc.options.getEffectiveRenderDistance());
		var msg = Component.literal("[Legit] ").withStyle(ChatFormatting.LIGHT_PURPLE)
			.append(Component.literal("BaseHunter found a trail at ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(p.getX() + " " + p.getY() + " " + p.getZ()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
			.append(Component.literal(" (" + dim + ", " + c[0] + " old chunks among " + c[1] + " new, old chunks lie " + dir + ", teleport #" + teleports
				+ "). Follow the green squares.").withStyle(ChatFormatting.GRAY));
		mc.gui.getChat().addMessage(msg);
		HeatMap.get().mark("trail", p.getX(), p.getY(), p.getZ(), "trail", c[0] + " old chunks, towards " + dir);
		if (sound.isOn()) mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 0.7f);
		if (logToFile.isOn()) {
			String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
			String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " | " + server + " | " + dim + " | "
				+ p.getX() + " " + p.getY() + " " + p.getZ() + " | TRAIL: " + c[0] + " old chunks among " + c[1] + " new, towards " + dir + System.lineSeparator();
			try {
				Files.writeString(logFile(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (IOException e) {
				PrismClient.LOG.warn("Could not write the base log", e);
			}
		}
		if (keepHunting.isOn()) {
			status = "found " + found + ", hunting on";
			state = State.SEND;
			nextSendAt = now() + 1000;
		} else {
			status = "found a trail";
			setEnabled(false);
			if (showFound.isOn()) {
				hideOthers(TrailFinder.class);
				// high above you, looking down at the green trail
				Freecam fc = PrismClient.modules().get(Freecam.class);
				if (!fc.isEnabled()) fc.setEnabled(true);
				fc.moveTo(mc.player.position().add(0, 70, -45), 0, 57);
				showing = true;
			}
		}
	}

	private void log(BlockFinder.Base b, String dim) {
		BlockPos c = b.center();
		String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
		String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " | " + server + " | " + dim + " | "
			+ c.getX() + " " + c.getY() + " " + c.getZ() + " | " + b.chests() + " chests, " + b.shulkers() + " shulkers | " + b.score() + " points | "
			+ (b.status().isEmpty() ? "unchecked" : b.status()) + (b.evidence().isEmpty() ? "" : " (" + String.join(", ", b.evidence()) + ")") + System.lineSeparator();
		try {
			Files.writeString(logFile(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			PrismClient.LOG.warn("Could not write the base log", e);
		}
	}

	public static Path logFile() { return FabricLoader.getInstance().getConfigDir().resolve("prismclient-bases.txt"); }

	/** Stops (and says why) when hurt, low on health or someone is close. */
	private boolean safetyStop() {
		Player p = mc.player;
		String why = null;
		float hp = p.getHealth();
		if (stopOnDamage.isOn() && lastHealth >= 0 && hp < lastHealth - 0.01f) why = "you took damage";
		lastHealth = hp;
		if (why == null && hp < minHealth.get() * 2) why = "low health";
		if (why == null && stopNearPlayers.isOn()) {
			double d2 = playerDistance.get() * playerDistance.get();
			for (Player o : mc.level.players()) {
				if (o != p && !o.isSpectator() && o.distanceToSqr(p) < d2) { why = o.getGameProfile().name() + " is " + Math.round(o.distanceTo(p)) + "m away"; break; }
			}
		}
		if (why == null) return false;
		mc.gui.getChat().addMessage(Component.literal("[Legit] ").withStyle(ChatFormatting.LIGHT_PURPLE)
			.append(Component.literal("BaseHunter stopped: " + why).withStyle(ChatFormatting.RED)));
		status = "stopped: " + why;
		setEnabled(false);
		return true;
	}

	/**
	 * The Quick RTP key. While hunting it skips the wait and teleports now; otherwise it sends one rtp command for
	 * the chosen dimension, and when it lands BlockFinder forgets the old area and scans the new one fast.
	 */
	public void quickRtp() {
		if (mc.player == null || mc.getConnection() == null) return;
		if (isEnabled()) {
			if (state == State.LOOKING || state == State.SEND) { state = State.SEND; nextSendAt = now(); }
			return;
		}
		String cmd = nextCommand();
		if (cmd.isEmpty()) return;
		lastCommand = "/" + cmd;
		quickFrom = mc.player.position();
		quickDimension = mc.level.dimension();
		quickUntil = now() + teleportTimeout.getInt() * 1000L;
		mc.getConnection().sendCommand(cmd);
	}

	/** Runs every tick, also while hunting is off, to finish a Quick RTP. */
	public void watchQuickRtp() {
		if (quickFrom != null && mc.player != null && mc.level != null) {
			if (mc.level.dimension() != quickDimension || mc.player.position().distanceTo(quickFrom) > 48) {
				blocks().resetScan();
				blocks().setFastScan(true);
				quickFastUntil = now() + 15000;
				quickFrom = null;
			} else if (now() > quickUntil) quickFrom = null;
		}
		if (quickFastUntil != 0 && now() > quickFastUntil && !isEnabled()) {
			blocks().setFastScan(false);
			quickFastUntil = 0;
		}
	}

	/** Switches every ESP off except {@code keep} (and PlayerFinder when wanted), remembering which were on. */
	private void hideOthers(Class<? extends Module> keep) {
		endShow();
		List<Class<? extends Module>> esp = List.of(StorageFinder.class, SpawnerFinder.class, OreFinder.class, ItemFinder.class,
			PlayerFinder.class, TrailFinder.class, BlockFinder.class);
		for (Class<? extends Module> c : esp) {
			if (c == keep || (c == PlayerFinder.class && keepPlayers.isOn())) continue;
			Module m = PrismClient.modules().get(c);
			if (m.isEnabled()) { m.setEnabled(false); hidden.add(m); }
		}
	}

	/** Brings back what showing a find switched off. */
	private void endShow() {
		if (!showing && hidden.isEmpty()) return;
		showing = false;
		blocks().focus(null);
		for (Module m : hidden) m.setEnabled(true);
		hidden.clear();
	}

	/** Every tick: showing a find ends when Freecam is turned off. */
	public void watchShow() {
		if (showing && Freecam.active() == null) endShow();
	}

	public boolean showing() { return showing; }

	public String status() { return status; }
	public int teleports() { return teleports; }
	public int found() { return found; }
	public String lastCommand() { return lastCommand; }

	@Override
	public String getInfo() { return "#" + teleports + (found > 0 ? " found " + found : ""); }
}
