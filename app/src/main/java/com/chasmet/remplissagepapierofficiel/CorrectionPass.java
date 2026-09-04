package com.chasmet.remplissagepapierofficiel;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Set;
public final class CorrectionPass { private CorrectionPass(){} public static JSONObject json(long revision,Set<String> modified) throws Exception { JSONArray a=new JSONArray(); if(modified!=null)for(String id:modified)a.put(id); return new JSONObject().put("revision",revision).put("modified_overlay_ids",a).put("full_preview_required",true).put("crops_required",a.length()>0).put("await_chatgpt_validation",true); } }
