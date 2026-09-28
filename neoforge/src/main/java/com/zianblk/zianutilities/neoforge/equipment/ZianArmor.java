package com.zianblk.zianutilities.neoforge.equipment;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Three themed families. Existing armor IDs stay unchanged for saved worlds. */
public final class ZianArmor {
    private static final DeferredRegister<ArmorMaterial> MATERIALS =
        DeferredRegister.create(Registries.ARMOR_MATERIAL, ZianUtilitiesMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ZianUtilitiesMod.MOD_ID);
    private static final List<Supplier<? extends Item>> COMBAT_ITEMS = new ArrayList<>();
    private static final List<Supplier<? extends Item>> TOOLS_ITEMS = new ArrayList<>();
    private static final Tier ENHANCED_NETHERITE = new SimpleTier(
        BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2032, 10.0F, 5.0F, 16,
        () -> Ingredient.of(Items.NETHERITE_INGOT));

    static {
        registerFamily("captura");   // Pikachu
        registerFamily("explorador"); // Dragonite
        registerFamily("campeon");    // Lucario
    }

    private ZianArmor() {}

    private static void registerFamily(String theme) {
        DeferredHolder<ArmorMaterial, ArmorMaterial> material = MATERIALS.register(theme, () -> {
            ArmorMaterial netherite = net.minecraft.world.item.ArmorMaterials.NETHERITE.value();
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(netherite.defense());
            for (ArmorItem.Type type : List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
                ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
                defense.put(type, netherite.getDefense(type) + 1);
            }
            return new ArmorMaterial(defense, netherite.enchantmentValue(),
                SoundEvents.ARMOR_EQUIP_NETHERITE,
                () -> Ingredient.of(Items.NETHERITE_INGOT),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(ZianUtilitiesMod.MOD_ID, theme))),
                netherite.toughness() + 1.0F,
                netherite.knockbackResistance() + 0.05F);
        });

        for (ArmorItem.Type type : List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
            String suffix = type.getName();
            COMBAT_ITEMS.add(ITEMS.register(theme + "_" + suffix, () -> new ArmorItem(material, type,
                new Item.Properties().fireResistant().durability(type.getDurability(37)))));
        }

        COMBAT_ITEMS.add(ITEMS.register(theme + "_sword", () -> new SwordItem(ENHANCED_NETHERITE,
            toolProperties().attributes(SwordItem.createAttributes(ENHANCED_NETHERITE, 3, -2.4F)))));
        TOOLS_ITEMS.add(ITEMS.register(theme + "_axe", () -> new AxeItem(ENHANCED_NETHERITE,
            toolProperties().attributes(AxeItem.createAttributes(ENHANCED_NETHERITE, 5.0F, -3.0F)))));
        TOOLS_ITEMS.add(ITEMS.register(theme + "_pickaxe", () -> new PickaxeItem(ENHANCED_NETHERITE,
            toolProperties().attributes(PickaxeItem.createAttributes(ENHANCED_NETHERITE, 1.0F, -2.8F)))));
        TOOLS_ITEMS.add(ITEMS.register(theme + "_shovel", () -> new ShovelItem(ENHANCED_NETHERITE,
            toolProperties().attributes(ShovelItem.createAttributes(ENHANCED_NETHERITE, 1.5F, -3.0F)))));
        TOOLS_ITEMS.add(ITEMS.register(theme + "_hoe", () -> new HoeItem(ENHANCED_NETHERITE,
            toolProperties().attributes(HoeItem.createAttributes(ENHANCED_NETHERITE, -4.0F, 0.0F)))));
        COMBAT_ITEMS.add(ITEMS.register(theme + "_bow", () -> new ZianBowItem(
            new Item.Properties().fireResistant().durability(2032))));
        COMBAT_ITEMS.add(ITEMS.register(theme + "_shield", () -> new ZianShieldItem(
            new Item.Properties().fireResistant().durability(2032).attributes(shieldAttributes(theme)))));
    }

    private static Item.Properties toolProperties() {
        return new Item.Properties().fireResistant();
    }

    private static ItemAttributeModifiers shieldAttributes(String theme) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ZianUtilitiesMod.MOD_ID, theme + "_shield_bonus");
        return ItemAttributeModifiers.builder()
            .add(Attributes.ARMOR, new AttributeModifier(id, 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND)
            .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND)
            .add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, 0.1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND)
            .build();
    }

    public static void register(IEventBus bus) {
        MATERIALS.register(bus);
        ITEMS.register(bus);
        bus.addListener(ZianArmor::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        List<Supplier<? extends Item>> entries = event.getTabKey().equals(CreativeModeTabs.COMBAT)
            ? COMBAT_ITEMS : event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)
                ? TOOLS_ITEMS : List.of();
        for (Supplier<? extends Item> entry : entries) event.accept(entry.get());
    }
}
