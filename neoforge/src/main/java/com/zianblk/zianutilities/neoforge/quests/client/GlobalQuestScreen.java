package com.zianblk.zianutilities.neoforge.quests.client;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.zianblk.zianutilities.neoforge.quests.GlobalQuestNetwork;
import com.zianblk.zianutilities.neoforge.hub.ZianHubNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Client-only view. No progress or payment is decided here. */
public final class GlobalQuestScreen extends Screen {
    private static final int GOLD = 0xFFF0C75E;
    private static final int WHITE = 0xFFF4F7FA;
    private static final int MUTED = 0xFF9FAAB5;
    private static final int GREEN = 0xFF78D498;
    private static final ZoneId ECUADOR = ZoneId.of("America/Guayaquil");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private GlobalQuestNetwork.State state;
    private final FloatingState previewState = new FloatingState();
    private Button acceptButton;
    private boolean waiting;

    public GlobalQuestScreen(GlobalQuestNetwork.State state) {
        super(Component.literal("Zian Utilities | Misiones"));
        this.state = state;
    }

    public static void receive(GlobalQuestNetwork.State state) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (state.open()) {
                minecraft.setScreen(new GlobalQuestScreen(state));
            } else if (minecraft.screen instanceof GlobalQuestScreen screen) {
                screen.state = state;
                screen.waiting = false;
                screen.updateButtons();
            }
        });
    }

    @Override
    protected void init() {
        int x = width / 2;
        int controlsY = controlsY();
        addRenderableWidget(Button.builder(Component.literal("Inicio"), button ->
            ZianHubNetwork.requestHub()).bounds(10, 9, 55, 18).build());
        acceptButton = addRenderableWidget(Button.builder(Component.literal("Aceptar objetivos"), button -> {
            waiting = true;
            updateButtons();
            GlobalQuestNetwork.request(true, state.windowStartEpochMs());
        }).bounds(x - 111, controlsY, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Actualizar"), button -> {
            waiting = true;
            updateButtons();
            GlobalQuestNetwork.request(false, state.windowStartEpochMs());
        }).bounds(x + 1, controlsY, 110, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        if (acceptButton != null) {
            acceptButton.active = !state.accepted() && !waiting;
            acceptButton.setMessage(Component.literal(state.accepted() ? "Objetivos aceptados" : "Aceptar objetivos"));
        }
    }

    private boolean compact() { return width < 510; }

    private int cardTop() { return compact() ? 46 : Math.max(57, height / 2 - 86); }

    private int cardHeight() { return compact() ? Math.min(82, Math.max(65, (height - 105) / 2)) : 145; }

    private int controlsY() {
        return cardTop() + (compact() ? cardHeight() * 2 + 8 : cardHeight()) + 14;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The screen draws its own dark overlay; prevent a second menu blur.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE00D1117);
        graphics.fill(0, 0, width, 4, GOLD);
        graphics.drawCenteredString(font, title, width / 2, 12, GOLD);

        long seconds = Math.max(0, (state.expiresAtEpochMs() - System.currentTimeMillis()) / 1000);
        String reset = Instant.ofEpochMilli(state.expiresAtEpochMs()).atZone(ECUADOR).format(TIME);
        graphics.drawCenteredString(font,
            Component.literal("Misión global  ·  Reinicio " + reset + " Ecuador  ·  "
                + (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m"),
            width / 2, 27, MUTED);

        int gap = 10;
        int cardW = compact() ? Math.min(width - 24, 330) : Math.min((width - 40) / 2, 245);
        int left = compact() ? (width - cardW) / 2 : width / 2 - cardW - gap / 2;
        int top = cardTop();
        int battleX = compact() ? left : left + cardW + gap;
        int battleY = compact() ? top + cardHeight() + 8 : top;
        drawCaptureCard(graphics, left, top, cardW, cardHeight(), partialTick);
        drawBattleCard(graphics, battleX, battleY, cardW, cardHeight());

        String note = state.accepted() ? "Avance personal  ·  Misión compartida por todo el servidor"
            : "Acepta para empezar; las acciones anteriores no cuentan";
        graphics.drawCenteredString(font, Component.literal(note), width / 2,
            Math.min(height - 16, controlsY() + 25), MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCaptureCard(GuiGraphics graphics, int x, int y, int w, int h, float partialTick) {
        card(graphics, x, y, w, h, state.captureComplete() ? GREEN : GOLD);
        graphics.drawString(font, Component.literal("01  CAPTURA"), x + 10, y + 10, GOLD, false);
        String species = state.targetSpecies().isBlank() ? "Sin especie disponible"
            : friendly(state.targetSpecies());
        graphics.drawString(font, Component.literal(species), x + 10, y + 29, WHITE, false);
        graphics.drawString(font, Component.literal(state.captureComplete() ? "✓ Completado"
            : state.targetSpecies().isBlank() ? "Pausado" : "Captura 1 de esta especie"),
            x + 10, y + 43, state.captureComplete() ? GREEN : MUTED, false);

        if (h > 95) {
            graphics.drawString(font, Component.literal("Generaciones: " +
                (state.generationIds().isBlank() ? "ninguna" : state.generationIds())),
                x + 10, y + 64, MUTED, false);
        }
        graphics.drawString(font, Component.literal(reward(state.captureAmount(), state.captureCurrency())),
            x + 10, y + h - 18, state.rewardsEnabled() ? GOLD : MUTED, false);

        if (!state.targetSpecies().isBlank()) {
            int previewW = h > 95 ? 88 : 62;
            int previewH = h > 95 ? 87 : 57;
            int px = x + w - previewW - 8;
            int py = y + 7;
            graphics.fill(px, py, px + previewW, py + previewH, 0x8010161D);
            graphics.renderOutline(px, py, previewW, previewH, 0xFF343D47);
            renderPokemon(graphics, px + previewW / 2, py + previewH / 2 - 14, partialTick,
                h > 95 ? 23.0f : 16.0f);
        }
    }

    private void drawBattleCard(GuiGraphics graphics, int x, int y, int w, int h) {
        boolean complete = state.battleWins() >= 5;
        card(graphics, x, y, w, h, complete ? GREEN : GOLD);
        graphics.drawString(font, Component.literal("02  BATALLAS SALVAJES"), x + 10, y + 10, GOLD, false);
        graphics.drawString(font, Component.literal("Gana 5 batallas"), x + 10, y + 29, WHITE, false);
        graphics.drawString(font, Component.literal(complete ? "✓ Completado"
            : Math.min(5, state.battleWins()) + " / 5 victorias"),
            x + 10, y + 43, complete ? GREEN : MUTED, false);
        int barY = h > 95 ? y + 67 : y + 58;
        int segmentW = Math.max(12, (w - 30) / 5);
        for (int i = 0; i < 5; i++) {
            int bx = x + 10 + i * (segmentW + 2);
            graphics.fill(bx, barY, bx + segmentW, barY + 7,
                i < state.battleWins() ? GREEN : 0xFF39414A);
        }
        if (h > 95) {
            graphics.drawString(font, Component.literal("Solo combates contra Pokémon salvajes"),
                x + 10, y + 86, MUTED, false);
        }
        graphics.drawString(font, Component.literal(reward(state.battleAmount(), state.battleCurrency())),
            x + 10, y + h - 18, state.rewardsEnabled() ? GOLD : MUTED, false);
    }

    private void renderPokemon(GuiGraphics graphics, int centerX, int centerY, float partialTick, float scale) {
        ResourceLocation species = ResourceLocation.tryParse(state.targetSpecies());
        if (species == null || PokemonSpecies.INSTANCE.getByIdentifier(species) == null) return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(centerX, centerY, 90);
            PokemonGuiUtilsKt.drawProfilePokemon(species, graphics.pose(),
                new Quaternionf().rotationXYZ(0.08f, 0.45f, 0f), PoseType.PROFILE,
                previewState, partialTick, scale, ProfileTransformType.PROFILE,
                false, false, 1f, 1f, 1f, 1f, 0f, 0f, 13);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void card(GuiGraphics graphics, int x, int y, int w, int h, int accent) {
        graphics.fill(x, y, x + w, y + h, 0xFF59616B);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xF0181D24);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 4, accent);
    }

    private String reward(long amount, String currency) {
        return state.rewardsEnabled() ? "Premio: " + amount + " " + friendly(currency)
            : "Prueba sin pago";
    }

    private static String friendly(String id) {
        String raw = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        return raw.isEmpty() ? id : raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
    }

    @Override public boolean isPauseScreen() { return false; }
}
