package com.zianblk.zianutilities.core.generation

sealed interface SpawnDecision {
    data object Allow : SpawnDecision

    data class Deny(val reason: String) : SpawnDecision {
        init {
            require(reason.isNotBlank()) { "reason must not be blank" }
        }
    }
}
