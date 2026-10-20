"""
Generates 5 realistic, physically consistent synthetic Photovoltaic (PV)
Electroluminescence (EL) reference demonstration frames in data/el_samples/.
Each frame includes wafer texture, busbars, front-contact fingers, and specific defect physics.
"""

import os
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(BASE_DIR, "data", "el_samples")
os.makedirs(OUT_DIR, exist_ok=True)

DIM = 800

def create_base_wafer(seed=42):
    """
    Creates a base crystalline silicon wafer in forward-biased EL state:
    - High uniform carrier luminescence (~185 grayscale)
    - Pseudo-square wafer with chamfered / rounded corners
    - Slight silicon crystal grain texture
    - 3 vertical busbars (dark conductive ribbons)
    - Periodic horizontal front-contact grid fingers
    """
    np.random.seed(seed)
    
    # 1. Base luminescent emission with subtle silicon grain noise
    base_lum = np.ones((DIM, DIM), dtype=np.float32) * 190.0
    grain = np.random.normal(0.0, 4.0, (DIM, DIM)).astype(np.float32)
    # Radial vignetting (subtle edge drop)
    y, x = np.ogrid[:DIM, :DIM]
    center_y, center_x = DIM / 2.0, DIM / 2.0
    dist_from_center = np.sqrt((x - center_x)**2 + (y - center_y)**2) / (DIM / 2.0)
    vignette = 1.0 - 0.08 * (dist_from_center ** 2)
    
    img_arr = np.clip((base_lum + grain) * vignette, 0, 255)
    
    # 2. Mask out background outside wafer (wafer margin: 30px, chamfered corners: 80px)
    margin = 30
    chamfer = 85
    wafer_mask = np.zeros((DIM, DIM), dtype=bool)
    wafer_mask[margin:DIM-margin, margin:DIM-margin] = True
    
    # Cut chamfered corners (monocrystalline pseudo-square)
    for i in range(margin, margin + chamfer):
        for j in range(margin, margin + chamfer - (i - margin)):
            wafer_mask[i, j] = False
            wafer_mask[i, DIM - 1 - j] = False
            wafer_mask[DIM - 1 - i, j] = False
            wafer_mask[DIM - 1 - i, DIM - 1 - j] = False
            
    img_arr[~wafer_mask] = 12.0  # Dark background outside cell
    
    img = Image.fromarray(img_arr.astype(np.uint8), mode="L")
    draw = ImageDraw.Draw(img)
    
    # 3. Horizontal front grid fingers (spaced every 14 px)
    for gy in range(margin + 5, DIM - margin - 5, 14):
        # Draw faint dark finger line across wafer
        draw.line([(margin + 5, gy), (DIM - margin - 5, gy)], fill=155, width=1)
        
    # 4. Vertical conductive Busbars (3 busbars at 25%, 50%, 75%)
    busbar_x_positions = [int(DIM * 0.25), int(DIM * 0.50), int(DIM * 0.75)]
    for bx in busbar_x_positions:
        # Busbar ribbon width ~ 8 px, darker (~50 grayscale)
        draw.rectangle([bx - 4, margin + 4, bx + 4, DIM - margin - 4], fill=55)
        # Solder contact pads
        for py in [int(DIM * 0.2), int(DIM * 0.5), int(DIM * 0.8)]:
            draw.rectangle([bx - 7, py - 12, bx + 7, py + 12], fill=40)
            
    return img, busbar_x_positions, wafer_mask

def save_sample_and_meta(name, img, meta_data):
    png_path = os.path.join(OUT_DIR, f"{name}.png")
    json_path = os.path.join(OUT_DIR, f"{name}_meta.json")
    
    # Save as 8-bit grayscale PNG
    img.save(png_path, format="PNG")
    
    meta_data["name"] = name
    meta_data["filename"] = f"{name}.png"
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(meta_data, f, indent=2)
        
    print(f"Generated {name} -> {png_path}")

