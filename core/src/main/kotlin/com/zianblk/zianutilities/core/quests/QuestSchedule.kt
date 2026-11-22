package com.zianblk.zianutilities.core.quests

import java.time.ZoneId

/** Zona horaria única de las misiones (ventanas de 3 h, semanas y textos de la GUI). */
object QuestSchedule {
    // shortcut: fija en código; cambiarla invalida las misiones guardadas (validan su ventana con esta zona). Hacerla configurable exige migrar o descartar esos datos.
    @JvmField val ZONE: ZoneId = ZoneId.of("America/Guayaquil")
}
