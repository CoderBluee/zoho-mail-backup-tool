package com.pstconverter.controller;

import com.pstconverter.core.model.SourceFileModel;
import com.pstconverter.util.SettingsManager;
import com.pstconverter.view.Step1ImportView;
import com.pstconverter.core.adapter.SourceAdapter;
import com.pstconverter.core.adapter.SourceAdapterFactory;
import com.pstconverter.view.Step2ExplorerView;
import com.pstconverter.view.Step3FilterView;
import com.pstconverter.view.Step4DestinationView;
import com.pstconverter.view.Step5ConversionView;
import com.pstconverter.view.Step6ReportView;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.io.File;
import java.text.DecimalFormat;
import java.util.List;

public class MainController extends BorderPane {

    private static final java.io.PrintStream ORIGINAL_OUT = System.out;
    private static final java.io.PrintStream ORIGINAL_ERR = System.err;

    private final Stage primaryStage;
    private final ObservableList<SourceFileModel> fileList;
    private int currentStep = 1;
    private String customDestinationParent = null;
    private String customExportFolderName = null;
    private boolean folderNameManuallyEdited = false;

    public boolean isFolderNameManuallyEdited() {
        return folderNameManuallyEdited;
    }

    public void setFolderNameManuallyEdited(boolean edited) {
        this.folderNameManuallyEdited = edited;
    }

    public void refreshFolderNameSuggestion() {
        if (!folderNameManuallyEdited) {
            String template = SettingsManager.getSetting("output_folder_template", "Mailbox_Export_[Timestamp]");
            if (!"Custom Name".equals(template)) {
                String dateStr = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
                String timestampStr = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                if ("Migration_[Date]".equals(template)) {
                    customExportFolderName = "Migration_" + dateStr;
                } else if ("Export_[Timestamp]".equals(template)) {
                    customExportFolderName = "Export_" + timestampStr;
                } else if ("Archive_[Timestamp]".equals(template)) {
                    customExportFolderName = "Archive_" + timestampStr;
                } else {
                    customExportFolderName = "Mailbox_Export_" + timestampStr;
                }
            } else {
                if (customExportFolderName == null) {
                    customExportFolderName = SettingsManager.getSetting("custom_export_folder_name", "Custom_Export");
                }
            }
        }
    }

    public String getCustomDestinationParent() {
        if (customDestinationParent != null && !customDestinationParent.trim().isEmpty()) {
            return customDestinationParent;
        }
        String parentPath = SettingsManager.getSetting(SettingsManager.KEY_CUSTOM_DESTINATION_PARENT, null);
        if (parentPath == null || parentPath.trim().isEmpty()) {
            parentPath = SettingsManager.getSetting(SettingsManager.KEY_DEFAULT_EXPORT_PATH, null);
        }
        if (parentPath == null || parentPath.trim().isEmpty()) {
            parentPath = System.getProperty("user.home") + File.separator + "Desktop";
        }
        customDestinationParent = parentPath;
        return customDestinationParent;
    }

    public void setCustomDestinationParent(String parent) {
        if (parent != null && !parent.trim().isEmpty()) {
            this.customDestinationParent = parent;
            SettingsManager.saveSetting(SettingsManager.KEY_CUSTOM_DESTINATION_PARENT, parent);
        }
    }

    public String getCustomExportFolderName() {
        if (customExportFolderName == null) {
            String template = SettingsManager.getSetting("output_folder_template", "Mailbox_Export_[Timestamp]");
            if ("Custom Name".equals(template)) {
                String savedName = SettingsManager.getSetting("custom_export_folder_name", null);
                if (savedName != null && !savedName.trim().isEmpty()) {
                    customExportFolderName = savedName;
                } else {
                    customExportFolderName = "Custom_Export";
                }
            } else {
                String dateStr = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
                String timestampStr = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                if ("Migration_[Date]".equals(template)) {
                    customExportFolderName = "Migration_" + dateStr;
                } else if ("Export_[Timestamp]".equals(template)) {
                    customExportFolderName = "Export_" + timestampStr;
                } else if ("Archive_[Timestamp]".equals(template)) {
                    customExportFolderName = "Archive_" + timestampStr;
                } else {
                    customExportFolderName = "Mailbox_Export_" + timestampStr;
                }
            }
        }
        return customExportFolderName;
    }

    public void setCustomExportFolderName(String name) {
        this.customExportFolderName = name;
        if (name != null && !name.trim().isEmpty()) {
            String template = SettingsManager.getSetting("output_folder_template", "Mailbox_Export_[Timestamp]");
            if ("Custom Name".equals(template) || folderNameManuallyEdited) {
                SettingsManager.saveSetting("custom_export_folder_name", name);
            }
        }
    }

    public String getCustomDestinationPath() {
        return getCustomDestinationParent() + File.separator + getCustomExportFolderName();
    }

    public void setCustomDestinationPath(String path) {
        if (path == null || path.trim().isEmpty()) return;
        File file = new File(path);
        String parent = file.getParent();
        String name = file.getName();
        if (parent != null) {
            setCustomDestinationParent(parent);
        }
        if (name != null) {
            setCustomExportFolderName(name);
        }
    }

    // Common Header Labels
    private Label titleLabel;
    private Label subtitleLabel;
    
    // Header Licensing Labels (used by updateAppHeaderLicensingState)
    private Label headerLicBadge;
    private Button btnActivateDeactivate;
    private Label lblBottomLicDetail;


    // Navigation Buttons
    private Button btnBack;
    private Button btnNext;

    private Label statusLabel;
    private File sessionDir;

    public void resetSession() {
        this.customExportFolderName = null;
        this.folderNameManuallyEdited = false;
        
        com.pstconverter.util.SettingsManager.saveSetting("export_field_subject", "true");
        com.pstconverter.util.SettingsManager.saveSetting("export_field_from", "true");
        com.pstconverter.util.SettingsManager.saveSetting("export_field_to", "true");
        com.pstconverter.util.SettingsManager.saveSetting("export_field_date", "true");
        com.pstconverter.util.SettingsManager.saveSetting("export_field_ccbcc", "true");
        com.pstconverter.util.SettingsManager.saveSetting("export_field_body", "true");
        
        if (step4DestinationView != null) {
            step4DestinationView.resetMetadataFieldsToDefault();
            step4DestinationView.resetCloudAccountState();
        }
        
        initSessionDirectory();
    }

    public void resumeMigrationSession(com.pstconverter.util.SettingsManager.MigrationSession session) {
        restoreSession(session, true);
    }

    public void rerunMigrationSession(com.pstconverter.util.SettingsManager.MigrationSession session) {
        restoreSession(session, false);
    }

