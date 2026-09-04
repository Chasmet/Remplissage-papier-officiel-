package com.chasmet.remplissagepapierofficiel;
public final class OverlayMutation {
 public final TextOverlay before; public final TextOverlay after;
 public OverlayMutation(TextOverlay before,TextOverlay after){ if(before==null||after==null||!before.overlayId.equals(after.overlayId))throw new IllegalArgumentException("same overlay_id required"); this.before=before; this.after=after; }
 public float xDelta(){return after.x-before.x;} public float yDelta(){return after.y-before.y;}
}
