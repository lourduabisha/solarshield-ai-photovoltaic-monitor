# SOLARSHIELD: AI-BASED SOLAR PANEL FAULT DETECTION & PREDICTIVE MONITORING

> **Academic Java Object-Oriented Programming (OOP) Capstone Project**  
> An enterprise-grade desktop application for automated photovoltaic (PV) fault detection, spatial anomaly localization, severity classification, energy loss derating estimation, maintenance tracking, and audit reporting.

---

## 1. Project Objective & Overview

Solar photovoltaic infrastructure experiences environmental degradation, micro-structural defects, localized thermal hotspots, and physical surface fouling. Without prompt detection, these defects reduce yield, accelerate degradation, and present severe fire hazards.

**SolarShield** delivers an automated, AI-assisted desktop monitoring platform developed in **Java 17+ and JavaFX**, integrated with **OpenCV for Java**, **SQLite JDBC**, and **OpenPDF**. The system processes RGB, electroluminescence, and thermal solar panel imagery to:

1. **Detect** whether a solar panel module is **Healthy** or **Faulty**.
2. **Classify** the defect into six operational fault categories:
   - `Healthy` (Nominal operation)
   - `Hotspot` (Localized thermal dissipation anomaly)
   - `Crack` (Silicon wafer micro-fractures)
   - `Soiling / Dirt` (Surface dust, pollen, bird dropping attenuation)
   - `Partial Shading` (Dynamic or stationary shadow obstructions)
   - `Physical Damage` (Glass shatter, impact trauma, delamination)
3. **Calculate Confidence Scores** for detected features.
4. **Localize Defects** using OpenCV contour and bounding box rendering with high-visibility HUD diagnostic reticles.
5. **Estimate Risk Severity** (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) using multi-factor defect physics and sensor data.
6. **Assign Maintenance Urgency Priority** (`LOW`, `MEDIUM`, `HIGH`, `URGENT`) with actionable field guidance.
7. **Compute Estimated Energy Loss** (in Watts and yield percentage derating).
8. **Raise Automated Warnings & Alerts** for high-risk and critical incidents.
9. **Log Diagnostics** into an ACID-compliant local SQLite database.
10. **Analyze Trends & Historical Faults** through interactive JavaFX charts and filtering.
11. **Track Service Work Orders** from initial dispatch to completion.
12. **Generate Formal Audit Reports** exported to professional **PDF** and **CSV** spreadsheets.

---

## 2. Technology Stack

- **Core Platform:** Java 17 LTS (Compatible with Java 17 through Java 26)
- **Desktop GUI:** JavaFX 21 (Controls, Graphics, FXML, Swing bridge)
- **Computer Vision:** OpenCV for Java (via `org.openpnp:opencv` with bundled self-extracting native binaries for Windows x64, macOS, and Linux)
- **Database:** SQLite 3 via JDBC (`org.xerial:sqlite-jdbc`)
- **Security & Cryptography:** BCrypt password hashing (`org.mindrot:jbcrypt`)
- **Reporting Engine:** OpenPDF (`com.github.librepdf:openpdf`) for PDF generation and standard Java IO for CSV
- **Testing:** JUnit 5 Jupiter for unit and end-to-end integration workflows
- **Build System:** Apache Maven 3.9+ with `javafx-maven-plugin` and `exec-maven-plugin`

---

## 3. Object-Oriented Architecture (OOP Principles)

SolarShield strictly adheres to clean layered OOP principles:

