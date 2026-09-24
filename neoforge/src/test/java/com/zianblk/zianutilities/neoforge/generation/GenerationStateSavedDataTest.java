package com.zianblk.zianutilities.neoforge.generation;

import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.generation.GenerationState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerationStateSavedDataTest {
    @Test
    void roundTripPreservesEnabledGenerations() {
        GenerationStateSavedData source = new GenerationStateSavedData(
            new GenerationState(Set.of(
                Generation.GEN_1,
                Generation.GEN_4,
                Generation.GEN_9
            )),
            Set.of()
        );

        CompoundTag encoded = source.encode(new CompoundTag());
        GenerationStateSavedData decoded = GenerationStateSavedData.decode(encoded);

        assertEquals(source.state(), decoded.state());
        assertEquals(Set.of(), decoded.unknownGenerationIds());
        assertEquals(
            GenerationStateSavedData.CURRENT_SCHEMA_VERSION,
            encoded.getInt(GenerationStateSavedData.KEY_SCHEMA_VERSION)
        );
    }

    @Test
    void emptyStateRoundTrips() {
        GenerationStateSavedData source = new GenerationStateSavedData();

        GenerationStateSavedData decoded =
            GenerationStateSavedData.decode(source.encode(new CompoundTag()));

        assertEquals(GenerationState.Companion.none(), decoded.state());
    }

    @Test
    void duplicateGenerationIdsCollapseSafely() {
        CompoundTag tag = tagWithIds(
            GenerationStateSavedData.CURRENT_SCHEMA_VERSION,
            "gen1",
            "gen1",
            "gen2"
        );

        GenerationStateSavedData decoded = GenerationStateSavedData.decode(tag);

        assertEquals(
            new GenerationState(Set.of(Generation.GEN_1, Generation.GEN_2)),
            decoded.state()
        );
    }

    @Test
    void unknownGenerationIdsArePreservedForForwardCompatibility() {
        CompoundTag tag = tagWithIds(
            GenerationStateSavedData.CURRENT_SCHEMA_VERSION,
            "gen1",
            "gen10",
            "addon_custom_gen"
        );

        GenerationStateSavedData decoded = GenerationStateSavedData.decode(tag);
        CompoundTag reencoded = decoded.encode(new CompoundTag());

        assertEquals(
            Set.of("gen10", "addon_custom_gen"),
            decoded.unknownGenerationIds()
        );

        GenerationStateSavedData secondDecode =
            GenerationStateSavedData.decode(reencoded);

        assertEquals(
            decoded.unknownGenerationIds(),
            secondDecode.unknownGenerationIds()
        );
        assertEquals(decoded.state(), secondDecode.state());
    }

    @Test
    void missingSchemaUsesExplicitLegacyV0Migration() {
        CompoundTag legacy = new CompoundTag();
        ListTag enabled = new ListTag();
        enabled.add(StringTag.valueOf("gen3"));
        enabled.add(StringTag.valueOf("gen8"));
        legacy.put(GenerationStateSavedData.KEY_ENABLED_GENERATIONS, enabled);

        GenerationStateSavedData decoded =
            GenerationStateSavedData.decode(legacy);

        assertEquals(
            new GenerationState(Set.of(Generation.GEN_3, Generation.GEN_8)),
            decoded.state()
        );
    }

    @Test
    void futureSchemaFailsInsteadOfSilentlyDiscardingData() {
        CompoundTag tag = tagWithIds(
            GenerationStateSavedData.CURRENT_SCHEMA_VERSION + 1,
            "gen1"
        );

        assertThrows(
            IllegalStateException.class,
            () -> GenerationStateSavedData.decode(tag)
        );
    }

    @Test
    void encodedGenerationOrderIsDeterministic() {
        LinkedHashSet<Generation> input = new LinkedHashSet<>();
        input.add(Generation.GEN_9);
        input.add(Generation.GEN_1);
        input.add(Generation.GEN_4);

        GenerationStateSavedData source = new GenerationStateSavedData(
            new GenerationState(input),
            Set.of()
        );

        CompoundTag first = source.encode(new CompoundTag());
        CompoundTag second = source.encode(new CompoundTag());

        assertEquals(first, second);
    }

    private static CompoundTag tagWithIds(int schemaVersion, String... ids) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(GenerationStateSavedData.KEY_SCHEMA_VERSION, schemaVersion);

        ListTag enabled = new ListTag();
        for (String id : ids) {
            enabled.add(StringTag.valueOf(id));
        }

        tag.put(GenerationStateSavedData.KEY_ENABLED_GENERATIONS, enabled);
        return tag;
    }
}
