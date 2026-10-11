package com.zianblk.zianutilities.neoforge.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class PlaneMiningTest {
    @Test
    void eachFaceUsesOnlyThePerpendicularEightNeighbors() {
        BlockPos center = new BlockPos(100, 80, -30);
        for (Direction face : Direction.values()) {
            var positions = PlaneMining.plane(center, face);
            assertEquals(8, positions.size());
            assertEquals(8, positions.stream().distinct().count());
            assertFalse(positions.contains(center));
            assertTrue(positions.stream().allMatch(pos -> switch (face.getAxis()) {
                case X -> pos.getX() == center.getX();
                case Y -> pos.getY() == center.getY();
                case Z -> pos.getZ() == center.getZ();
            }));
        }
    }
}
