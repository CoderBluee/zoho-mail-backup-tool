package com.pstconverter.view.destination;

import com.pstconverter.controller.MainController;
import com.pstconverter.util.MaterialIcons;
import com.pstconverter.util.SettingsManager;
import com.pstconverter.core.destination.EmailDestinationConfig;
import com.pstconverter.core.destination.DestinationAdapter;
import com.pstconverter.core.destination.DestinationAdapterFactory;
import com.pstconverter.imap.ImapAuthHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.enums.FloatMode;

public class CloudDestinationPanel extends VBox {
    private VBox loginCard = new VBox();
    private VBox connectedCard = new VBox();
    private boolean isUserMappingConfigured = false;
    private boolean isConnectionTested = false;
    private ComboBox<String> cmbSavedAccounts = new ComboBox<>();
    private MFXButton btnForgetSavedAccount = new MFXButton();
    private String activeAccountEmail;
    private javafx.scene.layout.VBox settingsPanel = this;
    private Label lblConnectedEmail = new Label();
    private boolean isUpdatingProgrammatically = false;
    private ComboBox<String> cmbServerPresets;
    private TextField tfAuthCode;
    private final MainController controller;
    private String selectedFormat = "PDF";
    private MFXCheckbox cbBulkMigration = new MFXCheckbox();
    private io.github.palexdev.materialfx.controls.MFXTextField tfBulkCsvPath = new io.github.palexdev.materialfx.controls.MFXTextField();
    private TextField tfThrottlingDelay = new TextField();
    private TextField tfThrottlingRate = new TextField("300");
    private TextField tfMaxRetries = new TextField("5");
    private TextField tfInitialBackoffMs = new TextField();
    
    public boolean isCloudFormat(String f) { return f.equals("Office 365") || f.equals("Gmail") || f.equals("IMAP Server") || f.equals("Yahoo Mail"); }


    private TextField tfCloudHost;
    private TextField tfCloudPort;
    private MFXCheckbox cbCloudSSL;
    private TextField tfCloudEmail;
    private PasswordField pfCloudPassword;
    private TextField tfCloudTargetFolder;
    private MFXButton btnTestConnection;
    private MFXButton btnLoginAccount;
    private Label lblTestConnectionFeedback;
    private MFXButton btnConnectedTestConnection;
    private Label lblConnectedTestConnectionFeedback;

    private ComboBox<String> cmbCloudAuthMode;
    private TextField tfClientId;
    private TextField tfCloudClientId;
    private PasswordField pfCloudClientSecret;
    private TextField tfCloudRedirectUri;
    private TextField tfCloudTenantId;
    private MFXButton btnOAuthAuthorize;
    private Label lblOAuthStatus;
    
    private TextField tfServiceAccountJsonPath;
    private RadioButton rbServiceKeyJson;
    private RadioButton rbServiceKeyP12;
    private TextField tfServiceAccountEmail;
    private TextField tfServiceAccountP12Path;
    private TextField tfImpersonatedTargetUserEmail;
    private final Map<String, String> userMappingMap = new HashMap<>();
    private Label lblUserMappingSummary;
    private MFXButton btnOpenUserMapping;
    private MFXCheckbox cbNativeItemRouting;
    private MFXCheckbox cbPreserveFolderHierarchy;
    private Button btnImapSingleMode;
    private Button btnImapBatchMode;
    private HBox imapModeSwitchBar;
    private VBox imapBox;
    private VBox batchImapBox;
    private HBox loginTestBox;

    public static class ImapBatchItem {
        private final String host;
        private final int port;
        private final String security;
        private final String username;
        private final String password;
        private boolean verified;
        private String status;

