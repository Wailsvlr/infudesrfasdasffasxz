package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.render.Highlight;
import com.wailsvlr.prism.render.HighlightStyle;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ColorSetting;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.SectionSetting;
import com.wailsvlr.prism.util.ColorUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Highlights other players, with name tags (health, distance, held item), tracers and an alert when someone comes close. */
public class PlayerFinder extends FinderModule {
	private final BoolSetting invisible, spectators, selfInFreecam;
	private final ModeSetting colorMode;
	private final ColorSetting color, nearColor, farColor, friendColor;
	private final BoolSetting labelHealth, labelItem, notify, notifySound, hudCount;

	private final Set<UUID> inRange = new HashSet<>();
	private ClientLevel lastLevel;
	private volatile List<Target> targets = List.of();

	public record Target(String name, double distance) { }

	public PlayerFinder() {
		super("PlayerFinder", "Highlights other players and shows their name, health and distance above them");
		add(new SectionSetting("Targets"));
		invisible = add(new BoolSetting("Invisible", "Also show players with the invisibility effect", true));
		spectators = add(new BoolSetting("Spectators", "Also show players in spectator mode", false));
		selfInFreecam = add(new BoolSetting("Self In Freecam", "Highlight your own player while Freecam is on", false));

		addLookSettings("2D", 15, "2D", "Outline", "Fill", "Both", "Corners", "Corners + Fill");

		add(new SectionSetting("Colors"));
		colorMode = add(new ModeSetting("Color Mode", "Static: one colour. Distance: near to far. Health: red when low. Team: the scoreboard team colour",
			"Static", "Static", "Distance", "Health", "Team"));
		color = add(new ColorSetting("Player Color", "Colour of player highlights", 0xFF38BDF8)).visibleWhen(() -> colorMode.is("Static"));
		nearColor = add(new ColorSetting("Near Color", "Colour of players close to you", 0xFFEF4444)).visibleWhen(() -> colorMode.is("Distance"));
		farColor = add(new ColorSetting("Far Color", "Colour of players at the end of the range", 0xFF22C55E)).visibleWhen(() -> colorMode.is("Distance"));
		friendColor = add(new ColorSetting("Friend Color", "Colour of players on your friends list (and a star in their label)", 0xFF4ADE80));

		addExtraSettings(256, true, true);
		labelHealth = add(new BoolSetting("Label Health", "Add health (with absorption) to the label", true)).visibleWhen(() -> labels.isOn());
		labelItem = add(new BoolSetting("Label Held Item", "Add the item in their main hand to the label", false)).visibleWhen(() -> labels.isOn());
		notify = add(new BoolSetting("Notify", "Chat message when a player comes into range", false));
		notifySound = add(new BoolSetting("Notify Sound", "Play a ping with the message", true)).visibleWhen(notify::isOn);
		hudCount = add(new BoolSetting("HUD Counter", "Show the number of players in range in the HUD", true));
	}

	@Override
	protected void onEnable() { inRange.clear(); }

	@Override
	protected void onDisable() { targets = List.of(); }

