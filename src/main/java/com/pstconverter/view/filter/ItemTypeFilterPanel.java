package com.pstconverter.view.filter;

import io.github.palexdev.materialfx.controls.MFXCheckbox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Properties;
import java.util.function.BooleanSupplier;

public class ItemTypeFilterPanel extends VBox {

    private final Runnable validationCallback;
    private final BooleanSupplier isDarkModeSupplier;

    private MFXCheckbox cbItemEmails;
    private MFXCheckbox cbItemCalendars;
    private MFXCheckbox cbItemContacts;
    private MFXCheckbox cbItemTasks;
    private MFXCheckbox cbItemNotes;
    private MFXCheckbox cbItemJournals;

    private HBox cardEmails;
    private HBox cardCalendars;
    private HBox cardContacts;
    private HBox cardTasks;
    private HBox cardNotes;
    private HBox cardJournals;

    public ItemTypeFilterPanel(Runnable validationCallback, BooleanSupplier isDarkModeSupplier) {
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

        Label header = new Label("📁  Item Types");
        header.getStyleClass().add("filter-section-header");

        VBox content = new VBox(14);
        content.setPadding(new Insets(10));

        Label lblTypes = new Label("Select Item Types to Export");
        lblTypes.getStyleClass().add("filter-group-label");

        cbItemEmails    = createFilterCheckBox("Emails", true);
        cbItemCalendars = createFilterCheckBox("Calendar Items", true);
        cbItemContacts  = createFilterCheckBox("Contacts", true);
        cbItemTasks     = createFilterCheckBox("Tasks", true);
        cbItemNotes     = createFilterCheckBox("Notes", true);
        cbItemJournals  = createFilterCheckBox("Journal Entries", true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        
        for (int col = 0; col < 2; col++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(50.0);
            grid.getColumnConstraints().add(cc);
        }

        cardEmails    = createItemTypeCard("✉️", "Emails", "Export email messages & threads", cbItemEmails);
        cardCalendars = createItemTypeCard("📅", "Calendar Items", "Export appointments, meetings & events", cbItemCalendars);
        cardContacts  = createItemTypeCard("👥", "Contacts", "Export address book contact cards", cbItemContacts);
        cardTasks     = createItemTypeCard("✅", "Tasks", "Export tasks, to-dos & action items", cbItemTasks);
        cardNotes     = createItemTypeCard("📝", "Notes", "Export digital sticky notes", cbItemNotes);
        cardJournals  = createItemTypeCard("📓", "Journal Entries", "Export diary & work log entries", cbItemJournals);

        grid.add(cardEmails, 0, 0);
        grid.add(cardCalendars, 1, 0);
        grid.add(cardContacts, 0, 1);
        grid.add(cardTasks, 1, 1);
        grid.add(cardNotes, 0, 2);
        grid.add(cardJournals, 1, 2);

        Label hint = new Label("💡 Unchecked item types will be skipped entirely during conversion.");
        hint.getStyleClass().add("filter-hint-label");
        hint.setWrapText(true);

        content.getChildren().addAll(lblTypes, grid, hint);
        this.getChildren().addAll(header, content);
    }

    private HBox createItemTypeCard(String icon, String title, String description, MFXCheckbox cb) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setStyle("-fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand;");

        StackPane iconContainer = new StackPane();
        iconContainer.setPrefSize(36, 36);
        iconContainer.setMinSize(36, 36);
        iconContainer.setMaxSize(36, 36);
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 18px;");
        iconContainer.getChildren().add(iconLabel);

        VBox textContainer = new VBox(2);
        textContainer.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 10px;");
        textContainer.getChildren().addAll(titleLabel, descLabel);
        HBox.setHgrow(textContainer, Priority.ALWAYS);

        cb.getStyleClass().add("filter-checkbox");
        cb.setMouseTransparent(true);

        card.getChildren().addAll(iconContainer, textContainer, cb);

        Runnable updateStyle = () -> {
            boolean isDark = isDarkModeSupplier.getAsBoolean();
            boolean selected = cb.isSelected();
            if (isDark) {
                iconContainer.setStyle("-fx-background-color: " + (selected ? "rgba(14, 165, 233, 0.2)" : "rgba(255,255,255,0.06)") + "; -fx-background-radius: 6px;");
                card.setStyle("-fx-background-color: " + (selected ? "rgba(14, 165, 233, 0.15)" : "rgba(255,255,255,0.02)") + "; -fx-border-color: " + (selected ? "#38bdf8" : "rgba(255,255,255,0.08)") + "; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand;");
                titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (selected ? "#ffffff" : "#e2e8f0") + ";");
                descLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (selected ? "#cbd5e1" : "#94a3b8") + ";");
            } else {
                iconContainer.setStyle("-fx-background-color: " + (selected ? "rgba(14, 165, 233, 0.12)" : "rgba(0,0,0,0.04)") + "; -fx-background-radius: 6px;");
                card.setStyle("-fx-background-color: " + (selected ? "rgba(14, 165, 233, 0.06)" : "rgba(0,0,0,0.01)") + "; -fx-border-color: " + (selected ? "#0284c7" : "rgba(0,0,0,0.08)") + "; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand;");
                titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (selected ? "#0284c7" : "#1e293b") + ";");
                descLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (selected ? "#0284c7" : "#64748b") + ";");
            }
        };

        cb.selectedProperty().addListener((o, ov, nv) -> updateStyle.run());
        updateStyle.run();

        card.setOnMouseClicked(e -> {
            cb.setSelected(!cb.isSelected());
        });

        card.setOnMouseEntered(e -> {
            if (!cb.isSelected()) {
                boolean isDark = isDarkModeSupplier.getAsBoolean();
                card.setStyle("-fx-background-color: " + (isDark ? "rgba(255,255,255,0.04)" : "rgba(0,0,0,0.03)") + "; -fx-border-color: #818cf8; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand;");
            }
        });

        card.setOnMouseExited(e -> updateStyle.run());

        return card;
    }

    public void updateCardVisibility(HBox card, boolean visible, MFXCheckbox cb) {
        if (card != null) {
            card.setVisible(visible);
            card.setManaged(visible);
            if (!visible && cb != null) {
                cb.setSelected(false);
            }
        }
    }

    public void saveProperties(Properties props) {
        props.setProperty("item.emails", String.valueOf(cbItemEmails.isSelected()));
        props.setProperty("item.calendars", String.valueOf(cbItemCalendars.isSelected()));
        props.setProperty("item.contacts", String.valueOf(cbItemContacts.isSelected()));
        props.setProperty("item.tasks", String.valueOf(cbItemTasks.isSelected()));
        props.setProperty("item.notes", String.valueOf(cbItemNotes.isSelected()));
        props.setProperty("item.journals", String.valueOf(cbItemJournals.isSelected()));
    }

    public void loadProperties(Properties props) {
        cbItemEmails.setSelected(Boolean.parseBoolean(props.getProperty("item.emails", "true")));
        cbItemCalendars.setSelected(Boolean.parseBoolean(props.getProperty("item.calendars", "true")));
        cbItemContacts.setSelected(Boolean.parseBoolean(props.getProperty("item.contacts", "true")));
        cbItemTasks.setSelected(Boolean.parseBoolean(props.getProperty("item.tasks", "true")));
        cbItemNotes.setSelected(Boolean.parseBoolean(props.getProperty("item.notes", "true")));
        cbItemJournals.setSelected(Boolean.parseBoolean(props.getProperty("item.journals", "true")));
    }

    public MFXCheckbox getCbItemEmails() { return cbItemEmails; }
    public MFXCheckbox getCbItemCalendars() { return cbItemCalendars; }
    public MFXCheckbox getCbItemContacts() { return cbItemContacts; }
    public MFXCheckbox getCbItemTasks() { return cbItemTasks; }
    public MFXCheckbox getCbItemNotes() { return cbItemNotes; }
    public MFXCheckbox getCbItemJournals() { return cbItemJournals; }

    public HBox getCardEmails() { return cardEmails; }
    public HBox getCardCalendars() { return cardCalendars; }
    public HBox getCardContacts() { return cardContacts; }
    public HBox getCardTasks() { return cardTasks; }
    public HBox getCardNotes() { return cardNotes; }
    public HBox getCardJournals() { return cardJournals; }
}
