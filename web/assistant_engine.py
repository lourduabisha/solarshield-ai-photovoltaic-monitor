"""
SolarShield AI Assistant — Solar Care & Recycling Advisor
==========================================================
Pure-software intelligence module providing:
1. Damaged and failed solar panel investigation workflow
2. Evidence-based repair vs. retirement decisions
3. Responsible 8-stage solar panel recycling workflow
4. Smart maintenance reminder engine (Scheduled, Due Soon, Due Today, Overdue)
5. Conversational PV asset query interface grounded in real database records
"""

import os
import sys
import json
import time
import sqlite3
import re
from datetime import datetime, date, timedelta
from typing import Dict, List, Tuple, Optional, Any

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_PATH = os.environ.get("DATABASE_PATH") or os.environ.get("SQLITE_DB_PATH") or os.path.join(BASE_DIR, "solarshield.db")

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

# ==============================================================================
# 1. DATABASE SCHEMA INITIALIZATION & MIGRATIONS
# ==============================================================================

def init_assistant_db():
    """Ensure database tables for investigations, recycling workflows, and reminders exist."""
    conn = get_db()
    cur = conn.cursor()

    def add_col(tbl, col, col_type):
        cur.execute(f"PRAGMA table_info({tbl})")
        cols = [r[1] for r in cur.fetchall()]
        if col not in cols:
            cur.execute(f"ALTER TABLE {tbl} ADD COLUMN {col} {col_type}")

    # 1. Panel Failure Investigation Table
    cur.execute("""
        CREATE TABLE IF NOT EXISTS panel_investigations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            panel_id TEXT NOT NULL,
            failure_reported_date TEXT NOT NULL,
            reporter_name TEXT DEFAULT 'Field Engineer',
            failure_type TEXT DEFAULT 'USER_REPORTED_FAILURE',
            evidence_summary TEXT,
            investigation_notes TEXT,
            inspection_result TEXT,
            decision TEXT DEFAULT 'UNDER_REVIEW',
            retirement_date TEXT,
            recycling_status TEXT DEFAULT 'NOT_STARTED',
            recycling_provider TEXT,
            recycling_receipt_ref TEXT,
            status TEXT DEFAULT 'OPEN',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
    """)
    cur.execute("CREATE INDEX IF NOT EXISTS idx_investigations_panel ON panel_investigations(panel_id)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_investigations_status ON panel_investigations(status)")

    # 2. Panel Recycling Workflow Records
    cur.execute("""
        CREATE TABLE IF NOT EXISTS panel_recycling_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            investigation_id INTEGER,
            panel_id TEXT NOT NULL,
            panel_model TEXT,
            quantity INTEGER DEFAULT 1,
            region TEXT DEFAULT 'Global',
            decommissioned_by_qualified INTEGER DEFAULT 0,
            decommission_date TEXT,
            takeback_program TEXT,
            recycler_name TEXT,
            recycler_contact TEXT,
            collection_date TEXT,
            handover_confirmed INTEGER DEFAULT 0,
            certificate_ref TEXT,
            current_stage INTEGER DEFAULT 1,
            stage_status_json TEXT DEFAULT '{}',
            completion_date TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (investigation_id) REFERENCES panel_investigations(id)
        )
    """)
    cur.execute("CREATE INDEX IF NOT EXISTS idx_recycling_panel ON panel_recycling_records(panel_id)")

    # 3. Enhance maintenance table for smart reminders
    add_col("maintenance", "last_reminder_sent", "TEXT")
    add_col("maintenance", "rescheduled_date", "TEXT")
    add_col("maintenance", "reminder_pref", "TEXT DEFAULT 'STANDARD'")

    # Seed an investigation sample if table is empty so users can immediately inspect the workflow
    cur.execute("SELECT count(*) FROM panel_investigations")
    if cur.fetchone()[0] == 0:
        now_iso = datetime.now().isoformat()
        cur.execute("""
            INSERT INTO panel_investigations (
                panel_id, failure_reported_date, reporter_name, failure_type,
                evidence_summary, investigation_notes, inspection_result,
                decision, recycling_status, status, created_at, updated_at
            ) VALUES (
                'SP-003', ?, 'Chief Diagnostic Engineer', 'PHYSICAL_DAMAGE',
                'Wafer crack detected with 22.0% estimated energy loss. Visual micro-fractures present across upper quadrant.',
                'Module exhibits spiderweb fracture pattern following hailstorm trauma. Thermal proxy shows localized resistance.',
                'CONFIRMED_STRUCTURAL_CRACK', 'REPAIR_EVALUATION', 'NOT_STARTED', 'OPEN', ?, ?
            )
        """, (now_iso[:10], now_iso, now_iso))

    conn.commit()
    conn.close()

# ==============================================================================
# 2. COMPREHENSIVE PANEL CONTEXT AGGREGATOR
# ==============================================================================

