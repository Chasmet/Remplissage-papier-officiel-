package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PreviewRequirements {
 private PreviewRequirements() {}
 public static JSONObject json() throws Exception { return new JSONObject().put("full_page",true).put("modified_overlay_crops",true).put("high_quality",true).put("same_coordinate_transform_as_pdf",true).put("after_every_modification",true); }
}
