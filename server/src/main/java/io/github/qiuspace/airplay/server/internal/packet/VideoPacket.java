package io.github.qiuspace.airplay.server.internal.packet;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicBoolean;

@Getter
public final class VideoPacket implements AutoCloseable {

    private final int payloadType;
    private final int payloadSize;

    /**
     * A retained slice owned by this packet.  The slice is valid until
     * {@link #close()} is called; consumers must finish synchronously during
     * the decoder callback and must not retain it.
     */
    private final ByteBuf payload;
    private final AtomicBoolean released = new AtomicBoolean();

    public VideoPacket(int payloadType, int payloadSize, ByteBuf payload) {
        this.payloadType = payloadType;
        this.payloadSize = payloadSize;
        this.payload = payload;
    }

    @Override
    public void close() {
        if (released.compareAndSet(false, true)) {
            ReferenceCountUtil.release(payload);
        }
    }
}
