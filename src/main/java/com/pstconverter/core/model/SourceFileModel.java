package com.pstconverter.core.model;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

public class SourceFileModel {
    private final SimpleStringProperty filePath;
    private final SimpleStringProperty fileName;
    private final SimpleStringProperty fileSize;
    private final SimpleStringProperty status;
    private final SimpleBooleanProperty valid;
    
    // Track where it came from to build the Step 2 Tree appropriately
    private final SimpleStringProperty importSourceType; // "File" or "Folder"
    private final SimpleStringProperty sourceFolderName; // The selected directory name if importSourceType is "Folder"
    
    // Track the format/adapter type for this file (e.g. "PST", "MBOX")
    private final SimpleStringProperty sourceType;

    public SourceFileModel(String filePath, String fileName, String fileSize, String status, boolean isValid, 
                           String importSourceType, String sourceFolderName, String sourceType) {
        this.filePath = new SimpleStringProperty(filePath);
        this.fileName = new SimpleStringProperty(fileName);
        this.fileSize = new SimpleStringProperty(fileSize);
        this.status = new SimpleStringProperty(status);
        this.valid = new SimpleBooleanProperty(isValid);
        this.importSourceType = new SimpleStringProperty(importSourceType);
        this.sourceFolderName = new SimpleStringProperty(sourceFolderName);
        this.sourceType = new SimpleStringProperty(sourceType);
    }

    public String getFilePath() {
        return filePath.get();
    }

    public SimpleStringProperty filePathProperty() {
        return filePath;
    }

    public String getFileName() {
        return fileName.get();
    }

    public SimpleStringProperty fileNameProperty() {
        return fileName;
    }

    public String getFileSize() {
        return fileSize.get();
    }

    public SimpleStringProperty fileSizeProperty() {
        return fileSize;
    }

    public String getStatus() {
        return status.get();
    }

    public SimpleStringProperty statusProperty() {
        return status;
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public boolean isValid() {
        return valid.get();
    }

    public SimpleBooleanProperty validProperty() {
        return valid;
    }

    public void setValid(boolean isValid) {
        this.valid.set(isValid);
    }

    public String getImportSourceType() {
        return importSourceType.get();
    }

    public SimpleStringProperty importSourceTypeProperty() {
        return importSourceType;
    }

    public String getSourceFolderName() {
        return sourceFolderName.get();
    }

    public SimpleStringProperty sourceFolderNameProperty() {
        return sourceFolderName;
    }

    public String getSourceType() {
        return sourceType.get();
    }

    public SimpleStringProperty sourceTypeProperty() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType.set(sourceType);
    }
}
