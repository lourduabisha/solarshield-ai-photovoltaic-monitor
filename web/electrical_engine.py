"""
SolarShield Electrical Fault Diagnosis Engine
=============================================
Dedicated, explainable electrical engineering diagnostics service for
Photovoltaic (PV) modules, strings, and arrays.

Standards & Engineering Framework:
- IEC 60891: Photovoltaic devices - Procedures for temperature and irradiance
  corrections to measured I-V characteristics.
- IEC 61724: Photovoltaic system performance monitoring - Guidelines for measurement,
  data exchange and analysis.
- IEC 62446: Photovoltaic systems - Requirements for testing, documentation and maintenance.

Diagnostic Principles:
1. Distinguishes direct Instrument Measurements (V, I), Calculated Quantities (P = V x I,
   Fill Factor, STC Deratings), Rule-Based Heuristic Alerts, and Validated Model Predictions.
2. Does NOT assume missing values are zero. Retains None/null and reports missingness.
3. Does NOT diagnose specific internal semiconductor faults on a single isolated point
   without accounting for irradiance, temperature, and operating load conditions.
4. Identifies characteristic I-V curve signatures:
   - Bypass diode conduction (inflection steps / ~1/3 or ~2/3 voltage deficit)
   - Current depression from uniform soiling or string shading
   - High series resistance Rs (soft knee, interconnect/ribbon corrosion)
   - Low shunt resistance Rsh (steep current slope, localized crystal shunts, PID)
   - Open-circuit or string disconnect states
5. Flags 'NEEDS REVIEW' when irradiance is low (< 150 W/m2) or required context is missing.
"""

import math
import io
import csv
import re
from datetime import datetime
from typing import Dict, List, Tuple, Optional, Any

# Engine Identification & Metadata
ELECTRICAL_ENGINE_NAME = "SolarShield Electrical Diagnostic Engine"
ELECTRICAL_PIPELINE_VERSION = "v1.0-rules (IEC 60891 / IEC 61724 Fault Diagnostics)"
ELECTRICAL_SCORE_TYPE = "Rule-Based Anomaly Index (0-100 Heuristic Thresholds)"

# Diagnostic Fault Categories
CAT_NOMINAL = "NOMINAL_OPERATION"
CAT_BYPASS_DIODE = "BYPASS_DIODE_CONDUCTION"
CAT_CURRENT_DEPRESSION = "CURRENT_DEPRESSION_SOILING"
CAT_HIGH_SERIES_RESISTANCE = "HIGH_SERIES_RESISTANCE"
CAT_LOW_SHUNT_RESISTANCE = "LOW_SHUNT_RESISTANCE"
CAT_OPEN_CIRCUIT = "OPEN_CIRCUIT_ISOLATION"
CAT_SHORT_CIRCUIT = "SHORT_CIRCUIT_EARTH_FAULT"
CAT_NEEDS_REVIEW = "NEEDS_REVIEW"
CAT_SENSOR_OUTLIER = "SENSOR_OUTLIER"

# Standard Test Conditions (STC)
STC_IRRADIANCE = 1000.0  # W/m2
STC_TEMPERATURE = 25.0   # °C

# Default Typical Monocrystalline PERC 420W Module Reference Specs
DEFAULT_SPECS = {
    "rated_power": 420.0,   # P_max (W)
    "vmp": 41.5,            # V_mp (V)
    "imp": 10.12,           # I_mp (A)
    "voc": 49.5,            # V_oc (V)
    "isc": 10.85,           # I_sc (A)
    "alpha_isc": 0.05,      # % / °C (current temp co)
    "beta_voc": -0.28,      # % / °C (voltage temp co)
    "gamma_pmp": -0.37,     # % / °C (power temp co)
    "bypass_diodes": 3      # Standard 3 bypass diodes
}


class ElectricalModelInterface:
    """Extensible interface for future machine-learning surrogate models (e.g., neural I-V estimators)."""
    def is_available(self) -> bool:
        return False

    def predict(self, features: dict) -> dict:
        raise NotImplementedError("ML surrogate model not configured.")


def safe_float(val: Any) -> Optional[float]:
    """Converts value to float safely. None or empty string returns None (NOT zero)."""
    if val is None:
        return None
    if isinstance(val, (int, float)):
        if math.isnan(val) or math.isinf(val):
            return None
        return float(val)
    val_str = str(val).strip()
    if not val_str or val_str.lower() in ("null", "none", "nan", "na", "n/a", "-"):
        return None
    try:
        f = float(val_str)
        if math.isnan(f) or math.isinf(f):
            return None
        return f
    except ValueError:
        return None


def calculate_stc_derating(specs: dict, irradiance: Optional[float], panel_temp: Optional[float]) -> dict:
    """
    Computes expected operating electrical parameters derated for actual operating
    irradiance and cell temperature per IEC 60891 simplified procedure.
    """
    p_stc = specs.get("rated_power", 420.0)
    vmp_stc = specs.get("vmp", 41.5)
    imp_stc = specs.get("imp", 10.12)
    voc_stc = specs.get("voc", 49.5)
    isc_stc = specs.get("isc", 10.85)
    gamma_pmp = specs.get("gamma_pmp", -0.37) / 100.0  # fraction per °C
    alpha_isc = specs.get("alpha_isc", 0.05) / 100.0   # fraction per °C
    beta_voc = specs.get("beta_voc", -0.28) / 100.0    # fraction per °C

    has_irradiance = irradiance is not None and irradiance > 0
    has_temp = panel_temp is not None

    g_ratio = (irradiance / STC_IRRADIANCE) if has_irradiance else 1.0
    delta_t = (panel_temp - STC_TEMPERATURE) if has_temp else 0.0

    # Temperature and irradiance scaling
    temp_factor_p = 1.0 + (gamma_pmp * delta_t) if has_temp else 1.0
    temp_factor_i = 1.0 + (alpha_isc * delta_t) if has_temp else 1.0
    temp_factor_v = 1.0 + (beta_voc * delta_t) if has_temp else 1.0

    expected_isc = isc_stc * g_ratio * temp_factor_i
    expected_imp = imp_stc * g_ratio * temp_factor_i
    
    # Voc drops logarithmically with irradiance and linearly with temperature
    if has_irradiance and irradiance > 50:
        g_voc_factor = 1.0 + 0.045 * math.log(max(irradiance, 50.0) / STC_IRRADIANCE)
    else:
        g_voc_factor = 1.0

    expected_voc = voc_stc * temp_factor_v * g_voc_factor
    expected_vmp = vmp_stc * temp_factor_v * g_voc_factor
    expected_power = p_stc * g_ratio * temp_factor_p

    return {
        "expectedPower": round(expected_power, 2) if (has_irradiance or has_temp) else round(p_stc, 2),
        "expectedVmp": round(expected_vmp, 2),
        "expectedImp": round(expected_imp, 2),
        "expectedVoc": round(expected_voc, 2),
        "expectedIsc": round(expected_isc, 2),
        "hasIrradiance": has_irradiance,
        "hasTemperature": has_temp,
        "isTheoreticalSTC": not (has_irradiance and has_temp)
    }