```
┌────────────────────────────────────────────────────────┐
│                      JavaFX UI                         │
│  (LoginView, RegisterView, MainView, DashboardView,   │
│   DetectionView, PanelView, HistoryView, ReportView)   │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                    Service Layer                       │
│  (AuthenticationService, FaultDetectionService,        │
│   ImagePreprocessingService, LocalizationService,     │
│   SeverityService, EnergyLossService, AlertService,    │
│   MaintenanceService, ReportService, DashboardService) │
└─────────────┬────────────────────────────┬─────────────┘
              │                            │
┌─────────────▼─────────────┐ ┌────────────▼─────────────┐
│    Polymorphic AI Layer   │ │    Database Repository   │
│  FaultDetectionModel (intf)│ │  (UserRepository,        │
│   ├── OpenCVFaultDetector │ │   SolarPanelRepository,  │
│   └── MockAIModel (demo)  │ │   FaultReportRepository, │
│                             │   MaintenanceRepository, │
│                             │   AlertRepository)       │
└───────────────────────────┘ └────────────┬─────────────┘
                                           │
                              ┌────────────▼─────────────┐
                              │     SQLite Database      │
                              │     (solarshield.db)     │
                              └──────────────────────────┘
```

### Demonstration of Core OOP Concepts:
- **Encapsulation:**
  - All domain model fields (`User`, `SolarPanel`, `FaultReport`, `Maintenance`, `AlertItem`, `SensorData`, `BoundingBox`) are `private`.
  - Defensive copying, invariant validation in setters, and formatted helper methods.
- **Abstraction:**
  - `FaultDetectionModel` interface defines `ModelPrediction predict(Mat image, SensorData sensorData)`.
  - UI components interact only with high-level services, completely decoupled from SQL queries or computer vision implementation details.
- **Inheritance:**
  - JavaFX component hierarchy (`StackPane`, `VBox`, `ScrollPane`, `BorderPane`).
- **Polymorphism:**
  - Both `OpenCVFaultDetector` and `MockAIModel` implement `FaultDetectionModel`.
  - `AIInferenceService` switches between OpenCV computer vision feature extraction and offline demo fallback dynamically without altering the detection service pipeline.
- **Composition:**
  - `MainView` composes the sidebar, header, and active submodule views.
  - `FaultDetectionService` composes image preprocessing, AI inference, localization, severity assessment, and database storage.

---

## 4. How the AI Detection & Computer Vision Pipeline Works

The detection pipeline consists of 7 modular phases:

```
  [1. Input Image] ───► [2. Preprocessing] ───► [3. CV / AI Inference]
  (Upload or Camera)     (Resize, Filter, Blur)   (OpenCV Color/Edge Analysis)
                                                         │
  [6. HUD Overlay] ◄─── [5. Energy & Priority] ◄─────────┴───► [4. Classification]
  (Reticles & Bounding)   (Loss Watts & Priority)               (Fault Type & Conf.)
         │
  [7. Persistence & Alert] ───► SQLite DB + Modal Warning + Work Order
```

1. **Format Validation & Standardization:**
   Validates image headers (`JPG`, `PNG`, `BMP`, `WEBP`) and scales dimensions to a standardized inspection resolution (max 800px) while maintaining aspect ratio.
2. **Noise Suppression & Filtering:**
   Applies Gaussian spatial filtering to eliminate sensor grain and thermal camera pixel noise while preserving sharp crack boundaries.
3. **Contrast & Luminance Normalization:**
   Converts to YCrCb space, equalizes luminance histogram (Y), and blends with the source to optimize feature salience under varying daylight conditions.
4. **OpenCV Feature Extraction (`OpenCVFaultDetector`):**
   - **Hotspot Detection:** HSV color-space thresholding detects localized radiant red/yellow chromatic signatures and measures contour areas.
   - **Crack Detection:** Canny edge detector filtered against regular rectilinear grid busbars identifies micro-fracture spidering.
   - **Soiling / Dirt Detection:** Grayscale texture entropy and spatial variance analysis identify diffuse particulate deposition.
   - **Partial Shading Detection:** Luminance disparity masking identifies contiguous shadow blocks across module cell strings.
   - **Physical Damage Detection:** High-density fracture clustering identifies shattered front glass and dented frames.
5. **Telemetry Fusion:**
   Optional in situ sensor readings (temperature, voltage, current, irradiance, humidity) are fused with computer vision metrics to elevate classification accuracy.
