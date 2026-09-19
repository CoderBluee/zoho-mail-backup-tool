package com.pstconverter.view;

import com.pstconverter.controller.MainController;
import com.pstconverter.core.model.SourceFileModel;
import com.pstconverter.util.MaterialIcons;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TreeView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeCell;
import javafx.scene.layout.*;
import java.io.File;
import java.text.DecimalFormat;
import java.util.List;

public class Step6ReportView extends BorderPane {

    private final MainController controller;

    // UI elements to update dynamically
    private VBox checkBadge;
    private Label checkIcon;
    private Label lblTitle;
    private Label lblSubtitle;

    private Label lblSummaryConverted;
    private Label lblSummarySkipped;
    private Label lblSummaryFailed;
    private Label lblSummaryTime;
    private Label lblSummaryOutput;
    private Label lblSummaryOutputFolder;

    private VBox filtersListContainer;

    // Badge and Icon references for metric tiles
    private StackPane badgeConverted;
    private StackPane badgeSkipped;
    private StackPane badgeFailed;
    private StackPane badgeTime;
    private StackPane badgeOutput;

    private Label iconConverted;
    private Label iconSkipped;
    private Label iconFailed;
    private Label iconTime;
    private Label iconOutput;

    // Button icon references
    private Label iconSessionBtn;
    private Label iconConvertBtn;

    // Telemetry visual components
    private VBox skipReasonsContainer;
    private TreeView<String> reportTreeView;
    private TreeItem<String> reportTreeRoot;
    private PieChart outcomePieChart;

    public Step6ReportView(MainController controller) {
        this.controller = controller;
        initializeUI();
    }

    private void initializeUI() {
        // ── TOP FIXED BAR ──
        HBox topBar = new HBox(24);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(20, 32, 20, 32));
        topBar.getStyleClass().add("report-header-bar");

        // Status Badge VBox
        checkBadge = new VBox();
        checkBadge.setAlignment(Pos.CENTER);
        checkBadge.setPrefSize(52, 52);
        checkBadge.setMaxSize(52, 52);
        checkBadge.setMinSize(52, 52);
        checkIcon = MaterialIcons.icon(MaterialIcons.CHECK_CIRCLE, "-fx-font-size: 28px;");
        checkBadge.getChildren().add(checkIcon);

        // Text titles
        VBox titleBox = new VBox(6);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        lblTitle = new Label("Migration Process Complete!");
        lblSubtitle = new Label("All selected folders and metadata have been written to the destination repository.");
        lblSubtitle.getStyleClass().add("report-subtitle");
        lblSubtitle.setStyle("-fx-font-size: 13px; -fx-wrap-text: true;");
        titleBox.getChildren().addAll(lblTitle, lblSubtitle);

        // Action Buttons Row
        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        boolean isDark = controller != null && controller.isDarkMode();

        Button btnOpenOutputBanner = new Button("Open Output Folder");
        Label iconOutputBtn = MaterialIcons.icon(MaterialIcons.FOLDER_OPEN);
        iconOutputBtn.setStyle("-fx-font-size: 16px; -fx-text-fill: white;");
        btnOpenOutputBanner.setGraphic(iconOutputBtn);
        btnOpenOutputBanner.getStyleClass().addAll("action-btn", "btn-primary");
        btnOpenOutputBanner.setStyle("-fx-background-color: linear-gradient(to right, #0ea5e9, #06b6d4); -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 8px 18px; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-cursor: hand;");
        btnOpenOutputBanner.setOnAction(e -> openOutputFolder());

        Button btnDownloadPdf = new Button("Download PDF Report");
        Label iconPdfBtn = MaterialIcons.icon(MaterialIcons.PICTURE_AS_PDF);
        iconPdfBtn.setStyle("-fx-font-size: 16px; -fx-text-fill: white;");
        btnDownloadPdf.setGraphic(iconPdfBtn);
        btnDownloadPdf.getStyleClass().addAll("action-btn", "btn-primary");
        btnDownloadPdf.setStyle("-fx-background-color: linear-gradient(to right, #0284c7, #0ea5e9); -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 8px 18px; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-cursor: hand;");
        btnDownloadPdf.setOnAction(e -> handleDownloadPdfReport());

        Button btnOpenSession = new Button("Open Session Folder");
        iconSessionBtn = MaterialIcons.icon(MaterialIcons.HISTORY);
        btnOpenSession.setGraphic(iconSessionBtn);
        btnOpenSession.getStyleClass().addAll("action-btn", "btn-secondary");
        String secBtnStyle = "-fx-background-color: " + (isDark ? "rgba(255, 255, 255, 0.06)" : "rgba(0, 0, 0, 0.04)") + "; -fx-text-fill: " + (isDark ? "#38bdf8" : "#0284c7") + "; -fx-border-color: " + (isDark ? "rgba(14, 165, 233, 0.3)" : "rgba(14, 165, 233, 0.25)") + "; -fx-border-width: 1px; -fx-font-size: 12px; -fx-padding: 8px 18px; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-cursor: hand;";
        btnOpenSession.setStyle(secBtnStyle);
        btnOpenSession.setOnAction(e -> openSessionFolder());

