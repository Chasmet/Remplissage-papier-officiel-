package com.chasmet.remplissagepapierofficiel;

import org.json.JSONObject;
import java.util.List;

/** Applies one authoritative correction without rebuilding the fill plan. */
public final class OverlayCorrection {
    private OverlayCorrection() {}

    public static boolean apply(JSONObject command, List<TextOverlay> overlays) {
        if (command == null || overlays == null) return false;
        String id = command.optString("overlay_id", "").trim();
        if (id.isEmpty()) return false;
        for (int i = 0; i < overlays.size(); i++) {
            TextOverlay old = overlays.get(i);
            if (old == null || !id.equals(old.overlayId)) continue;
            float x = command.has("x") ? strict(command.optDouble("x"), "x") : old.x;
            float y = command.has("y") ? strict(command.optDouble("y"), "y") : old.y;
            if (command.has("x_delta")) x = strict(x + command.optDouble("x_delta"), "x+x_delta");
            if (command.has("y_delta")) y = strict(y + command.optDouble("y_delta"), "y+y_delta");
            Float size = command.has("size") ? (float) command.optDouble("size") : null;
            overlays.set(i, old.withChanges(x, y, size,
                    command.has("align") ? command.optString("align") : null,
                    command.has("text") ? command.optString("text") : null));
            return true;
        }
        return false;
    }

    public static boolean delete(String overlayId, List<TextOverlay> overlays) {
        if (overlayId == null || overlays == null) return false;
        for (int i = 0; i < overlays.size(); i++) {
            TextOverlay item = overlays.get(i);
            if (item != null && overlayId.trim().equals(item.overlayId)) {
                overlays.remove(i);
                return true;
            }
        }
        return false;
    }

    private static float strict(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0d || value > 1d)
            throw new IllegalArgumentException(name + " hors plage 0..1");
        return (float) value;
    }
}
