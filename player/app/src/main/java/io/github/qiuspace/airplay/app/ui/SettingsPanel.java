package io.github.qiuspace.airplay.app.ui;

import io.github.qiuspace.airplay.app.i18n.I18n;
import io.github.qiuspace.airplay.app.settings.AppSettings;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Settings content hosted inside the main application window. */
final class SettingsPanel extends JPanel {

    private final I18n i18n;
    private final JLabel title = new JLabel();
    private final JLabel receiverSection = sectionHeading();
    private final JLabel applicationSection = sectionHeading();
    private final JLabel receiverNameLabel = new JLabel();
    private final JLabel displayModeLabel = new JLabel();
    private final JLabel customSizeLabel = new JLabel();
    private final JLabel maxFpsLabel = new JLabel();
    private final JLabel displayInfo = infoLabel();
    private final JLabel frameRateInfo = infoLabel();
    private final JLabel themeLabel = new JLabel();
    private final JLabel languageLabel = new JLabel();
    private final JLabel behaviorLabel = new JLabel();
    private final JLabel validation = new JLabel();
    private final JTextField receiverName = new JTextField();
    private final JComboBox<AppSettings.DisplayMode> displayMode =
            new JComboBox<>(AppSettings.DisplayMode.values());
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(1920, 640, 7680, 1));
    private final JSpinner height = new JSpinner(new SpinnerNumberModel(1080, 480, 4320, 1));
    private final JComboBox<AppSettings.FrameRateMode> fps =
            new JComboBox<>(AppSettings.FrameRateMode.values());
    private final JSpinner customFps = new JSpinner(new SpinnerNumberModel(
            60, AppSettings.MIN_CUSTOM_FRAME_RATE, AppSettings.MAX_CUSTOM_FRAME_RATE, 1));
    private final JComboBox<AppSettings.ThemeMode> theme =
            new JComboBox<>(AppSettings.ThemeMode.values());
    private final JComboBox<AppSettings.LanguageMode> language =
            new JComboBox<>(AppSettings.LanguageMode.values());
    private final JCheckBox startWithWindows = new JCheckBox();
    private final JCheckBox bringToFront = new JCheckBox();
    private final JCheckBox closeToTray = new JCheckBox();
    private final Timer autoSaveTimer;

    private AppSettings original = AppSettings.defaults();
    private Consumer<AppSettings> saveAction = ignored -> {
    };
    private boolean loading;
    private boolean saving;

    SettingsPanel(I18n i18n) {
        this.i18n = i18n;
        autoSaveTimer = new Timer(350, event -> persist());
        autoSaveTimer.setRepeats(false);
        setName("settings.page");
        setOpaque(false);
        setLayout(new BorderLayout(0, 18));
        setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 6));
        buildUi();
        refreshTexts();
    }

    void open(AppSettings settings, Consumer<AppSettings> onSave) {
        autoSaveTimer.stop();
        loading = true;
        original = settings;
        saveAction = onSave;
        receiverName.setText(settings.receiverName());
        displayMode.setSelectedItem(settings.displayMode());
        width.setValue(settings.customWidth());
        height.setValue(settings.customHeight());
        AppSettings normalized = settings.normalized();
        fps.setSelectedItem(normalized.frameRateMode());
        customFps.setValue(normalized.customFrameRate());
        theme.setSelectedItem(settings.theme());
        language.setSelectedItem(settings.language());
        startWithWindows.setSelected(settings.startWithWindows());
        bringToFront.setSelected(settings.bringToFront());
        closeToTray.setSelected(settings.closeToTray());
        validation.setVisible(false);
        updateCustomFields();
        refreshTexts();
        loading = false;
        receiverName.requestFocusInWindow();
    }

    void refreshTexts() {
        title.setText(i18n.text("settings.title"));
        receiverSection.setText(i18n.text("settings.receiverSection"));
        applicationSection.setText(i18n.text("settings.applicationSection"));
        receiverNameLabel.setText(i18n.text("settings.receiverName"));
        displayModeLabel.setText(i18n.text("settings.displayMode"));
        customSizeLabel.setText(i18n.text("settings.customSize"));
        maxFpsLabel.setText(i18n.text("settings.maxFps"));
        displayInfo.setToolTipText(i18n.text("settings.displayInfo"));
        frameRateInfo.setToolTipText(i18n.text("settings.frameRateInfo"));
        themeLabel.setText(i18n.text("settings.theme"));
        languageLabel.setText(i18n.text("settings.language"));
        behaviorLabel.setText(i18n.text("settings.behavior"));
        startWithWindows.setText(i18n.text("settings.startWithWindows"));
        bringToFront.setText(i18n.text("settings.bringToFront"));
        closeToTray.setText(i18n.text("settings.closeToTray"));
        validation.setText(i18n.text("settings.invalidName"));
        displayMode.repaint();
        theme.repaint();
        language.repaint();
    }

    private void buildUi() {
        title.setName("settings.title");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        add(title, BorderLayout.NORTH);

        receiverName.setName("settings.receiverName");
        displayMode.setName("settings.displayMode");
        width.setName("settings.width");
        height.setName("settings.height");
        fps.setName("settings.fps");
        customFps.setName("settings.customFps");
        theme.setName("settings.theme");
        language.setName("settings.language");
        startWithWindows.setName("settings.startWithWindows");
        bringToFront.setName("settings.bringToFront");
        closeToTray.setName("settings.closeToTray");

        JPanel receiverForm = sectionPanel();
        addRow(receiverForm, 0, receiverNameLabel, receiverName);
        addRow(receiverForm, 1, labelWithInfo(displayModeLabel, displayInfo), displayMode);
        JPanel dimensions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        dimensions.setOpaque(false);
        dimensions.add(width);
        dimensions.add(new JLabel("×"));
        dimensions.add(height);
        addRow(receiverForm, 2, customSizeLabel, dimensions);
        JPanel frameRate = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        frameRate.setOpaque(false);
        frameRate.add(fps);
        frameRate.add(customFps);
        frameRate.add(new JLabel("Hz"));
        addRow(receiverForm, 3, labelWithInfo(maxFpsLabel, frameRateInfo), frameRate);
        addVerticalFiller(receiverForm, 4);

        JPanel applicationForm = sectionPanel();
        addRow(applicationForm, 0, themeLabel, theme);
        addRow(applicationForm, 1, languageLabel, language);
        JPanel options = new JPanel();
        options.setOpaque(false);
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.add(startWithWindows);
        options.add(Box.createVerticalStrut(8));
        options.add(bringToFront);
        options.add(Box.createVerticalStrut(8));
        options.add(closeToTray);
        addRow(applicationForm, 2, behaviorLabel, options);
        addVerticalFiller(applicationForm, 3);

        JPanel receiverColumn = column(receiverSection, receiverForm);
        JPanel applicationColumn = column(applicationSection, applicationForm);
        JPanel columns = new JPanel(new GridLayout(1, 2, 34, 0));
        columns.setOpaque(false);
        columns.add(receiverColumn);
        columns.add(applicationColumn);

        JPanel card = BrandSurface.card(false, new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(28, 30, 28, 30));
        card.add(columns, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        validation.setName("settings.validation");
        validation.setForeground(new Color(224, 88, 88));
        validation.setVisible(false);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(validation, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        receiverName.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                scheduleAutoSave();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                scheduleAutoSave();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                scheduleAutoSave();
            }
        });
        displayMode.addActionListener(event -> {
            updateCustomFields();
            scheduleAutoSave();
        });
        width.addChangeListener(event -> scheduleAutoSave());
        height.addChangeListener(event -> scheduleAutoSave());
        fps.addActionListener(event -> {
            updateCustomFields();
            scheduleAutoSave();
        });
        customFps.addChangeListener(event -> scheduleAutoSave());
        theme.addActionListener(event -> scheduleAutoSave());
        language.addActionListener(event -> scheduleAutoSave());
        startWithWindows.addActionListener(event -> scheduleAutoSave());
        bringToFront.addActionListener(event -> scheduleAutoSave());
        closeToTray.addActionListener(event -> scheduleAutoSave());
        installLocalizedRenderer(displayMode, "displayMode.");
        installLocalizedRenderer(fps, "frameRateMode.");
        installLocalizedRenderer(theme, "theme.");
        installLocalizedRenderer(language, "language.");
    }

    void flushAutoSave() {
        if (autoSaveTimer.isRunning()) {
            autoSaveTimer.stop();
            persist();
        }
    }

    private void scheduleAutoSave() {
        if (!loading && !saving) {
            autoSaveTimer.restart();
        }
    }

    private void persist() {
        String name = receiverName.getText().trim();
        if (name.isBlank() || name.getBytes(StandardCharsets.UTF_8).length > 63) {
            validation.setVisible(true);
            return;
        }
        validation.setVisible(false);
        AppSettings updated = readSettings();
        if (updated.equals(original)) {
            return;
        }
        original = updated;
        saving = true;
        try {
            saveAction.accept(updated);
        } finally {
            saving = false;
        }
    }

    private AppSettings readSettings() {
        return new AppSettings(receiverName.getText(),
                (AppSettings.DisplayMode) displayMode.getSelectedItem(),
                (Integer) width.getValue(), (Integer) height.getValue(), original.maxFps(),
                (AppSettings.ThemeMode) theme.getSelectedItem(),
                (AppSettings.LanguageMode) language.getSelectedItem(),
                startWithWindows.isSelected(), bringToFront.isSelected(), closeToTray.isSelected(),
                true, original.volume(), (AppSettings.FrameRateMode) fps.getSelectedItem(),
                (Integer) customFps.getValue()).normalized();
    }

    private void updateCustomFields() {
        boolean custom = displayMode.getSelectedItem() == AppSettings.DisplayMode.CUSTOM;
        width.setEnabled(custom);
        height.setEnabled(custom);
        customFps.setEnabled(fps.getSelectedItem() == AppSettings.FrameRateMode.CUSTOM);
    }

    private <T extends Enum<T>> void installLocalizedRenderer(JComboBox<T> comboBox, String prefix) {
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean selected, boolean focused) {
                String text = value instanceof Enum<?> enumValue
                        ? i18n.text(prefix + enumValue.name().toLowerCase()) : "";
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        });
    }

    private static JPanel sectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel column(JLabel heading, JPanel form) {
        JPanel panel = new JPanel(new BorderLayout(0, 18));
        panel.setOpaque(false);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel sectionHeading() {
        JLabel label = new JLabel();
        label.setFont(label.getFont().deriveFont(Font.BOLD, 17f));
        return label;
    }

    private static JLabel infoLabel() {
        JLabel label = new JLabel(new FlatSVGIcon("icons/info.svg", 14, 14));
        label.setName("settings.info");
        label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        return label;
    }

    private static JPanel labelWithInfo(JLabel label, JLabel info) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setOpaque(false);
        panel.add(label);
        panel.add(info);
        return panel;
    }

    private static void addRow(JPanel form, int row, Component label, Component input) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 0;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(8, 0, 8, 14);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.insets = new Insets(8, 0, 8, 0);
        form.add(input, constraints);
    }

    private static void addVerticalFiller(JPanel form, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.VERTICAL;
        form.add(Box.createVerticalGlue(), constraints);
    }
}
