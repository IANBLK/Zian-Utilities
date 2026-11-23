package com.zianblk.zianutilities.neoforge.equipment;

import com.zianblk.zianutilities.core.equipment.WeaponBalance;
import com.zianblk.zianutilities.core.equipment.TooltipLayout;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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

  private static final String COOLDOWNS = "ZianUtilitiesPowers";

  /** Clonar y entrar al servidor conservan la espera, como en Zian-Manager. */
  static void listen() {
    NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> e.getEntity().getPersistentData().put(COOLDOWNS, e.getOriginal().getPersistentData().getCompound(COOLDOWNS).copy()));
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
      if (!(e.getEntity() instanceof ServerPlayer p)) return;
      for (String id : new String[] {"frost_axe", "molten_mace"}) {
        long wait = p.getPersistentData().getCompound(COOLDOWNS).getLong(id) - System.currentTimeMillis();
        var item = ImportedEquipment.ITEMS.get(id);
        if (wait > 0 && item != null) p.getCooldowns().addCooldown(item.get(), (int) Math.min(Integer.MAX_VALUE, (wait + 49) / 50));
      }
    });
  }

  /** Clic derecho de hacha de escarcha y maza fundida. */
  static InteractionResultHolder<ItemStack> use(String id, Level level, Player player, InteractionHand hand) {
    var stack = player.getItemInHand(hand);
    if (level.isClientSide) return InteractionResultHolder.success(stack);
    return player instanceof ServerPlayer p && activate(id, p, stack) ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
  }

  static boolean activate(String id, ServerPlayer p, ItemStack stack) {
    long now = System.currentTimeMillis();
    var data = p.getPersistentData().getCompound(COOLDOWNS);
    long wait = data.getLong(id) - now;
    if (wait > 0) {
      p.getCooldowns().addCooldown(stack.getItem(), (int) ((wait + 49) / 50));
      p.displayClientMessage(Component.translatable("message.zianutilities.weapon_wait", (wait + 999) / 1000), true);
      return false;
    }
    data.putInt("v", 1); // versión de formato; leer sin versión sigue valiendo
    data.putLong(id, now + WeaponBalance.ACTIVE_COOLDOWN_SECONDS * 1000L);
    p.getPersistentData().put(COOLDOWNS, data);
    p.getCooldowns().addCooldown(stack.getItem(), WeaponBalance.ACTIVE_COOLDOWN_SECONDS * 20);
    boolean frost = id.equals("frost_axe");
    int radius = frost ? WeaponBalance.FROST_RADIUS : WeaponBalance.MOLTEN_RADIUS;
    var level = p.serverLevel();
    for (var e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius), e -> e != p && e.isAlive() && !e.isSpectator() && e.distanceToSqr(p) <= radius * radius && !e.isInvulnerable() && !(e instanceof OwnableEntity o && o.getOwnerUUID() != null) && !e.isAlliedTo(p) && (e instanceof Player other ? p.server.isPvpAllowed() && p.canHarmPlayer(other) && !other.getAbilities().invulnerable : e instanceof net.minecraft.world.entity.monster.Enemy))) {
      if (frost) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WeaponBalance.FROST_SECONDS * 20, WeaponBalance.FROST_AMPLIFIER), p);
      else e.igniteForSeconds(WeaponBalance.MOLTEN_FIRE_SECONDS);
    }
    level.sendParticles(frost ? ParticleTypes.SNOWFLAKE : ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 40, radius / 2.0, 0.5, radius / 2.0, 0.02);
    level.playSound(null, p.blockPosition(), frost ? SoundEvents.GLASS_BREAK : SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1, 1);
    return true;
  }

  private static boolean shift() {
    return net.neoforged.fml.loading.FMLEnvironment.dist.isClient() && com.zianblk.zianutilities.neoforge.client.TooltipKeys.shift();
  }

  /** Tras el lore: habilidad y pista de Mayús; con Mayús, efecto y espera. Las demás armas no cambian. */
  static void tooltip(String id, List<Component> lines) {
    var spec = switch (id) {
      case "poison_dagger" -> new TooltipLayout.Spec(id, WeaponBalance.POISON_SECONDS, 0);
      case "reapers_scythe" -> new TooltipLayout.Spec(id, WeaponBalance.REAPER_HEAL / 2, 0);
      case "frost_axe" -> new TooltipLayout.Spec(id, WeaponBalance.FROST_SECONDS, WeaponBalance.ACTIVE_COOLDOWN_SECONDS);
      case "molten_mace" -> new TooltipLayout.Spec(id, WeaponBalance.MOLTEN_FIRE_SECONDS, WeaponBalance.ACTIVE_COOLDOWN_SECONDS);
      default -> null;
    };
    if (spec == null) return;
    for (var l : TooltipLayout.lines(spec, shift())) {
      var text = Component.translatable(l.key(), l.arg());
      lines.add(switch (l.kind()) {
        case ABILITY, COOLDOWN -> text.withStyle(ChatFormatting.GOLD);
        case DETAIL -> text.withStyle(ChatFormatting.DARK_GRAY);
        case HINT -> text.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
      });
    }
  }
}
