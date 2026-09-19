package com.pstconverter.view;
import com.pstconverter.controller.MainController;
import com.pstconverter.core.model.SourceFileModel;
import com.pstconverter.util.MaterialIcons;
import com.pstconverter.util.SettingsManager;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;
import com.pstconverter.view.filter.*;
import java.util.function.BooleanSupplier;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.scene.Scene;
import javafx.concurrent.Task;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXDatePicker;
import io.github.palexdev.materialfx.controls.MFXSpinner;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import io.github.palexdev.materialfx.enums.FloatMode;
public class Step3FilterView extends BorderPane {
    private final MainController controller;
    private Properties filterProperties = new Properties();
    private DateFilterPanel dateFilterPanel;
    private SenderRecipientFilterPanel senderRecipientPanel;
    private KeywordFilterPanel realKeywordPanel;
    private AttachmentFilterPanel attachmentPanel;
    private HygieneFilterPanel hygienePanel;
    private DedupFilterPanel dedupPanel;
    private ItemTypeFilterPanel itemTypePanel;
    // Date Range
    // Sender / Recipient
    // Keywords
    // Attachments
    // Item Types
    // Data Hygiene
    // Dynamic Deduplication Fields
    // Category Navigation & Settings Panel
    private VBox categoryNavBox;
    private VBox settingsPanel;
    private int selectedCategoryIndex = -1;
    private VBox[] categoryContents;
    private VBox[] categoryCards;
    private Label[] categoryActiveBadges;
    // Compact Summary Bar
    private FlowPane compactChipsPane;
    private Label compactActiveCount;
    private MFXButton btnSavePreset;
    private MFXTextField tfPresetName;
    public Step3FilterView(MainController controller) {
        this.controller = controller;
        initializeUI();
        refreshAvailableItemTypes();
    }
    private void initializeUI() {
        Runnable validationCallback = this::validateFilters;
        BooleanSupplier isDarkModeSupplier = () -> controller != null && controller.isDarkMode();
        dateFilterPanel = new DateFilterPanel(validationCallback);
        dateFilterPanel.setScanTriggerHandler(this::scanAvailableDateRange);
        senderRecipientPanel = new SenderRecipientFilterPanel(validationCallback, this::handleLoadSendersPopup, this::handleLoadRecipientsPopup);
        realKeywordPanel = new KeywordFilterPanel(validationCallback);
        attachmentPanel = new AttachmentFilterPanel(validationCallback, isDarkModeSupplier);
        hygienePanel = new HygieneFilterPanel(validationCallback, isDarkModeSupplier);
        dedupPanel = new DedupFilterPanel(validationCallback);
        itemTypePanel = new ItemTypeFilterPanel(validationCallback, isDarkModeSupplier);
        // ── TOP: Compact Filter Summary Bar ──────────────────────────────
        HBox summaryBar = createCompactSummaryBar();
        this.setTop(summaryBar);
        // ── CENTER: Category Nav (25%) + Settings Panel (75%) ────────────
        SplitPane filterSplit = new SplitPane();
        filterSplit.setDividerPositions(0.25);
        ScrollPane navScroll = createCategoryNav();
        settingsPanel = new VBox(0);
        settingsPanel.setPadding(new Insets(4));
        selectCategory(0);
        ScrollPane settingsScroll = new ScrollPane(settingsPanel);
        settingsScroll.setFitToWidth(true);
        settingsScroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        settingsScroll.setPadding(new Insets(0, 8, 0, 0));
        filterSplit.getItems().addAll(navScroll, settingsScroll);
        VBox centerBox = new VBox(0);
        VBox.setVgrow(filterSplit, Priority.ALWAYS);
        centerBox.getChildren().add(filterSplit);
        this.setCenter(centerBox);
    }
    public boolean validateFilters() {
        if (controller == null) return true;
        boolean isValid = true;
        if (!dateFilterPanel.validate()) isValid = false;
        if (!attachmentPanel.validate()) isValid = false;
        return isValid;
    }

    public void updateFilterSummary() {
        if (compactChipsPane == null) return;
        compactChipsPane.getChildren().clear();
        saveFilterSettings(); // Sync to properties first
        java.util.List<String> chips = getActiveSummariesFromProperties(this.filterProperties);
        int activeCount = chips.size();
        if (chips.isEmpty()) {
            Label noFilters = new Label("No filters active");
            noFilters.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-font-style: italic;");
            compactChipsPane.getChildren().add(noFilters);
        } else {
            boolean isDark = controller != null && controller.isDarkMode();
            for (String chipText : chips) {
                String display = chipText;
                if (display.length() > 50) {
                    display = display.substring(0, 47) + "...";
                }
                Label chip = new Label(display);
                chip.getStyleClass().add("filter-chip");
                chip.getStyleClass().add("filter-chip-active");
                chip.setStyle(
                    "-fx-background-color: " + (isDark ? "rgba(14, 165, 233, 0.2)" : "rgba(14, 165, 233, 0.12)") + "; " +
                    "-fx-text-fill: " + (isDark ? "#7dd3fc" : "#0284c7") + "; " +
                    "-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-padding: 3px 9px; -fx-background-radius: 6px; " +
                    "-fx-border-color: " + (isDark ? "rgba(14, 165, 233, 0.35)" : "rgba(14, 165, 233, 0.2)") + "; -fx-border-radius: 6px;"
                );
                if (chipText.length() > 50) {
                    Tooltip.install(chip, new Tooltip(chipText));
                }
                compactChipsPane.getChildren().add(chip);
            }
        }
        compactActiveCount.setText(activeCount + " active");
        updateSavePresetButtonState();
        updateCategoryActiveBadges();
    }

