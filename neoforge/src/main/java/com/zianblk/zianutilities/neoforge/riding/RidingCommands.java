package com.zianblk.zianutilities.neoforge.riding;
import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.server.level.ServerPlayer;

public final class RidingCommands {
 private RidingCommands(){}
 public static void install(){
  com.cobblemon.mod.common.api.events.CobblemonEvents.RIDE_EVENT_APPLY_STAMINA.subscribe(com.cobblemon.mod.common.api.Priority.LOWEST,(java.util.function.Consumer<com.cobblemon.mod.common.api.events.pokemon.RidePokemonEvent.ApplyStamina>)event->{
   var s=RidingPolicy.server();var prop=event.getPokemon().getRideProp();
   if(s.enabled()&&prop!=null&&prop.getBehaviours().containsKey(com.cobblemon.mod.common.api.riding.RidingStyle.AIR))event.setRideStamina(event.getRideStamina()<0?s.staminaPercent()/100f:Math.min(event.getRideStamina(),s.staminaPercent()/100f));
  });
  NeoForge.EVENT_BUS.addListener((ServerStartedEvent event)->{try{RidingPolicy.load();}catch(Exception error){org.slf4j.LoggerFactory.getLogger("ZianUtilities/Riding").error("Invalid riding configuration; defaults remain active",error);}});
  NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event)->{if(event.getEntity() instanceof ServerPlayer player)RidingNetwork.send(player);});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)->{for(var player:event.getServer().getPlayerList().getPlayers())if(player.getVehicle() instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pokemon){var controller=pokemon.getRidingController();if(controller!=null&&controller.getContext()!=null){var state=controller.getContext().getState();RidingPolicy.stamina(pokemon,state,state.getStamina().get());}}});
  NeoForge.EVENT_BUS.addListener(RidingCommands::register);
 }
 private static void register(RegisterCommandsEvent event){
  var node=Commands.literal("vuelo").requires(s->s.hasPermission(Commands.LEVEL_GAMEMASTERS))
   .then(Commands.literal("ver").executes(c->status(c.getSource())))
   .then(Commands.literal("activar").executes(c->update(c.getSource(),true,null,null)))
   .then(Commands.literal("desactivar").executes(c->update(c.getSource(),false,null,null)))
   .then(Commands.literal("velocidad").then(Commands.argument("porcentaje",IntegerArgumentType.integer(25,100)).suggests((c,b)->SharedSuggestionProvider.suggest(java.util.List.of("25","50","65","75","100"),b)).executes(c->update(c.getSource(),null,IntegerArgumentType.getInteger(c,"porcentaje"),null))))
   .then(Commands.literal("estamina").then(Commands.argument("porcentaje",IntegerArgumentType.integer(20,100)).suggests((c,b)->SharedSuggestionProvider.suggest(java.util.List.of("20","40","60","80","100"),b)).executes(c->update(c.getSource(),null,null,IntegerArgumentType.getInteger(c,"porcentaje")))))
   .then(Commands.literal("recargar").executes(c->{try{RidingPolicy.load();broadcast(c.getSource());return status(c.getSource());}catch(Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}}));
  node.executes(c->status(c.getSource()));event.getDispatcher().register(Commands.literal(ZianUtilitiesMod.COMMAND_ROOT).requires(s->true).then(node));
 }
 private static int status(CommandSourceStack source){var s=RidingPolicy.server();source.sendSuccess(()->Component.literal("✦ Vuelo Pokémon » "+(s.enabled()?"activo":"desactivado")+" · velocidad "+s.speedPercent()+"% · estamina máxima "+s.staminaPercent()+"%"),false);return 1;}
 private static void broadcast(CommandSourceStack source){source.getServer().getPlayerList().getPlayers().forEach(RidingNetwork::send);}
 private static int update(CommandSourceStack source,Boolean enabled,Integer speed,Integer stamina){try{var s=RidingPolicy.server();RidingPolicy.save(new RidingPolicy.Settings(enabled==null?s.enabled():enabled,speed==null?s.speedPercent():speed,stamina==null?s.staminaPercent():stamina));broadcast(source);return status(source);}catch(Exception e){source.sendFailure(Component.literal(e.getMessage()));return 0;}}
}
