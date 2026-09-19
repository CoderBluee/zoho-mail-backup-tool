package com.pstconverter.view.destination;

import com.pstconverter.controller.MainController;
import com.pstconverter.util.MaterialIcons;
import com.pstconverter.util.SettingsManager;
import com.pstconverter.core.destination.EmailDestinationConfig;
import com.pstconverter.core.destination.DestinationAdapter;
import com.pstconverter.core.destination.DestinationAdapterFactory;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.enums.FloatMode;

public class LocalDestinationPanel extends VBox {
    private Label lblActivePdfTemplateVal = new Label();
    private ComboBox<String> cmbPdfTemplate = new ComboBox<>();
    private Label lblActiveExportPathVal = new Label();
    private ComboBox<String> cmbAttachmentHandling = new ComboBox<>();
    private Label lblActiveLogLevelVal = new Label();
    private MFXTextField tfCloudTargetFolder = new MFXTextField();
    private MFXCheckbox cbSplitPst = new MFXCheckbox();
    private ComboBox<String> cmbNamingConvention = new ComboBox<>();
    private ComboBox<String> cmbSplitSize = new ComboBox<>();
    private Label lblActiveEncVal = new Label();
    private Label lblActiveThreadsVal = new Label();
    
    private final MainController controller;
    private String selectedFormat = "PDF";
    private boolean isUpdatingProgrammatically = false;

    private MFXTextField tfOutputPath;
    private TextField tfParentPath;
    private MFXTextField tfFolderName;
    private ComboBox<String> cmbFolderTemplate;
    private MFXCheckbox cbCustomizeFolderName;
    private MFXButton btnBrowse;
    private Label lblPathPreview;
    private VBox commonLocalBox;
    
    public void buildUI() {
        createCommonLocalBox();
        this.getChildren().add(commonLocalBox);
    }
    
    public void refreshDestinationPathsLocal() {
        refreshDestinationPaths();
    }


    public LocalDestinationPanel(MainController controller) {
        this.controller = controller;
        this.setSpacing(10);
    }
    
    public void setSelectedFormat(String format) {
        this.selectedFormat = format;
    }
    
    public boolean validateDestination() { return true; } // Hack for now to compile
    /* public void validateDestinationOld() {  }
    
    */
    public VBox getUI() {
        if (this.getChildren().isEmpty()) {
            buildUI();
        }
        return this;
    }
    
    
    public void styleModernTextField(MFXTextField field) {
        field.setStyle("-fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 8; -fx-font-size: 13px; -fx-background-color: #f8fafc;");
        field.focusedProperty().addListener((o, ov, nv) -> {
            if (nv) field.setStyle("-fx-border-color: #0ea5e9; -fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 7; -fx-font-size: 13px; -fx-background-color: #ffffff; -fx-effect: dropshadow(three-pass-box, rgba(14, 165, 233, 0.25), 5, 0, 0, 0);");
            else field.setStyle("-fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8; -fx-font-size: 13px; -fx-background-color: #f8fafc;");
        });
    }

    public void styleModernCheckBox(MFXCheckbox cb) {
        cb.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155;");
    }
    
    public void styleModernComboBox(ComboBox<?> cb) {
        // generic styling
    }


    private void createCommonLocalBox() {
        commonLocalBox = new VBox(10);
        commonLocalBox.setPadding(new Insets(5, 0, 5, 0));
        commonLocalBox.setMaxWidth(Double.MAX_VALUE);

        boolean isDark = controller != null && controller.isDarkMode();

        // Backing Parent Path (now visible and styled cleanly)
        tfParentPath = new TextField();
        tfParentPath.setEditable(false);
        tfParentPath.setText(controller.getCustomDestinationParent());
        tfParentPath.setPromptText("Select target export directory...");
        tfParentPath.setStyle("-fx-background-color: " + (isDark ? "#0f172a" : "#ffffff") + "; " +
                              "-fx-text-fill: " + (isDark ? "#f8fafc" : "#1e293b") + "; " +
                              "-fx-font-weight: bold; " +
                              "-fx-font-size: 12px; " +
                              "-fx-border-color: " + (isDark ? "#334155" : "#cbd5e1") + "; " +
                              "-fx-border-radius: 6px; " +
                              "-fx-background-radius: 6px; " +
                              "-fx-padding: 8px 12px;");
        tfParentPath.setPrefWidth(550);
        tfParentPath.setMinWidth(300);
        tfParentPath.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tfParentPath, Priority.ALWAYS);

