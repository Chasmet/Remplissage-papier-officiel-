package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionDocumentSession { private PrecisionDocumentSession(){} public static JSONObject json(String jobId,int pages,long revision) throws Exception {return new JSONObject().put("job_id",jobId==null?"":jobId).put("page_count",pages).put("preview_revision",revision).put("coordinate_system",PrecisionConstants.COORDINATE_SYSTEM).put("origin",PrecisionConstants.ORIGIN);} }
