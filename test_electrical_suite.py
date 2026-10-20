"""
SolarShield Electrical Fault Diagnosis Automated Test Suite
============================================================
Comprehensive test suite validating all 9 Acceptance Criteria:
1. Valid manual measurements & power calculation (P = V x I)
2. Missing and invalid values handling & field validation
3. Valid and malformed CSV files (rejection reporting & non-silent errors)
4. Insufficient data for an I–V curve (< 5 points warning)
5. Power calculation, measured power comparison & units distinction
6. Abnormal readings with documented test cases:
   - Bypass diode step conduction
   - Soiling current loss (current depression)
   - High series resistance (Rs) & reduced Fill Factor
   - Low shunt resistance (Rsh) / PID leakage slope
   - Open-circuit isolation fault
   - Needs Review status on incomplete environmental telemetry
7. Database persistence and retrieval (SQLite electrical_inspections & work orders)
8. Authentication and authorization verification
9. Regression tests for IRT, EL Inspection, and core SolarShield modules
"""

import sys
import os
import json
import time
import urllib.request
import urllib.error
import sqlite3

BASE_URL = "http://localhost:3000"
DB_PATH = "solarshield.db"

def make_request(path, method="GET", data=None, headers=None):
    url = f"{BASE_URL}{path}"
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)
        
    encoded_data = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=encoded_data, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = resp.read().decode("utf-8")
            status = resp.status
            try:
                parsed = json.loads(body)
            except Exception:
                parsed = body
            return status, parsed
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        try:
            parsed = json.loads(body)
        except Exception:
            parsed = body
        return e.code, parsed
    except Exception as e:
        return 0, str(e)


