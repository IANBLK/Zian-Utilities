package com.zianblk.zianutilities.neoforge.raids;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * D-R6: when Raid Dens is loaded and the duel hook is not exactly where we expect it, the server must not start.
 * Unlike the riding hook this throws instead of warning.
 */
public final class RaidDuelHookCheck {
  private RaidDuelHookCheck() {}

  /** Returns "mixin" if the marker is missing and the target method if it is absent or has another signature. */
  public static List<String> missing(Class<?> target) {
    var out = new ArrayList<String>();
    if (!RaidDuelHooked.class.isAssignableFrom(target)) out.add("mixin");
    boolean found = false;
    for (Method m : target.getDeclaredMethods())
      if (m.getName().equals("handleServer")
          && m.getParameterCount() == 1
          && m.getParameterTypes()[0] == ServerPlayer.class
          && m.getReturnType() == void.class) found = true;
    if (!found) out.add("handleServer(ServerPlayer)");
    return out;
  }

  public static void verifyOrThrow(Class<?> target) {
    var missing = missing(target);
    if (!missing.isEmpty())
      throw new IllegalStateException(
          "Zian Utilities (raids): el duelo no encaja con esta version de Cobblemon Raid Dens (probado con 0.14.3). Faltan: "
              + missing + ". El servidor no arranca; actualiza Zian Utilities o quita Raid Dens.");
  }
}
