package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class OverlayCorrectionExample { private OverlayCorrectionExample(){} public static JSONObject identityFourPixelsUp(int previewHeight) throws Exception {return PixelCorrection.toNormalized("identity_01",0,-4,1000,previewHeight);} }
