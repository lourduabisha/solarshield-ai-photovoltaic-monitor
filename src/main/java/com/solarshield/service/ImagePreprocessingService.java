package com.solarshield.service;

import com.solarshield.util.ImageUtils;
import com.solarshield.util.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Service orchestrating the image preprocessing pipeline before AI/CV inference.
 *
 * Pipeline Steps:
 * 1. File format and integrity validation
 * 2. Safe image loading into memory
 * 3. Aspect-ratio preserving dimension standardization (max 800px)
 * 4. High-frequency sensor noise attenuation (Gaussian/Bilateral filtering)
 * 5. Contrast and illumination normalization
 * 6. Conversion to standard OpenCV Mat matrix
 */
public class ImagePreprocessingService {

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".bmp", ".webp");
    public static final int TARGET_MAX_DIMENSION = 800;

    public static class PreprocessResult {
        private final boolean success;
        private final String message;
        private final Mat originalMat;
        private final Mat processedMat;

        public PreprocessResult(boolean success, String message, Mat originalMat, Mat processedMat) {
            this.success = success;
            this.message = message;
            this.originalMat = originalMat;
            this.processedMat = processedMat;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Mat getOriginalMat() {
            return originalMat;
        }

        public Mat getProcessedMat() {
            return processedMat;
        }
    }

    public PreprocessResult preprocessImageFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return new PreprocessResult(false, "Selected image file does not exist or is invalid.", null, null);
        }

        String name = file.getName().toLowerCase();
        boolean validExt = ALLOWED_EXTENSIONS.stream().anyMatch(name::endsWith);
        if (!validExt) {
            return new PreprocessResult(false, "Unsupported file format. Please upload JPG, PNG, or BMP solar images.", null, null);
        }

        Mat rawMat = null;
        if (OpenCVLoader.isAvailable()) {
            rawMat = Imgcodecs.imread(file.getAbsolutePath());
        }

        // Fallback loading via Java ImageIO if OpenCV imread returns empty
        if (rawMat == null || rawMat.empty()) {
            try {
                BufferedImage bi = ImageIO.read(file);
                if (bi != null) {
                    rawMat = ImageUtils.bufferedImageToMat(bi);
                }
            } catch (Exception e) {
                System.err.println("[ImagePreprocessingService] ImageIO fallback error: " + e.getMessage());
            }
        }

        if (rawMat == null || rawMat.empty()) {
            return new PreprocessResult(false, "Failed to decode image data. The file may be corrupt or unreadable.", null, null);
        }

        return preprocessMat(rawMat);
    }

    public PreprocessResult preprocessMat(Mat rawMat) {
        if (rawMat == null || rawMat.empty()) {
            return new PreprocessResult(false, "Input image matrix is empty.", null, null);
        }

        try {
            Mat originalCopy = rawMat.clone();
            Mat workingMat = rawMat.clone();

            // 1. Resize if image exceeds maximum inspection dimension
            int cols = workingMat.cols();
            int rows = workingMat.rows();
            if (cols > TARGET_MAX_DIMENSION || rows > TARGET_MAX_DIMENSION) {
                double scale = (double) TARGET_MAX_DIMENSION / Math.max(cols, rows);
                int newW = (int) (cols * scale);
                int newH = (int) (rows * scale);
                Mat resized = new Mat();
                Imgproc.resize(workingMat, resized, new Size(newW, newH), 0, 0, Imgproc.INTER_AREA);
                workingMat.release();
                workingMat = resized;
            }

            // 2. Noise suppression: Apply light Gaussian filter to remove thermal camera sensor grain
            Mat filtered = new Mat();
            Imgproc.GaussianBlur(workingMat, filtered, new Size(3, 3), 0.6);
            workingMat.release();
            workingMat = filtered;

            // 3. Contrast normalization
            if (workingMat.channels() == 3) {
                Mat ycrcb = new Mat();
                Imgproc.cvtColor(workingMat, ycrcb, Imgproc.COLOR_BGR2YCrCb);
                List<Mat> channels = new java.util.ArrayList<>();
                Core.split(ycrcb, channels);
                // Equalize luminance channel Y
                Imgproc.equalizeHist(channels.get(0), channels.get(0));
                Core.merge(channels, ycrcb);
                Mat normalizedBgr = new Mat();
                Imgproc.cvtColor(ycrcb, normalizedBgr, Imgproc.COLOR_YCrCb2BGR);

                // Blend with original to prevent over-enhancement
                Mat finalMat = new Mat();
                Core.addWeighted(workingMat, 0.6, normalizedBgr, 0.4, 0, finalMat);

                ycrcb.release();
                normalizedBgr.release();
                for (Mat m : channels) m.release();
                workingMat.release();
                workingMat = finalMat;
            }

            return new PreprocessResult(true, "Image preprocessed successfully.", originalCopy, workingMat);

        } catch (Exception e) {
            System.err.println("[ImagePreprocessingService] Preprocessing failed: " + e.getMessage());
            return new PreprocessResult(false, "Preprocessing error: " + e.getMessage(), rawMat, rawMat);
        }
    }

    /**
     * Applies false-color thermal infrared palettes or multi-spectral edge filters to a solar panel image.
     */
    public Mat applyThermalColormap(Mat src, com.solarshield.model.ThermalColormap colormap) {
        if (src == null || src.empty()) return src;
        if (colormap == null || colormap == com.solarshield.model.ThermalColormap.NATURAL) {
            return src.clone();
        }

        try {
            if (colormap == com.solarshield.model.ThermalColormap.EDGE_ENHANCED) {
                return generateMultiSpectralEdgeMat(src);
            }

            // Convert to grayscale luminance matrix
            Mat gray = new Mat();
            if (src.channels() == 3) {
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = src.clone();
            }

            // Contrast stretch to maximize dynamic range of thermal gradients
            Mat stretched = new Mat();
            Core.normalize(gray, stretched, 0, 255, Core.NORM_MINMAX);

            Mat thermalMat = new Mat();
            Imgproc.applyColorMap(stretched, thermalMat, colormap.getOpencvCode());

            gray.release();
            stretched.release();
            return thermalMat;
        } catch (Exception e) {
            System.err.println("[ImagePreprocessingService] Colormap application failed: " + e.getMessage());
            return src.clone();
        }
    }

    /**
     * Generates a multi-spectral edge-enhanced representation using CLAHE
     * (Contrast Limited Adaptive Histogram Equalization) combined with morphological gradient.
     */
    public Mat generateMultiSpectralEdgeMat(Mat src) {
        if (src == null || src.empty()) return src;
        try {
            Mat gray = new Mat();
            if (src.channels() == 3) {
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = src.clone();
            }

            // 1. CLAHE adaptive equalization to expose faint micro-cracks
            org.opencv.imgproc.CLAHE clahe = Imgproc.createCLAHE(3.5, new Size(8, 8));
            Mat claheMat = new Mat();
            clahe.apply(gray, claheMat);

            // 2. Morphological gradient for edge boundary highlights
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
            Mat morphGrad = new Mat();
            Imgproc.morphologyEx(claheMat, morphGrad, Imgproc.MORPH_GRADIENT, kernel);

            // 3. Composite into cyan/blue multi-spectral HUD look
            Mat bgrEdge = new Mat();
            Imgproc.cvtColor(claheMat, bgrEdge, Imgproc.COLOR_GRAY2BGR);

            // Highlight edges with cyan tint (B=255, G=200, R=0)
            Mat coloredEdges = new Mat();
            Mat colorOverlay = new Mat(src.size(), src.type(), new org.opencv.core.Scalar(255, 200, 0));
            Core.bitwise_and(colorOverlay, colorOverlay, coloredEdges, morphGrad);

            Mat result = new Mat();
            Core.addWeighted(bgrEdge, 0.75, coloredEdges, 0.5, 0, result);

            gray.release();
            claheMat.release();
            kernel.release();
            morphGrad.release();
            bgrEdge.release();
            colorOverlay.release();
            coloredEdges.release();

            return result;
        } catch (Exception e) {
            System.err.println("[ImagePreprocessingService] Edge enhancement failed: " + e.getMessage());
            return src.clone();
        }
    }
}

