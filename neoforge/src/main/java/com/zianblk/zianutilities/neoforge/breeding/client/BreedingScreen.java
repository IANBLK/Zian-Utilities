package com.zianblk.zianutilities.neoforge.breeding.client;

import com.zianblk.zianutilities.neoforge.breeding.BreedingNetwork;
import com.zianblk.zianutilities.neoforge.hub.ZianHubNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Same palette/cards/buttons as the quest and gacha panels; no inventory or fabricated items. */
public final class BreedingScreen extends Screen {
  private static final int GOLD = 0xFFF0C75E,
      WHITE = 0xFFF4F7FA,
      MUTED = 0xFF9FAAB5,
      GREEN = 0xFF78D498,
      RED = 0xFFF19191;
  private BreedingNetwork.State state;
  private boolean jobs;
  private int jobIndex, currencyIndex, ticks;
  private long received = System.nanoTime();
  private String hovered = "";

  private BreedingScreen(BreedingNetwork.State state) {
    super(Component.literal("Zian Utilities | Crianza"));
    this.state = state;
  }

  public static void receive(BreedingNetwork.State state) {
    Minecraft mc = Minecraft.getInstance();
    mc.execute(
        () -> {
          if (state.open()) mc.setScreen(new BreedingScreen(state));
          else if (mc.screen instanceof BreedingScreen old) {
            old.state = state;
            old.received = System.nanoTime();
            old.jobIndex = Math.max(0, Math.min(old.jobIndex, state.jobs().size() - 1));
            old.rebuildWidgets();
          }
        });
  }

  private int left() {
    return (width - contentWidth()) / 2;
  }

  private int contentWidth() {
    return Math.min(width - 20, 620);
  }

  private void act(int kind, String target, boolean maximum) {
    BreedingNetwork.action(state, kind, target, maximum);
  }

  private void button(int x, int y, int w, String label, Runnable action, boolean enabled) {
    var b = addRenderableWidget(new StyledButton(x, y, w, 19, label, action));
    b.active = enabled;
  }

  private BreedingNetwork.Job job() {
    return state.jobs().stream().filter(j -> j.id().equals(state.job())).findFirst().orElse(null);
  }

  private BreedingNetwork.Currency currency() {
    return state.currencies().stream()
        .filter(c -> c.id().equals(state.currency()))
        .findFirst()
        .orElse(null);
  }

  @Override
  protected void init() {
    int x = left(), w = contentWidth();
    button(
        10,
        9,
        55,
        "Inicio",
        () -> {
          act(11, "", false);
          ZianHubNetwork.requestHub();
        },
        true);
    button(width - 65, 9, 55, "Cerrar", this::onClose, true);
    if (!state.pending().isEmpty()) {
      button(x + w / 2 - 111, height - 29, 105, "Confirmar", () -> act(9, "", false), true);
      button(x + w / 2 + 6, height - 29, 105, "Volver", () -> act(10, "", false), true);
      return;
    }
    button(
        x,
        39,
        Math.min(150, w / 2 - 4),
        "Elegir padres",
        () -> {
          jobs = false;
          rebuildWidgets();
        },
        true);
    button(
        x + w / 2,
        39,
        Math.min(170, w / 2),
        "Mis crianzas (" + state.used() + "/" + state.capacity() + ")",
        () -> {
          jobs = true;
          rebuildWidgets();
        },
        true);
    if (!jobs) {
      int gap = 6, cw = (w - 2 * gap) / 3, ch = cardHeight();
      for (int i = 0; i < state.parents().size(); i++) {
        var p = state.parents().get(i);
        final String id = p.id();
        addRenderableWidget(
            new PokemonButton(
                x + (i % 3) * (cw + gap),
                65 + (i / 3) * (ch + gap),
                cw,
                ch,
                p,
                () -> act(1, id, false)));
      }
      button(
          x,
          height - 29,
          Math.min(145, w / 2 - 5),
          "Limpiar selección",
          () -> act(2, "", false),
          !state.first().isEmpty() || !state.second().isEmpty());
      button(
          x + w / 2,
          height - 29,
          w / 2,
          "Iniciar gratis",
          () -> act(3, "", false),
          state.compatible() && state.used() < state.capacity());
    } else if (!state.jobs().isEmpty()) {
      button(x, 66, 35, "<", () -> selectJob(-1), state.jobs().size() > 1);
      button(x + w - 35, 66, 35, ">", () -> selectJob(1), state.jobs().size() > 1);
      var j = job();
      if (j == null) {
        act(4, state.jobs().get(Math.min(jobIndex, state.jobs().size() - 1)).id(), false);
        return;
      }
      int y = Math.max(120, height - 105);
      var currency = currency();
      if (currency != null) {
        button(
            x,
            y,
            w,
            "Moneda: "
                + friendly(currency.id())
                + " · "
                + currency.minutes()
                + " min/u · saldo "
                + (currency.balance() < 0 ? "?" : currency.balance()),
            this::nextCurrency,
            true);
      }
      boolean canBuy = j.status().equals("ACTIVE") && j.readyAt() > now() && currency != null;
      button(x, y + 24, w / 2 - 3, "Acelerar 1 unidad", () -> act(6, "", false), canBuy);
      button(x + w / 2 + 3, y + 24, w / 2 - 3, "Hasta el mínimo", () -> act(6, "", true), canBuy);
      boolean claim =
          (j.status().equals("ACTIVE") || j.status().equals("BLOCKED")) && j.readyAt() <= now();
      button(x, height - 29, w / 2 - 3, "Entregar al PC", () -> act(7, "", false), claim);
      button(
          x + w / 2 + 3,
          height - 29,
          w / 2 - 3,
          "Cancelar crianza",
          () -> act(8, "", false),
          j.status().equals("ACTIVE") || j.status().equals("BLOCKED"));
    }
  }