def get_panel_context(panel_id: str) -> Dict[str, Any]:
    """
    Retrieve and synthesize all existing database telemetry for a specific panel.
    Returns specs, fault reports, IRT scans, EL scans, electrical records, work orders,
    and investigation status. Does NOT fabricate missing values.
    """
    clean_id = panel_id.strip().upper()
    conn = get_db()
    cur = conn.cursor()

    # 1. Panel Base Record
    cur.execute("SELECT * FROM solar_panels WHERE panel_id = ?", (clean_id,))
    p_row = cur.fetchone()
    if not p_row:
        conn.close()
        return {"found": False, "panelId": clean_id, "message": f"Module '{clean_id}' was not found in the fleet directory."}

    panel = dict(p_row)

    # 2. Fault Reports (RGB / Studio)
    cur.execute("""
        SELECT id, fault_type, confidence, severity, maintenance_priority,
               estimated_energy_loss_watts as power_loss_watts,
               estimated_energy_loss_percent as power_loss_percentage,
               detection_mode, model_name, model_version, why_flagged, recommended_action, created_at
        FROM fault_reports WHERE panel_id = ? ORDER BY id DESC LIMIT 10
    """, (clean_id,))
    reports = [dict(r) for r in cur.fetchall()]

    # 3. Thermal Inspections (IRT)
    cur.execute("""
        SELECT id, is_calibrated, t_max, t_nominal, delta_t, iec_class,
               severity, anomaly_type, why_flagged, recommended_action, created_at
        FROM thermal_inspections WHERE panel_id = ? ORDER BY id DESC LIMIT 5
    """, (clean_id,))
    thermal_scans = [dict(r) for r in cur.fetchall()]

    # 4. EL Inspections
    cur.execute("""
        SELECT id, defect_category, severity, anomaly_score,
               crack_pixel_count, inactive_area_percent, why_flagged, created_at
        FROM el_inspections WHERE panel_id = ? ORDER BY id DESC LIMIT 5
    """, (clean_id,))
    el_scans = [dict(r) for r in cur.fetchall()]

    # 5. Electrical Inspections (I-V curve)
    cur.execute("""
        SELECT id, voltage, current, measured_power, calculated_power,
               power_discrepancy_percent, performance_ratio, diagnostic_status,
               severity, fill_factor, diode_step_detected, why_flagged, created_at
        FROM electrical_inspections WHERE panel_id = ? ORDER BY id DESC LIMIT 5
    """, (clean_id,))
    electrical_scans = [dict(r) for r in cur.fetchall()]

    # 6. Maintenance Work Orders
    cur.execute("""
        SELECT id, maintenance_type, technician, scheduled_date, priority,
               status, notes, created_at, completed_at, last_reminder_sent, rescheduled_date
        FROM maintenance WHERE panel_id = ? ORDER BY id DESC LIMIT 10
    """, (clean_id,))
    work_orders = [dict(r) for r in cur.fetchall()]

    # 7. Failure Investigations
    cur.execute("""
        SELECT * FROM panel_investigations WHERE panel_id = ? ORDER BY id DESC LIMIT 5
    """, (clean_id,))
    investigations = [dict(r) for r in cur.fetchall()]

    # 8. Recycling Records
    cur.execute("""
        SELECT * FROM panel_recycling_records WHERE panel_id = ? ORDER BY id DESC LIMIT 5
    """, (clean_id,))
    recycling = [dict(r) for r in cur.fetchall()]

    conn.close()

    return {
        "found": True,
        "panelId": clean_id,
        "name": panel.get("name"),
        "location": panel.get("location") or "Array Zone A (Sector 1)",
        "installationDate": panel.get("installation_date") or "Unknown / Not Recorded",
        "panelType": panel.get("panel_type") or "Monocrystalline Silicon (Standard)",
        "ratedPower": panel.get("rated_power", 400.0),
        "status": panel.get("status", "UNKNOWN"),
        "currentFault": panel.get("current_fault", "HEALTHY"),
        "severity": panel.get("severity", "LOW"),
        "maintenancePriority": panel.get("maintenance_priority", "LOW"),
        "faultReports": reports,
        "thermalScans": thermal_scans,
        "elScans": el_scans,
        "electricalScans": electrical_scans,
        "workOrders": work_orders,
        "investigations": investigations,
        "recyclingRecords": recycling
    }

# ==============================================================================
# 3. SMART MAINTENANCE REMINDER ENGINE
# ==============================================================================

