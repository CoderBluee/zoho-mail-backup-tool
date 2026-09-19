package com.pstconverter.view.filter;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXDatePicker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Properties;

public class DateFilterPanel extends VBox {

    private final Runnable validationCallback;

    private MFXDatePicker dpFromDate;
    private MFXDatePicker dpToDate;
    private Label lblDateValidationError;

    // Available Date Range UI
    private Label lblRangeDisplay;
    private Label lblRangeStatus;
    private MFXButton btnDetectRange;
    private MFXButton btnApplyRange;
    private io.github.palexdev.materialfx.controls.MFXProgressSpinner rangeSpinner;
    private LocalDate detectedEarliest = null;
    private LocalDate detectedLatest = null;
    private Runnable scanTriggerHandler;

    public DateFilterPanel(Runnable validationCallback) {
        this.validationCallback = validationCallback;
        buildUI();
    }

    public void setScanTriggerHandler(Runnable scanTriggerHandler) {
        this.scanTriggerHandler = scanTriggerHandler;
    }

    private VBox createAvailableRangeCard() {
        VBox card = new VBox(6);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.getStyleClass().add("available-range-card");
        card.setStyle("-fx-background-color: rgba(99, 102, 241, 0.06); " +
                      "-fx-border-color: rgba(99, 102, 241, 0.22); " +
                      "-fx-border-width: 1px; " +
                      "-fx-border-radius: 8px; " +
                      "-fx-background-radius: 8px;");

        HBox headerBox = new HBox(8);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label iconLbl = com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.CALENDAR, 16);
        iconLbl.setStyle("-fx-text-fill: #0ea5e9;");

        Label titleLbl = new Label("Available Mailbox Date Boundaries");
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #0284c7;");

        headerBox.getChildren().addAll(iconLbl, titleLbl);

        lblRangeDisplay = new Label("Available Email Date Range: Not Scanned Yet");
        lblRangeDisplay.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        lblRangeStatus = new Label("Click \"Detect Date Range\" to scan selected mailbox folders for valid date boundaries.");
        lblRangeStatus.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #64748b;");
        lblRangeStatus.setWrapText(true);

        rangeSpinner = new io.github.palexdev.materialfx.controls.MFXProgressSpinner();
        rangeSpinner.setPrefSize(16, 16);
        rangeSpinner.setVisible(false);
        rangeSpinner.setManaged(false);

        HBox btnBox = new HBox(8);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        btnDetectRange = new MFXButton("🔍 Detect Date Range");
        btnDetectRange.getStyleClass().addAll("action-btn", "btn-secondary");
        btnDetectRange.setStyle("-fx-font-size: 11px; -fx-padding: 4px 12px;");
        btnDetectRange.setOnAction(e -> {
            if (scanTriggerHandler != null) {
                scanTriggerHandler.run();
            }
        });

        btnApplyRange = new MFXButton("⚡ Apply Range to Filter");
        btnApplyRange.getStyleClass().addAll("action-btn", "btn-primary");
        btnApplyRange.setStyle("-fx-font-size: 11px; -fx-padding: 4px 12px;");
        btnApplyRange.setDisable(true);
        btnApplyRange.setOnAction(e -> {
            if (detectedEarliest != null && detectedLatest != null) {
                dpFromDate.setValue(detectedEarliest);
                dpToDate.setValue(detectedLatest);
            }
        });

        btnBox.getChildren().addAll(btnDetectRange, btnApplyRange, rangeSpinner);

