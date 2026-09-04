package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionReleaseGate { private PrecisionReleaseGate(){} public static JSONObject json(boolean ciPassed,boolean serverWired) throws Exception {return new JSONObject().put("ci_passed",ciPassed).put("server_wired",serverWired).put("release_ready",ciPassed&&serverWired);} }
