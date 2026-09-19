package com.pstconverter.util;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

public final class MaterialIcons {

    private MaterialIcons() {}

    // ── Standard Material Icons v3 Range (\uE000 - \uE92F) ───────────────────
    public static final String INBOX = "\uE55C";
    public static final String SEND = "\uE2C6";
    public static final String ATTACHMENT = "\uE2C7";
    public static final String CALENDAR = "\uE878"; // EVENT calendar
    public static final String PERSON = "\uE7FD";
    public static final String ACCOUNT_CIRCLE = "\uE853";
    public static final String CHECK_CIRCLE = "\uE86C";
    public static final String WARNING = "\uE002";
    public static final String FOLDER = "\uE2C9";
    public static final String FOLDER_OPEN = "\uE2C8";
    public static final String DASHBOARD = "\uE8F0";
    public static final String SEARCH = "\uE8B6";
    public static final String SETTINGS = "\uE8B8";
    public static final String ADD = "\uE145";
    public static final String DELETE = "\uE872";
    public static final String REFRESH = "\uE5D5";
    public static final String EMAIL = "\uE0BE";
    public static final String BAR_CHART = "\uE6E1";
    public static final String PRINT = "\uE8AD";
    public static final String SAVE = "\uE161";
    public static final String LINK = "\uE157";
    public static final String PUBLIC = "\uE894";
    public static final String NOTE = "\uE86F";
    public static final String EDIT_NOTE = "\uE3C9";
    public static final String CLEANING = "\uE864";
    public static final String CLOUD = "\uE2BD";
    public static final String BOLT = "\uE1B2";
    public static final String HOURGLASS = "\uE88F";
    public static final String ARCHIVE = "\uE149";
    public static final String BOOK = "\uE865";
    public static final String INVENTORY = "\uE2CA";
    public static final String EDIT = "\uE254";
    public static final String VISIBILITY = "\uE8F4";
    public static final String GROUP = "\uE7EF";
    public static final String BLOCK = "\uE14B";
    public static final String MOVE_TO_INBOX = "\uE0E5";
    public static final String ARROW_BACK = "\uE5C4";
    public static final String ARROW_FORWARD = "\uE315";
    public static final String INFO = "\uE88E";
    public static final String CHECK_BOX = "\uE876";
    public static final String DESKTOP = "\uE30C";
    public static final String STAR = "\uE838";
    public static final String FLAG = "\uE153";
    public static final String FILE_PRESENT = "\uE873";
    public static final String DONE = "\uE5CA";
    public static final String DONE_ALL = "\uE5C8";
    public static final String ERROR = "\uE000";
    public static final String HOME = "\uE88A";
    public static final String PEOPLE = "\uE7FB";
    public static final String EVENT = "\uE878";
    public static final String LANGUAGE = "\uE89E";
    public static final String NOTIFICATIONS = "\uE85E";
    public static final String CHECK = "\uE5CA";
    public static final String CLEAR = "\uE5CD";
    public static final String MENU = "\uE5D2";
    public static final String LOCK = "\uE897";
    public static final String LOCK_OPEN = "\uE898";
    public static final String HELP = "\uE887";

    // ── Verified Standard Codepoints ──────────────────────────────────────
    public static final String TUNE = "\uE429";
    public static final String SPEED = "\uE01B";       // AV_TIMER
    public static final String SCHEDULE = "\uE8B5";    // SCHEDULE
    public static final String DATA_USAGE = "\uE1AF";  // DATA_USAGE
    public static final String HISTORY = "\uE889";     // HISTORY
    public static final String VERIFIED = "\uE8E8";    // VERIFIED_USER
    public static final String BUG_REPORT = "\uE868";  // BUG_REPORT
    public static final String PLAY_ARROW = "\uE037";  // PLAY_ARROW
    public static final String CLOUD_UPLOAD = "\uE2C3";// CLOUD_UPLOAD
    public static final String PICTURE_AS_PDF = "\uE415";// PICTURE_AS_PDF
    public static final String TERMINAL = "\uE86F";    // NOTE
    public static final String SOURCE = "\uE873";      // DESCRIPTION / DOCUMENT
    public static final String INVENTORY_2 = "\uE2CA";  // INVENTORY
    public static final String FIBER_MANUAL_RECORD = "\uE061";
    public static final String CLOUD_SYNC = "\uE2BD";  // CLOUD
    public static final String DESCRIPTION = "\uE873"; // DESCRIPTION

    public static Label icon(String codepoint) {
        return icon(codepoint, null);
    }

    public static Label icon(String codepoint, String style) {
        Label label = new Label(codepoint);
        label.getStyleClass().add("material-icon");
        if (style != null && !style.isEmpty()) {
            label.setStyle(style);
        }
        return label;
    }

    public static Label icon(String codepoint, int size) {
        Label label = icon(codepoint);
        label.setStyle("-fx-font-size: " + size + "px;");
        return label;
    }

    public static HBox withText(String codepoint, String text) {
        return withText(codepoint, text, null);
    }

    public static HBox withText(String codepoint, String text, String iconStyle) {
        HBox box = new HBox(6);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label iconLabel = icon(codepoint, iconStyle);
        Label textLabel = new Label(text);
        box.getChildren().addAll(iconLabel, textLabel);
        return box;
    }

    public static String combine(String codepoint, String text) {
        return codepoint + " " + text;
    }
}
