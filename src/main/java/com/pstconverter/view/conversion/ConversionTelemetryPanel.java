package com.pstconverter.view.conversion;

import com.pstconverter.util.MaterialIcons;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ConversionTelemetryPanel extends VBox {

    private ProgressBar masterProgressBar;
    private ProgressBar folderProgressBar;
    private Label lblMasterStatus;
    private Label lblFolderStatus;

    private Label lblCurrentSubject;
    private Label lblCurrentFileName;
    private Label lblCurrentFolderPath;
    private Label lblCurrentDestFolder;
    private ProgressBar currentLabelProgressBar;
    private Label lblCurrentLabelProgressText;

    private Label lblSuccess;
    private Label lblFailed;
    private Label lblSkipped;
    private Label lblElapsed;
    private Label lblEta;
    private Label lblSpeed;
    private Label lblOutputSize;
    private Label lblStatus;

    public ConversionTelemetryPanel(Button btnPauseResume, Button btnStop) {
        super(8);
        this.setPadding(new Insets(8, 12, 2, 12));

        // ── Progress section ────────────────────────────────────────────
        VBox progressSection = new VBox(6);
        progressSection.getStyleClass().add("telemetry-progress-card");

        // Master progress
        HBox masterLabelRow = new HBox(6);
        masterLabelRow.setAlignment(Pos.CENTER_LEFT);
        Label masterIcon  = MaterialIcons.icon(MaterialIcons.BAR_CHART, 14);
        masterIcon.getStyleClass().add("master-progress-title");
        Label masterTitle = new Label("MASTER PROGRESS");
        masterTitle.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 800;");
        masterTitle.getStyleClass().add("master-progress-title");
        Region ms = new Region(); HBox.setHgrow(ms, Priority.ALWAYS);
        lblMasterStatus = new Label("Initializing…");
        lblMasterStatus.setStyle("-fx-font-size: 12px;");
        lblMasterStatus.getStyleClass().add("session-files");
        masterLabelRow.getChildren().addAll(masterIcon, masterTitle, ms, lblMasterStatus);

        HBox masterProgressRow = new HBox(8);
        masterProgressRow.setAlignment(Pos.CENTER_LEFT);
        masterProgressRow.setMaxWidth(Double.MAX_VALUE);

        masterProgressBar = new ProgressBar(0);
        masterProgressBar.getStyleClass().add("progress-master");
        masterProgressBar.setMinWidth(100);
        masterProgressBar.setMinHeight(14);
        masterProgressBar.setMaxWidth(Double.MAX_VALUE);
        masterProgressBar.setPrefHeight(14);
        HBox.setHgrow(masterProgressBar, Priority.ALWAYS);

        masterProgressRow.getChildren().addAll(masterProgressBar, btnPauseResume, btnStop);

        // Folder progress
        HBox folderLabelRow = new HBox(6);
        folderLabelRow.setAlignment(Pos.CENTER_LEFT);
        Label folderIcon  = MaterialIcons.icon(MaterialIcons.FOLDER_OPEN, 14);
        folderIcon.getStyleClass().add("folder-progress-title");
        Label folderTitle = new Label("CURRENT FILE PROGRESS");
        folderTitle.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 800;");
        folderTitle.getStyleClass().add("folder-progress-title");
        Region fs = new Region(); HBox.setHgrow(fs, Priority.ALWAYS);
        lblFolderStatus = new Label("Waiting…");
        lblFolderStatus.setStyle("-fx-font-size: 12px;");
        lblFolderStatus.getStyleClass().add("session-files");
        folderLabelRow.getChildren().addAll(folderIcon, folderTitle, fs, lblFolderStatus);

        folderProgressBar = new ProgressBar(0);
        folderProgressBar.getStyleClass().add("progress-folder");
        folderProgressBar.setMinWidth(100);
        folderProgressBar.setMinHeight(10);
        folderProgressBar.setMaxWidth(Double.MAX_VALUE);
        folderProgressBar.setPrefHeight(10);

        progressSection.getChildren().addAll(masterLabelRow, masterProgressRow, folderLabelRow, folderProgressBar);

        // ── Currently processing ────────────────────────────────────────
        VBox statusBox = new VBox(6);
        statusBox.getStyleClass().add("telemetry-card");
        statusBox.setPadding(new Insets(8, 12, 8, 12));

        HBox statusHeader = new HBox(6);
        statusHeader.setAlignment(Pos.CENTER_LEFT);
        Label statusIcon = MaterialIcons.icon(MaterialIcons.TUNE, 13);
        Label statusTitle = new Label("CURRENT MIGRATION DETAILS");
        statusTitle.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 800;");
        statusTitle.getStyleClass().add("migration-detail-header");
        statusHeader.getChildren().addAll(statusIcon, statusTitle);
        statusBox.getChildren().add(statusHeader);

        // 1. File Row
        HBox fileRow = new HBox(8);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        Label fileIcon = MaterialIcons.icon(MaterialIcons.INVENTORY_2, 12);
        fileIcon.setStyle("-fx-text-fill: #818cf8;");
        Label fileLabel = new Label("Source File:");
        fileLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-min-width: 150px;");
        fileLabel.getStyleClass().add("migration-detail-label");
        lblCurrentFileName = new Label("—");
        lblCurrentFileName.setStyle("-fx-font-size: 11.5px;");
        lblCurrentFileName.getStyleClass().add("migration-detail-value");
        lblCurrentFileName.setMinWidth(0);
        lblCurrentFileName.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblCurrentFileName, Priority.ALWAYS);
        lblCurrentFileName.setEllipsisString("…");
        lblCurrentFileName.setTextOverrun(OverrunStyle.ELLIPSIS);
        fileRow.getChildren().addAll(fileIcon, fileLabel, lblCurrentFileName);

        // 2. Folder Row
        HBox folderRow = new HBox(8);
        folderRow.setAlignment(Pos.CENTER_LEFT);
        Label folderIcon2 = MaterialIcons.icon(MaterialIcons.FOLDER, 12);
        folderIcon2.setStyle("-fx-text-fill: #22d3ee;");
        Label folderLabel = new Label("Source Folder:");
        folderLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-min-width: 150px;");
        folderLabel.getStyleClass().add("migration-detail-label");
        lblCurrentFolderPath = new Label("—");
        lblCurrentFolderPath.setStyle("-fx-font-size: 11.5px;");
        lblCurrentFolderPath.getStyleClass().add("migration-detail-value");
        lblCurrentFolderPath.setMinWidth(0);
        lblCurrentFolderPath.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblCurrentFolderPath, Priority.ALWAYS);
        lblCurrentFolderPath.setEllipsisString("…");
        lblCurrentFolderPath.setTextOverrun(OverrunStyle.ELLIPSIS);
        folderRow.getChildren().addAll(folderIcon2, folderLabel, lblCurrentFolderPath);

        // 3. Destination Row
        HBox destRow = new HBox(8);
        destRow.setAlignment(Pos.CENTER_LEFT);
        Label destIcon = MaterialIcons.icon(MaterialIcons.SAVE, 12);
        destIcon.setStyle("-fx-text-fill: #34d399;");
        Label destLabel = new Label("Destination Folder Name:");
        destLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-min-width: 150px;");
        destLabel.getStyleClass().add("migration-detail-label");
        lblCurrentDestFolder = new Label("—");
        lblCurrentDestFolder.setStyle("-fx-font-size: 11.5px;");
        lblCurrentDestFolder.getStyleClass().add("migration-detail-value");
        lblCurrentDestFolder.setMinWidth(0);
        lblCurrentDestFolder.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblCurrentDestFolder, Priority.ALWAYS);
        lblCurrentDestFolder.setEllipsisString("…");
        lblCurrentDestFolder.setTextOverrun(OverrunStyle.ELLIPSIS);
        destRow.getChildren().addAll(destIcon, destLabel, lblCurrentDestFolder);

        // 3.5. Destination Progress Row
        HBox destProgressRow = new HBox(8);
        destProgressRow.setAlignment(Pos.CENTER_LEFT);
        Label destProgIcon = MaterialIcons.icon(MaterialIcons.BAR_CHART, 12);
        destProgIcon.setStyle("-fx-text-fill: #10b981;");
        Label destProgLabel = new Label("Folder/Label Progress:");
        destProgLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-min-width: 150px;");
        destProgLabel.getStyleClass().add("migration-detail-label");
        
        currentLabelProgressBar = new ProgressBar(0);
        currentLabelProgressBar.getStyleClass().add("progress-folder");
        currentLabelProgressBar.setMinWidth(100);
        currentLabelProgressBar.setMinHeight(10);
        currentLabelProgressBar.setMaxWidth(Double.MAX_VALUE);
        currentLabelProgressBar.setPrefHeight(10);
        HBox.setHgrow(currentLabelProgressBar, Priority.ALWAYS);
        
        lblCurrentLabelProgressText = new Label("—");
        lblCurrentLabelProgressText.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold;");
        lblCurrentLabelProgressText.getStyleClass().add("migration-detail-value");
        destProgressRow.getChildren().addAll(destProgIcon, destProgLabel, currentLabelProgressBar, lblCurrentLabelProgressText);

        // 4. Subject Row
        HBox subjectRow = new HBox(8);
        subjectRow.setAlignment(Pos.CENTER_LEFT);
        Label msgIcon = MaterialIcons.icon(MaterialIcons.EMAIL, 12);
        msgIcon.setStyle("-fx-text-fill: #fbbf24;");
        Label msgLabel = new Label("Current Email:");
        msgLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-min-width: 150px;");
        msgLabel.getStyleClass().add("migration-detail-label");
        lblCurrentSubject = new Label("—");
        lblCurrentSubject.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 600;");
        lblCurrentSubject.getStyleClass().add("migration-detail-value");
        lblCurrentSubject.setMinWidth(0);
        lblCurrentSubject.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblCurrentSubject, Priority.ALWAYS);
        lblCurrentSubject.setEllipsisString("…");
        lblCurrentSubject.setTextOverrun(OverrunStyle.ELLIPSIS);
        subjectRow.getChildren().addAll(msgIcon, msgLabel, lblCurrentSubject);

        statusBox.getChildren().addAll(fileRow, folderRow, destRow, destProgressRow, subjectRow);

        // ── Telemetry grid ──────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            grid.getColumnConstraints().add(cc);
        }
        lblSuccess    = makeTelemetryTile(grid, 0, 0, "SUCCESS",     "0",     MaterialIcons.DONE_ALL,  "tile-success");
        lblFailed     = makeTelemetryTile(grid, 1, 0, "FAILED",      "0",     MaterialIcons.ERROR,     "tile-failed");
        lblSkipped    = makeTelemetryTile(grid, 2, 0, "SKIPPED",     "0",     MaterialIcons.BLOCK,     "tile-skipped");
        lblElapsed    = makeTelemetryTile(grid, 3, 0, "ELAPSED",     "00:00", MaterialIcons.SCHEDULE,            "tile-elapsed");
        lblEta        = makeTelemetryTile(grid, 0, 1, "ETA",         "—",     MaterialIcons.SCHEDULE,             "tile-eta");
        lblSpeed      = makeTelemetryTile(grid, 1, 1, "SPEED",       "—",     MaterialIcons.SPEED,                "tile-speed");
        lblOutputSize = makeTelemetryTile(grid, 2, 1, "OUTPUT SIZE", "0 B",   MaterialIcons.DATA_USAGE,           "tile-outputsize");
        lblStatus     = makeTelemetryTile(grid, 3, 1, "STATUS",      "Idle",  MaterialIcons.FIBER_MANUAL_RECORD,  "tile-status");

        this.getChildren().addAll(progressSection, statusBox, grid);
    }

    private Label makeTelemetryTile(GridPane grid, int col, int row, String titleText, String initialValue, String iconCode, String styleClass) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().addAll("telemetry-tile", styleClass);
        
        HBox titleBox = new HBox(4);
        titleBox.setAlignment(Pos.CENTER);
        Label icon = MaterialIcons.icon(iconCode, 14);
        icon.getStyleClass().addAll("telemetry-icon", styleClass + "-icon");
        Label title = new Label(titleText);
        title.getStyleClass().add("telemetry-title");
        titleBox.getChildren().addAll(icon, title);

        Label value = new Label(initialValue);
        value.getStyleClass().addAll("telemetry-value", styleClass + "-value");

        box.getChildren().addAll(titleBox, value);
        grid.add(box, col, row);
        return value;
    }

    // ── Public Update Methods ──────────────────────────────────────────

    public void updateMasterProgress(double progress) {
        Platform.runLater(() -> masterProgressBar.setProgress(progress));
    }

    public void updateFolderProgress(double progress) {
        Platform.runLater(() -> folderProgressBar.setProgress(progress));
    }

    public void setMasterStatus(String status) {
        Platform.runLater(() -> lblMasterStatus.setText(status));
    }

    public void setFolderStatus(String status) {
        Platform.runLater(() -> lblFolderStatus.setText(status));
    }

    public void updateTelemetry(String success, String failed, String skipped, String elapsed, String eta, String speed, String outputSize, String status) {
        Platform.runLater(() -> {
            lblSuccess.setText(success);
            lblFailed.setText(failed);
            lblSkipped.setText(skipped);
            lblElapsed.setText(elapsed);
            lblEta.setText(eta);
            lblSpeed.setText(speed);
            lblOutputSize.setText(outputSize);
            if (status != null) {
                lblStatus.setText(status);
            }
        });
    }

    public void updateCurrentLabel(String subject, String folderPath, String fileName, String destFolder) {
        Platform.runLater(() -> {
            if (subject != null) lblCurrentSubject.setText(subject);
            if (folderPath != null) lblCurrentFolderPath.setText(folderPath);
            if (fileName != null) {
                String cleanName = fileName.trim();
                if (cleanName.toLowerCase().endsWith(".gmail")) {
                    cleanName = cleanName.substring(0, cleanName.length() - 6);
                } else if (cleanName.toLowerCase().endsWith(".imap")) {
                    cleanName = cleanName.substring(0, cleanName.length() - 5);
                }
                lblCurrentFileName.setText(cleanName);
            }
            if (destFolder != null) lblCurrentDestFolder.setText(destFolder);
        });
    }

    public void updateCurrentLabelProgress(double progress, String text) {
        Platform.runLater(() -> {
            if (progress >= 0 && currentLabelProgressBar != null) {
                currentLabelProgressBar.setProgress(progress);
            }
            if (text != null && lblCurrentLabelProgressText != null) {
                lblCurrentLabelProgressText.setText(text);
            }
        });
    }

    public void resetTelemetry() {
        Platform.runLater(() -> {
            masterProgressBar.setProgress(0);
            folderProgressBar.setProgress(0);
            lblMasterStatus.setText("Ready to start");
            lblFolderStatus.setText("Waiting…");

            lblCurrentFileName.setText("None");
            lblCurrentFolderPath.setText("None");
            lblCurrentDestFolder.setText("—");
            if (currentLabelProgressBar != null) currentLabelProgressBar.setProgress(0);
            if (lblCurrentLabelProgressText != null) lblCurrentLabelProgressText.setText("—");
            lblCurrentSubject.setText("—");

            lblSuccess.setText("0");
            lblFailed.setText("0");
            lblSkipped.setText("0");
            lblElapsed.setText("0s");
            lblEta.setText("--");
            lblSpeed.setText("0.00");
            lblOutputSize.setText("0.00 MB");
            lblStatus.setText("Ready");
        });
    }
}