    /**
     * Shared implementation for resume and re-run session workflows.
     * @param session The migration session to restore.
     * @param isResume If true, enables skip-already-migrated; if false, runs a clean re-export.
     */
    private void restoreSession(com.pstconverter.util.SettingsManager.MigrationSession session, boolean isResume) {
        if (session == null) return;

        // 1. Clear fileList, add the session's source file
        fileList.clear();
        File sourceFile = new File(session.sourceFilePath());
        if (!sourceFile.exists()) {
            if (session.sourceFilePath().toLowerCase().endsWith(".gmail") || session.sourceFilePath().toLowerCase().endsWith(".imap")) {
                try { sourceFile.createNewFile(); } catch (Exception ignored) {}
            } else {
                showAlert(Alert.AlertType.ERROR, "File Not Found", "The source file no longer exists at:\n" + session.sourceFilePath());
                return;
            }
        }

        // Add file to list
        addFileToList(sourceFile, "File", "Gmail Account");

        // 2. Deserialize properties
        java.util.Properties filterProps = new java.util.Properties();
        if (session.filterSettings() != null) {
            for (String line : session.filterSettings().split("\n")) {
                int idx = line.indexOf('=');
                if (idx > 0) {
                    filterProps.setProperty(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                }
            }
        }

        java.util.Properties outputProps = new java.util.Properties();
        if (session.outputProperties() != null) {
            for (String line : session.outputProperties().split("\n")) {
                int idx = line.indexOf('=');
                if (idx > 0) {
                    outputProps.setProperty(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                }
            }
        }

        // 3. Start analysis for Step 2
        if (step2ExplorerView != null) {
            step2ExplorerView.startAnalysis(() -> {
                // 4. Restore selected folders in Step 2
                String foldersStr = session.selectedFolders();
                if (foldersStr != null && !foldersStr.trim().isEmpty()) {
                    String[] folderArr = foldersStr.split(",");
                    step2ExplorerView.selectFoldersByKeys(java.util.Arrays.asList(folderArr));
                }

                // 5. Restore filter settings in Step 3
                if (step3FilterView != null) {
                    step3FilterView.loadFilterSettings(filterProps);
                }

                // 6. Restore destination settings in Step 4 and controller state
                setCustomDestinationPath(session.destinationPath());
                setFolderNameManuallyEdited(true);
                if (step4DestinationView != null) {
                    step4DestinationView.loadDestinationSettings(outputProps, session.destinationPath());
                    step4DestinationView.refreshDestinationPaths();
                }

                // 7. Transition directly to Step 5 (Conversion panel)
                showStep(5);

                // 8. Set resume flag appropriately
                if (step5ConversionView != null) {
                    step5ConversionView.setShouldAutoResume(isResume);
                    step5ConversionView.prepareConversionView();
                }
            });
        }
    }

    // Decoupled Views
    private Step1ImportView step1ImportView;
    private Step2ExplorerView step2ExplorerView;
    private Step3FilterView step3FilterView;
    private Step4DestinationView step4DestinationView;
    private Step5ConversionView step5ConversionView;
    private Step6ReportView step6ReportView;
    private HBox bottomBar;

    // Quick Settings Drawer
    private com.pstconverter.view.QuickSettingsDrawer quickSettingsDrawer;
    private boolean quickSettingsVisible = false;

    public MainController(Stage primaryStage) {
        initSessionDirectory();
        this.primaryStage = primaryStage;
        this.fileList = FXCollections.observableArrayList();

        // Initialize decoupled views first
        this.step1ImportView = new Step1ImportView(this);
        this.step2ExplorerView = new Step2ExplorerView(this);
        this.step3FilterView = new Step3FilterView(this);
        this.step4DestinationView = new Step4DestinationView(this);
        this.step5ConversionView = new Step5ConversionView(this);
        this.step6ReportView = new Step6ReportView(this);

        initializeUI();

        fileList.addListener((ListChangeListener<SourceFileModel>) c -> {
            updateStatusSummary();
            long validCount = fileList.stream().filter(SourceFileModel::isValid).count();
            btnNext.setDisable(validCount == 0);
        });
    }

    // SaaS Stepper Fields
    private final Label[] stepNumberBadges = new Label[6];
    private final Label[] stepTitleTexts = new Label[6];
    private final HBox[] stepPillBoxes = new HBox[6];
    private final Region[] stepConnectingLines = new Region[5];
    private HBox stepperBar;

    private void initializeUI() {
        this.setPadding(new Insets(16, 20, 16, 20));
        this.getStyleClass().add("root-pane");

        // --- TOP: Common SaaS Header ---
        StackPane brandEmblem = new StackPane();
        brandEmblem.setPrefSize(42, 42);
        brandEmblem.setMinSize(42, 42);
        brandEmblem.setMaxSize(42, 42);
        brandEmblem.setStyle("-fx-background-color: rgba(14, 165, 233, 0.12); -fx-background-radius: 11px; -fx-border-color: rgba(14, 165, 233, 0.35); -fx-border-radius: 11px; -fx-effect: dropshadow(three-pass-box, rgba(14, 165, 233, 0.35), 8, 0, 0, 2);");
        
        javafx.scene.image.ImageView logoView = new javafx.scene.image.ImageView();
        try {
            javafx.scene.image.Image logo = new javafx.scene.image.Image(getClass().getResourceAsStream("/images/logo.png"));
            logoView.setImage(logo);
            logoView.setFitWidth(36);
            logoView.setFitHeight(36);
            logoView.setPreserveRatio(true);
            logoView.setSmooth(true);
            if (primaryStage != null && primaryStage.getIcons().isEmpty()) {
                primaryStage.getIcons().add(logo);
            }
        } catch (Exception ignored) {}
        brandEmblem.getChildren().add(logoView);

        VBox titleBox = new VBox(3);
        titleLabel = new Label(com.pstconverter.config.BrandConfig.TOOL_NAME);
        titleLabel.getStyleClass().add("title-label");

        headerLicBadge = new Label();
        headerLicBadge.setCursor(Cursor.HAND);
        headerLicBadge.setOnMouseClicked(e -> handleShowSettings(true));

        Label editionBadge = new Label("ENTERPRISE v2026");
        editionBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.14); -fx-border-color: rgba(16, 185, 129, 0.4); -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-text-fill: #10b981; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2px 7px;");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.getChildren().addAll(titleLabel, editionBadge, headerLicBadge);

        subtitleLabel = new Label("Professional Mailbox Format Migration & Validation Suite");
        subtitleLabel.getStyleClass().add("subtitle-label");
        titleBox.getChildren().addAll(titleRow, subtitleLabel);

        HBox headerLeft = new HBox(12, brandEmblem, titleBox);
        headerLeft.setAlignment(Pos.CENTER_LEFT);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button btnQuickSettings = new Button();
        btnQuickSettings.getStyleClass().addAll("action-btn", "btn-secondary");
        btnQuickSettings.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.MENU, 16));
        btnQuickSettings.setStyle("-fx-padding: 8px 12px; -fx-background-radius: 8px; -fx-cursor: hand;");
        btnQuickSettings.setTooltip(new Tooltip("Toggle Quick Settings Drawer"));
        btnQuickSettings.setOnAction(e -> toggleQuickSettings());

