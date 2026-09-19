package com.pstconverter.config;

public final class BrandConfig {

    // Tools Data
    public static final String TOOL_NAME = "Enterprise Email Migrator Pro";
    public static final String VERSION = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM"));
    public static final String COMPANY_NAME = "Brand B";

    // Webpage Links
    public static final String HOME_PAGE_URL = "https://www.brand-b.com";
    public static final String SUPPORT_URL = "https://www.brand-b.com/support";
    public static final String UPGRADE_URL = "https://www.brand-b.com/upgrade";
    public static final String LICENSE_API_URL = "https://api.brand-b.com/license/activate";
    public static final String FAQ_URL = "https://www.brand-b.com/faq";
    public static final String PRIVACY_POLICY_URL = "https://www.brand-b.com/privacy-policy";
    public static final String TERMS_OF_SERVICE_URL = "https://www.brand-b.com/terms-of-service";
    public static final String REFUND_POLICY_URL = "https://www.brand-b.com/refund-policy";

    // Directories (grouped by Company Name to prevent conflicts even if Tool Names
    // match)
    public static final String REPORT_DIR_NAME = COMPANY_NAME + java.io.File.separator + TOOL_NAME;
    public static final String SETTINGS_DB_DIR = (COMPANY_NAME + java.io.File.separator + TOOL_NAME).replace(" ", "-");
    public static final String HIDDEN_SETTINGS_DB_DIR = "."
            + (COMPANY_NAME + "-" + TOOL_NAME).toLowerCase().replace(" ", "-").replace("/", "-").replace("\\", "-");

    // License Key Validation Pattern
    public static final String LICENSE_KEY_PATTERN = "^PSTB-ELITE-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$";
    public static final String LICENSE_KEY_FORMAT_HINT = "PSTB-ELITE-XXXX-XXXX-XXXX";

    // About Dialog Texts
    public static final String TAGLINE = "Fast, Secure Mailbox Conversion & Enterprise Email Migration Tool";
    public static final String ABOUT_TITLE = TOOL_NAME;
    public static final String ABOUT_DESCRIPTION = TOOL_NAME
            + " is a professional, high-performance modular desktop utility designed to parse, filter, and convert Outlook PST files and other mailbox formats securely.";
}
