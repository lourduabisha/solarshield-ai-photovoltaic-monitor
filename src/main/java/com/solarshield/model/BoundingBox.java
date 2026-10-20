package com.solarshield.model;

/**
 * Model class encapsulating localized spatial defect coordinates on a solar panel image.
 * Demonstrates encapsulation with private fields, validation, and helper methods.
 */
public class BoundingBox {
    private int x;
    private int y;
    private int width;
    private int height;
    private String regionLabel;

    public BoundingBox() {
        this(0, 0, 0, 0, "No defect region");
    }

    public BoundingBox(int x, int y, int width, int height, String regionLabel) {
        this.x = Math.max(0, x);
        this.y = Math.max(0, y);
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.regionLabel = regionLabel != null ? regionLabel : "Detected region";
    }

    public boolean isValid() {
        return width > 0 && height > 0;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = Math.max(0, x);
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = Math.max(0, y);
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = Math.max(0, width);
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = Math.max(0, height);
    }

    public String getRegionLabel() {
        return regionLabel;
    }

    public void setRegionLabel(String regionLabel) {
        this.regionLabel = regionLabel;
    }

    /**
     * Calculates the area of the localized bounding region in pixels.
     */
    public int getArea() {
        return width * height;
    }

    @Override
    public String toString() {
        return String.format("[x=%d, y=%d, w=%d, h=%d - %s]", x, y, width, height, regionLabel);
    }
}
