package com.zianblk.zianutilities.neoforge.raids.mixin;

import com.necro.raid.dens.common.network.packets.RaidChallengePacket;
import com.zianblk.zianutilities.neoforge.raids.RaidDuelHooked;
import com.zianblk.zianutilities.neoforge.raids.RaidDuelRuntime;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * D-R6: the only entry point of a raid battle. For tiers at or above duelFromTier the duel takes over and the
 * Pokemon battle is never built; everything else runs Raid Dens untouched. require=1: if the target moves, Mixin fails.
 */
@Mixin(value = RaidChallengePacket.class, remap = false)
public abstract class RaidChallengeMixin implements RaidDuelHooked {
  @Inject(method = "handleServer(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
  private void zianDuel(ServerPlayer player, CallbackInfo ci) {
    if (RaidDuelRuntime.intercept((RaidChallengePacket) (Object) this, player)) ci.cancel();
  }
}
