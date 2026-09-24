package com.zianblk.zianutilities.neoforge.generation;

import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

final class SpawnSourceClassifier {
    enum ControlledSource {
        NATURAL,
        FISHING
    }

    ControlledSource classify(SpawnEvent<PokemonEntity> event) {
        if (event == null || event.getSpawnablePosition() == null) {
            return null;
        }

        Object cause = event.getSpawnablePosition().getCause();
        if (cause instanceof FishingSpawnCause) {
            return ControlledSource.FISHING;
        }

        Object spawner = event.getSpawnablePosition().getSpawner();
        if (spawner instanceof PlayerSpawner) {
            return ControlledSource.NATURAL;
        }

        return null;
    }
}
