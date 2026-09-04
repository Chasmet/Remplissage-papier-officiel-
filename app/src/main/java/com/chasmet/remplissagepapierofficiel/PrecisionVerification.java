package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionVerification { private PrecisionVerification(){} public static JSONObject json() throws Exception {return new JSONObject().put("coordinate_vector",PrecisionCoordinateExample.json()).put("correction_example",PrecisionCommandExample.emailMoveUp()).put("guarantees",PrecisionGuarantees.json()).put("release_checklist",PrecisionReleaseChecklist.json());} }
