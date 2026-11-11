package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.render.Highlight;
import com.wailsvlr.prism.render.HighlightStyle;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ChipsSetting;
import com.wailsvlr.prism.setting.ColorSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.SectionSetting;
import com.wailsvlr.prism.util.BlockEntityScanner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Highlights mob spawners (and optionally trial spawners), with mob-type colours, labels and alerts. */
public class SpawnerFinder extends FinderModule {
	private final BoolSetting mobSpawners, trialSpawners;
	private final ColorSetting color, trialColor;
	private final BoolSetting colorByMob;
	private final BoolSetting activationRange;
	private final NumberSetting activationOpacity;
	private final BoolSetting notify, notifySound, hudCount;

	private volatile List<Target> targets = List.of();
	private final Set<BlockPos> announced = new HashSet<>();
	private ClientLevel lastLevel;
	private int tickCounter;

	public record Target(BlockPos pos, boolean trial, String mob, int mobColor) { }

	/** Colours for the mobs that come from dungeon / mineshaft / fortress / stronghold spawners. */
	private static final Map<String, Integer> MOB_COLORS = Map.of(
		"zombie", 0x4ADE80, "skeleton", 0xE5E7EB, "spider", 0xDC2626, "cave_spider", 0x3B82F6,
		"blaze", 0xF59E0B, "silverfish", 0x94A3B8, "magma_cube", 0xEA580C, "creeper", 0x22C55E,
		"husk", 0xD6B370, "stray", 0x93C5FD);

	public SpawnerFinder() {
		super("SpawnerFinder", "Highlights mob spawners, shows which mob they spawn and alerts you when one is found");
		add(new SectionSetting("Targets"));
		ChipsSetting chips = add(new ChipsSetting("Spawners", "Click to show or hide, right click to change the colour"));
		mobSpawners = add(new BoolSetting("Mob Spawners", "Highlight mob spawners", true));
		trialSpawners = add(new BoolSetting("Trial Spawners", "Also highlight trial spawners (trial chambers)", false));

		addLookSettings("Both", 28);

		add(new SectionSetting("Colors"));
		colorByMob = add(new BoolSetting("Color By Mob", "Colour each spawner by the mob it spawns (zombie green, spider red…)", false));
		color = add(new ColorSetting("Spawner Color", "Colour of spawner highlights", 0xFFFF3B6B));
		trialColor = add(new ColorSetting("Trial Spawner Color", "Colour of trial spawner highlights", 0xFFF59E0B));
		chips.chip(mobSpawners, color, () -> !colorByMob.isOn()).chip(trialSpawners, trialColor);

		addExtraSettings(128, true, true);
		activationRange = add(new BoolSetting("Activation Range", "Show the 16-block area in which the spawner is active", false));
		activationOpacity = add(new NumberSetting("Range Opacity", "Opacity of the activation area", 25, 5, 100, 1, "%")).visibleWhen(activationRange::isOn);
		notify = add(new BoolSetting("Notify", "Chat message when a new spawner is found", true));
		notifySound = add(new BoolSetting("Notify Sound", "Play a ping with the message", true)).visibleWhen(notify::isOn);
		hudCount = add(new BoolSetting("HUD Counter", "Show the number of spawners in the HUD", true));
	}

	@Override
	protected void onEnable() {
		announced.clear();
		tickCounter = 0;
	}

	@Override
	protected void onDisable() {
		targets = List.of();
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) return;
		if (tickCounter-- > 0) return;
		tickCounter = scanInterval.getInt();
		rescan();
	}

	public void rescan() {
		if (mc.level == null || mc.player == null) return;
		if (mc.level != lastLevel) { lastLevel = mc.level; announced.clear(); }   // new world or dimension
		List<BlockEntity> found = BlockEntityScanner.scan(mc.level, mc.player.getEyePosition(), range.get(),
			be -> (be instanceof SpawnerBlockEntity && mobSpawners.isOn()) || (be instanceof TrialSpawnerBlockEntity && trialSpawners.isOn()));
		List<Target> out = new ArrayList<>();
		for (BlockEntity be : found) {
			String mob = "Trial Spawner", id = "";
			boolean trial = be instanceof TrialSpawnerBlockEntity;
			if (be instanceof SpawnerBlockEntity s) {
				Entity display = s.getSpawner().getOrCreateDisplayEntity(mc.level, be.getBlockPos());
				if (display != null) {
					mob = display.getType().getDescription().getString();
					id = EntityType.getKey(display.getType()).getPath();
				} else mob = "Empty";
			}
			int mobColor = MOB_COLORS.getOrDefault(id, id.isEmpty() ? 0xFF3B6B : 0xFF000000 | (id.hashCode() * 0x9E3779B1));
			Target t = new Target(be.getBlockPos().immutable(), trial, mob, mobColor & 0xFFFFFF);
			out.add(t);
			if (notify.isOn() && announced.add(t.pos())) announce(t);
		}
		targets = out;
		Target near = null;
		double nd = Double.MAX_VALUE;
		for (Target t : out) {
			double d = t.pos().distToCenterSqr(mc.player.position());
			if (d < nd) { nd = d; near = t; }
		}
		if (near == null) nearest(null, null, 0);
		else nearest(net.minecraft.world.phys.Vec3.atCenterOf(near.pos()), near.trial() ? "Trial spawner" : near.mob() + " spawner",
			near.trial() ? trialColor.rgb() : colorByMob.isOn() ? near.mobColor() : color.rgb());
	}

	@Override
	protected boolean hasAlerts() { return notify.isOn(); }

	private void announce(Target t) {
		BlockPos p = t.pos();
		int dist = (int) Math.round(Math.sqrt(p.distToCenterSqr(mc.player.position())));
		Component msg = Component.literal("[Legit] ").withStyle(ChatFormatting.LIGHT_PURPLE)
			.append(Component.literal((t.trial() ? "Trial spawner" : t.mob() + " spawner") + " found at ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(p.getX() + " " + p.getY() + " " + p.getZ()).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" (" + dist + "m)").withStyle(ChatFormatting.GRAY));
		mc.gui.getChat().addMessage(msg);
		card(p, (t.trial() ? "Trial spawner" : t.mob() + " spawner") + " at " + p.getX() + " " + p.getY() + " " + p.getZ() + " (" + dist + "m)");
		if (notifySound.isOn() && mc.player != null) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.6f);
	}

	public void collect(List<Highlight> out) {
		if (!isEnabled()) return;
		HighlightStyle st = style();
		for (Target t : targets) {
			int rgb = t.trial() ? trialColor.rgb() : colorByMob.isOn() ? t.mobColor() : color.rgb((t.pos().getX() + t.pos().getZ()) * 0.02f);
			String label = null;
			if (labels.isOn()) {
				label = t.trial() ? "Trial Spawner" : t.mob() + " Spawner";
				if (labelDistance.isOn() && mc.player != null) label += " " + Math.round(Math.sqrt(t.pos().distToCenterSqr(mc.player.position()))) + "m";
			}
			out.add(new Highlight(new AABB(t.pos()), rgb, st, label));
			if (activationRange.isOn() && !t.trial()) {
				// spawners only work while a player is within 16 blocks
				HighlightStyle area = new HighlightStyle("Outline", activationOpacity.getFloat() / 100f, 0, Math.max(1f, st.lineWidth() * 0.6f),
					st.throughWalls(), false, 0, st.distanceFade(), st.range(), false, 1, 0, false);
				out.add(new Highlight(new AABB(t.pos()).inflate(16), rgb, area, null));
			}
		}
	}

	public List<Target> targets() { return targets; }
	public boolean showHudCount() { return hudCount.isOn(); }
}
