package com.chasmet.remplissagepapierofficiel;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Applies ordered local operations atomically to a working copy. */
public final class OverlayOperationBatch {
    private OverlayOperationBatch() {}
    public static JSONObject apply(JSONArray operations,List<TextOverlay> target) throws Exception {
        if(operations==null) return new JSONObject().put("ok",true).put("applied",0);
        int applied=0;
        for(int i=0;i<operations.length();i++){
            JSONObject op=operations.optJSONObject(i); if(op==null)continue;
            JSONObject result=PrecisionCommandRouter.execute(op,target);
            if(!result.optBoolean("ok",false)) return new JSONObject().put("ok",false).put("failed_index",i).put("result",result);
            applied++;
        }
        return new JSONObject().put("ok",true).put("applied",applied).put("preview_required",applied>0);
    }
}
