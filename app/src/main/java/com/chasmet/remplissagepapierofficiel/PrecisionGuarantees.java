package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionGuarantees { private PrecisionGuarantees(){} public static JSONObject json() throws Exception {return new JSONObject().put("no_coordinate_clamping",true).put("no_percent_guessing",true).put("no_snap",true).put("no_forced_centering",true).put("no_full_plan_rebuild_for_local_correction",true).put("preview_revision_tracking",true);} }
