package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionReviewRequest { private PrecisionReviewRequest(){} public static JSONObject json(long revision) throws Exception {return new JSONObject().put("preview_revision",revision).put("inspect_alignment",true).put("inspect_baseline",true).put("inspect_overlap",true).put("inspect_checkbox_center",true).put("respond_with_local_corrections_only",true);} }
