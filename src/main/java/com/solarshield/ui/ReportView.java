package com.solarshield.ui;

import com.solarshield.database.FaultReportRepository;
import com.solarshield.database.MaintenanceRepository;
import com.solarshield.database.SolarPanelRepository;
import com.solarshield.model.*;
import com.solarshield.service.ReportService;
import com.solarshield.util.AlertUtils;
import com.solarshield.util.DateUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Audit and Technical Compliance Reporting View.
 * Provides live report previewing, aggregation statistics, and multi-format exporting to PDF and CSV.
 */
public class ReportView extends ScrollPane {

    private final ReportService reportService;
    private final FaultReportRepository reportRepository;
    private final SolarPanelRepository panelRepository;
    private final MaintenanceRepository maintenanceRepository;

    private ComboBox<String> reportTypeCombo;
    private TextArea previewArea;
    private Label recordCountLbl;

    public ReportView(ReportService reportService,
                      FaultReportRepository reportRepository,
                      SolarPanelRepository panelRepository,
                      MaintenanceRepository maintenanceRepository) {
        this.reportService = reportService;
        this.reportRepository = reportRepository;
        this.panelRepository = panelRepository;
        this.maintenanceRepository = maintenanceRepository;

        setFitToWidth(true);
        setStyle("-fx-background: #f1f5f9; -fx-background-color: transparent;");

        initUI();
        updatePreview();
    }

