package io.github.qiuspace.airplay.app;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsInstallerDefinitionTest {

    private static final String WIX_NAMESPACE = "http://schemas.microsoft.com/wix/2006/wi";
    private static final String UTIL_NAMESPACE = "http://schemas.microsoft.com/wix/UtilExtension";

    @Test
    void closesTheReceiverBeforeAStandardsCompliantMajorUpgrade() throws Exception {
        Document definition = loadDefinition();

        Element restartManager = element(definition, WIX_NAMESPACE, "Property", "Id", "MSIRMSHUTDOWN");
        assertThat(restartManager.getAttribute("Value")).isEqualTo("1");
        assertThat(restartManager.getAttribute("Secure")).isEqualTo("yes");

        Element detection = element(
                definition, UTIL_NAMESPACE, "CloseApplication", "Id", "DetectAirPlayReceiver");
        assertThat(detection.getAttribute("Target")).isEqualTo("AirPlay Receiver.exe");
        assertThat(detection.getAttribute("Property")).isEqualTo("AIRPLAY_APP_RUNNING");
        assertThat(detection.hasAttribute("EndSessionMessage")).isFalse();
        assertThat(detection.hasAttribute("TerminateProcess")).isFalse();
        assertThat(detection.getTextContent()).contains("AIRPLAY_CHECK_RUNNING=1");

        Element gracefulClose = element(
                definition, UTIL_NAMESPACE, "CloseApplication",
                "Id", "GracefulCloseAirPlayReceiver");
        assertThat(gracefulClose.getAttribute("Target")).isEqualTo("AirPlay Receiver.exe");
        assertThat(gracefulClose.getAttribute("EndSessionMessage")).isEqualTo("yes");
        assertThat(gracefulClose.getAttribute("RebootPrompt")).isEqualTo("no");
        assertThat(gracefulClose.getAttribute("Timeout")).isEqualTo("15");
        assertThat(gracefulClose.hasAttribute("TerminateProcess")).isFalse();
        assertThat(gracefulClose.getTextContent()).contains(
                "AIRPLAY_CLOSE_ALLOWED=1", "UILevel < 4",
                "Installed", "JP_UPGRADABLE_FOUND", "JP_DOWNGRADABLE_FOUND");

        Element forceCloseCommand = element(
                definition, WIX_NAMESPACE, "CustomAction",
                "Id", "AirPlaySetForceCloseCommand");
        assertThat(forceCloseCommand.getAttribute("Property"))
                .isEqualTo("WixQuietExecCmdLine");
        assertThat(forceCloseCommand.getAttribute("Value"))
                .contains("taskkill.exe", "/F", "/T", "/IM", "AirPlay Receiver.exe");
        Element forceClose = element(
                definition, WIX_NAMESPACE, "CustomAction", "Id", "AirPlayQuietForceClose");
        assertThat(forceClose.getAttribute("BinaryKey")).isEqualTo("WixCA");
        assertThat(forceClose.getAttribute("DllEntry")).isEqualTo("WixQuietExec");
        assertThat(forceClose.getAttribute("Execute")).isEqualTo("immediate");
        assertThat(forceClose.getAttribute("Return")).isEqualTo("ignore");
        assertThat(forceClose.hasAttribute("ExeCommand")).isFalse();

        Element closeSequence = element(
                definition, WIX_NAMESPACE, "Custom", "Action", "WixCloseApplications");
        assertThat(closeSequence.getAttribute("After")).isEqualTo("InstallValidate");
        Element forceCommandSequence = element(
                definition, WIX_NAMESPACE, "Custom",
                "Action", "AirPlaySetForceCloseCommand");
        assertThat(forceCommandSequence.getAttribute("After"))
                .isEqualTo("WixCloseApplications");
        Element forceSequence = element(
                definition, WIX_NAMESPACE, "Custom", "Action", "AirPlayQuietForceClose");
        assertThat(forceSequence.getAttribute("After"))
                .isEqualTo("AirPlaySetForceCloseCommand");

        Element removeExisting = first(definition, WIX_NAMESPACE, "RemoveExistingProducts");
        assertThat(removeExisting.getAttribute("After")).isEqualTo("InstallInitialize");
        assertThat(removeExisting.hasAttribute("Before")).isFalse();
    }

    @Test
    void preservesUserDataUnlessExplicitRemovalIsSelected() throws Exception {
        Document definition = loadDefinition();
        Document ui = loadXml(projectFile(
                "player/app/src/jpackage/windows/ui.wxf",
                "src/jpackage/windows/ui.wxf"));

        Element removeProperty = element(
                definition, WIX_NAMESPACE, "Property", "Id", "AIRPLAY_REMOVE_USER_DATA");
        assertThat(removeProperty.getAttribute("Secure")).isEqualTo("yes");
        assertThat(removeProperty.hasAttribute("Value")).isFalse();

        Element removeCheckbox = element(
                ui, WIX_NAMESPACE, "Control", "Id", "RemoveUserData");
        assertThat(removeCheckbox.getAttribute("Property")).isEqualTo("AIRPLAY_REMOVE_USER_DATA");
        assertThat(removeCheckbox.getAttribute("CheckBoxValue")).isEqualTo("1");
        assertThat(removeCheckbox.getAttribute("Text")).isEqualTo("彻底移除所有个人数据");
        assertThat(element(
                ui, WIX_NAMESPACE, "Control", "Id", "CleanupDescription")
                .getAttribute("Text"))
                .contains("如果您打算以后重新安装并继续使用，请不要勾选此项。");

        Element registrationCleanup = element(
                definition, WIX_NAMESPACE, "CustomAction", "Id", "AirPlayCleanupRegistration");
        assertThat(registrationCleanup.getAttribute("ExeCommand"))
                .contains("--uninstall-cleanup=registration");
        assertThat(registrationCleanup.getAttribute("Execute")).isEqualTo("immediate");
        assertThat(registrationCleanup.getAttribute("Impersonate")).isEqualTo("yes");
        assertThat(registrationCleanup.getAttribute("Return")).isEqualTo("ignore");

        Element dataCleanup = element(
                definition, WIX_NAMESPACE, "CustomAction", "Id", "AirPlayCleanupUserData");
        assertThat(dataCleanup.getAttribute("ExeCommand"))
                .contains("--uninstall-cleanup=user-data");
        assertThat(dataCleanup.getAttribute("Execute")).isEqualTo("immediate");
        assertThat(dataCleanup.getAttribute("Impersonate")).isEqualTo("yes");
        assertThat(dataCleanup.getAttribute("Return")).isEqualTo("ignore");

        Element registrationSequence = element(
                definition, WIX_NAMESPACE, "Custom",
                "Action", "AirPlayCleanupRegistration");
        assertThat(registrationSequence.getAttribute("After"))
                .isEqualTo("RemoveShortcuts");
        assertThat(registrationSequence.getTextContent())
                .contains("REMOVE~=\"ALL\"", "NOT UPGRADINGPRODUCTCODE");
        Element dataSequence = element(
                definition, WIX_NAMESPACE, "Custom",
                "Action", "AirPlayCleanupUserData");
        assertThat(dataSequence.getAttribute("After"))
                .isEqualTo("AirPlayCleanupRegistration");
        assertThat(dataSequence.getTextContent())
                .contains(
                        "REMOVE~=\"ALL\"",
                        "NOT UPGRADINGPRODUCTCODE",
                        "AIRPLAY_REMOVE_USER_DATA=1");

        Element installDirectoryCleanup = element(
                definition, WIX_NAMESPACE, "RemoveFolder",
                "Id", "AirPlayRemoveInstallDirectory");
        assertThat(installDirectoryCleanup.getAttribute("Directory"))
                .isEqualTo("INSTALLDIR");
        assertThat(installDirectoryCleanup.getAttribute("On"))
                .isEqualTo("uninstall");

        Element uninstallMode = element(
                ui, WIX_NAMESPACE, "Publish",
                "Dialog", "AirPlayUninstallOptionsDlg",
                "Control", "Next",
                "Property", "WixUI_InstallMode");
        assertThat(uninstallMode.getAttribute("Value")).isEqualTo("Remove");

        Element postCleanupCommand = element(
                definition, WIX_NAMESPACE, "CustomAction",
                "Id", "AirPlaySetPostCleanupCommand");
        assertThat(postCleanupCommand.getAttribute("Property"))
                .isEqualTo("WixQuietExecCmdLine");
        assertThat(postCleanupCommand.getAttribute("Value"))
                .contains(
                        "WindowsPowerShell\\v1.0\\powershell.exe",
                        "-WindowStyle Hidden",
                        "-ExecutionPolicy Bypass",
                        "[TempFolder]AirPlayReceiver-UninstallCleanup.ps1");
        Element postCleanup = element(
                definition, WIX_NAMESPACE, "CustomAction", "Id", "AirPlayQuietPostCleanup");
        assertThat(postCleanup.getAttribute("BinaryKey")).isEqualTo("WixCA");
        assertThat(postCleanup.getAttribute("DllEntry")).isEqualTo("WixQuietExec");
        Element postCleanupSequence = element(
                definition, WIX_NAMESPACE, "Custom",
                "Action", "AirPlaySetPostCleanupCommand");
        assertThat(postCleanupSequence.getAttribute("After")).isEqualTo("InstallFinalize");

        Element forceCloseCommand = element(
                definition, WIX_NAMESPACE, "CustomAction",
                "Id", "AirPlaySetForceCloseCommand");
        assertThat(forceCloseCommand.getAttribute("Property"))
                .isEqualTo("WixQuietExecCmdLine");
        assertThat(forceCloseCommand.getAttribute("Value"))
                .contains("taskkill.exe", "/F", "/T", "AirPlay Receiver.exe");
    }

    @Test
    void publishesAControlPanelEntryThatOpensTheMaintenanceWizard() throws Exception {
        Document definition = loadDefinition();
        Document ui = loadXml(projectFile(
                "player/app/src/jpackage/windows/ui.wxf",
                "src/jpackage/windows/ui.wxf"));
        String definitionSource = Files.readString(projectFile(
                "player/app/src/jpackage/windows/main.wxs",
                "src/jpackage/windows/main.wxs"));
        String uiSource = Files.readString(projectFile(
                "player/app/src/jpackage/windows/ui.wxf",
                "src/jpackage/windows/ui.wxf"));

        Element hiddenMsiEntry = element(
                definition, WIX_NAMESPACE, "Property", "Id", "ARPSYSTEMCOMPONENT");
        assertThat(hiddenMsiEntry.getAttribute("Value")).isEqualTo("1");
        Element controlPanelEntry = element(
                definition, WIX_NAMESPACE, "Component", "Id", "AirPlayControlPanelEntry");
        assertThat(controlPanelEntry.getAttribute("Guid"))
                .isEqualTo("{31C0D136-EFAD-4A86-9224-39E3665C6962}");
        Element uninstall = descendantElement(
                controlPanelEntry, WIX_NAMESPACE, "RegistryValue", "Name", "UninstallString");
        assertThat(uninstall.getAttribute("Value"))
                .isEqualTo("MsiExec.exe /I{$(var.JpProductCode)}");
        Element quietUninstall = descendantElement(
                controlPanelEntry, WIX_NAMESPACE,
                "RegistryValue", "Name", "QuietUninstallString");
        assertThat(quietUninstall.getAttribute("Value"))
                .isEqualTo("MsiExec.exe /X{$(var.JpProductCode)} /qn REBOOT=ReallySuppress");
        assertThat(definitionSource)
                .contains(
                        "AirPlayUninstallKey",
                        "ComponentRef Id=\"AirPlayControlPanelEntry\"")
                .doesNotContain(
                        "AirPlayMarkInteractiveUninstall",
                        "AirPlayResetRemove",
                        "AirPlayResetPreselected");

        Element maintenance = element(
                ui, WIX_NAMESPACE, "Dialog", "Id", "AirPlayMaintenanceTypeDlg");
        assertThat(maintenance.getAttribute("Title")).isEqualTo("AirPlay Receiver 维护");
        assertThat(uiSource)
                .contains(
                        "Dialog=\"MaintenanceWelcomeDlg\" Control=\"Next\" Event=\"NewDialog\"",
                        "Value=\"AirPlayMaintenanceTypeDlg\"",
                        "Dialog=\"AirPlayMaintenanceTypeDlg\" Control=\"RepairButton\"",
                        "Dialog=\"AirPlayMaintenanceTypeDlg\" Control=\"RemoveButton\"",
                        "Text=\"卸载(&amp;R)\"")
                .doesNotContain(
                        "<Show Dialog=\"AirPlayUninstallOptionsDlg\"",
                        "Dialog=\"AirPlayMaintenanceTypeDlg\" Control=\"ChangeButton\"");
        assertThat(element(
                ui, WIX_NAMESPACE, "Publish",
                "Dialog", "AirPlayMaintenanceTypeDlg",
                "Control", "RemoveButton",
                "Value", "AirPlayUninstallOptionsDlg").getAttribute("Event"))
                .isEqualTo("NewDialog");
    }

    @Test
    void asksBeforeClosingAnInteractiveRunningReceiver() throws Exception {
        Document ui = loadXml(projectFile(
                "player/app/src/jpackage/windows/ui.wxf",
                "src/jpackage/windows/ui.wxf"));

        Element runningDialog = element(
                ui, WIX_NAMESPACE, "Dialog", "Id", "AirPlayRunningDlg");
        assertThat(runningDialog.getAttribute("Title")).isEqualTo("AirPlay Receiver 正在运行");
        Element continueButton = descendantElement(
                runningDialog, WIX_NAMESPACE, "Control", "Id", "Continue");
        assertThat(continueButton.getAttribute("Text")).isEqualTo("结束程序并继续");
        NodeList continueEvents = continueButton.getElementsByTagNameNS(
                WIX_NAMESPACE, "Publish");
        assertThat(continueEvents.getLength()).isEqualTo(6);
        assertThat(((Element) continueEvents.item(0)).getAttribute("Property"))
                .isEqualTo("AIRPLAY_CLOSE_ALLOWED");
        assertThat(((Element) continueEvents.item(1)).getAttribute("Event"))
                .isEqualTo("DoAction");
        assertThat(((Element) continueEvents.item(1)).getAttribute("Value"))
                .isEqualTo("WixCloseApplications");
        assertThat(((Element) continueEvents.item(2)).getAttribute("Value"))
                .isEqualTo("AirPlaySetForceCloseCommand");
        assertThat(((Element) continueEvents.item(3)).getAttribute("Value"))
                .isEqualTo("AirPlayQuietForceClose");
        assertThat(((Element) continueEvents.item(4)).getAttribute("Value"))
                .isEqualTo("AirPlayResetRunningCheck");
        assertThat(((Element) continueEvents.item(5)).getAttribute("Event"))
                .isEqualTo("NewDialog");
        assertThat(((Element) continueEvents.item(5)).getAttribute("Value"))
                .isEqualTo("VerifyReadyDlg");
        assertThat(descendantElement(
                runningDialog, WIX_NAMESPACE, "Control", "Id", "Cancel").getAttribute("Text"))
                .isEqualTo("取消");

        Element check = element(
                ui, WIX_NAMESPACE, "Publish", "Value", "WixCloseApplications");
        assertThat(check.getAttribute("Dialog")).isEqualTo("ShortcutPromptDlg");
        assertThat(element(
                ui, WIX_NAMESPACE, "Publish", "Value", "AirPlayRunningDlg")
                .getTextContent()).contains("AIRPLAY_APP_RUNNING");
        assertThat(Files.readString(projectFile(
                "player/app/src/jpackage/windows/ui.wxf",
                "src/jpackage/windows/ui.wxf")))
                .contains(
                        "Dialog=\"AirPlayMaintenanceTypeDlg\" Control=\"RemoveButton\" Event=\"NewDialog\"",
                        "Value=\"AirPlayUninstallOptionsDlg\"");
    }

    @Test
    void showsOnlyASimpleCompletionMessageAfterARealUninstall() throws Exception {
        Document exitDialog = loadXml(projectFile(
                "player/app/src/jpackage/windows/ui.wxf",
                "src/jpackage/windows/ui.wxf"));

        Element title = element(
                exitDialog, WIX_NAMESPACE, "Control", "Id", "CompletedTitle");
        assertThat(title.getAttribute("Hidden")).isEqualTo("yes");
        assertThat(title.getTextContent())
                .contains("NOT (WixUI_InstallMode=\"Remove\" OR REMOVE~=\"ALL\")");
        Element description = element(
                exitDialog, WIX_NAMESPACE, "Control", "Id", "CompletedDescription");
        assertThat(description.getAttribute("Hidden")).isEqualTo("yes");
        Element uninstallTitle = element(
                exitDialog, WIX_NAMESPACE, "Control", "Id", "UninstallTitle");
        assertThat(uninstallTitle.getAttribute("Text"))
                .isEqualTo("{\\WixUI_Font_Bigger}AirPlay Receiver 已卸载");
        assertThat(uninstallTitle.getTextContent())
                .contains("WixUI_InstallMode=\"Remove\" OR REMOVE~=\"ALL\"");
        assertThat(Files.readString(projectFile(
                "player/app/src/jpackage/windows/ui.wxf",
                "src/jpackage/windows/ui.wxf")))
                .doesNotContain(
                        "UninstallDescription",
                        "UninstallCleanupComplete",
                        "UninstallDataRetained");
        Element successDialog = element(
                exitDialog, WIX_NAMESPACE, "Show",
                "Dialog", "AirPlayExitDialog");
        assertThat(successDialog.getAttribute("OnExit")).isEqualTo("success");
        assertThat(successDialog.getTextContent().trim()).isEqualTo("1");
    }

    @Test
    void providesAChineseBrandedInstallerAndRemembersWizardChoices() throws Exception {
        Document definition = loadDefinition();
        String source = Files.readString(definitionPath());

        assertThat(source).contains("<?define JpProductLanguage=2052 ?>");

        Element savedInstallDir = element(
                definition, WIX_NAMESPACE, "Property", "Id", "AIRPLAY_SAVED_INSTALLDIR");
        assertThat(savedInstallDir.getAttribute("Secure")).isEqualTo("yes");
        assertThat(element(
                definition, WIX_NAMESPACE, "RegistrySearch",
                "Id", "AirPlaySavedInstallDirSearch").getAttribute("Root")).isEqualTo("HKCU");

        assertThat(element(
                definition, WIX_NAMESPACE, "CustomAction",
                "Id", "AirPlayClearDesktopShortcut").getAttribute("Value"))
                .isEqualTo("[AIRPLAY_EMPTY_VALUE]");
        assertThat(element(
                definition, WIX_NAMESPACE, "CustomAction",
                "Id", "AirPlayClearStartMenuShortcut").getAttribute("Value"))
                .isEqualTo("[AIRPLAY_EMPTY_VALUE]");

        Element preferences = element(
                definition, WIX_NAMESPACE, "Component",
                "Id", "AirPlayInstallerPreferences");
        assertThat(preferences.getAttribute("Win64")).isEqualTo("yes");
        Element preferencesKey = element(
                definition, WIX_NAMESPACE, "RegistryKey",
                "Key", "$(var.AirPlayInstallerPreferencesKey)");
        assertThat(preferencesKey.getAttribute("ForceDeleteOnUninstall")).isEqualTo("yes");

        assertThat(element(
                definition, WIX_NAMESPACE, "WixVariable",
                "Id", "WixUIDialogBmp").getAttribute("Value")).endsWith("installer-dialog.bmp");
        assertThat(element(
                definition, WIX_NAMESPACE, "WixVariable",
                "Id", "WixUIBannerBmp").getAttribute("Value")).endsWith("installer-banner.bmp");

        Element launch = element(
                definition, WIX_NAMESPACE, "CustomAction",
                "Id", "LaunchAirPlayReceiver");
        assertThat(launch.getAttribute("DllEntry")).isEqualTo("WixShellExec");
        assertThat(launch.getAttribute("Impersonate")).isEqualTo("yes");
        assertThat(launch.hasAttribute("Return")).isFalse();
        assertThat(element(
                definition, WIX_NAMESPACE, "CustomAction",
                "Id", "AirPlaySetLaunchTarget").getAttribute("Value"))
                .isEqualTo("[INSTALLDIR]AirPlay Receiver.exe");
        assertThat(element(
                definition, WIX_NAMESPACE, "CustomAction",
                "Id", "AirPlayShowLaunchOption").getAttribute("Value"))
                .isEqualTo("安装完成后运行 AirPlay Receiver");
        assertThat(element(
                definition, WIX_NAMESPACE, "Publish",
                "Value", "LaunchAirPlayReceiver").getTextContent())
                .contains("WIXUI_EXITDIALOGOPTIONALCHECKBOX", "NOT Installed");
        assertThat(element(
                definition, WIX_NAMESPACE, "Publish",
                "Event", "[AIRPLAY_INSTALLDIR_CONFIRMED]").getAttribute("Dialog"))
                .isEqualTo("InstallDirDlg");
        assertThat(element(
                definition, WIX_NAMESPACE, "Publish",
                "Event", "[AIRPLAY_SHORTCUTS_CONFIRMED]").getAttribute("Dialog"))
                .isEqualTo("ShortcutPromptDlg");
        assertThat(element(
                definition, WIX_NAMESPACE, "Custom",
                "Action", "AirPlayRestoreInstallDir").getTextContent())
                .contains("NOT AIRPLAY_INSTALLDIR_CONFIRMED");
    }

    @Test
    void usesTheExpectedWizardOptionsAndReleaseFilename() throws Exception {
        String build = Files.readString(projectFile("player/app/build.gradle", "build.gradle"));

        assertThat(build).contains(
                "-Duser.language=zh",
                "-Duser.country=CN",
                "'--win-dir-chooser'",
                "'--win-shortcut-prompt'",
                "'--win-menu-group', 'AirPlay Receiver for Windows'",
                "AirPlay-Receiver-${project.version}-windows-x64-setup.exe");
    }

    @Test
    void installerBrandingHasTheNativeWixDimensions() throws Exception {
        BufferedImage dialog = ImageIO.read(projectFile(
                "player/app/src/jpackage/windows/installer-dialog.bmp",
                "src/jpackage/windows/installer-dialog.bmp").toFile());
        BufferedImage banner = ImageIO.read(projectFile(
                "player/app/src/jpackage/windows/installer-banner.bmp",
                "src/jpackage/windows/installer-banner.bmp").toFile());

        assertThat(dialog).isNotNull();
        assertThat(dialog.getWidth()).isEqualTo(493);
        assertThat(dialog.getHeight()).isEqualTo(312);
        assertThat(banner).isNotNull();
        assertThat(banner.getWidth()).isEqualTo(493);
        assertThat(banner.getHeight()).isEqualTo(58);
        assertThat(containsRgb(dialog, 0x526DFF)).isFalse();
        assertThat(containsRgb(banner, 0x526DFF)).isFalse();
    }

    @Test
    void skipsTheNonEmptyDirectoryWarningForRecognizedUpgrades() throws Exception {
        Document dialog = loadXml(projectFile(
                "player/app/src/jpackage/windows/InstallDirNotEmptyDlg.wxs",
                "src/jpackage/windows/InstallDirNotEmptyDlg.wxs"));

        Element checkDirectory = element(
                dialog, WIX_NAMESPACE, "Publish", "Value", "JpCheckInstallDir");
        assertThat(checkDirectory.getTextContent()).contains(
                "NOT Installed",
                "NOT JP_UPGRADABLE_FOUND",
                "NOT JP_DOWNGRADABLE_FOUND");

        Element showWarning = element(
                dialog, WIX_NAMESPACE, "Publish", "Value", "InstallDirNotEmptyDlg");
        assertThat(showWarning.getTextContent()).contains(
                "INSTALLDIR_VALID=\"0\"",
                "NOT JP_UPGRADABLE_FOUND",
                "NOT JP_DOWNGRADABLE_FOUND");

        Element continueInstallation = elementContainingText(
                dialog, WIX_NAMESPACE, "Publish", "Value",
                "$(var.JpAfterInstallDirDlg)", "INSTALLDIR_VALID");
        assertThat(continueInstallation.getTextContent()).contains(
                "INSTALLDIR_VALID=\"1\"",
                "Installed",
                "JP_UPGRADABLE_FOUND",
                "JP_DOWNGRADABLE_FOUND");
    }

    @Test
    void publishesTheInstallerAsADirectVersionedReleaseAsset() throws Exception {
        String workflow = Files.readString(projectFile(
                ".github/workflows/build.yaml",
                "../../.github/workflows/build.yaml"));
        String installerHelper = Files.readString(projectFile(
                ".github/scripts/InstallerTestHelpers.ps1",
                "../../.github/scripts/InstallerTestHelpers.ps1"));
        String interactiveUninstall = Files.readString(projectFile(
                ".github/scripts/InteractiveUninstallTest.ps1",
                "../../.github/scripts/InteractiveUninstallTest.ps1"));

        assertThat(workflow).contains(
                "tags:",
                "- 'v*'",
                "name: AirPlay Receiver CI",
                "name: JVM tests",
                "name: Windows package",
                "name: Release",
                "concurrency:",
                "test -x ./gradlew",
                ":lib:test :server:test :player:gstreamer:test",
                "cache-provider: basic",
                "expected_name=\"AirPlay-Receiver-${version}-windows-x64-setup.exe\"",
                "actions/download-artifact@v7",
                "gh release create",
                "gh release upload",
                "--clobber",
                "--verify-tag",
                "--generate-notes",
                "actions/checkout@v6",
                "actions/setup-java@v5",
                "actions/cache@v5",
                "actions/upload-artifact@v7",
                "gradle/actions/setup-gradle@v6",
                "Resolve and download real previous release",
                "https://api.github.com/repos/$env:GITHUB_REPOSITORY/releases",
                "sha256:",
                "airplay-previous-version.exe",
                "AIRPLAY_REMOVE_USER_DATA=1",
                "airplay-previous-install.log",
                "AIRPLAY_TEST_MSI",
                "InteractiveUninstallTest.ps1",
                "Assert-AirPlayControlPanelUninstallDefinition",
                "-UninstallString $entry.UninstallString",
                "-MsiPath $env:AIRPLAY_TEST_MSI");
        int windowsJobStart = workflow.indexOf("  windows-package:");
        int releaseJobStart = workflow.indexOf("\n  release:", windowsJobStart);
        assertThat(windowsJobStart).isGreaterThanOrEqualTo(0);
        assertThat(releaseJobStart).isGreaterThan(windowsJobStart);
        assertThat(workflow.substring(windowsJobStart, releaseJobStart))
                .doesNotContain("contents: write")
                .contains("cache-disabled: true");
        assertThat(installerHelper).contains(
                "TimeoutSeconds = 180",
                "$Arguments -join ' '",
                "WaitForExit(1000)",
                "taskkill.exe",
                "$_.SessionId -ne 0",
                "Get-Content -LiteralPath $LogPath -Tail 200");
        assertThat(interactiveUninstall).contains(
                "欢迎使用",
                "修复或卸载",
                "彻底移除所有个人数据",
                "AirPlay Receiver 已卸载",
                "CheckState",
                "MsiExec",
                "Assert-AirPlayControlPanelUninstallDefinition",
                "MaintenanceWelcomeDlg",
                "AirPlayMaintenanceTypeDlg",
                "MSIRESTARTMANAGERCONTROL=Disable");
        assertThat(workflow)
                .doesNotContain("Build previous-version upgrade fixture")
                .doesNotContain("synthetic 0.9.0")
                .doesNotContain("airplay-previous-version.msi")
                .doesNotContain("name: airplay-receiver-windows-x64");
        assertThat(Stream.of(
                        Path.of(".github/workflows/release.yaml"),
                        Path.of("../../.github/workflows/release.yaml"))
                .map(Path::toAbsolutePath)
                .noneMatch(Files::exists))
                .as("the obsolete Spring Boot release workflow must stay removed")
                .isTrue();
    }

    private static Document loadDefinition() throws Exception {
        return loadXml(definitionPath());
    }

    private static Document loadXml(Path definition) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(definition.toFile());
    }

    private static boolean containsRgb(BufferedImage image, int expectedRgb) {
        int rgbMask = 0x00FFFFFF;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & rgbMask) == expectedRgb) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Path definitionPath() {
        return projectFile(
                "player/app/src/jpackage/windows/main.wxs",
                "src/jpackage/windows/main.wxs");
    }

    private static Path projectFile(String rootPath, String modulePath) {
        return Stream.of(Path.of(rootPath), Path.of(modulePath))
                .map(Path::toAbsolutePath)
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Required project file was not found: " + rootPath));
    }

    private static Element element(Document document,
                                   String namespace,
                                   String name,
                                   String... attributes) {
        if (attributes.length == 0 || attributes.length % 2 != 0) {
            throw new IllegalArgumentException("Attribute names and values must be paired");
        }
        NodeList elements = document.getElementsByTagNameNS(namespace, name);
        for (int index = 0; index < elements.getLength(); index++) {
            Node node = elements.item(index);
            if (node instanceof Element element) {
                boolean matches = true;
                for (int attributeIndex = 0;
                     attributeIndex < attributes.length;
                     attributeIndex += 2) {
                    if (!attributes[attributeIndex + 1].equals(
                            element.getAttribute(attributes[attributeIndex]))) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return element;
                }
            }
        }
        throw new AssertionError("Missing " + name + " with attributes "
                + java.util.Arrays.toString(attributes));
    }

    private static Element elementContainingText(Document document,
                                                 String namespace,
                                                 String name,
                                                 String attribute,
                                                 String value,
                                                 String text) {
        NodeList elements = document.getElementsByTagNameNS(namespace, name);
        for (int index = 0; index < elements.getLength(); index++) {
            Node node = elements.item(index);
            if (node instanceof Element element
                    && value.equals(element.getAttribute(attribute))
                    && element.getTextContent().contains(text)) {
                return element;
            }
        }
        throw new AssertionError(
                "Missing " + name + " with " + attribute + "=" + value + " and text=" + text);
    }

    private static Element descendantElement(Element parent,
                                             String namespace,
                                             String name,
                                             String... attributes) {
        if (attributes.length == 0 || attributes.length % 2 != 0) {
            throw new IllegalArgumentException("Attribute names and values must be paired");
        }
        NodeList elements = parent.getElementsByTagNameNS(namespace, name);
        for (int index = 0; index < elements.getLength(); index++) {
            Node node = elements.item(index);
            if (node instanceof Element element) {
                boolean matches = true;
                for (int attributeIndex = 0;
                     attributeIndex < attributes.length;
                     attributeIndex += 2) {
                    if (!attributes[attributeIndex + 1].equals(
                            element.getAttribute(attributes[attributeIndex]))) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return element;
                }
            }
        }
        throw new AssertionError("Missing descendant " + name + " with attributes "
                + java.util.Arrays.toString(attributes));
    }

    private static Element first(Document document, String namespace, String name) {
        NodeList elements = document.getElementsByTagNameNS(namespace, name);
        assertThat(elements.getLength()).isPositive();
        return (Element) elements.item(0);
    }
}
