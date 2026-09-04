package com.chasmet.remplissagepapierofficiel;

/** Unknown/user-required values stay empty until explicitly supplied. */
public final class UnknownValuePolicy {
    private UnknownValuePolicy() {}
    public static boolean mayRenderText(TextOverlay o){
        if(o==null)return false;
        if(TextOverlay.STATE_UNKNOWN.equals(o.dataState)||TextOverlay.STATE_REQUIRES_USER.equals(o.dataState)||TextOverlay.STATE_REQUIRES_SIGNATURE.equals(o.dataState)) return false;
        return o.text!=null&&!o.text.isEmpty();
    }
}