6. **Localization & HUD Reticles (`LocalizationService`):**
   Renders diagnostic overlays with semi-transparent tinted defect masks, high-visibility borders, corner reticles, and diagnostic tags onto the output matrix.
7. **Severity & Energy Loss Modeling (`SeverityService`, `EnergyLossService`):**
   Computes power derating in Watts (`ratedPower * lossPercent / 100`) and assigns maintenance priorities (`LOW`, `MEDIUM`, `HIGH`, `URGENT`).

---

## 5. Important Realism & Academic Honesty Notice

> **Academic Honesty Notice:**
> - The application clearly labels the fallback mode as **"DEMO AI / FALLBACK MODEL"**.
> - The computer vision engine uses **OpenCV feature extraction** (color-space thresholding, Canny edge detection, contour geometry, and texture variance).
> - It does **not** claim to be a 100% accurate deep neural network.
> - The system interface (`FaultDetectionModel`) is designed so that a real trained deep-learning model (such as an **ONNX** or **TensorFlow** ResNet/YOLO model) can be plugged in without changing any controller or UI code.
> - Energy loss and spatial localization values are clearly designated as **engineering estimations**.

---

## 6. Project Directory Structure

```
solarshield/
├── pom.xml                                    # Maven configuration & dependencies
├── README.md                                  # Complete project documentation
├── solarshield.db                             # SQLite database (auto-generated)
├── .vscode/
│   ├── launch.json                            # VS Code F5 Run configuration
│   └── tasks.json                             # VS Code Maven task shortcuts
├── sample_images/                             # Pre-generated synthetic solar test images
│   ├── 1_healthy_panel.jpg
│   ├── 2_hotspot_panel.jpg
│   ├── 3_crack_panel.jpg
│   ├── 4_soiled_panel.jpg
│   ├── 5_shaded_panel.jpg
│   └── 6_damaged_panel.jpg
├── data/
│   └── images/                                # Safe local image storage
├── src/
│   ├── main/
│   │   ├── java/com/solarshield/
│   │   │   ├── Main.java                      # Standalone JVM launcher
│   │   │   ├── MainApp.java                   # JavaFX Application entrypoint
│   │   │   ├── model/                         # Domain Entities & Enums
│   │   │   │   ├── FaultType.java
│   │   │   │   ├── Severity.java
│   │   │   │   ├── PanelStatus.java
│   │   │   │   ├── MaintenancePriority.java
│   │   │   │   ├── MaintenanceStatus.java
│   │   │   │   ├── User.java
│   │   │   │   ├── SolarPanel.java
│   │   │   │   ├── FaultReport.java
│   │   │   │   ├── Maintenance.java
│   │   │   │   ├── AlertItem.java
│   │   │   │   ├── SensorData.java
│   │   │   │   └── BoundingBox.java
│   │   │   ├── ai/                            # AI & Computer Vision Subsystem
│   │   │   │   ├── FaultDetectionModel.java   # Abstraction Interface
│   │   │   │   ├── ModelPrediction.java
│   │   │   │   ├── OpenCVFaultDetector.java   # Real OpenCV Computer Vision Engine
│   │   │   │   ├── MockAIModel.java           # Transparent Demo Fallback
│   │   │   │   └── AIInferenceService.java    # Engine Orchestrator
│   │   │   ├── database/                      # Persistence & JDBC Repositories
│   │   │   │   ├── DatabaseConnection.java
│   │   │   │   ├── DatabaseInitializer.java
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── SolarPanelRepository.java
│   │   │   │   ├── FaultReportRepository.java
│   │   │   │   ├── MaintenanceRepository.java
│   │   │   │   └── AlertRepository.java
│   │   │   ├── service/                       # Business Services Layer
│   │   │   │   ├── AuthenticationService.java
│   │   │   │   ├── FaultDetectionService.java
│   │   │   │   ├── ImagePreprocessingService.java
│   │   │   │   ├── LocalizationService.java
│   │   │   │   ├── SeverityService.java
│   │   │   │   ├── EnergyLossService.java
│   │   │   │   ├── AlertService.java
│   │   │   │   ├── MaintenanceService.java
│   │   │   │   ├── ReportService.java
│   │   │   │   └── DashboardService.java
│   │   │   ├── ui/                            # JavaFX Desktop GUI Components
│   │   │   │   ├── LoginView.java
│   │   │   │   ├── RegisterView.java
│   │   │   │   ├── MainView.java
│   │   │   │   ├── DashboardView.java
│   │   │   │   ├── PanelView.java
│   │   │   │   ├── DetectionView.java
│   │   │   │   ├── FaultHistoryView.java
│   │   │   │   ├── MaintenanceView.java
│   │   │   │   ├── ReportView.java
│   │   │   │   └── SettingsView.java
│   │   │   └── util/                          # Common Utilities
│   │   │       ├── OpenCVLoader.java
│   │   │       ├── ImageUtils.java
│   │   │       ├── ValidationUtils.java
│   │   │       ├── DateUtils.java
│   │   │       ├── AlertUtils.java
│   │   │       ├── SessionManager.java
│   │   │       └── SampleImageGenerator.java
│   │   └── resources/
│   │       └── css/
│   │           └── style.css                  # Modern Solar Theme Stylesheet
│   └── test/
│       └── java/com/solarshield/
│           ├── SolarShieldCoreTest.java       # Unit test suite
│           └── SolarShieldWorkflowTest.java   # Complete integration workflow test
```

