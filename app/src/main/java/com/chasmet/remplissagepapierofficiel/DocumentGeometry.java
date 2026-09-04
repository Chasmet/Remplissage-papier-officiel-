package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;

/** Describes one page without changing its coordinate system. */
public final class DocumentGeometry {
    private DocumentGeometry() {}

    public static JSONObject page(int pageIndex,float pdfWidth,float pdfHeight,int imageWidth,int imageHeight) throws Exception {
        return new JSONObject()
                .put("page_index",pageIndex)
                .put("pdf_width",pdfWidth)
                .put("pdf_height",pdfHeight)
                .put("image_width",imageWidth)
                .put("image_height",imageHeight)
                .put("normalized_origin","top_left")
                .put("x_formula","x_normalized * width")
                .put("y_formula","y_normalized * height")
                .put("text_y_reference","baseline")
                .put("checkbox_xy_reference","center");
    }
}
