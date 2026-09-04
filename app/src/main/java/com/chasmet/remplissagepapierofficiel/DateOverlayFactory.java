package com.chasmet.remplissagepapierofficiel;

import java.util.ArrayList;
import java.util.List;

/** Creates explicit date overlays; no date value is inferred. */
public final class DateOverlayFactory {
    private DateOverlayFactory() {}

    public static List<TextOverlay> split(String idPrefix, int pageIndex,
                                          String day, String month, String year,
                                          float dayX, float monthX, float yearX,
                                          float baselineY, float size) {
        List<TextOverlay> out=new ArrayList<>();
        out.add(part(idPrefix+"_day",pageIndex,day,dayX,baselineY,size));
        out.add(part(idPrefix+"_month",pageIndex,month,monthX,baselineY,size));
        out.add(part(idPrefix+"_year",pageIndex,year,yearX,baselineY,size));
        return out;
    }

    private static TextOverlay part(String id,int page,String value,float x,float y,float size) {
        String state=(value==null||value.trim().isEmpty())?TextOverlay.STATE_REQUIRES_USER:TextOverlay.STATE_KNOWN;
        return new TextOverlay(id,page,x,y,value==null?"":value,size,"center","date",0,0,state);
    }
}
