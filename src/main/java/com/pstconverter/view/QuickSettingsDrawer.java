package com.pstconverter.view;

import com.pstconverter.util.SettingsManager;
import com.pstconverter.util.ThemeManager;
import com.pstconverter.util.MaterialIcons;
import com.pstconverter.controller.MainController;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.File;

/**
 * Self-contained Quick Settings Drawer component, extracted from MainController.
 * Extends VBox and can be placed as a right-side panel in the wizard layout.
 */
public class QuickSettingsDrawer extends VBox {

    private final MainController controller;
    private boolean isRefreshing = false;

    // Controls
    private ComboBox<String> qsTheme;
    private ComboBox<String> qsThreads;
    private ComboBox<String> qsThrottle;
    private ComboBox<String> qsLogLevel;
    private CheckBox qsSkipCorrupt;
    private CheckBox qsDeepAttach;
    private CheckBox qsIgnoreEmpty;
    private CheckBox qsEnableBranding;
    private TextField qsCompanyName;
    private TextField qsContactInfo;
    private TextField qsLogoPath;

    public QuickSettingsDrawer(MainController controller) {
        super(12);
        this.controller = controller;
        buildUI();
        BorderPane.setMargin(this, new Insets(0, 0, 0, 15));
    }

    private void buildUI() {
        this.getStyleClass().add("settings-card");
        this.setPrefWidth(270);
        this.setPadding(new Insets(15));

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleIcon = MaterialIcons.icon(MaterialIcons.TUNE, 16);
        Label titleLbl = new Label("Quick Settings");
        titleLbl.getStyleClass().add("settings-section-title");
        titleLbl.setStyle("-fx-font-size: 14px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClose = new Button();
        btnClose.getStyleClass().addAll("action-btn", "btn-secondary");
        btnClose.setGraphic(MaterialIcons.icon(MaterialIcons.CLEAR, 12));
        btnClose.setStyle("-fx-padding: 4px 6px; -fx-background-radius: 6px;");
        btnClose.setOnAction(e -> controller.toggleQuickSettings());

        header.getChildren().addAll(titleIcon, titleLbl, spacer, btnClose);

        // Separator
        Separator sep = new Separator();

        // Controls
        // 1. Theme
        Label lblTheme = new Label("UI Theme:");
        lblTheme.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        qsTheme = new ComboBox<>();
        qsTheme.getItems().addAll("Dark Mode", "Light Mode", "System Default");
        qsTheme.getStyleClass().add("filter-combo");
        qsTheme.setPrefWidth(240);
        qsTheme.setOnAction(e -> {
            if (isRefreshing) return;
            String val = qsTheme.getValue();
            if (val != null) {
                SettingsManager.saveSetting("ui_theme", val);
                ThemeManager.applyTheme(controller.getScene(), val);
            }
        });

        // 2. Threads
        Label lblThreads = new Label("Parallel Threads:");
        lblThreads.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        qsThreads = new ComboBox<>();
        qsThreads.getItems().addAll(
            "1 (Monolithic)",
            "2 (Standard)",
            "3 (Cloud Optimal)",
            "4 (Performance)",
            "5",
            "6",
            "7",
            "8 (High-End)",
            "9 (Maximum)"
        );
        qsThreads.getStyleClass().add("filter-combo");
        qsThreads.setPrefWidth(240);
        qsThreads.setOnAction(e -> {
            if (isRefreshing) return;
            String val = qsThreads.getValue();
            if (val != null) {
                String th = "4";
                String digitsOnly = val.replaceAll("[^0-9]", "");
                if (!digitsOnly.isEmpty()) {
                    th = digitsOnly;
                }
                SettingsManager.saveSetting(SettingsManager.KEY_THREAD_COUNT, th);
            }
        });

        // 3. Network Limit
        Label lblThrottle = new Label("Network Limit:");
        lblThrottle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        qsThrottle = new ComboBox<>();
        qsThrottle.getItems().addAll("Unlimited", "512 KB/s", "1 MB/s", "5 MB/s", "10 MB/s");
        qsThrottle.getStyleClass().add("filter-combo");
        qsThrottle.setPrefWidth(240);
        qsThrottle.setOnAction(e -> {
            if (isRefreshing) return;
            String val = qsThrottle.getValue();
            if (val != null) {
                SettingsManager.saveSetting("network_throttle", val);
            }
        });

        // 4. Log Level
        Label lblLogLevel = new Label("Logging Severity:");
        lblLogLevel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        qsLogLevel = new ComboBox<>();
        qsLogLevel.getItems().addAll("DEBUG", "INFO", "WARN", "ERROR", "SEVERE");
        qsLogLevel.getStyleClass().add("filter-combo");
        qsLogLevel.setPrefWidth(240);
        qsLogLevel.setOnAction(e -> {
            if (isRefreshing) return;
            String val = qsLogLevel.getValue();
            if (val != null) {
                SettingsManager.saveSetting("log_level", val);
                MainController.TimestampedOutputStream.setActiveLogLevel(val);
            }
        });

        // Checkboxes
        qsSkipCorrupt = new CheckBox("Skip corrupt email items");
        qsSkipCorrupt.getStyleClass().add("filter-checkbox");
        qsSkipCorrupt.setOnAction(e -> {
            if (isRefreshing) return;
            SettingsManager.saveSetting("skip_corrupt_items", String.valueOf(qsSkipCorrupt.isSelected()));
        });

        qsDeepAttach = new CheckBox("Deep attachment scan");
        qsDeepAttach.getStyleClass().add("filter-checkbox");
        qsDeepAttach.setOnAction(e -> {
            if (isRefreshing) return;
            SettingsManager.saveSetting("deep_attachment_scan", String.valueOf(qsDeepAttach.isSelected()));
        });

        qsIgnoreEmpty = new CheckBox("Ignore empty folders");
        qsIgnoreEmpty.getStyleClass().add("filter-checkbox");
        qsIgnoreEmpty.setOnAction(e -> {
            if (isRefreshing) return;
            SettingsManager.saveSetting("ignore_empty_folders", String.valueOf(qsIgnoreEmpty.isSelected()));
        });

        // Branding Section
        Separator sepBranding = new Separator();
        Label lblBrandingTitle = new Label("PDF REPORT BRANDING");
        lblBrandingTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #0ea5e9;");

        qsEnableBranding = new CheckBox("Enable Custom Branding");
        qsEnableBranding.getStyleClass().add("filter-checkbox");
        qsEnableBranding.setOnAction(e -> {
            if (isRefreshing) return;
            SettingsManager.saveSetting("pdf_branding_enabled", String.valueOf(qsEnableBranding.isSelected()));
        });

        Label lblCompanyName = new Label("Company Name:");
        lblCompanyName.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        qsCompanyName = new TextField();
        qsCompanyName.setPromptText("Enter company name...");
        qsCompanyName.setStyle("-fx-font-size: 11px;");
        qsCompanyName.textProperty().addListener((obs, oldV, newV) -> {
            if (isRefreshing) return;
            SettingsManager.saveSetting("pdf_branding_company", newV);
        });

        Label lblContactInfo = new Label("Contact Info:");
        lblContactInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        qsContactInfo = new TextField();
        qsContactInfo.setPromptText("Email or phone number...");
        qsContactInfo.setStyle("-fx-font-size: 11px;");
        qsContactInfo.textProperty().addListener((obs, oldV, newV) -> {
            if (isRefreshing) return;
            SettingsManager.saveSetting("pdf_branding_contact", newV);
        });

        Label lblLogoPath = new Label("Company Logo:");
        lblLogoPath.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        HBox logoBox = new HBox(5);
        qsLogoPath = new TextField();
        qsLogoPath.setPromptText("Logo image path...");
        qsLogoPath.setStyle("-fx-font-size: 11px;");
        HBox.setHgrow(qsLogoPath, Priority.ALWAYS);
        qsLogoPath.textProperty().addListener((obs, oldV, newV) -> {
            if (isRefreshing) return;
            SettingsManager.saveSetting("pdf_branding_logo", newV);
        });

        Button btnBrowseLogo = new Button("Browse");
        btnBrowseLogo.setStyle("-fx-font-size: 10px; -fx-padding: 3px 8px;");
        btnBrowseLogo.setOnAction(e -> {
            javafx.stage.FileChooser logoChooser = new javafx.stage.FileChooser();
            logoChooser.setTitle("Select Logo Image");
            logoChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg)", "*.png", "*.jpg", "*.jpeg"));
            File file = logoChooser.showOpenDialog(getScene().getWindow());
            if (file != null) {
                qsLogoPath.setText(file.getAbsolutePath());
            }
        });
        logoBox.getChildren().addAll(qsLogoPath, btnBrowseLogo);

        VBox scrollContent = new VBox(10);
        scrollContent.getChildren().addAll(
            lblTheme, qsTheme,
            lblThreads, qsThreads,
            lblThrottle, qsThrottle,
            lblLogLevel, qsLogLevel,
            new Separator(),
            qsSkipCorrupt,
            qsDeepAttach,
            qsIgnoreEmpty,
            sepBranding,
            lblBrandingTitle,
            qsEnableBranding,
            lblCompanyName,
            qsCompanyName,
            lblContactInfo,
            qsContactInfo,
            lblLogoPath,
            logoBox
        );

        ScrollPane settingsScroll = new ScrollPane(scrollContent);
        settingsScroll.setFitToWidth(true);
        settingsScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        settingsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(settingsScroll, Priority.ALWAYS);

        this.getChildren().addAll(header, sep, settingsScroll);
    }

