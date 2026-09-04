package com.chasmet.remplissagepapierofficiel;
import java.util.List;
public final class PrecisionPassValidator { private PrecisionPassValidator(){} public static void validate(List<TextOverlay> overlays){if(overlays!=null&&overlays.size()>PrecisionLimits.MAX_OVERLAYS)throw new IllegalStateException("too many overlays");PrecisionAssertions.uniqueIds(overlays);} }
