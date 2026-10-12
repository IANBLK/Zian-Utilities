package com.zianblk.zianutilities.neoforge.riding;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.api.riding.RidingStyle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Only invoked by the existing isolated native smoke fixture. */
public final class RidingSmoke {
 private RidingSmoke(){}
 public static void verify(ServerPlayer player)throws Exception{
  var saved=RidingPolicy.server();var level=player.serverLevel();
  var pokemon=PokemonProperties.Companion.parse("charizard level=30", " ","=").create();
  var party=com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);var previousPokemon=party.get(5);party.set(5,pokemon);
  var mount=new PokemonEntity(level,pokemon,com.cobblemon.mod.common.CobblemonEntities.POKEMON);
  mount.refreshRiding();var controller=mount.getRidingController();
  var air=controller.getBehaviours().get(RidingStyle.AIR);if(air==null)throw new IllegalStateException("Charizard air behaviour missing");
  controller.changeBehaviour(air.getKey());
  var originalConnection=player.connection;
  if(originalConnection==null)player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(player.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false));
  try{
   RidingPolicy.save(new RidingPolicy.Settings(true,65,60));RidingPolicy.load();
   Vec3 input=new Vec3(2,-.2,3);if(RidingPolicy.velocity(mount,input)!=input)throw new IllegalStateException("Unridden Pokemon must not be changed");
   if(!player.startRiding(mount,true))throw new IllegalStateException("Native riding fixture failed");
   controller=mount.getRidingController();controller.changeBehaviour(air.getKey());
   float flySpeed=player.getAbilities().getFlyingSpeed();boolean mayfly=player.getAbilities().mayfly,flying=player.getAbilities().flying;
   var result=RidingPolicy.velocity(mount,input);if(Math.abs(result.x-1.3)>1e-6||Math.abs(result.z-1.95)>1e-6||result.y!=input.y)throw new IllegalStateException("Flight scale/fall preservation failed; driver="+mount.getControllingPassenger()+", style="+controller.getContext().getStyle()+", result="+result);
   var state=controller.getContext().getState();state.getStamina().set(.9f,true);
   if(Math.abs(mount.setRideBar()-.6f)>1e-5||Math.abs(state.getStamina().get()-.6f)>1e-5)throw new IllegalStateException("Native stamina hook/cap failed; bar="+mount.setRideBar()+", state="+state.getStamina().get());
   RidingPolicy.save(new RidingPolicy.Settings(false,65,60));if(RidingPolicy.velocity(mount,input)!=input)throw new IllegalStateException("Disabled policy must preserve movement");
   if(player.getAbilities().getFlyingSpeed()!=flySpeed||player.getAbilities().mayfly!=mayfly||player.getAbilities().flying!=flying)throw new IllegalStateException("Rank fly abilities changed");
   boolean denied=false;try{player.getServer().getCommands().getDispatcher().execute("ZianUtilities vuelo velocidad 50",player.createCommandSourceStack().withPermission(0));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){denied=true;}
   if(!denied)throw new IllegalStateException("Riding settings must require administration");
   org.slf4j.LoggerFactory.getLogger("RidingSmoke").info("Native riding passed: persisted limits, concrete stamina hook, velocity scale, no-rider/disabled passthrough, fly abilities and admin gate");
  }catch(Exception failure){org.slf4j.LoggerFactory.getLogger("RidingSmoke").error("Native riding fixture failed",failure);throw failure;}
  finally{try{player.stopRiding();mount.discard();if(previousPokemon==null)party.remove(pokemon);else party.set(5,previousPokemon);}finally{player.connection=originalConnection;RidingPolicy.save(saved);}}
 }
}
