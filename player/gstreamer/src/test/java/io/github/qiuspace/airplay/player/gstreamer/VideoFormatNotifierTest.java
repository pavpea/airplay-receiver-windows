package io.github.qiuspace.airplay.player.gstreamer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoFormatNotifierTest {

    @Test
    void announcesFormatBeforeTheMatchingFrameCanBecomeReady() {
        VideoFormatNotifier notifier = new VideoFormatNotifier();
        List<String> events = new ArrayList<>();
        GstPlayerListener listener = new GstPlayerListener() {
            @Override
            public void onVideoFormatChanged(int width, int height) {
                events.add("format:" + width + "x" + height);
            }

            @Override
            public void onVideoFrameReady(int width, int height) {
                events.add("frame:" + width + "x" + height);
            }
        };

        notifier.beforeBuffer(1179, 2556, listener);
        notifier.frameReady(1179, 2556, listener);

        assertEquals(List.of(
                "format:1179x2556",
                "frame:1179x2556"), events);
    }

    @Test
    void repeatedBuffersDoNotRepeatTheFormatNotification() {
        VideoFormatNotifier notifier = new VideoFormatNotifier();
        List<String> events = new ArrayList<>();
        GstPlayerListener listener = new GstPlayerListener() {
            @Override
            public void onVideoFormatChanged(int width, int height) {
                events.add(width + "x" + height);
            }
        };

        notifier.beforeBuffer(1920, 1080, listener);
        notifier.beforeBuffer(1920, 1080, listener);
        notifier.beforeBuffer(1179, 2556, listener);

        assertEquals(List.of("1920x1080", "1179x2556"), events);
    }

    @Test
    void failedUiPreparationIsRetriedByTheNextBuffer() {
        VideoFormatNotifier notifier = new VideoFormatNotifier();
        AtomicInteger attempts = new AtomicInteger();
        GstPlayerListener listener = new GstPlayerListener() {
            @Override
            public void onVideoFormatChanged(int width, int height) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("simulated layout failure");
                }
            }
        };

        try {
            notifier.beforeBuffer(1920, 1080, listener);
        } catch (IllegalStateException ignored) {
            // The streaming callback will log this failure and allow the buffer through.
        }
        notifier.beforeBuffer(1920, 1080, listener);

        assertEquals(2, attempts.get());
    }
}
