package com.solarshield.ui.component;

import com.solarshield.model.BoundingBox;
import com.solarshield.model.FaultType;
import com.solarshield.model.Severity;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * High-resolution interactive Zoom & Pan image inspection dialog.
 * Features:
 * - Smooth scroll-wheel zooming (25% to 800%)
 * - Click & drag panning
 * - Instant toggle between Raw image and AI HUD Reticle overlay
 * - Bounding box coordinate HUD telemetry chip
 * - Fit-to-screen and 1:1 pixel reset controls
 */
public class ImageZoomDialog {

    private final Stage stage;
    private final Image rawImage;
    private final Image analyzedImage;
    private final ImageView imageView;
    private final ScrollPane scrollPane;
    private final StackPane imageContainer;
    private final Label zoomLabel;

    private double currentScale = 1.0;
    private static final double MIN_SCALE = 0.25;
    private static final double MAX_SCALE = 8.0;
    private static final double ZOOM_FACTOR = 1.15;

    // Drag-to-pan tracking
    private double mouseAnchorX;
    private double mouseAnchorY;
    private double scrollH;
    private double scrollV;

    public ImageZoomDialog(Window owner, String title, Image rawImage, Image analyzedImage,
                           FaultType faultType, Severity severity, Double confidence, BoundingBox bbox) {
        this.rawImage = rawImage;
        this.analyzedImage = analyzedImage != null ? analyzedImage : rawImage;

        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title != null ? title : "SolarShield - High-Resolution Image Inspector");
        stage.setWidth(1000);
        stage.setHeight(750);

        // Center Viewport: Zoomable & Pannable ImageView
        imageView = new ImageView(analyzedImage != null ? analyzedImage : rawImage);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        imageContainer = new StackPane(imageView);
        imageContainer.setAlignment(Pos.CENTER);
        imageContainer.setStyle("-fx-background-color: #020617;");

        scrollPane = new ScrollPane(imageContainer);
        scrollPane.setPannable(false); // custom pan handler handles both pan and zoom anchor
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setStyle("-fx-background: #020617; -fx-background-color: #020617; -fx-border-color: transparent;");

        setupInteractionHandlers();

        // Header Toolbar
        HBox toolbar = new HBox(12);
        toolbar.setPadding(new Insets(10, 16, 10, 16));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #0f172a; -fx-border-color: #1e293b; -fx-border-width: 0 0 1 0;");

        Label titleLbl = new Label("🔬 HIGH-RES INSPECTION VIEWER");
        titleLbl.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: 900; -fx-font-size: 13px;");

        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        Button zoomOutBtn = new Button("➖");
        zoomOutBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6px;");
        zoomOutBtn.setOnAction(e -> zoom(1.0 / ZOOM_FACTOR));

        zoomLabel = new Label("100%");
        zoomLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: bold; -fx-min-width: 50px; -fx-alignment: center;");

        Button zoomInBtn = new Button("➕");
        zoomInBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6px;");
        zoomInBtn.setOnAction(e -> zoom(ZOOM_FACTOR));

        Button resetBtn = new Button("1:1 Reset");
        resetBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 6px;");
        resetBtn.setOnAction(e -> resetZoom());

        Button fitBtn = new Button("⛶ Fit Screen");
        fitBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 6px;");
        fitBtn.setOnAction(e -> fitToScreen());

        ToggleButton toggleOverlayBtn = new ToggleButton("👁 AI HUD Reticle");
        toggleOverlayBtn.setSelected(analyzedImage != null);
        toggleOverlayBtn.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 6px;");

        // Thermal Palettes ComboBox
        javafx.scene.control.ComboBox<com.solarshield.model.ThermalColormap> paletteCombo = new javafx.scene.control.ComboBox<>();
        paletteCombo.getItems().addAll(com.solarshield.model.ThermalColormap.values());
        paletteCombo.setValue(com.solarshield.model.ThermalColormap.NATURAL);
        paletteCombo.setStyle("-fx-background-color: #1e293b; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 6px;");

        com.solarshield.service.ImagePreprocessingService prepService = new com.solarshield.service.ImagePreprocessingService();

        Runnable updateDisplayImage = () -> {
            com.solarshield.model.ThermalColormap colormap = paletteCombo.getValue();
            Image baseImg = toggleOverlayBtn.isSelected() ? this.analyzedImage : this.rawImage;
            if (colormap == null || colormap == com.solarshield.model.ThermalColormap.NATURAL) {
                imageView.setImage(baseImg);
            } else {
                org.opencv.core.Mat baseMat = com.solarshield.util.ImageUtils.fxImageToMat(baseImg);
                if (baseMat != null && !baseMat.empty()) {
                    org.opencv.core.Mat thermalMat = prepService.applyThermalColormap(baseMat, colormap);
                    imageView.setImage(com.solarshield.util.ImageUtils.matToFxImage(thermalMat));
                    baseMat.release();
                    thermalMat.release();
                }
            }
        };

        toggleOverlayBtn.setOnAction(e -> {
            if (toggleOverlayBtn.isSelected()) {
                toggleOverlayBtn.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 6px;");
            } else {
                toggleOverlayBtn.setStyle("-fx-background-color: #334155; -fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 6px;");
            }
            updateDisplayImage.run();
        });

