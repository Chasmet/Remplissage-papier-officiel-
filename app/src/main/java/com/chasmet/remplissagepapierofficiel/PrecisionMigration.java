package com.chasmet.remplissagepapierofficiel;
/** Legacy overlays without explicit IDs are upgraded by TextOverlay's generated ID, without moving coordinates. */
public final class PrecisionMigration { private PrecisionMigration(){} public static TextOverlay legacy(TextOverlay old){if(old==null)return null;return new TextOverlay(old.overlayId,old.pageIndex,old.x,old.y,old.text,old.textSize,old.align,old.kind,old.width,old.height,old.dataState);} }
