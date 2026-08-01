package io.github.qiuspace.airplay.app.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;

import static org.assertj.core.api.Assertions.assertThat;

class MainFrameLayoutTest {

    @Test
    void instructionBadgeAndFirstTextLineShareTheSameVerticalCenter() {
        JLabel text = new JLabel("Keep the computer and Apple device on the same network");
        JPanel row = MainFrame.instructionRow(1, text);
        row.setSize(480, row.getPreferredSize().height);
        row.doLayout();

        JLabel badge = find(row, "instructions.badge.1", JLabel.class);
        JLabel copy = find(row, "instructions.text.1", JLabel.class);
        int badgeCenter = badge.getY() + badge.getHeight() / 2;
        int textCenter = copy.getY() + copy.getHeight() / 2;

        assertThat(Math.abs(badgeCenter - textCenter)).isLessThanOrEqualTo(1);
    }

    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName()) && type.isInstance(component)) {
                return type.cast(component);
            }
            if (component instanceof Container container) {
                T match = find(container, name, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
