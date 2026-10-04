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
import com.zianblk.zianutilities.neoforge.ZianFeatureSettings;
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

    public static boolean moduleEnabled() { return ZianFeatureSettings.get().quests(); }
    public static boolean rewardsEnabled() { return ZianFeatureSettings.get().questRewardsActive(); }
    private static final String[] LEGACY_POOLS = {
        "cobblemon:caterpie,cobblemon:pidgey",
        "cobblemon:sentret,cobblemon:hoothoot",
        "cobblemon:zigzagoon,cobblemon:poochyena",
        "cobblemon:bidoof,cobblemon:starly",
        "cobblemon:patrat,cobblemon:lillipup",
        "cobblemon:bunnelby,cobblemon:fletchling",
        "cobblemon:yungoos,cobblemon:komala,cobblemon:minior",
        "cobblemon:rookidee,cobblemon:woo…9461 tokens truncated…
                            ))
                        )
                    )
                    .then(Commands.literal("testcredit")
                        .requires(source -> source.hasPermission(3) && Boolean.getBoolean(TEST_FLAG))
                        .executes(context -> testCredit(context.getSource())))
                )
        );
    }

    private static int status(CommandSourceStack source) {
        var result = AvecoinsContractProbe.inspect();
        source.sendSuccess(
            () -> Component.literal("Misiones: "
                + (ZianFeatureSettings.get().quests() ? "activadas" : "desactivadas")
                + "; pagos de misiones: "
                + (ZianFeatureSettings.get().questRewardsActive() ? "activados" : "desactivados")
                + "; prueba de crédito: " + (Boolean.getBoolean(TEST_FLAG) ? "activada" : "desactivada") + "."),
            false
        );
        source.sendSuccess(() -> Component.literal(result.detail()), false);
        if (result.compatible()) {
            String currencies = result.currencies().isEmpty()
                ? "ninguna"
                : String.join(", ", result.currencies());
            source.sendSuccess(
                () -> Component.literal("Monedas administradas: " + currencies),
                false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int balance(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Ejecuta este diagnóstico como jugador operador."));
            return 0;
        }
        try {
            var result = new AvecoinsEconomyPort().balance(player.getUUID(), TEST_CURRENCY);
            if (result instanceof com.zianblk.zianutilities.core.economy.EconomyBalanceResult.Available known) {
                source.sendSuccess(
                    () -> Component.literal("Saldo de " + TEST_CURRENCY + ": " + known.getAmount()),
                    false
                );
                return Command.SINGLE_SUCCESS;
            }
            source.sendFailure(Component.literal("No se pudo leer el saldo AVECOINS."));
            return 0;
        } catch (RuntimeException error) {
            source.sendFailure(Component.literal("AVECOINS no disponible: " + error.getMessage()));
            return 0;
        }
    }

    private static int testCredit(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("La prueba solo puede ejecutarla un jugador operador."));
            return 0;
        }
        UUID claimId = UUID.nameUUIDFromBytes(
            ("zianutilities:admin_copper_credit:v1:" + player.getUUID())
                .getBytes(StandardCharsets.UTF_8)
        );
        RewardClaim claim = new RewardClaim(
            claimId, player.getUUID(), "admin_probe", "copper_credit_v1",
            List.of(new RewardComponent("copper", new Reward.Currency(TEST_CURRENCY, 1)))
        );

        try {
            RewardClaimRecord record = service(source.getServer()).claim(claim);
            ClaimStatus state = record.status();
            LOGGER.info(
                "[ZIAN-AUDIT] action=reward_test_credit actorUuid={} claimId={} result={}",
                player.getUUID(), claimId, state
            );
            if (state == ClaimStatus.CLAIMED) {
                source.sendSuccess(
                    () -> Component.literal("Prueba de 1 coppercoin confirmada. Reclamación "
                        + claimId + ". Repetir este comando no vuelve a acreditarla."),
                    true
                );
                return Command.SINGLE_SUCCESS;
            }
            source.sendFailure(Component.literal("Reclamación " + claimId + ": " + state
                + ". No crees otra prueba si el resultado es incierto."));
            return 0;
        } catch (Exception error) {
            LOGGER.error(
                "[ZIAN-AUDIT] action=reward_test_credit actorUuid={} claimId={} result=error",
                player.getUUID(), claimId, error
            );
            source.sendFailure(Component.literal("Prueba detenida; revisa /ZianUtilities reward inspect "
                + claimId + " antes de cualquier otro intento."));
            return 0;
        }
    }

    private static int inspect(CommandSourceStack source, String rawId) {
        UUID claimId;
        try {
            claimId = UUID.fromString(rawId);
        } catch (IllegalArgumentException error) {
            source.sendFailure(Component.literal("ID de reclamación inválido."));
            return 0;
        }

        try {
            RewardClaimRecord record = store(source.getServer()).load(claimId);
            if (record == null) {
                source.sendFailure(Component.literal("Reclamación no encontrada."));
                return 0;
            }
            ClaimStatus state = record.status();
            String components = record.getComponents().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().getStatus().name())
                .sorted()
                .collect(Collectors.joining(", "));
            source.sendSuccess(
                () -> Component.literal("Reclamación " + claimId + ": " + state.name()
                    + " [" + components + "]"),
                false
            );
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            source.sendFailure(Component.literal(
                "No se pudo leer el registro: " + error.getClass().getSimpleName()
            ));
            return 0;
        }
    }

    private static synchronized FileRewardClaimStore store(MinecraftServer server) {
        if (activeServer != server) {
            Path directory = server.getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("zianutilities").resolve("reward_claims");
            activeStore = new FileRewardClaimStore(directory);
            activeService = null;
            activeServer = server;
        }
        return activeStore;
    }

    private static synchronized RewardClaimService service(MinecraftServer server) {
        FileRewardClaimStore store = store(server);
        if (activeService == null) {
            activeService = new RewardClaimService(
                store, new EconomyRewardDelivery(new AvecoinsEconomyPort())
            );
        }
        return activeService;
    }
}

