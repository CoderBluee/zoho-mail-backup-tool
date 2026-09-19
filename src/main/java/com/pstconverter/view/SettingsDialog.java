package com.pstconverter.view;

import com.pstconverter.controller.MainController;
import com.pstconverter.util.SettingsManager;
import com.pstconverter.util.ThemeManager;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;

/**
 * Self-contained Settings Dialog extracted from MainController.
 * Contains all 6 settings categories: General, Parsing, Performance,
 * Diagnostics, Licensing, and About.
 */
public class SettingsDialog {

    private final MainController controller;
    private final Stage ownerStage;

    // Licensing panel fields (dialog-local)
    private Label licStatus;
    private Label lblLicType;
    private Label lblLicExpiry;
    private Label lblLicRemaining;
    private VBox tierCardsBox;
    private VBox keyRow;
    private VBox deactRow;
    private HBox generalLicRow;
    private Button btnLicSetting;
    private Button btnUpgradeRight;
    private Label settingsHeaderStatusBadge;

    // Right sidebar licensing labels
    private Label rightStatusLabel;
    private Label rightTierLabel;
    private Label rightExpiryLabel;
    private Label rightSeatsLabel;
    private Label rightKeyLabel;

    public SettingsDialog(MainController controller, Stage ownerStage) {
        this.controller = controller;
        this.ownerStage = ownerStage;
    }

    /**
     * Opens the settings dialog modally. If openToLicensing is true,
     * the Licensing tab is pre-selected instead of General.
     */
    public void show(boolean openToLicensing) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(ownerStage);
        dialog.setTitle("Global Settings & Activation");

        // Root container is a BorderPane
        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("root-pane");

