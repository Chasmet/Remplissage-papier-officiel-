package com.chasmet.remplissagepapierofficiel;

/** Safety rule: a signature overlay is only renderable from explicit user-provided signature data. */
public final class SignaturePolicy {
    private SignaturePolicy() {}

    public static boolean canRender(TextOverlay overlay, boolean userSignatureProvided) {
        if (overlay == null || !overlay.isSignature()) return true;
        return userSignatureProvided && !TextOverlay.STATE_REQUIRES_SIGNATURE.equals(overlay.dataState);
    }
}