	/** Players to show this frame. */
	private List<Player> players() {
		List<Player> out = new ArrayList<>();
		if (mc.level == null || mc.player == null) return out;
		boolean freecam = Freecam.active() != null;
		double r2 = range.get() * range.get();
		for (Player p : mc.level.players()) {
			if (p == mc.player && !(freecam && selfInFreecam.isOn())) continue;
			if (p.isRemoved() || !p.isAlive()) continue;
			if (p.isSpectator() && !spectators.isOn()) continue;
			if (p.isInvisible() && !invisible.isOn()) continue;
			if (p != mc.player && p.distanceToSqr(mc.player) > r2) continue;
			out.add(p);
		}
		return out;
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) return;
		if (mc.level != lastLevel) { lastLevel = mc.level; inRange.clear(); }
		List<Target> list = new ArrayList<>();
		Set<UUID> now = new HashSet<>();
		Player near = null;
		double nd = Double.MAX_VALUE;
		for (Player p : players()) {
			if (p == mc.player) continue;
			double d2 = p.distanceToSqr(mc.player);
			if (d2 < nd) { nd = d2; near = p; }
			list.add(new Target(p.getGameProfile().name(), Math.sqrt(p.distanceToSqr(mc.player))));
			now.add(p.getUUID());
			if (notify.isOn() && !inRange.contains(p.getUUID())) announce(p);
		}
		inRange.clear();
		inRange.addAll(now);
		list.sort(Comparator.comparingDouble(Target::distance));
		targets = list;
		if (near == null) nearest(null, null, 0);
		else nearest(near.position().add(0, near.getBbHeight() / 2, 0), near.getGameProfile().name(), colorOf(near, Math.sqrt(nd)));
	}

	@Override
	protected boolean hasAlerts() { return notify.isOn(); }

	private void announce(Player p) {
		int dist = (int) Math.round(Math.sqrt(p.distanceToSqr(mc.player)));
		Component msg = Component.literal("[Legit] ").withStyle(ChatFormatting.LIGHT_PURPLE)
			.append(Component.literal(p.getGameProfile().name()).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" is in range ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal("(" + dist + "m)").withStyle(ChatFormatting.GRAY));
		mc.gui.getChat().addMessage(msg);
		card(p.getUUID(), p.getGameProfile().name() + " is in range (" + dist + "m)");
		if (notifySound.isOn()) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
	}

	private int colorOf(Player p, double dist) {
		if (com.wailsvlr.prism.util.Friends.isFriend(p.getGameProfile().name())) return friendColor.rgb();
		return switch (colorMode.get()) {
			case "Distance" -> ColorUtil.lerp(0xFF000000 | nearColor.rgb(), 0xFF000000 | farColor.rgb(), (float) Math.min(1, dist / Math.max(1, range.get()))) & 0xFFFFFF;
			case "Health" -> {
				float f = Math.max(0, Math.min(1, p.getHealth() / Math.max(1, p.getMaxHealth())));
				yield ColorUtil.lerp(0xFFEF4444, 0xFF22C55E, f) & 0xFFFFFF;
			}
			case "Team" -> p.getTeamColor() & 0xFFFFFF;
			default -> color.rgb((float) (p.getId() * 0.37));
		};
	}

	private String label(Player p, double dist) {
		StringBuilder sb = new StringBuilder((com.wailsvlr.prism.util.Friends.isFriend(p.getGameProfile().name()) ? "\u2605 " : "") + p.getGameProfile().name());
		if (labelHealth.isOn()) sb.append(' ').append(Math.round(p.getHealth() + p.getAbsorptionAmount())).append('❤');
		if (labelDistance.isOn() && p != mc.player) sb.append(' ').append(Math.round(dist)).append('m');
		if (labelItem.isOn()) {
			ItemStack held = p.getMainHandItem();
			if (!held.isEmpty()) sb.append(" [").append(held.getHoverName().getString()).append(']');
		}
		return sb.toString();
	}

	public void collect(List<Highlight> out) {
		if (!isEnabled() || mc.player == null) return;
		HighlightStyle st = style();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 me = mc.player.getPosition(partial);
		for (Player p : players()) {
			// the box follows the smooth (interpolated) position, not the last tick
			Vec3 pos = p.getPosition(partial);
			AABB box = p.getBoundingBox().move(pos.subtract(p.position()));
			double dist = pos.distanceTo(me);
			// 0.75 puts the tag above the vanilla name tag instead of on top of it
			float hp = Math.max(0, Math.min(1, (p.getHealth() + p.getAbsorptionAmount()) / Math.max(1, p.getMaxHealth())));
			out.add(new Highlight(box, colorOf(p, dist), st, labels.isOn() ? label(p, dist) : null, st.screen2D() ? 0.3 : 0.75, hp));
		}
	}

	public List<Target> targets() { return targets; }
	public boolean showHudCount() { return hudCount.isOn(); }
	public int hudColor() { return colorMode.is("Static") ? color.rgb() : colorMode.is("Distance") ? nearColor.rgb() : 0x38BDF8; }

	@Override
	public String getInfo() { return mode.get() + " " + targets.size(); }
}