  private int cardHeight() {
    return Math.max(43, Math.min(88, (height - 142) / 2));
  }

  private long now() {
    return state.serverNow() + Math.max(0, (System.nanoTime() - received) / 1000000);
  }

  private void selectJob(int delta) {
    jobIndex = Math.floorMod(jobIndex + delta, state.jobs().size());
    act(4, state.jobs().get(jobIndex).id(), false);
  }

  private void nextCurrency() {
    if (state.currencies().isEmpty()) return;
    for (int i = 0; i < state.currencies().size(); i++)
      if (state.currencies().get(i).id().equals(state.currency())) currencyIndex = i;
    currencyIndex = (currencyIndex + 1) % state.currencies().size();
    act(5, state.currencies().get(currencyIndex).id(), false);
  }

  @Override
  public void tick() {
    super.tick();
    if (++ticks % 40 == 0) act(0, "", false);
  }

  @Override
  public void onClose() {
    act(11, "", false);
    super.onClose();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    g.fill(0, 0, width, height, 0xE00D1117);
    g.fill(0, 0, width, 4, GOLD);
    g.drawCenteredString(font, title, width / 2, 12, GOLD);
    hovered = "";
    int x = left(), w = contentWidth();
    if (jobs && state.pending().isEmpty()) card(g, x, 64, w, height - 98, GOLD);
    if (!state.pending().isEmpty()) {
      card(g, x, 65, w, height - 105, GOLD);
      g.drawCenteredString(
          font,
          Component.literal(
              switch (state.pending()) {
                case "start" -> "Confirmar crianza gratuita";
                case "cancel" -> "Confirmar cancelación";
                default -> "Confirmar aceleración";
              }),
          width / 2,
          79,
          GOLD);
      wrap(g, state.confirmation(), x + 12, 100, w - 24, WHITE, 3);
      wrap(
          g,
          state.pending().equals("accelerate")
              ? "Se cobra la cantidad completa. La última unidad puede cubrir tiempo parcial; no"
                  + " hay devolución parcial."
              : state.pending().equals("cancel")
                  ? "Los padres quedarán libres. No se devuelven monedas de aceleraciones."
                  : "Los padres seguirán contigo. La cría se entregará al PC al finalizar.",
          x + 12,
          Math.max(132, height - 96),
          w - 24,
          MUTED,
          3);
    } else if (!jobs) {
      if (state.parents().isEmpty())
        wrap(
            g,
            "Tu equipo está vacío. Añade dos Pokémon y vuelve a abrir Crianza.",
            x + 12,
            80,
            w - 24,
            MUTED,
            3);
      int y = 65 + 2 * (cardHeight() + 6) + 2;
      wrap(
          g,
          !state.message().isEmpty()
              ? state.message()
              : state.compatible()
                  ? "Pareja compatible · Inicio gratuito · "
                      + state.baseMinutes() / 60
                      + " h (mínimo "
                      + state.minimumMinutes() / 60
                      + " h)"
                  : state.compatibility(),
          x,
          y,
          w,
          state.compatible() ? GREEN : MUTED,
          2);

    } else if (state.jobs().isEmpty())
      wrap(
          g,
          "Todavía no tienes crianzas pendientes. Elige dos padres para iniciar una.",
          x + 12,
          84,
          w - 24,
          MUTED,
          3);
    else {
      var j = job();
      if (j != null) {
        g.drawCenteredString(
            font,
            Component.literal("Crianza " + (jobIndex + 1) + " · " + j.id().substring(0, 8)),
            width / 2,
            70,
            WHITE);
        String label =
            switch (j.status()) {
              case "ACTIVE" -> j.readyAt() <= now() ? "Lista para entregar" : "En progreso";
              case "BLOCKED" -> "Entrega pendiente";
              case "UNCERTAIN" -> "Necesita revisión administrativa";
              case "DELIVERING" -> "Comprobando guardado del PC";
              default -> "Comprobando cobro";
            };
        g.drawCenteredString(
            font,
            Component.literal(label + " · " + remaining(j.readyAt() - now())),
            width / 2,
            93,
            j.status().equals("UNCERTAIN") ? RED : GOLD);
        String why =
            switch (j.reason()) {
              case "PC_FULL" -> "Tu PC está lleno: libera espacio y reclama aquí";
              case "PARENTS_NOT_OWNED" -> "Debes recuperar los padres en tu equipo o PC";
              case "SPECIES_OR_GENERATION" -> "La generación de la cría está deshabilitada";
              default ->
                  j.status().equals("UNCERTAIN")
                      ? "Un administrador debe revisar la operación antes de continuar"
                      : "La cría permanece reservada; no se vuelve a sortear";
            };
        wrap(
            g,
            state.message().isEmpty() ? why : state.message(),
            x,
            108,
            w,
            MUTED,
            height < 300 ? 1 : 2);
      }
    }
    super.render(g, mx, my, partial);
    if (!hovered.isEmpty()) {
      var p = state.parents().stream().filter(a -> a.id().equals(hovered)).findFirst().orElse(null);
      if (p != null) {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(p.name() + " · Nv. " + p.level() + " · " + sex(p.sex())));
        lines.add(Component.translatable("cobblemon.nature." + p.nature()));
        lines.add(
            Component.literal("Habilidad: ")
                .append(Component.translatable("cobblemon.ability." + p.ability())));
        String[] labels = {"PS", "Ataque", "Defensa", "At. especial", "Def. especial", "Velocidad"};
        for (int i = 0; i < 6; i++)
          lines.add(
              Component.literal(
                  labels[i] + ": " + p.stats().get(i) + " · IV " + p.ivs().get(i) + "/31"));
        lines.add(Component.literal("Grupos huevo: " + p.groups()));
        lines.add(
            Component.literal(p.reason().isEmpty() ? "Disponible para seleccionar" : p.reason()));
        g.renderComponentTooltip(font, lines, mx, my);
      }
    }
  }

  private void wrap(GuiGraphics g, String text, int x, int y, int w, int color, int rows) {
    var lines = font.split(Component.literal(text), w);
    for (int i = 0; i < Math.min(rows, lines.size()); i++)
      g.drawString(font, lines.get(i), x, y + i * 11, color, false);
  }

  private String fit(String text, int pixels) {
    return font.width(text) <= pixels
        ? text
        : font.plainSubstrByWidth(text, Math.max(1, pixels - 8)) + "…";
  }

  private static String sex(String s) {
    return switch (s) {
      case "MALE" -> "♂ Macho";
      case "FEMALE" -> "♀ Hembra";
      default -> "Sin sexo";
    };
  }

  private static String remaining(long ms) {
    long seconds = Math.max(0, (ms + 999) / 1000);
    return String.format("%dh %02dm %02ds", seconds / 3600, seconds / 60 % 60, seconds % 60);
  }

  private static String friendly(String id) {
    String name = id.substring(id.indexOf(':') + 1);
    return switch (name) {
      case "coppercoin" -> "Moneda de Cobre";
      case "ironcoin" -> "Moneda de Hierro";
      case "goldcoin" -> "Moneda de Oro";
      case "diamondcoin" -> "Moneda de Diamante";
      case "netheritecoin" -> "Moneda de Netherita";
      case "goldticket" -> "Ticket de Oro";
      case "diamondticket" -> "Ticket de Diamante";
      case "netheriteticket" -> "Ticket de Netherita";
      default -> name;
    };
  }

  private static void card(GuiGraphics g, int x, int y, int w, int h, int accent) {
    g.fill(x, y, x + w, y + h, 0xFF59616B);
    g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF181D24);
    g.fill(x + 1, y + 1, x + w - 1, y + 4, accent);
  }

  private final class PokemonButton extends StyledButton {
    private final BreedingNetwork.Parent parent;

    PokemonButton(int x, int y, int w, int h, BreedingNetwork.Parent p, Runnable action) {
      super(x, y, w, h, p.name(), action);
      parent = p;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
      boolean chosen = parent.id().equals(state.first()) || parent.id().equals(state.second());
      card(
          g,
          getX(),
          getY(),
          width,
          height,
          chosen ? GOLD : parent.reason().isEmpty() ? 0xFF526575 : RED);
      if (isHovered) {
        g.renderOutline(getX(), getY(), width, height, GOLD);
        hovered = parent.id();
      }
      g.pose().pushPose();
      g.pose().translate(getX() + 6, getY() + 12, 0);
      g.pose().scale(1.5f, 1.5f, 1);
      g.renderItem(parent.icon(), 0, 0);
      g.pose().popPose();
      g.drawString(font, fit(parent.name(), width - 42), getX() + 35, getY() + 9, WHITE, false);
      g.drawString(
          font,
          fit(sex(parent.sex()) + " · Nv. " + parent.level(), width - 42),
          getX() + 35,
          getY() + 21,
          parent.sex().equals("MALE")
              ? 0xFF79B9EF
              : parent.sex().equals("FEMALE") ? 0xFFF79BC4 : MUTED,
          false);
      String marker =
          parent.id().equals(state.first())
              ? "Padre 1"
              : parent.id().equals(state.second())
                  ? "Padre 2"
                  : parent.reason().isEmpty() ? "Seleccionar" : "No compatible";
      g.drawString(
          font,
          fit(height < 65 ? "PS " + parent.stats().get(0) + " � " + marker : marker, width - 42),
          getX() + 35,
          getY() + 33,
          chosen ? GOLD : parent.reason().isEmpty() ? GREEN : RED,
          false);
      if (height >= 65) {
        g.drawString(
            font,
            fit(
                "PS "
                    + parent.stats().get(0)
                    + "  Atq "
                    + parent.stats().get(1)
                    + "  Def "
                    + parent.stats().get(2),
                width - 12),
            getX() + 6,
            getY() + 49,
            MUTED,
            false);
      }
      if (height >= 80) {
        g.drawString(
            font,
            fit(
                "AtE "
                    + parent.stats().get(3)
                    + "  DfE "
                    + parent.stats().get(4)
                    + "  Vel "
                    + parent.stats().get(5),
                width - 12),
            getX() + 6,
            getY() + 61,
            MUTED,
            false);
      }
    }
  }

  private static class StyledButton extends AbstractButton {
    private final Runnable action;

    StyledButton(int x, int y, int w, int h, String label, Runnable action) {
      super(x, y, w, h, Component.literal(label));
      this.action = action;
    }

    @Override
    public void onPress() {
      action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
      defaultButtonNarrationText(output);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
      int border = isHovered && active ? GOLD : active ? 0xFF526575 : 0xFF3B4148;
      g.fill(getX(), getY(), getX() + width, getY() + height, active ? 0xFF20262D : 0xFF161A20);
      g.renderOutline(getX(), getY(), width, height, border);
      g.drawCenteredString(
          Minecraft.getInstance().font,
          getMessage(),
          getX() + width / 2,
          getY() + (height - 8) / 2,
          active ? WHITE : 0xFF7C838B);
    }
  }
}
