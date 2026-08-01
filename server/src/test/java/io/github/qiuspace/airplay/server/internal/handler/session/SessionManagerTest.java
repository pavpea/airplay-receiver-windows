package io.github.qiuspace.airplay.server.internal.handler.session;

import io.github.qiuspace.airplay.lib.AudioStreamInfo;
import io.github.qiuspace.airplay.lib.MediaStreamInfo;
import io.github.qiuspace.airplay.lib.VideoStreamInfo;
import io.github.qiuspace.airplay.server.AirPlayConsumer;
import io.github.qiuspace.airplay.server.AirPlayServerListener;
import io.github.qiuspace.airplay.server.SessionInfo;
import io.github.qiuspace.airplay.server.SessionState;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {

    @Test
    void allowsOneActiveSessionAndReleasesIt() {
        List<SessionState> states = new ArrayList<>();
        SessionManager manager = new SessionManager(new NoOpConsumer(), new AirPlayServerListener() {
            @Override
            public void onSessionChanged(SessionInfo session, SessionState state) {
                states.add(state);
            }
        });
        EmbeddedChannel firstChannel = new EmbeddedChannel();
        EmbeddedChannel secondChannel = new EmbeddedChannel();
        Session first = manager.getSession("one", new InetSocketAddress("127.0.0.1", 7000), firstChannel);
        Session second = manager.getSession("two", new InetSocketAddress("127.0.0.1", 7001), secondChannel);

        assertTrue(manager.claim(first));
        assertFalse(manager.claim(second));
        manager.streamStarted(first, MediaStreamInfo.StreamType.VIDEO);
        assertTrue(manager.activeSession().isPresent());

        manager.disconnectActiveSession();

        assertTrue(manager.activeSession().isEmpty());
        assertTrue(manager.claim(second));
        assertTrue(states.contains(SessionState.CONNECTING));
        assertTrue(states.contains(SessionState.PLAYING));
        assertTrue(states.contains(SessionState.STOPPED));
        secondChannel.close();
    }

    @Test
    void unexpectedControlDisconnectReclaimsTheActiveSession() {
        AtomicInteger videoDisconnects = new AtomicInteger();
        AirPlayConsumer consumer = new NoOpConsumer() {
            @Override
            public void onVideoSrcDisconnect() {
                videoDisconnects.incrementAndGet();
            }
        };
        SessionManager manager = new SessionManager(consumer, new AirPlayServerListener() { });
        EmbeddedChannel channel = new EmbeddedChannel();
        Session session = manager.getSession("unexpected", new InetSocketAddress("127.0.0.1", 7000), channel);
        assertTrue(manager.claim(session));
        manager.streamStarted(session, MediaStreamInfo.StreamType.VIDEO);

        manager.channelClosed(channel);

        assertTrue(manager.activeSession().isEmpty());
        assertEquals(1, videoDisconnects.get());
        channel.close();
    }

    private static class NoOpConsumer implements AirPlayConsumer {
        @Override public void onVideoFormat(VideoStreamInfo info) { }
        @Override public void onVideo(byte[] bytes) { }
        @Override public void onVideoSrcDisconnect() { }
        @Override public void onAudioFormat(AudioStreamInfo info) { }
        @Override public void onAudio(byte[] bytes) { }
        @Override public void onAudioSrcDisconnect() { }
    }
}