        Tooltip pathTooltip = new Tooltip(tfParentPath.getText());
        pathTooltip.setShowDelay(javafx.util.Duration.millis(200));
        Tooltip.install(tfParentPath, pathTooltip);
        tfParentPath.textProperty().addListener((o, ov, nv) -> pathTooltip.setText(nv));

        btnBrowse = new MFXButton("Browse...");
        btnBrowse.setMinWidth(100);
        btnBrowse.setStyle("-fx-background-color: linear-gradient(to right, #0ea5e9, #0284c7); -fx-text-fill: white; -fx-font-size: 11.5px; -fx-padding: 8px 16px; -fx-background-radius: 8px; -fx-cursor: hand; -fx-font-weight: bold;");

        // Hidden output path sync target for validateDestination()
        tfOutputPath = new MFXTextField();
        tfOutputPath.setVisible(false);
        tfOutputPath.setManaged(false);
        tfOutputPath.setText(controller.getCustomDestinationPath());

        // Folder name is auto-generated from Settings — read-only display
        tfFolderName = new MFXTextField();
        styleModernTextField(tfFolderName);
        tfFolderName.setPrefWidth(200);

        cbCustomizeFolderName = new MFXCheckbox("Customize output folder name:");
        styleModernCheckBox(cbCustomizeFolderName);

        String initTemplate = SettingsManager.getSetting("output_folder_template", "Mailbox_Export_[Timestamp]");
        boolean initCustom = "Custom Name".equals(initTemplate);
        cbCustomizeFolderName.setSelected(initCustom);
        tfFolderName.setDisable(!initCustom);
        if (initCustom || controller.isFolderNameManuallyEdited()) {
            tfFolderName.setText(controller.getCustomExportFolderName());
        } else {
            tfFolderName.setText(generateFolderNameFromTemplate(initTemplate));
        }

        // Target Export Directory Card Box
        VBox rootFolderBox = new VBox(6);
        rootFolderBox.setPadding(new Insets(10, 12, 10, 12));
        rootFolderBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(rootFolderBox, Priority.ALWAYS);
        rootFolderBox.setStyle("-fx-background-color: " + (isDark ? "rgba(30, 41, 59, 0.6)" : "rgba(248, 250, 252, 0.9)") + "; " +
                              "-fx-border-color: " + (isDark ? "rgba(99, 102, 241, 0.25)" : "#cbd5e1") + "; " +
                              "-fx-border-width: 1px; " +
                              "-fx-border-radius: 8px; " +
                              "-fx-background-radius: 8px;");

        Label lblRootText = new Label("Target Export Directory");
        lblRootText.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#475569") + ";");

        HBox rootRow = new HBox(10);
        rootRow.setAlignment(Pos.CENTER_LEFT);
        rootRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(rootRow, Priority.ALWAYS);
        rootRow.getChildren().addAll(tfParentPath, btnBrowse);
        rootFolderBox.getChildren().addAll(lblRootText, rootRow);

