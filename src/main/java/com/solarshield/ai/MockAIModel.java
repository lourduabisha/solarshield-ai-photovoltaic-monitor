package com.solarshield.ai;

import com.solarshield.model.*;
import org.opencv.core.Mat;

/**
 * Mock AI Inference engine providing deterministic fallback diagnostics for offline or testing workflows.
 *
 * IMPORTANT ACADEMIC HONESTY NOTICE:
 * This class is explicitly labeled as a "DEMO AI / FALLBACK MODEL".
 * It demonstrates architectural separation of concerns, polymorphic interchangeability,
 * and interface contracts without masquerading as a trained neural network.
 */
public class MockAIModel implements FaultDetectionModel {

    @Override
    public String getModelName() {
        return "SolarShield Demo Engine (Fallback)";
    }

    @Override
    public String getModelType() {
        return "DEMO AI / FALLBACK MODEL";
    }

    @Override
    public boolean isTrainedNeuralNetwork() {
        return false;
    }

    @Override
    public ModelPrediction predict(Mat image, SensorData sensorData) {
        int width = (image != null && !image.empty()) ? image.cols() : 800;
        int height = (image != null && !image.empty()) ? image.rows() : 600;

        // Compute deterministic seed based on image dimension and properties
        long hashSeed = (long) width * 31 + height * 17;
        if (image != null && !image.empty()) {
            double[] pixelSample = image.get(height / 2, width / 2);
            if (pixelSample != null && pixelSample.length > 0) {
                hashSeed += (long) (pixelSample[0] * 100);
            }
        }

        // Account for environmental telemetry if supplied
        FaultType faultType;
        double confidence;
        Severity severity;
        MaintenancePriority priority;
        BoundingBox box;
        String notes;

        if (sensorData != null && sensorData.getTemperature() != null && sensorData.getTemperature() > 62.0) {
            faultType = FaultType.HOTSPOT;
            confidence = 94.2;
            severity = Severity.HIGH;
            priority = MaintenancePriority.URGENT;
            box = new BoundingBox((int)(width * 0.55), (int)(height * 0.25), (int)(width * 0.25), (int)(height * 0.3), "Upper-Right Cell Group - Thermal Hotspot");
            notes = "DEMO AI FALLBACK: High module temperature detected (>60°C). Indicative of localized thermal dissipation anomaly.";

        } else {
            // Deterministic selection based on seed
            int modulo = (int) (Math.abs(hashSeed) % 5);
            switch (modulo) {
                case 0:
                    faultType = FaultType.HEALTHY;
                    confidence = 98.4;
                    severity = Severity.LOW;
                    priority = MaintenancePriority.LOW;
                    box = new BoundingBox(0, 0, 0, 0, "Nominal Cell Matrix");
                    notes = "DEMO AI FALLBACK: Module exhibits uniform surface emissivity and intact cell conductors.";
                    break;
                case 1:
                    faultType = FaultType.SOILING;
                    confidence = 91.6;
                    severity = Severity.MEDIUM;
                    priority = MaintenancePriority.MEDIUM;
                    box = new BoundingBox((int)(width * 0.15), (int)(height * 0.15), (int)(width * 0.7), (int)(height * 0.7), "Surface Particulate Deposition Area");
                    notes = "DEMO AI FALLBACK: Diffuse surface attenuation consistent with particulate dust or bird droppings.";
                    break;
                case 2:
                    faultType = FaultType.HOTSPOT;
                    confidence = 93.8;
                    severity = Severity.HIGH;
                    priority = MaintenancePriority.HIGH;
                    box = new BoundingBox((int)(width * 0.6), (int)(height * 0.2), (int)(width * 0.25), (int)(height * 0.25), "Top-Right Module - Localized Hotspot");
                    notes = "DEMO AI FALLBACK: Localized heat anomaly signature simulated on cell segment.";
                    break;
                case 3:
                    faultType = FaultType.CRACK;
                    confidence = 89.7;
                    severity = Severity.HIGH;
                    priority = MaintenancePriority.HIGH;
                    box = new BoundingBox((int)(width * 0.3), (int)(height * 0.4), (int)(width * 0.35), (int)(height * 0.35), "Lower-Center Silicon Cell Fracture");
                    notes = "DEMO AI FALLBACK: Structural micro-fracture pattern simulated across solar wafer.";
                    break;
                default:
                    faultType = FaultType.PARTIAL_SHADING;
                    confidence = 95.1;
                    severity = Severity.MEDIUM;
                    priority = MaintenancePriority.MEDIUM;
                    box = new BoundingBox(0, (int)(height * 0.5), width, (int)(height * 0.5), "Bottom Row Obstruction & Shading");
                    notes = "DEMO AI FALLBACK: Continuous shadow gradient identified along bottom module string.";
                    break;
            }
        }

        ModelPrediction pred = new ModelPrediction(
                faultType, confidence, severity, priority, box, true, getModelName(), notes
        );
        pred.addMetric("Engine Status", "Demo Fallback Mode Active");
        pred.addMetric("Deterministic Seed", hashSeed);
        return pred;
    }
}
