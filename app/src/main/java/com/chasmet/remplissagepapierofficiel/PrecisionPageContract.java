package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionPageContract { private PrecisionPageContract(){} public static JSONObject json(int pageIndex,int width,int height) throws Exception {return new JSONObject().put("page_index",pageIndex).put("image",PreviewCoordinateMetadata.json(width,height)).put("render_semantics",RenderSemantics.json()).put("same_transform_for_preview_and_final_pdf",true);} }
