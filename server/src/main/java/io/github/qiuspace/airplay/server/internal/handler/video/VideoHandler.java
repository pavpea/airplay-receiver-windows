package io.github.qiuspace.airplay.server.internal.handler.video;

import io.github.qiuspace.airplay.lib.AirPlay;
import io.github.qiuspace.airplay.server.AirPlayConsumer;
import io.github.qiuspace.airplay.server.internal.packet.VideoPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@ChannelHandler.Sharable
public class VideoHandler extends ChannelInboundHandlerAdapter {

    private final AirPlay airPlay;
    private final AirPlayConsumer dataConsumer;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        VideoPacket packet = (VideoPacket) msg;
        try (packet) {
            ByteBuf payload = packet.getPayload();
            if (packet.getPayloadType() == 0) {
                decryptVideo(payload);
                preparePictureNALUnits(payload);
                dataConsumer.onVideo(payload);
            } else if (packet.getPayloadType() == 1) {
                ByteBuf spsPps = prepareSpsPpsNALUnits(payload);
                try {
                    dataConsumer.onVideo(spsPps);
                } finally {
                    spsPps.release();
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Decrypts each native component without asking Netty to consolidate a
     * composite buffer.  The FairPlay decryptor carries its CTR tail state
     * across components, so this remains in-place even for fragmented input.
     */
    private void decryptVideo(ByteBuf payload) throws Exception {
        int readerIndex = payload.readerIndex();
        int length = payload.readableBytes();
        if (payload.nioBufferCount() == 1) {
            airPlay.decryptVideo(payload.internalNioBuffer(readerIndex, length));
            return;
        }
        for (java.nio.ByteBuffer component : payload.nioBuffers(readerIndex, length)) {
            airPlay.decryptVideo(component);
        }
    }

    private void preparePictureNALUnits(ByteBuf payload) {
        int idx = payload.readerIndex();
        int end = payload.writerIndex();
        while (idx < end) {
            if (end - idx < Integer.BYTES) {
                log.error("Video packet contains a truncated NAL unit length");
                return;
            }
            long naluSize = payload.getUnsignedInt(idx);
            if (naluSize == 1) {
                return;
            }
            if (naluSize <= 0 || naluSize > end - idx - Integer.BYTES) {
                log.error("Video packet contains corrupted NAL unit. It might be decrypt error");
                return;
            }
            payload.setInt(idx, 1);
            idx += Integer.BYTES + (int) naluSize;
        }
    }

    private ByteBuf prepareSpsPpsNALUnits(ByteBuf payload) {
        ByteBuf payloadBuf = payload.duplicate();
        payloadBuf.readerIndex(payload.readerIndex());
        if (payloadBuf.readableBytes() < 8) {
            throw new IllegalArgumentException("SPS/PPS packet is too short");
        }
        payloadBuf.skipBytes(6);

        int spsLen = payloadBuf.readUnsignedShort();
        if (payloadBuf.readableBytes() < spsLen + 3) {
            throw new IllegalArgumentException("SPS/PPS packet has an invalid SPS length");
        }
        ByteBuf sequenceParameterSet = payloadBuf.readSlice(spsLen);

        payloadBuf.skipBytes(1); // pps count

        int ppsLen = payloadBuf.readUnsignedShort();
        if (payloadBuf.readableBytes() < ppsLen) {
            throw new IllegalArgumentException("SPS/PPS packet has an invalid PPS length");
        }
        ByteBuf pictureParameterSet = payloadBuf.readSlice(ppsLen);

        int spsPpsLen = spsLen + ppsLen + 8;
        log.info("SPS PPS length: {}", spsPpsLen);
        ByteBuf spsPps = payload.alloc().buffer(spsPpsLen, spsPpsLen);
        spsPps.writeInt(1);
        spsPps.writeBytes(sequenceParameterSet, sequenceParameterSet.readerIndex(), spsLen);
        spsPps.writeInt(1);
        spsPps.writeBytes(pictureParameterSet, pictureParameterSet.readerIndex(), ppsLen);
        return spsPps;
    }
}