def generate_all():
    # --------------------------------------------------------------------------
    # 1. Nominal Monocrystalline Silicon Cell
    # --------------------------------------------------------------------------
    img_nom, _, _ = create_base_wafer(seed=101)
    meta_nom = {
        "title": "Nominal Monocrystalline Silicon Cell",
        "defectCategory": "UNIFORM_NOMINAL_EMISSION",
        "severity": "LOW",
        "maintenancePriority": "LOW",
        "anomalyScore": 6.8,
        "inactiveAreaPercent": 0.0,
        "crackPixelCount": 0,
        "description": "Uniform near-infrared luminescence across crystalline wafer. Intact busbars and front finger grid; no microcracks or dark shunts.",
        "whyFlagged": "Nominal radiative carrier recombination across entire active silicon area.",
        "recommendedAction": "Maintain standard periodic operational surveillance.",
        "cellType": "Mono-Si PERC M10 (182mm)"
    }
    save_sample_and_meta("el_nominal_cell", img_nom, meta_nom)

    # --------------------------------------------------------------------------
    # 2. Dendritic Wafer Microcrack
    # --------------------------------------------------------------------------
    img_crack, _, _ = create_base_wafer(seed=202)
    c_draw = ImageDraw.Draw(img_crack)
    # Fine dark dendritic crack points traversing from bottom-left across central busbar
    crack_pts = [
        (110, 580), (145, 540), (190, 510), (230, 485), (280, 440),
        (335, 410), (395, 375), (450, 360), (510, 320), (565, 305), (620, 275)
    ]
    # Draw crack branches
    for i in range(len(crack_pts) - 1):
        p1 = crack_pts[i]
        p2 = crack_pts[i+1]
        c_draw.line([p1, p2], fill=25, width=3)
        # Minor fracture branching
        if i in (3, 6, 8):
            bx = p1[0] + 25
            by = p1[1] + (20 if i % 2 == 0 else -25)
            c_draw.line([p1, (bx, by)], fill=35, width=2)
    # Apply subtle local blurring to crack
    meta_crack = {
        "title": "Dendritic Wafer Microcrack",
        "defectCategory": "MICROCRACK",
        "severity": "HIGH",
        "maintenancePriority": "HIGH",
        "anomalyScore": 68.4,
        "inactiveAreaPercent": 1.2,
        "crackPixelCount": 420,
        "description": "Wafer microcrack propagating diagonally across central busbar, creating localized recombination centers.",
        "whyFlagged": "Sub-millimeter crystal microcrack localized via morphological top-hat filtering; risk of mechanical fracture propagation.",
        "recommendedAction": "Perform high-resolution electroluminescence survey under mechanical load; monitor during next field audit.",
        "box": {"x": 110, "y": 275, "width": 510, "height": 305},
        "cellType": "Mono-Si PERC M10"
    }
    save_sample_and_meta("el_microcrack_dendritic", img_crack, meta_crack)

    # --------------------------------------------------------------------------
    # 3. Electrically Isolated Inactive Cell Region
    # --------------------------------------------------------------------------
    img_inact, _, _ = create_base_wafer(seed=303)
    # A traversing crack has completely severed the top-right corner region.
    # The detached polygon has NO electrical path to busbars, so it emits ZERO luminescence (dark island).
    arr_inact = np.array(img_inact, dtype=np.float32)
    # Disconnected corner polygon: x > 510 and y < 380, slope line x + y > 760
    y_idx, x_idx = np.ogrid[:DIM, :DIM]
    poly_mask = (x_idx >= 490) & (y_idx <= 340) & ((x_idx - 490) + (340 - y_idx) >= 140)
    # Apply severe luminescence dropout (almost black ~28 grayscale)
    arr_inact[poly_mask] = np.random.normal(32.0, 3.0, np.sum(poly_mask))
    
    img_inact = Image.fromarray(np.clip(arr_inact, 0, 255).astype(np.uint8), mode="L")
    i_draw = ImageDraw.Draw(img_inact)
    # Draw dark crack line separating the inactive piece
    i_draw.line([(490, 340), (630, 200), (740, 110)], fill=15, width=4)
    
    meta_inact = {
        "title": "Electrically Disconnected Inactive Region",
        "defectCategory": "DISCONNECTED_CELL_REGION",
        "severity": "CRITICAL",
        "maintenancePriority": "URGENT",
        "anomalyScore": 92.6,
        "inactiveAreaPercent": 14.8,
        "crackPixelCount": 310,
        "description": "Severed corner wafer piece completely isolated from carrier injection, emitting no electroluminescence (dead cell zone).",
        "whyFlagged": "Critical inactive cell region (14.8% area loss). High reverse-bias shunting and thermal hotspot risk under solar illumination.",
        "recommendedAction": "Immediate module isolation and replacement. Conduct dark I-V curve tracing to verify sub-string derating.",
        "box": {"x": 490, "y": 30, "width": 280, "height": 310},
        "cellType": "Bifacial Dual-Glass Monocrystalline"
    }
    save_sample_and_meta("el_isolated_inactive_region", img_inact, meta_inact)

    # --------------------------------------------------------------------------
    # 4. Broken Cell-Finger & Interconnect Pattern
    # --------------------------------------------------------------------------
    img_finger, _, _ = create_base_wafer(seed=404)
    arr_finger = np.array(img_finger, dtype=np.float32)
    # Interrupted fingers across central horizontal swath between busbar 1 and 2
    # Creates horizontal bands of reduced luminescence
    for band_y in [280, 360, 440, 520]:
        y_span = (y_idx >= band_y) & (y_idx <= band_y + 26) & (x_idx >= 210) & (x_idx <= 390)
        arr_finger[y_span] *= 0.52  # 48% luminescence drop
        
    img_finger = Image.fromarray(np.clip(arr_finger, 0, 255).astype(np.uint8), mode="L")
    meta_finger = {
        "title": "Broken Cell-Finger & Interconnect Failure",
        "defectCategory": "BROKEN_FINGER_INTERCONNECT",
        "severity": "MEDIUM",
        "maintenancePriority": "MEDIUM",
        "anomalyScore": 54.2,
        "inactiveAreaPercent": 3.8,
        "crackPixelCount": 110,
        "description": "Front-contact silver grid finger interruptions causing localized current crowding and dark horizontal stripes.",
        "whyFlagged": "Interrupted finger metallization grid between busbar 1 and 2 resulting in localized carrier attenuation.",
        "recommendedAction": "Measure module series resistance (Rs) via flash test; inspect front glass for micro-impact trauma.",
        "box": {"x": 210, "y": 280, "width": 180, "height": 266},
        "cellType": "Mono-Si PERC M10"
    }
    save_sample_and_meta("el_finger_interconnect_break", img_finger, meta_finger)

    # --------------------------------------------------------------------------
    # 5. Dark Shunt & Luminescence Quenching
    # --------------------------------------------------------------------------
    img_shunt, _, _ = create_base_wafer(seed=505)
    arr_shunt = np.array(img_shunt, dtype=np.float32)
    # Shunt center at (360, 480)
    sc_x, sc_y = 360.0, 480.0
    r_dist = np.sqrt((x_idx - sc_x)**2 + (y_idx - sc_y)**2)
    # Gaussian quenching profile (radius ~ 65px)
    quench_profile = np.exp(- (r_dist ** 2) / (2 * (42.0 ** 2)))
    arr_shunt = np.clip(arr_shunt * (1.0 - 0.72 * quench_profile), 0, 255)
    
    img_shunt = Image.fromarray(arr_shunt.astype(np.uint8), mode="L")
    meta_shunt = {
        "title": "Localized Dark Shunt Quenching",
        "defectCategory": "DARK_INACTIVE_SHUNT",
        "severity": "MEDIUM",
        "maintenancePriority": "MEDIUM",
        "anomalyScore": 48.5,
        "inactiveAreaPercent": 2.4,
        "crackPixelCount": 65,
        "description": "High-conductance localized shunting channel quenching radiative carrier recombination in surrounding silicon matrix.",
        "whyFlagged": "Severe localized luminescence quenching; parasitic shunt drawing leakage current.",
        "recommendedAction": "Perform Infrared Thermography (IRT) cross-check to evaluate if shunt generates a thermal dissipation hotspot.",
        "box": {"x": 300, "y": 420, "width": 120, "height": 120},
        "cellType": "Polycrystalline 72-cell"
    }
    save_sample_and_meta("el_dark_shunt_quenched", img_shunt, meta_shunt)

if __name__ == "__main__":
    generate_all()
