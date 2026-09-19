package com.pstconverter.view.conversion;

import com.pstconverter.util.MaterialIcons;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ConversionLogPanel extends VBox {

    private TextArea logArea;

    public ConversionLogPanel() {
        super(6);
        VBox.setVgrow(this, Priority.ALWAYS);

        HBox logHeader = new HBox(6);
        logHeader.setAlignment(Pos.CENTER_LEFT);
        Label logIcon = MaterialIcons.icon(MaterialIcons.TERMINAL, 13);
        logIcon.getStyleClass().add("session-files");
        Label logTitle = new Label("CONVERSION LOG");
        logTitle.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 800;");
        logTitle.getStyleClass().add("session-files");
        logHeader.getChildren().addAll(logIcon, logTitle);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.getStyleClass().add("conversion-log");
        logArea.setStyle("-fx-font-family: 'Menlo', 'Consolas', 'Courier New', monospace; -fx-font-size: 11.5px;");
        logArea.setMinHeight(85);
        logArea.setPrefHeight(115);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        this.getChildren().addAll(logHeader, logArea);
        this.setMinHeight(115);
    }

    public void appendLog(String message) {
        String baseMessage;
        if (!message.startsWith("[")) {
            String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
            baseMessage = "[" + ts + "]  " + message;
        } else {
            baseMessage = message;
        }
        
        final String finalMessage = baseMessage.endsWith("\n") ? baseMessage : baseMessage + "\n";
        
        Platform.runLater(() -> {
            logArea.appendText(finalMessage);
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    public void appendError(String error) {
        appendLog("[ERROR] " + error);
    }
}
