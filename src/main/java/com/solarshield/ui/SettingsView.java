package com.solarshield.ui;

import com.solarshield.ai.AIInferenceService;
import com.solarshield.ai.MockAIModel;
import com.solarshield.ai.OpenCVFaultDetector;
import com.solarshield.database.DatabaseConnection;
import com.solarshield.database.DatabaseInitializer;
import com.solarshield.model.User;
import com.solarshield.util.AlertUtils;
import com.solarshield.util.OpenCVLoader;
import com.solarshield.util.SessionManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.opencv.videoio.VideoCapture;

import java.sql.Connection;

/**
 * System Settings, Diagnostics & Environment Health View.
 * Displays runtime hardware bindings, database connection health, OpenCV status,
 * camera availability, and AI engine switching.
 */
public class SettingsView extends ScrollPane {

    private final AIInferenceService aiInferenceService;

    private Label userLbl;
    private Label emailLbl;
    private Label roleLbl;
    private Label dbStatusLbl;
    private Label cvStatusLbl;
    private Label camStatusLbl;
    private Label aiModeLbl;
    private ComboBox<String> aiEngineCombo;

    public SettingsView(AIInferenceService aiInferenceService) {
        this.aiInferenceService = aiInferenceService;

        setFitToWidth(true);
        setStyle("-fx-background: #f1f5f9; -fx-background-color: transparent;");

        initUI();
        refreshStatus();
    }

