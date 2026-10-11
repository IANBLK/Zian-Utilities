package com.zianblk.zianutilities.neoforge.client;

import com.zianblk.zianutilities.core.gui.ResponsiveLayout;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.21.1 adaptation: one render transform, inverse input, native unscaled tooltips. */
public abstract class ResponsiveScreen extends Screen {
  private ResponsiveLayout layout;
  private List<Component> tooltip = List.of();

  protected ResponsiveScreen(Component title) {
    super(title);
  }

  protected final void fitViewport(int minimumWidth, int minimumHeight) {
    // Rebuilding widgets must read the real logical viewport, never rescale the previous virtual
    // one.
    layout =
        ResponsiveLayout.fit(
            minecraft.getWindow().getGuiScaledWidth(),
            minecraft.getWindow().getGuiScaledHeight(),
            minimumWidth,
            minimumHeight);
    width = layout.width();
    height = layout.height();
  }

  protected abstract void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY, float partial);

  protected final void renderWidgets(GuiGraphics g, int x, int y, float partial) {
    super.render(g, x, y, partial);
  }

  protected final void queueTooltip(List<Component> lines) {
    tooltip = List.copyOf(lines);
  }

  @Override
  public final void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    tooltip = List.of();
    g.pose().pushPose();
    try {
      g.pose().translate(layout.x(), layout.y(), 0);
      g.pose().scale((float) layout.scale(), (float) layout.scale(), 1);
      renderCanvas(
          g,
          (int) Math.floor(layout.mouseX(mouseX)),
          (int) Math.floor(layout.mouseY(mouseY)),
          partial);
    } finally {
      g.pose().popPose();
    }
    // Tooltip bounds are calculated by Minecraft in its actual logical viewport.
    if (!tooltip.isEmpty()) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    return super.mouseClicked(layout.mouseX(x), layout.mouseY(y), button);
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    return super.mouseReleased(layout.mouseX(x), layout.mouseY(y), button);
  }

  @Override
  public void mouseMoved(double x, double y) {
    super.mouseMoved(layout.mouseX(x), layout.mouseY(y));
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    return super.mouseDragged(
        layout.mouseX(x), layout.mouseY(y), button, dx / layout.scale(), dy / layout.scale());
  }

  @Override
  public boolean mouseScrolled(double x, double y, double dx, double dy) {
    return super.mouseScrolled(layout.mouseX(x), layout.mouseY(y), dx, dy);
  }
}
