package com.zianblk.zianutilities.neoforge.gacha.client;

import com.zianblk.zianutilities.neoforge.gacha.GachaNetwork;
import com.zianblk.zianutilities.neoforge.hub.ZianHubNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Client preview and admin editor. All mutation and random selection happens on the server. */
public final class GachaScreen extends Screen {
    private static final int GOLD = 0xFFF0C75E;
    private static final int WHITE = 0xFFF4F7FA;
    private static final int MUTED = 0xFF9FAAB5;
    private static final int GREEN = 0xFF78D498;
    private GachaNetwork.State state;
    private int poolId;
    private int prizePage;
    private int pendingPage;
    private boolean editing;
    private boolean confirming;
    private EditBox nameBox;

    private GachaScreen(GachaNetwork.State state, int poolId, int prizePage, int pendingPage,
                        boolean editing, boolean confirming) {
        super(Component.literal("Zian Utilities | Gachas"));
        this.state = state;
        this.poolId = poolId;
        this.prizePage = prizePage;
        this.pendingPage = pendingPage;
        this.editing = editing && state.admin();
        this.confirming = confirming;
    }

    public static void receive(GachaNetwork.State state) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (state.open() || !(minecraft.screen instanceof GachaScreen old)) {
                minecraft.setScreen(new GachaScreen(state, firstId(state), 0, 0, false, false));
            } else {
                minecraft.setScreen(new GachaScreen(state, old.poolId, old.prizePage,
                    old.pendingPage, old.editing, false));
            }
        });
    }

    private static int firstId(GachaNetwork.State state) {
        return state.pools().isEmpty() ? 0 : state.pools().getFirst().id();
    }

    private GachaNetwork.PoolView pool() {
        for (var pool : state.pools()) if (pool.id() == poolId) return pool;
        return state.pools().isEmpty() ? null : state.pools().getFirst();
    }

    private int poolIndex() {
        for (int i = 0; i < state.pools().size(); i++)
            if (state.pools().get(i).id() == poolId) return i;
        return 0;
    }

    private void reopen() {
        Minecraft.getInstance().setScreen(new GachaScreen(state, poolId,
            prizePage, pendingPage, editing, confirming));
    }

    private int left() { return Math.max(10, (width - Math.min(width - 20, 470)) / 2); }
    private int contentWidth() { return Math.min(width - 20, 470); }

    @Override
    protected void init() {
        addRenderableWidget(new StyledButton(10, 9, 55, 18, "Inicio", ZianHubNetwork::requestHub));
        if (state.admin()) addRenderableWidget(new StyledButton(width - 81, 9, 71, 18,
            editing ? "Jugador" : "Editar", () -> { editing = !editing; confirming = false; reopen(); }));
        int x = left(), w = contentWidth();
        addRenderableWidget(new StyledButton(x, 40, 40, 19, "<", () -> movePool(-1)));
        addRenderableWidget(new StyledButton(x + w - 40, 40, 40, 19, ">", () -> movePool(1)));
        GachaNetwork.PoolView pool = pool();
        if (editing) initAdmin(x, w, pool);
        else initPlayer(x, w, pool);
    }

    private void movePool(int delta) {
        if (state.pools().isEmpty()) return;
        int index = Math.floorMod(poolIndex() + delta, state.pools().size());
        poolId = state.pools().get(index).id();
        prizePage = 0;
        confirming = false;
        reopen();
    }

    private void initPlayer(int x, int w, GachaNetwork.PoolView pool) {
        if (pool != null) {
            int prizeCount = pool.prizes().size();
            addRenderableWidget(new StyledButton(x + 8, 202, 46, 18, "◀", () -> {
                prizePage = Math.max(0, prizePage - 1); reopen();
            })).active = prizePage > 0;
            addRenderableWidget(new StyledButton(x + w - 54, 202, 46, 18, "▶", () -> {
                prizePage = Math.min(Math.max(0, (prizeCount - 1) / 4), prizePage + 1); reopen();
            })).active = (prizePage + 1) * 4 < prizeCount;
            StyledButton roll = addRenderableWidget(new StyledButton(x + w / 2 - 77, 228, 154, 21,
                confirming ? "Confirmar tirada" : "Girar una vez", () -> {
                    if (!confirming) { confirming = true; reopen(); }
                    else { confirming = false; GachaNetwork.request(9, pool.id(), 0, ""); }
                }));
            roll.active = pool.enabled() && state.paymentEnabled() && !pool.prizes().isEmpty();
        }
        if (!state.pending().isEmpty()) {
            pendingPage = Math.min(pendingPage, state.pending().size() - 1);
            addRenderableWidget(new StyledButton(x, 284, 40, 18, "<", () -> {
                pendingPage = Math.floorMod(pendingPage - 1, state.pending().size()); reopen();
            }));
            addRenderableWidget(new StyledButton(x + w - 40, 284, 40, 18, ">", () -> {
                pendingPage = (pendingPage + 1) % state.pending().size(); reopen();
            }));
            var pending = state.pending().get(pendingPage);
            addRenderableWidget(new StyledButton(x + w / 2 - 65, 308, 130, 19,
                "Reclamar premio", () -> GachaNetwork.request(10, 0, 0, pending.id())));
        }
        addRenderableWidget(new StyledButton(x + w - 76, height - 27, 76, 18,
            "Actualizar", () -> GachaNetwork.request(0, 0, 0, "")));
    }

    private void initAdmin(int x, int w, GachaNetwork.PoolView pool) {
        addRenderableWidget(new StyledButton(x + w - 123, 72, 115, 18,
            "Crear gacha", () -> GachaNetwork.request(1, 0, 0, "")));
        if (pool == null) return;
        nameBox = new EditBox(font, x + 8, 72, Math.min(160, w - 140), 18, Component.literal("Nombre"));
        nameBox.setMaxLength(32);
        nameBox.setValue(pool.name());
        addRenderableWidget(nameBox);
        addRenderableWidget(new StyledButton(x + 8, 94, 76, 18, "Guardar", () ->
            GachaNetwork.request(2, pool.id(), 0, nameBox.getValue())));
        addRenderableWidget(new StyledButton(x + 90, 94, 153, 18,
            "Ticket: " + ticketName(pool.ticket()), () -> GachaNetwork.request(3, pool.id(), 0, "")));
        addRenderableWidget(new StyledButton(x + 249, 94, 26, 18, "-", () ->
            GachaNetwork.request(4, pool.id(), -1, "")));
        addRenderableWidget(new StyledButton(x + 279, 94, 26, 18, "+", () ->
            GachaNetwork.request(4, pool.id(), 1, "")));
        addRenderableWidget(new StyledButton(x + 8, 117, 130, 18,
            "Añadir objeto en mano", () -> GachaNetwork.request(5, pool.id(), 0, "")));
        addRenderableWidget(new StyledButton(x + w - 116, 117, 108, 18,
            pool.enabled() ? "Desactivar" : "Publicar", () -> GachaNetwork.request(8, pool.id(), 0, "")));
        for (int row = 0; row < 4; row++) {
            int index = prizePage * 4 + row;
            if (index >= pool.prizes().size()) break;
            int y = 150 + row * 26;
            addRenderableWidget(new StyledButton(x + w - 106, y, 25, 18, "-", () ->
                GachaNetwork.request(6, pool.id(), index, "-1")));
            addRenderableWidget(new StyledButton(x + w - 77, y, 25, 18, "+", () ->
                GachaNetwork.request(6, pool.id(), index, "1")));
            addRenderableWidget(new StyledButton(x + w - 48, y, 40, 18, "Quitar", () ->
                GachaNetwork.request(7, pool.id(), index, "")));
        }
        addRenderableWidget(new StyledButton(x + 8, 260, 42, 18, "◀", () -> {
            prizePage = Math.max(0, prizePage - 1); reopen();
        })).active = prizePage > 0;
        addRenderableWidget(new StyledButton(x + w - 50, 260, 42, 18, "▶", () -> {
            prizePage++; reopen();
        })).active = (prizePage + 1) * 4 < pool.prizes().size();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // This screen draws its own opaque background to avoid stacked NeoForge blur.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE00D1117);
        graphics.fill(0, 0, width, 4, GOLD);
        graphics.drawCenteredString(font, title, width / 2, 12, GOLD);
        int x = left(), w = contentWidth();
        GachaNetwork.PoolView pool = pool();
        graphics.drawCenteredString(font, Component.literal(pool == null ? "No hay gachas disponibles"
            : pool.name() + (state.admin() && !pool.enabled() ? " (sin publicar)" : "")), width / 2, 45, WHITE);
        if (editing) renderAdmin(graphics, x, w, pool);
        else renderPlayer(graphics, x, w, pool);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayer(GuiGraphics graphics, int x, int w, GachaNetwork.PoolView pool) {
        if (pool != null) {
            card(graphics, x, 65, w, 158);
            graphics.drawString(font, "Costo: " + pool.cost() + " " + ticketName(pool.ticket()),
                x + 8, 75, GOLD, false);
            long total = pool.prizes().stream().mapToLong(GachaNetwork.PrizeView::weight).sum();
            for (int row = 0; row < 4; row++) {
                int index = prizePage * 4 + row;
                if (index >= pool.prizes().size()) break;
                var prize = pool.prizes().get(index);
                int y = 96 + row * 24;
                graphics.renderItem(prize.item(), x + 8, y);
                graphics.drawString(font, prize.item().getHoverName().getString(),
                    x + 30, y + 2, WHITE, false);
                String odds = total <= 0 ? "0%" : String.format(Locale.ROOT, "%.2f%%",
                    prize.weight() * 100.0 / total);
                graphics.drawString(font, odds, x + w - 53, y + 2, GOLD, false);
            }
            graphics.drawCenteredString(font, Component.literal(
                state.paymentEnabled() ? (confirming ? "Confirma: se descontarán tickets"
                    : "Cada tirada da un objeto") : "Tiradas de pago desactivadas"),
                width / 2, 255, MUTED);
        }
        if (!state.pending().isEmpty()) {
            var pending = state.pending().get(Math.min(pendingPage, state.pending().size() - 1));
            graphics.drawString(font, "Premios pendientes: " + state.pending().size(),
                x, 273, GOLD, false);
            graphics.renderItem(pending.item(), x + 48, 285);
            graphics.drawCenteredString(font, pending.item().getHoverName(),
                width / 2, 289, WHITE);
        }
    }

    private void renderAdmin(GuiGraphics graphics, int x, int w, GachaNetwork.PoolView pool) {
        if (pool == null) return;
        card(graphics, x, 61, w, 229);
        graphics.drawString(font, "Costo: " + pool.cost() + " ticket(s)",
            x + 313, 99, MUTED, false);
        graphics.drawString(font, pool.enabled() ? "Publicado" : "Borrador",
            x + 145, 121, pool.enabled() ? GREEN : MUTED, false);
        long total = pool.prizes().stream().mapToLong(GachaNetwork.PrizeView::weight).sum();
        for (int row = 0; row < 4; row++) {
            int index = prizePage * 4 + row;
            if (index >= pool.prizes().size()) break;
            var prize = pool.prizes().get(index);
            int y = 150 + row * 26;
            graphics.renderItem(prize.item(), x + 8, y);
            graphics.drawString(font, prize.item().getHoverName().getString(),
                x + 30, y + 3, WHITE, false);
            graphics.drawString(font, String.format(Locale.ROOT, "%d · %.2f%%",
                prize.weight(), total <= 0 ? 0 : prize.weight() * 100.0 / total),
                x + Math.max(110, w - 193), y + 3, GOLD, false);
        }
        graphics.drawCenteredString(font, Component.literal(
            "Editar un gacha publicado lo devuelve a borrador"), width / 2, 292, MUTED);
    }

    private static String ticketName(String id) {
        return id.substring(id.indexOf(':') + 1).replace("ticket", "").toUpperCase(Locale.ROOT);
    }

    private static void card(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF59616B);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF181D24);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 4, GOLD);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static final class StyledButton extends AbstractButton {
        private final Runnable action;
        StyledButton(int x, int y, int width, int height, String label, Runnable action) {
            super(x, y, width, height, Component.literal(label));
            this.action = action;
        }
        @Override public void onPress() { action.run(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int border = isHovered && active ? GOLD : (active ? 0xFF526575 : 0xFF3B4148);
            graphics.fill(getX(), getY(), getX() + width, getY() + height,
                active ? 0xFF20262D : 0xFF161A20);
            graphics.renderOutline(getX(), getY(), width, height, border);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + width / 2, getY() + (height - 8) / 2,
                active ? WHITE : 0xFF7C838B);
        }
    }
}