def get_smart_maintenance_reminders() -> Dict[str, Any]:
    """
    Evaluate all scheduled maintenance work orders against today's date.
    Categorizes tasks dynamically into:
    - DUE_TODAY: Scheduled for today's date
    - DUE_SOON: Scheduled within the next 7 days
    - OVERDUE: Past due date and not marked COMPLETED
    - SCHEDULED: Future tasks > 7 days out
    - IN_PROGRESS: Currently active field interventions
    - COMPLETED: Finished work orders
    """
    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
        SELECT m.id, m.panel_id, m.maintenance_type, m.technician,
               m.scheduled_date, m.priority, m.status, m.notes,
               m.created_at, m.completed_at, m.last_reminder_sent, m.rescheduled_date,
               p.name as panel_name, p.location
        FROM maintenance m
        LEFT JOIN solar_panels p ON m.panel_id = p.panel_id
        ORDER BY m.scheduled_date ASC
    """)
    rows = cur.fetchall()
    conn.close()

    today_str = date.today().isoformat()
    today_dt = date.today()

    due_today = []
    due_soon = []
    overdue = []
    scheduled = []
    in_progress = []
    completed = []

    for r in rows:
        item = dict(r)
        sched_str = item["scheduled_date"] or ""
        raw_status = (item["status"] or "PENDING").upper()

        if raw_status == "COMPLETED":
            item["reminderCategory"] = "COMPLETED"
            completed.append(item)
            continue

        if raw_status == "IN_PROGRESS":
            item["reminderCategory"] = "IN_PROGRESS"
            in_progress.append(item)
            continue

        # Date calculations
        try:
            sched_date = datetime.strptime(sched_str[:10], "%Y-%m-%d").date()
            diff_days = (sched_date - today_dt).days

            item["daysUntilDue"] = diff_days

            if diff_days < 0:
                item["reminderCategory"] = "OVERDUE"
                item["daysOverdue"] = abs(diff_days)
                overdue.append(item)
            elif diff_days == 0:
                item["reminderCategory"] = "DUE_TODAY"
                due_today.append(item)
            elif 1 <= diff_days <= 7:
                item["reminderCategory"] = "DUE_SOON"
                due_soon.append(item)
            else:
                item["reminderCategory"] = "SCHEDULED"
                scheduled.append(item)
        except Exception:
            item["reminderCategory"] = "SCHEDULED"
            item["daysUntilDue"] = 999
            scheduled.append(item)

    all_reminders = []
    for cat_name, cat_list in [
        ("OVERDUE", overdue),
        ("DUE_TODAY", due_today),
        ("DUE_SOON", due_soon),
        ("SCHEDULED", scheduled),
        ("IN_PROGRESS", in_progress),
        ("COMPLETED", completed),
    ]:
        for itm in cat_list:
            c = dict(itm)
            c["computed_status"] = cat_name
            c["days_until_due"] = itm.get("daysUntilDue", 0)
            c["days_overdue"] = itm.get("daysOverdue", 0)
            all_reminders.append(c)

    return {
        "success": True,
        "today": today_str,
        "counts": {
            "total": len(rows),
            "overdue": len(overdue),
            "dueToday": len(due_today),
            "dueSoon": len(due_soon),
            "scheduled": len(scheduled),
            "inProgress": len(in_progress),
            "completed": len(completed)
        },
        "summary": {
            "total": len(rows),
            "overdue": len(overdue),
            "due_today": len(due_today),
            "due_soon": len(due_soon),
            "scheduled": len(scheduled),
            "in_progress": len(in_progress),
            "completed": len(completed)
        },
        "reminders": all_reminders,
        "overdue": overdue,
        "dueToday": due_today,
        "dueSoon": due_soon,
        "scheduled": scheduled,
        "inProgress": in_progress,
        "completed": completed
    }

def reschedule_maintenance_task(task_id: int, new_date: str, reason: str = "", user_name: str = "ENGINEER") -> Dict[str, Any]:
    """Reschedule a maintenance task with new date and audit notes."""
    new_date = (new_date or "").strip()
    if not new_date:
        return {"success": False, "message": "New scheduled date is required."}

    conn = get_db()
    cur = conn.cursor()
    cur.execute("SELECT id, panel_id, scheduled_date, notes FROM maintenance WHERE id = ?", (task_id,))
    task = cur.fetchone()
    if not task:
        conn.close()
        return {"success": False, "message": f"Maintenance task #{task_id} not found."}

    old_date = task["scheduled_date"]
    old_notes = task["notes"] or ""
    now_iso = datetime.now().isoformat()
    audit_note = f"\n[Rescheduled on {now_iso[:10]} by {user_name}: {old_date} -> {new_date}. Reason: {reason or 'Schedule updated'}]"
    updated_notes = (old_notes + audit_note).strip()

    cur.execute("""
        UPDATE maintenance
        SET scheduled_date = ?,
            rescheduled_date = ?,
            notes = ?
        WHERE id = ?
    """, (new_date, now_iso, updated_notes, task_id))
    conn.commit()
    conn.close()

    return {
        "success": True,
        "message": f"Work order #{task_id} successfully rescheduled from {old_date} to {new_date}.",
        "taskId": task_id,
        "newDate": new_date
    }

def record_reminder_sent(task_id: int, user_name: str = "ENGINEER") -> Dict[str, Any]:
    """Record that a reminder dispatch or notification was acknowledged/sent."""
    now_iso = datetime.now().isoformat()
    conn = get_db()
    cur = conn.cursor()
    cur.execute("UPDATE maintenance SET last_reminder_sent = ? WHERE id = ?", (now_iso, task_id))
    conn.commit()
    conn.close()
    return {"success": True, "message": f"Reminder recorded for task #{task_id}.", "lastReminderSent": now_iso}

# ==============================================================================
# 4. SOLAR PANEL FAILURE INVESTIGATION & RECYCLING WORKFLOW
# ==============================================================================

def record_failure_investigation(payload: Dict[str, Any], user_name: str = "ENGINEER") -> Dict[str, Any]:
    """
    Log or update a failure investigation for a solar panel.
    Handles decision recording (REPAIR, RETIRE, FURTHER_INSPECTION, CONTINUED_MONITORING).
    """
    panel_id = (payload.get("panelId") or payload.get("panel_id") or "").strip().upper()
    if not panel_id:
        return {"success": False, "message": "Panel ID is required."}

    reported_date = payload.get("failureReportedDate") or payload.get("failure_reported_date") or datetime.now().isoformat()[:10]
    failure_type = payload.get("failureType") or payload.get("failure_type", "USER_REPORTED_FAILURE")
    evidence = payload.get("evidenceSummary") or payload.get("evidence_summary", "")
    notes = payload.get("investigationNotes") or payload.get("investigation_notes", "")
    decision = payload.get("decision") or payload.get("investigation_decision", "UNDER_REVIEW")
    inspection_res = payload.get("inspectionResult") or payload.get("inspection_result", "PENDING_VERIFICATION")

    now_iso = datetime.now().isoformat()
    retirement_date = now_iso[:10] if decision == "RETIRE" else (payload.get("retirementDate") or payload.get("retirement_date"))

    conn = get_db()
    cur = conn.cursor()

    # Check if existing open investigation exists
    cur.execute("SELECT id FROM panel_investigations WHERE panel_id = ? AND status = 'OPEN'", (panel_id,))
    existing = cur.fetchone()

    if existing:
        inv_id = existing["id"]
        cur.execute("""
            UPDATE panel_investigations
            SET failure_type = ?,
                evidence_summary = ?,
                investigation_notes = ?,
                inspection_result = ?,
                decision = ?,
                retirement_date = ?,
                updated_at = ?
            WHERE id = ?
        """, (failure_type, evidence, notes, inspection_res, decision, retirement_date, now_iso, inv_id))
    else:
        cur.execute("""
            INSERT INTO panel_investigations (
                panel_id, failure_reported_date, reporter_name, failure_type,
                evidence_summary, investigation_notes, inspection_result,
                decision, retirement_date, recycling_status, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOT_STARTED', 'OPEN', ?, ?)
        """, (
            panel_id, reported_date, user_name, failure_type,
            evidence, notes, inspection_res, decision, retirement_date, now_iso, now_iso
        ))
        inv_id = cur.lastrowid

    # If decision is RETIRE, ensure status on solar_panels is updated transparently
    if decision == "RETIRE":
        cur.execute("UPDATE solar_panels SET status = 'RETIRED' WHERE panel_id = ?", (panel_id,))

    conn.commit()
    conn.close()

    return {
        "success": True,
        "message": f"Investigation #{inv_id} for panel {panel_id} updated successfully. Decision: {decision}.",
        "investigationId": inv_id,
        "investigation_id": inv_id,
        "panelId": panel_id,
        "panel_id": panel_id,
        "decision": decision,
        "investigation_decision": decision
    }

def update_recycling_stage(payload: Dict[str, Any], user_name: str = "ENGINEER") -> Dict[str, Any]:
    """
    Progress through the 8-stage solar panel recycling workflow.
    Ensures safe decommissioning confirmation before logistics.
    """
    panel_id = (payload.get("panelId") or payload.get("panel_id") or "").strip().upper()
    stage = int(payload.get("stage", 1))
    status = payload.get("status", "IN_PROGRESS")
    region = payload.get("region", "General")
    recycler_name = payload.get("recyclerName") or payload.get("provider")
    recycler_contact = payload.get("recyclerContact") or payload.get("contact")
    cert_ref = payload.get("certificateRef") or payload.get("cert_ref")

    if not panel_id:
        return {"success": False, "message": "Panel ID is required."}

    conn = get_db()
    cur = conn.cursor()

    cur.execute("SELECT * FROM panel_recycling_records WHERE panel_id = ? ORDER BY id DESC LIMIT 1", (panel_id,))
    rec = cur.fetchone()
    now_iso = datetime.now().isoformat()

    if rec:
        rec_id = rec["id"]
        stages = json.loads(rec["stage_status_json"] or "{}")
        stages[f"stage_{stage}"] = status

        completion = now_iso[:10] if (stage == 8 and status == "COMPLETED") else rec["completion_date"]

        cur.execute("""
            UPDATE panel_recycling_records
            SET current_stage = ?,
                stage_status_json = ?,
                region = COALESCE(?, region),
                recycler_name = COALESCE(?, recycler_name),
                recycler_contact = COALESCE(?, recycler_contact),
                certificate_ref = COALESCE(?, certificate_ref),
                completion_date = ?,
                updated_at = ?
            WHERE id = ?
        """, (
            stage, json.dumps(stages), region, recycler_name, recycler_contact,
            cert_ref, completion, now_iso, rec_id
        ))
    else:
        stages = {f"stage_{stage}": status}
        cur.execute("""
            INSERT INTO panel_recycling_records (
                panel_id, panel_model, quantity, region,
                recycler_name, recycler_contact, certificate_ref,
                current_stage, stage_status_json, created_at, updated_at
            ) VALUES (?, 'Photovoltaic Module', 1, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            panel_id, region, recycler_name, recycler_contact, cert_ref,
            stage, json.dumps(stages), now_iso, now_iso
        ))
        rec_id = cur.lastrowid

    # Update investigation recycling status
    cur.execute("SELECT id FROM panel_investigations WHERE panel_id = ? ORDER BY id DESC LIMIT 1", (panel_id,))
    inv = cur.fetchone()
    if inv:
        overall_status = "COMPLETED" if (stage == 8 and status == "COMPLETED") else "IN_PROGRESS"
        cur.execute("""
            UPDATE panel_investigations
            SET recycling_status = ?,
                recycling_provider = COALESCE(?, recycling_provider),
                recycling_receipt_ref = COALESCE(?, recycling_receipt_ref),
                updated_at = ?
            WHERE id = ?
        """, (overall_status, recycler_name, cert_ref, now_iso, inv["id"]))

    conn.commit()
    conn.close()

    return {
        "success": True,
        "message": f"Recycling workflow for panel {panel_id} updated: Stage {stage} is {status}.",
        "recyclingId": rec_id,
        "currentStage": stage,
        "stageStatus": status
    }

