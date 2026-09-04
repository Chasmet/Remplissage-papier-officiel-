package com.chasmet.remplissagepapierofficiel;

import android.graphics.Rect;
import org.json.JSONObject;

public final class PreviewCropDescriptor {
 private PreviewCropDescriptor() {}
 public static JSONObject json(TextOverlay o,Rect r,int pageWidth,int pageHeight) throws Exception {
  return new JSONObject().put("overlay_id",o.overlayId).put("page_index",o.pageIndex)
   .put("left_px",r.left).put("top_px",r.top).put("right_px",r.right).put("bottom_px",r.bottom)
   .put("page_width_px",pageWidth).put("page_height_px",pageHeight).put("purpose","fine_alignment_review");
 }
}
