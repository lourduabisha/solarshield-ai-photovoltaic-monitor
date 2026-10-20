"""
SolarShield EL Electroluminescence Diagnostic Engine
=====================================================
Dedicated, explainable computer vision pipeline for Photovoltaic (PV)
Electroluminescence (EL) image analysis.

Carrier Recombination Luminescence Physics:
Under forward electrical bias in the dark, crystalline silicon solar cells emit
near-infrared photons (~1150 nm) through radiative electron-hole recombination.
Healthy silicon produces uniform luminescence. Structural wafer defects,
microcracks, wafer fractures, electrically disconnected inactive cell regions,
broken grid fingers, and localized shunts exhibit reduced or quenched emission.

Pipeline Components:
1. Input validation & Modality verification (monochrome NIR vs daylight RGB photo / thermal scan)
2. Image quality assessment (Dynamic range, SNR, Sharpness, Illumination uniformity)
3. Active wafer envelope segmentation & Busbar / Grid-Finger decoupling
4. Mathematical morphological Black Top-Hat filtering for crack / microcrack detection
5. Connected-component luminescence analysis for electrically disconnected inactive cell zones
6. Broken grid finger and interconnect interruption analysis
7. Localized dark shunt quenching detection
8. Defect categorization, severity rating, and explainable Heuristic Anomaly Index
9. Precision annotated diagnostic HUD image generation and enhanced contrast matrix
"""

import os
import time
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageEnhance

# Model Tag and Engine Identification
EL_ENGINE_NAME = "SolarShield EL Heuristic Diagnostic Engine"
EL_PIPELINE_VERSION = "v1.0-heuristic (Morphology & Carrier Luminescence Analysis)"
EL_SCORE_TYPE = "Heuristic Anomaly Index (0-100 Rule-Based Feature Extraction)"
EL_SUPPORTED_FORMATS = [".png", ".jpg", ".jpeg", ".tif", ".tiff", ".webp"]
MAX_IMAGE_FILE_SIZE_BYTES = 15 * 1024 * 1024  # 15 MB max
MIN_DIMENSION = 64
MAX_DIMENSION = 8192

# Defect Categories
CAT_NOMINAL = "UNIFORM_NOMINAL_EMISSION"
CAT_MICROCRACK = "MICROCRACK"
CAT_DISCONNECTED = "DISCONNECTED_CELL_REGION"
CAT_BROKEN_FINGER = "BROKEN_FINGER_INTERCONNECT"
CAT_DARK_SHUNT = "DARK_INACTIVE_SHUNT"


class ELModelInterface:
    """Extensible interface for future trained deep learning (CNN/Transformer) models."""
    def is_available(self) -> bool:
        return False

    def predict(self, img_array: np.ndarray) -> dict:
        raise NotImplementedError("Deep learning inference model not loaded.")


class ELCnnModel(ELModelInterface):
    """
    Placeholder class for a trained PyTorch/ONNX/TensorFlow EL deep learning model.
    Documented Input Requirements:
      - Spatial dimension: 512x512 or 1024x1024 single-channel float32 tensor
      - Normalization: Mean=0.485, Std=0.229 or [0.0, 1.0]
      - Supported Output: Multi-class defect logits or pixel-level segmentation mask
    Currently unvalidated / no trained model weights exist in repository.
    """
    def __init__(self, weights_path=None):
        self.weights_path = weights_path
        self.is_loaded = False

    def is_available(self) -> bool:
        return self.is_loaded and self.weights_path and os.path.exists(self.weights_path)


