package com.zianblk.zianutilities.neoforge.breeding;

import net.minecraft.server.level.ServerPlayer;

/** Entry point shared by the command and hub; no container or fake inventory items. */
public final class BreedingMenu {
  private BreedingMenu() {}

  public static void open(ServerPlayer player) {
    BreedingNetwork.open(player);
  }
}