        Button btnSettings = new Button();
        btnSettings.getStyleClass().addAll("action-btn", "btn-secondary");
        btnSettings.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.SETTINGS, 16));
        btnSettings.setStyle("-fx-padding: 8px 12px; -fx-background-radius: 8px; -fx-cursor: hand;");
        btnSettings.setTooltip(new Tooltip("Global Settings & Licensing"));
        btnSettings.setOnAction(e -> handleShowSettings(false));

        Button btnHelp = new Button("Live Help");
        btnHelp.getStyleClass().addAll("action-btn", "btn-secondary");
        btnHelp.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.HELP, 16));
        btnHelp.setStyle("-fx-padding: 8px 14px; -fx-background-radius: 8px; -fx-cursor: hand;");
        btnHelp.setTooltip(new Tooltip("Open Live Support Chat"));
        btnHelp.setOnAction(e -> openHelpChat());

        btnActivateDeactivate = new Button();
        btnActivateDeactivate.getStyleClass().addAll("action-btn");
        btnActivateDeactivate.setStyle("-fx-font-weight: bold; -fx-padding: 8px 16px; -fx-font-size: 12px; -fx-background-radius: 8px; -fx-cursor: hand;");
        btnActivateDeactivate.setOnAction(e -> handleActivateDeactivateClick());

        HBox headerHBox = new HBox(10);
        headerHBox.setPadding(new Insets(0, 0, 10, 0));
        headerHBox.setAlignment(Pos.CENTER_LEFT);
        headerHBox.getChildren().addAll(headerLeft, headerSpacer, btnHelp, btnActivateDeactivate, btnQuickSettings, btnSettings);

        // --- MODERN SAAS HORIZONTAL STEPPER TIMELINE ---
        stepperBar = createSaaSStepper();

        VBox topContainer = new VBox(10, headerHBox, stepperBar);
        this.setTop(topContainer);

        // Navigation buttons
        btnBack = new Button("Back");
        btnBack.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.ARROW_BACK, 15));
        btnBack.getStyleClass().addAll("action-btn", "btn-secondary");
        btnBack.setStyle("-fx-padding: 9px 20px; -fx-background-radius: 8px; -fx-cursor: hand; -fx-font-weight: bold;");
        btnBack.setOnAction(e -> handleBack());
        btnBack.setDisable(true);

        btnNext = new Button("Next");
        btnNext.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.ARROW_FORWARD, 15));
        btnNext.setContentDisplay(ContentDisplay.RIGHT);
        btnNext.getStyleClass().addAll("action-btn", "btn-primary");
        btnNext.setStyle("-fx-padding: 9px 24px; -fx-background-radius: 8px; -fx-cursor: hand; -fx-font-weight: bold;");
        btnNext.setOnAction(e -> handleNext());
        btnNext.setDisable(true);

        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                applyTheme(SettingsManager.getSetting("ui_theme", "System Default"));
            }
        });

        bottomBar = new HBox(15);
        bottomBar.setPadding(new Insets(14, 0, 0, 0));
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("No mailboxes connected.");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600;");

        lblBottomLicDetail = new Label();
        lblBottomLicDetail.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #94a3b8;");

        VBox statusBox = new VBox(3);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.getChildren().addAll(statusLabel, lblBottomLicDetail);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomBar.getChildren().addAll(btnBack, statusBox, spacer, btnNext);
        this.setBottom(bottomBar);

        updateAppHeaderLicensingState();
        showStep(1);
        updateStatusSummary();
    }

    private HBox createSaaSStepper() {
        HBox container = new HBox(8);
        container.setAlignment(Pos.CENTER_LEFT);
        container.getStyleClass().add("stepper-bar-container");

        String[] stepTitles = {
            "1. Connect Accounts",
            "2. Explore Folders",
            "3. Smart Filters",
            "4. Target Destination",
            "5. Live Migration",
            "6. Summary Report"
        };

        for (int i = 0; i < 6; i++) {
            final int stepIndex = i + 1;
            HBox pill = new HBox(8);
            pill.setAlignment(Pos.CENTER_LEFT);
            pill.getStyleClass().add("stepper-step");

            Label numBadge = new Label(String.valueOf(stepIndex));
            numBadge.getStyleClass().add("stepper-step-number");
            stepNumberBadges[i] = numBadge;

            Label titleLbl = new Label(stepTitles[i]);
            titleLbl.getStyleClass().add("stepper-step-title");
            stepTitleTexts[i] = titleLbl;

            pill.getChildren().addAll(numBadge, titleLbl);
            pill.setOnMouseClicked(e -> {
                if (stepIndex < currentStep && currentStep != 5) {
                    showStep(stepIndex);
                }
            });

            stepPillBoxes[i] = pill;
            HBox.setHgrow(pill, Priority.ALWAYS);
            container.getChildren().add(pill);

            if (i < 5) {
                Region line = new Region();
                line.getStyleClass().add("stepper-line");
                line.setMinWidth(16);
                line.setPrefWidth(24);
                line.setMaxWidth(36);
                stepConnectingLines[i] = line;
                container.getChildren().add(line);
            }
        }

        return container;
    }

    public void updateStepper(int activeStep) {
        if (stepperBar == null) return;
        boolean dark = isDarkMode();

        for (int i = 0; i < 6; i++) {
            int stepNum = i + 1;
            HBox pill = stepPillBoxes[i];
            Label badge = stepNumberBadges[i];
            Label title = stepTitleTexts[i];
            if (pill == null || badge == null || title == null) continue;

            pill.getStyleClass().removeAll("stepper-step-active", "stepper-step-completed");

            if (stepNum < activeStep) {
                // Completed step
                pill.getStyleClass().add("stepper-step-completed");
                badge.setText("✓");
                badge.setStyle("-fx-background-color: #10b981; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-alignment: center; -fx-background-radius: 11px; -fx-font-size: 11px;");
                title.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 600; -fx-text-fill: " + (dark ? "#e2e8f0" : "#1e293b") + ";");
                if (i < 5 && stepConnectingLines[i] != null) {
                    stepConnectingLines[i].setStyle("-fx-background-color: #10b981; -fx-pref-height: 2px; -fx-min-height: 2px; -fx-max-height: 2px;");
                }
            } else if (stepNum == activeStep) {
                // Active step
                pill.getStyleClass().add("stepper-step-active");
                badge.setText(String.valueOf(stepNum));
                badge.setStyle("-fx-background-color: #0ea5e9; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-alignment: center; -fx-background-radius: 11px; -fx-font-size: 11px; -fx-effect: dropshadow(gaussian, rgba(14, 165, 233, 0.5), 8, 0, 0, 0);");
                title.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 800; -fx-text-fill: #0ea5e9;");
                if (i < 5 && stepConnectingLines[i] != null) {
                    stepConnectingLines[i].setStyle("-fx-background-color: rgba(148, 163, 184, 0.2); -fx-pref-height: 2px; -fx-min-height: 2px; -fx-max-height: 2px;");
                }
            } else {
                // Pending step
                badge.setText(String.valueOf(stepNum));
                badge.setStyle("-fx-background-color: " + (dark ? "rgba(148, 163, 184, 0.15)" : "#f1f5f9") + "; -fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-alignment: center; -fx-background-radius: 11px; -fx-font-size: 11px;");
                title.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 600; -fx-text-fill: #94a3b8;");
                if (i < 5 && stepConnectingLines[i] != null) {
                    stepConnectingLines[i].setStyle("-fx-background-color: rgba(148, 163, 184, 0.2); -fx-pref-height: 2px; -fx-min-height: 2px; -fx-max-height: 2px;");
                }
            }
        }
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void setNextButtonDisable(boolean disable) {
        if (btnNext != null) {
            btnNext.setDisable(disable);
        }
    }

    public void updateStatusLabel(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }

    public ObservableList<SourceFileModel> getFileList() {
        return fileList;
    }

    public File getSessionDir() {
        return sessionDir;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void showStep(int step) {
        this.currentStep = step;
        updateStepper(step);
        if (step <= 4) {
            if (bottomBar != null) {
                bottomBar.setVisible(true);
                bottomBar.setManaged(true);
            }
        } else {
            if (bottomBar != null) {
                bottomBar.setVisible(false);
                bottomBar.setManaged(false);
            }
        }
        if (step == 1) {
            customExportFolderName = null;
            folderNameManuallyEdited = false;
            if (step4DestinationView != null) {
                step4DestinationView.resetCloudAccountState();
            }
            this.setCenter(step1ImportView);
            if (step1ImportView != null) {
                step1ImportView.refreshRecentMailboxes();
            }
            btnBack.setVisible(false);
            btnBack.setManaged(false);
            titleLabel.setText(com.pstconverter.config.BrandConfig.TOOL_NAME);
            subtitleLabel.setText("Professional Mailbox Format Migration & Validation Suite");
            if (btnNext != null) {
                btnNext.setVisible(true);
                btnNext.setManaged(true);
                long validCount = fileList.stream().filter(SourceFileModel::isValid).count();
                btnNext.setDisable(validCount == 0);
                btnNext.setText("Next");
            }
            updateStatusSummary();
        } else if (step == 2) {
            this.setCenter(step2ExplorerView);
            btnBack.setVisible(true);
            btnBack.setManaged(true);
            btnBack.setDisable(false);
            if (btnNext != null) {
                btnNext.setVisible(true);
                btnNext.setManaged(true);
                btnNext.setText("Next");
                btnNext.setDisable(false);
            }
            titleLabel.setText("Mailbox Data & Folder Explorer");
            subtitleLabel.setText("Navigate through imported mailbox files and folder structures.");
        } else if (step == 3) {
            this.setCenter(step3FilterView);
            btnBack.setVisible(true);
            btnBack.setManaged(true);
            btnBack.setDisable(false);
            if (btnNext != null) {
                btnNext.setVisible(true);
                btnNext.setManaged(true);
                btnNext.setText("Next");
                btnNext.setDisable(false);
            }
            titleLabel.setText("Advanced Migration Filters");
            subtitleLabel.setText("Refine which data gets exported — apply date, sender, keyword, attachment & hygiene filters.");
            step3FilterView.refreshAvailableItemTypes();
            step3FilterView.updateFilterSummary();
            step3FilterView.validateFilters();
        } else if (step == 4) {
            refreshFolderNameSuggestion();
            step4DestinationView.refreshDestinationPaths();
            step4DestinationView.resetMetadataFieldsToDefault();
            this.setCenter(step4DestinationView);
            btnBack.setVisible(true);
            btnBack.setManaged(true);
            btnBack.setDisable(false);
            if (btnNext != null) {
                btnNext.setVisible(true);
                btnNext.setManaged(true);
                btnNext.setText("Proceed to Dashboard");
                btnNext.setDisable(false);
            }
            titleLabel.setText("Destination & Output Selection");
            subtitleLabel.setText("Select your output format, configure paths, or direct cloud migration details.");
            step4DestinationView.validateDestination();
        } else if (step == 5) {
            this.setCenter(step5ConversionView);
            // Hide all navigation during conversion
            btnBack.setVisible(false);
            btnBack.setManaged(false);
            btnNext.setVisible(false);
            btnNext.setManaged(false);
            titleLabel.setText("Active Migration — Converting Your Data");
            subtitleLabel.setText("Your mailbox data is being exported. Monitor live progress below.");
        } else if (step == 6) {
            this.setCenter(step6ReportView);
            // Hide navigation in report panel too
            btnBack.setVisible(false);
            btnBack.setManaged(false);
            btnNext.setVisible(false);
            btnNext.setManaged(false);
            titleLabel.setText("Migration Summary Report");
            subtitleLabel.setText("Your mailbox data migration is complete. Review details below.");
            step6ReportView.loadReportData();
        }
    }

    private void handleBack() {
        if (currentStep == 5 || currentStep == 6) {
            return; // Cannot go back during conversion or from report
        } else if (currentStep == 2) {
            showStep(1);
        } else if (currentStep == 3) {
            showStep(2);
        } else if (currentStep == 4) {
            showStep(3);
        }
    }

    private void handleNext() {
        if (currentStep == 1) {
            if (step1ImportView != null) {
                boolean ready = step1ImportView.prepareFileListForNextStep();
                if (!ready) {
                    return;
                }
            }
            long validCount = fileList.stream().filter(SourceFileModel::isValid).count();
            if (validCount == 0) {
                showAlert(Alert.AlertType.WARNING, "No Account Selected",
                        "Please select at least one connected Google account using the checkbox before proceeding.");
                return;
            }
            
            System.out.println("================================================================================");
            System.out.println("STEP 1 -> STEP 2: SOURCE FILES IMPORTED");
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("Total Files in Queue: " + fileList.size());
            for (int i = 0; i < fileList.size(); i++) {
                SourceFileModel f = fileList.get(i);
                System.out.println(String.format("  File [%d]: %s", i + 1, f.getFilePath()));
                System.out.println(String.format("    Name: %s | Size: %s | Status: %s | Source Type: %s", 
                    f.getFileName(), f.getFileSize(), f.getStatus(), f.getSourceType()));
            }
            System.out.println("================================================================================");
            // Mirror detailed file import state to the DiagnosticLogger report
            com.pstconverter.util.DiagnosticLogger.logStep1to2(new java.util.ArrayList<>(fileList));
            
            step2ExplorerView.startAnalysis(() -> showStep(2));
        } else if (currentStep == 2) {
            if (!step2ExplorerView.hasSelectedFolders()) {
                showAlert(Alert.AlertType.WARNING, "No Folders Selected",
                        "Please select at least one mailbox folder in the tree before proceeding to filters.");
                return;
            }
            step2ExplorerView.autoSaveSelectedTreeToSession();
            
            // Log folder selection details directly to standard output (session_run.log)
            System.out.println("================================================================================");
            System.out.println("STEP 2 -> STEP 3: SELECTED SOURCE FOLDERS");
            System.out.println("--------------------------------------------------------------------------------");
            List<TreeItem<String>> checkedItems = step2ExplorerView.getCheckedTreeItems();
            System.out.println("Number of folders checked: " + checkedItems.size());
            for (int i = 0; i < checkedItems.size(); i++) {
                System.out.println("  Folder [" + (i + 1) + "]: " + checkedItems.get(i).getValue());
            }
            System.out.println("================================================================================");
            // Mirror folder selection state to DiagnosticLogger with total-folders context
            int _totalFoldersInTree = step2ExplorerView.getTotalFolderCount();
            com.pstconverter.util.DiagnosticLogger.logStep2to3(checkedItems, _totalFoldersInTree);
            
            showStep(3);
        } else if (currentStep == 3) {
            if (!step3FilterView.validateFilters()) {
                showAlert(Alert.AlertType.WARNING, "Invalid Filter Settings",
                        "Please resolve all active validation errors before proceeding.");
                return;
            }
            step3FilterView.saveFilterSettings();
            
            // Log active filter properties directly to standard output (session_run.log)
            java.util.Properties props = step3FilterView.getFilterProperties();
            System.out.println("================================================================================");
            System.out.println("STEP 3 -> STEP 4: SELECTED FILTERS");
            System.out.println("--------------------------------------------------------------------------------");
            if (props == null || props.isEmpty()) {
                System.out.println("  No filters configured / active.");
            } else {
                for (String key : new java.util.TreeSet<>(props.stringPropertyNames())) {
                    System.out.println("  " + key + " = " + props.getProperty(key));
                }
            }
            System.out.println("================================================================================");
            // Mirror all active filter settings to the DiagnosticLogger report
            com.pstconverter.util.DiagnosticLogger.logStep3to4(props);
            
            showStep(4);
        } else if (currentStep == 4) {
            if (!step4DestinationView.validateDestination()) {
                showAlert(Alert.AlertType.WARNING, "Invalid Output Settings",
                        "Please fill in all required destination details.");
                return;
            }
            showStep(5);
            
            // Log destination and output parameters directly to standard output (session_run.log)
            String format = step4DestinationView.getSelectedFormat();
            String outputPath = getCustomDestinationPath();
            String attachHandling = step4DestinationView.getAttachmentHandling();
            System.out.println("================================================================================");
            System.out.println("STEP 4 -> STEP 5: DESTINATION & OUTPUT SETTINGS");
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("  Output Format: " + format);
            System.out.println("  Target Output Folder: " + outputPath);
            System.out.println("  Attachment Handling Strategy: " + attachHandling);
            System.out.println("  File Output Structure: " + step4DestinationView.getExportStructure());
            System.out.println("  File Naming Rules: " + step4DestinationView.getNamingConvention());
            System.out.println("  Metadata Fields to Include:");
            System.out.println("    Subject: " + SettingsManager.getSetting("export_field_subject", "true"));
            System.out.println("    From: " + SettingsManager.getSetting("export_field_from", "true"));
            System.out.println("    To: " + SettingsManager.getSetting("export_field_to", "true"));
            System.out.println("    Date: " + SettingsManager.getSetting("export_field_date", "true"));
            System.out.println("    CC/BCC: " + SettingsManager.getSetting("export_field_ccbcc", "true"));
            System.out.println("    Body: " + SettingsManager.getSetting("export_field_body", "true"));
            System.out.println("================================================================================");
            System.out.println("STEP 5 - LIVE CONVERSION LOG");
            System.out.println("--------------------------------------------------------------------------------");
            // Mirror destination & output settings to DiagnosticLogger report
            java.util.Map<String, String> _metaFields = new java.util.LinkedHashMap<>();
            _metaFields.put("Subject", SettingsManager.getSetting("export_field_subject", "true"));
            _metaFields.put("From",    SettingsManager.getSetting("export_field_from",    "true"));
            _metaFields.put("To",      SettingsManager.getSetting("export_field_to",      "true"));
            _metaFields.put("Date",    SettingsManager.getSetting("export_field_date",    "true"));
            _metaFields.put("CC/BCC",  SettingsManager.getSetting("export_field_ccbcc",   "true"));
            _metaFields.put("Body",    SettingsManager.getSetting("export_field_body",    "true"));
            com.pstconverter.util.DiagnosticLogger.logStep4to5(
                format, outputPath, attachHandling,
                step4DestinationView.getExportStructure(),
                step4DestinationView.getNamingConvention(),
                _metaFields);
            
            step5ConversionView.prepareConversionView();
        }
    }

    public List<String> getSelectedSenders() {
        return step2ExplorerView.getSelectedSenders();
    }

    public List<String> getSelectedRecipients() {
        return step2ExplorerView.getSelectedRecipients();
    }

    public boolean hasSelectedFolders() {
        return step2ExplorerView != null && step2ExplorerView.hasSelectedFolders();
    }

    /** Expose Step2 view so conversion panel can read the actual checked tree. */
    public Step2ExplorerView getStep2ExplorerView() {
        return step2ExplorerView;
    }

    /** Expose Step4 view so conversion panel can read the configured output path. */
    public Step4DestinationView getStep4DestinationView() {
        return step4DestinationView;
    }

    /** Expose Step3 view so conversion panel can read filter settings. */
    public Step3FilterView getStep3FilterView() {
        return step3FilterView;
    }

    public Step5ConversionView getStep5ConversionView() {
        return step5ConversionView;
    }

    public Step6ReportView getStep6ReportView() {
        return step6ReportView;
    }

    public void handleAutoDetect() {
        System.out.println("User triggered auto-detection of local mailboxes.");
        showNotification("Auto-detecting local mailboxes...");
        List<File> detected = SourceAdapterFactory.detectAllLocalMailboxes();
        if (detected.isEmpty()) {
            System.out.println("Auto-detection finished: no local mailboxes found.");
            showAlert(Alert.AlertType.INFORMATION, "Auto-Detection",
                    "No local mailboxes were found in the standard directories.");
            showNotification("Auto-detection finished: 0 files found.");
        } else {
            System.out.println("Auto-detection found " + detected.size() + " mailbox file(s):");
            for (File file : detected) {
                System.out.println("  Detected path: " + file.getAbsolutePath());
            }
            int addedCount = 0;
            int duplicateCount = 0;
            for (File file : detected) {
                if (addFileToList(file, "File", "")) {
                    addedCount++;
                } else {
                    duplicateCount++;
                    System.out.println("Skipped auto-detected file (Duplicate in queue): " + file.getAbsolutePath());
                }
            }
            System.out.println("Auto-detect import complete. Successfully added: " + addedCount + ", skipped duplicates: " + duplicateCount);
            showAlert(Alert.AlertType.INFORMATION, "Auto-Detection Complete",
                    String.format(
                            "Successfully detected and processed Outlook folders.\nAdded: %d file(s).\nDuplicates/Skip: %d file(s).",
                            addedCount, duplicateCount));
            showNotification(String.format("Auto-detection complete. Added %d files.", addedCount));
        }
    }

    public boolean addFileToList(File file, String importType, String folderName) {
        for (SourceFileModel item : fileList) {
            if (item.getFilePath().equalsIgnoreCase(file.getAbsolutePath())) {
                return false;
            }
        }

        String name = file.getName();
        String path = file.getAbsolutePath();
        String size = getFormattedFileSize(file.length());

        if (name.toLowerCase().endsWith(".gmail") || name.toLowerCase().endsWith(".imap")) {
            if (name.toLowerCase().endsWith(".gmail")) {
                name = name.substring(0, name.length() - 6).trim();
            } else {
                name = name.substring(0, name.length() - 5).trim();
            }
            size = "Online Account";
            if (folderName == null || folderName.trim().isEmpty()) {
                folderName = "IMAP Account";
            }
        }

        String status = "Valid";
        boolean isValid = true;

        if (!file.exists()) {
            status = "File Not Found";
            isValid = false;
        } else if (!SourceAdapterFactory.isSupported(file)) {
            status = "Unsupported Extension";
            isValid = false;
        } else {
            SourceAdapter adapter = SourceAdapterFactory.getAdapterForFile(file);
            if (adapter == null || !adapter.validateFile(file)) {
                status = "Corrupt/Invalid Mailbox";
                isValid = false;
            }
        }

        String sourceType = "";
        if (isValid) {
            SourceAdapter adapter = SourceAdapterFactory.getAdapterForFile(file);
            if (adapter != null) {
                sourceType = adapter.getSourceType();
            }
            // Log source file details directly to session_run.log
            System.out.println("================================================================================");
            System.out.println("SOURCE FILE IMPORTED");
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("  File Name:   " + name);
            System.out.println("  File Path:   " + path);
            System.out.println("  File Size:   " + size);
            System.out.println("  Source Type: " + sourceType);
            System.out.println("================================================================================");
        } else {
            // Log validation failure to standard error stream (session_run.log)
            System.err.println("================================================================================");
            System.err.println("SOURCE FILE IMPORT VALIDATION FAILURE");
            System.err.println("--------------------------------------------------------------------------------");
            System.err.println("  File Name:      " + name);
            System.err.println("  File Path:      " + path);
            System.err.println("  File Size:      " + size);
            System.err.println("  Failure Status: " + status);
            System.err.println("================================================================================");
        }

        SourceFileModel model = new SourceFileModel(path, name, size, status, isValid, importType, folderName, sourceType);
        fileList.add(model);
        return true;
    }

    public String getFormattedFileSize(long bytes) {
        if (bytes <= 0)
            return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public void updateStatusSummary() {
        if (statusLabel == null) return;
        int totalFiles = fileList.size();
        long validFiles = fileList.stream().filter(SourceFileModel::isValid).count();

        if (totalFiles == 0) {
            statusLabel.setText("No mailboxes connected.");
        } else {
            statusLabel.setText(String.format("Queue: %d mailbox account(s) ready.", totalFiles));
        }
        if (btnNext != null) {
            btnNext.setDisable(validFiles == 0);
        }
    }


    private void applyTheme(String themeName) {
        com.pstconverter.util.ThemeManager.applyTheme(getScene(), themeName);
    }

    public String getActiveThemeStylesheet() {
        return com.pstconverter.util.ThemeManager.getActiveThemeStylesheet();
    }

    public boolean isDarkMode() {
        return com.pstconverter.util.ThemeManager.isDarkMode();
    }

    public void showNotification(String message) {
        if (message == null || message.isEmpty()) return;
        statusLabel.setText(message);
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(3.5), e -> updateStatusSummary())
        );
        timeline.play();
    }

    public void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.initOwner(primaryStage);

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("custom-alert-dialog");
        try {
            dialogPane.getStylesheets().add(getActiveThemeStylesheet());
        } catch (Exception ignored) {}

        alert.showAndWait();
    }

    private void initSessionDirectory() {
        try {
            File documentsDir = new File(System.getProperty("user.home"), "Documents");
            File toolDir = new File(documentsDir, com.pstconverter.config.BrandConfig.REPORT_DIR_NAME);
            if (!toolDir.exists()) {
                toolDir.mkdirs();
            }
            String timeStamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            sessionDir = new File(toolDir, "Session_" + timeStamp);
            if (!sessionDir.exists()) {
                sessionDir.mkdirs();
            }

            File logsDir = new File(sessionDir, "logs");
            logsDir.mkdirs();

            // All migration metadata goes into migration_data/
            File migrationDataDir = new File(sessionDir, "migration_data");
            migrationDataDir.mkdirs();

            File logFile = new File(logsDir, "session_run.log");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(logFile);

            // Fetch the setting BEFORE redirecting standard streams to avoid classloader / print re-entrancy deadlock
            String initialLogLevel = SettingsManager.getSetting("log_level", "INFO");
            TimestampedOutputStream.setActiveLogLevel(initialLogLevel);

            System.setOut(new java.io.PrintStream(new TimestampedOutputStream(fos, ORIGINAL_OUT, "[INFO]"), true, java.nio.charset.StandardCharsets.UTF_8));
            System.setErr(new java.io.PrintStream(new TimestampedOutputStream(fos, ORIGINAL_ERR, "[ERROR]"), true, java.nio.charset.StandardCharsets.UTF_8));

            System.out.println("================================================================================");
            System.out.println(com.pstconverter.config.BrandConfig.TOOL_NAME.toUpperCase() + " - APPLICATION STARTUP");
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("Operating System: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
            System.out.println("Java Version:     " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
            long maxMem = Runtime.getRuntime().maxMemory();
            long totalMem = Runtime.getRuntime().totalMemory();
            long freeMem = Runtime.getRuntime().freeMemory();
            System.out.println("Heap Memory:      Max " + (maxMem / (1024 * 1024)) + "MB / Allocated " + (totalMem / (1024 * 1024)) + "MB / Free " + (freeMem / (1024 * 1024)) + "MB");
            System.out.println("Session Directory: " + sessionDir.getAbsolutePath());
            System.out.println("Console output redirected to: " + logFile.getAbsolutePath());
            System.out.println("================================================================================");
        } catch (Exception e) {
            System.err.println("Failed to initialize session directory or redirect logs: " + e.getMessage());
        }
    }

    private void handleShowSettings(boolean openToLicensing) {
        new com.pstconverter.view.SettingsDialog(this, primaryStage).show(openToLicensing);
    }


    public void updateAppHeaderLicensingState() {
        boolean active = com.pstconverter.util.LicenseManager.isActivated();
        String type = com.pstconverter.util.LicenseManager.getLicenseType();
        int days = com.pstconverter.util.LicenseManager.getDaysRemaining();
        boolean dark = isDarkMode();
        
        if (headerLicBadge != null) {
            headerLicBadge.setText("");
            headerLicBadge.setGraphic(null);
            if (active) {
                headerLicBadge.setText(" " + type + " TIER ");
                headerLicBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.1); " +
                                        "-fx-text-fill: #10b981; " +
                                        "-fx-border-color: rgba(16, 185, 129, 0.35); " +
                                        "-fx-border-width: 1px; " +
                                        "-fx-border-radius: 12px; " +
                                        "-fx-background-radius: 12px; " +
                                        "-fx-padding: 3px 8px; " +
                                        "-fx-font-weight: bold; " +
                                        "-fx-font-size: 10px;");
                headerLicBadge.setGraphic(com.pstconverter.util.MaterialIcons.icon(
                    "ENTERPRISE".equals(type) ? com.pstconverter.util.MaterialIcons.DONE_ALL : com.pstconverter.util.MaterialIcons.CHECK_CIRCLE, 10
                ));
                if (headerLicBadge.getGraphic() instanceof Label lbl) {
                    lbl.setStyle("-fx-text-fill: #10b981; -fx-font-size: 10px;");
                }
            } else {
                headerLicBadge.setText(" TRIAL MODE ");
                headerLicBadge.setStyle("-fx-background-color: rgba(245, 158, 11, 0.1); " +
                                        "-fx-text-fill: #f59e0b; " +
                                        "-fx-border-color: rgba(245, 158, 11, 0.35); " +
                                        "-fx-border-width: 1px; " +
                                        "-fx-border-radius: 12px; " +
                                        "-fx-background-radius: 12px; " +
                                        "-fx-padding: 3px 8px; " +
                                        "-fx-font-weight: bold; " +
                                        "-fx-font-size: 10px;");
                headerLicBadge.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.WARNING, 10));
                if (headerLicBadge.getGraphic() instanceof Label lbl) {
                    lbl.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 10px;");
                }
            }
        }

        if (btnActivateDeactivate != null) {
            if (active) {
                btnActivateDeactivate.setText("Deactivate License");
                btnActivateDeactivate.getStyleClass().removeAll("btn-accent", "btn-danger");
                btnActivateDeactivate.getStyleClass().add("btn-danger");
                btnActivateDeactivate.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.LOCK, 14));
            } else {
                btnActivateDeactivate.setText("Activate License");
                btnActivateDeactivate.getStyleClass().removeAll("btn-accent", "btn-danger");
                btnActivateDeactivate.getStyleClass().add("btn-accent");
                btnActivateDeactivate.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.LOCK_OPEN, 14));
            }
        }
        
        if (titleLabel != null) {
            if (active) {
                if ("ENTERPRISE".equals(type)) {
                    titleLabel.setStyle("-fx-text-fill: linear-gradient(to right, #10b981, #06b6d4); -fx-font-weight: bold;");
                } else if ("BUSINESS".equals(type)) {
                    titleLabel.setStyle("-fx-text-fill: linear-gradient(to right, #6366f1, #d946ef); -fx-font-weight: bold;");
                } else {
                    titleLabel.setStyle("-fx-text-fill: linear-gradient(to right, #3b82f6, #6366f1); -fx-font-weight: bold;");
                }
            } else {
                titleLabel.setStyle("-fx-text-fill: " + (dark ? "#ffffff" : "#1e293b") + "; -fx-font-weight: bold;");
            }
        }
        
        if (subtitleLabel != null) {
            if (active) {
                String desc = type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase() + " Edition";
                String limitText = days == -1 ? "Never Expires (Lifetime License)" : days + " days remaining";
                subtitleLabel.setText(desc + " — " + limitText + " — " + (com.pstconverter.util.LicenseManager.getMaxActivations() - com.pstconverter.util.LicenseManager.getActivationsRemaining()) + " of " + com.pstconverter.util.LicenseManager.getMaxActivations() + " PCs Activated");
            } else {
                subtitleLabel.setText("Free Trial Edition — " + days + " days remaining — Capped at " + com.pstconverter.util.LicenseManager.TRIAL_LIMIT_PER_FOLDER + " email exports per folder");
            }
        }

        if (lblBottomLicDetail != null) {
            if (active) {
                String key = SettingsManager.getSetting(com.pstconverter.util.LicenseManager.LICENSE_KEY_SETTING, "");
                String maskedKey = "";
                if (key != null && !key.trim().isEmpty()) {
                    String[] parts = key.split("-");
                    if (parts.length == 5) {
                        maskedKey = parts[0] + "-" + parts[1] + "-****-****-" + parts[4];
                    } else {
                        maskedKey = key;
                    }
                }
                int maxAct = com.pstconverter.util.LicenseManager.getMaxActivations();
                int remaining = com.pstconverter.util.LicenseManager.getActivationsRemaining();
                String expiryText = com.pstconverter.util.LicenseManager.getExpirationDateText();
                String limitText = days == -1 ? "Lifetime" : expiryText + " (" + days + " days remaining)";
                
                String detailText = "Activation: " + type + " Edition" + 
                                   (!maskedKey.isEmpty() ? " | Key: " + maskedKey : "") +
                                   " | Expiry: " + limitText + 
                                   " | Seats: " + (maxAct - remaining) + " of " + maxAct + " occupied";
                lblBottomLicDetail.setText(detailText);
            } else {
                String expiryText = com.pstconverter.util.LicenseManager.getExpirationDateText();
                lblBottomLicDetail.setText("Activation: Trial Mode | Expiry: " + expiryText + " (" + days + " days remaining) | Limit: " + com.pstconverter.util.LicenseManager.TRIAL_LIMIT_PER_FOLDER + " exports per folder");
            }
        }
    }

    /**
     * Callback invoked by SettingsDialog after settings are saved,
     * to refresh the Quick Settings drawer if it is currently visible.
     */
    public void onSettingsSaved() {
        if (quickSettingsVisible) {
            refreshQuickSettingsValues();
        }
    }

    public void toggleQuickSettings() {
        quickSettingsVisible = !quickSettingsVisible;
        if (quickSettingsVisible) {
            if (quickSettingsDrawer == null) {
                quickSettingsDrawer = new com.pstconverter.view.QuickSettingsDrawer(this);
            }
            quickSettingsDrawer.refresh();
            this.setRight(quickSettingsDrawer);
        } else {
            this.setRight(null);
        }
    }

    private void refreshQuickSettingsValues() {
        if (quickSettingsDrawer != null) {
            quickSettingsDrawer.refresh();
        }
    }

    public static class TimestampedOutputStream extends java.io.OutputStream {
        private final java.io.OutputStream fileOut;
        private final java.io.OutputStream consoleOut;
        private final String level;
        private final java.io.ByteArrayOutputStream lineBuffer = new java.io.ByteArrayOutputStream();

        private static volatile java.util.function.Consumer<String> activeLogListener = null;
        private static volatile String activeLogLevel = "INFO";

        public static void setLogListener(java.util.function.Consumer<String> listener) {
            activeLogListener = listener;
        }

        public static java.util.function.Consumer<String> getLogListener() {
            return activeLogListener;
        }

        public static void setActiveLogLevel(String level) {
            activeLogLevel = level != null ? level : "INFO";
        }

        public TimestampedOutputStream(java.io.OutputStream fileOut, java.io.OutputStream consoleOut, String level) {
            this.fileOut = fileOut;
            this.consoleOut = consoleOut;
            this.level = level;
        }

        @Override
        public synchronized void write(int b) throws java.io.IOException {
            if (b != '\r' && b != '\n') {
                lineBuffer.write(b);
            } else if (b == '\n') {
                String line = lineBuffer.toString(java.nio.charset.StandardCharsets.UTF_8.name());
                lineBuffer.reset();
                
                if (shouldLog(line)) {
                    String ts = "[" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date()) + "] " + level + " " + line + "\n";
                    byte[] tsBytes = ts.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    fileOut.write(tsBytes);
                    consoleOut.write(tsBytes);
                    
                    var listener = activeLogListener;
                    if (listener != null) {
                        listener.accept(level + " " + line);
                    }
                }
            }
        }

        private boolean shouldLog(String line) {
            int configVal = getLevelValue(activeLogLevel);
            int lineVal = getLineLevelValue(line);
            return lineVal >= configVal;
        }

        private int getLevelValue(String lvl) {
            if (lvl == null) return 1;
            return switch (lvl.toUpperCase()) {
                case "DEBUG" -> 0;
                case "INFO" -> 1;
                case "WARN", "WARNING" -> 2;
                case "ERROR", "SEVERE" -> 3;
                default -> 1;
            };
        }

        private int getLineLevelValue(String line) {
            String upper = line.toUpperCase();
            if (upper.contains("[DEBUG]")) return 0;
            if (upper.contains("[INFO]")) return 1;
            if (upper.contains("[WARN") || upper.contains("WARNING") || upper.contains("WARN:")) return 2;
            if (upper.contains("[ERROR") || upper.contains("ERROR:") || upper.contains("FAILED") || upper.contains("EXCEPTION") || upper.contains("[SEVERE]") || upper.contains("SEVERE:")) return 3;
            
            // Fallback to the stream's native default level
            if (this.level != null) {
                return getLevelValue(this.level.replace("[", "").replace("]", ""));
            }
            return 1;
        }

        @Override
        public synchronized void flush() throws java.io.IOException {
            fileOut.flush();
            consoleOut.flush();
        }

        @Override
        public synchronized void close() throws java.io.IOException {
            try { fileOut.close(); } finally { consoleOut.close(); }
        }
    }

    private void handleActivateDeactivateClick() {
        boolean active = com.pstconverter.util.LicenseManager.isActivated();
        if (active) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirm Deactivation");
            confirmAlert.setHeaderText("Deactivate License?");
            confirmAlert.setContentText("Are you sure you want to deactivate your license key? This will return the application to Trial Evaluation Mode.");
            confirmAlert.initOwner(primaryStage);
            
            DialogPane dp = confirmAlert.getDialogPane();
            dp.getStyleClass().add("custom-alert-dialog");
            try {
                dp.getStylesheets().add(getActiveThemeStylesheet());
            } catch (Exception ignored) {}

            java.util.Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                com.pstconverter.util.LicenseManager.deactivate();
                updateAppHeaderLicensingState();
                showAlert(Alert.AlertType.INFORMATION, "License Deactivated", "The application has been successfully returned to Trial Evaluation Mode.");
                promptAndRestart();
            }
        } else {
            handleShowSettings(true);
        }
    }

    public void promptAndRestart() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Restart Required");
        alert.setHeaderText("Application Restart Required");
        alert.setContentText("The application needs to restart to apply the licensing changes. Restart now?");
        alert.initOwner(primaryStage);
        
        DialogPane dp = alert.getDialogPane();
        dp.getStyleClass().add("custom-alert-dialog");
        try {
            dp.getStylesheets().add(getActiveThemeStylesheet());
        } catch (Exception ignored) {}

        java.util.Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            restartApplication();
        }
    }

    private void restartApplication() {
        try {
            String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java";
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                javaBin += ".exe";
            }
            String classpath = System.getProperty("java.class.path");
            String mainClass = "com.pstconverter.Launcher";

            java.util.List<String> command = new java.util.ArrayList<>();
            command.add(javaBin);
            command.add("-cp");
            command.add(classpath);
            command.add(mainClass);

            new ProcessBuilder(command).start();
            javafx.application.Platform.exit();
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Failed to auto-restart: " + e.getMessage());
            showAlert(Alert.AlertType.WARNING, "Manual Restart Required", 
                      "Failed to automatically restart the application. Please close and re-open it manually.");
        }
    }

    public void openWebpage(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) return;

        try {
            java.net.URI uri = new java.net.URI(urlString);
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(uri);
                return;
            }
        } catch (Exception ignored) {}

        // Fallback using ProcessBuilder based on OS
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd.exe", "/c", "start", "", urlString).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", urlString).start();
            } else {
                new ProcessBuilder("xdg-open", urlString).start();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("Failed to open help link: " + ex.getMessage());
            showAlert(Alert.AlertType.INFORMATION, "Support URL", "Please visit: " + urlString);
        }
    }

    public void openHelpChat() {
        openWebpage("https://tawk.to/chat/6a39aeb5351d6e1d43433240/1jrol4u8n");
        com.pstconverter.view.HelpDialog.show(this);
    }

    private Integer lastScannedMatchingCount = null;
    private Integer lastScannedTotalCount = null;
    private volatile boolean isScanningFilters = false;
    private final List<Runnable> scanListeners = new java.util.ArrayList<>();

    public void addScanListener(Runnable r) {
        synchronized (scanListeners) {
            scanListeners.add(r);
        }
    }

    public Integer getLastScannedMatchingCount() { return lastScannedMatchingCount; }
    public Integer getLastScannedTotalCount() { return lastScannedTotalCount; }
    public boolean isScanningFilters() { return isScanningFilters; }

    public void clearScannedFilterCount() {
        if (!isScanningFilters) {
            lastScannedMatchingCount = null;
            lastScannedTotalCount = null;
            notifyScanListeners();
        }
    }

    private void notifyScanListeners() {
        java.util.List<Runnable> copy;
        synchronized (scanListeners) {
            copy = new java.util.ArrayList<>(scanListeners);
        }
        for (Runnable r : copy) {
            try {
                r.run();
            } catch (Exception ex) {
                System.err.println("Error notifying scan listener: " + ex.getMessage());
            }
        }
    }

    public void runFilterScan(java.util.function.Consumer<String> statusConsumer, java.util.function.BiConsumer<Integer, Integer> callback) {
        if (isScanningFilters) return;
        isScanningFilters = true;
        notifyScanListeners();
        if (statusConsumer != null) statusConsumer.accept("Scanning...");

        javafx.concurrent.Task<Void> scanTask = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                int totalCount = 0;
                int matchingCount = 0;

                // 1. Get step 3 filters properties
                java.util.Properties props = step3FilterView != null ? step3FilterView.getFilterProperties() : new java.util.Properties();
                com.pstconverter.core.filter.FilterEngine engine = new com.pstconverter.core.filter.FilterEngine(props);

                // Check if we need message body search
                boolean needsBody = false;
                String incKw = props.getProperty("keyword.include", "");
                String excKw = props.getProperty("keyword.exclude", "");
                boolean searchBody = Boolean.parseBoolean(props.getProperty("keyword.searchBody", "true"));
                if (searchBody && (!incKw.isEmpty() || !excKw.isEmpty())) {
                    needsBody = true;
                }

                // 2. Get checked folders from Step 2
                List<javafx.scene.control.TreeItem<String>> checkedItems = new java.util.ArrayList<>();
                if (step2ExplorerView != null) {
                    checkedItems = step2ExplorerView.getCheckedTreeItems();
                }

                if (checkedItems.isEmpty()) {
                    javafx.application.Platform.runLater(() -> {
                        lastScannedTotalCount = 0;
                        lastScannedMatchingCount = 0;
                        isScanningFilters = false;
                        if (callback != null) callback.accept(0, 0);
                        notifyScanListeners();
                    });
                    return null;
                }

                // Count total folders for reporting
                int folderIndex = 0;
                int totalFolders = checkedItems.size();

                for (javafx.scene.control.TreeItem<String> item : checkedItems) {
                    if (isCancelled()) break;
                    
                    File file = step2ExplorerView.getSourceFileForNode(item);
                    List<String> path = step2ExplorerView.getFolderPathFromNode(item);
                    
                    if (file != null && path != null && file.exists()) {
                        com.pstconverter.core.adapter.SourceAdapter parser = com.pstconverter.core.adapter.SourceAdapterFactory.getAdapterForFile(file);
                        if (parser != null) {
                            folderIndex++;
                            final int currentIdx = folderIndex;
                            final String statusMsg = "Scanning folder " + currentIdx + "/" + totalFolders + ": " + item.getValue();
                            javafx.application.Platform.runLater(() -> {
                                if (statusConsumer != null) statusConsumer.accept(statusMsg);
                            });

                            final int[] counts = {0, 0}; // [total, matching]
                            try {
                                if (needsBody) {
                                    parser.streamEmails(file, path, msg -> {
                                        counts[0]++;
                                        if (engine.test(msg)) {
                                            counts[1]++;
                                        }
                                    });
                                } else {
                                    parser.streamEmailsMetadata(file, path, msg -> {
                                        counts[0]++;
                                        if (engine.test(msg)) {
                                            counts[1]++;
                                        }
                                    });
                                }
                                totalCount += counts[0];
                                matchingCount += counts[1];
                            } catch (Exception ex) {
                                System.err.println("Error scanning emails in folder " + item.getValue() + ": " + ex.getMessage());
                            }
                        }
                    }
                }

                final int finalTotal = totalCount;
                final int finalMatching = matchingCount;
                javafx.application.Platform.runLater(() -> {
                    lastScannedTotalCount = finalTotal;
                    lastScannedMatchingCount = finalMatching;
                    isScanningFilters = false;
                    if (callback != null) callback.accept(finalMatching, finalTotal);
                    notifyScanListeners();
                });
                return null;
            }

            @Override
            protected void failed() {
                super.failed();
                javafx.application.Platform.runLater(() -> {
                    isScanningFilters = false;
                    if (statusConsumer != null) statusConsumer.accept("Scan failed");
                    notifyScanListeners();
                });
            }

            @Override
            protected void cancelled() {
                super.cancelled();
                javafx.application.Platform.runLater(() -> {
                    isScanningFilters = false;
                    if (statusConsumer != null) statusConsumer.accept("Scan cancelled");
                    notifyScanListeners();
                });
            }
        };

        Thread t = new Thread(scanTask);
        t.setName("filter-scanner-thread");
        t.setDaemon(true);
        t.start();
    }
}