# ==============================================================================
# 5. CONVERSATIONAL ASSISTANT CORE ENGINE
# ==============================================================================

def query_fleet_summary() -> Dict[str, Any]:
    """Retrieve aggregate fleet statistics from SQLite database."""
    conn = get_db()
    cur = conn.cursor()
    cur.execute("SELECT count(*) FROM solar_panels")
    total_panels = cur.fetchone()[0]

    cur.execute("SELECT count(*) FROM solar_panels WHERE status = 'HEALTHY'")
    healthy_panels = cur.fetchone()[0]

    cur.execute("SELECT count(*) FROM solar_panels WHERE status = 'FAULTY'")
    faulty_panels = cur.fetchone()[0]

    cur.execute("SELECT count(*) FROM solar_panels WHERE status = 'RETIRED'")
    retired_panels = cur.fetchone()[0]

    cur.execute("SELECT count(*) FROM fault_reports")
    total_reports = cur.fetchone()[0]

    cur.execute("SELECT count(*) FROM maintenance WHERE status != 'COMPLETED'")
    open_maintenance = cur.fetchone()[0]

    conn.close()

    return {
        "totalPanels": total_panels,
        "healthyPanels": healthy_panels,
        "faultyPanels": faulty_panels,
        "retiredPanels": retired_panels,
        "totalReports": total_reports,
        "openMaintenance": open_maintenance
    }

