package com.zianblk.zianutilities.neoforge.references;
import java.util.*;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
public final class References {
 private static MinecraftServer owner;private static HumanReferences index;
 private static HumanReferences index(MinecraftServer server){if(owner!=server||index==null){try{index=new HumanReferences(server.getWorldPath(LevelResource.ROOT).resolve("data/zianutilities-references.json"),"UTIL");owner=server;}catch(java.io.IOException e){throw new IllegalStateException(e);}}return index;}
 public static String label(MinecraftServer server,UUID owner,UUID id){String name=server.getProfileCache().get(owner).map(com.mojang.authlib.GameProfile::getName).orElse("JUGADOR");return index(server).reference(id,owner,name);}
 public static UUID resolve(MinecraftServer server,UUID owner,String value){return index(server).resolve(value,owner);}
 public static void clear(){owner=null;index=null;}
}
