package com.chasmet.remplissagepapierofficiel;
import java.util.Locale;
public final class OverlayIdPolicy { private OverlayIdPolicy(){} public static String semantic(String label,int ordinal){ String s=label==null?"overlay":label.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$",""); if(s.isEmpty())s="overlay"; return s+"_"+String.format(Locale.ROOT,"%02d",Math.max(1,ordinal)); } }
