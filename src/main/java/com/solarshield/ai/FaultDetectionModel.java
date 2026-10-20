package com.solarshield.ai;

import com.solarshield.model.SensorData;
import org.opencv.core.Mat;

/**
 * Interface representing a solar panel fault detection model.
 * Demonstrates Java OOP Abstraction and Polymorphism.
 * Real trained deep learning models (ONNX, TensorFlow, PyTorch) or computer vision
 * heuristic engines implement this contract interchangeably.
 */
public interface FaultDetectionModel {

    /**
     * Executes inference on the preprocessed solar panel image matrix
     * and optional environmental telemetry.
     *
     * @param image      Preprocessed OpenCV Mat of the solar panel module
     * @param sensorData Environmental and electrical telemetry (optional)
     * @return ModelPrediction containing fault classification, confidence, and localization
     */
    ModelPrediction predict(Mat image, SensorData sensorData);

    /**
     * Human-readable name of this model implementation.
     */
    String getModelName();

    /**
     * Category/description of this model (e.g. Heuristic CV, Demo Fallback, Deep Learning).
     */
    String getModelType();

    /**
     * Indicates whether this model is a real trained deep neural network
     * or a fallback heuristic/demo model.
     */
    boolean isTrainedNeuralNetwork();
}
