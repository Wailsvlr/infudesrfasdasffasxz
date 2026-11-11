package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class Scaffold extends Module {

    private final BoolSetting tower;
    private final NumberSetting extend;

    public Scaffold() {
        super("Scaffold", "Places blocks under you while walking", Category.MISC);
        tower = add(new BoolSetting("Tower", "Place blocks while jumping up", true));
        extend = add(new NumberSetting("Extend", "Blocks ahead to place", 0, 0, 3, 1, ""));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        int blockSlot = findBlockSlot();
        if (blockSlot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();

        BlockPos below = mc.player.blockPosition().below();
        boolean placed = false;

        if (mc.level.getBlockState(below).isAir()) {
            mc.player.getInventory().setSelectedSlot(blockSlot);
            placed = placeBlock(below);
        }

        int ext = extend.getInt();
        if (ext > 0) {
            Vec3 look = mc.player.getDeltaMovement();
            double dx = look.x == 0 ? 0 : (look.x > 0 ? 1 : -1);
            double dz = look.z == 0 ? 0 : (look.z > 0 ? 1 : -1);
            for (int i = 1; i <= ext; i++) {
                BlockPos ahead = below.offset((int) (dx * i), 0, (int) (dz * i));
                if (mc.level.getBlockState(ahead).isAir()) {
                    mc.player.getInventory().setSelectedSlot(blockSlot);
                    if (placeBlock(ahead)) placed = true;
                }
            }
        }

        if (tower.isOn() && mc.options.keyJump.isDown() && mc.player.getDeltaMovement().y > 0) {
            BlockPos underFeet = mc.player.blockPosition().below();
            if (!mc.level.getBlockState(underFeet).isAir()) {
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.42, mc.player.getDeltaMovement().z);
            }
        }

        if (placed) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        mc.player.getInventory().setSelectedSlot(prevSlot);
    }

    private boolean placeBlock(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = mc.level.getBlockState(neighbor);
            if (neighborState.isAir()) continue;

            Vec3 hitVec = Vec3.atCenterOf(neighbor).add(
                    dir.getStepX() * 0.5, dir.getStepY() * 0.5, dir.getStepZ() * 0.5);
            BlockHitResult hit = new BlockHitResult(hitVec, dir.getOpposite(), neighbor, false);
            var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            if (result.consumesAction()) return true;
        }
        return false;
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String getInfo() { return tower.isOn() ? "Tower" : null; }
}