        Button btnConvertAgain = new Button("Convert Again");
        iconConvertBtn = MaterialIcons.icon(MaterialIcons.REFRESH);
        btnConvertAgain.setGraphic(iconConvertBtn);
        btnConvertAgain.getStyleClass().addAll("action-btn", "btn-secondary");
        btnConvertAgain.setStyle(secBtnStyle);
        btnConvertAgain.setOnAction(e -> handleConvertAgain());

        actionRow.getChildren().addAll(btnOpenOutputBanner, btnDownloadPdf, btnOpenSession, btnConvertAgain);
        topBar.getChildren().addAll(checkBadge, titleBox, actionRow);
        this.setTop(topBar);

        // ── CENTER SCROLLABLE REPORT CONTENT ──
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("conversion-scrollpane");
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        VBox contentBox = new VBox(24);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPadding(new Insets(24, 32, 24, 32));
        contentBox.setStyle("-fx-background-color: transparent;");

        // Stats Grid
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(16);
        statsGrid.setVgap(12);
        statsGrid.setAlignment(Pos.CENTER);
        for (int i = 0; i < 5; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(20.0);
            cc.setHalignment(HPos.CENTER);
            statsGrid.getColumnConstraints().add(cc);
        }
        addSummaryTile(statsGrid, 0, MaterialIcons.CHECK_CIRCLE, "Successfully Converted", "—", "#34d399", "converted");
        addSummaryTile(statsGrid, 1, MaterialIcons.BLOCK, "Skipped Items", "—", "#64748b", "skipped");
        addSummaryTile(statsGrid, 2, MaterialIcons.WARNING, "Failed Items", "—", "#f87171", "failed");
        addSummaryTile(statsGrid, 3, MaterialIcons.SCHEDULE, "Elapsed Duration", "—", "#a78bfa", "time");
        addSummaryTile(statsGrid, 4, MaterialIcons.DATA_USAGE, "Exported Data Size", "—", "#38bdf8", "output");

        // Main 2-column layout Split
        HBox columnsContainer = new HBox(20);
        columnsContainer.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(columnsContainer, Priority.ALWAYS);

        // ── LEFT COLUMN: Pie Chart + Parameters ──
        VBox leftColumn = new VBox(20);
        leftColumn.setAlignment(Pos.TOP_CENTER);
        leftColumn.setPrefWidth(380);
        leftColumn.setMinWidth(300);
        HBox.setHgrow(leftColumn, Priority.ALWAYS);

        // Visual Metrics Card
        VBox metricsCard = new VBox(14);
        metricsCard.setPadding(new Insets(18));
        metricsCard.getStyleClass().add("report-card");
        Label metricsTitle = new Label("VISUAL RUN METRICS");
        metricsTitle.getStyleClass().add("report-card-title");
        metricsTitle.setStyle("-fx-font-size: 11.5px; -fx-font-weight: extra-bold;");

        outcomePieChart = new PieChart();
        outcomePieChart.setLabelsVisible(true);
        outcomePieChart.setLegendVisible(false);
        outcomePieChart.setPrefHeight(200);
        outcomePieChart.setPrefWidth(200);

        metricsCard.getChildren().addAll(metricsTitle, new Separator(), outcomePieChart);

        // Migration Parameters Card
        VBox parametersCard = new VBox(14);
        parametersCard.setPadding(new Insets(18));
        parametersCard.getStyleClass().add("report-card");
        Label paramsTitle = new Label("MIGRATION PARAMETERS");
        paramsTitle.getStyleClass().add("report-card-title");
        paramsTitle.setStyle("-fx-font-size: 11.5px; -fx-font-weight: extra-bold;");

        VBox pathBox = new VBox(6);
        pathBox.getStyleClass().add("path-preview-box");
        Label lblPathHeader = new Label("Destination Folder Path");
        lblPathHeader.getStyleClass().add("path-preview-title");
        lblPathHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        lblSummaryOutputFolder = new Label("—");
        lblSummaryOutputFolder.setWrapText(true);
        lblSummaryOutputFolder.getStyleClass().add("path-preview-text");
        lblSummaryOutputFolder.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        pathBox.getChildren().addAll(lblPathHeader, lblSummaryOutputFolder);

        VBox filtersBox = new VBox(8);
        Label lblFiltersHeader = new Label("Applied Filters & Exclusion Rules");
        lblFiltersHeader.getStyleClass().add("report-label-header");
        lblFiltersHeader.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold;");
        filtersListContainer = new VBox(6);
        filtersListContainer.setPadding(new Insets(10));
        filtersListContainer.setStyle("-fx-background-color: rgba(255, 255, 255, 0.02); -fx-background-radius: 8px; -fx-border-color: rgba(0, 0, 0, 0.06); -fx-border-radius: 8px;");
        filtersBox.getChildren().addAll(lblFiltersHeader, filtersListContainer);

