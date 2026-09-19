package com.pstconverter.model;

import java.util.ArrayList;
import java.util.List;

public class MailboxFolder {
    private final String name;
    private final int contentCount;
    private final boolean isSystemFolder;
    private final List<MailboxFolder> children = new ArrayList<>();

    public MailboxFolder(String name, int contentCount, boolean isSystemFolder) {
        this.name = name;
        this.contentCount = contentCount;
        this.isSystemFolder = isSystemFolder;
    }

    public String getName() {
        return name;
    }

    public int getContentCount() {
        return contentCount;
    }

    public boolean isSystemFolder() {
        return isSystemFolder;
    }

    public List<MailboxFolder> getChildren() {
        return children;
    }

    public void addChild(MailboxFolder child) {
        children.add(child);
    }
}
