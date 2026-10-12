package com.zianblk.zianutilities.neoforge.equipment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** In-place, idempotent legacy conversion. Never creates a second inventory copy. */
public final class RetiredReforged {
 private static boolean installed;
 private RetiredReforged() {}
 public static ItemStack convert(ItemStack source) {
  if(source.isEmpty())return source;
  var id=BuiltInRegistries.ITEM.getKey(source.getItem());
  if(!id.getNamespace().equals("zianutilities")||!id.getPath().startsWith("reforged_"))return source;
  String kind=id.getPath().substring(id.getPath().lastIndexOf('_')+1);
  boolean netherite=id.getPath().startsWith("reforged_netherite_");
  Item target=switch(kind){
   case "helmet"->netherite?Items.NETHERITE_HELMET:Items.DIAMOND_HELMET;
   case "chestplate"->netherite?Items.NETHERITE_CHESTPLATE:Items.DIAMOND_CHESTPLATE;
   case "leggings"->netherite?Items.NETHERITE_LEGGINGS:Items.DIAMOND_LEGGINGS;
   case "boots"->netherite?Items.NETHERITE_BOOTS:Items.DIAMOND_BOOTS;
   default->null;
  };
  if(target==null)return source;
  var result=new ItemStack(target,source.getCount());
  result.applyComponents(source.getComponentsPatch());
  // Preserve the displayed fantasy name in addition to enchantments/damage/data.
  if(!source.has(DataComponents.CUSTOM_NAME))result.set(DataComponents.CUSTOM_NAME,source.getHoverName());
  return result;
 }
 private static int convert(Container inventory){int changed=0;for(int i=0;i<inventory.getContainerSize();i++){var old=inventory.getItem(i);var next=convert(old);if(next!=old){inventory.setItem(i,next);changed++;}}if(changed>0)inventory.setChanged();return changed;}
 private static void migrate(ServerPlayer player){
  int changed=convert(player.getInventory())+convert(player.getEnderChestInventory());
  var menu=player.containerMenu;
  for(var slot:menu.slots){var old=slot.getItem();var next=convert(old);if(next!=old){slot.set(next);changed++;}}
  var carried=menu.getCarried();var next=convert(carried);if(next!=carried){menu.setCarried(next);changed++;}
  if(changed>0){menu.broadcastChanges();player.getServer().getPlayerList().saveAll();}
 }
 public static synchronized void install(){
  if(installed)return;installed=true;
  NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event)->{if(event.getEntity() instanceof ServerPlayer player)migrate(player);});
  NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event)->{if(event.getEntity() instanceof ServerPlayer player&&player.tickCount%20==0)migrate(player);});
  NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event)->{if(!event.getLevel().isClientSide()&&event.getEntity() instanceof ItemEntity item){var old=item.getItem();var next=convert(old);if(next!=old)item.setItem(next);}});
 }
}
