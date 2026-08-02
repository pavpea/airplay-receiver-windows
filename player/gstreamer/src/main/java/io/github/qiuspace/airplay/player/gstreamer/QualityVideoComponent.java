package io.github.qiuspace.airplay.player.gstreamer;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.elements.AppSink;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Objects;

/**
 * Swing video surface with an explicit high-quality interpolation policy.
 *
 * <p>The gst1-java-swing component hard-codes bilinear interpolation.  That
 * is inexpensive, but makes text and one-pixel edges visibly soft whenever a
 * phone frame is fitted to a non-integer window/DPI size.  This component
 * keeps the same one-copy sample-to-image path and uses bicubic interpolation
 * for the final fit, while leaving the compressed/native pipeline unchanged.
 */
final class QualityVideoComponent extends JComponent implements AutoCloseable {

    private final AppSink sink;
    private final VideoMetrics metrics;
    private final Object frameLock = new Object();
    private final AppSink.NEW_SAMPLE sampleListener = this::onSample;
    private final AppSink.NEW_PREROLL prerollListener = this::onPreroll;

    private BufferedImage currentImage;
    private volatile int preferredWidth;
    private volatile int preferredHeight;
    private volatile boolean closed;

    QualityVideoComponent(AppSink sink, VideoMetrics metrics) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        setName("gstreamer.qualityVideo");
        setOpaque(true);
        setBackground(Color.BLACK);
        setDoubleBuffered(false);
        sink.set("emit-signals", true);
        try (Caps caps = Caps.fromString("video/x-raw,pixel-aspect-ratio=1/1,"
                + (ByteOrderSupport.isLittleEndian() ? "format=BGRx" : "format=xRGB"))) {
            sink.setCaps(caps);
        }
        sink.connect(sampleListener);
        sink.connect(prerollListener);
    }

    @Override
    public Dimension getPreferredSize() {
        int width = preferredWidth;
        int height = preferredHeight;
        return width > 0 && height > 0
                ? new Dimension(width, height)
                : super.getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        BufferedImage image;
        synchronized (frameLock) {
            image = currentImage;
        }
        Graphics2D target = (Graphics2D) graphics.create();
        try {
            target.setColor(getBackground());
            target.fillRect(0, 0, getWidth(), getHeight());
            if (image == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }

            double scale = Math.min((double) getWidth() / image.getWidth(),
                    (double) getHeight() / image.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int x = (getWidth() - drawWidth) / 2;
            int y = (getHeight() - drawHeight) / 2;

            target.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            target.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            int deviceDrawWidth = Math.max(1, (int) Math.round(drawWidth * target.getTransform().getScaleX()));
            int deviceDrawHeight = Math.max(1, (int) Math.round(drawHeight * target.getTransform().getScaleY()));
            target.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    interpolationHint(image.getWidth(), image.getHeight(), deviceDrawWidth, deviceDrawHeight));
            target.drawImage(image, x, y, drawWidth, drawHeight, null);
        } finally {
            target.dispose();
        }
    }

    static Object interpolationHint(int sourceWidth, int sourceHeight,
                                    int targetWidth, int targetHeight) {
        if (sourceWidth == targetWidth && sourceHeight == targetHeight) {
            return RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
        }
        return RenderingHints.VALUE_INTERPOLATION_BICUBIC;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        sink.disconnect(sampleListener);
        sink.disconnect(prerollListener);
        synchronized (frameLock) {
            if (currentImage != null) {
                currentImage.flush();
                currentImage = null;
            }
        }
    }

    private FlowReturn onSample(AppSink source) {
        return consumeSample(source.pullSample());
    }

    private FlowReturn onPreroll(AppSink source) {
        return consumeSample(source.pullPreroll());
    }

    private FlowReturn consumeSample(Sample sample) {
        if (sample == null) {
            return FlowReturn.OK;
        }
        try {
            if (closed || sample.getCaps() == null || sample.getCaps().size() == 0) {
                return FlowReturn.OK;
            }
            Structure structure = sample.getCaps().getStructure(0);
            int width = structure.getInteger("width");
            int height = structure.getInteger("height");
            if (width <= 0 || height <= 0) {
                return FlowReturn.OK;
            }
            Buffer buffer = sample.getBuffer();
            if (buffer == null) {
                return FlowReturn.OK;
            }
            ByteBuffer mapped = buffer.map(false);
            try {
                if (mapped == null) {
                    return FlowReturn.OK;
                }
                IntBuffer pixels = mapped.asIntBuffer();
                int pixelCount = Math.multiplyExact(width, height);
                if (pixels.remaining() < pixelCount) {
                    return FlowReturn.ERROR;
                }
                BufferedImage image = imageFor(width, height);
                int[] destination = ((java.awt.image.DataBufferInt) image.getRaster()
                        .getDataBuffer()).getData();
                pixels.get(destination, 0, pixelCount);
            } finally {
                buffer.unmap();
            }
            publishFrame(width, height);
            return FlowReturn.OK;
        } catch (RuntimeException error) {
            return FlowReturn.ERROR;
        } finally {
            sample.dispose();
        }
    }

    private BufferedImage imageFor(int width, int height) {
        synchronized (frameLock) {
            if (currentImage == null
                    || currentImage.getWidth() != width
                    || currentImage.getHeight() != height) {
                if (currentImage != null) {
                    currentImage.flush();
                }
                currentImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                currentImage.setAccelerationPriority(0f);
            }
            return currentImage;
        }
    }

    private void publishFrame(int width, int height) {
        boolean sizeChanged = preferredWidth != width || preferredHeight != height;
        preferredWidth = width;
        preferredHeight = height;
        metrics.rendered();
        if (sizeChanged) {
            SwingUtilities.invokeLater(() -> {
                if (!closed) {
                    setPreferredSize(new Dimension(width, height));
                    revalidate();
                }
            });
        }
        repaint();
    }

    private static final class ByteOrderSupport {
        private ByteOrderSupport() {
        }

        private static boolean isLittleEndian() {
            return java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN;
        }
    }
}
