package com.chasmet.remplissagepapierofficiel;
import java.util.LinkedHashSet;
import java.util.Set;
public final class PrecisionChangedPage { private PrecisionChangedPage(){} public static Set<Integer> from(Iterable<TextOverlay> overlays,Set<String> modifiedIds){Set<Integer>pages=new LinkedHashSet<>();if(overlays!=null&&modifiedIds!=null)for(TextOverlay o:overlays)if(o!=null&&modifiedIds.contains(o.overlayId))pages.add(o.pageIndex);return pages;} }
