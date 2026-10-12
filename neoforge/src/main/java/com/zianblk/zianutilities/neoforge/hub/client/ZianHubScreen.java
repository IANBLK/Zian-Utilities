package com.zianblk.zianutilities.neoforge.hub.client;

import com.zianblk.zianutilities.neoforge.hub.ZianHubNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Entry point for player-facing Zian Utilities features. */
public final class ZianHubScreen extends com.zianblk.zianutilities.neoforge.client.ResponsiveScreen {
    private static final int GOLD = 0xFFF0C75E;
    private static final int WHITE = 0xFFF4F7FA;
    private static final int MUTED = 0xFF9FAAB5;
    private final ZianHubNetwork.State state;
    private StyledButton questsButton;

    public ZianHubScreen(ZianHubNetwork.State state) {
        super(Component.literal("Zian Utilities"));
        this.state = state;
    }

    public static void receive(ZianHubNetwork.State state) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new ZianHubScreen(state)));
    }



    private int cardWidth() {
        return Math.min((width - 64) / 3, 184);
    }

    private int cardHeight() { return 168; }

    private int firstX() {
        return (width - 3 * cardWidth() - 24) / 2;
    }

    private int firstY() { return Math.max(58, height / 2 - cardHeight() / 2); }

    private int secondX() { return firstX() + cardWidth() + 12; }

    private int secondY() { return firstY(); }
    private int thirdX() { return secondX() + cardWidth() + 12; }

    @Override
    protected void init() {fitViewport(600,360);
        int w = cardWidth();
        questsButton = addRenderableWidget(new StyledButton(firstX() + 10,
            firstY() + cardHeight() - 29, w - 20, 19,
            Component.literal("Abrir misiones"), ZianHubNetwork::requestQuests));
        questsButton.active = state.questsEnabled();
        StyledButton gachas = addRenderableWidget(new StyledButton(secondX() + 10,
            secondY() + cardHeight() - 29, w - 20, 19,
            Component.literal("Abrir gachas"), ZianHubNetwork::requestGachas));
        gachas.active = state.gachasEnabled();
        StyledButton breeding = addRenderableWidget(new StyledButton(thirdX() + 10,
            firstY() + cardHeight() - 29, w - 20, 19,
            Component.literal("Abrir crianza"), ZianHubNetwork::requestBreeding));
        breeding.active = state.breedingEnabled();
        addRenderableWidget(new StyledButton(width-68,12,56,19,Component.literal("Cerrar"),this::onClose));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The hub supplies its own full-screen backdrop.
    }

    @Override
    protected void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE00D1117);
        graphics.fill(0, 0, width, 4, GOLD);
        graphics.drawCenteredString(font, title, width / 2, 12, GOLD);
        graphics.drawCenteredString(font, Component.literal("Elige una sección"), width / 2, 28, MUTED);

        drawCard(graphics, firstX(), firstY(), cardWidth(), cardHeight(), GOLD);
        drawCard(graphics, secondX(), secondY(), cardWidth(), cardHeight(), 0xFFB590E8);
        drawCard(graphics, thirdX(), firstY(), cardWidth(), cardHeight(), 0xFF7DCEAD);
        drawQuestContent(graphics);
        drawGachaContent(graphics);
        drawBreedingContent(graphics);
        graphics.drawCenteredString(font,Component.literal("Tu aventura, tus recompensas, tu equipo"),width/2,Math.min(height-24,firstY()+cardHeight()+18),MUTED);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void drawQuestContent(GuiGraphics graphics) {
        int x = firstX();
        int y = firstY();
        graphics.renderItem(new ItemStack(Items.BOOK), x + 11, y + 11);
        graphics.drawString(font, Component.literal("Misiones"), x + 35, y + 13, WHITE, false);
        graphics.drawWordWrap(font,Component.literal("Captura Pokémon y supera desafíos.\n\nObjetivos globales con avance personal."),x+11,y+40,cardWidth()-22,MUTED);
        status(graphics,x,y,state.questsEnabled());
    }

    private void drawGachaContent(GuiGraphics graphics) {
        int x = secondX();
        int y = secondY();
        graphics.renderItem(new ItemStack(Items.AMETHYST_SHARD), x + 11, y + 11);
        graphics.drawString(font, Component.literal("Gachas"), x + 35, y + 13, WHITE, false);
        graphics.drawWordWrap(font,Component.literal("Descubre recompensas con tickets.\n\nConsulta premios y probabilidades antes de girar."),x+11,y+40,cardWidth()-22,MUTED);
        status(graphics,x,y,state.gachasEnabled());
    }

    private void drawBreedingContent(GuiGraphics graphics){
        int x=thirdX(),y=firstY();
        graphics.renderItem(new ItemStack(Items.EGG),x+11,y+11);
        graphics.drawString(font,Component.literal("Crianza"),x+35,y+13,WHITE,false);
        graphics.drawWordWrap(font,Component.literal("Elige padres de tu equipo.\n\nConsulta la posible cría y gestiona tus crianzas."),x+11,y+40,cardWidth()-22,MUTED);
        status(graphics,x,y,state.breedingEnabled());
    }
    private void status(GuiGraphics graphics,int x,int y,boolean enabled){graphics.drawString(font,Component.literal(enabled?"Disponible":"No disponible"),x+11,y+cardHeight()-48,enabled?0xFF88D5AD:MUTED,false);}

    private static void drawCard(GuiGraphics graphics, int x, int y, int w, int h, int accent) {
        graphics.fill(x, y, x + w, y + h, 0xFF59616B);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF181D24);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 4, accent);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static final class StyledButton extends AbstractButton {
        private final Runnable action;

        private StyledButton(int x, int y, int width, int height, Component message, Runnable action) {
            super(x, y, width, height, message);
            this.action = action;
        }

        @Override public void onPress() { action.run(); }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hover = isHovered && active;
            int border = hover ? GOLD : (active ? 0xFF526575 : 0xFF3B4148);
            int fill = active ? 0xFF20262D : 0xFF161A20;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, fill);
            graphics.renderOutline(getX(), getY(), width, height, border);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + width / 2, getY() + (height - 8) / 2,
                active ? 0xFFE2E8F0 : 0xFF7C838B);
        }
    }
}
