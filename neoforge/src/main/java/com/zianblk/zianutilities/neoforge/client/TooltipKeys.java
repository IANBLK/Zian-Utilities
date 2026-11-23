package com.zianblk.zianutilities.neoforge.client;

/** Estado de teclas solo cliente; se alcanza desde WeaponAbilities.shift() para que el servidor dedicado no cargue Screen. */
public final class TooltipKeys {
  private TooltipKeys() {}

  public static boolean shift() {
    return net.minecraft.client.gui.screens.Screen.hasShiftDown();
  }
}
