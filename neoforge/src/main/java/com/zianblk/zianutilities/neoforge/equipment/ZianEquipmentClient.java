package com.zianblk.zianutilities.neoforge.equipment;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only bow predicates; never loads Minecraft client classes on a dedicated server. */
public final class ZianEquipmentClient {
    private ZianEquipmentClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ZianEquipmentClient::setup);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (ZianBowItem bow : ZianArmor.bows()) {
                ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"),
                    (stack, level, entity, seed) -> entity == null || entity.getUseItem() != stack ? 0.0F
                        : (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F);
                ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem()
                        && entity.getUseItem() == stack ? 1.0F : 0.0F);
            }
        });
    }
}
