package io.github.qiuspace.airplay.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UninstallCleanupTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void removesSettingsLogsBackupsAndNestedTemporaryFiles() throws Exception {
        Path settings = temporaryDirectory.resolve("roaming/AirPlay Receiver");
        Path local = temporaryDirectory.resolve("local/AirPlay Receiver");
        Files.createDirectories(settings.resolve("temporary/nested"));
        Files.createDirectories(local.resolve("logs/archive"));
        Files.writeString(settings.resolve("settings.json"), "{}");
        Files.writeString(settings.resolve("settings.corrupt-20260729.json"), "{broken");
        Files.writeString(settings.resolve("temporary/nested/session.tmp"), "temporary");
        Files.writeString(local.resolve("logs/archive/receiver.log.1"), "log");

        new UninstallCleanup(settings, local, () -> {
        }).removeUserData();

        assertThat(settings).doesNotExist();
        assertThat(local).doesNotExist();
    }

    @Test
    void removesReadOnlyFilesAndCanRunRepeatedly() throws Exception {
        Path settings = temporaryDirectory.resolve("settings");
        Path local = temporaryDirectory.resolve("local");
        Files.createDirectories(settings);
        Files.createDirectories(local);
        Path readOnly = Files.writeString(settings.resolve("settings.json"), "{}");
        DosFileAttributeView dos = Files.getFileAttributeView(readOnly, DosFileAttributeView.class);
        assumeTrue(dos != null);
        dos.setReadOnly(true);

        UninstallCleanup cleanup = new UninstallCleanup(settings, local, () -> {
        });
        cleanup.removeUserData();
        cleanup.removeUserData();

        assertThat(settings).doesNotExist();
        assertThat(local).doesNotExist();
    }

    @Test
    void deletesSymbolicLinkWithoutFollowingIt() throws Exception {
        Path outside = Files.createDirectories(temporaryDirectory.resolve("outside"));
        Path outsideFile = Files.writeString(outside.resolve("keep.txt"), "keep");
        Path settings = Files.createDirectories(temporaryDirectory.resolve("settings"));
        Path link = settings.resolve("linked-directory");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException error) {
            assumeTrue(false, "Symbolic links are unavailable: " + error.getMessage());
        }

        new UninstallCleanup(settings, temporaryDirectory.resolve("missing"), () -> {
        }).removeUserData();

        assertThat(settings).doesNotExist();
        assertThat(outsideFile).hasContent("keep");
    }

    @Test
    void invokesRegistrationCleanupWithoutTouchingUserData() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Path settings = temporaryDirectory.resolve("settings");
        Path local = temporaryDirectory.resolve("local");
        Path script = temporaryDirectory.resolve(UninstallCleanup.POST_CLEANUP_SCRIPT_NAME);
        UninstallCleanup cleanup = new UninstallCleanup(
                settings, local, calls::incrementAndGet, script);

        cleanup.removeRegistration();

        assertThat(calls).hasValue(1);
        assertThat(settings).doesNotExist();
        assertThat(local).doesNotExist();
        assertThat(script).exists();
        assertThat(script).content()
                .contains(
                        "AppListBackup",
                        "airplay receiver.exe",
                        "1..8 | ForEach-Object",
                        "Start-Sleep -Milliseconds 500",
                        "CurrentVersion\\Run",
                        "AirPlay Receiver.lnk",
                        "Remove-Item -LiteralPath $scriptPath");

        cleanup.removeRegistration();
        assertThat(calls).hasValue(2);
        assertThat(script).exists();
    }

    @Test
    void stagesSyntacticallyValidWindowsPowerShell() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        Path script = temporaryDirectory.resolve(UninstallCleanup.POST_CLEANUP_SCRIPT_NAME);
        new UninstallCleanup(
                temporaryDirectory.resolve("settings"),
                temporaryDirectory.resolve("local"),
                () -> {
                },
                script).removeRegistration();

        String escapedPath = script.toString().replace("'", "''");
        String parserCommand = "$tokens=$null; $errors=$null; "
                + "[System.Management.Automation.Language.Parser]::ParseFile('"
                + escapedPath
                + "', [ref]$tokens, [ref]$errors) | Out-Null; "
                + "if ($errors.Count -gt 0) { $errors | Out-String | Write-Error; exit 1 }";
        Process process = new ProcessBuilder(
                "powershell.exe",
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                parserCommand)
                .redirectErrorStream(true)
                .start();

        assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.exitValue()).withFailMessage(output).isZero();
    }
}
