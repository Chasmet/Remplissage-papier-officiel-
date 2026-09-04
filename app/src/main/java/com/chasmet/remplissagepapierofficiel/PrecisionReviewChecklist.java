package com.chasmet.remplissagepapierofficiel;
import org.json.JSONArray;
public final class PrecisionReviewChecklist { private PrecisionReviewChecklist(){} public static JSONArray json(){return new JSONArray().put("text alignment").put("baseline above form line").put("text width inside field").put("no label overlap").put("checkbox centered").put("date components aligned").put("signature left for user when required");} }
