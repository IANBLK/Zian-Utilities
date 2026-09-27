package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.quests.FileGlobalQuestProgressStore;
import com.zianblk.zianutilities.core.quests.GlobalQuestOffer;
import com.zianblk.zianutilities.core.quests.GlobalQuestProgress;
import com.zianblk.zianutilities.core.quests.GlobalQuestService;
import com.zianblk.zianutilities.core.quests.SharedCaptureSchedule;
import com.zianblk.zianutilities.core.rewards.ClaimStatus;
import com.zianblk.zianutilities.core.rewards.EconomyRewardDelivery;
import com.zianblk.zianutilities.core.rewards.FileRewardClaimStore;
import com.zianblk.zianutilities.core.rewards.Reward;
import com.zianblk.zianutilities.core.rewards.RewardClaim;
import com.zianblk.zianutilities.core.rewards.RewardClaimRecord;
import com.zianblk.zianutilities.core.rewards.RewardClaimService;
import com.zianblk.zianutilities.core.rewards.RewardComponent;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import com.zianblk.zianutilities.neoforge.economy.AvecoinsContractProbe;
import com.zianblk.zianutilities.neoforge.economy.AvecoinsEconomyPort;
import com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore;
import net.minecraft.server.MinecraftServer;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/** Server-owned global definition; reward amounts are frozen when the window begins. */
public final class GlobalQuestRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/GlobalQuest");
    static final String TEST_FLAG = "zianutilities.globalQuestTestEnabled";
    static final String REWARD_FLAG = "zianutilities.globalQuestRewardTestEnabled";
    private static final String[] LEGACY_POOLS = {
        "cobblemon:caterpie,cobblemon:pidgey",
        "cobblemon:sentret,cobblemon:hoothoot",
        "cobblemon:zigzagoon,cobblemon:poochyena",
        "cobblemon:bidoof,cobblemon:starly",
        "cobblemon:patrat,cobblemon:lillipup",
        "cobblemon:bunnelby,cobblemon:fletchling",
        "cobblemon:yungoos,cobblemon:komala,cobblemon:minior",
        "cobblemon:rookidee,cobblemon:wooloo",
        "cobblemon:lechonk,cobblemon:pawmi"
    };
    private static final String[] DEFAULT_POOLS = {
        "cobblemon:caterpie,cobblemon:pidgey,cobblemon:rattata,cobblemon:zubat,cobblemon:oddish,cobblemon:geodude",
        "cobblemon:sentret,cobblemon:hoothoot,cobblemon:spinarak,cobblemon:wooper,cobblemon:marill,cobblemon:murkrow",
        "cobblemon:zigzagoon,cobblemon:poochyena,cobblemon:taillow,cobblemon:wingull,cobblemon:lotad,cobblemon:seedot",
        "cobblemon:bidoof,cobblemon:starly,cobblemon:shinx,cobblemon:buizel,cobblemon:buneary,cobblemon:kricketot",
        "cobblemon:patrat,cobblemon:lillipup,cobblemon:pidove,cobblemon:roggenrola,cobblemon:venipede,cobblemon:woobat",
        "cobblemon:bunnelby,cobblemon:fletchling,cobblemon:scatterbug,cobblemon:espurr,cobblemon:pumpkaboo",
        "cobblemon:yungoos,cobblemon:komala,cobblemon:minior,cobblemon:mudbray,cobblemon:cutiefly,cobblemon:comfey",
        "cobblemon:rookidee,cobblemon:wooloo,cobblemon:skwovet,cobblemon:chewtle,cobblemon:nickit,cobblemon:yamper",
        "cobblemon:lechonk,cobblemon:pawmi,cobblemon:nacli,cobblemon:fidough,cobblemon:tandemaus,cobblemon:maschiff"
    };
    private static final CobblemonGenerationResolver RESOLVER = new CobblemonGenerationResolver(null);
    private static MinecraftServer activeServer;
    private static GlobalQuestService service;
    private static FileRewardClaimStore rewardStore;
    private static RewardClaimService rewardService;
    private static Path root;

    private GlobalQuestRuntime() {}

    public static synchronized GlobalQuestService service(MinecraftServer server) {
        initialize(server);
        return service;
    }

    public static synchronized GlobalQuestOffer offer(MinecraftServer server) throws IOException {
        initialize(server);
        long window = SharedCaptureSchedule.INSTANCE.at(System.currentTimeMillis()).getWindowStartEpochMs();
        Set<Generation> enabled = enabled(server);
        String generations = enabled.stream().map(Generation::getId).sorted().collect(Collectors.joining(","));
        Path path = root.resolve("offer.properties");
        GlobalQuestOffer previous = readOffer(path);
        if (previous != null && previous.getWindowStartEpochMs() == window
            && previous.getGenerationIds().equals(generations)) return previous;

        Properties config = readConfig();
        String captureCurrency = previous != null && previous.getWindowStartEpochMs() == window
            ? previous.getCaptureCurrency() : config.getProperty("capture.currency").trim();
        long captureAmount = previous != null && previous.getWindowStartEpochMs() == window
            ? previous.getCaptureAmount() : Long.parseLong(config.getProperty("capture.amount").trim());
        String battleCurrency = previous != null && previous.getWindowStartEpochMs() == window
            ? previous.getBattleCurrency() : config.getProperty("battle.currency").trim();
        long battleAmount = previous != null && previous.getWindowStartEpochMs() == window
            ? previous.getBattleAmount() : Long.parseLong(config.getProperty("battle.amount").trim());
        boolean rewardsEnabled = previous != null && previous.getWindowStartEpochMs() == window
            ? previous.getRewardsEnabled() : Boolean.getBoolean(REWARD_FLAG);
        List<String> pool = eligibleSpecies(config, enabled);
        String priorTarget = previous != null && previous.getWindowStartEpochMs() != window
            ? previous.getTargetSpecies() : null;
        String target = selectTarget(pool, priorTarget, ThreadLocalRandom.current());
        GlobalQuestOffer next = new GlobalQuestOffer(window, target, generations,
            captureCurrency, captureAmount, battleCurrency, battleAmount, rewardsEnabled);
        writeOffer(path, next);
        LOGGER.info("[ZIAN-GLOBAL-QUEST] window={} generations={} species={} candidates={} rewards={}",
            window, generations, target, pool.size(), Boolean.getBoolean(REWARD_FLAG) ? "test-enabled" : "disabled");
        return next;
    }

    private static List<String> eligibleSpecies(Properties config, Set<Generation> enabled) {
        if (!"configured".equals(config.getProperty("species.mode", "world_spawn_pool"))) {
            return CobblemonWorldSpawnSpeciesPool.eligible(enabled, RESOLVER);
        }
        List<String> rawPool = new ArrayList<>();
        for (Generation generation : enabled.stream().sorted(Comparator.comparing(Generation::getId)).toList()) {
            String csv = config.getProperty("species." + generation.getId(), "");
            Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .filter(s -> RESOLVER.resolve(s).contains(generation))
                .forEach(rawPool::add);
        }
        return rawPool.stream().distinct().sorted().toList();
    }

    public static synchronized int eligibleSpeciesCount(MinecraftServer server) throws IOException {
        initialize(server);
        return eligibleSpecies(readConfig(), enabled(server)).size();
    }

    /** Rotate the offer inside the current real window for an operator test; no reward can be earned from it. */
    public static synchronized GlobalQuestOffer rotateForTest(MinecraftServer server) throws IOException {
        if (!Boolean.getBoolean(TEST_FLAG)) throw new IllegalStateException("global quest test is disabled");
        GlobalQuestOffer current = offer(server);
        List<String> pool = eligibleSpecies(readConfig(), enabled(server));
        GlobalQuestOffer rotated = testRotationOffer(current, pool);
        writeOffer(root.resolve("offer.properties"), rotated);
        LOGGER.info("[ZIAN-AUDIT] action=global_quest_test_rotate window={} from={} to={} candidates={} rewards=disabled",
            current.getWindowStartEpochMs(), current.getTargetSpecies(), rotated.getTargetSpecies(), pool.size());
        return rotated;
    }

    static GlobalQuestOffer testRotationOffer(GlobalQuestOffer current, List<String> pool) {
        if (pool.size() < 2 || current.getTargetSpecies() == null || !pool.contains(current.getTargetSpecies())) {
            throw new IllegalStateException("at least two eligible species including the current target are required");
        }
        String target = selectTarget(pool, current.getTargetSpecies(), ThreadLocalRandom.current());
        return new GlobalQuestOffer(current.getWindowStartEpochMs(), target, current.getGenerationIds(),
            current.getCaptureCurrency(), current.getCaptureAmount(),
            current.getBattleCurrency(), current.getBattleAmount(), false);
    }

    static String selectTarget(List<String> pool, String priorTarget, RandomGenerator random) {
        if (pool.isEmpty()) return null;
        int priorIndex = pool.size() > 1 ? pool.indexOf(priorTarget) : -1;
        int index = random.nextInt(pool.size() - (priorIndex >= 0 ? 1 : 0));
        if (priorIndex >= 0 && index >= priorIndex) index++;
        return pool.get(index);
    }

    public static Set<Generation> enabled(MinecraftServer server) {
        return new NeoForgeGenerationStateStore(server).load().getEnabled();
    }

    public static UUID claimId(GlobalQuestProgress progress, String objective) {
        String key = "zianutilities:global_quest_v1:" + progress.getPlayerId() + ":"
            + progress.getOffer().getWindowStartEpochMs() + ":" + objective;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    /** Claim IDs do not include species, so generation changes cannot pay twice in one window. */
    public static synchronized ClaimStatus pay(GlobalQuestProgress progress, String objective) {
        if (!Boolean.getBoolean(REWARD_FLAG) || !progress.getOffer().getRewardsEnabled()) return null;
        if (!((objective.equals("capture") && progress.getCaptureComplete())
            || (objective.equals("battle") && progress.getBattleComplete()))) return null;
        if (!AvecoinsContractProbe.inspect().compatible()) {
            LOGGER.warn("[ZIAN-GLOBAL-QUEST] reward blocked: AVECOINS contract unavailable");
            return null;
        }
        initialize(activeServer);
        GlobalQuestOffer offer = progress.getOffer();
        String currency = objective.equals("capture") ? offer.getCaptureCurrency() : offer.getBattleCurrency();
        long amount = objective.equals("capture") ? offer.getCaptureAmount() : offer.getBattleAmount();
        RewardClaim claim = new RewardClaim(claimId(progress, objective), progress.getPlayerId(),
            "global_quest", offer.getWindowStartEpochMs() + ":" + objective,
            List.of(new RewardComponent(objective, new Reward.Currency(currency, amount))));
        RewardClaimRecord result = rewardService.claim(claim);
        LOGGER.info("[ZIAN-AUDIT] action=global_quest_credit playerUuid={} objective={} claimId={} result={}",
            progress.getPlayerId(), objective, claim.getClaimId(), result.status());
        return result.status();
    }

    private static void initialize(MinecraftServer server) {
        if (activeServer == server && service != null) return;
        activeServer = server;
        root = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("zianutilities")
            .resolve("global_quest_v1");
        // Share the existing journal location so /zian reward inspect can diagnose uncertain claims.
        rewardStore = new FileRewardClaimStore(root.getParent().resolve("reward_claims"));
        rewardService = new RewardClaimService(rewardStore, new EconomyRewardDelivery(new AvecoinsEconomyPort()));
        service = new GlobalQuestService(new FileGlobalQuestProgressStore(root.resolve("players"), (old, current) -> {
            if (old.getOffer().getWindowStartEpochMs() < current.getOffer().getWindowStartEpochMs()) {
                rewardStore.deleteClaimed(claimId(old, "capture"));
                rewardStore.deleteClaimed(claimId(old, "battle"));
            }
            return kotlin.Unit.INSTANCE;
        }));
    }

    private static Properties readConfig() throws IOException {
        Path path = root.resolve("config.properties");
        Properties p = new Properties();
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(reader); }
            boolean changed = false;
            if (!"2".equals(p.getProperty("species.poolRevision"))) {
                upgradeLegacyPools(p);
                p.setProperty("species.poolRevision", "2");
                changed = true;
            }
            if (!p.containsKey("species.mode")) {
                p.setProperty("species.mode", "world_spawn_pool");
                changed = true;
            }
            if (changed) write(path, p);
            return p;
        }
        p.setProperty("capture.currency", "avecoins:coppercoin");
        p.setProperty("capture.amount", "1");
        p.setProperty("battle.currency", "avecoins:coppercoin");
        p.setProperty("battle.amount", "1");
        p.setProperty("species.poolRevision", "2");
        p.setProperty("species.mode", "world_spawn_pool");
        for (int i = 0; i < DEFAULT_POOLS.length; i++) {
            p.setProperty("species.gen" + (i + 1), DEFAULT_POOLS[i]);
        }
        write(path, p);
        return p;
    }

    static void upgradeLegacyPools(Properties p) {
        for (int i = 0; i < LEGACY_POOLS.length; i++) {
            String key = "species.gen" + (i + 1);
            if (LEGACY_POOLS[i].equals(p.getProperty(key))) {
                p.setProperty(key, DEFAULT_POOLS[i]);
            }
        }
    }

    private static GlobalQuestOffer readOffer(Path path) throws IOException {
        if (!Files.exists(path)) return null;
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(reader); }
        if (!"1".equals(p.getProperty("schema"))) throw new IOException("unsupported global quest offer schema");
        return new GlobalQuestOffer(Long.parseLong(p.getProperty("windowStart")),
            p.getProperty("targetSpecies").isBlank() ? null : p.getProperty("targetSpecies"),
            p.getProperty("generationIds"), p.getProperty("captureCurrency"),
            Long.parseLong(p.getProperty("captureAmount")), p.getProperty("battleCurrency"),
            Long.parseLong(p.getProperty("battleAmount")), Boolean.parseBoolean(p.getProperty("rewardsEnabled")));
    }

    private static void writeOffer(Path path, GlobalQuestOffer offer) throws IOException {
        Properties p = new Properties();
        p.setProperty("schema", "1");
        p.setProperty("windowStart", Long.toString(offer.getWindowStartEpochMs()));
        p.setProperty("targetSpecies", offer.getTargetSpecies() == null ? "" : offer.getTargetSpecies());
        p.setProperty("generationIds", offer.getGenerationIds());
        p.setProperty("captureCurrency", offer.getCaptureCurrency());
        p.setProperty("captureAmount", Long.toString(offer.getCaptureAmount()));
        p.setProperty("battleCurrency", offer.getBattleCurrency());
        p.setProperty("battleAmount", Long.toString(offer.getBattleAmount()));
        p.setProperty("rewardsEnabled", Boolean.toString(offer.getRewardsEnabled()));
        write(path, p);
    }

    private static void write(Path path, Properties p) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "global-quest-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { p.store(writer, "Zian Utilities global quest"); }
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}

