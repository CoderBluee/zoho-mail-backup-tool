package com.pstconverter.view;

import com.pstconverter.controller.MainController;
import com.pstconverter.util.MaterialIcons;
import com.pstconverter.util.SettingsManager;
import com.pstconverter.view.destination.LocalDestinationPanel;
import com.pstconverter.view.destination.CloudDestinationPanel;
import com.pstconverter.view.destination.DestinationSettingsPanel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Arrays;

public class Step4DestinationView extends BorderPane {

    private final MainController controller;
    private String selectedCategory = "DOCUMENTS";
    private String selectedFormat = "PDF";

    private ScrollPane settingsScroll;
    private VBox settingsContainer;

    private String[] docFormats;
    private String[] emailFormats;
    private String[] cloudFormats;

    private FlowPane formatSelectionPane;
    private final Map<String, HBox> formatCardsMap = new HashMap<>();
    
    // Category radio cards (replace former MFXButton tab bar)
    private VBox cardDocs;
    private VBox cardEmails;
    private VBox cardCloud;

    // Extracted Panels
    private LocalDestinationPanel localPanel;
    private CloudDestinationPanel cloudPanel;
    private DestinationSettingsPanel settingsPanelExtracted;
    private Label lblDestinationValidationError;

    public Step4DestinationView(MainController controller) {
        this.controller = controller;
        loadDynamicFormats();
        initPanels();
        initializeUI();
        this.visibleProperty().addListener((o, ov, nv) -> {
            if (nv) {
                refreshDestinationPaths();
                settingsPanelExtracted.resetMetadataFieldsToDefault();
            }
        });
    }

    private void initPanels() {
        localPanel = new LocalDestinationPanel(controller);
        cloudPanel = new CloudDestinationPanel(controller);
        cloudPanel.setValidationListener(this::validateDestination);
        settingsPanelExtracted = new DestinationSettingsPanel(controller, this::validateDestination);
    }

    private void loadDynamicFormats() {
        List<String> docs = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        List<String> clouds = new ArrayList<>();
        for (com.pstconverter.core.output.OutputCategory cat : com.pstconverter.core.output.OutputFactory.getSupportedCategories()) {
            if ("DOCUMENTS".equals(cat.getGroup())) docs.add(cat.getDisplayName());
            else if ("EMAILS".equals(cat.getGroup())) emails.add(cat.getDisplayName());
            else if ("CLOUD".equals(cat.getGroup())) clouds.add(cat.getDisplayName());
        }
        docFormats = docs.toArray(new String[0]);
        emailFormats = emails.toArray(new String[0]);
        cloudFormats = clouds.toArray(new String[0]);
    }

    public void refreshDestinationPaths() {
        localPanel.refreshDestinationPathsLocal();
    }

    private void initializeUI() {
        boolean isDark = controller != null && controller.isDarkMode();
        // Create the main unified dashboard layout card
        VBox mainDashboard = new VBox(10);
        mainDashboard.getStyleClass().add("sidebar-card");
        mainDashboard.setPadding(new Insets(14));
        VBox.setVgrow(mainDashboard, Priority.ALWAYS);

        // 1. Title/Header Row
        HBox headerRow = new HBox(12);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(6, 10, 6, 10));
        headerRow.setStyle(
            "-fx-background-color: " + (isDark ? "rgba(30, 41, 59, 0.6)" : "rgba(241, 245, 249, 0.8)") + "; " +
            "-fx-border-color: " + (isDark ? "rgba(14, 165, 233, 0.25)" : "rgba(14, 165, 233, 0.15)") + "; " +
            "-fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;"
        );

        Label iconHeader = MaterialIcons.icon(MaterialIcons.TUNE, 18);
        iconHeader.setStyle("-fx-text-fill: " + (isDark ? "#38bdf8" : "#0284c7") + ";");

