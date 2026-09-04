package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;

/** Small state machine preventing validation of an old preview after a correction. */
public final class PrecisionLoopState {
    private long revision=0;
    private long validatedRevision=-1;
    public synchronized long changed(){ return ++revision; }
    public synchronized void validate(long previewRevision){ if(previewRevision==revision) validatedRevision=previewRevision; }
    public synchronized boolean isCurrentValidated(){ return revision==validatedRevision; }
    public synchronized JSONObject json() throws Exception { return new JSONObject().put("revision",revision).put("validated_revision",validatedRevision).put("current_validated",isCurrentValidated()); }
}
