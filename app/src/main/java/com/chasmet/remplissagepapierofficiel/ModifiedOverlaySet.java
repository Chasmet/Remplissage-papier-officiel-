package com.chasmet.remplissagepapierofficiel;

import java.util.LinkedHashSet;
import java.util.Set;

/** Keeps only overlay IDs changed in the current correction pass. */
public final class ModifiedOverlaySet {
    private final Set<String> ids=new LinkedHashSet<>();
    public synchronized void mark(String id) { if(id!=null&&!id.trim().isEmpty()) ids.add(id.trim()); }
    public synchronized Set<String> drain() { Set<String> copy=new LinkedHashSet<>(ids); ids.clear(); return copy; }
    public synchronized boolean isEmpty() { return ids.isEmpty(); }
}
