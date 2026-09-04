package com.chasmet.remplissagepapierofficiel;
public final class PrecisionInvariant { private PrecisionInvariant(){} public static void assertOverlay(TextOverlay o){if(o==null)throw new IllegalArgumentException("overlay");if(o.overlayId==null||o.overlayId.isEmpty())throw new IllegalStateException("overlay_id");if(o.x<0||o.x>1||o.y<0||o.y>1)throw new IllegalStateException("coordinates");} }
