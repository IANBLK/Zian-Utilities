package com.zianblk.zianutilities.neoforge.generation;

import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.generation.GenerationState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class GenerationStateSavedData extends SavedData {
    static final int CURRENT_SCHEMA_VERSION = 1;
    static final String KEY_SCHEMA_VERSION = "schema_version";
    static final String KEY_ENABLED_GENERATIONS = "enabled_generations";

    public static final SavedData.Factory<GenerationStateSavedData> FACTORY =
        new SavedData.Factory<>(
            GenerationStateSavedData::new,
            GenerationStateSavedData::load
        );

    private GenerationState state;
    private final Set<String> unknownGenerationIds;

    public GenerationStateSavedData() {
        this(GenerationState.Companion.none(), Set.of());
    }

    GenerationStateSavedData(
        GenerationState state,
        Set<String> unknownGenerationIds
    ) {
        this.state = state;
        this.unknownGenerationIds = new LinkedHashSet<>(unknownGenerationIds);
    }

    public GenerationState state() {
        return state;
    }

    public Set<String> unknownGenerationIds() {
        return Collections.unmodifiableSet(unknownGenerationIds);
    }

    public void updateState(GenerationState newState) {
        if (state.equals(newState)) {
            return;
        }

        this.state = newState;
        setDirty();
    }

    public static GenerationStateSavedData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        return decode(tag);
    }

    @Override
    public CompoundTag save(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        return encode(tag);
    }

    CompoundTag encode(CompoundTag tag) {
        tag.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);

        ListTag enabled = new ListTag();
        for (Generation generation : state.getEnabled()) {
            enabled.add(StringTag.valueOf(generation.getId()));
        }

        for (String unknownId : unknownGenerationIds) {
            enabled.add(StringTag.valueOf(unknownId));
        }

        tag.put(KEY_ENABLED_GENERATIONS, enabled);
        return tag;
    }

    static GenerationStateSavedData decode(CompoundTag tag) {
        int schemaVersion = tag.contains(KEY_SCHEMA_VERSION, Tag.TAG_INT)
            ? tag.getInt(KEY_SCHEMA_VERSION)
            : 0;

        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException(
                "Unsupported generation state schema version: " + schemaVersion
            );
        }

        return switch (schemaVersion) {
            case 0 -> migrateLegacyV0(tag);
            case CURRENT_SCHEMA_VERSION -> readV1(tag);
            default -> throw new IllegalStateException(
                "Unsupported generation state schema version: " + schemaVersion
            );
        };
    }

    private static GenerationStateSavedData migrateLegacyV0(CompoundTag tag) {
        return readGenerationList(tag);
    }

    private static GenerationStateSavedData readV1(CompoundTag tag) {
        return readGenerationList(tag);
    }

    private static GenerationStateSavedData readGenerationList(CompoundTag tag) {
        Set<Generation> enabled = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();

        ListTag list = tag.getList(KEY_ENABLED_GENERATIONS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String rawId = list.getString(i);
            Generation generation = Generation.Companion.fromCanonicalId(rawId);

            if (generation != null) {
                enabled.add(generation);
            } else if (!rawId.isBlank()) {
                unknown.add(rawId);
            }
        }

        return new GenerationStateSavedData(
            new GenerationState(enabled),
            unknown
        );
    }
}
