package com.zianblk.zianutilities.neoforge.equipment;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ordinary bow behavior with one extra base point of arrow damage. */
public final class ZianBowItem extends BowItem {
    public ZianBowItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index,
                                   float velocity, float inaccuracy, float angle, LivingEntity target) {
        if (projectile instanceof AbstractArrow arrow) arrow.setBaseDamage(arrow.getBaseDamage() + 1.0);
        super.shootProjectile(shooter, projectile, index, velocity, inaccuracy, angle, target);
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
