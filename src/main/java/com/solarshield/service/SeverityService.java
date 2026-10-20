package com.solarshield.service;

import com.solarshield.model.BoundingBox;
import com.solarshield.model.FaultType;
import com.solarshield.model.SensorData;
import com.solarshield.model.Severity;

/**
 * Service evaluating and computing fault severity based on defect physics,
 * affected area proportion, and electrical telemetry.
 */
public class SeverityService {

    /**
     * Determines Severity based on defect characteristics and optional sensor telemetry.
     */
    public Severity evaluateSeverity(FaultType faultType, double confidence, BoundingBox box,
                                     SensorData sensorData, double ratedPower) {
        if (faultType == null || faultType == FaultType.HEALTHY) {
            return Severity.LOW;
        }

        // Base severity from fault type intrinsic risk
        int score = 0;
        switch (faultType) {
            case SOILING:
                score = 2; // Medium baseline
                break;
            case PARTIAL_SHADING:
                score = 2;
                break;
            case CRACK:
                score = 3; // High risk of propagation
                break;
            case HOTSPOT:
                score = 3; // Fire and cell burn hazard
                break;
            case PHYSICAL_DAMAGE:
                score = 4; // Critical glass destruction
                break;
            default:
                score = 1;
        }

        // Check environmental telemetry escalation
        if (sensorData != null) {
            // Extreme temperature escalation
            if (sensorData.getTemperature() != null) {
                if (sensorData.getTemperature() >= 65.0) {
                    score = Math.max(score, 4); // Immediate Critical
                } else if (sensorData.getTemperature() >= 52.0) {
                    score = Math.max(score, 3);
                }
            }

            // Severe electrical degradation (power drop > 40% of rated capacity under good sunlight)
            Double measuredPower = sensorData.calculateMeasuredPower();
            if (measuredPower != null && sensorData.getIrradiance() != null && sensorData.getIrradiance() > 700.0) {
                double expectedMin = ratedPower * 0.65;
                if (measuredPower < expectedMin) {
                    score = Math.min(4, score + 1);
                }
            }
        }

        // Affected area factor (if large section of module is damaged)
        if (box != null && box.isValid()) {
            int area = box.getArea();
            if (area > 150000) { // Large proportion of standard 800x600 image
                score = Math.min(4, score + 1);
            }
        }

        // Map score to Severity Enum
        switch (score) {
            case 1:
                return Severity.LOW;
            case 2:
                return Severity.MEDIUM;
            case 3:
                return Severity.HIGH;
            case 4:
            default:
                return Severity.CRITICAL;
        }
    }
}
