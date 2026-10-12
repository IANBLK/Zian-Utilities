package com.zianblk.zianutilities.neoforge.riding.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.api.riding.behaviour.*;
import com.zianblk.zianutilities.neoforge.riding.RidingPolicy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Hook the common entity call sites; air behaviours contain client-only classes. */
@Mixin(PokemonEntity.class)
public abstract class PokemonRidingMixin {
 @Inject(method="travel$lambda$0",at=@At("RETURN"),cancellable=true,remap=false)
 private static void zianVelocity(PokemonEntity entity,RidingBehaviour<?,?> behaviour,RidingBehaviourSettings settings,RidingBehaviourState state,CallbackInfoReturnable<Vec3> result){
  result.setReturnValue(RidingPolicy.velocity(entity,result.getReturnValue()));
 }
 @Inject(method="setRideBar$lambda$0",at=@At("RETURN"),cancellable=true,remap=false)
 private static void zianStamina(PokemonEntity entity,Player player,RidingBehaviour<?,?> behaviour,RidingBehaviourSettings settings,RidingBehaviourState state,CallbackInfoReturnable<Float> result){
  result.setReturnValue(RidingPolicy.stamina(entity,state,result.getReturnValue()));
 }
}
