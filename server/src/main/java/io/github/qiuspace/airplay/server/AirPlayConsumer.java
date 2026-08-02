package io.github.qiuspace.airplay.server;

import io.github.qiuspace.airplay.lib.AudioStreamInfo;
import io.github.qiuspace.airplay.lib.VideoStreamInfo;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public interface AirPlayConsumer {

    void onVideoFormat(VideoStreamInfo videoStreamInfo);

    void onVideo(byte[] bytes);

    /**
     * Receives a retained video payload without an intermediate heap copy.
     * Implementations must consume the buffer before returning and must not
     * retain it.  The default keeps existing library consumers source and
     * binary compatible by making the legacy byte[] copy at the boundary.
     */
    default void onVideo(ByteBuf bytes) {
        byte[] copy = new byte[bytes.readableBytes()];
        bytes.getBytes(bytes.readerIndex(), copy);
        onVideo(copy);
    }

    void onVideoSrcDisconnect();

    void onAudioFormat(AudioStreamInfo audioStreamInfo);

    void onAudio(byte[] bytes);

    void onAudioSrcDisconnect();

    // HLS stuff, youtube
    default void onMediaPlaylist(String playlistUri) {
    }

    default void onMediaPlaylistRemove() {
    }

    default void onMediaPlaylistPause() {
    }

    default void onMediaPlaylistResume() {
    }

    default PlaybackInfo playbackInfo() {
        return new PlaybackInfo(0, 0);
    }

    record PlaybackInfo(double duration, double position) {
    }
}
