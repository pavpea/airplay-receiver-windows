package io.github.qiuspace.airplay.player.gstreamer;

import io.github.qiuspace.airplay.lib.AudioStreamInfo;
import io.github.qiuspace.airplay.lib.VideoStreamInfo;
import io.github.qiuspace.airplay.server.AirPlayConsumer;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeInfo;
import org.freedesktop.gstreamer.PadProbeReturn;
import org.freedesktop.gstreamer.PadProbeType;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.Version;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.AppSrc;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public final class GstPlayer implements AirPlayConsumer, AutoCloseable {

    private static final long VIDEO_QUEUE_BYTES = 4L * 1024 * 1024;

    private final Object videoLock = new Object();
    private final Object audioLock = new Object();
    private final JPanel videoHost = new JPanel(new BorderLayout());
    private final ScheduledExecutorService maintenanceExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final VideoMetrics videoMetrics = new VideoMetrics();
    private final VideoFormatNotifier videoFormatNotifier = new VideoFormatNotifier();
    private final AudioPipeline alacAudio;
    private final AudioPipeline aacEldAudio;

    private volatile VideoPipeline video;
    private volatile GstPlayerListener listener = new GstPlayerListener() {
    };
    private volatile AudioStreamInfo.CompressionType audioCompressionType;
    private volatile double volume = 1.0;
    private volatile boolean muted;
    private volatile ScheduledFuture<?> memoryReclaim;
    private volatile ScheduledFuture<?> metricsLogger;

    public GstPlayer() {
        GstRuntime.RuntimeCheck runtime = GstRuntime.configure();
        if (!runtime.available()) {
            throw new IllegalStateException(String.join(System.lineSeparator(), runtime.problems()));
        }
        Gst.init(Version.of(1, 20), "AirPlay Receiver");

        videoHost.setBackground(Color.BLACK);
        videoHost.setOpaque(true);
        maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "airplay-player-maintenance");
            thread.setDaemon(true);
            return thread;
        });
        metricsLogger = maintenanceExecutor.scheduleAtFixedRate(this::logVideoMetrics,
                30, 30, TimeUnit.SECONDS);

        video = createVideoPipeline();
        installVideoComponent(video.component());
        alacAudio = createAudioPipeline(AudioStreamInfo.CompressionType.ALAC);
        aacEldAudio = createAudioPipeline(AudioStreamInfo.CompressionType.AAC_ELD);
        applyVolume();
    }

    public JComponent videoComponent() {
        return videoHost;
    }

    public void setListener(GstPlayerListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    public void setVolume(double volume) {
        this.volume = normalizeVolume(volume);
        applyVolume();
    }

    public double volume() {
        return volume;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        applyVolume();
    }

    public boolean muted() {
        return muted;
    }

    @Override
    public void onVideoFormat(VideoStreamInfo videoStreamInfo) {
        synchronized (videoLock) {
            if (closed.get()) {
                return;
            }
            if (video == null) {
                video = createVideoPipeline();
                installVideoComponent(video.component());
            }
            video.pipeline().play();
        }
    }

    @Override
    public void onVideo(byte[] bytes) {
        synchronized (videoLock) {
            VideoPipeline current = video;
            if (current != null && !closed.get()) {
                videoMetrics.compressed(bytes.length);
                push(current.source(), bytes);
            }
        }
    }

    @Override
    public void onVideo(ByteBuf bytes) {
        synchronized (videoLock) {
            VideoPipeline current = video;
            if (current != null && !closed.get()) {
                videoMetrics.compressed(bytes.readableBytes());
                push(current.source(), bytes);
            }
        }
    }

    @Override
    public void onVideoSrcDisconnect() {
        synchronized (videoLock) {
            releaseVideoPipeline(video);
            video = null;
            videoFormatNotifier.reset();
        }
        requestMemoryReclaim();
    }

    @Override
    public void onAudioFormat(AudioStreamInfo audioStreamInfo) {
        synchronized (audioLock) {
            if (closed.get()) {
                return;
            }
            audioCompressionType = audioStreamInfo.getCompressionType();
            if (audioCompressionType == AudioStreamInfo.CompressionType.ALAC) {
                aacEldAudio.pipeline().stop();
                alacAudio.pipeline().play();
            } else if (audioCompressionType == AudioStreamInfo.CompressionType.AAC_ELD) {
                alacAudio.pipeline().stop();
                aacEldAudio.pipeline().play();
            } else {
                throw new IllegalArgumentException("Unsupported AirPlay audio compression: " + audioCompressionType);
            }
        }
    }

    @Override
    public void onAudio(byte[] bytes) {
        synchronized (audioLock) {
            if (closed.get()) {
                return;
            }
            if (audioCompressionType == AudioStreamInfo.CompressionType.ALAC) {
                push(alacAudio.source(), bytes);
            } else if (audioCompressionType == AudioStreamInfo.CompressionType.AAC_ELD) {
                push(aacEldAudio.source(), bytes);
            }
        }
    }

    @Override
    public void onAudioSrcDisconnect() {
        synchronized (audioLock) {
            alacAudio.pipeline().stop();
            aacEldAudio.pipeline().stop();
            audioCompressionType = null;
        }
        requestMemoryReclaim();
    }

    @Override
    public void onMediaPlaylist(String playlistUri) {
        listener.onPlaybackError("Media URL casting is not supported in this release", null);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        maintenanceExecutor.shutdownNow();
        ScheduledFuture<?> logger = metricsLogger;
        if (logger != null) {
            logger.cancel(false);
        }
        synchronized (audioLock) {
            releaseAudioPipeline(alacAudio);
            releaseAudioPipeline(aacEldAudio);
            audioCompressionType = null;
        }
        synchronized (videoLock) {
            releaseVideoPipeline(video);
            video = null;
        }
    }

    private VideoPipeline createVideoPipeline() {
        boolean hardwareDecode = GstRuntime.hardwareVideoDecodeAvailable();
        Pipeline pipeline;
        try {
            pipeline = (Pipeline) Gst.parseLaunch(videoPipelineDescription(hardwareDecode));
            log.info("Video decoder selected: {}", hardwareDecode ? "D3D11 hardware" : "software");
        } catch (RuntimeException error) {
            if (!hardwareDecode) {
                throw error;
            }
            log.warn("D3D11 video decoder could not be created; falling back to software H.264", error);
            pipeline = (Pipeline) Gst.parseLaunch(videoPipelineDescription(false));
        }
        AppSrc source = (AppSrc) pipeline.getElementByName("video-source");
        configureSource(source,
                "video/x-h264,colorimetry=bt709,stream-format=(string)byte-stream,alignment=(string)au",
                VIDEO_QUEUE_BYTES, false);
        AppSink sink = (AppSink) pipeline.getElementByName("video-sink");
        Pad sinkPad = sink.getStaticPad("sink");
        Pad.PROBE formatProbe = this::inspectVideoBuffer;
        sinkPad.addProbe(PadProbeType.BUFFER, formatProbe);
        QualityVideoComponent component = createVideoComponent(sink);
        FrameReadyObserver frameObserver = installFrameReadyObserver(component);
        Bus bus = pipeline.getBus();
        attachBusHandlers(bus);
        return new VideoPipeline(
                pipeline, source, sink, sinkPad, formatProbe, bus, component, frameObserver);
    }

    static String videoPipelineDescription() {
        return videoPipelineDescription(GstRuntime.hardwareVideoDecodeAvailable());
    }

    static String videoPipelineDescription(boolean hardwareDecode) {
        String decoder = hardwareDecode
                ? "d3d11h264dec ! d3d11convert ! d3d11download ! videoconvert"
                : "avdec_h264 ! videoconvert";
        return "appsrc name=video-source max-bytes=" + VIDEO_QUEUE_BYTES
                + " max-buffers=12 block=true "
                + "! h264parse ! " + decoder + " "
                + "! appsink name=video-sink sync=false max-buffers=2 "
                + "drop=true enable-last-sample=false";
    }

    private AudioPipeline createAudioPipeline(AudioStreamInfo.CompressionType type) {
        String decoder;
        String caps;
        if (type == AudioStreamInfo.CompressionType.ALAC) {
            decoder = "avdec_alac";
            caps = "audio/x-alac,mpegversion=(int)4,channels=(int)2,rate=(int)44100,"
                    + "stream-format=raw,codec_data=(buffer)"
                    + "00000024616c616300000000000001600010280a0e0200ff00000000000000000000ac44";
        } else if (type == AudioStreamInfo.CompressionType.AAC_ELD) {
            decoder = "avdec_aac";
            caps = "audio/mpeg,mpegversion=(int)4,channels=(int)2,rate=(int)44100,"
                    + "stream-format=raw,codec_data=(buffer)f8e85000";
        } else {
            throw new IllegalArgumentException("Unsupported AirPlay audio compression: " + type);
        }

        Pipeline pipeline = (Pipeline) Gst.parseLaunch(
                "appsrc name=audio-source ! " + decoder + " ! audioconvert ! audioresample "
                        + "! volume name=audio-volume ! autoaudiosink sync=false");
        AppSrc source = (AppSrc) pipeline.getElementByName("audio-source");
        configureLegacyAudioSource(source, caps);
        Element volumeElement = pipeline.getElementByName("audio-volume");
        Bus bus = pipeline.getBus();
        attachBusHandlers(bus);
        return new AudioPipeline(type, pipeline, source, volumeElement, bus);
    }

    private QualityVideoComponent createVideoComponent(AppSink sink) {
        final QualityVideoComponent[] result = new QualityVideoComponent[1];
        Runnable create = () -> {
            result[0] = new QualityVideoComponent(sink, videoMetrics);
        };
        runOnEdtAndWait(create);
        return result[0];
    }

    private void installVideoComponent(QualityVideoComponent component) {
        runOnEdtAndWait(() -> {
            videoHost.removeAll();
            if (component != null) {
                videoHost.add(component, BorderLayout.CENTER);
            }
            videoHost.revalidate();
            videoHost.repaint();
        });
    }

    private void configureSource(AppSrc source, String caps, long maxBytes, boolean timestamp) {
        source.setStreamType(AppSrc.StreamType.STREAM);
        try (Caps parsedCaps = Caps.fromString(caps)) {
            source.setCaps(parsedCaps);
        }
        source.setMaxBytes(maxBytes);
        source.set("is-live", true);
        source.set("format", Format.TIME);
        source.set("emit-signals", false);
        source.set("do-timestamp", timestamp);
    }

    private void configureLegacyAudioSource(AppSrc source, String caps) {
        source.setStreamType(AppSrc.StreamType.STREAM);
        try (Caps parsedCaps = Caps.fromString(caps)) {
            source.setCaps(parsedCaps);
        }
        source.set("is-live", true);
        source.set("format", Format.TIME);
        source.set("emit-signals", true);
    }

    private void attachBusHandlers(Bus bus) {
        bus.connect((Bus.ERROR) (source, code, message) -> {
            log.error("GStreamer error {} from {}: {}", code, source, message);
            listener.onPlaybackError(message, null);
        });
        bus.connect((Bus.EOS) source -> listener.onEndOfStream());
    }

    private void applyVolume() {
        synchronized (audioLock) {
            double effectiveVolume = muted ? 0 : volume;
            alacAudio.volume().set("volume", effectiveVolume);
            aacEldAudio.volume().set("volume", effectiveVolume);
        }
    }

    private void push(AppSrc source, byte[] bytes) {
        Buffer buffer = new Buffer(bytes.length);
        boolean ownershipTransferred = false;
        try {
            try {
                buffer.map(true).put(bytes);
            } finally {
                buffer.unmap();
            }
            // gst1-java marks gst_app_src_push_buffer's Buffer argument with @Invalidate.
            // The binding invalidates this wrapper when native ownership transfers, so it
            // must not be disposed or disowned after this call.
            ownershipTransferred = true;
            FlowReturn result = source.pushBuffer(buffer);
            if (result != FlowReturn.OK && result != FlowReturn.FLUSHING) {
                log.debug("GStreamer rejected an input buffer with status {}", result);
            }
        } catch (RuntimeException error) {
            if (!ownershipTransferred) {
                buffer.dispose();
            }
            throw error;
        }
    }

    /** Copies a retained Netty payload directly into the native GStreamer buffer. */
    private void push(AppSrc source, ByteBuf bytes) {
        Buffer buffer = new Buffer(bytes.readableBytes());
        boolean ownershipTransferred = false;
        try {
            try {
                ByteBuffer target = buffer.map(true);
                if (target == null) {
                    throw new IllegalStateException("Unable to map GStreamer video buffer");
                }
                // ByteBuf writes directly into the mapped native buffer.  This
                // handles heap, direct and composite buffers without creating a
                // temporary byte[]; this is the single copy retained in phase 1.
                bytes.getBytes(bytes.readerIndex(), target);
            } finally {
                buffer.unmap();
            }
            videoMetrics.nativeCopy(bytes.readableBytes());
            ownershipTransferred = true;
            FlowReturn result = source.pushBuffer(buffer);
            if (result != FlowReturn.OK && result != FlowReturn.FLUSHING) {
                videoMetrics.rejected();
                log.debug("GStreamer rejected an input buffer with status {}", result);
            }
        } catch (RuntimeException error) {
            if (!ownershipTransferred) {
                buffer.dispose();
            }
            throw error;
        }
    }

    private void releaseVideoPipeline(VideoPipeline current) {
        if (current == null) {
            return;
        }
        current.sinkPad().removeProbe(current.formatProbe());
        current.frameObserver().close();
        current.component().close();
        installVideoComponent(null);
        stopPipeline(current.pipeline());
        current.sinkPad().dispose();
        current.source().dispose();
        current.sink().dispose();
        current.bus().dispose();
        current.pipeline().dispose();
    }

    private void releaseAudioPipeline(AudioPipeline current) {
        if (current == null) {
            return;
        }
        stopPipeline(current.pipeline());
        current.source().dispose();
        current.volume().dispose();
        current.bus().dispose();
        current.pipeline().dispose();
    }

    private void stopPipeline(Pipeline pipeline) {
        pipeline.stop();
        pipeline.getState(500, TimeUnit.MILLISECONDS);
    }

    private void requestMemoryReclaim() {
        if (closed.get() || maintenanceExecutor.isShutdown()) {
            return;
        }
        ScheduledFuture<?> previous = memoryReclaim;
        if (previous != null) {
            previous.cancel(false);
        }
        try {
            memoryReclaim = maintenanceExecutor.schedule(System::gc, 1500, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            // Application shutdown won the race.
        }
    }

    private PadProbeReturn inspectVideoBuffer(Pad pad, PadProbeInfo probeInfo) {
        int width;
        int height;
        try {
            if (closed.get() || !pad.hasCurrentCaps()) {
                return PadProbeReturn.OK;
            }
            try (Caps caps = pad.getCurrentCaps()) {
                if (caps == null || caps.size() == 0) {
                    return PadProbeReturn.OK;
                }
                Structure format = caps.getStructure(0);
                width = format.getInteger("width");
                height = format.getInteger("height");
            }
        } catch (RuntimeException error) {
            log.debug("Video caps are not available yet", error);
            return PadProbeReturn.OK;
        }
        try {
            if (videoFormatNotifier.beforeBuffer(width, height, listener)) {
                videoMetrics.formatChanged();
            }
        } catch (RuntimeException error) {
            log.warn("Could not prepare the playback window for video format {}x{}",
                    width, height, error);
        }
        return PadProbeReturn.OK;
    }

    private FrameReadyObserver installFrameReadyObserver(QualityVideoComponent component) {
        Component renderComponent = component;
        PropertyChangeListener observer = event -> {
            if (event.getNewValue() instanceof Dimension size
                    && size.width > 0 && size.height > 0) {
                int frameWidth = size.width;
                int frameHeight = size.height;
                // The video surface publishes preferredSize before it paints its own
                // frame dimensions and paints.  Keep the black transition overlay in
                // place until that EDT turn has completed.
                SwingUtilities.invokeLater(() ->
                        videoFormatNotifier.frameReady(frameWidth, frameHeight, listener));
            }
        };
        renderComponent.addPropertyChangeListener("preferredSize", observer);
        return new FrameReadyObserver(renderComponent, observer);
    }

    private void logVideoMetrics() {
        VideoMetrics.Snapshot snapshot = videoMetrics.snapshotAndReset();
        if (snapshot.hasVideo()) {
            log.info("Video performance: compressedBuffers={}, compressedMiB={}, nativeBuffers={}, "
                            + "nativeMiB={}, renderedFrames={}, formatChanges={}, rejectedBuffers={}",
                    snapshot.compressedBuffers(),
                    String.format(java.util.Locale.ROOT, "%.1f", snapshot.compressedBytes() / 1024d / 1024d),
                    snapshot.nativeBuffers(),
                    String.format(java.util.Locale.ROOT, "%.1f", snapshot.nativeBytes() / 1024d / 1024d),
                    snapshot.renderedFrames(), snapshot.formatChanges(), snapshot.rejectedBuffers());
        }
    }

    private void runOnEdtAndWait(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while updating the video component", error);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("Unable to update the video component", error.getCause());
        }
    }

    static double normalizeVolume(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private record VideoPipeline(Pipeline pipeline,
                                 AppSrc source,
                                 AppSink sink,
                                 Pad sinkPad,
                                 Pad.PROBE formatProbe,
                                 Bus bus,
                                 QualityVideoComponent component,
                                 FrameReadyObserver frameObserver) {
    }

    private record FrameReadyObserver(Component source, PropertyChangeListener listener) {
        private static final FrameReadyObserver NONE = new FrameReadyObserver(null, null);

        private void close() {
            if (source != null && listener != null) {
                source.removePropertyChangeListener("preferredSize", listener);
            }
        }
    }

    private record AudioPipeline(AudioStreamInfo.CompressionType type,
                                 Pipeline pipeline,
                                 AppSrc source,
                                 Element volume,
                                 Bus bus) {
    }
}
