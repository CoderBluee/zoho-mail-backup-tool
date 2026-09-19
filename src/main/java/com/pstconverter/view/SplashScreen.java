package com.pstconverter.view;

import com.pstconverter.config.BrandConfig;
import com.pstconverter.util.MaterialIcons;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Modern High-End Startup Splash Screen for Prism IMAP Backup Tool.
 * Features glowing brand emblem, SaaS version badges, dynamic status steps,
 * animated progress bar, and smooth fade transitions.
 */
public class SplashScreen {

    private final Stage stage;
    private final Label statusLabel;
    private final Timeline timeline;

    public SplashScreen() {
        stage = new Stage(StageStyle.TRANSPARENT);

        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(32, 40, 26, 40));
        card.setPrefSize(540, 330);
        card.setMaxSize(540, 330);
        card.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #090d16, #0f172a, #162036); " +
            "-fx-background-radius: 20px; -fx-border-radius: 20px; " +
            "-fx-border-color: linear-gradient(to bottom right, rgba(14, 165, 233, 0.7), rgba(99, 102, 241, 0.4)); " +
            "-fx-border-width: 1.5px; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.75), 35, 0, 0, 12);"
        );

        // Header Row: Brand Emblem + Title + Subtitle
        StackPane brandEmblem = new StackPane();
        brandEmblem.setPrefSize(54, 54);
        brandEmblem.setMinSize(54, 54);
        brandEmblem.setMaxSize(54, 54);
        brandEmblem.setStyle(
            "-fx-background-color: rgba(15, 23, 42, 0.6); " +
            "-fx-background-radius: 14px; -fx-border-radius: 14px; " +
            "-fx-border-color: rgba(14, 165, 233, 0.4); -fx-border-width: 1.2px; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(14, 165, 233, 0.5), 18, 0, 0, 3);"
        );

        javafx.scene.image.ImageView logoImgView = new javafx.scene.image.ImageView();
        try {
            javafx.scene.image.Image logo = new javafx.scene.image.Image(getClass().getResourceAsStream("/images/logo.png"));
            logoImgView.setImage(logo);
            logoImgView.setFitWidth(44);
            logoImgView.setFitHeight(44);
            logoImgView.setPreserveRatio(true);
            logoImgView.setSmooth(true);
            if (stage != null && stage.getIcons().isEmpty()) {
                stage.getIcons().add(logo);
            }
        } catch (Exception ignored) {}
        brandEmblem.getChildren().add(logoImgView);

        VBox titleBox = new VBox(3);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label titleLbl = new Label(BrandConfig.TOOL_NAME);
        titleLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 21));
        titleLbl.setStyle("-fx-text-fill: #ffffff; -fx-effect: dropshadow(three-pass-box, rgba(14, 165, 233, 0.3), 8, 0, 0, 2);");

        Label subtitleLbl = new Label("Universal Mailbox Migration & Cloud Sync Platform");
        subtitleLbl.setFont(Font.font("Segoe UI", 11.5));
        subtitleLbl.setStyle("-fx-text-fill: #94a3b8;");
        titleBox.getChildren().addAll(titleLbl, subtitleLbl);

        HBox headerBox = new HBox(16, brandEmblem, titleBox);
        headerBox.setAlignment(Pos.CENTER);

        // Badges Row - Centered
        HBox badgesBox = new HBox(8);
        badgesBox.setAlignment(Pos.CENTER);

        Label badgeEnterprise = new Label("ENTERPRISE v2026");
        badgeEnterprise.setStyle("-fx-background-color: rgba(16, 185, 129, 0.15); -fx-text-fill: #10b981; -fx-border-color: rgba(16, 185, 129, 0.4); -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 3px 8px;");

        Label badgeImap = new Label("IMAP4rev1 • SSL/TLS");
        badgeImap.setStyle("-fx-background-color: rgba(14, 165, 233, 0.15); -fx-text-fill: #38bdf8; -fx-border-color: rgba(14, 165, 233, 0.4); -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 3px 8px;");

        Label badgeFormats = new Label("17 TARGET FORMATS");
        badgeFormats.setStyle("-fx-background-color: rgba(99, 102, 241, 0.15); -fx-text-fill: #818cf8; -fx-border-color: rgba(99, 102, 241, 0.4); -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 3px 8px;");

        badgesBox.getChildren().addAll(badgeEnterprise, badgeImap, badgeFormats);

        // Center feature highlights card - Centered
        HBox featureCard = new HBox(10);
        featureCard.setAlignment(Pos.CENTER);
        featureCard.setPadding(new Insets(10, 20, 10, 20));
        featureCard.setStyle("-fx-background-color: rgba(15, 23, 42, 0.65); -fx-background-radius: 10px; -fx-border-color: rgba(148, 163, 184, 0.15); -fx-border-radius: 10px;");
        
        Label featureIcon = MaterialIcons.icon(MaterialIcons.LOCK, 18);
        featureIcon.setFont(Font.font("Material Icons", 18));
        featureIcon.setStyle("-fx-text-fill: #38bdf8;");
        Label featureText = new Label("End-to-End Encrypted IMAP Extraction • Smart Deduplication Engine");
        featureText.setFont(Font.font("Segoe UI", 11));
        featureText.setStyle("-fx-text-fill: #cbd5e1;");
        featureCard.getChildren().addAll(featureIcon, featureText);

        // Progress section with centered alignment and moving laser sheen
        VBox progressBox = new VBox(9);
        progressBox.setAlignment(Pos.CENTER);

        statusLabel = new Label("Initializing Chilkat security runtime...");
        statusLabel.setFont(Font.font("Segoe UI", 11.5));
        statusLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: 600;");
        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        double totalBarW = 460;
        StackPane progressTrack = new StackPane();
        progressTrack.setPrefSize(totalBarW, 7);
        progressTrack.setMinSize(totalBarW, 7);
        progressTrack.setMaxSize(totalBarW, 7);
        progressTrack.setAlignment(Pos.CENTER_LEFT);
        progressTrack.setStyle("-fx-background-color: rgba(30, 41, 59, 0.85); -fx-background-radius: 5px; -fx-border-color: rgba(148, 163, 184, 0.2); -fx-border-radius: 5px;");

        Region progressFill = new Region();
        progressFill.setPrefHeight(7);
        progressFill.setMinHeight(7);
        progressFill.setMaxHeight(7);
        progressFill.setPrefWidth(25);
        progressFill.setStyle(
            "-fx-background-color: linear-gradient(to right, #0ea5e9, #06b6d4, #10b981); " +
            "-fx-background-radius: 5px; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(14, 165, 233, 0.6), 8, 0, 0, 1);"
        );

        // Active glowing shimmer pulse that sweeps continuously from left to right
        Region progressShimmer = new Region();
        progressShimmer.setPrefSize(90, 7);
        progressShimmer.setMinHeight(7);
        progressShimmer.setMaxHeight(7);
        progressShimmer.setStyle(
            "-fx-background-color: linear-gradient(to right, transparent, rgba(255, 255, 255, 0.85), transparent); " +
            "-fx-background-radius: 5px;"
        );
        progressTrack.getChildren().addAll(progressFill, progressShimmer);

        javafx.scene.shape.Rectangle trackClip = new javafx.scene.shape.Rectangle(totalBarW, 7);
        trackClip.setArcWidth(10);
        trackClip.setArcHeight(10);
        progressTrack.setClip(trackClip);

        TranslateTransition shimmerWave = new TranslateTransition(Duration.millis(1300), progressShimmer);
        shimmerWave.setFromX(-90);
        shimmerWave.setToX(totalBarW);
        shimmerWave.setCycleCount(Animation.INDEFINITE);
        shimmerWave.setInterpolator(Interpolator.LINEAR);
        shimmerWave.play();

        HBox footerBox = new HBox();
        footerBox.setAlignment(Pos.CENTER);
        footerBox.setMaxWidth(totalBarW);
        Label copyrightLbl = new Label("© 2026 " + BrandConfig.COMPANY_NAME + " Inc.");
        copyrightLbl.setFont(Font.font("Segoe UI", 10));
        copyrightLbl.setStyle("-fx-text-fill: #64748b;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label sysLbl = new Label("IMAP Engine v2026.09 (64-bit)");
        sysLbl.setFont(Font.font("Segoe UI", 10));
        sysLbl.setStyle("-fx-text-fill: #64748b;");
        footerBox.getChildren().addAll(copyrightLbl, sp, sysLbl);

        progressBox.getChildren().addAll(statusLabel, progressTrack, footerBox);

        card.getChildren().addAll(headerBox, badgesBox, featureCard, progressBox);

        StackPane root = new StackPane(card);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, 580, 370);
        scene.setFill(Color.TRANSPARENT);
        try {
            scene.getStylesheets().add(getClass().getResource("/style-dark.css").toExternalForm());
        } catch (Exception ignored) {}
        stage.setScene(scene);

        timeline = new Timeline(
            new KeyFrame(Duration.millis(0), 
                e -> statusLabel.setText("Initializing Chilkat security runtime & SSL/TLS engine..."),
                new KeyValue(progressFill.prefWidthProperty(), 25)
            ),
            new KeyFrame(Duration.millis(750), 
                e -> statusLabel.setText("Loading MaterialFX & Prism modern UI design system..."),
                new KeyValue(progressFill.prefWidthProperty(), 155, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(Duration.millis(1550), 
                e -> statusLabel.setText("Synchronizing SQLite configuration & migration store..."),
                new KeyValue(progressFill.prefWidthProperty(), 290, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(Duration.millis(2300), 
                e -> statusLabel.setText("Initializing Prism IMAP multi-account connector..."),
                new KeyValue(progressFill.prefWidthProperty(), 410, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(Duration.millis(3000), 
                e -> statusLabel.setText("Prism IMAP Backup Tool is ready!"),
                new KeyValue(progressFill.prefWidthProperty(), totalBarW, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(Duration.millis(3250))
        );
    }

    public void showAndAnimate(java.util.function.Consumer<Runnable> onAppReadyToLaunch) {
        stage.centerOnScreen();
        stage.show();

        timeline.setOnFinished(e -> {
            if (onAppReadyToLaunch != null) {
                // Splash timeline complete! Prepare the main app while splash is still 100% visible
                onAppReadyToLaunch.accept(() -> {
                    // Main application is now loaded and displayed! Smoothly cross-fade and close splash
                    FadeTransition ft = new FadeTransition(Duration.millis(350), stage.getScene().getRoot());
                    ft.setFromValue(1.0);
                    ft.setToValue(0.0);
                    ft.setOnFinished(ev -> stage.close());
                    ft.play();
                });
            } else {
                stage.close();
            }
        });
        timeline.play();
    }
}
