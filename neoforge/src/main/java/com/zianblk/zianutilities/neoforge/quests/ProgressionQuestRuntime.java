package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.rewards.ClaimStatus;
import com.zianblk.zianutilities.core.rewards.EconomyRewardDelivery;
import com.zianblk.zianutilities.core.rewards.FileRewardClaimStore;
import com.zianblk.zianutilities.core.rewards.Reward;
import com.zianblk.zianutilities.core.rewards.RewardClaim;
import com.zianblk.zianutilities.core.rewards.RewardClaimService;
import com.zianblk.zianutilities.core.rewards.RewardComponent;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import com.zianblk.zianutilities.neoforge.economy.AvecoinsContractProbe;
import com.zianblk.zianutilities.neoforge.economy.AvecoinsEconomyPort;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Server-owned weekly and generation campaign progress. All methods run on the server thread. */
public final class ProgressionQuestRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/ProgressionQuests");
    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
    private static final String COIN = "avecoins:coppercoin";
    private static final int[] CHAPTER_GOALS = {3, 8, 15};
    private static final CobblemonGenerationResolver RESOLVER = new CobblemonGenerationResolver(null);
    private static MinecraftServer activeServer;
    private static Path root;
    private static RewardClaimService claims;
    private static final Set<UUID> recoveredPlayers = new HashSet<>();

    private ProgressionQuestRuntime() {}

    public record Weekly(long startsAt, long endsAt, boolean testWindow, boolean accepted, int captures,
                         int battles, boolean capturePaid, boolean battlePaid,
                         String captureCurrency, long captureAmount,
                         String battleCurrency, long battleAmount) {}

    public record Campaign(String generation, boolean available, boolean accepted,
                           int chapter, int capturedSpecies, int goal,
                           boolean rewardPending, String currency, long amount) {}

    public static long weekStart(long now) {
        return Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    public static synchronized Weekly weekly(ServerPlayer player) throws IOException {
        initialize(player.getServer());
        recoverOldWeeks(player.getUUID());
        long realStart = weekStart(System.currentTimeMillis());
        long start = weeklyKey(realStart);
        Properties p = read(weeklyPath(start, player.getUUID()));
        Properties offer = weeklyOffer(start);
        settleWeekly(player.getUUID(), start, p);
        return new Weekly(start, Instant.ofEpochMilli(realStart).atZone(ZONE).plusWeeks(1)
            .toInstant().toEpochMilli(), start != realStart, accepted(p), ids(p, "capture").size(),
            ids(p, "battle").size(), Boolean.parseBoolean(p.getProperty("capture.paid")),
            Boolean.parseBoolean(p.getProperty("battle.paid")),
            p.getProperty("capture.currency", offer.getProperty("weekly.capture.currency")),
            amount(p, "capture.amount", amount(offer, "weekly.capture.amount", 5)),
            p.getProperty("battle.currency", offer.getProperty("weekly.battle.currency")),
            amount(p, "battle.amount", amount(offer, "weekly.battle.amount", 10)));
    }

    public static synchronized List<Campaign> campaigns(ServerPlayer player) throws IOException {
        initialize(player.getServer());
        Set<Generation> enabled = GlobalQuestRuntime.enabled(player.getServer());
        List<Campaign> result = new ArrayList<>();
        for (Generation generation : Generation.values()) {
            Properties p = read(campaignPath(player.getUUID(), generation));
            settleCampaign(player.getUUID(), generation, p);
            int chapter = Integer.parseInt(p.getProperty("chapter", "0"));
            result.add(new Campaign(generation.getId(), enabled.contains(generation), accepted(p),
                chapter, species(p).size(), chapter < 3 ? CHAPTER_GOALS[chapter] : 0,
                chapter < 3 && species(p).size() >= CHAPTER_GOALS[chapter],
                p.getProperty("reward.currency", COIN), amount(p, "reward.amount", 0)));
        }
        return result;
    }

    public static synchronized void acceptWeekly(ServerPlayer player) throws IOException {
        initialize(player.getServer());
        long start = weeklyKey(weekStart(System.currentTimeMillis()));
        Path path = weeklyPath(start, player.getUUID());
        Properties p = read(path);
        if (accepted(p)) return;
        Properties config = weeklyOffer(start);
        p.setProperty("acceptedAt", Long.toString(System.currentTimeMillis()));
        p.setProperty("capture.currency", config.getProperty("weekly.capture.currency"));
        p.setProperty("capture.amount", config.getProperty("weekly.capture.amount"));
        p.setProperty("battle.currency", config.getProperty("weekly.battle.currency"));
        p.setProperty("battle.amount", config.getProperty("weekly.battle.amount"));
        write(path, p);
    }

    public static synchronized void acceptCampaign(ServerPlayer player, Generation generation) throws IOException {
        initialize(player.getServer());
        if (!GlobalQuestRuntime.enabled(player.getServer()).contains(generation)) return;
        Path path = campaignPath(player.getUUID(), generation);
        Properties p = read(path);
        if (accepted(p)) return;
        Properties config = config();
        p.setProperty("acceptedAt", Long.toString(System.currentTimeMillis()));
        p.setProperty("chapter", "0");
        freezeChapterReward(p, config, 0);
        write(path, p);
    }

    public static synchronized void capture(ServerPlayer player, UUID pokemonId,
                                            String speciesId, Set<Generation> generations) throws IOException {
        initialize(player.getServer());
        Set<Generation> enabled = GlobalQuestRuntime.enabled(player.getServer());
        if (generations.stream().noneMatch(enabled::contains)) return;
        if (!CobblemonWorldSpawnSpeciesPool.eligible(enabled, RESOLVER).contains(speciesId)) return;

        long start = weeklyKey(weekStart(System.currentTimeMillis()));
        Path weeklyPath = weeklyPath(start, player.getUUID());
        Properties weekly = read(weeklyPath);
        if (accepted(weekly) && !Boolean.parseBoolean(weekly.getProperty("capture.paid"))) {
            Set<String> captures = ids(weekly, "capture");
            if (captures.size() < 25 && captures.add(pokemonId.toString())) {
                weekly.setProperty("capture.ids", String.join(",", captures));
                write(weeklyPath, weekly);
                settleWeekly(player.getUUID(), start, weekly);
                ProgressionQuestNetwork.send(player, false);
            }
        }

        for (Generation generation : generations) {
            if (!enabled.contains(generation)
                || !CobblemonWorldSpawnSpeciesPool.eligible(Set.of(generation), RESOLVER).contains(speciesId)) continue;
            Path path = campaignPath(player.getUUID(), generation);
            Properties p = read(path);
            int chapter = Integer.parseInt(p.getProperty("chapter", "0"));
            if (!accepted(p) || chapter >= 3) continue;
            Set<String> captured = species(p);
            if (captured.size() < CHAPTER_GOALS[chapter] && captured.add(speciesId)) {
                p.setProperty("species", String.join(",", captured));
                write(path, p);
                settleCampaign(player.getUUID(), generation, p);
                ProgressionQuestNetwork.send(player, false);
            }
        }
    }

    public static synchronized void wildVictory(ServerPlayer player, UUID battleId) throws IOException {
        initialize(player.getServer());
        long start = weeklyKey(weekStart(System.currentTimeMillis()));
        Path path = weeklyPath(start, player.getUUID());
        Properties p = read(path);
        if (!accepted(p) || Boolean.parseBoolean(p.getProperty("battle.paid"))) return;
        Set<String> battles = ids(p, "battle");
        if (battles.size() >= 50 || !battles.add(battleId.toString())) return;
        p.setProperty("battle.ids", String.join(",", battles));
        write(path, p);
        settleWeekly(player.getUUID(), start, p);
        ProgressionQuestNetwork.send(player, false);
    }

    private static void settleWeekly(UUID player, long start, Properties p) throws IOException {
        if (!accepted(p)) return;
        Path path = weeklyPath(start, player);
        for (String objective : List.of("capture", "battle")) {
            int goal = objective.equals("capture") ? 25 : 50;
            if (ids(p, objective).size() < goal || Boolean.parseBoolean(p.getProperty(objective + ".paid"))) continue;
            ClaimStatus status = start == weekStart(start)
                ? pay(player, "weekly:" + start + ":" + objective,
                    p.getProperty(objective + ".currency", COIN), amount(p, objective + ".amount", 0))
                : null;
            if (status == ClaimStatus.CLAIMED || status == null) {
                p.setProperty(objective + ".paid", "true");
                write(path, p);
            }
        }
    }

    private static void recoverOldWeeks(UUID player) throws IOException {
        if (recoveredPlayers.contains(player)) return;
        Path directory = root.resolve("weekly");
        if (!Files.isDirectory(directory)) {
            recoveredPlayers.add(player);
            return;
        }
        long current = weekStart(System.currentTimeMillis());
        try (var periods = Files.list(directory)) {
            for (Path period : periods.filter(Files::isDirectory).toList()) {
                long start;
                try { start = Long.parseLong(period.getFileName().toString()); }
                catch (NumberFormatException ignored) { continue; }
                if (start >= current || start != weekStart(start)) continue;
                Path path = weeklyPath(start, player);
                if (Files.exists(path)) settleWeekly(player, start, read(path));
            }
        }
        recoveredPlayers.add(player);
    }

    private static void settleCampaign(UUID player, Generation generation, Properties p) throws IOException {
        if (!accepted(p)) return;
        Path path = campaignPath(player, generation);
        int chapter = Integer.parseInt(p.getProperty("chapter", "0"));
        if (chapter >= 3 || species(p).size() < CHAPTER_GOALS[chapter]) return;
        ClaimStatus status = pay(player, "campaign:" + generation.getId() + ":" + chapter,
            p.getProperty("reward.currency", COIN), amount(p, "reward.amount", 0));
        if (status != ClaimStatus.CLAIMED && status != null) return;
        p.setProperty("chapter", Integer.toString(chapter + 1));
        p.remove("species");
        if (chapter + 1 < 3) freezeChapterReward(p, config(), chapter + 1);
        write(path, p);
    }

    private static ClaimStatus pay(UUID player, String key, String currency, long amount) {
        if (!GlobalQuestRuntime.rewardsEnabled()) return null;
        if (!AvecoinsContractProbe.inspect().compatible()) {
            LOGGER.warn("[ZIAN-PROGRESSION-QUEST] reward blocked: AVECOINS contract unavailable key={}", key);
            return ClaimStatus.RECOVERY_REQUIRED;
        }
        UUID id = UUID.nameUUIDFromBytes(("zianutilities:progression_quest_v1:" + player + ":" + key)
            .getBytes(StandardCharsets.UTF_8));
        RewardClaim claim = new RewardClaim(id, player, "progression_quest", key,
            List.of(new RewardComponent("reward", new Reward.Currency(currency, amount))));
        ClaimStatus status = claims.claim(claim).status();
        LOGGER.info("[ZIAN-AUDIT] action=progression_quest_credit playerUuid={} key={} claimId={} result={}",
            player, key, id, status);
        return status;
    }

    private static void freezeChapterReward(Properties p, Properties config, int chapter) {
        String prefix = "campaign.chapter" + (chapter + 1);
        p.setProperty("reward.currency", config.getProperty(prefix + ".currency"));
        p.setProperty("reward.amount", config.getProperty(prefix + ".amount"));
    }

    private static Properties weeklyOffer(long start) throws IOException {
        Path path = root.resolve("weekly").resolve(Long.toString(start)).resolve("offer.properties");
        if (Files.exists(path)) return read(path);
        Properties source = config();
        Properties offer = new Properties();
        for (String objective : List.of("capture", "battle")) {
            for (String field : List.of("currency", "amount")) {
                String key = "weekly." + objective + "." + field;
                offer.setProperty(key, source.getProperty(key));
            }
        }
        write(path, offer);
        return offer;
    }

    /** A rehearsal changes only the weekly progress key and never pays weekly rewards. */
    public static synchronized void rotateWeekForTest(MinecraftServer server) throws IOException {
        if (!Boolean.getBoolean(GlobalQuestRuntime.TEST_FLAG))
            throw new IllegalStateException("quest tests are disabled");
        initialize(server);
        long realStart = weekStart(System.currentTimeMillis());
        Path path = root.resolve("weekly-test.properties");
        Properties p = read(path);
        int serial = realStart == amount(p, "realStart", -1)
            ? Integer.parseInt(p.getProperty("serial", "0")) + 1 : 1;
        if (serial > 1000) throw new IllegalStateException("too many test rotations");
        p.setProperty("realStart", Long.toString(realStart));
        p.setProperty("serial", Integer.toString(serial));
        write(path, p);
        LOGGER.info("[ZIAN-AUDIT] action=weekly_quest_test_rotate realWeek={} serial={} rewards=disabled",
            realStart, serial);
    }

    private static long weeklyKey(long realStart) throws IOException {
        Properties p = read(root.resolve("weekly-test.properties"));
        if (amount(p, "realStart", -1) != realStart) return realStart;
        int serial = Integer.parseInt(p.getProperty("serial", "0"));
        return realStart + serial * 1000L;
    }

    private static Properties config() throws IOException {
        Path path = root.resolve("config.properties");
        if (Files.exists(path)) {
            Properties p = read(path);
            validateConfig(p);
            return p;
        }
        Properties p = new Properties();
        p.setProperty("weekly.capture.currency", COIN);
        p.setProperty("weekly.capture.amount", "5");
        p.setProperty("weekly.battle.currency", COIN);
        p.setProperty("weekly.battle.amount", "10");
        for (int i = 1; i <= 3; i++) {
            p.setProperty("campaign.chapter" + i + ".currency", COIN);
            p.setProperty("campaign.chapter" + i + ".amount", Integer.toString(i * 5));
        }
        write(path, p);
        return p;
    }

    private static void validateConfig(Properties p) {
        for (String key : List.of("weekly.capture", "weekly.battle", "campaign.chapter1",
            "campaign.chapter2", "campaign.chapter3")) {
            String currency = p.getProperty(key + ".currency");
            if (currency == null || currency.isBlank()) throw new IllegalArgumentException("missing " + key + ".currency");
            long value = Long.parseLong(p.getProperty(key + ".amount"));
            if (value <= 0) throw new IllegalArgumentException("invalid " + key + ".amount");
        }
    }

    private static long amount(Properties p, String key, long fallback) {
        return Long.parseLong(p.getProperty(key, Long.toString(fallback)));
    }

    private static boolean accepted(Properties p) { return p.containsKey("acceptedAt"); }
    private static Set<String> ids(Properties p, String objective) {
        String text = p.getProperty(objective + ".ids", "");
        return text.isBlank() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(text.split(",")));
    }
    private static Set<String> species(Properties p) {
        String text = p.getProperty("species", "");
        return text.isBlank() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(text.split(",")));
    }

    private static Path weeklyPath(long start, UUID player) {
        return root.resolve("weekly").resolve(Long.toString(start)).resolve(player + ".properties");
    }
    private static Path campaignPath(UUID player, Generation generation) {
        return root.resolve("campaigns").resolve(player.toString()).resolve(generation.getId() + ".properties");
    }

    private static Properties read(Path path) throws IOException {
        Properties p = new Properties();
        if (Files.exists(path)) try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(reader); }
        return p;
    }

    private static void write(Path path, Properties p) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "progression-quest-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                p.store(writer, "Zian Utilities progression quests");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void initialize(MinecraftServer server) {
        if (activeServer == server && claims != null) return;
        activeServer = server;
        recoveredPlayers.clear();
        Path base = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("zianutilities");
        root = base.resolve("progression_quests_v1");
        claims = new RewardClaimService(new FileRewardClaimStore(base.resolve("reward_claims")),
            new EconomyRewardDelivery(new AvecoinsEconomyPort()));
    }
}
