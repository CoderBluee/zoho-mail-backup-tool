package com.pstconverter.view.destination;

import com.pstconverter.core.model.SourceFileModel;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

/**
 * Interactive UI Dialog for mapping imported PST / Mailbox source files to target domain user email addresses.
 * Supports live fetched domain user lists via ComboBox, manual inline editing, CSV batch import, and auto-mapping.
 */
public class UserMappingDialog extends Dialog<Map<String, String>> {

    public static class MappingRow {
        private final SourceFileModel sourceFile;
        private final ComboBox<String> cmbTargetEmail;

        public MappingRow(SourceFileModel sourceFile, String initialTargetEmail, List<String> availableDomainUsers) {
            this.sourceFile = sourceFile;
            ObservableList<String> userList = FXCollections.observableArrayList();
            if (availableDomainUsers != null && !availableDomainUsers.isEmpty()) {
                userList.addAll(availableDomainUsers);
            }
            if (initialTargetEmail != null && !initialTargetEmail.trim().isEmpty() && !userList.contains(initialTargetEmail.trim())) {
                userList.add(initialTargetEmail.trim());
            }
            this.cmbTargetEmail = new ComboBox<>(userList);
            this.cmbTargetEmail.setEditable(true);
            this.cmbTargetEmail.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-font-size: 12px;");
            this.cmbTargetEmail.setPromptText("Select or type target user email...");
            this.cmbTargetEmail.setPrefWidth(320);
            this.cmbTargetEmail.setValue(initialTargetEmail != null ? initialTargetEmail : "");
        }

        public SourceFileModel getSourceFile() { return sourceFile; }
        public String getSourcePath() { return sourceFile.getFilePath(); }
        public String getFileName() { return sourceFile.getFileName(); }
        public String getTargetEmail() {
            String val = cmbTargetEmail.getValue();
            if (val == null || val.isEmpty()) {
                val = cmbTargetEmail.getEditor().getText();
            }
            return val != null ? val.trim() : "";
        }
        public void setTargetEmail(String email) {
            cmbTargetEmail.setValue(email);
            cmbTargetEmail.getEditor().setText(email != null ? email : "");
        }
        public ComboBox<String> getCmbTargetEmail() { return cmbTargetEmail; }
    }

    private final ObservableList<MappingRow> rows = FXCollections.observableArrayList();
    private final String defaultDomain;
    private final String defaultAdminEmail;
    private final List<String> domainUsersList;

    public UserMappingDialog(List<SourceFileModel> fileList, String defaultAdminEmail, Map<String, String> existingMapping) {
        this(fileList, defaultAdminEmail, existingMapping, null);
    }

    public UserMappingDialog(List<SourceFileModel> fileList, String defaultAdminEmail, Map<String, String> existingMapping, List<String> domainUsers) {
        this.defaultAdminEmail = defaultAdminEmail != null ? defaultAdminEmail : "";
        this.domainUsersList = domainUsers != null ? domainUsers : Collections.emptyList();

        String domain = "company.com";
        if (!this.defaultAdminEmail.isEmpty() && this.defaultAdminEmail.contains("@")) {
            domain = this.defaultAdminEmail.substring(this.defaultAdminEmail.indexOf('@') + 1);
        }
        this.defaultDomain = domain;

        setTitle("User Mailbox Mapping Matrix");
        setHeaderText(null);

        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("dialog-pane");
        pane.setPrefWidth(750);
        pane.setPrefHeight(540);

        // Header Title
        VBox headerBox = new VBox(4);
        Label titleLbl = new Label("User Mailbox Mapping Matrix");
        titleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        
        String subText = "Map each imported PST/Mailbox file to its target domain user account for Admin Impersonation.";
        if (!domainUsersList.isEmpty()) {
            subText += " (" + domainUsersList.size() + " live domain users fetched from server)";
        }
        Label subtitleLbl = new Label(subText);
        subtitleLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        headerBox.getChildren().addAll(titleLbl, subtitleLbl);
        headerBox.setPadding(new Insets(10, 14, 6, 14));

        // Toolbar Buttons
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(6, 14, 6, 14));

        MFXButton btnImportCsv = new MFXButton("Import CSV...");
        btnImportCsv.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-size: 11.5px; -fx-font-weight: bold; -fx-padding: 6px 12px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnImportCsv.setOnAction(e -> handleImportCsv());

        toolbar.getChildren().add(btnImportCsv);

        // Build TableView Rows
        if (fileList != null) {
            for (SourceFileModel model : fileList) {
                String key = model.getFilePath();
                String initialTarget = existingMapping != null ? existingMapping.get(key) : null;
                if (initialTarget == null && existingMapping != null) {
                    initialTarget = existingMapping.get(model.getFileName());
                }
                if (initialTarget == null || initialTarget.isEmpty()) {
                    initialTarget = this.defaultAdminEmail;
                }
                rows.add(new MappingRow(model, initialTarget, this.domainUsersList));
            }
        }

        // Table View layout
        TableView<MappingRow> tableView = new TableView<>(rows);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 6px;");

        TableColumn<MappingRow, String> colSource = new TableColumn<>("Source Mailbox File");
        colSource.setPrefWidth(320);
        colSource.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getFileName() + " (" + data.getValue().getSourceFile().getFileSize() + ")"
        ));

        TableColumn<MappingRow, ComboBox<String>> colTarget = new TableColumn<>("Target Domain User Email (Impersonated)");
        colTarget.setPrefWidth(380);
        colTarget.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getCmbTargetEmail()));

        tableView.getColumns().add(colSource);
        tableView.getColumns().add(colTarget);

        VBox contentBox = new VBox(8, headerBox, toolbar, tableView);
        contentBox.setPadding(new Insets(10));
        VBox.setVgrow(tableView, Priority.ALWAYS);

        pane.setContent(contentBox);

        // Buttons
        ButtonType btnTypeApply = new ButtonType("Apply Mapping Matrix", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(btnTypeApply, btnTypeCancel);

        setResultConverter(buttonType -> {
            if (buttonType == btnTypeApply) {
                Map<String, String> map = new LinkedHashMap<>();
                for (MappingRow r : rows) {
                    String email = r.getTargetEmail();
                    if (email.isEmpty()) email = this.defaultAdminEmail;
                    map.put(r.getSourcePath(), email);
                    map.put(r.getFileName(), email);
                }
                return map;
            }
            return null;
        });
    }

    private void handleImportCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Mapping CSV File");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File selectedFile = fc.showOpenDialog(getDialogPane().getScene().getWindow());
        if (selectedFile != null && selectedFile.exists()) {
            Map<String, String> csvMap = new HashMap<>();
            try (BufferedReader br = new BufferedReader(new FileReader(selectedFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split("[,;\t]");
                    if (parts.length >= 2) {
                        String src = parts[0].trim();
                        String email = parts[1].trim();
                        csvMap.put(src.toLowerCase(Locale.ROOT), email);
                    }
                }
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to read CSV file: " + ex.getMessage(), ButtonType.OK);
                alert.showAndWait();
                return;
            }

            for (MappingRow row : rows) {
                String fileName = row.getFileName().toLowerCase(Locale.ROOT);
                String fullPath = row.getSourcePath().toLowerCase(Locale.ROOT);
                if (csvMap.containsKey(fileName)) {
                    row.setTargetEmail(csvMap.get(fileName));
                } else if (csvMap.containsKey(fullPath)) {
                    row.setTargetEmail(csvMap.get(fullPath));
                }
            }
        }
    }
}
