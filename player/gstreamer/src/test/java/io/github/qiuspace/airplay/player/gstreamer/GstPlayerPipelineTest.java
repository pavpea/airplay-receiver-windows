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

    @Test
    void hardwarePipelineKeepsTheBoundedQueueAndDownloadsOnlyAfterGpuDecode() {
        String pipeline = GstPlayer.videoPipelineDescription(true);

        assertTrue(pipeline.contains("h264parse ! d3d11h264dec ! d3d11convert ! d3d11download ! videoconvert"));
        assertTrue(pipeline.contains("max-bytes="));
        assertTrue(pipeline.contains("block=true"));
        assertTrue(pipeline.contains("appsink name=video-sink sync=false max-buffers=2 drop=true"));
    }
}
