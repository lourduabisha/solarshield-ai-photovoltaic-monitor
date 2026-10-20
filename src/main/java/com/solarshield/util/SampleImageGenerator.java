package com.solarshield.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Utility to generate realistic synthetic solar panel test images for live demonstrations.
 * Generates test images covering:
 * - Healthy module
 * - Thermal Hotspot defect
 * - Wafer micro-cracks
 * - Dust and surface soiling
 * - Obstruction and partial shading
 * - Severe physical shatter damage
 */
public class SampleImageGenerator {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final String SAMPLES_DIR = "sample_images";

    public static void generateAllSamples() {
        File dir = new File(SAMPLES_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try {
            saveImage(createHealthyPanel(), new File(dir, "1_healthy_panel.jpg"));
            saveImage(createHotspotPanel(), new File(dir, "2_hotspot_panel.jpg"));
            saveImage(createCrackPanel(), new File(dir, "3_crack_panel.jpg"));
            saveImage(createSoiledPanel(), new File(dir, "4_soiled_panel.jpg"));
            saveImage(createShadedPanel(), new File(dir, "5_shaded_panel.jpg"));
            saveImage(createDamagedPanel(), new File(dir, "6_damaged_panel.jpg"));
            System.out.println("[SampleImageGenerator] Generated 6 sample demonstration images in " + SAMPLES_DIR);
        } catch (Exception e) {
            System.err.println("[SampleImageGenerator] Error generating sample images: " + e.getMessage());
        }
    }

    private static BufferedImage createBaseSolarPanel() {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Aluminum frame background
        g.setColor(new Color(180, 185, 195));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Frame inner bevel
        g.setColor(new Color(130, 135, 145));
        g.setStroke(new BasicStroke(4));
        g.drawRect(8, 8, WIDTH - 16, HEIGHT - 16);

        // Dark silicon PV module backing
        int marginX = 24;
        int marginY = 24;
        int gridW = WIDTH - (marginX * 2);
        int gridH = HEIGHT - (marginY * 2);

        GradientPaint pvGrad = new GradientPaint(
                marginX, marginY, new Color(18, 38, 70),
                gridW, gridH, new Color(10, 22, 45)
        );
        g.setPaint(pvGrad);
        g.fillRect(marginX, marginY, gridW, gridH);

        // Draw 6x4 solar cell wafer matrix
        int cols = 6;
        int rows = 4;
        int cellW = gridW / cols;
        int cellH = gridH / rows;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int cx = marginX + (c * cellW) + 2;
                int cy = marginY + (r * cellH) + 2;
                int cw = cellW - 4;
                int ch = cellH - 4;

                // Silicon wafer body with pseudo-hexagonal corner cuts (monocrystalline PERC look)
                g.setColor(new Color(22, 48, 88));
                g.fillRoundRect(cx, cy, cw, ch, 14, 14);

                // Wafer border gap
                g.setColor(new Color(12, 28, 52));
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(cx, cy, cw, ch, 14, 14);

                // Silver busbars (3 vertical silver conductor bars per cell)
                g.setColor(new Color(205, 215, 230, 160));
                g.setStroke(new BasicStroke(1.5f));
                g.drawLine(cx + cw / 4, cy, cx + cw / 4, cy + ch);
                g.drawLine(cx + cw / 2, cy, cx + cw / 2, cy + ch);
                g.drawLine(cx + (3 * cw) / 4, cy, cx + (3 * cw) / 4, cy + ch);

                // Micro fingers (fine horizontal grid lines)
                g.setColor(new Color(180, 200, 225, 60));
                g.setStroke(new BasicStroke(0.75f));
                for (int yf = cy + 6; yf < cy + ch - 4; yf += 8) {
                    g.drawLine(cx + 4, yf, cx + cw - 4, yf);
                }
            }
        }

