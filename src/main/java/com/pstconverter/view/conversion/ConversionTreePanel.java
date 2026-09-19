package com.pstconverter.view.conversion;

import com.pstconverter.core.model.SourceFileModel;
import com.pstconverter.util.MaterialIcons;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConversionTreePanel extends VBox {

    private TreeView<String> folderTreeView;
    private TreeItem<String> rootItem;
    private Label lblSessionFormat;
    private Label lblSessionFiles;
    private Label lblSessionItems;
    private Label lblSessionInfo;

    // Mapping from unique folder path to the TreeItem
    private final Map<String, TreeItem<String>> folderNodeMap = new LinkedHashMap<>();
    private final boolean isDarkMode;

    public ConversionTreePanel(boolean isDarkMode) {
        super(0);
        this.isDarkMode = isDarkMode;
        this.getStyleClass().add("sidebar-card");

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 14, 12, 14));
        header.getStyleClass().add("card-header");
        Label headerIcon  = MaterialIcons.icon(MaterialIcons.FOLDER_OPEN, 15);
        Label headerTitle = new Label("FOLDER PROGRESS");
        headerTitle.getStyleClass().add("card-header-title");
        header.getChildren().addAll(headerIcon, headerTitle);

        // Session info strip
        VBox sessionInfo = new VBox(4);
        sessionInfo.setPadding(new Insets(10, 14, 10, 14));
        sessionInfo.setStyle("-fx-background-color: rgba(99,102,241,0.08); "
                + "-fx-border-color: rgba(99,102,241,0.18); -fx-border-width: 0 0 1 0;");

        lblSessionFormat = new Label("Format: —");
        lblSessionFormat.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
        lblSessionFormat.getStyleClass().add("session-format");

        lblSessionFiles = new Label("Files: 0/0 processed");
        lblSessionFiles.setStyle("-fx-font-size: 11.5px;");
        lblSessionFiles.getStyleClass().add("session-files");

        lblSessionItems = new Label("Items: calculating…");
        lblSessionItems.setStyle("-fx-font-size: 11.5px;");
        lblSessionItems.getStyleClass().add("session-items");

        lblSessionInfo = new Label("Output: —");
        lblSessionInfo.setStyle("-fx-font-size: 11px; -fx-wrap-text: true;");
        lblSessionInfo.getStyleClass().add("session-info");

        sessionInfo.getChildren().addAll(lblSessionFormat, lblSessionFiles, lblSessionItems, lblSessionInfo);

        // Tree
        folderTreeView = new TreeView<>();
        folderTreeView.getStyleClass().add("conversion-folder-tree");
        folderTreeView.setShowRoot(false);
        folderTreeView.setEditable(false);
        VBox.setVgrow(folderTreeView, Priority.ALWAYS);

        rootItem = new TreeItem<>("Root");
        rootItem.setExpanded(true);
        folderTreeView.setRoot(rootItem);

        // Custom cell factory
        folderTreeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    // Expected format: "STATUS|OriginalName|ProgressSuffix"
                    // E.g. "ACTIVE|Inbox| (5/10)"
                    String[] parts = item.split("\\|", 3);
                    String status = parts.length > 0 ? parts[0] : "PENDING";
                    String name   = parts.length > 1 ? parts[1] : item;
                    String suffix = parts.length > 2 ? parts[2] : "";
                    
                    setText(null);

                    HBox row = new HBox(6);
                    row.setAlignment(Pos.CENTER_LEFT);

                    Label iconL;
                    String textStyle;
                    switch (status) {
                        case "DONE":
                            iconL = MaterialIcons.icon(MaterialIcons.DONE, 13);
                            iconL.setStyle("-fx-text-fill: #34d399; -fx-font-size: 13px;");
                            textStyle = "-fx-text-fill: #34d399; -fx-font-size: 13px;";
                            break;
                        case "ERROR":
                            iconL = MaterialIcons.icon(MaterialIcons.ERROR, 13);
                            iconL.setStyle("-fx-text-fill: #f87171; -fx-font-size: 13px;");
                            textStyle = "-fx-text-fill: #f87171; -fx-font-size: 13px;";
                            break;
                        case "ACTIVE":
                            iconL = MaterialIcons.icon(MaterialIcons.PLAY_ARROW, 13);
                            iconL.setStyle("-fx-text-fill: #fbbf24; -fx-font-size: 13px;");
                            textStyle = "-fx-text-fill: #fbbf24; -fx-font-weight: 700; -fx-font-size: 13px;";
                            break;
                        case "FILE":
                            iconL = MaterialIcons.icon(MaterialIcons.INVENTORY_2, 14);
                            iconL.setStyle("-fx-text-fill: #0ea5e9; -fx-font-size: 14px;");
                            textStyle = "-fx-text-fill: " + (isDarkMode ? "#7dd3fc" : "#0284c7") + "; -fx-font-weight: 700; -fx-font-size: 13px;";
                            break;
                        default: // PENDING
                            iconL = MaterialIcons.icon(MaterialIcons.FOLDER, 13);
                            iconL.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
                            textStyle = "-fx-text-fill: " + (isDarkMode ? "#94a3b8" : "#64748b") + "; -fx-font-size: 13px;";
                    }

                    Label nameL = new Label(name + suffix);
                    nameL.setStyle(textStyle);
                    row.getChildren().addAll(iconL, nameL);
                    setGraphic(row);
                    setStyle("-fx-padding: 3px 8px; -fx-background-color: transparent;");
                }
            }
        });

        this.getChildren().addAll(header, sessionInfo, folderTreeView);
    }

    public void setSessionInfo(String format, String files, String items, String info) {
        Platform.runLater(() -> {
            if (format != null) lblSessionFormat.setText(format);
            if (files != null) lblSessionFiles.setText(files);
            if (items != null) lblSessionItems.setText(items);
            if (info != null) lblSessionInfo.setText(info);
        });
    }

    public void updateTreeItemStatus(String folderKey, String status, int success, int skipped, int failed, int total) {
        Platform.runLater(() -> {
            TreeItem<String> item = folderNodeMap.get(folderKey);
            if (item != null) {
                // Item value is formatted as "STATUS|OriginalName|Suffix"
                String val = item.getValue();
                String[] parts = val.split("\\|", 3);
                String name = parts.length > 1 ? parts[1] : val;
                
                String newSuffix = "";
                if (total > 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(" (");
                    if (failed > 0 || skipped > 0) {
                        sb.append(success).append(" success");
                        if (failed > 0) {
                            sb.append(", ").append(failed).append(" failed");
                        }
                        if (skipped > 0) {
                            sb.append(", ").append(skipped).append(" skipped");
                        }
                    } else {
                        sb.append(success);
                    }
                    sb.append("/").append(total).append(")");
                    newSuffix = sb.toString();
                } else if ("DONE".equals(status)) {
                    newSuffix = " (Completed)";
                }
                
                item.setValue(status + "|" + name + "|" + newSuffix);
                folderTreeView.refresh();
            }
        });
    }

    public void markAllComplete() {
        Platform.runLater(() -> {
            for (TreeItem<String> item : rootItem.getChildren()) {
                String val = item.getValue();
                if (val.startsWith("ACTIVE|")) {
                    item.setValue(val.replace("ACTIVE|", "DONE|"));
                }
            }
            folderTreeView.refresh();
        });
    }
    
    public void addFolderNode(String key, TreeItem<String> node) {
        folderNodeMap.put(key, node);
    }

    public TreeItem<String> getRootItem() {
        return rootItem;
    }

    public void setRootItem(TreeItem<String> root) {
        this.rootItem = root;
        folderTreeView.setRoot(rootItem);
    }

    public void setFolderNodeMap(java.util.Map<String, TreeItem<String>> map) {
        this.folderNodeMap.clear();
        this.folderNodeMap.putAll(map);
    }
}
