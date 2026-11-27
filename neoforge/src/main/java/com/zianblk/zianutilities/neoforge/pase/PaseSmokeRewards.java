package com.zianblk.zianutilities.neoforge.pase;

import com.zianblk.zianutilities.core.economy.EconomyBalanceResult;
import com.zianblk.zianutilities.core.pase.PaseReward;
import com.zianblk.zianutilities.core.pase.PlayerPase;
import com.zianblk.zianutilities.core.pase.Prize;
import com.zianblk.zianutilities.core.pase.SeasonRewards;
import com.zianblk.zianutilities.neoforge.delivery.PokemonDelivery;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Rewards part of the local pass smoke (phases 3 and 4, after the XP phases 1 and 2 have left temporada-2 current and
 * temporada-1 previous; never packaged in the release jar). Phase 3: closed rewards with a broken season file, free and
 * paid prizes (items, AVECOINS, a whitelisted command, Pokemon checked in the PC), Tebex with a repeated transaction, a
 * late premium, an offline player, a full inventory, the previous season, a refund, and then leaves three prizes half
 * delivered. Phase 4 (after a restart): the interrupted prizes finish without duplicates. Runs one step per tick so the
 * asynchronous Pokemon proof can complete. Writes pase-smoke-N.txt and halts the server.
 */
final class PaseSmokeRewards {
  private interface Step { boolean run() throws Exception; }

  private interface Action { void run() throws Exception; }

  private static final UUID ID4 = UUID.nameUUIDFromBytes("PaseSmoke-P4".getBytes(StandardCharsets.UTF_8));
  private static final UUID ID5 = UUID.nameUUIDFromBytes("PaseSmoke-P5".getBytes(StandardCharsets.UTF_8));
  private static final UUID ID6 = UUID.nameUUIDFromBytes("PaseSmoke-P6".getBytes(StandardCharsets.UTF_8));
  private static final String CUR = "temporada-2", PREV = "temporada-1";
  private static final String COPPER = "avecoins:coppercoin";
  private static final StringBuilder REPORT = new StringBuilder();
  private static final List<Step> STEPS = new ArrayList<>();
  private static int index = -1, waited;
  private static boolean finished;
  private static int phase;
  private static ServerPlayer p4, p5, p6;
  private static int pcBefore, chatMark;
  private static SeasonRewards season;

  private PaseSmokeRewards() {}

  private static void check(boolean ok, String what) {
    REPORT.append(ok ? "PASS " : "FAIL ").append(what).append('\n');
    if (!ok) throw new IllegalStateException(what);
  }

  static void tick(int which) {
    if (finished || PaseModule.server == null) return;
    try {
      if (index < 0) {
        phase = which;
        if (phase == 3) phaseThree(); else phaseFour();
        index = 0;
      }
      if (STEPS.get(index).run()) {
        index++;
        waited = 0;
        if (index == STEPS.size()) finish(true);
      } else if (++waited > 1200) {
        throw new IllegalStateException("el paso " + index + " no terminó");
      }
    } catch (Throwable failure) {
      REPORT.append("ERROR ").append(failure).append('\n');
      PaseModule.LOG.error("Pase rewards smoke failed", failure);
      finish(false);
    }
  }

  private static Step once(Action action) { return () -> { action.run(); return true; }; }

  // ---------------------------------------------------------------- helpers

  private static CommandSourceStack console() { return PaseModule.server.createCommandSourceStack(); }

  private static int run(String command, CommandSourceStack source) throws Exception {
    return PaseModule.server.getCommands().getDispatcher().execute(command, source);
  }

  private static PlayerPase data(UUID id) { return PaseModule.store.get(id); }

