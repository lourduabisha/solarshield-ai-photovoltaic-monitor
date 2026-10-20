"""
Generate Realistic Calibrated Electrical & I-V Curve Reference Samples
======================================================================
Generates 5 distinct I-V sweep datasets, a multi-panel timeseries log,
a malformed test CSV, and a downloadable template.
"""

import os
import json
import math

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", "electrical_samples")
os.makedirs(OUTPUT_DIR, exist_ok=True)

def generate_iv_points(voc, isc, vmp, imp, n_points=25, step_v=None, step_i=None, rs=0.2, rsh=1000.0):
    """Generates synthetic (V, I) points along a realistic solar cell diode characteristic."""
    points = []
    v_values = [i * (voc / (n_points - 1)) for i in range(n_points)]
    
    for v in v_values:
        # Standard two-resistor single-diode model approximation
        if step_v is not None and v > step_v and step_i is not None:
            # Bypass diode conduction step: current drops to step_i and rolls off to Voc
            if v >= voc:
                curr = 0.0
            else:
                v_frac = (v - step_v) / (voc - step_v)
                curr = step_i * (1.0 - (v_frac ** 2.0))
        else:
            if v <= vmp:
                # Flat current region modified by Rsh
                i_ideal = isc - (v / rsh)
                # Soft curvature towards MPP
                ratio = (v / vmp) ** 4.5
                curr = i_ideal - (isc - imp) * ratio
            else:
                # Steep drop towards Voc modified by Rs
                if v >= voc:
                    curr = 0.0
                else:
                    v_excess = (v - vmp) / (voc - vmp)
                    curr = imp * (1.0 - (v_excess ** (1.8 / max(rs, 0.1))))
        
        curr = max(0.0, curr)
        points.append({
            "voltage": round(v, 2),
            "current": round(curr, 2),
            "power": round(v * curr, 2)
        })
    return points


def save_iv_csv(filename, points, panel_id, irradiance, temp, source, notes, title, desc, anomaly_type):
    csv_path = os.path.join(OUTPUT_DIR, filename)
    with open(csv_path, "w", encoding="utf-8") as f:
        f.write("# SolarShield Calibrated Electrical Measurement Dataset\n")
        f.write(f"# Title: {title}\n")
        f.write(f"# Panel: {panel_id} | Irradiance: {irradiance} W/m2 | Temperature: {temp} °C\n")
        f.write("point_index,voltage_v,current_a,measured_power_w,irradiance_wm2,panel_temp_c,panel_id,source,notes\n")
        for idx, pt in enumerate(points, 1):
            f.write(f"{idx},{pt['voltage']},{pt['current']},{pt['power']},{irradiance},{temp},{panel_id},{source},{notes}\n")

    meta = {
        "filename": filename,
        "title": title,
        "panelId": panel_id,
        "description": desc,
        "expectedAnomaly": anomaly_type,
        "irradiance": irradiance,
        "panelTemp": temp,
        "source": source,
        "pointsCount": len(points),
        "nominalVoc": points[-1]["voltage"],
        "nominalIsc": points[0]["current"]
    }
    meta_path = os.path.join(OUTPUT_DIR, filename.replace(".csv", "_meta.json"))
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)