---

## 7. Database Design & Schema

The application uses an embedded **SQLite** database (`solarshield.db`) initialized automatically on startup:

### Tables:
1. **`users`**
   - `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
   - `name` (TEXT NOT NULL)
   - `email` (TEXT UNIQUE NOT NULL)
   - `password_hash` (TEXT NOT NULL - BCrypt salted hash)
   - `role` (TEXT DEFAULT 'ENGINEER')
   - `created_at` (TEXT NOT NULL)

2. **`solar_panels`**
   - `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
   - `panel_id` (TEXT UNIQUE NOT NULL, e.g. `SP-001`)
   - `name`, `location`, `installation_date`, `panel_type`, `rated_power`, `latitude`, `longitude`
   - `status` (`HEALTHY`, `WARNING`, `FAULTY`, `CRITICAL`, `MAINTENANCE`)
   - `last_inspection_date`, `current_fault`, `severity`, `maintenance_priority`, `created_at`

3. **`fault_reports`**
   - `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
   - `panel_id` (TEXT NOT NULL)
   - `fault_type` (TEXT NOT NULL)
   - `confidence` (REAL NOT NULL)
   - `severity`, `maintenance_priority`
   - `affected_x`, `affected_y`, `affected_width`, `affected_height`, `affected_region_label`
   - `estimated_energy_loss_watts`, `estimated_energy_loss_percent`
   - `image_path`, `analyzed_image_path`, `analysis_notes`
   - `temperature`, `voltage`, `current`, `irradiance`, `humidity`
   - `created_at` (TEXT NOT NULL)

4. **`maintenance`**
   - `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
   - `panel_id`, `fault_report_id`, `technician`, `maintenance_type`, `scheduled_date`
   - `priority`, `status` (`PENDING`, `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`), `notes`, `created_at`, `completed_at`

