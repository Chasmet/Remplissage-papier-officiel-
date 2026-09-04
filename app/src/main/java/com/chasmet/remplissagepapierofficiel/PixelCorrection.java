package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
/** Converts an explicit ChatGPT pixel delta using the exact preview dimensions. */
public final class PixelCorrection { private PixelCorrection(){} public static JSONObject toNormalized(String overlayId,float dxPx,float dyPx,float pageWidth,float pageHeight) throws Exception {return new JSONObject().put("operation","update_overlay").put("overlay_id",overlayId).put("x_delta",CorrectionUnits.pixelsToNormalizedX(dxPx,pageWidth)).put("y_delta",CorrectionUnits.pixelsToNormalizedY(dyPx,pageHeight)).put("source_units","preview_pixels");} }
