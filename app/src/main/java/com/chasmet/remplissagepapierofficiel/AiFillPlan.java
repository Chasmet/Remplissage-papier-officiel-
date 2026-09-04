package com.chasmet.remplissagepapierofficiel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Protocol used by the future MCP/ChatGPT integration.
 * Detected form fields are only visual hints. AI placements are free and may target
 * any normalized x/y coordinate on any page of the PDF.
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

            String kind = item.optString("kind", item.optString("type", "text")).trim().toLowerCase(java.util.Locale.ROOT);
            boolean checked = item.optBoolean("checked", false);
            String text = item.optString("text", "").trim();
            if (text.isEmpty() && ("checkbox".equals(kind) || "check".equals(kind)) && checked) {
                text = "X";
            }
            if (text.isEmpty()) continue;

            int pageIndex;
            if (item.has("page_index")) {
                pageIndex = Math.max(0, item.optInt("page_index", 0));
            } else if (item.has("pageIndex")) {
                pageIndex = Math.max(0, item.optInt("pageIndex", 0));
            } else {
                int humanPage = item.optInt("page", 1);
                pageIndex = Math.max(0, humanPage - 1);
            }
            if (pageCount > 0) pageIndex = Math.min(pageCount - 1, Math.max(0, pageIndex));

            float x = normalizeCoordinate(item.optDouble("x", 0.10));
            float y = normalizeCoordinate(item.optDouble("y", 0.10));
            float size = (float) item.optDouble("size", item.optDouble("textSize", 8.0));
            size = Math.max(4f, Math.min(144f, size));
            String align = TextOverlay.normalizeAlign(item.optString("align", TextOverlay.ALIGN_LEFT));
            float width = normalizeOptionalSize(item.optDouble("width", 0.0));
            float height = normalizeOptionalSize(item.optDouble("height", 0.0));

            result.add(new TextOverlay(
                    pageIndex, x, y, text, size,
                    align, kind, width, height
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
        root.put("protocol", "remplissage-papier-officiel.ai-fill.v2");
        root.put("coordinateSystem", "normalized-0-to-1");
        root.put("detectedFieldsAreHintsOnly", true);
        root.put("fieldIdOptional", true);
        root.put("applicationSnappingDisabled", true);
        root.put("coordinatesAreAuthoritative", true);
        root.put("freePlacementAllowed", true);
        root.put("supportsAnyPage", true);
        root.put("supportsCheckboxes", true);
        root.put("supportsAppend", true);
        root.put("supportsReplaceDocument", true);
        root.put("supportsClearDocument", true);
        root.put("supportsReplacePage", true);
        root.put("supportsClearPage", true);
        root.put("supportsProfileReadWrite", true);
        root.put("supportsEditorStateControl", true);

        JSONObject placement = new JSONObject();
        placement.put("page", "1-based page number");
        placement.put("x", "0.0 to 1.0 from left edge. For text this is the alignment anchor; for checkbox this is the exact center.");
        placement.put("y", "0.0 to 1.0 from top edge. For text this is the exact baseline; for checkbox this is the exact center.");
        placement.put("text", "text to write; use X for a selected checkbox");
        placement.put("kind", "text or checkbox");
        placement.put("checked", "true for a selected checkbox");
        placement.put("size", "font size in PDF page units, 4 to 144; choose explicitly");
        placement.put("align", "left, center or right");
        placement.put("width", "optional normalized width metadata; application does not auto-fit");
        placement.put("height", "optional normalized height metadata; application does not auto-fit");
        root.put("placementSchema", placement);
        return root;
    }

    public static JSONObject pageContext(int pageIndex, int pageCount, int pixelWidth, int pixelHeight,
                                         List<FormField> detectedFields) throws Exception {
        JSONObject root = new JSONObject();
        root.put("page", pageIndex + 1);
        root.put("pageCount", pageCount);
        root.put("pixelWidth", pixelWidth);
        root.put("pixelHeight", pixelHeight);
        root.put("coordinateSystem", "normalized-0-to-1");

        JSONArray hints = new JSONArray();
        if (detectedFields != null) {
            for (FormField field : detectedFields) {
                JSONObject hint = new JSONObject();
                hint.put("x", field.x);
                hint.put("y", field.y);
                hint.put("width", field.width);
                hint.put("height", field.height);
                hint.put("type", field.type.name().toLowerCase(java.util.Locale.ROOT));
                hint.put("confidence", field.confidence);
                hints.put(hint);
            }
        }
        root.put("detectedFieldHints", hints);
        root.put("note", "Hints are advisory only. Final x/y/size/align from ChatGPT are authoritative and are not snapped by the application.");
        return root;
    }

    private static float normalizeCoordinate(double value) {
        double normalized = value;
        if (normalized > 1.0 && normalized <= 100.0) normalized /= 100.0;
        return (float) Math.max(0.0, Math.min(1.0, normalized));
    }

    private static float normalizeOptionalSize(double value) {
        if (value <= 0.0) return 0f;
        double normalized = value;
        if (normalized > 1.0 && normalized <= 100.0) normalized /= 100.0;
        return (float) Math.max(0.0, Math.min(1.0, normalized));
    }
}
