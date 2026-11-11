package com.wailsvlr.prism.test;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A stand-in for a survival server's /rtp, /rtp nether and /rtp end in the test world: teleports to planned spots
 * (or a default one per dimension) and, like real servers, refuses with "You must wait N seconds" when used too often.
 */
public class TestServerCommands implements ModInitializer {
	private static final Deque<String> PLAN = new ArrayDeque<>();
	private static final long COOLDOWN_MS = 4000;
	private static long lastTeleport;
	public static volatile String lastRequest = "";
	public static volatile int teleports, refused;
	/** When each successful teleport happened. */
	public static final List<Long> TIMES = new java.util.concurrent.CopyOnWriteArrayList<>();
	/** /sell: what the closed sell menus held (item id -> count), and how many menus were opened. */
	public static final java.util.Map<String, Integer> SOLD = new java.util.concurrent.ConcurrentHashMap<>();
	public static volatile int sellMenus;
	/** The money shown on the sidebar (objective "bal", line "money"); /sell pays $10 per item. */
	public static volatile long balance = 12345;

	/** Teleport commands (run as the player) for the next /rtp calls, in order. */
	public static synchronized void plan(List<String> commands) { PLAN.clear(); PLAN.addAll(commands); }

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(Commands.literal("rtp")
			.executes(c -> rtp(c, "overworld"))
			.then(Commands.literal("nether").executes(c -> rtp(c, "nether")))
			.then(Commands.literal("end").executes(c -> rtp(c, "end")))));
		// like a survival server's /sell: a small "Sell" chest menu; whatever is inside when it closes is sold (removed)
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(Commands.literal("sell").executes(c -> {
			if (!(c.getSource().getEntity() instanceof ServerPlayer player)) return 0;
			sellMenus++;
			SimpleContainer box = new SimpleContainer(27);
			player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, box, 3) {
				@Override
				public void removed(Player who) {
					super.removed(who);
					int count = 0;
					for (int i = 0; i < box.getContainerSize(); i++) {
						ItemStack s = box.removeItemNoUpdate(i);
						count += s.getCount();
						if (!s.isEmpty()) SOLD.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString() + (s.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? "=" + s.getHoverName().getString() : ""), s.getCount(), Integer::sum);
					}
					if (count > 0 && who instanceof ServerPlayer sp) {
						balance += count * 10L;
						showBalance(sp.level().getServer());
					}
				}
			}, Component.literal("Sell")));
			return 1;
		})));
		// like an auction house: a 6-row "Auction House" menu of listings whose lore holds "Price: $..."
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(Commands.literal("ah").executes(c -> {
			if (!(c.getSource().getEntity() instanceof ServerPlayer player)) return 0;
			SimpleContainer box = new SimpleContainer(54);
			Object[][] listings = {{"elytra", 1, "$5.5M"}, {"elytra", 1, "$4.2M"}, {"diamond", 64, "$6,400"}, {"diamond", 32, "$4,800"},
				{"totem_of_undying", 1, "$25K"}, {"golden_carrot", 64, "$640"}};
			for (int i = 0; i < listings.length; i++) {
				ItemStack s = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace((String) listings[i][0])), (Integer) listings[i][1]);
				s.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
					Component.literal("Seller: Bob"), Component.literal("Price: " + listings[i][2]), Component.literal("Expires in 2d"))));
				box.setItem(i * 2, s);
			}
			player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.sixRows(id, inv, box), Component.literal("Auction House (Page 1)")));
			return 1;
		})));
	}

	/** Writes the balance into the sidebar line, the way server scoreboards show money. */
	public static void showBalance(net.minecraft.server.MinecraftServer server) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
			"scoreboard players display name money bal \"Money: $" + String.format(java.util.Locale.ROOT, "%,d", balance) + "\"");
	}

	private static synchronized int rtp(CommandContext<CommandSourceStack> c, String dim) {
		CommandSourceStack src = c.getSource();
		lastRequest = dim;
		long now = System.currentTimeMillis();
		if (now - lastTeleport < COOLDOWN_MS) {
			refused++;
			long left = (COOLDOWN_MS - (now - lastTeleport) + 999) / 1000;
			src.sendFailure(Component.literal("You must wait " + left + " seconds before using /rtp again!"));
			return 0;
		}
		lastTeleport = now;
		teleports++;
		TIMES.add(now);
		String cmd = PLAN.isEmpty() ? switch (dim) {
			case "nether" -> "execute in minecraft:the_nether run tp @s 0.5 129 0.5";
			case "end" -> "execute in minecraft:the_end run tp @s 20.5 90 20.5";
			default -> "execute in minecraft:overworld run tp @s " + (1500.5 + teleports * 300) + " -60 0.5";
		} : PLAN.poll();
		// the player may not be allowed to /tp, so the server runs it on the player like a real /rtp plugin would
		if (src.getEntity() != null) cmd = cmd.replace("@s", src.getEntity().getStringUUID());
		src.getServer().getCommands().performPrefixedCommand(src.getServer().createCommandSourceStack().withSuppressedOutput(), cmd);
		return 1;
	}
}