        btnBrowse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Parent Export Directory");
            File current = new File(tfParentPath.getText().trim());
            if (current.exists() && current.isDirectory()) {
                chooser.setInitialDirectory(current);
            }
            File selected = chooser.showDialog((Stage) this.getScene().getWindow());
            if (selected != null) {
                tfParentPath.setText(selected.getAbsolutePath());
                SettingsManager.saveSetting("custom_destination_parent", selected.getAbsolutePath());
            }
        });

        // Folder custom name options in a clean horizontal row
        HBox folderControls = new HBox(12);
        folderControls.setAlignment(Pos.CENTER_LEFT);
        folderControls.getChildren().addAll(cbCustomizeFolderName, tfFolderName);

        // Path Preview Card
        VBox previewBox = new VBox(4);
        previewBox.getStyleClass().add("path-preview-box");
        previewBox.setStyle("-fx-background-color: rgba(14, 165, 233, 0.05); -fx-border-color: rgba(14, 165, 233, 0.2); -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 8px 12px;");

        Label lblPreviewTitle = new Label("Full Output Destination Path:");
        lblPreviewTitle.getStyleClass().add("path-preview-title");
        lblPreviewTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + (controller.isDarkMode() ? "#38bdf8" : "#0284c7") + ";");

        lblPathPreview = new Label(controller.getCustomDestinationPath());
        lblPathPreview.setWrapText(true);
        lblPathPreview.getStyleClass().add("path-preview-text");
        lblPathPreview.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        previewBox.getChildren().addAll(lblPreviewTitle, lblPathPreview);

        // Subtle, compact status summary row at the bottom instead of bulky GridPane
        HBox summaryRow = new HBox(16);
        summaryRow.setPadding(new Insets(4, 0, 0, 0));
        summaryRow.setAlignment(Pos.CENTER_LEFT);
        
        lblActiveThreadsVal = new Label();
        lblActiveLogLevelVal = new Label();
        lblActiveEncVal = new Label();

        summaryRow.getChildren().addAll(lblActiveThreadsVal, lblActiveLogLevelVal, lblActiveEncVal);

        // Listeners to keep hidden fields and preview in sync
        Runnable updatePathPreview = () -> {
            if (isUpdatingProgrammatically) return;
            String parent = tfParentPath.getText().trim();
            String name = tfFolderName.getText().trim();
            controller.setCustomDestinationParent(parent);
            controller.setCustomExportFolderName(name);
            String fullPath = controller.getCustomDestinationPath();
            tfOutputPath.setText(fullPath);
            lblPathPreview.setText(fullPath);
            validateDestination();
        };

        tfParentPath.textProperty().addListener((o, ov, nv) -> updatePathPreview.run());
        tfFolderName.textProperty().addListener((o, ov, nv) -> updatePathPreview.run());

        cbCustomizeFolderName.selectedProperty().addListener((o, ov, nv) -> {
            if (nv) {
                SettingsManager.saveSetting("output_folder_template", "Custom Name");
                tfFolderName.setDisable(false);
                String customName = tfFolderName.getText().trim();
                if (customName.isEmpty() || customName.equals("Custom_Export")) {
                    customName = SettingsManager.getSetting("custom_export_folder_name", "Custom_Export");
                    tfFolderName.setText(customName);
                }
                controller.setCustomExportFolderName(customName);
            } else {
                SettingsManager.saveSetting("output_folder_template", "Mailbox_Export_[Timestamp]");
                tfFolderName.setDisable(true);
                String generated = generateFolderNameFromTemplate("Mailbox_Export_[Timestamp]");
                tfFolderName.setText(generated);
                controller.setCustomExportFolderName(generated);
            }
            updatePathPreview.run();
        });

        commonLocalBox.getChildren().addAll(rootFolderBox, folderControls, previewBox, summaryRow);
    }

    public void setDestinationPath(String fullPath) {
        if (fullPath == null || fullPath.trim().isEmpty()) return;
        File file = new File(fullPath.trim());
        File parent = file.getParentFile();
        String folderName = file.getName();
        if (parent != null) {
            isUpdatingProgrammatically = true;
            try {
                if (tfParentPath != null) tfParentPath.setText(parent.getAbsolutePath());
                if (tfFolderName != null) tfFolderName.setText(folderName);
                if (cbCustomizeFolderName != null) cbCustomizeFolderName.setSelected(true);
                if (tfOutputPath != null) tfOutputPath.setText(fullPath);
                if (lblPathPreview != null) lblPathPreview.setText(fullPath);
                if (controller != null) {
                    controller.setCustomDestinationParent(parent.getAbsolutePath());
                    controller.setCustomExportFolderName(folderName);
                    controller.setFolderNameManuallyEdited(true);
                }
            } finally {
                isUpdatingProgrammatically = false;
            }
        }
    }

    private void handleBrowseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Directory");

        String currentPath = tfOutputPath.getText().trim();
        if (!currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            // If current path points to a file that does not exist, use its parent
            if (!currentDir.exists()) {
                currentDir = currentDir.getParentFile();
            }
            if (currentDir != null && currentDir.exists() && currentDir.isDirectory()) {
                chooser.setInitialDirectory(currentDir);
            }
        }

        Stage stage = (Stage) this.getScene().getWindow();
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            // Set the browsed directory as parent and append the smart suggestion folder name
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            String folderName = timestamp + "_" + selectedFormat.replace(" ", "_") + "_Export";
            tfOutputPath.setText(selected.getAbsolutePath() + File.separator + folderName);
            
            // Save this directory to SettingsManager as custom parent
            SettingsManager.saveSetting("custom_destination_parent", selected.getAbsolutePath());
        }
    }

    public String getOutputPath() {
        if (selectedFormat.equals("Office 365") || selectedFormat.equals("Gmail") || selectedFormat.equals("IMAP Server") || selectedFormat.equals("Yahoo Mail")) return null;
        if (tfOutputPath != null && !tfOutputPath.getText().trim().isEmpty()) {
            return tfOutputPath.getText().trim();
        }
        return null;
    }

    private String getSuggestedOutputPath(String format) {
        String parentPath = controller.getCustomDestinationParent();
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String folderName = "Mailbox_Export_" + timestamp;
        return parentPath + File.separator + folderName;
    }

    private String generateFolderNameFromTemplate(String template) {
        String dateStr = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        String timestampStr = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        switch (template) {
            case "Migration_[Date]":
                return "Migration_" + dateStr;
            case "Export_[Timestamp]":
                return "Export_" + timestampStr;
            case "Archive_[Timestamp]":
                return "Archive_" + timestampStr;
            case "Custom Name":
                return tfFolderName != null && !tfFolderName.getText().trim().isEmpty() ? tfFolderName.getText().trim() : SettingsManager.getSetting("custom_export_folder_name", "Custom_Export");
            default:
                return "Mailbox_Export_" + timestampStr;
        }
    }

    public void refreshDestinationPaths() {
        isUpdatingProgrammatically = true;
        try {
            if (tfParentPath != null) {
                tfParentPath.setText(controller.getCustomDestinationParent());
            }
            
            // Sync folder template selection from settings
            String activeTemplate = SettingsManager.getSetting("output_folder_template", "Mailbox_Export_[Timestamp]");
            boolean isCustom = "Custom Name".equals(activeTemplate);
            
            if (cbCustomizeFolderName != null) {
                cbCustomizeFolderName.setSelected(isCustom);
            }
            
            if (tfFolderName != null) {
                tfFolderName.setDisable(!isCustom);
                if (isCustom || controller.isFolderNameManuallyEdited()) {
                    tfFolderName.setText(controller.getCustomExportFolderName());
                } else {
                    tfFolderName.setText(generateFolderNameFromTemplate(activeTemplate));
                }
            }

            if (cmbFolderTemplate != null) {
                cmbFolderTemplate.setValue(activeTemplate);
            }

            if (cmbPdfTemplate != null) {
                String activePdfTemplate = SettingsManager.getSetting("pdf_layout_template", "Standard Card Layout");
                cmbPdfTemplate.setValue(activePdfTemplate);
            }
        } finally {
            isUpdatingProgrammatically = false;
        }

        // Keep local variables and UI previews in sync
        String parent = tfParentPath != null ? tfParentPath.getText().trim() : "";
        String name = tfFolderName != null ? tfFolderName.getText().trim() : "";
        controller.setCustomDestinationParent(parent);
        controller.setCustomExportFolderName(name);
        String fullPath = controller.getCustomDestinationPath();

        if (tfOutputPath != null) {
            tfOutputPath.setText(fullPath);
        }
        if (lblPathPreview != null) {
            lblPathPreview.setText(fullPath);
        }

        // Sync naming convention from settings
        if (cmbNamingConvention != null) {
            String globalNaming = com.pstconverter.util.SettingsManager.getSetting("eml_naming_convention", "[Date]_[Subject]");
            String mappedNaming = "Original Subject";
            if ("[Date]_[Subject]".equals(globalNaming) || "Date + Subject".equals(globalNaming)) mappedNaming = "Date + Subject";
            else if ("[Subject]_[Sender]".equals(globalNaming) || "From + Subject".equals(globalNaming)) mappedNaming = "From + Subject";
            else if ("[Message-ID]".equals(globalNaming)) mappedNaming = "[Message-ID]";
            else if ("[Sequential ID]".equals(globalNaming)) mappedNaming = "[Sequential ID]";
            else mappedNaming = globalNaming;
            
            if (cmbNamingConvention.getItems().contains(mappedNaming)) {
                cmbNamingConvention.setValue(mappedNaming);
            } else {
                cmbNamingConvention.getSelectionModel().selectFirst();
            }
        }

        // Sync attachment handling from settings
        if (cmbAttachmentHandling != null) {
            String globalMode = com.pstconverter.util.SettingsManager.getSetting("pdf_attachment_mode", "Extract and save in separate folder next to output");
            String defAttach = "Separate Attachment Files";
            if ("Ignore attachments".equalsIgnoreCase(globalMode)) {
                defAttach = "Skip / Drop Attachments";
            } else if ("Embed directly into output file".equalsIgnoreCase(globalMode) && cmbAttachmentHandling.getItems().contains("Embed Attachments in " + selectedFormat)) {
                defAttach = "Embed Attachments in " + selectedFormat;
            } else {
                defAttach = "Separate Attachment Files";
            }
            
            if (cmbAttachmentHandling.getItems().contains(defAttach)) {
                cmbAttachmentHandling.setValue(defAttach);
            } else {
                cmbAttachmentHandling.getSelectionModel().selectFirst();
            }
        }

        // Sync PST split settings from settings
        if (cbSplitPst != null && cmbSplitSize != null) {
            String splitPstSetting = com.pstconverter.util.SettingsManager.getSetting("split_pst_size", "Do not split output");
            if (splitPstSetting.equals("Do not split output")) {
                cbSplitPst.setSelected(false);
            } else {
                cbSplitPst.setSelected(true);
                String sizeOnly = splitPstSetting.replace(" limit", "");
                cmbSplitSize.setValue(sizeOnly);
            }
        }

        // Sync active preferences summaries
        boolean dark = controller.isDarkMode();
        String summaryStyle = "-fx-font-size: 11px; -fx-text-fill: " + (dark ? "#94a3b8" : "#475569") + ";";
        if (lblActiveThreadsVal != null) {
            lblActiveThreadsVal.setText("Parallel Threads: " + com.pstconverter.util.SettingsManager.getSetting(com.pstconverter.util.SettingsManager.KEY_THREAD_COUNT, "4"));
            lblActiveThreadsVal.setStyle(summaryStyle);
        }
        if (lblActiveLogLevelVal != null) {
            lblActiveLogLevelVal.setText("Log Severity: " + com.pstconverter.util.SettingsManager.getSetting("log_level", "INFO"));
            lblActiveLogLevelVal.setStyle(summaryStyle);
        }
        if (lblActivePdfTemplateVal != null) {
            lblActivePdfTemplateVal.setText("PDF Template: " + com.pstconverter.util.SettingsManager.getSetting("pdf_layout_template", "Standard Card Layout"));
            lblActivePdfTemplateVal.setStyle(summaryStyle);
        }
        if (lblActiveEncVal != null) {
            lblActiveEncVal.setText("Fallback Encoding: " + com.pstconverter.util.SettingsManager.getSetting("fallback_encoding", "Auto-Detect (Recommended)"));
            lblActiveEncVal.setStyle(summaryStyle);
        }
        if (lblActiveExportPathVal != null) {
            String exportRoot = com.pstconverter.util.SettingsManager.getSetting(com.pstconverter.util.SettingsManager.KEY_DEFAULT_EXPORT_PATH, System.getProperty("user.home") + java.io.File.separator + "Desktop");
            lblActiveExportPathVal.setText("Export Root: " + exportRoot);
            lblActiveExportPathVal.setStyle(summaryStyle);
        }
    }
}
