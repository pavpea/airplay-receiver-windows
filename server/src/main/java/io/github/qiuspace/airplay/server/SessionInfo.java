package io.github.qiuspace.airplay.server;

import io.github.qiuspace.airplay.lib.MediaStreamInfo;

import java.net.InetSocketAddress;
import java.util.Set;

public record SessionInfo(String id,
                          InetSocketAddress remoteAddress,
                          Set<MediaStreamInfo.StreamType> streams) {

    public SessionInfo {
        streams = Set.copyOf(streams);
    }
}