        VBox titleBox = new VBox(2);
        Label headerTitle = new Label("CONVERT & EXPORT CONFIGURATION");
        headerTitle.getStyleClass().add("card-header-title");
        headerTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#e0f2fe" : "#0f172a") + ";");
        Label headerSubtitle = new Label("Select category, choose format, and configure destination paths.");
        headerSubtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + ";");
        titleBox.getChildren().addAll(headerTitle, headerSubtitle);
        headerRow.getChildren().addAll(iconHeader, titleBox);

        // 2. Category Radio Cards — full-width, icon-rich selectable tiles
        HBox categoryBar = new HBox(10);
        categoryBar.setAlignment(Pos.CENTER_LEFT);
        categoryBar.setPadding(new Insets(2, 0, 6, 0));

        cardDocs   = buildCategoryCard("Document Formats",  "HTML · PDF · DOC · JSON…",  MaterialIcons.DESCRIPTION, "DOCUMENTS", "#06b6d4");
        cardEmails = buildCategoryCard("Email File Formats", "EML · MBOX · MSG · PST…",   MaterialIcons.EMAIL,        "EMAILS",    "#0ea5e9");
        cardCloud  = buildCategoryCard("Cloud Migrations",   "Gmail · Office 365 · IMAP…", MaterialIcons.CLOUD_SYNC,   "CLOUD",     "#10b981");

        HBox.setHgrow(cardDocs,   Priority.ALWAYS);
        HBox.setHgrow(cardEmails, Priority.ALWAYS);
        HBox.setHgrow(cardCloud,  Priority.ALWAYS);

        categoryBar.getChildren().addAll(cardDocs, cardEmails, cardCloud);

        // 3. Compact Flow Layout for Format Options
        formatSelectionPane = new FlowPane(8, 8);
        formatSelectionPane.setPadding(new Insets(2, 0, 6, 0));
        formatSelectionPane.setAlignment(Pos.CENTER_LEFT);

        // Body settings container
        settingsContainer = new VBox(12);
        settingsContainer.setStyle("-fx-background-color: transparent;");

        settingsScroll = new ScrollPane(settingsContainer);
        settingsScroll.setFitToWidth(true);
        settingsScroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(settingsScroll, Priority.ALWAYS);

        mainDashboard.getChildren().addAll(headerRow, categoryBar, new Separator(), formatSelectionPane, settingsScroll);
        this.setCenter(mainDashboard);

        selectCategory("DOCUMENTS");
    }

    /**
     * Builds a large selectable category radio-card tile.
     * Each card has an icon, bold title, subtitle of example formats, and count badge.
     * The card's accent color is applied on selection via applyCategoryCardStyle().
     */
    private VBox buildCategoryCard(String title, String subtitle, String iconCode, String categoryId, String accentColor) {
        boolean dark = controller != null && controller.isDarkMode();
        // Icon circle
        Label iconLabel = new Label(iconCode);
        iconLabel.getStyleClass().add("material-icon");
        iconLabel.setStyle("-fx-font-size: 20px; -fx-min-width: 40px; -fx-min-height: 40px; " +
                           "-fx-alignment: center; -fx-background-radius: 10px; " +
                           "-fx-text-fill: white; -fx-background-color: " + accentColor + ";");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (dark ? "#f8fafc" : "#1e293b") + ";");
        titleLabel.setId("cat-title-" + categoryId);

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (dark ? "#94a3b8" : "#64748b") + ";");
        subtitleLabel.setId("cat-subtitle-" + categoryId);

        VBox textBox = new VBox(2, titleLabel, subtitleLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);

        // Count badge (number of formats in this category)
        String[] fmts = getFormatsForCategory(categoryId);
        Label countBadge = new Label(fmts.length + " formats");
        countBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: white; " +
                            "-fx-background-color: " + accentColor + "; -fx-background-radius: 10px; " +
                            "-fx-padding: 2px 8px;");
        if ("CLOUD".equals(categoryId)) {
            countBadge.setText(fmts.length + " destinations");
        }
        HBox badgeRow = new HBox(countBadge);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        badgeRow.setPadding(new Insets(4, 0, 0, 0));

        VBox content = new VBox(6, textBox, badgeRow);
        content.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(content, Priority.ALWAYS);

        // Arrow indicator on the right
        Label arrowLabel = new Label(MaterialIcons.ARROW_FORWARD);
        arrowLabel.getStyleClass().add("material-icon");
        arrowLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + (dark ? "#64748b" : "#94a3b8") + ";");
        arrowLabel.setId("cat-arrow-" + categoryId);

        HBox row = new HBox(12, iconLabel, content, arrowLabel);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(row);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: " + (dark ? "rgba(30, 41, 59, 0.6)" : "#ffffff") + "; -fx-border-color: " + (dark ? "#334155" : "#e2e8f0") + "; " +
                      "-fx-border-width: 1.5px; -fx-border-radius: 10px; -fx-background-radius: 10px; " +
                      "-fx-cursor: hand;");
        // Store accent color as user data for later retrieval in style method
        card.setUserData(accentColor);

        card.setOnMouseClicked(e -> selectCategory(categoryId));

        // Hover effect
        card.setOnMouseEntered(e -> {
            if (!categoryId.equals(selectedCategory)) {
                boolean darkCurrent = controller != null && controller.isDarkMode();
                card.setStyle("-fx-background-color: " + (darkCurrent ? "rgba(30,41,59,0.85)" : "rgba(248,250,255,1)") + "; " +
                              "-fx-border-color: " + accentColor + "; -fx-border-width: 1.5px; " +
                              "-fx-border-radius: 10px; -fx-background-radius: 10px; -fx-cursor: hand;");
            }
        });
        card.setOnMouseExited(e -> {
            if (!categoryId.equals(selectedCategory)) {
                applyCategoryCardStyle(card, false, accentColor);
            }
        });

        return card;
    }

    private void selectCategory(String categoryId) {
        selectedCategory = categoryId;

        applyCategoryCardStyle(cardDocs,   "DOCUMENTS".equals(categoryId), "#06b6d4");
        applyCategoryCardStyle(cardEmails, "EMAILS".equals(categoryId),    "#0ea5e9");
        applyCategoryCardStyle(cardCloud,  "CLOUD".equals(categoryId),     "#10b981");

        formatSelectionPane.getChildren().clear();
        formatCardsMap.clear();

        String[] formatsToRender = getFormatsForCategory(categoryId);
        for (String fmt : formatsToRender) {
            HBox formatCard = new HBox(8);
            formatCard.setPadding(new Insets(6, 12, 6, 12));
            formatCard.setAlignment(Pos.CENTER_LEFT);

            Label lblIcon = new Label(getFormatIcon(fmt));
            // No material-icon class: abbreviation text is plain bold system font
            lblIcon.setStyle("-fx-font-size: 9px; -fx-min-width: 32px; -fx-min-height: 32px; -fx-max-width: 32px; -fx-max-height: 32px; "
                           + "-fx-alignment: center; -fx-background-radius: 8px; "
                           + "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-family: 'System'; "
                           + "-fx-background-color: " + getFormatIconBg(fmt) + ";");
            Label lblName = new Label(fmt);
            lblName.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

            formatCard.getChildren().addAll(lblIcon, lblName);
            formatCard.setOnMouseClicked(e -> selectFormat(fmt));

            formatCardsMap.put(fmt, formatCard);
            formatSelectionPane.getChildren().add(formatCard);
            
            applyFormatCardStyle(fmt, fmt.equals(selectedFormat));
        }

        if (formatsToRender.length > 0) {
            boolean hasCurrent = false;
            for (String f : formatsToRender) {
                if (f.equals(selectedFormat)) {
                    hasCurrent = true;
                    break;
                }
            }
            if (hasCurrent) {
                selectFormat(selectedFormat);
            } else {
                selectFormat(formatsToRender[0]);
            }
        }
    }

    /**
     * Applies active/inactive visual state to a category card.
     * Active: colored left-border accent + subtle background tint.
     * Inactive: neutral border.
     */
    private void applyCategoryCardStyle(VBox card, boolean active, String accentColor) {
        if (card == null) return;
        boolean dark = controller.isDarkMode();

        // Derive title/subtitle labels via lookup by ID (set during buildCategoryCard)
        String categoryId = null;
        if (card == cardDocs)   categoryId = "DOCUMENTS";
        else if (card == cardEmails) categoryId = "EMAILS";
        else if (card == cardCloud)  categoryId = "CLOUD";

        Label titleLbl = (Label) card.lookup("#cat-title-" + categoryId);
        Label subtitleLbl = (Label) card.lookup("#cat-subtitle-" + categoryId);
        Label arrowLbl = (Label) card.lookup("#cat-arrow-" + categoryId);

        if (active) {
            // Build a semi-transparent tint from the accent color
            String bg = dark
                ? "rgba(" + hexToRgb(accentColor) + ", 0.18)"
                : "rgba(" + hexToRgb(accentColor) + ", 0.07)";
            card.setStyle("-fx-background-color: " + bg + "; " +
                          "-fx-border-color: " + accentColor + "; -fx-border-width: 2px; " +
                          "-fx-border-radius: 10px; -fx-background-radius: 10px; -fx-cursor: hand;");
            if (titleLbl != null)
                titleLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");
            if (subtitleLbl != null)
                subtitleLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + accentColor + "; -fx-opacity: 0.8;");
            if (arrowLbl != null)
                arrowLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: " + accentColor + ";");
        } else {
            String bg = dark ? "#1e293b" : "#ffffff";
            String border = dark ? "#334155" : "#e2e8f0";
            card.setStyle("-fx-background-color: " + bg + "; " +
                          "-fx-border-color: " + border + "; -fx-border-width: 1.5px; " +
                          "-fx-border-radius: 10px; -fx-background-radius: 10px; -fx-cursor: hand;");
            if (titleLbl != null)
                titleLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (dark ? "#e2e8f0" : "#1e293b") + ";");
            if (subtitleLbl != null)
                subtitleLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (dark ? "#94a3b8" : "#64748b") + ";");
            if (arrowLbl != null)
                arrowLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: " + (dark ? "#64748b" : "#94a3b8") + ";");
        }
    }

    /** Converts a CSS hex color (#rrggbb) to an "r, g, b" string for rgba() usage. */
    private String hexToRgb(String hex) {
        String h = hex.replace("#", "");
        int r = Integer.parseInt(h.substring(0, 2), 16);
        int g = Integer.parseInt(h.substring(2, 4), 16);
        int b = Integer.parseInt(h.substring(4, 6), 16);
        return r + ", " + g + ", " + b;
    }

    private void applyFormatCardStyle(String format, boolean active) {
        HBox card = formatCardsMap.get(format);
        if (card == null) return;
        boolean dark = controller.isDarkMode();
        
        if (active) {
            String activeBorder = dark ? "#38bdf8" : "#0284c7";
            String activeBg = dark ? "rgba(14, 165, 233, 0.2)" : "rgba(14, 165, 233, 0.08)";
            card.setStyle("-fx-background-color: " + activeBg + "; " +
                          "-fx-border-color: " + activeBorder + "; -fx-border-width: 1.5px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand;");
            ((Label)card.getChildren().get(1)).setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + activeBorder + ";");
        } else {
            card.setStyle("-fx-background-color: " + (dark ? "rgba(30, 41, 59, 0.5)" : "#ffffff") + "; " +
                          "-fx-border-color: " + (dark ? "#334155" : "#e2e8f0") + "; " +
                          "-fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand;");
            ((Label)card.getChildren().get(1)).setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (dark ? "#cbd5e1" : "#475569") + ";");
        }
    }

    private void selectFormat(String format) {
        HBox prevCard = formatCardsMap.get(selectedFormat);
        if (prevCard != null) {
            applyFormatCardStyle(selectedFormat, false);
        }

        selectedFormat = format;

        HBox nextCard = formatCardsMap.get(selectedFormat);
        if (nextCard != null) {
            applyFormatCardStyle(selectedFormat, true);
        }

        localPanel.setSelectedFormat(format);
        settingsPanelExtracted.updateFormat(format);
        cloudPanel.setSelectedFormat(format);

        renderSettingsForm();
    }

    private String[] getFormatsForCategory(String categoryId) {
        if (categoryId.equals("DOCUMENTS")) return docFormats;
        else if (categoryId.equals("EMAILS")) return emailFormats;
        else return cloudFormats;
    }

    private void renderSettingsForm() {
        settingsContainer.getChildren().clear();

        boolean isCloud = isCloudFormat(selectedFormat);
        
        lblDestinationValidationError = new Label("");
        lblDestinationValidationError.setStyle("-fx-text-fill: #fca5a5; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10px 14px; -fx-background-color: rgba(239, 68, 68, 0.1); -fx-border-color: rgba(239, 68, 68, 0.3); -fx-background-radius: 6px; -fx-border-radius: 6px;");
        lblDestinationValidationError.setMaxWidth(Double.MAX_VALUE);
        lblDestinationValidationError.setVisible(false);
        lblDestinationValidationError.setManaged(false);

        if (isCloud) {
            settingsContainer.getChildren().addAll(cloudPanel.getUI(), lblDestinationValidationError);
        } else {
            settingsContainer.getChildren().addAll(localPanel.getUI(), settingsPanelExtracted, lblDestinationValidationError);
        }
        
        validateDestination();
    }

    public void resetMetadataFieldsToDefault() {
        if (settingsPanelExtracted != null) {
            settingsPanelExtracted.resetMetadataFieldsToDefault();
        }
    }

    public void resetCloudAccountState() {
        if (cloudPanel != null) {
            cloudPanel.resetCloudAccountState();
        }
    }

    public boolean validateDestination() {
        boolean isValid = true;
        String valMsg = "Validation Failed. Please check the required fields.";
        
        if (isCloudFormat(selectedFormat)) {
            isValid = cloudPanel.validateDestination();
            if (cloudPanel.getValidationMessage() != null && !cloudPanel.getValidationMessage().isEmpty()) {
                valMsg = cloudPanel.getValidationMessage();
            }
        } else {
            isValid = localPanel.validateDestination() && settingsPanelExtracted.validateSettings();
        }
        
        if (lblDestinationValidationError != null) {
            if (!isValid) {
                lblDestinationValidationError.setText(valMsg);
                lblDestinationValidationError.setVisible(true);
                lblDestinationValidationError.setManaged(true);
            } else {
                lblDestinationValidationError.setVisible(false);
                lblDestinationValidationError.setManaged(false);
            }
        }
        controller.setNextButtonDisable(!isValid);
        
        return isValid;
    }

    public boolean isCloudFormat(String format) {
        return Arrays.asList(cloudFormats).contains(format);
    }

    public String getExportStructure() {
        return settingsPanelExtracted != null ? settingsPanelExtracted.getExportStructure() : "Single Monolithic Archive File (Entire Migration - Default)";
    }

    public String getAttachmentHandling() {
        return settingsPanelExtracted != null ? settingsPanelExtracted.getAttachmentHandling() : "Separate Attachment Files";
    }

    public String getNamingConvention() {
        return settingsPanelExtracted != null ? settingsPanelExtracted.getNamingConvention() : "Original Subject";
    }

    public boolean isSplitPst() {
        return settingsPanelExtracted != null && settingsPanelExtracted.isSplitPst();
    }

    public String getSplitSize() {
        return settingsPanelExtracted != null ? settingsPanelExtracted.getSplitSize() : "10 GB";
    }

    public com.pstconverter.core.destination.EmailDestinationConfig getEmailDestinationConfig() {
        return cloudPanel != null ? cloudPanel.getEmailDestinationConfig() : null;
    }

    public String getOutputPath() {
        if (isCloudFormat(selectedFormat)) return null;
        return controller.getCustomDestinationPath();
    }

    public void loadDestinationSettings(Properties props, String destinationPath) {
        if (props == null) return;
        String fmt = props.getProperty("format", "TXT");
        selectFormat(fmt);

        if (!isCloudFormat(fmt)) {
            settingsPanelExtracted.loadSettings(props);
            if (destinationPath != null && !destinationPath.trim().isEmpty()) {
                localPanel.setDestinationPath(destinationPath);
            }
        } else {
            cloudPanel.loadSettings(props);
        }
    }

    public String getSelectedFormat() {
        return selectedFormat;
    }

    private String getFormatIcon(String format) {
        // Returns a short bold abbreviation rendered inside the colored circle badge.
        // This is cleaner and more instantly recognizable than generic glyphs.
        switch (format) {
            case "PDF":          return "PDF";
            case "HTML":         return "HTML";
            case "MHTML":        return "MHT";
            case "TXT":          return "TXT";
            case "RTF":          return "RTF";
            case "DOC":          return "DOC";
            case "JSON":         return "{ }";
            case "CSV":          return "CSV";
            case "MBOX":         return "MBX";
            case "EML":          return "EML";
            case "MSG":          return "MSG";
            case "PST":          return "PST";
            case "EMLX":         return "MLX";
            case "Office 365":   return "O365";
            case "Gmail":        return "G";
            case "IMAP Server":  return "IMAP";
            case "Yahoo Mail":   return "Y!";
            default:             return "FILE";
        }
    }

    private String getFormatIconBg(String format) {
        switch (format) {
            case "PDF": return "#ef4444";
            case "HTML": return "#3b82f6";
            case "MHTML": return "#8b5cf6";
            case "TXT": return "#64748b";
            case "RTF": return "#ec4899";
            case "DOC": return "#1d4ed8";
            case "JSON": return "#f59e0b";
            case "CSV": return "#10b981";
            case "MBOX": return "#14b8a6";
            case "EML": return "#06b6d4";
            case "MSG": return "#0284c7";
            case "PST": return "#0ea5e9";
            case "EMLX": return "#a855f7";
            case "Office 365": return "#f97316";
            case "Gmail": return "#ea4335";
            case "IMAP Server": return "#4b5563";
            case "Yahoo Mail": return "#6001d2";
            default: return "#0ea5e9";
        }
    }
}