    public void updateCategoryActiveBadges() {
        if (categoryActiveBadges == null) return;
        saveFilterSettings();
        Properties props = this.filterProperties;

        // 1. Date Range
        boolean dateActive = props.getProperty("date.from") != null || props.getProperty("date.to") != null;
        setCategoryBadgeActive(1, dateActive);

        // 2. Deduplication
        boolean dedupActive = Boolean.parseBoolean(props.getProperty("hygiene.removeDuplicates", "false"));
        setCategoryBadgeActive(2, dedupActive);

        // 3. Sender / Recipient
        boolean senderActive = !props.getProperty("sender.include", "").isEmpty() ||
                               !props.getProperty("sender.exclude", "").isEmpty() ||
                               !props.getProperty("recipient.include", "").isEmpty() ||
                               !props.getProperty("recipient.exclude", "").isEmpty();
        setCategoryBadgeActive(3, senderActive);

        // 4. Attachments
        boolean attachActive = !props.getProperty("attachment.mode", "Any Attachment State").equals("Any Attachment State") ||
                                !props.getProperty("attachment.maxSize", "0").equals("0") ||
                                !props.getProperty("attachment.excludeTypes", "").isEmpty() ||
                                !props.getProperty("attachment.includeTypes", "").isEmpty();
        setCategoryBadgeActive(4, attachActive);

        // 5. Keywords & Hygiene
        boolean kwActive = !props.getProperty("keyword.include", "").isEmpty() ||
                           !props.getProperty("keyword.exclude", "").isEmpty() ||
                           Boolean.parseBoolean(props.getProperty("hygiene.skipDeleted", "false")) ||
                           Boolean.parseBoolean(props.getProperty("hygiene.skipJunk", "false"));
        setCategoryBadgeActive(5, kwActive);

        // 6. Item Types
        boolean emails = Boolean.parseBoolean(props.getProperty("item.emails", "true"));
        boolean calendars = Boolean.parseBoolean(props.getProperty("item.calendars", "true"));
        boolean contacts = Boolean.parseBoolean(props.getProperty("item.contacts", "true"));
        boolean tasks = Boolean.parseBoolean(props.getProperty("item.tasks", "true"));
        boolean notes = Boolean.parseBoolean(props.getProperty("item.notes", "true"));
        boolean journals = Boolean.parseBoolean(props.getProperty("item.journals", "true"));
        int typesCount = (emails ? 1 : 0) + (calendars ? 1 : 0) + (contacts ? 1 : 0) + (tasks ? 1 : 0) + (notes ? 1 : 0) + (journals ? 1 : 0);
        boolean itemTypesActive = typesCount < 6;
        setCategoryBadgeActive(6, itemTypesActive);
    }

    private void setCategoryBadgeActive(int index, boolean active) {
        if (categoryActiveBadges != null && index >= 0 && index < categoryActiveBadges.length) {
            Label badge = categoryActiveBadges[index];
            if (badge != null) {
                badge.setVisible(active);
                badge.setManaged(active);
            }
        }
    }

