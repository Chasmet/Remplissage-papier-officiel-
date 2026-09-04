package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class CorrectionLoopResult { private CorrectionLoopResult(){} public static JSONObject applied(long revision,int modifiedCount) throws Exception {return new JSONObject().put("ok",true).put("revision",revision).put("modified_count",modifiedCount).put("preview_pending",true).put("chatgpt_validation_pending",true).put("final",false);} }
