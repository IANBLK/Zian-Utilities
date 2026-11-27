package com.zianblk.zianutilities.neoforge.raids;

import com.cobblemon.mod.common.api.Priority;
import com.necro.raid.dens.common.data.raid.RaidBoss;
import com.necro.raid.dens.common.events.RaidEvents;
import com.necro.raid.dens.common.registry.CustomRaidRegistries;
import com.zianblk.zianutilities.core.raids.RaidGenerationPick;
import com.zianblk.zianutilities.neoforge.Messages;
import com.zianblk.zianutilities.neoforge.Tr;
import java.util.ArrayList;
import java.util.Random;

/** The three Raid Dens events Utilities listens to: SET_RAID and RAID_JOIN for the generation filter, REWARD_POKEMON for delivery. */
final class RaidDensEvents {
  private static final Random RANDOM = new Random();

  private RaidDensEvents() {}

  static void install() {
    RaidEvents.SET_RAID.subscribe(Priority.NORMAL, event -> {
      if (!RaidsModule.active() || !RaidGenerationFilter.denied(event.getRaid())) return;
      var same = new ArrayList<RaidBoss>();
      for (RaidBoss candidate : CustomRaidRegistries.RAID_REGISTRY.getCache())
        if (candidate.getTier() == event.getRaid().getTier()) same.add(candidate);
      RaidGenerationPick.reroll(same, b -> !RaidGenerationFilter.denied(b), RANDOM).ifPresent(event::setRaid);
    });
    RaidEvents.RAID_JOIN.subscribe(Priority.HIGHEST, event -> {
      if (!RaidsModule.active() || !RaidGenerationFilter.denied(event.getRaid())) return;
      event.cancel();
      event.getPlayer().sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.err.generation_denied", "§cEste jefe pertenece a una generación desactivada.")));
    });
    RaidEvents.REWARD_POKEMON.subscribe(Priority.HIGHEST, RaidPendingRewards::onReward);
    RaidEvents.RAID_BATTLE_END.subscribe(Priority.NORMAL, event -> {
      if (event.isWin() && !WeeklyBossFactory.isWeekly(event.raidBoss())) com.zianblk.zianutilities.neoforge.pase.PaseApi.raidWon(event.player().getUUID(), false);
    });
  }
}