def parse_and_validate_csv(csv_content: str) -> dict:
    """
    Parses and rigorously validates CSV input containing electrical measurements.
    Supports both time-series logs and I-V tracer sweep curves.
    Reports rejected rows with exact line numbers and reasons.
    Does NOT assume missing fields are zero.
    """
    if not csv_content or not csv_content.strip():
        return {
            "success": False,
            "message": "CSV upload is empty (0 bytes).",
            "validRows": [],
            "rejectedRows": []
        }

    # Detect delimiter
    first_lines = csv_content.strip().splitlines()[:5]
    sample = "\n".join(first_lines)
    delimiter = ","
    if ";" in sample and sample.count(";") > sample.count(","):
        delimiter = ";"
    elif "\t" in sample and sample.count("\t") > sample.count(","):
        delimiter = "\t"

    reader = csv.reader(io.StringIO(csv_content), delimiter=delimiter)
    try:
        raw_rows = list(reader)
    except Exception as e:
        return {
            "success": False,
            "message": f"Failed to parse CSV file: {str(e)}",
            "validRows": [],
            "rejectedRows": []
        }

    if not raw_rows:
        return {
            "success": False,
            "message": "CSV content contains no rows.",
            "validRows": [],
            "rejectedRows": []
        }

    # Find the header row (skip comment lines starting with # and blank lines)
    header_idx = None
    for idx, r in enumerate(raw_rows):
        if r and any(c.strip() != "" for c in r):
            if not r[0].strip().startswith("#"):
                header_idx = idx
                break

    if header_idx is None:
        return {
            "success": False,
            "message": "CSV content contains only comments or blank rows.",
            "validRows": [],
            "rejectedRows": []
        }

    header = [h.strip().lower() for h in raw_rows[header_idx]]
    
    # Map column headers to canonical names and detect unit multipliers
    col_map = {}
    col_units = {}
    col_multipliers = {}
    detected_columns = {}

    for idx, h in enumerate(header):
        h_orig = raw_rows[header_idx][idx].strip()
        h_lower = h_orig.lower()

        # Extract unit text from parentheses or square brackets e.g. "Voltage (V)", "Current [mA]"
        unit_match = re.search(r'[\(\[\{]([^\)\]\}]+)[\)\]\}]', h_lower)
        unit_text = unit_match.group(1).strip() if unit_match else ""

        # Clean base header tokens
        clean_name = re.sub(r'[\(\[\{][^\)\]\}]*[\)\]\}]', '', h_lower)
        clean_token = re.sub(r'[^a-z0-9_]', '_', clean_name).strip('_')
        raw_token = re.sub(r'[^a-z0-9_]', '', h_lower.replace(' ', '_'))

        # Check for Voltage column
        is_volt = False
        v_mult = 1.0
        v_unit = "V"
        if (clean_token in ("voltage", "v", "voltage_v", "volt", "volts", "v_pv", "v_meas", "v_terminal", "terminal_voltage", "u", "u_v", "tension", "v_mod", "v_module", "v_cell", "v_string", "string_voltage") or
            raw_token in ("voltage", "v", "voltagev", "voltage_v", "volt", "volts", "vvolts", "vpv", "vmeas", "vterminal", "terminalvoltage", "uv", "u", "tension", "vmod", "vmodule")):
            is_volt = True
        elif clean_token.startswith("voltage") or clean_token.startswith("volt") or clean_token.endswith("_voltage") or clean_token == "v":
            is_volt = True

        if is_volt and "voltage" not in col_map:
            if "mv" in unit_text or "millivolt" in unit_text or raw_token.endswith("mv"):
                v_mult = 0.001
                v_unit = "mV"
            elif "kv" in unit_text or "kilovolt" in unit_text or raw_token.endswith("kv"):
                v_mult = 1000.0
                v_unit = "kV"
            col_map["voltage"] = idx
            col_units["voltage"] = v_unit
            col_multipliers["voltage"] = v_mult
            detected_columns["voltage"] = {"index": idx, "header": h_orig, "unit": v_unit, "multiplier": v_mult}
            continue

        # Check for Current column
        is_curr = False
        i_mult = 1.0
        i_unit = "A"
        if (clean_token in ("current", "i", "current_a", "amp", "amps", "ampere", "amperes", "i_pv", "i_meas", "string_current", "i_string", "courant", "intensite", "i_mod", "i_module", "i_cell") or
            raw_token in ("current", "i", "currenta", "current_a", "amp", "amps", "ampere", "amperes", "currentamps", "ipv", "imeas", "stringcurrent", "courant", "intensite", "imod", "imodule")):
            is_curr = True
        elif clean_token.startswith("current") or clean_token.startswith("amp") or clean_token.endswith("_current") or clean_token == "i":
            is_curr = True

        if is_curr and "current" not in col_map:
            if "ma" in unit_text or "milliamp" in unit_text or raw_token.endswith("ma"):
                i_mult = 0.001
                i_unit = "mA"
            elif "ka" in unit_text or "kiloamp" in unit_text or raw_token.endswith("ka"):
                i_mult = 1000.0
                i_unit = "kA"
            col_map["current"] = idx
            col_units["current"] = i_unit
            col_multipliers["current"] = i_mult
            detected_columns["current"] = {"index": idx, "header": h_orig, "unit": i_unit, "multiplier": i_mult}
            continue

        # Check for Power column
        is_pow = False
        p_mult = 1.0
        p_unit = "W"
        if (clean_token in ("power", "p", "measured_power", "measured_power_w", "power_w", "watts", "p_pv", "p_meas", "active_power", "puissance") or
            raw_token in ("power", "p", "measuredpower", "measuredpowerw", "powerw", "watts", "ppv", "pmeas", "activepower")):
            is_pow = True
        elif clean_token.startswith("power") or clean_token.startswith("watt") or clean_token.endswith("_power"):
            is_pow = True

        if is_pow and "measured_power" not in col_map:
            if "kw" in unit_text or "kilowatt" in unit_text or raw_token.endswith("kw"):
                p_mult = 1000.0
                p_unit = "kW"
            elif "mw" in unit_text or "megawatt" in unit_text or raw_token.endswith("mw"):
                p_mult = 1000000.0
                p_unit = "MW"
            col_map["measured_power"] = idx
            col_units["measured_power"] = p_unit
            col_multipliers["measured_power"] = p_mult
            detected_columns["measured_power"] = {"index": idx, "header": h_orig, "unit": p_unit, "multiplier": p_mult}
            continue

        # Check for Irradiance column
        if (clean_token in ("irradiance", "irr", "g", "irradiance_wm2", "radiation", "flux", "solar_flux", "solar_radiation", "g_poa", "poa", "ghi", "dni", "dhi", "insolation") or
            raw_token in ("irradiance", "irr", "g", "irradiancewm2", "radiation", "flux", "solarflux", "solarradiation", "gpoa", "poa", "ghi", "dni", "dhi", "insolation", "wm2")):
            if "irradiance" not in col_map:
                col_map["irradiance"] = idx
                col_units["irradiance"] = "W/m²"
                col_multipliers["irradiance"] = 1.0
                detected_columns["irradiance"] = {"index": idx, "header": h_orig, "unit": "W/m²", "multiplier": 1.0}
                continue

        # Check for Panel/Cell Temperature
        if (clean_token in ("temperature", "temp", "panel_temp", "panel_temp_c", "cell_temp", "cell_temp_c", "t_mod", "t_cell", "module_temp", "t_panel", "surface_temp") or
            raw_token in ("temperature", "temp", "paneltemp", "paneltempc", "celltemp", "celltempc", "tmod", "tcell", "moduletemp", "tpanel")):
            if "panel_temp" not in col_map:
                col_map["panel_temp"] = idx
                is_f = "f" in unit_text.lower() or "fahrenheit" in unit_text.lower()
                u_str = "°F" if is_f else "°C"
                col_units["panel_temp"] = u_str
                col_multipliers["panel_temp"] = 1.0
                detected_columns["panel_temp"] = {"index": idx, "header": h_orig, "unit": u_str, "is_f": is_f}
                continue

        # Check for Ambient Temperature
        if (clean_token in ("ambient_temp", "ambient_temp_c", "t_amb", "amb_temp", "ambient", "air_temp", "outside_temp") or
            raw_token in ("ambienttemp", "ambienttempc", "tamb", "ambtemp", "ambient", "airtemp", "outsidetemp")):
            if "ambient_temp" not in col_map:
                col_map["ambient_temp"] = idx
                is_f = "f" in unit_text.lower() or "fahrenheit" in unit_text.lower()
                u_str = "°F" if is_f else "°C"
                col_units["ambient_temp"] = u_str
                col_multipliers["ambient_temp"] = 1.0
                detected_columns["ambient_temp"] = {"index": idx, "header": h_orig, "unit": u_str, "is_f": is_f}
                continue

        # Timestamp, panel_id, source, point_index, notes
        if clean_token in ("timestamp", "time", "datetime", "date", "recorded_at", "log_time") or raw_token in ("timestamp", "time", "datetime", "date"):
            if "timestamp" not in col_map:
                col_map["timestamp"] = idx
                detected_columns["timestamp"] = {"index": idx, "header": h_orig}
                continue

        if clean_token in ("panel_id", "panel", "module_id", "module", "string_id", "string", "asset_id") or raw_token in ("panelid", "panel", "moduleid", "module", "stringid", "string"):
            if "panel_id" not in col_map:
                col_map["panel_id"] = idx
                detected_columns["panel_id"] = {"index": idx, "header": h_orig}
                continue

        if clean_token in ("source", "instrument", "measurement_source", "device") or raw_token in ("source", "instrument", "device"):
            if "source" not in col_map:
                col_map["source"] = idx
                detected_columns["source"] = {"index": idx, "header": h_orig}
                continue

        if clean_token in ("point_index", "point", "index", "pt") or raw_token in ("pointindex", "point", "index", "pt"):
            if "point_index" not in col_map:
                col_map["point_index"] = idx
                detected_columns["point_index"] = {"index": idx, "header": h_orig}
                continue

        if clean_token in ("notes", "operating_conditions", "condition", "remarks", "comment") or raw_token in ("notes", "operatingconditions", "condition", "remarks"):
            if "notes" not in col_map:
                col_map["notes"] = idx
                detected_columns["notes"] = {"index": idx, "header": h_orig}
                continue

    # If voltage or current column is missing in header, check if header itself is numeric data without header
    is_header_valid = "voltage" in col_map and "current" in col_map
    start_row = header_idx + 1
    if not is_header_valid:
        if len(raw_rows[header_idx]) >= 2:
            v_test = safe_float(raw_rows[header_idx][0])
            i_test = safe_float(raw_rows[header_idx][1])
            if v_test is not None and i_test is not None:
                col_map["voltage"] = 0
                col_map["current"] = 1
                col_multipliers["voltage"] = 1.0
                col_multipliers["current"] = 1.0
                col_units["voltage"] = "V"
                col_units["current"] = "A"
                detected_columns["voltage"] = {"index": 0, "header": "Column 1 (auto-detected)", "unit": "V", "multiplier": 1.0}
                detected_columns["current"] = {"index": 1, "header": "Column 2 (auto-detected)", "unit": "A", "multiplier": 1.0}
                if len(raw_rows[header_idx]) >= 3 and safe_float(raw_rows[header_idx][2]) is not None:
                    col_map["irradiance"] = 2
                    col_multipliers["irradiance"] = 1.0
                    col_units["irradiance"] = "W/m²"
                    detected_columns["irradiance"] = {"index": 2, "header": "Column 3 (auto-detected)", "unit": "W/m²", "multiplier": 1.0}
                if len(raw_rows[header_idx]) >= 4 and safe_float(raw_rows[header_idx][3]) is not None:
                    col_map["panel_temp"] = 3
                    col_multipliers["panel_temp"] = 1.0
                    col_units["panel_temp"] = "°C"
                    detected_columns["panel_temp"] = {"index": 3, "header": "Column 4 (auto-detected)", "unit": "°C", "multiplier": 1.0}
                start_row = header_idx
                is_header_valid = True

    if not is_header_valid:
        msg = "CSV header is missing required electrical columns. Must contain at least 'voltage' (V) and 'current' (A)."
        return {
            "success": False,
            "message": msg,
            "error": msg,
            "detectedHeader": header,
            "detectedColumns": detected_columns,
            "validRows": [],
            "validCount": 0,
            "validRowsCount": 0,
            "rejectedRows": [],
            "rejectedCount": 0,
            "rejectedRowsCount": 0
        }

    valid_rows = []
    rejected_rows = []
    seen_timestamps = set()

    for line_num, row in enumerate(raw_rows[start_row:], start=start_row + 1):
        if not row or all(c.strip() == "" for c in row):
            continue  # ignore empty lines

        # Extract values
        v_raw = row[col_map["voltage"]] if col_map.get("voltage") is not None and col_map["voltage"] < len(row) else None
        i_raw = row[col_map["current"]] if col_map.get("current") is not None and col_map["current"] < len(row) else None
        p_raw = row[col_map["measured_power"]] if col_map.get("measured_power") is not None and col_map["measured_power"] < len(row) else None
        g_raw = row[col_map["irradiance"]] if col_map.get("irradiance") is not None and col_map["irradiance"] < len(row) else None
        t_raw = row[col_map["panel_temp"]] if col_map.get("panel_temp") is not None and col_map["panel_temp"] < len(row) else None
        tamb_raw = row[col_map["ambient_temp"]] if col_map.get("ambient_temp") is not None and col_map["ambient_temp"] < len(row) else None
        ts_raw = row[col_map["timestamp"]].strip() if col_map.get("timestamp") is not None and col_map["timestamp"] < len(row) else None
        pid_raw = row[col_map["panel_id"]].strip() if col_map.get("panel_id") is not None and col_map["panel_id"] < len(row) else None
        src_raw = row[col_map["source"]].strip() if col_map.get("source") is not None and col_map["source"] < len(row) else None
        notes_raw = row[col_map["notes"]].strip() if col_map.get("notes") is not None and col_map["notes"] < len(row) else None

        v_raw_val = safe_float(v_raw)
        v = (v_raw_val * col_multipliers.get("voltage", 1.0)) if v_raw_val is not None else None

        i_raw_val = safe_float(i_raw)
        i = (i_raw_val * col_multipliers.get("current", 1.0)) if i_raw_val is not None else None

        p_raw_val = safe_float(p_raw)
        p_meas = (p_raw_val * col_multipliers.get("measured_power", 1.0)) if p_raw_val is not None else None

        g = safe_float(g_raw)
        
        t_raw_val = safe_float(t_raw)
        if t_raw_val is not None and detected_columns.get("panel_temp", {}).get("is_f"):
            t_pan = round((t_raw_val - 32.0) * 5.0 / 9.0, 2)
        else:
            t_pan = t_raw_val

        tamb_raw_val = safe_float(tamb_raw)
        if tamb_raw_val is not None and detected_columns.get("ambient_temp", {}).get("is_f"):
            t_amb = round((tamb_raw_val - 32.0) * 5.0 / 9.0, 2)
        else:
            t_amb = tamb_raw_val

        # Validation rules
        rejection_reasons = []
        if v is None:
            rejection_reasons.append(f"Invalid or missing Voltage value '{v_raw}'")
        elif v < -5.0 or v > 1500.0:
            rejection_reasons.append(f"Voltage {v} V out of physical operating range (-5 to 1500 V)")

        if i is None:
            rejection_reasons.append(f"Invalid or missing Current value '{i_raw}'")
        elif i < -5.0 or i > 100.0:
            rejection_reasons.append(f"Current {i} A out of physical operating range (-5 to 100 A)")

        if g is not None and (g < 0 or g > 2000):
            rejection_reasons.append(f"Irradiance {g} W/m2 out of range (0 to 2000 W/m2)")

        if t_pan is not None and (t_pan < -50 or t_pan > 120):
            rejection_reasons.append(f"Panel temperature {t_pan} °C out of range (-50 to 120 °C)")

        if rejection_reasons:
            rejected_rows.append({
                "lineNumber": line_num,
                "line": line_num,
                "raw": row,
                "reasons": rejection_reasons,
                "reason": " | ".join(rejection_reasons)
            })
            continue

        # Handle duplicate timestamp flag
        is_duplicate = False
        if ts_raw:
            ts_key = (ts_raw, pid_raw)
            if ts_key in seen_timestamps:
                is_duplicate = True
            else:
                seen_timestamps.add(ts_key)

        # Calculate power (P = V x I)
        p_calc = round(v * i, 3) if (v is not None and i is not None) else None

        valid_rows.append({
            "lineNumber": line_num,
            "line": line_num,
            "voltage": round(v, 3),
            "current": round(i, 3),
            "power": p_calc,
            "measuredPower": round(p_meas, 3) if p_meas is not None else None,
            "calculatedPower": p_calc,
            "irradiance": round(g, 1) if g is not None else None,
            "panelTemp": round(t_pan, 1) if t_pan is not None else None,
            "ambientTemp": round(t_amb, 1) if t_amb is not None else None,
            "timestamp": ts_raw or datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
            "panelId": pid_raw,
            "source": src_raw or "CSV_IMPORT",
            "notes": notes_raw or "",
            "isDuplicateTimestamp": is_duplicate
        })

    # Determine if this looks like an I-V curve sweep
    # (ordered set of points spanning from low V / high I to high V / low I)
    is_iv_sweep = False
    if len(valid_rows) >= 5:
        voltages = [r["voltage"] for r in valid_rows]
        currents = [r["current"] for r in valid_rows]
        v_span = max(voltages) - min(voltages)
        i_span = max(currents) - min(currents)
        if v_span > 5.0 and i_span > 0.5:
            is_iv_sweep = True

    if "voltage" in detected_columns:
        detected_columns["voltageColumn"] = detected_columns["voltage"].get("header")
    if "current" in detected_columns:
        detected_columns["currentColumn"] = detected_columns["current"].get("header")

    return {
        "success": True,
        "validCount": len(valid_rows),
        "validRowsCount": len(valid_rows),
        "rejectedCount": len(rejected_rows),
        "rejectedRowsCount": len(rejected_rows),
        "isIvSweep": is_iv_sweep,
        "validRows": valid_rows,
        "rejectedRows": rejected_rows,
        "detectedColumns": detected_columns,
        "detectedHeader": header
    }


