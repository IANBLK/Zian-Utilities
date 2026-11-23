package com.zianblk.zianutilities.neoforge.equipment;

import com.zianblk.zianutilities.core.equipment.WeaponBalance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Habilidades de las armas importadas (Lote J). */
final class WeaponAbilities {
  private WeaponAbilities() {}

  /** Pasivas al golpear: daga venenosa y guadaña del segador. */
  static void onHit(String id, LivingEntity target, LivingEntity attacker) {
    if (target.level().isClientSide) return;
    if (id.equals("poison_dagger") && target.isAlive())
      // addEffect conserva el nivel y solo alarga la duración: se renueva, no se acumula.
      target.addEffect(new MobEffectInstance(MobEffects.POISON, WeaponBalance.POISON_SECONDS * 20, WeaponBalance.POISON_AMPLIFIER), attacker);
    else if (id.equals("reapers_scythe") && !target.isAlive() && target instanceof Mob && attacker instanceof Player player)
      player.heal(WeaponBalance.REAPER_HEAL);
  }
}
