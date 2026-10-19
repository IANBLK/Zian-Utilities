package com.zianblk.zianutilities.neoforge;
import com.zianblk.zianutilities.neoforge.breeding.LuckPermsLookup;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
public final class UtilitiesPermissions {
 private UtilitiesPermissions(){}
 public static boolean allows(ServerPlayer player,String action,boolean admin){return allows(player.createCommandSourceStack(),action,admin);}
 public static boolean allows(CommandSourceStack source,String action,boolean admin){
  if(source.getPlayer()==null)return source.hasPermission(2);
  try{var specific=LuckPermsLookup.lookup(source.getPlayer(),"zianutilities."+action);if(specific==LuckPermsLookup.Decision.FALSE)return false;
   var parent=admin?LuckPermsLookup.lookup(source.getPlayer(),"zianutilities.admin"):LuckPermsLookup.Decision.UNDEFINED;
   if(parent==LuckPermsLookup.Decision.FALSE)return false;if(specific==LuckPermsLookup.Decision.TRUE||parent==LuckPermsLookup.Decision.TRUE)return true;
   return !admin||source.hasPermission(2);
  }catch(Exception|LinkageError unavailable){return false;}
 }
}
