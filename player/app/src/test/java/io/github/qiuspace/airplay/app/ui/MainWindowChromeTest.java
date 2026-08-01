package io.github.qiuspace.airplay.app.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.github.qiuspace.airplay.app.AppVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MainWindowChromeTest {

    @Test
    void configuresOnePieceTitleBarWithNoMaximizeButton() {
        JRootPane rootPane = new JRootPane();

        MainWindowChrome.configureRootPane(rootPane);

        assertThat(rootPane.getClientProperty(FlatClientProperties.USE_WINDOW_DECORATIONS)).isEqualTo(true);
        assertThat(rootPane.getClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT)).isEqualTo(true);
        assertThat(rootPane.getClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON)).isEqualTo(false);
        assertThat(rootPane.getClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE)).isEqualTo(false);
        assertThat(rootPane.getClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICONIFFY)).isEqualTo(true);
        assertThat(rootPane.getClientProperty(FlatClientProperties.TITLE_BAR_SHOW_MAXIMIZE)).isEqualTo(false);
        assertThat(rootPane.getClientProperty(FlatClientProperties.TITLE_BAR_SHOW_CLOSE)).isEqualTo(true);
        assertThat(rootPane.getClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT))
                .isEqualTo(MainWindowChrome.TITLE_BAR_HEIGHT);
    }

    @Test
    void reservesTheNativeWindowsButtonArea() {
        JPanel placeholder = MainWindowChrome.createWindowsButtonsPlaceholder();

        assertThat(placeholder.isOpaque()).isFalse();
        assertThat(placeholder.getClientProperty(
                FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER)).isEqualTo("win");
    }

    @Test
    void titleIconUsesThePackagedVectorArtwork() {
        FlatSVGIcon icon = new FlatSVGIcon(
                "icons/app-icon.svg", MainWindowChrome.TITLE_ICON_SIZE,
                MainWindowChrome.TITLE_ICON_SIZE);

        assertThat(icon.hasFound()).isTrue();
        assertThat(icon.getIconWidth()).isEqualTo(MainWindowChrome.TITLE_ICON_SIZE);
        assertThat(icon.getIconHeight()).isEqualTo(MainWindowChrome.TITLE_ICON_SIZE);
    }

    @Test
    void appBarKeepsIdentityAndTwoActionsInFixedCells() {
        JButton logs = new JButton();
        JToggleButton settings = new JToggleButton();

        JPanel appBar = MainWindowChrome.createAppBar(logs, settings);
        BorderLayout layout = (BorderLayout) appBar.getLayout();
        JPanel identity = (JPanel) layout.getLayoutComponent(BorderLayout.WEST);
        JPanel actions = (JPanel) layout.getLayoutComponent(BorderLayout.EAST);

        assertThat(Arrays.stream(identity.getComponents())
                .filter(JLabel.class::isInstance)
                .map(JLabel.class::cast))
                .extracting(JLabel::getName, JLabel::getText)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("appBar.icon", null),
                        org.assertj.core.groups.Tuple.tuple("appBar.product", "AirPlay Receiver"),
                        org.assertj.core.groups.Tuple.tuple("appBar.version", AppVersion.display()));
        JLabel icon = Arrays.stream(identity.getComponents())
                .filter(component -> "appBar.icon".equals(component.getName()))
                .map(JLabel.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(icon.getIcon()).isInstanceOf(FlatSVGIcon.class);
        assertThat(Arrays.stream(actions.getComponents())
                .filter(JButton.class::isInstance)
                .map(JButton.class::cast))
                .containsExactly(logs);
        assertThat(Arrays.stream(actions.getComponents())
                .filter(JToggleButton.class::isInstance)
                .map(JToggleButton.class::cast))
                .containsExactly(settings);
        assertThat(logs.getPreferredSize())
                .isEqualTo(new Dimension(MainWindowChrome.ACTION_BUTTON_SIZE,
                        MainWindowChrome.ACTION_BUTTON_SIZE));
        assertThat(settings.getPreferredSize()).isEqualTo(logs.getPreferredSize());
        Component separator = Arrays.stream(actions.getComponents())
                .filter(component -> "appBar.actionSeparator".equals(component.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(separator.getPreferredSize()).isEqualTo(
                new Dimension(MainWindowChrome.ACTION_SEPARATOR_WIDTH,
                        MainWindowChrome.TITLE_BAR_HEIGHT));
        Component placeholder = actions.getComponent(actions.getComponentCount() - 1);
        assertThat(placeholder.getName()).isEqualTo("appBar.nativeButtons");
    }

    @ParameterizedTest
    @CsvSource({
            "1920, 1040, 1040, 720",
            "1280, 680, 1040, 648",
            "960, 540, 928, 508"
    })
    void fixedWindowFitsInsideScaledWorkAreas(
            int workAreaWidth, int workAreaHeight, int expectedWidth, int expectedHeight) {
        Dimension fitted = MainWindowChrome.fitToWorkArea(
                new Rectangle(0, 0, workAreaWidth, workAreaHeight));

        assertThat(fitted).isEqualTo(new Dimension(expectedWidth, expectedHeight));
    }
}
