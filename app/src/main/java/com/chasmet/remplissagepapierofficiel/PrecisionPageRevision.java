package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionPageRevision { private PrecisionPageRevision(){} public static JSONObject json(int page,long revision) throws Exception {return new JSONObject().put("page_index",page).put("revision",revision).put("cache_key","page_"+page+"_rev_"+revision);} }