def main():
    print("Generating electrical diagnostic reference samples...")

    # 1. Nominal Healthy High-Efficiency PERC
    pts_nom = generate_iv_points(voc=49.5, isc=10.85, vmp=41.5, imp=10.12, n_points=26, rs=0.2, rsh=1500.0)
    save_iv_csv(
        filename="electrical_iv_nominal.csv",
        points=pts_nom,
        panel_id="SP-001",
        irradiance=1000.0,
        temp=25.0,
        source="IV_CURVE_TRACER_HT",
        notes="Calibrated STC benchmark sweep. Clear sky.",
        title="Nominal PERC 420W (STC 1000 W/m²)",
        desc="Uniform cell current, standard Fill Factor ~0.78, no diode activation steps.",
        anomaly_type="NOMINAL_OPERATION"
    )

    # 2. Bypass Diode Conduction (Step at V ~ 27V)
    pts_diode = generate_iv_points(voc=49.5, isc=10.85, vmp=41.5, imp=10.12, n_points=26, step_v=26.5, step_i=6.8, rs=0.25, rsh=800.0)
    save_iv_csv(
        filename="electrical_iv_bypass_diode.csv",
        points=pts_diode,
        panel_id="SP-002",
        irradiance=1000.0,
        temp=25.0,
        source="IV_CURVE_TRACER_HT",
        notes="Step at 27V indicating 1 sub-string bypass diode conduction.",
        title="Bypass Diode Activation (Step at 27V)",
        desc="Pronounced step/notch in I-V curve caused by 1 shaded or cracked sub-string.",
        anomaly_type="BYPASS_DIODE_CONDUCTION"
    )

    # 3. Severe Soiling / Current Depression (35% Current Loss)
    pts_soil = generate_iv_points(voc=49.0, isc=7.05, vmp=41.0, imp=6.58, n_points=26, rs=0.22, rsh=1200.0)
    save_iv_csv(
        filename="electrical_iv_soiling_current_loss.csv",
        points=pts_soil,
        panel_id="SP-003",
        irradiance=1000.0,
        temp=26.5,
        source="IV_CURVE_TRACER_HT",
        notes="Uniform surface dust accumulation causing 35% current reduction.",
        title="Uniform Soiling / Dust (35% Current Loss)",
        desc="Normal open-circuit voltage but current suppressed by 35% due to surface soiling.",
        anomaly_type="CURRENT_DEPRESSION_SOILING"
    )

    # 4. High Series Resistance (Soft Knee, Degraded FF)
    pts_rs = generate_iv_points(voc=49.5, isc=10.85, vmp=35.0, imp=8.2, n_points=26, rs=1.35, rsh=800.0)
    save_iv_csv(
        filename="electrical_iv_high_rs.csv",
        points=pts_rs,
        panel_id="SP-004",
        irradiance=1000.0,
        temp=32.0,
        source="SEAWARD_PV200",
        notes="Degraded fill factor ~0.61 with soft knee curve and ribbon corrosion.",
        title="High Series Resistance (Ribbon Degradation)",
        desc="Softened curve knee caused by elevated internal series resistance (corroded ribbons).",
        anomaly_type="HIGH_SERIES_RESISTANCE"
    )

    # 5. Low Shunt Resistance / PID
    pts_rsh = generate_iv_points(voc=48.2, isc=10.85, vmp=38.0, imp=9.1, n_points=26, rs=0.25, rsh=75.0)
    save_iv_csv(
        filename="electrical_iv_low_rsh_pid.csv",
        points=pts_rsh,
        panel_id="SP-005",
        irradiance=980.0,
        temp=28.0,
        source="SEAWARD_PV200",
        notes="Steep current slope in low-voltage region (Rsh < 100 Ω).",
        title="Low Shunt Resistance / PID Leakage",
        desc="Severe current leakage slope across low voltages indicative of cell shunting or PID.",
        anomaly_type="LOW_SHUNT_RESISTANCE"
    )

    # 6. Multi-Panel Fleet Operating Log (Time-Series)
    timeseries_path = os.path.join(OUTPUT_DIR, "electrical_timeseries_fleet.csv")
    with open(timeseries_path, "w", encoding="utf-8") as f:
        f.write("timestamp,panel_id,voltage_v,current_a,measured_power_w,irradiance_wm2,panel_temp_c,ambient_temp_c,source,notes\n")
        rows = [
            ("2026-10-09T10:00:00", "SP-001", 41.4, 10.05, 416.1, 1000.0, 25.0, 22.0, "INVERTER_MPPT", "Morning baseline scan"),
            ("2026-10-09T10:15:00", "SP-002", 27.6, 9.95, 274.6, 980.0, 26.5, 22.5, "INVERTER_MPPT", "Zone A East string 2"),
            ("2026-10-09T10:30:00", "SP-003", 41.0, 6.50, 266.5, 990.0, 27.0, 23.0, "INVERTER_MPPT", "Zone B Central soiling"),
            ("2026-10-09T10:45:00", "SP-004", 36.2, 8.40, 304.1, 950.0, 30.0, 23.5, "INVERTER_MPPT", "High resistance test"),
            ("2026-10-09T11:00:00", "SP-005", 38.5, 9.20, 354.2, 970.0, 28.5, 24.0, "INVERTER_MPPT", "Array Beta string 1"),
            ("2026-10-09T11:15:00", "SP-006", 48.9, 0.04, 2.0, 960.0, 29.0, 24.0, "INVERTER_MPPT", "Open circuit test")
        ]
        for r in rows:
            f.write(",".join(map(str, r)) + "\n")

    # 7. Malformed / Corrupt CSV sample (for testing rejected rows reporting)
    corrupt_path = os.path.join(OUTPUT_DIR, "electrical_corrupt_sample.csv")
    with open(corrupt_path, "w", encoding="utf-8") as f:
        f.write("timestamp,panel_id,voltage_v,current_a,measured_power_w,irradiance_wm2,panel_temp_c,source\n")
        f.write("2026-10-09T12:00:00,SP-001,41.2,10.0,412.0,1000.0,25.0,MANUAL\n")
        f.write("2026-10-09T12:05:00,SP-001,CORRUPT_VOLTAGE,10.0,,1000.0,25.0,MANUAL\n")  # Rejected: invalid voltage
        f.write("2026-10-09T12:10:00,SP-001,41.5,INVALID_CURRENT,,1000.0,25.0,MANUAL\n")  # Rejected: invalid current
        f.write("2026-10-09T12:15:00,SP-001,41.3,10.1,417.1,1000.0,999.0,MANUAL\n")         # Rejected: temp 999C out of bounds
        f.write("2026-10-09T12:20:00,SP-001,41.1,10.05,413.1,1000.0,26.0,MANUAL\n")        # Valid

    # 8. Downloadable Template
    template_path = os.path.join(OUTPUT_DIR, "electrical_measurement_template.csv")
    with open(template_path, "w", encoding="utf-8") as f:
        f.write("# SolarShield Photovoltaic Electrical Measurement Import Template\n")
        f.write("# Supported Columns (units indicated in header):\n")
        f.write("# - timestamp: ISO-8601 string (e.g. 2026-10-09T12:00:00)\n")
        f.write("# - panel_id: Module or String identifier (e.g. SP-001)\n")
        f.write("# - voltage_v: Terminal operating voltage in Volts (Required)\n")
        f.write("# - current_a: Operating current in Amperes (Required)\n")
        f.write("# - measured_power_w: Measured active power from meter/inverter in Watts (Optional)\n")
        f.write("# - irradiance_wm2: Solar plane-of-array irradiance in W/m² (Optional but recommended for STC derating)\n")
        f.write("# - panel_temp_c: Back-of-module cell temperature in °C (Optional)\n")
        f.write("# - ambient_temp_c: Ambient shade temperature in °C (Optional)\n")
        f.write("# - source: Instrument name (e.g. MULTIMETER, INVERTER_MPPT, IV_TRACER)\n")
        f.write("# - notes: Operating conditions, weather, soiling observations (Optional)\n")
        f.write("timestamp,panel_id,voltage_v,current_a,measured_power_w,irradiance_wm2,panel_temp_c,ambient_temp_c,source,notes\n")
        f.write("2026-10-09T12:00:00,SP-001,41.5,10.1,419.1,1000.0,25.0,22.0,IV_CURVE_TRACER,Clear sky benchmark\n")
        f.write("2026-10-09T12:15:00,SP-002,27.5,9.9,272.2,980.0,28.0,23.0,HANDHELD_MULTIMETER,Partial shading suspected\n")

    print(f"Successfully generated 8 sample files in {OUTPUT_DIR}")

if __name__ == "__main__":
    main()
