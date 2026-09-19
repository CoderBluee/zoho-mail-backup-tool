package com.pstconverter.view.filter;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DedupFilterPanel extends VBox {

    private final Runnable validationCallback;

    private MFXCheckbox cbRemoveDuplicates;
    private MFXCheckbox cbDedupSubject;
    private MFXCheckbox cbDedupSender;
    private MFXCheckbox cbDedupRecipients;
    private MFXCheckbox cbDedupDate;
    private MFXCheckbox cbDedupBody;
    private MFXCheckbox cbDedupMessageId;
    private MFXCheckbox cbDedupAttachmentNames;
    private Label lblDedupCriteriaPreview;

    public DedupFilterPanel(Runnable validationCallback) {
        this.validationCallback = validationCallback;
        buildUI();
    }

    private MFXCheckbox createFilterCheckBox(String text, boolean selected) {
        MFXCheckbox cb = new MFXCheckbox(text);
        cb.getStyleClass().add("filter-checkbox");
        cb.setSelected(selected);
        cb.selectedProperty().addListener((o, ov, nv) -> validationCallback.run());
        return cb;
    }

    private void buildUI() {
        this.setSpacing(8);
        this.setPadding(new Insets(4));

        Label header = new Label("🔄  Email Deduplication");
        header.getStyleClass().add("filter-section-header");

        VBox content = new VBox(10);
        content.setPadding(new Insets(8));

        VBox dedupCard = new VBox(10);
        dedupCard.getStyleClass().add("filter-sub-card");

        Label lblDedupHeader = new Label("🧹 Deduplication Options");
        lblDedupHeader.getStyleClass().add("filter-group-label");
        lblDedupHeader.setStyle("-fx-font-size: 13px; -fx-text-fill: #818cf8; -fx-font-weight: bold; -fx-padding: 0 0 2px 0;");

        cbRemoveDuplicates = new MFXCheckbox("Remove Duplicate Emails");
        cbRemoveDuplicates.getStyleClass().add("filter-checkbox");
        cbRemoveDuplicates.setSelected(false);
        cbRemoveDuplicates.setTooltip(new Tooltip("Enable duplicate email detection and removal based on selected fields."));
        cbRemoveDuplicates.selectedProperty().addListener((o, ov, nv) -> {
            updateDedupPreview();
            validationCallback.run();
        });

        Label lblDedupFields = new Label("Select Fields to Compare:");
        lblDedupFields.getStyleClass().add("filter-field-label");
        lblDedupFields.disableProperty().bind(cbRemoveDuplicates.selectedProperty().not());

        cbDedupSubject = createFilterCheckBox("Subject", true);
        cbDedupSender = createFilterCheckBox("From / Sender", false);
        cbDedupRecipients = createFilterCheckBox("To / Recipients", false);
        cbDedupDate = createFilterCheckBox("Sent Date & Time", true);
        cbDedupBody = createFilterCheckBox("Message Body / Text", false);
        cbDedupMessageId = createFilterCheckBox("Message-ID Header", false);
        cbDedupAttachmentNames = createFilterCheckBox("Attachment Names", false);

        MFXCheckbox[] fields = {cbDedupSubject, cbDedupSender, cbDedupRecipients, cbDedupDate, cbDedupBody, cbDedupMessageId, cbDedupAttachmentNames};
        for (MFXCheckbox cb : fields) {
            cb.selectedProperty().addListener((o, ov, nv) -> {
                updateDedupPreview();
            });
        }

        FlowPane fieldsPane = new FlowPane(12, 8);
        fieldsPane.setPadding(new Insets(4, 0, 4, 12));
        fieldsPane.getChildren().addAll(fields);
        fieldsPane.disableProperty().bind(cbRemoveDuplicates.selectedProperty().not());

        HBox presetsBox = new HBox(6);
        presetsBox.setAlignment(Pos.CENTER_LEFT);
        presetsBox.setPadding(new Insets(0, 0, 0, 12));
        
        Label lblPresetsLabel = new Label("Presets:");
        lblPresetsLabel.getStyleClass().add("filter-hint-label");
        lblPresetsLabel.setStyle("-fx-font-weight: bold;");
        presetsBox.getChildren().add(lblPresetsLabel);

        MFXButton btnStrict = new MFXButton("Strict (All)");
        btnStrict.getStyleClass().addAll("filter-chip");
        btnStrict.setStyle("-fx-font-size: 9px; -fx-padding: 2px 6px;");
        btnStrict.setOnAction(e -> {
            for (MFXCheckbox cb : fields) cb.setSelected(true);
        });

        MFXButton btnMetadata = new MFXButton("Metadata");
        btnMetadata.getStyleClass().addAll("filter-chip");
        btnMetadata.setStyle("-fx-font-size: 9px; -fx-padding: 2px 6px;");
        btnMetadata.setOnAction(e -> {
            for (MFXCheckbox cb : fields) cb.setSelected(false);
            cbDedupSubject.setSelected(true);
            cbDedupSender.setSelected(true);
            cbDedupDate.setSelected(true);
        });

        MFXButton btnContent = new MFXButton("Content Only");
        btnContent.getStyleClass().addAll("filter-chip");
        btnContent.setStyle("-fx-font-size: 9px; -fx-padding: 2px 6px;");
        btnContent.setOnAction(e -> {
            for (MFXCheckbox cb : fields) cb.setSelected(false);
            cbDedupSubject.setSelected(true);
            cbDedupBody.setSelected(true);
            cbDedupAttachmentNames.setSelected(true);
        });

        presetsBox.getChildren().addAll(btnStrict, btnMetadata, btnContent);
        presetsBox.disableProperty().bind(cbRemoveDuplicates.selectedProperty().not());

        lblDedupCriteriaPreview = new Label("");
        lblDedupCriteriaPreview.setWrapText(true);
        lblDedupCriteriaPreview.setPadding(new Insets(4, 0, 0, 12));

        VBox dedupContent = new VBox(8);
        dedupContent.getChildren().addAll(
            cbRemoveDuplicates,
            lblDedupFields,
            fieldsPane,
            presetsBox,
            lblDedupCriteriaPreview
        );
        dedupCard.getChildren().addAll(lblDedupHeader, new Separator(), dedupContent);

        Label hint = new Label("Deduplication prevents importing identical emails. Custom criteria lets you define what makes an email 'duplicate'.");
        hint.getStyleClass().add("filter-hint-label");
        hint.setWrapText(true);

        content.getChildren().addAll(dedupCard, hint);
        this.getChildren().addAll(header, content);

        updateDedupPreview();
    }

    private void updateDedupPreview() {
        if (lblDedupCriteriaPreview == null) return;
        List<String> activeFields = new ArrayList<>();
        if (cbDedupSubject.isSelected()) activeFields.add("Subject");
        if (cbDedupSender.isSelected()) activeFields.add("From");
        if (cbDedupRecipients.isSelected()) activeFields.add("To");
        if (cbDedupDate.isSelected()) activeFields.add("Date");
        if (cbDedupBody.isSelected()) activeFields.add("Body");
        if (cbDedupMessageId.isSelected()) activeFields.add("Message-ID");
        if (cbDedupAttachmentNames.isSelected()) activeFields.add("Attachments");

        if (!cbRemoveDuplicates.isSelected()) {
            lblDedupCriteriaPreview.setText("Deduplication is disabled.");
            lblDedupCriteriaPreview.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-style: italic;");
        } else if (activeFields.isEmpty()) {
            lblDedupCriteriaPreview.setText("⚠️ Please select at least one field to compare.");
            lblDedupCriteriaPreview.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else {
            lblDedupCriteriaPreview.setText("Comparison: " + String.join(" + ", activeFields));
            lblDedupCriteriaPreview.setStyle("-fx-text-fill: #818cf8; -fx-font-size: 11px; -fx-font-weight: bold;");
        }
    }

    public void saveProperties(Properties props) {
        props.setProperty("hygiene.removeDuplicates", String.valueOf(cbRemoveDuplicates.isSelected()));
        props.setProperty("hygiene.dedupSubject", String.valueOf(cbDedupSubject.isSelected()));
        props.setProperty("hygiene.dedupSender", String.valueOf(cbDedupSender.isSelected()));
        props.setProperty("hygiene.dedupRecipients", String.valueOf(cbDedupRecipients.isSelected()));
        props.setProperty("hygiene.dedupDate", String.valueOf(cbDedupDate.isSelected()));
        props.setProperty("hygiene.dedupBody", String.valueOf(cbDedupBody.isSelected()));
        props.setProperty("hygiene.dedupMessageId", String.valueOf(cbDedupMessageId.isSelected()));
        props.setProperty("hygiene.dedupAttachmentNames", String.valueOf(cbDedupAttachmentNames.isSelected()));
    }

    public void loadProperties(Properties props) {
        cbRemoveDuplicates.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.removeDuplicates", "false")));
        cbDedupSubject.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupSubject", "false")));
        cbDedupSender.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupSender", "false")));
        cbDedupRecipients.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupRecipients", "false")));
        cbDedupDate.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupDate", "false")));
        cbDedupBody.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupBody", "false")));
        cbDedupMessageId.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupMessageId", "false")));
        cbDedupAttachmentNames.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupAttachmentNames", "false")));
        updateDedupPreview();
    }
    
    public MFXCheckbox getCbRemoveDuplicates() { return cbRemoveDuplicates; }
}
