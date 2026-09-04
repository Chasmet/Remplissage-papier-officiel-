package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionJobState { private PrecisionJobState(){} public static JSONObject json(String jobId,long revision,String phase) throws Exception {return new JSONObject().put("job_id",jobId==null?"":jobId).put("revision",revision).put("phase",phase==null?"idle":phase).put("precision_engine",PrecisionVersion.ENGINE);} }
