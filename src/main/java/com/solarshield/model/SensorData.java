package com.solarshield.model;

/**
 * Model class encapsulating optional real-time electrical and environmental sensor telemetry.
 */
public class SensorData {
    private Double temperature; // in °C
    private Double voltage;     // in Volts
    private Double current;     // in Amperes
    private Double irradiance;  // in W/m²
    private Double humidity;    // in %

    public SensorData() {}

    public SensorData(Double temperature, Double voltage, Double current, Double irradiance, Double humidity) {
        this.temperature = temperature;
        this.voltage = voltage;
        this.current = current;
        this.irradiance = irradiance;
        this.humidity = humidity;
    }

    public boolean hasElectricalReadings() {
        return voltage != null && current != null;
    }

    public Double calculateMeasuredPower() {
        if (hasElectricalReadings()) {
            return voltage * current;
        }
        return null;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getVoltage() {
        return voltage;
    }

    public void setVoltage(Double voltage) {
        this.voltage = voltage;
    }

    public Double getCurrent() {
        return current;
    }

    public void setCurrent(Double current) {
        this.current = current;
    }

    public Double getIrradiance() {
        return irradiance;
    }

    public void setIrradiance(Double irradiance) {
        this.irradiance = irradiance;
    }

    public Double getHumidity() {
        return humidity;
    }

    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }

    @Override
    public String toString() {
        return String.format("SensorData[T=%s°C, V=%sV, I=%sA, G=%sW/m², H=%s%%]",
                temperature != null ? temperature : "N/A",
                voltage != null ? voltage : "N/A",
                current != null ? current : "N/A",
                irradiance != null ? irradiance : "N/A",
                humidity != null ? humidity : "N/A");
    }
}
