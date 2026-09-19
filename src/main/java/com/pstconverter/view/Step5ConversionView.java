package com.pstconverter.view;

import com.pstconverter.controller.ConversionTaskManager;
import com.pstconverter.controller.ConversionUIContext;
import com.pstconverter.controller.MainController;
import com.pstconverter.view.conversion.ConversionLogPanel;
import com.pstconverter.view.conversion.ConversionTelemetryPanel;
import com.pstconverter.view.conversion.ConversionTreePanel;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class Step5ConversionView extends BorderPane implements ConversionUIContext {

    private final MainController controller;
    private ConversionTaskManager taskManager;
    private ConversionTreePanel treePanel;
    private ConversionTelemetryPanel telemetryPanel;
    private ConversionLogPanel logPanel;

    private Button btnStart;
    private Button btnPauseResume;
    private Button btnStop;
    private Button btnPrevious;

    private boolean shouldAutoResume = false;

    public Step5ConversionView(MainController controller) {
        this.controller = controller;
        buildUI();
    }

    private void buildUI() {
        boolean isDark = controller != null && controller.isDarkMode();

        btnStart = new Button("▶ Start Conversion");
        btnStart.getStyleClass().addAll("action-btn", "btn-primary");
        String startBaseStyle = "-fx-background-color: linear-gradient(to right, #0ea5e9, #06b6d4); " +
                                "-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; " +
                                "-fx-padding: 8px 20px; -fx-background-radius: 8px; -fx-cursor: hand;";
        btnStart.setStyle(startBaseStyle);
        btnStart.setOnAction(e -> {
            if (taskManager != null) taskManager.startConversion();
        });

        btnPauseResume = new Button("⏸ Pause");
        btnPauseResume.getStyleClass().addAll("action-btn", "btn-secondary");
        String pauseBaseStyle = "-fx-background-color: " + (isDark ? "rgba(245, 158, 11, 0.15)" : "rgba(245, 158, 11, 0.08)") + "; " +
                                "-fx-text-fill: " + (isDark ? "#fcd34d" : "#d97706") + "; " +
                                "-fx-border-color: " + (isDark ? "rgba(245, 158, 11, 0.35)" : "rgba(245, 158, 11, 0.25)") + "; " +
                                "-fx-border-radius: 8px; -fx-background-radius: 8px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8px 18px; -fx-cursor: hand;";
        btnPauseResume.setStyle(pauseBaseStyle);
        btnPauseResume.setOnAction(e -> {
            if (taskManager != null) taskManager.requestPauseResume();
            if (btnPauseResume.getText().contains("Pause")) {
                btnPauseResume.setText("▶ Resume");
            } else {
                btnPauseResume.setText("⏸ Pause");
            }
        });

        btnStop = new Button("⏹ Stop");
        btnStop.getStyleClass().addAll("action-btn", "btn-danger");
        String stopBaseStyle = "-fx-background-color: " + (isDark ? "rgba(239, 68, 68, 0.15)" : "rgba(239, 68, 68, 0.08)") + "; " +
                               "-fx-text-fill: " + (isDark ? "#fca5a5" : "#dc2626") + "; " +
                               "-fx-border-color: " + (isDark ? "rgba(239, 68, 68, 0.35)" : "rgba(239, 68, 68, 0.25)") + "; " +
                               "-fx-border-radius: 8px; -fx-background-radius: 8px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8px 18px; -fx-cursor: hand;";
        btnStop.setStyle(stopBaseStyle);
        btnStop.setDisable(true);
        btnStop.setOnAction(e -> {
            if (taskManager != null) taskManager.confirmStop();
        });

        btnPrevious = new Button("⬅ Back to Destinations");
        btnPrevious.getStyleClass().addAll("action-btn", "btn-secondary");
        String prevBaseStyle = "-fx-background-color: " + (isDark ? "rgba(255, 255, 255, 0.06)" : "rgba(0, 0, 0, 0.04)") + "; " +
                               "-fx-text-fill: " + (isDark ? "#cbd5e1" : "#475569") + "; " +
                               "-fx-border-color: " + (isDark ? "rgba(255, 255, 255, 0.12)" : "#cbd5e1") + "; " +
                               "-fx-border-radius: 8px; -fx-background-radius: 8px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8px 18px; -fx-cursor: hand;";
        btnPrevious.setStyle(prevBaseStyle);
        btnPrevious.setOnAction(e -> controller.showStep(4));

        Region controlsSpacer = new Region();
        HBox.setHgrow(controlsSpacer, Priority.ALWAYS);

        HBox controlsBar = new HBox(12, btnPrevious, controlsSpacer, btnStart);
        controlsBar.setAlignment(Pos.CENTER_LEFT);
        controlsBar.setPadding(new Insets(10, 16, 10, 16));
        controlsBar.setStyle(
            "-fx-background-color: " + (isDark ? "rgba(30, 41, 59, 0.8)" : "rgba(241, 245, 249, 0.9)") + "; " +
            "-fx-border-color: " + (isDark ? "rgba(14, 165, 233, 0.2)" : "#e2e8f0") + "; " +
            "-fx-border-width: 1px 0 0 0;"
        );

        treePanel = new ConversionTreePanel(isDark);
        telemetryPanel = new ConversionTelemetryPanel(btnPauseResume, btnStop);
        logPanel = new ConversionLogPanel();

        VBox rightBox = new VBox(8, telemetryPanel, logPanel);
        rightBox.setPadding(new Insets(6, 14, 6, 14));
        VBox.setVgrow(logPanel, Priority.ALWAYS);
        VBox.setVgrow(rightBox, Priority.ALWAYS);

        this.setLeft(treePanel);
        this.setCenter(rightBox);
        this.setBottom(controlsBar);
    }

    public void setShouldAutoResume(boolean val) {
        this.shouldAutoResume = val;
    }

    public void prepareConversionView() {
        setControlsState("READY");
        // logPanel.clear();

        taskManager = new ConversionTaskManager(controller, this);
        taskManager.setShouldAutoResume(shouldAutoResume);
        taskManager.prepareConversionView();
        
        treePanel.setRootItem(taskManager.getRootItem());
        treePanel.setFolderNodeMap(taskManager.getFolderNodeMap());
        // taskManager.startConversion() is triggered by btnStart
    }

    @Override
    public void appendLog(String message) {
        logPanel.appendLog(message);
    }

    @Override
    public void appendError(String error) {
        logPanel.appendError(error);
    }

    @Override
    public void updateMasterProgress(double progress) {
        telemetryPanel.updateMasterProgress(progress);
    }

    @Override
    public void updateFolderProgress(double progress) {
        telemetryPanel.updateFolderProgress(progress);
    }

    @Override
    public void setMasterStatus(String status) {
        telemetryPanel.setMasterStatus(status);
    }

    @Override
    public void setFolderStatus(String status) {
        telemetryPanel.setFolderStatus(status);
    }

    @Override
    public void updateTelemetry(String success, String failed, String skipped, String elapsed, String eta, String speed, String outputSize, String status) {
        telemetryPanel.updateTelemetry(success, failed, skipped, elapsed, eta, speed, outputSize, status);
    }

    @Override
    public void updateCurrentLabel(String subject, String folderPath, String fileName, String destFolder) {
        telemetryPanel.updateCurrentLabel(subject, folderPath, fileName, destFolder);
    }

    @Override
    public void onConversionFinished(boolean stopped) {
        Platform.runLater(() -> {
            setControlsState("STOPPED");
            if (stopped) {
                telemetryPanel.setMasterStatus("Conversion stopped.");
            } else {
                telemetryPanel.setMasterStatus("Conversion completed successfully.");
            }
        });
    }

    @Override
    public void updateTreeItemStatus(String folderKey, String status, int success, int skipped, int failed, int total) {
        treePanel.updateTreeItemStatus(folderKey, status, success, skipped, failed, total);
    }

    @Override
    public void setResumeStatus(String status) {
        // Ignored
    }

    @Override
    public void setSessionInfo(String info) {
        treePanel.setSessionInfo(null, null, null, info);
    }

    @Override
    public void setSessionFormat(String format) {
        treePanel.setSessionInfo(format, null, null, null);
    }

    @Override
    public void setSessionItems(String items) {
        treePanel.setSessionInfo(null, null, items, null);
    }

    @Override
    public void updateCurrentLabelProgress(double progress, String text) {
        telemetryPanel.updateCurrentLabelProgress(progress, text);
    }

    @Override
    public void resetTelemetry() {
        telemetryPanel.resetTelemetry();
    }

    @Override
    public void updateSessionFiles(String text) {
        treePanel.setSessionInfo(null, text, null, null);
    }

    @Override
    public void setControlsState(String state) {
        Platform.runLater(() -> {
            switch (state) {
                case "READY":
                    btnStart.setVisible(true);
                    btnStart.setManaged(true);
                    btnStart.setDisable(false);
                    btnPrevious.setDisable(false);
                    
                    btnPauseResume.setVisible(false);
                    btnPauseResume.setManaged(false);
                    btnStop.setVisible(false);
                    btnStop.setManaged(false);
                    break;
                case "RUNNING":
                    btnStart.setVisible(false);
                    btnStart.setManaged(false);
                    btnPrevious.setDisable(true);
                    
                    btnPauseResume.setVisible(true);
                    btnPauseResume.setManaged(true);
                    btnPauseResume.setDisable(false);
                    btnPauseResume.setText("⏸ Pause");
                    
                    btnStop.setVisible(true);
                    btnStop.setManaged(true);
                    btnStop.setDisable(false);
                    break;
                case "PAUSED":
                    btnPauseResume.setText("▶ Resume");
                    break;
                case "STOPPED":
                    btnStart.setVisible(false);
                    btnStart.setManaged(false);
                    btnPrevious.setDisable(false);
                    
                    btnPauseResume.setVisible(false);
                    btnPauseResume.setManaged(false);
                    btnStop.setVisible(false);
                    btnStop.setManaged(false);
                    break;
            }
        });
    }

    // --- Delegated Getters for Step6ReportView & PdfReportGenerator ---
    
    public long getSuccessItems() {
        return taskManager != null ? taskManager.getSuccessItems() : 0;
    }

    public long getFailedItems() {
        return taskManager != null ? taskManager.getFailedItems() : 0;
    }

    public long getSkippedItems() {
        return taskManager != null ? taskManager.getSkippedItems() : 0;
    }

    public long getStartTimeMs() {
        return taskManager != null ? taskManager.getStartTimeMs() : 0;
    }

    public double getOutputSizeBytes() {
        return taskManager != null ? taskManager.getOutputSizeBytes() : 0;
    }

    public String getResolvedOutputPath() {
        return taskManager != null ? taskManager.getResolvedOutputPath() : "";
    }

    public boolean isStopRequested() {
        return taskManager != null && taskManager.isStopRequested();
    }

    public javafx.scene.control.TreeItem<String> getRootItem() {
        return taskManager != null ? taskManager.getRootItem() : null;
    }

    public java.util.Map<String, com.pstconverter.controller.conversion.ConversionTelemetryTracker.FolderTelemetry> getFolderTelemetryMap() {
        return taskManager != null ? taskManager.getFolderTelemetryMap() : new java.util.HashMap<>();
    }

    public java.util.Map<String, Integer> getFilterSkipCounts() {
        return taskManager != null ? taskManager.getFilterSkipCounts() : new java.util.HashMap<>();
    }

    public void openMigrationLog() {
        if (taskManager != null) {
            taskManager.openOutputFolder(); // Reuse or adjust based on logic
        }
    }
}
