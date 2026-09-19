package com.pstconverter.view;

import com.pstconverter.controller.MainController;
import com.pstconverter.util.DiagnosticsExporter;
import com.pstconverter.util.MaterialIcons;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class HelpDialog {

    private static final String CHAT_URL = "https://tawk.to/chat/6a39aeb5351d6e1d43433240/1jrol4u8n";

    public static void show(MainController controller) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (controller.getScene() != null && controller.getScene().getWindow() != null) {
            dialog.initOwner(controller.getScene().getWindow());
        }
        dialog.setTitle("Help & Support Center — " + com.pstconverter.config.BrandConfig.TOOL_NAME);

        boolean isDark = controller.isDarkMode();

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + (isDark ? "#0f172a" : "#f8fafc") + ";");

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label iconHelp = MaterialIcons.icon(MaterialIcons.HELP, "-fx-text-fill: #0ea5e9; -fx-font-size: 28px;");
        VBox titleBox = new VBox(2);
        Label lblTitle = new Label("Help & Support Center");
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#f8fafc" : "#0f172a") + ";");
        Label lblSub = new Label("User Guide, Frequently Asked Questions, and Live Assistance");
        lblSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        titleBox.getChildren().addAll(lblTitle, lblSub);
        header.getChildren().addAll(iconHelp, titleBox);

        // TabPane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("dashboard-tabs");

        // Tab 1: Live Support & Contact
        Tab tabSupport = new Tab("💬 Live Support", buildSupportTab(controller, isDark));
        // Tab 2: User Guide
        Tab tabGuide = new Tab("📖 User Guide", buildUserGuideTab(isDark));
        // Tab 3: FAQs
        Tab tabFaq = new Tab("❓ FAQs", buildFaqTab(isDark));

        tabPane.getTabs().addAll(tabSupport, tabGuide, tabFaq);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Footer buttons
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);

        MFXButton btnClose = new MFXButton("Close");
        btnClose.getStyleClass().addAll("action-btn", "btn-secondary");
        btnClose.setOnAction(e -> dialog.close());

        MFXButton btnOpenChat = new MFXButton("Open Live Chat in Browser");
        btnOpenChat.getStyleClass().addAll("action-btn", "btn-primary");
        btnOpenChat.setGraphic(MaterialIcons.icon(MaterialIcons.PUBLIC, "-fx-text-fill: white; -fx-font-size: 14px;"));
        btnOpenChat.setStyle("-fx-background-color: linear-gradient(to right, #0ea5e9, #06b6d4); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px;");
        btnOpenChat.setOnAction(e -> controller.openWebpage(CHAT_URL));

        footer.getChildren().addAll(btnOpenChat, btnClose);

        root.getChildren().addAll(header, new Separator(), tabPane, footer);

        Scene scene = new Scene(root, 650, 500);
        String cssPath = isDark ? "/style-dark.css" : "/style-light.css";
        try {
            scene.getStylesheets().add(HelpDialog.class.getResource(cssPath).toExternalForm());
        } catch (Exception ignored) {}

        dialog.setScene(scene);
        dialog.show();
    }

    private static Node buildSupportTab(MainController controller, boolean isDark) {
        VBox box = new VBox(15);
        box.setPadding(new Insets(15));

        // Live Chat Card
        VBox chatCard = new VBox(10);
        chatCard.setPadding(new Insets(15));
        chatCard.setStyle("-fx-background-color: " + (isDark ? "#1e293b" : "#ffffff") + "; " +
                          "-fx-border-color: " + (isDark ? "rgba(99, 102, 241, 0.3)" : "rgba(79, 70, 229, 0.2)") + "; " +
                          "-fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label lblChatTitle = new Label("Need Instant Assistance?");
        lblChatTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#ffffff" : "#1e293b") + ";");
        Label lblChatDesc = new Label("Our support team is available via live chat to help you with PST conversions, license activation, or troubleshooting.");
        lblChatDesc.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        lblChatDesc.setWrapText(true);

        HBox chatBtnBox = new HBox(10);
        chatBtnBox.setAlignment(Pos.CENTER_LEFT);

        MFXButton btnLaunchChat = new MFXButton("Launch Live Support Chat");
        btnLaunchChat.getStyleClass().addAll("action-btn", "btn-primary");
        btnLaunchChat.setGraphic(MaterialIcons.icon(MaterialIcons.PUBLIC, 14));
        btnLaunchChat.setStyle("-fx-font-weight: bold; -fx-padding: 8px 16px;");
        btnLaunchChat.setOnAction(e -> controller.openWebpage(CHAT_URL));

        chatBtnBox.getChildren().add(btnLaunchChat);
        chatCard.getChildren().addAll(lblChatTitle, lblChatDesc, chatBtnBox);

        // URL Box
        HBox urlBox = new HBox(8);
        urlBox.setAlignment(Pos.CENTER_LEFT);
        TextField tfUrl = new TextField(CHAT_URL);
        tfUrl.setEditable(false);
        HBox.setHgrow(tfUrl, Priority.ALWAYS);
        tfUrl.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        MFXButton btnCopy = new MFXButton("Copy Link");
        btnCopy.getStyleClass().addAll("action-btn", "btn-secondary");
        btnCopy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(CHAT_URL);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            controller.showNotification("Support URL copied to clipboard.");
        });
        urlBox.getChildren().addAll(new Label("Direct Link:"), tfUrl, btnCopy);

        // Diagnostics Card
        VBox diagCard = new VBox(10);
        diagCard.setPadding(new Insets(15));
        diagCard.setStyle("-fx-background-color: " + (isDark ? "#1e293b" : "#ffffff") + "; " +
                          "-fx-border-color: " + (isDark ? "rgba(255, 255, 255, 0.1)" : "rgba(0, 0, 0, 0.08)") + "; " +
                          "-fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label lblDiagTitle = new Label("Export Diagnostic Package");
        lblDiagTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#e2e8f0" : "#334155") + ";");
        Label lblDiagDesc = new Label("If reporting an issue, export your diagnostic log package to send to support.");
        lblDiagDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        lblDiagDesc.setWrapText(true);

        MFXButton btnExportDiag = new MFXButton("Export Diagnostics (ZIP)");
        btnExportDiag.getStyleClass().addAll("action-btn", "btn-secondary");
        btnExportDiag.setGraphic(MaterialIcons.icon(MaterialIcons.BUG_REPORT, 14));
        btnExportDiag.setOnAction(e -> {
            java.io.File zip = DiagnosticsExporter.exportDiagnostics(controller.getSessionDir());
            if (zip != null && zip.exists()) {
                controller.showNotification("Diagnostics exported to: " + zip.getName());
                controller.showAlert(Alert.AlertType.INFORMATION, "Diagnostics Exported", "Diagnostic package created successfully at:\n\n" + zip.getAbsolutePath());
            } else {
                controller.showAlert(Alert.AlertType.ERROR, "Export Failed", "Failed to generate diagnostics package.");
            }
        });

        diagCard.getChildren().addAll(lblDiagTitle, lblDiagDesc, btnExportDiag);

        box.getChildren().addAll(chatCard, urlBox, diagCard);
        return new ScrollPane(box);
    }

    private static Node buildUserGuideTab(boolean isDark) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(15));

        addGuideStep(box, "Step 1: Import Mailbox Files", 
            "Click 'Select File(s)' or 'Select Folder' to load Outlook PST files or other supported mailboxes into the queue. You can also drag and drop PST files directly into the window or use 'Auto Detect Mailboxes' to auto-discover local Outlook profiles.", isDark);

        addGuideStep(box, "Step 2: Browse & Select Folders", 
            "Expand the folder tree to inspect mailbox folders (Inbox, Sent Items, Drafts, Contacts, Calendar). Use checkboxes to select or deselect specific folders for export.", isDark);

        addGuideStep(box, "Step 3: Apply Conversion Filters", 
            "Configure optional filters including Date Range, Sender/Recipient rules, Keyword inclusion/exclusion, Item Type filters (Emails, Contacts, Calendars), and Deduplication.", isDark);

        addGuideStep(box, "Step 4: Select Destination & Target Format", 
            "Choose your target export format from 17 options categorized under Documents (PDF, HTML, RTF, DOC, TXT), Emails (EML, MSG, MBOX, PST, CSV, JSON), or Cloud APIs (Office 365, Gmail, IMAP, Yahoo). Choose the output destination folder.", isDark);

        addGuideStep(box, "Step 5: Processing & Migration Dashboard", 
            "Click 'Start Migration' to launch high-performance multi-threaded conversion. Track real-time progress, speed, item status, and logs.", isDark);

        addGuideStep(box, "Step 6: Conversion Telemetry Report", 
            "View comprehensive summary statistics, total converted messages, skipped items, and download PDF diagnostic reports.", isDark);

        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private static void addGuideStep(VBox parent, String title, String desc, boolean isDark) {
        VBox stepBox = new VBox(4);
        stepBox.setPadding(new Insets(10));
        stepBox.setStyle("-fx-background-color: " + (isDark ? "#1e293b" : "#ffffff") + "; " +
                         "-fx-border-color: " + (isDark ? "rgba(255, 255, 255, 0.08)" : "rgba(0, 0, 0, 0.06)") + "; " +
                         "-fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#38bdf8" : "#0284c7") + ";");
        Label lblDesc = new Label(desc);
        lblDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#cbd5e1" : "#475569") + ";");
        lblDesc.setWrapText(true);

        stepBox.getChildren().addAll(lblTitle, lblDesc);
        parent.getChildren().add(stepBox);
    }

    private static Node buildFaqTab(boolean isDark) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(15));

        addFaqItem(box, "Q: Which Outlook PST formats are supported?", 
            "A: Both Unicode PST (Outlook 2007, 2010, 2013, 2016, 2019, 2021, Office 365) and ANSI PST (Outlook 97-2002) files are fully supported without requiring Microsoft Outlook installed.", isDark);

        addFaqItem(box, "Q: How do I export PST files directly to PDF?", 
            "A: In Step 4, select the 'Documents' category and choose 'PDF'. You can customize attachment handling, header metadata inclusion, page layout, and naming conventions.", isDark);

        addFaqItem(box, "Q: Can I resume an interrupted conversion?", 
            "A: Yes! If a conversion is stopped or interrupted, the app automatically tracks progress in its database. When you reload the file or click 'Resume Migrations' on Step 1, it will skip already exported items.", isDark);

        addFaqItem(box, "Q: What is the trial version limit?", 
            "A: Trial mode allows processing up to 25 items per folder. Activate a full license key in the top right header bar to convert unlimited items.", isDark);

        addFaqItem(box, "Q: Are password-protected PST files supported?", 
            "A: Yes, the conversion engine bypasses local PST passwords automatically.", isDark);

        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private static void addFaqItem(VBox parent, String question, String answer, boolean isDark) {
        VBox faqBox = new VBox(4);
        faqBox.setPadding(new Insets(10));
        faqBox.setStyle("-fx-background-color: " + (isDark ? "#1e293b" : "#ffffff") + "; " +
                        "-fx-border-color: " + (isDark ? "rgba(255, 255, 255, 0.08)" : "rgba(0, 0, 0, 0.06)") + "; " +
                        "-fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label lblQ = new Label(question);
        lblQ.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#f8fafc" : "#1e293b") + ";");
        Label lblA = new Label(answer);
        lblA.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#cbd5e1" : "#475569") + ";");
        lblA.setWrapText(true);

        faqBox.getChildren().addAll(lblQ, lblA);
        parent.getChildren().add(faqBox);
    }
}