        public ImapBatchItem(String host, int port, String security, String username, String password) {
            this.host = host != null ? host.trim() : "";
            this.port = port;
            this.security = security != null ? security.trim() : "SSL/TLS";
            this.username = username != null ? username.trim() : "";
            this.password = password != null ? password.trim() : "";
            this.verified = false;
            this.status = "Pending";
        }

        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getSecurity() { return security; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public boolean isVerified() { return verified; }
        public void setVerified(boolean verified) { this.verified = verified; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getHostPort() { return host + ":" + port; }
    }

    private boolean isImapBatchMode = false;
    private final ObservableList<ImapBatchItem> imapBatchItemsList = FXCollections.observableArrayList();
    private final List<String> verifiedImapUsersList = new ArrayList<>();
    private TableView<ImapBatchItem> tblImapAccounts;
    private Label lblImapBatchStats;
    private ProgressBar pbImapVerify;

    private VBox savedAccountsBox;
    private ScrollPane accountsScroll;
    private Label lblCloudConnectedAccount;
    private HBox cloudConnectedCard;
    private HBox connectedSettingsRow;
    private HBox unconnectedFormRow;
    private MFXButton btnLogout;
    
    public void buildUI() {
        this.getChildren().add(new Label("Cloud Destination panel initialized"));
        renderCloudForm();
        // Since renderCloudForm adds to a local var or something, let's just make sure it compiles.
    }


    public CloudDestinationPanel(MainController controller) {
        this.controller = controller;
        this.setSpacing(10);
    }
    
    private String validationMessage = "";
    private Runnable validationListener;

    public void setValidationListener(Runnable listener) {
        this.validationListener = listener;
    }

    private void triggerValidation() {
        if (validationListener != null) {
            validationListener.run();
        } else {
            validateDestination();
        }
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    public void setSelectedFormat(String format) {
        this.selectedFormat = format;
        renderCloudForm();
    }
    
    private String sanitizeFilePath(String path) {
        if (path == null) return "";
        String p = path.trim();
        if (p.startsWith("\"") && p.endsWith("\"") && p.length() > 1) {
            p = p.substring(1, p.length() - 1).trim();
        }
        return p;
    }

    public boolean validateDestination() {
        return validateDestination(true);
    }

    public boolean validateDestination(boolean checkUserMapping) {
        validationMessage = "";
        
        String authMode = cmbCloudAuthMode != null ? cmbCloudAuthMode.getValue() : "App Password (IMAP)";
        boolean isOAuth = authMode != null && (authMode.contains("OAuth") || authMode.contains("Modern Auth")) && !authMode.contains("Application Permissions");
        boolean isGSuiteAdmin = authMode != null && authMode.contains("Service Account");
        boolean isO365AppAdmin = authMode != null && (authMode.contains("Admin Application Permissions") || authMode.contains("Office 365 Impersonation"));

        if (isGSuiteAdmin) {
            String adminEmail = tfCloudEmail != null ? tfCloudEmail.getText().trim() : "";
            if (adminEmail.isEmpty() || !adminEmail.contains("@")) {
                setValidationError("Valid Admin Email Address is required.");
                return false;
            }
            if (rbServiceKeyP12 != null && rbServiceKeyP12.isSelected()) {
                String saEmail = tfServiceAccountEmail != null ? tfServiceAccountEmail.getText().trim() : "";
                String p12Path = sanitizeFilePath(tfServiceAccountP12Path != null ? tfServiceAccountP12Path.getText() : "");
                if (saEmail.isEmpty()) {
                    setValidationError("Service Account Email is required for P12 key mode.");
                    return false;
                }
                if (!saEmail.contains("@")) {
                    setValidationError("Please enter a valid Service Account Email address.");
                    return false;
                }
                if (p12Path.isEmpty()) {
                    setValidationError("Service Account P12 Key File path is required.");
                    return false;
                }
                File p12File = new File(p12Path);
                if (!p12File.exists() || !p12File.isFile()) {
                    setValidationError("Service Account P12 Key File does not exist at: " + p12Path);
                    return false;
                }
            } else {
                String jsonPath = sanitizeFilePath(tfServiceAccountJsonPath != null ? tfServiceAccountJsonPath.getText() : "");
                if (jsonPath.isEmpty()) {
                    setValidationError("Service Account Key JSON File path is required.");
                    return false;
                }
                File jsonFile = new File(jsonPath);
                if (!jsonFile.exists() || !jsonFile.isFile()) {
                    setValidationError("Service Account Key JSON File does not exist at: " + jsonPath);
                    return false;
                }
            }
        } else if (isO365AppAdmin) {
            String clientId = tfCloudClientId != null ? tfCloudClientId.getText().trim() : "";
            String clientSecret = pfCloudClientSecret != null ? pfCloudClientSecret.getText().trim() : "";
            String tenantId = tfCloudTenantId != null ? tfCloudTenantId.getText().trim() : "";
            String adminEmail = tfCloudEmail != null ? tfCloudEmail.getText().trim() : "";
            if (clientId.isEmpty()) {
                setValidationError("Application (Client) ID is required for Office 365 Admin Impersonation.");
                return false;
            }
            if (clientSecret.isEmpty()) {
                setValidationError("Client Secret is required for Office 365 Admin Impersonation.");
                return false;
            }
            if (tenantId.isEmpty()) {
                setValidationError("Directory (Tenant) ID is required for Office 365 Admin Impersonation.");
                return false;
            }
            if (adminEmail.isEmpty() || !adminEmail.contains("@")) {
                setValidationError("Valid Admin Email Address is required.");
                return false;
            }
        } else if (isOAuth) {
            String clientId = tfCloudClientId != null ? tfCloudClientId.getText().trim() : "";
            String clientSecret = pfCloudClientSecret != null ? pfCloudClientSecret.getText().trim() : "";
            String redirectUri = tfCloudRedirectUri != null ? tfCloudRedirectUri.getText().trim() : "";

            if (clientId.isEmpty() || clientId.contains("dummy")) {
                if (selectedFormat.equals("Office 365")) {
                    var secrets = com.pstconverter.core.auth.MicrosoftOAuthService.loadClientSecrets();
                    clientId = secrets.clientId;
                    if (tfCloudClientId != null) tfCloudClientId.setText(secrets.clientId);
                    if (pfCloudClientSecret != null) pfCloudClientSecret.setText(secrets.clientSecret);
                    if (tfCloudRedirectUri != null) tfCloudRedirectUri.setText(secrets.redirectUri);
                } else if (selectedFormat.equals("Gmail")) {
                    var secrets = com.pstconverter.core.auth.GoogleOAuthService.loadClientSecrets();
                    clientId = secrets.clientId;
                    if (tfCloudClientId != null) tfCloudClientId.setText(secrets.clientId);
                    if (pfCloudClientSecret != null) pfCloudClientSecret.setText(secrets.clientSecret);
                    if (tfCloudRedirectUri != null) tfCloudRedirectUri.setText(secrets.redirectUri);
                }
            }

            String email = tfCloudEmail != null ? tfCloudEmail.getText().trim() : "";
            if (email.isEmpty()) {
                setValidationError("Email Address is required. Click 'Sign In' to authorize.");
                return false;
            }
            String rToken = com.pstconverter.core.auth.TokenManager.getRefreshToken(email);
            if (rToken == null || rToken.isEmpty()) {
                setValidationError("Account is not authorized. Click 'Sign In' to authorize.");
                return false;
            }
        } else if (isImapBatchMode && "IMAP Server".equalsIgnoreCase(selectedFormat)) {
            if (verifiedImapUsersList.isEmpty()) {
                setValidationError("Please import a CSV file and verify at least 1 IMAP account.");
                return false;
            }
        } else {
            String host = tfCloudHost != null ? sanitizeHost(tfCloudHost.getText()) : "";
            String port = tfCloudPort != null ? tfCloudPort.getText().trim() : "";
            String email = tfCloudEmail != null ? sanitizeEmail(tfCloudEmail.getText()) : "";
            String pass = pfCloudPassword != null ? sanitizePassword(pfCloudPassword.getText(), authMode) : "";

            if (host.isEmpty()) {
                setValidationError("IMAP host cannot be empty.");
                return false;
            }
            if (port.isEmpty()) {
                setValidationError("Port number cannot be empty.");
                return false;
            }
            if (email.isEmpty()) {
                setValidationError("Email address cannot be empty.");
                return false;
            }
            if (!email.contains("@") || !email.contains(".")) {
                setValidationError("Please provide a valid email address.");
                return false;
            }
            if (pass.isEmpty()) {
                setValidationError("App Password cannot be empty.");
                return false;
            }
        }

        // Target folder check
        String targetFolder = tfCloudTargetFolder != null ? tfCloudTargetFolder.getText().trim() : "";
        if (targetFolder.isEmpty()) {
            setValidationError("Target Folder Name cannot be empty.");
            return false;
        }

        // Admin mode User Mailbox Mapping check
        if (checkUserMapping) {
            boolean isAdminMode = isGSuiteAdmin || isO365AppAdmin || (authMode != null && authMode.contains("Admin Modern Auth"));
            if (isAdminMode) {
                if (!isUserMappingConfigured && userMappingMap.isEmpty()) {
                    setValidationError("User Mailbox Mapping is required for Admin mode. Click 'Configure Mailbox Mapping' to proceed.");
                    return false;
                }
            }
        }

        // Bulk check
        if (cbBulkMigration != null && cbBulkMigration.isSelected()) {
            String csvPath = tfBulkCsvPath != null ? tfBulkCsvPath.getText().trim() : "";
            if (csvPath.isEmpty()) {
                setValidationError("Bulk Accounts CSV File path must be specified.");
                return false;
            }
            File csvFile = new File(csvPath);
            if (!csvFile.exists() || !csvFile.isFile()) {
                setValidationError("Bulk Accounts CSV File does not exist.");
                return false;
            }
        }

        return true;
    }
    public VBox getUI() {
        if (this.getChildren().isEmpty()) {
            buildUI();
        }
        return this;
    }
    
    
    public void styleModernTextField(MFXTextField field) {
        field.setStyle("-fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 8; -fx-font-size: 13px; -fx-background-color: #f8fafc;");
        field.focusedProperty().addListener((o, ov, nv) -> {
            if (nv) field.setStyle("-fx-border-color: #0ea5e9; -fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 7; -fx-font-size: 13px; -fx-background-color: #ffffff; -fx-effect: dropshadow(three-pass-box, rgba(14,165,233,0.25), 5, 0, 0, 0);");
            else field.setStyle("-fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8; -fx-font-size: 13px; -fx-background-color: #f8fafc;");
        });
    }

    public void styleModernTextField(TextField tf) {
        tf.getStyleClass().add("standard-filter-textfield");
    }

    public void styleModernCheckBox(MFXCheckbox cb) {
        cb.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155;");
    }
    
    public void styleModernComboBox(ComboBox<?> cb) {
        // generic styling
    }


    private void refreshSavedAccounts() {
        if (cmbSavedAccounts == null) return;
        
        List<String> items = new java.util.ArrayList<>();
        items.add("[New Account]");
        
        List<com.pstconverter.util.SettingsManager.SavedAccount> saved = com.pstconverter.util.SettingsManager.getSavedAccounts();
        for (com.pstconverter.util.SettingsManager.SavedAccount acc : saved) {
            if (acc.format() != null && acc.format().equalsIgnoreCase(selectedFormat)) {
                items.add(acc.email());
            }
        }
        
        cmbSavedAccounts.setItems(FXCollections.observableArrayList(items));
        if (items.contains(activeAccountEmail)) {
            cmbSavedAccounts.setValue(activeAccountEmail);
        } else {
            activeAccountEmail = "[New Account]";
            cmbSavedAccounts.setValue("[New Account]");
        }
    }

    private void selectSavedAccount(String email) {
        System.out.println("Selecting account from dropdown: " + email);
        activeAccountEmail = email;
        boolean wasUpdating = isUpdatingProgrammatically;
        isUpdatingProgrammatically = true;
        
        // Reset manual folder name edits on account change if template is dynamic
        if (!wasUpdating) {
            String activeTemplate = SettingsManager.getSetting("output_folder_template", "Mailbox_Export_[Timestamp]");
            if ("Custom Name".equals(activeTemplate)) {
                activeTemplate = "Mailbox_Export_[Timestamp]";
            }
            controller.setFolderNameManuallyEdited(false);
            controller.setCustomExportFolderName(null);
            controller.refreshFolderNameSuggestion();
            if (tfCloudTargetFolder != null) {
                generateFolderNameFromTemplate(activeTemplate);
            }
        }
        
        try {
            if (email == null || email.equals("[New Account]")) {
                isConnectionTested = false;
                if (tfCloudEmail != null) tfCloudEmail.setText("");
                if (pfCloudPassword != null) pfCloudPassword.setText("");
                
                String hostVal = "";
                String portVal = "993";
                boolean sslVal = true;
                if (selectedFormat.equals("Office 365")) {
                    hostVal = "outlook.office365.com";
                } else if (selectedFormat.equals("Gmail")) {
                    hostVal = "imap.gmail.com";
                } else if (selectedFormat.equals("Yahoo Mail")) {
                    hostVal = "imap.mail.yahoo.com";
                }
                if (tfCloudHost != null) tfCloudHost.setText(hostVal);
                if (tfCloudPort != null) tfCloudPort.setText(portVal);
                if (cbCloudSSL != null) cbCloudSSL.setSelected(sslVal);
                
                if (lblOAuthStatus != null) {
                    lblOAuthStatus.setText("Ready to authorize");
                    lblOAuthStatus.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
                }
            } else {
                isConnectionTested = true;
                List<com.pstconverter.util.SettingsManager.SavedAccount> saved = com.pstconverter.util.SettingsManager.getSavedAccounts();
                for (com.pstconverter.util.SettingsManager.SavedAccount acc : saved) {
                    if (acc.email().equalsIgnoreCase(email) && acc.format().equalsIgnoreCase(selectedFormat)) {
                        if (tfCloudEmail != null) tfCloudEmail.setText(acc.email());
                        if (tfCloudHost != null) tfCloudHost.setText(acc.host() != null ? acc.host() : "");
                        if (tfCloudPort != null) tfCloudPort.setText(String.valueOf(acc.port()));
                        if (cbCloudSSL != null) cbCloudSSL.setSelected(acc.ssl());
                        if (cmbCloudAuthMode != null && acc.authMode() != null) {
                            cmbCloudAuthMode.setValue(acc.authMode());
                        }
                        if (acc.authMode() != null && acc.authMode().contains("Service Account")) {
                            if (acc.password() != null) {
                                String pass = acc.password();
                                if (pass.startsWith("P12|")) {
                                    String[] parts = pass.split("\\|", 3);
                                    if (parts.length >= 3) {
                                        if (rbServiceKeyP12 != null) rbServiceKeyP12.setSelected(true);
                                        if (tfServiceAccountEmail != null) tfServiceAccountEmail.setText(parts[1]);
                                        if (tfServiceAccountP12Path != null) tfServiceAccountP12Path.setText(parts[2]);
                                    }
                                } else {
                                    if (rbServiceKeyJson != null) rbServiceKeyJson.setSelected(true);
                                    if (tfServiceAccountJsonPath != null) tfServiceAccountJsonPath.setText(pass);
                                }
                            }
                        } else if (acc.authMode() != null && (acc.authMode().contains("Admin Application Permissions") || acc.authMode().contains("Office 365 Impersonation"))) {
                            if (pfCloudClientSecret != null && acc.password() != null) {
                                pfCloudClientSecret.setText(acc.password());
                            }
                        } else {
                            if (pfCloudPassword != null) pfCloudPassword.setText(acc.password() != null ? acc.password() : "");
                        }
                        if (lblOAuthStatus != null) {
                            boolean hasToken = com.pstconverter.core.auth.TokenManager.getRefreshToken(acc.email()) != null;
                            if (hasToken) {
                                lblOAuthStatus.setText("Authorized");
                                lblOAuthStatus.setStyle("-fx-font-size: 13px; -fx-text-fill: #34d399; -fx-font-weight: bold;");
                            } else {
                                lblOAuthStatus.setText("Ready to authorize");
                                lblOAuthStatus.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
                            }
                        }
                        break;
                    }
                }
            }
        } finally {
            isUpdatingProgrammatically = false;
        }
        updateCloudViewState();
        triggerValidation();
    }

    private void handleDeleteActiveAccount() {
        String email = activeAccountEmail;
        if (email != null && !email.equals("[New Account]")) {
            System.out.println("Deleting saved account: " + email);
            com.pstconverter.util.SettingsManager.deleteSavedAccount(email);
            com.pstconverter.core.auth.TokenManager.clearTokens(email);
            activeAccountEmail = "[New Account]";
            refreshSavedAccounts();
            selectSavedAccount("[New Account]");
        }
    }

    private void updateCloudViewState() {
        if (settingsPanel == null) return;
        
        if (lblConnectedTestConnectionFeedback != null) {
            lblConnectedTestConnectionFeedback.setText("");
            lblConnectedTestConnectionFeedback.setVisible(false);
            lblConnectedTestConnectionFeedback.setManaged(false);
        }
        if (lblTestConnectionFeedback != null) {
            lblTestConnectionFeedback.setText("");
            lblTestConnectionFeedback.setVisible(false);
            lblTestConnectionFeedback.setManaged(false);
        }

        String email = activeAccountEmail;
        boolean isLoggedIn = false;
        
        if (email != null && !email.equals("[New Account]") && !email.isEmpty()) {
            String mode = cmbCloudAuthMode != null ? cmbCloudAuthMode.getValue() : "";
            if (mode != null && mode.contains("OAuth") && !mode.contains("Application Permissions")) {
                String rToken = com.pstconverter.core.auth.TokenManager.getRefreshToken(email);
                isLoggedIn = (rToken != null && !rToken.isEmpty());
            } else if (mode != null && mode.contains("Service Account")) {
                String serviceConfig = "";
                if (rbServiceKeyP12 != null && rbServiceKeyP12.isSelected()) {
                    String saEmail = tfServiceAccountEmail != null ? tfServiceAccountEmail.getText().trim() : "";
                    String p12Path = sanitizeFilePath(tfServiceAccountP12Path != null ? tfServiceAccountP12Path.getText() : "");
                    if (!saEmail.isEmpty() && !p12Path.isEmpty()) {
                        serviceConfig = "P12|" + saEmail + "|" + p12Path;
                    }
                } else {
                    serviceConfig = sanitizeFilePath(tfServiceAccountJsonPath != null ? tfServiceAccountJsonPath.getText() : "");
                }
                if (serviceConfig.isEmpty()) {
                    List<com.pstconverter.util.SettingsManager.SavedAccount> saved = com.pstconverter.util.SettingsManager.getSavedAccounts();
                    for (com.pstconverter.util.SettingsManager.SavedAccount acc : saved) {
                        if (acc.email().equalsIgnoreCase(email) && acc.format().equalsIgnoreCase(selectedFormat)) {
                            if (acc.password() != null && !acc.password().isEmpty()) {
                                serviceConfig = acc.password();
                                if (serviceConfig.startsWith("P12|")) {
                                    String[] parts = serviceConfig.split("\\|", 3);
                                    if (parts.length >= 3) {
                                        if (rbServiceKeyP12 != null) rbServiceKeyP12.setSelected(true);
                                        if (tfServiceAccountEmail != null) tfServiceAccountEmail.setText(parts[1]);
                                        if (tfServiceAccountP12Path != null) tfServiceAccountP12Path.setText(parts[2]);
                                    }
                                } else {
                                    if (rbServiceKeyJson != null) rbServiceKeyJson.setSelected(true);
                                    String cleanJson = serviceConfig.startsWith("JSON|") ? serviceConfig.substring(5) : serviceConfig;
                                    if (tfServiceAccountJsonPath != null) tfServiceAccountJsonPath.setText(cleanJson);
                                }
                            }
                            break;
                        }
                    }
                }
                if (serviceConfig.startsWith("P12|")) {
                    String[] parts = serviceConfig.split("\\|", 3);
                    String p12Path = parts.length >= 3 ? sanitizeFilePath(parts[2]) : "";
                    isLoggedIn = (parts.length >= 3 && !parts[1].isEmpty() && new File(p12Path).exists());
                } else {
                    String jsonPath = sanitizeFilePath(serviceConfig.startsWith("JSON|") ? serviceConfig.substring(5) : serviceConfig);
                    isLoggedIn = !jsonPath.isEmpty() && new File(jsonPath).exists();
                }
            } else if (mode != null && (mode.contains("Admin Application Permissions") || mode.contains("Office 365 Impersonation"))) {
                String clientId = tfCloudClientId != null ? tfCloudClientId.getText().trim() : "";
                String clientSecret = pfCloudClientSecret != null ? pfCloudClientSecret.getText().trim() : "";
                String tenantId = tfCloudTenantId != null ? tfCloudTenantId.getText().trim() : "";
                if (clientSecret.isEmpty()) {
                    List<com.pstconverter.util.SettingsManager.SavedAccount> saved = com.pstconverter.util.SettingsManager.getSavedAccounts();
                    for (com.pstconverter.util.SettingsManager.SavedAccount acc : saved) {
                        if (acc.email().equalsIgnoreCase(email) && acc.format().equalsIgnoreCase(selectedFormat)) {
                            if (acc.password() != null && !acc.password().isEmpty()) {
                                clientSecret = acc.password();
                                if (pfCloudClientSecret != null) pfCloudClientSecret.setText(clientSecret);
                            }
                            break;
                        }
                    }
                }
                isLoggedIn = !clientId.isEmpty() && !clientSecret.isEmpty() && !tenantId.isEmpty();
            } else {
                List<com.pstconverter.util.SettingsManager.SavedAccount> saved = com.pstconverter.util.SettingsManager.getSavedAccounts();
                for (com.pstconverter.util.SettingsManager.SavedAccount acc : saved) {
                    if (acc.email().equalsIgnoreCase(email) && acc.format().equalsIgnoreCase(selectedFormat)) {
                        isLoggedIn = (acc.password() != null && !acc.password().isEmpty());
                        break;
                    }
                }
            }
        }
        
        if (isLoggedIn) {
            if (loginCard != null) { loginCard.setVisible(false); loginCard.setManaged(false); }
            if (batchImapBox != null) { batchImapBox.setVisible(false); batchImapBox.setManaged(false); }
            if (imapModeSwitchBar != null) { imapModeSwitchBar.setVisible(false); imapModeSwitchBar.setManaged(false); }
            connectedCard.setVisible(true);
            connectedCard.setManaged(true);
            lblConnectedEmail.setText("Connected Account: " + email);
        } else {
            boolean isImapFormat = "IMAP Server".equalsIgnoreCase(selectedFormat);
            if (imapModeSwitchBar != null) {
                imapModeSwitchBar.setVisible(isImapFormat);
                imapModeSwitchBar.setManaged(isImapFormat);
            }
            if (isImapFormat && isImapBatchMode) {
                if (loginCard != null) { loginCard.setVisible(false); loginCard.setManaged(false); }
                if (batchImapBox != null) { batchImapBox.setVisible(true); batchImapBox.setManaged(true); }
            } else {
                if (loginCard != null) { loginCard.setVisible(true); loginCard.setManaged(true); }
                if (batchImapBox != null) { batchImapBox.setVisible(false); batchImapBox.setManaged(false); }
            }
            connectedCard.setVisible(false);
            connectedCard.setManaged(false);
            if (email != null && !email.equals("[New Account]") && tfCloudEmail != null) {
                tfCloudEmail.setText(email);
            }
        }
        triggerValidation();
    }

    public void resetCloudAccountState() {
        activeAccountEmail = "[New Account]";
        isConnectionTested = false;
        isUserMappingConfigured = false;
        if (tfCloudEmail != null) tfCloudEmail.setText("");
        if (pfCloudPassword != null) pfCloudPassword.setText("");
        if (tfServiceAccountJsonPath != null) tfServiceAccountJsonPath.setText("");
        if (pfCloudClientSecret != null) pfCloudClientSecret.setText("");
        if (tfCloudClientId != null) tfCloudClientId.setText("");
        if (tfCloudTenantId != null) tfCloudTenantId.setText("");
        if (tfImpersonatedTargetUserEmail != null) tfImpersonatedTargetUserEmail.setText("");
        userMappingMap.clear();
        if (lblUserMappingSummary != null) {
            lblUserMappingSummary.setText("No users mapped. (Default target: admin account)");
        }
        if (cmbSavedAccounts != null) {
            cmbSavedAccounts.setValue("[New Account]");
        }
        updateCloudViewState();
    }

    private void handleLogOut() {
        resetCloudAccountState();
    }

    private void renderCloudForm() {
        settingsPanel.getChildren().clear();
        settingsPanel.setAlignment(Pos.TOP_CENTER);
        settingsPanel.setSpacing(12);

        HBox headerRow = new HBox(16);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(4, 8, 4, 8));
        headerRow.setStyle("-fx-background-color: transparent;");

        Label titleLabel = new Label(selectedFormat + " Integration");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        HBox accountSelectorBox = new HBox(8);
        accountSelectorBox.setAlignment(Pos.CENTER_RIGHT);
        Label lblAccount = new Label("Account:");
        lblAccount.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        cmbSavedAccounts = new ComboBox<>();
        cmbSavedAccounts.setPrefWidth(200);
        styleModernComboBox(cmbSavedAccounts);
        cmbSavedAccounts.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null && !nv.equals(activeAccountEmail)) {
                selectSavedAccount(nv);
            }
        });

