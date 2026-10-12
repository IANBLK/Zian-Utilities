package com.zianblk.zianutilities.neoforge.equipment;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** Keeps vanilla ammunition, enchantments and firing behavior with enhanced arrow damage. */
public final class ZianBowItem extends BowItem {
    private final double damageBonus;
    public ZianBowItem(Properties properties,double damageBonus) {
        super(properties);
        if(!Double.isFinite(damageBonus)||damageBonus<0||damageBonus>1)throw new IllegalArgumentException("Invalid bow damage bonus");
        this.damageBonus=damageBonus;
    }
    public double damageBonus(){return damageBonus;}

    @Override
    public int getEnchantmentValue() {
        return 16;
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        arrow.setBaseDamage(arrow.getBaseDamage() + damageBonus);
        return arrow;
    }
}
