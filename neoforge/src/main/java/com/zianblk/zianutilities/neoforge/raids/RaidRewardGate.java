package com.zianblk.zianutilities.neoforge.raids;

/**
 * Decides what to do with a Raid Dens Pokemon reward before it is inserted. Raid Dens is the only owner of a reward
 * it could not deliver (it keeps REWARD_QUEUE and the Ball), so anything that is not a clean go is cancelled and
 * never creates a journal entry of ours. A failure while deciding also cancels: nothing is delivered blindly.
 */
final class RaidRewardGate {
  enum Outcome { ALLOW, DENIED_GENERATION, DUPLICATE, PC_FULL, ERROR }

  interface Probe {
    boolean deniedByGeneration() throws Exception;
    boolean alreadyInStorage() throws Exception;
    boolean hasEntry();
    boolean pcFull() throws Exception;
  }

  private RaidRewardGate() {}

  static Outcome decide(Probe probe) {
    try {
      if (probe.deniedByGeneration()) return Outcome.DENIED_GENERATION;
      if (probe.hasEntry() || probe.alreadyInStorage()) return Outcome.DUPLICATE;
      if (probe.pcFull()) return Outcome.PC_FULL;
      return Outcome.ALLOW;
    } catch (Throwable failure) {
      return Outcome.ERROR;
    }
  }

  static boolean cancels(Outcome outcome) { return outcome != Outcome.ALLOW; }
}