def validate_el_image_file(file_path: str, max_size_bytes=MAX_IMAGE_FILE_SIZE_BYTES):
    """
    Validates file existence, format, readable decoding, dimensions, and size.
    Returns: (is_valid: bool, error_message: str or None, pil_image: Image or None)
    """
    if not file_path or not os.path.exists(file_path):
        return False, "File does not exist or file path is invalid.", None

    file_size = os.path.getsize(file_path)
    if file_size == 0:
        return False, "Supplied file is empty (0 bytes).", None

    if file_size > max_size_bytes:
        size_mb = file_size / (1024 * 1024)
        max_mb = max_size_bytes / (1024 * 1024)
        return False, f"File size exceeds maximum allowable limit ({size_mb:.1f} MB > {max_mb:.0f} MB).", None

    ext = os.path.splitext(file_path)[1].lower()
    if ext not in EL_SUPPORTED_FORMATS:
        return False, f"Unsupported file extension '{ext}'. Supported formats: PNG, JPG, JPEG, TIFF, WEBP.", None

    try:
        img = Image.open(file_path)
        img.verify()
    except Exception as e:
        return False, f"Corrupted or unreadable image file: {str(e)}", None

    try:
        # Re-open after verify() closes the file pointer
        img = Image.open(file_path)
        img.load()
    except Exception as e:
        return False, f"Failed to decode image raster data: {str(e)}", None

    w, h = img.size
    if w < MIN_DIMENSION or h < MIN_DIMENSION:
        return False, f"Image dimensions too small ({w}x{h} px). Minimum required: {MIN_DIMENSION}x{MIN_DIMENSION} px.", None

    if w > MAX_DIMENSION or h > MAX_DIMENSION:
        return False, f"Image dimensions exceed maximum supported resolution ({w}x{h} px).", None

    return True, None, img


def detect_modality_mismatch(img_rgb: Image.Image):
    """
    Analyzes chromatic distribution and channel divergence.
    True EL inspection is near-infrared luminescence (monochromatic silicon emission, R ~ G ~ B).
    Daylight RGB photographs or false-color IR images have high color saturation and divergence.
    
    Returns:
      is_mismatch: bool (True if image appears to be normal RGB photo or false-color IR)
      chroma_score: float (mean inter-channel difference)
      mismatch_type: str ("RGB_VISIBLE_PHOTOGRAPH", "FALSE_COLOR_THERMAL", "MONOCHROME_EL_CANDIDATE")
      warning_msg: str or None
    """
    rgb_arr = np.array(img_rgb.convert("RGB"), dtype=np.float32)
    r = rgb_arr[:, :, 0]
    g = rgb_arr[:, :, 1]
    b = rgb_arr[:, :, 2]

    diff_rg = np.abs(r - g)
    diff_gb = np.abs(g - b)
    diff_br = np.abs(b - r)
    chroma_score = float(np.mean(diff_rg + diff_gb + diff_br))

    # Also check HSV saturation
    hsv_img = img_rgb.convert("HSV")
    hsv_arr = np.array(hsv_img, dtype=np.float32)
    sat = hsv_arr[:, :, 1]
    mean_sat = float(np.mean(sat))

    if chroma_score > 30.0 or mean_sat > 45.0:
        return (
            True,
            chroma_score,
            "RGB_VISIBLE_PHOTOGRAPH",
            f"Modality Mismatch Warning: Visible-spectrum chromatic distribution detected (Chroma Index: {chroma_score:.1f}, Saturation: {mean_sat:.1f}/255). Standard color photography captures ambient optical reflectance, not near-infrared radiative carrier recombination. An ordinary RGB photograph cannot be interpreted as an EL scan."
        )
    elif chroma_score > 18.0 or mean_sat > 25.0:
        return (
            True,
            chroma_score,
            "SUSPECTED_NON_EL",
            f"Modality Alert: Noticeable chromatic divergence detected (Chroma Index: {chroma_score:.1f}). Electroluminescence captures are monochromatic NIR emissions. Please confirm acquisition modality."
        )
    else:
        return (
            False,
            chroma_score,
            "MONOCHROME_EL_CANDIDATE",
            None
        )


