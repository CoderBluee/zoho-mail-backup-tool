package com.pstconverter.view.filter;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.enums.FloatMode;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;

public class AttachmentFilterPanel extends VBox {

    private final Runnable validationCallback;
    private final BooleanSupplier isDarkModeSupplier;

    private MFXTextField tfMaxAttachmentSize;
    private MFXComboBox<String> cmbAttachSizeUnit;
    private MFXTextField tfExcludeAttachTypes;
    private MFXTextField tfIncludeAttachTypes;
    private Label lblAttachmentValidationError;

    public AttachmentFilterPanel(Runnable validationCallback, BooleanSupplier isDarkModeSupplier) {
        this.validationCallback = validationCallback;
        this.isDarkModeSupplier = isDarkModeSupplier;
        buildUI();
    }

    private void styleModernTextField(MFXTextField tf) {
        tf.setFloatMode(FloatMode.DISABLED);
        tf.setPrefWidth(200);
        tf.setMaxWidth(600);
    }

    private void styleModernComboBox(MFXComboBox<?> cmb) {
        cmb.getStyleClass().add("filter-combo");
        cmb.setFloatMode(FloatMode.DISABLED);
        cmb.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (!cmb.isShowing()) {
                cmb.requestFocus();
                cmb.show();
                e.consume();
            }
        });
    }

    private void buildUI() {
        this.setSpacing(8);
        this.setPadding(new Insets(4));

        Label header = new Label("📎  Attachments");
        header.getStyleClass().add("filter-section-header");

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        Label lblSize = new Label("Maximum Attachment Size");
        lblSize.getStyleClass().add("filter-group-label");

        HBox sizeBox = new HBox(8);
        sizeBox.setAlignment(Pos.CENTER_LEFT);

        tfMaxAttachmentSize = new MFXTextField();
        styleModernTextField(tfMaxAttachmentSize);
        tfMaxAttachmentSize.setPromptText("Enter max size (0 for unlimited)...");
        tfMaxAttachmentSize.getStyleClass().add("filter-textfield");
        tfMaxAttachmentSize.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tfMaxAttachmentSize, Priority.ALWAYS);

        tfMaxAttachmentSize.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.matches("\\d*")) {
                tfMaxAttachmentSize.setText(newVal.replaceAll("[^\\d]", ""));
            }
            validate();
            validationCallback.run();
        });

        cmbAttachSizeUnit = new MFXComboBox<>(FXCollections.observableArrayList("KB", "MB", "GB"));
        styleModernComboBox(cmbAttachSizeUnit);
        cmbAttachSizeUnit.getSelectionModel().selectItem("MB");
        cmbAttachSizeUnit.setPrefWidth(90);
        cmbAttachSizeUnit.valueProperty().addListener((o, ov, nv) -> validationCallback.run());

        sizeBox.getChildren().addAll(tfMaxAttachmentSize, cmbAttachSizeUnit);

        Label lblExcTypes = new Label("Exclude Attachment Extensions");
        lblExcTypes.getStyleClass().add("filter-group-label");
        tfExcludeAttachTypes = new MFXTextField();
        styleModernTextField(tfExcludeAttachTypes);
        tfExcludeAttachTypes.setPromptText("e.g. .exe, .zip, .msi");
        tfExcludeAttachTypes.getStyleClass().add("filter-textfield");
        tfExcludeAttachTypes.setMaxWidth(Double.MAX_VALUE);
        tfExcludeAttachTypes.textProperty().addListener((o, ov, nv) -> {
            validate();
            validationCallback.run();
        });
        FlowPane excPresets = createExtensionPresets(tfExcludeAttachTypes, new String[]{".exe", ".zip", ".msi", ".bat", ".dmg", ".rar"});

        Label lblIncTypes = new Label("Include Attachment Extensions");
        lblIncTypes.getStyleClass().add("filter-group-label");
        tfIncludeAttachTypes = new MFXTextField();
        styleModernTextField(tfIncludeAttachTypes);
        tfIncludeAttachTypes.setPromptText("e.g. .pdf, .docx, .xlsx");
        tfIncludeAttachTypes.getStyleClass().add("filter-textfield");
        tfIncludeAttachTypes.setMaxWidth(Double.MAX_VALUE);
        tfIncludeAttachTypes.textProperty().addListener((o, ov, nv) -> {
            validate();
            validationCallback.run();
        });
        FlowPane incPresets = createExtensionPresets(tfIncludeAttachTypes, new String[]{".pdf", ".docx", ".xlsx", ".png", ".jpg", ".txt"});

        lblAttachmentValidationError = new Label("");
        lblAttachmentValidationError.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px; -fx-font-weight: bold;");
        lblAttachmentValidationError.setVisible(false);
        lblAttachmentValidationError.setManaged(false);
        lblAttachmentValidationError.setWrapText(true);

        content.getChildren().addAll(
                lblSize, sizeBox,
                new Separator(),
                lblExcTypes, tfExcludeAttachTypes, excPresets,
                new Separator(),
                lblIncTypes, tfIncludeAttachTypes, incPresets,
                lblAttachmentValidationError
        );
        this.getChildren().addAll(header, content);
    }

    private FlowPane createExtensionPresets(MFXTextField tf, String[] presets) {
        FlowPane pane = new FlowPane(6, 6);
        pane.setPadding(new Insets(4, 0, 8, 0));
        for (String preset : presets) {
            MFXButton btn = new MFXButton(preset);
            btn.getStyleClass().addAll("filter-chip");
            btn.setStyle("-fx-font-size: 10px; -fx-padding: 3px 8px; -fx-cursor: hand;");
            btn.setOnAction(e -> {
                String text = tf.getText().trim();
                List<String> items = new ArrayList<>();
                if (!text.isEmpty()) {
                    for (String s : text.split(",")) {
                        String clean = s.trim();
                        if (!clean.isEmpty()) items.add(clean);
                    }
                }
                if (items.contains(preset)) {
                    items.remove(preset);
                } else {
                    items.add(preset);
                }
                tf.setText(String.join(", ", items));
            });
            pane.getChildren().add(btn);
        }
        return pane;
    }

    public int getMaxAttachmentSize() {
        if (tfMaxAttachmentSize == null) return 0;
        try {
            String val = tfMaxAttachmentSize.getText().trim();
            if (val.isEmpty()) return 0;
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean validate() {
        boolean isValid = true;
        String errorMessage = null;

        // 1. Check max attachment size
        String sizeText = tfMaxAttachmentSize != null ? tfMaxAttachmentSize.getText().trim() : "";
        if (!sizeText.isEmpty()) {
            if (!sizeText.matches("\\d+")) {
                isValid = false;
                errorMessage = "Maximum Attachment Size must contain numbers only.";
            } else {
                try {
                    long val = Long.parseLong(sizeText);
                    if (val < 0) {
                        isValid = false;
                        errorMessage = "Maximum Attachment Size cannot be negative.";
                    }
                } catch (NumberFormatException e) {
                    isValid = false;
                    errorMessage = "Maximum Attachment Size value is invalid.";
                }
            }
        }

        // 2. Check exclude attachment extensions format
        if (isValid) {
            String excText = tfExcludeAttachTypes != null ? tfExcludeAttachTypes.getText() : "";
            String badExcToken = validateExtensionsList(excText);
            if (badExcToken != null) {
                isValid = false;
                errorMessage = "Invalid extension format: '" + badExcToken + "' in Exclude Extensions. Extensions must be 1-10 alphanumeric characters (e.g. .exe, .zip).";
            }
        }

        // 3. Check include attachment extensions format
        if (isValid) {
            String incText = tfIncludeAttachTypes != null ? tfIncludeAttachTypes.getText() : "";
            String badIncToken = validateExtensionsList(incText);
            if (badIncToken != null) {
                isValid = false;
                errorMessage = "Invalid extension format: '" + badIncToken + "' in Include Extensions. Extensions must be 1-10 alphanumeric characters (e.g. .pdf, .docx).";
            }
        }

        // 4. Check duplicate / overlapping extension conflicts
        if (isValid) {
            String excText = tfExcludeAttachTypes != null ? tfExcludeAttachTypes.getText() : "";
            String incText = tfIncludeAttachTypes != null ? tfIncludeAttachTypes.getText() : "";
            List<String> excList = getNormalizedExtensionList(excText);
            List<String> incList = getNormalizedExtensionList(incText);

            List<String> duplicates = new ArrayList<>();
            for (String ext : incList) {
                if (excList.contains(ext)) {
                    duplicates.add("." + ext);
                }
            }

            if (!duplicates.isEmpty()) {
                isValid = false;
                errorMessage = "Extension(s) " + String.join(", ", duplicates) + " cannot be specified in both Include and Exclude lists simultaneously.";
            }
        }

        if (!isValid && errorMessage != null) {
            lblAttachmentValidationError.setText(errorMessage);
            lblAttachmentValidationError.setVisible(true);
            lblAttachmentValidationError.setManaged(true);
        } else {
            lblAttachmentValidationError.setVisible(false);
            lblAttachmentValidationError.setManaged(false);
        }

        return isValid;
    }

    private List<String> getNormalizedExtensionList(String rawInput) {
        List<String> result = new ArrayList<>();
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return result;
        }
        String[] tokens = rawInput.split(",");
        for (String token : tokens) {
            String clean = token.trim().toLowerCase();
            if (clean.isEmpty()) continue;
            if (clean.startsWith(".")) {
                clean = clean.substring(1);
            }
            if (!clean.isEmpty() && !result.contains(clean)) {
                result.add(clean);
            }
        }
        return result;
    }

    private String validateExtensionsList(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return null;
        }
        String[] tokens = rawInput.split(",");
        for (String token : tokens) {
            String clean = token.trim();
            if (clean.isEmpty()) continue;
            if (!clean.matches("^\\.?[a-zA-Z0-9]{1,10}$")) {
                return clean;
            }
        }
        return null;
    }

    public void saveProperties(Properties props) {
        props.setProperty("attachment.mode", "Any Attachment State");
        props.setProperty("attachment.maxSize", String.valueOf(getMaxAttachmentSize()));
        if (cmbAttachSizeUnit.getValue() != null) props.setProperty("attachment.sizeUnit", cmbAttachSizeUnit.getValue());
        props.setProperty("attachment.excludeTypes", tfExcludeAttachTypes.getText().trim());
        props.setProperty("attachment.includeTypes", tfIncludeAttachTypes.getText().trim());
    }

    public void loadProperties(Properties props) {
        String maxSizeStr = props.getProperty("attachment.maxSize", "0");
        try {
            int maxSize = Integer.parseInt(maxSizeStr);
            if (maxSize > 0) {
                tfMaxAttachmentSize.setText(String.valueOf(maxSize));
                String unit = props.getProperty("attachment.sizeUnit", "MB");
                cmbAttachSizeUnit.setValue(unit);
            } else {
                tfMaxAttachmentSize.setText("");
            }
        } catch (Exception ignored) {
            tfMaxAttachmentSize.setText("");
        }
        
        tfExcludeAttachTypes.setText(props.getProperty("attachment.excludeTypes", ""));
        tfIncludeAttachTypes.setText(props.getProperty("attachment.includeTypes", ""));
        validate();
    }

    public MFXComboBox<String> getCmbAttachSizeUnit() { return cmbAttachSizeUnit; }
    public MFXTextField getTfExcludeAttachTypes() { return tfExcludeAttachTypes; }
    public MFXTextField getTfIncludeAttachTypes() { return tfIncludeAttachTypes; }
}
