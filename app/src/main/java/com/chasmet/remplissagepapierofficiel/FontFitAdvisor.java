package com.chasmet.remplissagepapierofficiel;
import android.graphics.Paint;
import org.json.JSONObject;
/** Suggests a font size but never changes an overlay. */
public final class FontFitAdvisor { private FontFitAdvisor(){} public static JSONObject advise(String text,float currentSize,float maxWidth) throws Exception { Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(currentSize);float w=p.measureText(text==null?"":text);float suggested=currentSize;if(w>maxWidth&&maxWidth>0)suggested=currentSize*(maxWidth/w);return new JSONObject().put("current_size",currentSize).put("measured_width",w).put("max_width",maxWidth).put("suggested_size",suggested).put("applied",false); } }
