package com.zianblk.zianutilities.neoforge.hub.client;

import com.zianblk.zianutilities.neoforge.hub.ZianHubNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Entry point for player-facing Zian Utilities features. */
public final class ZianHubScreen extends Screen {
    private static final int GOLD = 0xFFF0C75E;
    private static final int WHITE = 0xFFF4F7FA;
    private static final int MUTED = 0xFF9FAAB5;
    private final ZianHubNetwork.State state;
    private Button questsButton;

    public ZianHubScreen(ZianHubNetwork.State state) {
        super(Component.literal("Zian Utilities"));
        this.state = state;
    }

    public static void receive(ZianHubNetwork.State state) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new ZianHubScreen(state)));
    }

    private boolean compact() { return width < 500; }

    private int cardWidth() {
        return compact() ? Math.min(width - 24, 330) : Math.min((width - 40) / 2, 232);
    }

    private int cardHeight() { return compact() ? 78 : 138; }

    private int firstX() {
        return compact() ? (width - cardWidth()) / 2 : width / 2 - cardWidth() - 5;
    }

    private int firstY() { return compact() ? 49 : Math.max(55, height / 2 - 76); }

    private int secondX() { return compact() ? firstX() : firstX() + cardWidth() + 10; }

    private int secondY() { return compact() ? firstY() + cardHeight() + 8 : firstY(); }

    @Override
    protected void init() {
        int w = cardWidth();
        questsButton = addRenderableWidget(Button.builder(Component.literal("Abrir misiones"), button ->
                ZianHubNetwork.requestQuests())
            .bounds(firstX() + 10, firstY() + cardHeight() - 29, w - 20, 19).build());
        questsButton.active = state.questsEnabled();
        Button gachas = addRenderableWidget(Button.builder(Component.literal("Próximamente"), button -> {})
            .bounds(secondX() + 10, secondY() + cardHeight() - 29, w - 20, 19).build());
        gachas.active = false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The hub supplies its own full-screen backdrop.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE00D1117);
        graphics.fill(0, 0, width, 4, GOLD);
        graphics.drawCenteredString(font, title, width / 2, 12, GOLD);
        graphics.drawCenteredString(font, Component.literal("Elige una sección"), width / 2, 28, MUTED);

        drawCard(graphics, firstX(), firstY(), cardWidth(), cardHeight(), GOLD);
        drawCard(graphics, secondX(), secondY(), cardWidth(), cardHeight(), 0xFF6F7C88);
        drawQuestContent(graphics);
        drawGachaContent(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawQuestContent(GuiGraphics graphics) {
        int x = firstX();
        int y = firstY();
        graphics.renderItem(new ItemStack(Items.BOOK), x + 11, y + 11);
        graphics.drawString(font, Component.literal("Misiones"), x + 35, y + 13, WHITE, false);
        graphics.drawString(font, Component.literal(state.questsEnabled()
            ? "Captura y batallas salvajes" : "Sin misión activa"), x + 11, y + 35, MUTED, false);
        if (!compact()) {
            graphics.drawString(font, Component.literal("Objetivos globales, avance personal"),
                x + 11, y + 52, MUTED, false);
        }
    }

    private void drawGachaContent(GuiGraphics graphics) {
        int x = secondX();
        int y = secondY();
        graphics.renderItem(new ItemStack(Items.AMETHYST_SHARD), x + 11, y + 11);
        graphics.drawString(font, Component.literal("Gachas"), x + 35, y + 13, WHITE, false);
        graphics.drawString(font, Component.literal("Se añadirá más adelante"),
            x + 11, y + 35, MUTED, false);
        if (!compact()) {
            graphics.drawString(font, Component.literal("Sin compras ni recompensas aún"),
                x + 11, y + 52, MUTED, false);
        }
    }

    private static void drawCard(GuiGraphics graphics, int x, int y, int w, int h, int accent) {
        graphics.fill(x, y, x + w, y + h, 0xFF59616B);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xF0181D24);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 4, accent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
