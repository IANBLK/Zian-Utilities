package com.zianblk.zianutilities.neoforge.quests.client;

import com.zianblk.zianutilities.neoforge.hub.ZianHubNetwork;
import com.zianblk.zianutilities.neoforge.quests.ProgressionQuestNetwork;
import com.zianblk.zianutilities.neoforge.quests.ProgressionQuestRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Read-only presentation of server-owned weekly and campaign state. */
public final class ProgressionQuestScreen extends Screen {
    private static final int GOLD = 0xFFF0C75E;
    private static final int WHITE = 0xFFF4F7FA;
    private static final int MUTED = 0xFF9FAAB5;
    private static final int GREEN = 0xFF78D498;
    private static final DateTimeFormatter RESET = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.forLanguageTag("es"))
        .withZone(ZoneId.of("America/Guayaquil"));
    private ProgressionQuestNetwork.State state;
    private int selectedGeneration;
    private int albumPage;
    private boolean showAlbum;
    private StyledButton accept;

    public ProgressionQuestScreen(ProgressionQuestNetwork.State state) {
        super(Component.literal("Zian Utilities | Misiones"));
        this.state = state;
    }

    public static void receive(ProgressionQuestNetwork.State state) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (state.open()) {
                minecraft.setScreen(new ProgressionQuestScreen(state));
            } else if (minecraft.screen instanceof ProgressionQuestScreen screen) {
                screen.state = state;
                screen.rebuildWidgets();
            }
        });
    }

    @Override protected void init() {
        int x = width / 2;
        addRenderableWidget(new StyledButton(10, 9, 70, 20, "Inicio", ZianHubNetwork::requestHub));
        addRenderableWidget(new StyledButton(x - 170, 35, 110, 20, "Cada 3 horas", ZianHubNetwork::requestQuests));
        addRenderableWidget(new StyledButton(x - 55, 35, 110, 20, "Semanales", () -> ProgressionQuestNetwork.request((byte) 0, 0)));
        addRenderableWidget(new StyledButton(x + 60, 35, 110, 20, "Campañas", () -> ProgressionQuestNetwork.request((byte) 1, 0)));

        if (state.tab() == 0) {
            accept = addRenderableWidget(new StyledButton(x - 80, Math.min(height - 35, 211), 160, 20,
                state.weeklyAccepted() ? "Objetivos aceptados" : "Aceptar objetivos",
                () -> ProgressionQuestNetwork.request((byte) 2, 0)));
            accept.active = !state.weeklyAccepted();
        } else {
            addRenderableWidget(new StyledButton(x - 145, 69, 48, 20, "<", () -> {
                selectedGeneration = (selectedGeneration + 8) % 9;
                albumPage = 0;
                showAlbum = false;
                rebuildWidgets();
            }));
            addRenderableWidget(new StyledButton(x + 97, 69, 48, 20, ">", () -> {
                selectedGeneration = (selectedGeneration + 1) % 9;
                albumPage = 0;
                showAlbum = false;
                rebuildWidgets();
            }));
            if (selectedGeneration < state.campaigns().size()) {
                ProgressionQuestRuntime.Campaign campaign = state.campaigns().get(selectedGeneration);
                if (campaign.accepted()) {
                    addRenderableWidget(new StyledButton(x + 150, 69, 90, 20,
                        showAlbum ? "Resumen" : "Capturados", () -> {
                            showAlbum = !showAlbum;
                            rebuildWidgets();
                        }));
                }
                if (showAlbum && campaign.accepted()) {
                    int pageSize = albumPageSize();
                    int pages = Math.max(1, (campaign.species().size() + pageSize - 1) / pageSize);
                    albumPage = Math.min(albumPage, pages - 1);
                    StyledButton previous = addRenderableWidget(new StyledButton(x - 100,
                        Math.min(height - 35, 211), 50, 20, "<", () -> {
                            albumPage--;
                            rebuildWidgets();
                        }));
                    previous.active = albumPage > 0;
                    StyledButton next = addRenderableWidget(new StyledButton(x + 50,
                        Math.min(height - 35, 211), 50, 20, ">", () -> {
                            albumPage++;
                            rebuildWidgets();
                        }));
                    next.active = albumPage + 1 < pages;
                } else {
                    accept = addRenderableWidget(new StyledButton(x - 80, Math.min(height - 35, 211), 160, 20,
                        campaign.accepted() ? "Campaña aceptada" : "Aceptar campaña",
                        () -> ProgressionQuestNetwork.request((byte) 3, selectedGeneration)));
                    accept.active = campaign.available() && campaign.finalGoal() > 0 && !campaign.accepted();
                }
            }
        }
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The screen provides its own overlay; NeoForge's menu blur would darken it twice.
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE00D1117);
        graphics.fill(0, 0, width, 4, GOLD);
        graphics.drawCenteredString(font, title, width / 2, 12, GOLD);
        if (state.tab() == 0) drawWeekly(graphics);
        else drawCampaign(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawWeekly(GuiGraphics graphics) {
        graphics.drawCenteredString(font, Component.literal(state.weeklyTest()
            ? "Rotación semanal de prueba · sin pago"
            : "Reinicio semanal: " + RESET.format(Instant.ofEpochMilli(state.weeklyEndsAt()))),
            width / 2, 65, MUTED);
        int w = Math.min(230, (width - 42) / 2);
        int left = width / 2 - w - 5;
        int top = 89;
        card(graphics, left, top, w, 106, state.capturePaid() ? GREEN : GOLD);
        card(graphics, left + w + 10, top, w, 106, state.battlePaid() ? GREEN : GOLD);
        graphics.drawString(font, "01  CAPTURAS", left + 10, top + 10, GOLD, false);
        graphics.drawString(font, "Captura 25 Pokémon", left + 10, top + 30, WHITE, false);
        graphics.drawString(font, Math.min(25, state.captures()) + " / 25", left + 10, top + 47,
            state.capturePaid() ? GREEN : MUTED, false);
        graphics.drawString(font, "Generaciones activas", left + 10, top + 64, MUTED, false);
        graphics.drawString(font, state.weeklyTest() ? "Prueba sin pago" :
            !state.rewardsEnabled() ? "Pago desactivado" :
            "Premio: " + state.captureAmount() + " " + friendly(state.captureCurrency()), left + 10, top + 85,
            GOLD, false);

        int right = left + w + 10;
        graphics.drawString(font, "02  BATALLAS SALVAJES", right + 10, top + 10, GOLD, false);
        graphics.drawString(font, "Gana 50 batallas", right + 10, top + 30, WHITE, false);
        graphics.drawString(font, Math.min(50, state.battles()) + " / 50", right + 10, top + 47,
            state.battlePaid() ? GREEN : MUTED, false);
        graphics.drawString(font, "Solo Pokémon salvajes", right + 10, top + 64, MUTED, false);
        graphics.drawString(font, state.weeklyTest() ? "Prueba sin pago" :
            !state.rewardsEnabled() ? "Pago desactivado" :
            "Premio: " + state.battleAmount() + " " + friendly(state.battleCurrency()), right + 10, top + 85,
            GOLD, false);
        graphics.drawCenteredString(font, state.weeklyAccepted() ? "Avance personal · Reinicio cada lunes"
            : "Acepta para empezar; las acciones anteriores no cuentan", width / 2,
            Math.min(height - 11, 240), MUTED);
    }

    private void drawCampaign(GuiGraphics graphics) {
        if (selectedGeneration >= state.campaigns().size()) return;
        ProgressionQuestRuntime.Campaign c = state.campaigns().get(selectedGeneration);
        graphics.drawCenteredString(font, "Generación " + (selectedGeneration + 1), width / 2, 75, GOLD);
        int w = Math.min(470, width - 30);
        int x = (width - w) / 2;
        if (showAlbum && c.accepted()) {
            drawAlbum(graphics, c, x, w);
            return;
        }
        card(graphics, x, 100, w, 95, c.chapter() == 3 ? GREEN : GOLD);
        if (!c.available()) {
            graphics.drawCenteredString(font, "Se desbloquea al activar esta generación", width / 2, 137, MUTED);
            return;
        }
        if (!c.accepted()) {
            graphics.drawCenteredString(font, "Captura la mitad de las especies elegibles en 3 etapas", width / 2, 127, WHITE);
            graphics.drawCenteredString(font, "Meta final: " + c.finalGoal() + " de " + c.eligibleSpecies(),
                width / 2, 151, MUTED);
            return;
        }
        if (c.chapter() >= 3) {
            graphics.drawCenteredString(font, "✓ Campaña completada", width / 2, 124, GREEN);
            graphics.drawCenteredString(font, c.capturedSpecies() + " / " + c.finalGoal()
                + " especies registradas", width / 2, 151, WHITE);
            return;
        }
        graphics.drawCenteredString(font, "Etapa " + (c.chapter() + 1) + " de 3", width / 2, 112, GOLD);
        graphics.drawCenteredString(font, "Especies distintas capturadas: " + c.capturedSpecies()
            + " / " + c.goal(), width / 2, 132, WHITE);
        graphics.drawCenteredString(font, "Meta final: " + c.finalGoal() + " de " + c.eligibleSpecies()
            + " especies elegibles", width / 2, 151, MUTED);
        graphics.drawCenteredString(font, !state.rewardsEnabled() ? "Pago desactivado" :
            c.rewardPending() ? "Premio pendiente de recuperación"
            : "Premio de etapa: " + c.amount() + " " + friendly(c.currency()), width / 2, 173, MUTED);
        graphics.drawCenteredString(font, "Pulsa Capturados para ver tu registro personal",
            width / 2, Math.min(height - 11, 240), MUTED);
    }

    private int albumPageSize() {
        return Math.max(4, Math.min(14, ((Math.min(height - 48, 210) - 122) / 12) * 2));
    }

    private void drawAlbum(GuiGraphics graphics, ProgressionQuestRuntime.Campaign campaign, int x, int w) {
        card(graphics, x, 100, w, Math.max(95, Math.min(height - 148, 110)), GOLD);
        graphics.drawCenteredString(font, "Especies capturadas: " + campaign.species().size()
            + " · Avance total: " + campaign.capturedSpecies() + " / " + campaign.finalGoal(),
            width / 2, 108, WHITE);
        List<String> captured = campaign.species();
        int pageSize = albumPageSize();
        int from = albumPage * pageSize;
        int until = Math.min(captured.size(), from + pageSize);
        if (captured.isEmpty()) {
            graphics.drawCenteredString(font, "Todavía no hay especies registradas", width / 2, 145, MUTED);
        } else {
            for (int i = from; i < until; i++) {
                int local = i - from;
                int column = local % 2;
                int row = local / 2;
                graphics.drawString(font, "✓ " + friendly(captured.get(i)),
                    x + 12 + column * (w / 2), 123 + row * 12, GREEN, false);
            }
        }
        if (campaign.legacyCount() > 0) {
            graphics.drawCenteredString(font, campaign.legacyCount()
                + " capturas previas acreditadas sin nombres guardados", width / 2,
                Math.min(height - 11, 240), MUTED);
        } else {
            int pages = Math.max(1, (captured.size() + pageSize - 1) / pageSize);
            graphics.drawCenteredString(font, "Página " + (albumPage + 1) + " / " + pages,
                width / 2, Math.min(height - 11, 240), MUTED);
        }
    }

    private static void card(GuiGraphics graphics, int x, int y, int w, int h, int accent) {
        graphics.fill(x, y, x + w, y + h, 0xFF59616B);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF181D24);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 4, accent);
    }

    private static String friendly(String id) {
        String raw = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        return raw.isEmpty() ? id : raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static final class StyledButton extends AbstractButton {
        private final Runnable action;

        private StyledButton(int x, int y, int width, int height, String label, Runnable action) {
            super(x, y, width, height, Component.literal(label));
            this.action = action;
        }
        @Override public void onPress() { action.run(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int border = isHovered && active ? GOLD : active ? 0xFF526575 : 0xFF3B4148;
            graphics.fill(getX(), getY(), getX() + width, getY() + height,
                active ? 0xFF20262D : 0xFF161A20);
            graphics.renderOutline(getX(), getY(), width, height, border);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + width / 2, getY() + (height - 8) / 2,
                active ? 0xFFE2E8F0 : 0xFF7C838B);
        }
    }
}
