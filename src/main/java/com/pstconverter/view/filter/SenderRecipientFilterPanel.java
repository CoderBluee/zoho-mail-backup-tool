package com.pstconverter.view.filter;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.enums.FloatMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Properties;
import java.util.function.Consumer;

public class SenderRecipientFilterPanel extends VBox {

    private final Runnable validationCallback;
    private final Consumer<TextField> onSmartSelectSenders;
    private final Consumer<TextField> onSmartSelectRecipients;

    private MFXTextField tfIncludeSenders;
    private MFXTextField tfExcludeSenders;
    private MFXTextField tfIncludeRecipients;
    private MFXTextField tfExcludeRecipients;
    private MFXCheckbox cbDomainMatch;

    public SenderRecipientFilterPanel(Runnable validationCallback, 
                                      Consumer<TextField> onSmartSelectSenders,
                                      Consumer<TextField> onSmartSelectRecipients) {
        this.validationCallback = validationCallback;
        this.onSmartSelectSenders = onSmartSelectSenders;
        this.onSmartSelectRecipients = onSmartSelectRecipients;
        buildUI();
    }

    private void styleModernTextField(MFXTextField tf) {
        tf.setFloatMode(FloatMode.DISABLED);
        tf.setPrefWidth(200);
        tf.setMaxWidth(600);
    }

    private void buildUI() {
        this.setSpacing(8);
        this.setPadding(new Insets(4));

        Label header = new Label("👤  Sender / Recipient");
        header.getStyleClass().add("filter-section-header");

        VBox content = new VBox(12);
        content.setPadding(new Insets(10));

        // ── Sender Addresses Group ──
        Label lblSenderHeader = new Label("👤 Sender Filters");
        lblSenderHeader.getStyleClass().add("filter-group-label");
        lblSenderHeader.setStyle("-fx-font-size: 12px; -fx-text-fill: #818cf8; -fx-font-weight: bold; -fx-padding: 0 0 4px 0;");

        VBox senderBox = new VBox(8);

        VBox incSenderBox = new VBox(4);
        Label lblIncSender = new Label("Include Senders");
        lblIncSender.getStyleClass().add("filter-field-label");
        HBox incSenderRow = new HBox(8);
        incSenderRow.setAlignment(Pos.CENTER_LEFT);
        incSenderRow.setMaxWidth(Double.MAX_VALUE);
        tfIncludeSenders = new MFXTextField();
        styleModernTextField(tfIncludeSenders);
        tfIncludeSenders.setPromptText("e.g. boss@corp.com, @vendor.com");
        tfIncludeSenders.getStyleClass().add("filter-textfield");
        tfIncludeSenders.setTooltip(new Tooltip("Specify sender email addresses or domains (comma separated) to include."));
        tfIncludeSenders.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tfIncludeSenders, Priority.ALWAYS);
        
        MFXButton btnLoadIncSender = new MFXButton("✨ Smart Select");
        btnLoadIncSender.getStyleClass().addAll("action-btn", "btn-primary");
        btnLoadIncSender.setStyle("-fx-font-size: 11px; -fx-padding: 6px 12px;");
        btnLoadIncSender.setTooltip(new Tooltip("Intelligently scans your mailboxes and loads all unique sender addresses automatically."));
        btnLoadIncSender.setOnAction(e -> onSmartSelectSenders.accept(tfIncludeSenders));
        incSenderRow.getChildren().addAll(tfIncludeSenders, btnLoadIncSender);
        incSenderBox.getChildren().addAll(lblIncSender, incSenderRow);

