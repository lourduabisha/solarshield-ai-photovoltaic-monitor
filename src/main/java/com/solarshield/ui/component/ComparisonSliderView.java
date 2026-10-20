package com.solarshield.ui.component;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * Interactive Before / After Split-Screen Comparison Slider.
 * Allows solar technicians to slide a vertical divider bar left and right
 * to reveal the raw solar panel imagery versus the OpenCV AI HUD reticle overlay.
 */
public class ComparisonSliderView extends StackPane {

    private final ImageView rawImageView;
    private final ImageView analyzedImageView;
    private final Rectangle clipRect;
    private final Pane overlayPane;
    private final Line dividerLine;
    private final StackPane sliderHandle;

    private final DoubleProperty splitRatio = new SimpleDoubleProperty(0.5); // 0.0 to 1.0

    private double prefViewWidth = 860;
    private double prefViewHeight = 360;

    public ComparisonSliderView() {
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: #0b1329; -fx-background-radius: 8px; -fx-border-color: #1e293b; -fx-border-radius: 8px;");
        setPrefSize(prefViewWidth, prefViewHeight);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        // Bottom Layer: Raw Image
        rawImageView = new ImageView();
        rawImageView.setFitWidth(prefViewWidth);
        rawImageView.setFitHeight(prefViewHeight);
        rawImageView.setPreserveRatio(true);

        // Top Layer: Analyzed Image with Clip
        analyzedImageView = new ImageView();
        analyzedImageView.setFitWidth(prefViewWidth);
        analyzedImageView.setFitHeight(prefViewHeight);
        analyzedImageView.setPreserveRatio(true);

        clipRect = new Rectangle();
        analyzedImageView.setClip(clipRect);

        // Overlay Pane for Divider & Handle
        overlayPane = new Pane();
        overlayPane.setPrefSize(prefViewWidth, prefViewHeight);

        // Divider Line
        dividerLine = new Line();
        dividerLine.setStroke(Color.web("#38bdf8"));
        dividerLine.setStrokeWidth(2.5);
        dividerLine.getStrokeDashArray().addAll(6.0, 4.0);

        // Slider Handle Capsule
        sliderHandle = new StackPane();
        sliderHandle.setPrefSize(38, 38);
        sliderHandle.setStyle("-fx-background-color: #0284c7; -fx-background-radius: 19px; -fx-border-color: #ffffff; -fx-border-width: 2px; -fx-border-radius: 19px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 2); -fx-cursor: h-resize;");

        Label handleIcon = new Label("◀ ▶");
        handleIcon.setStyle("-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: 900;");
        sliderHandle.getChildren().add(handleIcon);

        // Floating Badges
        Label rawBadge = new Label("RAW (BEFORE)");
        rawBadge.setStyle("-fx-background-color: rgba(15, 23, 42, 0.8); -fx-text-fill: #94a3b8; -fx-font-weight: 800; -fx-font-size: 11px; -fx-padding: 4 10 4 10; -fx-background-radius: 4px;");
        StackPane.setAlignment(rawBadge, Pos.TOP_LEFT);
        StackPane.setMargin(rawBadge, new Insets(12));

        Label aiBadge = new Label("AI HUD OVERLAY (AFTER)");
        aiBadge.setStyle("-fx-background-color: rgba(2, 132, 199, 0.85); -fx-text-fill: #ffffff; -fx-font-weight: 800; -fx-font-size: 11px; -fx-padding: 4 10 4 10; -fx-background-radius: 4px;");
        StackPane.setAlignment(aiBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(aiBadge, new Insets(12));

        overlayPane.getChildren().addAll(dividerLine, sliderHandle);

        getChildren().addAll(rawImageView, analyzedImageView, overlayPane, rawBadge, aiBadge);

        setupEventHandlers();
        updateDividerPosition();

        widthProperty().addListener((obs, oldVal, newVal) -> updateDividerPosition());
        heightProperty().addListener((obs, oldVal, newVal) -> updateDividerPosition());
        splitRatio.addListener((obs, oldVal, newVal) -> updateDividerPosition());
    }

    private void setupEventHandlers() {
        overlayPane.setOnMousePressed(this::handleMouseDrag);
        overlayPane.setOnMouseDragged(this::handleMouseDrag);

        sliderHandle.setOnMousePressed(this::handleMouseDrag);
        sliderHandle.setOnMouseDragged(this::handleMouseDrag);
    }

    private void handleMouseDrag(MouseEvent event) {
        double currentWidth = getWidth() > 0 ? getWidth() : prefViewWidth;
        double x = event.getX();
        if (event.getSource() == sliderHandle) {
            x += sliderHandle.getLayoutX();
        }
        double ratio = Math.max(0.02, Math.min(0.98, x / currentWidth));
        splitRatio.set(ratio);
        event.consume();
    }

    public void setImages(Image raw, Image analyzed) {
        rawImageView.setImage(raw);
        analyzedImageView.setImage(analyzed);

        if (raw != null) {
            updateDividerPosition();
        }
    }

    private void updateDividerPosition() {
        double w = getWidth() > 0 ? getWidth() : prefViewWidth;
        double h = getHeight() > 0 ? getHeight() : prefViewHeight;

        double splitX = w * splitRatio.get();

        // The analyzed image is clipped to show on the right portion of the slider
        clipRect.setX(splitX);
        clipRect.setY(0);
        clipRect.setWidth(Math.max(0, w - splitX));
        clipRect.setHeight(h);

        // Update divider line
        dividerLine.setStartX(splitX);
        dividerLine.setStartY(0);
        dividerLine.setEndX(splitX);
        dividerLine.setEndY(h);

        // Update handle position
        sliderHandle.setLayoutX(splitX - 19);
        sliderHandle.setLayoutY((h / 2.0) - 19);
    }

    public void clear() {
        rawImageView.setImage(null);
        analyzedImageView.setImage(null);
    }
}
