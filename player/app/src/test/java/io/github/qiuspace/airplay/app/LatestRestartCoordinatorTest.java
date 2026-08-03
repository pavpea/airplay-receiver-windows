package io.github.qiuspace.airplay.app;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LatestRestartCoordinatorTest {

    @Test
    void keepsOnlyLatestTargetBeforeQueuedTaskRuns() {
        ManualExecutor executor = new ManualExecutor();
        List<String> restarted = new ArrayList<>();
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor, () -> false, "initial", restarted::add, ignored -> { });

        coordinator.request("one");
        coordinator.request("two");
        coordinator.request("latest");

        assertThat(executor.size()).isOne();
        executor.runNext();

        assertThat(restarted).containsExactly("latest");
        assertThat(executor.size()).isZero();
        assertThat(coordinator.taskScheduled()).isFalse();
    }

    @Test
    void coalescesChangesDuringRestartIntoOneLatestFollowUp() {
        ManualExecutor executor = new ManualExecutor();
        List<String> restarted = new ArrayList<>();
        AtomicReference<LatestRestartCoordinator<String>> reference = new AtomicReference<>();
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor, () -> false, "initial", target -> {
                    restarted.add(target);
                    if (target.equals("one")) {
                        reference.get().request("two");
                        reference.get().request("three");
                        reference.get().request("latest");
                    }
                }, ignored -> { });
        reference.set(coordinator);

        coordinator.request("one");
        executor.runNext();

        assertThat(restarted).containsExactly("one", "latest");
        assertThat(executor.size()).isZero();
    }

    @Test
    void cancelsQueuedRestartWhenLatestTargetMatchesAppliedTarget() {
        ManualExecutor executor = new ManualExecutor();
        List<String> restarted = new ArrayList<>();
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor, () -> false, "initial", restarted::add, ignored -> { });

        coordinator.request("temporary");
        coordinator.request("initial");
        executor.runNext();

        assertThat(restarted).isEmpty();
        assertThat(coordinator.taskScheduled()).isFalse();
    }

    @Test
    void defersAllSessionChangesAndRunsLatestTargetOnceAfterResume() {
        ManualExecutor executor = new ManualExecutor();
        AtomicBoolean sessionActive = new AtomicBoolean(true);
        List<String> restarted = new ArrayList<>();
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor, sessionActive::get, "initial", restarted::add, ignored -> { });

        coordinator.request("one");
        coordinator.request("two");
        coordinator.request("latest");
        assertThat(executor.size()).isZero();

        sessionActive.set(false);
        coordinator.resume();
        coordinator.resume();

        assertThat(executor.size()).isOne();
        executor.runNext();
        assertThat(restarted).containsExactly("latest");
    }

    @Test
    void attemptsNewerTargetEvenWhenEarlierRestartFails() {
        ManualExecutor executor = new ManualExecutor();
        List<String> attempts = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        AtomicReference<LatestRestartCoordinator<String>> reference = new AtomicReference<>();
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor, () -> false, "initial", target -> {
                    attempts.add(target);
                    if (target.equals("failing")) {
                        reference.get().request("latest");
                        throw new IllegalStateException("expected");
                    }
                }, failures::add);
        reference.set(coordinator);

        coordinator.request("failing");
        executor.runNext();

        assertThat(attempts).containsExactly("failing", "latest");
        assertThat(failures).hasSize(1);
        assertThat(coordinator.taskScheduled()).isFalse();
    }

    @Test
    void restartsPreviousTargetWhenFailedRestartMadeServerStateUnknown() {
        ManualExecutor executor = new ManualExecutor();
        List<String> attempts = new ArrayList<>();
        AtomicReference<LatestRestartCoordinator<String>> reference = new AtomicReference<>();
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor, () -> false, "initial", target -> {
                    attempts.add(target);
                    if (target.equals("failing")) {
                        reference.get().request("initial");
                        throw new IllegalStateException("expected");
                    }
                }, ignored -> { });
        reference.set(coordinator);

        coordinator.request("failing");
        executor.runNext();

        assertThat(attempts).containsExactly("failing", "initial");
        assertThat(coordinator.taskScheduled()).isFalse();
    }

    @Test
    void retainsFailedLatestTargetForRetry() {
        ManualExecutor executor = new ManualExecutor();
        AtomicInteger attempts = new AtomicInteger();
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor, () -> false, "initial", target -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalStateException("expected");
                    }
                }, ignored -> { });

        coordinator.request("latest");
        executor.runNext();
        assertThat(coordinator.taskScheduled()).isFalse();

        coordinator.resume();
        assertThat(executor.size()).isOne();
        executor.runNext();

        assertThat(attempts).hasValue(2);
        coordinator.resume();
        assertThat(executor.size()).isZero();
    }

    @Test
    void forceRequestsStillShareTheSingleDrainTask() {
        ManualExecutor executor = new ManualExecutor();
        List<String> restarted = new ArrayList<>();
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor, () -> false, "initial", restarted::add, ignored -> { });

        coordinator.force("initial");
        coordinator.force("initial");

        assertThat(executor.size()).isOne();
        executor.runNext();
        assertThat(restarted).containsExactly("initial");
    }

    @Test
    void neverQueriesBlockedStateWhileHoldingMonitorOnRequestResumeForceOrSuccessfulDrain() {
        ManualExecutor executor = new ManualExecutor();
        AtomicReference<LatestRestartCoordinator<String>> reference = new AtomicReference<>();
        AtomicInteger blockerCalls = new AtomicInteger();
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor,
                unlockedBlocker(reference, blockerCalls, false),
                "initial",
                ignored -> { },
                ignored -> { });
        reference.set(coordinator);

        coordinator.request("updated");
        executor.runNext();
        coordinator.resume();
        coordinator.force("updated");
        executor.runNext();

        assertThat(blockerCalls).hasValue(5);
    }

    @Test
    void neverQueriesBlockedStateWhileHoldingMonitorAfterFailedDrain() {
        ManualExecutor executor = new ManualExecutor();
        AtomicReference<LatestRestartCoordinator<String>> reference = new AtomicReference<>();
        AtomicInteger blockerCalls = new AtomicInteger();
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor,
                unlockedBlocker(reference, blockerCalls, false),
                "initial",
                target -> {
                    if (target.equals("failing")) {
                        reference.get().request("latest");
                        throw new IllegalStateException("expected");
                    }
                },
                ignored -> { });
        reference.set(coordinator);

        coordinator.request("failing");
        executor.runNext();

        // request(failing), drain(failing), request(latest), drain(latest)
        assertThat(blockerCalls).hasValue(4);
        assertThat(coordinator.taskScheduled()).isFalse();
    }

    @Test
    void sessionResumeCannotBeLostWhileRequestIsStillReadingBlockedState() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        List<String> restarted = new ArrayList<>();
        CountDownLatch requestReadingBlocked = new CountDownLatch(1);
        CountDownLatch finishRequestRead = new CountDownLatch(1);
        AtomicReference<Thread> requestThread = new AtomicReference<>();
        AtomicBoolean sessionActive = new AtomicBoolean(true);
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor,
                () -> {
                    if (Thread.currentThread() == requestThread.get()) {
                        requestReadingBlocked.countDown();
                        await(finishRequestRead);
                        // Return the stale value deliberately. resume() must already
                        // have observed the published desired target and queued it.
                        return true;
                    }
                    return sessionActive.get();
                },
                "initial",
                restarted::add,
                ignored -> { });

        Thread requester = new Thread(() -> coordinator.request("latest"), "settings-request");
        requestThread.set(requester);
        requester.start();
        assertThat(requestReadingBlocked.await(5, TimeUnit.SECONDS)).isTrue();

        sessionActive.set(false);
        coordinator.resume();
        assertThat(executor.size()).isOne();

        finishRequestRead.countDown();
        requester.join(5_000);
        assertThat(requester.isAlive()).isFalse();
        executor.runNext();

        assertThat(restarted).containsExactly("latest");
        assertThat(coordinator.taskScheduled()).isFalse();
    }

    @Test
    void stoppedCallbackProducedByRestartDoesNotQueueAnotherDrain() {
        ManualExecutor executor = new ManualExecutor();
        List<String> restarted = new ArrayList<>();
        AtomicReference<LatestRestartCoordinator<String>> reference = new AtomicReference<>();
        LatestRestartCoordinator<String> coordinator = coordinator(
                executor,
                () -> false,
                "initial",
                target -> {
                    restarted.add(target);
                    // AirPlayServer.restart() emits STOPPED synchronously while this
                    // drain task is still the sole scheduled/running restart.
                    reference.get().resume();
                },
                ignored -> { });
        reference.set(coordinator);

        coordinator.request("updated");
        assertThat(executor.size()).isOne();
        executor.runNext();

        assertThat(restarted).containsExactly("updated");
        assertThat(executor.size()).isZero();
        assertThat(coordinator.taskScheduled()).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for deterministic race checkpoint");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for deterministic race checkpoint", error);
        }
    }

    private static java.util.function.BooleanSupplier unlockedBlocker(
            AtomicReference<? extends LatestRestartCoordinator<?>> coordinator,
            AtomicInteger calls,
            boolean result) {
        return () -> {
            LatestRestartCoordinator<?> current = coordinator.get();
            assertThat(current).isNotNull();
            assertThat(Thread.holdsLock(current)).isFalse();
            calls.incrementAndGet();
            return result;
        };
    }

    private static <T> LatestRestartCoordinator<T> coordinator(
            ManualExecutor executor,
            java.util.function.BooleanSupplier blocked,
            T initial,
            LatestRestartCoordinator.RestartAction<T> action,
            java.util.function.Consumer<Throwable> failureHandler) {
        return new LatestRestartCoordinator<>(executor, blocked, initial, action, failureHandler);
    }

    private static final class ManualExecutor implements java.util.concurrent.Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable command) {
            tasks.add(command);
        }

        synchronized int size() {
            return tasks.size();
        }

        void runNext() {
            Runnable task;
            synchronized (this) {
                task = tasks.remove();
            }
            task.run();
        }
    }
}
