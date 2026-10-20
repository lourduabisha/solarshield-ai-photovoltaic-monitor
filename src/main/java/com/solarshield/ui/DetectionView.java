package com.solarshield.ui;

import com.solarshield.database.SolarPanelRepository;
import com.solarshield.model.*;
import com.solarshield.service.EnergyLossService;
import com.solarshield.service.FaultDetectionService;
import com.solarshield.ui.component.ComparisonSliderView;
import com.solarshield.ui.component.ImageZoomDialog;
import com.solarshield.util.AlertUtils;
import com.solarshield.util.ImageUtils;
import com.solarshield.util.OpenCVLoader;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import com.solarshield.service.ReportService;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Diagnostic View: AI-assisted solar panel fault detection and localization workspace.
 * Features:
 * - Single Module Inspection:
 *   - Dual View (Side-by-side) & Interactive Before/After Split-Screen Slider
 *   - High-Resolution Zoom & Pan Modal with HUD Reticle toggle
 *   - Webcam live frame capture & synthetic sample presets
 *   - Physical sensor telemetry fusion & comprehensive diagnostic card
 * - Batch Farm Multi-Image Inspection:
 *   - Bulk image analysis with real-time progress bar
 *   - Aggregate statistics (healthy, defects, critical risk, total energy derating)
 *   - Interactive batch results table with thumbnail previews and row inspection
 *   - Batch database persistence and CSV summary export
 */
public class DetectionView extends ScrollPane {

    private final FaultDetectionService detectionService;
    private final SolarPanelRepository panelRepository;
    private final Consumer<FaultReport> onScheduleMaintenance;
    private final Consumer<FaultReport> onGenerateReport;

    private TabPane mainTabPane;

    // === SINGLE INSPECTION FIELDS ===
    private ComboBox<SolarPanel> panelComboBox;
    private File selectedImageFile;
    private Mat capturedMat;
    private Image currentRawImage;
    private Image currentAnalyzedImage;

    // Dual image comparison elements
    private HBox dualPreviewBox;
    private ImageView originalImageView;
    private ImageView analyzedImageView;
    private ComparisonSliderView comparisonSliderView;
    private ToggleButton dualViewToggle;
    private ToggleButton sliderViewToggle;
    private Button openZoomInspectorBtn;
    private ComboBox<ThermalColormap> thermalPaletteCombo;

    private TextField tempField;
    private TextField voltField;
    private TextField currField;
    private TextField irrField;
    private TextField humField;

    // Results UI Elements
    private VBox resultCard;
    private Label resultBanner;
    private Label faultTypeLbl;
    private Label confidenceLbl;
    private Label severityLbl;
    private Label priorityLbl;
    private Label energyLossLbl;
    private Label regionLbl;
    private Label engineBadge;
    private Label recommendationLbl;

    private Button saveResultBtn;
    private Button addMaintenanceBtn;
    private Button createReportBtn;
    private Button analyzeBtn;

    private FaultDetectionService.DetectionResult currentResult;
    private FaultReport savedReport;

    // === BATCH INSPECTION FIELDS ===
    private final ObservableList<BatchDetectionItem> batchItems = FXCollections.observableArrayList();
    private FilteredList<BatchDetectionItem> filteredBatchItems;
    private TableView<BatchDetectionItem> batchTable;
    private ComboBox<SolarPanel> batchPanelCombo;
    private ComboBox<String> assignmentModeCombo;
    private TextField missionNameField;
    private TextField batchSearchField;
    private ComboBox<String> batchFilterCombo;
    private Label batchCountSummaryLbl;
    private ProgressBar batchProgressBar;
    private Label batchProgressLabel;
    private Button runBatchBtn;
    private Button cancelBatchBtn;
    private Button saveAllBatchBtn;
    private Button exportBatchCsvBtn;
    private Button exportBatchPdfBtn;
    private Task<Void> activeBatchTask;
    private final ReportService reportService = new ReportService();

    // Batch summary labels
    private Label batchTotalLbl;
    private Label batchHealthyLbl;
    private Label batchFaultyLbl;
    private Label batchCriticalLbl;
    private Label batchLossLbl;

    public DetectionView(FaultDetectionService detectionService,
                          SolarPanelRepository panelRepository,
                          Consumer<FaultReport> onScheduleMaintenance,
                          Consumer<FaultReport> onGenerateReport) {
        this.detectionService = detectionService;
        this.panelRepository = panelRepository;
        this.onScheduleMaintenance = onScheduleMaintenance;
        this.onGenerateReport = onGenerateReport;

        setFitToWidth(true);
        setStyle("-fx-background: #f1f5f9; -fx-background-color: transparent;");

        initUI();
        refreshPanelList();
    }