def assess_image_quality(gray_arr: np.ndarray):
    """
    Assesses image contrast, sharpness, SNR estimate, and illumination uniformity.
    Returns dictionary with metrics and quality rating.
    """
    h, w = gray_arr.shape

    # Dynamic Range
    p2 = float(np.percentile(gray_arr, 2))
    p98 = float(np.percentile(gray_arr, 98))
    dynamic_range = max(1.0, p98 - p2)

    # Sharpness via Laplacian gradient approximation
    gy = np.abs(gray_arr[2:, 1:-1] - 2 * gray_arr[1:-1, 1:-1] + gray_arr[:-2, 1:-1])
    gx = np.abs(gray_arr[1:-1, 2:] - 2 * gray_arr[1:-1, 1:-1] + gray_arr[1:-1, :-2])
    sharpness = float(np.mean(gy + gx))

    # SNR estimate in active luminescent region
    med = float(np.median(gray_arr))
    bright_mask = gray_arr > med
    if np.any(bright_mask):
        mu_b = float(np.mean(gray_arr[bright_mask]))
        sigma_b = float(np.std(gray_arr[bright_mask])) + 1e-4
        snr_db = round(float(20 * np.log10(max(1.0, mu_b / sigma_b))), 1)
    else:
        snr_db = 0.0

    # Illumination uniformity (Center vs Corners)
    ch, cw = int(h * 0.2), int(w * 0.2)
    center_patch = gray_arr[int(h*0.4):int(h*0.6), int(w*0.4):int(w*0.6)]
    corners = [
        gray_arr[:ch, :cw],
        gray_arr[:ch, -cw:],
        gray_arr[-ch:, :cw],
        gray_arr[-ch:, -cw:]
    ]
    corner_mean = float(np.mean([np.mean(c) for c in corners]))
    center_mean = float(np.mean(center_patch))
    uniformity_ratio = round(corner_mean / max(1.0, center_mean), 2)

    warnings = []
    if dynamic_range < 40.0:
        warnings.append("Low luminescence dynamic range (insufficient contrast).")
    if sharpness < 1.8:
        warnings.append("Image appears blurred or out of focus.")
    if snr_db < 9.0:
        warnings.append("High sensor noise / low signal-to-noise ratio.")
    if uniformity_ratio < 0.30:
        warnings.append("Severe vignetting or optical edge falloff detected.")

    if len(warnings) == 0:
        rating = "EXCELLENT" if (dynamic_range > 110.0 and sharpness > 4.0) else "GOOD"
    elif len(warnings) <= 1:
        rating = "ACCEPTABLE"
    else:
        rating = "DEGRADED_LOW_CONTRAST"

    return {
        "dynamicRange": round(dynamic_range, 1),
        "sharpness": round(sharpness, 2),
        "snrDb": snr_db,
        "uniformityRatio": uniformity_ratio,
        "rating": rating,
        "warnings": warnings,
        "needsReview": rating == "DEGRADED_LOW_CONTRAST"
    }


def remove_horizontal_fingers(gray_arr: np.ndarray, k: int = 5) -> np.ndarray:
    """
    Applies 1D vertical median filter of height k to remove thin horizontal front grid fingers
    without blurring diagonal/vertical cracks or inactive cell boundaries.
    """
    h, w = gray_arr.shape
    pad = k // 2
    padded = np.pad(gray_arr, ((pad, pad), (0, 0)), mode="edge")
    windows = [padded[i:i+h, :] for i in range(k)]
    stacked = np.stack(windows, axis=0)
    return np.median(stacked, axis=0).astype(np.float32)


