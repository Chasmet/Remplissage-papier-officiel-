type Placement = Record<string, any>;
type AnchoringMode = "field_id" | "nearby_unique_field";
type AnchoringReport = {
  overlay_id: string;
  field_id: string;
  mode: AnchoringMode;
  page_index: number;
};

function objectValue(value: unknown): Placement {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Placement
    : {};
}

function finite(value: unknown, fallback = 0): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.max(minimum, Math.min(maximum, value));
}

function fieldType(hint: Placement): string {
  const type = String(hint.type ?? hint.kind ?? "line").trim().toLowerCase();
  return type === "checkbox" || type === "check" ? "checkbox" : type;
}

function pageGeometry(document: Placement, pageIndex: number): { width: number; height: number } {
  const pages = Array.isArray(document.pages) ? document.pages : [];
  const page = objectValue(pages[pageIndex]);
  return {
    width: Math.max(1, finite(page.width ?? page.pixelWidth ?? page.pixel_width, 595)),
    height: Math.max(1, finite(page.height ?? page.pixelHeight ?? page.pixel_height, 842)),
  };
}

function textUnits(text: string): number {
  let units = 0;
  for (const character of text) {
    if ("ilI.,' `".includes(character)) units += 0.28;
    else if ("MW@#%&".includes(character)) units += 0.88;
    else if (character === " ") units += 0.32;
    else units += 0.54;
  }
  return Math.max(0.01, units);
}

function safeTextSize(placement: Placement, hint: Placement, document: Placement): number {
  const pageIndex = Math.max(0, Math.trunc(finite(hint.page_index, placement.page_index)));
  const geometry = pageGeometry(document, pageIndex);
  const requested = clamp(finite(placement.size, 8), 4, 144);
  const recommended = clamp(finite(hint.recommended_size, requested), 4, 24);
  const contentWidth = Math.max(0.001,
    finite(hint.content_width, finite(hint.width, 0.1) * 0.94));
  const fieldHeight = Math.max(0.001, finite(hint.height, 0.025));
  const widthLimit = contentWidth * geometry.width;
  const heightLimit = fieldHeight * geometry.height * 0.76;
  const widthFit = widthLimit / textUnits(String(placement.text ?? ""));
  const heightFit = heightLimit / 1.22;
  return Math.round(clamp(Math.min(requested, recommended, widthFit, heightFit), 4, 24) * 100) / 100;
}

function fieldAnchor(hint: Placement): { x: number; y: number } {
  const type = fieldType(hint);
  if (type === "checkbox") {
    return {
      x: finite(hint.mark_x, finite(hint.x) + finite(hint.width) * 0.5),
      y: finite(hint.mark_y, finite(hint.y) + finite(hint.height) * 0.5),
    };
  }
  return {
    x: finite(hint.anchor_x, finite(hint.x)),
    y: finite(hint.baseline_y, finite(hint.y) + finite(hint.height) * 0.75),
  };
}

function checkboxLike(placement: Placement): boolean {
  if (String(placement.kind ?? "").toLowerCase() === "checkbox") return true;
  return /^[xX✓✔☒☑●]$/.test(String(placement.text ?? "").trim());
}

function nearestUniqueHint(placement: Placement, hints: Placement[]): Placement | null {
  const pageIndex = Math.trunc(finite(placement.page_index, -1));
  const wantsCheckbox = checkboxLike(placement);
  const candidates = hints
    .filter((hint) => Math.trunc(finite(hint.page_index, -2)) === pageIndex)
    .filter((hint) => (fieldType(hint) === "checkbox") === wantsCheckbox)
    .map((hint) => {
      const anchor = fieldAnchor(hint);
      const dx = Math.abs(finite(placement.x, -2) - anchor.x);
      const dy = Math.abs(finite(placement.y, -2) - anchor.y);
      const inside = finite(placement.x, -2) >= finite(hint.x) - 0.012
        && finite(placement.x, -2) <= finite(hint.x) + finite(hint.width) + 0.012
        && finite(placement.y, -2) >= finite(hint.y) - 0.012
        && finite(placement.y, -2) <= finite(hint.y) + finite(hint.height) + 0.012;
      return { hint, dx, dy, inside, score: Math.hypot(dx, dy * 1.35) };
    })
    .filter((candidate) => wantsCheckbox
      ? candidate.dx <= 0.040 && candidate.dy <= 0.035
      : candidate.inside || (candidate.dx <= 0.025 && candidate.dy <= 0.018))
    .sort((left, right) => left.score - right.score);

  if (!candidates.length) return null;
  if (candidates.length > 1) {
    const separation = candidates[1].score - candidates[0].score;
    if (separation < 0.005 && candidates[1].score < candidates[0].score * 1.35) return null;
  }
  return candidates[0].hint;
}

function applyHint(placement: Placement, hint: Placement): Placement {
  const type = fieldType(hint);
  const anchor = fieldAnchor(hint);
  const pageIndex = Math.max(0, Math.trunc(finite(hint.page_index, placement.page_index)));
  const width = Math.max(0.001, finite(hint.content_width, finite(hint.width, 0.001)));
  const height = Math.max(0.001, finite(hint.height, 0.001));
  const anchored = {
    ...placement,
    page_index: pageIndex,
    x: clamp(anchor.x, 0, 1),
    y: clamp(anchor.y, 0, 1),
    width: clamp(width, 0.001, 1),
    height: clamp(height, 0.001, 1),
    field_id: String(hint.field_id),
    anchored_to_field: true,
  };

  if (type === "checkbox") {
    anchored.kind = "checkbox";
    anchored.align = "center";
    anchored.text = String(placement.text ?? "").trim()
      || String(hint.recommended_text ?? "X")
      || "X";
    anchored.checked = true;
    anchored.size = clamp(
      Math.min(finite(placement.size, 8), finite(hint.recommended_size, 7)),
      4,
      18,
    );
  } else {
    anchored.kind = String(placement.kind ?? "text").toLowerCase() === "date" ? "date" : "text";
    anchored.align = "left";
  }
  return anchored;
}

export function anchorPlacementsToFields(
  rawPlacements: unknown[],
  validatedPlacements: Placement[],
  rawHints: unknown,
  document: Placement,
): { placements: Placement[]; report: AnchoringReport[] } {
  const hints = Array.isArray(rawHints)
    ? rawHints.map(objectValue).filter((hint) => String(hint.field_id ?? "").trim())
    : [];
  const byId = new Map(hints.map((hint) => [String(hint.field_id).trim(), hint]));
  const report: AnchoringReport[] = [];

  const placements = validatedPlacements.map((placement, index) => {
    const raw = objectValue(rawPlacements[index]);
    const requestedFieldId = String(raw.field_id ?? placement.field_id ?? "").trim();
    let hint: Placement | null = null;
    let mode: AnchoringMode = "nearby_unique_field";

    if (requestedFieldId) {
      hint = byId.get(requestedFieldId) ?? null;
      mode = "field_id";
      if (!hint && hints.length) {
        throw new Error(`Champ ${requestedFieldId} introuvable dans les repères de l'application`);
      }
    } else if (hints.length) {
      hint = nearestUniqueHint(placement, hints);
    }

    if (!hint) return placement;
    let anchored = applyHint(placement, hint);
    if (fieldType(hint) !== "checkbox") {
      anchored = { ...anchored, size: safeTextSize(anchored, hint, document) };
    }
    report.push({
      overlay_id: String(anchored.overlay_id ?? ""),
      field_id: String(hint.field_id),
      mode,
      page_index: Number(anchored.page_index),
    });
    return anchored;
  });

  return { placements, report };
}
