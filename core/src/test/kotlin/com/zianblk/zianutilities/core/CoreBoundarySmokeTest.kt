package com.zianblk.zianutilities.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreBoundarySmokeTest {
    @Test
    fun `core marker exposes bootstrap api version`() {
        assertEquals(1, ZianUtilitiesCore.API_VERSION)
    }
}
