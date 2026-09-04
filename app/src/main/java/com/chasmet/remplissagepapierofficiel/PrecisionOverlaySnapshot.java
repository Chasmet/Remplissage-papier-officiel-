package com.chasmet.remplissagepapierofficiel;
import org.json.JSONArray;
import java.util.List;
public final class PrecisionOverlaySnapshot { private PrecisionOverlaySnapshot(){} public static JSONArray json(List<TextOverlay> overlays) throws Exception {JSONArray a=new JSONArray();if(overlays!=null)for(TextOverlay o:overlays)if(o!=null)a.put(OverlayJson.toJson(o));return a;} }
