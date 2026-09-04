package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionCheckbox { private PrecisionCheckbox(){} public static JSONObject json() throws Exception {return new JSONObject().put("x_y_reference","exact_center").put("styles","X,check,dot").put("checked_boolean",true).put("automatic_box_detection_advisory_only",true);} }
