package io.github.qiuspace.airplay.app;

import io.github.qiuspace.airplay.app.platform.WindowsIntegration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Arrays;

/**
 * Performs the application-owned cleanup that MSI cannot safely express.
 *
 * <p>The command is intentionally handled before logging and media
 * initialization so uninstalling does not recreate either data directory.</p>
 */
public final class UninstallCleanup {

    static final String REGISTRATION_ARGUMENT = "--uninstall-cleanup=registration";
    static final String USER_DATA_ARGUMENT = "--uninstall-cleanup=user-data";
    static final String POST_CLEANUP_SCRIPT_NAME = "AirPlayReceiver-UninstallCleanup.ps1";
    private static final int DELETE_ATTEMPTS = 3;
    private static final String POST_CLEANUP_SCRIPT = """
            $ErrorActionPreference = 'SilentlyContinue'
            $scriptPath = $MyInvocation.MyCommand.Path
            try {
                $appListRoot = 'Registry::HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\AppListBackup'
                1..8 | ForEach-Object {
                    Start-Sleep -Milliseconds 500
                    if (Test-Path -LiteralPath $appListRoot) {
                        Get-ChildItem -LiteralPath $appListRoot -Recurse -ErrorAction SilentlyContinue |
                            Sort-Object { $_.Name.Length } -Descending |
                            ForEach-Object {
                                $key = $_
                                $matches = @($key.GetValueNames() | Where-Object {
                                    $value = $key.GetValue($_)
                                    if ($null -eq $value) {
                                        return $false
                                    }
                                    $normalized = $value.ToString().ToLowerInvariant()
                                    return (($normalized.Contains('airplay receiver for windows') -and
                                              $normalized.Contains('airplay receiver.exe')) -or
                                            $normalized.Contains('airplay receiver for windows app'))
                                })
                                if ($matches.Count -gt 0) {
                                    Remove-Item -LiteralPath $key.PSPath -Recurse -Force
                                }
                            }
                    }
                }

                Remove-ItemProperty `
                    -LiteralPath 'Registry::HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' `
                    -Name 'AirPlay Receiver' -Force
                Remove-Item `
                    -LiteralPath 'Registry::HKEY_CURRENT_USER\\Software\\qiuspace\\AirPlay Receiver for Windows' `
                    -Recurse -Force
                $vendor = 'Registry::HKEY_CURRENT_USER\\Software\\qiuspace'
                if (Test-Path -LiteralPath $vendor) {
                    $vendorKey = Get-Item -LiteralPath $vendor
                    if ($vendorKey.SubKeyCount -eq 0 -and $vendorKey.ValueCount -eq 0) {
                        Remove-Item -LiteralPath $vendor -Force
                    }
                }

                $shortcutRoots = @(
                    [Environment]::GetFolderPath('Desktop'),
                    [Environment]::GetFolderPath('CommonDesktopDirectory')
                )
                foreach ($root in $shortcutRoots) {
                    if ($root) {
                        Remove-Item -LiteralPath (Join-Path $root 'AirPlay Receiver.lnk') -Force
                    }
                }
                $programRoots = @(
                    [Environment]::GetFolderPath('Programs'),
                    [Environment]::GetFolderPath('CommonPrograms')
                )
                foreach ($root in $programRoots) {
                    if ($root) {
                        $menuDirectory = Join-Path $root 'AirPlay Receiver for Windows'
                        Remove-Item `
                            -LiteralPath (Join-Path $menuDirectory 'AirPlay Receiver.lnk') -Force
                        Remove-Item -LiteralPath $menuDirectory -Force
                    }
                }
            } finally {
                Remove-Item -LiteralPath $scriptPath -Force -ErrorAction SilentlyContinue
            }
            """;

    private final Path settingsDirectory;
    private final Path localDataDirectory;
    private final Runnable registrationCleaner;
    private final Path postCleanupScript;

    UninstallCleanup(Path settingsDirectory,
                     Path localDataDirectory,
                     Runnable registrationCleaner) {
        this(settingsDirectory, localDataDirectory, registrationCleaner, defaultPostCleanupScript());
    }

    UninstallCleanup(Path settingsDirectory,
                     Path localDataDirectory,
                     Runnable registrationCleaner,
                     Path postCleanupScript) {
        this.settingsDirectory = settingsDirectory;
        this.localDataDirectory = localDataDirectory;
        this.registrationCleaner = registrationCleaner;
        this.postCleanupScript = postCleanupScript;
    }

    public static boolean handle(String[] args) throws IOException {
        UninstallCleanup cleanup = new UninstallCleanup(
                AppPaths.settingsDirectory(),
                AppPaths.localDataDirectory(),
                WindowsIntegration::removeApplicationRegistration);
        if (Arrays.asList(args).contains(REGISTRATION_ARGUMENT)) {
            cleanup.removeRegistration();
            return true;
        }
        if (Arrays.asList(args).contains(USER_DATA_ARGUMENT)) {
            cleanup.removeUserData();
            return true;
        }
        return false;
    }

    void removeRegistration() throws IOException {
        registrationCleaner.run();
        stagePostCleanupScript();
    }

    void removeUserData() throws IOException {
        deleteTree(settingsDirectory);
        if (!localDataDirectory.equals(settingsDirectory)) {
            deleteTree(localDataDirectory);
        }
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                deleteWithRetry(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException error)
                    throws IOException {
                if (Files.isSymbolicLink(file)) {
                    deleteWithRetry(file);
                    return FileVisitResult.CONTINUE;
                }
                throw error;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error)
                    throws IOException {
                if (error != null) {
                    throw error;
                }
                deleteWithRetry(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void stagePostCleanupScript() throws IOException {
        Files.createDirectories(postCleanupScript.getParent());
        if (Files.exists(postCleanupScript, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            deleteWithRetry(postCleanupScript);
        }
        Files.writeString(
                postCleanupScript,
                POST_CLEANUP_SCRIPT,
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE);
    }

    private static Path defaultPostCleanupScript() {
        return Path.of(System.getProperty("java.io.tmpdir"), POST_CLEANUP_SCRIPT_NAME);
    }

    private static void deleteWithRetry(Path path) throws IOException {
        IOException failure = null;
        for (int attempt = 0; attempt < DELETE_ATTEMPTS; attempt++) {
            try {
                clearReadOnly(path);
                Files.deleteIfExists(path);
                return;
            } catch (IOException error) {
                failure = error;
                if (attempt + 1 < DELETE_ATTEMPTS) {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        error.addSuppressed(interrupted);
                        throw error;
                    }
                }
            }
        }
        throw failure;
    }

    private static void clearReadOnly(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            return;
        }
        DosFileAttributeView dos = Files.getFileAttributeView(
                path, DosFileAttributeView.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if (dos != null && dos.readAttributes().isReadOnly()) {
            dos.setReadOnly(false);
        }
    }
}
