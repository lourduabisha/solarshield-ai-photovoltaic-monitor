package com.solarshield.service;

import com.solarshield.model.FaultType;
import com.solarshield.model.MaintenancePriority;
import com.solarshield.model.SensorData;
import com.solarshield.model.Severity;

/**
 * Service estimating photovoltaic energy and yield degradation impact.
 *
 * IMPORTANT:
 * All calculations are clearly documented and labeled as engineering estimations
 * rather than laboratory-grade certified measurements.
 */
public class EnergyLossService {

    public static class EnergyLossEstimate {
        private final double lossWatts;
        private final double lossPercent;
        private final String explanation;

        public EnergyLossEstimate(double lossWatts, double lossPercent, String explanation) {
            this.lossWatts = Math.round(lossWatts * 10.0) / 10.0;
            this.lossPercent = Math.round(lossPercent * 10.0) / 10.0;
            this.explanation = explanation;
        }

        public double getLossWatts() {
            return lossWatts;
        }

        public double getLossPercent() {
            return lossPercent;
        }

        public String getExplanation() {
            return explanation;
        }

        @Override
        public String toString() {
            return String.format("%.1f W (%.1f%% impact)", lossWatts, lossPercent);
        }
    }

    /**
     * Estimates potential power loss in Watts and percentage.
     */
    public EnergyLossEstimate estimateLoss(double ratedPower, FaultType faultType, Severity severity, SensorData sensorData) {
        if (faultType == null || faultType == FaultType.HEALTHY || ratedPower <= 0) {
            return new EnergyLossEstimate(0.0, 0.0, "Nominal operation; 0% estimated yield loss.");
        }

        // Empirical base loss percentage by defect mechanism
        double baseLossPct;
        switch (faultType) {
            case SOILING:
                baseLossPct = 8.5; // Dust/dirt attenuation (5% - 15%)
                break;
            case PARTIAL_SHADING:
                baseLossPct = 22.0; // Bypass diode activation causing string loss
                break;
            case CRACK:
                baseLossPct = 14.5; // Wafer micro-fracture conductor disruption
                break;
            case HOTSPOT:
                baseLossPct = 25.0; // Dissipative reverse-bias resistance
                break;
            case PHYSICAL_DAMAGE:
                baseLossPct = 65.0; // Major structural failure
                break;
            default:
                baseLossPct = 5.0;
        }

        // Scale by computed severity
        double severityMultiplier;
        switch (severity) {
            case LOW:
                severityMultiplier = 0.7;
                break;
            case MEDIUM:
                severityMultiplier = 1.0;
                break;
            case HIGH:
                severityMultiplier = 1.35;
                break;
            case CRITICAL:
            default:
                severityMultiplier = 1.65;
        }

        double lossPercent = Math.min(95.0, baseLossPct * severityMultiplier);

        // Refine with sensor telemetry if available
        if (sensorData != null && sensorData.hasElectricalReadings() && sensorData.getIrradiance() != null && sensorData.getIrradiance() > 500) {
            double measured = sensorData.calculateMeasuredPower();
            double theoreticalExpected = ratedPower * (sensorData.getIrradiance() / 1000.0);
            if (theoreticalExpected > 0 && measured < theoreticalExpected) {
                double empiricalDrop = ((theoreticalExpected - measured) / theoreticalExpected) * 100.0;
                lossPercent = Math.min(95.0, (lossPercent * 0.4) + (empiricalDrop * 0.6));
            }
        }

        double lossWatts = ratedPower * (lossPercent / 100.0);
        String explanation = String.format("Estimated Power Loss: %.1f W (%.1f%% module derating based on %s signature).",
                lossWatts, lossPercent, faultType.getDisplayName());

        return new EnergyLossEstimate(lossWatts, lossPercent, explanation);
    }

    /**
     * Determines maintenance urgency priority based on severity, fault type, and energy loss.
     */
    public MaintenancePriority determineMaintenancePriority(Severity severity, FaultType faultType, double lossPercent) {
        if (faultType == FaultType.HEALTHY) {
            return MaintenancePriority.LOW;
        }

        if (severity == Severity.CRITICAL || faultType == FaultType.PHYSICAL_DAMAGE || lossPercent > 50.0) {
            return MaintenancePriority.URGENT;
        }

        if (severity == Severity.HIGH || faultType == FaultType.HOTSPOT || lossPercent > 20.0) {
            return MaintenancePriority.HIGH;
        }

        if (severity == Severity.MEDIUM || lossPercent > 10.0) {
            return MaintenancePriority.MEDIUM;
        }

        return MaintenancePriority.LOW;
    }

    public String getActionableRecommendation(MaintenancePriority priority, FaultType faultType) {
        switch (priority) {
            case URGENT:
                return "Immediate inspection required. De-energize string if fire/thermal risk persists and dispatch field team.";
            case HIGH:
                return "Urgent field intervention recommended within 48 hours to avert permanent cell damage.";
            case MEDIUM:
                return "Schedule maintenance within 14 business days. Plan panel surface cleaning or bypass diode test.";
            case LOW:
            default:
                return "Routine monitoring. Log for regular scheduled maintenance cycle.";
        }
    }
}