    public void updateSavePresetButtonState() {
        if (btnSavePreset == null) return;
        java.util.List<String> chips = getActiveSummariesFromProperties(this.filterProperties);
        boolean hasActiveFilters = chips != null && !chips.isEmpty();
        btnSavePreset.setDisable(!hasActiveFilters);
        if (tfPresetName != null) {
            tfPresetName.setDisable(!hasActiveFilters);
            if (!hasActiveFilters) {
                tfPresetName.setPromptText("Apply at least 1 filter to save a preset...");
                Tooltip.install(btnSavePreset, new Tooltip("Apply at least one filter to save a preset configuration."));
            } else {
                tfPresetName.setPromptText("Enter preset name (e.g. Q3 Audits)...");
                Tooltip.uninstall(btnSavePreset, null);
            }
        }
    }
    public void handleResetFilters() {
        System.out.println("User reset all filter settings to default values.");
        Properties defaultProps = new Properties();
        defaultProps.setProperty("item.emails", "true");
        defaultProps.setProperty("item.calendars", "true");
        defaultProps.setProperty("item.contacts", "true");
        defaultProps.setProperty("item.tasks", "true");
        defaultProps.setProperty("item.notes", "true");
        defaultProps.setProperty("item.journals", "true");
        defaultProps.setProperty("sender.domainMatch", "true");
        defaultProps.setProperty("keyword.searchSubject", "true");
        defaultProps.setProperty("keyword.searchBody", "true");
        defaultProps.setProperty("keyword.caseSensitive", "false");
        defaultProps.setProperty("hygiene.skipEmpty", "true");
        defaultProps.setProperty("hygiene.skipDeleted", "false");
        defaultProps.setProperty("hygiene.skipJunk", "false");
        defaultProps.setProperty("hygiene.removeDuplicates", "false");
        defaultProps.setProperty("hygiene.dedupSubject", "true");
        defaultProps.setProperty("hygiene.dedupDate", "true");
        loadFilterSettings(defaultProps);
    }
    private void handleLoadSendersPopup(TextField targetField) {
        System.out.println("Triggered smart selection scan for senders.");
        Stage progressStage = new Stage();
        progressStage.initOwner(controller.getPrimaryStage());
        progressStage.initStyle(StageStyle.UNDECORATED);
        progressStage.initModality(Modality.WINDOW_MODAL);
        VBox progressRoot = new VBox(15);
        progressRoot.setAlignment(Pos.CENTER);
        progressRoot.setPadding(new Insets(20));
        progressRoot.setStyle("-fx-background-color: #1e293b; -fx-border-color: #0ea5e9; -fx-border-width: 2; -fx-background-radius: 8px; -fx-border-radius: 8px;");
        Label lblProgress = new Label("Scanning selected folders for senders...");
        lblProgress.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 13px; -fx-font-weight: bold;");
        MFXProgressSpinner pin = new MFXProgressSpinner();
        MFXButton btnCancel = new MFXButton("Cancel");
        btnCancel.getStyleClass().addAll("action-btn", "btn-secondary");
        btnCancel.setStyle("-fx-text-fill: white;");
        progressRoot.getChildren().addAll(pin, lblProgress, btnCancel);
        Scene progressScene = new Scene(progressRoot, 320, 170);
        progressStage.setScene(progressScene);
        Task<List<String>> scanTask = new Task<List<String>>() {
            @Override
            protected List<String> call() throws Exception {
                return controller.getSelectedSenders();
            }
        };
        btnCancel.setOnAction(e -> {
            scanTask.cancel(true);
        });
        scanTask.setOnCancelled(event -> {
            progressStage.close();
            System.out.println("Smart select senders scan cancelled by user.");
            controller.showNotification("Sender scan cancelled.");
        });
        scanTask.setOnSucceeded(event -> {
            progressStage.close();
            List<String> senders = scanTask.getValue();
            if (senders.isEmpty()) {
                System.out.println("Smart select senders scan completed: 0 senders found.");
                controller.showAlert(Alert.AlertType.INFORMATION, "No Senders Found",
                        "No senders found in the selected folders or no folders are selected.");
                return;
            }
            System.out.println("Smart select senders scan succeeded. Found " + senders.size() + " unique sender(s).");
            showSelectionDialog("Select Senders", senders, targetField);
        });
        scanTask.setOnFailed(event -> {
            progressStage.close();
            Throwable ex = scanTask.getException();
            System.err.println("Smart select senders scan failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
            controller.showAlert(Alert.AlertType.ERROR, "Scan Failed",
                    "An error occurred while scanning for senders:\n" + (ex != null ? ex.getMessage() : "Unknown error"));
        });
        Thread bgThread = new Thread(scanTask);
        bgThread.setDaemon(true);
        bgThread.start();
        progressStage.show();
    }
    private void handleLoadRecipientsPopup(TextField targetField) {
        System.out.println("Triggered smart selection scan for recipients.");
        Stage progressStage = new Stage();
        progressStage.initOwner(controller.getPrimaryStage());
        progressStage.initStyle(StageStyle.UNDECORATED);
        progressStage.initModality(Modality.WINDOW_MODAL);
        VBox progressRoot = new VBox(15);
        progressRoot.setAlignment(Pos.CENTER);
        progressRoot.setPadding(new Insets(20));
        progressRoot.setStyle("-fx-background-color: #1e293b; -fx-border-color: #0ea5e9; -fx-border-width: 2; -fx-background-radius: 8px; -fx-border-radius: 8px;");
        Label lblProgress = new Label("Scanning selected folders for recipients...");
        lblProgress.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 13px; -fx-font-weight: bold;");
        MFXProgressSpinner pin = new MFXProgressSpinner();
        MFXButton btnCancel = new MFXButton("Cancel");
        btnCancel.getStyleClass().addAll("action-btn", "btn-secondary");
        btnCancel.setStyle("-fx-text-fill: white;");
        progressRoot.getChildren().addAll(pin, lblProgress, btnCancel);
        Scene progressScene = new Scene(progressRoot, 320, 170);
        progressStage.setScene(progressScene);
        Task<List<String>> scanTask = new Task<List<String>>() {
            @Override
            protected List<String> call() throws Exception {
                return controller.getSelectedRecipients();
            }
        };
        btnCancel.setOnAction(e -> {
            scanTask.cancel(true);
        });
        scanTask.setOnCancelled(event -> {
            progressStage.close();
            System.out.println("Smart select recipients scan cancelled by user.");
            controller.showNotification("Recipient scan cancelled.");
        });
        scanTask.setOnSucceeded(event -> {
            progressStage.close();
            List<String> recipients = scanTask.getValue();
            if (recipients.isEmpty()) {
                System.out.println("Smart select recipients scan completed: 0 recipients found.");
                controller.showAlert(Alert.AlertType.INFORMATION, "No Recipients Found",
                        "No recipients found in the selected folders or no folders are selected.");
                return;
            }
            System.out.println("Smart select recipients scan succeeded. Found " + recipients.size() + " unique recipient(s).");
            showSelectionDialog("Select Recipients", recipients, targetField);
        });
        scanTask.setOnFailed(event -> {
            progressStage.close();
            Throwable ex = scanTask.getException();
            System.err.println("Smart select recipients scan failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
            controller.showAlert(Alert.AlertType.ERROR, "Scan Failed",
                    "An error occurred while scanning for recipients:\n" + (ex != null ? ex.getMessage() : "Unknown error"));
        });
        Thread bgThread = new Thread(scanTask);
        bgThread.setDaemon(true);
        bgThread.start();
        progressStage.show();
    }
    private void showSelectionDialog(String titleStr, List<String> items, TextField targetField) {
        Stage dialog = new Stage();
        dialog.initOwner(controller.getPrimaryStage());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(titleStr);
        dialog.setMinWidth(420);
        dialog.setMinHeight(500);
        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("dialog-popup-root");
        Label title = new Label(titleStr);
        title.getStyleClass().add("dialog-popup-title");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search...");
        searchField.getStyleClass().add("search-field");
        ListView<CheckBox> listView = new ListView<>();
        listView.getStyleClass().addAll("popup-listview", "senders-listview");
        VBox.setVgrow(listView, Priority.ALWAYS);
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (String item : items) {
            CheckBox cb = new CheckBox(item);
            cb.getStyleClass().add("filter-checkbox");
            cb.setStyle("-fx-font-size: 13px; -fx-padding: 4px;");
            String currentText = targetField.getText().trim();
            if (!currentText.isEmpty()) {
                String[] currentList = currentText.split("\\s*,\\s*");
                for (String c : currentList) {
                    if (c.equalsIgnoreCase(item)) {
                        cb.setSelected(true);
                        break;
                    }
                }
            }
            checkBoxes.add(cb);
        }
        listView.getItems().addAll(checkBoxes);
        searchField.textProperty().addListener((o, ov, nv) -> {
            listView.getItems().clear();
            if (nv == null || nv.trim().isEmpty()) {
                listView.getItems().addAll(checkBoxes);
            } else {
                String query = nv.toLowerCase().trim();
                for (CheckBox cb : checkBoxes) {
                    if (cb.getText().toLowerCase().contains(query)) {
                        listView.getItems().add(cb);
                    }
                }
            }
        });
        HBox selectRow = new HBox(8);
        MFXButton btnSelectAll = new MFXButton("Select All");
        btnSelectAll.getStyleClass().addAll("action-btn", "btn-secondary");
        btnSelectAll.setStyle("-fx-font-size: 11px;");
        btnSelectAll.setOnAction(e -> {
            for (CheckBox cb : listView.getItems()) {
                cb.setSelected(true);
            }
        });
        MFXButton btnDeselectAll = new MFXButton("Deselect All");
        btnDeselectAll.getStyleClass().addAll("action-btn", "btn-secondary");
        btnDeselectAll.setStyle("-fx-font-size: 11px;");
        btnDeselectAll.setOnAction(e -> {
            for (CheckBox cb : listView.getItems()) {
                cb.setSelected(false);
            }
        });
        selectRow.getChildren().addAll(btnSelectAll, btnDeselectAll);
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10, 0, 0, 0));
        MFXButton btnCancel = new MFXButton("Cancel");
        btnCancel.getStyleClass().addAll("action-btn", "btn-secondary");
        btnCancel.setOnAction(e -> dialog.close());
        MFXButton btnApply = new MFXButton("Apply Selection");
        btnApply.getStyleClass().addAll("action-btn", "btn-primary");
        btnApply.setOnAction(e -> {
            List<String> selected = new ArrayList<>();
            for (CheckBox cb : checkBoxes) {
                if (cb.isSelected()) {
                    selected.add(cb.getText());
                }
            }
            if (!selected.isEmpty()) {
                targetField.setText(String.join(", ", selected));
            } else {
                targetField.clear();
            }
            dialog.close();
        });
        footer.getChildren().addAll(btnCancel, btnApply);
        root.getChildren().addAll(title, searchField, selectRow, listView, footer);
        Scene scene = new Scene(root, 420, 520);
        try {
            String css = controller.getActiveThemeStylesheet();
            scene.getStylesheets().add(css);
        } catch (Exception ignored) {}
        dialog.setScene(scene);
        dialog.show();
    }
    // ── COMPACT SUMMARY BAR ─────────────────────────────────────────────────
    private HBox createCompactSummaryBar() {
        boolean isDark = controller != null && controller.isDarkMode();
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 16, 8, 16));
        bar.getStyleClass().add("compact-summary-bar");
        bar.setStyle(
            "-fx-background-color: " + (isDark ? "rgba(30, 41, 59, 0.7)" : "rgba(241, 245, 249, 0.8)") + "; " +
            "-fx-border-color: " + (isDark ? "rgba(255, 255, 255, 0.08)" : "rgba(0, 0, 0, 0.06)") + "; " +
            "-fx-border-width: 0 0 1px 0;"
        );

        Label filterIcon = MaterialIcons.icon(MaterialIcons.SEARCH, "-fx-font-size: 15px; -fx-text-fill: " + (isDark ? "#38bdf8" : "#0284c7") + ";");
        compactActiveCount = new Label("0 active");
        compactActiveCount.getStyleClass().add("count-badge");
        compactActiveCount.setStyle(
            "-fx-background-color: " + (isDark ? "rgba(14, 165, 233, 0.2)" : "rgba(14, 165, 233, 0.12)") + "; " +
            "-fx-text-fill: " + (isDark ? "#7dd3fc" : "#0284c7") + "; " +
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3px 10px; -fx-background-radius: 8px;"
        );

        compactChipsPane = new FlowPane(6, 4);
        compactChipsPane.setMinHeight(24);
        compactChipsPane.setAlignment(Pos.CENTER_LEFT);
        Label noFilters = new Label("No filters active");
        noFilters.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + "; -fx-font-style: italic;");
        compactChipsPane.getChildren().add(noFilters);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnScan = new MFXButton("Scan Matches");
        Label iconScan = MaterialIcons.icon(MaterialIcons.SEARCH, 13);
        iconScan.setStyle("-fx-text-fill: white;");
        btnScan.setGraphic(iconScan);
        btnScan.getStyleClass().addAll("action-btn", "btn-primary");
        String scanBaseStyle = "-fx-background-color: linear-gradient(to right, #0ea5e9, #06b6d4); " +
                               "-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; " +
                               "-fx-padding: 6px 14px; -fx-background-radius: 8px; -fx-cursor: hand;";
        btnScan.setStyle(scanBaseStyle);

        lblScanStatus = new Label("");
        lblScanStatus.setStyle("-fx-font-size: 11.5px; -fx-text-fill: " + (isDark ? "#38bdf8" : "#0284c7") + "; -fx-font-weight: bold;");

        btnScan.setOnAction(e -> {
            btnScan.setDisable(true);
            saveFilterSettings(); // Save currently edited filter settings first
            controller.runFilterScan(
                status -> javafx.application.Platform.runLater(() -> lblScanStatus.setText(status)),
                (matching, total) -> javafx.application.Platform.runLater(() -> {
                    lblScanStatus.setText(matching + " / " + total + " match");
                    btnScan.setDisable(false);
                })
            );
        });

        // Initialize display from cached value if present
        if (controller.getLastScannedMatchingCount() != null) {
            lblScanStatus.setText(controller.getLastScannedMatchingCount() + " / " + controller.getLastScannedTotalCount() + " match");
        }
        // Add scan listener to keep UI in sync
        controller.addScanListener(() -> javafx.application.Platform.runLater(() -> {
            Integer matching = controller.getLastScannedMatchingCount();
            Integer total = controller.getLastScannedTotalCount();
            if (matching != null && total != null) {
                lblScanStatus.setText(matching + " / " + total + " match");
                btnScan.setDisable(false);
            } else if (controller.isScanningFilters()) {
                // scanning...
            } else {
                lblScanStatus.setText("");
                btnScan.setDisable(false);
            }
        }));

        MFXButton btnReset = new MFXButton("↺ Reset All");
        Label iconReset = MaterialIcons.icon(MaterialIcons.CLEAR, 12);
        iconReset.setStyle("-fx-text-fill: " + (isDark ? "#fbbf24" : "#d97706") + ";");
        btnReset.setGraphic(iconReset);
        btnReset.getStyleClass().addAll("action-btn", "btn-reset");
        String resetBaseStyle = "-fx-background-color: " + (isDark ? "rgba(245, 158, 11, 0.15)" : "rgba(245, 158, 11, 0.08)") + "; " +
                                "-fx-text-fill: " + (isDark ? "#fcd34d" : "#d97706") + "; " +
                                "-fx-border-color: " + (isDark ? "rgba(245, 158, 11, 0.35)" : "rgba(245, 158, 11, 0.25)") + "; " +
                                "-fx-border-radius: 12px; -fx-background-radius: 12px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5px 12px; -fx-cursor: hand;";
        btnReset.setStyle(resetBaseStyle);
        btnReset.setOnAction(e -> handleResetFilters());
        btnReset.setOnMouseEntered(ev -> btnReset.setStyle(resetBaseStyle + "-fx-background-color: " + (isDark ? "rgba(245, 158, 11, 0.3)" : "rgba(245, 158, 11, 0.18)") + ";"));
        btnReset.setOnMouseExited(ev -> btnReset.setStyle(resetBaseStyle));

        bar.getChildren().addAll(filterIcon, compactActiveCount, compactChipsPane, spacer, lblScanStatus, btnScan, btnReset);
        return bar;
    }
    // ── CATEGORY NAVIGATION ──────────────────────────────────────────────────
    private ScrollPane createCategoryNav() {
        categoryNavBox = new VBox(4);
        categoryNavBox.setPadding(new Insets(8));
        categoryNavBox.getStyleClass().add("category-nav-container");
        String[][] categories = {
            {MaterialIcons.STAR, "Saved Presets", "Load or save filter configurations"},
            {MaterialIcons.CALENDAR, "Date Range", "Filter by date range"},
            {MaterialIcons.REFRESH, "Deduplication", "Remove duplicate email messages"},
            {MaterialIcons.PERSON, "Sender / Recipient", "Include/exclude people"},
            {MaterialIcons.ATTACHMENT, "Attachments", "File type & size filters"},
            {MaterialIcons.TUNE, "Keywords & Hygiene", "Text matching & folder cleanup"},
            {MaterialIcons.FOLDER, "Item Types", "Email, calendar, contacts…"}
        };
        categoryContents = new javafx.scene.layout.VBox[categories.length];
        categoryContents[0] = createPresetsSection();
        categoryContents[1] = dateFilterPanel;
        categoryContents[2] = dedupPanel;
        categoryContents[3] = senderRecipientPanel;
        categoryContents[4] = attachmentPanel;
        VBox keywordsAndHygiene = new VBox(8);
        keywordsAndHygiene.getChildren().addAll(realKeywordPanel, hygienePanel);
        categoryContents[5] = keywordsAndHygiene;
        categoryContents[6] = itemTypePanel;
        categoryCards = new VBox[categories.length];
        categoryActiveBadges = new Label[categories.length];
        for (int i = 0; i < categories.length; i++) {
            VBox card = createCategoryNavCard(categories[i][0], categories[i][1], categories[i][2], i);
            categoryCards[i] = card;
            categoryNavBox.getChildren().add(card);
        }
        ScrollPane scroll = new ScrollPane(categoryNavBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        return scroll;
    }
    private VBox createCategoryNavCard(String iconCode, String title, String subtitle, int index) {
        boolean isDark = controller != null && controller.isDarkMode();
        VBox card = new VBox(3);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.getStyleClass().add("category-nav-card");

        // Icon accent color based on category index
        String iconColor;
        switch (index) {
            case 0: iconColor = isDark ? "#fbbf24" : "#d97706"; break; // Saved Presets (Amber)
            case 1: iconColor = isDark ? "#38bdf8" : "#0284c7"; break; // Date Range (Azure)
            case 2: iconColor = isDark ? "#22d3ee" : "#0891b2"; break; // Deduplication (Cyan)
            case 3: iconColor = isDark ? "#34d399" : "#059669"; break; // Sender/Recipient (Emerald)
            case 4: iconColor = isDark ? "#c084fc" : "#7c3aed"; break; // Attachments (Purple)
            case 5: iconColor = isDark ? "#f472b6" : "#db2777"; break; // Keywords & Hygiene (Pink/Rose)
            default: iconColor = isDark ? "#60a5fa" : "#2563eb"; break; // Item Types (Blue)
        }

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label iconLabel = MaterialIcons.icon(iconCode, "-fx-font-size: 16px; -fx-text-fill: " + iconColor + ";");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("category-nav-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label activeBadge = new Label("ACTIVE");
        activeBadge.getStyleClass().add("category-active-badge");
        activeBadge.setStyle("-fx-background-color: " + (isDark ? "rgba(16, 185, 129, 0.2)" : "rgba(16, 185, 129, 0.12)") + "; " +
                             "-fx-text-fill: " + (isDark ? "#34d399" : "#059669") + "; " +
                             "-fx-font-size: 9.5px; -fx-font-weight: bold; -fx-padding: 2px 6px; -fx-background-radius: 8px;");
        activeBadge.setVisible(false);
        activeBadge.setManaged(false);
        categoryActiveBadges[index] = activeBadge;

        header.getChildren().addAll(iconLabel, titleLabel, spacer, activeBadge);
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + "; -fx-padding: 0 0 0 24px;");
        card.getChildren().addAll(header, subtitleLabel);
        card.setOnMouseClicked(e -> selectCategory(index));
        return card;
    }
    private void selectCategory(int index) {
        boolean isDark = controller != null && controller.isDarkMode();
        // Update card highlight styles
        for (int i = 0; i < categoryNavBox.getChildren().size(); i++) {
            VBox card = (VBox) categoryNavBox.getChildren().get(i);
            card.getStyleClass().removeAll("category-nav-card", "category-nav-card-selected", "category-nav-card-hover");
            if (i == index) {
                card.getStyleClass().add("category-nav-card-selected");
                card.setStyle("-fx-background-color: " + (isDark ? "rgba(14, 165, 233, 0.18)" : "rgba(14, 165, 233, 0.08)") + "; " +
                              "-fx-border-color: " + (isDark ? "#38bdf8" : "#0284c7") + "; " +
                              "-fx-border-width: 1px 1px 1px 4px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
            } else {
                card.getStyleClass().add("category-nav-card");
                card.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-radius: 8px; -fx-background-radius: 8px;");
            }
        }
        selectedCategoryIndex = index;
        // If Saved Presets category is selected, refresh the presets list
        if (index == 0) {
            refreshPresetsList();
        }
        // Show selected category content in right panel
        settingsPanel.getChildren().clear();
        if (index >= 0 && index < categoryContents.length && categoryContents[index] != null) {
            ScrollPane contentScroll = new ScrollPane(categoryContents[index]);
            contentScroll.setFitToWidth(true);
            contentScroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
            contentScroll.setPadding(new Insets(4));
            VBox.setVgrow(contentScroll, Priority.ALWAYS);
            settingsPanel.getChildren().add(contentScroll);
        }
    }
    private void showPlaceholder() {
        settingsPanel.getChildren().clear();
        VBox placeholder = new VBox(12);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(60, 20, 60, 20));
        Label placeholderIcon = new Label("⚙");
        placeholderIcon.setStyle("-fx-font-size: 48px; -fx-text-fill: #0ea5e9;");
        Label placeholderText = new Label("Select a filter category");
        placeholderText.setStyle("-fx-font-size: 16px; -fx-text-fill: #94a3b8; -fx-font-weight: bold;");
        Label placeholderHint = new Label("Click a category on the left to configure filter settings.");
        placeholderHint.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        placeholder.getChildren().addAll(placeholderIcon, placeholderText, placeholderHint);
        settingsPanel.getChildren().add(placeholder);
    }
    // ── FILTER SETTINGS EXPORT ──────────────────────────────────────────────
    public void saveFilterSettings() {
        if (controller != null && controller.getSessionDir() == null) return;
        Properties props = new Properties();
        dateFilterPanel.saveProperties(props);
        senderRecipientPanel.saveProperties(props);
        realKeywordPanel.saveProperties(props);
        attachmentPanel.saveProperties(props);
        hygienePanel.saveProperties(props);
        dedupPanel.saveProperties(props);
        itemTypePanel.saveProperties(props);
        this.filterProperties = props;
        try {
            java.io.File migrationDataDir = new java.io.File(controller.getSessionDir(), "migration_data");
            if (!migrationDataDir.exists()) migrationDataDir.mkdirs();
            java.io.File humanReadableFile = new java.io.File(migrationDataDir, "filter_settings.txt");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(humanReadableFile)) {
                pw.println("=========================================");
                pw.println(com.pstconverter.config.BrandConfig.TOOL_NAME.toUpperCase() + " - FILTER SETTINGS REPORT");
                pw.println("Generated: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
                pw.println("=========================================");
                pw.println();
                pw.println("ACTIVE FILTERS:");
                java.util.List<String> summaries = getActiveSummariesFromProperties(props);
                if (summaries.isEmpty()) {
                    pw.println("No active filters (Migrate everything).");
                } else {
                    for (String summary : summaries) {
                        pw.println("- " + summary);
                    }
                }
            }
            System.out.println("Filter report saved to: " + humanReadableFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to save filter settings report: " + e.getMessage());
        }
    }
    /**
     * Loads and restores filter settings from a Properties object.
     * This is invoked during the Resume flow to configure active filters to match the interrupted session's parameters.
     */
    public void loadFilterSettings(Properties props) {
        if (props == null) return;
        this.filterProperties = props;
        dateFilterPanel.loadProperties(props);
        senderRecipientPanel.loadProperties(props);
        realKeywordPanel.loadProperties(props);
        attachmentPanel.loadProperties(props);
        hygienePanel.loadProperties(props);
        dedupPanel.loadProperties(props);
        itemTypePanel.loadProperties(props);
        updateFilterSummary();
        validateFilters();
    }
    public java.util.List<String> getActiveFilterSummaryChips() {
        return getActiveSummariesFromProperties(this.filterProperties);
    }
    public Properties getFilterProperties() {
        return filterProperties;
    }
    public void refreshAvailableItemTypes() {
        java.util.Set<String> supportedTypes = new java.util.HashSet<>();
        boolean hasFiles = false;
        if (controller != null && controller.getFileList() != null) {
            for (com.pstconverter.core.model.SourceFileModel model : controller.getFileList()) {
                if (model.isValid()) {
                    hasFiles = true;
                    java.io.File f = new java.io.File(model.getFilePath());
                    com.pstconverter.core.adapter.SourceAdapter adapter = com.pstconverter.core.adapter.SourceAdapterFactory.getAdapterForFile(f);
                    if (adapter != null) {
                        supportedTypes.addAll(adapter.getSupportedItemTypes());
                    }
                }
            }
        }
        if (!hasFiles) {
            supportedTypes.addAll(java.util.Arrays.asList("Emails", "Calendar Items", "Contacts", "Tasks", "Notes", "Journal Entries"));
        }
        itemTypePanel.updateCardVisibility(itemTypePanel.getCardEmails(), supportedTypes.contains("Emails"), itemTypePanel.getCbItemEmails());
        itemTypePanel.updateCardVisibility(itemTypePanel.getCardCalendars(), supportedTypes.contains("Calendar Items"), itemTypePanel.getCbItemCalendars());
        itemTypePanel.updateCardVisibility(itemTypePanel.getCardContacts(), supportedTypes.contains("Contacts"), itemTypePanel.getCbItemContacts());
        itemTypePanel.updateCardVisibility(itemTypePanel.getCardTasks(), supportedTypes.contains("Tasks"), itemTypePanel.getCbItemTasks());
        itemTypePanel.updateCardVisibility(itemTypePanel.getCardNotes(), supportedTypes.contains("Notes"), itemTypePanel.getCbItemNotes());
        itemTypePanel.updateCardVisibility(itemTypePanel.getCardJournals(), supportedTypes.contains("Journal Entries"), itemTypePanel.getCbItemJournals());
        boolean showItemTypesNav = supportedTypes.size() > 1;
        if (categoryCards != null && categoryCards.length > 6 && categoryCards[6] != null) {
            categoryCards[6].setVisible(showItemTypesNav);
            categoryCards[6].setManaged(showItemTypesNav);
        }
        if (!showItemTypesNav && selectedCategoryIndex == 6) {
            selectCategory(1);
        }
    }
    private VBox presetsListContainer;
    private MFXButton btnScan;
    private Label lblScanStatus;
    private VBox createPresetsSection() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(10));
        Label header = new Label("⭐  Saved Filter Presets");
        header.getStyleClass().add("filter-section-header");
        // --- SAVE CURRENT CONFIGURATION CARD ---
        VBox saveCard = new VBox(10);
        saveCard.getStyleClass().add("filter-sub-card");
        saveCard.setPadding(new Insets(12));
        Label lblSaveHeader = new Label("💾 Save Current Configuration");
        lblSaveHeader.getStyleClass().add("filter-group-label");
        lblSaveHeader.setStyle("-fx-font-size: 13px; -fx-text-fill: #818cf8; -fx-font-weight: bold; -fx-padding: 0 0 2px 0;");
        tfPresetName = new MFXTextField();
        tfPresetName.setFloatMode(io.github.palexdev.materialfx.enums.FloatMode.DISABLED);
        tfPresetName.setPrefWidth(200);
        tfPresetName.setMaxWidth(600);
        tfPresetName.setPromptText("Enter preset name (e.g. Q3 Audits)...");
        tfPresetName.getStyleClass().add("filter-textfield");
        tfPresetName.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tfPresetName, Priority.ALWAYS);
        btnSavePreset = new MFXButton("Save Configuration");
        btnSavePreset.getStyleClass().addAll("action-btn", "btn-primary");
        btnSavePreset.setStyle("-fx-font-size: 12px; -fx-padding: 8px 16px; -fx-font-weight: bold;");
        btnSavePreset.setOnAction(e -> {
            String name = tfPresetName.getText().trim();
            if (name.isEmpty()) {
                controller.showAlert(Alert.AlertType.WARNING, "Name Required", "Please enter a name for the filter preset.");
                return;
            }
            // Build the properties dynamically from current UI inputs
            saveFilterSettings();
            java.util.Properties currentProps = getFilterProperties();
            String serialized = serializePropertiesToString(currentProps);
            com.pstconverter.util.SettingsManager.saveFilterPreset(name, serialized);
            tfPresetName.clear();
            refreshPresetsList();
            controller.showNotification("Filter preset '" + name + "' saved successfully!");
        });
        HBox saveRow = new HBox(8);
        saveRow.setAlignment(Pos.CENTER_LEFT);
        saveRow.getChildren().addAll(tfPresetName, btnSavePreset);
        saveCard.getChildren().addAll(lblSaveHeader, new Separator(), saveRow);
        // --- PRESETS LIST ---
        VBox listCard = new VBox(10);
        listCard.getStyleClass().add("filter-sub-card");
        listCard.setPadding(new Insets(12));
        Label lblListHeader = new Label("📋 Available Presets");
        lblListHeader.getStyleClass().add("filter-group-label");
        lblListHeader.setStyle("-fx-font-size: 13px; -fx-text-fill: #818cf8; -fx-font-weight: bold; -fx-padding: 0 0 2px 0;");
        presetsListContainer = new VBox(10);
        listCard.getChildren().addAll(lblListHeader, new Separator(), presetsListContainer);
        root.getChildren().addAll(header, saveCard, listCard);
        refreshPresetsList();
        updateSavePresetButtonState();
        return root;
    }
    private void refreshPresetsList() {
        if (presetsListContainer == null) return;
        presetsListContainer.getChildren().clear();
        java.util.Map<String, String> presets = com.pstconverter.util.SettingsManager.getAllFilterPresets();
        if (presets.isEmpty()) {
            VBox emptyBox = new VBox(8);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(30, 10, 30, 10));
            Label lblEmpty = new Label("No filter presets saved yet.");
            lblEmpty.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-font-style: italic;");
            emptyBox.getChildren().addAll(lblEmpty);
            presetsListContainer.getChildren().add(emptyBox);
            return;
        }
        boolean isDark = controller.isDarkMode();
        for (java.util.Map.Entry<String, String> entry : presets.entrySet()) {
            String name = entry.getKey();
            String settingsStr = entry.getValue();
            // Deserialize settings to analyze what filters are active
            java.util.Properties props = deserializeProperties(settingsStr);
            List<String> activeSummaries = getActiveSummariesFromProperties(props);
            VBox card = new VBox(8);
            card.setPadding(new Insets(12));
            card.setStyle("-fx-background-color: " + (isDark ? "#1e293b" : "#ffffff") + "; " +
                          "-fx-border-color: " + (isDark ? "rgba(255,255,255,0.08)" : "rgba(0,0,0,0.08)") + "; " +
                          "-fx-border-width: 1px; " +
                          "-fx-border-radius: 8px; " +
                          "-fx-background-radius: 8px;");
            // Header: Name
            Label lblName = new Label(name);
            lblName.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#ffffff" : "#1e293b") + ";");
            // Details/Chips inside the preset
            FlowPane chips = new FlowPane(6, 4);
            chips.setAlignment(Pos.CENTER_LEFT);
            if (activeSummaries.isEmpty()) {
                Label lblNoF = new Label("No filters configured (Migrate everything)");
                lblNoF.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b; -fx-font-style: italic;");
                chips.getChildren().add(lblNoF);
            } else {
                for (String sum : activeSummaries) {
                    Label chip = new Label(sum);
                    chip.getStyleClass().addAll("filter-chip", "filter-chip-active");
                    chip.setStyle("-fx-font-size: 9px; -fx-padding: 2px 6px;");
                    chips.getChildren().add(chip);
                }
            }
            // Actions
            HBox actionBox = new HBox(8);
            actionBox.setAlignment(Pos.CENTER_LEFT);
            MFXButton btnLoad = new MFXButton("Apply Preset");
            btnLoad.getStyleClass().addAll("action-btn", "btn-primary");
            btnLoad.setStyle("-fx-font-size: 11px; -fx-padding: 6px 12px; -fx-font-weight: bold;");
            btnLoad.setOnAction(e -> {
                loadFilterSettings(props);
                controller.showNotification("Applied filter preset: " + name);
            });
            MFXButton btnDelete = new MFXButton("Delete");
            btnDelete.getStyleClass().addAll("action-btn", "btn-danger-outline");
            btnDelete.setStyle("-fx-font-size: 11px; -fx-padding: 6px 12px; -fx-font-weight: bold;");
            btnDelete.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Delete Preset");
                confirm.setHeaderText("Delete preset '" + name + "'?");
                confirm.setContentText("This will permanently delete this saved filter configuration. Are you sure?");
                confirm.initOwner(getScene() != null ? getScene().getWindow() : null);
                confirm.showAndWait().ifPresent(res -> {
                    if (res == ButtonType.OK) {
                        com.pstconverter.util.SettingsManager.deleteFilterPreset(name);
                        refreshPresetsList();
                        controller.showNotification("Deleted preset: " + name);
                    }
                });
            });
            actionBox.getChildren().addAll(btnLoad, btnDelete);
            card.getChildren().addAll(lblName, chips, actionBox);
            presetsListContainer.getChildren().add(card);
        }
    }
    private java.util.Properties deserializeProperties(String settingsStr) {
        java.util.Properties props = new java.util.Properties();
        if (settingsStr != null) {
            for (String line : settingsStr.split("\n")) {
                int idx = line.indexOf('=');
                if (idx > 0) {
                    props.setProperty(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                }
            }
        }
        return props;
    }
    private String serializePropertiesToString(java.util.Properties props) {
        if (props == null || props.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String key : props.stringPropertyNames()) {
            sb.append(key).append('=').append(props.getProperty(key)).append('\n');
        }
        return sb.toString().trim();
    }
    private List<String> getActiveSummariesFromProperties(java.util.Properties props) {
        List<String> list = new ArrayList<>();
        // Date
        String from = props.getProperty("date.from");
        String to = props.getProperty("date.to");
        if (from != null || to != null) {
            if (from != null && to != null) {
                list.add(from + " to " + to);
            } else if (from != null) {
                list.add("From " + from);
            } else {
                list.add("Until " + to);
            }
        }
        // Senders
        String incSenders = props.getProperty("sender.include", "");
        if (!incSenders.isEmpty()) list.add("Include Senders");
        String excSenders = props.getProperty("sender.exclude", "");
        if (!excSenders.isEmpty()) list.add("Exclude Senders");
        // Recipients
        String incRcpt = props.getProperty("recipient.include", "");
        if (!incRcpt.isEmpty()) list.add("Include Rcpts");
        String excRcpt = props.getProperty("recipient.exclude", "");
        if (!excRcpt.isEmpty()) list.add("Exclude Rcpts");
        // Keywords
        String incKeys = props.getProperty("keyword.include", "");
        if (!incKeys.isEmpty()) list.add("Include Keywords");
        String excKeys = props.getProperty("keyword.exclude", "");
        if (!excKeys.isEmpty()) list.add("Exclude Keywords");
        // Attachments
        String maxSize = props.getProperty("attachment.maxSize", "0");
        if (!maxSize.equals("0")) {
            list.add("Max size: " + maxSize + " " + props.getProperty("attachment.sizeUnit", "MB"));
        }
        String excTypes = props.getProperty("attachment.excludeTypes", "");
        if (!excTypes.isEmpty()) list.add("Exclude Extensions");
        String incTypes = props.getProperty("attachment.includeTypes", "");
        if (!incTypes.isEmpty()) list.add("Include Extensions");
        // Item types (if some are excluded)
        boolean emails = Boolean.parseBoolean(props.getProperty("item.emails", "true"));
        boolean calendars = Boolean.parseBoolean(props.getProperty("item.calendars", "true"));
        boolean contacts = Boolean.parseBoolean(props.getProperty("item.contacts", "true"));
        boolean tasks = Boolean.parseBoolean(props.getProperty("item.tasks", "true"));
        boolean notes = Boolean.parseBoolean(props.getProperty("item.notes", "true"));
        boolean journals = Boolean.parseBoolean(props.getProperty("item.journals", "true"));
        int typesCount = (emails ? 1 : 0) + (calendars ? 1 : 0) + (contacts ? 1 : 0) + (tasks ? 1 : 0) + (notes ? 1 : 0) + (journals ? 1 : 0);
        if (typesCount < 6) {
            list.add(typesCount + "/6 Item Types");
        }
        // Deduplication
        boolean removeDup = Boolean.parseBoolean(props.getProperty("hygiene.removeDuplicates", "false"));
        if (removeDup) {
            list.add("Deduplication");
        }
        return list;
    }

    public void scanAvailableDateRange() {
        if (controller == null || controller.getStep2ExplorerView() == null) return;

        dateFilterPanel.setRangeScanning(true);

        Task<LocalDate[]> scanTask = new Task<>() {
            @Override
            protected LocalDate[] call() throws Exception {
                List<TreeItem<String>> checkedItems = controller.getStep2ExplorerView().getCheckedTreeItems();
                if (checkedItems == null || checkedItems.isEmpty()) {
                    return new LocalDate[]{null, null};
                }

                LocalDate[] minMax = new LocalDate[]{null, null};

                for (TreeItem<String> item : checkedItems) {
                    if (isCancelled()) break;
                    File sourceFile = controller.getStep2ExplorerView().getSourceFileForNode(item);
                    List<String> path = controller.getStep2ExplorerView().getFolderPathFromNode(item);

                    if (sourceFile != null && sourceFile.exists() && sourceFile.length() > 0) {
                        com.pstconverter.core.adapter.SourceAdapter adapter = com.pstconverter.core.adapter.SourceAdapterFactory.getAdapterForFile(sourceFile);
                        if (adapter != null) {
                            try {
                                adapter.streamEmailsMetadata(sourceFile, path, msg -> {
                                    if (msg.getDate() != null && !msg.getDate().trim().isEmpty()) {
                                        LocalDate ld = parseStringToLocalDate(msg.getDate());
                                        if (ld != null) {
                                            if (minMax[0] == null || ld.isBefore(minMax[0])) minMax[0] = ld;
                                            if (minMax[1] == null || ld.isAfter(minMax[1])) minMax[1] = ld;
                                        }
                                    }
                                });
                            } catch (Exception ignored) {}
                        }
                    } else {
                        // Fallback to Explorer emails (e.g. mock/test emails)
                        List<com.pstconverter.model.MailMessage> emails = controller.getStep2ExplorerView().getEmailsForNode(item, 50);
                        if (emails != null) {
                            for (com.pstconverter.model.MailMessage msg : emails) {
                                if (msg.getDate() != null && !msg.getDate().trim().isEmpty()) {
                                    LocalDate ld = parseStringToLocalDate(msg.getDate());
                                    if (ld != null) {
                                        if (minMax[0] == null || ld.isBefore(minMax[0])) minMax[0] = ld;
                                        if (minMax[1] == null || ld.isAfter(minMax[1])) minMax[1] = ld;
                                    }
                                }
                            }
                        }
                    }
                }
                return minMax;
            }
        };

        scanTask.setOnSucceeded(e -> {
            LocalDate[] result = scanTask.getValue();
            dateFilterPanel.updateAvailableRange(result[0], result[1]);
        });

        scanTask.setOnFailed(e -> {
            dateFilterPanel.setRangeScanning(false);
        });

        Thread thread = new Thread(scanTask, "DateRangeScannerThread");
        thread.setDaemon(true);
        thread.start();
    }

    private static LocalDate parseStringToLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        String clean = dateStr.trim();

        // 1. Try FilterEngine date parser
        try {
            java.util.Date d = com.pstconverter.core.filter.FilterEngine.parseMessageDate(clean);
            if (d != null) {
                return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            }
        } catch (Exception ignored) {}

        // 2. Try LocalDate.parse ISO
        try {
            return LocalDate.parse(clean);
        } catch (Exception ignored) {}

        // 3. Clean timezone abbreviations and format with patterns
        String cleaned = clean.replaceAll("\\b[A-Z]{3,4}\\b", "").replaceAll("\\s+", " ").trim();
        String[] patterns = new String[]{
            "EEE MMM dd HH:mm:ss yyyy",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy",
            "MM/dd/yyyy",
            "yyyy/MM/dd"
        };
        for (String pattern : patterns) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern, java.util.Locale.US);
                java.util.Date d = sdf.parse(cleaned);
                if (d != null) {
                    return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