    private void initUI() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(24));

        // 1. Header
        VBox header = new VBox(4);
        Label title = new Label("SYSTEM SETTINGS & ENVIRONMENT DIAGNOSTICS");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        Label subtitle = new Label("Inspect runtime hardware acceleration, database connectivity, and AI engine parameters");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        header.getChildren().addAll(title, subtitle);

        // 2. User Profile Card
        VBox userCard = new VBox(14);
        userCard.getStyleClass().add("card");

        Label uTitle = new Label("Active User Session");
        uTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        GridPane uGrid = new GridPane();
        uGrid.setHgap(16);
        uGrid.setVgap(8);

        userLbl = new Label();
        emailLbl = new Label();
        roleLbl = new Label();

        uGrid.add(new Label("Current Operator:"), 0, 0); uGrid.add(userLbl, 1, 0);
        uGrid.add(new Label("Email:"), 0, 1); uGrid.add(emailLbl, 1, 1);
        uGrid.add(new Label("Access Level:"), 0, 2); uGrid.add(roleLbl, 1, 2);
        uGrid.add(new Label("App Version:"), 0, 3); uGrid.add(new Label("SolarShield Enterprise Academic v1.0.0 (LTS)"), 1, 3);

        userCard.getChildren().addAll(uTitle, new Separator(), uGrid);

        // 3. AI Inference Engine Selection Card
        VBox aiCard = new VBox(14);
        aiCard.getStyleClass().add("card");

        Label aiTitle = new Label("AI Inference Engine Configuration");
        aiTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        HBox aiSelectBox = new HBox(14);
        aiSelectBox.setAlignment(Pos.CENTER_LEFT);

        Label aiLbl = new Label("Active Detection Engine: *");
        aiLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e293b;");

        aiEngineCombo = new ComboBox<>();
        aiEngineCombo.getItems().addAll(
                "OpenCV Computer Vision Heuristic Engine",
                "Demo AI / Fallback Model"
        );
        aiEngineCombo.setValue("OpenCV Computer Vision Heuristic Engine");
        aiEngineCombo.setPrefWidth(320);
        aiEngineCombo.setOnAction(e -> handleSwitchEngine());

        aiSelectBox.getChildren().addAll(aiLbl, aiEngineCombo);

        Label disclaimer = new Label(
                "NOTE: The fallback engine is clearly labeled as 'DEMO AI / FALLBACK MODEL'. " +
                "The OpenCV engine executes real computer vision analysis on the input image. " +
                "The FaultDetectionModel interface is structured so real trained ONNX/TensorFlow weights can be plugged in seamlessly."
        );
        disclaimer.setWrapText(true);
        disclaimer.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-font-style: italic;");

        aiCard.getChildren().addAll(aiTitle, new Separator(), aiSelectBox, disclaimer);

        // 4. System Diagnostics & Hardware Verification
        VBox diagCard = new VBox(14);
        diagCard.getStyleClass().add("card");

        Label diagTitle = new Label("System Diagnostics & Hardware Status");
        diagTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        GridPane dGrid = new GridPane();
        dGrid.setHgap(20);
        dGrid.setVgap(12);

        dbStatusLbl = new Label("Checking...");
        cvStatusLbl = new Label("Checking...");
        camStatusLbl = new Label("Checking...");

        Button testDbBtn = new Button("Test Database");
        testDbBtn.getStyleClass().add("btn-outline");
        testDbBtn.setOnAction(e -> testDatabase());

        Button testCvBtn = new Button("Test OpenCV");
        testCvBtn.getStyleClass().add("btn-outline");
        testCvBtn.setOnAction(e -> testOpenCV());

        Button testCamBtn = new Button("Test Camera");
        testCamBtn.getStyleClass().add("btn-outline");
        testCamBtn.setOnAction(e -> testCamera());

        dGrid.add(new Label("SQLite Database:"), 0, 0);
        dGrid.add(dbStatusLbl, 1, 0);
        dGrid.add(testDbBtn, 2, 0);

        dGrid.add(new Label("OpenCV Native Lib:"), 0, 1);
        dGrid.add(cvStatusLbl, 1, 1);
        dGrid.add(testCvBtn, 2, 1);

        dGrid.add(new Label("Webcam Device:"), 0, 2);
        dGrid.add(camStatusLbl, 1, 2);
        dGrid.add(testCamBtn, 2, 2);

        diagCard.getChildren().addAll(diagTitle, new Separator(), dGrid);

        // 5. Data Reset & Demo Seeder
        VBox dataCard = new VBox(14);
        dataCard.getStyleClass().add("card");

        Label dataTitle = new Label("Demonstration Data Tools");
        dataTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        Label dataDesc = new Label(
                "Populate default demonstration panels (SP-001 through SP-005) and sample synthetic test images for presentation."
        );
        dataDesc.setStyle("-fx-text-fill: #64748b;");

        HBox dataBtns = new HBox(14);
        Button seedBtn = new Button("🌱 Seed Default Demo Data");
        seedBtn.getStyleClass().add("btn-outline");
        seedBtn.setOnAction(e -> handleSeedData());

        Button genImagesBtn = new Button("🎨 Regenerate Sample Images");
        genImagesBtn.getStyleClass().add("btn-outline");
        genImagesBtn.setOnAction(e -> {
            com.solarshield.util.SampleImageGenerator.generateAllSamples();
            AlertUtils.showInfo("Sample Images", "Done", "6 synthetic test images generated in sample_images/");
        });

        dataBtns.getChildren().addAll(seedBtn, genImagesBtn);
        dataCard.getChildren().addAll(dataTitle, new Separator(), dataDesc, dataBtns);

        root.getChildren().addAll(header, userCard, aiCard, diagCard, dataCard);
        setContent(root);
    }

    public void refreshStatus() {
        User u = SessionManager.getInstance().getCurrentUser();
        if (u != null) {
            userLbl.setText(u.getName());
            emailLbl.setText(u.getEmail());
            roleLbl.setText(u.getRole());
        }

        testDatabase();
        testOpenCV();
        checkCameraQuick();
    }

    private void handleSwitchEngine() {
        String val = aiEngineCombo.getValue();
        if (val.contains("OpenCV")) {
            aiInferenceService.setUseOpenCvEngine();
        } else {
            aiInferenceService.setUseDemoFallbackEngine();
        }
    }

    private void testDatabase() {
        boolean ok = DatabaseConnection.testConnection();
        if (ok) {
            dbStatusLbl.setText("CONNECTED (solarshield.db)");
            dbStatusLbl.setStyle("-fx-text-fill: #10b981; -fx-font-weight: 700;");
        } else {
            dbStatusLbl.setText("DISCONNECTED / ERROR");
            dbStatusLbl.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: 700;");
        }
    }

    private void testOpenCV() {
        boolean ok = OpenCVLoader.isAvailable();
        if (ok) {
            cvStatusLbl.setText("AVAILABLE (" + org.opencv.core.Core.VERSION + ")");
            cvStatusLbl.setStyle("-fx-text-fill: #10b981; -fx-font-weight: 700;");
        } else {
            cvStatusLbl.setText("UNAVAILABLE / FALLBACK");
            cvStatusLbl.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: 700;");
        }
    }

    private void checkCameraQuick() {
        camStatusLbl.setText("CHECKING...");
        new Thread(() -> {
            boolean available = false;
            try {
                if (OpenCVLoader.isAvailable()) {
                    VideoCapture cap = new VideoCapture(0);
                    available = cap.isOpened();
                    cap.release();
                }
            } catch (Throwable ignored) {}

            boolean finalAvail = available;
            javafx.application.Platform.runLater(() -> {
                if (finalAvail) {
                    camStatusLbl.setText("AVAILABLE (Webcam Ready)");
                    camStatusLbl.setStyle("-fx-text-fill: #10b981; -fx-font-weight: 700;");
                } else {
                    camStatusLbl.setText("UNAVAILABLE (Upload photo instead)");
                    camStatusLbl.setStyle("-fx-text-fill: #64748b; -fx-font-weight: 700;");
                }
            });
        }).start();
    }

    private void testCamera() {
        checkCameraQuick();
    }

    private void handleSeedData() {
        boolean ok = AlertUtils.showConfirmation("Seed Demo Data", "Seed Sample Panels & History?",
                "This will add demo panels SP-001 through SP-005 and sample historical records to the database.");
        if (ok) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                DatabaseInitializer.seedSampleData(conn);
                AlertUtils.showInfo("Sample Data Seeded", "Success", "Sample panels SP-001 to SP-005 and demo faults added.");
            } catch (Exception e) {
                AlertUtils.showError("Database Error", "Failed to Seed", e.getMessage());
            }
        }
    }
}
