package com.chasmet.remplissagepapierofficiel;
import org.json.JSONArray;
public final class PrecisionChecklist { private PrecisionChecklist(){} public static JSONArray json(){return new JSONArray().put("coordinates exact").put("overlay IDs stable").put("local corrections enabled").put("preview refreshed").put("modified crops available").put("layout validated").put("current preview approved before final export");} }