def analyze_iv_curve(points: List[Dict[str, Any]], specs: dict, irradiance: Optional[float] = None,
                     panel_temp: Optional[float] = None) -> dict:
    """
    Performs comprehensive I-V and P-V curve characterization:
    - Sorts and extracts I_sc, V_oc, P_mp, V_mp, I_mp
    - Computes Fill Factor (FF) = P_mp / (V_oc x I_sc)
    - Estimates Series Resistance (Rs) from slope near V_oc
    - Estimates Shunt Resistance (Rsh) from slope near I_sc
    - Detects inflections / bypass diode conduction steps
    - Generates smoothed curve data points for visualization
    """
    if len(points) < 5:
        return {
            "hasIvCurve": False,
            "reason": f"Insufficient data points for I-V curve analysis ({len(points)} points provided, minimum 5 required)."
        }

    # Clean and sort points by voltage ascending
    clean_pts = []
    for pt in points:
        v = pt.get("voltage")
        i = pt.get("current")
        if v is not None and i is not None and v >= 0 and i >= 0:
            clean_pts.append({"v": float(v), "i": float(i), "p": round(float(v) * float(i), 3)})

    if len(clean_pts) < 5:
        return {
            "hasIvCurve": False,
            "reason": f"Fewer than 5 positive voltage/current pairs ({len(clean_pts)} found)."
        }

    clean_pts.sort(key=lambda x: x["v"])

    # Remove strict duplicate voltages by averaging current
    unique_pts = []
    for pt in clean_pts:
        if not unique_pts:
            unique_pts.append(pt)
        elif abs(pt["v"] - unique_pts[-1]["v"]) < 0.05:
            # average current for close voltages
            unique_pts[-1]["i"] = (unique_pts[-1]["i"] + pt["i"]) / 2.0
            unique_pts[-1]["p"] = round(unique_pts[-1]["v"] * unique_pts[-1]["i"], 3)
        else:
            unique_pts.append(pt)

    if len(unique_pts) < 5:
        unique_pts = clean_pts

    voltages = [pt["v"] for pt in unique_pts]
    currents = [pt["i"] for pt in unique_pts]
    powers = [pt["p"] for pt in unique_pts]

    # Short-circuit current Isc:
    # Requires sweep points sufficiently near short-circuit (V <= 5.0 V or V <= 0.10 * Voc)
    min_v = min(voltages)
    isc = None
    if min_v <= 5.0:
        low_v_pts = sorted([pt for pt in unique_pts if pt["v"] <= 5.0], key=lambda x: x["v"])
        if low_v_pts:
            isc = low_v_pts[0]["i"]
    elif min_v <= 0.10 * specs.get("voc", 49.5):
        isc = max(currents)

    # Open-circuit voltage Voc:
    # Requires sweep points sufficiently near open-circuit (I <= 0.5 A or I <= 0.05 * Isc)
    min_i = min(currents)
    voc = None
    if min_i <= 0.5:
        high_v_pts = sorted([pt for pt in unique_pts if pt["i"] <= 0.5], key=lambda x: x["v"], reverse=True)
        if high_v_pts:
            voc = high_v_pts[0]["v"]
    elif min_i <= 0.05 * specs.get("isc", 10.85):
        voc = max(voltages)

    # Maximum Power Point (MPP) from valid measured curve points
    max_p_idx = int(powers.index(max(powers)))
    pmp = powers[max_p_idx]
    vmp = voltages[max_p_idx]
    imp = currents[max_p_idx]

    # Fill Factor: FF = Pmax / (Voc * Isc)
    # Only calculate when Voc, Isc, and Pmax are valid, positive, and compatible
    fill_factor = None
    if voc is not None and isc is not None and voc > 0 and isc > 0:
        theoretical_max = voc * isc
        if theoretical_max >= pmp and pmp > 0:
            fill_factor = round(pmp / theoretical_max, 4)

    # Series Resistance Rs estimation:
    # Reciprocal slope dV/dI in region near Voc (V >= 0.75 * Voc)
    # Requires at least 2 distinct points where current drops (di >= 0.05 A, dv > 0.1 V)
    rs_est = None
    if voc is not None:
        high_v_pts = [pt for pt in unique_pts if pt["v"] >= 0.75 * voc]
        if len(high_v_pts) >= 2:
            dv = high_v_pts[-1]["v"] - high_v_pts[0]["v"]
            di = high_v_pts[0]["i"] - high_v_pts[-1]["i"]
            if di >= 0.05 and dv > 0.1:
                rs_est = round(dv / di, 3)

    # Shunt Resistance Rsh estimation:
    # Reciprocal slope dV/dI in region near Isc (V <= 0.25 * Voc)
    # Requires at least 2 distinct points where voltage spans dv >= 0.5 V and current slope is measurable
    rsh_est = None
    if voc is not None:
        low_v_pts = [pt for pt in unique_pts if pt["v"] <= 0.25 * voc]
        if len(low_v_pts) >= 2:
            dv = low_v_pts[-1]["v"] - low_v_pts[0]["v"]
            di = low_v_pts[0]["i"] - low_v_pts[-1]["i"]
            if dv >= 0.5 and abs(di) >= 0.005:
                rsh_est = round(abs(dv / di), 1)

    # Step / Notch / Bypass Diode conduction detection in I-V curve
    # Healthy I-V curves have negative non-increasing slope dI/dV
    # A partial shading or diode conduction creates a sudden plateau or 'knee'
    diode_step_detected = False
    step_voltage_region = None
    if len(unique_pts) >= 8:
        slopes = []
        for k in range(len(unique_pts) - 1):
            dv = unique_pts[k + 1]["v"] - unique_pts[k]["v"]
            di = unique_pts[k + 1]["i"] - unique_pts[k]["i"]
            if dv > 0.1:
                slopes.append((unique_pts[k]["v"], di / dv))

        # Check for multiple local minima in power curve or inflection in current slope
        power_dips = 0
        for k in range(1, len(powers) - 1):
            if powers[k] < powers[k - 1] - 8.0 and powers[k] < powers[k + 1] - 8.0:
                power_dips += 1

        # Check if slope changes sign or has abrupt discontinuity
        for k in range(len(slopes) - 2):
            s1 = slopes[k][1]
            s2 = slopes[k + 1][1]
            if s1 < -0.15 and s2 > -0.03 and slopes[k][0] < 0.85 * voc:
                diode_step_detected = True
                step_voltage_region = round(slopes[k][0], 1)
                break

        if power_dips >= 1 and not diode_step_detected:
            diode_step_detected = True
            step_voltage_region = round(vmp, 1)

    return {
        "hasIvCurve": True,
        "pointsCount": len(unique_pts),
        "voc": round(voc, 2) if voc is not None else None,
        "isc": round(isc, 2) if isc is not None else None,
        "vmp": round(vmp, 2) if vmp is not None else None,
        "imp": round(imp, 2) if imp is not None else None,
        "pmp": round(pmp, 2) if pmp is not None else None,
        "fillFactor": fill_factor,
        "rsEst": rs_est,
        "rshEst": rsh_est,
        "diodeStepDetected": diode_step_detected,
        "stepVoltageRegion": step_voltage_region,
        "curvePoints": [
            {
                "v": pt["v"],
                "i": pt["i"],
                "p": pt["p"],
                "voltage": pt["v"],
                "current": pt["i"],
                "power": pt["p"]
            }
            for pt in unique_pts
        ]
    }


