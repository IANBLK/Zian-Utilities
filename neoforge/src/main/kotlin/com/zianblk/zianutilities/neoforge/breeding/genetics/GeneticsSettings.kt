package com.zianblk.zianutilities.neoforge.breeding.genetics
/** Pinned safe defaults. No mutation of parents and no Ditto×Ditto random-species lottery. */
object GeneticsSettings {
 @JvmField var dittoAndDittoRandomEgg = false
 @JvmField var hiddenAbilitiesEnabled = true
 @JvmField var forcedAbilitiesEnabled = false
 @JvmField var inheritAlpha = false
 @JvmField var shinyMethod = mutableMapOf("masuda" to 4.0F, "crystal" to 1.0F, "always" to 1.0F)
 @JvmField var inheritedFeatures = listOf("bagworm_cloak","color","dance_style","fish_stripes","striped","magikarp_jump","mooshtank","region_bias","alolan","galarian","hisuian","paldean","bull_breed","tatsugiri_texture","whiscash_nero","wooper_heart","wolf_form")
}
