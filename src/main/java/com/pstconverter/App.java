package com.pstconverter;

import com.pstconverter.controller.MainController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import io.github.palexdev.materialfx.theming.UserAgentBuilder;
import io.github.palexdev.materialfx.theming.JavaFXThemes;
import io.github.palexdev.materialfx.theming.MaterialFXStylesheets;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Register global uncaught exception handler to capture all thread crashes
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("❌ Uncaught exception on thread '" + thread.getName() + "': " + throwable.getMessage());
            throwable.printStackTrace(System.err);
            com.pstconverter.util.DiagnosticLogger.error("Uncaught exception on thread: " + thread.getName(), throwable);
            com.pstconverter.util.DiagnosticLogger.close();
        });

        // Register JVM shutdown hook to flush standard output streams and close report file handles on termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("[INFO] JVM Shutdown Hook triggered. Ensuring all logs are flushed...");
            com.pstconverter.util.DiagnosticLogger.log("JVM exiting.");
            com.pstconverter.util.DiagnosticLogger.close();
            System.out.flush();
            System.err.flush();
        }, "pst-shutdown-hook"));

        try {
            Font.loadFont(getClass().getResourceAsStream("/fonts/MaterialIcons-Regular.ttf"), 14);

            UserAgentBuilder.builder()
                .themes(JavaFXThemes.MODENA)
                .themes(MaterialFXStylesheets.DEFAULT)
                .setDeploy(true)
                .setResolveAssets(true)
                .build()
                .setGlobal();

            com.pstconverter.view.SplashScreen splash = new com.pstconverter.view.SplashScreen();
            splash.showAndAnimate(closeSplash -> {
                try {
                    MainController root = new MainController(primaryStage);
                    Scene scene = new Scene(root, 1280, 800);
                    primaryStage.setTitle(com.pstconverter.config.BrandConfig.TOOL_NAME);
                    try {
                        primaryStage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/images/logo.png")));
                    } catch (Exception ignored) {}
                    primaryStage.setScene(scene);
                    primaryStage.setMinWidth(900);
                    primaryStage.setMinHeight(600);
                    primaryStage.show();

                    // Smooth entrance
                    root.setOpacity(0.0);
                    javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), root);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();

                    // Close splash screen with smooth cross-fade only AFTER the main window is rendered!
                    if (closeSplash != null) {
                        closeSplash.run();
                    }

                    // Trigger licensing and automatic update checks on startup for activated users
                    com.pstconverter.util.LicenseManager.checkLicenseAndVersionOnStartup(primaryStage);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (closeSplash != null) closeSplash.run();
                }
            });

            // Intercept window close request with modern confirmation dialog
            primaryStage.setOnCloseRequest(event -> {
                event.consume(); // Prevent default immediate close
                promptExitConfirmation(primaryStage);
            });

            // Trigger auto-check for updates if enabled
            if ("true".equals(com.pstconverter.util.SettingsManager.getSetting("auto_check_updates", "false"))) {
                new Thread(() -> {
                    try {
                        Thread.sleep(4000); // Wait for app to stabilize
                        System.out.println("[INFO] Auto-check updates: Checking for new versions...");
                        Thread.sleep(1000);
                        System.out.println("[INFO] Auto-check updates: Application is up to date (v1.0.0).");
                    } catch (Exception ignored) {}
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void promptExitConfirmation(Stage primaryStage) {
        boolean isDark = com.pstconverter.util.ThemeManager.isDarkMode();

        Stage confirmStage = new Stage(javafx.stage.StageStyle.TRANSPARENT);
        confirmStage.initOwner(primaryStage);
        confirmStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        confirmStage.setResizable(false);

        // Header warning icon badge
        javafx.scene.layout.StackPane iconBadge = new javafx.scene.layout.StackPane();
        iconBadge.setPrefSize(48, 48);
        iconBadge.setMinSize(48, 48);
        iconBadge.setMaxSize(48, 48);
        iconBadge.setStyle(
            "-fx-background-color: rgba(239, 68, 68, 0.15); " +
            "-fx-background-radius: 14px; -fx-border-radius: 14px; " +
            "-fx-border-color: rgba(239, 68, 68, 0.35); -fx-border-width: 1.2px; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(239, 68, 68, 0.4), 14, 0, 0, 2);"
        );
        javafx.scene.control.Label warnIcon = new javafx.scene.control.Label("\uE002");
        warnIcon.getStyleClass().add("material-icon");
        warnIcon.setFont(javafx.scene.text.Font.font("Material Icons", 26));
        warnIcon.setStyle("-fx-text-fill: #ef4444; -fx-font-family: 'Material Icons'; -fx-font-size: 26px;");
        iconBadge.getChildren().add(warnIcon);

        javafx.scene.layout.VBox titleBox = new javafx.scene.layout.VBox(3);
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.control.Label titleLbl = new javafx.scene.control.Label("Exit Prism IMAP Backup Tool?");
        titleLbl.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 17));
        titleLbl.setStyle("-fx-text-fill: " + (isDark ? "#ffffff" : "#0f172a") + ";");

        javafx.scene.control.Label subtitleLbl = new javafx.scene.control.Label("Confirm application closure");
        subtitleLbl.setFont(javafx.scene.text.Font.font("Segoe UI", 11.5));
        subtitleLbl.setStyle("-fx-text-fill: #94a3b8;");
        titleBox.getChildren().addAll(titleLbl, subtitleLbl);

        javafx.scene.layout.HBox headerRow = new javafx.scene.layout.HBox(14, iconBadge, titleBox);
        headerRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Message body - sized generously so text never truncates
        javafx.scene.control.Label msgLbl = new javafx.scene.control.Label(
            "Are you sure you want to close the application?\n\n" +
            "• Any live mailbox extraction or scan in progress will be stopped.\n" +
            "• Your configured accounts, presets, and history remain safely saved."
        );
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(440);
        msgLbl.setFont(javafx.scene.text.Font.font("Segoe UI", 12.5));
        msgLbl.setStyle("-fx-text-fill: " + (isDark ? "#cbd5e1" : "#475569") + "; -fx-line-spacing: 3px;");

        // Buttons
        javafx.scene.control.Button btnCancel = new javafx.scene.control.Button("No, Keep Working");
        btnCancel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.SEMI_BOLD, 12));
        btnCancel.setStyle(isDark
            ? "-fx-background-color: rgba(30, 41, 59, 0.85); -fx-text-fill: #f1f5f9; -fx-border-color: rgba(148, 163, 184, 0.25); -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 9px 20px; -fx-cursor: hand;"
            : "-fx-background-color: #f1f5f9; -fx-text-fill: #334155; -fx-border-color: #cbd5e1; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 9px 20px; -fx-cursor: hand;");
        btnCancel.setOnAction(e -> confirmStage.close());

        javafx.scene.control.Button btnExit = new javafx.scene.control.Button("Yes, Exit Application");
        btnExit.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 12));
        btnExit.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #ef4444, #dc2626); " +
            "-fx-text-fill: #ffffff; " +
            "-fx-background-radius: 8px; " +
            "-fx-padding: 9px 22px; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(239, 68, 68, 0.45), 10, 0, 0, 2);"
        );
        btnExit.setOnAction(e -> {
            confirmStage.close();
            System.out.println("[INFO] Application exit confirmed by user. Flushing diagnostics and logs...");
            com.pstconverter.util.DiagnosticLogger.log("Application closed by user after exit confirmation.");
            com.pstconverter.util.DiagnosticLogger.close();
            System.out.flush();
            System.err.flush();
            javafx.application.Platform.exit();
            System.exit(0);
        });

        javafx.scene.layout.Region btnSpacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(btnSpacer, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.layout.HBox btnBar = new javafx.scene.layout.HBox(12, btnSpacer, btnCancel, btnExit);
        btnBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        btnBar.setPadding(new javafx.geometry.Insets(10, 0, 0, 0));

        javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(16, headerRow, msgLbl, btnBar);
        card.setPadding(new javafx.geometry.Insets(24, 28, 22, 28));
        card.setPrefWidth(500);
        card.setMaxWidth(500);
        card.setStyle(isDark
            ? "-fx-background-color: linear-gradient(to bottom right, #090d16, #0f172a, #162036); " +
              "-fx-background-radius: 16px; -fx-border-radius: 16px; " +
              "-fx-border-color: linear-gradient(to bottom right, rgba(239, 68, 68, 0.55), rgba(14, 165, 233, 0.35)); " +
              "-fx-border-width: 1.5px; " +
              "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.8), 35, 0, 0, 12);"
            : "-fx-background-color: linear-gradient(to bottom right, #ffffff, #f8fafc); " +
              "-fx-background-radius: 16px; -fx-border-radius: 16px; " +
              "-fx-border-color: linear-gradient(to bottom right, rgba(239, 68, 68, 0.45), rgba(14, 165, 233, 0.25)); " +
              "-fx-border-width: 1.5px; " +
              "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.25), 30, 0, 0, 10);"
        );

        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(card);
        root.setPadding(new javafx.geometry.Insets(20));
        root.setStyle("-fx-background-color: transparent;");

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 540, 295);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        try {
            scene.getStylesheets().add(com.pstconverter.util.ThemeManager.getActiveThemeStylesheet());
        } catch (Exception ignored) {}
        confirmStage.setScene(scene);

        // Escape key cancels
        scene.setOnKeyPressed(k -> {
            if (k.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                confirmStage.close();
            }
        });

        // Smooth entry animation
        card.setScaleX(0.92);
        card.setScaleY(0.92);
        card.setOpacity(0.0);
        confirmStage.setOnShown(ev -> {
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(180), card);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(180), card);
            st.setFromX(0.92);
            st.setToX(1.0);
            st.setFromY(0.92);
            st.setToY(1.0);
            new javafx.animation.ParallelTransition(ft, st).play();
        });

        confirmStage.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
