package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionServerActionMap { private PrecisionServerActionMap(){} public static JSONObject json() throws Exception {return new JSONObject().put("update_overlay","app_action=precision_update_overlay").put("delete_overlay","app_action=precision_delete_overlay").put("preview","app_action=upload_preview_page").put("preview_crop","app_action=upload_preview_crop").put("layout_validation","app_action=upload_layout_validation");} }
