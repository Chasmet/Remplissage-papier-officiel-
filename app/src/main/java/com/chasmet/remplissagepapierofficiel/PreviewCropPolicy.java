package com.chasmet.remplissagepapierofficiel;
import android.graphics.Rect;
public final class PreviewCropPolicy { private PreviewCropPolicy(){} public static Rect forModified(TextOverlay o,int w,int h){return PreviewCropGeometry.around(o,w,h,PrecisionDefaults.CROP_MARGIN_X,PrecisionDefaults.CROP_MARGIN_Y);} }
