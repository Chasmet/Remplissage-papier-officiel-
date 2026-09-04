package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;

public final class PrecisionAcceptance {
 private PrecisionAcceptance() {}
 public static JSONObject json() throws Exception {
  return new JSONObject().put("strict_coordinates",true).put("overlay_id",true)
   .put("local_update",true).put("local_delete",true).put("baseline",true)
   .put("text_metrics",true).put("full_preview",true).put("targeted_crops",true)
   .put("quality_gate",true).put("unknown_value_policy",true).put("signature_policy",true)
   .put("checkbox_center",true).put("split_date",true).put("stale_preview_protection",true);
 }
}
