package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class OverlayCorrectionAudit {
 private OverlayCorrectionAudit() {}
 public static JSONObject record(TextOverlay before,TextOverlay after,long revision) throws Exception {
  return new JSONObject().put("overlay_id",after.overlayId).put("revision",revision)
   .put("before_x",before.x).put("before_y",before.y).put("after_x",after.x).put("after_y",after.y)
   .put("before_size",before.textSize).put("after_size",after.textSize).put("other_overlays_unchanged",true);
 }
}