        card.getChildren().addAll(headerBox, lblRangeDisplay, lblRangeStatus, btnBox);
        return card;
    }

    private void buildUI() {
        this.setSpacing(8);
        this.setPadding(new Insets(4));

        Label header = new Label("📅  Date Range");
        header.getStyleClass().add("filter-section-header");

        VBox content = new VBox(10);
        content.setPadding(new Insets(8));

        VBox rangeCard = createAvailableRangeCard();

        dpFromDate = new MFXDatePicker();
        dpFromDate.getStyleClass().add("filter-datepicker");
        dpFromDate.setMaxWidth(Double.MAX_VALUE);
        dpFromDate.setPromptText("Start date...");
        HBox.setHgrow(dpFromDate, Priority.ALWAYS);

        MFXButton btnDecFrom = new MFXButton("-");
        btnDecFrom.getStyleClass().addAll("filter-chip");
        btnDecFrom.setStyle("-fx-font-size: 11px; -fx-padding: 2px 8px; -fx-font-weight: bold;");
        btnDecFrom.setOnAction(e -> shiftDate(dpFromDate, -1));

        MFXButton btnIncFrom = new MFXButton("+");
        btnIncFrom.getStyleClass().addAll("filter-chip");
        btnIncFrom.setStyle("-fx-font-size: 11px; -fx-padding: 2px 8px; -fx-font-weight: bold;");
        btnIncFrom.setOnAction(e -> shiftDate(dpFromDate, 1));

        HBox fromPickerBox = new HBox(4);
        fromPickerBox.setAlignment(Pos.CENTER_LEFT);
        fromPickerBox.getChildren().addAll(btnDecFrom, dpFromDate, btnIncFrom);

        dpToDate = new MFXDatePicker();
        dpToDate.getStyleClass().add("filter-datepicker");
        dpToDate.setMaxWidth(Double.MAX_VALUE);
        dpToDate.setPromptText("End date...");
        HBox.setHgrow(dpToDate, Priority.ALWAYS);

        MFXButton btnDecTo = new MFXButton("-");
        btnDecTo.getStyleClass().addAll("filter-chip");
        btnDecTo.setStyle("-fx-font-size: 11px; -fx-padding: 2px 8px; -fx-font-weight: bold;");
        btnDecTo.setOnAction(e -> shiftDate(dpToDate, -1));

        MFXButton btnIncTo = new MFXButton("+");
        btnIncTo.getStyleClass().addAll("filter-chip");
        btnIncTo.setStyle("-fx-font-size: 11px; -fx-padding: 2px 8px; -fx-font-weight: bold;");
        btnIncTo.setOnAction(e -> shiftDate(dpToDate, 1));

        HBox toPickerBox = new HBox(4);
        toPickerBox.setAlignment(Pos.CENTER_LEFT);
        toPickerBox.getChildren().addAll(btnDecTo, dpToDate, btnIncTo);

        HBox pickersContainer = new HBox(6);
        pickersContainer.setAlignment(Pos.CENTER_LEFT);

        VBox fromCol = new VBox(4);
        Label lblFrom = new Label("Start Date");
        lblFrom.getStyleClass().add("filter-group-label");
        fromCol.getChildren().addAll(lblFrom, fromPickerBox);
        HBox.setHgrow(fromCol, Priority.ALWAYS);

        Label arrow = new Label("➔");
        arrow.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b; -fx-padding: 20px 2px 0 2px;");

        VBox toCol = new VBox(4);
        Label lblTo = new Label("End Date");
        lblTo.getStyleClass().add("filter-group-label");
        toCol.getChildren().addAll(lblTo, toPickerBox);
        HBox.setHgrow(toCol, Priority.ALWAYS);

        pickersContainer.getChildren().addAll(fromCol, arrow, toCol);

        HBox relativeToStartBox = new HBox(6);
        relativeToStartBox.setAlignment(Pos.CENTER_LEFT);
        relativeToStartBox.setVisible(dpFromDate.getValue() != null);
        relativeToStartBox.managedProperty().bind(relativeToStartBox.visibleProperty());

        Label lblSuggest = new Label("Suggest End:");
        lblSuggest.getStyleClass().add("filter-hint-label");
        lblSuggest.setStyle("-fx-font-weight: bold; -fx-text-fill: #818cf8;");
        relativeToStartBox.getChildren().add(lblSuggest);

        String[] offsetLabels = {"+7d", "+30d", "+90d", "+180d", "+1y"};
        int[] offsetDays = {7, 30, 90, 180, 365};
        for (int i = 0; i < offsetLabels.length; i++) {
            MFXButton btnRelative = new MFXButton(offsetLabels[i]);
            btnRelative.getStyleClass().addAll("filter-chip");
            btnRelative.setStyle("-fx-font-size: 9px; -fx-padding: 2px 6px;");
            final int daysToAdd = offsetDays[i];
            btnRelative.setOnAction(e -> {
                LocalDate start = dpFromDate.getValue();
                if (start != null) {
                    LocalDate target = start.plusDays(daysToAdd);
                    LocalDate today = LocalDate.now();
                    if (target.isAfter(today)) {
                        target = today;
                    }
                    dpToDate.setValue(target);
                }
            });
            relativeToStartBox.getChildren().add(btnRelative);
        }

        lblDateValidationError = new Label("");
        lblDateValidationError.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px; -fx-font-weight: bold;");
        lblDateValidationError.setVisible(false);
        lblDateValidationError.setManaged(false);
        lblDateValidationError.setWrapText(true);

        dpFromDate.valueProperty().addListener((o, ov, nv) -> {
            relativeToStartBox.setVisible(nv != null);
            validationCallback.run();
        });

        dpToDate.valueProperty().addListener((o, ov, nv) -> {
            validationCallback.run();
        });

        Label lblPresets = new Label("Quick Presets");
        lblPresets.getStyleClass().add("filter-group-label");

        FlowPane presetPane = new FlowPane(6, 6);
        String[] presets = {"Last 30 Days", "Last 90 Days", "Last 6 Months", "Last 1 Year"};
        int[] days = {30, 90, 180, 365};
        for (int i = 0; i < presets.length; i++) {
            MFXButton btn = new MFXButton(presets[i]);
            btn.getStyleClass().addAll("filter-chip");
            btn.setStyle("-fx-font-size: 10px; -fx-padding: 3px 10px;");
            final int d = days[i];
            btn.setOnAction(e -> {
                dpFromDate.setValue(LocalDate.now().minusDays(d));
                dpToDate.setValue(LocalDate.now());
            });
            presetPane.getChildren().add(btn);
        }

        MFXButton btnClearDates = new MFXButton("Clear Dates");
        btnClearDates.getStyleClass().addAll("action-btn", "btn-secondary");
        btnClearDates.setStyle("-fx-font-size: 10px; -fx-padding: 3px 10px;");
        btnClearDates.setOnAction(e -> {
            dpFromDate.setValue(null);
            dpToDate.setValue(null);
        });
        presetPane.getChildren().add(btnClearDates);

        Label hint = new Label("Only emails within this date range will be exported.");
        hint.getStyleClass().add("filter-hint-label");
        hint.setWrapText(true);

        content.getChildren().addAll(rangeCard, pickersContainer, relativeToStartBox, lblDateValidationError, lblPresets, presetPane, hint);
        this.getChildren().addAll(header, content);
    }

    public void setRangeScanning(boolean scanning) {
        if (rangeSpinner != null) {
            rangeSpinner.setVisible(scanning);
            rangeSpinner.setManaged(scanning);
        }
        if (btnDetectRange != null) {
            btnDetectRange.setDisable(scanning);
        }
        if (scanning) {
            lblRangeStatus.setText("Scanning selected mailbox folders for date boundaries...");
            lblRangeStatus.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #0284c7; -fx-font-weight: bold;");
        }
    }

    public void updateAvailableRange(LocalDate earliest, LocalDate latest) {
        setRangeScanning(false);
        this.detectedEarliest = earliest;
        this.detectedLatest = latest;

        if (earliest != null && latest != null) {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.US);
            String strEarliest = earliest.format(fmt);
            String strLatest = latest.format(fmt);
            lblRangeDisplay.setText("Available Email Date Range: " + strEarliest + " → " + strLatest);
            lblRangeDisplay.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #059669;");
            lblRangeStatus.setText("Successfully scanned selected folders. Click \"Apply Range to Filter\" to fill Start/End dates.");
            lblRangeStatus.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #64748b;");
            btnApplyRange.setDisable(false);
        } else {
            lblRangeDisplay.setText("Available Email Date Range: No Valid Dates Found");
            lblRangeDisplay.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #ef4444;");
            lblRangeStatus.setText("Could not detect valid email date headers in selected folders.");
            lblRangeStatus.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #64748b;");
            btnApplyRange.setDisable(true);
        }
    }

    private void shiftDate(MFXDatePicker dp, int days) {
        LocalDate current = dp.getValue();
        if (current == null) {
            dp.setValue(LocalDate.now());
        } else {
            dp.setValue(current.plusDays(days));
        }
    }

    public void saveProperties(Properties props) {
        if (dpFromDate.getValue() != null) props.setProperty("date.from", dpFromDate.getValue().toString());
        if (dpToDate.getValue() != null) props.setProperty("date.to", dpToDate.getValue().toString());
    }

    public void loadProperties(Properties props) {
        String fromDateStr = props.getProperty("date.from");
        if (fromDateStr != null && !fromDateStr.isEmpty()) {
            try { dpFromDate.setValue(LocalDate.parse(fromDateStr)); } catch (Exception ignored) {}
        } else {
            dpFromDate.setValue(null);
        }

        String toDateStr = props.getProperty("date.to");
        if (toDateStr != null && !toDateStr.isEmpty()) {
            try { dpToDate.setValue(LocalDate.parse(toDateStr)); } catch (Exception ignored) {}
        } else {
            dpToDate.setValue(null);
        }
    }

    public boolean validate() {
        boolean isValid = true;
        if (dpFromDate.getValue() != null && dpToDate.getValue() != null) {
            if (dpToDate.getValue().isBefore(dpFromDate.getValue())) {
                isValid = false;
                lblDateValidationError.setText("End Date cannot be before Start Date.");
                lblDateValidationError.setVisible(true);
            } else {
                lblDateValidationError.setVisible(false);
            }
        } else {
            lblDateValidationError.setVisible(false);
        }
        lblDateValidationError.setManaged(lblDateValidationError.isVisible());
        return isValid;
    }

    public LocalDate getFromDate() {
        return dpFromDate.getValue();
    }

    public LocalDate getToDate() {
        return dpToDate.getValue();
    }
}
