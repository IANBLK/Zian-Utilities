package com.zianblk.zianutilities.core.equipment;

import java.util.ArrayList;
import java.util.List;

/** Orden de las líneas del tooltip de las armas con habilidad (diseño G3); clase pura sin Minecraft. */
public final class TooltipLayout {
  public enum Kind { ABILITY, HINT, DETAIL, COOLDOWN }

  public record Line(Kind kind, String key, int arg) {}

  /** {@code effectArg} se inserta en el texto del efecto; {@code cooldownSeconds} 0 = pasiva. */
  public record Spec(String id, int effectArg, int cooldownSeconds) {}

  private TooltipLayout() {}

  public static List<Line> lines(Spec spec, boolean expanded) {
    String p = "tooltip.zianutilities." + spec.id();
    var out = new ArrayList<Line>();
    out.add(new Line(Kind.ABILITY, p + ".ability", 0));
    if (!expanded) {
      out.add(new Line(Kind.HINT, "tooltip.zianutilities.shift", 0));
      return out;
    }
    out.add(new Line(Kind.DETAIL, p + ".effect", spec.effectArg()));
    if (spec.cooldownSeconds() > 0) out.add(new Line(Kind.COOLDOWN, "tooltip.zianutilities.cooldown", spec.cooldownSeconds()));
    return out;
  }
}
