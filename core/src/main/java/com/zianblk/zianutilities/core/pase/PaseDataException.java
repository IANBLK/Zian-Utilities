package com.zianblk.zianutilities.core.pase;

/** A reward value that is not valid, with a machine-readable code so the Minecraft side can show it translated. */
public final class PaseDataException extends IllegalArgumentException {
  /** "range" (field, min, max), "missing" (field) or "conflict" (field, other). */
  public final String code;
  public final Object[] args;

  private PaseDataException(String code, String message, Object... args) {
    super(message);
    this.code = code;
    this.args = args;
  }

  public static PaseDataException range(String field, Object min, Object max) { return new PaseDataException("range", field + " must be between " + min + " and " + max, field, min, max); }

  public static PaseDataException missing(String field) { return new PaseDataException("missing", field + " is required", field); }

  public static PaseDataException conflict(String field, String other) { return new PaseDataException("conflict", field + " and " + other + " cannot be used together", field, other); }
}
