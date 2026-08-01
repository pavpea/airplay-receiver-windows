package io.github.qiuspace.airplay.app.platform;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;
import com.sun.jna.platform.win32.WinReg;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Locale;
import java.util.Map;

public final class WindowsIntegration {

    private static final String RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String PERSONALIZE_KEY =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
    private static final String APP_LIST_BACKUP_KEY =
            "Software\\Microsoft\\Windows\\CurrentVersion\\AppListBackup";
    private static final String APPLICATION_KEY = "Software\\qiuspace\\AirPlay Receiver for Windows";
    private static final String VENDOR_KEY = "Software\\qiuspace";
    private static final String VALUE_NAME = "AirPlay Receiver";
    private static final String MENU_NAME = "AirPlay Receiver for Windows";
    private static final String SHORTCUT_NAME = VALUE_NAME + ".lnk";
    private static final long[] SHELL_CACHE_SETTLE_DELAYS_MILLIS = {0L, 200L, 500L, 1000L};

    private WindowsIntegration() {
    }

    public static void setStartWithWindows(boolean enabled) {
        if (!Platform.isWindows()) {
            return;
        }
        if (enabled) {
            String executablePath = System.getProperty("jpackage.app-path");
            if (executablePath == null || executablePath.isBlank()) {
                throw new IllegalStateException("Start with Windows is available in the packaged application");
            }
            String executable = '"' + executablePath + '"';
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME, executable);
        } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME);
        }
    }

    public static boolean isSystemDarkTheme() {
        if (!Platform.isWindows()) {
            return false;
        }
        try {
            return Advapi32Util.registryGetIntValue(
                    WinReg.HKEY_CURRENT_USER, PERSONALIZE_KEY, "AppsUseLightTheme") == 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void removeApplicationRegistration() {
        if (!Platform.isWindows()) {
            return;
        }
        runCleanup(WindowsIntegration::deleteStartupRegistration);
        runCleanup(WindowsIntegration::deleteKnownShortcuts);
        runCleanup(WindowsIntegration::deleteShellCacheRegistration);
        runCleanup(() -> deleteRegistryTree(APPLICATION_KEY));
        runCleanup(() -> deleteRegistryKeyIfEmpty(VENDOR_KEY));
    }

    public static void openFirewallSettings() {
        browse("windowsdefender://network/");
    }

    public static void openDirectory(Path directory) {
        try {
            java.nio.file.Files.createDirectories(directory);
            Desktop.getDesktop().open(directory.toFile());
        } catch (IOException error) {
            throw new IllegalStateException("Unable to open directory", error);
        }
    }

    private static void browse(String uri) {
        try {
            Desktop.getDesktop().browse(URI.create(uri));
        } catch (IOException error) {
            throw new IllegalStateException("Unable to open Windows settings", error);
        }
    }

    static boolean isApplicationReference(Object value) {
        if (value instanceof String text) {
            return containsApplicationReference(text);
        }
        if (value instanceof String[] strings) {
            for (String text : strings) {
                if (containsApplicationReference(text)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof byte[] bytes) {
            return containsApplicationReference(new String(bytes, StandardCharsets.UTF_8))
                    || containsApplicationReference(new String(bytes, StandardCharsets.UTF_16LE));
        }
        return false;
    }

    private static boolean containsApplicationReference(String text) {
        String normalized = text.toLowerCase(Locale.ROOT).replace("\\\\", "\\");
        String packagedPath = System.getProperty("jpackage.app-path", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('/', '\\');
        if (!packagedPath.isBlank() && normalized.contains(packagedPath)) {
            return true;
        }
        return normalized.contains("airplay receiver for windows")
                && normalized.contains("airplay receiver.exe");
    }

    private static void deleteStartupRegistration() {
        if (Advapi32Util.registryValueExists(
                WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)) {
            Advapi32Util.registryDeleteValue(
                    WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME);
        }
    }

    private static void deleteKnownShortcuts() {
        deleteKnownShortcut(KnownFolders.FOLDERID_Desktop, SHORTCUT_NAME);
        deleteKnownShortcut(KnownFolders.FOLDERID_PublicDesktop, SHORTCUT_NAME);
        deleteKnownShortcut(KnownFolders.FOLDERID_Programs, MENU_NAME, SHORTCUT_NAME);
        deleteKnownShortcut(KnownFolders.FOLDERID_Programs, SHORTCUT_NAME);
        deleteKnownShortcut(KnownFolders.FOLDERID_CommonPrograms, MENU_NAME, SHORTCUT_NAME);
        deleteKnownShortcut(KnownFolders.FOLDERID_CommonPrograms, SHORTCUT_NAME);
    }

    private static void deleteKnownShortcut(Guid.GUID folder, String... relativePath) {
        try {
            Path folderPath = Path.of(Shell32Util.getKnownFolderPath(folder));
            Path shortcut = folderPath;
            for (String part : relativePath) {
                shortcut = shortcut.resolve(part);
            }
            clearReadOnly(shortcut);
            Files.deleteIfExists(shortcut);
            if (relativePath.length > 1) {
                Files.deleteIfExists(shortcut.getParent());
            }
        } catch (IOException | RuntimeException ignored) {
            // Continue cleaning the remaining per-user artifacts.
        }
    }

    private static void clearReadOnly(Path path) throws IOException {
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        DosFileAttributeView attributes = Files.getFileAttributeView(
                path, DosFileAttributeView.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if (attributes != null && attributes.readAttributes().isReadOnly()) {
            attributes.setReadOnly(false);
        }
    }

    private static boolean deleteApplicationReferences(String key, boolean deleteEmptyKey) {
        if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key)) {
            return false;
        }

        boolean removed = false;
        for (Map.Entry<String, Object> entry : Advapi32Util.registryGetValues(
                WinReg.HKEY_CURRENT_USER, key).entrySet()) {
            if (isApplicationReference(entry.getValue())) {
                Advapi32Util.registryDeleteValue(
                        WinReg.HKEY_CURRENT_USER, key, entry.getKey());
                removed = true;
            }
        }
        for (String child : Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, key)) {
            removed |= deleteApplicationReferences(key + "\\" + child, true);
        }
        if (deleteEmptyKey
                && removed
                && Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key)
                && Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, key).length == 0
                && Advapi32Util.registryGetValues(WinReg.HKEY_CURRENT_USER, key).isEmpty()) {
            Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, key);
        }
        return removed;
    }

    private static void deleteShellCacheRegistration() {
        for (long delay : SHELL_CACHE_SETTLE_DELAYS_MILLIS) {
            if (delay > 0L) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            deleteApplicationReferences(APP_LIST_BACKUP_KEY, false);
        }
    }

    private static void runCleanup(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException ignored) {
            // A stale shell entry must never make the product impossible to uninstall.
        }
    }

    private static void deleteRegistryTree(String key) {
        if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key)) {
            return;
        }
        for (String child : Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, key)) {
            deleteRegistryTree(key + "\\" + child);
        }
        for (String value : Advapi32Util.registryGetValues(
                WinReg.HKEY_CURRENT_USER, key).keySet()) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, key, value);
        }
        Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, key);
    }

    private static void deleteRegistryKeyIfEmpty(String key) {
        if (Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key)
                && Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, key).length == 0
                && Advapi32Util.registryGetValues(WinReg.HKEY_CURRENT_USER, key).isEmpty()) {
            Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, key);
        }
    }
}
