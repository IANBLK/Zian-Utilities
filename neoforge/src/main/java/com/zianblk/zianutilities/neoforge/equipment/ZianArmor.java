package com.zianblk.zianutilities.neoforge.equipment;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ZianArmor {
    private static final DeferredRegister<ArmorMaterial> MATERIALS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.ARMOR_MATERIAL, ZianUtilitiesMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ZianUtilitiesMod.MOD_ID);

    private static final Supplier<ArmorMaterial> NETHERITE_STATS = () -> {
        ArmorMaterial vanilla = net.minecraft.world.item.ArmorMaterials.NETHERITE.value();
        return vanilla;
    };

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CAPTURA = material("captura");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> EXPLORADOR = material("explorador");
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CAMPEON = material("campeon");

    public static final Supplier<ArmorItem> CAPTURA_HELMET = piece("captura_helmet", CAPTURA, ArmorItem.Type.HELMET);
    public static final Supplier<ArmorItem> CAPTURA_CHESTPLATE = piece("captura_chestplate", CAPTURA, ArmorItem.Type.CHESTPLATE);
    public static final Supplier<ArmorItem> CAPTURA_LEGGINGS = piece("captura_leggings", CAPTURA, ArmorItem.Type.LEGGINGS);
    public static final Supplier<ArmorItem> CAPTURA_BOOTS = piece("captura_boots", CAPTURA, ArmorItem.Type.BOOTS);
    public static final Supplier<ArmorItem> EXPLORADOR_HELMET = piece("explorador_helmet", EXPLORADOR, ArmorItem.Type.HELMET);
    public static final Supplier<ArmorItem> EXPLORADOR_CHESTPLATE = piece("explorador_chestplate", EXPLORADOR, ArmorItem.Type.CHESTPLATE);
    public static final Supplier<ArmorItem> EXPLORADOR_LEGGINGS = piece("explorador_leggings", EXPLORADOR, ArmorItem.Type.LEGGINGS);
    public static final Supplier<ArmorItem> EXPLORADOR_BOOTS = piece("explorador_boots", EXPLORADOR, ArmorItem.Type.BOOTS);
    public static final Supplier<ArmorItem> CAMPEON_HELMET = piece("campeon_helmet", CAMPEON, ArmorItem.Type.HELMET);
    public static final Supplier<ArmorItem> CAMPEON_CHESTPLATE = piece("campeon_chestplate", CAMPEON, ArmorItem.Type.CHESTPLATE);
    public static final Supplier<ArmorItem> CAMPEON_LEGGINGS = piece("campeon_leggings", CAMPEON, ArmorItem.Type.LEGGINGS);
    public static final Supplier<ArmorItem> CAMPEON_BOOTS = piece("campeon_boots", CAMPEON, ArmorItem.Type.BOOTS);

    private ZianArmor() {}

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> material(String name) {
        return MATERIALS.register(name, () -> {
            ArmorMaterial vanilla = NETHERITE_STATS.get();
            return new ArmorMaterial(
                new EnumMap<>(vanilla.defense()),
                vanilla.enchantmentValue(),
                SoundEvents.ARMOR_EQUIP_NETHERITE,
                () -> Ingredient.of(Items.NETHERITE_INGOT),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(ZianUtilitiesMod.MOD_ID, name))),
                vanilla.toughness(),
                vanilla.knockbackResistance()
            );
        });
    }

    private static Supplier<ArmorItem> piece(String name, DeferredHolder<ArmorMaterial, ArmorMaterial> material, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new ArmorItem(material, type,
            new Item.Properties().fireResistant().durability(type.getDurability(37))));
    }

    public static void register(IEventBus bus) {
        MATERIALS.register(bus);
        ITEMS.register(bus);
        bus.addListener(ZianArmor::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CreativeModeTabs.COMBAT)) return;
        for (Supplier<ArmorItem> item : List.of(
            CAPTURA_HELMET, CAPTURA_CHESTPLATE, CAPTURA_LEGGINGS, CAPTURA_BOOTS,
            EXPLORADOR_HELMET, EXPLORADOR_CHESTPLATE, EXPLORADOR_LEGGINGS, EXPLORADOR_BOOTS,
            CAMPEON_HELMET, CAMPEON_CHESTPLATE, CAMPEON_LEGGINGS, CAMPEON_BOOTS
        )) event.accept(item.get());
    }
}
