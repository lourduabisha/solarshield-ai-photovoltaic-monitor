package com.solarshield.service;

import com.solarshield.model.BoundingBox;
import com.solarshield.model.FaultType;
import com.solarshield.util.ImageUtils;
import org.opencv.core.Mat;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service handling spatial fault localization and visual overlay rendering on solar panel images.
 */
public class LocalizationService {

    private static final String IMAGES_DIR = "data/images";

    public LocalizationService() {
        File dir = new File(IMAGES_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public static class LocalizationResult {
        private final Mat analyzedMat;
        private final String analyzedImagePath;
        private final BoundingBox boundingBox;
        private final String regionDescription;

        public LocalizationResult(Mat analyzedMat, String analyzedImagePath, BoundingBox boundingBox, String regionDescription) {
            this.analyzedMat = analyzedMat;
            this.analyzedImagePath = analyzedImagePath;
            this.boundingBox = boundingBox;
            this.regionDescription = regionDescription;
        }

        public Mat getAnalyzedMat() {
            return analyzedMat;
        }

        public String getAnalyzedImagePath() {
            return analyzedImagePath;
        }

        public BoundingBox getBoundingBox() {
            return boundingBox;
        }

        public String getRegionDescription() {
            return regionDescription;
        }
    }

    public LocalizationResult localizeAndRender(Mat originalMat, BoundingBox box, FaultType faultType, double confidence, String panelId) {
        if (originalMat == null || originalMat.empty()) {
            return new LocalizationResult(null, null, box, "No image matrix available");
        }

        // Render HUD overlay
        Mat overlay = ImageUtils.drawDetectionOverlay(originalMat, box, faultType, confidence);

        // Save analyzed image to data/images/
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String cleanPanelId = panelId != null ? panelId.replaceAll("[^A-Za-z0-9]", "") : "PANEL";
        String filename = String.format("analyzed_%s_%s.jpg", cleanPanelId, timestamp);
        File destFile = new File(IMAGES_DIR, filename);

        boolean saved = ImageUtils.saveMatToFile(overlay, destFile);
        String savedPath = saved ? destFile.getAbsolutePath() : "";

        // Formulate spatial description
        String regionDesc;
        if (faultType == FaultType.HEALTHY || box == null || !box.isValid()) {
            regionDesc = "Uniform surface; no localized defect detected.";
        } else {
            String label = box.getRegionLabel() != null ? box.getRegionLabel() : "Affected sector";
            regionDesc = String.format("Approximate region detected: %s (x:%d, y:%d, w:%d, h:%d)",
                    label, box.getX(), box.getY(), box.getWidth(), box.getHeight());
        }

        return new LocalizationResult(overlay, savedPath, box, regionDesc);
    }
}
