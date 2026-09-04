package com.chasmet.remplissagepapierofficiel;

import java.util.List;

public final class OverlayLookup {
    private OverlayLookup() {}
    public static TextOverlay byId(List<TextOverlay> overlays,String id){
        if(overlays==null||id==null)return null;
        for(TextOverlay o:overlays) if(o!=null&&id.equals(o.overlayId)) return o;
        return null;
    }
}
