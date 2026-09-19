package com.pstconverter.controller;

public interface ConversionUIContext {
    void appendLog(String message);
    void appendError(String error);
    void updateMasterProgress(double progress);
    void updateFolderProgress(double progress);
    void setMasterStatus(String status);
    void setFolderStatus(String status);
    void updateTelemetry(String success, String failed, String skipped, String elapsed, String eta, String speed, String outputSize, String status);
    void updateCurrentLabel(String subject, String folderPath, String fileName, String destFolder);
    void onConversionFinished(boolean stopped);
    void updateTreeItemStatus(String folderKey, String status, int success, int skipped, int failed, int total);
    void setResumeStatus(String status);
    void setSessionInfo(String info);
    void setSessionFormat(String format);
    void setSessionItems(String items);
    void updateCurrentLabelProgress(double progress, String text);
    void updateSessionFiles(String text);
    void setControlsState(String state);
    void resetTelemetry();
}
