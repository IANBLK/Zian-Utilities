package com.zianblk.zianutilities.neoforge.equipment;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** Keeps vanilla ammunition, enchantments and firing behavior with enhanced arrow damage. */
public final class ZianBowItem extends BowItem {
    public ZianBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue() {
        return 16;
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        arrow.setBaseDamage(arrow.getBaseDamage() + 1.0D);
        return arrow;
    }
}
