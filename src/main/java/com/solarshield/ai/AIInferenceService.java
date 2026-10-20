package com.solarshield.ai;

import com.solarshield.model.SensorData;
import com.solarshield.util.OpenCVLoader;
import org.opencv.core.Mat;

/**
 * Service managing AI model execution and polymorphic model switching.
 * Encapsulates the active inference backend (OpenCV Vision vs Demo Fallback vs future Deep Learning ONNX).
 */
public class AIInferenceService {

    private FaultDetectionModel activeModel;

    public AIInferenceService() {
        // Default to OpenCV if native library is functional, otherwise gracefully fallback
        if (OpenCVLoader.isAvailable()) {
            this.activeModel = new OpenCVFaultDetector();
        } else {
            this.activeModel = new MockAIModel();
        }
    }

    public ModelPrediction runInference(Mat image, SensorData sensorData) {
        if (activeModel == null) {
            activeModel = new MockAIModel();
        }
        return activeModel.predict(image, sensorData);
    }

    public void setModel(FaultDetectionModel model) {
        if (model != null) {
            this.activeModel = model;
            System.out.println("[AIInferenceService] Active model switched to: " + model.getModelName());
        }
    }

    public void setUseOpenCvEngine() {
        if (OpenCVLoader.isAvailable()) {
            this.activeModel = new OpenCVFaultDetector();
        } else {
            this.activeModel = new MockAIModel();
        }
    }

    public void setUseDemoFallbackEngine() {
        this.activeModel = new MockAIModel();
    }

    public FaultDetectionModel getActiveModel() {
        return activeModel;
    }

    public String getActiveModelName() {
        return activeModel != null ? activeModel.getModelName() : "None";
    }

    public String getActiveModelType() {
        return activeModel != null ? activeModel.getModelType() : "Unknown";
    }
}
