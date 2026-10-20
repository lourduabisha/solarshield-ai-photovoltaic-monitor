"""
SolarShield EL Electroluminescence Inspection Automated Test Suite
===================================================================
Tests all 10 Acceptance Criteria specified in the project requirements:
1. Valid EL image (nominal silicon cell)
2. Valid EL image with visible microcrack or inactive region
3. Normal RGB photograph (modality mismatch detection & rejection)
4. Unsupported or corrupted file handling
5. Oversized image (> 15 MB rejection)
6. Missing panel ID or required metadata validation
7. Analysis failure / graceful pipeline error handling
8. Saving and retrieving inspection history (SQLite persistence)
9. Authentication and authorization verification
10. Regression tests for IRT and other existing SolarShield modules
"""

import sys
import os
import json
import base64
import io
import time
import urllib.request
import urllib.error
import sqlite3
import numpy as np
from PIL import Image

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
    print("=" * 70)
    print(" SOLARSHIELD EL MODULE ACCEPTANCE TEST SUITE")
    print(f" Testing Live Server Target: {BASE_URL}")
    print("=" * 70)
    
    results = {}
    
    # --------------------------------------------------------------------------
    # Test 1: Valid EL Image (Nominal Cell)
    # --------------------------------------------------------------------------
    print("\n[TEST 1] Valid EL Image (Nominal Silicon Cell)...")
    payload1 = {
        "sampleName": "el_nominal_cell.png",
        "panelId": "SP-001",
        "confirmedModality": True,
        "sensitivity": "STANDARD"
    }
    status1, resp1 = make_request("/api/el/inspect", method="POST", data=payload1)
    t1_pass = (
        status1 == 200 and
        resp1.get("success") is True and
        resp1.get("defectCategory") == "UNIFORM_NOMINAL_EMISSION" and
        resp1.get("severity") == "LOW" and
        resp1.get("anomalyScore") <= 15.0 and
        "annotatedImagePath" in resp1
    )
    results["Test 1: Valid Nominal EL Image"] = {
        "passed": t1_pass,
        "details": f"Status: {status1}, Category: {resp1.get('defectCategory')}, Score: {resp1.get('anomalyScore')}, Severity: {resp1.get('severity')}"
    }
    print(f"  Result: {'PASS' if t1_pass else 'FAIL'} -> {results['Test 1: Valid Nominal EL Image']['details']}")

    # --------------------------------------------------------------------------
    # Test 2: Valid EL Image containing visible crack or inactive region
    # --------------------------------------------------------------------------
    print("\n[TEST 2] Valid EL Image with Visible Microcrack & Inactive Region...")
    # Test 2A: Microcrack
    payload2a = {
        "sampleName": "el_microcrack_dendritic.png",
        "panelId": "SP-002",
        "confirmedModality": True,
        "sensitivity": "STANDARD"
    }
    status2a, resp2a = make_request("/api/el/inspect", method="POST", data=payload2a)
    
    # Test 2B: Disconnected Inactive Region
    payload2b = {
        "sampleName": "el_isolated_inactive_region.png",
        "panelId": "SP-004",
        "confirmedModality": True,
        "sensitivity": "STANDARD"
    }
    status2b, resp2b = make_request("/api/el/inspect", method="POST", data=payload2b)
    
    t2_pass = (
        status2a == 200 and resp2a.get("defectCategory") == "MICROCRACK" and resp2a.get("severity") in ("HIGH", "MEDIUM") and
        status2b == 200 and resp2b.get("defectCategory") == "DISCONNECTED_CELL_REGION" and resp2b.get("severity") == "CRITICAL" and
        len(resp2a.get("regions", [])) > 0 and len(resp2b.get("regions", [])) > 0
    )
    results["Test 2: Defect Detection (Microcrack & Inactive Region)"] = {
        "passed": t2_pass,
        "details": f"Crack Sample: {resp2a.get('defectCategory')} (Score: {resp2a.get('anomalyScore')}, Bounding Regions: {len(resp2a.get('regions', []))}) | Inactive Sample: {resp2b.get('defectCategory')} (Score: {resp2b.get('anomalyScore')}, Inactive %: {resp2b.get('inactiveAreaPercent')}%)"
    }
    print(f"  Result: {'PASS' if t2_pass else 'FAIL'} -> {results['Test 2: Defect Detection (Microcrack & Inactive Region)']['details']}")

    # --------------------------------------------------------------------------
    # Test 3: Normal RGB Photograph (Modality Mismatch)
    # --------------------------------------------------------------------------
    print("\n[TEST 3] Normal RGB Photograph (Modality Mismatch Check)...")
    # Load RGB image from sample_images/2_hotspot_panel.jpg and send as base64 without confirmedModality
    with open("sample_images/2_hotspot_panel.jpg", "rb") as f:
        rgb_b64 = "data:image/jpeg;base64," + base64.b64encode(f.read()).decode("utf-8")
        
    payload3 = {
        "base64Image": rgb_b64,
        "panelId": "SP-003",
        "confirmedModality": False
    }
    status3, resp3 = make_request("/api/el/inspect", method="POST", data=payload3)
    t3_pass = (
        status3 == 200 and
        resp3.get("success") is False and
        resp3.get("isModalityMismatch") is True and
        "Modality Mismatch Warning" in resp3.get("message", "")
    )
    results["Test 3: Normal RGB Photo Rejection"] = {
        "passed": t3_pass,
        "details": f"Status: {status3}, Mismatch Flag: {resp3.get('isModalityMismatch')}, Message: '{resp3.get('message', '')[:65]}...'"
    }
    print(f"  Result: {'PASS' if t3_pass else 'FAIL'} -> {results['Test 3: Normal RGB Photo Rejection']['details']}")

    # --------------------------------------------------------------------------
    # Test 4: Unsupported or Corrupted File
    # --------------------------------------------------------------------------
    print("\n[TEST 4] Unsupported or Corrupted File Handling...")
    corrupt_b64 = "data:image/png;base64," + base64.b64encode(b"NOT_A_VALID_IMAGE_DATA_RANDOM_CORRUPTION_BYTES").decode("utf-8")
    payload4 = {
        "base64Image": corrupt_b64,
        "panelId": "SP-001"
    }
    status4, resp4 = make_request("/api/el/inspect", method="POST", data=payload4)
    t4_pass = (
        status4 == 400 and
        resp4.get("success") is False and
        "Invalid EL image upload" in resp4.get("message", "")
    )
    results["Test 4: Corrupted File Rejection"] = {
        "passed": t4_pass,
        "details": f"Status: {status4}, Error Message: '{resp4.get('message')}'"
    }
    print(f"  Result: {'PASS' if t4_pass else 'FAIL'} -> {results['Test 4: Corrupted File Rejection']['details']}")

    # --------------------------------------------------------------------------
    # Test 5: Oversized Image Rejection (> 15 MB)
    # --------------------------------------------------------------------------
    print("\n[TEST 5] Oversized Image Rejection (> 15 MB)...")
    # Simulate an oversized payload by passing 15MB + 1KB of dummy bytes
    oversized_bytes = b"0" * (15 * 1024 * 1024 + 1024)
    oversized_b64 = "data:image/png;base64," + base64.b64encode(oversized_bytes).decode("utf-8")
    payload5 = {
        "base64Image": oversized_b64,
        "panelId": "SP-001"
    }
    status5, resp5 = make_request("/api/el/inspect", method="POST", data=payload5)
    t5_pass = (
        status5 == 400 and
        resp5.get("success") is False and
        ("exceeds" in resp5.get("message", "").lower() or "15 mb" in resp5.get("message", "").lower())
    )
    results["Test 5: Oversized Image Rejection"] = {
        "passed": t5_pass,
        "details": f"Status: {status5}, Error Message: '{resp5.get('message')}'"
    }
    print(f"  Result: {'PASS' if t5_pass else 'FAIL'} -> {results['Test 5: Oversized Image Rejection']['details']}")

    # --------------------------------------------------------------------------
    # Test 6: Missing Panel ID or Required Metadata
    # --------------------------------------------------------------------------
    print("\n[TEST 6] Missing Panel ID or Required Metadata...")
    payload6 = {
        "sampleName": "el_nominal_cell.png",
        "panelId": ""  # Empty panel ID
    }
    status6, resp6 = make_request("/api/el/inspect", method="POST", data=payload6)
    t6_pass = (
        status6 == 400 and
        resp6.get("success") is False and
        "Panel ID is required" in resp6.get("message", "")
    )
    results["Test 6: Missing Panel ID Validation"] = {
        "passed": t6_pass,
        "details": f"Status: {status6}, Error Message: '{resp6.get('message')}'"
    }
    print(f"  Result: {'PASS' if t6_pass else 'FAIL'} -> {results['Test 6: Missing Panel ID Validation']['details']}")

    # --------------------------------------------------------------------------
    # Test 7: Analysis Failure Handling
    # --------------------------------------------------------------------------
    print("\n[TEST 7] Analysis Failure Graceful Handling...")
    # Missing image target entirely
    payload7 = {
        "panelId": "SP-001"
    }
    status7, resp7 = make_request("/api/el/inspect", method="POST", data=payload7)
    
    # Non-existent sample
    payload7b = {
        "sampleName": "non_existent_fake_sample_999.png",
        "panelId": "SP-001"
    }
    status7b, resp7b = make_request("/api/el/inspect", method="POST", data=payload7b)
    
    t7_pass = (
        status7 == 400 and resp7.get("success") is False and
        status7b == 404 and resp7b.get("success") is False
    )
    results["Test 7: Graceful Failure Handling"] = {
        "passed": t7_pass,
        "details": f"No image provided: {status7} ('{resp7.get('message')}') | Missing sample frame: {status7b} ('{resp7b.get('message')}')"
    }
    print(f"  Result: {'PASS' if t7_pass else 'FAIL'} -> {results['Test 7: Graceful Failure Handling']['details']}")

    # --------------------------------------------------------------------------
    # Test 8: Saving & Retrieving Inspection History in SQLite
    # --------------------------------------------------------------------------
    print("\n[TEST 8] Saving & Retrieving Inspection History...")
    # Clear history first
    make_request("/api/el/history/clear", method="POST")
    
    # Run two distinct inspections
    make_request("/api/el/inspect", method="POST", data={
        "sampleName": "el_microcrack_dendritic.png",
        "panelId": "SP-002",
        "confirmedModality": True
    })
    make_request("/api/el/inspect", method="POST", data={
        "sampleName": "el_isolated_inactive_region.png",
        "panelId": "SP-004",
        "confirmedModality": True
    })
    
    status8, resp8 = make_request("/api/el/history?limit=10")
    
    # Check directly in SQLite database
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM el_inspections")
    db_count = cur.fetchone()[0]
    conn.close()
    
    t8_pass = (
        status8 == 200 and
        resp8.get("success") is True and
        resp8.get("totalCount") >= 2 and
        db_count >= 2 and
        len(resp8.get("history", [])) >= 2
    )
    results["Test 8: History Persistence & Retrieval"] = {
        "passed": t8_pass,
        "details": f"API history items: {len(resp8.get('history', []))}, SQLite row count: {db_count}"
    }
    print(f"  Result: {'PASS' if t8_pass else 'FAIL'} -> {results['Test 8: History Persistence & Retrieval']['details']}")

    # --------------------------------------------------------------------------
    # Test 9: Authentication & Unauthorized Access
    # --------------------------------------------------------------------------
    print("\n[TEST 9] Authentication & Protected Route Verification...")
    # 9A: Unauthorized request to /api/auth/me should return 401
    status9a, resp9a = make_request("/api/auth/me", headers={"Authorization": "Bearer invalid_token_xyz"})
    
    # 9B: Valid login
    status9b, resp9b = make_request("/api/auth/login", method="POST", data={"email": "admin@solarshield.com", "password": "admin123"})
    valid_token = resp9b.get("token")
    
    # 9C: Authorized request with valid session token
    status9c, resp9c = make_request("/api/auth/me", headers={"Authorization": f"Bearer {valid_token}"})
    
    t9_pass = (
        status9a == 401 and
        resp9a.get("success") is False and
        status9b == 200 and
        resp9b.get("success") is True and
        status9c == 200 and
        resp9c.get("success") is True and
        resp9c.get("user", {}).get("email") == "admin@solarshield.com"
    )
    results["Test 9: Authentication & Authorization"] = {
        "passed": t9_pass,
        "details": f"Invalid token rejected (Status {status9a}), Login succeeded (Status {status9b}), Session verified (Status {status9c}, User: {resp9c.get('user', {}).get('name')})"
    }
    print(f"  Result: {'PASS' if t9_pass else 'FAIL'} -> {results['Test 9: Authentication & Authorization']['details']}")

    # --------------------------------------------------------------------------
    # Test 10: Regression Tests for IRT Module and Other Modules
    # --------------------------------------------------------------------------
    print("\n[TEST 10] Regression Tests for IRT and Existing Modules...")
    
    # Check IRT samples
    status10_irt_samples, resp10_irt_samples = make_request("/api/thermal/samples")
    # Check IRT inspect execution
    status10_irt_inspect, resp10_irt_inspect = make_request("/api/thermal/inspect", method="POST", data={
        "sampleName": "thermal_hotspot_cell.png",
        "panelId": "SP-001",
        "isCalibrated": True,
        "tMin": 25.0,
        "tMax": 75.0,
        "colormap": "IRONBOW"
    })
    # Check System Health
    status10_health, resp10_health = make_request("/api/system/health")
    # Check Fleet Panels
    status10_panels, resp10_panels = make_request("/api/panels")
    # Check Maintenance Orders
    status10_maint, resp10_maint = make_request("/api/maintenance")
    # Check Fault Reports
    status10_reports, resp10_reports = make_request("/api/reports")
    # Check CSV export
    status10_csv, resp10_csv = make_request("/api/export/csv")
    
    t10_pass = (
        status10_irt_samples == 200 and resp10_irt_samples.get("success") is True and
        status10_irt_inspect == 200 and resp10_irt_inspect.get("anomalyType") == "HOTSPOT" and
        status10_health == 200 and resp10_health.get("success") is True and
        status10_panels == 200 and len(resp10_panels.get("data", [])) > 0 and
        status10_maint == 200 and resp10_maint.get("success") is True and
        status10_reports == 200 and resp10_reports.get("success") is True and
        status10_csv == 200 and "ReportID" in str(resp10_csv)
    )
    results["Test 10: Regression Testing (IRT, Fleet, Reports)"] = {
        "passed": t10_pass,
        "details": f"IRT Samples: {status10_irt_samples}, IRT Hotspot: {status10_irt_inspect} (Type: {resp10_irt_inspect.get('anomalyType')}), System Health: {status10_health}, Fleet Panels: {len(resp10_panels.get('data', []))}, Maintenance: {status10_maint}, Reports: {status10_reports}, CSV Export: {status10_csv}"
    }
    print(f"  Result: {'PASS' if t10_pass else 'FAIL'} -> {results['Test 10: Regression Testing (IRT, Fleet, Reports)']['details']}")

    # --------------------------------------------------------------------------
    # Summary Report
    # --------------------------------------------------------------------------
    print("\n" + "=" * 70)
    print(" TEST EXECUTION SUMMARY")
    print("=" * 70)
    all_passed = True
    for test_name, res in results.items():
        status_str = "[PASS]" if res["passed"] else "[FAIL]"
        print(f" {status_str} {test_name}")
        print(f"        -> {res['details']}")
        if not res["passed"]:
            all_passed = False
            
    print("=" * 70)
    print(f" OVERALL RESULT: {'ALL 10 TEST CRITERIA PASSED' if all_passed else 'SOME TESTS FAILED'}")
    print("=" * 70)
    return all_passed

if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)
