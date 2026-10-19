package com.zianblk.zianutilities.neoforge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
public final class ItemPresentation {
 private ItemPresentation(){}
 public static void register(){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ItemPresentation::tooltip);}
 private static void tooltip(ItemTooltipEvent event){
  var stack=event.getItemStack();var id=BuiltInRegistries.ITEM.getKey(stack.getItem());if(!id.getNamespace().equals("zianutilities")||event.getToolTip().isEmpty())return;
  if(!stack.has(DataComponents.CUSTOM_NAME))event.getToolTip().set(0,event.getToolTip().getFirst().copy().withStyle(s->s.withBold(true).withItalic(false).withColor(event.getToolTip().getFirst().getStyle().getColor()==null?net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.GOLD):event.getToolTip().getFirst().getStyle().getColor())));
  var lore=stack.get(DataComponents.LORE);if(lore!=null&&!lore.lines().isEmpty())return;
  if(com.zianblk.zianutilities.neoforge.equipment.ImportedEquipment.ITEMS.containsKey(id.getPath()))return;
  String fallback=stack.getItem() instanceof net.minecraft.world.item.ArmorItem?"Una promesa de protección acompaña cada paso.":stack.getItem() instanceof net.minecraft.world.item.TieredItem?"Forjado para escribir tu próxima aventura.":stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem?"Una criatura aguarda al otro lado del sello.":"Un vestigio de historias aún por descubrir.";
  event.getToolTip().add(1,Component.translatableWithFallback("flavor.zianutilities."+id.getPath()+".short",fallback).withStyle(ChatFormatting.GRAY));
 }
}
