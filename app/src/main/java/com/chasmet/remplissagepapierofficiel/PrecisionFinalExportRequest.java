package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionFinalExportRequest { private PrecisionFinalExportRequest(){} public static JSONObject json(String jobId,long validatedRevision) throws Exception {return new JSONObject().put("job_id",jobId).put("validated_preview_revision",validatedRevision).put("require_exact_revision_match",true).put("preserve_original_pdf_geometry",true);} }
