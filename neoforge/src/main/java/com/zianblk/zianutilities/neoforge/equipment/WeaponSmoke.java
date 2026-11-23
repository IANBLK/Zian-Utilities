package com.zianblk.zianutilities.neoforge.equipment;

import java.util.ArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/** Solo la invoca la smoke nativa aislada; entidades en el aire y sin IA para no depender del terreno. */
public final class WeaponSmoke {
  private WeaponSmoke() {}

  private static void require(boolean condition, String reason) {
    if (!condition) throw new IllegalStateException(reason);
  }

  private static ItemStack weapon(String id) {
    return new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("zianutilities", id)));
  }

  public static void verify(ServerPlayer player) throws Exception {
    var level = player.serverLevel();
    double y = level.getMaxBuildHeight() - 24;
    var spawned = new ArrayList<Entity>();
    var originalConnection = player.connection;
    var originalHand = player.getMainHandItem();
    if (originalConnection == null)
      player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(player.getServer(), new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player, net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false));
    try {
      player.setPos(0.5, y, 0.5);
      // Objetivos: x = distancia horizontal al jugador.
      java.util.function.BiFunction<EntityType<? extends Mob>, Double, Mob> at = (type, x) -> {
        Mob mob = type.create(level);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPos(0.5 + x, y, 0.5);
        require(level.addFreshEntity(mob), "No se pudo crear " + type);
        spawned.add(mob);
        return mob;
      };
      // Daga: golpe real, Veneno I 80 ticks (vaca: los no muertos son inmunes).
      var cow = at.apply(EntityType.COW, 1.0);
      player.setItemInHand(InteractionHand.MAIN_HAND, weapon("poison_dagger"));
      player.attack(cow);
      var poison = cow.getEffect(MobEffects.POISON);
      require(poison != null && poison.getDuration() == 80 && poison.getAmplifier() == 0, "Daga: Veneno I 80 ticks esperado, hay " + poison);
      // Guadaña: matar un mob de poca vida cura 2.
      var victim = at.apply(EntityType.PIG, 1.0);
      victim.setHealth(0.1f);
      player.setHealth(10);
      player.setItemInHand(InteractionHand.MAIN_HAND, weapon("reapers_scythe"));
      player.attack(victim);
      require(!victim.isAlive() && player.getHealth() == 12, "Guadaña: curación esperada 12, hay " + player.getHealth() + " vivo=" + victim.isAlive());
      // Hacha: radio 4, espera 30 s, ni mascotas ni fuera de radio.
      var frostStack = weapon("frost_axe");
      var near = at.apply(EntityType.ZOMBIE, 3.0);
      var far = at.apply(EntityType.ZOMBIE, 6.0);
      var wolf = (net.minecraft.world.entity.animal.Wolf) at.apply(EntityType.WOLF, 2.0);
      wolf.tame(player);
      require(WeaponAbilities.activate("frost_axe", player, frostStack), "Hacha: primera activación rechazada");
      var slow = near.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
      require(slow != null && slow.getDuration() == 80 && slow.getAmplifier() == 1, "Hacha: Lentitud II 80 ticks esperada, hay " + slow);
      require(far.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null, "Hacha: afectó fuera del radio");
      require(wolf.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null, "Hacha: afectó a una mascota");
      near.removeAllEffects();
      require(!WeaponAbilities.activate("frost_axe", player, frostStack) && near.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null, "Hacha: la segunda activación seguida debía esperar");
      // Maza: radio 3.
      var maceStack = weapon("molten_mace");
      var hot = at.apply(EntityType.ZOMBIE, 2.0);
      var cold = at.apply(EntityType.ZOMBIE, 5.0);
      require(WeaponAbilities.activate("molten_mace", player, maceStack), "Maza: primera activación rechazada");
      require(hot.getRemainingFireTicks() >= 80, "Maza: fuego >= 80 ticks esperado, hay " + hot.getRemainingFireTicks());
      require(cold.getRemainingFireTicks() <= 0 && wolf.getRemainingFireTicks() <= 0, "Maza: afectó fuera del radio o a una mascota");
      org.slf4j.LoggerFactory.getLogger("WeaponSmoke").info("Lote J weapons PASSED: poison refresh, reaper heal, frost radius+cooldown, molten fire, owned pets untouched");
    } finally {
      for (var e : spawned) e.discard();
      player.setItemInHand(InteractionHand.MAIN_HAND, originalHand);
      player.setHealth(player.getMaxHealth());
      player.connection = originalConnection;
    }
  }
}