    private void initUI() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20));

        // Header Banner
        VBox header = new VBox(4);
        Label title = new Label("AI SOLAR FAULT DETECTION & INSPECTION SUITE");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        Label subtitle = new Label("Multi-spectral defect localization, interactive comparison slider, high-res zoom, and batch farm processing");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        header.getChildren().addAll(title, subtitle);

        // Tab Pane for Single vs Batch
        mainTabPane = new TabPane();
        mainTabPane.setStyle("-fx-background-color: transparent;");

        Tab singleTab = new Tab("🔍 Single Module Inspection", createSingleInspectionView());
        singleTab.setClosable(false);

        Tab batchTab = new Tab("⚡ Batch Farm Inspection", createBatchInspectionView());
        batchTab.setClosable(false);

        mainTabPane.getTabs().addAll(singleTab, batchTab);

        root.getChildren().addAll(header, mainTabPane);
        setContent(root);
    }

    // =========================================================================
    // 1. SINGLE MODULE INSPECTION WORKSPACE
    // =========================================================================
    private VBox createSingleInspectionView() {
        VBox singleBox = new VBox(18);
        singleBox.setPadding(new Insets(14, 0, 14, 0));

        // 1. Top Config Card: Panel Selection + Image Input + Optional Telemetry
        VBox configCard = new VBox(16);
        configCard.getStyleClass().add("card");

        // Row 1: Select Solar Panel
        HBox panelSelectRow = new HBox(14);
        panelSelectRow.setAlignment(Pos.CENTER_LEFT);

        Label selectLbl = new Label("Select Target Solar Panel: *");
        selectLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e293b; -fx-font-size: 13px;");

        panelComboBox = new ComboBox<>();
        panelComboBox.setPrefWidth(320);
        panelComboBox.setPromptText("Choose panel module...");

        Button refreshPanelsBtn = new Button("🔄");
        refreshPanelsBtn.getStyleClass().add("btn-outline");
        refreshPanelsBtn.setOnAction(e -> refreshPanelList());

        panelSelectRow.getChildren().addAll(selectLbl, panelComboBox, refreshPanelsBtn);

        // Row 2: Image Input Controls
        HBox imgInputRow = new HBox(12);
        imgInputRow.setAlignment(Pos.CENTER_LEFT);

        Label imgInputLbl = new Label("Image Input Source: *");
        imgInputLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e293b; -fx-font-size: 13px;");

        Button uploadBtn = new Button("📁 Upload Solar Image");
        uploadBtn.getStyleClass().add("btn-primary");
        uploadBtn.setOnAction(e -> handleUploadImage());

        Button sampleBtn = new Button("🔬 Load Demo Test Sample");
        sampleBtn.getStyleClass().add("btn-outline");
        sampleBtn.setOnAction(e -> handleSelectDemoSample());

        Button webcamBtn = new Button("📷 Capture From Camera");
        webcamBtn.getStyleClass().add("btn-outline");
        webcamBtn.setOnAction(e -> handleWebcamCapture());

        imgInputRow.getChildren().addAll(imgInputLbl, uploadBtn, sampleBtn, webcamBtn);

        // Row 2b: Thermal Colormap & Multi-Spectral Preprocessing Palette
        HBox thermalRow = new HBox(12);
        thermalRow.setAlignment(Pos.CENTER_LEFT);

        Label thermalLbl = new Label("Thermography / Spectral Palette:");
        thermalLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e293b; -fx-font-size: 13px;");

        thermalPaletteCombo = new ComboBox<>();
        thermalPaletteCombo.getItems().addAll(ThermalColormap.values());
        thermalPaletteCombo.setValue(ThermalColormap.NATURAL);
        thermalPaletteCombo.setPrefWidth(280);
        thermalPaletteCombo.setOnAction(e -> handleApplyThermalPalette());

        Label thermalHint = new Label("Simulates FLIR/DJI radiometric palettes & adaptive CLAHE multi-spectral micro-crack enhancement");
        thermalHint.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-style: italic;");

        thermalRow.getChildren().addAll(thermalLbl, thermalPaletteCombo, thermalHint);

        // Row 3: Optional Environmental & Electrical Sensor Inputs
        TitledPane sensorPane = createSensorTelemetryPane();

        // Row 4: Action Buttons (Analyze & Clear)
        HBox actionRow = new HBox(14);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        analyzeBtn = new Button("⚡ ANALYZE IMAGE WITH AI");
        analyzeBtn.getStyleClass().add("btn-success");
        analyzeBtn.setPrefHeight(40);
        analyzeBtn.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-padding: 8 22 8 22;");
        analyzeBtn.setOnAction(e -> handleRunDetection());

        Button clearBtn = new Button("Clear Workspace");
        clearBtn.getStyleClass().add("btn-outline");
        clearBtn.setPrefHeight(40);
        clearBtn.setOnAction(e -> handleClear());

        actionRow.getChildren().addAll(analyzeBtn, clearBtn);

        configCard.getChildren().addAll(panelSelectRow, new Separator(), imgInputRow, thermalRow, sensorPane, actionRow);

        // 2. View Mode Switcher & Inspector Toolbar
        HBox viewToolbar = new HBox(12);
        viewToolbar.setAlignment(Pos.CENTER_LEFT);

        Label modeLbl = new Label("Inspection View Mode:");
        modeLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155; -fx-font-size: 12px;");

        ToggleGroup viewModeGroup = new ToggleGroup();
        dualViewToggle = new ToggleButton("◫ Dual Side-by-Side");
        dualViewToggle.setToggleGroup(viewModeGroup);
        dualViewToggle.setSelected(true);
        dualViewToggle.getStyleClass().addAll("btn-outline", "segmented-toggle-left");

        sliderViewToggle = new ToggleButton("⬌ Interactive Split Slider");
        sliderViewToggle.setToggleGroup(viewModeGroup);
        sliderViewToggle.getStyleClass().addAll("btn-outline", "segmented-toggle-right");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        openZoomInspectorBtn = new Button("🔍 Open High-Res Zoom Inspector");
        openZoomInspectorBtn.getStyleClass().add("btn-primary");
        openZoomInspectorBtn.setDisable(true);
        openZoomInspectorBtn.setOnAction(e -> handleOpenZoomInspector());

        viewToolbar.getChildren().addAll(modeLbl, dualViewToggle, sliderViewToggle, spacer, openZoomInspectorBtn);

        // 3. Image Comparison Display Area
        // Dual Side-by-Side View
        dualPreviewBox = new HBox(20);
        dualPreviewBox.setAlignment(Pos.CENTER);

        VBox rawBox = new VBox(8);
        rawBox.getStyleClass().add("card");
        HBox.setHgrow(rawBox, Priority.ALWAYS);
        rawBox.setAlignment(Pos.CENTER);
        Label rawTitle = new Label("ORIGINAL INPUT IMAGE");
        rawTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #475569;");
        originalImageView = new ImageView();
        originalImageView.setFitWidth(420);
        originalImageView.setFitHeight(300);
        originalImageView.setPreserveRatio(true);
        originalImageView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && currentRawImage != null) handleOpenZoomInspector();
        });
        rawBox.getChildren().addAll(rawTitle, originalImageView);

        VBox analyzedBox = new VBox(8);
        analyzedBox.getStyleClass().add("card");
        HBox.setHgrow(analyzedBox, Priority.ALWAYS);
        analyzedBox.setAlignment(Pos.CENTER);
        Label analyzedTitle = new Label("AI LOCALIZATION & HUD OVERLAY (Double-click to Zoom)");
        analyzedTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #0284c7;");
        analyzedImageView = new ImageView();
        analyzedImageView.setFitWidth(420);
        analyzedImageView.setFitHeight(300);
        analyzedImageView.setPreserveRatio(true);
        analyzedImageView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && currentAnalyzedImage != null) handleOpenZoomInspector();
        });
        analyzedBox.getChildren().addAll(analyzedTitle, analyzedImageView);

        dualPreviewBox.getChildren().addAll(rawBox, analyzedBox);

        // Split-Screen Comparison Slider View
        comparisonSliderView = new ComparisonSliderView();
        comparisonSliderView.setVisible(false);
        comparisonSliderView.setManaged(false);

        StackPane imageDisplayContainer = new StackPane(dualPreviewBox, comparisonSliderView);
        imageDisplayContainer.setAlignment(Pos.CENTER);

        // Mode switch listener
        viewModeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == sliderViewToggle) {
                dualPreviewBox.setVisible(false);
                dualPreviewBox.setManaged(false);
                comparisonSliderView.setVisible(true);
                comparisonSliderView.setManaged(true);
            } else {
                comparisonSliderView.setVisible(false);
                comparisonSliderView.setManaged(false);
                dualPreviewBox.setVisible(true);
                dualPreviewBox.setManaged(true);
            }
        });

        // 4. Diagnostic Results Card
        resultCard = createResultCard();
        resultCard.setVisible(false);
        resultCard.setManaged(false);

        singleBox.getChildren().addAll(configCard, viewToolbar, imageDisplayContainer, resultCard);
        return singleBox;
    }

    private TitledPane createSensorTelemetryPane() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        tempField = new TextField();
        tempField.setPromptText("e.g. 48.5");
        tempField.setPrefWidth(100);

        voltField = new TextField();
        voltField.setPromptText("e.g. 36.2");
        voltField.setPrefWidth(100);

        currField = new TextField();
        currField.setPromptText("e.g. 8.4");
        currField.setPrefWidth(100);

        irrField = new TextField();
        irrField.setPromptText("e.g. 850.0");
        irrField.setPrefWidth(100);

        humField = new TextField();
        humField.setPromptText("e.g. 35.0");
        humField.setPrefWidth(100);

        grid.add(new Label("Temperature (°C):"), 0, 0); grid.add(tempField, 1, 0);
        grid.add(new Label("Voltage (V):"), 2, 0); grid.add(voltField, 3, 0);
        grid.add(new Label("Current (A):"), 4, 0); grid.add(currField, 5, 0);

        grid.add(new Label("Irradiance (W/m²):"), 0, 1); grid.add(irrField, 1, 1);
        grid.add(new Label("Humidity (%):"), 2, 1); grid.add(humField, 3, 1);

        TitledPane pane = new TitledPane("Optional Operating & Environmental Telemetry (In Situ Sensor Readings)", grid);
        pane.setExpanded(false);
        return pane;
    }

    private VBox createResultCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);

        resultBanner = new Label("FAULT DETECTED");
        resultBanner.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-padding: 6 16 6 16; -fx-background-radius: 6px;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        engineBadge = new Label("DEMO AI / FALLBACK MODEL");
        engineBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-padding: 4 10 4 10; -fx-background-radius: 12px;");

        top.getChildren().addAll(resultBanner, sp, engineBadge);

        // Metrics Grid
        GridPane grid = new GridPane();
        grid.setHgap(28);
        grid.setVgap(14);
        grid.setPadding(new Insets(10, 0, 10, 0));

        faultTypeLbl = new Label("-");
        faultTypeLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        confidenceLbl = new Label("-");
        confidenceLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #0284c7;");

        severityLbl = new Label("-");
        severityLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 800;");

        priorityLbl = new Label("-");
        priorityLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 800;");

        energyLossLbl = new Label("-");
        energyLossLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #dc2626;");

        regionLbl = new Label("-");
        regionLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #334155;");

        addMetricRow(grid, 0, 0, "FAULT CLASSIFICATION:", faultTypeLbl);
        addMetricRow(grid, 2, 0, "CONFIDENCE SCORE:", confidenceLbl);
        addMetricRow(grid, 0, 1, "RISK SEVERITY:", severityLbl);
        addMetricRow(grid, 2, 1, "MAINTENANCE PRIORITY:", priorityLbl);
        addMetricRow(grid, 0, 2, "ESTIMATED POWER LOSS:", energyLossLbl);
        addMetricRow(grid, 2, 2, "AFFECTED REGION:", regionLbl);

        // Recommendation Box
        VBox recBox = new VBox(6);
        recBox.setPadding(new Insets(12));
        recBox.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 8px; -fx-border-color: #e2e8f0; -fx-border-radius: 8px;");
        Label recTitle = new Label("Actionable Engineering Recommendation:");
        recTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #475569;");
        recommendationLbl = new Label("-");
        recommendationLbl.setWrapText(true);
        recommendationLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #0f172a;");
        recBox.getChildren().addAll(recTitle, recommendationLbl);

        // Action Buttons Row
        HBox btnRow = new HBox(14);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        saveResultBtn = new Button("💾 Save Diagnostic Result to Database");
        saveResultBtn.getStyleClass().add("btn-primary");
        saveResultBtn.setOnAction(e -> handleSaveResult());

        addMaintenanceBtn = new Button("🔧 Schedule Maintenance Task");
        addMaintenanceBtn.getStyleClass().add("btn-outline");
        addMaintenanceBtn.setOnAction(e -> {
            if (savedReport != null) {
                onScheduleMaintenance.accept(savedReport);
            } else if (currentResult != null) {
                handleSaveResult();
                if (savedReport != null) {
                    onScheduleMaintenance.accept(savedReport);
                }
            }
        });

        createReportBtn = new Button("📑 Generate PDF Report");
        createReportBtn.getStyleClass().add("btn-outline");
        createReportBtn.setOnAction(e -> {
            if (savedReport != null) {
                onGenerateReport.accept(savedReport);
            } else if (currentResult != null) {
                handleSaveResult();
                if (savedReport != null) {
                    onGenerateReport.accept(savedReport);
                }
            }
        });

        Button newDetectionBtn = new Button("New Inspection");
        newDetectionBtn.getStyleClass().add("btn-outline");
        newDetectionBtn.setOnAction(e -> handleClear());

        btnRow.getChildren().addAll(saveResultBtn, addMaintenanceBtn, createReportBtn, newDetectionBtn);

        card.getChildren().addAll(top, new Separator(), grid, recBox, new Separator(), btnRow);
        return card;
    }

    private void addMetricRow(GridPane grid, int col, int row, String title, Label valueLabel) {
        VBox box = new VBox(2);
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #64748b;");
        box.getChildren().addAll(t, valueLabel);
        grid.add(box, col, row);
    }

    // =========================================================================
    // 2. BATCH FARM MULTI-IMAGE INSPECTION WORKSPACE
    // =========================================================================
    private VBox createBatchInspectionView() {
        VBox batchBox = new VBox(16);
        batchBox.setPadding(new Insets(14, 0, 14, 0));

        // 1. Controls Card
        VBox controlsCard = new VBox(14);
        controlsCard.getStyleClass().add("card");

        // Row 1: Setup & Target Configuration
        HBox row1 = new HBox(14);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label batchPanelLbl = new Label("Default Solar Panel: *");
        batchPanelLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e293b; -fx-font-size: 13px;");

        batchPanelCombo = new ComboBox<>();
        batchPanelCombo.setPrefWidth(220);
        batchPanelCombo.setPromptText("Select target panel...");

        Label strategyLbl = new Label("Mapping:");
        strategyLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e293b; -fx-font-size: 13px;");

        assignmentModeCombo = new ComboBox<>();
        assignmentModeCombo.getItems().addAll(
                "Auto-Distribute Across Registered Farm Panels",
                "Smart Match by Filename (e.g. SP-001)",
                "Assign All to Selected Default Panel"
        );
        assignmentModeCombo.setValue("Auto-Distribute Across Registered Farm Panels");
        assignmentModeCombo.setPrefWidth(260);

        Label missionLbl = new Label("Mission Tag:");
        missionLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e293b; -fx-font-size: 13px;");

        missionNameField = new TextField("Drone-Survey-Alpha");
        missionNameField.setPrefWidth(180);

        row1.getChildren().addAll(batchPanelLbl, batchPanelCombo, strategyLbl, assignmentModeCombo, missionLbl, missionNameField);

        // Row 2: Ingestion & Loading Actions
        HBox row2 = new HBox(12);
        row2.setAlignment(Pos.CENTER_LEFT);

        Button addFolderBtn = new Button("🚁 Ingest Drone Survey Folder");
        addFolderBtn.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 16 8 16; -fx-cursor: hand; -fx-background-radius: 6px;");
        addFolderBtn.setOnAction(e -> handleScanDroneFolder());

        Button addFilesBtn = new Button("📁 Select Image Files");
        addFilesBtn.getStyleClass().add("btn-primary");
        addFilesBtn.setOnAction(e -> handleAddBatchFiles());

        Button addSamplesBtn = new Button("🔬 Load All 6 Synthetic Test Samples");
        addSamplesBtn.getStyleClass().add("btn-outline");
        addSamplesBtn.setOnAction(e -> handleLoadAllDemoSamples());

        Button clearBatchBtn = new Button("Clear List");
        clearBatchBtn.getStyleClass().add("btn-outline");
        clearBatchBtn.setOnAction(e -> handleClearBatch());

        row2.getChildren().addAll(addFolderBtn, addFilesBtn, addSamplesBtn, clearBatchBtn);

        // Row 3: Progress & Execution Bar
        HBox row3 = new HBox(12);
        row3.setAlignment(Pos.CENTER_LEFT);

        runBatchBtn = new Button("⚡ START BATCH ANALYSIS");
        runBatchBtn.getStyleClass().add("btn-success");
        runBatchBtn.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-padding: 8 18 8 18;");
        runBatchBtn.setOnAction(e -> handleStartBatch());

        cancelBatchBtn = new Button("⏹ Stop");
        cancelBatchBtn.getStyleClass().add("btn-danger");
        cancelBatchBtn.setDisable(true);
        cancelBatchBtn.setOnAction(e -> handleCancelBatch());

        batchProgressBar = new ProgressBar(0.0);
        batchProgressBar.setPrefWidth(240);
        batchProgressBar.setPrefHeight(22);

        batchProgressLabel = new Label("Ready to analyze");
        batchProgressLabel.setStyle("-fx-text-fill: #64748b; -fx-font-weight: 600; -fx-font-size: 12px;");

        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);

        saveAllBatchBtn = new Button("💾 Save All to Database");
        saveAllBatchBtn.getStyleClass().add("btn-outline");
        saveAllBatchBtn.setDisable(true);
        saveAllBatchBtn.setOnAction(e -> handleSaveAllBatch());

        exportBatchPdfBtn = new Button("📑 Export Batch Audit PDF");
        exportBatchPdfBtn.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 7 14 7 14; -fx-cursor: hand; -fx-background-radius: 6px;");
        exportBatchPdfBtn.setDisable(true);
        exportBatchPdfBtn.setOnAction(e -> handleExportBatchPdf());

        exportBatchCsvBtn = new Button("📄 Export Batch CSV");
        exportBatchCsvBtn.getStyleClass().add("btn-outline");
        exportBatchCsvBtn.setDisable(true);
        exportBatchCsvBtn.setOnAction(e -> handleExportBatchCsv());

        row3.getChildren().addAll(runBatchBtn, cancelBatchBtn, batchProgressBar, batchProgressLabel, sp2, saveAllBatchBtn, exportBatchPdfBtn, exportBatchCsvBtn);

        controlsCard.getChildren().addAll(row1, new Separator(), row2, new Separator(), row3);

        // 2. Summary Statistics Cards
        HBox summaryRow = new HBox(16);
        summaryRow.setAlignment(Pos.CENTER);

        batchTotalLbl = new Label("0");
        batchHealthyLbl = new Label("0");
        batchFaultyLbl = new Label("0");
        batchCriticalLbl = new Label("0");
        batchLossLbl = new Label("0.0 W");

        summaryRow.getChildren().addAll(
                createStatCard("TOTAL QUEUED", batchTotalLbl, "#0284c7"),
                createStatCard("HEALTHY MODULES", batchHealthyLbl, "#16a34a"),
                createStatCard("FAULTS DETECTED", batchFaultyLbl, "#d97706"),
                createStatCard("CRITICAL / HIGH", batchCriticalLbl, "#dc2626"),
                createStatCard("TOTAL DERATING LOSS", batchLossLbl, "#ef4444")
        );

        // 3. Search & Filter Bar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(6, 12, 6, 12));
        filterBar.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8px; -fx-border-color: #e2e8f0; -fx-border-radius: 8px;");

        Label searchLbl = new Label("Search:");
        searchLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        batchSearchField = new TextField();
        batchSearchField.setPromptText("Filter by filename, panel, or defect...");
        batchSearchField.setPrefWidth(260);
        batchSearchField.textProperty().addListener((obs, oldV, newV) -> applyBatchFilter());

        Label filterLbl = new Label("View Filter:");
        filterLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        batchFilterCombo = new ComboBox<>();
        batchFilterCombo.getItems().addAll("All Records", "Faults Only", "Healthy Only", "Critical & High Severity");
        batchFilterCombo.setValue("All Records");
        batchFilterCombo.setOnAction(e -> applyBatchFilter());

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        batchCountSummaryLbl = new Label("Showing 0 of 0 images");
        batchCountSummaryLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #64748b;");

        filterBar.getChildren().addAll(searchLbl, batchSearchField, filterLbl, batchFilterCombo, filterSpacer, batchCountSummaryLbl);

        // 4. TableView of Batch Items
        batchTable = new TableView<>();
        filteredBatchItems = new FilteredList<>(batchItems, p -> true);
        SortedList<BatchDetectionItem> sortedData = new SortedList<>(filteredBatchItems);
        sortedData.comparatorProperty().bind(batchTable.comparatorProperty());
        batchTable.setItems(sortedData);
        batchTable.setPlaceholder(new Label("No aerial images added. Click 'Ingest Drone Survey Folder' or 'Select Image Files'."));
        batchTable.setPrefHeight(360);

        // Thumbnail Column
        TableColumn<BatchDetectionItem, Image> thumbCol = new TableColumn<>("Preview");
        thumbCol.setPrefWidth(70);
        thumbCol.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getThumbnailImage()));
        thumbCol.setCellFactory(col -> new TableCell<BatchDetectionItem, Image>() {
            private final ImageView iv = new ImageView();
            {
                iv.setFitWidth(50);
                iv.setFitHeight(36);
                iv.setPreserveRatio(true);
            }
            @Override
            protected void updateItem(Image img, boolean empty) {
                super.updateItem(img, empty);
                if (empty || img == null) {
                    setGraphic(null);
                } else {
                    iv.setImage(img);
                    setGraphic(iv);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<BatchDetectionItem, String> fileCol = new TableColumn<>("Image File");
        fileCol.setPrefWidth(170);
        fileCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));

        TableColumn<BatchDetectionItem, String> missionCol = new TableColumn<>("Mission Tag");
        missionCol.setPrefWidth(130);
        missionCol.setCellValueFactory(new PropertyValueFactory<>("missionTag"));

        TableColumn<BatchDetectionItem, String> panelCol = new TableColumn<>("Assigned Panel");
        panelCol.setPrefWidth(125);
        panelCol.setCellValueFactory(new PropertyValueFactory<>("panelId"));

        TableColumn<BatchDetectionItem, String> faultCol = new TableColumn<>("Condition Detected");
        faultCol.setPrefWidth(145);
        faultCol.setCellValueFactory(new PropertyValueFactory<>("faultType"));
        faultCol.setCellFactory(col -> new TableCell<BatchDetectionItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");
                    if (item.equalsIgnoreCase("Healthy")) {
                        badge.getStyleClass().add("badge-healthy");
                    } else if (item.equalsIgnoreCase("Pending")) {
                        badge.getStyleClass().add("badge-info");
                    } else {
                        badge.getStyleClass().add("badge-faulty");
                    }
                    setGraphic(badge);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        TableColumn<BatchDetectionItem, String> confCol = new TableColumn<>("Confidence");
        confCol.setPrefWidth(90);
        confCol.setCellValueFactory(new PropertyValueFactory<>("confidence"));

        TableColumn<BatchDetectionItem, String> sevCol = new TableColumn<>("Severity");
        sevCol.setPrefWidth(85);
        sevCol.setCellValueFactory(new PropertyValueFactory<>("severity"));
        sevCol.setCellFactory(col -> new TableCell<BatchDetectionItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("-")) {
                    setText(item);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("CRITICAL")) setStyle("-fx-text-fill: #991b1b; -fx-font-weight: 800;");
                    else if (item.equals("HIGH")) setStyle("-fx-text-fill: #dc2626; -fx-font-weight: 800;");
                    else if (item.equals("MEDIUM")) setStyle("-fx-text-fill: #d97706; -fx-font-weight: 700;");
                    else setStyle("-fx-text-fill: #16a34a; -fx-font-weight: 700;");
                }
            }
        });

        TableColumn<BatchDetectionItem, String> lossCol = new TableColumn<>("Est. Power Loss");
        lossCol.setPrefWidth(125);
        lossCol.setCellValueFactory(new PropertyValueFactory<>("powerLoss"));

        TableColumn<BatchDetectionItem, String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(110);
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        TableColumn<BatchDetectionItem, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(155);
        actionCol.setCellFactory(col -> new TableCell<BatchDetectionItem, Void>() {
            private final Button inspectBtn = new Button("🔍 Inspect");
            private final Button saveBtn = new Button("💾 Save");
            private final HBox box = new HBox(6, inspectBtn, saveBtn);
            {
                inspectBtn.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0284c7; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px;");
                saveBtn.setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px;");

                inspectBtn.setOnAction(e -> {
                    BatchDetectionItem item = getTableView().getItems().get(getIndex());
                    handleInspectBatchItem(item);
                });

                saveBtn.setOnAction(e -> {
                    BatchDetectionItem item = getTableView().getItems().get(getIndex());
                    handleSaveBatchItem(item, saveBtn);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    BatchDetectionItem bItem = getTableView().getItems().get(getIndex());
                    inspectBtn.setDisable(bItem.getDetectionResult() == null);
                    saveBtn.setDisable(bItem.getDetectionResult() == null || bItem.isSaved());
                    if (bItem.isSaved()) {
                        saveBtn.setText("✔ Saved");
                    } else {
                        saveBtn.setText("💾 Save");
                    }
                    setGraphic(box);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        batchTable.getColumns().addAll(thumbCol, fileCol, missionCol, panelCol, faultCol, confCol, sevCol, lossCol, statusCol, actionCol);

        // Double click row to inspect
        batchTable.setRowFactory(tv -> {
            TableRow<BatchDetectionItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    handleInspectBatchItem(row.getItem());
                }
            });
            return row;
        });

        batchBox.getChildren().addAll(controlsCard, summaryRow, filterBar, batchTable);
        return batchBox;
    }

    private VBox createStatCard(String title, Label valueLabel, String colorHex) {
        VBox card = new VBox(4);
        card.getStyleClass().add("metric-chip");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setAlignment(Pos.CENTER);

        Label t = new Label(title);
        t.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #64748b;");

        valueLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: " + colorHex + ";");

        card.getChildren().addAll(t, valueLabel);
        return card;
    }

    // =========================================================================
    // 3. SINGLE INSPECTION ACTION HANDLERS & ZOOM MODAL
    // =========================================================================
    public void selectPanel(SolarPanel panel) {
        if (panel == null) return;
        refreshPanelList();
        for (SolarPanel p : panelComboBox.getItems()) {
            if (p.getPanelId().equalsIgnoreCase(panel.getPanelId())) {
                panelComboBox.setValue(p);
                batchPanelCombo.setValue(p);
                break;
            }
        }
    }

    private void refreshPanelList() {
        List<SolarPanel> panels = panelRepository.findAll();
        panelComboBox.getItems().setAll(panels);
        batchPanelCombo.getItems().setAll(panels);
        if (!panels.isEmpty()) {
            if (panelComboBox.getValue() == null) panelComboBox.setValue(panels.get(0));
            if (batchPanelCombo.getValue() == null) batchPanelCombo.setValue(panels.get(0));
        }
    }

    private void handleUploadImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Solar Panel Image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png", "*.bmp")
        );
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            loadImageFile(file);
        }
    }

    private void handleSelectDemoSample() {
        File samplesDir = new File("sample_images");
        if (!samplesDir.exists() || samplesDir.listFiles() == null) {
            com.solarshield.util.SampleImageGenerator.generateAllSamples();
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Synthetic Defect Test Image");
        chooser.setInitialDirectory(samplesDir);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Sample Images", "*.jpg", "*.png")
        );
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            loadImageFile(file);
        }
    }

    private void handleApplyThermalPalette() {
        if (currentRawImage == null && selectedImageFile == null && capturedMat == null) return;
        ThermalColormap selectedPalette = thermalPaletteCombo != null ? thermalPaletteCombo.getValue() : ThermalColormap.NATURAL;

        if (selectedPalette == null || selectedPalette == ThermalColormap.NATURAL) {
            if (selectedImageFile != null) {
                currentRawImage = new Image(selectedImageFile.toURI().toString());
            } else if (capturedMat != null) {
                currentRawImage = ImageUtils.matToFxImage(capturedMat);
            }
        } else {
            Mat baseMat = null;
            if (selectedImageFile != null) {
                baseMat = org.opencv.imgcodecs.Imgcodecs.imread(selectedImageFile.getAbsolutePath());
            } else if (capturedMat != null) {
                baseMat = capturedMat.clone();
            }
            if (baseMat != null && !baseMat.empty()) {
                Mat thermalMat = detectionService.applyThermalFilter(baseMat, selectedPalette);
                currentRawImage = ImageUtils.matToFxImage(thermalMat);
                baseMat.release();
                thermalMat.release();
            }
        }

        originalImageView.setImage(currentRawImage);
        comparisonSliderView.setImages(currentRawImage, currentAnalyzedImage);
    }

    private void loadImageFile(File file) {
        this.selectedImageFile = file;
        this.capturedMat = null;
        this.currentRawImage = new Image(file.toURI().toString());
        this.currentAnalyzedImage = null;

        if (thermalPaletteCombo != null && thermalPaletteCombo.getValue() != ThermalColormap.NATURAL) {
            handleApplyThermalPalette();
        } else {
            originalImageView.setImage(currentRawImage);
            comparisonSliderView.setImages(currentRawImage, null);
        }

        analyzedImageView.setImage(null);
        openZoomInspectorBtn.setDisable(false);

        resultCard.setVisible(false);
        resultCard.setManaged(false);
    }

    private void handleOpenZoomInspector() {
        if (currentRawImage == null && currentAnalyzedImage == null) return;

        FaultType ft = currentResult != null ? currentResult.getPrediction().getFaultType() : null;
        Severity sev = currentResult != null ? currentResult.getSeverity() : null;
        Double conf = currentResult != null ? currentResult.getPrediction().getConfidence() : null;
        BoundingBox bbox = currentResult != null ? currentResult.getPrediction().getBoundingBox() : null;

        String title = "SolarShield High-Res Inspector";
        if (panelComboBox.getValue() != null) {
            title += " - Panel " + panelComboBox.getValue().getPanelId();
        }

        ImageZoomDialog dialog = new ImageZoomDialog(
                getScene().getWindow(),
                title,
                currentRawImage,
                currentAnalyzedImage,
                ft, sev, conf, bbox
        );
        dialog.show();
    }

    private void handleWebcamCapture() {
        if (!OpenCVLoader.isAvailable()) {
            AlertUtils.showWarning("Camera Unavailable", "OpenCV Library Required",
                    "OpenCV camera drivers are not active on this environment. Please upload an image instead.");
            return;
        }

        Stage camStage = new Stage();
        camStage.initModality(Modality.APPLICATION_MODAL);
        camStage.setTitle("Webcam Solar Inspection Capture");

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-background-color: #0f172a;");

        ImageView camView = new ImageView();
        camView.setFitWidth(500);
        camView.setFitHeight(360);
        camView.setPreserveRatio(true);

        Label statusLbl = new Label("Initializing camera device...");
        statusLbl.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: 600;");

        HBox btnBox = new HBox(12);
        btnBox.setAlignment(Pos.CENTER);

        Button snapBtn = new Button("📸 Freeze Frame / Capture");
        snapBtn.getStyleClass().add("btn-primary");

        Button useBtn = new Button("✔ Use Captured Image");
        useBtn.getStyleClass().add("btn-success");
        useBtn.setDisable(true);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-outline");

        btnBox.getChildren().addAll(snapBtn, useBtn, cancelBtn);
        content.getChildren().addAll(statusLbl, camView, btnBox);

        Scene scene = new Scene(content, 540, 480);
        camStage.setScene(scene);

        final VideoCapture[] captureHolder = new VideoCapture[1];
        final Mat[] currentFrame = new Mat[1];
        final boolean[] isRunning = {true};
        final boolean[] isFrozen = {false};

        Thread camThread = new Thread(() -> {
            try {
                VideoCapture cap = new VideoCapture(0);
                captureHolder[0] = cap;

                if (!cap.isOpened()) {
                    Platform.runLater(() -> {
                        statusLbl.setText("Camera device unavailable. Please upload an image instead.");
                        statusLbl.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: 700;");
                        snapBtn.setDisable(true);
                    });
                    return;
                }

                Platform.runLater(() -> statusLbl.setText("Live Camera Stream Ready"));

                Mat frame = new Mat();
                while (isRunning[0]) {
                    if (!isFrozen[0] && cap.read(frame) && !frame.empty()) {
                        currentFrame[0] = frame.clone();
                        javafx.scene.image.Image fxImg = ImageUtils.matToFxImage(frame);
                        Platform.runLater(() -> camView.setImage(fxImg));
                    }
                    try { Thread.sleep(33); } catch (InterruptedException ignored) {}
                }
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    statusLbl.setText("Camera error: " + t.getMessage() + ". Please upload an image.");
                    statusLbl.setStyle("-fx-text-fill: #ef4444;");
                });
            }
        });
        camThread.setDaemon(true);
        camThread.start();

        snapBtn.setOnAction(e -> {
            isFrozen[0] = !isFrozen[0];
            if (isFrozen[0]) {
                snapBtn.setText("🔄 Unfreeze / Retry");
                useBtn.setDisable(false);
            } else {
                snapBtn.setText("📸 Freeze Frame / Capture");
                useBtn.setDisable(true);
            }
        });

        useBtn.setOnAction(e -> {
            if (currentFrame[0] != null && !currentFrame[0].empty()) {
                this.capturedMat = currentFrame[0].clone();
                this.selectedImageFile = null;
                this.currentRawImage = ImageUtils.matToFxImage(this.capturedMat);
                this.currentAnalyzedImage = null;

                originalImageView.setImage(this.currentRawImage);
                analyzedImageView.setImage(null);
                comparisonSliderView.setImages(this.currentRawImage, null);
                openZoomInspectorBtn.setDisable(false);

                resultCard.setVisible(false);
                resultCard.setManaged(false);
                camStage.close();
            }
        });

        cancelBtn.setOnAction(e -> camStage.close());

        camStage.setOnCloseRequest(e -> {
            isRunning[0] = false;
            if (captureHolder[0] != null && captureHolder[0].isOpened()) {
                captureHolder[0].release();
            }
        });

        camStage.showAndWait();
        isRunning[0] = false;
        if (captureHolder[0] != null && captureHolder[0].isOpened()) {
            captureHolder[0].release();
        }
    }

    private void handleRunDetection() {
        SolarPanel panel = panelComboBox.getValue();
        if (panel == null) {
            AlertUtils.showWarning("Missing Selection", "Select Solar Panel", "Please select a solar panel from the dropdown.");
            return;
        }

        if (selectedImageFile == null && (capturedMat == null || capturedMat.empty())) {
            AlertUtils.showWarning("Missing Image", "No Image Provided", "Please upload a solar panel image or capture from camera.");
            return;
        }

        SensorData sensorData = collectSensorData();

        analyzeBtn.setDisable(true);
        analyzeBtn.setText("Processing Pipeline...");

        new Thread(() -> {
            FaultDetectionService.DetectionResult res;
            if (selectedImageFile != null) {
                res = detectionService.analyze(panel, selectedImageFile, sensorData);
            } else {
                res = detectionService.analyzeMatDirect(panel, capturedMat, sensorData);
            }

            Platform.runLater(() -> {
                analyzeBtn.setDisable(false);
                analyzeBtn.setText("⚡ ANALYZE IMAGE WITH AI");

                if (!res.isSuccess()) {
                    AlertUtils.showError("Analysis Failure", "Detection Pipeline Error", res.getErrorMessage());
                    return;
                }

                displayResults(res);
            });
        }).start();
    }

    private void displayResults(FaultDetectionService.DetectionResult res) {
        this.currentResult = res;
        this.savedReport = null;

        // Render Analyzed Image
        Mat analyzedMat = res.getAnalyzedMat();
        if (analyzedMat != null && !analyzedMat.empty()) {
            this.currentAnalyzedImage = ImageUtils.matToFxImage(analyzedMat);
            analyzedImageView.setImage(this.currentAnalyzedImage);
            comparisonSliderView.setImages(this.currentRawImage, this.currentAnalyzedImage);
            openZoomInspectorBtn.setDisable(false);
        }

        FaultType ft = res.getPrediction().getFaultType();
        boolean isFaulty = ft.isFault();

        if (isFaulty) {
            resultBanner.setText("⚠ FAULT DETECTED: " + ft.getDisplayName().toUpperCase());
            resultBanner.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-font-size: 16px; -fx-font-weight: 900; -fx-padding: 6 16 6 16; -fx-background-radius: 6px;");
        } else {
            resultBanner.setText("✔ NOMINAL OPERATION - HEALTHY MODULE");
            resultBanner.setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-font-size: 16px; -fx-font-weight: 900; -fx-padding: 6 16 6 16; -fx-background-radius: 6px;");
        }

        engineBadge.setText(res.getPrediction().getModelName() + " (" + res.getPrediction().getModelType() + ")");

        faultTypeLbl.setText(ft.getDisplayName());
        confidenceLbl.setText(String.format("%.1f%%", res.getPrediction().getConfidence()));

        Severity sev = res.getSeverity();
        severityLbl.setText(sev.name());
        severityLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: " + sev.getColorHex() + ";");

        MaintenancePriority pri = res.getMaintenancePriority();
        priorityLbl.setText(pri.name());
        priorityLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: " + pri.getColorHex() + ";");

        EnergyLossService.EnergyLossEstimate loss = res.getEnergyLossEstimate();
        energyLossLbl.setText(String.format("%.1f W (%.1f%% loss)", loss.getLossWatts(), loss.getLossPercent()));

        regionLbl.setText(res.getLocalizationResult().getRegionDescription());
        recommendationLbl.setText(res.getActionableRecommendation());

        saveResultBtn.setDisable(false);
        saveResultBtn.setText("💾 Save Diagnostic Result to Database");

        resultCard.setVisible(true);
        resultCard.setManaged(true);
    }

    private void handleSaveResult() {
        if (currentResult == null) return;

        FaultReport report = detectionService.saveResult(currentResult);
        if (report != null) {
            this.savedReport = report;
            saveResultBtn.setText("✔ Result Saved to Database (Report #" + report.getId() + ")");
            saveResultBtn.setDisable(true);
            AlertUtils.showInfo("Saved Successfully", "Report #" + report.getId() + " Logged",
                    "Diagnostic inspection recorded in database and panel status updated.");
        } else {
            AlertUtils.showError("Database Error", "Failed to Save", "Unable to persist fault report to database.");
        }
    }

    private void handleClear() {
        selectedImageFile = null;
        capturedMat = null;
        currentResult = null;
        savedReport = null;
        currentRawImage = null;
        currentAnalyzedImage = null;

        originalImageView.setImage(null);
        analyzedImageView.setImage(null);
        comparisonSliderView.clear();
        openZoomInspectorBtn.setDisable(true);

        tempField.clear();
        voltField.clear();
        currField.clear();
        irrField.clear();
        humField.clear();
        if (thermalPaletteCombo != null) {
            thermalPaletteCombo.setValue(ThermalColormap.NATURAL);
        }

        resultCard.setVisible(false);
        resultCard.setManaged(false);
    }

    private SensorData collectSensorData() {
        Double t = parseOpt(tempField.getText());
        Double v = parseOpt(voltField.getText());
        Double c = parseOpt(currField.getText());
        Double irr = parseOpt(irrField.getText());
        Double hum = parseOpt(humField.getText());

        if (t == null && v == null && c == null && irr == null && hum == null) {
            return null;
        }
        return new SensorData(t, v, c, irr, hum);
    }

    private Double parseOpt(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // =========================================================================
    // 4. BATCH MULTI-IMAGE PROCESSING HANDLERS
    // =========================================================================
    private void handleAddBatchFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Solar Panel Images for Batch Processing");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png", "*.bmp")
        );
        List<File> files = chooser.showOpenMultipleDialog(getScene().getWindow());
        if (files != null && !files.isEmpty()) {
            addFilesToBatch(files);
        }
    }

    private void handleLoadAllDemoSamples() {
        File samplesDir = new File("sample_images");
        if (!samplesDir.exists() || samplesDir.listFiles() == null) {
            com.solarshield.util.SampleImageGenerator.generateAllSamples();
        }

        File[] files = samplesDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".png");
        });

        if (files != null && files.length > 0) {
            addFilesToBatch(List.of(files));
        }
    }

    private void handleScanDroneFolder() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Aerial Drone Survey / Inspection Folder");

        File defaultDir = new File("sample_images");
        if (defaultDir.exists() && defaultDir.isDirectory()) {
            dirChooser.setInitialDirectory(defaultDir);
        }

        File selectedDir = dirChooser.showDialog(getScene().getWindow());
        if (selectedDir == null) return;

        List<File> imageFiles = scanDirectoryForImages(selectedDir);
        if (imageFiles.isEmpty()) {
            AlertUtils.showWarning("No Images Found", "No Supported Images in Folder",
                    "No supported aerial images (.jpg, .jpeg, .png, .bmp, .webp, .tiff) were found in:\n" + selectedDir.getAbsolutePath());
            return;
        }

        if (missionNameField != null && (missionNameField.getText() == null || missionNameField.getText().trim().isEmpty() || missionNameField.getText().startsWith("Drone-Survey"))) {
            missionNameField.setText("Drone-" + selectedDir.getName());
        }

        addFilesToBatch(imageFiles);
        AlertUtils.showInfo("Folder Ingested", "Drone Survey Images Loaded",
                String.format("Successfully loaded %d aerial images from folder:\n%s", imageFiles.size(), selectedDir.getName()));
    }

    private List<File> scanDirectoryForImages(File dir) {
        List<File> list = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return list;

        File[] files = dir.listFiles();
        if (files == null) return list;

        for (File f : files) {
            if (f.isFile()) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                    name.endsWith(".bmp") || name.endsWith(".webp") || name.endsWith(".tif") || name.endsWith(".tiff")) {
                    list.add(f);
                }
            }
        }
        list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return list;
    }

    private void addFilesToBatch(List<File> files) {
        SolarPanel defaultPanel = batchPanelCombo.getValue();
        List<SolarPanel> allPanels = panelRepository.findAll();
        String assignmentStrategy = assignmentModeCombo != null && assignmentModeCombo.getValue() != null ?
                assignmentModeCombo.getValue() : "Auto-Distribute Across Registered Farm Panels";
        String missionTag = missionNameField != null && !missionNameField.getText().trim().isEmpty() ?
                missionNameField.getText().trim() : "Drone-Survey";

        int panelIdx = 0;
        for (File f : files) {
            SolarPanel assigned = defaultPanel;

            if ("Smart Match by Filename (e.g. SP-001)".equals(assignmentStrategy)) {
                String fNameLower = f.getName().toLowerCase();
                for (SolarPanel p : allPanels) {
                    if (fNameLower.contains(p.getPanelId().toLowerCase()) ||
                        fNameLower.contains("panel" + p.getId()) ||
                        fNameLower.contains("panel_" + p.getId())) {
                        assigned = p;
                        break;
                    }
                }
            } else if ("Auto-Distribute Across Registered Farm Panels".equals(assignmentStrategy) && !allPanels.isEmpty()) {
                assigned = allPanels.get(panelIdx % allPanels.size());
                panelIdx++;
            }

            if (assigned == null && !allPanels.isEmpty()) {
                assigned = allPanels.get(0);
            }

            batchItems.add(new BatchDetectionItem(f, assigned, missionTag));
        }
        updateBatchSummary();
        batchProgressLabel.setText(batchItems.size() + " images queued");
    }

    private void handleClearBatch() {
        if (activeBatchTask != null && activeBatchTask.isRunning()) {
            activeBatchTask.cancel();
        }
        batchItems.clear();
        batchProgressBar.setProgress(0.0);
        batchProgressLabel.setText("Ready to analyze");
        saveAllBatchBtn.setDisable(true);
        exportBatchCsvBtn.setDisable(true);
        if (exportBatchPdfBtn != null) {
            exportBatchPdfBtn.setDisable(true);
        }
        updateBatchSummary();
    }

    private void handleStartBatch() {
        if (batchItems.isEmpty()) {
            AlertUtils.showWarning("Empty Batch", "No Images to Process", "Please select images before starting batch analysis.");
            return;
        }

        SolarPanel fallbackPanel = batchPanelCombo.getValue();
        if (fallbackPanel == null) {
            List<SolarPanel> panels = panelRepository.findAll();
            if (!panels.isEmpty()) fallbackPanel = panels.get(0);
        }

        final SolarPanel defaultPanel = fallbackPanel;
        if (defaultPanel == null) {
            AlertUtils.showWarning("No Solar Panel", "Solar Panel Required", "Please create or select a solar panel first.");
            return;
        }

        runBatchBtn.setDisable(true);
        cancelBatchBtn.setDisable(false);
        saveAllBatchBtn.setDisable(true);
        exportBatchCsvBtn.setDisable(true);
        if (exportBatchPdfBtn != null) {
            exportBatchPdfBtn.setDisable(true);
        }

        activeBatchTask = new Task<Void>() {
            @Override
            protected Void call() {
                int total = batchItems.size();
                long startTime = System.currentTimeMillis();

                for (int i = 0; i < total; i++) {
                    if (isCancelled()) break;

                    final int index = i;
                    final BatchDetectionItem item = batchItems.get(i);
                    final long itemStartTime = System.currentTimeMillis();

                    Platform.runLater(() -> {
                        item.setStatus("Analyzing...");
                        long elapsed = System.currentTimeMillis() - startTime;
                        double avgPerItem = index > 0 ? (double) elapsed / index : 1200.0;
                        long estRemaining = (long) (avgPerItem * (total - index));
                        batchProgressLabel.setText(String.format("Processing %d of %d: %s | Elapsed: %.1fs | ETA: %.1fs",
                                (index + 1), total, item.getFileName(), elapsed / 1000.0, Math.max(0, estRemaining / 1000.0)));
                        batchProgressBar.setProgress((double) (index) / total);
                    });

                    SolarPanel panelToUse = item.getPanel() != null ? item.getPanel() : defaultPanel;
                    FaultDetectionService.DetectionResult res = detectionService.analyze(panelToUse, item.getImageFile(), null);
                    final long itemDuration = System.currentTimeMillis() - itemStartTime;

                    Platform.runLater(() -> {
                        item.setPanel(panelToUse);
                        item.setProcessingDurationMs(itemDuration);
                        item.setDetectionResult(res);
                        batchProgressBar.setProgress((double) (index + 1) / total);
                        updateBatchSummary();
                        batchTable.refresh();
                    });

                    try { Thread.sleep(50); } catch (InterruptedException e) {
                        if (isCancelled()) break;
                    }
                }
                return null;
            }

            @Override
            protected void succeeded() {
                finishBatch("Batch analysis completed successfully!");
            }

            @Override
            protected void cancelled() {
                finishBatch("Batch analysis stopped by user.");
            }

            @Override
            protected void failed() {
                finishBatch("Batch analysis encountered an error: " + (getException() != null ? getException().getMessage() : "Unknown"));
            }
        };

        Thread th = new Thread(activeBatchTask);
        th.setDaemon(true);
        th.start();
    }

    private void finishBatch(String statusMsg) {
        runBatchBtn.setDisable(false);
        cancelBatchBtn.setDisable(true);
        saveAllBatchBtn.setDisable(batchItems.isEmpty());
        exportBatchCsvBtn.setDisable(batchItems.isEmpty());
        if (exportBatchPdfBtn != null) {
            exportBatchPdfBtn.setDisable(batchItems.isEmpty());
        }
        batchProgressLabel.setText(statusMsg);
        updateBatchSummary();
    }

    private void handleCancelBatch() {
        if (activeBatchTask != null && activeBatchTask.isRunning()) {
            activeBatchTask.cancel();
        }
    }

    private void updateBatchSummary() {
        int total = batchItems.size();
        int healthy = 0;
        int faulty = 0;
        int critical = 0;
        double totalLoss = 0.0;

        for (BatchDetectionItem item : batchItems) {
            FaultDetectionService.DetectionResult res = item.getDetectionResult();
            if (res != null && res.isSuccess()) {
                if (res.getPrediction().getFaultType().isFault()) {
                    faulty++;
                } else {
                    healthy++;
                }

                if (res.getSeverity() == Severity.CRITICAL || res.getSeverity() == Severity.HIGH) {
                    critical++;
                }

                if (res.getEnergyLossEstimate() != null) {
                    totalLoss += res.getEnergyLossEstimate().getLossWatts();
                }
            }
        }

        batchTotalLbl.setText(String.valueOf(total));
        batchHealthyLbl.setText(String.valueOf(healthy));
        batchFaultyLbl.setText(String.valueOf(faulty));
        batchCriticalLbl.setText(String.valueOf(critical));
        batchLossLbl.setText(String.format("%.1f W", totalLoss));

        applyBatchFilter();
    }

    private void applyBatchFilter() {
        if (filteredBatchItems == null) return;
        String search = batchSearchField != null && batchSearchField.getText() != null ?
                batchSearchField.getText().trim().toLowerCase() : "";
        String filter = batchFilterCombo != null && batchFilterCombo.getValue() != null ?
                batchFilterCombo.getValue() : "All Records";

        filteredBatchItems.setPredicate(item -> {
            if (item == null) return false;

            boolean matchesSearch = search.isEmpty() ||
                    (item.getFileName() != null && item.getFileName().toLowerCase().contains(search)) ||
                    (item.getPanelId() != null && item.getPanelId().toLowerCase().contains(search)) ||
                    (item.getFaultType() != null && item.getFaultType().toLowerCase().contains(search)) ||
                    (item.getMissionTag() != null && item.getMissionTag().toLowerCase().contains(search));
            if (!matchesSearch) return false;

            if ("Faults Only".equals(filter)) {
                return item.isFault();
            } else if ("Healthy Only".equals(filter)) {
                return "Healthy".equalsIgnoreCase(item.getFaultType());
            } else if ("Critical & High Severity".equals(filter)) {
                return item.isCriticalOrHigh();
            }
            return true;
        });

        if (batchCountSummaryLbl != null) {
            batchCountSummaryLbl.setText(String.format("Showing %d of %d images", filteredBatchItems.size(), batchItems.size()));
        }
    }

    private void handleInspectBatchItem(BatchDetectionItem item) {
        if (item == null || item.getDetectionResult() == null) {
            AlertUtils.showInfo("Not Yet Analyzed", "Analysis Pending", "Run batch analysis first to inspect results.");
            return;
        }

        FaultDetectionService.DetectionResult res = item.getDetectionResult();
        Image rawImg = new Image(item.getImageFile().toURI().toString());
        Image analyzedImg = null;
        if (res.getAnalyzedMat() != null && !res.getAnalyzedMat().empty()) {
            analyzedImg = ImageUtils.matToFxImage(res.getAnalyzedMat());
        }

        ImageZoomDialog dialog = new ImageZoomDialog(
                getScene().getWindow(),
                "SolarShield Batch Inspector - " + item.getFileName() + " (" + item.getMissionTag() + ")",
                rawImg,
                analyzedImg,
                res.getPrediction().getFaultType(),
                res.getSeverity(),
                res.getPrediction().getConfidence(),
                res.getPrediction().getBoundingBox()
        );
        dialog.show();
    }

    private void handleSaveBatchItem(BatchDetectionItem item, Button btn) {
        if (item == null || item.getDetectionResult() == null) return;
        FaultReport report = detectionService.saveResult(item.getDetectionResult());
        if (report != null) {
            item.setSavedReport(report);
            item.setSaved(true);
            btn.setText("✔ Saved");
            btn.setDisable(true);
            batchTable.refresh();
        }
    }

    private void handleSaveAllBatch() {
        int savedCount = 0;
        for (BatchDetectionItem item : batchItems) {
            if (item.getDetectionResult() != null && item.getDetectionResult().isSuccess() && !item.isSaved()) {
                FaultReport report = detectionService.saveResult(item.getDetectionResult());
                if (report != null) {
                    item.setSavedReport(report);
                    item.setSaved(true);
                    savedCount++;
                }
            }
        }
        batchTable.refresh();
        AlertUtils.showInfo("Batch Saved", "Database Persistence Complete",
                String.format("Successfully saved %d diagnostic reports into the database.", savedCount));
    }

    private void handleExportBatchPdf() {
        if (batchItems.isEmpty()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Drone Aerial Survey PDF Audit Report");
        String mission = missionNameField != null ? missionNameField.getText().trim().replaceAll("[^a-zA-Z0-9_-]", "_") : "drone_survey";
        chooser.setInitialFileName("solarshield_drone_batch_audit_" + mission + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"));

        File target = chooser.showSaveDialog(getScene().getWindow());
        if (target == null) return;

        String title = missionNameField != null ? missionNameField.getText().trim() : "Aerial Drone Fleet Survey";
        boolean ok = reportService.exportBatchInspectionPdf(batchItems, title, target);
        if (ok) {
            AlertUtils.showInfo("PDF Exported", "Drone Audit PDF Created",
                    "Batch aerial survey audit report successfully exported to:\n" + target.getAbsolutePath());
        } else {
            AlertUtils.showError("Export Error", "Failed to Export PDF", "Unable to generate the PDF audit report.");
        }
    }

    private void handleExportBatchCsv() {
        if (batchItems.isEmpty()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Batch Farm Inspection CSV");
        chooser.setInitialFileName("solarshield_batch_inspection_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File target = chooser.showSaveDialog(getScene().getWindow());
        if (target == null) return;

        try (FileWriter writer = new FileWriter(target)) {
            writer.write("Image_File,Mission_Tag,Panel_ID,Condition,Confidence_Percent,Severity,Power_Loss_Watts,Power_Loss_Percent,Priority,Processing_Time_ms,Status\n");
            for (BatchDetectionItem item : batchItems) {
                FaultDetectionService.DetectionResult res = item.getDetectionResult();
                String conf = res != null ? String.format("%.2f", res.getPrediction().getConfidence()) : "0";
                String lossW = res != null ? String.format("%.2f", res.getEnergyLossEstimate().getLossWatts()) : "0";
                String lossP = res != null ? String.format("%.2f", res.getEnergyLossEstimate().getLossPercent()) : "0";
                String pri = res != null ? res.getMaintenancePriority().name() : "N/A";

                writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\",%s,%s,\"%s\",%d,\"%s\"\n",
                        item.getFileName(),
                        item.getMissionTag(),
                        item.getPanelId(),
                        item.getFaultType(),
                        conf,
                        item.getSeverity(),
                        lossW,
                        lossP,
                        pri,
                        item.getProcessingDurationMs(),
                        item.getStatus()));
            }
            AlertUtils.showInfo("CSV Exported", "Batch CSV Created",
                    "Batch inspection summary successfully exported to:\n" + target.getAbsolutePath());
        } catch (IOException ex) {
            AlertUtils.showError("Export Error", "Failed to Export CSV", ex.getMessage());
        }
    }
}
