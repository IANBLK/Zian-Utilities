package com.zianblk.zianutilities.neoforge.breeding;

import net.minecraft.server.level.ServerPlayer;

public final class BreedingPermissions {
  private BreedingPermissions() {}

  public static boolean allows(ServerPlayer player, String action, boolean admin) {
    try {
      return switch (LuckPermsLookup.lookup(player, "zianutilities.breeding." + action)) {
        case TRUE -> true;
        case FALSE -> false;
        case ABSENT, UNDEFINED -> !admin || (action.equals("admin")?com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(player,"admin.breeding",true):player.hasPermissions(2));
      };
    } catch (Exception | LinkageError error) {
      return false;
    }
  }

  public static int slots(ServerPlayer p, BreedingConfig c) {
    int count = c.defaultSlots;
    if (allows(p, "slots.vip", true)) count = c.vipSlots;
    for (int n = 1; n <= c.maxSlots; n++) if (explicit(p, "slots." + n)) count = Math.max(count, n);
    return Math.min(count, c.maxSlots);
  }

  private static boolean explicit(ServerPlayer p, String node) {
    try {
      return LuckPermsLookup.lookup(p, "zianutilities.breeding." + node)
          == LuckPermsLookup.Decision.TRUE;
    } catch (Exception | LinkageError e) {
      return false;
    }
  }
}
