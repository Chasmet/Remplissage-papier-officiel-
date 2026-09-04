package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionOperationResult { private PrecisionOperationResult(){} public static JSONObject ok(String operation,long revision) throws Exception {return new JSONObject().put("ok",true).put("operation",operation).put("revision",revision).put("preview_required",CorrectionPreviewPolicy.shouldRenderAfter(operation));} }
