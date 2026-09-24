package com.zianblk.research.spawnobserver;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.cooking.PokeSnackSpawnPokemonEvent;
import com.cobblemon.mod.common.api.events.entity.PokemonEntityLoadEvent;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.api.events.fishing.BobberSpawnPokemonEvent;
import com.cobblemon.mod.common.api.events.habitats.HabitatSpawnActivatedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent;
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Observer-only research mod.
 *
 * It never cancels or mutates Cobblemon events. Its only purpose is to
 * produce runtime evidence for the future Zian Utilities Generation Control.
 */
@Mod(ZianSpawnObserver.MOD_ID)
public final class ZianSpawnObserver {
    public static final String MOD_ID = "zian_spawn_observer";
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianSpawnObserver");

    private final Map<Integer, String> sourceBySpawnAction = new ConcurrentHashMap<>();
    private final Map<UUID, String> finalSpawnByEntity = new ConcurrentHashMap<>();

    public ZianSpawnObserver() {
        subscribe();
        log("BOOT", "observer=ready mode=READ_ONLY");
    }

    private void subscribe() {
        CobblemonEvents.ENTITY_SPAWN.subscribe(
                Priority.LOWEST,
                (Consumer<SpawnEvent<?>>) this::onEntitySpawn
        );

        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(
                Priority.LOWEST,
                (Consumer<SpawnEvent<PokemonEntity>>) this::onPokemonEntitySpawn
        );

        CobblemonEvents.BOBBER_SPAWN_POKEMON_PRE.subscribe(
                Priority.LOWEST,
                (Consumer<BobberSpawnPokemonEvent.Pre>) this::onFishingPre
        );

        CobblemonEvents.BOBBER_SPAWN_POKEMON_POST.subscribe(
                Priority.LOWEST,
                (Consumer<BobberSpawnPokemonEvent.Post>) this::onFishingPost
        );

        CobblemonEvents.POKE_SNACK_SPAWN_POKEMON_PRE.subscribe(
                Priority.LOWEST,
                (Consumer<PokeSnackSpawnPokemonEvent.Pre>) this::onPokeSnackPre
        );

        CobblemonEvents.POKE_SNACK_SPAWN_POKEMON_POST.subscribe(
                Priority.LOWEST,
                (Consumer<PokeSnackSpawnPokemonEvent.Post>) this::onPokeSnackPost
        );

        CobblemonEvents.HABITAT_SPAWN_ACTIVATED.subscribe(
                Priority.LOWEST,
                (Consumer<HabitatSpawnActivatedEvent>) this::onHabitatActivated
        );

        CobblemonEvents.POKEMON_SENT_POST.subscribe(
                Priority.LOWEST,
                (Consumer<PokemonSentEvent.Post>) this::onPokemonSentPost
        );

        CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe(
                Priority.LOWEST,
                (Consumer<PokemonEntityLoadEvent>) this::onPokemonEntityLoad
        );
    }

    private void onEntitySpawn(SpawnEvent<?> event) {
        if (!(event.getEntity() instanceof PokemonEntity pokemonEntity)) {
            return;
        }

        log("ENTITY_SPAWN",
                commonSpawnFields(event, pokemonEntity)
                        + " canceled=" + event.isCanceled());
    }

    private void onPokemonEntitySpawn(SpawnEvent<PokemonEntity> event) {
        PokemonEntity entity = event.getEntity();
        String summary = commonSpawnFields(event, entity)
                + " canceled=" + event.isCanceled();

        finalSpawnByEntity.put(entity.getUUID(), summary);
        log("POKEMON_ENTITY_SPAWN", summary);
    }

    private void onFishingPre(BobberSpawnPokemonEvent.Pre event) {
        int actionId = actionId(event.getSpawnAction());
        sourceBySpawnAction.put(actionId, "FISHING");

        log("FISHING_PRE",
                "action=" + actionId
                        + " actionClass=" + className(event.getSpawnAction())
                        + " detail=" + className(event.getSpawnAction().getDetail())
                        + " canceled=" + event.isCanceled()
                        + " bobber=" + event.getBobber().getUUID());
    }

