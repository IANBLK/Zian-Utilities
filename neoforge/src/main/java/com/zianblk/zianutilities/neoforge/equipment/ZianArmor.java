package com.zianblk.zianutilities.neoforge.equipment;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Enchantable equipment sets and bows, available without crafting recipes. */
public final class ZianArmor {
    private static final DeferredRegister<ArmorMaterial> MATERIALS =
        DeferredRegister.create(Registries.ARMOR_MATERIAL, ZianUtilitiesMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ZianUtilitiesMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZianUtilitiesMod.MOD_ID);
    private static final List<Supplier<? extends Item>> EQUIPMENT = new ArrayList<>();
    public static final List<String> BOW_NAMES = List.of("curve_bow", "regular_bow", "long_bow", "harp_bow");
    private static final List<Supplier<ZianBowItem>> BOWS = new ArrayList<>();
    private static final Tier ENHANCED_NETHERITE = new SimpleTier(
        BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2032, 10.0F, 5.0F, 16,
        () -> Ingredient.of(Items.NETHERITE_INGOT));

    static {
        registerSet("prismatic");
        registerSet("dark_reaper");
        for (String name : BOW_NAMES) {
            Supplier<ZianBowItem> bow = ITEMS.register(name,
                () -> new ZianBowItem(toolProperties().durability(2032)));
            BOWS.add(bow);
            EQUIPMENT.add(bow);
        }

        TABS.register("equipment", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.zianutilities.equipment"))
            .icon(() -> new ItemStack(EQUIPMENT.getFirst().get()))
            .displayItems((parameters, output) -> EQUIPMENT.forEach(item -> output.accept(item.get())))
            .build());
    }

    private ZianArmor() {}

    private static void registerSet(String name) {
        DeferredHolder<ArmorMaterial, ArmorMaterial> material = MATERIALS.register(name, () -> {
            ArmorMaterial netherite = net.minecraft.world.item.ArmorMaterials.NETHERITE.value();
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(netherite.defense());
            for (ArmorItem.Type type : List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
                ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
                defense.put(type, netherite.getDefense(type) + 1);
            }
            return new ArmorMaterial(defense, netherite.enchantmentValue(),
                SoundEvents.ARMOR_EQUIP_NETHERITE,
                () -> Ingredient.of(Items.NETHERITE_INGOT),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(
                    ZianUtilitiesMod.MOD_ID, name))),
                netherite.toughness() + 1.0F,
                netherite.knockbackResistance() + 0.05F);
        });

        for (ArmorItem.Type type : List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
            EQUIPMENT.add(ITEMS.register(name + "_" + type.getName(), () -> new ArmorItem(material, type,
                new Item.Properties().fireResistant().durability(type.getDurability(37)))));
        }
        EQUIPMENT.add(ITEMS.register(name + "_sword", ZianArmor::sword));
        EQUIPMENT.add(ITEMS.register(name + "_axe", () -> new AxeItem(ENHANCED_NETHERITE,
            toolProperties().attributes(AxeItem.createAttributes(ENHANCED_NETHERITE, 5.0F, -3.0F)))));
        EQUIPMENT.add(ITEMS.register(name + "_pickaxe", () -> new PickaxeItem(ENHANCED_NETHERITE,
            toolProperties().attributes(PickaxeItem.createAttributes(ENHANCED_NETHERITE, 1.0F, -2.8F)))));
        EQUIPMENT.add(ITEMS.register(name + "_shovel", () -> new ShovelItem(ENHANCED_NETHERITE,
            toolProperties().attributes(ShovelItem.createAttributes(ENHANCED_NETHERITE, 1.5F, -3.0F)))));
        EQUIPMENT.add(ITEMS.register(name + "_hoe", () -> new HoeItem(ENHANCED_NETHERITE,
            toolProperties().attributes(HoeItem.createAttributes(ENHANCED_NETHERITE, -4.0F, 0.0F)))));

    }

    public static List<ZianBowItem> bows() {
        return BOWS.stream().map(Supplier::get).toList();
    }

    private static SwordItem sword() {
        return new SwordItem(ENHANCED_NETHERITE,
            toolProperties().attributes(SwordItem.createAttributes(ENHANCED_NETHERITE, 3, -2.4F)));
    }

    private static Item.Properties toolProperties() {
        return new Item.Properties().fireResistant();
    }

    public static void register(IEventBus bus) {
        MATERIALS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
