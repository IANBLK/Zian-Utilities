package com.zianblk.zianutilities.neoforge.pase;

import com.zianblk.zianutilities.neoforge.Messages;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** TEMPORAL: arnes de capturas (solo admin-premium-errores). No se commitea. */
@EventBusSubscriber(modid = "zianutilities", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TmpShots {
  static final String LANG = System.getProperty("zian.shots.lang", "es_es");
  static int step, wait, boot;

  static Component fail(Component c) { return Component.empty().append(Messages.format(c)).withStyle(ChatFormatting.RED); }

  static final class Shot extends Screen {
    Shot() { super(Component.empty()); }
    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
      g.fill(0, 0, width, height, 0xFF555555);
      var mc = Minecraft.getInstance();
      mc.gui.getChat().render(g, mc.gui.getGuiTicks(), 0, 0, false);
    }
    @Override public boolean isPauseScreen() { return false; }
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    if (System.getProperty("zian.shots") == null) return;
    var mc = Minecraft.getInstance();
    try {
      if (step == 0) {
        if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen) || ++boot < 40) return;
        if (!LANG.equals(mc.options.languageCode) || mc.getWindow().getGuiScale() != 3) { System.out.println("TMPSHOTS ABORT"); mc.stop(); return; }
        mc.gui.getChat().clearMessages(true);
        for (Component c : List.of(
            fail(PaseMessages.premiumBadSeason("temporada-2", "temporada-1")), fail(PaseMessages.premiumBadSeason("temporada-1", null)),
            fail(PaseMessages.premiumBadTransaction()), fail(PaseMessages.premiumFailed(PaseMessages.premiumLocked())),
            fail(PaseMessages.premiumFailed(PaseMessages.premiumTxUnreadable())), fail(PaseMessages.premiumFailed(Component.literal("el premium no quedó registrado"))),
            fail(PaseMessages.premiumRemoveFailed()))) mc.gui.getChat().addMessage(c);
        mc.setScreen(new Shot());
        step = 1;
        wait = 5;
        return;
      }
      if (wait-- > 0) return;
      var img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
      img.writeToFile(Path.of("D:/Claude/zian/capturas/pase-p2/admin-premium-errores-" + LANG + ".png"));
      img.close();
      System.out.println("TMPSHOTS DONE");
      mc.stop();
    } catch (Throwable t) {
      t.printStackTrace();
      System.out.println("TMPSHOTS FAIL " + t);
      mc.stop();
    }
  }
}