    private void onFishingPost(BobberSpawnPokemonEvent.Post event) {
        PokemonEntity pokemon = event.getPokemon();
        int actionId = actionId(event.getSpawnAction());

        log("FISHING_POST",
                "action=" + actionId
                        + " sourceHint=" + sourceBySpawnAction.remove(actionId)
                        + " uuid=" + pokemon.getUUID()
                        + " species=" + species(pokemon)
                        + " finalSpawnSeen=" + finalSpawnByEntity.containsKey(pokemon.getUUID()));
    }

    private void onPokeSnackPre(PokeSnackSpawnPokemonEvent.Pre event) {
        int actionId = actionId(event.getSpawnAction());
        sourceBySpawnAction.put(actionId, "POKE_SNACK");

        log("POKE_SNACK_PRE",
                "action=" + actionId
                        + " actionClass=" + className(event.getSpawnAction())
                        + " detail=" + className(event.getSpawnAction().getDetail())
                        + " canceled=" + event.isCanceled()
                        + " blockPos=" + event.getPokeSnackBlockEntity().getBlockPos());
    }

    private void onPokeSnackPost(PokeSnackSpawnPokemonEvent.Post event) {
        PokemonEntity pokemon = event.getPokemonEntity();
        int actionId = actionId(event.getSpawnAction());

        log("POKE_SNACK_POST",
                "action=" + actionId
                        + " sourceHint=" + sourceBySpawnAction.remove(actionId)
                        + " uuid=" + pokemon.getUUID()
                        + " species=" + species(pokemon)
                        + " finalSpawnSeen=" + finalSpawnByEntity.containsKey(pokemon.getUUID()));
    }

    private void onHabitatActivated(HabitatSpawnActivatedEvent event) {
        log("HABITAT_ACTIVATED",
                "blockPos=" + event.getHabitatBlockEntity().getBlockPos()
                        + " spawner=" + className(event.getSpawner())
                        + " cause=" + className(event.getCause())
                        + " maxSpawns=" + event.getMaxSpawns()
                        + " canceled=" + event.isCanceled());
    }

    private void onPokemonSentPost(PokemonSentEvent.Post event) {
        PokemonEntity entity = event.getPokemonEntity();

        log("POKEMON_SENT_POST",
                "uuid=" + entity.getUUID()
                        + " species=" + species(entity)
                        + " dimension=" + event.getLevel().dimension().location()
                        + " finalSpawnSeen=" + finalSpawnByEntity.containsKey(entity.getUUID()));
    }

    private void onPokemonEntityLoad(PokemonEntityLoadEvent event) {
        PokemonEntity entity = event.getPokemonEntity();

        log("POKEMON_ENTITY_LOAD",
                "uuid=" + entity.getUUID()
                        + " species=" + species(entity)
                        + " canceled=" + event.isCanceled()
                        + " finalSpawnSeen=" + finalSpawnByEntity.containsKey(entity.getUUID()));
    }

    private String commonSpawnFields(SpawnEvent<?> event, PokemonEntity entity) {
        String owner = entity.getOwner() == null
                ? "null"
                : entity.getOwner().getClass().getName() + ":" + entity.getOwner().getUUID();

        return "uuid=" + entity.getUUID()
                + " species=" + species(entity)
                + " labels=" + entity.getPokemon().getSpecies().getLabels()
                + " dimension=" + entity.level().dimension().location()
                + " spawner=" + className(event.getSpawner())
                + " cause=" + className(event.getCause())
                + " owner=" + owner;
    }

    private static int actionId(SpawnAction<?> action) {
        return System.identityHashCode(action);
    }

    private static String species(PokemonEntity entity) {
        try {
            return entity.getPokemon().getSpecies().getResourceIdentifier().toString();
        } catch (Exception error) {
            return "<species-error:" + error.getClass().getSimpleName() + ">";
        }
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static void log(String event, String fields) {
        LOGGER.info("[ZIAN-OBS] event={} {}", event, fields);
    }
}
