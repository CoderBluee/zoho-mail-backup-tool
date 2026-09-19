package com.pstconverter.view.filter;

import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.enums.FloatMode;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Properties;

public class KeywordFilterPanel extends VBox {

    private final Runnable validationCallback;

    private MFXTextField tfIncludeKeywords;
    private MFXTextField tfExcludeKeywords;
    private MFXCheckbox cbSearchSubject;
    private MFXCheckbox cbSearchBody;
    private MFXCheckbox cbCaseSensitive;

    public KeywordFilterPanel(Runnable validationCallback) {
        this.validationCallback = validationCallback;
        buildUI();
    }

    private void styleModernTextField(MFXTextField tf) {
        tf.setFloatMode(FloatMode.DISABLED);
        tf.setPrefWidth(200);
        tf.setMaxWidth(600);
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

        Label header = new Label("🔍  Keyword Filtering");
        header.getStyleClass().add("filter-section-header");

        VBox content = new VBox(12);
        content.setPadding(new Insets(8));

        VBox keywordsCard = new VBox(10);
        keywordsCard.getStyleClass().add("filter-sub-card");

        Label lblKeywordsHeader = new Label("🔍 Keyword Filters");
        lblKeywordsHeader.getStyleClass().add("filter-group-label");
        lblKeywordsHeader.setStyle("-fx-font-size: 13px; -fx-text-fill: #818cf8; -fx-font-weight: bold; -fx-padding: 0 0 2px 0;");

        Label lblIncKey = new Label("Include Keywords");
        lblIncKey.getStyleClass().add("filter-field-label");
        tfIncludeKeywords = new MFXTextField();
        styleModernTextField(tfIncludeKeywords);
        tfIncludeKeywords.setPromptText("e.g. invoice, contract, confidential");
        tfIncludeKeywords.getStyleClass().add("filter-textfield");
        tfIncludeKeywords.setTooltip(new Tooltip("Enter words or phrases to include. Separate multiple entries with commas."));
        tfIncludeKeywords.textProperty().addListener((o, ov, nv) -> validationCallback.run());

        Label lblExcKey = new Label("Exclude Keywords");
        lblExcKey.getStyleClass().add("filter-field-label");
        tfExcludeKeywords = new MFXTextField();
        styleModernTextField(tfExcludeKeywords);
        tfExcludeKeywords.setPromptText("e.g. draft, promo, social");
        tfExcludeKeywords.getStyleClass().add("filter-textfield");
        tfExcludeKeywords.setTooltip(new Tooltip("Enter words or phrases to exclude. Separate multiple entries with commas."));
        tfExcludeKeywords.textProperty().addListener((o, ov, nv) -> validationCallback.run());

        Label lblScope = new Label("Search Scope");
        lblScope.getStyleClass().add("filter-field-label");

        cbSearchSubject = createFilterCheckBox("Search Subject Line", true);
        cbSearchSubject.setTooltip(new Tooltip("Scan the Subject line of emails for specified keywords."));
        cbSearchBody    = createFilterCheckBox("Search Message Body", true);
        cbSearchBody.setTooltip(new Tooltip("Scan the Body text of emails for specified keywords."));
        cbCaseSensitive = createFilterCheckBox("Case Sensitive Matching", false);
        cbCaseSensitive.setTooltip(new Tooltip("Perform exact case-sensitive keyword matching."));

        HBox scopeBox = new HBox(12, cbSearchSubject, cbSearchBody, cbCaseSensitive);
        scopeBox.setPadding(new Insets(4, 0, 4, 0));

        VBox keywordsContent = new VBox(8);
        keywordsContent.getChildren().addAll(
            lblIncKey, tfIncludeKeywords,
            lblExcKey, tfExcludeKeywords,
            lblScope, scopeBox
        );
        keywordsCard.getChildren().addAll(lblKeywordsHeader, new Separator(), keywordsContent);

        Label hint = new Label("Only items matching the include/exclude keywords within the selected scope will be migrated.");
        hint.getStyleClass().add("filter-hint-label");
        hint.setWrapText(true);

        content.getChildren().addAll(keywordsCard, hint);
        this.getChildren().addAll(header, content);
    }

    public void saveProperties(Properties props) {
        props.setProperty("keyword.include", tfIncludeKeywords.getText().trim());
        props.setProperty("keyword.exclude", tfExcludeKeywords.getText().trim());
        props.setProperty("keyword.searchSubject", String.valueOf(cbSearchSubject.isSelected()));
        props.setProperty("keyword.searchBody", String.valueOf(cbSearchBody.isSelected()));
        props.setProperty("keyword.caseSensitive", String.valueOf(cbCaseSensitive.isSelected()));
    }

    public void loadProperties(Properties props) {
        tfIncludeKeywords.setText(props.getProperty("keyword.include", ""));
        tfExcludeKeywords.setText(props.getProperty("keyword.exclude", ""));
        cbSearchSubject.setSelected(Boolean.parseBoolean(props.getProperty("keyword.searchSubject", "true")));
        cbSearchBody.setSelected(Boolean.parseBoolean(props.getProperty("keyword.searchBody", "true")));
        cbCaseSensitive.setSelected(Boolean.parseBoolean(props.getProperty("keyword.caseSensitive", "false")));
    }

    public MFXTextField getTfIncludeKeywords() { return tfIncludeKeywords; }
    public MFXTextField getTfExcludeKeywords() { return tfExcludeKeywords; }
}
