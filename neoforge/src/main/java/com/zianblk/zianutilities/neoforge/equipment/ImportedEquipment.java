package com.zianblk.zianutilities.neoforge.equipment;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.*;
import net.minecraft.world.item.TooltipFlag;
/** Original pack appearances with standard tool/armor behavior; no plugin/shader dependency. */
public final class ImportedEquipment {
 public static final Map<String,DeferredItem<? extends Item>> ITEMS=new LinkedHashMap<>();
 private static final Tier PLUS=new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL,2031,9,5,15,()->Ingredient.of(Items.NETHERITE_INGOT));
 private static Component name(ItemStack stack){return Component.translatable(stack.getItem().getDescriptionId()).withStyle(s->s.withColor(color(stack)).withBold(true));}
 private static int color(ItemStack stack){String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();return id.contains("netherite")||id.contains("reaper")?0xC979FF:id.contains("lightning")||id.contains("storm")?0x79B8FF:id.contains("golden")||id.contains("molten")?0xF2CB68:id.contains("poison")?0x88D868:0x48E4DA;}
 private static void lore(ItemStack stack,List<Component> lines){int color=color(stack);lines.add(Component.literal("━━━━━━━━ ✦ ━━━━━━━━").withStyle(s->s.withColor(color)));lines.add(Component.translatable(stack.getItem().getDescriptionId()+".lore").withStyle(net.minecraft.ChatFormatting.GRAY,net.minecraft.ChatFormatting.ITALIC));lines.add(Component.literal("━━━━━━━━ ✦ ━━━━━━━━").withStyle(s->s.withColor(color)));}
 public static void register(DeferredRegister<ArmorMaterial> materials,DeferredRegister.Items items,List<Supplier<? extends Item>> equipment){
  // Retain old registry IDs for existing saves, but retire them from the catalog.
  for(String skin:List.of("leather","chainmail","copper","golden","iron","diamond","netherite"))armor(materials,items,new ArrayList<>(),"reforged_"+skin,skin.equals("netherite"),skin.equals("netherite")?1:0,false);
  RetiredReforged.install();
  armor(materials,items,equipment,"dark_lightning",true,0,false);
  for(String id:List.of("dark_lightning_sword","dark_lightning_axe","dark_lightning_pickaxe","cosmic_sword","frost_axe","molten_mace","poison_dagger","reapers_scythe","storm_katana")){
   Tier tier=id.startsWith("dark_lightning")?Tiers.NETHERITE:id.equals("cosmic_sword")||id.equals("reapers_scythe")||id.equals("molten_mace")?PLUS:Tiers.DIAMOND;
   DeferredItem<? extends Item> item=items.register(id,()->{var p=new Item.Properties();if(tier!=Tiers.DIAMOND)p.fireResistant();return id.endsWith("pickaxe")?new NamedPickaxe(tier,p.attributes(DiggerItem.createAttributes(tier,1,-2.8f))):id.endsWith("axe")?new NamedAxe(tier,p.attributes(AxeItem.createAttributes(tier,5,-3))):new NamedSword(tier,p.attributes(SwordItem.createAttributes(tier,3,-2.4f)));});ITEMS.put(id,item);equipment.add(item);
  }
  var book=items.register("dark_lightning_book",()->new Item(new Item.Properties()){@Override public Component getName(ItemStack stack){return name(stack);}@Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){lore(stack,lines);}});ITEMS.put("dark_lightning_book",book);equipment.add(book);
 }
 private static void armor(DeferredRegister<ArmorMaterial> materials,DeferredRegister.Items items,List<Supplier<? extends Item>> equipment,String set,boolean netherite,int bonus,boolean mesh){
  var material=materials.register(set,()->{var base=(netherite?ArmorMaterials.NETHERITE:ArmorMaterials.DIAMOND).value();var defense=new EnumMap<ArmorItem.Type,Integer>(base.defense());for(var type:List.of(ArmorItem.Type.HELMET,ArmorItem.Type.CHESTPLATE,ArmorItem.Type.LEGGINGS,ArmorItem.Type.BOOTS))defense.put(type,base.getDefense(type)+bonus);return new ArmorMaterial(defense,base.enchantmentValue(),netherite?SoundEvents.ARMOR_EQUIP_NETHERITE:SoundEvents.ARMOR_EQUIP_DIAMOND,()->Ingredient.of(netherite?Items.NETHERITE_INGOT:Items.DIAMOND),List.of(new ArmorMaterial.Layer(set.startsWith("reforged_")?ResourceLocation.withDefaultNamespace(netherite?"netherite":"diamond"):ResourceLocation.fromNamespaceAndPath("zianutilities",set))),base.toughness(),base.knockbackResistance());});
  for(var type:List.of(ArmorItem.Type.HELMET,ArmorItem.Type.CHESTPLATE,ArmorItem.Type.LEGGINGS,ArmorItem.Type.BOOTS)){
   String id=set+"_"+type.getName();var item=items.register(id,()->{var p=new Item.Properties().durability(type.getDurability(netherite?37:33));if(netherite)p.fireResistant();return new NamedArmor(material,type,p,set,mesh);});ITEMS.put(id,item);equipment.add(item);
  }
 }
 public static final class NamedArmor extends ArmorItem {public final String set;public final boolean mesh;public NamedArmor(net.minecraft.core.Holder<ArmorMaterial> material,Type type,Properties p,String set,boolean mesh){super(material,type,p);this.set=set;this.mesh=mesh;}@Override public Component getName(ItemStack stack){return name(stack);}@Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){lore(stack,lines);}}
 private static final class NamedSword extends SwordItem {NamedSword(Tier tier,Properties p){super(tier,p);}@Override public Component getName(ItemStack stack){return name(stack);}@Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){lore(stack,lines);}}
 private static final class NamedAxe extends AxeItem {NamedAxe(Tier tier,Properties p){super(tier,p);}@Override public Component getName(ItemStack stack){return name(stack);}@Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){lore(stack,lines);}}
 private static final class NamedPickaxe extends PickaxeItem {NamedPickaxe(Tier tier,Properties p){super(tier,p);}@Override public Component getName(ItemStack stack){return name(stack);}@Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){lore(stack,lines);}}
}
