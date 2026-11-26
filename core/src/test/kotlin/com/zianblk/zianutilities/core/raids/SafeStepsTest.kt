package com.zianblk.zianutilities.core.raids

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SafeStepsTest {
    @Test fun `a failing step does not skip the next ones and all failures are reported`() {
        val ran = mutableListOf<String>()
        val failures = SafeSteps.runAll(
            SafeSteps.Step { ran += "boss"; error("boss gone") },
            SafeSteps.Step { ran += "journal" },
            SafeSteps.Step { ran += "close"; throw IllegalArgumentException("close failed") },
            SafeSteps.Step { ran += "notify" },
        )
        assertEquals(listOf("boss", "journal", "close", "notify"), ran)
        assertEquals(2, failures.size)
        assertTrue(failures[0] is IllegalStateException && failures[1] is IllegalArgumentException)
    }

    @Test fun `even an Error in a step is contained`() {
        val failures = SafeSteps.runAll(SafeSteps.Step { throw LinkageError("x") }, SafeSteps.Step { })
        assertEquals(1, failures.size)
    }

    @Test fun `guard sends the failure to the handler, which is where the duel is aborted with refund`() {
        var aborted: Throwable? = null
        assertFalse(SafeSteps.guard({ throw IllegalStateException("tick blew up") }) { aborted = it })
        assertEquals("tick blew up", aborted!!.message)
        assertTrue(SafeSteps.guard({ }) { fail("not called") })
    }

    @Test fun `a handler that throws cannot escape the guard`() {
        assertFalse(SafeSteps.guard({ error("a") }) { error("b") })
    }

    @Test fun `lethal rule is shared by the engine and the grace window`() {
        assertTrue(DuelEngine.isLethal(10.0, 9.0)); assertTrue(DuelEngine.isLethal(10.0, 12.0)); assertFalse(DuelEngine.isLethal(10.0, 8.9))
    }
}
