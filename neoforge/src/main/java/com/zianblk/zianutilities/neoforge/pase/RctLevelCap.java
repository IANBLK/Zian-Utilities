package com.zianblk.zianutilities.neoforge.pase;

import java.lang.reflect.Method;

/**
 * The Pokemon level cap Zian-RCT keeps for a player, read by reflection so RCT stays optional. A prize Pokemon is never above
 * min(levelCap of the season file, this). When RCT is not installed or its API cannot be read, the season cap alone applies and one
 * warning is logged.
 */
final class RctLevelCap {
  static final String RUNTIME = "com.ianblk.zianrct.standalone.StandaloneRuntime";
  private static boolean warned;

  private RctLevelCap() {}

  /** The cap of this player in RCT, or Integer.MAX_VALUE when there is none to read. */
  static int of(Object player) { return of(RUNTIME, player); }

  static int of(String runtimeClass, Object player) {
    try {
      Class<?> type = Class.forName(runtimeClass);
      Object runtime = type.getMethod("getInstance").invoke(null);
      Method getData = null;
      for (Method m : type.getMethods()) if (m.getName().equals("getData") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(player)) getData = m;
      if (getData == null) throw new NoSuchMethodException("getData(player)");
      Object data = getData.invoke(runtime, player);
      int cap = ((Number) data.getClass().getMethod("getLevelCap").invoke(data)).intValue();
      if (cap < 1) throw new IllegalStateException("level cap " + cap);
      return cap;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
      if (!warned) {
        warned = true;
        PaseModule.LOG.warn("No se puede leer el tope de nivel de Zian-RCT ({}); los Pokémon de premio usan solo el levelCap del archivo de la temporada", unavailable.toString());
      }
      return Integer.MAX_VALUE;
    }
  }

  /** For tests: forget that the warning was already given. */
  static void resetWarning() { warned = false; }
}
