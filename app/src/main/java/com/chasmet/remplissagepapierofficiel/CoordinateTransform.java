package com.chasmet.remplissagepapierofficiel;

/** Single coordinate formula shared by page image, preview and PDF rendering. */
public final class CoordinateTransform {
 private CoordinateTransform() {}
 public static float x(float normalized,float width){ strict(normalized); return normalized*width; }
 public static float y(float normalized,float height){ strict(normalized); return normalized*height; }
 public static float normalizeX(float absolute,float width){ if(width<=0)throw new IllegalArgumentException("width"); return strict(absolute/width); }
 public static float normalizeY(float absolute,float height){ if(height<=0)throw new IllegalArgumentException("height"); return strict(absolute/height); }
 private static float strict(float v){ if(Float.isNaN(v)||Float.isInfinite(v)||v<0||v>1)throw new IllegalArgumentException("coordinate hors 0..1"); return v; }
}
