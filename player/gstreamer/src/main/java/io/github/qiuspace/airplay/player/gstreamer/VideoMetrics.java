package io.github.qiuspace.airplay.player.gstreamer;

import java.util.concurrent.atomic.LongAdder;

/** Low-overhead counters used to diagnose video backpressure and leaks. */
final class VideoMetrics {

    private final LongAdder compressedBuffers = new LongAdder();
    private final LongAdder compressedBytes = new LongAdder();
    private final LongAdder nativeBuffers = new LongAdder();
    private final LongAdder nativeBytes = new LongAdder();
    private final LongAdder rejectedBuffers = new LongAdder();
    private final LongAdder formatChanges = new LongAdder();
    private final LongAdder renderedFrames = new LongAdder();

    void compressed(int bytes) {
        compressedBuffers.increment();
        compressedBytes.add(bytes);
    }

    void nativeCopy(int bytes) {
        nativeBuffers.increment();
        nativeBytes.add(bytes);
    }

    void rejected() {
        rejectedBuffers.increment();
    }

    void formatChanged() {
        formatChanges.increment();
    }

    void rendered() {
        renderedFrames.increment();
    }

    Snapshot snapshotAndReset() {
        return new Snapshot(
                compressedBuffers.sumThenReset(),
                compressedBytes.sumThenReset(),
                nativeBuffers.sumThenReset(),
                nativeBytes.sumThenReset(),
                rejectedBuffers.sumThenReset(),
                formatChanges.sumThenReset(),
                renderedFrames.sumThenReset());
    }

    Snapshot snapshot() {
        return new Snapshot(
                compressedBuffers.sum(),
                compressedBytes.sum(),
                nativeBuffers.sum(),
                nativeBytes.sum(),
                rejectedBuffers.sum(),
                formatChanges.sum(),
                renderedFrames.sum());
    }

    record Snapshot(long compressedBuffers, long compressedBytes,
                    long nativeBuffers, long nativeBytes,
                    long rejectedBuffers, long formatChanges,
                    long renderedFrames) {
        boolean hasVideo() {
            return compressedBuffers > 0 || nativeBuffers > 0 || renderedFrames > 0;
        }
    }
}
