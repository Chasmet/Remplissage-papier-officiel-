package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionPreviewReview { private PrecisionPreviewReview(){} public static JSONObject json(long revision,boolean fullPage,boolean crops) throws Exception {return new JSONObject().put("revision",revision).put("full_page_available",fullPage).put("crops_available",crops).put("ready_for_visual_review",fullPage).put("review_checklist",PrecisionReviewChecklist.json());} }
