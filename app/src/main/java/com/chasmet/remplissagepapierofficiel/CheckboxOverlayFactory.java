package com.chasmet.remplissagepapierofficiel;

/** Checkbox coordinates always describe the exact visual center. */
public final class CheckboxOverlayFactory {
    private CheckboxOverlayFactory() {}

    public static TextOverlay create(String id,int page,float centerX,float centerY,
                                     boolean checked,String style,float size) {
        String mark="";
        if (checked) {
            if ("check".equalsIgnoreCase(style)) mark="✓";
            else if ("dot".equalsIgnoreCase(style)) mark="●";
            else mark="X";
        }
        return new TextOverlay(id,page,centerX,centerY,mark,size,"center","checkbox",0,0,"known");
    }
}