def diagnose_electrical_fault(
    panel_id: str,
    voltage: Optional[float],
    current: Optional[float],
    measured_power: Optional[float] = None,
    irradiance: Optional[float] = None,
    panel_temp: Optional[float] = None,
    ambient_temp: Optional[float] = None,
    source: str = "MANUAL_ENTRY",
    notes: str = "",
    timestamp: Optional[str] = None,
    iv_points: Optional[List[Dict[str, Any]]] = None,
    panel_spec: Optional[dict] = None
) -> dict:
    """
    Main diagnostic orchestration function.
    Applies transparent, configurable engineering rules to evaluate operating parameters,
    I-V curve characteristics, and compare against reference module specifications.
    Distinguishes measured, calculated, rule-flagged, and model predictions.
    """
    specs = panel_spec or DEFAULT_SPECS
    ts = timestamp or datetime.now().strftime("%Y-%m-%dT%H:%M:%S")

    # 1. Validation & Power Calculation
    data_quality_warnings = []
    rule_flags = []
    
    if voltage is None and current is None:
        return {
            "success": False,
            "message": "Both Voltage and Current are missing. At least one valid electrical reading is required.",
            "needsReview": True
        }

    # Physical outlier checks
    is_outlier = False
    if voltage is not None and (voltage < -1.0 or voltage > 1500.0):
        data_quality_warnings.append(f"Voltage reading ({voltage} V) violates physical module operational limits.")
        is_outlier = True
    if current is not None and (current < -0.5 or current > 100.0):
        data_quality_warnings.append(f"Current reading ({current} A) violates physical module operational limits.")
        is_outlier = True
    if irradiance is not None and (irradiance < 0.0 or irradiance > 1600.0):
        data_quality_warnings.append(f"Irradiance reading ({irradiance} W/m2) is outside meteorological limits.")
        is_outlier = True
    if panel_temp is not None and (panel_temp < -40.0 or panel_temp > 125.0):
        data_quality_warnings.append(f"Panel temperature ({panel_temp} °C) is outside physical module bounds.")
        is_outlier = True

    if is_outlier:
        return {
            "success": True,
            "panelId": panel_id,
            "timestamp": ts,
            "diagnosticStatus": CAT_SENSOR_OUTLIER,
            "severity": "CRITICAL",
            "maintenancePriority": "IMMEDIATE_ACTION",
            "anomalyScore": 95.0,
            "scoreType": ELECTRICAL_SCORE_TYPE,
            "voltage": voltage,
            "current": current,
            "measuredPower": measured_power,
            "calculatedPower": round(voltage * current, 2) if (voltage is not None and current is not None) else None,
            "powerDiscrepancyPercent": None,
            "irradiance": irradiance,
            "panelTemp": panel_temp,
            "ambientTemp": ambient_temp,
            "expectedPower": specs.get("rated_power", 420.0),
            "performanceRatio": None,
            "needsReview": True,
            "whyFlagged": "Instrument sensor outlier: One or more parameters violate physical solar PV limits.",
            "evidence": data_quality_warnings,
            "recommendedAction": "Inspect instrumentation leads, sensor calibration, or multimeter probe polarity.",
            "dataQualityWarnings": data_quality_warnings,
            "analysisMethod": ELECTRICAL_PIPELINE_VERSION
        }

    # Calculated Power
    calc_power = None
    if voltage is not None and current is not None:
        calc_power = round(voltage * current, 2)

    # Power Discrepancy (Measured vs Calculated)
    power_discrepancy = None
    if measured_power is not None and calc_power is not None and calc_power > 0:
        power_discrepancy = round((measured_power - calc_power) / calc_power * 100.0, 1)
        if abs(power_discrepancy) > 12.0:
            data_quality_warnings.append(
                f"Instrument discrepancy: Measured power ({measured_power} W) deviates from calculated V x I ({calc_power} W) by {abs(power_discrepancy)}%."
            )

    # 2. STC Derating Expectations
    deratings = calculate_stc_derating(specs, irradiance, panel_temp)
    expected_p = deratings["expectedPower"]
    expected_v = deratings["expectedVmp"]
    expected_i = deratings["expectedImp"]
    has_g = deratings["hasIrradiance"]
    has_t = deratings["hasTemperature"]

    # Performance Ratio / Power Yield
    perf_ratio = None
    perf_ratio_type = None
    effective_power = calc_power if calc_power is not None else measured_power
    if effective_power is not None and expected_p > 0:
        perf_ratio = round((effective_power / expected_p) * 100.0, 1)
        perf_ratio_type = "IEC_61724_WEATHER_CORRECTED" if (has_g and irradiance is not None and irradiance >= 150.0) else "UNCORRECTED_STC_RATIO"

    # Context warnings
    needs_review = False
    if not has_g:
        data_quality_warnings.append("Irradiance (W/m2) was not provided; analysis assumes nominal 1000 W/m2 STC reference.")
        needs_review = True
    elif irradiance is not None and irradiance < 150.0:
        data_quality_warnings.append(f"Low irradiance condition ({irradiance} W/m2 < 150 W/m2). PV non-linearities reduce diagnosis confidence.")
        needs_review = True

    if not has_t:
        data_quality_warnings.append("Module temperature (°C) was not provided; temperature derating could not be applied.")

    # 3. I-V Curve Analysis (if points provided)
    iv_analysis = {"hasIvCurve": False}
    if iv_points and len(iv_points) >= 5:
        iv_analysis = analyze_iv_curve(iv_points, specs, irradiance, panel_temp)
    elif iv_points and len(iv_points) < 5:
        data_quality_warnings.append(f"Insufficient data points for I-V curve analysis ({len(iv_points)} points provided, minimum 5 required for curve tracing).")

    # 4. Transparent Rule Evaluation
    anomaly_score = 0.0
    detected_cat = CAT_NOMINAL
    severity = "LOW"
    priority = "MONITOR"
    evidence_list = []
    action_rec = "Module operates within normal electrical limits. Continue scheduled maintenance monitoring."

    # RULE 1: Open Circuit / Disconnected String
    if voltage is not None and voltage > (0.4 * specs.get("voc", 49.5)) and current is not None and current < 0.25:
        if not has_g or (irradiance is not None and irradiance > 150.0):
            detected_cat = CAT_OPEN_CIRCUIT
            severity = "CRITICAL"
            priority = "URGENT"
            anomaly_score = 92.0
            evidence_list.append(f"Terminal voltage present ({voltage:.1f} V) with near-zero current ({current:.2f} A < 0.25 A) under active daylight.")
            evidence_list.append("Hypothesis: Probable open circuit, disconnected string plug, tripped DC isolator, or blown fuse.")
            action_rec = "Isolate DC string and check MC4 connectors, combiner box fuses, and inverter input DC contactors."

    # RULE 2: Short Circuit / Ground Fault
    elif voltage is not None and voltage < 2.0 and current is not None and current > (0.6 * specs.get("isc", 10.8)):
        detected_cat = CAT_SHORT_CIRCUIT
        severity = "CRITICAL"
        priority = "IMMEDIATE_ACTION"
        anomaly_score = 96.0
        evidence_list.append(f"Terminal voltage near zero ({voltage:.1f} V) under high current ({current:.1f} A).")
        evidence_list.append("Hypothesis: Probable dead short circuit, shorted bypass diodes, or earth fault path.")
        action_rec = "Immediately check for module terminal shorts, junction box bridge, and verify string insulation resistance (Megger test)."

    # RULE 3: I-V Curve Step / Bypass Diode Conduction
    elif iv_analysis.get("hasIvCurve") and iv_analysis.get("diodeStepDetected"):
        detected_cat = CAT_BYPASS_DIODE
        severity = "HIGH"
        priority = "SCHEDULED"
        anomaly_score = 78.0
        v_step = iv_analysis.get("stepVoltageRegion", "intermediate")
        evidence_list.append(f"Pronounced knee / inflection step detected in I-V curve near {v_step} V.")
        ff_val = iv_analysis.get('fillFactor')
        if ff_val is not None:
            evidence_list.append(f"Fill factor suppressed to {ff_val:.3f}.")
        action_rec = "Conduct IR thermography scan and EL inspection to isolate the shaded or cracked sub-string causing diode conduction."

    # RULE 4: Operating Point Voltage Deficit (1/3 or 2/3 Vmp drop -> Bypass diode conduction)
    elif voltage is not None and current is not None and current >= (0.65 * expected_i):
        v_ratio = voltage / expected_v if expected_v > 0 else 1.0
        # Check for ~33% voltage loss (1 diode bypassed in 3-diode module)
        if 0.58 <= v_ratio <= 0.76:
            detected_cat = CAT_BYPASS_DIODE
            severity = "HIGH"
            priority = "SCHEDULED"
            anomaly_score = 75.0
            evidence_list.append(f"Operating voltage ({voltage:.1f} V) is ~{v_ratio*100:.0f}% of expected Vmp ({expected_v:.1f} V) while current ({current:.1f} A) remains healthy.")
            evidence_list.append("Hypothesis: Potential bypass diode conduction (1 of 3 sub-strings bypassed due to cell fault or localized shading).")
            action_rec = "Inspect module surface for localized shading/soiling; verify bypass diode health and cross-reference with IRT."
        # Check for ~66% voltage loss (2 diodes bypassed)
        elif 0.25 <= v_ratio < 0.45:
            detected_cat = CAT_BYPASS_DIODE
            severity = "CRITICAL"
            priority = "URGENT"
            anomaly_score = 88.0
            evidence_list.append(f"Operating voltage ({voltage:.1f} V) is ~{v_ratio*100:.0f}% of expected Vmp ({expected_v:.1f} V) (2/3 voltage deficit).")
            evidence_list.append("Hypothesis: Potential bypass diode conduction (2 sub-strings deactivated by forward-biased bypass diodes).")
            action_rec = "Urgent field review: test junction box bypass diodes and perform IRT thermal scan."

    # RULE 5: Low Shunt Resistance Rsh / PID
    if detected_cat == CAT_NOMINAL and iv_analysis.get("hasIvCurve"):
        ff = iv_analysis.get("fillFactor", 0.8)
        rsh = iv_analysis.get("rshEst")
        if rsh is not None and rsh < 150.0:
            detected_cat = CAT_LOW_SHUNT_RESISTANCE
            severity = "HIGH"
            priority = "SCHEDULED"
            anomaly_score = 72.0
            evidence_list.append(f"Steep current slope in low-voltage region indicates low shunt resistance (Rsh est = {rsh:.1f} Ω < 150 Ω).")
            evidence_list.append("Hypothesis: Suspected parasitic shunt leakage paths, Potential-Induced Degradation (PID), or localized crystal defects.")
            action_rec = "Perform dark I-V curve sweep and high-resolution Electroluminescence (EL) scan to locate active shunts."

    # RULE 6: High Series Resistance Rs
    if detected_cat == CAT_NOMINAL and iv_analysis.get("hasIvCurve"):
        ff = iv_analysis.get("fillFactor")
        rs = iv_analysis.get("rsEst")
        rsh = iv_analysis.get("rshEst")
        if (ff is not None and ff < 0.68) and rs is not None and rs > 0.8 and (rsh is None or rsh >= 150.0):
            detected_cat = CAT_HIGH_SERIES_RESISTANCE
            severity = "MEDIUM"
            priority = "SCHEDULED"
            anomaly_score = 65.0
            evidence_list.append(f"Suppressed fill factor (FF = {ff:.3f} < 0.70) with elevated series resistance (Rs est = {rs:.2f} Ω).")
            evidence_list.append("Hypothesis: Possible elevated series resistance degradation (softened I-V knee) indicative of solder ribbon corrosion or degraded cell interconnects.")
            action_rec = "Inspect internal interconnect ribbons and terminal box contacts; check for hot-spot heating."

    # RULE 7: Current Depression / Soiling / Uniform Shading
    if detected_cat == CAT_NOMINAL and has_g and irradiance is not None and irradiance >= 200.0:
        if current is not None and current < (0.72 * expected_i) and (voltage is not None and voltage >= 0.80 * expected_v):
            detected_cat = CAT_CURRENT_DEPRESSION
            severity = "MEDIUM"
            priority = "SCHEDULED"
            i_loss = round((1.0 - (current / expected_i)) * 100.0, 1)
            anomaly_score = 55.0
            evidence_list.append(f"Operating current ({current:.2f} A) shows a {i_loss}% deficit compared to irradiance-expected current ({expected_i:.2f} A).")
            evidence_list.append("Hypothesis: Potential uniform surface soiling, dust accumulation, or light uniform shading while operating voltage remains stable.")
            action_rec = "Schedule module surface cleaning and reassess operating current post-wash."

    # RULE 8: Under-performance / Power Deficit
    if detected_cat == CAT_NOMINAL and perf_ratio is not None and has_g and irradiance is not None and irradiance >= 250.0:
        if perf_ratio < 75.0:
            detected_cat = CAT_CURRENT_DEPRESSION
            severity = "MEDIUM"
            priority = "SCHEDULED"
            anomaly_score = 50.0
            evidence_list.append(f"Yield efficiency ({perf_ratio}%) is significantly below expected operating power ({expected_p:.1f} W).")
            evidence_list.append("Hypothesis: Underperformance attributable to soiling, non-optimal tilt angle, or inverter MPPT tracking error.")
            action_rec = "Verify module soiling, shading profiles, and inverter MPPT tracking health."

    # RULE 9: Incomplete Information / Single Point without Irradiance -> Needs Review
    if detected_cat == CAT_NOMINAL and (not has_g or needs_review):
        detected_cat = CAT_NEEDS_REVIEW
        severity = "UNKNOWN"
        priority = "MONITOR"
        anomaly_score = 15.0
        evidence_list.append("Single operating point supplied without concurrent irradiance (W/m2) and temperature data.")
        evidence_list.append("Hypothesis: Physical baseline cannot be normalized to STC conditions with high confidence.")
        action_rec = "Perform full I-V curve tracer sweep or record simultaneous calibrated solar irradiance."

    # RULE 10: Nominal Health
    if detected_cat == CAT_NOMINAL:
        anomaly_score = 5.0
        severity = "LOW"
        priority = "MONITOR"
        evidence_list.append(f"Voltage ({voltage:.1f} V) and Current ({current:.2f} A) match expected STC operating envelopes.")
        if perf_ratio is not None:
            evidence_list.append(f"Yield Performance Ratio is healthy at {perf_ratio}%.")
        if iv_analysis.get("hasIvCurve"):
            if iv_analysis.get("fillFactor") is not None:
                evidence_list.append(f"I-V curve demonstrates healthy squareness with Fill Factor FF = {iv_analysis.get('fillFactor'):.3f}.")
            else:
                evidence_list.append("I-V curve measurements logged within normal operating limits.")

    # Format why_flagged text
    why_flagged_text = " | ".join(evidence_list) if evidence_list else "Routine measurement evaluation."

    return {
        "success": True,
        "panelId": panel_id,
        "timestamp": ts,
        "source": source,
        "notes": notes,
        "diagnosticStatus": detected_cat,
        "severity": severity,
        "maintenancePriority": priority,
        "anomalyScore": anomaly_score,
        "scoreType": ELECTRICAL_SCORE_TYPE,
        # Direct Measured Values
        "voltage": voltage,
        "current": current,
        "measuredPower": measured_power,
        "irradiance": irradiance,
        "panelTemp": panel_temp,
        "ambientTemp": ambient_temp,
        # Calculated Quantities
        "calculatedPower": calc_power,
        "powerDiscrepancyPercent": power_discrepancy,
        "expectedPower": expected_p,
        "expectedVmp": expected_v,
        "expectedImp": expected_i,
        "performanceRatio": perf_ratio,
        # I-V Curve Analytics
        "hasIvCurve": iv_analysis.get("hasIvCurve", False),
        "ivPointsCount": iv_analysis.get("pointsCount", 0),
        "voc": iv_analysis.get("voc"),
        "isc": iv_analysis.get("isc"),
        "vmp": iv_analysis.get("vmp") if iv_analysis.get("hasIvCurve") else None,
        "imp": iv_analysis.get("imp") if iv_analysis.get("hasIvCurve") else None,
        "pmp": iv_analysis.get("pmp") if iv_analysis.get("hasIvCurve") else None,
        "fillFactor": iv_analysis.get("fillFactor"),
        "rsEst": iv_analysis.get("rsEst"),
        "rshEst": iv_analysis.get("rshEst"),
        "perfRatioType": perf_ratio_type,
        "diodeStepDetected": iv_analysis.get("diodeStepDetected", False),
        "curvePoints": iv_analysis.get("curvePoints", []),
        # Diagnosis & Recommendations
        "needsReview": needs_review or (detected_cat == CAT_NEEDS_REVIEW),
        "whyFlagged": why_flagged_text,
        "evidence": evidence_list,
        "diagnosticHypotheses": evidence_list,
        "recommendedAction": action_rec,
        "dataQualityWarnings": data_quality_warnings,
        "analysisMethod": ELECTRICAL_PIPELINE_VERSION,
        "safetyNotice": (
            "SolarShield Electrical Diagnostics is a software diagnostic tool analyzing ingested telemetry. "
            "No live physical measurement hardware is connected."
        ),
        "disclaimer": (
            "Electrical measurements identify candidate electrical anomalies and performance degradation. "
            "Internal root cause verification should be cross-referenced with IRT thermography and EL imaging "
            "before module decommissioning."
        )
    }