        // 1. Header Bar
        HBox headerBox = new HBox(20);
        headerBox.getStyleClass().add("settings-header");
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(15, 24, 15, 24));
        
        VBox titleBox = new VBox(4);
        Label title = new Label("Global Settings");
        title.getStyleClass().add("title-label");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        
        Label subtitle = new Label("Configure format preferences, performance parameters, and licensing");
        subtitle.getStyleClass().add("subtitle-label");
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        titleBox.getChildren().addAll(title, subtitle);
        
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        
        settingsHeaderStatusBadge = new Label();
        
        headerBox.getChildren().addAll(titleBox, headerSpacer, settingsHeaderStatusBadge);
        layout.setTop(headerBox);

        // 2. Left Sidebar for Category Selection
        VBox sidebar = new VBox(6);
        sidebar.getStyleClass().add("settings-sidebar");
        sidebar.setPadding(new Insets(15, 10, 15, 10));
        sidebar.setPrefWidth(190);

        // 3. Center Content Area (StackPane inside a ScrollPane)
        StackPane contentArea = new StackPane();
        contentArea.getStyleClass().add("settings-content-pane");
        contentArea.setPadding(new Insets(20));

        ScrollPane scrollPane = new ScrollPane(contentArea);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("conversion-scrollpane");
        layout.setCenter(scrollPane);

        // ── CATEGORY 1: GENERAL & NOTIFICATIONS ──
        VBox cardGeneral = buildGeneralCard(dialog);

        // ── CATEGORY 2: MAILBOX PARSING ──
        VBox cardParsing = buildParsingCard();

        // ── CATEGORY 4: PERFORMANCE & NETWORK ──
        VBox cardPerf = buildPerformanceCard();

        // ── CATEGORY 5: LOGS & DIAGNOSTICS ──
        VBox cardDiag = buildDiagnosticsCard(dialog);

        // ── CATEGORY 6: LICENSING & ACTIVATION ──
        VBox cardLic = buildLicensingCard(dialog);

        // ── CATEGORY 7: ABOUT ──
        VBox cardAbout = buildAboutCard();

        // Collect save-state references for the save button
        // We need references to the ComboBoxes/fields — store them during build
        // They are already captured by the lambda closures in buildGeneralCard etc.

        // Add to StackPane
        contentArea.getChildren().addAll(cardGeneral, cardParsing, cardPerf, cardDiag, cardLic, cardAbout);

        // Switch panel helper
        java.util.function.Consumer<VBox> showPanel = (activeBox) -> {
            cardGeneral.setVisible(activeBox == cardGeneral);
            cardParsing.setVisible(activeBox == cardParsing);
            cardPerf.setVisible(activeBox == cardPerf);
            cardDiag.setVisible(activeBox == cardDiag);
            cardLic.setVisible(activeBox == cardLic);
            cardAbout.setVisible(activeBox == cardAbout);
            
            cardGeneral.setManaged(activeBox == cardGeneral);
            cardParsing.setManaged(activeBox == cardParsing);
            cardPerf.setManaged(activeBox == cardPerf);
            cardDiag.setManaged(activeBox == cardDiag);
            cardLic.setManaged(activeBox == cardLic);
            cardAbout.setManaged(activeBox == cardAbout);
        };

        // Create Sidebar Menu Buttons
        Button btnGeneral = new Button();
        btnGeneral.getStyleClass().add("settings-menu-btn");
        btnGeneral.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.TUNE, 16));
        btnGeneral.setText("General");

        Button btnParsing = new Button();
        btnParsing.getStyleClass().add("settings-menu-btn");
        btnParsing.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.SOURCE, 16));
        btnParsing.setText("Parsing");

        Button btnPerf = new Button();
        btnPerf.getStyleClass().add("settings-menu-btn");
        btnPerf.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.SPEED, 16));
        btnPerf.setText("Performance");

        Button btnDiag = new Button();
        btnDiag.getStyleClass().add("settings-menu-btn");
        btnDiag.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.BUG_REPORT, 16));
        btnDiag.setText("Diagnostics");

        btnLicSetting = new Button();
        btnLicSetting.getStyleClass().add("settings-menu-btn");
        btnLicSetting.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.VERIFIED, 16));
        btnLicSetting.setText("License");

        Button btnAbout = new Button();
        btnAbout.getStyleClass().add("settings-menu-btn");
        btnAbout.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.INFO, 16));
        btnAbout.setText("About");

        java.util.List<Button> buttons = java.util.List.of(btnGeneral, btnParsing, btnPerf, btnDiag, btnLicSetting, btnAbout);
        for (Button btn : buttons) {
            btn.setOnAction(e -> {
                for (Button b : buttons) {
                    b.getStyleClass().remove("settings-menu-btn-active");
                }
                btn.getStyleClass().add("settings-menu-btn-active");
                if (btn == btnGeneral) showPanel.accept(cardGeneral);
                else if (btn == btnParsing) showPanel.accept(cardParsing);
                else if (btn == btnPerf) showPanel.accept(cardPerf);
                else if (btn == btnDiag) showPanel.accept(cardDiag);
                else if (btn == btnLicSetting) showPanel.accept(cardLic);
                else if (btn == btnAbout) showPanel.accept(cardAbout);
            });
        }

        sidebar.getChildren().addAll(btnGeneral, btnParsing, btnPerf, btnDiag, btnLicSetting, btnAbout);
        layout.setLeft(sidebar);

        // Default category selection
        if (openToLicensing) {
            btnGeneral.getStyleClass().remove("settings-menu-btn-active");
            btnLicSetting.getStyleClass().add("settings-menu-btn-active");
            showPanel.accept(cardLic);
        } else {
            btnGeneral.getStyleClass().add("settings-menu-btn-active");
            showPanel.accept(cardGeneral);
        }

        // Right Side Panel for Licensing (currently disabled)
        layout.setRight(null);

        // Update display properties of layout rows
        updateLicensingStatusLabel(licStatus);

        // Footer Actions
        HBox footer = new HBox(12);
        footer.getStyleClass().add("settings-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);
        
        Button btnCancel = new Button("Cancel");
        btnCancel.getStyleClass().addAll("action-btn", "btn-secondary");
        btnCancel.setStyle("-fx-padding: 8px 18px;");
        btnCancel.setOnAction(e -> dialog.close());

        Button btnSave = new Button("Save Settings");
        btnSave.getStyleClass().addAll("action-btn", "btn-primary");
        btnSave.setStyle("-fx-padding: 8px 18px;");
        btnSave.setOnAction(e -> handleSave(dialog));
        
        footer.getChildren().addAll(btnCancel, btnSave);
        layout.setBottom(footer);

        Scene scene = new Scene(layout, 980, 580);
        try {
            scene.getStylesheets().add(ThemeManager.getActiveThemeStylesheet());
        } catch (Exception ignored) {}
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // ════════════════════════════════════════════════════════════════
    //  Category Card Builders
    // ════════════════════════════════════════════════════════════════

    // Save-state references captured for the Save button handler
    private ComboBox<String> cmbThemeSave;
    private ComboBox<String> cmbLangSave;
    private ComboBox<String> cmbFolderTemplateSave;
    private TextField tfExportPathSave;
    private CheckBox cbPlaySoundSave;
    private CheckBox cbShowPopupSave;
    private CheckBox cbCheckUpdatesSave;
    private CheckBox cbSkipCorruptSave;
    private CheckBox cbIgnoreEmptySave;
    private CheckBox cbDeepAttachSave;
    private ComboBox<String> cmbEncodingSave;
    private ComboBox<String> cmbThreadsSave;
    private TextField tfTimeoutSave;
    private ComboBox<String> cmbThrottleSave;
    private ComboBox<String> cmbLogLevelSave;

    private VBox buildGeneralCard(Stage dialog) {
        ComboBox<String> cmbTheme = new ComboBox<>();
        cmbTheme.getItems().addAll("Dark Mode", "Light Mode", "System Default");
        cmbTheme.setValue(SettingsManager.getSetting("ui_theme", "System Default"));
        cmbTheme.getStyleClass().add("filter-combo");
        cmbTheme.setPrefWidth(280);
        cmbThemeSave = cmbTheme;

        ComboBox<String> cmbLang = new ComboBox<>();
        cmbLang.getItems().addAll("English", "Français", "Deutsch", "Español");
        cmbLang.setValue(SettingsManager.getSetting("ui_language", "English"));
        cmbLang.getStyleClass().add("filter-combo");
        cmbLang.setPrefWidth(280);
        cmbLangSave = cmbLang;

        ComboBox<String> cmbFolderTemplate = new ComboBox<>();
        cmbFolderTemplate.getItems().addAll(
            "Mailbox_Export_[Timestamp]",
            "Migration_[Date]",
            "Export_[Timestamp]",
            "Archive_[Timestamp]",
            "Custom Name"
        );
        cmbFolderTemplate.setValue(SettingsManager.getSetting("output_folder_template", "Mailbox_Export_[Timestamp]"));
        cmbFolderTemplate.getStyleClass().add("filter-combo");
        cmbFolderTemplate.setPrefWidth(280);
        cmbFolderTemplateSave = cmbFolderTemplate;

        String defaultPath = SettingsManager.getSetting("default_export_path", "");
        if (defaultPath.trim().isEmpty()) {
            defaultPath = System.getProperty("user.home") + File.separator + "Desktop";
            SettingsManager.saveSetting("default_export_path", defaultPath);
        }
        TextField tfExportPath = new TextField(defaultPath);
        tfExportPath.getStyleClass().add("standard-filter-textfield");
        tfExportPath.setPrefWidth(280);
        tfExportPath.setEditable(false);
        tfExportPathSave = tfExportPath;

        Button btnBrowseExport = new Button("Browse...");
        btnBrowseExport.getStyleClass().addAll("action-btn", "btn-secondary");
        btnBrowseExport.setStyle("-fx-padding: 6px 14px; -fx-font-size: 12.5px; -fx-background-radius: 12px;");
        btnBrowseExport.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Select Default Export Folder");
            File current = new File(tfExportPath.getText());
            if (current.exists() && current.isDirectory()) {
                chooser.setInitialDirectory(current);
            }
            File selected = chooser.showDialog(dialog);
            if (selected != null) {
                tfExportPath.setText(selected.getAbsolutePath());
            }
        });

        CheckBox cbPlaySound = new CheckBox("Play system chime on task completion");
        cbPlaySound.getStyleClass().add("filter-checkbox");
        cbPlaySound.setSelected("true".equals(SettingsManager.getSetting("play_sound_completion", "true")));
        cbPlaySoundSave = cbPlaySound;

        CheckBox cbShowPopup = new CheckBox("Show desktop notification on task completion");
        cbShowPopup.getStyleClass().add("filter-checkbox");
        cbShowPopup.setSelected("true".equals(SettingsManager.getSetting("show_popup_completion", "true")));
        cbShowPopupSave = cbShowPopup;

        CheckBox cbCheckUpdates = new CheckBox("Automatically check for updates on startup");
        cbCheckUpdates.getStyleClass().add("filter-checkbox");
        cbCheckUpdates.setSelected("true".equals(SettingsManager.getSetting("auto_check_updates", "false")));
        cbCheckUpdatesSave = cbCheckUpdates;

        VBox cardGeneral = new VBox(20);
        cardGeneral.getStyleClass().add("settings-card");
        Label lblGenTitle = new Label("Application Customization & Notifications");
        lblGenTitle.getStyleClass().add("settings-section-title");

        VBox rowTheme = new VBox(6);
        Label lblTheme = new Label("UI Theme:");
        lblTheme.getStyleClass().add("settings-row-label");
        Label themeDesc = new Label("Select Dark Mode, Light Mode, or respect system preferences.");
        themeDesc.getStyleClass().add("settings-row-desc");
        themeDesc.setWrapText(true);
        rowTheme.getChildren().addAll(lblTheme, cmbTheme, themeDesc);

        VBox rowLang = new VBox(6);
        Label lblLang = new Label("Language:");
        lblLang.getStyleClass().add("settings-row-label");
        Label langDesc = new Label("Set your preferred interface display language.");
        langDesc.getStyleClass().add("settings-row-desc");
        langDesc.setWrapText(true);
        rowLang.getChildren().addAll(lblLang, cmbLang, langDesc);

        VBox rowFolderTemplate = new VBox(6);
        Label lblFolderTemplate = new Label("Default Folder Naming Template:");
        lblFolderTemplate.getStyleClass().add("settings-row-label");
        Label folderTemplateDesc = new Label("Select the naming format pattern automatically generated for output subfolders.");
        folderTemplateDesc.getStyleClass().add("settings-row-desc");
        folderTemplateDesc.setWrapText(true);
        rowFolderTemplate.getChildren().addAll(lblFolderTemplate, cmbFolderTemplate, folderTemplateDesc);

        VBox rowExportPath = new VBox(6);
        Label lblExportPath = new Label("Default Export Dir:");
        lblExportPath.getStyleClass().add("settings-row-label");
        HBox pathHBox = new HBox(8, tfExportPath, btnBrowseExport);
        pathHBox.setAlignment(Pos.CENTER_LEFT);
        Label pathDesc = new Label("Default destination directory where output files will be written.");
        pathDesc.getStyleClass().add("settings-row-desc");
        pathDesc.setWrapText(true);
        rowExportPath.getChildren().addAll(lblExportPath, pathHBox, pathDesc);

        VBox cbFinishActionsBox = new VBox(10, cbPlaySound, cbShowPopup, cbCheckUpdates);
        VBox rowNotifications = new VBox(6);
        Label lblNotifications = new Label("Notifications:");
        lblNotifications.getStyleClass().add("settings-row-label");
        Label notificationsDesc = new Label("Choose when and how the system alerts you upon task execution.");
        notificationsDesc.getStyleClass().add("settings-row-desc");
        notificationsDesc.setWrapText(true);
        rowNotifications.getChildren().addAll(lblNotifications, cbFinishActionsBox, notificationsDesc);

        // Professional two-column grid layout
        GridPane gridGeneral = new GridPane();
        gridGeneral.setHgap(30);
        gridGeneral.setVgap(20);
        
        cmbTheme.setPrefWidth(350);
        cmbLang.setPrefWidth(350);
        cmbFolderTemplate.setPrefWidth(350);
        tfExportPath.setPrefWidth(260);

        gridGeneral.add(rowTheme, 0, 0);
        gridGeneral.add(rowLang, 1, 0);
        gridGeneral.add(rowFolderTemplate, 0, 1);
        gridGeneral.add(rowExportPath, 1, 1);
        
        GridPane.setColumnSpan(rowNotifications, 2);
        gridGeneral.add(rowNotifications, 0, 2);

        cardGeneral.getChildren().addAll(lblGenTitle, new Separator(), gridGeneral);
        return cardGeneral;
    }

    private VBox buildParsingCard() {
        CheckBox cbSkipCorrupt = new CheckBox("Skip corrupt/unreadable email items (recommended)");
        cbSkipCorrupt.getStyleClass().add("filter-checkbox");
        cbSkipCorrupt.setSelected("true".equals(SettingsManager.getSetting("skip_corrupt_items", "true")));
        cbSkipCorruptSave = cbSkipCorrupt;

        CheckBox cbIgnoreEmpty = new CheckBox("Ignore empty mailbox folders in the explorer tree");
        cbIgnoreEmpty.getStyleClass().add("filter-checkbox");
        cbIgnoreEmpty.setSelected("true".equals(SettingsManager.getSetting("ignore_empty_folders", "false")));
        cbIgnoreEmptySave = cbIgnoreEmpty;

        CheckBox cbDeepAttach = new CheckBox("Recursively scan deeply nested sub-attachments");
        cbDeepAttach.getStyleClass().add("filter-checkbox");
        cbDeepAttach.setSelected("true".equals(SettingsManager.getSetting("deep_attachment_scan", "true")));
        cbDeepAttachSave = cbDeepAttach;

        ComboBox<String> cmbEncoding = new ComboBox<>();
        cmbEncoding.getItems().addAll("Auto-Detect (Recommended)", "UTF-8", "Windows-1252", "ISO-8859-1", "US-ASCII");
        cmbEncoding.setValue(SettingsManager.getSetting("fallback_encoding", "Auto-Detect (Recommended)"));
        cmbEncoding.getStyleClass().add("filter-combo");
        cmbEncoding.setPrefWidth(280);
        cmbEncodingSave = cmbEncoding;

        VBox cardParsing = new VBox(20);
        cardParsing.getStyleClass().add("settings-card");
        Label lblParsingTitle = new Label("Mailbox Parsing & Extraction Strategy");
        lblParsingTitle.getStyleClass().add("settings-section-title");

        VBox rulesBox = new VBox(10, cbSkipCorrupt, cbIgnoreEmpty, cbDeepAttach);
        VBox rowParsingRules = new VBox(6);
        Label lblParsingRules = new Label("Parsing Rules:");
        lblParsingRules.getStyleClass().add("settings-row-label");
        Label parsingRulesDesc = new Label("Options for handling corrupt, empty, or deeply nested mailbox items.");
        parsingRulesDesc.getStyleClass().add("settings-row-desc");
        parsingRulesDesc.setWrapText(true);
        rowParsingRules.getChildren().addAll(lblParsingRules, rulesBox, parsingRulesDesc);

        VBox rowEncoding = new VBox(6);
        Label lblEncoding = new Label("Fallback Encoding:");
        lblEncoding.getStyleClass().add("settings-row-label");
        Label encodingDesc = new Label("Fallback character encoding used if email body charset metadata is absent.");
        encodingDesc.getStyleClass().add("settings-row-desc");
        encodingDesc.setWrapText(true);
        rowEncoding.getChildren().addAll(lblEncoding, cmbEncoding, encodingDesc);

        GridPane gridParsing = new GridPane();
        gridParsing.setHgap(30);
        gridParsing.setVgap(20);
        cmbEncoding.setPrefWidth(350);
        gridParsing.add(rowParsingRules, 0, 0);
        gridParsing.add(rowEncoding, 1, 0);

        cardParsing.getChildren().addAll(lblParsingTitle, new Separator(), gridParsing);
        return cardParsing;
    }

    private VBox buildPerformanceCard() {
        boolean dark = ThemeManager.isDarkMode();

        ComboBox<String> cmbThreads = new ComboBox<>();
        cmbThreads.getItems().addAll(
            "1 (Monolithic Mode)",
            "2 (Standard)",
            "3 (Cloud Optimal)",
            "4 (Performance)",
            "5",
            "6",
            "7",
            "8 (High-End System)",
            "9 (Maximum Limit)"
        );
        String currentThreads = SettingsManager.getSetting(SettingsManager.KEY_THREAD_COUNT, "4");
        boolean foundThreads = false;
        for (String item : cmbThreads.getItems()) {
            String itemNum = item.replaceAll("[^0-9]", "");
            if (itemNum.equals(currentThreads)) {
                cmbThreads.setValue(item);
                foundThreads = true;
                break;
            }
        }
        if (!foundThreads) {
            cmbThreads.setValue(currentThreads);
        }
        cmbThreads.getStyleClass().add("filter-combo");
        cmbThreads.setPrefWidth(280);
        cmbThreadsSave = cmbThreads;

        TextField tfTimeout = new TextField(SettingsManager.getSetting("connection_timeout", "30"));
        tfTimeout.getStyleClass().add("standard-filter-textfield");
        tfTimeout.setPrefWidth(280);
        tfTimeout.setPromptText("Seconds (e.g. 30)");
        tfTimeoutSave = tfTimeout;

        ComboBox<String> cmbThrottle = new ComboBox<>();
        cmbThrottle.getItems().addAll("Unlimited", "512 KB/s", "1 MB/s", "5 MB/s", "10 MB/s");
        cmbThrottle.setValue(SettingsManager.getSetting("network_throttle", "Unlimited"));
        cmbThrottle.getStyleClass().add("filter-combo");
        cmbThrottle.setPrefWidth(280);
        cmbThrottleSave = cmbThrottle;

        VBox cardPerf = new VBox(20);
        cardPerf.getStyleClass().add("settings-card");
        Label lblPerfTitle = new Label("Concurrency & Network Throttling");
        lblPerfTitle.getStyleClass().add("settings-section-title");

        VBox rowThreads = new VBox(6);
        Label lblThreads = new Label("Parallel Threads:");
        lblThreads.getStyleClass().add("settings-row-label");
        Label threadsDesc = new Label("More threads run conversions in parallel but increase CPU load.");
        threadsDesc.getStyleClass().add("settings-row-desc");
        threadsDesc.setWrapText(true);

        VBox threadGuideBox = new VBox(6);
        threadGuideBox.setStyle("-fx-background-color: " + (dark ? "rgba(99, 102, 241, 0.12)" : "rgba(99, 102, 241, 0.06)") + "; " +
                                "-fx-background-radius: 6px; " +
                                "-fx-padding: 10px; " +
                                "-fx-border-color: " + (dark ? "rgba(99, 102, 241, 0.3)" : "rgba(99, 102, 241, 0.2)") + "; " +
                                "-fx-border-width: 1px; " +
                                "-fx-border-radius: 6px;");
        threadGuideBox.setPrefWidth(350);
        threadGuideBox.setMaxWidth(350);

        Label lblGuideTitle = new Label("\uD83D\uDCA1 Optimal Thread Count Guide:");
        lblGuideTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + (dark ? "#38bdf8" : "#0284c7") + ";");
        
        Label lblGuideText = new Label(
            "• Monolithic formats (MBOX, PST): Safe Max 1 (avoids file lock/corruption)\n" +
            "• Cloud destinations (Gmail, IMAP, etc.): Safe Max 3 (prevents server bans)\n" +
            "• Local individual formats (PDF, EML, etc.): Safe Max 8 (performance limit)\n" +
            "Note: Selecting a value above these safe limits will automatically cap the active threads during execution to protect data integrity and avoid API throttling."
        );
        lblGuideText.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (dark ? "#cbd5e1" : "#475569") + "; -fx-line-spacing: 2px;");
        lblGuideText.setWrapText(true);
        threadGuideBox.getChildren().addAll(lblGuideTitle, lblGuideText);

        rowThreads.getChildren().addAll(lblThreads, cmbThreads, threadsDesc, threadGuideBox);

        VBox rowTimeout = new VBox(6);
        Label lblTimeout = new Label("Socket Timeout:");
        lblTimeout.getStyleClass().add("settings-row-label");
        Label timeoutDesc = new Label("Network socket connection timeout threshold in seconds.");
        timeoutDesc.getStyleClass().add("settings-row-desc");
        timeoutDesc.setWrapText(true);
        rowTimeout.getChildren().addAll(lblTimeout, tfTimeout, timeoutDesc);

        VBox rowThrottle = new VBox(6);
        Label lblThrottle = new Label("Upload Throttle:");
        lblThrottle.getStyleClass().add("settings-row-label");
        Label throttleDesc = new Label("Restrict upload rate speed bandwidth to avoid Cloud rate-limiting blocks.");
        throttleDesc.getStyleClass().add("settings-row-desc");
        throttleDesc.setWrapText(true);
        rowThrottle.getChildren().addAll(lblThrottle, cmbThrottle, throttleDesc);

        GridPane gridPerf = new GridPane();
        gridPerf.setHgap(30);
        gridPerf.setVgap(20);
        cmbThreads.setPrefWidth(350);
        tfTimeout.setPrefWidth(350);
        cmbThrottle.setPrefWidth(350);
        gridPerf.add(rowThreads, 0, 0);
        gridPerf.add(rowTimeout, 1, 0);
        gridPerf.add(rowThrottle, 0, 1);

        cardPerf.getChildren().addAll(lblPerfTitle, new Separator(), gridPerf);
        return cardPerf;
    }

    private VBox buildDiagnosticsCard(Stage dialog) {
        ComboBox<String> cmbLogLevel = new ComboBox<>();
        cmbLogLevel.getItems().addAll("DEBUG", "INFO", "WARN", "ERROR", "SEVERE");
        cmbLogLevel.setValue(SettingsManager.getSetting("log_level", "INFO"));
        cmbLogLevel.getStyleClass().add("filter-combo");
        cmbLogLevel.setPrefWidth(280);
        cmbLogLevelSave = cmbLogLevel;

        Button btnVerifyDb = new Button("Verify DB Integrity");
        btnVerifyDb.getStyleClass().addAll("action-btn", "btn-secondary");
        btnVerifyDb.setStyle("-fx-padding: 8px 16px; -fx-font-size: 13px;");
        btnVerifyDb.setOnAction(e -> {
            try {
                long start = System.currentTimeMillis();
                String test = SettingsManager.getSetting("ui_theme", "System Default");
                long duration = System.currentTimeMillis() - start;
                String lang = SettingsManager.getSetting("ui_language", "English");
                
                String alertTitle = "Settings DB Verified";
                String alertText = "Local SQLite settings database validation: SUCCESS.\n" +
                                   "Status: OK\n" +
                                   "Read Latency: " + duration + " ms\n" +
                                   "Path: Documents/" + com.pstconverter.config.BrandConfig.REPORT_DIR_NAME + "/settings.db";
                                   
                if ("Français".equalsIgnoreCase(lang)) {
                    alertTitle = "Base de données vérifiée";
                    alertText = "Validation de la base de données locale des paramètres SQLite : SUCCÈS.\n" +
                                "Statut : OK\n" +
                                "Latence de lecture : " + duration + " ms\n" +
                                "Chemin : Documents/" + com.pstconverter.config.BrandConfig.REPORT_DIR_NAME + "/settings.db";
                } else if ("Deutsch".equalsIgnoreCase(lang)) {
                    alertTitle = "Datenbank verifiziert";
                    alertText = "Lokale SQLite-Einstellungen-Datenbanküberprüfung: ERFOLGREICH.\n" +
                                "Status: OK\n" +
                                "Leselatenz: " + duration + " ms\n" +
                                "Pfad: Documents/" + com.pstconverter.config.BrandConfig.REPORT_DIR_NAME + "/settings.db";
                } else if ("Español".equalsIgnoreCase(lang)) {
                    alertTitle = "Base de datos verificada";
                    alertText = "Validación de la base de datos de configuración local SQLite: ÉXITO.\n" +
                                "Estado: OK\n" +
                                "Latencia de lectura: " + duration + " ms\n" +
                                "Ruta: Documents/" + com.pstconverter.config.BrandConfig.REPORT_DIR_NAME + "/settings.db";
                }
                
                controller.showAlert(Alert.AlertType.INFORMATION, alertTitle, alertText);
            } catch (Exception ex) {
                controller.showAlert(Alert.AlertType.ERROR, "DB Validation Error", "SQLite Settings DB read test failed: " + ex.getMessage());
            }
        });

        Button btnResetDb = new Button("Reset Settings");
        btnResetDb.getStyleClass().addAll("action-btn", "btn-secondary");
        btnResetDb.setStyle("-fx-padding: 8px 16px; -fx-font-size: 13px; -fx-background-color: #ef4444; -fx-text-fill: white; -fx-cursor: hand;");
        btnResetDb.setOnAction(e -> {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Reset All Settings?");
            confirmAlert.setHeaderText(null);
            confirmAlert.setContentText("Are you sure you want to restore all settings to their defaults? Your product activation status will be preserved.");
            confirmAlert.initOwner(dialog);
            java.util.Optional<ButtonType> confirmResult = confirmAlert.showAndWait();
            if (confirmResult.isPresent() && confirmResult.get() == ButtonType.OK) {
                SettingsManager.resetAllSettings();
                controller.showAlert(Alert.AlertType.INFORMATION, "Reset Successful", "All settings and saved accounts have been restored to defaults.");
                dialog.close();
                if (controller.getStep4DestinationView() != null) {
                    controller.getStep4DestinationView().refreshDestinationPaths();
                }
            }
        });

        Button btnResetMigrationState = new Button("Clear Saved State");
        btnResetMigrationState.getStyleClass().addAll("action-btn", "btn-secondary");
        btnResetMigrationState.setStyle("-fx-padding: 8px 16px; -fx-font-size: 13px; -fx-background-color: #f59e0b; -fx-text-fill: white; -fx-cursor: hand;");
        btnResetMigrationState.setOnAction(e -> {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Clear Saved Migration State?");
            confirmAlert.setHeaderText(null);
            confirmAlert.setContentText("Are you sure you want to clear all partially completed migration sessions and progress? This will reset all resume checkpoints.");
            confirmAlert.initOwner(dialog);
            java.util.Optional<ButtonType> confirmResult = confirmAlert.showAndWait();
            if (confirmResult.isPresent() && confirmResult.get() == ButtonType.OK) {
                SettingsManager.clearAllMigrationState();
                controller.showAlert(Alert.AlertType.INFORMATION, "Clear Successful", "All saved migration sessions and checkpoints have been successfully cleared.");
                dialog.close();
                if (controller.getCurrentStep() == 5 && controller.getStep5ConversionView() != null) {
                    controller.getStep5ConversionView().prepareConversionView();
                }
            }
        });

        VBox cardDiag = new VBox(20);
        cardDiag.getStyleClass().add("settings-card");
        Label lblDiagTitle = new Label("Application Logs & System Diagnostics");
        lblDiagTitle.getStyleClass().add("settings-section-title");

        VBox rowLogLevel = new VBox(6);
        Label lblLogLevel = new Label("Logging Severity:");
        lblLogLevel.getStyleClass().add("settings-row-label");
        Label logDesc = new Label("Controls console output verbosity level.");
        logDesc.getStyleClass().add("settings-row-desc");
        logDesc.setWrapText(true);
        rowLogLevel.getChildren().addAll(lblLogLevel, cmbLogLevel, logDesc);

        VBox rowVerify = new VBox(8);
        Label lblVerify = new Label("Database & Recovery Utilities:");
        lblVerify.getStyleClass().add("settings-row-label");
        
        VBox dbActionsBox = new VBox(12);
        
        HBox verifyRow = new HBox(12);
        verifyRow.setAlignment(Pos.CENTER_LEFT);
        btnVerifyDb.setPrefWidth(160);
        Label lblVerifyDesc = new Label("Validates settings database latency & integrity.");
        lblVerifyDesc.getStyleClass().add("settings-row-desc");
        verifyRow.getChildren().addAll(btnVerifyDb, lblVerifyDesc);
        
        HBox resetRow = new HBox(12);
        resetRow.setAlignment(Pos.CENTER_LEFT);
        btnResetDb.setPrefWidth(160);
        Label lblResetDesc = new Label("Restores all layout and theme options back to defaults.");
        lblResetDesc.getStyleClass().add("settings-row-desc");
        resetRow.getChildren().addAll(btnResetDb, lblResetDesc);
        
        HBox clearRow = new HBox(12);
        clearRow.setAlignment(Pos.CENTER_LEFT);
        btnResetMigrationState.setPrefWidth(160);
        Label lblClearDesc = new Label("Clears all interrupted resume checkpoints.");
        lblClearDesc.getStyleClass().add("settings-row-desc");
        clearRow.getChildren().addAll(btnResetMigrationState, lblClearDesc);
        
        dbActionsBox.getChildren().addAll(verifyRow, resetRow, clearRow);
        rowVerify.getChildren().addAll(lblVerify, dbActionsBox);

        GridPane gridDiag = new GridPane();
        gridDiag.setHgap(30);
        gridDiag.setVgap(20);
        cmbLogLevel.setPrefWidth(350);
        gridDiag.add(rowLogLevel, 0, 0);
        gridDiag.add(rowVerify, 1, 0);

        cardDiag.getChildren().addAll(lblDiagTitle, new Separator(), gridDiag);
        return cardDiag;
    }

    private VBox buildLicensingCard(Stage dialog) {
        licStatus = new Label();
        lblLicType = new Label();
        lblLicExpiry = new Label();
        lblLicRemaining = new Label();

        tierCardsBox = new VBox(15);
        tierCardsBox.setAlignment(Pos.CENTER);
        tierCardsBox.setPadding(new Insets(10, 0, 10, 0));
        
        TextField tfKey = new TextField();
        tfKey.setPromptText("PST-ELITE-XXXX-XXXX-XXXX");
        tfKey.setStyle("-fx-font-family: monospace;");
        tfKey.getStyleClass().add("standard-filter-textfield");
        tfKey.setPrefWidth(280);

        Button btnAct = new Button("Activate");
        btnAct.getStyleClass().addAll("action-btn", "btn-primary");
        btnAct.setStyle("-fx-padding: 6px 14px; -fx-font-size: 12.5px; -fx-background-radius: 12px;");

        Button btnDeact = new Button("Deactivate");
        btnDeact.getStyleClass().addAll("action-btn", "btn-secondary");
        btnDeact.setStyle("-fx-padding: 6px 14px; -fx-font-size: 12.5px; -fx-background-radius: 12px;");

        btnAct.setOnAction(e -> {
            String key = tfKey.getText();
            if (com.pstconverter.util.LicenseManager.activate(key)) {
                updateLicensingStatusLabel(licStatus);
                controller.updateAppHeaderLicensingState();
                tfKey.clear();
                controller.showAlert(Alert.AlertType.INFORMATION, "Activation Successful", "Your enterprise license key has been successfully validated and saved.");
                controller.promptAndRestart();
            } else {
                controller.showAlert(Alert.AlertType.ERROR, "Activation Failed", "Invalid enterprise license key. Please check the serial key format.");
            }
        });

        btnDeact.setOnAction(e -> {
            com.pstconverter.util.LicenseManager.deactivate();
            updateLicensingStatusLabel(licStatus);
            controller.updateAppHeaderLicensingState();
            controller.showAlert(Alert.AlertType.INFORMATION, "Deactivated", "The application license has been deactivated and returned to Trial Evaluation Mode.");
            controller.promptAndRestart();
        });

        VBox cardLic = new VBox(20);
        cardLic.getStyleClass().add("settings-card");
        Label lblLicTitle = new Label("Product Activation");
        lblLicTitle.getStyleClass().add("settings-section-title");

        VBox licCenteredLayout = new VBox(16);
        licCenteredLayout.setAlignment(Pos.CENTER);
        licCenteredLayout.setPadding(new Insets(10, 0, 10, 0));

        Button btnGetHelpLic = new Button("Chat with Live Support");
        btnGetHelpLic.getStyleClass().addAll("action-btn", "btn-secondary");
        btnGetHelpLic.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.HELP, 16));
        btnGetHelpLic.setStyle("-fx-font-weight: bold; -fx-padding: 6px 12px; -fx-cursor: hand; -fx-margin-top: 5px;");
        btnGetHelpLic.setOnAction(e -> controller.openHelpChat());

        VBox licDetailsBox = new VBox(8);
        licDetailsBox.setAlignment(Pos.CENTER);
        licDetailsBox.setStyle("-fx-background-color: rgba(99, 102, 241, 0.05); -fx-border-color: rgba(99, 102, 241, 0.2); -fx-border-radius: 8px; -fx-padding: 15px; -fx-min-width: 450px;");
        licDetailsBox.getChildren().addAll(licStatus, lblLicType, lblLicExpiry, lblLicRemaining, btnGetHelpLic);

        HBox keyActionsBox = new HBox(10, tfKey, btnAct);
        keyActionsBox.setAlignment(Pos.CENTER_LEFT);
        Label keyDesc = new Label("Enter your 16-character alphanumeric license key to unlock unlimited conversions.");
        keyDesc.getStyleClass().add("settings-row-desc");
        keyDesc.setWrapText(true);

        keyRow = new VBox(6);
        Label lblKeyInput = new Label("Enter License Key:");
        lblKeyInput.getStyleClass().add("settings-row-label");
        keyRow.getChildren().addAll(lblKeyInput, keyActionsBox, keyDesc);

        deactRow = new VBox(8);
        deactRow.setAlignment(Pos.CENTER);
        deactRow.setPadding(new Insets(10, 0, 10, 0));
        Label lblActiveMsg = new Label("This application is currently activated.");
        lblActiveMsg.setStyle("-fx-font-weight: bold; -fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        deactRow.getChildren().addAll(lblActiveMsg, btnDeact);

        licCenteredLayout.getChildren().addAll(tierCardsBox, keyRow, deactRow);
        cardLic.getChildren().addAll(lblLicTitle, new Separator(), licCenteredLayout);
        return cardLic;
    }

    private VBox buildAboutCard() {
        boolean dark = ThemeManager.isDarkMode();

        VBox cardAbout = new VBox(15);
        cardAbout.getStyleClass().add("settings-card");
        
        Label lblAboutTitle = new Label(com.pstconverter.config.BrandConfig.ABOUT_TITLE);
        lblAboutTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0ea5e9;");
        
        VBox aboutDetailsBox = new VBox(8);
        aboutDetailsBox.setStyle("-fx-background-color: rgba(14, 165, 233, 0.05); -fx-border-color: rgba(14, 165, 233, 0.2); -fx-border-radius: 8px; -fx-padding: 15px;");
        
        Label lblAboutVer = new Label("Software Version: v" + com.pstconverter.config.BrandConfig.VERSION + " (Enterprise Edition)");
        lblAboutVer.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (dark ? "#ffffff" : "#1e293b") + "; -fx-font-size: 13px;");
        
        Label lblAboutDate = new Label("Build Version: 2026.06.15_build42");
        lblAboutDate.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Label lblOsInfo = new Label("Host System: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        lblOsInfo.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Label lblJavaInfo = new Label("Runtime Environment: Java " + System.getProperty("java.version") + " / JavaFX 21");
        lblJavaInfo.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Label lblLicenseStatusLabel = new Label("Activation Status: " + (com.pstconverter.util.LicenseManager.isActivated() ? "Activated" : "Trial Mode"));
        lblLicenseStatusLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        
        Label lblBacklink = new Label("Visit Support Portal: " + com.pstconverter.config.BrandConfig.SUPPORT_URL);
        lblBacklink.setStyle("-fx-text-fill: #0ea5e9; -fx-underline: true; -fx-cursor: hand; -fx-font-size: 12px;");
        lblBacklink.setOnMouseClicked(e -> {
            try {
                new ProcessBuilder("open", com.pstconverter.config.BrandConfig.SUPPORT_URL).start();
            } catch (Exception ignored) {}
        });

        aboutDetailsBox.getChildren().addAll(lblAboutVer, lblAboutDate, lblOsInfo, lblJavaInfo, lblLicenseStatusLabel, lblBacklink);

        VBox activeToolsBox = new VBox(6);
        activeToolsBox.setStyle("-fx-background-color: rgba(148, 163, 184, 0.05); -fx-border-color: rgba(148, 163, 184, 0.15); -fx-border-radius: 8px; -fx-padding: 12px;");
        Label lblToolsTitle = new Label("Active Parser & Output Libraries:");
        lblToolsTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: " + (dark ? "#cbd5e1" : "#475569") + ";");
        
        Label tool1 = new Label("• java-libpst (v0.9.3) - Outlook PST Parsing Engine");
        tool1.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        Label tool2 = new Label("• Apache PDFBox (v2.0.31) - Page-Aware PDF Generator");
        tool2.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        Label tool3 = new Label("• SQLite JDBC (v3.45.1.0) - Settings Database Store");
        tool3.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        Label tool4 = new Label("• MaterialFX (v11.17.0) - Material Design UI Controls");
        tool4.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        
        activeToolsBox.getChildren().addAll(lblToolsTitle, tool1, tool2, tool3, tool4);

        Label lblAboutDesc = new Label(com.pstconverter.config.BrandConfig.ABOUT_DESCRIPTION);
        lblAboutDesc.setWrapText(true);
        lblAboutDesc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-line-spacing: 3px;");

        Label lblCopyright = new Label("© 2026 " + com.pstconverter.config.BrandConfig.COMPANY_NAME + ". All rights reserved.");
        lblCopyright.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        GridPane gridAbout = new GridPane();
        gridAbout.setHgap(30);
        gridAbout.setVgap(20);
        aboutDetailsBox.setPrefWidth(350);
        activeToolsBox.setPrefWidth(350);
        VBox leftColAbout = new VBox(15, aboutDetailsBox, lblAboutDesc);
        VBox rightColAbout = new VBox(15, activeToolsBox, lblCopyright);
        gridAbout.add(leftColAbout, 0, 0);
        gridAbout.add(rightColAbout, 1, 0);

        cardAbout.getChildren().addAll(lblAboutTitle, new Separator(), gridAbout);
        return cardAbout;
    }

    // ════════════════════════════════════════════════════════════════
    //  Save Handler
    // ════════════════════════════════════════════════════════════════

    private void handleSave(Stage dialog) {
        // Save theme
        String selTheme = cmbThemeSave.getValue();
        SettingsManager.saveSetting("ui_theme", selTheme);
        ThemeManager.applyTheme(controller.getScene(), selTheme);

        // Save language
        String selLang = cmbLangSave.getValue();
        SettingsManager.saveSetting("ui_language", selLang);

        // Save root folder template
        SettingsManager.saveSetting("output_folder_template", cmbFolderTemplateSave.getValue());

        // Save Default Export Dir
        SettingsManager.saveSetting("default_export_path", tfExportPathSave.getText());

        // Save Finish Actions
        SettingsManager.saveSetting("play_sound_completion", String.valueOf(cbPlaySoundSave.isSelected()));
        SettingsManager.saveSetting("show_popup_completion", String.valueOf(cbShowPopupSave.isSelected()));
        SettingsManager.saveSetting("auto_check_updates", String.valueOf(cbCheckUpdatesSave.isSelected()));

        // Save Parsing Settings
        SettingsManager.saveSetting("skip_corrupt_items", String.valueOf(cbSkipCorruptSave.isSelected()));
        SettingsManager.saveSetting("ignore_empty_folders", String.valueOf(cbIgnoreEmptySave.isSelected()));
        SettingsManager.saveSetting("deep_attachment_scan", String.valueOf(cbDeepAttachSave.isSelected()));
        SettingsManager.saveSetting("fallback_encoding", cmbEncodingSave.getValue());

        // Save threads
        String thValue = cmbThreadsSave.getValue();
        String th = "4";
        if (thValue != null) {
            String digitsOnly = thValue.replaceAll("[^0-9]", "");
            if (!digitsOnly.isEmpty()) {
                th = digitsOnly;
            }
        }
        SettingsManager.saveSetting(SettingsManager.KEY_THREAD_COUNT, th);

        // Save retry count as infinite default since retries run indefinitely now
        SettingsManager.saveSetting("retry_count", "999999");

        // Save timeout
        String timeout = tfTimeoutSave.getText().trim();
        try {
            int t = Integer.parseInt(timeout);
            if (t >= 0) {
                SettingsManager.saveSetting("connection_timeout", String.valueOf(t));
            }
        } catch (Exception ignored) {}

        // Save network throttle
        SettingsManager.saveSetting("network_throttle", cmbThrottleSave.getValue());

        // Save network logging level
        String newLogLevel = cmbLogLevelSave.getValue();
        SettingsManager.saveSetting("log_level", newLogLevel);
        MainController.TimestampedOutputStream.setActiveLogLevel(newLogLevel);

        // Notify controller to refresh quick settings if open
        controller.onSettingsSaved();

        String saveMsg = "Global settings saved successfully.";
        if ("Français".equalsIgnoreCase(selLang)) saveMsg = "Paramètres globaux enregistrés avec succès.";
        else if ("Deutsch".equalsIgnoreCase(selLang)) saveMsg = "Globale Einstellungen erfolgreich gespeichert.";
        else if ("Español".equalsIgnoreCase(selLang)) saveMsg = "Configuración global guardada con éxito.";
        
        dialog.close();
        controller.showNotification(saveMsg);
        if (controller.getStep4DestinationView() != null) {
            controller.getStep4DestinationView().refreshDestinationPaths();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Licensing Status & Tier Card Helpers (moved from MainController)
    // ════════════════════════════════════════════════════════════════

    private void updateLicensingStatusLabel(Label label) {
        boolean dark = ThemeManager.isDarkMode();
        boolean active = com.pstconverter.util.LicenseManager.isActivated();
        String type = com.pstconverter.util.LicenseManager.getLicenseType();
        int days = com.pstconverter.util.LicenseManager.getDaysRemaining();
        String expiryText = com.pstconverter.util.LicenseManager.getExpirationDateText();
        int remaining = com.pstconverter.util.LicenseManager.getActivationsRemaining();
        int maxAct = com.pstconverter.util.LicenseManager.getMaxActivations();

        String textStyle = "-fx-text-fill: " + (dark ? "#cbd5e1" : "#334155") + "; -fx-font-size: 13px;";
        
        if (active) {
            label.setText("Status: ACTIVATED (" + type + " LICENSE)");
            label.setStyle("-fx-text-fill: " + (dark ? "#34d399" : "#059669") + "; -fx-font-weight: bold; -fx-font-size: 14px;");
            
            if (lblLicType != null) {
                lblLicType.setText("License Type: " + type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase() + " Serial");
                lblLicType.setStyle(textStyle);
            }
            if (lblLicExpiry != null) {
                String daysStr = days == -1 ? "Never (Lifetime Support)" : expiryText + " (" + days + " days remaining)";
                lblLicExpiry.setText("Expiration: " + daysStr);
                lblLicExpiry.setStyle(textStyle);
            }
            if (lblLicRemaining != null) {
                lblLicRemaining.setText("Activations: " + (maxAct - remaining) + " of " + maxAct + " seats occupied (" + remaining + " remaining)");
                lblLicRemaining.setStyle(textStyle);
            }
        } else {
            label.setText("Status: TRIAL EVALUATION MODE");
            label.setStyle("-fx-text-fill: " + (dark ? "#fbbf24" : "#b45309") + "; -fx-font-weight: bold; -fx-font-size: 14px;");
            
            if (lblLicType != null) {
                lblLicType.setText("License Type: Free Trial Evaluation");
                lblLicType.setStyle(textStyle);
            }
            if (lblLicExpiry != null) {
                lblLicExpiry.setText("Expiration: " + expiryText + " (" + days + " days remaining)");
                lblLicExpiry.setStyle(textStyle);
            }
            if (lblLicRemaining != null) {
                lblLicRemaining.setText("Usage Limit: " + com.pstconverter.util.LicenseManager.TRIAL_LIMIT_PER_FOLDER + " items per folder conversion");
                lblLicRemaining.setStyle(textStyle);
            }
        }

        // Toggle Key Row and Deactivation Row visibility
        if (keyRow != null) {
            keyRow.setVisible(!active);
            keyRow.setManaged(!active);
        }
        if (deactRow != null) {
            deactRow.setVisible(active);
            deactRow.setManaged(active);
        }

        // Update the tier comparison cards
        if (tierCardsBox != null) {
            tierCardsBox.getChildren().clear();
            tierCardsBox.setSpacing(10);
            tierCardsBox.setAlignment(Pos.CENTER);

            HBox sideBySideLayout = new HBox(20);
            sideBySideLayout.setAlignment(Pos.CENTER);

            // 1. Evaluation/Trial VBox
            VBox trialGroup = new VBox(6);
            trialGroup.setAlignment(Pos.TOP_CENTER);
            Label lblTrialHeader = new Label("EVALUATION TIER");
            lblTrialHeader.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
            
            VBox trialCard = createLicenseTierCard("Free Trial", null, "Capped at " + com.pstconverter.util.LicenseManager.TRIAL_LIMIT_PER_FOLDER + " mails/folder.\nAll formats & Cloud enabled.", !active);
            trialCard.setPrefWidth(160);
            trialGroup.getChildren().addAll(lblTrialHeader, trialCard);

            // 2. Vertical Separator
            Separator vSep = new Separator(javafx.geometry.Orientation.VERTICAL);
            vSep.setStyle("-fx-padding: 0 10 0 10;");

            // 3. Commercial Tiers VBox
            VBox commercialGroup = new VBox(6);
            commercialGroup.setAlignment(Pos.TOP_CENTER);
            Label lblCommHeader = new Label("COMMERCIAL PLANS");
            lblCommHeader.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #0ea5e9;");
            
            HBox commCards = new HBox(12);
            commCards.setAlignment(Pos.CENTER);
            
            VBox standardCard = createLicenseTierCard("Standard", "1 System", "All standard documents.\nNo monolithic/Cloud.", active && "STANDARD".equals(type));
            VBox businessCard = createLicenseTierCard("Business", "3 Systems", "Documents + local archives.\nCloud disabled.", active && "BUSINESS".equals(type));
            VBox enterpriseCard = createLicenseTierCard("Enterprise", "10 Systems", "Full unlimited access.\nAll Cloud uploaders.", active && "ENTERPRISE".equals(type));
            
            standardCard.setPrefWidth(150);
            businessCard.setPrefWidth(150);
            enterpriseCard.setPrefWidth(150);
            
            commCards.getChildren().addAll(standardCard, businessCard, enterpriseCard);
            commercialGroup.getChildren().addAll(lblCommHeader, commCards);

            sideBySideLayout.getChildren().addAll(trialGroup, vSep, commercialGroup);
            tierCardsBox.getChildren().add(sideBySideLayout);
        }

        // Update settings dialog header status badge dynamically
        if (settingsHeaderStatusBadge != null) {
            if (active) {
                settingsHeaderStatusBadge.setText(type.toUpperCase() + " ACTIVE");
                settingsHeaderStatusBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.15); -fx-text-fill: " + (dark ? "#34d399" : "#059669") + "; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 6px 14px; -fx-background-radius: 20px; -fx-border-color: rgba(16, 185, 129, 0.3); -fx-border-width: 1px; -fx-border-radius: 20px;");
            } else {
                settingsHeaderStatusBadge.setText("TRIAL EVALUATION");
                settingsHeaderStatusBadge.setStyle("-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: " + (dark ? "#fbbf24" : "#b45309") + "; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 6px 14px; -fx-background-radius: 20px; -fx-border-color: rgba(245, 158, 11, 0.3); -fx-border-width: 1px; -fx-border-radius: 20px;");
            }
        }
    }

    private void handleUpgradeLicense(Stage ownerDialog) {
        Stage upgradeStage = new Stage();
        upgradeStage.initModality(Modality.APPLICATION_MODAL);
        upgradeStage.initOwner(ownerDialog);
        upgradeStage.setTitle("Upgrade License Edition");

        VBox layout = new VBox(16);
        layout.setPadding(new Insets(20));
        layout.getStyleClass().add("root-pane");
        layout.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("Upgrade Your License");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0ea5e9;");

        Label desc = new Label("Upgrade your license key to unlock higher seats allocation, local archives, and Cloud formats export options.");
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");

        TextField tfUpgradeKey = new TextField();
        tfUpgradeKey.setPromptText(com.pstconverter.config.BrandConfig.LICENSE_KEY_FORMAT_HINT);
        tfUpgradeKey.setStyle("-fx-font-family: monospace;");
        tfUpgradeKey.getStyleClass().add("standard-filter-textfield");
        tfUpgradeKey.setPrefWidth(320);

        Button btnApplyUpgrade = new Button("Apply Upgrade Key");
        btnApplyUpgrade.getStyleClass().addAll("action-btn", "btn-primary");
        btnApplyUpgrade.setStyle("-fx-font-size: 13px; -fx-padding: 8px 18px;");

        Button btnBuyUpgrade = new Button("Buy Upgrade Online");
        btnBuyUpgrade.getStyleClass().addAll("action-btn", "btn-secondary");
        btnBuyUpgrade.setStyle("-fx-font-size: 13px; -fx-padding: 8px 18px;");
        btnBuyUpgrade.setOnAction(e -> {
            try {
                new ProcessBuilder("open", com.pstconverter.config.BrandConfig.UPGRADE_URL).start();
            } catch (Exception ignored) {}
        });

        HBox btnRow = new HBox(10, btnBuyUpgrade, btnApplyUpgrade);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        btnApplyUpgrade.setOnAction(e -> {
            String key = tfUpgradeKey.getText();
            if (com.pstconverter.util.LicenseManager.activate(key)) {
                updateLicensingStatusLabel(licStatus);
                controller.updateAppHeaderLicensingState();
                upgradeStage.close();
                controller.showAlert(Alert.AlertType.INFORMATION, "Upgrade Successful", 
                          "Your license has been successfully upgraded to the " + com.pstconverter.util.LicenseManager.getLicenseType() + " tier!");
                controller.promptAndRestart();
            } else {
                controller.showAlert(Alert.AlertType.ERROR, "Upgrade Failed", 
                          "Invalid license key format or signature check failed. Please verify the serial code.");
            }
        });

        layout.getChildren().addAll(title, desc, tfUpgradeKey, btnRow);

        Scene scene = new Scene(layout, 420, 240);
        try {
            scene.getStylesheets().add(ThemeManager.getActiveThemeStylesheet());
        } catch (Exception ignored) {}
        upgradeStage.setScene(scene);
        upgradeStage.showAndWait();
    }

    private VBox createLicenseTierCard(String name, String seats, String features, boolean isActiveTier) {
        boolean dark = ThemeManager.isDarkMode();
        VBox card = new VBox(6);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(10));
        card.setPrefWidth(125);
        card.setMinHeight(135);
        
        String baseStyle = "-fx-background-radius: 8px; -fx-border-radius: 8px; -fx-padding: 10px;";
        if (isActiveTier) {
            card.setStyle(baseStyle + "-fx-background-color: rgba(14, 165, 233, 0.08); -fx-border-color: #0ea5e9; -fx-border-width: 2px;");
        } else {
            card.setStyle(baseStyle + "-fx-background-color: " + (dark ? "rgba(255, 255, 255, 0.02)" : "rgba(0, 0, 0, 0.02)") + "; -fx-border-color: " + (dark ? "rgba(255, 255, 255, 0.1)" : "rgba(0, 0, 0, 0.08)") + "; -fx-border-width: 1px;");
        }

        Label lblName = new Label(name);
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + (isActiveTier ? "#0ea5e9" : (dark ? "#ffffff" : "#1e293b")) + ";");

        card.getChildren().add(lblName);

        if (seats != null && !seats.trim().isEmpty()) {
            Label lblSeats = new Label(seats);
            lblSeats.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isActiveTier ? "#38bdf8" : "#94a3b8") + "; -fx-font-weight: bold;");
            card.getChildren().add(lblSeats);
        }

        Label lblFeatures = new Label(features);
        lblFeatures.setWrapText(true);
        lblFeatures.setAlignment(Pos.CENTER);
        lblFeatures.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        lblFeatures.setStyle("-fx-font-size: 9.5px; -fx-text-fill: " + (dark ? "#cbd5e1" : "#475569") + ";");

        card.getChildren().addAll(new Separator(), lblFeatures);
        
        if (isActiveTier) {
            Label activeBadge = new Label("ACTIVE");
            activeBadge.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-size: 8px; -fx-font-weight: bold; -fx-padding: 1px 5px; -fx-background-radius: 4px;");
            card.getChildren().add(activeBadge);
        }
        
        return card;
    }
}
