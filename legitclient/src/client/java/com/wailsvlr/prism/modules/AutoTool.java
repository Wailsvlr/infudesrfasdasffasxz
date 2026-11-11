package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * While you hold attack on a block, picks the hotbar tool that breaks it fastest (a tool that also gets the drops
 * wins a tie), and goes back to the item you had when you stop. Only the selected hotbar slot changes, as when you
 * press a number key.
 */
public class AutoTool extends Module {
	private final BoolSetting switchBack, protect;
	private final NumberSetting minDurability;
	/** The slot you had before the switch (-1: nothing switched). */
	private int previous = -1;
	private int idle;

	public AutoTool() {
		super("AutoTool", "While you mine, holds the hotbar tool that breaks the block fastest, then goes back to your item", Category.WORLD);
		switchBack = add(new BoolSetting("Switch Back", "Go back to the item you held when you stop mining", true));
		protect = add(new BoolSetting("Save Tools", "Do not pick tools that are about to break", true));
		minDurability = add(new NumberSetting("Min Durability", "Uses left below which a tool is not picked", 10, 1, 100, 1, "")).visibleWhen(protect::isOn);
	}

	@Override
	protected void onDisable() {
		if (previous >= 0 && mc.player != null && switchBack.isOn()) mc.player.getInventory().setSelectedSlot(previous);
		previous = -1;
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.gameMode == null) return;
		Inventory inv = mc.player.getInventory();
		boolean mining = mc.screen == null && mc.options.keyAttack.isDown() && !mc.player.getAbilities().instabuild
			&& mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK;
		if (mining) {
			idle = 0;
			BlockState state = mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos());
			if (state.isAir()) return;
			int best = bestSlot(inv, state);
			if (best >= 0 && best != inv.getSelectedSlot()) {
				if (previous < 0) previous = inv.getSelectedSlot();
				inv.setSelectedSlot(best);
			}
		} else if (previous >= 0 && ++idle > 3) {
			if (switchBack.isOn()) inv.setSelectedSlot(previous);
			previous = -1;
		}
	}

	private boolean usable(ItemStack s) {
		return !protect.isOn() || !s.isDamageableItem() || s.getMaxDamage() - s.getDamageValue() > minDurability.getInt();
	}

	private static float score(ItemStack s, BlockState state) {
		float speed = s.getDestroySpeed(state);
		if (state.requiresCorrectToolForDrops() && s.isCorrectToolForDrops(state)) speed += 0.5f;
		return speed;
	}

	/** The fastest usable hotbar slot, or -1 when the held item is already as good. */
	public int bestSlot(Inventory inv, BlockState state) {
		int sel = inv.getSelectedSlot();
		ItemStack held = inv.getItem(sel);
		float bestScore = usable(held) ? score(held, state) : 0;
		int best = -1;
		for (int i = 0; i < 9; i++) {
			if (i == sel) continue;
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || !usable(s)) continue;
			float sc = score(s, state);
			if (sc > bestScore + 0.01f) { bestScore = sc; best = i; }
		}
		return best;
	}
}
