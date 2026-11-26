package com.zianblk.zianutilities.core.raids;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Runs cleanup steps one by one so a failure in one never skips the next (restore the boss, close the log, close the raid...). */
public final class SafeSteps {
    @FunctionalInterface
    public interface Step { void run() throws Exception; }

    private SafeSteps() {}

    /** Every step runs, in order. Returns the failures, empty when all went well. */
    public static List<Throwable> runAll(Step... steps) {
        List<Throwable> failures = new ArrayList<>();
        for (Step step : steps) {
            try {
                step.run();
            } catch (Throwable failure) {
                failures.add(failure);
            }
        }
        return failures;
    }

    /** Runs one unit of work; any failure goes to onFailure (which itself must not throw out of here). Returns whether it succeeded. */
    public static boolean guard(Step step, Consumer<Throwable> onFailure) {
        try {
            step.run();
            return true;
        } catch (Throwable failure) {
            try {
                onFailure.accept(failure);
            } catch (Throwable ignored) {
                // The handler failed too: nothing else can be done here.
            }
            return false;
        }
    }
}
