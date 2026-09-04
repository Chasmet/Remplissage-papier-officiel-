package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;

/** Field detection is advisory only and can never mutate ChatGPT coordinates. */
public final class DetectedFieldHint {
    private DetectedFieldHint() {}

    public static JSONObject create(String label,int page,float x,float y,float width,float height,String type,float confidence) throws Exception {
        return new JSONObject()
                .put("label",label==null?"":label)
                .put("page",page)
                .put("x",x).put("y",y).put("width",width).put("height",height)
                .put("type",type==null?"unknown":type)
                .put("confidence",confidence)
                .put("advisory_only",true)
                .put("may_modify_placement",false);
    }
}
