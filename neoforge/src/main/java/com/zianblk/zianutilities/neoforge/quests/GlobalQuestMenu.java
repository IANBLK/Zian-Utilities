package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.quests.GlobalQuestOffer;
import com.zianblk.zianutilities.core.quests.GlobalQuestProgress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A vanilla chest menu: no client mod, packet, or custom screen is needed. */
public final class GlobalQuestMenu extends ChestMenu {
    private static final int CAPTURE_SLOT = 11;
    private static final int ACCEPT_SLOT = 13;
    private static final int BATTLE_SLOT = 15;
    private final ServerPlayer owner;
    private final SimpleContainer display;

    private GlobalQuestMenu(int id, Inventory inventory, ServerPlayer owner, SimpleContainer display) {
        super(MenuType.GENERIC_9x3, id, inventory, display, 3);
        this.owner = owner;
        this.display = display;
        refresh();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inventory, ignored) -> new GlobalQuestMenu(id, inventory, player, new SimpleContainer(27)),
            Component.literal("Zian Utilities - Misiones")));
    }

    public void refresh() {
        try {
            GlobalQuestOffer offer = GlobalQuestRuntime.offer(owner.getServer());
            GlobalQuestProgress progress = GlobalQuestRuntime.service(owner.getServer())
                .inspect(owner.getUUID(), offer);
            String target = offer.getTargetSpecies() == null ? "Pausada: sin especie disponible"
                : offer.getTargetSpecies();
            String capture = "Captura " + target + " - " + (progress != null && progress.getCaptureComplete() ? "✓" : "pendiente")
                + rewardText(offer.getCaptureAmount(), offer.getCaptureCurrency(), offer.getRewardsEnabled());
            String battles = "Gana 5 batallas salvajes - " + (progress == null ? 0 : progress.getBattleIds().size()) + "/5"
                + (progress != null && progress.getBattleComplete() ? " ✓" : "")
                + rewardText(offer.getBattleAmount(), offer.getBattleCurrency(), offer.getRewardsEnabled());
            display.setItem(CAPTURE_SLOT, icon(progress != null && progress.getCaptureComplete()
                ? Items.LIME_DYE : Items.ENDER_PEARL, capture));
            display.setItem(BATTLE_SLOT, icon(progress != null && progress.getBattleComplete()
                ? Items.LIME_DYE : Items.IRON_SWORD, battles));
            display.setItem(ACCEPT_SLOT, icon(progress == null ? Items.LIME_CONCRETE : Items.CLOCK,
                progress == null ? "Clic para aceptar ambos objetivos" : "Mision aceptada; cambia cada 3 horas"));
            broadcastChanges();
        } catch (Exception error) {
            owner.sendSystemMessage(Component.literal("No se pudo leer la misión; revisa la consola."));
        }
    }

    private static ItemStack icon(net.minecraft.world.item.Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static String rewardText(long amount, String currency, boolean enabled) {
        return enabled ? " | premio: " + amount + " " + currency : " | prueba sin pago";
    }

    @Override
    public void clicked(int slot, int button, ClickType click, Player player) {
        if (slot == ACCEPT_SLOT && player == owner && !owner.level().isClientSide()) {
            try {
                GlobalQuestOffer offer = GlobalQuestRuntime.offer(owner.getServer());
                GlobalQuestRuntime.service(owner.getServer()).accept(owner.getUUID(), offer);
                owner.sendSystemMessage(Component.literal("Aceptaste los dos objetivos de este bloque."));
                refresh();
            } catch (Exception error) {
                owner.sendSystemMessage(Component.literal("No se pudo guardar la aceptación; revisa la consola."));
            }
        }
        // The display is read-only; never move menu icons or player inventory items.
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}

