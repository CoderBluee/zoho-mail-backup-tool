package com.pstconverter.config;

public final class BrandConfig {

    // Tools Data
    public static final String TOOL_NAME = "Outlook Export & PST Recovery";
    public static final String VERSION = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM"));
    public static final String COMPANY_NAME = "Brand C";

    // Webpage Links
    public static final String HOME_PAGE_URL = "https://www.brand-c.com";
    public static final String SUPPORT_URL = "https://www.brand-c.com/support";
    public static final String UPGRADE_URL = "https://www.brand-c.com/upgrade";
    public static final String LICENSE_API_URL = "https://api.brand-c.com/license/activate";
    public static final String FAQ_URL = "https://www.brand-c.com/faq";
    public static final String PRIVACY_POLICY_URL = "https://www.brand-c.com/privacy-policy";
    public static final String TERMS_OF_SERVICE_URL = "https://www.brand-c.com/terms-of-service";
    public static final String REFUND_POLICY_URL = "https://www.brand-c.com/refund-policy";

    // Directories (grouped by Company Name to prevent conflicts even if Tool Names
    // match)
    public static final String REPORT_DIR_NAME = COMPANY_NAME + java.io.File.separator + TOOL_NAME;
    public static final String SETTINGS_DB_DIR = (COMPANY_NAME + java.io.File.separator + TOOL_NAME).replace(" ", "-");
    public static final String HIDDEN_SETTINGS_DB_DIR = "."
            + (COMPANY_NAME + "-" + TOOL_NAME).toLowerCase().replace(" ", "-").replace("/", "-").replace("\\", "-");

    // License Key Validation Pattern
    public static final String LICENSE_KEY_PATTERN = "^PSTC-ELITE-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$";
    public static final String LICENSE_KEY_FORMAT_HINT = "PSTC-ELITE-XXXX-XXXX-XXXX";

    // About Dialog Texts
    public static final String TAGLINE = "Export Outlook Mailboxes & Recover Corrupted PST File Data";
    public static final String ABOUT_TITLE = TOOL_NAME;
    public static final String ABOUT_DESCRIPTION = TOOL_NAME
            + " is a professional, high-performance modular desktop utility designed to parse, filter, and convert Outlook PST files and other mailbox formats securely.";
}