        VBox excSenderBox = new VBox(4);
        Label lblExcSender = new Label("Exclude Senders");
        lblExcSender.getStyleClass().add("filter-field-label");
        HBox excSenderRow = new HBox(8);
        excSenderRow.setAlignment(Pos.CENTER_LEFT);
        excSenderRow.setMaxWidth(Double.MAX_VALUE);
        tfExcludeSenders = new MFXTextField();
        styleModernTextField(tfExcludeSenders);
        tfExcludeSenders.setPromptText("e.g. spam@ads.com, @badnews.com");
        tfExcludeSenders.getStyleClass().add("filter-textfield");
        tfExcludeSenders.setTooltip(new Tooltip("Specify sender email addresses or domains (comma separated) to exclude."));
        tfExcludeSenders.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tfExcludeSenders, Priority.ALWAYS);

        MFXButton btnLoadExcSender = new MFXButton("✨ Smart Select");
        btnLoadExcSender.getStyleClass().addAll("action-btn", "btn-primary");
        btnLoadExcSender.setStyle("-fx-font-size: 11px; -fx-padding: 6px 12px;");
        btnLoadExcSender.setTooltip(new Tooltip("Intelligently scans your mailboxes and loads all unique sender addresses automatically."));
        btnLoadExcSender.setOnAction(e -> onSmartSelectSenders.accept(tfExcludeSenders));
        excSenderRow.getChildren().addAll(tfExcludeSenders, btnLoadExcSender);
        excSenderBox.getChildren().addAll(lblExcSender, excSenderRow);

        senderBox.getChildren().addAll(lblSenderHeader, incSenderBox, excSenderBox);

        // ── Recipient Addresses Group ──
        Label lblRecipientHeader = new Label("📬 Recipient Filters");
        lblRecipientHeader.getStyleClass().add("filter-group-label");
        lblRecipientHeader.setStyle("-fx-font-size: 12px; -fx-text-fill: #818cf8; -fx-font-weight: bold; -fx-padding: 0 0 4px 0;");

        VBox recipientBox = new VBox(8);

        VBox incRcptBox = new VBox(4);
        Label lblIncRcpt = new Label("Include Recipients");
        lblIncRcpt.getStyleClass().add("filter-field-label");
        HBox incRcptRow = new HBox(8);
        incRcptRow.setAlignment(Pos.CENTER_LEFT);
        incRcptRow.setMaxWidth(Double.MAX_VALUE);
        tfIncludeRecipients = new MFXTextField();
        styleModernTextField(tfIncludeRecipients);
        tfIncludeRecipients.setPromptText("e.g. sales@myorg.com");
        tfIncludeRecipients.getStyleClass().add("filter-textfield");
        tfIncludeRecipients.setTooltip(new Tooltip("Specify recipient email addresses or domains (comma separated) to include."));
        tfIncludeRecipients.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tfIncludeRecipients, Priority.ALWAYS);

        MFXButton btnLoadIncRcpt = new MFXButton("✨ Smart Select");
        btnLoadIncRcpt.getStyleClass().addAll("action-btn", "btn-primary");
        btnLoadIncRcpt.setStyle("-fx-font-size: 11px; -fx-padding: 6px 12px;");
        btnLoadIncRcpt.setTooltip(new Tooltip("Intelligently scans your mailboxes and loads all unique recipient addresses automatically."));
        btnLoadIncRcpt.setOnAction(e -> onSmartSelectRecipients.accept(tfIncludeRecipients));
        incRcptRow.getChildren().addAll(tfIncludeRecipients, btnLoadIncRcpt);
        incRcptBox.getChildren().addAll(lblIncRcpt, incRcptRow);

        VBox excRcptBox = new VBox(4);
        Label lblExcRcpt = new Label("Exclude Recipients");
        lblExcRcpt.getStyleClass().add("filter-field-label");
        HBox excRcptRow = new HBox(8);
        excRcptRow.setAlignment(Pos.CENTER_LEFT);
        excRcptRow.setMaxWidth(Double.MAX_VALUE);
        tfExcludeRecipients = new MFXTextField();
        styleModernTextField(tfExcludeRecipients);
        tfExcludeRecipients.setPromptText("e.g. newsletter@spam.com");
        tfExcludeRecipients.getStyleClass().add("filter-textfield");
        tfExcludeRecipients.setTooltip(new Tooltip("Specify recipient email addresses or domains (comma separated) to exclude."));
        tfExcludeRecipients.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tfExcludeRecipients, Priority.ALWAYS);

        MFXButton btnLoadExcRcpt = new MFXButton("✨ Smart Select");
        btnLoadExcRcpt.getStyleClass().addAll("action-btn", "btn-primary");
        btnLoadExcRcpt.setStyle("-fx-font-size: 11px; -fx-padding: 6px 12px;");
        btnLoadExcRcpt.setTooltip(new Tooltip("Intelligently scans your mailboxes and loads all unique recipient addresses automatically."));
        btnLoadExcRcpt.setOnAction(e -> onSmartSelectRecipients.accept(tfExcludeRecipients));
        excRcptRow.getChildren().addAll(tfExcludeRecipients, btnLoadExcRcpt);
        excRcptBox.getChildren().addAll(lblExcRcpt, excRcptRow);

        recipientBox.getChildren().addAll(lblRecipientHeader, incRcptBox, excRcptBox);

        tfIncludeSenders.textProperty().addListener((o, ov, nv) -> validationCallback.run());
        tfExcludeSenders.textProperty().addListener((o, ov, nv) -> validationCallback.run());
        tfIncludeRecipients.textProperty().addListener((o, ov, nv) -> validationCallback.run());
        tfExcludeRecipients.textProperty().addListener((o, ov, nv) -> validationCallback.run());

        cbDomainMatch = new MFXCheckbox("Domain-level matching (wildcard)");
        cbDomainMatch.getStyleClass().add("filter-checkbox");
        cbDomainMatch.setSelected(true);
        cbDomainMatch.setTooltip(new Tooltip("Match domain names (e.g. @company.com) as a wildcard for all addresses under that domain."));
        cbDomainMatch.selectedProperty().addListener((o, ov, nv) -> validationCallback.run());

        Label hint = new Label("Separate multiple addresses/domains with commas.");
        hint.getStyleClass().add("filter-hint-label");

        content.getChildren().addAll(senderBox, new Separator(), recipientBox, new Separator(), cbDomainMatch, hint);
        this.getChildren().addAll(header, content);
    }

    public void saveProperties(Properties props) {
        props.setProperty("sender.include", tfIncludeSenders.getText().trim());
        props.setProperty("sender.exclude", tfExcludeSenders.getText().trim());
        props.setProperty("recipient.include", tfIncludeRecipients.getText().trim());
        props.setProperty("recipient.exclude", tfExcludeRecipients.getText().trim());
        props.setProperty("sender.domainMatch", String.valueOf(cbDomainMatch.isSelected()));
    }

    public void loadProperties(Properties props) {
        tfIncludeSenders.setText(props.getProperty("sender.include", ""));
        tfExcludeSenders.setText(props.getProperty("sender.exclude", ""));
        tfIncludeRecipients.setText(props.getProperty("recipient.include", ""));
        tfExcludeRecipients.setText(props.getProperty("recipient.exclude", ""));
        cbDomainMatch.setSelected(Boolean.parseBoolean(props.getProperty("sender.domainMatch", "false")));
    }

    public MFXTextField getTfIncludeSenders() { return tfIncludeSenders; }
    public MFXTextField getTfExcludeSenders() { return tfExcludeSenders; }
    public MFXTextField getTfIncludeRecipients() { return tfIncludeRecipients; }
    public MFXTextField getTfExcludeRecipients() { return tfExcludeRecipients; }
}