        g.dispose();
        return img;
    }

    private static BufferedImage createHealthyPanel() {
        BufferedImage img = createBaseSolarPanel();
        Graphics2D g = img.createGraphics();
        // Add subtle anti-reflective glass gloss reflection
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gloss = new GradientPaint(
                0, 0, new Color(255, 255, 255, 45),
                WIDTH, HEIGHT / 2, new Color(255, 255, 255, 0)
        );
        g.setPaint(gloss);
        GeneralPath path = new GeneralPath();
        path.moveTo(0, 0);
        path.lineTo(WIDTH, 0);
        path.lineTo(WIDTH, HEIGHT / 3);
        path.lineTo(0, HEIGHT / 2);
        path.closePath();
        g.fill(path);
        g.dispose();
        return img;
    }

    private static BufferedImage createHotspotPanel() {
        BufferedImage img = createBaseSolarPanel();
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Upper-right cell hotspot coordinates
        int hx = 560;
        int hy = 160;

        // Radial glow for thermal hotspot
        RadialGradientPaint rgp = new RadialGradientPaint(
                hx, hy, 90,
                new float[]{0.0f, 0.25f, 0.55f, 0.85f, 1.0f},
                new Color[]{
                        new Color(255, 255, 240, 255), // White-hot core
                        new Color(255, 215, 0, 240),   // Bright yellow
                        new Color(255, 69, 0, 210),    // Vibrant orange
                        new Color(220, 20, 60, 150),   // Deep crimson
                        new Color(180, 0, 0, 0)        // Outer fade
                }
        );
        g.setPaint(rgp);
        g.fillOval(hx - 90, hy - 90, 180, 180);

        // Burn blister mark at hotspot center
        g.setColor(new Color(60, 10, 10, 200));
        g.fillOval(hx - 14, hy - 14, 28, 28);

        g.dispose();
        return img;
    }

    private static BufferedImage createCrackPanel() {
        BufferedImage img = createBaseSolarPanel();
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Micro-fracture crack in center module wafer
        int startX = 320;
        int startY = 240;

        g.setColor(new Color(245, 245, 255, 230)); // Bright reflective fracture edge
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        GeneralPath crack = new GeneralPath();
        crack.moveTo(startX, startY);
        crack.lineTo(startX + 35, startY + 28);
        crack.lineTo(startX + 20, startY + 65);
        crack.lineTo(startX + 60, startY + 95);
        crack.lineTo(startX + 85, startY + 80);
        crack.lineTo(startX + 130, startY + 135);
        crack.lineTo(startX + 165, startY + 120);
        g.draw(crack);

        // Branching secondary fractures
        GeneralPath branch1 = new GeneralPath();
        branch1.moveTo(startX + 35, startY + 28);
        branch1.lineTo(startX + 75, startY + 20);
        branch1.lineTo(startX + 105, startY + 45);
        g.draw(branch1);

        GeneralPath branch2 = new GeneralPath();
        branch2.moveTo(startX + 60, startY + 95);
        branch2.lineTo(startX + 40, startY + 140);
        branch2.lineTo(startX + 70, startY + 175);
        g.draw(branch2);

        // Dark shadowing inside fissure
        g.setColor(new Color(10, 15, 25, 180));
        g.setStroke(new BasicStroke(1.0f));
        g.draw(crack);

        g.dispose();
        return img;
    }

    private static BufferedImage createSoiledPanel() {
        BufferedImage img = createBaseSolarPanel();
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dust/dirt diffuse soiling layer across center-left
        for (int i = 0; i < 60; i++) {
            int x = 60 + (int) (Math.random() * 450);
            int y = 80 + (int) (Math.random() * 380);
            int radius = 30 + (int) (Math.random() * 80);
            int alpha = 40 + (int) (Math.random() * 50);

            Color soilColor = (i % 3 == 0) ?
                    new Color(175, 140, 90, alpha) :
                    new Color(195, 165, 115, alpha);

            g.setColor(soilColor);
            g.fillOval(x, y, radius, radius);
        }

        // Dropping spot
        g.setColor(new Color(230, 230, 220, 220));
        g.fillOval(280, 220, 42, 34);
        g.fillOval(305, 240, 22, 18);

        g.dispose();
        return img;
    }

    private static BufferedImage createShadedPanel() {
        BufferedImage img = createBaseSolarPanel();
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark diagonal tree branch / structural shadow across bottom-right
        GeneralPath shadow = new GeneralPath();
        shadow.moveTo(WIDTH * 0.35, HEIGHT);
        shadow.lineTo(WIDTH, HEIGHT * 0.45);
        shadow.lineTo(WIDTH, HEIGHT);
        shadow.closePath();

        // Semi-transparent deep shadow
        g.setColor(new Color(5, 10, 20, 215));
        g.fill(shadow);

        // Soft penumbra edge
        g.setColor(new Color(10, 15, 28, 80));
        g.setStroke(new BasicStroke(18));
        g.drawLine((int)(WIDTH * 0.35), HEIGHT, WIDTH, (int)(HEIGHT * 0.45));

        g.dispose();
        return img;
    }

    private static BufferedImage createDamagedPanel() {
        BufferedImage img = createBaseSolarPanel();
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Shattered glass impact epicenter in upper-left
        int cx = 220;
        int cy = 180;

        // Concentric shattered impact rings
        g.setColor(new Color(240, 245, 255, 220));
        g.setStroke(new BasicStroke(1.8f));
        for (int r = 18; r < 140; r += 16) {
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
        }

        // Spiderweb radial fracture lines radiating outward
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double angle = 0; angle < Math.PI * 2; angle += 0.28) {
            int len = 120 + (int)(Math.sin(angle * 4) * 60) + (int)(Math.random() * 40);
            int ex = cx + (int)(Math.cos(angle) * len);
            int ey = cy + (int)(Math.sin(angle) * len);

            GeneralPath ray = new GeneralPath();
            ray.moveTo(cx, cy);
            int midX = cx + (int)(Math.cos(angle + 0.1) * (len / 2));
            int midY = cy + (int)(Math.sin(angle + 0.1) * (len / 2));
            ray.lineTo(midX, midY);
            ray.lineTo(ex, ey);
            g.draw(ray);
        }

        // Crushed white glass powdered core
        g.setColor(new Color(255, 255, 255, 240));
        g.fillOval(cx - 24, cy - 24, 48, 48);

        // Dented aluminum frame at border
        g.setColor(new Color(80, 85, 95));
        g.fillRect(8, 120, 28, 120);

        g.dispose();
        return img;
    }

    private static void saveImage(BufferedImage img, File file) throws IOException {
        ImageIO.write(img, "jpg", file);
    }
}