def analyze_el_pipeline(
    image_path: str,
    panel_id: str = "SP-001",
    confirmed_el_modality: bool = True,
    sensitivity: str = "STANDARD",
    output_dir: str = None
) -> dict:
    """
    Executes the comprehensive EL image processing pipeline.
    
    Parameters:
      image_path: Path to input image file.
      panel_id: Target solar panel ID.
      confirmed_el_modality: Whether user confirmed acquisition modality.
      sensitivity: Detection sensitivity ('LOW', 'STANDARD', 'HIGH').
      output_dir: Directory where annotated and processed images are saved.
      
    Returns:
      Comprehensive analysis result dictionary.
    """
    if not output_dir:
        output_dir = os.path.dirname(image_path)

    os.makedirs(output_dir, exist_ok=True)

    # 1. Input Validation
    is_valid, err_msg, pil_img = validate_el_image_file(image_path)
    if not is_valid:
        raise ValueError(f"Input Validation Failed: {err_msg}")

    orig_w, orig_h = pil_img.size

    # 2. Modality Verification
    is_mismatch, chroma_val, mismatch_type, mismatch_warn = detect_modality_mismatch(pil_img)
    if is_mismatch and not confirmed_el_modality:
        return {
            "success": False,
            "isModalityMismatch": True,
            "mismatchType": mismatch_type,
            "chromaScore": round(chroma_val, 1),
            "message": mismatch_warn or "Modality Mismatch: Normal RGB photograph detected instead of EL scan.",
            "panelId": panel_id,
            "needsReview": True,
            "reviewReason": "User supplied visible optical photo or false-color image; EL acquisition unconfirmed."
        }

    # 3. Standardization & Preprocessing
    target_dim = 800
    img_std = pil_img.convert("L").resize((target_dim, target_dim), Image.Resampling.LANCZOS)
    gray_arr = np.array(img_std, dtype=np.float32)

    # 4. Image Quality Assessment
    quality_info = assess_image_quality(gray_arr)

    # 5. Contrast Enhancement Matrix
    p2 = np.percentile(gray_arr, 2)
    p98 = np.percentile(gray_arr, 98)
    stretched = np.clip((gray_arr - p2) / max(1.0, p98 - p2) * 255.0, 0, 255).astype(np.uint8)

    # 6. Active Wafer Envelope Detection
    # Wafer interior is within margin ~50px to 750px
    margin = 55
    wafer_mask = np.zeros((target_dim, target_dim), dtype=bool)
    wafer_mask[margin:target_dim-margin, margin:target_dim-margin] = True
    # Mask chamfered corners
    chamfer = 75
    for i in range(margin, margin + chamfer):
        for j in range(margin, margin + chamfer - (i - margin)):
            wafer_mask[i, j] = False
            wafer_mask[i, target_dim - 1 - j] = False
            wafer_mask[target_dim - 1 - i, j] = False
            wafer_mask[target_dim - 1 - i, target_dim - 1 - j] = False

    total_wafer_px = float(np.sum(wafer_mask))

    # 7. Busbar Identification (Vertical Conductive Ribbons)
    # Detect 3 main vertical busbars where column profile has dark depressions
    col_profile = np.mean(gray_arr[wafer_mask.any(axis=1), :], axis=0)
    med_col = np.median(col_profile)
    busbar_mask = np.zeros((target_dim, target_dim), dtype=bool)

    # Check nominal positions (~25%, 50%, 75%) or detect valleys
    candidate_cols = [int(target_dim * 0.25), int(target_dim * 0.50), int(target_dim * 0.75)]
    for cand in candidate_cols:
        window = col_profile[cand-15:cand+16]
        min_c = cand - 15 + int(np.argmin(window))
        busbar_mask[:, max(0, min_c-7):min(target_dim, min_c+8)] = True

    # 8. Decouple Horizontal Front Fingers via 1D Vertical Median Filter
    # Horizontal grid fingers repeat every ~14px; 1D vertical filter of height 5 removes them cleanly
    cleaned_wafer = remove_horizontal_fingers(gray_arr, k=5)

    # Nominal active wafer baseline luminescence
    wafer_clean_px = cleaned_wafer[wafer_mask & (~busbar_mask)]
    nom_median = float(np.median(wafer_clean_px)) if len(wafer_clean_px) > 0 else 180.0

    # 9. Defect Extraction: Mathematical Morphological Black Top-Hat on Finger-Cleaned Image
    filt_size = 5 if sensitivity == "HIGH" else (5 if sensitivity == "STANDARD" else 7)
    c_pil = Image.fromarray(np.clip(cleaned_wafer, 0, 255).astype(np.uint8), mode="L")
    closed = c_pil.filter(ImageFilter.MaxFilter(filt_size)).filter(ImageFilter.MinFilter(filt_size))
    tophat = np.maximum(0.0, np.array(closed, dtype=np.float32) - cleaned_wafer)

    # Ignore busbars and background
    tophat_wafer = np.where(wafer_mask & (~busbar_mask), tophat, 0.0)

    # Crack Threshold
    crack_thresh = 18.0 if sensitivity == "HIGH" else (22.0 if sensitivity == "STANDARD" else 28.0)
    crack_binary = tophat_wafer >= crack_thresh
    crack_px_count = int(np.sum(crack_binary))

    # 10. Inactive / Electrically Disconnected Region Detection
    # Inactive zones emit virtually zero NIR photons (< 42% of nominal active median)
    inactive_thresh = nom_median * 0.42
    inactive_binary = (cleaned_wafer < inactive_thresh) & wafer_mask & (~busbar_mask)
    # Remove single pixel noise with morphological opening
    inact_pil = Image.fromarray((inactive_binary * 255).astype(np.uint8), mode="L")
    inact_cleaned = np.array(inact_pil.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(5))) > 120
    inactive_px_count = int(np.sum(inact_cleaned))
    inactive_area_percent = round((inactive_px_count / total_wafer_px) * 100.0, 2)

    # 11. Broken Finger Interconnect & Shunt Luminescence Quenching Detection
    # Front-finger difference between raw and cleaned
    finger_diff = np.abs(gray_arr - cleaned_wafer)
    # Shunt quenching: diffuse dark luminescence (between 0.42 and 0.78 of nominal)
    shunt_mask = (cleaned_wafer >= inactive_thresh) & (cleaned_wafer < nom_median * 0.78) & wafer_mask & (~busbar_mask)
    shunt_px = int(np.sum(shunt_mask))
    shunt_area_percent = round((shunt_px / total_wafer_px) * 100.0, 2)

    # 12. Bounding Reticles & Defect Regions
    regions = []

    # A. Inactive Region Contours & Bounding Box
    if inactive_px_count > 300:
        ys_i, xs_i = np.where(inact_cleaned)
        min_xi, max_xi = int(np.min(xs_i)), int(np.max(xs_i))
        min_yi, max_yi = int(np.min(ys_i)), int(np.max(ys_i))
        bw_i = max_xi - min_xi + 1
        bh_i = max_yi - min_yi + 1

        r_idx = 1 if min_yi < target_dim // 2 else 2
        c_idx = 1 if min_xi < target_dim // 2 else 2

        regions.append({
            "type": "INACTIVE_REGION",
            "label": "Disconnected Inactive Zone",
            "box": {"x": min_xi, "y": min_yi, "width": bw_i, "height": bh_i},
            "cellCoordinate": f"Wafer Sector [R{r_idx}, C{c_idx}]",
            "areaPercent": inactive_area_percent,
            "severity": "CRITICAL" if inactive_area_percent > 8.0 else "HIGH"
        })

    # B. Microcrack Bounding Box
    if crack_px_count > 120:
        ys_c, xs_c = np.where(crack_binary)
        min_xc, max_xc = int(np.min(xs_c)), int(np.max(xs_c))
        min_yc, max_yc = int(np.min(ys_c)), int(np.max(ys_c))
        bw_c = max_xc - min_xc + 1
        bh_c = max_yc - min_yc + 1

        r_c = 1 if min_yc < target_dim // 2 else 2
        c_c = 1 if min_xc < target_dim // 2 else 2

        regions.append({
            "type": "MICROCRACK_CLUSTER",
            "label": "Dendritic Wafer Microcrack",
            "box": {"x": min_xc, "y": min_yc, "width": bw_c, "height": bh_c},
            "cellCoordinate": f"Wafer Sector [R{r_c}, C{c_c}]",
            "pixelLength": crack_px_count,
            "severity": "HIGH" if (bw_c > 180 or bh_c > 180 or crack_px_count > 350) else "MEDIUM"
        })

    # C. Shunt / Finger Break Box
    if shunt_area_percent > 1.2 and len(regions) < 3:
        ys_s, xs_s = np.where(shunt_mask)
        if len(ys_s) > 100:
            min_xs, max_xs = int(np.min(xs_s)), int(np.max(xs_s))
            min_ys, max_ys = int(np.min(ys_s)), int(np.max(ys_s))
            bw_s = max_xs - min_xs + 1
            bh_s = max_ys - min_ys + 1

            r_s = 1 if min_ys < target_dim // 2 else 2
            c_s = 1 if min_xs < target_dim // 2 else 2

            is_finger = bw_s < 260 and bh_s > 180
            reg_label = "Interrupted Grid Fingers" if is_finger else "Dark Shunt Quenching"
            reg_type = "FINGER_INTERRUPT" if is_finger else "DARK_SHUNT"

            regions.append({
                "type": reg_type,
                "label": reg_label,
                "box": {"x": min_xs, "y": min_ys, "width": bw_s, "height": bh_s},
                "cellCoordinate": f"Wafer Sector [R{r_s}, C{c_s}]",
                "areaPercent": shunt_area_percent,
                "severity": "MEDIUM"
            })

    # 13. Defect Classification, Score & Field Guidance
    if inactive_area_percent >= 5.0:
        defect_category = CAT_DISCONNECTED
        severity = "CRITICAL"
        priority = "URGENT"
        anomaly_score = round(min(98.5, 75.0 + inactive_area_percent * 1.8), 1)
        why_flagged = (
            f"Electrically disconnected inactive cell region detected ({inactive_area_percent}% wafer area inactive). "
            "Severe dark zone indicates traversing crack or busbar fracture completely isolating silicon segment from carrier conduction."
        )
        recommended_action = (
            "Immediate module isolation required. Conduct dark I-V curve tracing and on-site mechanical load test; "
            "schedule module wafer replacement to avoid localized reverse-bias shunting and hot-spot overheating."
        )
    elif crack_px_count > 250:
        defect_category = CAT_MICROCRACK
        severity = "HIGH" if crack_px_count > 500 else "MEDIUM"
        priority = "HIGH" if severity == "HIGH" else "MEDIUM"
        anomaly_score = round(min(88.0, 48.0 + (crack_px_count / 35.0)), 1)
        why_flagged = (
            f"Silicon wafer microcrack network detected ({crack_px_count} dark fissure pixels localized via morphological top-hat filtering). "
            "Sub-millimeter crystal fracture poses high risk of mechanical propagation and future cell region isolation."
        )
        recommended_action = (
            "Perform high-resolution electroluminescence survey under mechanical load; verify bypass diode conduction state; "
            "monitor during next scheduled field audit."
        )
    elif shunt_area_percent >= 3.0:
        defect_category = CAT_BROKEN_FINGER
        severity = "MEDIUM"
        priority = "MEDIUM"
        anomaly_score = round(min(65.0, 42.0 + shunt_area_percent * 3.5), 1)
        why_flagged = (
            f"Broken front-contact grid fingers or interconnect attenuation identified ({shunt_area_percent}% area affected). "
            "Localized current crowding along busbar interface causing dark emission streaks."
        )
        recommended_action = (
            "Inspect module front glass for micro-impacts; check series resistance (Rs) via flash test or portable I-V curve tracer."
        )
    elif shunt_area_percent >= 1.2:
        defect_category = CAT_DARK_SHUNT
        severity = "MEDIUM"
        priority = "MEDIUM"
        anomaly_score = round(min(58.0, 36.0 + shunt_area_percent * 4.5), 1)
        why_flagged = (
            f"Localized dark shunt luminescence quenching detected ({shunt_area_percent}% area affected). Crystal impurity or localized shunting path "
            "drawing excessive leakage current and reducing radiative recombination."
        )
        recommended_action = (
            "Perform infrared thermography cross-check (IRT) to observe whether shunt creates thermal dissipation hotspot."
        )
    else:
        defect_category = CAT_NOMINAL
        severity = "LOW"
        priority = "LOW"
        anomaly_score = round(max(3.2, 5.0 + inactive_area_percent * 2.0), 1)
        why_flagged = (
            "Module exhibits uniform near-infrared luminescence across crystalline wafer. "
            "Busbars and front finger grid intact; no significant cracks, dark shunts, or disconnected regions detected."
        )
        recommended_action = "Nominal operation confirmed. Maintain standard recurring preventive maintenance interval."

    needs_review = quality_info["needsReview"]
    review_reason = None
    if quality_info["rating"] == "DEGRADED_LOW_CONTRAST":
        needs_review = True
        review_reason = f"Image quality degraded ({', '.join(quality_info['warnings'])}). Localization may be uncertain."

    # 14. Render Output Images (Annotated HUD & Processed Matrix)
    ts_now = int(time.time() * 1000)

    # A. Enhanced Matrix Image (Grayscale with Grid Overlay)
    processed_rgb = Image.fromarray(stretched, mode="L").convert("RGB")
    p_draw = ImageDraw.Draw(processed_rgb)
    for step in range(1, 4):
        pos = int(target_dim * (step / 4.0))
        p_draw.line([(pos, 0), (pos, target_dim)], fill=(0, 240, 255), width=1)
        p_draw.line([(0, pos), (target_dim, pos)], fill=(0, 240, 255), width=1)
    
    processed_fname = f"el_processed_{ts_now}.png"
    processed_path = os.path.join(output_dir, processed_fname)
    processed_rgb.save(processed_path, format="PNG")

    # B. Annotated Diagnostic HUD Image
    annotated_rgb = Image.fromarray(stretched, mode="L").convert("RGBA")
    overlay = Image.new("RGBA", (target_dim, target_dim), (0, 0, 0, 0))
    a_draw = ImageDraw.Draw(overlay)

    # Inactive region overlay (semi-transparent rose mask)
    if inactive_px_count > 0:
        mask_rgba = np.zeros((target_dim, target_dim, 4), dtype=np.uint8)
        mask_rgba[inact_cleaned] = [244, 63, 94, 120]  # Rose with 47% opacity
        inactive_layer = Image.fromarray(mask_rgba, mode="RGBA")
        overlay = Image.alpha_composite(overlay, inactive_layer)
        a_draw = ImageDraw.Draw(overlay)

    # Crack traces overlay (electric yellow / neon amber)
    if crack_px_count > 0:
        crack_rgba = np.zeros((target_dim, target_dim, 4), dtype=np.uint8)
        crack_rgba[crack_binary] = [251, 191, 36, 230]  # Amber-yellow
        crack_layer = Image.fromarray(crack_rgba, mode="RGBA")
        overlay = Image.alpha_composite(overlay, crack_layer)
        a_draw = ImageDraw.Draw(overlay)

    # Diagnostic Reticles, Bounding Corners, and Badges
    for r in regions:
        b = r["box"]
        bx, by, bw, bh = b["x"], b["y"], b["width"], b["height"]
        color = (244, 63, 94, 255) if r["severity"] == "CRITICAL" else ((251, 191, 36, 255) if r["severity"] == "HIGH" else (0, 240, 255, 255))

        c_len = min(24, max(8, min(bw, bh) // 4))
        # Top-left corner
        a_draw.line([(bx, by), (bx + c_len, by)], fill=color, width=2)
        a_draw.line([(bx, by), (bx, by + c_len)], fill=color, width=2)
        # Top-right corner
        a_draw.line([(bx + bw, by), (bx + bw - c_len, by)], fill=color, width=2)
        a_draw.line([(bx + bw, by), (bx + bw, by + c_len)], fill=color, width=2)
        # Bottom-left corner
        a_draw.line([(bx, by + bh), (bx + c_len, by + bh)], fill=color, width=2)
        a_draw.line([(bx, by + bh), (bx, by + bh - c_len)], fill=color, width=2)
        # Bottom-right corner
        a_draw.line([(bx + bw, by + bh), (bx + bw - c_len, by + bh)], fill=color, width=2)
        a_draw.line([(bx + bw, by + bh), (bx + bw, by + bh - c_len)], fill=color, width=2)

        badge_txt = f"{r['label']} [{r['cellCoordinate']}]"
        badge_y = max(4, by - 24)
        badge_w = len(badge_txt) * 7 + 14
        a_draw.rectangle([bx, badge_y, bx + badge_w, badge_y + 20], fill=(15, 23, 42, 230), outline=color, width=1)
        a_draw.text((bx + 6, badge_y + 4), badge_txt, fill=(255, 255, 255, 255))

    # Grid Coordinate Borders along edges
    for i in range(1, 4):
        coord_px = int(target_dim * (i / 4.0))
        a_draw.line([(coord_px, 0), (coord_px, 8)], fill=(0, 240, 255, 180), width=1)
        a_draw.text((coord_px + 2, 2), f"X{coord_px}", fill=(0, 240, 255, 180))
        a_draw.line([(0, coord_px), (8, coord_px)], fill=(0, 240, 255, 180), width=1)
        a_draw.text((2, coord_px + 2), f"Y{coord_px}", fill=(0, 240, 255, 180))

    annotated_final = Image.alpha_composite(annotated_rgb, overlay).convert("RGB")
    annotated_fname = f"el_annotated_{ts_now}.png"
    annotated_path = os.path.join(output_dir, annotated_fname)
    annotated_final.save(annotated_path, format="PNG")

    processed_rel = f"/data/images/{processed_fname}"
    annotated_rel = f"/data/images/{annotated_fname}"
    orig_rel = f"/data/images/{os.path.basename(image_path)}" if "images" in image_path else f"/data/el_samples/{os.path.basename(image_path)}" if "el_samples" in image_path else f"/data/images/{os.path.basename(image_path)}"

    score_explanation = (
        f"Score represents Heuristic Anomaly Index (0-100) derived from 1D decoupled morphological Black Top-Hat crack extraction ({crack_px_count} fissure px), "
        f"electrically inactive cell region luminescence dropout ({inactive_area_percent}%), and dark shunt quenching ({shunt_area_percent}%). "
        "This is an explainable heuristic feature extraction metric, NOT a trained deep-learning probability."
    )

    disclaimer = (
        "Engineering Notice: Electroluminescence image analysis identifies probable structural wafer defects, fractures, and inactive segments. "
        "Optical/NIR image analysis does not by itself establish the complete electrical condition, open-circuit voltage (Voc), or power output (Pmax) of the photovoltaic module."
    )

    return {
        "success": True,
        "panelId": panel_id,
        "modality": "ELECTROLUMINESCENCE",
        "confirmedModality": confirmed_el_modality,
        "analysisMethod": EL_PIPELINE_VERSION,
        "modelName": EL_ENGINE_NAME,
        "scoreType": EL_SCORE_TYPE,
        "scoreExplanation": score_explanation,
        "anomalyScore": anomaly_score,
        "defectCategory": defect_category,
        "severity": severity,
        "maintenancePriority": priority,
        "inactiveAreaPercent": inactive_area_percent,
        "crackPixelCount": crack_px_count,
        "shuntAreaPercent": shunt_area_percent,
        "quality": quality_info,
        "whyFlagged": why_flagged,
        "recommendedAction": recommended_action,
        "needsReview": needs_review,
        "reviewReason": review_reason or ("Needs Review: Low contrast / ambiguous localization." if needs_review else None),
        "disclaimer": disclaimer,
        "regions": regions,
        "originalImagePath": orig_rel,
        "processedImagePath": processed_rel,
        "annotatedImagePath": annotated_rel,
        "dimensions": {"width": target_dim, "height": target_dim},
        "createdAt": time.strftime("%Y-%m-%dT%H:%M:%S")
    }