        parametersCard.getChildren().addAll(paramsTitle, new Separator(), pathBox, filtersBox);
        leftColumn.getChildren().addAll(metricsCard, parametersCard);

        // ── RIGHT COLUMN: Folders Tree + Skips Breakdown ──
        VBox rightColumn = new VBox(20);
        rightColumn.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);

        // Folder Migration Tree Card
        VBox treeCard = new VBox(14);
        treeCard.setPadding(new Insets(18));
        treeCard.getStyleClass().add("report-card");
        Label treeTitle = new Label("MIGRATION TARGET TREE");
        treeTitle.getStyleClass().add("report-card-title");
        treeTitle.setStyle("-fx-font-size: 11.5px; -fx-font-weight: extra-bold;");

        reportTreeView = new TreeView<>();
        reportTreeView.setShowRoot(false);
        reportTreeView.setEditable(false);
        reportTreeView.getStyleClass().add("conversion-folder-tree");
        reportTreeView.setPrefHeight(250);
        reportTreeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    String[] parts  = item.split("\\|", 2);
                    String status   = parts.length > 1 ? parts[0] : "";
                    String name     = parts.length > 1 ? parts[1] : item;
                    setText(null);

                    HBox row = new HBox(6);
                    row.setAlignment(Pos.CENTER_LEFT);

                    Label iconL;
                    String textStyle;
                    boolean isDarkCurrent = controller != null && controller.isDarkMode();
                    switch (status) {
                        case "DONE" -> {
                            iconL = MaterialIcons.icon(MaterialIcons.CHECK_CIRCLE, 13);
                            iconL.setStyle("-fx-text-fill: #10b981; -fx-font-size: 13px;");
                            textStyle = "-fx-text-fill: " + (isDarkCurrent ? "#34d399" : "#065f46") + "; -fx-font-size: 13px;";
                        }
                        case "ERROR" -> {
                            iconL = MaterialIcons.icon(MaterialIcons.ERROR, 13);
                            iconL.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 13px;");
                            textStyle = "-fx-text-fill: #ef4444; -fx-font-size: 13px;";
                        }
                        case "ACTIVE" -> {
                            iconL = MaterialIcons.icon(MaterialIcons.PLAY_ARROW, 13);
                            iconL.setStyle("-fx-text-fill: #fb923c; -fx-font-size: 13px;");
                            textStyle = "-fx-text-fill: #fb923c; -fx-font-weight: bold; -fx-font-size: 13px;";
                        }
                        case "FILE" -> {
                            iconL = MaterialIcons.icon(MaterialIcons.INVENTORY_2, 14);
                            iconL.setStyle("-fx-text-fill: #0ea5e9; -fx-font-size: 14px;");
                            textStyle = "-fx-text-fill: " + (isDarkCurrent ? "#7dd3fc" : "#0284c7") + "; -fx-font-weight: bold; -fx-font-size: 13px;";
                        }
                        case "PENDING" -> {
                            iconL = MaterialIcons.icon(MaterialIcons.FOLDER, 13);
                            iconL.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");
                            textStyle = "-fx-text-fill: " + (isDarkCurrent ? "#94a3b8" : "#475569") + "; -fx-font-size: 13px;";
                        }
                        default -> {
                            iconL = MaterialIcons.icon(MaterialIcons.FOLDER, 13);
                            iconL.setStyle("-fx-text-fill: #818cf8; -fx-font-size: 13px;");
                            textStyle = "-fx-text-fill: " + (isDarkCurrent ? "#94a3b8" : "#475569") + "; -fx-font-size: 13px;";
                        }
                    }

                    Label nameL = new Label(name);
                    nameL.setStyle(textStyle);
                    row.getChildren().addAll(iconL, nameL);
                    setGraphic(row);
                    setStyle("-fx-padding: 3px 8px; -fx-background-color: transparent;");
                }
            }
        });
        reportTreeRoot = new TreeItem<>("Root");
        reportTreeView.setRoot(reportTreeRoot);

        treeCard.getChildren().addAll(treeTitle, new Separator(), reportTreeView);

        // Exclusion Breakdown Card
        VBox skipsCard = new VBox(14);
        skipsCard.setPadding(new Insets(18));
        skipsCard.getStyleClass().add("report-card");
        Label skipsTitle = new Label("FILTER EXCLUSION BREAKDOWN");
        skipsTitle.getStyleClass().add("report-card-title");
        skipsTitle.setStyle("-fx-font-size: 11.5px; -fx-font-weight: extra-bold;");

        skipReasonsContainer = new VBox(8);

        skipsCard.getChildren().addAll(skipsTitle, new Separator(), skipReasonsContainer);

        rightColumn.getChildren().addAll(treeCard, skipsCard);

        columnsContainer.getChildren().addAll(leftColumn, rightColumn);

        contentBox.getChildren().addAll(statsGrid, columnsContainer);
        scrollPane.setContent(contentBox);
        this.setCenter(scrollPane);
    }

    private void addSummaryTile(GridPane grid, int col, String iconCodepoint, String caption, String value, String colorHex, String id) {
        HBox tile = new HBox(16);
        tile.setAlignment(Pos.CENTER_LEFT);
        tile.setPadding(new Insets(16, 20, 16, 20));
        tile.getStyleClass().add("report-tile");
        boolean isDark = controller != null && controller.isDarkMode();
        String tileBg = "-fx-background-color: " + (isDark ? "rgba(30, 41, 59, 0.6)" : "#ffffff") + "; " +
                        "-fx-border-color: " + (isDark ? "rgba(255, 255, 255, 0.08)" : "#e2e8f0") + "; " +
                        "-fx-border-width: 1.5px; -fx-border-radius: 12px; -fx-background-radius: 12px;";
        tile.setStyle(tileBg);

        StackPane badge = new StackPane();
        badge.setPrefSize(42, 42);
        badge.setMaxSize(42, 42);
        badge.setMinSize(42, 42);

        String bgStyle = "-fx-background-color: " + colorHex + "1a; -fx-background-radius: 50%; -fx-border-color: " + colorHex + "33; -fx-border-width: 1.5px; -fx-border-radius: 50%;";
        badge.setStyle(bgStyle);

        Label iconLabel = MaterialIcons.icon(iconCodepoint, "-fx-font-size: 20px; -fx-text-fill: " + colorHex + ";");
        badge.getChildren().add(iconLabel);

        VBox textBox = new VBox(2);
        textBox.setAlignment(Pos.CENTER_LEFT);

        Label valL = new Label(value);
        valL.setStyle("-fx-font-size: 24px; -fx-font-weight: extra-bold; -fx-text-fill: " + colorHex + ";");

        Label capL = new Label(caption);
        capL.getStyleClass().add("report-tile-caption");
        capL.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        switch (id) {
            case "converted" -> {
                lblSummaryConverted = valL;
                badgeConverted = badge;
                iconConverted = iconLabel;
            }
            case "skipped" -> {
                lblSummarySkipped = valL;
                badgeSkipped = badge;
                iconSkipped = iconLabel;
            }
            case "failed" -> {
                lblSummaryFailed = valL;
                badgeFailed = badge;
                iconFailed = iconLabel;
            }
            case "time" -> {
                lblSummaryTime = valL;
                badgeTime = badge;
                iconTime = iconLabel;
            }
            case "output" -> {
                lblSummaryOutput = valL;
                badgeOutput = badge;
                iconOutput = iconLabel;
            }
        }

        textBox.getChildren().addAll(valL, capL);
        tile.getChildren().addAll(badge, textBox);

        // Hover scale animation
        tile.setOnMouseEntered(e -> {
            tile.setScaleX(1.02);
            tile.setScaleY(1.02);
            tile.setStyle(tileBg + "-fx-effect: dropshadow(three-pass-box, " + colorHex + "26, 12, 0, 0, 4);");
        });
        tile.setOnMouseExited(e -> {
            tile.setScaleX(1.0);
            tile.setScaleY(1.0);
            tile.setStyle(tileBg);
        });

        GridPane.setConstraints(tile, col, 0);
        grid.getChildren().add(tile);
    }

    public void loadReportData() {
        System.out.println("Entering Step 6 (Report View). Loading telemetry and report data.");
        Step5ConversionView step5 = controller.getStep5ConversionView();
        if (step5 == null) return;

        long success = step5.getSuccessItems();
        long failed = step5.getFailedItems();
        long skipped = step5.getSkippedItems();
        long startTime = step5.getStartTimeMs();
        double bytes = step5.getOutputSizeBytes();
        String outputPath = step5.getResolvedOutputPath();
        boolean stopped = step5.isStopRequested();

        // 1. Calculate Elapsed Time
        long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
        String timeStr = String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60);

        // 2. Format Exported Data Size
        double sizeMb = bytes / (1024.0 * 1024.0);
        String sizeStr = sizeMb > 1024 ? String.format("%.1f GB", sizeMb / 1024) : String.format("%.1f MB", sizeMb);

        System.out.println("=== Migration Report Loaded ===");
        System.out.println("Successfully Converted: " + success);
        System.out.println("Skipped Items: " + skipped);
        System.out.println("Failed Items: " + failed);
        System.out.println("Elapsed Duration: " + timeStr);
        System.out.println("Exported Data Size: " + sizeStr);
        System.out.println("Output Folder: " + (outputPath != null ? outputPath : "Cloud Storage Migration"));

        // Populate basic stats labels
        lblSummaryConverted.setText(String.valueOf(success));
        lblSummarySkipped.setText(String.valueOf(skipped));
        lblSummaryFailed.setText(String.valueOf(failed));
        lblSummaryTime.setText(timeStr);
        lblSummaryOutput.setText(sizeStr);

        boolean isDark = controller.isDarkMode();
        String colorSuccess = isDark ? "#34d399" : "#059669";
        String colorSkipped = isDark ? "#94a3b8" : "#64748b";
        String colorFailed = isDark ? "#f87171" : "#dc2626";
        String colorTime = isDark ? "#a78bfa" : "#7c3aed";
        String colorSize = isDark ? "#38bdf8" : "#0284c7";

        lblSummaryConverted.setStyle("-fx-font-size: 24px; -fx-font-weight: extra-bold; -fx-text-fill: " + colorSuccess + ";");
        lblSummarySkipped.setStyle("-fx-font-size: 24px; -fx-font-weight: extra-bold; -fx-text-fill: " + colorSkipped + ";");
        lblSummaryFailed.setStyle("-fx-font-size: 24px; -fx-font-weight: extra-bold; -fx-text-fill: " + colorFailed + ";");
        lblSummaryTime.setStyle("-fx-font-size: 24px; -fx-font-weight: extra-bold; -fx-text-fill: " + colorTime + ";");
        lblSummaryOutput.setStyle("-fx-font-size: 24px; -fx-font-weight: extra-bold; -fx-text-fill: " + colorSize + ";");

        // Update tile badges & icons
        badgeConverted.setStyle("-fx-background-color: " + colorSuccess + "1a; -fx-background-radius: 50%; -fx-border-color: " + colorSuccess + "33; -fx-border-width: 1.5px; -fx-border-radius: 50%;");
        badgeSkipped.setStyle("-fx-background-color: " + colorSkipped + "1a; -fx-background-radius: 50%; -fx-border-color: " + colorSkipped + "33; -fx-border-width: 1.5px; -fx-border-radius: 50%;");
        badgeFailed.setStyle("-fx-background-color: " + colorFailed + "1a; -fx-background-radius: 50%; -fx-border-color: " + colorFailed + "33; -fx-border-width: 1.5px; -fx-border-radius: 50%;");
        badgeTime.setStyle("-fx-background-color: " + colorTime + "1a; -fx-background-radius: 50%; -fx-border-color: " + colorTime + "33; -fx-border-width: 1.5px; -fx-border-radius: 50%;");
        badgeOutput.setStyle("-fx-background-color: " + colorSize + "1a; -fx-background-radius: 50%; -fx-border-color: " + colorSize + "33; -fx-border-width: 1.5px; -fx-border-radius: 50%;");

        iconConverted.setStyle("-fx-font-size: 20px; -fx-text-fill: " + colorSuccess + ";");
        iconSkipped.setStyle("-fx-font-size: 20px; -fx-text-fill: " + colorSkipped + ";");
        iconFailed.setStyle("-fx-font-size: 20px; -fx-text-fill: " + colorFailed + ";");
        iconTime.setStyle("-fx-font-size: 20px; -fx-text-fill: " + colorTime + ";");
        iconOutput.setStyle("-fx-font-size: 20px; -fx-text-fill: " + colorSize + ";");

        // Action button icon colors
        String btnSecColor = isDark ? "#38bdf8" : "#0284c7";
        iconSessionBtn.setStyle("-fx-font-size: 16px; -fx-text-fill: " + btnSecColor + ";");
        iconConvertBtn.setStyle("-fx-font-size: 16px; -fx-text-fill: " + btnSecColor + ";");

        // 3. Configure PieChart Outcomes Visual
        outcomePieChart.getData().clear();
        if (success > 0) {
            outcomePieChart.getData().add(new PieChart.Data("Success (" + success + ")", success));
        }
        if (skipped > 0) {
            outcomePieChart.getData().add(new PieChart.Data("Skipped (" + skipped + ")", skipped));
        }
        if (failed > 0) {
            outcomePieChart.getData().add(new PieChart.Data("Failed (" + failed + ")", failed));
        }

        // Apply slice colors
        for (PieChart.Data data : outcomePieChart.getData()) {
            String style = "";
            if (data.getName().startsWith("Success")) {
                style = "-fx-pie-color: " + (isDark ? "#10b981" : "#059669") + ";";
            } else if (data.getName().startsWith("Skipped")) {
                style = "-fx-pie-color: " + (isDark ? "#94a3b8" : "#64748b") + ";";
            } else if (data.getName().startsWith("Failed")) {
                style = "-fx-pie-color: " + (isDark ? "#ef4444" : "#dc2626") + ";";
            }
            if (!style.isEmpty() && data.getNode() != null) {
                data.getNode().setStyle(style);
            }
        }

        // 4. Configure completion badges and header colors based on outcomes
        if (stopped) {
            String col = isDark ? "#fb923c" : "#d97706";
            checkBadge.setStyle("-fx-background-color: " + col + "1a; -fx-background-radius: 50%; -fx-border-color: " + col + "33; -fx-border-width: 2px; -fx-border-radius: 50%;");
            checkIcon.setText(MaterialIcons.INFO);
            checkIcon.setStyle("-fx-font-size: 28px; -fx-text-fill: " + col + ";");
            lblTitle.setText("Migration Process Terminated");
            lblTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: " + col + ";");
            lblSubtitle.setText("The migration was cancelled by the user. Output contains partially migrated data.");
        } else if (failed > 0) {
            String col = isDark ? "#f87171" : "#dc2626";
            checkBadge.setStyle("-fx-background-color: " + col + "1a; -fx-background-radius: 50%; -fx-border-color: " + col + "33; -fx-border-width: 2px; -fx-border-radius: 50%;");
            checkIcon.setText(MaterialIcons.WARNING);
            checkIcon.setStyle("-fx-font-size: 28px; -fx-text-fill: " + col + ";");
            lblTitle.setText("Migration Completed with Issues");
            lblTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: " + col + ";");
            lblSubtitle.setText("The migration process finished, but some items encountered failures.");
        } else {
            String col = isDark ? "#34d399" : "#059669";
            checkBadge.setStyle("-fx-background-color: " + col + "1a; -fx-background-radius: 50%; -fx-border-color: " + col + "33; -fx-border-width: 2px; -fx-border-radius: 50%;");
            checkIcon.setText(MaterialIcons.CHECK_CIRCLE);
            checkIcon.setStyle("-fx-font-size: 28px; -fx-text-fill: " + col + ";");
            lblTitle.setText("Migration Process Complete!");
            lblTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: " + col + ";");
            lblSubtitle.setText("All selected folders and metadata have been written to the destination repository.");
        }

        // 5. Set Output Destination Folder Path
        lblSummaryOutputFolder.setText(outputPath != null ? outputPath : "Cloud Storage Migration");

        // 6. Populate applied filters list
        filtersListContainer.getChildren().clear();
        Step3FilterView step3 = controller.getStep3FilterView();
        if (step3 != null) {
            List<String> activeFilters = step3.getActiveFilterSummaryChips();
            for (String filter : activeFilters) {
                HBox chip = new HBox(8);
                chip.setAlignment(Pos.CENTER_LEFT);
                chip.setPadding(new Insets(4, 10, 4, 10));
                chip.setStyle("-fx-background-color: " + (isDark ? "rgba(14, 165, 233, 0.15)" : "rgba(14, 165, 233, 0.08)") + "; -fx-background-radius: 8px; -fx-border-color: " + (isDark ? "rgba(14, 165, 233, 0.3)" : "rgba(14, 165, 233, 0.15)") + "; -fx-border-radius: 8px;");
                
                Label dot = MaterialIcons.icon(MaterialIcons.BOLT, "-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#38bdf8" : "#0284c7") + ";");
                Label text = new Label(filter);
                text.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cbd5e1" : "#1e293b") + ";");
                
                chip.getChildren().addAll(dot, text);
                filtersListContainer.getChildren().add(chip);
            }
            if (activeFilters.isEmpty()) {
                Label noFilters = new Label("No filters applied (Full migration)");
                noFilters.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + "; -fx-font-style: italic;");
                filtersListContainer.getChildren().add(noFilters);
            }
        } else {
            filtersListContainer.getChildren().add(new Label("No active filters found."));
        }

        // 7. Mirrored Folder Tree
        reportTreeRoot.getChildren().clear();
        if (step5 != null && step5.getRootItem() != null) {
            java.util.Map<String, com.pstconverter.controller.conversion.ConversionTelemetryTracker.FolderTelemetry> telemetryMap = step5.getFolderTelemetryMap();
            for (TreeItem<String> child : step5.getRootItem().getChildren()) {
                buildReportTree(child, reportTreeRoot, "", telemetryMap);
            }
        }

        // 8. Populate filter skip reasons breakdown
        skipReasonsContainer.getChildren().clear();
        java.util.Map<String, Integer> skipCounts = step5.getFilterSkipCounts();
        if (skipCounts != null && !skipCounts.isEmpty()) {
            long totalSkipped = skipped;
            if (totalSkipped == 0) {
                for (int count : skipCounts.values()) {
                    totalSkipped += count;
                }
            }
            if (totalSkipped == 0) totalSkipped = 1;

            for (java.util.Map.Entry<String, Integer> entry : skipCounts.entrySet()) {
                VBox rowContainer = new VBox(6);
                rowContainer.setPadding(new Insets(8, 12, 8, 12));
                rowContainer.setStyle("-fx-background-color: " + (isDark ? "rgba(255,255,255,0.02)" : "#f8fafc") 
                        + "; -fx-border-color: " + (isDark ? "rgba(255,255,255,0.06)" : "#e2e8f0") 
                        + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");
                HBox.setHgrow(rowContainer, Priority.ALWAYS);

                HBox textRow = new HBox(12);
                textRow.setAlignment(Pos.CENTER_LEFT);

                Label bullet = MaterialIcons.icon(MaterialIcons.BLOCK, "-fx-font-size: 14px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + ";");

                Label nameLbl = new Label(entry.getKey());
                nameLbl.getStyleClass().add("report-label-value");
                nameLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
                HBox.setHgrow(nameLbl, Priority.ALWAYS);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                double pct = (entry.getValue() / (double) totalSkipped) * 100.0;
                Label valLbl = new Label(entry.getValue() + " skipped (" + String.format("%.0f%%", pct) + ")");
                valLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + ";");

                textRow.getChildren().addAll(bullet, nameLbl, spacer, valLbl);

                ProgressBar bar = new ProgressBar(entry.getValue() / (double) totalSkipped);
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.setPrefHeight(6);
                bar.setMinHeight(6);
                bar.setStyle("-fx-accent: " + (isDark ? "#94a3b8" : "#64748b") + ";");
                
                rowContainer.getChildren().addAll(textRow, bar);
                skipReasonsContainer.getChildren().add(rowContainer);
            }
        } else {
            Label noSkips = new Label("No messages were skipped in this run.");
            noSkips.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + "; -fx-font-style: italic;");
            skipReasonsContainer.getChildren().add(noSkips);
        }
    }

    private void buildReportTree(TreeItem<String> source, TreeItem<String> parentDest, String pathPrefix, java.util.Map<String, com.pstconverter.controller.conversion.ConversionTelemetryTracker.FolderTelemetry> telemetryMap) {
        if (source == null) return;
        String val = source.getValue();
        if (val == null) return;

        String status = "";
        String label = val;
        if (val.contains("|")) {
            String[] parts = val.split("\\|", 2);
            status = parts[0];
            label = parts[1];
        }

        String uniqueKey = pathPrefix.isEmpty() ? label : pathPrefix + "/" + label;

        // Lookup folder telemetry
        com.pstconverter.controller.conversion.ConversionTelemetryTracker.FolderTelemetry tel = telemetryMap != null ? telemetryMap.get(uniqueKey) : null;
        String displayLabel = label;
        if (tel != null) {
            StringBuilder sb = new StringBuilder(label);
            sb.append(" (").append(tel.successCount());
            if (tel.failedCount() > 0 || tel.skippedCount() > 0) {
                sb.append(" success");
                if (tel.failedCount() > 0) {
                    sb.append(", ").append(tel.failedCount()).append(" failed");
                }
                if (tel.skippedCount() > 0) {
                    sb.append(", ").append(tel.skippedCount()).append(" skipped");
                }
            }
            sb.append("/").append(tel.totalCount()).append(" items)");
            displayLabel = sb.toString();
        }

        TreeItem<String> dest = new TreeItem<>(status + "|" + displayLabel);
        dest.setExpanded(true);
        parentDest.getChildren().add(dest);

        for (TreeItem<String> child : source.getChildren()) {
            buildReportTree(child, dest, uniqueKey, telemetryMap);
        }
    }

    private void openSessionFolder() {
        File dir = controller.getSessionDir();
        if (dir == null || !dir.exists()) {
            dir = new File(System.getProperty("user.home"), "Documents/" + com.pstconverter.config.BrandConfig.REPORT_DIR_NAME);
        }
        if (!dir.exists()) {
            dir = new File(System.getProperty("user.home"));
        }
        System.out.println("Open Session Folder button clicked. Opening folder: " + dir.getAbsolutePath());
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(dir);
            } else {
                new ProcessBuilder("open", dir.getAbsolutePath()).start();
            }
        } catch (Exception ex) {
            System.err.println("Failed to open session folder: " + ex.getMessage());
        }
    }

    private void openOutputFolder() {
        Step4DestinationView step4 = controller.getStep4DestinationView();
        String pathToOpen = step4 != null ? step4.getOutputPath() : null;
        if (pathToOpen == null || pathToOpen.trim().isEmpty()) {
            Step5ConversionView step5 = controller.getStep5ConversionView();
            if (step5 != null) {
                pathToOpen = step5.getResolvedOutputPath();
            }
        }

        if (pathToOpen != null && !pathToOpen.trim().isEmpty()) {
            File dir = new File(pathToOpen.trim());
            if (!dir.exists()) {
                try {
                    dir.mkdirs();
                } catch (Exception ex) {
                    System.err.println("Failed to create folder when opening output directory: " + ex.getMessage());
                }
            }
            System.out.println("Open Output Folder button clicked. Opening folder: " + dir.getAbsolutePath());
            try {
                boolean opened = false;
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                    try {
                        java.awt.Desktop.getDesktop().open(dir);
                        opened = true;
                    } catch (Exception ignored) {}
                }
                if (!opened) {
                    String os = System.getProperty("os.name", "").toLowerCase();
                    if (os.contains("win")) {
                        new ProcessBuilder("explorer.exe", dir.getAbsolutePath()).start();
                    } else if (os.contains("mac")) {
                        new ProcessBuilder("open", dir.getAbsolutePath()).start();
                    } else {
                        new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
                    }
                }
            } catch (Exception ex) {
                System.err.println("Failed to open output folder: " + ex.getMessage());
            }
        }
    }

    private void openMigrationLog() {
        System.out.println("Open Migration Log button clicked.");
        Step5ConversionView step5 = controller.getStep5ConversionView();
        if (step5 != null) {
            step5.openMigrationLog();
        }
    }

    private void handleConvertAgain() {
        System.out.println("Convert Again button clicked. Resetting session and navigating back to Step 1.");
        controller.resetSession();
        if (controller.getFileList() != null) {
            controller.getFileList().clear();
        }
        if (controller.getStep2ExplorerView() != null) {
            controller.getStep2ExplorerView().resetView();
        }
        controller.showStep(1);
    }

    private void handleDownloadPdfReport() {
        System.out.println("Download PDF Report button clicked.");
        
        Step5ConversionView step5 = controller.getStep5ConversionView();
        if (step5 == null) {
            controller.showAlert(javafx.scene.control.Alert.AlertType.ERROR, "Error", "Conversion session telemetry not found.");
            return;
        }

        // Open save dialog
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Save PDF Migration Report");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Document (*.pdf)", "*.pdf"));
        fileChooser.setInitialFileName("migration_report_" + System.currentTimeMillis() + ".pdf");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home"), "Documents"));
        
        File file = fileChooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;

        // Gather all required parameters
        long success = step5.getSuccessItems();
        long failed = step5.getFailedItems();
        long skipped = step5.getSkippedItems();
        long startTime = step5.getStartTimeMs();
        double bytes = step5.getOutputSizeBytes();
        String outputPath = step5.getResolvedOutputPath();

        long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
        String timeStr = String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60);

        double sizeMb = bytes / (1024.0 * 1024.0);
        String sizeStr = sizeMb > 1024 ? String.format("%.1f GB", sizeMb / 1024) : String.format("%.1f MB", sizeMb);

        Step4DestinationView step4 = controller.getStep4DestinationView();
        String targetFormat = step4 != null ? step4.getSelectedFormat() : "TXT";
        String exportStructure = step4 != null ? step4.getExportStructure() : "Single Combined File per Folder";
        String attachmentHandling = step4 != null ? step4.getAttachmentHandling() : "Separate Attachment Files";
        String namingConvention = step4 != null ? step4.getNamingConvention() : "Subject_Date";

        java.util.List<String> activeFiltersList = new java.util.ArrayList<>();
        Step3FilterView step3 = controller.getStep3FilterView();
        if (step3 != null) {
            activeFiltersList = step3.getActiveFilterSummaryChips();
        }
        final java.util.List<String> activeFilters = activeFiltersList;

        java.util.List<String> importedFiles = new java.util.ArrayList<>();
        if (controller.getFileList() != null) {
            for (com.pstconverter.core.model.SourceFileModel m : controller.getFileList()) {
                importedFiles.add(m.getFileName() + " (" + m.getFileSize() + ")");
            }
        }

        java.util.Map<String, com.pstconverter.controller.conversion.ConversionTelemetryTracker.FolderTelemetry> folderTelemetryMap = step5.getFolderTelemetryMap();
        java.util.Map<String, Integer> skipReasons = step5.getFilterSkipCounts();

        // Spin up background task to generate PDF
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                com.pstconverter.util.PdfReportGenerator.generateReport(
                    file,
                    success,
                    skipped,
                    failed,
                    timeStr,
                    sizeStr,
                    outputPath,
                    targetFormat,
                    exportStructure,
                    attachmentHandling,
                    namingConvention,
                    activeFilters,
                    importedFiles,
                    folderTelemetryMap,
                    skipReasons
                );
                return null;
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                controller.showNotification("PDF Migration Report downloaded successfully!");
            }

            @Override
            protected void failed() {
                super.failed();
                Throwable ex = getException();
                ex.printStackTrace();
                controller.showAlert(javafx.scene.control.Alert.AlertType.ERROR, "PDF Export Failed", "An error occurred while generating the PDF report:\n" + ex.getMessage());
            }
        };

        Thread t = new Thread(task);
        t.setName("pdf-report-generator-thread");
        t.setDaemon(true);
        t.start();
    }
}