def run_tests():
    print("=" * 75)
    print(" SOLARSHIELD ELECTRICAL FAULT DIAGNOSIS ACCEPTANCE TEST SUITE")
    print(f" Target Server: {BASE_URL}")
    print(f" Target Database: {DB_PATH}")
    print("=" * 75)
    
    results = {}

    # --------------------------------------------------------------------------
    # Test 1: Valid Manual Measurements & Power Calculation (P = V x I)
    # --------------------------------------------------------------------------
    print("\n[TEST 1] Valid Manual Measurements & Power Calculation (P = V x I)...")
    payload1 = {
        "panelId": "SP-001",
        "source": "HANDHELD_MULTIMETER",
        "voltage": 41.5,
        "current": 10.12,
        "irradiance": 1000.0,
        "panelTemp": 25.0,
        "ambientTemp": 22.0,
        "notes": "Field handheld test"
    }
    status1, resp1 = make_request("/api/electrical/inspect", method="POST", data=payload1)
    data1 = resp1.get("data") if (isinstance(resp1, dict) and resp1.get("data") is not None) else resp1
    calc_power = data1.get("calculatedPower")
    expected_p = round(41.5 * 10.12, 2)
    
    t1_pass = (
        status1 == 200 and
        resp1.get("success") is True and
        calc_power is not None and
        abs(calc_power - expected_p) < 0.1 and
        data1.get("diagnosticStatus") == "NOMINAL_OPERATION" and
        data1.get("voltage") == 41.5 and
        data1.get("current") == 10.12 and
        data1.get("anomalyScore") <= 15.0 and
        "Rule-Based Anomaly Index" in data1.get("scoreType", "")
    )
    results["Test 1: Valid Manual Measurements & P = V x I"] = {
        "passed": t1_pass,
        "details": f"Status: {status1}, Calculated Power: {calc_power} W (expected {expected_p} W), Status: {data1.get('diagnosticStatus')}"
    }
    print(f"  Result: {'PASS' if t1_pass else 'FAIL'} -> {results['Test 1: Valid Manual Measurements & P = V x I']['details']}")

    # --------------------------------------------------------------------------
    # Test 2: Missing and Invalid Values Validation
    # --------------------------------------------------------------------------
    print("\n[TEST 2] Missing and Invalid Values Handling...")
    # 2A: Missing current
    status2a, resp2a = make_request("/api/electrical/inspect", method="POST", data={"panelId": "SP-001", "voltage": 40.0})
    # 2B: Missing voltage
    status2b, resp2b = make_request("/api/electrical/inspect", method="POST", data={"panelId": "SP-001", "current": 10.0})
    # 2C: Missing panelId
    status2c, resp2c = make_request("/api/electrical/inspect", method="POST", data={"voltage": 40.0, "current": 10.0, "panelId": ""})
    # 2D: Extreme negative voltage (out of physical bounds)
    status2d, resp2d = make_request("/api/electrical/inspect", method="POST", data={"panelId": "SP-001", "voltage": -100.0, "current": 10.0})

    t2_pass = (
        status2a == 400 and
        status2b == 400 and
        status2c == 400 and
        status2d == 400
    )
    results["Test 2: Missing and Invalid Values Validation"] = {
        "passed": t2_pass,
        "details": f"Missing I: HTTP {status2a}, Missing V: HTTP {status2b}, Missing PanelID: HTTP {status2c}, Out of bounds: HTTP {status2d}"
    }
    print(f"  Result: {'PASS' if t2_pass else 'FAIL'} -> {results['Test 2: Missing and Invalid Values Validation']['details']}")

    # --------------------------------------------------------------------------
    # Test 3: Valid and Malformed CSV Upload & Row Rejection Reporting
    # --------------------------------------------------------------------------
    print("\n[TEST 3] Valid and Malformed CSV Files (Non-Silent Rejections)...")
    corrupt_csv_content = """# Corrupted instrument dump
voltage,current,irradiance,temperature
41.5,10.2,1000,25
INVALID_NUM,8.5,1000,25
35.0,-99.9,1000,25
38.0,9.1,1000,25
"""
    status3, resp3 = make_request("/api/electrical/import/csv", method="POST", data={
        "csvContent": corrupt_csv_content,
        "panelId": "SP-002",
        "fileName": "corrupted_test.csv"
    })
    
    valid_cnt = resp3.get("validRowsCount", resp3.get("validCount", 0)) if isinstance(resp3, dict) else 0
    rej_cnt = resp3.get("rejectedRowsCount", resp3.get("rejectedCount", 0)) if isinstance(resp3, dict) else 0
    rej_list = resp3.get("rejectedRows", []) if isinstance(resp3, dict) else []

    t3_pass = (
        status3 == 200 and
        resp3.get("success") is True and
        valid_cnt == 2 and
        rej_cnt == 2 and
        len(rej_list) == 2
    )
    results["Test 3: CSV Validation & Rejected Row Reporting"] = {
        "passed": t3_pass,
        "details": f"Status: {status3}, Valid: {valid_cnt}, Rejected: {rej_cnt}, Rows: {[r.get('reason') for r in rej_list]}"
    }
    print(f"  Result: {'PASS' if t3_pass else 'FAIL'} -> {results['Test 3: CSV Validation & Rejected Row Reporting']['details']}")

    # --------------------------------------------------------------------------
    # Test 4: Insufficient Data for I–V Curve (< 5 Points Warning)
    # --------------------------------------------------------------------------
    print("\n[TEST 4] Insufficient Data for an I–V Curve (< 5 points)...")
    sparse_csv = """voltage,current
40.0,9.5
41.0,8.0
42.0,5.0
"""
    status4, resp4 = make_request("/api/electrical/import/csv", method="POST", data={
        "csvContent": sparse_csv,
        "panelId": "SP-003",
        "fileName": "sparse_points.csv"
    })
    data4 = resp4.get("data") if (isinstance(resp4, dict) and resp4.get("data") is not None) else (resp4.get("diagnosticResult") or resp4)
    warnings4 = data4.get("dataQualityWarnings", [])
    has_sparse_warning = any("insufficient data points" in w.lower() for w in warnings4)

    t4_pass = (
        status4 == 200 and
        resp4.get("success") is True and
        data4.get("hasIvCurve") is False and
        has_sparse_warning
    )
    results["Test 4: Insufficient Data for I–V Curve (< 5 pts)"] = {
        "passed": t4_pass,
        "details": f"Status: {status4}, hasIvCurve: {data4.get('hasIvCurve')}, Warnings: {warnings4}"
    }
    print(f"  Result: {'PASS' if t4_pass else 'FAIL'} -> {results['Test 4: Insufficient Data for I–V Curve (< 5 pts)']['details']}")

    # --------------------------------------------------------------------------
    # Test 5: Power Calculation & Measured vs Calculated Power Distinction
    # --------------------------------------------------------------------------
    print("\n[TEST 5] Power Calculation & Measured vs Calculated Power Distinction...")
    payload5 = {
        "panelId": "SP-001",
        "voltage": 40.0,
        "current": 10.0,
        "measuredPower": 380.0,  # Deliberate discrepancy: meter reads 380W, calculated is 400W
        "irradiance": 1000.0,
        "panelTemp": 25.0
    }
    status5, resp5 = make_request("/api/electrical/inspect", method="POST", data=payload5)
    data5 = resp5.get("data") if (isinstance(resp5, dict) and resp5.get("data") is not None) else resp5
    calc_p5 = data5.get("calculatedPower")
    meas_p5 = data5.get("measuredPower")
    disc5 = data5.get("powerDiscrepancyPercent")

    t5_pass = (
        status5 == 200 and
        calc_p5 == 400.0 and
        meas_p5 == 380.0 and
        abs(disc5 - (-5.0)) < 0.2 and
        "evidence" in data5
    )
    results["Test 5: Power Calculation & Discrepancy Distinction"] = {
        "passed": t5_pass,
        "details": f"Status: {status5}, Calc: {calc_p5}W, Measured: {meas_p5}W, Discrepancy: {disc5}%"
    }
    print(f"  Result: {'PASS' if t5_pass else 'FAIL'} -> {results['Test 5: Power Calculation & Discrepancy Distinction']['details']}")

    # --------------------------------------------------------------------------
    # Test 6: Abnormal Readings with Documented Test Cases
    # --------------------------------------------------------------------------
    print("\n[TEST 6] Abnormal Readings Diagnoses across Documented Scenarios...")
    
    # 6A: Bypass Diode Conduction (Calibrated sample)
    status6a, resp6a = make_request("/api/electrical/inspect", method="POST", data={
        "sampleName": "electrical_iv_bypass_diode.csv",
        "panelId": "SP-001"
    })
    data6a = resp6a.get("data") if (isinstance(resp6a, dict) and resp6a.get("data") is not None) else resp6a
    t6a_pass = (
        status6a == 200 and
        data6a.get("diagnosticStatus") == "BYPASS_DIODE_CONDUCTION" and
        data6a.get("diodeStepDetected") is True and
        data6a.get("severity") in ("HIGH", "CRITICAL") and
        data6a.get("vmp") < 32.0
    )

    # 6B: Soiling Current Depression (Calibrated sample)
    status6b, resp6b = make_request("/api/electrical/inspect", method="POST", data={
        "sampleName": "electrical_iv_soiling_current_loss.csv",
        "panelId": "SP-001"
    })
    data6b = resp6b.get("data") if (isinstance(resp6b, dict) and resp6b.get("data") is not None) else resp6b
    t6b_pass = (
        status6b == 200 and
        data6b.get("diagnosticStatus") == "CURRENT_DEPRESSION_SOILING" and
        data6b.get("severity") in ("MEDIUM", "HIGH")
    )

    # 6C: High Series Resistance (Calibrated sample)
    status6c, resp6c = make_request("/api/electrical/inspect", method="POST", data={
        "sampleName": "electrical_iv_high_rs.csv",
        "panelId": "SP-001"
    })
    data6c = resp6c.get("data") if (isinstance(resp6c, dict) and resp6c.get("data") is not None) else resp6c
    t6c_pass = (
        status6c == 200 and
        data6c.get("diagnosticStatus") == "HIGH_SERIES_RESISTANCE" and
        data6c.get("fillFactor") < 0.70
    )

    # 6D: Low Shunt Resistance / PID (Calibrated sample)
    status6d, resp6d = make_request("/api/electrical/inspect", method="POST", data={
        "sampleName": "electrical_iv_low_rsh_pid.csv",
        "panelId": "SP-001"
    })
    data6d = resp6d.get("data") if (isinstance(resp6d, dict) and resp6d.get("data") is not None) else resp6d
    t6d_pass = (
        status6d == 200 and
        data6d.get("diagnosticStatus") == "LOW_SHUNT_RESISTANCE" and
        data6d.get("fillFactor") < 0.68
    )

    # 6E: Open Circuit / Isolation Fault (Manual entry: V=48V, I=0.01A)
    status6e, resp6e = make_request("/api/electrical/inspect", method="POST", data={
        "panelId": "SP-001",
        "voltage": 48.0,
        "current": 0.01,
        "irradiance": 900.0,
        "panelTemp": 25.0
    })
    data6e = resp6e.get("data") if (isinstance(resp6e, dict) and resp6e.get("data") is not None) else resp6e
    t6e_pass = (
        status6e == 200 and
        data6e.get("diagnosticStatus") == "OPEN_CIRCUIT_ISOLATION" and
        data6e.get("severity") == "CRITICAL"
    )

    # 6F: Needs Review (Manual entry with missing environmental telemetry)
    status6f, resp6f = make_request("/api/electrical/inspect", method="POST", data={
        "panelId": "SP-001",
        "voltage": 38.0,
        "current": 9.2
    })
    data6f = resp6f.get("data") if (isinstance(resp6f, dict) and resp6f.get("data") is not None) else resp6f
    t6f_pass = (
        status6f == 200 and
        data6f.get("needsReview") is True and
        len(data6f.get("dataQualityWarnings", [])) > 0
    )

    t6_all_pass = t6a_pass and t6b_pass and t6c_pass and t6d_pass and t6e_pass and t6f_pass
    results["Test 6: Abnormal Readings with Documented Test Cases"] = {
        "passed": t6_all_pass,
        "details": f"Diode: {t6a_pass}, Soiling: {t6b_pass}, High Rs: {t6c_pass}, Low Rsh: {t6d_pass}, Open: {t6e_pass}, Review: {t6f_pass}"
    }
    print(f"  Result: {'PASS' if t6_all_pass else 'FAIL'} -> {results['Test 6: Abnormal Readings with Documented Test Cases']['details']}")

    # --------------------------------------------------------------------------
    # Test 7: Database Persistence, Retrieval, CSV Export & Work Order Dispatch
    # --------------------------------------------------------------------------
    print("\n[TEST 7] Database Persistence & History Retrieval...")
    status7_hist, resp7_hist = make_request("/api/electrical/history?limit=10")
    hist_records = resp7_hist.get("data", []) if isinstance(resp7_hist, dict) else []
    
    # Verify export CSV endpoint
    status7_csv, resp7_csv = make_request("/api/electrical/export/csv")
    csv_has_headers = isinstance(resp7_csv, str) and "InspectionID" in resp7_csv and "CalculatedPower_W" in resp7_csv

    # Verify Work Order creation
    latest_id = hist_records[0].get("id") if hist_records else None
    wo_status, wo_resp = make_request("/api/electrical/workorder", method="POST", data={
        "electricalInspectionId": latest_id,
        "panelId": "SP-001",
        "technician": "Solar Specialist",
        "maintenanceType": "Diode Remediation",
        "priority": "HIGH",
        "scheduledDate": "2026-10-12",
        "notes": "Automated test order"
    })
    
    # Direct DB verification
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM electrical_inspections")
    db_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM maintenance WHERE electrical_inspection_id IS NOT NULL")
    maint_count = cur.fetchone()[0]
    conn.close()

    t7_pass = (
        status7_hist == 200 and
        len(hist_records) > 0 and
        status7_csv == 200 and
        csv_has_headers and
        wo_status == 200 and
        db_count > 0 and
        maint_count > 0
    )
    results["Test 7: Database Persistence, History & Work Orders"] = {
        "passed": t7_pass,
        "details": f"Hist Status: {status7_hist}, Count: {len(hist_records)}, CSV Status: {status7_csv}, DB Inspections: {db_count}, Maintenance Linked: {maint_count}"
    }
    print(f"  Result: {'PASS' if t7_pass else 'FAIL'} -> {results['Test 7: Database Persistence, History & Work Orders']['details']}")

    # --------------------------------------------------------------------------
    # Test 8: Authentication and Access Verification
    # --------------------------------------------------------------------------
    print("\n[TEST 8] Authentication Verification...")
    # Unauthorized test
    unauth_status, unauth_resp = make_request("/api/auth/me", headers={"Authorization": "Bearer fake_token_123"})
    
    login_status, login_resp = make_request("/api/auth/login", method="POST", data={
        "email": "admin@solarshield.com",
        "password": "admin123"
    })
    auth_token = login_resp.get("token") if isinstance(login_resp, dict) else None

    # Authorized test
    me_status, me_resp = make_request("/api/auth/me", headers={"Authorization": f"Bearer {auth_token}"}) if auth_token else (0, {})

    t8_pass = (
        unauth_status == 401 and
        login_status == 200 and
        auth_token is not None and
        me_status == 200 and
        me_resp.get("success") is True
    )
    results["Test 8: Authentication & Session Access"] = {
        "passed": t8_pass,
        "details": f"Unauth 401: {unauth_status}, Login: {login_status}, Token: {'Valid' if auth_token else 'Missing'}, Auth Me: {me_status}"
    }
    print(f"  Result: {'PASS' if t8_pass else 'FAIL'} -> {results['Test 8: Authentication & Session Access']['details']}")

    # --------------------------------------------------------------------------
    # Test 9: Regression Tests for IRT, EL Inspection, and Existing Modules
    # --------------------------------------------------------------------------
    print("\n[TEST 9] Regression Tests for IRT, EL Inspection & SolarShield Modules...")
    # 9A: IRT Thermal Samples & History
    s_irt, r_irt = make_request("/api/thermal/samples")
    s_irt_h, r_irt_h = make_request("/api/thermal/history")

    # 9B: EL Samples & History
    s_el, r_el = make_request("/api/el/samples")
    s_el_h, r_el_h = make_request("/api/el/history")

    # 9C: Fleet Dashboard Stats & Panels
    s_stats, r_stats = make_request("/api/stats")
    s_panels, r_panels = make_request("/api/panels")

    # 9D: System Health
    s_health, r_health = make_request("/api/system/health")

    t9_pass = (
        s_irt == 200 and r_irt.get("success") is True and
        s_irt_h == 200 and
        s_el == 200 and r_el.get("success") is True and
        s_el_h == 200 and
        s_stats == 200 and r_stats.get("success") is True and
        s_panels == 200 and r_panels.get("success") is True and
        s_health == 200
    )
    results["Test 9: Regression Tests for IRT, EL & Core SolarShield"] = {
        "passed": t9_pass,
        "details": f"IRT: {s_irt}, EL: {s_el}, Stats: {s_stats}, Panels: {s_panels}, Health: {s_health}"
    }
    print(f"  Result: {'PASS' if t9_pass else 'FAIL'} -> {results['Test 9: Regression Tests for IRT, EL & Core SolarShield']['details']}")

    # --------------------------------------------------------------------------
    # SUMMARY REPORT
    # --------------------------------------------------------------------------
    print("\n" + "=" * 75)
    print(" ACCEPTANCE TEST SUITE SUMMARY")
    print("=" * 75)
    total = len(results)
    passed_count = sum(1 for v in results.values() if v["passed"])
    failed_count = total - passed_count

    for name, outcome in results.items():
        status_tag = "PASS" if outcome["passed"] else "FAIL"
        print(f"[{status_tag}] {name}")
        print(f"       {outcome['details']}")

    print("-" * 75)
    print(f"Total Tests: {total} | Passed: {passed_count} | Failed: {failed_count}")
    print(f"Overall Result: {'ALL TESTS PASSED' if failed_count == 0 else 'TEST FAILURES ENCOUNTERED'}")
    print("=" * 75)

    return failed_count == 0

if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)
