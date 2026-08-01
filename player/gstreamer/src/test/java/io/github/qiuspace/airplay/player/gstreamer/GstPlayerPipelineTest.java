package io.github.qiuspace.airplay.player.gstreamer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GstPlayerPipelineTest {

    @Test
    void compressedVideoQueueAppliesBackpressureInsteadOfDroppingReferenceFrames() {
        String pipeline = GstPlayer.videoPipelineDescription();

        assertTrue(pipeline.contains("appsrc name=video-source"));
        assertTrue(pipeline.contains("block=true"));
        assertFalse(pipeline.contains("leaky-type"));
        assertTrue(pipeline.contains(
                "appsink name=video-sink sync=false max-buffers=2 drop=true"));
    }
}