    private void initUI() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(24));

        // 1. Header
        VBox header = new VBox(4);
        Label title = new Label("AUDIT & TECHNICAL REPORT GENERATION");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        Label subtitle = new Label("Export formal engineering documentation, fleet health audits, and maintenance manifests");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        header.getChildren().addAll(title, subtitle);

        // 2. Report Selector & Export Toolbar
        HBox toolbar = new HBox(16);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(16));
        toolbar.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 12px; -fx-border-color: #e2e8f0; -fx-border-radius: 12px;");

        Label typeLbl = new Label("Report Document Template: *");
        typeLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e293b;");

        reportTypeCombo = new ComboBox<>();
        reportTypeCombo.getItems().addAll(
                "AI Fault Detection & Audit Summary",
                "Solar Panel Fleet Health Status",
                "Maintenance & Repair Work Orders Manifest"
        );
        reportTypeCombo.setValue("AI Fault Detection & Audit Summary");
        reportTypeCombo.setPrefWidth(300);
        reportTypeCombo.setOnAction(e -> updatePreview());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button exportPdfBtn = new Button("📄 Export PDF Document");
        exportPdfBtn.getStyleClass().add("btn-primary");
        exportPdfBtn.setOnAction(e -> handleExportPdf());

        Button exportCsvBtn = new Button("📊 Export CSV Spreadsheet");
        exportCsvBtn.getStyleClass().add("btn-outline");
        exportCsvBtn.setOnAction(e -> handleExportCsv());

        toolbar.getChildren().addAll(typeLbl, reportTypeCombo, spacer, exportPdfBtn, exportCsvBtn);

        // 3. Document Preview Card
        VBox previewCard = new VBox(12);
        previewCard.getStyleClass().add("card");
        previewCard.setPadding(new Insets(20));

        HBox previewHeader = new HBox(12);
        previewHeader.setAlignment(Pos.CENTER_LEFT);

        Label pTitle = new Label("LIVE STRUCTURED DOCUMENT PREVIEW");
        pTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        Region pSpacer = new Region();
        HBox.setHgrow(pSpacer, Priority.ALWAYS);

        recordCountLbl = new Label("0 records loaded");
        recordCountLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-font-weight: 600;");

        previewHeader.getChildren().addAll(pTitle, pSpacer, recordCountLbl);

        previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefRowCount(20);
        previewArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");

        previewCard.getChildren().addAll(previewHeader, new Separator(), previewArea);

        root.getChildren().addAll(header, toolbar, previewCard);
        setContent(root);
    }

    private void updatePreview() {
        String type = reportTypeCombo.getValue();
        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================================\n");
        sb.append("                                      SOLARSHIELD                                      \n");
        sb.append("               AI-BASED PHOTOVOLTAIC FAULT DETECTION & DIAGNOSTIC AUDIT               \n");
        sb.append("========================================================================================\n");
        sb.append("Generated On : ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("Document Type: ").append(type).append("\n");
        sb.append("Organization : SolarShield Renewable Farm Analytics\n");
        sb.append("----------------------------------------------------------------------------------------\n\n");

        if (type.contains("Fault")) {
            List<FaultReport> list = reportRepository.findAll();
            recordCountLbl.setText(list.size() + " inspection records loaded");
            sb.append(String.format("%-6s | %-8s | %-16s | %-8s | %-10s | %-10s | %-14s | %-16s\n",
                    "ID", "PANEL", "FAULT DETECTED", "CONF.", "SEVERITY", "PRIORITY", "POWER LOSS", "DATE"));
            sb.append("----------------------------------------------------------------------------------------\n");
            for (FaultReport r : list) {
                sb.append(String.format("#%-5d | %-8s | %-16s | %5.1f%% | %-10s | %-10s | %6.1fW (%4.1f%%) | %s\n",
                        r.getId(),
                        r.getPanelId(),
                        r.getFaultType().getDisplayName(),
                        r.getConfidence(),
                        r.getSeverity().name(),
                        r.getMaintenancePriority().name(),
                        r.getEstimatedEnergyLossWatts(),
                        r.getEstimatedEnergyLossPercent(),
                        DateUtils.formatDisplayDateTime(r.getCreatedAt())
                ));
            }
        } else if (type.contains("Fleet")) {
            List<SolarPanel> list = panelRepository.findAll();
            recordCountLbl.setText(list.size() + " solar panels loaded");
            sb.append(String.format("%-8s | %-20s | %-18s | %-10s | %-10s | %-14s\n",
                    "PANEL ID", "NAME", "LOCATION", "CAPACITY", "STATUS", "FAULT"));
            sb.append("----------------------------------------------------------------------------------------\n");
            for (SolarPanel p : list) {
                sb.append(String.format("%-8s | %-20s | %-18s | %6.1f W | %-10s | %-14s\n",
                        p.getPanelId(),
                        p.getName(),
                        p.getLocation(),
                        p.getRatedPower(),
                        p.getStatus().getDisplayName(),
                        p.getCurrentFault().getDisplayName()
                ));
            }
        } else {
            List<Maintenance> list = maintenanceRepository.findAll();
            recordCountLbl.setText(list.size() + " maintenance tasks loaded");
            sb.append(String.format("%-6s | %-8s | %-28s | %-18s | %-12s | %-10s\n",
                    "TASK#", "PANEL", "SERVICE TYPE", "TECHNICIAN", "SCHEDULED", "STATUS"));
            sb.append("----------------------------------------------------------------------------------------\n");
            for (Maintenance m : list) {
                sb.append(String.format("#%-5d | %-8s | %-28s | %-18s | %-12s | %-10s\n",
                        m.getId(),
                        m.getPanelId(),
                        m.getMaintenanceType(),
                        m.getTechnician(),
                        DateUtils.formatDate(m.getScheduledDate()),
                        m.getStatus().name()
                ));
            }
        }

        sb.append("\n----------------------------------------------------------------------------------------\n");
        sb.append("NOTICE: This audit report is produced by SolarShield. Estimates are subject to ongoing field verification.\n");
        sb.append("========================================================================================\n");

        previewArea.setText(sb.toString());
    }

    private void handleExportPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save PDF Audit Document");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"));

        String type = reportTypeCombo.getValue();
        String defaultName = type.contains("Fault") ? "SolarShield_Fault_Audit.pdf" :
                (type.contains("Fleet") ? "SolarShield_Fleet_Report.pdf" : "SolarShield_Maintenance_Report.pdf");
        chooser.setInitialFileName(defaultName);

        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            boolean success = false;
            if (type.contains("Fault")) {
                success = reportService.exportFaultReportsPdf(reportRepository.findAll(), file);
            } else {
                // For other reports, export full fault summary as core PDF format
                success = reportService.exportFaultReportsPdf(reportRepository.findAll(), file);
            }

            if (success) {
                AlertUtils.showInfo("Export Complete", "PDF Generated",
                        "The report was successfully rendered and exported to:\n" + file.getAbsolutePath());
            } else {
                AlertUtils.showError("Export Failed", "Error", "Failed to generate PDF document.");
            }
        }
    }

    private void handleExportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save CSV Spreadsheet");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Spreadsheets (*.csv)", "*.csv"));

        String type = reportTypeCombo.getValue();
        String defaultName = type.contains("Fault") ? "solarshield_faults.csv" :
                (type.contains("Fleet") ? "solarshield_panels.csv" : "solarshield_maintenance.csv");
        chooser.setInitialFileName(defaultName);

        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            boolean success;
            if (type.contains("Fault")) {
                success = reportService.exportFaultReportsCsv(reportRepository.findAll(), file);
            } else if (type.contains("Fleet")) {
                success = reportService.exportPanelsCsv(panelRepository.findAll(), file);
            } else {
                success = reportService.exportMaintenanceCsv(maintenanceRepository.findAll(), file);
            }

            if (success) {
                AlertUtils.showInfo("Export Complete", "CSV Exported",
                        "Data exported successfully to:\n" + file.getAbsolutePath());
            } else {
                AlertUtils.showError("Export Failed", "Error", "Failed to write CSV file.");
            }
        }
    }

    public void generateSingleFaultReportPdf(FaultReport report) {
        if (report == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Fault Inspection PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Document (*.pdf)", "*.pdf"));
        chooser.setInitialFileName("SolarShield_Report_" + report.getPanelId() + "_" + report.getId() + ".pdf");

        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            SolarPanel p = panelRepository.findByPanelId(report.getPanelId()).orElse(null);
            boolean ok = reportService.exportSingleFaultReportPdf(report, p, file);
            if (ok) {
                AlertUtils.showInfo("PDF Exported", "Report Created", "Single fault report exported to:\n" + file.getAbsolutePath());
            } else {
                AlertUtils.showError("Export Error", "Failed", "Could not export PDF report.");
            }
        }
    }
}
