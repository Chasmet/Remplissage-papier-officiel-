package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import java.util.List;

/** Local command router used by the bridge for fine-grained correction operations. */
public final class PrecisionCommandRouter {
    private PrecisionCommandRouter() {}

    public static JSONObject execute(JSONObject command,List<TextOverlay> overlays) throws Exception {
        if (command==null) throw new IllegalArgumentException("commande requise");
        String op=command.optString("operation","").trim();
        if ("update_overlay".equals(op)) {
            String id=command.optString("overlay_id","");
            if (!OverlayCorrection.apply(command,overlays)) return error("overlay_not_found",id);
            return CorrectionResult.success(id);
        }
        if ("delete_overlay".equals(op)) {
            String id=command.optString("overlay_id","");
            if (!OverlayCorrection.delete(id,overlays)) return error("overlay_not_found",id);
            return new JSONObject().put("ok",true).put("overlay_id",id).put("deleted",true).put("preview_required",true);
        }
        if ("validate_layout".equals(op)) return OverlayQualityReport.build(overlays);
        if ("capabilities".equals(op)) return PrecisionProtocol.capabilities();
        return error("unsupported_operation",op);
    }

    private static JSONObject error(String error,String value) throws Exception {
        return new JSONObject().put("ok",false).put("error",error).put("value",value==null?"":value);
    }
}