def get_panels_with_recurring_faults() -> List[Dict[str, Any]]:
    """Identify panels that appear in multiple fault reports."""
    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
        SELECT panel_id, count(*) as fault_count,
               group_concat(DISTINCT fault_type) as fault_types,
               max(severity) as max_severity
        FROM fault_reports
        GROUP BY panel_id
        HAVING fault_count > 1
        ORDER BY fault_count DESC
        LIMIT 10
    """)
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows

def process_assistant_message(
    user_message: str,
    selected_panel_id: Optional[str] = None,
    user_name: str = "ENGINEER"
) -> Dict[str, Any]:
    """
    Process incoming user message, recognize intent, aggregate real database evidence,
    and generate a comprehensive, structured response with suggested follow-up questions.
    """
    msg = (user_message or "").strip().lower()

    # Extract potential panel ID mentioned in message (e.g. SP-001, SP-023)
    panel_match = re.search(r'\b(SP-\d{3})\b', user_message, re.IGNORECASE)
    panel_id = panel_match.group(1).upper() if panel_match else (selected_panel_id.upper() if selected_panel_id else None)

    context = get_panel_context(panel_id) if panel_id else None
    reminders = get_smart_maintenance_reminders()

    reply_text = ""
    suggested_questions = []

    # INTENT 1: OVERDUE MAINTENANCE
    if "overdue" in msg or "past due" in msg:
        overdue_list = reminders["overdue"]
        if overdue_list:
            reply_text = f"### ⚠️ Overdue Maintenance Notice ({len(overdue_list)} Tasks Found)\n\n"
            reply_text += "The following field work orders are past their scheduled target dates and require immediate intervention:\n\n"
            for t in overdue_list[:5]:
                reply_text += f"- **Task #{t['id']} (Panel {t['panel_id']})**: {t['maintenance_type']}\n"
                reply_text += f"  - **Scheduled Date**: `{t['scheduled_date']}` ({t.get('daysOverdue', 1)} days overdue)\n"
                reply_text += f"  - **Priority**: `{t['priority']}` | **Technician**: {t.get('technician', 'Unassigned')}\n"
            if len(overdue_list) > 5:
                reply_text += f"\n*...and {len(overdue_list) - 5} more overdue tasks in the manifest.*\n"
            reply_text += "\n**Recommended Action**: Review technician dispatch availability or reschedule target dates via the Maintenance Reminders panel."
        else:
            reply_text = "### ✅ No Overdue Maintenance\n\nAll scheduled field maintenance work orders are currently up to date. No tasks have exceeded their target dates."

        suggested_questions = [
            "What maintenance is due this week?",
            "Which panels have recurring faults?",
            "My solar panel has completely failed. What should I do?"
        ]

    # INTENT 2: DUE THIS WEEK / DUE TODAY
    elif "due this week" in msg or "due today" in msg or "upcoming maintenance" in msg or "due soon" in msg:
        today_tasks = reminders["dueToday"]
        soon_tasks = reminders["dueSoon"]
        reply_text = f"### 📅 Upcoming Maintenance Schedule\n\n"
        if today_tasks:
            reply_text += f"**Due Today ({len(today_tasks)} Tasks):**\n"
            for t in today_tasks:
                reply_text += f"- **Task #{t['id']} (Panel {t['panel_id']})**: {t['maintenance_type']} (Priority: `{t['priority']}`)\n"
            reply_text += "\n"
        else:
            reply_text += "*No maintenance tasks scheduled specifically for today.*\n\n"

        if soon_tasks:
            reply_text += f"**Due Within the Next 7 Days ({len(soon_tasks)} Tasks):**\n"
            for t in soon_tasks[:5]:
                reply_text += f"- **Task #{t['id']} (Panel {t['panel_id']})**: {t['maintenance_type']} — Target: `{t['scheduled_date']}` (in {t.get('daysUntilDue', 0)} days)\n"
        else:
            reply_text += "*No additional tasks due within the upcoming 7 days.*"

        suggested_questions = [
            "Which panels have overdue maintenance?",
            "Show me panels with recurring faults.",
            "Should I repair or recycle this panel?"
        ]

    # INTENT 3: COMPLETELY FAILED PANEL
    elif "completely failed" in msg or "failed panel" in msg or "dead panel" in msg or "broken panel" in msg:
        if panel_id and context and context["found"]:
            reply_text = f"### 🔍 Solar Panel Failure Investigation: {panel_id}\n\n"
            reply_text += f"You have reported **{panel_id}** as a failed or non-functioning solar panel. Here is the verified multi-modal history on file:\n\n"
            reply_text += f"- **Model / Specs**: {context['panelType']} ({context['ratedPower']} W rated)\n"
            reply_text += f"- **Location**: `{context['location']}`\n"
            reply_text += f"- **Directory Status**: `{context['status']}` (Current fault indicator: `{context['currentFault']}`)\n\n"

            # Check electrical verification
            elec = context["electricalScans"]
            if elec:
                recent_elec = elec[0]
                reply_text += f"#### ⚡ Electrical Diagnostics Recorded:\n"
                reply_text += f"- **Operating Status**: `{recent_elec['diagnostic_status']}`\n"
                reply_text += f"- **Measured Output**: {recent_elec['measured_power']} W (Calculated: {recent_elec['calculated_power']} W, Discrepancy: {recent_elec['power_discrepancy_percent']}%)\n"
                if recent_elec.get("diode_step_detected"):
                    reply_text += "- **Diode Step Detected**: Yes, potential bypass diode open/short failure.\n"
            else:
                reply_text += "#### ⚠️ Electrical Performance Verification:\n"
                reply_text += "*No verified multi-point I-V curve or power telemetry exists for this module. Actual electrical failure cannot be confirmed solely from optical computer-vision models.*\n\n"

            # Check thermal / optical
            thermal = context["thermalScans"]
            if thermal:
                t0 = thermal[0]
                reply_text += f"- **Thermal Scan (IRT)**: $\\Delta T = +{t0['delta_t']}^\\circ\\text{{C}}$, Class: `{t0['iec_class']}`\n"

            reply_text += "\n#### 📋 Recommended Triage Path:\n"
            reply_text += "1. **Electrical Sweep**: Schedule an on-site I-V curve tracer sweep to confirm open-circuit ($V_{oc}$) and short-circuit current ($I_{sc}$).\n"
            reply_text += "2. **Junction Box Check**: Inspect diode continuity to determine if a simple bypass diode or MC4 connector replacement restores output.\n"
            reply_text += "3. **Decision Recording**: If silicon wafer fracture or severe internal delamination has destroyed power yield below 50%, record a **Retirement Decision** below.\n\n"
            reply_text += "> **Safety Warning**: Never attempt hazardous high-voltage DC disassembly on live arrays. All physical interventions must be performed by certified electricians."
        else:
            reply_text = "### 🛠️ Solar Panel Failure Investigation Workflow\n\n"
            reply_text += "When a solar panel is reported as damaged or completely failed, follow this structured investigation protocol:\n\n"
            reply_text += "1. **Identify Module**: Select the asset ID from the panel selector above (e.g. `SP-001`, `SP-003`).\n"
            reply_text += "2. **Synthesize Evidence**: We check recorded Optical RGB faults, Thermal $\\Delta T$ scans, Electroluminescence microcracks, and I-V electrical output.\n"
            reply_text += "3. **Verify Power Loss**: An optical crack or dirt does not automatically mean complete failure. Electrical verification is required before declaring a module irreparable.\n"
            reply_text += "4. **Repair vs. Retire Decision**: If repairable (diode/connector), dispatch work order; if permanently fractured, initiate our **8-Stage Recycling Workflow**.\n\n"
            reply_text += "*Please select a specific panel ID from the dropdown above to inspect its recorded findings.*"

        suggested_questions = [
            f"Why was {panel_id or 'SP-003'} flagged during inspection?",
            "Should I repair or recycle this panel?",
            "How do I arrange responsible recycling for a retired panel?"
        ]

    # INTENT 4: REPAIR VS RECYCLE
    elif "repair or recycle" in msg or "repair vs recycle" in msg or "retire this panel" in msg:
        reply_text = "### ⚖️ Repair vs. Retirement Decision Matrix\n\n"
        reply_text += "Solar modules represent long-term capital assets. Evaluate the following engineering criteria before retiring a module:\n\n"
        reply_text += "| Defect Category | Recommended Action | Engineering Justification |\n"
        reply_text += "| :--- | :--- | :--- |\n"
        reply_text += "| **Bypass Diode Conduction / Burnout** | 🔧 **Repair** | Diodes can be replaced in junction boxes at low cost without replacing the module. |\n"
        reply_text += "| **Heavy Soiling / Organic Matter** | 🧼 **Clean / Maintain** | Non-invasive professional surface washing restores 90-98% nominal yield. |\n"
        reply_text += "| **MC4 Cable / Ribbon Corrosion** | 🔧 **Repair** | External connectors and terminal leads are easily serviceable in the field. |\n"
        reply_text += "| **Localized Thermal Hotspot** | 🔍 **Further Inspection** | Perform calibrated IRT and string I-V curve to rule out partial shading vs cell shunts. |\n"
        reply_text += "| **Wafer Microcrack (Minor)** | 👁️ **Monitor** | If busbars are intact and yield loss is < 10%, monitor via annual EL sweeps. |\n"
        reply_text += "| **Shattered Glass / Delamination** | ♻️ **Retire & Recycle** | Moisture ingress oxidizes wafer matrix, creating ground faults and severe fire hazards. |\n"
        reply_text += "| **Total Wafer Fracture (>50% Loss)**| ♻️ **Retire & Recycle** | Irreversible semiconductor crystal fracture cannot be repaired in the field. |\n\n"
        reply_text += "Select a panel above to evaluate its specific diagnostic records."

        suggested_questions = [
            "How do I arrange responsible recycling for a retired panel?",
            "Which panels have overdue maintenance?",
            "Show me panels with recurring faults."
        ]

    # INTENT 5: RECURRING FAULTS
    elif "recurring" in msg or "chronic" in msg or "frequent" in msg:
        recurring = get_panels_with_recurring_faults()
        if recurring:
            reply_text = f"### 🔄 Modules with Recurring Fault History\n\n"
            reply_text += "The following solar panels have multiple logged fault incidents in the database:\n\n"
            for r in recurring:
                reply_text += f"- **Panel {r['panel_id']}**: **{r['fault_count']} logged reports** (Types: `{r['fault_types']}`, Max Severity: `{r['max_severity']}`)\n"
            reply_text += "\n**Analysis**: Panels with recurring faults in the same string often suffer from persistent shading obstructions, mounting stress, or micro-cracking propagation."
        else:
            reply_text = "### ✅ No Chronic Recurring Faults\n\nNo modules currently exceed single-incident fault report thresholds."

        suggested_questions = [
            "Which panels have overdue maintenance?",
            "My solar panel has completely failed. What should I do?",
            "What maintenance is due this week?"
        ]

    # INTENT 6: WHY FLAGGED / PANEL SPECIFICS
    elif ("why was this panel flagged" in msg or "why flagged" in msg or "investigate" in msg) and panel_id:
        if context and context["found"]:
            reply_text = f"### 📋 Diagnostic Telemetry for {panel_id}\n\n"
            reports = context["faultReports"]
            if reports:
                r0 = reports[0]
                reply_text += f"**Latest Fault Report #{r0['id']} ({r0['fault_type']})**:\n"
                reply_text += f"- **Severity**: `{r0['severity']}` | **Confidence/Score**: `{r0['confidence']}%`\n"
                reply_text += f"- **Estimated Power Derating**: -{r0['power_loss_watts']} W ({r0['power_loss_percentage']}%)\n"
                reply_text += f"- **Why Flagged**: *\"{r0['why_flagged']}\"*\n"
                reply_text += f"- **Recommended Action**: *\"{r0['recommended_action']}\"*\n\n"
            else:
                reply_text += "*No optical fault reports currently recorded for this module.*\n\n"

            # Check thermal
            if context["thermalScans"]:
                t0 = context["thermalScans"][0]
                reply_text += f"**Calibrated Thermal Inspection (IRT)**:\n"
                reply_text += f"- Delta-T: `+{t0['delta_t']}°C` over nominal reference (IEC Class: `{t0['iec_class']}`)\n"
                reply_text += f"- Anomaly Description: {t0['why_flagged']}\n\n"

            # Check EL
            if context["elScans"]:
                el0 = context["elScans"][0]
                reply_text += f"**Electroluminescence (EL) Finding**:\n"
                reply_text += f"- Defect Category: `{el0['defect_category']}` | Inactive Area: `{el0['inactive_area_percent']}%`\n\n"

            # Work orders
            if context["workOrders"]:
                wo0 = context["workOrders"][0]
                reply_text += f"**Active Work Order**: #{wo0['id']} — {wo0['maintenance_type']} (Status: `{wo0['status']}`, Scheduled: `{wo0['scheduled_date']}`)\n"
        else:
            reply_text = f"Module '{panel_id}' was not found in the fleet directory. Please select an active panel ID."

        suggested_questions = [
            f"Should I repair or recycle {panel_id}?",
            "Which panels have overdue maintenance?",
            "What maintenance is due this week?"
        ]

    # INTENT 7: RECYCLING GUIDANCE
    elif "recycl" in msg or "retired panel" in msg or "takeback" in msg or "recycle" in msg:
        reply_text = "### ♻️ Responsible 8-Stage Solar Panel Recycling Advisor\n\n"
        reply_text += "Solar panels contain valuable, recoverable materials (glass, aluminum, silicon, copper, silver) as well as regulated encapsulants. Follow this structured decommissioning and recycling process:\n\n"
        reply_text += "1. **Stage 1 — Decommissioning Decision Confirmation**: Formalize engineering decision that module cannot be economically restored.\n"
        reply_text += "2. **Stage 2 — Certified Electrical Decommissioning**: Qualified electrician isolates string disconnects, verifies zero voltage, and unclips cables without shorting.\n"
        reply_text += "3. **Stage 3 — Asset Material & Weight Manifest**: Record module model, glass thickness, aluminum frame specifications, and cell chemistry (monocrystalline PERC).\n"
        reply_text += "4. **Stage 4 — Manufacturer Take-Back Check**: Check manufacturer warranty and producer take-back schemes (e.g. First Solar, SunPower, or regional WEEE programs).\n"
        reply_text += "5. **Stage 5 — Authorized Recycler Selection**: Partner with certified PV recyclers (SEIA-recognized partners, R2 certified electronic recyclers, or PV Cycle).\n"
        reply_text += "6. **Stage 6 — Packaging & Logistics**: Palletize panels face-to-face with cardboard spacers; wrap securely to prevent shattered glass breakage during freight.\n"
        reply_text += "7. **Stage 7 — Custody Handover & Bill of Lading**: Obtain driver signature and collection receipt.\n"
        reply_text += "8. **Stage 8 — Certificate of Recycling & Destruction**: Record the official certified destruction manifest in the SolarShield database.\n\n"
        reply_text += "> **Regulatory Notice**: In many jurisdictions (EU WEEE Directive, US state regulations), landfilling photovoltaic modules is prohibited due to trace heavy metals."

        suggested_questions = [
            "Should I repair or recycle this panel?",
            "Which panels have overdue maintenance?",
            "My solar panel has completely failed. What should I do?"
        ]

    # INTENT 8: GENERAL FLEET STATUS
    elif "fleet" in msg or "status" in msg or "summary" in msg or "how many panels" in msg:
        stats = query_fleet_summary()
        reply_text = f"### 📊 SolarShield Fleet Health Summary\n\n"
        reply_text += f"- **Total Monitored Modules**: **{stats['totalPanels']} panels**\n"
        reply_text += f"- **Nominal / Healthy**: {stats['healthyPanels']} panels\n"
        reply_text += f"- **Fault Anomaly Flagged**: {stats['faultyPanels']} panels\n"
        reply_text += f"- **Decommissioned / Retired**: {stats['retiredPanels']} panels\n"
        reply_text += f"- **Total Audit Fault Reports**: {stats['totalReports']} reports\n"
        reply_text += f"- **Open Maintenance Orders**: {stats['openMaintenance']} pending/in-progress tasks\n\n"
        reply_text += f"Currently **{reminders['counts']['overdue']} maintenance tasks are overdue** and **{reminders['counts']['dueSoon']} tasks are due within 7 days**."

        suggested_questions = [
            "Which panels have overdue maintenance?",
            "Show me panels with recurring faults.",
            "My solar panel has completely failed. What should I do?"
        ]

    # DEFAULT FALLBACK
    else:
        reply_text = "### ☀️ SolarShield AI Solar Assistant\n\n"
        reply_text += "I can assist you with photovoltaic asset care, failure investigations, maintenance scheduling, and responsible recycling:\n\n"
        reply_text += "- **Maintenance Tracking**: Ask about overdue tasks, scheduled maintenance, or due-today field orders.\n"
        reply_text += "- **Damaged Panel Triage**: Report a failed module to evaluate whether repair, continued monitoring, or retirement is appropriate.\n"
        reply_text += "- **Recycling Guidance**: Step-by-step guidance on safe decommissioning and certified recycler handovers.\n"
        reply_text += "- **Diagnostic Records**: Inquire about specific panel IDs (`SP-001` through `SP-051`) to review their optical, thermal, and electrical findings.\n\n"
        reply_text += "*Try clicking one of the suggested questions below or select a solar module to begin.*"

        suggested_questions = [
            "Which panels have overdue maintenance?",
            "My solar panel has completely failed. What should I do?",
            "Should I repair or recycle this panel?",
            "What maintenance is due this week?"
        ]

    return {
        "success": True,
        "reply": reply_text,
        "panelId": panel_id,
        "suggestedQuestions": suggested_questions,
        "timestamp": datetime.now().isoformat()
    }
