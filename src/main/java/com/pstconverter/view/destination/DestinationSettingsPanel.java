package com.pstconverter.view.destination;

import com.pstconverter.controller.MainController;
import com.pstconverter.util.MaterialIcons;
import com.pstconverter.util.SettingsManager;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class DestinationSettingsPanel extends VBox {
    private final MainController controller;
    private String selectedFormat = "PDF";
    private Runnable validationCallback;

    private ComboBox<String> cmbExportStructure;
    private ComboBox<String> cmbAttachmentHandling;
    private ComboBox<String> cmbPdfTemplate;
    private ComboBox<String> cmbNamingConvention;

    private VBox pdfPreviewContainer;
    private VBox structureBox;
    private VBox attachmentBox;
    private VBox pdfTemplateBox;
    private VBox namingBox;
    private HBox formatSettingsRow;

    private VBox fieldsBox;
    private MFXCheckbox cbIncSubject;
    private MFXCheckbox cbIncFrom;
    private MFXCheckbox cbIncTo;
    private MFXCheckbox cbIncDate;
    private MFXCheckbox cbIncCcBcc;
    private MFXCheckbox cbIncBody;

    private VBox pstBox;
    private MFXCheckbox cbSplitPst;
    private ComboBox<String> cmbSplitSize;
    private HBox splitDetails;

    private final List<String> docFormats = Arrays.asList("PDF", "HTML", "MHTML", "TXT", "RTF", "DOC", "CSV", "JSON");

    public DestinationSettingsPanel(MainController controller, Runnable validationCallback) {
        this.controller = controller;
        this.validationCallback = validationCallback;
        this.setSpacing(10);
        this.setPadding(new Insets(10, 0, 10, 0));
        buildUI();
    }

    public void updateFormat(String format) {
        this.selectedFormat = format;
        refreshUI();
    }

    private void styleModernComboBox(ComboBox<?> cmb) {
        cmb.getStyleClass().add("filter-combo");
        cmb.setStyle("-fx-padding: 4px; -fx-font-size: 13px;");
        cmb.setMaxWidth(Double.MAX_VALUE);
    }

    private void styleModernCheckBox(MFXCheckbox cb) {
        cb.getStyleClass().add("filter-checkbox");
        cb.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155;");
    }

    private void buildUI() {
        this.getChildren().clear();

        // 1. Dropdowns GridPane (Compact 2x2 layout)
        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(16);
        settingsGrid.setVgap(10);
        settingsGrid.setAlignment(Pos.CENTER_LEFT);

        // 1.1 Structure Dropdown
        structureBox = new VBox(4);
        Label lblStructure = new Label("File Output Structure");
        lblStructure.getStyleClass().add("dest-form-label");
        lblStructure.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        cmbExportStructure = new ComboBox<>();
        styleModernComboBox(cmbExportStructure);
        structureBox.getChildren().addAll(lblStructure, cmbExportStructure);
        settingsGrid.add(structureBox, 0, 0);
        GridPane.setHgrow(structureBox, Priority.ALWAYS);

        // 1.2 Attachment Handling Dropdown
        attachmentBox = new VBox(4);
        Label lblAttachment = new Label("Attachment Handling");
        lblAttachment.getStyleClass().add("dest-form-label");
        lblAttachment.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        cmbAttachmentHandling = new ComboBox<>();
        styleModernComboBox(cmbAttachmentHandling);
        attachmentBox.getChildren().addAll(lblAttachment, cmbAttachmentHandling);
        settingsGrid.add(attachmentBox, 1, 0);
        GridPane.setHgrow(attachmentBox, Priority.ALWAYS);

        // 1.3 PDF Layout Design Dropdown (Only for PDF)
        pdfTemplateBox = new VBox(4);
        Label lblPdfTemplate = new Label("PDF Layout Design");
        lblPdfTemplate.getStyleClass().add("dest-form-label");
        lblPdfTemplate.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        cmbPdfTemplate = new ComboBox<>(FXCollections.observableArrayList(
            "Standard Card Layout", "Minimalist Clean Text", "Compact Grid Header", "Detailed Metadata Header", "Classic Email Archive"
        ));
        styleModernComboBox(cmbPdfTemplate);
        
        pdfPreviewContainer = new VBox(6);
        pdfPreviewContainer.setPadding(new Insets(6, 0, 0, 0));

        String defTemplate = SettingsManager.getSetting("pdf_layout_template", "Standard Card Layout");
        cmbPdfTemplate.setValue(defTemplate);
        cmbPdfTemplate.getSelectionModel().select(defTemplate);
        cmbPdfTemplate.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                SettingsManager.saveSetting("pdf_layout_template", newVal);
                updatePdfPreview(newVal);
            }
        });
        pdfTemplateBox.getChildren().addAll(lblPdfTemplate, cmbPdfTemplate, pdfPreviewContainer);
        settingsGrid.add(pdfTemplateBox, 0, 1);
        GridPane.setHgrow(pdfTemplateBox, Priority.ALWAYS);

        // 1.4 File Naming Rules Dropdown
        namingBox = new VBox(4);
        Label lblNaming = new Label("File Naming Rules");
        lblNaming.getStyleClass().add("dest-form-label");
        lblNaming.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        cmbNamingConvention = new ComboBox<>(FXCollections.observableArrayList(
            "Original Subject", "Date + Subject", "From + Subject", "Date + From + Subject", "Subject + Date", "[Message-ID]", "[Sequential ID]"
        ));
        styleModernComboBox(cmbNamingConvention);
        
        String globalNamingSetting = SettingsManager.getSetting("eml_naming_convention", "Date + Subject");
        cmbNamingConvention.setValue(globalNamingSetting);
        cmbNamingConvention.getSelectionModel().select(globalNamingSetting);

        cmbNamingConvention.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                SettingsManager.saveSetting("eml_naming_convention", newVal);
            }
        });
        namingBox.getChildren().addAll(lblNaming, cmbNamingConvention);
        settingsGrid.add(namingBox, 1, 1);
        GridPane.setHgrow(namingBox, Priority.ALWAYS);

        // Bind visibility/managed properties to avoid empty grid cells
        pdfTemplateBox.visibleProperty().addListener((o, ov, nv) -> updateGridPositions(settingsGrid));
        namingBox.visibleProperty().addListener((o, ov, nv) -> updateGridPositions(settingsGrid));
        structureBox.visibleProperty().addListener((o, ov, nv) -> updateGridPositions(settingsGrid));

        // 1.5 Metadata Fields to Include
        buildMetadataFieldsBox();

        // 2. Special settings for Archive formats (PST)
        buildPstBox();

        this.getChildren().addAll(settingsGrid, fieldsBox, pstBox);
        
        refreshUI();
    }

    private void updateGridPositions(GridPane grid) {
        grid.getChildren().clear();
        int col = 0;
        int row = 0;
        
        if (structureBox.isVisible()) {
            grid.add(structureBox, col++, row);
        }
        if (attachmentBox.isVisible()) {
            grid.add(attachmentBox, col++, row);
        }
        if (col > 1) {
            col = 0;
            row++;
        }
        if (pdfTemplateBox.isVisible()) {
            grid.add(pdfTemplateBox, col++, row);
        }
        if (col > 1) {
            col = 0;
            row++;
        }
        if (namingBox.isVisible()) {
            grid.add(namingBox, col++, row);
        }
    }

    private void buildMetadataFieldsBox() {
        fieldsBox = new VBox(8);
        fieldsBox.getStyleClass().add("filter-sub-card");
        fieldsBox.setPadding(new Insets(10, 14, 10, 14));
        fieldsBox.setStyle("-fx-background-color: rgba(99, 102, 241, 0.02); -fx-border-color: rgba(99, 102, 241, 0.15); -fx-border-radius: 8px; -fx-background-radius: 8px;");
        
        HBox fieldsHeaderRow = new HBox(12);
        fieldsHeaderRow.setAlignment(Pos.CENTER_LEFT);
        
        HBox titleIconBox = new HBox(6);
        titleIconBox.setAlignment(Pos.CENTER_LEFT);
        Label lblFieldsIcon = MaterialIcons.icon(MaterialIcons.TUNE, 14);
        lblFieldsIcon.setStyle("-fx-text-fill: #0ea5e9;");
        Label lblFieldsTitle = new Label("Include Metadata Fields in Output");
        lblFieldsTitle.getStyleClass().add("dest-form-label");
        lblFieldsTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (controller.isDarkMode() ? "#e0f2fe" : "#0f172a") + ";");
        titleIconBox.getChildren().addAll(lblFieldsIcon, lblFieldsTitle);
        HBox.setHgrow(titleIconBox, Priority.ALWAYS);
        
        HBox presetsBox = new HBox(6);
        presetsBox.setAlignment(Pos.CENTER_RIGHT);
        
        MFXButton btnSelectAll = new MFXButton("Select All");
        btnSelectAll.setStyle("-fx-background-color: rgba(14, 165, 233, 0.1); -fx-text-fill: #0284c7; -fx-font-size: 10px; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-cursor: hand;");
        MFXButton btnClearAll = new MFXButton("Clear All");
        btnClearAll.setStyle("-fx-background-color: rgba(239, 68, 68, 0.08); -fx-text-fill: #ef4444; -fx-font-size: 10px; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-cursor: hand;");
        MFXButton btnPresetsStandard = new MFXButton("Standard");
        btnPresetsStandard.setStyle("-fx-background-color: rgba(148, 163, 184, 0.1); -fx-text-fill: #475569; -fx-font-size: 10px; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-cursor: hand;");
        
        presetsBox.getChildren().addAll(btnSelectAll, btnClearAll, btnPresetsStandard);
        fieldsHeaderRow.getChildren().addAll(titleIconBox, presetsBox);
        
        FlowPane fieldsPane = new FlowPane(8, 8);
        fieldsPane.setPadding(new Insets(2, 0, 2, 0));
        
        cbIncSubject = new MFXCheckbox();
        cbIncFrom = new MFXCheckbox();
        cbIncTo = new MFXCheckbox();
        cbIncDate = new MFXCheckbox();
        cbIncCcBcc = new MFXCheckbox();
        cbIncBody = new MFXCheckbox();
        
        cbIncSubject.setSelected(Boolean.parseBoolean(SettingsManager.getSetting("export_field_subject", "true")));
        cbIncFrom.setSelected(Boolean.parseBoolean(SettingsManager.getSetting("export_field_from", "true")));
        cbIncTo.setSelected(Boolean.parseBoolean(SettingsManager.getSetting("export_field_to", "true")));
        cbIncDate.setSelected(Boolean.parseBoolean(SettingsManager.getSetting("export_field_date", "true")));
        cbIncCcBcc.setSelected(Boolean.parseBoolean(SettingsManager.getSetting("export_field_ccbcc", "true")));
        cbIncBody.setSelected(Boolean.parseBoolean(SettingsManager.getSetting("export_field_body", "true")));
        
        cbIncSubject.selectedProperty().addListener((o, ov, nv) -> { SettingsManager.saveSetting("export_field_subject", String.valueOf(nv)); fireValidation(); });
        cbIncFrom.selectedProperty().addListener((o, ov, nv) -> { SettingsManager.saveSetting("export_field_from", String.valueOf(nv)); fireValidation(); });
        cbIncTo.selectedProperty().addListener((o, ov, nv) -> { SettingsManager.saveSetting("export_field_to", String.valueOf(nv)); fireValidation(); });
        cbIncDate.selectedProperty().addListener((o, ov, nv) -> { SettingsManager.saveSetting("export_field_date", String.valueOf(nv)); fireValidation(); });
        cbIncCcBcc.selectedProperty().addListener((o, ov, nv) -> { SettingsManager.saveSetting("export_field_ccbcc", String.valueOf(nv)); fireValidation(); });
        cbIncBody.selectedProperty().addListener((o, ov, nv) -> { SettingsManager.saveSetting("export_field_body", String.valueOf(nv)); fireValidation(); });
        
        btnSelectAll.setOnAction(e -> {
            cbIncSubject.setSelected(true); cbIncFrom.setSelected(true); cbIncTo.setSelected(true); cbIncDate.setSelected(true); cbIncCcBcc.setSelected(true); cbIncBody.setSelected(true);
            fireValidation();
        });
        
        btnClearAll.setOnAction(e -> {
            cbIncSubject.setSelected(false); cbIncFrom.setSelected(false); cbIncTo.setSelected(false); cbIncDate.setSelected(false); cbIncCcBcc.setSelected(false); cbIncBody.setSelected(false);
            fireValidation();
        });
        
        btnPresetsStandard.setOnAction(e -> {
            cbIncSubject.setSelected(true); cbIncFrom.setSelected(true); cbIncTo.setSelected(true); cbIncDate.setSelected(true); cbIncCcBcc.setSelected(false); cbIncBody.setSelected(true);
            fireValidation();
        });
        
        fieldsPane.getChildren().addAll(
            createFieldToggleCard(cbIncSubject, "Subject", MaterialIcons.SEND),
            createFieldToggleCard(cbIncFrom, "From", MaterialIcons.PERSON),
            createFieldToggleCard(cbIncTo, "To", MaterialIcons.PEOPLE),
            createFieldToggleCard(cbIncDate, "Date", MaterialIcons.EVENT),
            createFieldToggleCard(cbIncCcBcc, "CC / BCC", MaterialIcons.GROUP),
            createFieldToggleCard(cbIncBody, "Body", MaterialIcons.BOOK)
        );
        fieldsBox.getChildren().addAll(fieldsHeaderRow, new Separator(), fieldsPane);
    }

    private HBox createFieldToggleCard(MFXCheckbox cb, String title, String iconCode) {
        HBox card = new HBox(8);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(6, 12, 6, 12));
        
        Label icon = MaterialIcons.icon(iconCode, 14);
        icon.setStyle("-fx-text-fill: #0ea5e9;");
        cb.setText(title);
        cb.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1e293b;");
        
        card.getChildren().addAll(icon, cb);
        
        Runnable updateStyle = () -> {
            boolean isDark = controller.isDarkMode();
            if (cb.isSelected()) {
                card.setStyle("-fx-background-color: " + (isDark ? "rgba(14, 165, 233, 0.15)" : "rgba(14, 165, 233, 0.08)") + "; " +
                              "-fx-border-color: " + (isDark ? "#38bdf8" : "#0ea5e9") + "; " +
                              "-fx-border-width: 1px; " +
                              "-fx-border-radius: 6px; " +
                              "-fx-background-radius: 6px;");
            } else {
                card.setStyle("-fx-background-color: " + (isDark ? "rgba(30, 41, 59, 0.3)" : "rgba(241, 245, 249, 0.5)") + "; " +
                              "-fx-border-color: " + (isDark ? "#334155" : "#e2e8f0") + "; " +
                              "-fx-border-width: 1px; " +
                              "-fx-border-radius: 6px; " +
                              "-fx-background-radius: 6px;");
            }
        };

        updateStyle.run();
        cb.selectedProperty().addListener((o, ov, nv) -> updateStyle.run());
        
        card.setOnMouseClicked(e -> {
            if (e.getTarget() != cb) {
                cb.setSelected(!cb.isSelected());
            }
        });
        
        card.setPrefWidth(125);
        return card;
    }

    private void buildPstBox() {
        pstBox = new VBox(6);
        pstBox.setPadding(new Insets(10, 14, 10, 14));
        pstBox.setStyle("-fx-background-color: rgba(14, 165, 233, 0.03); -fx-border-color: rgba(14, 165, 233, 0.15); -fx-border-radius: 8px; -fx-background-radius: 8px;");

        String splitPstSetting = SettingsManager.getSetting("split_pst_size", "Do not split output");
        boolean splitEnabled = !splitPstSetting.equals("Do not split output");
        String defaultSplitSize = splitEnabled ? splitPstSetting.replace(" limit", "") : "10 GB";

        cbSplitPst = new MFXCheckbox("Split Output Archive File");
        styleModernCheckBox(cbSplitPst);
        cbSplitPst.setSelected(splitEnabled);

        splitDetails = new HBox(12);
        splitDetails.setAlignment(Pos.CENTER_LEFT);
        Label lblSize = new Label("Max Size:");
        lblSize.getStyleClass().add("dest-form-label");
        lblSize.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        
        cmbSplitSize = new ComboBox<>(FXCollections.observableArrayList(
                "1 GB", "2 GB", "5 GB", "10 GB", "20 GB", "50 GB"
        ));
        styleModernComboBox(cmbSplitSize);
        cmbSplitSize.setValue(defaultSplitSize);
        cmbSplitSize.getSelectionModel().select(defaultSplitSize);
        splitDetails.getChildren().addAll(lblSize, cmbSplitSize);

        splitDetails.visibleProperty().bind(cbSplitPst.selectedProperty());
        splitDetails.managedProperty().bind(cbSplitPst.selectedProperty());

        cbSplitPst.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                SettingsManager.saveSetting("split_pst_size", "Do not split output");
            } else {
                String size = cmbSplitSize.getValue();
                SettingsManager.saveSetting("split_pst_size", size + " limit");
            }
        });

        cmbSplitSize.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (cbSplitPst.isSelected() && newVal != null) {
                SettingsManager.saveSetting("split_pst_size", newVal + " limit");
            }
        });

        HBox splitRow = new HBox(20);
        splitRow.setAlignment(Pos.CENTER_LEFT);
        splitRow.getChildren().addAll(cbSplitPst, splitDetails);
        pstBox.getChildren().add(splitRow);
    }

    private void updatePdfPreview(String template) {
        if (pdfPreviewContainer == null) return;
        pdfPreviewContainer.getChildren().clear();

        VBox page = new VBox(2);
        page.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 2, 0, 0, 1);");
        page.setPrefSize(280, 80);
        page.setMaxSize(280, 80);

        Label lblDocTitle = new Label("LIVE PDF LAYOUT PREVIEW");
        lblDocTitle.setStyle("-fx-font-size: 7px; -fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-alignment: center;");
        lblDocTitle.setMaxWidth(Double.MAX_VALUE);
        page.getChildren().add(lblDocTitle);

        VBox headerContent = new VBox(1);
        
        switch (template) {
            case "Minimalist Clean Text": {
                Label lblSubj = new Label("Project Roadmap Update");
                lblSubj.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
                Label lblMeta = new Label("john.doe@company.com  |  June 15, 2026");
                lblMeta.setStyle("-fx-font-size: 6.5px; -fx-text-fill: #64748b;");
                headerContent.getChildren().addAll(lblSubj, lblMeta);
                break;
            }
            case "Compact Grid Header": {
                Label lblSubj = new Label("Project Roadmap Update");
                lblSubj.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
                GridPane grid = new GridPane();
                grid.setHgap(6); grid.setVgap(1);
                Label lblFrom = new Label("From: john.doe@company.com");
                lblFrom.setStyle("-fx-font-size: 6px; -fx-text-fill: #475569;");
                Label lblTo = new Label("To: team@company.com");
                lblTo.setStyle("-fx-font-size: 6px; -fx-text-fill: #475569;");
                grid.add(lblFrom, 0, 0); grid.add(lblTo, 1, 0);
                headerContent.getChildren().addAll(lblSubj, grid);
                break;
            }
            case "Detailed Metadata Header": {
                Label lblSubj = new Label("Project Roadmap Update");
                lblSubj.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
                VBox table = new VBox(1);
                table.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 2px; -fx-padding: 2px;");
                Label lblMsgId = new Label("ID: <road-987123@company.com>");
                lblMsgId.setStyle("-fx-font-size: 6px; -fx-text-fill: #475569; -fx-font-family: monospace;");
                Label lblFrom = new Label("From: john.doe@company.com");
                lblFrom.setStyle("-fx-font-size: 6px; -fx-text-fill: #475569;");
                table.getChildren().addAll(lblMsgId, lblFrom);
                headerContent.getChildren().addAll(lblSubj, table);
                break;
            }
            case "Classic Email Archive": {
                GridPane grid = new GridPane();
                grid.setHgap(4); grid.setVgap(1);
                Label lblSubjLbl = new Label("Subject:");
                lblSubjLbl.setStyle("-fx-font-size: 7px; -fx-font-weight: bold; -fx-text-fill: #334155;");
                Label lblSubjVal = new Label("Project Roadmap Update");
                lblSubjVal.setStyle("-fx-font-size: 7px; -fx-text-fill: #0f172a;");
                Label lblFromLbl = new Label("From:");
                lblFromLbl.setStyle("-fx-font-size: 7px; -fx-font-weight: bold; -fx-text-fill: #334155;");
                Label lblFromVal = new Label("john.doe@company.com");
                lblFromVal.setStyle("-fx-font-size: 7px; -fx-text-fill: #0f172a;");
                grid.add(lblSubjLbl, 0, 0); grid.add(lblSubjVal, 1, 0);
                grid.add(lblFromLbl, 0, 1); grid.add(lblFromVal, 1, 1);
                headerContent.getChildren().add(grid);
                break;
            }
            case "Standard Card Layout":
            default: {
                VBox card = new VBox(1);
                card.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 2px; -fx-padding: 3px;");
                Label lblSubj = new Label("Project Roadmap Update");
                lblSubj.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #0284c7;");
                Label lblFrom = new Label("From: john.doe@company.com");
                lblFrom.setStyle("-fx-font-size: 6.5px; -fx-text-fill: #475569;");
                card.getChildren().addAll(lblSubj, lblFrom);
                headerContent.getChildren().add(card);
                break;
            }
        }

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #cbd5e1; -fx-margin: 1px 0;");

        VBox bodyContent = new VBox(1);
        Label lblBody1 = new Label("We have finalized the core milestones for the migration template.");
        lblBody1.setStyle("-fx-font-size: 6.5px; -fx-text-fill: #334155;");
        bodyContent.getChildren().add(lblBody1);

        page.getChildren().addAll(headerContent, sep, bodyContent);
        pdfPreviewContainer.getChildren().add(page);
    }

    private void refreshUI() {
        // Structure Dropdown options
        List<String> structOptions = new ArrayList<>();
        if (selectedFormat.equals("MBOX")) {
            structOptions.add("Generic \"messages.mbox\" inside Folder Subdirectory (Default)");
            structOptions.add("Naming after Folder Label (\"FolderName.mbox\" directly)");
            structOptions.add("Single Monolithic MBOX for Entire Migration");
        } else if (selectedFormat.equals("PST")) {
            structOptions.add("Single Monolithic Archive File (Entire Migration - Default)");
            structOptions.add("Archive per Folder");
            structOptions.add("Individual Archive per Email (One file per message)");
        } else {
            structOptions.add("Single Combined File per Folder (All emails in one file)");
            structOptions.add("Individual File per Email (One file per message)");
        }
        cmbExportStructure.setItems(FXCollections.observableArrayList(structOptions));

        if (selectedFormat.equals("MBOX")) {
            cmbExportStructure.setValue(structOptions.get(1));
        } else if (selectedFormat.equals("PST")) {
            cmbExportStructure.setValue(structOptions.get(0));
        } else if (selectedFormat.equals("CSV") || selectedFormat.equals("JSON") || selectedFormat.equals("TXT")) {
            cmbExportStructure.setValue(structOptions.get(0));
        } else {
            cmbExportStructure.setValue(structOptions.get(1));
        }

        boolean showStructure = !(selectedFormat.equals("EML") || selectedFormat.equals("MSG") || selectedFormat.equals("EMLX"));
        structureBox.setVisible(showStructure);
        structureBox.setManaged(showStructure);

        // Attachment Handling
        List<String> attachOptions = new ArrayList<>();
        boolean supportsEmbed = selectedFormat.equals("PDF") || selectedFormat.equals("MHTML") || 
                               selectedFormat.equals("EML") || selectedFormat.equals("MSG") || 
                               selectedFormat.equals("PST") || selectedFormat.equals("EMLX");

        if (supportsEmbed) {
            attachOptions.add("Embed Attachments in " + selectedFormat);
        }
        attachOptions.add("Separate Attachment Files");
        attachOptions.add("Skip / Drop Attachments");

        cmbAttachmentHandling.setItems(FXCollections.observableArrayList(attachOptions));
        String defAttach = "Separate Attachment Files";
        cmbAttachmentHandling.setValue(defAttach);

        // PDF Template
        boolean showPdfTemplate = selectedFormat.equals("PDF");
        pdfTemplateBox.setVisible(showPdfTemplate);
        pdfTemplateBox.setManaged(showPdfTemplate);
        if (showPdfTemplate) {
            updatePdfPreview(cmbPdfTemplate.getValue());
        }

        // Naming Rules
        boolean alwaysIndividual = selectedFormat.equals("EML") || selectedFormat.equals("MSG") || selectedFormat.equals("EMLX");
        if (alwaysIndividual) {
            namingBox.visibleProperty().unbind();
            namingBox.managedProperty().unbind();
            namingBox.setVisible(true);
            namingBox.setManaged(true);
        } else {
            namingBox.visibleProperty().bind(cmbExportStructure.valueProperty().isEqualTo("Individual File per Email (One file per message)"));
            namingBox.managedProperty().bind(cmbExportStructure.valueProperty().isEqualTo("Individual File per Email (One file per message)"));
        }

        // Metadata Fields
        boolean isDocFormat = docFormats.contains(selectedFormat);
        fieldsBox.setVisible(isDocFormat);
        fieldsBox.setManaged(isDocFormat);

        // PST Special Settings
        boolean isPst = selectedFormat.equals("PST");
        pstBox.setVisible(isPst);
        pstBox.setManaged(isPst);
    }

    public void loadSettings(Properties props) {
        if (props == null) return;
        
        String struct = props.getProperty("export_structure");
        if (struct != null) cmbExportStructure.setValue(struct);
        
        String attach = props.getProperty("attachment_handling");
        if (attach != null) cmbAttachmentHandling.setValue(attach);
        
        String naming = props.getProperty("naming_convention");
        if (naming != null) cmbNamingConvention.setValue(naming);

        cbSplitPst.setSelected(Boolean.parseBoolean(props.getProperty("split_pst", "false")));
        String splitSize = props.getProperty("split_pst_size");
        if (splitSize != null) cmbSplitSize.setValue(splitSize);
    }

    private void fireValidation() {
        if (validationCallback != null) validationCallback.run();
    }

    public boolean validateSettings() {
        if (docFormats.contains(selectedFormat)) {
            return cbIncSubject.isSelected() || cbIncFrom.isSelected() || 
                   cbIncTo.isSelected() || cbIncDate.isSelected() || 
                   cbIncCcBcc.isSelected() || cbIncBody.isSelected();
        }
        return true;
    }

    public void resetMetadataFieldsToDefault() {
        cbIncSubject.setSelected(true);
        cbIncFrom.setSelected(true);
        cbIncTo.setSelected(true);
        cbIncDate.setSelected(true);
        cbIncCcBcc.setSelected(true);
        cbIncBody.setSelected(true);
        cmbPdfTemplate.setValue("Standard Card Layout");
        SettingsManager.saveSetting("pdf_layout_template", "Standard Card Layout");
    }

    public String getExportStructure() {
        return cmbExportStructure.getValue() != null ? cmbExportStructure.getValue() : "Individual File per Email (One file per message)";
    }

    public String getAttachmentHandling() {
        String val = cmbAttachmentHandling.getValue();
        if (val == null) return "Keep Attachments in Folder";
        if (val.startsWith("Embed")) return "Embed Attachments in " + selectedFormat;
        else if (val.startsWith("Separate")) return "Keep Attachments in Folder";
        else return "Skip / Drop Attachments";
    }

    public String getNamingConvention() {
        return cmbNamingConvention.getValue() != null ? cmbNamingConvention.getValue() : "Original Subject";
    }
    
    public boolean isSplitPst() {
        return cbSplitPst.isSelected();
    }

    public String getSplitSize() {
        return cmbSplitSize.getValue() != null ? cmbSplitSize.getValue() : "10 GB";
    }
}
