package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionDate { private PrecisionDate(){} public static JSONObject json() throws Exception {return new JSONObject().put("formats","DD / MM / YYYY|DD/MM/YYYY|DD MM YYYY").put("split_components_supported",true).put("missing_component_requires_user",true).put("invent_missing_component",false);} }
