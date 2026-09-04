package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionPreviewContract { private PrecisionPreviewContract(){} public static JSONObject json() throws Exception {return new JSONObject().put("render_from_original_pdf",true).put("apply_current_overlays",true).put("same_baseline_math_as_final",true).put("same_alignment_math_as_final",true).put("same_normalized_coordinates_as_final",true).put("full_page_and_crops",true);} }
