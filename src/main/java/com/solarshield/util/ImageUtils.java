package com.solarshield.util;

import com.solarshield.model.BoundingBox;
import com.solarshield.model.FaultType;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Image processing and format conversion utility bridging OpenCV Mat,
 * Java AWT BufferedImage, and JavaFX Image representations.
 */
public final class ImageUtils {

    private ImageUtils() {
        // Prevent instantiation
    }

    /**
     * Converts an OpenCV Mat into a JavaFX Image.
     */
    public static Image matToFxImage(Mat mat) {
        if (mat == null || mat.empty()) {
            return null;
        }

        try {
            MatOfByte matOfByte = new MatOfByte();
            Imgcodecs.imencode(".png", mat, matOfByte);
            byte[] byteArray = matOfByte.toArray();
            return new Image(new ByteArrayInputStream(byteArray));
        } catch (Exception e) {
            System.err.println("[ImageUtils] Error converting Mat to JavaFX Image: " + e.getMessage());
            return null;
        }
    }

    /**
     * Converts a JavaFX Image into an OpenCV Mat.
     */
    public static Mat fxImageToMat(Image fxImage) {
        if (fxImage == null) return null;

        int width = (int) fxImage.getWidth();
        int height = (int) fxImage.getHeight();
        if (width <= 0 || height <= 0) return null;

        Mat mat = new Mat(height, width, CvType.CV_8UC3);
        byte[] data = new byte[width * height * 3];

        PixelReader reader = fxImage.getPixelReader();
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = reader.getArgb(x, y);
                byte r = (byte) ((argb >> 16) & 0xFF);
                byte g = (byte) ((argb >> 8) & 0xFF);
                byte b = (byte) (argb & 0xFF);
                // OpenCV BGR order
                data[idx++] = b;
                data[idx++] = g;
                data[idx++] = r;
            }
        }
        mat.put(0, 0, data);
        return mat;
    }

    /**
     * Converts a BufferedImage into an OpenCV Mat.
     */
    public static Mat bufferedImageToMat(BufferedImage bi) {
        if (bi == null) return null;
        try {
            int width = bi.getWidth();
            int height = bi.getHeight();
            Mat mat = new Mat(height, width, CvType.CV_8UC3);
            byte[] data = new byte[width * height * 3];

            int idx = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = bi.getRGB(x, y);
                    byte r = (byte) ((rgb >> 16) & 0xFF);
                    byte g = (byte) ((rgb >> 8) & 0xFF);
                    byte b = (byte) (rgb & 0xFF);
                    data[idx++] = b;
                    data[idx++] = g;
                    data[idx++] = r;
                }
            }
            mat.put(0, 0, data);
            return mat;
        } catch (Exception e) {
            System.err.println("[ImageUtils] Error converting BufferedImage to Mat: " + e.getMessage());
            return null;
        }
    }

    /**
     * Converts an OpenCV Mat into a BufferedImage.
     */
    public static BufferedImage matToBufferedImage(Mat mat) {
        if (mat == null || mat.empty()) return null;
        try {
            MatOfByte matOfByte = new MatOfByte();
            Imgcodecs.imencode(".png", mat, matOfByte);
            byte[] byteArray = matOfByte.toArray();
            return ImageIO.read(new ByteArrayInputStream(byteArray));
        } catch (Exception e) {
            System.err.println("[ImageUtils] Error converting Mat to BufferedImage: " + e.getMessage());
            return null;
        }
    }

    /**
     * Renders a professional HUD-style defect localization overlay with bounding box,
     * reticles, and diagnostic badge onto a copy of the input Mat.
     */
    public static Mat drawDetectionOverlay(Mat inputMat, BoundingBox box, FaultType faultType, double confidence) {
        if (inputMat == null || inputMat.empty()) return null;

        Mat output = inputMat.clone();
        if (output.channels() == 1) {
            Imgproc.cvtColor(output, output, Imgproc.COLOR_GRAY2BGR);
        }

        int imgW = output.cols();
        int imgH = output.rows();

        if (faultType == FaultType.HEALTHY || box == null || !box.isValid()) {
            // Draw nominal status overlay banner
            Scalar green = new Scalar(50, 205, 50); // BGR
            Imgproc.rectangle(output, new Point(20, 20), new Point(320, 65), new Scalar(15, 25, 35), -1);
            Imgproc.rectangle(output, new Point(20, 20), new Point(320, 65), green, 2);
            Imgproc.putText(output, "STATUS: NOMINAL (HEALTHY)", new Point(32, 48),
                    Imgproc.FONT_HERSHEY_DUPLEX, 0.6, green, 2);
            return output;
        }

        // Clamp bounding box to image bounds
        int x = Math.max(0, Math.min(box.getX(), imgW - 10));
        int y = Math.max(0, Math.min(box.getY(), imgH - 10));
        int w = Math.min(box.getWidth(), imgW - x);
        int h = Math.min(box.getHeight(), imgH - y);

        // Select color based on fault type (BGR)
        Scalar boxColor;
        switch (faultType) {
            case HOTSPOT:
                boxColor = new Scalar(0, 0, 240); // Bright Red
                break;
            case CRACK:
                boxColor = new Scalar(0, 140, 255); // Orange
                break;
            case SOILING:
                boxColor = new Scalar(0, 215, 255); // Yellow/Amber
                break;
            case PARTIAL_SHADING:
                boxColor = new Scalar(230, 92, 139); // Violet
                break;
            case PHYSICAL_DAMAGE:
                boxColor = new Scalar(30, 30, 220); // Dark Red
                break;
            default:
                boxColor = new Scalar(0, 220, 100);
        }

        // 1. Semi-transparent tint on the affected region
        try {
            Rect roi = new Rect(x, y, w, h);
            Mat sub = output.submat(roi);
            Mat tint = new Mat(sub.size(), sub.type(), boxColor);
            Core.addWeighted(sub, 0.75, tint, 0.25, 0, sub);
        } catch (Exception ignored) {}

        // 2. Main bounding rectangle
        Point p1 = new Point(x, y);
        Point p2 = new Point(x + w, y + h);
        Imgproc.rectangle(output, p1, p2, boxColor, 2);

        // 3. Futuristic corner brackets
        int cornerLen = Math.min(20, Math.min(w / 3, h / 3));
        if (cornerLen > 3) {
            Scalar brightWhite = new Scalar(255, 255, 255);
            // Top-left
            Imgproc.line(output, new Point(x, y), new Point(x + cornerLen, y), brightWhite, 3);
            Imgproc.line(output, new Point(x, y), new Point(x, y + cornerLen), brightWhite, 3);
            // Top-right
            Imgproc.line(output, new Point(x + w, y), new Point(x + w - cornerLen, y), brightWhite, 3);
            Imgproc.line(output, new Point(x + w, y), new Point(x + w, y + cornerLen), brightWhite, 3);
            // Bottom-left
            Imgproc.line(output, new Point(x, y + h), new Point(x + cornerLen, y + h), brightWhite, 3);
            Imgproc.line(output, new Point(x, y + h), new Point(x, y + h - cornerLen), brightWhite, 3);
            // Bottom-right
            Imgproc.line(output, new Point(x + w, y + h), new Point(x + w - cornerLen, y + h), brightWhite, 3);
            Imgproc.line(output, new Point(x + w, y + h), new Point(x + w, y + h - cornerLen), brightWhite, 3);
        }

        // 4. Diagnostic tag banner above or inside the box
        String label = String.format("%s (%.1f%%)", faultType.getDisplayName().toUpperCase(), confidence);
        int bannerY = y > 30 ? y - 6 : y + 25;
        int bannerX = Math.max(10, x);

        // Background for text
        Imgproc.rectangle(output, new Point(bannerX - 4, bannerY - 18),
                new Point(bannerX + label.length() * 11 + 10, bannerY + 6), new Scalar(15, 25, 35), -1);
        Imgproc.rectangle(output, new Point(bannerX - 4, bannerY - 18),
                new Point(bannerX + label.length() * 11 + 10, bannerY + 6), boxColor, 1);

        Imgproc.putText(output, label, new Point(bannerX, bannerY),
                Imgproc.FONT_HERSHEY_DUPLEX, 0.55, new Scalar(255, 255, 255), 1);

        return output;
    }

    public static boolean saveMatToFile(Mat mat, File file) {
        if (mat == null || file == null) return false;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            return Imgcodecs.imwrite(file.getAbsolutePath(), mat);
        } catch (Exception e) {
            System.err.println("[ImageUtils] Error saving Mat to file: " + e.getMessage());
            return false;
        }
    }
}
