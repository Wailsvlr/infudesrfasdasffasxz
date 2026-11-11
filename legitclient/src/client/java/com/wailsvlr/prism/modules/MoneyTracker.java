package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.SectionSetting;
import com.wailsvlr.prism.setting.TextSetting;
import com.wailsvlr.prism.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Reads your balance from the server's sidebar (the line with a "$" amount) and shows it in the HUD with what you made
 * this session and per hour. After an AutoSell it says in chat how much the sale brought in.
 */
public class MoneyTracker extends Module {
	private final BoolSetting hud, saleMessages;
	private final TextSetting lineFilter;
	private final NumberSetting window;

	private Double balance, sessionStart;
	private final Deque<double[]> samples = new ArrayDeque<>();   // {time ms, balance}
	private ClientLevel lastLevel;
	private int timer;
	// AutoSell sale being waited for
	private Double saleBefore;
	private int saleItems;
	private long saleDeadline;
	private double lastSale, soldTotal;

	public MoneyTracker() {
		super("MoneyTracker", "Shows your balance from the sidebar, what you made this session and per hour, and what each AutoSell brought in", Category.MISC);
		add(new SectionSetting("Reading"));
		lineFilter = add(new TextSetting("Line Contains", "Only read sidebar lines with this text (leave empty: the first line with a $ amount)", "", "e.g. money"));
		add(new SectionSetting("Show"));
		hud = add(new BoolSetting("HUD Line", "Balance, session gain and per hour in the HUD", true));
		window = add(new NumberSetting("Rate Window", "Minutes the per-hour rate looks back over", 60, 5, 180, 5, " min"));
		saleMessages = add(new BoolSetting("Sale Messages", "After AutoSell: \"+$1.2M for 1925 items\" in chat", true));
	}

	@Override
	protected void onEnable() { reset(); }

	private void reset() {
		balance = sessionStart = null;
		samples.clear();
		saleBefore = null;
		soldTotal = 0;
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) return;
		if (mc.level != lastLevel) {
			// a new server or world: start the session again (a dimension change keeps the same scoreboard)
			if (lastLevel == null || mc.getConnection() == null || lastLevel.getScoreboard() != mc.level.getScoreboard()) reset();
			lastLevel = mc.level;
		}
		if (--timer > 0) return;
		timer = 10;
		Double now = read(mc.level.getScoreboard());
		if (now == null) return;
		long t = System.currentTimeMillis();
		if (sessionStart == null) sessionStart = now;
		if (balance == null || Math.abs(now - balance) > 1e-6) samples.addLast(new double[]{t, now});
		balance = now;
		long keep = (long) (window.get() * 60_000);
		while (samples.size() > 1 && t - samples.peekFirst()[0] > keep) samples.pollFirst();
		checkSale(t);
	}

	/** The balance on the sidebar: the first line (name and score text) with a "$" amount. */
	private Double read(Scoreboard board) {
		Objective obj = board.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (obj == null) return null;
		String want = lineFilter.get().trim().toLowerCase(Locale.ROOT);
		for (PlayerScoreEntry e : board.listPlayerScores(obj)) {
			if (e.isHidden()) continue;
			Component name = e.display() != null ? e.display() : PlayerTeam.formatNameForTeam(board.getPlayersTeam(e.owner()), e.ownerName());
			String line = name.getString() + " " + e.formatValue(obj.numberFormatOrDefault(net.minecraft.network.chat.numbers.StyledFormat.SIDEBAR_DEFAULT)).getString();
			if (!want.isEmpty() && !line.toLowerCase(Locale.ROOT).contains(want)) continue;
			Double d = Money.dollars(line);
			if (d != null) return d;
		}
		return null;
	}

	// ---------------------------------------------------------------- AutoSell

	/** AutoSell is about to sell: remember the balance before. */
	public void beginSale() { saleBefore = balance; saleDeadline = 0; }

	/** AutoSell finished: wait a few seconds for the balance to go up. */
	public void endSale(int items) {
		if (saleBefore == null) return;
		saleItems = items;
		saleDeadline = System.currentTimeMillis() + 8000;
	}

	private void checkSale(long t) {
		if (saleBefore == null || saleDeadline == 0 || balance == null) return;
		double gain = balance - saleBefore;
		if (gain > 0) {
			// keep waiting a moment in case the second menu's money lands a second later
			if (t < saleDeadline - 6000) return;
			lastSale = gain;
			soldTotal += gain;
			if (isEnabled() && saleMessages.isOn() && mc.player != null)
				mc.gui.getChat().addMessage(Component.literal("[Legit] ").withStyle(ChatFormatting.LIGHT_PURPLE)
					.append(Component.literal("AutoSell: +$" + Money.format(gain)).withStyle(ChatFormatting.GREEN))
					.append(Component.literal(" for " + saleItems + " items (~$" + Money.format(gain / Math.max(1, saleItems)) + " each)").withStyle(ChatFormatting.GRAY)));
			saleBefore = null;
		} else if (t > saleDeadline) {
			saleBefore = null;   // the balance did not move (or the sidebar shows it too roughly)
		}
	}

	// ---------------------------------------------------------------- HUD

	public Double balance() { return balance; }
	public double session() { return balance == null || sessionStart == null ? 0 : balance - sessionStart; }
	public double lastSale() { return lastSale; }

	/** Money per hour over the rate window (0 until there are two readings a minute apart). */
	public double perHour() {
		if (samples.size() < 2 || balance == null) return 0;
		double[] first = samples.peekFirst();
		double hours = (System.currentTimeMillis() - first[0]) / 3_600_000.0;
		return hours < 1 / 60.0 ? 0 : (balance - first[1]) / hours;
	}

	public boolean showHud() { return isEnabled() && hud.isOn() && balance != null; }

	public String hudDetail() {
		double s = session(), r = perHour();
		String out = (s >= 0 ? "+" : "") + Money.format(s) + " this session";
		if (r != 0) out += "  ~" + Money.format(r) + "/h";
		if (soldTotal > 0) out += "  sold " + Money.format(soldTotal);
		return out;
	}
}
