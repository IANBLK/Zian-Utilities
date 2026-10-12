package com.zianblk.zianutilities.neoforge.riding;

import com.google.gson.*;
import java.nio.file.*;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;

/** Server-owned policy, mirrored to clients for the same riding physics. */
public final class RidingPolicy {
 public record Settings(boolean enabled,int speedPercent,int staminaPercent){
  public Settings{if(speedPercent<25||speedPercent>100||staminaPercent<20||staminaPercent>100)throw new IllegalArgumentException("Velocidad: 25–100; estamina: 20–100");}
 }
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private static final Path FILE=FMLPaths.CONFIGDIR.get().resolve("zianutilities-riding.json");
 private static volatile Settings server=new Settings(true,65,60),client=new Settings(false,100,100);
 private RidingPolicy(){}
 public static Settings server(){return server;}
 public static void client(Settings settings){client=settings;}
 public static void load()throws Exception{if(Files.exists(FILE)){var s=GSON.fromJson(Files.readString(FILE),Settings.class);if(s==null)throw new IllegalArgumentException("Configuración vacía");server=s;}else save(server);}
 public static void save(Settings next)throws Exception{
  Files.createDirectories(FILE.getParent());var temp=FILE.resolveSibling(FILE.getFileName()+".tmp");Files.writeString(temp,GSON.toJson(next));
  Files.move(temp,FILE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);server=next;
 }
 private static boolean flyingMount(PokemonEntity entity){var controller=entity.getRidingController();return entity.getControllingPassenger() instanceof Player&&controller!=null&&controller.getContext()!=null&&controller.getContext().getStyle()==com.cobblemon.mod.common.api.riding.RidingStyle.AIR;}
 public static net.minecraft.world.phys.Vec3 velocity(PokemonEntity entity,net.minecraft.world.phys.Vec3 original){
  var s=entity.level().isClientSide()?client:server;if(!s.enabled()||!flyingMount(entity))return original;
  double scale=s.speedPercent()/100.;return new net.minecraft.world.phys.Vec3(original.x*scale,original.y>0?original.y*scale:original.y,original.z*scale);
 }
 public static float stamina(PokemonEntity entity,RidingBehaviourState state,float original){
  var s=entity.level().isClientSide()?client:server;
  if(!s.enabled()||!flyingMount(entity)||!Float.isFinite(original))return original;
  float cap=s.staminaPercent()/100f;float bounded=original==-1f?cap:Math.max(0,Math.min(original,cap));
  if(bounded!=original)state.getStamina().set(bounded,true);
  return bounded;
 }
}