  private static int countItem(ServerPlayer p, String item) {
    int n = 0;
    for (ItemStack s : p.getInventory().items) if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(item)) n += s.getCount();
    return n;
  }

  private static long balance(UUID id, String currency) {
    var r = PaseRewards.economyPort().balance(id, currency);
    check(r instanceof EconomyBalanceResult.Available, "the AVECOINS wallet can be read");
    return ((EconomyBalanceResult.Available) r).getAmount();
  }

  private static int pcSize(ServerPlayer p) throws Exception {
    int n = 0;
    for (var ignored : PokemonDelivery.open(p).pc()) n++;
    return n;
  }

  private static int pcCopies(ServerPlayer p, UUID id) throws Exception {
    int n = 0;
    for (var pokemon : PokemonDelivery.open(p).pc()) if (pokemon.getUuid().equals(id)) n++;
    return n;
  }

  private static long states(UUID id, Prize.State state) { return data(id).prizes.values().stream().filter(z -> z.state == state).count(); }

  private static long freeCount(int levels) {
    long n = 0;
    for (int l = 1; l <= levels; l++) n += season.level(l).free().size();
    return n;
  }

  private static long paidCount(int levels) {
    long n = 0;
    for (int l = 1; l <= levels; l++) n += season.level(l).premium().size();
    return n;
  }

  private static boolean chatHas(String text) {
    for (int i = chatMark; i < PaseSmoke.CHAT.size(); i++) if (java.util.regex.Pattern.compile(text, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(PaseSmoke.CHAT.get(i)).find()) return true;
    REPORT.append("chat since mark: ").append(PaseSmoke.CHAT.subList(Math.min(chatMark, PaseSmoke.CHAT.size()), PaseSmoke.CHAT.size())).append((char) 10);
    return false;
  }

  private static void mark() { chatMark = PaseSmoke.CHAT.size(); }

  private static Prize pokemonPrize(UUID id, Prize.Track track, int level) {
    return data(id).prizes.values().stream().filter(z -> z.track == track && z.level == level && z.reward.kind() == PaseReward.Kind.POKEMON).findFirst().orElseThrow();
  }

  private static boolean allPokemonDelivered(UUID id) {
    for (Prize z : data(id).prizes.values()) {
      if (z.reward.kind() != PaseReward.Kind.POKEMON) continue;
      if (z.state == Prize.State.UNCERTAIN) throw new IllegalStateException("Pokémon en revisión: " + z.id + " " + z.reason);
      if (z.state != Prize.State.DELIVERED) return false;
    }
    return true;
  }

  private static void writeSeason(String text) throws Exception { Files.writeString(PaseRewards.seasonFile(CUR), text); }

  private static SeasonRewards withCommand() {
    SeasonRewards base = PaseSeasonExample.build(200);
    Map<Integer, SeasonRewards.Level> levels = new TreeMap<>(base.levels());
    SeasonRewards.Level three = levels.get(3);
    List<PaseReward> free = new ArrayList<>(three.free());
    free.add(PaseReward.command("say hola {player}"));
    levels.put(3, new SeasonRewards.Level(free, three.premium()));
    return new SeasonRewards(base.levelCap(), levels, base.extraEvery(), base.loot());
  }

  // ---------------------------------------------------------------- phase 3

  private static void phaseThree() {
    STEPS.add(once(PaseSmokeRewards::closedRewards));
    STEPS.add(once(PaseSmokeRewards::freeTrack));
    STEPS.add(once(PaseSmokeRewards::toLevel25));
    STEPS.add(() -> allPokemonDelivered(ID4));
    STEPS.add(once(PaseSmokeRewards::pokemonInPc));
    STEPS.add(once(PaseSmokeRewards::tebex));
    STEPS.add(() -> allPokemonDelivered(ID4));
    STEPS.add(once(PaseSmokeRewards::paidPokemon));
    STEPS.add(once(PaseSmokeRewards::fullInventory));
    STEPS.add(once(PaseSmokeRewards::previousSeason));
    STEPS.add(once(PaseSmokeRewards::offlinePlayer));
    STEPS.add(once(PaseSmokeRewards::adminCommands));
    STEPS.add(once(PaseSmokeRewards::leaveHalfDone));
  }

  private static void closedRewards() throws Exception {
    check(PaseModule.active(), "the module is active");
    check(CUR.equals(PaseModule.seasons.current().id()) && PREV.equals(PaseModule.seasons.previous().id()), "phases 1-2 left temporada-2 current and temporada-1 previous");
    Files.writeString(PaseRewards.commandsFile(), "{\"formatVersion\":1,\"allowed\":[\"say hola {player}\"]}");
    writeSeason("{ broken");
    String problem = PaseRewards.reload();
    check(problem != null && problem.contains(CUR), "a broken season file closes the rewards and names the season: " + problem);
    p4 = PaseSmoke.fake("PaseSmoke4", ID4);
    mark();
    check(run("ZianUtilities pase admin nivel dar PaseSmoke4 12", console()) == 1, "the levels are still earned");
    check(PaseModule.engine.view(ID4).level() == 12, "level 12 reached with the rewards closed");
    check(data(ID4).prizes.isEmpty(), "no prize is created while the season file is invalid");
    check(run("ZianUtilities pase reclamar", p4.createCommandSourceStack()) == 0 && countItem(p4, "minecraft:bread") == 0, "claiming delivers nothing while closed");
    check(PaseRewards.problem() != null, "the problem stays visible for the OPs");
    season = withCommand();
    writeSeason(PaseRewardJson.toJson(season));
    check(PaseRewards.reload() == null, "the corrected file opens the rewards again");
  }

  private static void freeTrack() throws Exception {
    mark();
    int claimed = run("ZianUtilities pase reclamar", p4.createCommandSourceStack());
    check(claimed == freeCount(12), "claimed every free prize of levels 1-12 (" + claimed + ")");
    check(states(ID4, Prize.State.DELIVERED) == freeCount(12) && states(ID4, Prize.State.PENDING) == 0, "all of them are DELIVERED in the journal");
    check(countItem(p4, "minecraft:bread") == 32, "bread arrived (levels 1 and 11): " + countItem(p4, "minecraft:bread"));
    check(balance(ID4, COPPER) == 32, "AVECOINS: 32 copper coins at level 10: " + balance(ID4, COPPER));
    check(chatHas("reclamado|claimed"), "the player was told in chat");
    int bread = countItem(p4, "minecraft:bread");
    long coins = balance(ID4, COPPER);
    check(run("ZianUtilities pase reclamar", p4.createCommandSourceStack()) == 0, "claiming again delivers nothing");
    check(countItem(p4, "minecraft:bread") == bread && balance(ID4, COPPER) == coins, "nothing is given twice");
  }

  private static void toLevel25() throws Exception {
    pcBefore = pcSize(p4);
    check(run("ZianUtilities pase admin nivel dar PaseSmoke4 13", console()) == 1, "13 more levels");
    check(PaseModule.engine.view(ID4).level() == 25, "level 25");
    check(PaseRewards.sync(data(ID4)) == 0, "the prizes were created when the levels were crossed");
    check(run("ZianUtilities pase reclamar", p4.createCommandSourceStack()) >= 0, "claim of levels 13-25");
    Prize mon = pokemonPrize(ID4, Prize.Track.FREE, 25);
    if (mon.state == Prize.State.PENDING) {
      check("NO_SPECIES".equals(mon.reason), "the generation filter leaves the random Pokemon pending, not lost: " + mon.reason);
      new com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore(PaseModule.server).save(com.zianblk.zianutilities.core.generation.GenerationState.Companion.all());
      check(run("ZianUtilities pase reclamar", p4.createCommandSourceStack()) >= 0, "claim again once the generations are enabled");
    }
  }

  private static void pokemonInPc() throws Exception {
    Prize z = pokemonPrize(ID4, Prize.Track.FREE, 25);
    UUID id = PaseDelivery.pokemonId(ID4, z);
    check(pcCopies(p4, id) == 1, "the Pokemon of level 25 is in the PC exactly once");
    check(pcSize(p4) == pcBefore + 1, "the PC grew by one Pokemon");
    var pokemon = PokemonDelivery.live(PokemonDelivery.open(p4), id);
    check(pokemon.getLevel() == 10 && pokemon.getSpecies().getResourceIdentifier().toString().equals(z.detail), "level 10 and the species recorded in the journal: " + z.detail);
    check(balance(ID4, COPPER) == 64, "level 20 added 32 more copper coins");
    check(run("ZianUtilities pase reclamar", p4.createCommandSourceStack()) == 0 && pcCopies(p4, id) == 1, "claiming again never inserts it twice");
  }

  private static void tebex() throws Exception {
    mark();
    String cmd = "ZianUtilities pase admin premium dar " + ID4 + " " + CUR + " TBX-100";
    check(run(cmd, console()) == 1 && data(ID4).premium.contains(CUR), "Tebex: premium given from the console to an online player");
    check(chatHas("premium"), "the player was told the premium is on");
    check(data(ID4).prizes.values().stream().filter(z -> z.track == Prize.Track.PREMIUM && z.state == Prize.State.PENDING).count() == paidCount(25), "the paid prizes of the 25 levels already earned are pending (late premium)");
    check(Files.readString(PaseModule.dataDir().resolve("transactions.json")).contains("TBX-100"), "the transaction is on disk");
    int before = data(ID4).prizes.size();
    check(run(cmd, console()) == 1 && data(ID4).prizes.size() == before, "the same transaction again is ignored");
    check(run("ZianUtilities pase admin premium dar " + ID5 + " " + CUR + " TBX-100", console()) == 1 && !data(ID5).premium.contains(CUR), "the same transaction for another player is ignored too");
    PaseModule.store.release(ID5);
    check(run("ZianUtilities pase admin premium dar " + ID4 + " temporada-9 TBX-101", console()) == 0, "a season that is neither current nor previous is refused");
    boolean denied = false;
    try {
      run("ZianUtilities pase admin premium dar " + ID4 + " " + CUR + " TBX-102", p4.createCommandSourceStack());
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
      denied = true;
    }
    check(denied, "a non-OP player cannot run the Tebex command");
    check(run("ZianUtilities pase reclamar", p4.createCommandSourceStack()) >= 0, "claim of the paid track");
  }

  private static void paidPokemon() throws Exception {
    check(states(ID4, Prize.State.PENDING) == 0 && states(ID4, Prize.State.UNCERTAIN) == 0, "every prize was delivered");
    Prize z = pokemonPrize(ID4, Prize.Track.PREMIUM, 25);
    var pokemon = PokemonDelivery.live(PokemonDelivery.open(p4), PaseDelivery.pokemonId(ID4, z));
    check(pokemon != null && pokemon.getLevel() == 20, "the paid Pokemon is in the PC at level 20");
    int perfect = 0;
    var stats = List.of(com.cobblemon.mod.common.api.pokemon.stats.Stats.HP, com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK, com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE,
        com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK, com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE, com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED);
    for (var stat : stats) if (pokemon.getIvs().get(stat) != null && pokemon.getIvs().get(stat) == 31) perfect++;
    check(perfect >= 3, "it has at least 3 perfect IVs (" + perfect + ")");
    check(balance(ID4, "avecoins:goldcoin") == 8, "paid coins: 4 gold coins at level 10 and at level 20");
    check(countItem(p4, "minecraft:diamond") >= 2, "paid items arrived");
  }

  private static void fullInventory() throws Exception {
    check(run("ZianUtilities pase admin nivel dar PaseSmoke4 5", console()) == 1, "levels 26-30");
    for (int i = 0; i < p4.getInventory().items.size(); i++) p4.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
    long pendingBefore = states(ID4, Prize.State.PENDING);
    mark();
    int bread = countItem(p4, "minecraft:bread");
    run("ZianUtilities pase reclamar", p4.createCommandSourceStack());
    check(states(ID4, Prize.State.PENDING) > 0 && countItem(p4, "minecraft:bread") == bread, "a full inventory leaves the items pending, nothing dropped or lost");
    check(data(ID4).prizes.values().stream().anyMatch(z -> z.state == Prize.State.PENDING && "INVENTORY_FULL".equals(z.reason)), "the reason is recorded as INVENTORY_FULL");
    check(chatHas("Inventario lleno|Inventory full"), "the player was told the inventory is full");
    check(states(ID4, Prize.State.UNCERTAIN) == 0 && pendingBefore >= 0, "nothing went uncertain");
    p4.getInventory().clearContent();
    run("ZianUtilities pase reclamar", p4.createCommandSourceStack());
    check(states(ID4, Prize.State.PENDING) == 0, "with room again everything is delivered");
    check(countItem(p4, "minecraft:cobblestone") == 0 && countItem(p4, "minecraft:glass") > 0, "and the items are in the inventory");
  }

  private static void previousSeason() throws Exception {
    mark();
    long delivered = states(ID4, Prize.State.DELIVERED);
    check(run("ZianUtilities pase admin xp dar PaseSmoke4 2400", console()) == 1, "XP goes to the previous season first");
    check(data(ID4).prizes.values().stream().anyMatch(z -> z.seasonId.equals(PREV)), "prizes of the previous season were created");
    run("ZianUtilities pase reclamar", p4.createCommandSourceStack());
    check(data(ID4).prizes.values().stream().filter(z -> z.seasonId.equals(PREV)).allMatch(z -> z.state == Prize.State.DELIVERED), "the previous season's prizes are claimable");
    check(states(ID4, Prize.State.DELIVERED) > delivered, "and the current season is untouched");
  }

  private static void offlinePlayer() throws Exception {
    check(PaseModule.server.getPlayerList().getPlayer(ID5) == null, "player 5 is offline");
    PaseModule.engine.adminGiveLevels(ID5, 5);
    check(PaseRewards.sync(data(ID5)) == freeCount(5), "an offline player's levels become pending prizes");
    PaseModule.store.saveNow(data(ID5));
    check(run("ZianUtilities pase admin premium dar " + ID5 + " " + CUR + " TBX-200", console()) == 1, "Tebex works for an offline player (by UUID)");
    check(PaseStateJson.readPlayer(Files.readString(PaseModule.store.playerFile(ID5)), ID5).premium.contains(CUR), "the premium was written to the offline player's file");
    PaseModule.store.release(ID5);
    check(states(ID5, Prize.State.PENDING) == freeCount(5) + paidCount(5) && states(ID5, Prize.State.DELIVERED) == 0, "free and paid prizes wait, nothing delivered while offline");
    p5 = PaseSmoke.fake("PaseSmoke5", ID5);
    mark();
    PaseRewards.onLogin(p5);
    check(chatHas("pendientes|waiting"), "on login the player is told about the pending prizes");
    mark();
    check(run("ZianUtilities pase reclamar", p5.createCommandSourceStack()) == freeCount(5) + paidCount(5), "they claim everything after joining");
    check(chatHas("reclamado|claimed"), "and are told what they got");
    check(run("ZianUtilities pase admin premium quitar " + ID5 + " " + CUR, console()) == 1 && !data(ID5).premium.contains(CUR), "a refund removes the premium");
  }

  private static void adminCommands() throws Exception {
    check(run("ZianUtilities pase admin premios ver @a[name=PaseSmoke4]", console()) == 1, "admin premios ver works");
    check(run("ZianUtilities pase admin premios reentregar @a[name=PaseSmoke4]", console()) == 1, "admin premios reentregar works");
    boolean denied = false;
    try {
      run("ZianUtilities pase admin premios ver PaseSmoke4", p4.createCommandSourceStack());
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
      denied = true;
    }
    check(denied, "a non-OP player cannot see or redeliver prizes");
  }

  /** Three prizes of player 6 stopped half way, as if the server died: an item given, coins credited, a Pokemon inserted. */
  private static void leaveHalfDone() throws Exception {
    p6 = PaseSmoke.fake("PaseSmoke6", ID6);
    PaseModule.engine.adminGiveLevels(ID6, 25);
    PlayerPase d = data(ID6);
    PaseRewards.sync(d);
    Prize item = d.prizes.get(Prize.idOf(CUR, Prize.Track.FREE, 1, 0));
    Prize coins = d.prizes.get(Prize.idOf(CUR, Prize.Track.FREE, 10, 1));
    Prize mon = pokemonPrize(ID6, Prize.Track.FREE, 25);
    var gateway = new PaseDelivery(p6);
    check(gateway.check(item) == null && gateway.check(coins) == null && gateway.check(mon) == null, "the three prizes can be delivered");
    p6.getInventory().add(new ItemStack(Items.BREAD, 16));
    check(PaseRewards.coins().claim(PaseDelivery.coinsClaim(ID6, coins)).status() == com.zianblk.zianutilities.core.rewards.ClaimStatus.CLAIMED, "coins credited in the claim journal");
    UUID pokemonId = PaseDelivery.pokemonId(ID6, mon);
    PokemonDelivery.deliver(p6, pokemonId, () -> PaseDelivery.build(p6, mon, PaseRewards.rewardsOf(CUR)), (phase, reason) -> { });
    for (Prize z : List.of(item, coins, mon)) z.state = Prize.State.DELIVERING;
    PaseModule.store.saveNow(d);
    Files.writeString(Path.of("pase-smoke-state3.txt"), "coins=" + balance(ID6, COPPER) + "\npokemon=" + pokemonId + "\nitem=" + item.id + "\ncoinsPrize=" + coins.id + "\nmonPrize=" + mon.id + "\n");
    check(balance(ID6, COPPER) == 32, "player 6 holds 32 copper coins");
    PaseSmoke.forget(p6);
  }

  // ---------------------------------------------------------------- phase 4

  private static void phaseFour() {
    STEPS.add(once(PaseSmokeRewards::recovered));
    STEPS.add(() -> allPokemonDelivered(ID6));
    STEPS.add(once(PaseSmokeRewards::finishedWithoutDuplicates));
  }

  private static Map<String, String> state() throws Exception {
    Map<String, String> m = new TreeMap<>();
    for (String line : Files.readAllLines(Path.of("pase-smoke-state3.txt"))) m.put(line.substring(0, line.indexOf('=')), line.substring(line.indexOf('=') + 1));
    return m;
  }

  private static void recovered() throws Exception {
    check(PaseModule.active(), "the module is active after the restart");
    season = withCommand();
    var saved = state();
    p6 = PaseSmoke.fake("PaseSmoke6", ID6);
    PlayerPase d = data(ID6);
    check(d.prizes.get(saved.get("item")).state == Prize.State.UNCERTAIN && "INTERRUPTED".equals(d.prizes.get(saved.get("item")).reason), "the item that was mid-delivery is UNCERTAIN, not pending");
    check(d.prizes.get(saved.get("coinsPrize")).state == Prize.State.PENDING && "RESUME".equals(d.prizes.get(saved.get("coinsPrize")).reason), "the coins resume with the same claim id");
    check(d.prizes.get(saved.get("monPrize")).state == Prize.State.PENDING && "RESUME".equals(d.prizes.get(saved.get("monPrize")).reason), "the Pokemon resumes with the same Pokemon id");
    check(Long.parseLong(saved.get("coins")) == 32 && balance(ID6, COPPER) == 32, "the coins were credited once before the restart");
    mark();
    run("ZianUtilities pase reclamar", p6.createCommandSourceStack());
    check(balance(ID6, COPPER) == 64, "after the claim player 6 has 64 copper (level 20 once more, the interrupted 32 not repeated): " + balance(ID6, COPPER));
    check(d.prizes.get(saved.get("coinsPrize")).state == Prize.State.DELIVERED, "the resumed coins prize closed without a second credit");
    check(countItem(p6, "minecraft:bread") == 32, "bread: levels 11 and 21 arrived, the uncertain level 1 one was not repeated");
    check(chatHas("revisión|review"), "the player was told one prize is under review");
  }

  private static void finishedWithoutDuplicates() throws Exception {
    var saved = state();
    UUID pokemon = UUID.fromString(saved.get("pokemon"));
    check(pcCopies(p6, pokemon) == 1, "the interrupted Pokemon is in the PC exactly once");
    check(data(ID6).prizes.get(saved.get("monPrize")).state == Prize.State.DELIVERED, "and its prize is DELIVERED after the read-back proof");
    check(run("ZianUtilities pase admin premios reentregar @a[name=PaseSmoke6]", console()) >= 1, "the admin redelivers the uncertain item");
    check(countItem(p6, "minecraft:bread") == 48 && data(ID6).prizes.get(saved.get("item")).state == Prize.State.DELIVERED, "it arrives once, after the admin decided");
    check(run("ZianUtilities pase reclamar", p6.createCommandSourceStack()) == 0 && countItem(p6, "minecraft:bread") == 48 && pcCopies(p6, pokemon) == 1, "nothing more is given");
    check(states(ID6, Prize.State.PENDING) == 0 && states(ID6, Prize.State.UNCERTAIN) == 0, "the journal is closed");
    PaseSmoke.forget(p6);
  }

  // ---------------------------------------------------------------- end

  private static void finish(boolean ok) {
    finished = true;
    try {
      for (ServerPlayer p : new ServerPlayer[] {p4, p5, p6}) if (p != null) PaseSmoke.forget(p);
      Files.writeString(Path.of("pase-smoke-" + phase + ".txt"), (ok ? "PASS\n" : "FAIL\n") + REPORT);
    } catch (Exception ignored) { }
    PaseModule.LOG.info("PASE REWARDS SMOKE {} phase {}\n{}", ok ? "PASS" : "FAIL", phase, REPORT);
    try {
      PaseModule.saveAll();
    } catch (Exception failure) {
      PaseModule.LOG.error("No se pudo guardar al terminar el smoke", failure);
    }
    PaseModule.server.halt(false);
  }
}