        btnForgetSavedAccount = new MFXButton("");
        btnForgetSavedAccount.setGraphic(MaterialIcons.withText(MaterialIcons.DELETE, ""));
        btnForgetSavedAccount.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand; -fx-font-size: 16px; -fx-padding: 4px;");
        btnForgetSavedAccount.setOnAction(e -> handleDeleteActiveAccount());

        accountSelectorBox.getChildren().addAll(lblAccount, cmbSavedAccounts, btnForgetSavedAccount);
        headerRow.getChildren().addAll(titleLabel, accountSelectorBox);
        settingsPanel.getChildren().addAll(headerRow, new Separator());

        // IMAP Mode Switch Bar (Modern Pill Segmented Tabs)
        imapModeSwitchBar = new HBox(14);
        imapModeSwitchBar.setAlignment(Pos.CENTER);
        imapModeSwitchBar.setPadding(new Insets(8, 0, 14, 0));

        Label lblImapIcon = MaterialIcons.icon(MaterialIcons.TUNE, 16);
        lblImapIcon.setStyle("-fx-text-fill: #475569;");

        Label lblImapMode = new Label("IMAP Migration Mode:");
        lblImapMode.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #1e293b;");

        HBox modeTitleBox = new HBox(6, lblImapIcon, lblImapMode);
        modeTitleBox.setAlignment(Pos.CENTER);

        btnImapSingleMode = new Button("Single Account Mode");
        btnImapBatchMode = new Button("Batch CSV Mode");

        btnImapSingleMode.setOnAction(e -> switchImapMode(false));
        btnImapBatchMode.setOnAction(e -> switchImapMode(true));

        HBox toggleSegment = new HBox(4, btnImapSingleMode, btnImapBatchMode);
        toggleSegment.setAlignment(Pos.CENTER);
        toggleSegment.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 20px; -fx-padding: 4px; -fx-border-color: #e2e8f0; -fx-border-radius: 20px;");

        imapModeSwitchBar.getChildren().addAll(modeTitleBox, toggleSegment);

        boolean isImapFormat = "IMAP Server".equalsIgnoreCase(selectedFormat);
        imapModeSwitchBar.setVisible(isImapFormat);
        imapModeSwitchBar.setManaged(isImapFormat);

        settingsPanel.getChildren().add(imapModeSwitchBar);

        loginCard = new VBox(12);
        loginCard.setAlignment(Pos.TOP_CENTER);
        loginCard.setStyle("-fx-background-color: white; -fx-background-radius: 12px; -fx-border-radius: 12px; -fx-border-color: #e2e8f0; -fx-padding: 16px;");
        loginCard.setMaxWidth(600);

        List<String> authModes = new ArrayList<>();
        if (selectedFormat.equals("Gmail")) {
            authModes.add("OAuth 2.0 (Modern Auth / Gmail API)");
            authModes.add("Service Account (GSuite Admin Impersonation)");
            authModes.add("App Password (IMAP)");
        } else if (selectedFormat.equals("Office 365")) {
            authModes.add("OAuth 2.0 (Modern Auth / Microsoft Graph API)");
            authModes.add("Admin Modern Auth (Office 365 Admin OAuth)");
            authModes.add("Admin Application Permissions (Office 365 Impersonation)");
            authModes.add("App Password (IMAP)");
        } else {
            authModes.add("App Password (IMAP)");
        }
        cmbCloudAuthMode = new ComboBox<>(FXCollections.observableArrayList(authModes));
        styleModernComboBox(cmbCloudAuthMode);
        cmbCloudAuthMode.setMaxWidth(Double.MAX_VALUE);
        if (!authModes.isEmpty()) {
            cmbCloudAuthMode.setValue(authModes.get(0));
        }
        cmbCloudAuthMode.valueProperty().addListener((o, ov, nv) -> {
            if (isUpdatingProgrammatically) return;
            isConnectionTested = false;
            if (nv != null && (nv.contains("Admin Application Permissions") || nv.contains("Office 365 Impersonation"))) {
                if (tfCloudTenantId != null) tfCloudTenantId.setText("");
                if (tfCloudClientId != null) tfCloudClientId.setText("");
                if (pfCloudClientSecret != null) pfCloudClientSecret.setText("");
            } else if (nv != null && (nv.contains("OAuth") || nv.contains("Modern Auth"))) {
                if (selectedFormat.equals("Gmail")) {
                    var secrets = com.pstconverter.core.auth.GoogleOAuthService.loadClientSecrets();
                    if (tfCloudClientId != null) tfCloudClientId.setText(secrets.clientId);
                    if (pfCloudClientSecret != null) pfCloudClientSecret.setText(secrets.clientSecret);
                    if (tfCloudRedirectUri != null) tfCloudRedirectUri.setText(secrets.redirectUri);
                } else {
                    var secrets = com.pstconverter.core.auth.MicrosoftOAuthService.loadClientSecrets();
                    if (tfCloudClientId != null) tfCloudClientId.setText(secrets.clientId);
                    if (pfCloudClientSecret != null) pfCloudClientSecret.setText(secrets.clientSecret);
                    if (tfCloudRedirectUri != null) tfCloudRedirectUri.setText(secrets.redirectUri);
                    if (tfCloudTenantId != null) tfCloudTenantId.setText(secrets.tenantId);
                }
            }

            if (btnOAuthAuthorize != null && nv != null) {
                if (nv.contains("Admin Modern Auth")) {
                    btnOAuthAuthorize.setText("Sign in as Office 365 Admin");
                } else {
                    btnOAuthAuthorize.setText("Sign in with " + selectedFormat);
                }
            }
            triggerValidation();
        });

        VBox authModeBox = new VBox(4);
        Label lblAuthMode = new Label("Authentication Method");
        lblAuthMode.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        authModeBox.getChildren().addAll(lblAuthMode, cmbCloudAuthMode);

        boolean hasMultipleAuth = selectedFormat.equals("Gmail") || selectedFormat.equals("Office 365");
        authModeBox.setVisible(hasMultipleAuth);
        authModeBox.setManaged(hasMultipleAuth);
        loginCard.getChildren().add(authModeBox);

        imapBox = new VBox(10);
        imapBox.setStyle("-fx-background-color: transparent;");

        // Popular Server Presets Dropdown
        VBox presetBox = new VBox(4);
        Label lblPreset = new Label("Popular Server Presets");
        lblPreset.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        cmbServerPresets = new ComboBox<>(FXCollections.observableArrayList(
            "Custom IMAP...",
            "Gmail (imap.gmail.com)",
            "Yahoo Mail (imap.mail.yahoo.com)",
            "Outlook / Hotmail (outlook.office365.com)",
            "Office 365 (outlook.office365.com)",
            "iCloud Mail (imap.mail.me.com)",
            "Zoho Mail (imap.zoho.com)",
            "AOL Mail (imap.aol.com)"
        ));
        styleModernComboBox(cmbServerPresets);
        cmbServerPresets.setMaxWidth(Double.MAX_VALUE);
        cmbServerPresets.setValue("Custom IMAP...");
        presetBox.getChildren().addAll(lblPreset, cmbServerPresets);

        HBox hostPortRow = new HBox(12);
        VBox hostBox = new VBox(4);
        Label lblHost = new Label("IMAP Host Server");
        lblHost.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        tfCloudHost = new TextField();
        styleModernTextField(tfCloudHost);
        tfCloudHost.textProperty().addListener((o, ov, nv) -> {
            if (isUpdatingProgrammatically) return;
            isConnectionTested = false;
            triggerValidation();
        });
        HBox.setHgrow(hostBox, Priority.ALWAYS);
        hostBox.getChildren().addAll(lblHost, tfCloudHost);

        VBox portBox = new VBox(4);
        Label lblPort = new Label("Port");
        lblPort.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        tfCloudPort = new TextField();
        styleModernTextField(tfCloudPort);
        tfCloudPort.setPrefWidth(80);
        tfCloudPort.textProperty().addListener((o, ov, nv) -> {
            if (isUpdatingProgrammatically) return;
            isConnectionTested = false;
            triggerValidation();
        });
        portBox.getChildren().addAll(lblPort, tfCloudPort);
        hostPortRow.getChildren().addAll(hostBox, portBox);

        cbCloudSSL = new MFXCheckbox("Enable SSL / TLS connection");
        styleModernCheckBox(cbCloudSSL);
        cbCloudSSL.setSelected(true);
        cbCloudSSL.selectedProperty().addListener((o, ov, nv) -> {
            if (isUpdatingProgrammatically) return;
            isConnectionTested = false;
            triggerValidation();
        });

        // Event handler for presets to auto-fill details
        cmbServerPresets.valueProperty().addListener((o, ov, nv) -> {
            if (isUpdatingProgrammatically) return;
            if (nv == null) return;
            
            isUpdatingProgrammatically = true;
            try {
                if (nv.startsWith("Gmail")) {
                    tfCloudHost.setText("imap.gmail.com");
                    tfCloudPort.setText("993");
                    cbCloudSSL.setSelected(true);
                } else if (nv.startsWith("Yahoo")) {
                    tfCloudHost.setText("imap.mail.yahoo.com");
                    tfCloudPort.setText("993");
                    cbCloudSSL.setSelected(true);
                } else if (nv.startsWith("Outlook") || nv.startsWith("Office 365")) {
                    tfCloudHost.setText("outlook.office365.com");
                    tfCloudPort.setText("993");
                    cbCloudSSL.setSelected(true);
                } else if (nv.startsWith("iCloud")) {
                    tfCloudHost.setText("imap.mail.me.com");
                    tfCloudPort.setText("993");
                    cbCloudSSL.setSelected(true);
                } else if (nv.startsWith("Zoho")) {
                    tfCloudHost.setText("imap.zoho.com");
                    tfCloudPort.setText("993");
                    cbCloudSSL.setSelected(true);
                } else if (nv.startsWith("AOL")) {
                    tfCloudHost.setText("imap.aol.com");
                    tfCloudPort.setText("993");
                    cbCloudSSL.setSelected(true);
                }
            } finally {
                isUpdatingProgrammatically = false;
            }
            isConnectionTested = false;
            triggerValidation();
        });

        HBox emailPassRow = new HBox(12);
        VBox imapEmailBox = new VBox(4);
        Label lblImapEmail = new Label("Email Address");
        lblImapEmail.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        TextField tfImapEmail = new TextField();
        styleModernTextField(tfImapEmail);
        HBox.setHgrow(imapEmailBox, Priority.ALWAYS);
        imapEmailBox.getChildren().addAll(lblImapEmail, tfImapEmail);

        VBox passBox = new VBox(4);
        Label lblPassword = new Label("App Password");
        lblPassword.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        pfCloudPassword = new PasswordField();
        pfCloudPassword.setPromptText("16-character App Password");
        styleModernTextField(pfCloudPassword);
        pfCloudPassword.textProperty().addListener((o, ov, nv) -> {
            if (isUpdatingProgrammatically) return;
            isConnectionTested = false;
            triggerValidation();
        });
        HBox.setHgrow(passBox, Priority.ALWAYS);
        passBox.getChildren().addAll(lblPassword, pfCloudPassword);
        emailPassRow.getChildren().addAll(imapEmailBox, passBox);

        boolean isGenericImap = selectedFormat.equals("IMAP Server");
        presetBox.setVisible(isGenericImap);
        presetBox.setManaged(isGenericImap);
        hostPortRow.setVisible(isGenericImap);
        hostPortRow.setManaged(isGenericImap);
        cbCloudSSL.setVisible(isGenericImap);
        cbCloudSSL.setManaged(isGenericImap);

        // Pre-fill default values for specific formats
        isUpdatingProgrammatically = true;
        try {
            if (selectedFormat.equals("Gmail")) {
                tfCloudHost.setText("imap.gmail.com");
                tfCloudPort.setText("993");
                cbCloudSSL.setSelected(true);
            } else if (selectedFormat.equals("Yahoo Mail")) {
                tfCloudHost.setText("imap.mail.yahoo.com");
                tfCloudPort.setText("993");
                cbCloudSSL.setSelected(true);
            } else if (selectedFormat.equals("Office 365")) {
                tfCloudHost.setText("outlook.office365.com");
                tfCloudPort.setText("993");
                cbCloudSSL.setSelected(true);
            }
        } finally {
            isUpdatingProgrammatically = false;
        }