def auto_analyze_electrical_csv(
    csv_content: str,
    panel_id: str = "SP-001",
    filename: Optional[str] = None,
    is_sample: bool = False,
    panel_spec: Optional[dict] = None
) -> dict:
    """
    Fully automatic electrical data analysis pipeline.
    Parses CSV, maps columns and validates units, computes P = V x I for every valid measurement,
    derives P_mp, V_mp, I_mp, and (if >= 5 curve sweep points) V_oc, I_sc, Fill Factor, Rs, and Rsh.
    Formulates anomalies as hypotheses with evidence, and distinguishes real uploads from sample demos.
    Does NOT require manual parameter entry.
    """
    if not csv_content or not csv_content.strip():
        msg = "Uploaded CSV file is empty (0 bytes). Please upload a valid CSV file with voltage and current data."
        return {
            "success": False,
            "message": msg,
            "error": msg,
            "validCount": 0,
            "validRowsCount": 0,
            "rejectedCount": 0,
            "rejectedRowsCount": 0
        }

    parsed = parse_and_validate_csv(csv_content)
    if not parsed.get("success", False):
        if not parsed.get("error"):
            parsed["error"] = parsed.get("message", "CSV parsing failed.")
        return parsed

    valid_rows = parsed.get("validRows", [])
    if not valid_rows:
        msg = f"Uploaded file does not contain valid electrical measurement rows. {parsed.get('rejectedCount', 0)} rows were rejected."
        return {
            "success": False,
            "message": msg,
            "error": msg,
            "validCount": 0,
            "validRowsCount": 0,
            "rejectedCount": parsed.get("rejectedCount", 0),
            "rejectedRowsCount": parsed.get("rejectedCount", 0),
            "rejectedRows": parsed.get("rejectedRows", []),
            "detectedHeader": parsed.get("detectedHeader", [])
        }

    specs = panel_spec or DEFAULT_SPECS
    target_pid = valid_rows[0].get("panelId") or panel_id or "SP-001"

    # Aggregated or representative operating conditions
    g_vals = [r["irradiance"] for r in valid_rows if r.get("irradiance") is not None]
    t_vals = [r["panelTemp"] for r in valid_rows if r.get("panelTemp") is not None]
    tamb_vals = [r["ambientTemp"] for r in valid_rows if r.get("ambientTemp") is not None]

    avg_g = round(sum(g_vals) / len(g_vals), 1) if g_vals else None
    avg_t = round(sum(t_vals) / len(t_vals), 1) if t_vals else None
    avg_tamb = round(sum(tamb_vals) / len(tamb_vals), 1) if tamb_vals else None

    # Derive Maximum Power Point (MPP) from measurements
    max_pt = max(valid_rows, key=lambda r: (r.get("calculatedPower") if r.get("calculatedPower") is not None else ((r.get("voltage") or 0) * (r.get("current") or 0))))
    v_mpp = max_pt.get("voltage")
    i_mpp = max_pt.get("current")
    p_meas_mpp = max_pt.get("measuredPower")
    ts_repr = valid_rows[0].get("timestamp") or datetime.now().strftime("%Y-%m-%dT%H:%M:%S")

    # Curve sweep sufficiency check: minimum 5 points spanning at least 3V and 0.3A
    voltages = [r["voltage"] for r in valid_rows]
    currents = [r["current"] for r in valid_rows]
    v_span = max(voltages) - min(voltages)
    i_span = max(currents) - min(currents)
    is_curve = (len(valid_rows) >= 5 and v_span > 3.0 and i_span > 0.3) or parsed.get("isIvSweep", False)

    source_tag = "SAMPLE_DATASET" if is_sample else ("CSV_IV_SWEEP" if is_curve else "CSV_MEASUREMENT")

    # Run diagnostic fault evaluation
    diag_res = diagnose_electrical_fault(
        panel_id=target_pid,
        voltage=v_mpp,
        current=i_mpp,
        measured_power=p_meas_mpp,
        irradiance=avg_g,
        panel_temp=avg_t,
        ambient_temp=avg_tamb,
        source=source_tag,
        notes=f"Auto-analyzed from {'sample dataset' if is_sample else 'uploaded CSV'} ({len(valid_rows)} points)",
        timestamp=ts_repr,
        iv_points=valid_rows if is_curve else None,
        panel_spec=specs
    )

    if not diag_res.get("success", False):
        return diag_res

    # If insufficient points (< 5), guarantee Voc, Isc, Fill Factor, Rs, Rsh are None (never invented)
    if not is_curve:
        diag_res["hasIvCurve"] = False
        diag_res["voc"] = None
        diag_res["isc"] = None
        diag_res["fillFactor"] = None
        diag_res["rsEst"] = None
        diag_res["rshEst"] = None
        diag_res["diodeStepDetected"] = False
        diag_res["curvePoints"] = valid_rows
        diag_res["dataQualityWarnings"].append(
            f"Dataset contains {len(valid_rows)} measurement points (minimum 5 required for full curve sweep). "
            "Voc, Isc, Fill Factor, and series/shunt resistances are not estimated to prevent data fabrication."
        )

    # Distinct data source identification
    data_source_badge = "SAMPLE_DATASET" if is_sample else "REAL_UPLOAD"
    data_source_label = "DEMO / SAMPLE DATASET (Simulated Reference)" if is_sample else "REAL MEASUREMENTS (Uploaded Telemetry)"
    raw_fname = filename or ("sample_demonstration.csv" if is_sample else "uploaded_measurements.csv")

    diag_res["isSample"] = bool(is_sample)
    diag_res["dataSource"] = data_source_badge
    diag_res["dataSourceLabel"] = data_source_label
    diag_res["filename"] = raw_fname
    diag_res["rawFilename"] = raw_fname

    # Extracted inputs summary
    diag_res["extractedInputs"] = {
        "rawFilename": raw_fname,
        "filename": raw_fname,
        "dataSource": data_source_badge,
        "dataSourceLabel": data_source_label,
        "totalRows": len(valid_rows) + parsed.get("rejectedCount", 0),
        "validRowsCount": len(valid_rows),
        "rejectedRowsCount": parsed.get("rejectedCount", 0),
        "detectedColumns": parsed.get("detectedColumns", {}),
        "detectedHeader": parsed.get("detectedHeader", []),
        "isIvSweep": is_curve,
        "irradiance": avg_g,
        "panelTemp": avg_t,
        "ambientTemp": avg_tamb,
        "environmentalContext": {
            "irradiance": avg_g,
            "panelTemperature": avg_t,
            "ambientTemperature": avg_tamb
        }
    }

    # Calculated parameters summary
    pmax_val = diag_res.get("pmp") or diag_res.get("calculatedPower") or round(v_mpp * i_mpp, 2)
    diag_res["calculatedParameters"] = {
        "calculatedPower": diag_res.get("calculatedPower") or round(v_mpp * i_mpp, 2),
        "pmaxCalculated": pmax_val,
        "pmax": pmax_val,
        "vmp": diag_res.get("vmp"),
        "imp": diag_res.get("imp"),
        "voc": diag_res.get("voc"),
        "isc": diag_res.get("isc"),
        "fillFactor": diag_res.get("fillFactor"),
        "rsEst": diag_res.get("rsEst"),
        "rshEst": diag_res.get("rshEst"),
        "seriesResistance": diag_res.get("rsEst"),
        "shuntResistance": diag_res.get("rshEst"),
        "expectedPower": diag_res.get("expectedPower"),
        "performanceRatio": diag_res.get("performanceRatio"),
        "performanceRatioType": diag_res.get("perfRatioType"),
        "powerDiscrepancyPercent": diag_res.get("powerDiscrepancyPercent")
    }

    # Hypotheses list
    diag_res["diagnosticHypotheses"] = diag_res.get("evidence", [])
    diag_res["hypotheses"] = [{"hypothesis": h} for h in diag_res.get("evidence", [])]

    # Software-only disclaimer
    diag_res["safetyNotice"] = (
        "SolarShield Electrical Diagnostics is a software diagnostic tool analyzing ingested telemetry. "
        "No live physical measurement hardware is connected."
    )

    # Attach validation details for feedback in UI and tests
    diag_res["validation"] = {
        "validRows": len(valid_rows),
        "validRowsCount": len(valid_rows),
        "rejectedRows": parsed.get("rejectedRows", []),
        "rejectedRowsCount": parsed.get("rejectedCount", 0),
        "hasFullCurve": is_curve
    }
    diag_res["detectedColumns"] = parsed.get("detectedColumns", {})
    diag_res["validCount"] = len(valid_rows)
    diag_res["validRowsCount"] = len(valid_rows)
    diag_res["rejectedCount"] = parsed.get("rejectedCount", 0)
    diag_res["rejectedRowsCount"] = parsed.get("rejectedCount", 0)
    diag_res["rejectedRows"] = parsed.get("rejectedRows", [])

    return diag_res
