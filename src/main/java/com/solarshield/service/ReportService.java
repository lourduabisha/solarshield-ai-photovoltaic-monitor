package com.solarshield.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.solarshield.model.*;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service generating structured PDF and CSV audit and diagnostic reports.
 * Uses OpenPDF for PDF document generation and file IO for CSV exports.
 */
public class ReportService {

    private static final Font FONT_TITLE = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(15, 35, 58));
    private static final Font FONT_SUBTITLE = new Font(Font.HELVETICA, 11, Font.ITALIC, new Color(100, 116, 139));
    private static final Font FONT_HEADER = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font FONT_CELL = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(30, 41, 59));
    private static final Font FONT_SECTION = new Font(Font.HELVETICA, 13, Font.BOLD, new Color(15, 35, 58));

    public boolean exportSingleFaultReportPdf(FaultReport report, SolarPanel panel, File targetFile) {
        if (report == null || targetFile == null) return false;

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            PdfWriter.getInstance(document, fos);
            document.open();

            // Header Banner
            Paragraph title = new Paragraph("SOLARSHIELD", FONT_TITLE);
            Paragraph subtitle = new Paragraph("AI-BASED SOLAR PANEL FAULT DETECTION & DIAGNOSTIC REPORT", FONT_SUBTITLE);
            document.add(title);
            document.add(subtitle);
            document.add(new Paragraph(" "));

            // Metadata Box
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingBefore(10f);
            metaTable.setSpacingAfter(15f);

            addCellToTable(metaTable, "Report ID: #" + report.getId(), FONT_CELL, new Color(241, 245, 249));
            addCellToTable(metaTable, "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), FONT_CELL, new Color(241, 245, 249));
            addCellToTable(metaTable, "Panel ID: " + report.getPanelId(), FONT_CELL, Color.WHITE);
            addCellToTable(metaTable, "Panel Name: " + (panel != null ? panel.getName() : "N/A"), FONT_CELL, Color.WHITE);
            addCellToTable(metaTable, "Location: " + (panel != null ? panel.getLocation() : "N/A"), FONT_CELL, new Color(241, 245, 249));
            addCellToTable(metaTable, "Rated Capacity: " + (panel != null ? panel.getRatedPower() + " W" : "N/A"), FONT_CELL, new Color(241, 245, 249));
            document.add(metaTable);

            // Diagnostic Section
            document.add(new Paragraph("AI Diagnostic Findings", FONT_SECTION));
            document.add(new Paragraph(" "));

            PdfPTable diagTable = new PdfPTable(4);
            diagTable.setWidthPercentage(100);
            diagTable.setWidths(new float[]{25f, 25f, 25f, 25f});

            addHeaderCell(diagTable, "Fault Classification");
            addHeaderCell(diagTable, "Confidence");
            addHeaderCell(diagTable, "Estimated Severity");
            addHeaderCell(diagTable, "Maintenance Priority");

            addCellToTable(diagTable, report.getFaultType().getDisplayName(), FONT_CELL, Color.WHITE);
            addCellToTable(diagTable, String.format("%.1f%%", report.getConfidence()), FONT_CELL, Color.WHITE);
            addCellToTable(diagTable, report.getSeverity().name(), FONT_CELL, Color.WHITE);
            addCellToTable(diagTable, report.getMaintenancePriority().name(), FONT_CELL, Color.WHITE);
            document.add(diagTable);

            // Energy Impact & Localization
            document.add(new Paragraph(" "));
            PdfPTable impactTable = new PdfPTable(2);
            impactTable.setWidthPercentage(100);

            addHeaderCell(impactTable, "Metric");
            addHeaderCell(impactTable, "Evaluation");

            addCellToTable(impactTable, "Estimated Power Loss", FONT_CELL, new Color(241, 245, 249));
            addCellToTable(impactTable, String.format("%.1f Watts (%.1f%% module derating)", report.getEstimatedEnergyLossWatts(), report.getEstimatedEnergyLossPercent()), FONT_CELL, new Color(241, 245, 249));

            addCellToTable(impactTable, "Localized Affected Region", FONT_CELL, Color.WHITE);
            addCellToTable(impactTable, report.getAffectedRegionLabel(), FONT_CELL, Color.WHITE);

            addCellToTable(impactTable, "Bounding Coordinates", FONT_CELL, new Color(241, 245, 249));
            addCellToTable(impactTable, String.format("X: %d, Y: %d, W: %d, H: %d", report.getAffectedX(), report.getAffectedY(), report.getAffectedWidth(), report.getAffectedHeight()), FONT_CELL, new Color(241, 245, 249));

            if (report.getTemperature() != null) {
                addCellToTable(impactTable, "Module Temperature", FONT_CELL, Color.WHITE);
                addCellToTable(impactTable, String.format("%.1f °C", report.getTemperature()), FONT_CELL, Color.WHITE);
            }

            document.add(impactTable);

            // Analysis Notes & Disclaimer
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Technical Notes & Recommendations:", FONT_SECTION));
            Paragraph notes = new Paragraph(report.getAnalysisNotes() != null && !report.getAnalysisNotes().isEmpty() ?
                    report.getAnalysisNotes() : "No special technician notes.", FONT_CELL);
            document.add(notes);

            document.add(new Paragraph(" "));
            Paragraph disclaimer = new Paragraph(
                    "NOTICE: This automated report is generated by SolarShield AI-assisted photovoltaic monitoring system. " +
                    "Power loss and localization figures are engineering estimates based on computer vision feature analysis.",
                    FONT_SUBTITLE
            );
            document.add(disclaimer);

            document.close();
            return true;
        } catch (Exception e) {
            System.err.println("[ReportService] Error creating PDF: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean exportFaultReportsPdf(List<FaultReport> reports, File targetFile) {
        if (targetFile == null) return false;

        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            PdfWriter.getInstance(document, fos);
            document.open();

            document.add(new Paragraph("SOLARSHIELD - FAULT AUDIT SUMMARY REPORT", FONT_TITLE));
            document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " | Total Records: " + reports.size(), FONT_SUBTITLE));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{8f, 12f, 16f, 12f, 12f, 12f, 14f, 14f});

            addHeaderCell(table, "ID");
            addHeaderCell(table, "Panel");
            addHeaderCell(table, "Fault Type");
            addHeaderCell(table, "Confidence");
            addHeaderCell(table, "Severity");
            addHeaderCell(table, "Priority");
            addHeaderCell(table, "Power Loss");
            addHeaderCell(table, "Date");

            boolean alt = false;
            for (FaultReport r : reports) {
                Color bg = alt ? new Color(248, 250, 252) : Color.WHITE;
                alt = !alt;

                addCellToTable(table, "#" + r.getId(), FONT_CELL, bg);
                addCellToTable(table, r.getPanelId(), FONT_CELL, bg);
                addCellToTable(table, r.getFaultType().getDisplayName(), FONT_CELL, bg);
                addCellToTable(table, String.format("%.1f%%", r.getConfidence()), FONT_CELL, bg);
                addCellToTable(table, r.getSeverity().name(), FONT_CELL, bg);
                addCellToTable(table, r.getMaintenancePriority().name(), FONT_CELL, bg);
                addCellToTable(table, String.format("%.1f W (%.1f%%)", r.getEstimatedEnergyLossWatts(), r.getEstimatedEnergyLossPercent()), FONT_CELL, bg);
                addCellToTable(table, r.getCreatedAt() != null ? r.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "N/A", FONT_CELL, bg);
            }

            document.add(table);
            document.close();
            return true;
        } catch (Exception e) {
            System.err.println("[ReportService] Error creating PDF: " + e.getMessage());
            return false;
        }
    }

    public boolean exportFaultReportsCsv(List<FaultReport> reports, File targetFile) {
        if (targetFile == null) return false;
        com.solarshield.database.SolarPanelRepository panelRepo = new com.solarshield.database.SolarPanelRepository();
        try (FileWriter writer = new FileWriter(targetFile)) {
            writer.write("ReportID,PanelID,PanelName,Location,DetectionDate,FaultType,DetectionMode,ModelVersion,DetectionScore,Severity,MaintenancePriority,AffectedRegion,EstimatedPowerImpact,WhyWasThisFlagged,RecommendedAction,MaintenanceStatus\n");
            for (FaultReport r : reports) {
                java.util.Optional<SolarPanel> pOpt = panelRepo.findByPanelId(r.getPanelId());
                String pName = pOpt.map(SolarPanel::getName).orElse("Solar Module " + r.getPanelId());
                String pLoc = pOpt.map(SolarPanel::getLocation).orElse("Array Zone A");
                String date = r.getCreatedAt() != null ? r.getCreatedAt().toString() : "";
                String fType = r.getFaultType() != null ? r.getFaultType().getDisplayName() : "HEALTHY";
                String detMode = "AI-ASSISTED DEMO";
                String modelVer = "v2.5-cv";
                double score = r.getConfidence();
                String sev = r.getSeverity() != null ? r.getSeverity().name() : "LOW";
                String prio = r.getMaintenancePriority() != null ? r.getMaintenancePriority().name() : "LOW";
                String region = r.getAffectedRegionLabel() != null ? r.getAffectedRegionLabel() : "Approximate Region";
                String powerImpact = String.format("%.1f W (%.1f%%)", r.getEstimatedEnergyLossWatts(), r.getEstimatedEnergyLossPercent());
                
                String why;
                String action;
                String upper = fType.toUpperCase();
                if (upper.contains("HOTSPOT")) {
                    why = "Localized high-temperature anomaly detected in the upper-right section.";
                    action = "Inspect thermal anomaly and associated electrical connections.";
                } else if (upper.contains("CRACK")) {
                    why = "Linear structural anomaly detected in the panel surface crossing busbars.";
                    action = "Perform detailed module inspection and assess replacement requirement.";
                } else if (upper.contains("SOIL")) {
                    why = "Surface contamination pattern detected across the panel area.";
                    action = "Schedule panel cleaning and surface inspection.";
                } else if (upper.contains("SHAD")) {
                    why = "Non-uniform illumination pattern detected across module strings.";
                    action = "Inspect surrounding objects and shading conditions.";
                } else if (upper.contains("DAMAG")) {
                    why = "Visible structural irregularity and mechanical glass fracture detected.";
                    action = "Perform physical inspection and evaluate module replacement.";
                } else {
                    why = "No significant anomaly detected across photovoltaic cells.";
                    action = "No immediate maintenance action required.";
                }

                String maintStatus = "RECORDED";

                writer.write(String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.1f,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        r.getId(),
                        escapeCsv(r.getPanelId()),
                        escapeCsv(pName),
                        escapeCsv(pLoc),
                        escapeCsv(date),
                        escapeCsv(fType),
                        escapeCsv(detMode),
                        escapeCsv(modelVer),
                        score,
                        escapeCsv(sev),
                        escapeCsv(prio),
                        escapeCsv(region),
                        escapeCsv(powerImpact),
                        escapeCsv(why),
                        escapeCsv(action),
                        escapeCsv(maintStatus)
                ));
            }
            return true;
        } catch (IOException e) {
            System.err.println("[ReportService] Error exporting CSV: " + e.getMessage());
            return false;
        }
    }

    public boolean exportPanelsCsv(List<SolarPanel> panels, File targetFile) {
        if (targetFile == null) return false;
        try (FileWriter writer = new FileWriter(targetFile)) {
            writer.write("PanelID,Name,Location,PanelType,RatedPowerWatts,Status,CurrentFault,Severity,Priority,LastInspection\n");
            for (SolarPanel p : panels) {
                writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%.1f,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        escapeCsv(p.getPanelId()),
                        escapeCsv(p.getName()),
                        escapeCsv(p.getLocation()),
                        escapeCsv(p.getPanelType()),
                        p.getRatedPower(),
                        p.getStatus().name(),
                        p.getCurrentFault().getDisplayName(),
                        p.getSeverity().name(),
                        p.getMaintenancePriority().name(),
                        p.getLastInspectionDate() != null ? p.getLastInspectionDate().toString() : "N/A"
                ));
            }
            return true;
        } catch (IOException e) {
            System.err.println("[ReportService] Error exporting panels CSV: " + e.getMessage());
            return false;
        }
    }

    public boolean exportBatchInspectionPdf(List<BatchDetectionItem> items, String missionTitle, File targetFile) {
        if (targetFile == null || items == null) return false;

        Document document = new Document(PageSize.A4.rotate(), 32, 32, 32, 32);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            PdfWriter.getInstance(document, fos);
            document.open();

            // Title & Subtitle Banner
            Paragraph title = new Paragraph("SOLARSHIELD - AERIAL DRONE INSPECTION & BATCH AUDIT REPORT", FONT_TITLE);
            String mission = (missionTitle != null && !missionTitle.trim().isEmpty()) ? missionTitle.trim() : "Aerial Drone Fleet Survey";
            Paragraph subtitle = new Paragraph(
                    String.format("Mission: %s  |  Generated: %s  |  Inspected Units: %d",
                            mission,
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            items.size()),
                    FONT_SUBTITLE
            );
            document.add(title);
            document.add(subtitle);
            document.add(new Paragraph(" "));

            // Aggregate Analytics
            int total = items.size();
            int healthy = 0;
            int faulty = 0;
            int critical = 0;
            double totalLossWatts = 0.0;
            int hotspotCount = 0;
            int crackCount = 0;
            int soilingCount = 0;
            int shadingCount = 0;
            int damageCount = 0;

            for (BatchDetectionItem item : items) {
                FaultDetectionService.DetectionResult res = item.getDetectionResult();
                if (res != null && res.isSuccess()) {
                    FaultType ft = res.getPrediction().getFaultType();
                    if (ft.isFault()) {
                        faulty++;
                        switch (ft) {
                            case HOTSPOT -> hotspotCount++;
                            case CRACK -> crackCount++;
                            case SOILING -> soilingCount++;
                            case PARTIAL_SHADING -> shadingCount++;
                            case PHYSICAL_DAMAGE -> damageCount++;
                            default -> {}
                        }
                    } else {
                        healthy++;
                    }

                    if (res.getSeverity() == Severity.CRITICAL || res.getSeverity() == Severity.HIGH) {
                        critical++;
                    }
                    if (res.getEnergyLossEstimate() != null) {
                        totalLossWatts += res.getEnergyLossEstimate().getLossWatts();
                    }
                }
            }

            // Summary Table
            PdfPTable summaryTable = new PdfPTable(5);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{20f, 20f, 20f, 20f, 20f});

            addHeaderCell(summaryTable, "Total Frames Analyzed");
            addHeaderCell(summaryTable, "Healthy Modules");
            addHeaderCell(summaryTable, "Detected Faults");
            addHeaderCell(summaryTable, "High / Critical Risks");
            addHeaderCell(summaryTable, "Total Power Loss");

            addCellToTable(summaryTable, String.valueOf(total), FONT_CELL, new Color(241, 245, 249));
            addCellToTable(summaryTable, String.format("%d (%.1f%%)", healthy, total > 0 ? (healthy * 100.0 / total) : 0), FONT_CELL, new Color(240, 253, 244));
            addCellToTable(summaryTable, String.format("%d (%.1f%%)", faulty, total > 0 ? (faulty * 100.0 / total) : 0), FONT_CELL, new Color(254, 249, 195));
            addCellToTable(summaryTable, String.valueOf(critical), FONT_CELL, critical > 0 ? new Color(254, 226, 226) : new Color(240, 253, 244));
            addCellToTable(summaryTable, String.format("%.1f Watts", totalLossWatts), FONT_CELL, new Color(254, 226, 226));

            document.add(summaryTable);
            document.add(new Paragraph(" "));

            // Defect Category Breakdown Table
            document.add(new Paragraph("Defect Category Distribution", FONT_SECTION));
            document.add(new Paragraph(" "));

            PdfPTable breakdownTable = new PdfPTable(6);
            breakdownTable.setWidthPercentage(100);
            breakdownTable.setWidths(new float[]{16.6f, 16.6f, 16.6f, 16.6f, 16.6f, 17f});

            addHeaderCell(breakdownTable, "Healthy");
            addHeaderCell(breakdownTable, "Hotspots");
            addHeaderCell(breakdownTable, "Micro-Cracks");
            addHeaderCell(breakdownTable, "Soiling / Dirt");
            addHeaderCell(breakdownTable, "Partial Shading");
            addHeaderCell(breakdownTable, "Physical Damage");

            addCellToTable(breakdownTable, String.valueOf(healthy), FONT_CELL, Color.WHITE);
            addCellToTable(breakdownTable, String.valueOf(hotspotCount), FONT_CELL, Color.WHITE);
            addCellToTable(breakdownTable, String.valueOf(crackCount), FONT_CELL, Color.WHITE);
            addCellToTable(breakdownTable, String.valueOf(soilingCount), FONT_CELL, Color.WHITE);
            addCellToTable(breakdownTable, String.valueOf(shadingCount), FONT_CELL, Color.WHITE);
            addCellToTable(breakdownTable, String.valueOf(damageCount), FONT_CELL, Color.WHITE);

            document.add(breakdownTable);
            document.add(new Paragraph(" "));

            // Inspection Roster Table
            document.add(new Paragraph("Comprehensive Inspection Inventory", FONT_SECTION));
            document.add(new Paragraph(" "));

            PdfPTable rosterTable = new PdfPTable(9);
            rosterTable.setWidthPercentage(100);
            rosterTable.setWidths(new float[]{5f, 20f, 13f, 15f, 10f, 10f, 13f, 10f, 14f});

            addHeaderCell(rosterTable, "#");
            addHeaderCell(rosterTable, "Aerial File");
            addHeaderCell(rosterTable, "Panel ID");
            addHeaderCell(rosterTable, "Condition");
            addHeaderCell(rosterTable, "Confidence");
            addHeaderCell(rosterTable, "Severity");
            addHeaderCell(rosterTable, "Est. Loss");
            addHeaderCell(rosterTable, "Priority");
            addHeaderCell(rosterTable, "Status");

            int idx = 1;
            for (BatchDetectionItem item : items) {
                FaultDetectionService.DetectionResult res = item.getDetectionResult();
                Color rowBg = (idx % 2 == 0) ? new Color(248, 250, 252) : Color.WHITE;

                // Highlight severity
                Color sevBg = rowBg;
                if (res != null && res.isSuccess()) {
                    Severity s = res.getSeverity();
                    if (s == Severity.CRITICAL) sevBg = new Color(254, 226, 226);
                    else if (s == Severity.HIGH) sevBg = new Color(255, 237, 213);
                    else if (s == Severity.MEDIUM) sevBg = new Color(254, 249, 195);
                    else if (s == Severity.LOW) sevBg = new Color(240, 253, 244);
                }

                String conf = res != null && res.isSuccess() ? String.format("%.1f%%", res.getPrediction().getConfidence()) : "-";
                String loss = res != null && res.isSuccess() ?
                        String.format("%.1f W (%.1f%%)", res.getEnergyLossEstimate().getLossWatts(), res.getEnergyLossEstimate().getLossPercent()) : "-";
                String priority = res != null && res.isSuccess() ? res.getMaintenancePriority().name() : "-";

                addCellToTable(rosterTable, String.valueOf(idx++), FONT_CELL, rowBg);
                addCellToTable(rosterTable, item.getFileName(), FONT_CELL, rowBg);
                addCellToTable(rosterTable, item.getPanelId(), FONT_CELL, rowBg);
                addCellToTable(rosterTable, item.getFaultType(), FONT_CELL, rowBg);
                addCellToTable(rosterTable, conf, FONT_CELL, rowBg);
                addCellToTable(rosterTable, item.getSeverity(), FONT_CELL, sevBg);
                addCellToTable(rosterTable, loss, FONT_CELL, rowBg);
                addCellToTable(rosterTable, priority, FONT_CELL, rowBg);
                addCellToTable(rosterTable, item.getStatus(), FONT_CELL, rowBg);
            }

            document.add(rosterTable);

            // Bottom Notice
            document.add(new Paragraph(" "));
            Paragraph footerNote = new Paragraph(
                    "OFFICIAL SOLARSHIELD AUDIT NOTICE: This batch document aggregates automated computer vision diagnostics from drone/aerial survey flights. " +
                    "All energy loss values and spatial coordinates are engineering estimations. Dispatch certified photovoltaic technicians for critical findings.",
                    FONT_SUBTITLE
            );
            document.add(footerNote);

            document.close();
            return true;
        } catch (Exception e) {
            System.err.println("[ReportService] Error creating batch PDF: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean exportMaintenanceCsv(List<Maintenance> tasks, File targetFile) {
        if (targetFile == null) return false;
        try (FileWriter writer = new FileWriter(targetFile)) {
            writer.write("TaskID,PanelID,Technician,MaintenanceType,ScheduledDate,Priority,Status,Notes\n");
            for (Maintenance m : tasks) {
                writer.write(String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        m.getId(),
                        escapeCsv(m.getPanelId()),
                        escapeCsv(m.getTechnician()),
                        escapeCsv(m.getMaintenanceType()),
                        m.getScheduledDate() != null ? m.getScheduledDate().toString() : "",
                        m.getPriority().name(),
                        m.getStatus().name(),
                        escapeCsv(m.getNotes())
                ));
            }
            return true;
        } catch (IOException e) {
            System.err.println("[ReportService] Error exporting maintenance CSV: " + e.getMessage());
            return false;
        }
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_HEADER));
        cell.setBackgroundColor(new Color(15, 35, 58));
        cell.setPadding(6f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addCellToTable(PdfPTable table, String text, Font font, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        return str.replace("\"", "\"\"");
    }
}
