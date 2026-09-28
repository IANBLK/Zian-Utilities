package com.zianblk.zianutilities.neoforge.equipment;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;

public final class ZianShieldItem extends ShieldItem {
    public ZianShieldItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue() {
        return 16;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.NETHERITE_INGOT) || super.isValidRepairItem(stack, repair);
    }
}
