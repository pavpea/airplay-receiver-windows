package io.github.qiuspace.airplay.app.platform;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsIntegrationTest {

    @Test
    void recognizesOnlyShellCacheValuesThatReferenceTheReceiver() {
        assertThat(WindowsIntegration.isApplicationReference(
                "[{\"tileId\":\"W~C:\\Users\\test\\AppData\\Local\\AirPlay Receiver for Windows App"
                        + "\\AirPlay Receiver.exe\",\"appIconLight\":\"icon\"}]")).isTrue();
        assertThat(WindowsIntegration.isApplicationReference(
                "{\"tileId\":\"W~C:\\\\Users\\\\qiuxtao\\\\AppData\\\\Local\\\\"
                        + "AirPlay Receiver for Windows App\\\\AirPlay Receiver.exe\", "
                        + "\"appIconLightAssetId\":\"\", \"appIconDarkAssetId\":\"\", "
                        + "\"displayName\":\"\", \"sortName\":\"\", \"suiteName\":\"\", "
                        + "\"packageId\":\"\", \"action\":\"2\", \"shortcutArgs\":\"\", "
                        + "\"targetPath\":\"\"}")).isTrue();
        assertThat(WindowsIntegration.isApplicationReference(new String[]{
                "unrelated",
                "C:\\Portable\\AirPlay Receiver for Windows\\AirPlay Receiver.exe"
        })).isTrue();
        assertThat(WindowsIntegration.isApplicationReference(
                "C:\\Portable\\AirPlay Receiver for Windows\\AirPlay Receiver.exe"
                        .getBytes(StandardCharsets.UTF_16LE)))
                .isTrue();

        assertThat(WindowsIntegration.isApplicationReference(
                "C:\\Portable\\AirPlay Receiver.exe")).isFalse();

        assertThat(WindowsIntegration.isApplicationReference(
                "C:\\Windows\\System32\\DisplaySwitch.exe")).isFalse();
        assertThat(WindowsIntegration.isApplicationReference(42)).isFalse();
    }
}
