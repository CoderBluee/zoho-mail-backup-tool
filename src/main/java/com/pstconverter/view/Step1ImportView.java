package com.pstconverter.view;

import com.pstconverter.controller.MainController;
import com.pstconverter.core.model.SourceFileModel;
import com.pstconverter.imap.ImapSourceAdapter;
import com.pstconverter.imap.ImapAuthHelper;
import com.pstconverter.util.ChilkatLibraryLoader;
import com.pstconverter.util.MaterialIcons;
import com.pstconverter.util.SettingsManager;
import com.pstconverter.util.SettingsManager.ImapAccountRecord;
import com.pstconverter.util.SettingsManager.MigrationSession;

import com.chilkatsoft.CkImap;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Step 1: IMAP Account Connection & Multi-Account Selection Screen.
 * Features Single Account IMAP Login with Presets, Batch CSV Import,
 * Persistent SQLite Database Storage, Account Selection Queue, and Migration History.
 */
public class Step1ImportView extends BorderPane {

    public static class AccountCardItem {
        private final String email;
        private final String host;
        private final int port;
        private final boolean ssl;
        private final String password;
        private boolean selected;
        private boolean isNewlyLoggedIn;

        public AccountCardItem(String email, String host, int port, boolean ssl, String password, boolean selected, boolean isNewlyLoggedIn) {
            this.email = email != null ? email.trim().toLowerCase() : "";
            this.host = host != null ? host.trim() : "";
            this.port = port;
            this.ssl = ssl;
            this.password = password != null ? password : "";
            this.selected = selected;
            this.isNewlyLoggedIn = isNewlyLoggedIn;
        }

        public String getEmail() { return email; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public boolean isSsl() { return ssl; }
        public String getPassword() { return password; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
        public boolean isNewlyLoggedIn() { return isNewlyLoggedIn; }
        public void setNewlyLoggedIn(boolean newlyLoggedIn) { isNewlyLoggedIn = newlyLoggedIn; }
    }

    public static class CsvAccountItem {
        private String host;
        private int port;
        private boolean ssl;
        private String email;
        private String password;
        private String status;

        public CsvAccountItem(String host, int port, boolean ssl, String email, String password) {
            this.host = host;
            this.port = port;
            this.ssl = ssl;
            this.email = email;
            this.password = password;
            this.status = "Pending";
        }

        public String getHost() { return host; }
        public int getPort() { return port; }
        public boolean isSsl() { return ssl; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    private final MainController mainController;
    private final ObservableList<SourceFileModel> fileList;
    private final List<AccountCardItem> accountCardItems = new ArrayList<>();
    private final Map<String, ImapSourceAdapter> connectedAdapters = new LinkedHashMap<>();

    private VBox accountsContainer;
    private CheckBox chkSelectAll;
    private Label lblQueueCounter;
    private MFXProgressSpinner loginSpinner;

    // Single IMAP Login Controls
    private ComboBox<String> cmbServerPresets;
    private TextField tfEmail;
    private PasswordField pfPassword;
    private TextField tfHost;
    private TextField tfPort;
    private CheckBox cbSSL;
    private MFXButton btnTestConnection;
    private MFXButton btnAddAccount;
    private Label lblTestFeedback;

    // Batch CSV Import Controls
    private VBox singleImapBox;
    private VBox batchImapBox;
    private MFXButton btnModeSingle;
    private MFXButton btnModeBatch;
    private final ObservableList<CsvAccountItem> csvAccountList = FXCollections.observableArrayList();
    private TableView<CsvAccountItem> tblCsvAccounts;
    private Label lblCsvFilePath;
    private MFXButton btnVerifyCsv;

    private VBox incompleteCardsContainer;
    private VBox historyCardsContainer;

    public Step1ImportView(MainController mainController) {
        this.mainController = mainController;
        this.fileList = mainController.getFileList();

        setPadding(new Insets(20));
        getStyleClass().add("wizard-step-container");

        initUI();
        loadSavedAccountsFromDB();
    }

    private FlowPane providerChipsPane;
    private final Map<Integer, Button> providerChipButtons = new HashMap<>();

    private void initUI() {
        boolean dark = mainController != null && mainController.isDarkMode();

        VBox leftCard = new VBox(10);
        leftCard.setPrefWidth(390);
        leftCard.setMinWidth(370);
        leftCard.setMaxWidth(420);
        leftCard.setPadding(new Insets(14));
        leftCard.setStyle("-fx-background-color: " + (dark ? "#111c30" : "#ffffff") + "; " +
            "-fx-background-radius: 12px; -fx-border-radius: 12px; " +
            "-fx-border-color: " + (dark ? "rgba(148, 163, 184, 0.18)" : "#e2e8f0") + "; " +
            "-fx-border-width: 1px; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, " + (dark ? "0.3" : "0.04") + "), 8, 0, 0, 2);");

        Label lblSection1 = new Label("1. CONNECT IMAP ACCOUNT");
        lblSection1.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #0ea5e9; -fx-padding: 0 0 2px 0;");

        // Mode Switcher Header (SaaS Segmented Control)
        HBox modeSwitcher = new HBox(6);
        modeSwitcher.setAlignment(Pos.CENTER);
        modeSwitcher.setPadding(new Insets(3));
        modeSwitcher.setStyle("-fx-background-color: " + (dark ? "rgba(15, 23, 42, 0.8)" : "#f1f5f9") + "; -fx-background-radius: 10px; -fx-border-color: " + (dark ? "rgba(148, 163, 184, 0.15)" : "#e2e8f0") + "; -fx-border-radius: 10px;");

        btnModeSingle = new MFXButton("⚡ Quick Connect");
        btnModeSingle.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnModeSingle, Priority.ALWAYS);

        btnModeBatch = new MFXButton("📁 Enterprise CSV Batch");
        btnModeBatch.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnModeBatch, Priority.ALWAYS);

        btnModeSingle.setOnAction(e -> switchMode(true));
        btnModeBatch.setOnAction(e -> switchMode(false));

        modeSwitcher.getChildren().addAll(btnModeSingle, btnModeBatch);

        // --- SINGLE IMAP FORM ---
        singleImapBox = new VBox(10);
        singleImapBox.setPadding(new Insets(2, 0, 2, 0));
        singleImapBox.setStyle("-fx-background-color: transparent;");

        Label lblPreset = new Label("Choose Provider Preset:");
        lblPreset.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + (dark ? "#cbd5e1" : "#475569") + ";");

        // Provider quick chips
        providerChipsPane = new FlowPane(6, 6);
        String[] providers = {"Gmail", "Yahoo", "Outlook", "Zoho", "iCloud", "AOL", "Custom"};
        for (int i = 0; i < providers.length; i++) {
            final int pIndex = i;
            Button chip = new Button(providers[i]);
            chip.getStyleClass().add("provider-chip");
            chip.setOnAction(e -> {
                cmbServerPresets.getSelectionModel().select(pIndex);
                applyServerPreset();
                updateProviderChipsSelection(pIndex);
            });
            providerChipButtons.put(pIndex, chip);
            providerChipsPane.getChildren().add(chip);
        }

        cmbServerPresets = new ComboBox<>();
        cmbServerPresets.getItems().addAll(
                "Gmail IMAP (imap.gmail.com:993)",
                "Yahoo Mail (imap.mail.yahoo.com:993)",
                "Outlook / Hotmail (outlook.office365.com:993)",
                "AOL Mail (imap.aol.com:993)",
                "iCloud Mail (imap.mail.me.com:993)",
                "Zoho Mail (imap.zoho.com:993)",
                "Custom IMAP Server"
        );
        cmbServerPresets.getSelectionModel().select(5);
        cmbServerPresets.setMaxWidth(Double.MAX_VALUE);
        cmbServerPresets.setVisible(false);
        cmbServerPresets.setManaged(false);
        cmbServerPresets.setOnAction(e -> {
            applyServerPreset();
            updateProviderChipsSelection(cmbServerPresets.getSelectionModel().getSelectedIndex());
        });
        updateProviderChipsSelection(5);

        Label lblCredentials = new Label("Account Credentials:");
        lblCredentials.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + (dark ? "#cbd5e1" : "#475569") + ";");

