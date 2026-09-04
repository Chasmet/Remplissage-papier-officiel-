package com.chasmet.remplissagepapierofficiel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
public final class PrecisionAssertions { private PrecisionAssertions(){} public static void uniqueIds(List<TextOverlay> overlays){Set<String>s=new HashSet<>();if(overlays==null)return;for(TextOverlay o:overlays){PrecisionInvariant.assertOverlay(o);if(!s.add(o.overlayId))throw new IllegalStateException("duplicate overlay_id: "+o.overlayId);}} }