5. **`alerts`**
   - `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
   - `panel_id`, `fault_report_id`, `severity`, `message`, `status`, `created_at`

---

## 8. Prerequisites & Installation

### Requirements:
- **Operating System:** Windows 10/11 (64-bit), macOS, or Linux
- **Java:** JDK 17 or newer LTS (tested with Java 17 and Java 26)
- **Maven:** Apache Maven 3.8+ (or bundled Maven in your IDE)
- **Webcam (Optional):** Any standard USB or integrated laptop webcam

### Quick Verification:
```bash
java -version
mvn -version
```

---

## 9. How to Run the Application

### Option A: Run via Maven Command Line (Recommended)
Open a terminal in the project directory and execute:

```bash
mvn javafx:run
```

Or run via the standalone launcher:
```bash
mvn compile exec:java
```

### Option B: Run from VS Code
1. Open the project folder in VS Code (`File -> Open Folder... -> c:\Users\...\solar panel`).
2. Ensure the **Extension Pack for Java** is installed.
3. Open `src/main/java/com/solarshield/Main.java` or `MainApp.java`.
4. Press **`F5`** or click the **▶ Run** button at the top right of the editor.

### Option C: Run Tests
To run all unit tests and integration tests:
```bash
mvn test
```

### Option D: Run Standalone Executable JAR (Zero-Dependency Package)
Build and run the shaded uber-jar containing all runtime dependencies, OpenCV native libraries, SQLite, OpenPDF, and JavaFX:
```bash
mvn package -DskipTests
java --enable-native-access=ALL-UNNAMED -jar target/solarshield-1.0.0.jar
```

---

## 10. Step-by-Step Demonstration Walkthrough (For College Viva & Presentation)

Follow this sequence to demonstrate all system capabilities:

### Step 1: Authentication & Security
1. The app launches with the **Login Screen**. The dashboard is protected and inaccessible before authentication.
2. Log in using default credentials:
   - **Email:** `admin@solarshield.com`
   - **Password:** `admin123`
3. *(Optional)* Click **"CREATE NEW ACCOUNT"** to demonstrate client-side input validation, password matching, email regex checking, and BCrypt salted password hashing.

### Step 2: Main Dashboard & Live Analytics
1. Upon logging in, view the **Overview Banner** with:
   - Live system clock
   - User identity badge
   - Active critical alerts counter
2. Observe the **KPI Cards**:
   - `Total Monitored Panels`
   - `Healthy Modules`
   - `Active Fault Detections`
   - `Critical Severity Faults`
   - `Pending Maintenance`
3. Review the **Interactive JavaFX Charts**:
   - Chart 1: **Fault Distribution** (PieChart)
   - Chart 2: **Risk Severity Breakdown** (BarChart)
   - Chart 3: **Temporal Fault Timeline** (Timeline trend)
4. View the **Active Critical Alerts** box displaying live high-risk notices.

### Step 3: Solar Panel Management
1. Click **"Solar Panels"** on the sidebar.
2. View the table of registered modules (SP-001 through SP-005).
3. Search and filter panels by ID, location, or status.
4. Click **"✚ Add Solar Panel"** to register a new module (e.g. `SP-006`, 450W, Bifacial).
5. Click **"View"** on any panel to open its technical specification card.
6. Click **"Detect"** to transition directly into the detection view with that module pre-selected.

### Step 4: AI Fault Detection & Localization
1. Navigate to **"Detect Fault"**.
2. Select target module from the dropdown.
3. Choose an image:
   - Click **"📁 Upload Solar Image"** to browse your computer.
   - Or click **"🔬 Load Demo Test Sample"** and select one of the pre-generated synthetic images from `sample_images/`:
     - `1_healthy_panel.jpg`
     - `2_hotspot_panel.jpg`
     - `3_crack_panel.jpg`
     - `4_soiled_panel.jpg`
     - `5_shaded_panel.jpg`
     - `6_damaged_panel.jpg`
   - Or click **"📷 Capture From Camera"** to test live webcam video capture with freeze-frame and snapshot capabilities.
4. *(Optional)* Expand the **In Situ Telemetry** pane to input operating values (e.g., Temp: `68.5°C`, Voltage: `32V`).
5. Click **"⚡ ANALYZE IMAGE WITH AI"**:
   - The preprocessing pipeline normalizes the image.
   - The OpenCV vision engine extracts features.
   - The HUD overlay draws corner brackets, colored bounding boxes, and diagnostic labels.
   - Displays:
     - Classification (e.g. `HOTSPOT`)
     - Confidence (e.g. `94.8%`)
     - Severity Badge (`HIGH` or `CRITICAL`)
     - Maintenance Priority (`URGENT` / `HIGH`)
     - Estimated Power Loss (e.g. `87.4 W (23.0% loss)`)
     - Spatial Quadrant (e.g. `Upper-Right Cell Block`)
     - Actionable Recommendation
6. Click **"💾 Save Diagnostic Result to Database"**.
   - If Severity is High or Critical, an automated modal alert is raised and logged.
   - The panel's status in the database is automatically updated.

### Step 4B: Drone Aerial Survey & Batch Folder Inspection (New Feature)
1. In the Detection Suite, click the tab **"⚡ Batch Farm Inspection"**.
2. Configure Flight & Array Settings:
   - **Mission Tag:** Enter survey flight title (e.g. `Drone-Survey-Alpha-ZoneB`).
   - **Mapping Strategy:** Choose between:
     - `Auto-Distribute Across Registered Farm Panels` (cycles evenly through fleet modules)
     - `Smart Match by Filename (e.g. SP-001)` (automatically binds images containing panel IDs)
     - `Assign All to Selected Default Panel`
3. Ingest Inspection Imagery:
   - Click **"🚁 Ingest Drone Survey Folder"** to select an entire flight directory. Supported formats: `.jpg`, `.jpeg`, `.png`, `.bmp`, `.webp`, `.tif`, `.tiff`.
   - Or click **"📁 Select Image Files"** / **"🔬 Load All 6 Synthetic Test Samples"**.
4. Click **"⚡ START BATCH ANALYSIS"**:
   - High-throughput asynchronous pipeline processes every aerial image.
   - Live progress bar with real-time **Elapsed Time & Remaining ETA** calculations.
   - Live-updating KPI cards: **Total Queued**, **Healthy Modules**, **Faults Detected**, **Critical / High Alerts**, and **Total Derating Loss (Watts)**.
5. Search & Filter Findings:
   - Use the live search bar to query by image filename or panel ID.
   - Use the **View Filter** dropdown to isolate `Faults Only`, `Healthy Only`, or `Critical & High Severity`.
6. Inspect & Export:
   - Double-click any row to open the full-screen **HUD Reticle Image Zoom Inspector**.
   - Click **"📑 Export Batch Audit PDF"** to generate an executive A4 Landscape aerial survey report complete with aggregate analytics, defect distribution breakdown, and color-coded severity tables.
   - Click **"📄 Export Batch CSV"** to export structured flight data for external GIS or spreadsheet tools.
   - Click **"💾 Save All to Database"** to commit all diagnostic records and trigger automated maintenance alerts.

### Step 5: Historical Fault Inspection
1. Click **"Fault History"** on the sidebar.
2. Filter past records by panel, defect type, severity, or date range.
3. Double-click or click **"Details"** on any row to open the complete inspection modal showing the saved input image, analyzed HUD overlay, telemetry, and notes.

### Step 6: Maintenance Work Orders
1. Click **"Maintenance"** on the sidebar.
2. View pending, scheduled, in-progress, and completed tasks.
3. Click **"✚ Create Maintenance Task"** to assign a technician (e.g., `Module Replacement` for `SP-004`).
4. Click **"Mark Done"** on an active task:
   - Status updates to `COMPLETED`.
   - The target panel's status is automatically restored to `HEALTHY`.
   - Dashboard pending count updates dynamically.

### Step 7: Reports & PDF/CSV Export
1. Click **"Reports"** on the sidebar.
2. Choose from 3 templates:
   - `AI Fault Detection & Audit Summary`
   - `Solar Panel Fleet Health Status`
   - `Maintenance & Repair Work Orders Manifest`
3. View the live structured text preview.
4. Click **"📄 Export PDF Document"** to generate a formally styled PDF using OpenPDF.
5. Click **"📊 Export CSV Spreadsheet"** to export structured comma-separated data for spreadsheet analysis.

### Step 8: System Settings & Diagnostics
1. Click **"Settings"** on the sidebar.
2. Inspect active user credentials and application version.
3. Switch between **OpenCV Computer Vision Engine** and **Demo AI / Fallback Model**.
4. Run diagnostics:
   - **Test Database:** Verifies SQLite connectivity.
   - **Test OpenCV:** Verifies native OpenCV DLL bindings.
   - **Test Camera:** Checks webcam availability.
5. Use **"Seed Default Demo Data"** to refresh sample fleet records if needed.

### Step 9: Logout & Security
1. Click **"🚪 Logout"** at the bottom of the sidebar.
2. Session state is cleared and the view safely returns to the **Login Screen**.

---

## 11. Future AI Model Integration Guide

SolarShield is designed so that a trained **Deep Learning Model** (e.g. YOLOv8-OBB, MobileNetV3, or ResNet-50 exported to **ONNX**) can be integrated with zero changes to the UI or database layers:

1. Create a class implementing `FaultDetectionModel`:
   ```java
   public class ONNXFaultDetectionModel implements FaultDetectionModel {
       // Load ONNX runtime weights
       @Override
       public ModelPrediction predict(Mat image, SensorData sensorData) {
           // 1. Convert Mat to FloatBuffer tensor [1, 3, 224, 224]
           // 2. Run session.run(...)
           // 3. Return ModelPrediction with bounding boxes
       }
   }
   ```
2. Register the class in `AIInferenceService.java`.
3. The rest of the application (JavaFX UI, HUD rendering, severity evaluation, SQLite logging, alerts, reports) continues to operate identically.

---

## 12. Full-Stack Web Application & Electrical Fault Diagnosis Module

In addition to the JavaFX desktop application, SolarShield features an integrated high-performance Python web application serving a rich, responsive interface with dedicated diagnostic modules for **Infrared Thermography (IRT)**, **Electroluminescence (EL)**, and **Electrical Fault Diagnosis & I–V Curve Tracing**.

### Electrical Fault Diagnosis Features
* **Standards-Compliant Analysis:** Implements IEC 60891 (temperature & irradiance normalization) and IEC 61724 (performance ratio calculation).
* **Power Calculations:** Explicitly distinguishes *calculated power* ($P = V \times I$), *measured power*, *rule-based warnings*, and *model predictions*.
* **I–V & P–V Curve Tracing:** Interactive vector SVG visualization of multi-point sweeps with MPP marker, theoretical STC comparison curve, and fill factor estimation.
* **Fault Classification Engine:** Transparent rule-based diagnosis of bypass diode conduction (sub-string drop), severe soiling current depression, high series resistance ($R_s$), low shunt resistance / PID leakage ($R_{sh}$), open circuits, and sensor outliers.
* **Non-Silent CSV Ingestion:** Full validation of uploaded multi-point and time-series CSV files reporting rejected rows with line numbers and reasons.
* **Work Order Integration:** One-click dispatch of electrical remediation work orders directly linked to SQLite fleet records.
* **Audit CSV Export:** Formally formatted CSV audit export via `/api/electrical/export/csv`.

### Running the Web Application
```bash
# Start the web server on port 3000
python web/server.py 3000

# Access the Web UI in your browser:
# http://localhost:3000
```

### Running the Electrical Test Suite
```bash
# Execute the comprehensive 9-criteria acceptance test suite
python test_electrical_suite.py
```

---

## 13. License & Academic Attribution

Developed for academic coursework in **Object-Oriented Programming using Java** and photovoltaic system diagnostics.  
Demonstrates modular design patterns, software security, computer vision integration, physics-based electrical analysis, and enterprise user experiences.