    /**
     * Refreshes all control values from SettingsManager to reflect current persisted state.
     */
    public void refresh() {
        isRefreshing = true;
        try {
            qsTheme.setValue(SettingsManager.getSetting("ui_theme", "System Default"));

            String th = SettingsManager.getSetting(SettingsManager.KEY_THREAD_COUNT, "4");
            boolean foundQs = false;
            for (String item : qsThreads.getItems()) {
                String itemNum = item.replaceAll("[^0-9]", "");
                if (itemNum.equals(th)) {
                    qsThreads.setValue(item);
                    foundQs = true;
                    break;
                }
            }
            if (!foundQs) {
                qsThreads.setValue(th);
            }

            qsThrottle.setValue(SettingsManager.getSetting("network_throttle", "Unlimited"));
            qsLogLevel.setValue(SettingsManager.getSetting("log_level", "INFO"));

            qsSkipCorrupt.setSelected("true".equals(SettingsManager.getSetting("skip_corrupt_items", "true")));
            qsDeepAttach.setSelected("true".equals(SettingsManager.getSetting("deep_attachment_scan", "true")));
            qsIgnoreEmpty.setSelected("true".equals(SettingsManager.getSetting("ignore_empty_folders", "false")));

            qsEnableBranding.setSelected("true".equals(SettingsManager.getSetting("pdf_branding_enabled", "false")));
            qsCompanyName.setText(SettingsManager.getSetting("pdf_branding_company", ""));
            qsContactInfo.setText(SettingsManager.getSetting("pdf_branding_contact", ""));
            qsLogoPath.setText(SettingsManager.getSetting("pdf_branding_logo", ""));
        } finally {
            isRefreshing = false;
        }
    }
}
