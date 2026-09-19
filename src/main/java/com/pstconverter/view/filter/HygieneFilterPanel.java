package com.pstconverter.view.filter;

import io.github.palexdev.materialfx.controls.MFXCheckbox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Properties;
import java.util.function.BooleanSupplier;

public class HygieneFilterPanel extends VBox {

    private final Runnable validationCallback;
    private final BooleanSupplier isDarkModeSupplier;

    private MFXCheckbox cbSkipEmpty;
    private MFXCheckbox cbSkipDeleted;
    private MFXCheckbox cbSkipJunk;

    public HygieneFilterPanel(Runnable validationCallback, BooleanSupplier isDarkModeSupplier) {
        this.validationCallback = validationCallback;
        this.isDarkModeSupplier = isDarkModeSupplier;
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

        VBox foldersCard = new VBox(10);
        foldersCard.getStyleClass().add("filter-sub-card");

        Label lblFoldersHeader = new Label("📁 Folder Exclusion");
        lblFoldersHeader.getStyleClass().add("filter-group-label");
        lblFoldersHeader.setStyle("-fx-font-size: 13px; -fx-text-fill: #818cf8; -fx-font-weight: bold; -fx-padding: 0 0 2px 0;");

        cbSkipEmpty   = createFilterCheckBox("Skip Empty Folders", true);
        cbSkipEmpty.setTooltip(new Tooltip("Do not export folders that contain no matching messages."));
        cbSkipDeleted = createFilterCheckBox("Skip \"Deleted Items\" Folder", false);
        cbSkipDeleted.setTooltip(new Tooltip("Skip messages located in the Deleted Items folder."));
        cbSkipJunk    = createFilterCheckBox("Skip \"Junk Email\" / Spam Folder", false);
        cbSkipJunk.setTooltip(new Tooltip("Skip messages located in the Junk/Spam folder."));

        HBox rowEmpty = createHygieneRow("Skip Empty Folders", "Do not export folders containing no matching messages", cbSkipEmpty);
        HBox rowDeleted = createHygieneRow("Skip Deleted Items", "Exclude messages from the Deleted Items/Trash folder", cbSkipDeleted);
        HBox rowJunk = createHygieneRow("Skip Junk/Spam Email", "Exclude messages from the Junk, Bulk, or Spam folders", cbSkipJunk);

        VBox foldersContent = new VBox(8);
        foldersContent.getChildren().addAll(rowEmpty, rowDeleted, rowJunk);
        foldersCard.getChildren().addAll(lblFoldersHeader, new Separator(), foldersContent);

        this.getChildren().add(foldersCard);
    }

    private HBox createHygieneRow(String title, String description, MFXCheckbox cb) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setStyle("-fx-border-radius: 6px; -fx-background-radius: 6px;");
        
        VBox texts = new VBox(2);
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        Label descLbl = new Label(description);
        descLbl.setStyle("-fx-font-size: 10px;");
        texts.getChildren().addAll(titleLbl, descLbl);
        HBox.setHgrow(texts, Priority.ALWAYS);
        
        cb.getStyleClass().add("filter-checkbox");
        cb.setMouseTransparent(true);
        
        row.getChildren().addAll(texts, cb);
        
        Runnable updateStyle = () -> {
            boolean isDark = isDarkModeSupplier.getAsBoolean();
            if (isDark) {
                row.setStyle("-fx-background-color: " + (cb.isSelected() ? "rgba(14, 165, 233, 0.12)" : "rgba(255,255,255,0.02)") + "; -fx-border-color: " + (cb.isSelected() ? "#38bdf8" : "rgba(255,255,255,0.08)") + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-cursor: hand;");
                titleLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
                descLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");
            } else {
                row.setStyle("-fx-background-color: " + (cb.isSelected() ? "rgba(14, 165, 233, 0.05)" : "rgba(0,0,0,0.01)") + "; -fx-border-color: " + (cb.isSelected() ? "#0284c7" : "rgba(0,0,0,0.08)") + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-cursor: hand;");
                titleLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
                descLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
            }
        };
        
        cb.selectedProperty().addListener((o, ov, nv) -> updateStyle.run());
        updateStyle.run();
        
        row.setOnMouseClicked(e -> cb.setSelected(!cb.isSelected()));
        return row;
    }

    public void saveProperties(Properties props) {
        props.setProperty("hygiene.skipEmpty", String.valueOf(cbSkipEmpty.isSelected()));
        props.setProperty("hygiene.skipDeleted", String.valueOf(cbSkipDeleted.isSelected()));
        props.setProperty("hygiene.skipJunk", String.valueOf(cbSkipJunk.isSelected()));
    }

    public void loadProperties(Properties props) {
        cbSkipEmpty.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.skipEmpty", "false")));
        cbSkipDeleted.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.skipDeleted", "false")));
        cbSkipJunk.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.skipJunk", "false")));
    }

    public MFXCheckbox getCbSkipEmpty() { return cbSkipEmpty; }
    public MFXCheckbox getCbSkipDeleted() { return cbSkipDeleted; }
    public MFXCheckbox getCbSkipJunk() { return cbSkipJunk; }
}
