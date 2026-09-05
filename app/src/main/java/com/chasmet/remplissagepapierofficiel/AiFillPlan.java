package com.chasmet.remplissagepapierofficiel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Protocole de placement contrôlé par ChatGPT.
 * Les coordonnées sont normalisées 0..1 et sont autoritaires.
 */
public final class AiFillPlan {
    private AiFillPlan() {
    }

    public static List<TextOverlay> parse(String json, int pageCount) throws Exception {
        List<TextOverlay> result = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return result;

        JSONObject root = new JSONObject(json);
        JSONArray items = root.optJSONArray("placements");
        if (items == null) items = root.optJSONArray("actions");
        if (items == null) items = root.optJSONArray("fields");
        if (items == null) return result;

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            String kind = item.optString("kind", item.optString("type", "text"))
                    .trim().toLowerCase(java.util.Locale.ROOT);
            boolean checked = item.optBoolean("checked", false);
            String text = item.optString("text", "");
            if (text.isEmpty() && ("checkbox".equals(kind) || "check".equals(kind)) && checked) {
                text = item.optString("mark", "X");
                if (text.trim().isEmpty()) text = "X";
            }

            String dataState = TextOverlay.normalizeDataState(
                    item.optString("data_state", item.optString("state", TextOverlay.STATE_KNOWN)));

            // Une signature ne doit jamais être générée automatiquement.
            if (TextOverlay.KIND_SIGNATURE.equals(TextOverlay.normalizeKind(kind))
                    && text.trim().isEmpty()) {
                dataState = TextOverlay.STATE_REQUIRES_SIGNATURE;
            }

            if (text.trim().isEmpty()) {
                // unknown/requires_user/requires_signature sont informatifs et ne dessinent rien.
                continue;
            }

            int pageIndex;
            if (item.has("page_index")) {
                pageIndex = item.getInt("page_index");
            } else if (item.has("pageIndex")) {
                pageIndex = item.getInt("pageIndex");
            } else {
                pageIndex = item.optInt("page", 1) - 1;
            }
            if (pageIndex < 0 || (pageCount > 0 && pageIndex >= pageCount)) {
                throw new IllegalArgumentException("page_index hors limites");
            }

            float x = strictCoordinate(item, "x");
            float y = strictCoordinate(item, "y");
            float size = (float) item.optDouble("size", item.optDouble("textSize", 8.0));
            if (!Float.isFinite(size) || size < 4f || size > 144f) {
                throw new IllegalArgumentException("size doit être compris entre 4 et 144");
            }
            String align = TextOverlay.normalizeAlign(
                    item.optString("align", TextOverlay.ALIGN_LEFT));
            float width = strictOptionalCoordinate(item.optDouble("width", 0.0), "width");
            float height = strictOptionalCoordinate(item.optDouble("height", 0.0), "height");
            String overlayId = item.optString("overlay_id",
                    item.optString("id", item.optString("field_id", "")));

            result.add(new TextOverlay(
                    overlayId,
                    pageIndex,
                    x,
                    y,
                    text,
                    size,
                    align,
                    kind,
                    width,
                    height,
                    dataState
            ));
        }
        return result;
    }

    public static int appendTo(String json, int pageCount, List<TextOverlay> target) throws Exception {
        List<TextOverlay> parsed = parse(json, pageCount);
        if (target != null) target.addAll(parsed);
        return parsed.size();
    }

    public static JSONObject capabilities() throws Exception {
        JSONObject root = new JSONObject();
        root.put("protocol", "remplissage-papier-officiel.ai-fill.v5");
        root.put("coordinateSystem", "normalized-0-to-1");
        root.put("coordinatePolicy", "field-id-exact-anchor; free-coordinates-fallback");
        root.put("textYReference", "exact-baseline");
        root.put("checkboxXYReference", "exact-center");
        root.put("detectedFieldsAreExactAnchorsWhenSelected", true);
        root.put("fieldIdOptional", true);
        root.put("fieldIdPreferred", true);
        root.put("fieldIdOverridesCoordinatesOnServer", true);
        root.put("freeCoordinatesAreAuthoritativeWithoutFieldId", true);
        root.put("freePlacementAllowed", true);
        root.put("supportsAnyPage", true);
        root.put("supportsCheckboxes", true);
        root.put("supportsOverlayIds", true);
        root.put("supportsLocalOverlayUpdate", true);
        root.put("supportsLocalOverlayDelete", true);
        root.put("supportsTextMeasurement", true);
        root.put("supportsLayoutValidation", true);
        root.put("supportsDataState", true);
        root.put("supportsDateKind", true);
        root.put("supportsSignatureKind", true);
        root.put("supportsAppend", true);
        root.put("supportsReplaceDocument", true);
        root.put("supportsClearDocument", true);
        root.put("supportsReplacePage", true);
        root.put("supportsClearPage", true);

        JSONObject placement = new JSONObject();
        placement.put("overlay_id", "stable unique id used for later local corrections");
        placement.put("page_index", "0-based page index");
        placement.put("x", "strict 0.0..1.0 from left; text alignment anchor or checkbox center");
        placement.put("y", "strict 0.0..1.0 from top; exact text baseline or checkbox center");
        placement.put("text", "text to write; use X/✓/● for checkbox if checked");
        placement.put("kind", "text, checkbox, date or signature");
        placement.put("size", "font size in PDF page units, 4..144");
        placement.put("align", "left, center or right");
        placement.put("width", "optional normalized expected field width; no auto-fit");
        placement.put("height", "optional normalized expected field height; no auto-fit");
        placement.put("field_id", "preferred exact field guide id; overrides x/y and auto-fits to the measured field");
        placement.put("data_state", "known, unknown, requires_user or requires_signature");
        root.put("placementSchema", placement);
        return root;
    }

    public static JSONObject pageContext(int pageIndex, int pageCount, int pixelWidth, int pixelHeight,
                                         List<FormField> detectedFields) throws Exception {
        JSONObject root = new JSONObject();
        root.put("page_index", pageIndex);
        root.put("page", pageIndex + 1);
        root.put("pageCount", pageCount);
        root.put("pixelWidth", pixelWidth);
        root.put("pixelHeight", pixelHeight);
        root.put("coordinateSystem", "normalized-0-to-1");
        root.put("textYReference", "baseline");
        root.put("checkboxXYReference", "center");

        JSONArray hints = new JSONArray();
        if (detectedFields != null) {
            int index = 0;
            for (FormField field : detectedFields) {
                JSONObject hint = new JSONObject();
                String fieldId = String.format(java.util.Locale.ROOT,
                        "p%d_f%03d", pageIndex + 1, index++ + 1);
                hint.put("field_id", fieldId);
                hint.put("page_index", pageIndex);
                hint.put("x", field.x);
                hint.put("y", field.y);
                hint.put("width", field.width);
                hint.put("height", field.height);
                hint.put("type", field.type.name().toLowerCase(java.util.Locale.ROOT));
                hint.put("confidence", field.confidence);
                hint.put("anchor_x", field.textX());
                hint.put("baseline_y", field.textBaselineY());
                hint.put("mark_x", field.centerX());
                hint.put("mark_y", field.centerY());
                hint.put("exact_anchor_available", true);
                hints.put(hint);
            }
        }
        root.put("detectedFieldHints", hints);
        root.put("note", "Choose a field_id by meaning; the MCP server applies its exact Android anchor. Use free x/y only when no field matches.");
        return root;
    }

    private static float strictCoordinate(JSONObject item, String key) throws Exception {
        if (!item.has(key)) throw new IllegalArgumentException(key + " manquant");
        double value = item.getDouble(key);
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(key + " doit être compris strictement entre 0 et 1");
        }
        return (float) value;
    }

    private static float strictOptionalCoordinate(double value, String key) {
        if (value <= 0.0) return 0f;
        if (!Double.isFinite(value) || value > 1.0) {
            throw new IllegalArgumentException(key + " doit être compris entre 0 et 1");
        }
        return (float) value;
    }
}
