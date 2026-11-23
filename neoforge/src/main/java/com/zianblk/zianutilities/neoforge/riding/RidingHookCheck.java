package com.zianblk.zianutilities.neoforge.riding;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.lang.reflect.Method;
import java.util.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Warns at server start when the optional riding mixin did not apply (Cobblemon changed). */
public final class RidingHookCheck {
 private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("ZianUtilities");
 static final List<String> HOOKS=List.of("travel$lambda$0","setRideBar$lambda$0");
 private RidingHookCheck(){}
 /** Returns "mixin" if the marker is missing plus every hook target method absent from the class. */
 static List<String> missing(Class<?> target){
  var names=new HashSet<String>();for(Method m:target.getDeclaredMethods())names.add(m.getName());
  var out=new ArrayList<String>();if(!ZianRidingHooked.class.isAssignableFrom(target))out.add("mixin");
  for(String h:HOOKS)if(!names.contains(h))out.add(h);return out;
 }
 public static void install(){
  // shortcut: solo compara nombres; si Cobblemon cambia la firma de la lambda y conserva el nombre, Mixin registra el error pero aquí no se avisa. Comparar getParameterTypes() si vuelve a pasar.
  NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->{try{var m=missing(PokemonEntity.class);
   if(!m.isEmpty())LOGGER.warn("Zian Utilities: el control de vuelo/estamina no se aplicó; versión de Cobblemon no compatible (probado con 1.8.4). Faltan: {}",m);
  }catch(Throwable t){LOGGER.warn("Zian Utilities: no se pudo comprobar el control de vuelo/estamina",t);}});
 }
}
