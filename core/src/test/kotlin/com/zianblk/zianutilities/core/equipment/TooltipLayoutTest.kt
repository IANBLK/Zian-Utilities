package com.zianblk.zianutilities.core.equipment

import com.zianblk.zianutilities.core.equipment.TooltipLayout.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TooltipLayoutTest {
    @Test fun `plegado muestra la habilidad y la pista de Mayus`() {
        val lines = TooltipLayout.lines(TooltipLayout.Spec("frost_axe", 4, 30), false)
        assertEquals(listOf(Kind.ABILITY, Kind.HINT), lines.map { it.kind() })
        assertEquals("tooltip.zianutilities.frost_axe.ability", lines[0].key())
        assertEquals("tooltip.zianutilities.shift", lines[1].key())
    }

    @Test fun `expandido de una activa anade efecto y espera`() {
        val lines = TooltipLayout.lines(TooltipLayout.Spec("molten_mace", 4, 30), true)
        assertEquals(listOf(Kind.ABILITY, Kind.DETAIL, Kind.COOLDOWN), lines.map { it.kind() })
        assertEquals(4, lines[1].arg())
        assertEquals(30, lines[2].arg())
    }

    @Test fun `expandido de una pasiva no muestra espera ni pista`() {
        val lines = TooltipLayout.lines(TooltipLayout.Spec("poison_dagger", 4, 0), true)
        assertEquals(listOf(Kind.ABILITY, Kind.DETAIL), lines.map { it.kind() })
    }
}
