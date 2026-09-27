package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.quests.GlobalQuestOffer;
import com.zianblk.zianutilities.core.quests.GlobalQuestProgress;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A server-only, read-only vanilla menu; no client mod or custom packets. */
public final class GlobalQuestMenu extends ChestMenu {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/GlobalQuest");
    private static final ZoneId ECUADOR = ZoneId.of("America/Guayaquil");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CAPTURE_SLOT = 20;
    private static final int ACCEPT_SLOT = 22;
    private static final int BATTLE_SLOT = 24;
    private static final int ROWS = 5;
    private static final int SIZE = ROWS * 9;

    private final ServerPlayer owner;
    private final SimpleContainer display;

    private GlobalQuestMenu(int id, Inventory inventory, ServerPlayer owner, SimpleContainer display) {
        super(MenuType.GENERIC_9x5, id, inventory, display, ROWS);
        this.owner = owner;
        this.display = display;
        refresh();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inventory, ignored) -> new GlobalQuestMenu(id, inventory, player, new SimpleContainer(SIZE)),
            Component.literal("Zian Utilities | Misiones")));
    }

    public void refresh() {
        try {
            GlobalQuestOffer offer = GlobalQuestRuntime.offer(owner.getServer());
            GlobalQuestProgress progress = GlobalQuestRuntime.service(owner.getServer())
                .inspect(owner.getUUID(), offer);
            render(offer, progress);
            broadcastChanges();
        } catch (Exception error) {
            LOGGER.error("[ZIAN-GLOBAL-QUEST] action=menu_refresh playerUuid={} result=error", owner.getUUID(), error);
            owner.sendSystemMessage(Component.literal("No se pudo leer la misión; revisa la consola."));
        }
    }

    private void render(GlobalQuestOffer offer, GlobalQuestProgress progress) {
        display.clearContent();
        String resetTime = Instant.ofEpochMilli(offer.getExpiresAtEpochMs()).atZone(ECUADOR).format(TIME);
        long minutes = Math.max(0, (offer.getExpiresAtEpochMs() - System.currentTimeMillis() + 59_999) / 60_000);
        boolean accepted = progress != null;
        boolean captureComplete = accepted && progress.getCaptureComplete();
        int wins = accepted ? progress.getBattleIds().size() : 0;
        boolean battleComplete = wins == 5;
        boolean paused = offer.getTargetSpecies() == null;
        boolean rewardsActive = offer.getRewardsEnabled() && Boolean.getBoolean(GlobalQuestRuntime.REWARD_FLAG);
        String species = paused ? "Sin especie disponible" : speciesName(offer.getTargetSpecies());

        for (int slot = 0; slot < 9; slot++) {
            if (slot != 4) display.setItem(slot, decoration(Items.BLUE_STAINED_GLASS_PANE));
        }
        for (int slot = 36; slot < SIZE; slot++) {
            if (slot != 39 && slot != 40 && slot != 41) {
                display.setItem(slot, decoration(Items.GRAY_STAINED_GLASS_PANE));
            }
        }

        display.setItem(4, icon(Items.NETHER_STAR, "Misiones del servidor", ChatFormatting.AQUA,
            "Generaciones: " + (offer.getGenerationIds().isEmpty() ? "ninguna" : offer.getGenerationIds()),
            "Dos objetivos compartidos; avance individual.",
            "Se renuevan cada 3 horas (Ecuador)."));
        display.setItem(11, icon(paused ? Items.BARRIER : Items.ENDER_PEARL,
            "01 | Captura", ChatFormatting.GOLD,
            paused ? "Activa una generación para ver una especie." : "Especie objetivo: " + species,
            "Captura 1 Pokémon salvaje de esa especie.",
            accepted ? "Estado: " + (captureComplete ? "COMPLETADO" : "pendiente") : "Acepta la misión para comenzar."));
        display.setItem(13, icon(Items.CLOCK, "Próximo reinicio", ChatFormatting.YELLOW,
            "Hora: " + resetTime + " (Ecuador)",
            "Tiempo restante: " + minutes + " min",
            "Al reiniciar, acepta la nueva misión."));
        display.setItem(15, icon(Items.IRON_SWORD, "02 | Batallas salvajes", ChatFormatting.GOLD,
            "Gana 5 batallas contra Pokémon salvajes.",
            "No cuentan PvP, NPC ni capturas en combate.",
            accepted ? "Tu avance: " + wins + "/5" : "Acepta la misión para comenzar."));

        display.setItem(CAPTURE_SLOT, icon(captureComplete ? Items.LIME_DYE : paused ? Items.GRAY_DYE : Items.ORANGE_DYE,
            captureComplete ? "✓ Captura completada" : paused ? "Captura pausada" : "Captura pendiente",
            captureComplete ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
            paused ? "No hay especie elegible en las generaciones activas." : "Objetivo: " + species,
            rewardLine(offer.getCaptureAmount(), offer.getCaptureCurrency(), rewardsActive)));
        display.setItem(ACCEPT_SLOT, icon(accepted ? Items.BOOK : Items.EMERALD_BLOCK,
            accepted ? "Misión aceptada" : "Haz clic para aceptar", accepted ? ChatFormatting.GREEN : ChatFormatting.AQUA,
            accepted ? "Captura: " + (captureComplete ? "✓" : "pendiente") : "Acepta ambos objetivos con un clic.",
            accepted ? "Batallas: " + wins + "/5" : "Las acciones anteriores no cuentan.",
            "Renovación: " + resetTime + " (Ecuador)"));
        display.setItem(BATTLE_SLOT, icon(battleComplete ? Items.LIME_DYE : Items.ORANGE_DYE,
            battleComplete ? "✓ Batallas completadas" : "Batallas: " + wins + "/5",
            battleComplete ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
            "Cuenta una victoria por combate salvaje.",
            rewardLine(offer.getBattleAmount(), offer.getBattleCurrency(), rewardsActive)));

        for (int i = 0; i < 5; i++) {
            display.setItem(29 + i, icon(i < wins ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE,
                "Victoria " + (i + 1) + ": " + (i < wins ? "✓" : "pendiente"),
                i < wins ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        display.setItem(39, icon(Items.COPPER_INGOT, "Premio de captura", ChatFormatting.GOLD,
            rewardLine(offer.getCaptureAmount(), offer.getCaptureCurrency(), rewardsActive)));
        display.setItem(40, icon(Items.PAPER, "Estado del bloque", ChatFormatting.AQUA,
            accepted ? "Objetivos completos: " + ((captureComplete ? 1 : 0) + (battleComplete ? 1 : 0)) + "/2"
                : "Aún no has aceptado.",
            "Reinicio: " + resetTime + " (Ecuador)"));
        display.setItem(41, icon(Items.COPPER_INGOT, "Premio de batallas", ChatFormatting.GOLD,
            rewardLine(offer.getBattleAmount(), offer.getBattleCurrency(), rewardsActive)));
    }

    private static ItemStack decoration(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        return stack;
    }

    private static ItemStack icon(Item item, String title, ChatFormatting color, String... lines) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(color));
        List<Component> lore = new ArrayList<>(lines.length);
        for (String line : lines) {
            lore.add(Component.literal(line).withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)));
        }
        if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static String speciesName(String identifier) {
        String raw = identifier.substring(identifier.indexOf(':') + 1).replace('_', ' ');
        return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
    }

    private static String rewardLine(long amount, String currency, boolean enabled) {
        return enabled ? "Recompensa: " + amount + " " + currency : "Prueba sin pago";
    }

    @Override
    public void clicked(int slot, int button, ClickType click, Player player) {
        if (slot == ACCEPT_SLOT && player == owner && !owner.level().isClientSide()) {
            try {
                GlobalQuestOffer offer = GlobalQuestRuntime.offer(owner.getServer());
                GlobalQuestProgress existing = GlobalQuestRuntime.service(owner.getServer())
                    .inspect(owner.getUUID(), offer);
                if (existing == null) {
                    GlobalQuestRuntime.service(owner.getServer()).accept(owner.getUUID(), offer);
                    owner.sendSystemMessage(Component.literal("Aceptaste los dos objetivos de este bloque."));
                }
                refresh();
            } catch (Exception error) {
                LOGGER.error("[ZIAN-GLOBAL-QUEST] action=accept playerUuid={} result=error", owner.getUUID(), error);
                owner.sendSystemMessage(Component.literal("No se pudo guardar la aceptación; revisa la consola."));
            }
        }
        // Never move display icons or the player's items through this read-only screen.
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}