        tfEmail = new TextField();
        tfEmail.setPromptText("Email address (e.g. user@gmail.com)");
        tfEmail.textProperty().addListener((obs, oldV, newV) -> autoDetectPresetFromEmail(newV));

        pfPassword = new PasswordField();
        pfPassword.setPromptText("App Password");
        // Automatically sanitize spaces, NBSP (\u00A0), dashes, and labels when pasted or typed
        pfPassword.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isEmpty()) {
                String clean = ImapAuthHelper.sanitizeAppPassword(newV);
                if (!clean.isEmpty() && !clean.equals(newV) && (newV.length() > clean.length() || newV.toLowerCase().contains("password"))) {
                    Platform.runLater(() -> {
                        pfPassword.setText(clean);
                        pfPassword.positionCaret(clean.length());
                    });
                }
            }
        });

        Label lblServer = new Label("Server & Security Settings:");
        lblServer.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + (dark ? "#cbd5e1" : "#475569") + ";");

        HBox hostPortBox = new HBox(8);
        tfHost = new TextField("imap.zoho.com");
        tfHost.setPromptText("IMAP Host");
        HBox.setHgrow(tfHost, Priority.ALWAYS);

        tfPort = new TextField("993");
        tfPort.setPromptText("Port");
        tfPort.setPrefWidth(65);

        cbSSL = new CheckBox("SSL/TLS");
        cbSSL.setSelected(true);
        cbSSL.setAlignment(Pos.CENTER_LEFT);
        cbSSL.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold;");

        hostPortBox.getChildren().addAll(tfHost, tfPort, cbSSL);

        HBox actionBtnBox = new HBox(10);
        btnTestConnection = new MFXButton("Test Connection");
        btnTestConnection.setStyle("-fx-background-color: " + (dark ? "rgba(14, 165, 233, 0.12)" : "#f0f9ff") + "; -fx-text-fill: #0ea5e9; -fx-font-weight: bold; -fx-border-color: #0ea5e9; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;");
        btnTestConnection.setOnAction(e -> handleTestConnection());

        btnAddAccount = new MFXButton("Add to Queue");
        btnAddAccount.setStyle("-fx-background-color: linear-gradient(to right, #0ea5e9, #0284c7); -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 18px;");
        btnAddAccount.setOnAction(e -> handleAddSingleAccount());
        HBox.setHgrow(btnAddAccount, Priority.ALWAYS);
        btnAddAccount.setMaxWidth(Double.MAX_VALUE);

        actionBtnBox.getChildren().addAll(btnTestConnection, btnAddAccount);

        lblTestFeedback = new Label();
        lblTestFeedback.setFont(Font.font("Segoe UI", 11.5));
        lblTestFeedback.setWrapText(true);

        loginSpinner = new MFXProgressSpinner();
        loginSpinner.setVisible(false);
        loginSpinner.setPrefSize(18, 18);

        HBox feedbackRow = new HBox(8, loginSpinner, lblTestFeedback);
        feedbackRow.setAlignment(Pos.CENTER_LEFT);

        singleImapBox.getChildren().addAll(
                lblPreset, providerChipsPane, cmbServerPresets,
                lblCredentials, tfEmail, pfPassword,
                lblServer, hostPortBox,
                actionBtnBox, feedbackRow
        );

        // --- BATCH CSV FORM ---
        batchImapBox = new VBox(10);
        batchImapBox.setPadding(new Insets(2, 0, 2, 0));
        batchImapBox.setStyle("-fx-background-color: transparent;");
        batchImapBox.setManaged(false);
        batchImapBox.setVisible(false);

        Label lblCsvInstruction = new Label("Upload CSV file with format: Host,Port,Security,Username,Password or Email,Password");
        lblCsvInstruction.setWrapText(true);
        lblCsvInstruction.setFont(Font.font("Segoe UI", 11.5));
        lblCsvInstruction.setStyle("-fx-text-fill: #94a3b8;");

        MFXButton btnBrowseCsv = new MFXButton("Browse CSV File...");
        btnBrowseCsv.setStyle("-fx-background-color: #0284c7; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand;");
        btnBrowseCsv.setOnAction(e -> handleBrowseCsv());

        lblCsvFilePath = new Label("No CSV file loaded");
        lblCsvFilePath.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        tblCsvAccounts = new TableView<>(csvAccountList);
        tblCsvAccounts.setPrefHeight(140);
        tblCsvAccounts.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<CsvAccountItem, String> colEmail = new TableColumn<>("Email/User");
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));

        TableColumn<CsvAccountItem, String> colHost = new TableColumn<>("Host");
        colHost.setCellValueFactory(new PropertyValueFactory<>("host"));

        TableColumn<CsvAccountItem, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        tblCsvAccounts.getColumns().addAll(colEmail, colHost, colStatus);

        btnVerifyCsv = new MFXButton("Verify & Import Accounts");
        btnVerifyCsv.setStyle("-fx-background-color: #10b981; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand;");
        btnVerifyCsv.setMaxWidth(Double.MAX_VALUE);
        btnVerifyCsv.setOnAction(e -> handleVerifyAndImportCsv());

        batchImapBox.getChildren().addAll(lblCsvInstruction, btnBrowseCsv, lblCsvFilePath, tblCsvAccounts, btnVerifyCsv);

        leftCard.getChildren().addAll(lblSection1, modeSwitcher, singleImapBox, batchImapBox);

        // Right Column: Connected Accounts Queue Card Box
        VBox rightCard = new VBox(10);
        HBox.setHgrow(rightCard, Priority.ALWAYS);
        rightCard.setPadding(new Insets(14));
        rightCard.setStyle("-fx-background-color: " + (dark ? "#111c30" : "#ffffff") + "; " +
            "-fx-background-radius: 12px; -fx-border-radius: 12px; " +
            "-fx-border-color: " + (dark ? "rgba(148, 163, 184, 0.18)" : "#e2e8f0") + "; " +
            "-fx-border-width: 1px; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, " + (dark ? "0.3" : "0.04") + "), 8, 0, 0, 2);");

        HBox queueHeader = new HBox(10);
        queueHeader.setAlignment(Pos.CENTER_LEFT);

        Label lblQueueTitle = new Label("2. CONNECTED MAILBOXES QUEUE");
        lblQueueTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #0ea5e9; -fx-padding: 0 0 2px 0;");

        lblQueueCounter = new Label("0 accounts selected");
        lblQueueCounter.setStyle("-fx-background-color: " + (dark ? "rgba(14, 165, 233, 0.18)" : "#e0f2fe") + "; -fx-text-fill: " + (dark ? "#38bdf8" : "#0369a1") + "; -fx-font-size: 10.5px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 6;");

        Label sslBadge = new Label("🔒 SSL / TLS Verified");
        sslBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.12); -fx-text-fill: #10b981; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 7; -fx-background-radius: 6;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        MFXButton btnClear = new MFXButton("Remove All Accounts");
        btnClear.setGraphic(MaterialIcons.icon(MaterialIcons.DELETE, "-fx-text-fill: #f43f5e; -fx-font-size: 14px;"));
        btnClear.setStyle("-fx-background-color: " + (dark ? "rgba(244, 63, 94, 0.12)" : "#fff1f2") + "; -fx-text-fill: #f43f5e; -fx-font-weight: bold; -fx-font-size: 11px; -fx-border-color: rgba(244, 63, 94, 0.3); -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-padding: 3px 9px;");
        btnClear.setOnAction(e -> handleClearAll());

        queueHeader.getChildren().addAll(lblQueueTitle, lblQueueCounter, sslBadge, spacer, btnClear);

        // Sub-toolbar: Select All on left (directly above account card checkboxes)
        HBox queueSubToolbar = new HBox(10);
        queueSubToolbar.setAlignment(Pos.CENTER_LEFT);
        queueSubToolbar.setPadding(new Insets(2, 0, 2, 0));

        chkSelectAll = new CheckBox("Select All");
        chkSelectAll.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
        chkSelectAll.setStyle("-fx-cursor: hand; -fx-text-fill: " + (dark ? "#f8fafc" : "#0f172a") + ";");
        chkSelectAll.setOnAction(e -> {
            boolean sel = chkSelectAll.isSelected();
            for (AccountCardItem item : accountCardItems) {
                item.setSelected(sel);
            }
            refreshAccountsUI();
        });

        queueSubToolbar.getChildren().add(chkSelectAll);

        accountsContainer = new VBox(8);
        accountsContainer.setPadding(new Insets(4, 2, 4, 2));

        ScrollPane scrollAccounts = new ScrollPane(accountsContainer);
        scrollAccounts.setFitToWidth(true);
        scrollAccounts.setPrefHeight(255);
        scrollAccounts.setMinHeight(160);
        VBox.setVgrow(scrollAccounts, Priority.ALWAYS);
        scrollAccounts.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        rightCard.getChildren().addAll(queueHeader, queueSubToolbar, scrollAccounts);

        HBox mainSplit = new HBox(16, leftCard, rightCard);

        // --- BOTTOM: Modern Segmented Card for Resume Migrations & History ---
        VBox bottomCard = new VBox(0);
        bottomCard.setPrefHeight(150);
        bottomCard.setMinHeight(125);
        bottomCard.setStyle("-fx-background-color: " + (dark ? "#0f172a" : "#ffffff") + "; " +
            "-fx-background-radius: 12px; -fx-border-radius: 12px; " +
            "-fx-border-color: " + (dark ? "rgba(148, 163, 184, 0.18)" : "#e2e8f0") + "; " +
            "-fx-border-width: 1px; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, " + (dark ? "0.3" : "0.04") + "), 8, 0, 0, 2);");

        HBox tabHeaderBar = new HBox(10);
        tabHeaderBar.setAlignment(Pos.CENTER_LEFT);
        tabHeaderBar.setPadding(new Insets(8, 14, 8, 14));
        tabHeaderBar.setStyle("-fx-background-color: " + (dark ? "rgba(15, 23, 42, 0.75)" : "#f8fafc") + "; " +
            "-fx-border-color: " + (dark ? "rgba(148, 163, 184, 0.12)" : "#e2e8f0") + "; " +
            "-fx-border-width: 0 0 1 0; -fx-background-radius: 12px 12px 0 0;");

        MFXButton btnTabIncomplete = new MFXButton("Incomplete Migrations (Resume Center)");
        btnTabIncomplete.setGraphic(MaterialIcons.icon(MaterialIcons.PLAY_ARROW, "-fx-text-fill: inherit; -fx-font-size: 15px;"));
        
        MFXButton btnTabHistory = new MFXButton("Migration History");
        btnTabHistory.setGraphic(MaterialIcons.icon(MaterialIcons.HISTORY, "-fx-text-fill: inherit; -fx-font-size: 15px;"));

        incompleteCardsContainer = new VBox(6);
        incompleteCardsContainer.setPadding(new Insets(10));
        ScrollPane incompleteScroll = new ScrollPane(incompleteCardsContainer);
        incompleteScroll.setFitToWidth(true);
        incompleteScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        historyCardsContainer = new VBox(6);
        historyCardsContainer.setPadding(new Insets(10));
        ScrollPane historyScroll = new ScrollPane(historyCardsContainer);
        historyScroll.setFitToWidth(true);
        historyScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        historyScroll.setVisible(false);
        historyScroll.setManaged(false);

        StackPane contentStack = new StackPane(incompleteScroll, historyScroll);
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        Runnable updateTabStyles = () -> {
            boolean isIncompleteActive = incompleteScroll.isVisible();
            if (isIncompleteActive) {
                btnTabIncomplete.setStyle("-fx-background-color: linear-gradient(to right, #0ea5e9, #0284c7); -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11.5px; -fx-background-radius: 8px; -fx-padding: 6px 14px; -fx-cursor: hand;");
                btnTabHistory.setStyle("-fx-background-color: " + (dark ? "rgba(148, 163, 184, 0.12)" : "#f1f5f9") + "; -fx-text-fill: " + (dark ? "#94a3b8" : "#475569") + "; -fx-font-weight: bold; -fx-font-size: 11.5px; -fx-background-radius: 8px; -fx-padding: 6px 14px; -fx-cursor: hand;");
            } else {
                btnTabHistory.setStyle("-fx-background-color: linear-gradient(to right, #0ea5e9, #0284c7); -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11.5px; -fx-background-radius: 8px; -fx-padding: 6px 14px; -fx-cursor: hand;");
                btnTabIncomplete.setStyle("-fx-background-color: " + (dark ? "rgba(148, 163, 184, 0.12)" : "#f1f5f9") + "; -fx-text-fill: " + (dark ? "#94a3b8" : "#475569") + "; -fx-font-weight: bold; -fx-font-size: 11.5px; -fx-background-radius: 8px; -fx-padding: 6px 14px; -fx-cursor: hand;");
            }
        };

        btnTabIncomplete.setOnAction(e -> {
            incompleteScroll.setVisible(true);
            incompleteScroll.setManaged(true);
            historyScroll.setVisible(false);
            historyScroll.setManaged(false);
            updateTabStyles.run();
        });

        btnTabHistory.setOnAction(e -> {
            historyScroll.setVisible(true);
            historyScroll.setManaged(true);
            incompleteScroll.setVisible(false);
            incompleteScroll.setManaged(false);
            updateTabStyles.run();
        });

        updateTabStyles.run();
        tabHeaderBar.getChildren().addAll(btnTabIncomplete, btnTabHistory);
        bottomCard.getChildren().addAll(tabHeaderBar, contentStack);

        VBox topCenterVBox = new VBox(14, mainSplit, bottomCard);
        topCenterVBox.setPadding(new Insets(0, 4, 0, 0));

        ScrollPane centerScrollPane = new ScrollPane(topCenterVBox);
        centerScrollPane.setFitToWidth(true);
        centerScrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        setCenter(centerScrollPane);

        switchMode(true);
        refreshRecentMailboxes();
    }

    private void updateProviderChipsSelection(int selectedIndex) {
        if (providerChipButtons == null) return;
        for (Map.Entry<Integer, Button> entry : providerChipButtons.entrySet()) {
            Button btn = entry.getValue();
            if (entry.getKey() == selectedIndex) {
                btn.getStyleClass().add("provider-chip-selected");
            } else {
                btn.getStyleClass().remove("provider-chip-selected");
            }
        }
    }

    private void switchMode(boolean isSingle) {
        singleImapBox.setVisible(isSingle);
        singleImapBox.setManaged(isSingle);

        batchImapBox.setVisible(!isSingle);
        batchImapBox.setManaged(!isSingle);

        boolean dark = mainController != null && mainController.isDarkMode();

        if (isSingle) {
            btnModeSingle.setStyle("-fx-background-color: linear-gradient(to right, #0ea5e9, #0284c7); -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 7px 12px;");
            btnModeBatch.setStyle("-fx-background-color: " + (dark ? "rgba(148, 163, 184, 0.12)" : "#f1f5f9") + "; -fx-text-fill: " + (dark ? "#94a3b8" : "#475569") + "; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 7px 12px;");
        } else {
            btnModeBatch.setStyle("-fx-background-color: linear-gradient(to right, #0ea5e9, #0284c7); -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 7px 12px;");
            btnModeSingle.setStyle("-fx-background-color: " + (dark ? "rgba(148, 163, 184, 0.12)" : "#f1f5f9") + "; -fx-text-fill: " + (dark ? "#94a3b8" : "#475569") + "; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 7px 12px;");
        }
    }

    private void applyServerPreset() {
        int idx = cmbServerPresets.getSelectionModel().getSelectedIndex();
        switch (idx) {
            case 0: // Gmail
                tfHost.setText("imap.gmail.com");
                tfPort.setText("993");
                cbSSL.setSelected(true);
                break;
            case 1: // Yahoo
                tfHost.setText("imap.mail.yahoo.com");
                tfPort.setText("993");
                cbSSL.setSelected(true);
                break;
            case 2: // Outlook
                tfHost.setText("outlook.office365.com");
                tfPort.setText("993");
                cbSSL.setSelected(true);
                break;
            case 3: // AOL
                tfHost.setText("imap.aol.com");
                tfPort.setText("993");
                cbSSL.setSelected(true);
                break;
            case 4: // iCloud
                tfHost.setText("imap.mail.me.com");
                tfPort.setText("993");
                cbSSL.setSelected(true);
                break;
            case 5: // Zoho
                tfHost.setText("imap.zoho.com");
                tfPort.setText("993");
                cbSSL.setSelected(true);
                break;
            case 6: // Custom
            default:
                tfHost.setText("");
                tfHost.setPromptText("mail.yourdomain.com");
                tfPort.setText("993");
                cbSSL.setSelected(true);
                break;
        }
    }

    private void autoDetectPresetFromEmail(String email) {
        if (email == null) return;
        String trimmed = email.trim();
        if (trimmed.isEmpty()) return;

        ImapAuthHelper.ProviderPreset preset = ImapAuthHelper.detectPreset(trimmed);
        int targetIndex = 6;
        if ("Gmail".equals(preset.name)) targetIndex = 0;
        else if ("Yahoo".equals(preset.name)) targetIndex = 1;
        else if ("Outlook".equals(preset.name)) targetIndex = 2;
        else if ("AOL".equals(preset.name)) targetIndex = 3;
        else if ("iCloud".equals(preset.name)) targetIndex = 4;
        else if ("Zoho".equals(preset.name)) targetIndex = 5;

        if (targetIndex != cmbServerPresets.getSelectionModel().getSelectedIndex()) {
            cmbServerPresets.getSelectionModel().select(targetIndex);
            updateProviderChipsSelection(targetIndex);
            if (targetIndex != 6) {
                tfHost.setText(preset.host);
                tfPort.setText(String.valueOf(preset.port));
                cbSSL.setSelected(preset.ssl);
            } else if (!preset.host.isEmpty() && (tfHost.getText() == null || tfHost.getText().trim().isEmpty() || tfHost.getText().contains("gmail.com"))) {
                tfHost.setText(preset.host);
                tfPort.setText(String.valueOf(preset.port));
                cbSSL.setSelected(preset.ssl);
            }
        }
    }

    private void handleTestConnection() {
        String email = tfEmail.getText() != null ? tfEmail.getText().replaceAll("[\\p{Z}\\s\\u00A0\\uFEFF]+", "").trim().toLowerCase() : "";
        tfEmail.setText(email);
        String pass = ImapAuthHelper.sanitizeAppPassword(pfPassword.getText());
        if (!pass.isEmpty()) {
            pfPassword.setText(pass);
        } else if (pfPassword.getText() != null) {
            pass = pfPassword.getText().replaceAll("[\\p{Z}\\s\\u00A0\\uFEFF]+", "").trim();
        }
        String host = tfHost.getText() != null ? tfHost.getText().replaceAll("[\\p{Z}\\s\\u00A0\\uFEFF]+", "").trim() : "";
        tfHost.setText(host);
        int port = 993;
        try { port = Integer.parseInt(tfPort.getText().trim()); } catch (Exception ignored) {}
        boolean ssl = cbSSL.isSelected();

        if (email.isEmpty() || pass.isEmpty() || host.isEmpty()) {
            lblTestFeedback.setText("Please enter email, app password, and IMAP host.");
            lblTestFeedback.setTextFill(Color.RED);
            return;
        }

        loginSpinner.setVisible(true);
        btnTestConnection.setDisable(true);
        lblTestFeedback.setText("Testing IMAP connection & verifying credentials...");
        lblTestFeedback.setTextFill(Color.web("#0ea5e9"));

        final int finalPort = port;
        final String finalEmail = email;
        final String finalPass = pass;
        final String finalHost = host;
        new Thread(() -> {
            ImapAuthHelper.AuthResult res = ImapAuthHelper.testAndAuthenticate(finalHost, finalPort, ssl, finalEmail, finalPass, 25);

            Platform.runLater(() -> {
                loginSpinner.setVisible(false);
                btnTestConnection.setDisable(false);

                if (res.success) {
                    if (res.verifiedPassword != null && !res.verifiedPassword.isEmpty()) {
                        pfPassword.setText(res.verifiedPassword);
                    }
                    lblTestFeedback.setText("✓ Connection & Authentication Successful!");
                    lblTestFeedback.setTextFill(Color.web("#16a34a"));
                } else {
                    lblTestFeedback.setText("✗ " + res.errorMessage);
                    lblTestFeedback.setTextFill(Color.RED);
                }
            });
        }).start();
    }

    private void handleAddSingleAccount() {
        String email = tfEmail.getText() != null ? tfEmail.getText().replaceAll("[\\p{Z}\\s\\u00A0\\uFEFF]+", "").trim().toLowerCase() : "";
        tfEmail.setText(email);
        String pass = ImapAuthHelper.sanitizeAppPassword(pfPassword.getText());
        if (!pass.isEmpty()) {
            pfPassword.setText(pass);
        } else if (pfPassword.getText() != null) {
            pass = pfPassword.getText().replaceAll("[\\p{Z}\\s\\u00A0\\uFEFF]+", "").trim();
        }
        String host = tfHost.getText() != null ? tfHost.getText().replaceAll("[\\p{Z}\\s\\u00A0\\uFEFF]+", "").trim() : "";
        tfHost.setText(host);
        int port = 993;
        try { port = Integer.parseInt(tfPort.getText().trim()); } catch (Exception ignored) {}
        boolean ssl = cbSSL.isSelected();

        if (email.isEmpty() || pass.isEmpty() || host.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please enter email, app password, and IMAP host.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        loginSpinner.setVisible(true);
        btnAddAccount.setDisable(true);
        btnTestConnection.setDisable(true);
        lblTestFeedback.setText("Authenticating with IMAP server...");
        lblTestFeedback.setTextFill(Color.web("#0ea5e9"));

        final int finalPort = port;
        final String finalEmail = email;
        final String finalPass = pass;
        final String finalHost = host;
        new Thread(() -> {
            ImapAuthHelper.AuthResult res = ImapAuthHelper.testAndAuthenticate(finalHost, finalPort, ssl, finalEmail, finalPass, 25);

            Platform.runLater(() -> {
                loginSpinner.setVisible(false);
                btnAddAccount.setDisable(false);
                btnTestConnection.setDisable(false);

                if (res.success) {
                    String workingPass = res.verifiedPassword != null ? res.verifiedPassword : finalPass.trim();
                    SettingsManager.saveImapSourceAccount(finalEmail, finalHost, finalPort, ssl, workingPass);
                    addOrUpdateAccountItem(finalEmail, finalHost, finalPort, ssl, workingPass, true, true);
                    tfEmail.clear();
                    pfPassword.clear();
                    lblTestFeedback.setText("✓ Account verified & added to queue successfully!");
                    lblTestFeedback.setTextFill(Color.web("#16a34a"));
                } else {
                    lblTestFeedback.setText("✗ " + res.errorMessage);
                    lblTestFeedback.setTextFill(Color.RED);
                    Alert alert = new Alert(Alert.AlertType.ERROR, res.errorMessage, ButtonType.OK);
                    alert.setTitle("IMAP Login Failed");
                    alert.setHeaderText("Could Not Connect to " + email);
                    alert.showAndWait();
                }
            });
        }).start();
    }

    private void handleBrowseCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select IMAP Accounts CSV File");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV & Text Files (*.csv, *.txt)", "*.csv", "*.txt"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f != null) {
            lblCsvFilePath.setText(f.getName());
            loadCsvAccounts(f);
        }
    }

    private void loadCsvAccounts(File csvFile) {
        csvAccountList.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.toLowerCase().startsWith("host,") || line.toLowerCase().startsWith("email,")) {
                    continue; // Skip comments and header
                }
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    // Host, Port, Security, Username, Password
                    String host = parts[0].trim();
                    int port = 993;
                    try { port = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {}
                    boolean ssl = parts[2].trim().equalsIgnoreCase("SSL") || parts[2].trim().equalsIgnoreCase("SSL/TLS") || parts[2].trim().equalsIgnoreCase("true");
                    String user = parts[3].trim().toLowerCase();
                    String pass = ImapAuthHelper.sanitizeAppPassword(parts[4]);
                    csvAccountList.add(new CsvAccountItem(host, port, ssl, user, pass));
                } else if (parts.length >= 2) {
                    // Email, Password
                    String user = parts[0].trim().toLowerCase();
                    String pass = ImapAuthHelper.sanitizeAppPassword(parts[1]);
                    ImapAuthHelper.ProviderPreset preset = ImapAuthHelper.detectPreset(user);
                    csvAccountList.add(new CsvAccountItem(preset.host, preset.port, preset.ssl, user, pass));
                }
            }
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to parse CSV file: " + ex.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    private void handleVerifyAndImportCsv() {
        if (csvAccountList.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "No accounts loaded from CSV file.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        btnVerifyCsv.setDisable(true);
        ExecutorService pool = Executors.newFixedThreadPool(5);

        new Thread(() -> {
            int total = csvAccountList.size();
            int[] verifiedCount = {0};

            for (CsvAccountItem item : csvAccountList) {
                pool.submit(() -> {
                    try {
                        ImapAuthHelper.AuthResult res = ImapAuthHelper.testAndAuthenticate(
                            item.getHost(), item.getPort(), item.isSsl(), item.getEmail(), item.getPassword(), 15
                        );
                        if (res.success) {
                            item.setStatus("Verified ✓");
                            verifiedCount[0]++;
                            String workingPass = res.verifiedPassword != null ? res.verifiedPassword : item.getPassword();
                            Platform.runLater(() -> {
                                SettingsManager.saveImapSourceAccount(item.getEmail(), item.getHost(), item.getPort(), item.isSsl(), workingPass);
                                addOrUpdateAccountItem(item.getEmail(), item.getHost(), item.getPort(), item.isSsl(), workingPass, true, true);
                            });
                        } else {
                            item.setStatus("Login Failed ✗");
                        }
                    } catch (Exception ex) {
                        item.setStatus("Error ✗");
                    }
                    Platform.runLater(() -> tblCsvAccounts.refresh());
                });
            }

            pool.shutdown();
            while (!pool.isTerminated()) {
                try { Thread.sleep(200); } catch (Exception ignored) {}
            }

            Platform.runLater(() -> {
                btnVerifyCsv.setDisable(false);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Batch CSV verification completed.\nVerified & Imported: " + verifiedCount[0] + " / " + total + " account(s).", ButtonType.OK);
                alert.showAndWait();
            });
        }).start();
    }

    private void loadSavedAccountsFromDB() {
        accountCardItems.clear();
        List<ImapAccountRecord> dbRecords = SettingsManager.getSavedImapSourceAccounts();
        for (ImapAccountRecord rec : dbRecords) {
            if (rec.email() != null && !rec.email().trim().isEmpty()) {
                String cleanEmail = rec.email().trim().toLowerCase();
                // Select saved accounts by default so Next button is active immediately
                AccountCardItem item = new AccountCardItem(cleanEmail, rec.host(), rec.port(), rec.ssl(), rec.password(), true, false);
                accountCardItems.add(item);
            }
        }
        refreshAccountsUI();
    }

    public void addOrUpdateAccountItem(String email, String host, int port, boolean ssl, String password, boolean selected, boolean isNew) {
        if (email == null || email.trim().isEmpty()) return;
        String cleanEmail = email.trim().toLowerCase();

        AccountCardItem existing = null;
        for (AccountCardItem item : accountCardItems) {
            if (item.getEmail().equalsIgnoreCase(cleanEmail)) {
                existing = item;
                break;
            }
        }

        if (existing != null) {
            existing.setSelected(selected);
            existing.setNewlyLoggedIn(isNew);
        } else {
            AccountCardItem newItem = new AccountCardItem(cleanEmail, host, port, ssl, password, selected, isNew);
            accountCardItems.add(newItem);
        }

        refreshAccountsUI();
    }

    public void refreshAccountsUI() {
        accountsContainer.getChildren().clear();
        int selectedCount = 0;
        boolean dark = mainController != null && mainController.isDarkMode();

        if (accountCardItems.isEmpty()) {
            HBox emptyBox = new HBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(24));
            Label emptyLbl = new Label("No connected mailboxes in queue. Add an account using Quick Connect or CSV Batch.");
            emptyLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-font-style: italic;");
            emptyBox.getChildren().add(emptyLbl);
            accountsContainer.getChildren().add(emptyBox);
        } else {
            for (AccountCardItem item : accountCardItems) {
                if (item.isSelected()) selectedCount++;

                HBox card = new HBox(12);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(10, 14, 10, 14));
                card.setStyle("-fx-background-color: " + (dark ? "#111c30" : "#ffffff") + "; -fx-background-radius: 10px; -fx-border-color: " + (dark ? "rgba(148, 163, 184, 0.2)" : "#e2e8f0") + "; -fx-border-radius: 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0," + (dark ? "0.3" : "0.03") + "), 6, 0, 0, 1);");

                CheckBox chk = new CheckBox();
                chk.setSelected(item.isSelected());
                chk.setStyle("-fx-cursor: hand;");
                chk.setOnAction(e -> {
                    item.setSelected(chk.isSelected());
                    refreshAccountsUI();
                });

                Label mailIcon = MaterialIcons.icon(MaterialIcons.ACCOUNT_CIRCLE, 22);
                mailIcon.setStyle("-fx-text-fill: #0ea5e9;");

                VBox info = new VBox(2);
                Label lblEmail = new Label(item.getEmail());
                lblEmail.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                lblEmail.setStyle("-fx-text-fill: " + (dark ? "#f8fafc" : "#0f172a") + ";");

                HBox subRow = new HBox(8);
                subRow.setAlignment(Pos.CENTER_LEFT);
                Label lblServer = new Label(item.getHost() + ":" + item.getPort());
                lblServer.setFont(Font.font("Segoe UI", 11));
                lblServer.setStyle("-fx-text-fill: " + (dark ? "#94a3b8" : "#64748b") + ";");

                Label tagSsl = new Label(item.isSsl() ? "SSL/TLS" : "Plain");
                tagSsl.setStyle("-fx-background-color: " + (item.isSsl() ? "rgba(16, 185, 129, 0.12)" : "rgba(245, 158, 11, 0.12)") + "; -fx-text-fill: " + (item.isSsl() ? "#10b981" : "#f59e0b") + "; -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-padding: 1 6; -fx-background-radius: 4;");

                subRow.getChildren().addAll(lblServer, tagSsl);
                info.getChildren().addAll(lblEmail, subRow);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button btnDelete = new Button("");
                btnDelete.setGraphic(MaterialIcons.icon(MaterialIcons.DELETE, "-fx-text-fill: #f43f5e; -fx-font-size: 15px;"));
                btnDelete.setStyle("-fx-background-color: " + (dark ? "rgba(244, 63, 94, 0.15)" : "#fee2e2") + "; -fx-padding: 6px 10px; -fx-background-radius: 6; -fx-cursor: hand;");
                btnDelete.setTooltip(new Tooltip("Remove Account Connection"));
                btnDelete.setOnAction(e -> handleDeleteAccount(item));

                card.getChildren().addAll(chk, mailIcon, info, spacer, btnDelete);
                accountsContainer.getChildren().add(card);
            }
        }

        lblQueueCounter.setText(selectedCount + " / " + accountCardItems.size() + " account(s) selected");
        chkSelectAll.setSelected(selectedCount > 0 && selectedCount == accountCardItems.size());

        // Automatically sync fileList with MainController to enable Next button immediately
        syncFileListWithController();
    }

    public void syncFileListWithController() {
        fileList.clear();
        connectedAdapters.clear();

        List<AccountCardItem> selectedItems = accountCardItems.stream().filter(AccountCardItem::isSelected).toList();

        for (AccountCardItem item : selectedItems) {
            String email = item.getEmail();

            File fakeFile = new File(System.getProperty("user.home"), email + ".imap");
            if (!fakeFile.exists()) {
                try { fakeFile.createNewFile(); } catch (Exception ignored) {}
            }
            SourceFileModel model = new SourceFileModel(
                    fakeFile.getAbsolutePath(),
                    email,
                    "Online Account",
                    "Valid",
                    true,
                    "File",
                    "IMAP Account",
                    "IMAP"
            );
            fileList.add(model);

            ImapSourceAdapter adapter = new ImapSourceAdapter();
            adapter.setConnectedAccount(email);
            connectedAdapters.put(email, adapter);
        }

        if (mainController != null) {
            mainController.updateStatusSummary();
        }
    }

    private void handleDeleteAccount(AccountCardItem item) {
        boolean confirmed = showModernConfirmDialog(
            "Remove Account Connection?",
            "IMAP DISCONNECT",
            item.getEmail(),
            "Are you sure you want to remove this account connection?\n\n" +
            "• The saved account credentials and queue item will be removed.\n" +
            "• Remote mailbox emails on your email server remain completely safe.",
            "Yes, Remove Account"
        );
        if (confirmed) {
            SettingsManager.deleteImapSourceAccount(item.getEmail());
            accountCardItems.remove(item);
            refreshAccountsUI();
        }
    }

    private void handleClearAll() {
        if (accountCardItems.isEmpty()) return;

        boolean confirmed = showModernConfirmDialog(
            "Remove All Accounts?",
            "CLEAR QUEUE",
            "Remove all " + accountCardItems.size() + " connected IMAP mailboxes",
            "Are you sure you want to remove ALL connected accounts?\n\n" +
            "• All queued account credentials and folder caches will be cleared.\n" +
            "• Remote mailbox data on all email servers remains completely safe.",
            "Yes, Remove All Accounts"
        );
        if (confirmed) {
            SettingsManager.deleteAllImapSourceAccounts();
            accountCardItems.clear();
            fileList.clear();
            connectedAdapters.clear();
            refreshAccountsUI();
        }
    }

    private boolean showModernConfirmDialog(String title, String badgeText, String subtitle, String bodyText, String confirmBtnText) {
        boolean dark = mainController != null ? mainController.isDarkMode() : com.pstconverter.util.ThemeManager.isDarkMode();

        Stage confirmStage = new Stage(StageStyle.TRANSPARENT);
        if (mainController != null && mainController.getPrimaryStage() != null) {
            confirmStage.initOwner(mainController.getPrimaryStage());
        }
        confirmStage.initModality(Modality.APPLICATION_MODAL);
        confirmStage.setResizable(false);

        // Header danger icon badge (Trash icon)
        StackPane iconBadge = new StackPane();
        iconBadge.setPrefSize(48, 48);
        iconBadge.setMinSize(48, 48);
        iconBadge.setMaxSize(48, 48);
        iconBadge.setStyle(
            "-fx-background-color: rgba(239, 68, 68, 0.15); " +
            "-fx-background-radius: 14px; -fx-border-radius: 14px; " +
            "-fx-border-color: rgba(239, 68, 68, 0.35); -fx-border-width: 1.2px; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(239, 68, 68, 0.4), 14, 0, 0, 2);"
        );
        Label trashIcon = new Label(MaterialIcons.DELETE);
        trashIcon.getStyleClass().add("material-icon");
        trashIcon.setFont(Font.font("Material Icons", 24));
        trashIcon.setStyle("-fx-text-fill: #ef4444; -fx-font-family: 'Material Icons'; -fx-font-size: 24px;");
        iconBadge.getChildren().add(trashIcon);

        VBox titleBox = new VBox(3);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        titleLbl.setStyle("-fx-text-fill: " + (dark ? "#ffffff" : "#0f172a") + ";");

        HBox topTitleRow = new HBox(8, titleLbl);
        topTitleRow.setAlignment(Pos.CENTER_LEFT);
        if (badgeText != null && !badgeText.isEmpty()) {
            Label badge = new Label(badgeText);
            badge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.15); -fx-text-fill: #ef4444; -fx-border-color: rgba(239, 68, 68, 0.4); -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-font-size: 9.5px; -fx-font-weight: 800; -fx-padding: 2px 7px;");
            topTitleRow.getChildren().add(badge);
        }

        Label subtitleLbl = new Label(subtitle);
        subtitleLbl.setFont(Font.font("Segoe UI", 11.5));
        subtitleLbl.setStyle("-fx-text-fill: #94a3b8;");
        titleBox.getChildren().addAll(topTitleRow, subtitleLbl);

        HBox headerRow = new HBox(14, iconBadge, titleBox);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // Body message
        Label msgLbl = new Label(bodyText);
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(440);
        msgLbl.setFont(Font.font("Segoe UI", 12.5));
        msgLbl.setStyle("-fx-text-fill: " + (dark ? "#cbd5e1" : "#475569") + "; -fx-line-spacing: 3px;");

        // Action Buttons
        final boolean[] confirmed = new boolean[]{false};

        Button btnCancel = new Button("No, Keep");
        btnCancel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        btnCancel.setStyle(dark
            ? "-fx-background-color: rgba(30, 41, 59, 0.85); -fx-text-fill: #f1f5f9; -fx-border-color: rgba(148, 163, 184, 0.25); -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 9px 20px; -fx-cursor: hand;"
            : "-fx-background-color: #f1f5f9; -fx-text-fill: #334155; -fx-border-color: #cbd5e1; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 9px 20px; -fx-cursor: hand;");
        btnCancel.setOnAction(e -> confirmStage.close());

        Button btnConfirm = new Button(confirmBtnText);
        btnConfirm.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        btnConfirm.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #ef4444, #dc2626); " +
            "-fx-text-fill: #ffffff; " +
            "-fx-background-radius: 8px; " +
            "-fx-padding: 9px 22px; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(239, 68, 68, 0.45), 10, 0, 0, 2);"
        );
        btnConfirm.setOnAction(e -> {
            confirmed[0] = true;
            confirmStage.close();
        });

        Region btnSpacer = new Region();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);

        HBox btnBar = new HBox(12, btnSpacer, btnCancel, btnConfirm);
        btnBar.setAlignment(Pos.CENTER_RIGHT);
        btnBar.setPadding(new Insets(10, 0, 0, 0));

        VBox card = new VBox(16, headerRow, msgLbl, btnBar);
        card.setPadding(new Insets(24, 28, 22, 28));
        card.setPrefWidth(500);
        card.setMaxWidth(500);
        card.setStyle(dark
            ? "-fx-background-color: linear-gradient(to bottom right, #090d16, #0f172a, #162036); " +
              "-fx-background-radius: 16px; -fx-border-radius: 16px; " +
              "-fx-border-color: linear-gradient(to bottom right, rgba(239, 68, 68, 0.55), rgba(14, 165, 233, 0.35)); " +
              "-fx-border-width: 1.5px; " +
              "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.8), 35, 0, 0, 12);"
            : "-fx-background-color: linear-gradient(to bottom right, #ffffff, #f8fafc); " +
              "-fx-background-radius: 16px; -fx-border-radius: 16px; " +
              "-fx-border-color: linear-gradient(to bottom right, rgba(239, 68, 68, 0.45), rgba(14, 165, 233, 0.25)); " +
              "-fx-border-width: 1.5px; " +
              "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.25), 30, 0, 0, 10);"
        );

        StackPane root = new StackPane(card);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, 540, 295);
        scene.setFill(Color.TRANSPARENT);
        try {
            if (mainController != null) scene.getStylesheets().add(mainController.getActiveThemeStylesheet());
        } catch (Exception ignored) {}
        confirmStage.setScene(scene);

        scene.setOnKeyPressed(k -> {
            if (k.getCode() == KeyCode.ESCAPE) {
                confirmStage.close();
            }
        });

        card.setScaleX(0.92);
        card.setScaleY(0.92);
        card.setOpacity(0.0);
        confirmStage.setOnShown(ev -> {
            FadeTransition ft = new FadeTransition(Duration.millis(180), card);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);

            ScaleTransition st = new ScaleTransition(Duration.millis(180), card);
            st.setFromX(0.92);
            st.setFromY(0.92);
            st.setToX(1.0);
            st.setToY(1.0);

            ParallelTransition pt = new ParallelTransition(ft, st);
            pt.play();
        });

        confirmStage.showAndWait();
        return confirmed[0];
    }

    /**
     * Called when clicking 'Next'. Prepares fileList for Step 2.
     */
    public boolean prepareFileListForNextStep() {
        syncFileListWithController();

        if (fileList.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please select at least one IMAP account checkbox to proceed.", ButtonType.OK);
            alert.setTitle("No Account Selected");
            alert.setHeaderText("Account Selection Required");
            alert.showAndWait();
            return false;
        }

        return true;
    }

    public void refreshRecentMailboxes() {
        refreshIncompleteMigrations();
        refreshHistoryMigrations();
        if (accountCardItems.isEmpty()) {
            loadSavedAccountsFromDB();
        } else {
            refreshAccountsUI();
        }
    }

    private String formatSourcePathForDisplay(String path) {
        if (path == null || path.trim().isEmpty()) return "";
        String clean = path.trim();
        if (clean.toLowerCase().endsWith(".gmail")) {
            clean = clean.substring(0, clean.length() - 6);
        } else if (clean.toLowerCase().endsWith(".imap")) {
            clean = clean.substring(0, clean.length() - 5);
        }
        File f = new File(clean);
        return f.getName();
    }

    private void refreshIncompleteMigrations() {
        incompleteCardsContainer.getChildren().clear();
        List<MigrationSession> inProgressList = SettingsManager.getAllMigrationSessions().stream()
                .filter(s -> "IN_PROGRESS".equalsIgnoreCase(s.status()))
                .toList();

        boolean dark = mainController != null && mainController.isDarkMode();

        if (inProgressList.isEmpty()) {
            HBox emptyBox = new HBox(8);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(18));
            Label emptyLbl = new Label("No interrupted migration sessions found. Paused or stopped migrations will appear here to resume.");
            emptyLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11.5px; -fx-font-style: italic;");
            emptyBox.getChildren().add(emptyLbl);
            incompleteCardsContainer.getChildren().add(emptyBox);
        } else {
            for (MigrationSession s : inProgressList) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(10, 14, 10, 14));
                row.setStyle("-fx-background-color: " + (dark ? "#111c30" : "#ffffff") + "; -fx-background-radius: 8px; -fx-border-color: " + (dark ? "rgba(245, 158, 11, 0.35)" : "#fde68a") + "; -fx-border-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0," + (dark ? "0.3" : "0.03") + "), 4, 0, 0, 1);");

                Label statusBadge = new Label("⏸ PAUSED");
                statusBadge.setStyle("-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #f59e0b; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 3px 8px; -fx-background-radius: 4px;");

                String cleanName = formatSourcePathForDisplay(s.sourceFilePath());
                VBox sessionDetails = new VBox(2);
                Label lblEmail = new Label(cleanName);
                lblEmail.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12.5));
                lblEmail.setStyle("-fx-text-fill: " + (dark ? "#f8fafc" : "#0f172a") + ";");

                Label lblSub = new Label("Target Format: " + s.format() + "  •  Interrupted session available to resume");
                lblSub.setFont(Font.font("Segoe UI", 11));
                lblSub.setStyle("-fx-text-fill: " + (dark ? "#94a3b8" : "#64748b") + ";");
                sessionDetails.getChildren().addAll(lblEmail, lblSub);

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                MFXButton btnResume = new MFXButton("Resume Migration");
                btnResume.setStyle("-fx-background-color: linear-gradient(to right, #0ea5e9, #0284c7); -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-padding: 6px 14px;");
                btnResume.setOnAction(e -> mainController.resumeMigrationSession(s));

                Button btnDismiss = new Button("Dismiss");
                btnDismiss.setStyle("-fx-background-color: " + (dark ? "rgba(244, 63, 94, 0.12)" : "#fee2e2") + "; -fx-text-fill: #f43f5e; -fx-font-weight: bold; -fx-font-size: 11px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-padding: 5px 12px;");
                btnDismiss.setOnAction(e -> {
                    SettingsManager.deleteMigrationSession(s.sourceFilePath(), s.format(), s.destinationPath());
                    refreshIncompleteMigrations();
                });

                row.getChildren().addAll(statusBadge, sessionDetails, sp, btnResume, btnDismiss);
                incompleteCardsContainer.getChildren().add(row);
            }
        }
    }

    private void refreshHistoryMigrations() {
        historyCardsContainer.getChildren().clear();
        List<MigrationSession> completedList = SettingsManager.getAllMigrationSessions().stream()
                .filter(s -> "COMPLETED".equalsIgnoreCase(s.status()))
                .toList();

        boolean dark = mainController != null && mainController.isDarkMode();

        if (completedList.isEmpty()) {
            HBox emptyBox = new HBox(8);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(18));
            Label emptyLbl = new Label("No past conversion history recorded yet.");
            emptyLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11.5px; -fx-font-style: italic;");
            emptyBox.getChildren().add(emptyLbl);
            historyCardsContainer.getChildren().add(emptyBox);
        } else {
            for (MigrationSession s : completedList) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(10, 14, 10, 14));
                row.setStyle("-fx-background-color: " + (dark ? "#111c30" : "#ffffff") + "; -fx-background-radius: 8px; -fx-border-color: " + (dark ? "rgba(148, 163, 184, 0.16)" : "#e2e8f0") + "; -fx-border-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0," + (dark ? "0.3" : "0.03") + "), 4, 0, 0, 1);");

                Label statusBadge = new Label("✓ COMPLETED");
                statusBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.12); -fx-text-fill: #10b981; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 3px 8px; -fx-background-radius: 4px;");

                String cleanName = formatSourcePathForDisplay(s.sourceFilePath());
                VBox sessionDetails = new VBox(2);
                Label lblEmail = new Label(cleanName);
                lblEmail.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12.5));
                lblEmail.setStyle("-fx-text-fill: " + (dark ? "#f8fafc" : "#0f172a") + ";");

                Label lblSub = new Label("Format: " + s.format() + "  •  Migrated Messages: " + s.totalMessages());
                lblSub.setFont(Font.font("Segoe UI", 11));
                lblSub.setStyle("-fx-text-fill: " + (dark ? "#94a3b8" : "#64748b") + ";");
                sessionDetails.getChildren().addAll(lblEmail, lblSub);

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                row.getChildren().addAll(statusBadge, sessionDetails, sp);
                historyCardsContainer.getChildren().add(row);
            }
        }
    }
}