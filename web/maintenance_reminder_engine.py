"""
SolarShield AI Photovoltaic Monitor v2.5
Automatic Maintenance Reminder & Notification Engine
Pure software-driven due-date tracking, persistence, and reminder evaluation.
"""

import sqlite3
import threading
import time
from datetime import datetime, date, timedelta
from typing import Dict, Any, List, Optional
import os

DB_PATH = os.environ.get("DATABASE_PATH") or os.environ.get("SQLITE_DB_PATH") or os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "solarshield.db")
if not os.path.exists(DB_PATH) and os.path.exists("solarshield.db"):
    DB_PATH = "solarshield.db"

def get_db():
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    return conn

def init_maintenance_reminder_db():
    """Ensure maintenance_notifications table and required schema exist."""
    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
    CREATE TABLE IF NOT EXISTS maintenance_notifications (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        work_order_id INTEGER NOT NULL,
        panel_id TEXT NOT NULL,
        reminder_category TEXT NOT NULL, -- 'UPCOMING', 'DUE_TODAY', 'OVERDUE'
        due_date TEXT NOT NULL,
        title TEXT NOT NULL,
        message TEXT NOT NULL,
        priority TEXT NOT NULL,
        assigned_to TEXT NOT NULL,
        task_description TEXT NOT NULL,
        is_read INTEGER DEFAULT 0,
        created_at TEXT NOT NULL,
        read_at TEXT,
        FOREIGN KEY(work_order_id) REFERENCES maintenance(id),
        UNIQUE(work_order_id, reminder_category, due_date)
    )
    """)
    cur.execute("CREATE INDEX IF NOT EXISTS idx_maint_notif_wo ON maintenance_notifications(work_order_id)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_maint_notif_read ON maintenance_notifications(is_read)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_maint_notif_cat ON maintenance_notifications(reminder_category)")
    conn.commit()
    conn.close()

def evaluate_maintenance_reminders() -> Dict[str, Any]:
    """
    Evaluate all work orders against today's date.
    - Categorizes tasks: UPCOMING, DUE_TODAY, OVERDUE, COMPLETED, IN_PROGRESS, SCHEDULED.
    - Generates persistent notifications without duplicates.
    - Computes live summary metrics.
    """
    init_maintenance_reminder_db()
    conn = get_db()
    cur = conn.cursor()

    today = date.today()
    today_str = today.isoformat()

    # Fetch all work orders with joined panel details
    cur.execute("""
        SELECT m.id, m.panel_id, m.fault_report_id, m.technician,
               m.maintenance_type, m.scheduled_date, m.priority,
               m.status, m.notes, m.created_at, m.completed_at,
               p.name as panel_name, p.location
        FROM maintenance m
        LEFT JOIN solar_panels p ON m.panel_id = p.panel_id
        ORDER BY m.scheduled_date ASC
    """)
    orders = [dict(r) for r in cur.fetchall()]

    upcoming_count = 0
    due_today_count = 0
    overdue_count = 0
    completed_count = 0
    in_progress_count = 0

    for o in orders:
        raw_status = (o.get("status") or "PENDING").upper()
        sched_str = (o.get("scheduled_date") or "").strip()

        if raw_status == "COMPLETED":
            completed_count += 1
            o["computed_status"] = "COMPLETED"
            continue

        if raw_status == "CANCELLED":
            o["computed_status"] = "CANCELLED"
            continue

        if raw_status == "IN_PROGRESS":
            in_progress_count += 1

        try:
            sched_date = datetime.strptime(sched_str[:10], "%Y-%m-%d").date()
            diff = (sched_date - today).days
            o["days_until_due"] = diff

            if diff < 0:
                overdue_count += 1
                cat = "OVERDUE"
                o["computed_status"] = "OVERDUE"
                title = f"Overdue Maintenance - {o['panel_id']}"
                msg = f"Maintenance for panel {o['panel_id']} is overdue by {abs(diff)} day(s). Scheduled date was {sched_date}. Please review work order WO-{o['id']} and update its status."
            elif diff == 0:
                due_today_count += 1
                cat = "DUE_TODAY"
                o["computed_status"] = "DUE_TODAY"
                title = f"Due Today - {o['panel_id']}"
                msg = f"Work order WO-{o['id']} for panel {o['panel_id']} is scheduled for today ({sched_date}). Immediate field dispatch recommended."
            elif diff == 1:
                upcoming_count += 1
                cat = "UPCOMING"
                o["computed_status"] = "DUE_SOON"
                title = f"Upcoming Maintenance - {o['panel_id']}"
                msg = f"Work order WO-{o['id']} for panel {o['panel_id']} is due tomorrow ({sched_date}). Prepare technician and equipment."
            elif diff > 1:
                upcoming_count += 1
                o["computed_status"] = "DUE_SOON" if diff <= 7 else "SCHEDULED"
                # Generate upcoming reminder if within 14 days
                if diff <= 14:
                    cat = "UPCOMING"
                    title = f"Upcoming Maintenance - {o['panel_id']}"
                    msg = f"Work order WO-{o['id']} for panel {o['panel_id']} is approaching its scheduled due date ({sched_date}, in {diff} days)."
                else:
                    cat = None
            else:
                cat = None
                o["computed_status"] = "SCHEDULED"

            # Persist notification if category applies
            if cat:
                cur.execute("""
                    INSERT OR IGNORE INTO maintenance_notifications
                    (work_order_id, panel_id, reminder_category, due_date, title, message, priority, assigned_to, task_description, is_read, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
                """, (
                    o["id"],
                    o["panel_id"],
                    cat,
                    sched_str,
                    title,
                    msg,
                    o.get("priority") or "HIGH",
                    o.get("technician") or "Field Service Specialist",
                    o.get("maintenance_type") or "General Remediation",
                    datetime.now().isoformat()
                ))

        except Exception as e:
            o["computed_status"] = "SCHEDULED"
            o["days_until_due"] = 999

    conn.commit()

    # Get unread notifications count
    cur.execute("SELECT COUNT(*) FROM maintenance_notifications WHERE is_read = 0")
    unread_count = cur.fetchone()[0]

    # Fetch all stored notifications joined with work order status
    cur.execute("""
        SELECT n.*, m.status as work_order_status, m.technician as current_technician, m.maintenance_type as current_maintenance_type
        FROM maintenance_notifications n
        LEFT JOIN maintenance m ON n.work_order_id = m.id
        ORDER BY n.id DESC
    """)
    notifications = []
    for raw in cur.fetchall():
        d = dict(raw)
        d["technician"] = d.get("assigned_to") or d.get("current_technician") or "Field Service Specialist"
        d["maintenance_type"] = d.get("task_description") or d.get("current_maintenance_type") or "General Remediation"
        notifications.append(d)
    conn.close()

    summary = {
        "upcoming": upcoming_count,
        "due_today": due_today_count,
        "overdue": overdue_count,
        "completed": completed_count,
        "in_progress": in_progress_count,
        "total_orders": len(orders),
        "unread_notifications": unread_count,
        "total_notifications": len(notifications),
        "reference_date": today_str
    }

    return {
        "success": True,
        "summary": summary,
        "notifications": notifications,
        "orders": orders
    }

def get_maintenance_notifications(category: Optional[str] = None, unread_only: bool = False) -> Dict[str, Any]:
    """Retrieve notifications with optional category and unread filtering."""
    data = evaluate_maintenance_reminders()
    notifs = data["notifications"]

    if category and category.upper() not in ("ALL", ""):
        target_cat = category.upper()
        notifs = [n for n in notifs if n.get("reminder_category") == target_cat]

    if unread_only:
        notifs = [n for n in notifs if n.get("is_read") == 0]

    return {
        "success": True,
        "summary": data["summary"],
        "data": notifs,
        "notifications": notifs,
        "count": len(notifs)
    }

def mark_notification_as_read(notification_id: Optional[int] = None, mark_all: bool = False, is_read: int = 1) -> Dict[str, Any]:
    """
    Mark one or all notifications as read or unread.
    CRITICAL: Does NOT change the status of the underlying maintenance order.
    """
    conn = get_db()
    cur = conn.cursor()
    now_str = datetime.now().isoformat() if is_read else None

    if mark_all:
        cur.execute("UPDATE maintenance_notifications SET is_read = ?, read_at = ? WHERE is_read != ?", (is_read, now_str, is_read))
        affected = cur.rowcount
    elif notification_id is not None:
        cur.execute("UPDATE maintenance_notifications SET is_read = ?, read_at = ? WHERE id = ?", (is_read, now_str, notification_id))
        affected = cur.rowcount
    else:
        conn.close()
        return {"success": False, "message": "Notification ID or all=True required"}

    conn.commit()

    cur.execute("SELECT COUNT(*) FROM maintenance_notifications WHERE is_read = 0")
    remaining_unread = cur.fetchone()[0]
    conn.close()

    return {
        "success": True,
        "affected": affected,
        "unread_notifications": remaining_unread
    }

def update_work_order(
    work_order_id: int,
    status: Optional[str] = None,
    scheduled_date: Optional[str] = None,
    technician: Optional[str] = None,
    maintenance_type: Optional[str] = None,
    priority: Optional[str] = None,
    notes: Optional[str] = None
) -> Dict[str, Any]:
    """
    Update work order details and dynamically recalculate reminders.
    If marked COMPLETED, records completed_at and ceases future reminders.
    If marked CANCELLED, ceases future reminders.
    If scheduled_date is changed, recalculates reminder schedule.
    """
    conn = get_db()
    cur = conn.cursor()

    cur.execute("SELECT * FROM maintenance WHERE id = ?", (work_order_id,))
    row = cur.fetchone()
    if not row:
        conn.close()
        return {"success": False, "message": f"Work order WO-{work_order_id} not found."}

    current = dict(row)
    new_status = (status or current.get("status") or "PENDING").upper()
    new_date = (scheduled_date or current.get("scheduled_date") or "").strip()
    new_tech = technician if technician is not None else current.get("technician")
    new_type = maintenance_type if maintenance_type is not None else current.get("maintenance_type")
    new_prio = priority if priority is not None else current.get("priority")
    new_notes = notes if notes is not None else current.get("notes")

    completed_at = current.get("completed_at")
    if new_status == "COMPLETED" and not completed_at:
        completed_at = datetime.now().isoformat()
    elif new_status != "COMPLETED":
        completed_at = None

    cur.execute("""
        UPDATE maintenance
        SET status = ?, scheduled_date = ?, technician = ?, maintenance_type = ?, priority = ?, notes = ?, completed_at = ?
        WHERE id = ?
    """, (new_status, new_date, new_tech, new_type, new_prio, new_notes, completed_at, work_order_id))
    conn.commit()
    conn.close()

    # Recalculate reminders
    evaluate_maintenance_reminders()

    return {
        "success": True,
        "message": f"Work order WO-{work_order_id} updated successfully.",
        "work_order": {
            "id": work_order_id,
            "status": new_status,
            "scheduled_date": new_date,
            "technician": new_tech,
            "completed_at": completed_at
        }
    }

# Background thread daemon
_scheduler_running = False
_scheduler_thread = None

def _reminder_worker(interval: int = 60):
    global _scheduler_running
    while _scheduler_running:
        try:
            evaluate_maintenance_reminders()
        except Exception as e:
            print(f"[MaintenanceReminderEngine] Background error: {e}")
        time.sleep(interval)

def start_reminder_scheduler(interval_seconds: int = 60):
    """Start background daemon for periodic automated reminder generation."""
    global _scheduler_running, _scheduler_thread
    if _scheduler_running:
        return
    _scheduler_running = True
    _scheduler_thread = threading.Thread(target=_reminder_worker, args=(interval_seconds,), daemon=True, name="MaintenanceReminderScheduler")
    _scheduler_thread.start()
    print(f"[MaintenanceReminderEngine] Automated scheduler active (checks every {interval_seconds}s).")
