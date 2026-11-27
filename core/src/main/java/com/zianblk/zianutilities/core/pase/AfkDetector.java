package com.zianblk.zianutilities.core.pase;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A player is AFK when neither position nor rotation has changed beyond a small threshold for the given time. Fed
 * from a periodic sample; the clock is whatever millisecond counter the caller passes (the server tick, not wall time).
 */
public final class AfkDetector {
  static final double MOVE_SQ = 0.09;
  static final float TURN_DEGREES = 2f;

  private static final class Last {
    double x, y, z;
    float yaw, pitch;
    long movedAt;
  }

  private final Map<UUID, Last> last = new HashMap<>();

  public void sample(UUID id, double x, double y, double z, float yaw, float pitch, long nowMillis) {
    Last l = last.get(id);
    if (l == null) {
      l = new Last();
      last.put(id, l);
      l.x = x; l.y = y; l.z = z; l.yaw = yaw; l.pitch = pitch; l.movedAt = nowMillis;
      return;
    }
    double dx = x - l.x, dy = y - l.y, dz = z - l.z;
    boolean moved = dx * dx + dy * dy + dz * dz > MOVE_SQ
        || Math.abs(wrap(yaw - l.yaw)) > TURN_DEGREES || Math.abs(pitch - l.pitch) > TURN_DEGREES;
    if (moved) {
      l.x = x; l.y = y; l.z = z; l.yaw = yaw; l.pitch = pitch;
      l.movedAt = nowMillis;
    }
  }

  /** Unknown players are not AFK (no sample yet means we cannot say). */
  public boolean afk(UUID id, long nowMillis, long afkMillis) {
    Last l = last.get(id);
    return l != null && nowMillis - l.movedAt >= afkMillis;
  }

  public void forget(UUID id) { last.remove(id); }

  private static float wrap(float degrees) {
    float d = degrees % 360f;
    if (d > 180f) d -= 360f;
    if (d < -180f) d += 360f;
    return d;
  }
}