        imapBox.getChildren().addAll(emailPassRow, presetBox, hostPortRow, cbCloudSSL);

        // IMAP Batch CSV Card (Standalone Card)
        batchImapBox = new VBox(12);
        batchImapBox.setAlignment(Pos.TOP_CENTER);
        batchImapBox.setStyle("-fx-background-color: white; -fx-background-radius: 12px; -fx-border-radius: 12px; -fx-border-color: #e2e8f0; -fx-padding: 16px;");
        batchImapBox.setMaxWidth(600);

        Label lblBatchTitle = new Label("IMAP Multi-Account Batch Import");
        lblBatchTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label lblBatchSub = new Label("Upload a CSV file containing IMAP account details (Host, Port, Security, Username, Password) to test connections, map PST files, and migrate in batch.");
        lblBatchSub.setWrapText(true);
        lblBatchSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        HBox batchButtonsBar = new HBox(10);
        batchButtonsBar.setAlignment(Pos.CENTER);

        MFXButton btnImportCsv = new MFXButton("Import CSV...");
        btnImportCsv.setStyle("-fx-background-color: linear-gradient(to right, #0ea5e9, #0284c7); -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 7px 14px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnImportCsv.setOnAction(e -> handleImportImapCsv());

        MFXButton btnSampleCsv = new MFXButton("Sample CSV");
        btnSampleCsv.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 7px 14px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnSampleCsv.setOnAction(e -> handleSampleImapCsv());

        batchButtonsBar.getChildren().addAll(btnImportCsv, btnSampleCsv);

        pbImapVerify = new ProgressBar();
        pbImapVerify.setPrefWidth(220);
        pbImapVerify.setVisible(false);
        pbImapVerify.setManaged(false);

        lblImapBatchStats = new Label("Total: 0 | Verified: 0 | Failed: 0");
        lblImapBatchStats.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #334155;");

        HBox statsRow = new HBox(12, pbImapVerify, lblImapBatchStats);
        statsRow.setAlignment(Pos.CENTER);

        tblImapAccounts = new TableView<>(imapBatchItemsList);
        tblImapAccounts.setPrefHeight(180);
        tblImapAccounts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ImapBatchItem, String> colUser = new TableColumn<>("Username / Email");
        colUser.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getUsername()));

        TableColumn<ImapBatchItem, String> colHost = new TableColumn<>("Host : Port");
        colHost.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getHostPort()));

        TableColumn<ImapBatchItem, String> colSec = new TableColumn<>("Security");
        colSec.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getSecurity()));

        TableColumn<ImapBatchItem, String> colStatus = new TableColumn<>("Connection Status");
        colStatus.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getStatus()));

        tblImapAccounts.getColumns().addAll(colUser, colHost, colSec, colStatus);

        MFXButton btnContinueBatch = new MFXButton("Continue with Verified Accounts");
        btnContinueBatch.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-size: 12.5px; -fx-font-weight: bold; -fx-padding: 8px 16px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnContinueBatch.setOnAction(e -> handleContinueImapBatch());

        batchImapBox.getChildren().addAll(lblBatchTitle, lblBatchSub, batchButtonsBar, statsRow, tblImapAccounts, btnContinueBatch);

        loginCard.getChildren().add(imapBox);

        tfCloudEmail = new TextField();
        tfImapEmail.textProperty().bindBidirectional(tfCloudEmail.textProperty());
        tfImapEmail.textProperty().addListener((o, ov, nv) -> {
            if (isUpdatingProgrammatically) return;
            if (nv != null && nv.contains("@") && nv.contains(".")) {
                ImapAuthHelper.ProviderPreset preset = ImapAuthHelper.detectPreset(nv.trim());
                if (preset != null && !"Custom".equalsIgnoreCase(preset.name)) {
                    isUpdatingProgrammatically = true;
                    try {
                        tfCloudHost.setText(preset.host);
                        tfCloudPort.setText(String.valueOf(preset.port));
                        cbCloudSSL.setSelected(preset.ssl);
                        if (cmbServerPresets != null) {
                            for (String item : cmbServerPresets.getItems()) {
                                if (item.toLowerCase().contains(preset.name.toLowerCase())) {
                                    cmbServerPresets.setValue(item);
                                    break;
                                }
                            }
                        }
                    } finally {
                        isUpdatingProgrammatically = false;
                    }
                }
            }
            isConnectionTested = false;
            triggerValidation();
        });

        VBox oauthBox = new VBox(10);
        oauthBox.setStyle("-fx-background-color: transparent;");
        oauthBox.setAlignment(Pos.CENTER);

        VBox oauthEmailBox = new VBox(4);
        Label lblOauthEmail = new Label("Email Address to Authorize");
        lblOauthEmail.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569; -fx-alignment: center-left;");
        TextField tfOauthEmail = new TextField();
        styleModernTextField(tfOauthEmail);
        tfOauthEmail.setPromptText("Enter your email address...");
        tfOauthEmail.textProperty().bindBidirectional(tfCloudEmail.textProperty());
        tfOauthEmail.textProperty().addListener((o, ov, nv) -> {
            if (isUpdatingProgrammatically) return;
            isConnectionTested = false;
            triggerValidation();
        });
        oauthEmailBox.getChildren().addAll(lblOauthEmail, tfOauthEmail);

        btnOAuthAuthorize = new MFXButton("Sign in with " + selectedFormat);
        btnOAuthAuthorize.setStyle("-fx-background-color: linear-gradient(to right, #0ea5e9, #0284c7); -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8px 16px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnOAuthAuthorize.setOnAction(e -> handleOAuthAuthorizationFlow());

        lblOAuthStatus = new Label("Ready to authorize");
        lblOAuthStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

        oauthBox.getChildren().addAll(oauthEmailBox, btnOAuthAuthorize, lblOAuthStatus);
        loginCard.getChildren().add(oauthBox);

        // Admin Impersonation Controls (GSuite Service Account & O365 Admin App Permissions)
        VBox gsuiteAdminBox = new VBox(10);
        gsuiteAdminBox.setStyle("-fx-background-color: #f8fafc; -fx-padding: 12px; -fx-border-color: #cbd5e1; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        VBox gsuiteEmailBox = new VBox(4);
        Label lblGsuiteEmail = new Label("GSuite Admin Email Address");
        lblGsuiteEmail.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        TextField tfGsuiteEmail = new TextField();
        styleModernTextField(tfGsuiteEmail);
        tfGsuiteEmail.setPromptText("admin@yourdomain.com");
        tfGsuiteEmail.textProperty().bindBidirectional(tfCloudEmail.textProperty());
        gsuiteEmailBox.getChildren().addAll(lblGsuiteEmail, tfGsuiteEmail);

        // Key Type Radio Buttons (JSON vs P12)
        HBox keyTypeBox = new HBox(16);
        keyTypeBox.setAlignment(Pos.CENTER_LEFT);
        Label lblKeyType = new Label("Key Type:");
        lblKeyType.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");

        ToggleGroup tgKeyType = new ToggleGroup();
        rbServiceKeyJson = new RadioButton("JSON File");
        rbServiceKeyJson.setToggleGroup(tgKeyType);
        rbServiceKeyJson.setSelected(true);
        rbServiceKeyJson.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");

        rbServiceKeyP12 = new RadioButton("P12 Key File");
        rbServiceKeyP12.setToggleGroup(tgKeyType);
        rbServiceKeyP12.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");

        keyTypeBox.getChildren().addAll(lblKeyType, rbServiceKeyJson, rbServiceKeyP12);

        // JSON File Box
        VBox jsonBox = new VBox(4);
        Label lblJson = new Label("Service Account Private Key (.json)");
        lblJson.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        HBox jsonRow = new HBox(8);
        tfServiceAccountJsonPath = new TextField();
        styleModernTextField(tfServiceAccountJsonPath);
        HBox.setHgrow(tfServiceAccountJsonPath, Priority.ALWAYS);

        MFXButton btnBrowseJson = new MFXButton("Browse...");
        btnBrowseJson.setStyle("-fx-background-color: #0ea5e9; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6px 12px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnBrowseJson.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Select Service Account Key JSON");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
            java.io.File f = fc.showOpenDialog(getScene().getWindow());
            if (f != null) {
                tfServiceAccountJsonPath.setText(f.getAbsolutePath());
                triggerValidation();
            }
        });
        jsonRow.getChildren().addAll(tfServiceAccountJsonPath, btnBrowseJson);
        jsonBox.getChildren().addAll(lblJson, jsonRow);

        // Service Account Email Box (for P12 mode)
        VBox saEmailBox = new VBox(4);
        Label lblSaEmail = new Label("Service Account Email");
        lblSaEmail.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        tfServiceAccountEmail = new TextField();
        styleModernTextField(tfServiceAccountEmail);
        tfServiceAccountEmail.setPromptText("e.g. service-account@project-id.iam.gserviceaccount.com");
        saEmailBox.getChildren().addAll(lblSaEmail, tfServiceAccountEmail);

        // P12 Key File Box (for P12 mode)
        VBox p12Box = new VBox(4);
        Label lblP12 = new Label("Service Account Private Key (.p12)");
        lblP12.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        HBox p12Row = new HBox(8);
        tfServiceAccountP12Path = new TextField();
        styleModernTextField(tfServiceAccountP12Path);
        HBox.setHgrow(tfServiceAccountP12Path, Priority.ALWAYS);

        MFXButton btnBrowseP12 = new MFXButton("Browse...");
        btnBrowseP12.setStyle("-fx-background-color: #0ea5e9; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6px 12px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnBrowseP12.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Select Service Account Key P12");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("P12 Key Files (*.p12)", "*.p12"));
            java.io.File f = fc.showOpenDialog(getScene().getWindow());
            if (f != null) {
                tfServiceAccountP12Path.setText(f.getAbsolutePath());
                triggerValidation();
            }
        });
        p12Row.getChildren().addAll(tfServiceAccountP12Path, btnBrowseP12);
        p12Box.getChildren().addAll(lblP12, p12Row);

        // Dynamic visibility bindings
        jsonBox.visibleProperty().bind(rbServiceKeyJson.selectedProperty());
        jsonBox.managedProperty().bind(jsonBox.visibleProperty());

        saEmailBox.visibleProperty().bind(rbServiceKeyP12.selectedProperty());
        saEmailBox.managedProperty().bind(saEmailBox.visibleProperty());

        p12Box.visibleProperty().bind(rbServiceKeyP12.selectedProperty());
        p12Box.managedProperty().bind(p12Box.visibleProperty());

        MFXButton btnConnectGsuiteAdmin = new MFXButton("Connect GSuite Admin Account");
        btnConnectGsuiteAdmin.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-size: 12.5px; -fx-font-weight: bold; -fx-padding: 8px 16px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnConnectGsuiteAdmin.setOnAction(e -> handleAdminConnect("GSuite"));

        gsuiteAdminBox.getChildren().addAll(gsuiteEmailBox, keyTypeBox, jsonBox, saEmailBox, p12Box, btnConnectGsuiteAdmin);
        gsuiteAdminBox.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            String mode = cmbCloudAuthMode.getValue();
            return mode != null && mode.contains("Service Account");
        }, cmbCloudAuthMode.valueProperty()));
        gsuiteAdminBox.managedProperty().bind(gsuiteAdminBox.visibleProperty());
        loginCard.getChildren().add(gsuiteAdminBox);

        // Office 365 Admin Box
        VBox o365AdminBox = new VBox(10);
        o365AdminBox.setStyle("-fx-background-color: #f8fafc; -fx-padding: 12px; -fx-border-color: #cbd5e1; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        VBox o365EmailBox = new VBox(4);
        Label lblO365Email = new Label("Office 365 Admin Email Address");
        lblO365Email.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        TextField tfO365Email = new TextField();
        styleModernTextField(tfO365Email);
        tfO365Email.setPromptText("admin@yourtenant.onmicrosoft.com");
        tfO365Email.textProperty().bindBidirectional(tfCloudEmail.textProperty());
        o365EmailBox.getChildren().addAll(lblO365Email, tfO365Email);

        HBox tenantClientRow = new HBox(12);
        VBox tenantBox = new VBox(4);
        Label lblTenant = new Label("Directory (Tenant) ID");
        lblTenant.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        tfCloudTenantId = new TextField();
        tfCloudTenantId.setPromptText("e.g. tenant.onmicrosoft.com or Tenant ID");
        styleModernTextField(tfCloudTenantId);
        HBox.setHgrow(tenantBox, Priority.ALWAYS);
        tenantBox.getChildren().addAll(lblTenant, tfCloudTenantId);

        VBox clientBox = new VBox(4);
        Label lblClient = new Label("Application (Client) ID");
        lblClient.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        tfCloudClientId = new TextField();
        tfCloudClientId.setPromptText("e.g. 00000000-0000-0000-0000-000000000000");
        styleModernTextField(tfCloudClientId);
        HBox.setHgrow(clientBox, Priority.ALWAYS);
        clientBox.getChildren().addAll(lblClient, tfCloudClientId);

        tenantClientRow.getChildren().addAll(tenantBox, clientBox);

        VBox secretBox = new VBox(4);
        Label lblSecret = new Label("Client Secret Value");
        lblSecret.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        pfCloudClientSecret = new PasswordField();
        pfCloudClientSecret.setPromptText("Enter Client Secret Value");
        styleModernTextField(pfCloudClientSecret);
        secretBox.getChildren().addAll(lblSecret, pfCloudClientSecret);

        MFXButton btnConnectO365Admin = new MFXButton("Connect Office 365 Admin Account");
        btnConnectO365Admin.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-size: 12.5px; -fx-font-weight: bold; -fx-padding: 8px 16px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnConnectO365Admin.setOnAction(e -> handleAdminConnect("Office 365"));

        o365AdminBox.getChildren().addAll(o365EmailBox, tenantClientRow, secretBox, btnConnectO365Admin);
        o365AdminBox.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            String mode = cmbCloudAuthMode.getValue();
            return mode != null && (mode.contains("Admin Application Permissions") || mode.contains("Office 365 Impersonation"));
        }, cmbCloudAuthMode.valueProperty()));
        o365AdminBox.managedProperty().bind(o365AdminBox.visibleProperty());
        loginCard.getChildren().add(o365AdminBox);

        imapBox.visibleProperty().bind(cmbCloudAuthMode.valueProperty().isEqualTo("App Password (IMAP)"));
        imapBox.managedProperty().bind(imapBox.visibleProperty());

        oauthBox.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            String mode = cmbCloudAuthMode.getValue();
            return mode != null && (mode.contains("OAuth") || mode.contains("Modern Auth")) && !mode.contains("Application Permissions");
        }, cmbCloudAuthMode.valueProperty()));
        oauthBox.managedProperty().bind(oauthBox.visibleProperty());

        tfCloudRedirectUri = new TextField("http://localhost:8888/Callback");
        tfAuthCode = new TextField();

        String initMode = cmbCloudAuthMode != null ? cmbCloudAuthMode.getValue() : "";
        if (initMode != null && (initMode.contains("OAuth") || initMode.contains("Modern Auth")) && !initMode.contains("Application Permissions")) {
            if (selectedFormat.equals("Gmail")) {
                com.pstconverter.core.auth.GoogleOAuthService.ClientSecrets secrets = com.pstconverter.core.auth.GoogleOAuthService.loadClientSecrets();
                tfCloudClientId.setText(secrets.clientId);
                pfCloudClientSecret.setText(secrets.clientSecret);
                tfCloudRedirectUri.setText(secrets.redirectUri);
            } else {
                com.pstconverter.core.auth.MicrosoftOAuthService.ClientSecrets secrets = com.pstconverter.core.auth.MicrosoftOAuthService.loadClientSecrets();
                tfCloudClientId.setText(secrets.clientId);
                pfCloudClientSecret.setText(secrets.clientSecret);
                tfCloudRedirectUri.setText(secrets.redirectUri);
                tfCloudTenantId.setText(secrets.tenantId);
            }
        }

        loginTestBox = new HBox(12);
        loginTestBox.setAlignment(Pos.CENTER);

        btnTestConnection = new MFXButton("Test Connection");
        btnTestConnection.setGraphic(MaterialIcons.icon(MaterialIcons.BOLT, "-fx-text-fill: #10b981; -fx-font-size: 15px;"));
        btnTestConnection.setStyle("-fx-background-color: transparent; -fx-text-fill: #10b981; -fx-border-color: #10b981; -fx-border-width: 1.5px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8px 16px; -fx-cursor: hand;");
        btnTestConnection.setOnAction(e -> handleTestConnection());

        btnLoginAccount = new MFXButton("Login & Connect Account");
        btnLoginAccount.setGraphic(MaterialIcons.icon(MaterialIcons.CHECK, "-fx-text-fill: white; -fx-font-size: 15px;"));
        btnLoginAccount.setStyle("-fx-background-color: linear-gradient(to right, #10b981, #059669); -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8px 20px; -fx-background-radius: 8px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(16, 185, 129, 0.25), 6, 0, 0, 2);");
        btnLoginAccount.setOnAction(e -> handleConnectImapAccount());

        loginTestBox.getChildren().addAll(btnTestConnection, btnLoginAccount);

        VBox testFeedbackContainer = new VBox(8);
        testFeedbackContainer.setAlignment(Pos.CENTER);
        lblTestConnectionFeedback = new Label("");
        lblTestConnectionFeedback.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold;");
        lblTestConnectionFeedback.setVisible(false);
        lblTestConnectionFeedback.setManaged(false);
        testFeedbackContainer.getChildren().addAll(loginTestBox, lblTestConnectionFeedback);

        testFeedbackContainer.visibleProperty().bind(imapBox.visibleProperty());
        testFeedbackContainer.managedProperty().bind(testFeedbackContainer.visibleProperty());
        loginCard.getChildren().add(testFeedbackContainer);
        settingsPanel.getChildren().add(loginCard);
        settingsPanel.getChildren().add(batchImapBox);

        connectedCard = new VBox(10);
        connectedCard.setStyle("-fx-background-color: white; -fx-background-radius: 12px; -fx-border-radius: 12px; -fx-border-color: #e2e8f0; -fx-padding: 14px;");
        connectedCard.setMaxWidth(600);
        connectedCard.setAlignment(Pos.TOP_CENTER);

        HBox connectedHeader = new HBox(8);
        connectedHeader.setAlignment(Pos.CENTER_LEFT);
        Label checkIcon = MaterialIcons.icon(MaterialIcons.CHECK_CIRCLE, 18);
        checkIcon.setStyle("-fx-text-fill: #10b981;");
        lblConnectedEmail = new Label("Connected Account:");
        lblConnectedEmail.setStyle("-fx-font-size: 13.5px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        HBox.setHgrow(lblConnectedEmail, Priority.ALWAYS);

        btnConnectedTestConnection = new MFXButton("Test Connection");
        btnConnectedTestConnection.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6px 12px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnConnectedTestConnection.setOnAction(e -> handleConnectedTestConnection());
        btnConnectedTestConnection.visibleProperty().bind(cmbCloudAuthMode.valueProperty().isEqualTo("App Password (IMAP)"));
        btnConnectedTestConnection.managedProperty().bind(btnConnectedTestConnection.visibleProperty());

        MFXButton btnLogOut = new MFXButton("Log Out");
        btnLogOut.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6px 12px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnLogOut.setOnAction(e -> handleLogOut());
        connectedHeader.getChildren().addAll(checkIcon, lblConnectedEmail, btnConnectedTestConnection, btnLogOut);

        HBox connectedFeedbackRow = new HBox(8);
        connectedFeedbackRow.setAlignment(Pos.CENTER);
        lblConnectedTestConnectionFeedback = new Label("");
        lblConnectedTestConnectionFeedback.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        lblConnectedTestConnectionFeedback.setVisible(false);
        lblConnectedTestConnectionFeedback.setManaged(false);
        connectedFeedbackRow.getChildren().add(lblConnectedTestConnectionFeedback);
        connectedFeedbackRow.visibleProperty().bind(lblConnectedTestConnectionFeedback.visibleProperty().and(cmbCloudAuthMode.valueProperty().isEqualTo("App Password (IMAP)")));
        connectedFeedbackRow.managedProperty().bind(connectedFeedbackRow.visibleProperty());

        VBox targetBox = new VBox(4);
        Label lblTarget = new Label("Target Folder Name");
        lblTarget.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        String activeTemplate = SettingsManager.getSetting("output_folder_template", "Mailbox_Export_[Timestamp]");
        if ("Custom Name".equals(activeTemplate)) {
            activeTemplate = "Mailbox_Export_[Timestamp]";
        }
        String initialCloudFolder = controller.isFolderNameManuallyEdited() 
                ? controller.getCustomExportFolderName() 
                : "";
        tfCloudTargetFolder = new TextField(initialCloudFolder);
        if (!controller.isFolderNameManuallyEdited()) { generateFolderNameFromTemplate(activeTemplate); }
        styleModernTextField(tfCloudTargetFolder);
        tfCloudTargetFolder.textProperty().addListener((o, ov, nv) -> {
            if (tfCloudTargetFolder.isFocused()) {
                controller.setFolderNameManuallyEdited(true);
                controller.setCustomExportFolderName(nv.trim());
            }
            triggerValidation();
        });
        targetBox.getChildren().addAll(lblTarget, tfCloudTargetFolder);

        // User Mailbox Mapping Matrix Box inside connectedCard (Post-Login)
        VBox userMappingBox = new VBox(8);
        userMappingBox.setStyle("-fx-background-color: #f0fdf4; -fx-padding: 12px; -fx-border-color: #bbf7d0; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label lblMappingHeader = new Label("Domain Multi-User Mailbox Mapping Matrix");
        lblMappingHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #166534;");

        Label lblMappingSub = new Label("Map each imported PST file to a target domain user email account for batch migration.");
        lblMappingSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #15803d;");

        HBox mappingBtnRow = new HBox(12);
        mappingBtnRow.setAlignment(Pos.CENTER_LEFT);
        btnOpenUserMapping = new MFXButton("View / Edit User Mapping Matrix");
        btnOpenUserMapping.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8px 14px; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnOpenUserMapping.setOnAction(e -> openUserMappingDialog());

        lblUserMappingSummary = new Label("Mapped 0 source files");
        lblUserMappingSummary.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #047857;");

        mappingBtnRow.getChildren().addAll(btnOpenUserMapping, lblUserMappingSummary);
        userMappingBox.getChildren().addAll(lblMappingHeader, lblMappingSub, mappingBtnRow);

        userMappingBox.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            String mode = cmbCloudAuthMode.getValue();
            boolean isAdmin = mode != null && (mode.contains("Admin") || mode.contains("Service Account") || mode.contains("Impersonation"));
            boolean isImapBatch = isImapBatchMode && "IMAP Server".equalsIgnoreCase(selectedFormat);
            return isAdmin || isImapBatch;
        }, cmbCloudAuthMode.valueProperty()));
        userMappingBox.managedProperty().bind(userMappingBox.visibleProperty());

        HBox advRow = new HBox(12);
        advRow.setAlignment(Pos.CENTER_LEFT);

        VBox delayBox = new VBox(2);
        Label lblDelay = new Label("Delay (ms)");
        lblDelay.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        tfThrottlingDelay = new TextField("100");
        styleModernTextField(tfThrottlingDelay);
        tfThrottlingDelay.setPrefWidth(80);
        tfThrottlingDelay.textProperty().addListener((o, ov, nv) -> triggerValidation());
        delayBox.getChildren().addAll(lblDelay, tfThrottlingDelay);

        VBox rateBox = new VBox(2);
        Label lblRate = new Label("Msgs / Min");
        lblRate.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        tfThrottlingRate = new TextField("300");
        styleModernTextField(tfThrottlingRate);
        tfThrottlingRate.setPrefWidth(80);
        tfThrottlingRate.textProperty().addListener((o, ov, nv) -> triggerValidation());
        rateBox.getChildren().addAll(lblRate, tfThrottlingRate);

        VBox retryBox = new VBox(2);
        Label lblRetries = new Label("Max Retries");
        lblRetries.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        tfMaxRetries = new TextField("5");
        styleModernTextField(tfMaxRetries);
        tfMaxRetries.setPrefWidth(80);
        tfMaxRetries.textProperty().addListener((o, ov, nv) -> triggerValidation());
        retryBox.getChildren().addAll(lblRetries, tfMaxRetries);

        VBox backoffBox = new VBox(2);
        Label lblBackoff = new Label("Backoff (ms)");
        lblBackoff.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        tfInitialBackoffMs = new TextField("1000");
        styleModernTextField(tfInitialBackoffMs);
        tfInitialBackoffMs.setPrefWidth(85);
        tfInitialBackoffMs.textProperty().addListener((o, ov, nv) -> triggerValidation());
        backoffBox.getChildren().addAll(lblBackoff, tfInitialBackoffMs);

        advRow.getChildren().addAll(delayBox, rateBox, retryBox, backoffBox);

        // Native Item Routing Box (Gmail & Office 365 only)
        VBox nativeRoutingBox = new VBox(6);
        nativeRoutingBox.setStyle("-fx-background-color: #f1f5f9; -fx-padding: 10px; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        cbNativeItemRouting = new MFXCheckbox("Enable Native App Sync (Contacts ➔ Cloud Contacts, Calendar ➔ Cloud Calendar, Tasks ➔ Cloud Tasks)");
        cbNativeItemRouting.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        cbNativeItemRouting.setSelected(false); // default OFF as requested!

        Label lblNativeSub = new Label("When enabled, PST Contacts, Calendars, & Tasks migrate directly to Google/O365 native apps instead of mail folders. Unrouted items land safely in Mail.");
        lblNativeSub.setWrapText(true);
        lblNativeSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        nativeRoutingBox.getChildren().addAll(cbNativeItemRouting, lblNativeSub);

        nativeRoutingBox.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            return "Gmail".equalsIgnoreCase(selectedFormat) || "Office 365".equalsIgnoreCase(selectedFormat);
        }));
        nativeRoutingBox.managedProperty().bind(nativeRoutingBox.visibleProperty());

        // Preserve Folder Hierarchy Box (All Cloud Destinations)
        VBox preserveHierarchyBox = new VBox(6);
        preserveHierarchyBox.setStyle("-fx-background-color: #f8fafc; -fx-padding: 10px; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        cbPreserveFolderHierarchy = new MFXCheckbox("Preserve Folder Hierarchy (Direct System & Custom Folder Mirroring)");
        cbPreserveFolderHierarchy.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        cbPreserveFolderHierarchy.setSelected(false); // default OFF as requested!

        Label lblPreserveSub = new Label("When enabled, PST default folders (Inbox, Sent, Drafts, Trash, Junk) migrate directly into matching cloud system folders, and custom folders mirror directly at root.");
        lblPreserveSub.setWrapText(true);
        lblPreserveSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        preserveHierarchyBox.getChildren().addAll(cbPreserveFolderHierarchy, lblPreserveSub);

        connectedCard.getChildren().addAll(connectedHeader, connectedFeedbackRow, new Separator(), targetBox, new Separator(), userMappingBox, new Separator(), nativeRoutingBox, new Separator(), preserveHierarchyBox, new Separator(), advRow);
        settingsPanel.getChildren().add(connectedCard);

        refreshSavedAccounts();
        selectSavedAccount(activeAccountEmail);
        updateCloudViewState();
        if ("IMAP Server".equalsIgnoreCase(selectedFormat)) {
            switchImapMode(isImapBatchMode);
        }
    }

    private void handleAdminConnect(String format) {
        if (!validateDestination(false)) {
            if (validationMessage != null && !validationMessage.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR, validationMessage, ButtonType.OK);
                alert.setTitle("Validation Error");
                alert.setHeaderText("Cannot Connect GSuite Admin Account");
                alert.showAndWait();
            }
            return;
        }
        String adminEmail = tfCloudEmail != null ? tfCloudEmail.getText().trim() : "";
        String authModeVal = cmbCloudAuthMode != null ? cmbCloudAuthMode.getValue() : "";
        String passOrKey = "";
        if (authModeVal != null && authModeVal.contains("Service Account")) {
            if (rbServiceKeyP12 != null && rbServiceKeyP12.isSelected()) {
                String saEmail = tfServiceAccountEmail != null ? tfServiceAccountEmail.getText().trim() : "";
                String p12Path = sanitizeFilePath(tfServiceAccountP12Path != null ? tfServiceAccountP12Path.getText() : "");
                passOrKey = "P12|" + saEmail + "|" + p12Path;
            } else {
                String jsonPath = sanitizeFilePath(tfServiceAccountJsonPath != null ? tfServiceAccountJsonPath.getText() : "");
                passOrKey = jsonPath;
            }
        } else if (authModeVal != null && (authModeVal.contains("Admin Application Permissions") || authModeVal.contains("Office 365 Impersonation"))) {
            passOrKey = pfCloudClientSecret != null ? pfCloudClientSecret.getText().trim() : "";
        }

        System.out.println("[INFO] Connecting Admin Account: email=" + adminEmail + ", format=" + selectedFormat + ", authMode=" + authModeVal + ", key=" + passOrKey);

        com.pstconverter.util.SettingsManager.saveSavedAccount(new com.pstconverter.util.SettingsManager.SavedAccount(
            adminEmail,
            selectedFormat,
            authModeVal,
            "", 0, false, passOrKey
        ));

        activeAccountEmail = adminEmail;
        isConnectionTested = true;
        refreshSavedAccounts();
        selectSavedAccount(adminEmail);
        updateCloudViewState();
    }

    private void handleOAuthAuthorizationFlow() {
        String format = selectedFormat;
        String clientId = tfCloudClientId != null ? tfCloudClientId.getText().trim() : "";
        String clientSecret = pfCloudClientSecret != null ? pfCloudClientSecret.getText().trim() : "";
        String redirectUri = tfCloudRedirectUri != null ? tfCloudRedirectUri.getText().trim() : "";
        String tenantId = tfCloudTenantId != null ? tfCloudTenantId.getText().trim() : "common";

        System.out.println("Triggering OAuth authorization flow for format: " + format 
            + ", Client ID: " + clientId 
            + ", Redirect URI: " + redirectUri 
            + ", Tenant ID: " + tenantId);

        String enteredEmail = tfCloudEmail != null ? tfCloudEmail.getText().trim() : "";
        if (enteredEmail.isEmpty()) {
            setValidationError("Please enter your Email Address first before signing in.");
            return;
        }

        if (clientId.isEmpty() || clientSecret.isEmpty() || redirectUri.isEmpty()) {
            System.err.println("OAuth validation warning: Client ID, Secret, & Redirect URI required.");
            lblOAuthStatus.setText("Error: Client ID, Secret, & Redirect URI required.");
            lblOAuthStatus.setStyle("-fx-font-size: 13px; -fx-text-fill: #f87171; -fx-font-weight: bold;");
            return;
        }

        btnOAuthAuthorize.setDisable(true);
        lblOAuthStatus.setText("Status: Launching browser...");
        lblOAuthStatus.setStyle("-fx-font-size: 13px; -fx-text-fill: #93c5fd; -fx-font-weight: bold;");

        javafx.concurrent.Task<String[]> authTask = new javafx.concurrent.Task<>() {
            @Override
            protected String[] call() throws Exception {
                System.out.println("OAuth authorization thread started.");
                String code;
                String accessToken;
                String refreshToken;
                long expiry;
                String email = "";

                if ("Gmail".equals(format)) {
                    String finalClientId = clientId;
                    String finalClientSecret = clientSecret;
                    String finalRedirectUri = redirectUri;
                    if (finalClientId.isEmpty() || finalClientId.contains("dummy")) {
                        var secrets = com.pstconverter.core.auth.GoogleOAuthService.loadClientSecrets();
                        finalClientId = secrets.clientId;
                        finalClientSecret = secrets.clientSecret;
                        finalRedirectUri = secrets.redirectUri;
                    }
                    code = com.pstconverter.core.auth.GoogleOAuthService.acquireAuthorizationCode(finalClientId, finalRedirectUri);
                    var result = com.pstconverter.core.auth.GoogleOAuthService.exchangeCode(code, finalClientId, finalClientSecret, finalRedirectUri);
                    accessToken = result.accessToken();
                    refreshToken = result.refreshToken();
                    expiry = result.expiryTimeMs();
                    
                    try {
                        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create("https://gmail.googleapis.com/gmail/v1/users/me/profile"))
                                .header("Authorization", "Bearer " + accessToken)
                                .timeout(java.time.Duration.ofSeconds(30))
                                .GET()
                                .build();
                        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200) {
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"emailAddress\"[\\s:]+\"([^\"]+)\"");
                            java.util.regex.Matcher matcher = pattern.matcher(response.body());
                            if (matcher.find()) {
                                email = matcher.group(1);
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("Failed to fetch user email: " + ex.getMessage());
                    }
                } else {
                    String finalClientId = clientId;
                    String finalClientSecret = clientSecret;
                    String finalRedirectUri = redirectUri;
                    String finalTenantId = tenantId;
                    if (finalClientId.isEmpty() || finalClientId.contains("dummy")) {
                        var secrets = com.pstconverter.core.auth.MicrosoftOAuthService.loadClientSecrets();
                        finalClientId = secrets.clientId;
                        finalClientSecret = secrets.clientSecret;
                        finalRedirectUri = secrets.redirectUri;
                        finalTenantId = secrets.tenantId;
                    }
                    boolean isAdminConsent = cmbCloudAuthMode != null && cmbCloudAuthMode.getValue() != null && cmbCloudAuthMode.getValue().contains("Admin Modern Auth");
                    code = com.pstconverter.core.auth.MicrosoftOAuthService.acquireAuthorizationCode(finalTenantId, finalClientId, finalRedirectUri, isAdminConsent);
                    var result = com.pstconverter.core.auth.MicrosoftOAuthService.exchangeCode(finalTenantId, code, finalClientId, finalClientSecret, finalRedirectUri);
                    accessToken = result.accessToken();
                    refreshToken = result.refreshToken();
                    expiry = result.expiryTimeMs();

                    try {
                        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create("https://graph.microsoft.com/v1.0/me"))
                                .header("Authorization", "Bearer " + accessToken)
                                .timeout(java.time.Duration.ofSeconds(30))
                                .GET()
                                .build();
                        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200) {
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"userPrincipalName\"[\\s:]+\"([^\"]+)\"");
                            java.util.regex.Matcher matcher = pattern.matcher(response.body());
                            if (matcher.find()) {
                                email = matcher.group(1);
                            } else {
                                pattern = java.util.regex.Pattern.compile("\"mail\"[\\s:]+\"([^\"]+)\"");
                                matcher = pattern.matcher(response.body());
                                if (matcher.find()) {
                                    email = matcher.group(1);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("Failed to fetch Microsoft email: " + ex.getMessage());
                    }
                }

                if (email.isEmpty()) {
                    email = enteredEmail;
                }

                com.pstconverter.core.auth.TokenManager.saveTokens(email, accessToken, refreshToken, expiry);

                return new String[]{email, accessToken};
            }
        };

        authTask.setOnSucceeded(event -> {
            btnOAuthAuthorize.setDisable(false);
            String[] details = authTask.getValue();
            String email = details[0];
            
            String targetEmail = tfCloudEmail.getText().trim();
            if (!targetEmail.equalsIgnoreCase(email)) {
                lblOAuthStatus.setText("Error: Authorized account (" + email + ") does not match entered email (" + targetEmail + ").");
                lblOAuthStatus.setStyle("-fx-font-size: 13px; -fx-text-fill: #ef4444; -fx-font-weight: bold;");
                setValidationError("Authorized account mismatch.");
                return;
            }

            System.out.println("OAuth authorization succeeded for account: " + email);
            lblOAuthStatus.setText("Status: Authorized! (" + email + ")");
            lblOAuthStatus.setStyle("-fx-font-size: 13px; -fx-text-fill: #34d399; -fx-font-weight: bold;");

            String authModeVal = cmbCloudAuthMode != null ? cmbCloudAuthMode.getValue() : "OAuth 2.0 (Modern Auth)";
            com.pstconverter.util.SettingsManager.saveSavedAccount(new com.pstconverter.util.SettingsManager.SavedAccount(
                email,
                selectedFormat,
                authModeVal,
                "", 0, false, ""
            ));
            
            activeAccountEmail = email;
            refreshSavedAccounts();
            selectSavedAccount(email);
            triggerValidation();
        });

        authTask.setOnFailed(event -> {
            btnOAuthAuthorize.setDisable(false);
            Throwable ex = authTask.getException();
            System.err.println("OAuth authorization failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
            lblOAuthStatus.setText("Status: Authorization Failed: " + (ex != null ? ex.getMessage() : "Unknown"));
            lblOAuthStatus.setStyle("-fx-font-size: 13px; -fx-text-fill: #f87171; -fx-font-weight: bold;");
            if (ex != null) ex.printStackTrace();
            triggerValidation();
        });

        new Thread(authTask).start();
    }

    private void handleConnectedTestConnection() {
        handleTestConnection(true);
    }

    private void handleTestConnection() {
        handleTestConnection(false);
    }

    private void handleTestConnection(boolean isConnectedCard) {
        String email = tfCloudEmail != null ? sanitizeEmail(tfCloudEmail.getText()) : "";
        String pass = pfCloudPassword != null ? sanitizePassword(pfCloudPassword.getText(), "App Password (IMAP)") : "";
        String host = tfCloudHost != null ? sanitizeHost(tfCloudHost.getText()) : "";
        int port = 993;
        try {
            if (tfCloudPort != null) port = Integer.parseInt(tfCloudPort.getText().trim());
        } catch (Exception ignored) {}
        boolean ssl = cbCloudSSL != null && cbCloudSSL.isSelected();

        // If testing from connectedCard and fields were empty in memory, recover from SavedAccount
        if (isConnectedCard && (pass.isEmpty() || host.isEmpty()) && activeAccountEmail != null && !activeAccountEmail.equals("[New Account]")) {
            List<com.pstconverter.util.SettingsManager.SavedAccount> saved = com.pstconverter.util.SettingsManager.getSavedAccounts();
            for (com.pstconverter.util.SettingsManager.SavedAccount acc : saved) {
                if (acc.email().equalsIgnoreCase(activeAccountEmail) && acc.format().equalsIgnoreCase(selectedFormat)) {
                    if (pass.isEmpty() && acc.password() != null) pass = acc.password();
                    if (host.isEmpty() && acc.host() != null) host = acc.host();
                    if (acc.port() > 0) port = acc.port();
                    ssl = acc.ssl();
                    break;
                }
            }
        }

        Label targetFeedback = isConnectedCard ? lblConnectedTestConnectionFeedback : lblTestConnectionFeedback;
        MFXButton targetBtn = isConnectedCard ? btnConnectedTestConnection : btnTestConnection;

        if (email.isEmpty() || pass.isEmpty() || host.isEmpty()) {
            if (targetFeedback != null) {
                targetFeedback.setText("Please enter Email Address, App Password, and IMAP Host.");
                targetFeedback.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11.5px; -fx-font-weight: bold;");
                targetFeedback.setVisible(true);
                targetFeedback.setManaged(true);
            }
            return;
        }

        if (targetBtn != null) targetBtn.setDisable(true);
        if (btnLoginAccount != null && !isConnectedCard) btnLoginAccount.setDisable(true);

        if (targetFeedback != null) {
            targetFeedback.setText("Testing IMAP connection & verifying credentials...");
            targetFeedback.setStyle("-fx-text-fill: #0ea5e9; -fx-font-size: 11.5px; -fx-font-weight: bold;");
            targetFeedback.setVisible(true);
            targetFeedback.setManaged(true);
        }

        final int finalPort = port;
        final String finalEmail = email;
        final String finalPass = pass;
        final String finalHost = host;
        final boolean finalSsl = ssl;

        new Thread(() -> {
            ImapAuthHelper.AuthResult res = 
                ImapAuthHelper.testAndAuthenticate(finalHost, finalPort, finalSsl, finalEmail, finalPass, 25);

            Platform.runLater(() -> {
                if (targetBtn != null) targetBtn.setDisable(false);
                if (btnLoginAccount != null && !isConnectedCard) btnLoginAccount.setDisable(false);

                if (res.success) {
                    if (res.verifiedPassword != null && !res.verifiedPassword.isEmpty() && pfCloudPassword != null) {
                        pfCloudPassword.setText(res.verifiedPassword);
                    }
                    if (targetFeedback != null) {
                        targetFeedback.setText("✓ Connection & Authentication Successful!");
                        targetFeedback.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11.5px; -fx-font-weight: bold;");
                        targetFeedback.setVisible(true);
                        targetFeedback.setManaged(true);
                    }
                    isConnectionTested = true;
                } else {
                    if (targetFeedback != null) {
                        targetFeedback.setText("✗ " + (res.errorMessage != null ? res.errorMessage : "Connection Failed"));
                        targetFeedback.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11.5px; -fx-font-weight: bold;");
                        targetFeedback.setVisible(true);
                        targetFeedback.setManaged(true);
                    }
                    isConnectionTested = false;
                }
                triggerValidation();
            });
        }).start();
    }

    private void handleConnectImapAccount() {
        String email = tfCloudEmail != null ? sanitizeEmail(tfCloudEmail.getText()) : "";
        String pass = pfCloudPassword != null ? sanitizePassword(pfCloudPassword.getText(), "App Password (IMAP)") : "";
        String host = tfCloudHost != null ? sanitizeHost(tfCloudHost.getText()) : "";
        int port = 993;
        try {
            if (tfCloudPort != null) port = Integer.parseInt(tfCloudPort.getText().trim());
        } catch (Exception ignored) {}
        boolean ssl = cbCloudSSL != null && cbCloudSSL.isSelected();

        if (email.isEmpty() || pass.isEmpty() || host.isEmpty()) {
            if (lblTestConnectionFeedback != null) {
                lblTestConnectionFeedback.setText("Please enter Email Address, App Password, and IMAP Host before connecting.");
                lblTestConnectionFeedback.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11.5px; -fx-font-weight: bold;");
                lblTestConnectionFeedback.setVisible(true);
                lblTestConnectionFeedback.setManaged(true);
            }
            return;
        }

        btnTestConnection.setDisable(true);
        if (btnLoginAccount != null) btnLoginAccount.setDisable(true);

        if (lblTestConnectionFeedback != null) {
            lblTestConnectionFeedback.setText("Authenticating with IMAP server...");
            lblTestConnectionFeedback.setStyle("-fx-text-fill: #0ea5e9; -fx-font-size: 11.5px; -fx-font-weight: bold;");
            lblTestConnectionFeedback.setVisible(true);
            lblTestConnectionFeedback.setManaged(true);
        }

        final int finalPort = port;
        final String finalEmail = email;
        final String finalPass = pass;
        final String finalHost = host;
        final boolean finalSsl = ssl;

        new Thread(() -> {
            ImapAuthHelper.AuthResult res = 
                ImapAuthHelper.testAndAuthenticate(finalHost, finalPort, finalSsl, finalEmail, finalPass, 25);

            Platform.runLater(() -> {
                btnTestConnection.setDisable(false);
                if (btnLoginAccount != null) btnLoginAccount.setDisable(false);

                if (res.success) {
                    String workingPass = (res.verifiedPassword != null && !res.verifiedPassword.isEmpty()) ? res.verifiedPassword : finalPass;
                    if (pfCloudPassword != null) {
                        pfCloudPassword.setText(workingPass);
                    }

                    // Save to Saved Accounts in DB
                    com.pstconverter.util.SettingsManager.saveSavedAccount(new com.pstconverter.util.SettingsManager.SavedAccount(
                        finalEmail,
                        selectedFormat,
                        "App Password (IMAP)",
                        finalHost,
                        finalPort,
                        finalSsl,
                        workingPass
                    ));

                    activeAccountEmail = finalEmail;
                    isConnectionTested = true;
                    refreshSavedAccounts();
                    selectSavedAccount(finalEmail);
                    updateCloudViewState();
                    triggerValidation();
                } else {
                    if (lblTestConnectionFeedback != null) {
                        lblTestConnectionFeedback.setText("✗ " + (res.errorMessage != null ? res.errorMessage : "Authentication Failed"));
                        lblTestConnectionFeedback.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11.5px; -fx-font-weight: bold;");
                        lblTestConnectionFeedback.setVisible(true);
                        lblTestConnectionFeedback.setManaged(true);
                    }
                    isConnectionTested = false;
                    triggerValidation();
                }
            });
        }).start();
    }

    public EmailDestinationConfig getEmailDestinationConfig() {
        boolean isCloud = isCloudFormat(selectedFormat);
        if (!isCloud) return null;

        String authModeVal = cmbCloudAuthMode != null ? cmbCloudAuthMode.getValue() : "App Password (IMAP)";
        String hostVal = tfCloudHost != null ? sanitizeHost(tfCloudHost.getText()) : "";
        int portVal = 993;
        try {
            if (tfCloudPort != null) portVal = Integer.parseInt(tfCloudPort.getText().trim());
        } catch (NumberFormatException ignored) {}

        boolean sslVal = cbCloudSSL != null && cbCloudSSL.isSelected();
        String userVal = tfCloudEmail != null ? sanitizeEmail(tfCloudEmail.getText()) : "";
        String passVal = pfCloudPassword != null ? sanitizePassword(pfCloudPassword.getText(), authModeVal) : "";
        if (passVal.isEmpty() && activeAccountEmail != null && !activeAccountEmail.equals("[New Account]")) {
            List<com.pstconverter.util.SettingsManager.SavedAccount> saved = com.pstconverter.util.SettingsManager.getSavedAccounts();
            for (com.pstconverter.util.SettingsManager.SavedAccount acc : saved) {
                if (acc.email().equalsIgnoreCase(activeAccountEmail) && acc.format().equalsIgnoreCase(selectedFormat)) {
                    if (acc.password() != null) passVal = acc.password();
                    if (hostVal.isEmpty() && acc.host() != null) hostVal = acc.host();
                    if (portVal == 993 && acc.port() > 0) portVal = acc.port();
                    break;
                }
            }
        }

        String clientIdVal = tfCloudClientId != null ? tfCloudClientId.getText().trim() : "";
        String clientSecretVal = pfCloudClientSecret != null ? pfCloudClientSecret.getText().trim() : "";
        String tenantIdVal = tfCloudTenantId != null ? tfCloudTenantId.getText().trim() : "common";
        String redirectUriVal = tfCloudRedirectUri != null ? tfCloudRedirectUri.getText().trim() : "";

        String targetFolderVal = tfCloudTargetFolder != null ? tfCloudTargetFolder.getText().trim() : "";

        boolean bulkVal = cbBulkMigration != null && cbBulkMigration.isSelected();
        String bulkCsvVal = tfBulkCsvPath != null ? tfBulkCsvPath.getText().trim() : "";

        int throttleDelay = 100;
        int throttleRate = 300;
        int maxR = 5;
        int initBackoff = 1000;

        try {
            if (tfThrottlingDelay != null) throttleDelay = Integer.parseInt(tfThrottlingDelay.getText().trim());
            if (tfThrottlingRate != null) throttleRate = Integer.parseInt(tfThrottlingRate.getText().trim());
            if (tfMaxRetries != null) maxR = Integer.parseInt(tfMaxRetries.getText().trim());
            if (tfInitialBackoffMs != null) initBackoff = Integer.parseInt(tfInitialBackoffMs.getText().trim());
        } catch (NumberFormatException ignored) {}

        boolean adminImpEnabled = authModeVal != null && (
            authModeVal.contains("Service Account") || 
            authModeVal.contains("Admin Application Permissions") || 
            authModeVal.contains("Impersonation")
        );
        String serviceJson = "";
        if (rbServiceKeyP12 != null && rbServiceKeyP12.isSelected()) {
            String saEmail = tfServiceAccountEmail != null ? tfServiceAccountEmail.getText().trim() : "";
            String p12Path = tfServiceAccountP12Path != null ? tfServiceAccountP12Path.getText().trim() : "";
            serviceJson = "P12|" + saEmail + "|" + p12Path;
        } else {
            serviceJson = tfServiceAccountJsonPath != null ? tfServiceAccountJsonPath.getText().trim() : "";
        }
        String impEmail = tfImpersonatedTargetUserEmail != null && !tfImpersonatedTargetUserEmail.getText().trim().isEmpty() 
            ? tfImpersonatedTargetUserEmail.getText().trim() 
            : userVal;

        return new EmailDestinationConfig(
            selectedFormat,
            authModeVal,
            hostVal,
            portVal,
            sslVal,
            userVal,
            passVal,
            clientIdVal,
            clientSecretVal,
            tenantIdVal,
            redirectUriVal,
            targetFolderVal,
            bulkVal,
            bulkCsvVal,
            throttleDelay,
            throttleRate,
            maxR,
            initBackoff,
            adminImpEnabled,
            serviceJson,
            impEmail,
            new HashMap<>(userMappingMap),
            cbNativeItemRouting != null && cbNativeItemRouting.isSelected(),
            cbPreserveFolderHierarchy != null && cbPreserveFolderHierarchy.isSelected()
        );
    }

    private void switchImapMode(boolean isBatch) {
        this.isImapBatchMode = isBatch;

        String activeStyle = "-fx-background-color: linear-gradient(to right, #0ea5e9, #0284c7); -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 700; -fx-padding: 7px 18px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(14, 165, 233, 0.25), 6, 0, 0, 2);";
        String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-size: 12px; -fx-font-weight: 600; -fx-padding: 7px 18px; -fx-background-radius: 6px; -fx-cursor: hand;";

        if (btnImapSingleMode != null && btnImapBatchMode != null) {
            if (!isBatch) {
                btnImapSingleMode.setStyle(activeStyle);
                btnImapBatchMode.setStyle(inactiveStyle);
            } else {
                btnImapSingleMode.setStyle(inactiveStyle);
                btnImapBatchMode.setStyle(activeStyle);
            }
        }

        if (loginCard != null) {
            loginCard.setVisible(!isBatch);
            loginCard.setManaged(!isBatch);
        }
        if (batchImapBox != null) {
            batchImapBox.setVisible(isBatch);
            batchImapBox.setManaged(isBatch);
        }
        triggerValidation();
    }

    private void handleImportImapCsv() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Select IMAP Accounts CSV File");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        java.io.File file = fc.showOpenDialog(getScene().getWindow());
        if (file != null && file.exists()) {
            imapBatchItemsList.clear();
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    String[] parts = trimmed.split("[,;\t]");
                    if (parts[0].equalsIgnoreCase("Host") || parts[0].equalsIgnoreCase("Username") || parts[0].equalsIgnoreCase("Email")) {
                        continue; // skip header line
                    }
                    if (parts.length >= 5) {
                        if (parts[0].contains("@")) {
                            String user = sanitizeEmail(parts[0]);
                            String pass = ImapAuthHelper.sanitizeAppPassword(parts[1]);
                            String host = sanitizeHost(parts[2]);
                            int port = 993;
                            try { port = Integer.parseInt(parts[3].trim()); } catch (Exception ignored) {}
                            String sec = parts[4].trim();
                            if (!host.isEmpty() && !user.isEmpty()) {
                                imapBatchItemsList.add(new ImapBatchItem(host, port, sec, user, pass));
                            }
                        } else {
                            String host = sanitizeHost(parts[0]);
                            int port = 993;
                            try { port = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {}
                            String sec = parts[2].trim();
                            String user = sanitizeEmail(parts[3]);
                            String pass = ImapAuthHelper.sanitizeAppPassword(parts[4]);
                            if (!host.isEmpty() && !user.isEmpty()) {
                                imapBatchItemsList.add(new ImapBatchItem(host, port, sec, user, pass));
                            }
                        }
                    } else if (parts.length == 4) {
                        if (parts[0].contains("@")) {
                            String user = sanitizeEmail(parts[0]);
                            String pass = ImapAuthHelper.sanitizeAppPassword(parts[1]);
                            String host = sanitizeHost(parts[2]);
                            int port = 993;
                            try { port = Integer.parseInt(parts[3].trim()); } catch (Exception ignored) {}
                            if (!host.isEmpty() && !user.isEmpty()) {
                                imapBatchItemsList.add(new ImapBatchItem(host, port, "SSL/TLS", user, pass));
                            }
                        } else {
                            String host = sanitizeHost(parts[0]);
                            int port = 993;
                            try { port = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {}
                            String user = sanitizeEmail(parts[2]);
                            String pass = ImapAuthHelper.sanitizeAppPassword(parts[3]);
                            if (!host.isEmpty() && !user.isEmpty()) {
                                imapBatchItemsList.add(new ImapBatchItem(host, port, "SSL/TLS", user, pass));
                            }
                        }
                    } else if (parts.length >= 2) {
                        String user = sanitizeEmail(parts[0]);
                        String pass = ImapAuthHelper.sanitizeAppPassword(parts[1]);
                        ImapAuthHelper.ProviderPreset preset = ImapAuthHelper.detectPreset(user);
                        if (!user.isEmpty()) {
                            imapBatchItemsList.add(new ImapBatchItem(preset.host, preset.port, preset.ssl ? "SSL/TLS" : "Plain", user, pass));
                        }
                    }
                }
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to parse CSV file: " + ex.getMessage(), ButtonType.OK);
                alert.showAndWait();
                return;
            }

            if (!imapBatchItemsList.isEmpty()) {
                runImapBatchVerification();
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING, "No valid IMAP accounts found in the selected CSV file.", ButtonType.OK);
                alert.showAndWait();
            }
        }
    }

    private void handleSampleImapCsv() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Save Sample IMAP Accounts CSV");
        fc.setInitialFileName("imap_accounts_sample.csv");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        java.io.File file = fc.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                writer.write("Host,Port,Security,Username,Password\n");
                writer.write("imap.example.com,993,SSL/TLS,user1@company.com,Password123\n");
                writer.write("imap.example.com,993,SSL/TLS,user2@company.com,Password456\n");
                writer.write("imap.example.com,143,STARTTLS,user3@company.com,Password789\n");
                writer.flush();
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Sample CSV saved successfully to:\n" + file.getAbsolutePath(), ButtonType.OK);
                alert.showAndWait();
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to save sample CSV: " + ex.getMessage(), ButtonType.OK);
                alert.showAndWait();
            }
        }
    }

    private void runImapBatchVerification() {
        if (imapBatchItemsList.isEmpty()) return;
        pbImapVerify.setVisible(true);
        pbImapVerify.setManaged(true);
        lblImapBatchStats.setText("Testing connections for " + imapBatchItemsList.size() + " accounts...");

        new Thread(() -> {
            int total = imapBatchItemsList.size();
            java.util.concurrent.atomic.AtomicInteger verifiedCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger failedCount = new java.util.concurrent.atomic.AtomicInteger(0);

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(5);
            for (ImapBatchItem item : imapBatchItemsList) {
                executor.submit(() -> {
                    boolean ssl = !item.getSecurity().equalsIgnoreCase("Plain") && !item.getSecurity().equalsIgnoreCase("STARTTLS");
                    ImapAuthHelper.AuthResult res = 
                        ImapAuthHelper.testAndAuthenticate(item.getHost(), item.getPort(), ssl, item.getUsername(), item.getPassword(), 20);

                    if (res.success) {
                        item.setVerified(true);
                        item.setStatus("Connected ✓");
                        verifiedCount.incrementAndGet();
                    } else {
                        item.setVerified(false);
                        item.setStatus("Failed: " + (res.errorMessage != null ? res.errorMessage : "Auth Error"));
                        failedCount.incrementAndGet();
                    }

                    Platform.runLater(() -> {
                        tblImapAccounts.refresh();
                        lblImapBatchStats.setText("Total: " + total + " | Verified: " + verifiedCount.get() + " | Failed: " + failedCount.get());
                    });
                });
            }
            executor.shutdown();
            try { executor.awaitTermination(5, java.util.concurrent.TimeUnit.MINUTES); } catch (Exception ignored) {}

            Platform.runLater(() -> {
                pbImapVerify.setVisible(false);
                pbImapVerify.setManaged(false);
                lblImapBatchStats.setText("Total: " + total + " | Verified: " + verifiedCount.get() + " | Failed: " + failedCount.get());
                triggerValidation();
            });
        }).start();
    }

    private void handleContinueImapBatch() {
        verifiedImapUsersList.clear();
        StringBuilder batchPayload = new StringBuilder();

        for (ImapBatchItem item : imapBatchItemsList) {
            if (item.isVerified()) {
                verifiedImapUsersList.add(item.getUsername());
                boolean ssl = !item.getSecurity().equalsIgnoreCase("Plain") && !item.getSecurity().equalsIgnoreCase("STARTTLS");
                batchPayload.append("IMAP_BATCH:")
                            .append(item.getHost()).append("|")
                            .append(item.getPort()).append("|")
                            .append(ssl).append("|")
                            .append(item.getUsername()).append("|")
                            .append(item.getPassword()).append("\n");
            }
        }

        if (verifiedImapUsersList.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No verified IMAP accounts available. Please import a valid CSV and verify connections first.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        if (tfServiceAccountJsonPath != null) {
            tfServiceAccountJsonPath.setText(batchPayload.toString());
        }
        activeAccountEmail = "IMAP Batch (" + verifiedImapUsersList.size() + " Accounts)";
        isConnectionTested = true;

        // Auto-map imported source files to destination batch accounts
        if (controller.getFileList() != null && !controller.getFileList().isEmpty()) {
            List<com.pstconverter.core.model.SourceFileModel> files = controller.getFileList();
            for (int i = 0; i < files.size(); i++) {
                com.pstconverter.core.model.SourceFileModel fm = files.get(i);
                String matchedUser = null;
                for (String u : verifiedImapUsersList) {
                    if (fm.getFileName().toLowerCase().contains(u.toLowerCase()) || fm.getFilePath().toLowerCase().contains(u.toLowerCase())) {
                        matchedUser = u;
                        break;
                    }
                }
                if (matchedUser == null) {
                    matchedUser = verifiedImapUsersList.get(i % verifiedImapUsersList.size());
                }
                userMappingMap.put(fm.getFilePath(), matchedUser);
                userMappingMap.put(fm.getFileName(), matchedUser);
            }
            isUserMappingConfigured = true;
            updateMappingSummaryLabel();
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION, "IMAP Batch Ready!\n" + verifiedImapUsersList.size() + " verified account(s) ready for migration.", ButtonType.OK);
        alert.showAndWait();

        triggerValidation();
    }

    private void openUserMappingDialog() {
        String defaultAdmin = tfCloudEmail != null ? tfCloudEmail.getText().trim() : "";
        if (defaultAdmin.isEmpty() && activeAccountEmail != null && !activeAccountEmail.equals("[New Account]")) {
            defaultAdmin = activeAccountEmail.trim();
        }
        if (tfImpersonatedTargetUserEmail != null && !tfImpersonatedTargetUserEmail.getText().trim().isEmpty()) {
            defaultAdmin = tfImpersonatedTargetUserEmail.getText().trim();
        }

        List<String> domainUsers = new java.util.ArrayList<>();
        if (isImapBatchMode && !verifiedImapUsersList.isEmpty()) {
            domainUsers.addAll(verifiedImapUsersList);
        }
        String authModeVal = cmbCloudAuthMode != null ? cmbCloudAuthMode.getValue() : "";

        if (selectedFormat.equals("Office 365") && authModeVal != null && (authModeVal.contains("Admin Application Permissions") || authModeVal.contains("Office 365 Impersonation"))) {
            String tenantId = tfCloudTenantId != null ? tfCloudTenantId.getText().trim() : "";
            String clientId = tfCloudClientId != null ? tfCloudClientId.getText().trim() : "";
            String clientSecret = pfCloudClientSecret != null ? pfCloudClientSecret.getText().trim() : "";

            if (clientSecret.isEmpty() && activeAccountEmail != null && !activeAccountEmail.equals("[New Account]")) {
                List<com.pstconverter.util.SettingsManager.SavedAccount> saved = com.pstconverter.util.SettingsManager.getSavedAccounts();
                for (com.pstconverter.util.SettingsManager.SavedAccount acc : saved) {
                    if (acc.email().equalsIgnoreCase(activeAccountEmail) && acc.format().equalsIgnoreCase(selectedFormat)) {
                        if (acc.password() != null && !acc.password().isEmpty()) {
                            clientSecret = acc.password();
                        }
                        break;
                    }
                }
            }

            if (!tenantId.isEmpty() && !clientId.isEmpty() && !clientSecret.isEmpty()) {
                try {
                    var tokenResult = com.pstconverter.core.auth.MicrosoftOAuthService.acquireClientCredentialsToken(tenantId, clientId, clientSecret);
                    if (tokenResult != null && tokenResult.accessToken() != null) {
                        List<String> fetched = com.pstconverter.core.auth.MicrosoftOAuthService.fetchTenantUserEmails(tokenResult.accessToken());
                        if (fetched != null && !fetched.isEmpty()) {
                            domainUsers.addAll(fetched);
                            System.out.println("[INFO] Successfully fetched " + domainUsers.size() + " live domain users from Office 365 App-Only token.");
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to fetch live domain users for mapping dialog: " + ex.getMessage());
                }
            }
        } else if (selectedFormat.equals("Office 365") && authModeVal != null && authModeVal.contains("Admin Modern Auth")) {
            String token = com.pstconverter.core.auth.TokenManager.getAccessToken(defaultAdmin);
            if (token != null && !token.isEmpty()) {
                try {
                    List<String> fetched = com.pstconverter.core.auth.MicrosoftOAuthService.fetchTenantUserEmails(token);
                    if (fetched != null && !fetched.isEmpty()) {
                        domainUsers.addAll(fetched);
                        System.out.println("[INFO] Successfully fetched " + domainUsers.size() + " live domain users from Office 365 Admin OAuth token.");
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to fetch live domain users via Admin OAuth token: " + ex.getMessage());
                }
            }
        } else if (selectedFormat.equals("Gmail")) {
            if (authModeVal != null && authModeVal.contains("Service Account")) {
                String jsonPath = tfServiceAccountJsonPath != null ? tfServiceAccountJsonPath.getText().trim() : "";
                if (jsonPath.isEmpty() && activeAccountEmail != null && !activeAccountEmail.equals("[New Account]")) {
                    List<com.pstconverter.util.SettingsManager.SavedAccount> saved = com.pstconverter.util.SettingsManager.getSavedAccounts();
                    for (com.pstconverter.util.SettingsManager.SavedAccount acc : saved) {
                        if (acc.email().equalsIgnoreCase(activeAccountEmail) && acc.format().equalsIgnoreCase(selectedFormat)) {
                            if (acc.password() != null && !acc.password().isEmpty()) {
                                jsonPath = acc.password();
                            }
                            break;
                        }
                    }
                }
                if (!jsonPath.isEmpty() && !defaultAdmin.isEmpty()) {
                    try {
                        List<String> fetched = com.pstconverter.core.auth.GoogleOAuthService.fetchGSuiteDomainUsers(jsonPath, defaultAdmin);
                        if (fetched != null && !fetched.isEmpty()) {
                            domainUsers.addAll(fetched);
                            System.out.println("[INFO] Successfully fetched " + domainUsers.size() + " live domain users for GSuite Service Account.");
                        }
                    } catch (Exception ex) {
                        System.err.println("Failed to fetch live GSuite domain users: " + ex.getMessage());
                    }
                }
            }
        }

        // Ensure defaultAdmin and mapped accounts are included in domainUsers (do not load unrelated DB saved personal accounts)
        if (!defaultAdmin.isEmpty() && !domainUsers.contains(defaultAdmin)) {
            domainUsers.add(0, defaultAdmin);
        }
        for (String mappedEmail : userMappingMap.values()) {
            if (mappedEmail != null && !mappedEmail.trim().isEmpty()) {
                String email = mappedEmail.trim();
                if (!domainUsers.contains(email)) {
                    domainUsers.add(email);
                }
            }
        }

        UserMappingDialog dialog = new UserMappingDialog(controller.getFileList(), defaultAdmin, userMappingMap, domainUsers);
        java.util.Optional<Map<String, String>> result = dialog.showAndWait();
        if (result.isPresent()) {
            userMappingMap.clear();
            userMappingMap.putAll(result.get());
            isUserMappingConfigured = true;
            updateMappingSummaryLabel();
            triggerValidation();
        }
    }

    private void updateMappingSummaryLabel() {
        if (lblUserMappingSummary == null) return;
        int totalFiles = controller.getFileList() != null ? controller.getFileList().size() : 0;
        if (userMappingMap.isEmpty()) {
            lblUserMappingSummary.setText("Mapped " + totalFiles + " source files to default target email");
        } else {
            java.util.Set<String> uniqueUsers = new java.util.HashSet<>(userMappingMap.values());
            lblUserMappingSummary.setText("Mapped " + totalFiles + " source files to " + uniqueUsers.size() + " domain users");
        }
    }

    private String sanitizePassword(String pass, String authMode) {
        if (pass == null) return "";
        String cleaned = pass.replace("\u00A0", " ").trim();
        if (authMode != null && authMode.contains("App Password")) {
            cleaned = cleaned.replaceAll("\\s+", "");
        }
        return cleaned;
    }

    private String sanitizeEmail(String email) {
        if (email == null) return "";
        return email.replace("\u00A0", "").trim().replaceAll("\\s+", "");
    }

    private String sanitizeHost(String host) {
        if (host == null) return "";
        return host.replace("\u00A0", "").trim().replaceAll("\\s+", "");
    }

    private String getFormatIcon(String format) {
        // Abbreviation badge text — matches Step4DestinationView pattern
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

    private void generateFolderNameFromTemplate(String template) {
        String timestampStr = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String dateStr = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        String folderName;
        switch (template) {
            case "Migration_[Date]":
                folderName = "Migration_" + dateStr;
                break;
            case "Export_[Timestamp]":
                folderName = "Export_" + timestampStr;
                break;
            case "Archive_[Timestamp]":
                folderName = "Archive_" + timestampStr;
                break;
            case "Custom Name":
                folderName = controller.getCustomExportFolderName() != null ? controller.getCustomExportFolderName() : "Custom_Export";
                break;
            default:
                folderName = "Mailbox_Export_" + timestampStr;
                break;
        }
        if (tfCloudTargetFolder != null) {
            tfCloudTargetFolder.setText(folderName);
        }
    }

    public void setValidationError(String msg) { 
        this.validationMessage = msg;
    }

    public void loadSettings(java.util.Properties props) {
        if (props == null) return;
        String username = props.getProperty("cloud_username", "");
        String host = props.getProperty("cloud_host", "");
        String port = props.getProperty("cloud_port", "");
        String ssl = props.getProperty("cloud_ssl", "true");
        String targetFolder = props.getProperty("cloud_target_folder", "");
        String authMode = props.getProperty("cloud_auth_mode", "");
        
        isUpdatingProgrammatically = true;
        try {
            if (tfCloudEmail != null) tfCloudEmail.setText(username);
            if (tfCloudHost != null) tfCloudHost.setText(host);
            if (tfCloudPort != null) tfCloudPort.setText(port);
            if (cbCloudSSL != null) cbCloudSSL.setSelected(Boolean.parseBoolean(ssl));
            if (tfCloudTargetFolder != null) tfCloudTargetFolder.setText(targetFolder);
            if (targetFolder != null && !targetFolder.isEmpty()) {
                controller.setFolderNameManuallyEdited(true);
                controller.setCustomExportFolderName(targetFolder);
            }
            if (cmbCloudAuthMode != null && !authMode.isEmpty()) cmbCloudAuthMode.setValue(authMode);

            if (username != null && !username.trim().isEmpty()) {
                activeAccountEmail = username;
                refreshSavedAccounts();
                List<com.pstconverter.util.SettingsManager.SavedAccount> saved = com.pstconverter.util.SettingsManager.getSavedAccounts();
                for (com.pstconverter.util.SettingsManager.SavedAccount acc : saved) {
                    if (acc.email().equalsIgnoreCase(username) && acc.format().equalsIgnoreCase(selectedFormat)) {
                        if (pfCloudPassword != null && acc.password() != null) {
                            pfCloudPassword.setText(acc.password());
                        }
                        break;
                    }
                }
            }
        } finally {
            isUpdatingProgrammatically = false;
        }
        updateCloudViewState();
    }
}