        paletteCombo.setOnAction(e -> updateDisplayImage.run());

        Button closeBtn = new Button("✕ Close");
        closeBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 6px;");
        closeBtn.setOnAction(e -> stage.close());

        toolbar.getChildren().addAll(titleLbl, spacer1, zoomOutBtn, zoomLabel, zoomInBtn, resetBtn, fitBtn, paletteCombo, toggleOverlayBtn, closeBtn);

        // Footer Telemetry Bar
        HBox footer = new HBox(16);
        footer.setPadding(new Insets(8, 16, 8, 16));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-background-color: #090d16; -fx-border-color: #1e293b; -fx-border-width: 1 0 0 0;");

        String faultText = faultType != null ? faultType.getDisplayName() : "Inspection Preview";
        String confText = confidence != null ? String.format("%.1f%%", confidence) : "-";
        String sevText = severity != null ? severity.name() : "-";
        String bboxText = bbox != null ? String.format("[%d, %d, %d, %d]", bbox.getX(), bbox.getY(), bbox.getWidth(), bbox.getHeight()) : "Full Module";

        Label faultChip = new Label("Fault: " + faultText);
        faultChip.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label confChip = new Label("Confidence: " + confText);
        confChip.setStyle("-fx-text-fill: #a7f3d0; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label sevChip = new Label("Severity: " + sevText);
        String sevColor = severity != null ? severity.getColorHex() : "#cbd5e1";
        sevChip.setStyle("-fx-text-fill: " + sevColor + "; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label bboxChip = new Label("ROI Reticle: " + bboxText);
        bboxChip.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        Label hintLabel = new Label("Tip: Scroll wheel zooms in/out • Click & drag to pan viewport");
        hintLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-style: italic;");

        footer.getChildren().addAll(faultChip, confChip, sevChip, bboxChip, spacer2, hintLabel);

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(scrollPane);
        root.setBottom(footer);

        Scene scene = new Scene(root);
        stage.setScene(scene);
    }

    private void setupInteractionHandlers() {
        // Scroll wheel to zoom
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0) return;
            double factor = event.getDeltaY() > 0 ? ZOOM_FACTOR : (1.0 / ZOOM_FACTOR);
            zoom(factor);
            event.consume();
        });

        // Click & drag to pan
        imageContainer.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                mouseAnchorX = event.getSceneX();
                mouseAnchorY = event.getSceneY();
                scrollH = scrollPane.getHvalue();
                scrollV = scrollPane.getVvalue();
                imageContainer.setCursor(javafx.scene.Cursor.CLOSED_HAND);
            }
        });

        imageContainer.setOnMouseDragged(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                double deltaX = event.getSceneX() - mouseAnchorX;
                double deltaY = event.getSceneY() - mouseAnchorY;

                double contentWidth = imageContainer.getBoundsInLocal().getWidth();
                double contentHeight = imageContainer.getBoundsInLocal().getHeight();
                double viewportWidth = scrollPane.getViewportBounds().getWidth();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();

                double scrollableWidth = contentWidth - viewportWidth;
                double scrollableHeight = contentHeight - viewportHeight;

                if (scrollableWidth > 0) {
                    scrollPane.setHvalue(scrollH - deltaX / scrollableWidth);
                }
                if (scrollableHeight > 0) {
                    scrollPane.setVvalue(scrollV - deltaY / scrollableHeight);
                }
            }
        });

        imageContainer.setOnMouseReleased(event -> {
            imageContainer.setCursor(javafx.scene.Cursor.OPEN_HAND);
        });

        imageContainer.setCursor(javafx.scene.Cursor.OPEN_HAND);
    }

    private void zoom(double factor) {
        double newScale = currentScale * factor;
        if (newScale < MIN_SCALE) newScale = MIN_SCALE;
        if (newScale > MAX_SCALE) newScale = MAX_SCALE;

        currentScale = newScale;
        applyScale();
    }

    private void resetZoom() {
        currentScale = 1.0;
        applyScale();
    }

    private void fitToScreen() {
        Image img = imageView.getImage();
        if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) return;

        double viewWidth = scrollPane.getViewportBounds().getWidth();
        double viewHeight = scrollPane.getViewportBounds().getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            viewWidth = 900;
            viewHeight = 600;
        }

        double scaleX = (viewWidth - 40) / img.getWidth();
        double scaleY = (viewHeight - 40) / img.getHeight();
        currentScale = Math.min(scaleX, scaleY);
        if (currentScale < MIN_SCALE) currentScale = MIN_SCALE;
        if (currentScale > MAX_SCALE) currentScale = MAX_SCALE;

        applyScale();
    }

    private void applyScale() {
        Image img = imageView.getImage();
        if (img != null) {
            imageView.setFitWidth(img.getWidth() * currentScale);
            imageView.setFitHeight(img.getHeight() * currentScale);
        }
        zoomLabel.setText(String.format("%d%%", Math.round(currentScale * 100)));
    }

    public void show() {
        stage.show();
        // Trigger initial fit after window layout
        javafx.application.Platform.runLater(this::fitToScreen);
    }
}
