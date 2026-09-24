package com.zianblk.zianutilities.neoforge.generation;

import com.zianblk.zianutilities.core.generation.GenerationState;
import com.zianblk.zianutilities.core.generation.GenerationStateStore;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;

public final class NeoForgeGenerationStateStore implements GenerationStateStore {
    static final String DATA_FILE_NAME = "zianutilities_generation_state";

    private final MinecraftServer server;

    public NeoForgeGenerationStateStore(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public GenerationState load() {
        return data().state();
    }

    @Override
    public void save(GenerationState state) {
        Objects.requireNonNull(state, "state");
        data().updateState(state);
    }

    GenerationStateSavedData data() {
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(
                GenerationStateSavedData.FACTORY,
                DATA_FILE_NAME
            );
    }
}
