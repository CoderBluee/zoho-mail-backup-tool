package com.pstconverter.core.output;

/**
 * Enum holding all 17 target formats categorized by their respective group.
 */
public enum OutputCategory {
    // Document formats
    PDF("PDF", "DOCUMENTS"),
    HTML("HTML", "DOCUMENTS"),
    MHTML("MHTML", "DOCUMENTS"),
    TXT("TXT", "DOCUMENTS"),
    RTF("RTF", "DOCUMENTS"),
    DOC("DOC", "DOCUMENTS"),
    JSON("JSON", "DOCUMENTS"),
    CSV("CSV", "DOCUMENTS"),

    // Email formats
    MBOX("MBOX", "EMAILS"),
    EML("EML", "EMAILS"),
    MSG("MSG", "EMAILS"),
    PST("PST", "EMAILS"),
    EMLX("EMLX", "EMAILS"),

    // Cloud formats
    OFFICE_365("Office 365", "CLOUD"),
    GMAIL("Gmail", "CLOUD"),
    IMAP("IMAP Server", "CLOUD"),
    YAHOO("Yahoo Mail", "CLOUD");

    private final String displayName;
    private final String group;

    OutputCategory(String displayName, String group) {
        this.displayName = displayName;
        this.group = group;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getGroup() {
        return group;
    }

    /**
     * Finds the matching OutputCategory from a display name or string representation.
     *
     * @param name The format name.
     * @return The matched OutputCategory, or null if not found.
     */
    public static OutputCategory fromString(String name) {
        if (name == null) return null;
        String normalized = name.trim().replace(" ", "_").toUpperCase();
        for (OutputCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(normalized) || cat.displayName.equalsIgnoreCase(name.trim())) {
                return cat;
            }
        }
        // Fallback checks
        if (name.equalsIgnoreCase("IMAP Server") || name.equalsIgnoreCase("IMAP")) return IMAP;
        if (name.equalsIgnoreCase("Office 365") || name.equalsIgnoreCase("OFFICE365")) return OFFICE_365;
        if (name.equalsIgnoreCase("Yahoo Mail") || name.equalsIgnoreCase("Yahoo")) return YAHOO;
        
        return null;
    }
}
