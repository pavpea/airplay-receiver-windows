package io.github.qiuspace.airplay.app;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Serializes restart work while retaining only the most recently requested target.
 *
 * <p>The coordinator deliberately keeps the desired target after a failure. A later
 * request or {@link #resume()} can therefore retry it instead of silently treating
 * the failed target as applied.</p>
 */
final class LatestRestartCoordinator<T> {

    @FunctionalInterface
    interface RestartAction<T> {
        void restart(T target) throws Exception;
    }

    private final Executor executor;
    private final BooleanSupplier blocked;
    private final RestartAction<T> restartAction;
    private final Consumer<Throwable> failureHandler;

    private T desiredTarget;
    private T appliedTarget;
    private boolean appliedTargetKnown = true;
    private boolean taskScheduled;
    private long forceGeneration;
    private long completedForceGeneration;

    LatestRestartCoordinator(Executor executor,
                             BooleanSupplier blocked,
                             T initialTarget,
                             RestartAction<T> restartAction,
                             Consumer<Throwable> failureHandler) {
        this.executor = Objects.requireNonNull(executor);
        this.blocked = Objects.requireNonNull(blocked);
        desiredTarget = Objects.requireNonNull(initialTarget);
        appliedTarget = initialTarget;
        this.restartAction = Objects.requireNonNull(restartAction);
        this.failureHandler = Objects.requireNonNull(failureHandler);
    }

    /** Updates the desired target and schedules at most one drain task. */
    void request(T target) {
        T requestedTarget = Objects.requireNonNull(target);
        // Publish the desired target before observing the external session state.
        // If a session-stop resume races with that observation, it can now see and
        // schedule this target instead of incorrectly concluding that work is done.
        synchronized (this) {
            desiredTarget = requestedTarget;
        }
        boolean blockedNow = blocked.getAsBoolean();
        boolean submit;
        synchronized (this) {
            submit = prepareSchedule(blockedNow);
        }
        if (submit) {
            submitDrain();
        }
    }

    /** Requests an explicit restart even when the target configuration is unchanged. */
    void force(T target) {
        T requestedTarget = Objects.requireNonNull(target);
        synchronized (this) {
            desiredTarget = requestedTarget;
            forceGeneration++;
        }
        boolean blockedNow = blocked.getAsBoolean();
        boolean submit;
        synchronized (this) {
            submit = prepareSchedule(blockedNow);
        }
        if (submit) {
            submitDrain();
        }
    }

    /** Rechecks deferred or failed work, for example after an active session ends. */
    void resume() {
        boolean blockedNow = blocked.getAsBoolean();
        boolean submit;
        synchronized (this) {
            submit = prepareSchedule(blockedNow);
        }
        if (submit) {
            submitDrain();
        }
    }

    synchronized boolean taskScheduled() {
        return taskScheduled;
    }

    private boolean prepareSchedule(boolean blockedNow) {
        if (taskScheduled || blockedNow || isSatisfied()) {
            return false;
        }
        taskScheduled = true;
        return true;
    }

    private void submitDrain() {
        try {
            executor.execute(this::drain);
        } catch (RuntimeException error) {
            synchronized (this) {
                taskScheduled = false;
            }
            failureHandler.accept(error);
        }
    }

    private void drain() {
        while (true) {
            boolean blockedNow = blocked.getAsBoolean();
            T target;
            long targetForceGeneration;
            synchronized (this) {
                if (blockedNow) {
                    taskScheduled = false;
                    return;
                }
                if (isSatisfied()) {
                    taskScheduled = false;
                    return;
                }
                target = desiredTarget;
                targetForceGeneration = forceGeneration;
            }

            try {
                restartAction.restart(target);
            } catch (Exception error) {
                failureHandler.accept(error);
                synchronized (this) {
                    // AirPlayServer.restart() replaces its configuration before the
                    // subsequent start can fail. The previously applied target can no
                    // longer be trusted after any failure.
                    appliedTargetKnown = false;
                    boolean superseded = !Objects.equals(desiredTarget, target)
                            || forceGeneration != targetForceGeneration;
                    if (superseded) {
                        continue;
                    }
                    taskScheduled = false;
                    return;
                }
            }

            synchronized (this) {
                appliedTarget = target;
                appliedTargetKnown = true;
                completedForceGeneration = targetForceGeneration;
                // Loop in the same worker task. Changes made during the restart are
                // represented by one latest target instead of one queued task each.
                if (isSatisfied()) {
                    taskScheduled = false;
                    return;
                }
            }
        }
    }

    private boolean isSatisfied() {
        return appliedTargetKnown
                && Objects.equals(desiredTarget, appliedTarget)
                && forceGeneration == completedForceGeneration;
    }
}
