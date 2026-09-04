import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
  auth: { persistSession: false, autoRefreshToken: false },
});

const FUNCTION_SLUG = "remplissage-papier-mcp";
const PROTOCOL_VERSION = "2025-06-18";
const SERVER_VERSION = "5.1.0";
const PDF_BUCKET = "remplissage-mcp-pdfs";
const PAGE_BUCKET = "remplissage-mcp-pages";
const MAX_PAGE_IMAGE_BYTES = 1_500_000;
const MAX_PDF_BYTES = 25 * 1024 * 1024;
const ACTIVE_MS = 120_000;

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, mcp-session-id, x-remplissage-session",
  "Access-Control-Allow-Methods": "GET,POST,DELETE,OPTIONS",
  "Access-Control-Expose-Headers": "Mcp-Session-Id",
  "Cache-Control": "no-store",
};

function safeObject(value: unknown): Record<string, any> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, any>
    : {};
}

function jsonResponse(body: unknown, status = 200, extra: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, ...extra, "Content-Type": "application/json; charset=utf-8" },
  });
}

function htmlResponse(html: string, status = 200) {
  return new Response(html, {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "text/html; charset=utf-8",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer",
    },
  });
}

function rpcResult(id: unknown, result: unknown, sessionId?: string) {
  return jsonResponse({ jsonrpc: "2.0", id, result }, 200,
    sessionId ? { "Mcp-Session-Id": sessionId } : {});
}

function rpcError(id: unknown, code: number, message: string, data?: unknown, sessionId?: string) {
  return jsonResponse({
    jsonrpc: "2.0",
    id: id ?? null,
    error: { code, message, ...(data === undefined ? {} : { data }) },
  }, 200, sessionId ? { "Mcp-Session-Id": sessionId } : {});
}

function toolResult(data: unknown, isError = false) {
  const text = typeof data === "string" ? data : JSON.stringify(data);
  return {
    content: [{ type: "text", text }],
    structuredContent: typeof data === "object" && data !== null ? data : { value: data },
    isError,
  };
}

function bytesToBase64(bytes: Uint8Array) {
  const chunk = 0x8000;
  let binary = "";
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, Math.min(i + chunk, bytes.length)));
  }
  return btoa(binary);
}

function base64Url(bytes: Uint8Array) {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function randomToken() {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

async function sha256Hex(value: string) {
  const data = new TextEncoder().encode(value);
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", data));
  return Array.from(digest).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function tokenFromRequest(req: Request) {
  const url = new URL(req.url);
  return (url.searchParams.get("session") || req.headers.get("x-remplissage-session") || "").trim();
}

async function getSession(req: Request) {
  const token = tokenFromRequest(req);
  if (!token) {
    const { data, error } = await supabase
      .from("remplissage_mcp_sessions")
      .select("id,revoked,expires_at")
      .eq("label", "Android principal")
      .eq("revoked", false)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();
    if (error || !data) return null;
    if (new Date(data.expires_at).getTime() <= Date.now()) return null;
    void supabase.from("remplissage_mcp_sessions")
      .update({ last_seen_at: new Date().toISOString() }).eq("id", data.id);
    return { id: data.id as string, token: "" };
  }

  if (token.length < 32 || token.length > 256) return null;
  const tokenHash = await sha256Hex(token);
  const { data, error } = await supabase.from("remplissage_mcp_sessions")
    .select("id,revoked,expires_at").eq("token_hash", tokenHash).maybeSingle();
  if (error || !data || data.revoked) return null;
  if (new Date(data.expires_at).getTime() <= Date.now()) return null;
  void supabase.from("remplissage_mcp_sessions")
    .update({ last_seen_at: new Date().toISOString() }).eq("id", data.id);
  return { id: data.id as string, token };
}

function activeCutoffIso() {
  return new Date(Date.now() - ACTIVE_MS).toISOString();
}

function normalizeAlign(value: unknown) {
  const clean = String(value ?? "left").trim().toLowerCase();
  return clean === "center" || clean === "right" ? clean : "left";
}

function normalizeKind(value: unknown) {
  const clean = String(value ?? "text").trim().toLowerCase();
  if (clean === "checkbox" || clean === "check") return "checkbox";
  if (clean === "date") return "date";
  if (clean === "signature") return "signature";
  return "text";
}

function normalizeDataState(value: unknown) {
  const clean = String(value ?? "known").trim().toLowerCase();
  if (["unknown", "requires_user", "requires_signature"].includes(clean)) return clean;
  return "known";
}

function stableOverlayId(value: unknown, index: number) {
  const clean = String(value ?? "").trim();
  return clean || `overlay_${String(index + 1).padStart(3, "0")}_${crypto.randomUUID().slice(0, 8)}`;
}

function validatePlacements(raw: unknown) {
  if (!Array.isArray(raw)) throw new Error("placements doit être un tableau");
  if (raw.length > 2000) throw new Error("Maximum technique: 2000 placements par document");

  return raw.map((item, index) => {
    const p = safeObject(item);
    const pageIndex = Number(p.page_index ?? p.pageIndex ?? p.page ?? 0);
    const x = Number(p.x);
    const y = Number(p.y);
    const kind = normalizeKind(p.kind ?? p.type);
    const state = normalizeDataState(p.data_state ?? p.state);
    let text = String(p.text ?? "");
    const checked = Boolean(p.checked ?? false);
    if (!text.trim() && kind === "checkbox" && checked) text = String(p.mark ?? "X") || "X";

    if (!Number.isInteger(pageIndex) || pageIndex < 0 || pageIndex > 999) {
      throw new Error(`Placement ${index + 1}: page_index invalide`);
    }
    if (!Number.isFinite(x) || x < 0 || x > 1 || !Number.isFinite(y) || y < 0 || y > 1) {
      throw new Error(`Placement ${index + 1}: x/y doivent être strictement normalisés entre 0 et 1`);
    }

    if (kind === "signature") {
      throw new Error(`Placement ${index + 1}: une signature ne peut pas être créée automatiquement. Utilisez la signature utilisateur dans l'application.`);
    }

    if (!text.trim()) {
      if (state === "unknown" || state === "requires_user" || state === "requires_signature") {
        return {
          overlay_id: stableOverlayId(p.overlay_id ?? p.id ?? p.field_id, index),
          page_index: pageIndex,
          x, y,
          text: "",
          size: Number(p.size ?? 8),
          align: normalizeAlign(p.align),
          kind,
          data_state: state,
          render: false,
        };
      }
      throw new Error(`Placement ${index + 1}: texte vide`);
    }

    const size = Number(p.size ?? p.text_size ?? 8);
    if (!Number.isFinite(size) || size < 4 || size > 144) {
      throw new Error(`Placement ${index + 1}: taille de texte invalide`);
    }

    const out: Record<string, any> = {
      overlay_id: stableOverlayId(p.overlay_id ?? p.id ?? p.field_id, index),
      page_index: pageIndex,
      x, y, text, size,
      align: normalizeAlign(p.align),
      kind,
      data_state: state,
      render: true,
    };

    const width = Number(p.width ?? 0);
    const height = Number(p.height ?? 0);
    if (Number.isFinite(width) && width > 0 && width <= 1) out.width = width;
    if (Number.isFinite(height) && height > 0 && height <= 1) out.height = height;
    if (typeof p.field_id === "string" && p.field_id.trim()) out.field_id = p.field_id.trim();
    if (typeof p.checked === "boolean") out.checked = p.checked;
    return out;
  });
}

function renderablePlacements(registry: Array<Record<string, any>>) {
  return registry.filter((p) => p && p.render !== false && String(p.text ?? "").length > 0)
    .map((p) => {
      const copy = { ...p };
      delete copy.render;
      return copy;
    });
}

function registryFromDocument(document: Record<string, any>) {
  const raw = Array.isArray(document.overlay_registry)
    ? document.overlay_registry
    : (Array.isArray(document.current_overlays) ? document.current_overlays : []);
  try {
    return validatePlacements(raw);
  } catch {
    return raw.map((p: any, i: number) => ({
      overlay_id: stableOverlayId(p?.overlay_id ?? p?.id, i),
      page_index: Number(p?.page_index ?? 0),
      x: Number(p?.x ?? 0),
      y: Number(p?.y ?? 0),
      text: String(p?.text ?? ""),
      size: Number(p?.size ?? 8),
      align: normalizeAlign(p?.align),
      kind: normalizeKind(p?.kind),
      data_state: normalizeDataState(p?.data_state),
      render: true,
      ...(Number(p?.width) > 0 ? { width: Number(p.width) } : {}),
      ...(Number(p?.height) > 0 ? { height: Number(p.height) } : {}),
    }));
  }
}

function recentVisualPages(document: Record<string, any>, maxAgeMs = 15 * 60 * 1000) {
  const viewed = new Set<number>();
  const raw = safeObject(document.vision_viewed_at);
  const now = Date.now();
  for (const [key, value] of Object.entries(raw)) {
    const pageIndex = Number(key);
    const time = typeof value === "string" ? Date.parse(value) : NaN;
    if (Number.isInteger(pageIndex) && pageIndex >= 0 && Number.isFinite(time)
        && now - time >= 0 && now - time <= maxAgeMs) viewed.add(pageIndex);
  }
  return viewed;
}

function missingVisualInspectionPages(document: Record<string, any>, placements: Array<Record<string, any>>) {
  const viewed = recentVisualPages(document);
  const required = new Set<number>();
  for (const placement of placements) {
    const p = Number(placement.page_index ?? -1);
    if (Number.isInteger(p) && p >= 0 && placement.render !== false) required.add(p);
  }
  return Array.from(required).filter((p) => !viewed.has(p)).sort((a, b) => a - b);
}

async function markVisualPageViewed(sessionId: string, jobId: string,
                                    document: Record<string, any>, pageIndex: number) {
  const viewedAt = safeObject(document.vision_viewed_at);
  viewedAt[String(pageIndex)] = new Date().toISOString();
  document.vision_viewed_at = viewedAt;
  const pages = Array.isArray(document.vision_viewed_pages)
    ? document.vision_viewed_pages.map(Number).filter((v: number) => Number.isInteger(v) && v >= 0)
    : [];
  if (!pages.includes(pageIndex)) pages.push(pageIndex);
  document.vision_viewed_pages = pages;
  const { error } = await supabase.from("remplissage_mcp_jobs")
    .update({ document, updated_at: new Date().toISOString() })
    .eq("id", jobId).eq("session_id", sessionId);
  if (error) throw error;
}

async function pageImageToolResult(sessionId: string, jobId: string, pageIndex: number,
                                   document: Record<string, any>, text: string,
                                   structuredExtra: Record<string, unknown> = {}) {
  const path = `${sessionId}/${jobId}/page-${String(pageIndex).padStart(4, "0")}.jpg`;
  const { data, error } = await supabase.storage.from(PAGE_BUCKET).download(path);
  if (error || !data) throw new Error("Image de cette page non disponible");
  const bytes = new Uint8Array(await data.arrayBuffer());
  if (bytes.length > MAX_PAGE_IMAGE_BYTES) throw new Error("Image de page trop volumineuse");
  await markVisualPageViewed(sessionId, jobId, document, pageIndex);
  return {
    __mcp_image_result: true,
    content: [
      { type: "text", text },
      { type: "image", data: bytesToBase64(bytes), mimeType: "image/jpeg" },
    ],
    structuredContent: {
      job_id: jobId,
      page_index: pageIndex,
      page_number: pageIndex + 1,
      page_count: document.page_count ?? document.pageCount ?? null,
      coordinate_system: "normalized_0_1",
      text_y_reference: "baseline",
      checkbox_xy_reference: "center",
      ...structuredExtra,
    },
  };
}

async function previewImageToolResult(sessionId: string, jobId: string, pageIndex: number,
                                      text: string,
                                      structuredExtra: Record<string, unknown> = {}) {
  const path = `${sessionId}/${jobId}/preview-${String(pageIndex).padStart(4, "0")}.jpg`;
  const { data, error } = await supabase.storage.from(PAGE_BUCKET).download(path);
  if (error || !data) throw new Error("Prévisualisation indisponible");
  const bytes = new Uint8Array(await data.arrayBuffer());
  if (bytes.length > MAX_PAGE_IMAGE_BYTES) throw new Error("Prévisualisation trop volumineuse");
  return {
    __mcp_image_result: true,
    content: [
      { type: "text", text },
      { type: "image", data: bytesToBase64(bytes), mimeType: "image/jpeg" },
    ],
    structuredContent: {
      job_id: jobId,
      page_index: pageIndex,
      page_number: pageIndex + 1,
      ...structuredExtra,
    },
  };
}

async function signedPdfUrl(path: string, seconds = 900) {
  const { data, error } = await supabase.storage.from(PDF_BUCKET).createSignedUrl(path, seconds);
  if (error || !data?.signedUrl) throw new Error("Impossible de créer le lien PDF");
  return data.signedUrl;
}

async function filledDocumentMeta(job: any) {
  if (!job?.filled_pdf_path) return { filled_pdf_available: false };
  return {
    filled_pdf_available: true,
    filename: "rempli-" + String(job.document?.name ?? "document.pdf"),
    download_url: await signedPdfUrl(String(job.filled_pdf_path), 900),
    download_url_expires_in_seconds: 900,
    uploaded_at: job.filled_pdf_uploaded_at ?? null,
  };
}

async function getJobOwned(sessionId: string, jobId: string) {
  const { data, error } = await supabase.from("remplissage_mcp_jobs")
    .select("*").eq("id", jobId).eq("session_id", sessionId).maybeSingle();
  if (error) throw error;
  if (!data) throw new Error("Document introuvable");
  return data;
}

async function submitRegistryCommand(sessionId: string, job: any,
                                     registry: Array<Record<string, any>>,
                                     notes: string,
                                     changedIds: string[] = []) {
  const document = safeObject(job.document);
  document.overlay_registry = registry;
  document.overlay_registry_updated_at = new Date().toISOString();
  document.last_changed_overlay_ids = changedIds;

  const plan = {
    command_id: crypto.randomUUID(),
    schema_version: 4,
    mode: "replace_document",
    coordinate_system: "normalized_0_1",
    text_y_reference: "baseline",
    checkbox_xy_reference: "center",
    placements: renderablePlacements(registry),
    profile_updates: {},
    notes,
    generated_at: new Date().toISOString(),
  };
  const submittedAt = new Date().toISOString();
  const { error } = await supabase.from("remplissage_mcp_jobs")
    .update({
      status: "ready",
      fill_plan: plan,
      document,
      preview_pages: [],
      preview_updated_at: null,
      filled_pdf_path: null,
      filled_pdf_uploaded_at: null,
      error_message: null,
      updated_at: submittedAt,
      is_active: true,
    }).eq("id", job.id).eq("session_id", sessionId);
  if (error) throw error;
  return { plan, submittedAt };
}

async function waitForPreview(sessionId: string, jobId: string, pageIndex: number,
                              submittedAt: string, maxMs = 12_000) {
  const started = Date.now();
  while (Date.now() - started < maxMs) {
    await new Promise((resolve) => setTimeout(resolve, 600));
    const { data, error } = await supabase.from("remplissage_mcp_jobs")
      .select("status,preview_pages,preview_updated_at,document")
      .eq("id", jobId).eq("session_id", sessionId).maybeSingle();
    if (error) throw error;
    const pages = Array.isArray(data?.preview_pages) ? data.preview_pages.map(Number) : [];
    const t = data?.preview_updated_at ? Date.parse(String(data.preview_updated_at)) : NaN;
    if (pages.includes(pageIndex) && Number.isFinite(t) && t >= Date.parse(submittedAt)) {
      return data;
    }
  }
  return null;
}

function getPageGeometry(document: Record<string, any>, pageIndex: number) {
  const pages = Array.isArray(document.pages) ? document.pages : [];
  const p = pages[pageIndex] ?? {};
  const width = Number(p.width ?? p.pixelWidth ?? p.pixel_width ?? document.page_width ?? 0);
  const height = Number(p.height ?? p.pixelHeight ?? p.pixel_height ?? document.page_height ?? 0);
  return {
    page_index: pageIndex,
    width: Number.isFinite(width) && width > 0 ? width : null,
    height: Number.isFinite(height) && height > 0 ? height : null,
  };
}

function estimateTextWidthPx(text: string, size: number) {
  // Approximation de sécurité côté serveur. Le calcul exact est disponible dans l'APK 1.10 via Android Paint.
  let units = 0;
  for (const c of text) {
    if ("ilI.,' `".includes(c)) units += 0.28;
    else if ("MW@#%&".includes(c)) units += 0.88;
    else if (c === " ") units += 0.32;
    else units += 0.54;
  }
  return Math.max(0, units * size);
}

function validateLayoutServer(document: Record<string, any>, registry: Array<Record<string, any>>) {
  const warnings: any[] = [];
  const boxes: any[] = [];
  for (const p of registry) {
    if (p.render === false || !String(p.text ?? "")) continue;
    const geom = getPageGeometry(document, Number(p.page_index ?? 0));
    const pageW = geom.width ?? 1000;
    const pageH = geom.height ?? 1414;
    const size = Number(p.size ?? 8);
    const widthPx = estimateTextWidthPx(String(p.text), size);
    const heightPx = size * 1.25;
    const anchorX = Number(p.x) * pageW;
    const baselineY = Number(p.y) * pageH;
    let left = anchorX;
    if (p.align === "center" || p.kind === "checkbox") left -= widthPx / 2;
    else if (p.align === "right") left -= widthPx;
    const top = p.kind === "checkbox" ? baselineY - heightPx / 2 : baselineY - size;
    const bottom = p.kind === "checkbox" ? baselineY + heightPx / 2 : baselineY + size * 0.25;
    const right = left + widthPx;
    if (left < 0 || top < 0 || right > pageW || bottom > pageH) {
      warnings.push({ overlay_id: p.overlay_id, problem: "text_outside_page" });
    }
    if (Number(p.width) > 0 && widthPx > Number(p.width) * pageW) {
      warnings.push({ overlay_id: p.overlay_id, problem: "text_too_wide" });
    }
    if (Number(p.height) > 0 && heightPx > Number(p.height) * pageH) {
      warnings.push({ overlay_id: p.overlay_id, problem: "font_too_large" });
    }
    boxes.push({ overlay_id: p.overlay_id, page_index: p.page_index, left, top, right, bottom });
  }
  for (let i = 0; i < boxes.length; i++) {
    for (let j = i + 1; j < boxes.length; j++) {
      const a = boxes[i], b = boxes[j];
      if (a.page_index !== b.page_index) continue;
      const overlap = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
      if (overlap) warnings.push({
        overlay_id: a.overlay_id,
        other_overlay_id: b.overlay_id,
        problem: "overlay_overlap",
      });
    }
  }
  return {
    valid: warnings.length === 0,
    warnings,
    engine: "server_safety_estimate",
    exact_android_metrics_available_in_app_version: "1.10.0",
  };
}

async function removeJobStorage(sessionId: string, jobId: string,
                                originalPdfPath: unknown, filledPdfPath: unknown) {
  const pdfPaths = [originalPdfPath, filledPdfPath]
    .filter((p) => typeof p === "string" && p.length > 0) as string[];
  if (pdfPaths.length) {
    const { error } = await supabase.storage.from(PDF_BUCKET).remove(pdfPaths);
    if (error) throw error;
  }
  const folder = `${sessionId}/${jobId}`;
  const { data: files, error: listError } = await supabase.storage.from(PAGE_BUCKET).list(folder, { limit: 1000 });
  if (listError) throw listError;
  const paths = (files ?? []).filter((f: any) => f?.name).map((f: any) => `${folder}/${f.name}`);
  if (paths.length) {
    const { error } = await supabase.storage.from(PAGE_BUCKET).remove(paths);
    if (error) throw error;
  }
}

const placementSchema = {
  type: "object",
  properties: {
    overlay_id: { type: "string", description: "Identifiant stable pour correction locale ultérieure." },
    page_index: { type: "integer", minimum: 0 },
    x: { type: "number", minimum: 0, maximum: 1 },
    y: { type: "number", minimum: 0, maximum: 1 },
    text: { type: "string", maxLength: 10000 },
    size: { type: "number", minimum: 4, maximum: 144 },
    align: { type: "string", enum: ["left", "center", "right"] },
    kind: { type: "string", enum: ["text", "checkbox", "date", "signature"] },
    width: { type: "number", minimum: 0, maximum: 1 },
    height: { type: "number", minimum: 0, maximum: 1 },
    field_id: { type: "string", description: "Hint informatif uniquement; ne modifie jamais x/y." },
    checked: { type: "boolean" },
    mark: { type: "string" },
    data_state: { type: "string", enum: ["known", "unknown", "requires_user", "requires_signature"] },
  },
  required: ["page_index", "x", "y", "size"],
  additionalProperties: false,
};

const tools = [
  {
    name: "paper_open_active_document",
    description: "Point d'entrée principal. Trouve le document actif et renvoie immédiatement sa vraie page 1 en image, avec système de coordonnées exact.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
    annotations: { title: "Ouvrir le document actif", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_get_active_document",
    description: "Retourne les métadonnées du document actif sans image.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
    annotations: { title: "Document actif", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_list_pending_documents",
    description: "Liste les documents actifs actuellement synchronisés par l'application Android.",
    inputSchema: { type: "object", properties: { limit: { type: "integer", minimum: 1, maximum: 50, default: 10 } }, additionalProperties: false },
    annotations: { title: "Documents disponibles", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_list_documents",
    description: "Liste l'historique persistant des documents de la session.",
    inputSchema: { type: "object", properties: { limit: { type: "integer", minimum: 1, maximum: 50, default: 20 } }, additionalProperties: false },
    annotations: { title: "Historique des documents", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_get_bridge_status",
    description: "Vérifie si l'application Android contacte réellement le pont MCP maintenant.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
    annotations: { title: "État liaison Android ↔ ChatGPT", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_get_document_context",
    description: "Retourne le contexte et la vraie page courante en image. Les hints n'ont aucune autorité sur les coordonnées choisies par ChatGPT.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" } }, required: ["job_id"], additionalProperties: false },
    annotations: { title: "Contexte du document", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_get_document_geometry",
    description: "Retourne les dimensions de chaque page et le système de coordonnées. x/y sont strictement 0..1; y texte = baseline; x/y checkbox = centre.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" } }, required: ["job_id"], additionalProperties: false },
    annotations: { title: "Géométrie exacte du document", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_get_detected_fields",
    description: "Retourne uniquement les zones détectées comme indications visuelles. Elles ne déplacent jamais les overlays.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" } }, required: ["job_id"], additionalProperties: false },
    annotations: { title: "Zones détectées", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_get_page_image",
    description: "Renvoie la vraie page entière du PDF sans overlays. ChatGPT doit se baser sur cette image pour les placements.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" }, page_index: { type: "integer", minimum: 0 } }, required: ["job_id", "page_index"], additionalProperties: false },
    annotations: { title: "Voir une page", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_get_preview_image",
    description: "Renvoie la vraie prévisualisation Android après application. À utiliser pour contrôler puis corriger localement.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" }, page_index: { type: "integer", minimum: 0 } }, required: ["job_id", "page_index"], additionalProperties: false },
    annotations: { title: "Voir le résultat rempli", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_get_profile",
    description: "Lit le profil synchronisé par l'application.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" } }, additionalProperties: false },
    annotations: { title: "Lire le profil", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_submit_fill_plan",
    description: "Envoie le plan visuel initial. Les coordonnées sont autoritaires et chaque élément reçoit un overlay_id stable. Après application, la preview Android peut être renvoyée directement.",
    inputSchema: {
      type: "object",
      properties: {
        job_id: { type: "string" },
        placements: { type: "array", maxItems: 2000, items: placementSchema },
        notes: { type: "string", maxLength: 10000 },
      },
      required: ["job_id", "placements"],
      additionalProperties: false,
    },
    annotations: { title: "Remplir le document", readOnlyHint: false, destructiveHint: true, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_add_overlay",
    description: "Ajoute un seul élément sans demander à ChatGPT de renvoyer les autres. Le serveur conserve le registre et reconstruit le plan Android automatiquement.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" }, overlay: placementSchema }, required: ["job_id", "overlay"], additionalProperties: false },
    annotations: { title: "Ajouter un élément", readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: false },
  },
  {
    name: "paper_update_overlay",
    description: "Correction locale d'un seul overlay par overlay_id. Accepte x/y absolus ou x_delta/y_delta, taille, alignement ou texte. Les autres éléments restent inchangés.",
    inputSchema: {
      type: "object",
      properties: {
        job_id: { type: "string" },
        overlay_id: { type: "string" },
        x: { type: "number", minimum: 0, maximum: 1 },
        y: { type: "number", minimum: 0, maximum: 1 },
        x_delta: { type: "number", minimum: -1, maximum: 1 },
        y_delta: { type: "number", minimum: -1, maximum: 1 },
        size: { type: "number", minimum: 4, maximum: 144 },
        align: { type: "string", enum: ["left", "center", "right"] },
        text: { type: "string", maxLength: 10000 },
      },
      required: ["job_id", "overlay_id"],
      additionalProperties: false,
    },
    annotations: { title: "Corriger un élément", readOnlyHint: false, destructiveHint: true, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_delete_overlay",
    description: "Supprime un seul overlay par son identifiant stable, sans toucher aux autres.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" }, overlay_id: { type: "string" } }, required: ["job_id", "overlay_id"], additionalProperties: false },
    annotations: { title: "Supprimer un élément", readOnlyHint: false, destructiveHint: true, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_list_overlays",
    description: "Retourne le registre courant avec overlay_id, coordonnées, texte, taille, état et type.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" } }, required: ["job_id"], additionalProperties: false },
    annotations: { title: "Éléments actuels", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_validate_layout",
    description: "Contrôle les dépassements, largeurs, tailles et chevauchements. Le contrôle serveur est une estimation de sécurité; l'APK 1.10 possède le moteur Android Paint exact.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" } }, required: ["job_id"], additionalProperties: false },
    annotations: { title: "Valider la mise en page", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_clear_overlays",
    description: "Efface les éléments ajoutés sans supprimer le PDF.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" }, page_index: { type: "integer", minimum: 0 } }, required: ["job_id"], additionalProperties: false },
    annotations: { title: "Effacer les remplissages", readOnlyHint: false, destructiveHint: true, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_control_application",
    description: "Commande de compatibilité pour append/replace/clear/update_profile. Préférer paper_update_overlay pour les corrections fines.",
    inputSchema: {
      type: "object",
      properties: {
        job_id: { type: "string" },
        action: { type: "string", enum: ["append", "replace_document", "clear_document", "replace_page", "clear_page", "update_profile", "set_editor_state"] },
        page_index: { type: "integer", minimum: 0 },
        placements: { type: "array", maxItems: 2000, items: placementSchema },
        profile_updates: { type: "object" },
        editor_updates: { type: "object" },
        notes: { type: "string", maxLength: 10000 },
      },
      required: ["job_id", "action"],
      additionalProperties: false,
    },
    annotations: { title: "Contrôler l'application", readOnlyHint: false, destructiveHint: true, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_get_job_status",
    description: "Vérifie l'état et renvoie la preview ainsi que le lien du PDF final lorsqu'ils sont disponibles.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" } }, required: ["job_id"], additionalProperties: false },
    annotations: { title: "État du document", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_get_filled_document",
    description: "Récupère le PDF final renvoyé par l'application.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" } }, required: ["job_id"], additionalProperties: false },
    annotations: { title: "Récupérer le PDF final", readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_delete_document",
    description: "Supprime définitivement un document précis du backend.",
    inputSchema: { type: "object", properties: { job_id: { type: "string" } }, required: ["job_id"], additionalProperties: false },
    annotations: { title: "Supprimer un document", readOnlyHint: false, destructiveHint: true, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_clear_server_documents",
    description: "Vide tous les documents de cette session côté serveur.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
    annotations: { title: "Vider les documents serveur", readOnlyHint: false, destructiveHint: true, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "paper_import_pdf",
    description: "Importe un PDF vers l'application, directement depuis une URL si possible, sinon prépare un lien sécurisé d'envoi.",
    inputSchema: { type: "object", properties: { filename: { type: "string" }, source_url: { type: "string" }, label: { type: "string" } }, required: ["filename"], additionalProperties: false },
    annotations: { title: "Importer un PDF", readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: true },
  },
  {
    name: "paper_prepare_chat_upload",
    description: "Prépare l'envoi manuel d'un PDF depuis ChatGPT vers l'application.",
    inputSchema: { type: "object", properties: { filename: { type: "string" }, label: { type: "string" } }, required: ["filename"], additionalProperties: false },
    annotations: { title: "Envoyer un PDF vers l'application", readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: false },
  },
];

async function handleToolCall(sessionId: string, name: string, args: Record<string, any>) {
  if (name === "paper_list_pending_documents") {
    const limit = Math.max(1, Math.min(50, Number(args.limit ?? 10)));
    const { data, error } = await supabase.from("remplissage_mcp_jobs")
      .select("id,status,source,is_active,document,created_at,updated_at,app_last_seen_at")
      .eq("session_id", sessionId).eq("is_active", true).neq("status", "cancelled")
      .gte("app_last_seen_at", activeCutoffIso())
      .order("updated_at", { ascending: false }).limit(limit);
    if (error) throw error;
    return {
      jobs: data ?? [],
      active_document_available: (data ?? []).length > 0,
      next_action: (data ?? []).length > 0
        ? "Appelez paper_get_document_context sur le job_id avant tout placement afin de recevoir la vraie page en image."
        : "Ouvrez un PDF dans l'application Android.",
    };
  }

  if (name === "paper_list_documents") {
    const limit = Math.max(1, Math.min(50, Number(args.limit ?? 20)));
    const { data, error } = await supabase.from("remplissage_mcp_jobs")
      .select("id,status,document,created_at,updated_at,expires_at,app_last_seen_at")
      .eq("session_id", sessionId).order("created_at", { ascending: false }).limit(limit);
    if (error) throw error;
    return { jobs: data ?? [] };
  }

  if (name === "paper_get_bridge_status") {
    const { data } = await supabase.from("remplissage_mcp_jobs")
      .select("id,status,is_active,document,app_last_seen_at,updated_at")
      .eq("session_id", sessionId).eq("is_active", true).neq("status", "cancelled")
      .order("updated_at", { ascending: false }).limit(1);
    const row = data?.[0] ?? null;
    if (!row) return { connected: false, active_document_available: false, reason: "Aucun document actif" };
    const last = row.app_last_seen_at ? Date.parse(row.app_last_seen_at) : NaN;
    const connected = Number.isFinite(last) && Date.now() - last <= ACTIVE_MS;
    return {
      connected,
      active_document_available: connected,
      job_id: row.id,
      status: row.status,
      name: row.document?.name ?? "Document",
      page_count: row.document?.page_count ?? row.document?.pageCount ?? null,
      app_version: row.document?.app_version ?? null,
      app_last_seen_at: row.app_last_seen_at,
      seconds_since_app_contact: Number.isFinite(last) ? Math.max(0, Math.floor((Date.now() - last) / 1000)) : null,
    };
  }

  if (name === "paper_open_active_document" || name === "paper_get_active_document") {
    const { data, error } = await supabase.from("remplissage_mcp_jobs")
      .select("id,status,source,is_active,document,created_at,updated_at,expires_at,app_last_seen_at,preview_pages,preview_updated_at")
      .eq("session_id", sessionId).eq("is_active", true).neq("status", "cancelled")
      .gte("app_last_seen_at", activeCutoffIso())
      .order("updated_at", { ascending: false }).limit(1);
    if (error) throw error;
    const row = data?.[0] ?? null;
    if (!row) return { active_document_available: false, document: null };
    const document = safeObject(row.document);
    const meta = {
      job_id: row.id,
      name: document.name ?? "Document",
      page_count: document.page_count ?? document.pageCount ?? null,
      status: row.status,
      source: row.source,
      app_version: document.app_version ?? null,
      page_images_ready: document.page_images_ready === true,
      current_overlays: registryFromDocument(document),
      preview_pages: row.preview_pages ?? [],
      coordinate_system: "normalized_0_1",
      text_y_reference: "baseline",
      checkbox_xy_reference: "center",
    };
    if (name === "paper_get_active_document") return { active_document_available: true, document: meta };
    const pageCount = Number(document.page_count ?? document.pageCount ?? 0);
    if (document.page_images_ready === true && pageCount > 0) {
      return await pageImageToolResult(sessionId, row.id, 0, document,
        "Document actif trouvé. Voici la vraie page 1. Les coordonnées sont strictes: x/y 0..1; y texte = baseline; checkbox = centre.",
        { active_document_available: true, document: meta });
    }
    return { active_document_available: true, document: meta, instruction: "Images de pages pas encore prêtes." };
  }

  if (name === "paper_get_document_context" || name === "paper_get_document_geometry" || name === "paper_get_detected_fields") {
    const job = await getJobOwned(sessionId, String(args.job_id ?? ""));
    const document = safeObject(job.document);
    if (name === "paper_get_document_geometry") {
      const count = Number(document.page_count ?? document.pageCount ?? 0);
      return {
        job_id: job.id,
        coordinate_system: "normalized_0_1",
        x_reference: "left_to_right",
        y_reference: "top_to_bottom",
        text_y_reference: "baseline",
        checkbox_xy_reference: "center",
        coordinates_authoritative: true,
        snapping_disabled: true,
        pages: Array.from({ length: Math.max(0, count) }, (_, i) => getPageGeometry(document, i)),
      };
    }
    if (name === "paper_get_detected_fields") {
      return { job_id: job.id, detected_fields_are_hints_only: true, fields: job.field_hints ?? [] };
    }
    const context = {
      job_id: job.id,
      status: job.status,
      coordinate_system: {
        x: "0..1 left to right",
        y: "0..1 top to bottom",
        text_y_reference: "baseline",
        checkbox_xy_reference: "center",
        authoritative: true,
      },
      document,
      profile: job.profile ?? {},
      field_hints: job.field_hints ?? [],
      overlay_registry: registryFromDocument(document),
    };
    const pageCount = Number(document.page_count ?? document.pageCount ?? 0);
    const requestedPage = Math.max(0, Math.min(
      Math.max(0, pageCount - 1),
      Number(document.current_page_index ?? 0),
    ));
    if (document.page_images_ready === true && pageCount > 0) {
      return await pageImageToolResult(sessionId, job.id, requestedPage, document,
        `Voici la vraie page ${requestedPage + 1} du PDF. Placez les éléments uniquement après cette inspection visuelle. Après chaque remplissage, contrôlez la preview et corrigez jusqu'à alignement parfait.`,
        {
          ...context,
          legacy_connector_compatible: true,
          next_action: "Appelez paper_submit_fill_plan avec des coordonnées calculées sur cette image.",
        });
    }
    return {
      ...context,
      next_action: "Attendez la fin de la synchronisation des images de pages, puis relancez cet outil.",
    };
  }

  if (name === "paper_get_page_image") {
    const jobId = String(args.job_id ?? "").trim();
    const pageIndex = Number(args.page_index ?? -1);
    const job = await getJobOwned(sessionId, jobId);
    if (!Number.isInteger(pageIndex) || pageIndex < 0) throw new Error("page_index invalide");
    return await pageImageToolResult(sessionId, jobId, pageIndex, safeObject(job.document),
      `Page ${pageIndex + 1}. Regardez directement l'image réelle; n'utilisez pas l'OCR comme source de placement.`);
  }

  if (name === "paper_get_preview_image") {
    const jobId = String(args.job_id ?? "").trim();
    const pageIndex = Number(args.page_index ?? -1);
    const job = await getJobOwned(sessionId, jobId);
    const pages = Array.isArray(job.preview_pages) ? job.preview_pages.map(Number) : [];
    if (!pages.includes(pageIndex)) throw new Error("Prévisualisation pas encore disponible pour cette page");
    return await previewImageToolResult(sessionId, jobId, pageIndex,
      `Prévisualisation Android réelle, page ${pageIndex + 1}. Vérifiez les lignes, tailles et collisions puis corrigez avec paper_update_overlay.`,
      { preview_updated_at: job.preview_updated_at, overlay_registry: registryFromDocument(safeObject(job.document)) });
  }

  if (name === "paper_get_profile") {
    const requested = String(args.job_id ?? "").trim();
    let query = supabase.from("remplissage_mcp_jobs").select("id,profile,updated_at")
      .eq("session_id", sessionId).order("created_at", { ascending: false }).limit(1);
    if (requested) query = query.eq("id", requested);
    const { data, error } = await query;
    if (error) throw error;
    if (!data?.[0]) throw new Error("Aucun profil synchronisé");
    return { job_id: data[0].id, profile: data[0].profile ?? {}, updated_at: data[0].updated_at };
  }

  if (name === "paper_submit_fill_plan") {
    const jobId = String(args.job_id ?? "").trim();
    const job = await getJobOwned(sessionId, jobId);
    const document = safeObject(job.document);
    const rawPlacements = Array.isArray(args.placements) ? args.placements : [];
    const previousRegistry = registryFromDocument(document);
    const registry = validatePlacements(rawPlacements).map((placement, index) => {
      const raw = safeObject(rawPlacements[index]);
      const explicitId = String(raw.overlay_id ?? raw.id ?? raw.field_id ?? "").trim();
      const previous = previousRegistry[index];
      if (!explicitId && previous?.overlay_id) {
        return { ...placement, overlay_id: previous.overlay_id };
      }
      return placement;
    });
    const missing = missingVisualInspectionPages(document, registry);
    if (missing.length) {
      if (document.page_images_ready !== true) throw new Error("Les pages visuelles ne sont pas encore prêtes");
      return await pageImageToolResult(sessionId, jobId, missing[0], document,
        `Aucun changement n'a été appliqué. Inspectez réellement la page ${missing[0] + 1}, corrigez vos coordonnées à partir de cette image, puis renvoyez le plan complet avec paper_submit_fill_plan.`,
        {
          inspection_required: true,
          plan_applied: false,
          remaining_pages: missing.slice(1).map((p) => p + 1),
        });
    }
    const { submittedAt } = await submitRegistryCommand(sessionId, job, registry,
      String(args.notes ?? "Plan visuel initial"), registry.map((p) => String(p.overlay_id)));
    const renderable = renderablePlacements(registry);
    if (renderable.length) {
      const page = Number(renderable[0].page_index ?? 0);
      const state = await waitForPreview(sessionId, jobId, page, submittedAt);
      if (state) {
        return await previewImageToolResult(sessionId, jobId, page,
          `Android a appliqué le plan. Inspectez cette vraie preview. Si paper_update_overlay est visible, corrigez localement; sinon renvoyez le plan complet corrigé avec paper_submit_fill_plan. Ne terminez pas tant que l'alignement n'est pas propre.`,
          {
            applied: true,
            review_required: true,
            legacy_correction_tool: "paper_submit_fill_plan",
            overlay_registry: registry,
            changed_overlay_ids: registry.map((p) => p.overlay_id),
          });
      }
    }
    return { ok: true, job_id: jobId, status: "ready", overlay_registry: registry, preview_pending: true };
  }

  if (name === "paper_add_overlay") {
    const jobId = String(args.job_id ?? "").trim();
    const job = await getJobOwned(sessionId, jobId);
    const registry = registryFromDocument(safeObject(job.document));
    const incoming = validatePlacements([args.overlay])[0];
    if (registry.some((p) => p.overlay_id === incoming.overlay_id)) {
      throw new Error("overlay_id déjà utilisé");
    }
    registry.push(incoming);
    const { submittedAt } = await submitRegistryCommand(sessionId, job, registry,
      `Ajout local ${incoming.overlay_id}`, [incoming.overlay_id]);
    const state = incoming.render !== false
      ? await waitForPreview(sessionId, jobId, Number(incoming.page_index), submittedAt)
      : null;
    if (state) return await previewImageToolResult(sessionId, jobId, Number(incoming.page_index),
      `Overlay ${incoming.overlay_id} ajouté. Vérifiez la preview puis corrigez localement si nécessaire.`,
      { overlay_id: incoming.overlay_id, overlay_registry: registry });
    return { ok: true, job_id: jobId, overlay: incoming, preview_pending: true };
  }

  if (name === "paper_update_overlay") {
    const jobId = String(args.job_id ?? "").trim();
    const overlayId = String(args.overlay_id ?? "").trim();
    if (!overlayId) throw new Error("overlay_id manquant");
    const job = await getJobOwned(sessionId, jobId);
    const registry = registryFromDocument(safeObject(job.document));
    const index = registry.findIndex((p) => p.overlay_id === overlayId);
    if (index < 0) throw new Error("overlay_id introuvable");
    const p = { ...registry[index] };

    if (args.x !== undefined) p.x = Number(args.x);
    if (args.y !== undefined) p.y = Number(args.y);
    if (args.x_delta !== undefined) p.x = Number(p.x) + Number(args.x_delta);
    if (args.y_delta !== undefined) p.y = Number(p.y) + Number(args.y_delta);
    if (!Number.isFinite(p.x) || p.x < 0 || p.x > 1 || !Number.isFinite(p.y) || p.y < 0 || p.y > 1) {
      throw new Error("La correction ferait sortir x/y de 0..1");
    }
    if (args.size !== undefined) p.size = Number(args.size);
    if (args.align !== undefined) p.align = normalizeAlign(args.align);
    if (args.text !== undefined) p.text = String(args.text);
    registry[index] = validatePlacements([p])[0];

    const { submittedAt } = await submitRegistryCommand(sessionId, job, registry,
      `Correction locale ${overlayId}`, [overlayId]);
    const state = registry[index].render !== false
      ? await waitForPreview(sessionId, jobId, Number(registry[index].page_index), submittedAt)
      : null;
    if (state) return await previewImageToolResult(sessionId, jobId, Number(registry[index].page_index),
      `Correction locale appliquée à ${overlayId}. Vérifiez cette preview et recommencez paper_update_overlay si nécessaire.`,
      { overlay_id: overlayId, updated_overlay: registry[index], overlay_registry: registry });
    return { ok: true, job_id: jobId, overlay_id: overlayId, updated_overlay: registry[index], preview_pending: true };
  }

  if (name === "paper_delete_overlay") {
    const jobId = String(args.job_id ?? "").trim();
    const overlayId = String(args.overlay_id ?? "").trim();
    const job = await getJobOwned(sessionId, jobId);
    const registry = registryFromDocument(safeObject(job.document));
    const before = registry.length;
    const next = registry.filter((p) => p.overlay_id !== overlayId);
    if (next.length === before) throw new Error("overlay_id introuvable");
    await submitRegistryCommand(sessionId, job, next, `Suppression locale ${overlayId}`, [overlayId]);
    return { ok: true, job_id: jobId, deleted_overlay_id: overlayId, remaining_overlays: next.length };
  }

  if (name === "paper_list_overlays") {
    const job = await getJobOwned(sessionId, String(args.job_id ?? ""));
    return { job_id: job.id, overlays: registryFromDocument(safeObject(job.document)) };
  }

  if (name === "paper_validate_layout") {
    const job = await getJobOwned(sessionId, String(args.job_id ?? ""));
    const document = safeObject(job.document);
    return { job_id: job.id, ...validateLayoutServer(document, registryFromDocument(document)) };
  }

  if (name === "paper_clear_overlays") {
    const jobId = String(args.job_id ?? "").trim();
    const job = await getJobOwned(sessionId, jobId);
    const pageIndex = Number(args.page_index ?? -1);
    let registry = registryFromDocument(safeObject(job.document));
    if (Number.isInteger(pageIndex) && pageIndex >= 0) registry = registry.filter((p) => Number(p.page_index) !== pageIndex);
    else registry = [];
    await submitRegistryCommand(sessionId, job, registry,
      Number.isInteger(pageIndex) && pageIndex >= 0 ? `Effacement page ${pageIndex}` : "Effacement document", []);
    return { ok: true, job_id: jobId, remaining_overlays: registry.length };
  }

  if (name === "paper_control_application") {
    const jobId = String(args.job_id ?? "").trim();
    const action = String(args.action ?? "").trim();
    const job = await getJobOwned(sessionId, jobId);
    const document = safeObject(job.document);
    let registry = registryFromDocument(document);
    const incoming = Array.isArray(args.placements) ? validatePlacements(args.placements) : [];
    const targetPage = Number(args.page_index ?? -1);

    if (action === "replace_document") registry = incoming;
    else if (action === "append") registry = [...registry, ...incoming];
    else if (action === "clear_document") registry = [];
    else if (action === "replace_page") {
      if (!Number.isInteger(targetPage) || targetPage < 0) throw new Error("page_index requis");
      registry = registry.filter((p) => p.page_index !== targetPage).concat(incoming);
    } else if (action === "clear_page") {
      if (!Number.isInteger(targetPage) || targetPage < 0) throw new Error("page_index requis");
      registry = registry.filter((p) => p.page_index !== targetPage);
    } else if (action === "update_profile") {
      const merged = { ...(job.profile ?? {}), ...safeObject(args.profile_updates) };
      const { error } = await supabase.from("remplissage_mcp_jobs").update({ profile: merged, updated_at: new Date().toISOString() })
        .eq("id", jobId).eq("session_id", sessionId);
      if (error) throw error;
      return { ok: true, job_id: jobId, profile: merged };
    } else if (action === "set_editor_state") {
      return { ok: true, job_id: jobId, editor_updates: safeObject(args.editor_updates) };
    } else throw new Error("Commande d'application invalide");

    await submitRegistryCommand(sessionId, job, registry,
      String(args.notes ?? `Commande ${action}`), incoming.map((p) => String(p.overlay_id)));
    return { ok: true, job_id: jobId, action, overlay_registry: registry };
  }

  if (name === "paper_get_job_status") {
    const job = await getJobOwned(sessionId, String(args.job_id ?? ""));
    const pages = Array.isArray(job.preview_pages) ? job.preview_pages.map(Number) : [];
    const filledDocument = await filledDocumentMeta(job);
    if (pages.length) {
      return await previewImageToolResult(sessionId, job.id, pages[0],
        `Prévisualisation réelle disponible pour la page ${pages[0] + 1}. Vérifiez-la visuellement. Si les outils fins ne sont pas visibles, renvoyez tous les placements corrigés avec paper_submit_fill_plan.`,
        {
          status: job.status,
          review_required: true,
          legacy_correction_tool: "paper_submit_fill_plan",
          preview_pages: pages,
          preview_updated_at: job.preview_updated_at,
          overlay_registry: registryFromDocument(safeObject(job.document)),
          filled_document: filledDocument,
        });
    }
    return {
      job_id: job.id,
      status: job.status,
      error_message: job.error_message,
      preview_pages: pages,
      filled_document: filledDocument,
      updated_at: job.updated_at,
    };
  }

  if (name === "paper_get_filled_document") {
    const job = await getJobOwned(sessionId, String(args.job_id ?? ""));
    const result = await filledDocumentMeta(job);
    return { ok: result.filled_pdf_available, job_id: job.id, status: job.status, ...result };
  }

  if (name === "paper_delete_document") {
    const job = await getJobOwned(sessionId, String(args.job_id ?? ""));
    await removeJobStorage(sessionId, job.id, job.original_pdf_path, job.filled_pdf_path);
    const { error } = await supabase.from("remplissage_mcp_jobs").delete().eq("id", job.id).eq("session_id", sessionId);
    if (error) throw error;
    return { ok: true, job_id: job.id, deleted: true };
  }

  if (name === "paper_clear_server_documents") {
    const { data, error } = await supabase.from("remplissage_mcp_jobs")
      .select("id,original_pdf_path,filled_pdf_path").eq("session_id", sessionId);
    if (error) throw error;
    for (const row of data ?? []) await removeJobStorage(sessionId, row.id, row.original_pdf_path, row.filled_pdf_path);
    const { error: del } = await supabase.from("remplissage_mcp_jobs").delete().eq("session_id", sessionId);
    if (del) throw del;
    return { ok: true, deleted_documents: (data ?? []).length };
  }

  if (name === "paper_import_pdf" || name === "paper_prepare_chat_upload") {
    const filename = String(args.filename ?? "").trim().slice(0, 180);
    if (!filename.toLowerCase().endsWith(".pdf")) throw new Error("Le document doit être un PDF");
    const uploadToken = randomToken();
    const uploadTokenHash = await sha256Hex(uploadToken);
    const expires = new Date(Date.now() + 30 * 60 * 1000).toISOString();
    const document = { name: filename, page_count: null, current_overlays: [], overlay_registry: [], origin: "chatgpt", label: String(args.label ?? "").slice(0, 300) };
    const { data, error } = await supabase.from("remplissage_mcp_jobs").insert({
      session_id: sessionId, status: "processing", source: "chatgpt", is_active: true,
      document, profile: {}, field_hints: [], upload_token_hash: uploadTokenHash,
      upload_token_expires_at: expires, app_last_seen_at: new Date().toISOString(),
    }).select("id,status,created_at,expires_at").single();
    if (error) throw error;

    if (name === "paper_import_pdf" && String(args.source_url ?? "").trim()) {
      try {
        const r = await fetch(String(args.source_url).trim(), { redirect: "follow" });
        if (r.ok) {
          const bytes = new Uint8Array(await r.arrayBuffer());
          if (bytes.length <= MAX_PDF_BYTES && bytes[0] === 0x25 && bytes[1] === 0x50 && bytes[2] === 0x44 && bytes[3] === 0x46) {
            const path = `${sessionId}/${data.id}/original.pdf`;
            const { error: up } = await supabase.storage.from(PDF_BUCKET).upload(path, bytes, { contentType: "application/pdf", upsert: true });
            if (up) throw up;
            await supabase.from("remplissage_mcp_jobs").update({
              status: "pending", original_pdf_path: path, file_uploaded_at: new Date().toISOString(),
              upload_token_hash: null, upload_token_expires_at: null, updated_at: new Date().toISOString(),
            }).eq("id", data.id).eq("session_id", sessionId);
            return { ok: true, job_id: data.id, status: "pending", imported_directly: true, filename };
          }
        }
      } catch {
        // fallback vers upload manuel
      }
    }

    const uploadUrl = `${SUPABASE_URL}/functions/v1/${FUNCTION_SLUG}?chat_upload=${encodeURIComponent(uploadToken)}`;
    return { ok: true, job_id: data.id, status: "processing", filename, upload_url: uploadUrl, upload_expires_at: expires };
  }

  throw new Error("Outil inconnu");
}

async function handleAppApi(req: Request, body: Record<string, any>) {
  const action = String(body.action ?? "");
  const session = await getSession(req);
  if (!session) return jsonResponse({ ok: false, error: "Session invalide ou expirée" }, 401);

  if (action === "create_job") {
    const document = safeObject(body.document);
    document.overlay_registry = Array.isArray(document.overlay_registry) ? document.overlay_registry : [];
    const profile = safeObject(body.profile);
    const fieldHints = Array.isArray(body.field_hints) ? body.field_hints : [];
    await supabase.from("remplissage_mcp_jobs").update({
      status: "cancelled", error_message: "Remplacé par un document plus récent", updated_at: new Date().toISOString(),
    }).eq("session_id", session.id).neq("status", "cancelled");
    const { data, error } = await supabase.from("remplissage_mcp_jobs").insert({
      session_id: session.id, status: "pending", source: "android", is_active: true,
      document, profile, field_hints: fieldHints, app_last_seen_at: new Date().toISOString(),
    }).select("id,status,created_at,expires_at").single();
    if (error) throw error;
    return jsonResponse({ ok: true, job: data }, 201);
  }

  if (action === "update_job_context") {
    const jobId = String(body.job_id ?? "").trim();
    const existing = await getJobOwned(session.id, jobId);
    const incomingDoc = safeObject(body.document);
    const mergedDocument = { ...safeObject(existing.document), ...incomingDoc };
    if (!Array.isArray(mergedDocument.overlay_registry)) mergedDocument.overlay_registry = registryFromDocument(mergedDocument);
    const keepReady = existing.status === "ready" && existing.fill_plan != null;
    const now = new Date().toISOString();
    const { error } = await supabase.from("remplissage_mcp_jobs").update({
      document: mergedDocument,
      profile: safeObject(body.profile),
      field_hints: Array.isArray(body.field_hints) ? body.field_hints : [],
      status: keepReady ? "ready" : "pending",
      is_active: true, app_last_seen_at: now, updated_at: now,
    }).eq("id", jobId).eq("session_id", session.id);
    if (error) throw error;
    return jsonResponse({ ok: true, job_id: jobId, status: keepReady ? "ready" : "pending" });
  }

  if (action === "get_inbox") {
    const { data, error } = await supabase.from("remplissage_mcp_jobs")
      .select("id,status,document,original_pdf_path,created_at")
      .eq("session_id", session.id).eq("source", "chatgpt").neq("status", "cancelled")
      .not("original_pdf_path", "is", null).order("created_at", { ascending: false }).limit(1);
    if (error) throw error;
    const row = data?.[0];
    if (!row) return jsonResponse({ ok: true, document: null });
    return jsonResponse({ ok: true, document: {
      job_id: row.id, status: row.status, name: row.document?.name ?? "document.pdf",
      download_url: await signedPdfUrl(String(row.original_pdf_path), 900), created_at: row.created_at,
    }});
  }

  if (action === "deactivate_job") {
    const jobId = String(body.job_id ?? "").trim();
    const { error } = await supabase.from("remplissage_mcp_jobs")
      .update({ is_active: false, updated_at: new Date().toISOString() })
      .eq("id", jobId).eq("session_id", session.id);
    if (error) throw error;
    return jsonResponse({ ok: true, job_id: jobId, is_active: false });
  }

  if (action === "bridge_status") {
    const jobId = String(body.job_id ?? "").trim();
    const { data: sessionRow } = await supabase.from("remplissage_mcp_sessions")
      .select("chatgpt_last_seen_at,last_seen_at").eq("id", session.id).maybeSingle();
    let job: any = null;
    if (jobId) {
      const { data } = await supabase.from("remplissage_mcp_jobs")
        .select("id,status,is_active,document,app_last_seen_at,updated_at")
        .eq("id", jobId).eq("session_id", session.id).maybeSingle();
      job = data;
    }
    const seen = sessionRow?.chatgpt_last_seen_at ? Date.parse(sessionRow.chatgpt_last_seen_at) : NaN;
    return jsonResponse({
      ok: true,
      server_time: new Date().toISOString(),
      chatgpt_connected: Number.isFinite(seen) && Date.now() - seen <= 90_000,
      chatgpt_last_seen_at: sessionRow?.chatgpt_last_seen_at ?? null,
      chatgpt_last_seen_ms: Number.isFinite(seen) ? seen : 0,
      job,
    });
  }

  if (action === "wait_job" || action === "get_job") {
    const jobId = String(body.job_id ?? "").trim();
    const started = Date.now();
    const maxWait = action === "wait_job" ? 8000 : 0;
    while (true) {
      const now = new Date().toISOString();
      await supabase.from("remplissage_mcp_jobs").update({ app_last_seen_at: now, is_active: true })
        .eq("id", jobId).eq("session_id", session.id);
      const { data, error } = await supabase.from("remplissage_mcp_jobs")
        .select("id,status,fill_plan,error_message,updated_at,expires_at,app_last_seen_at")
        .eq("id", jobId).eq("session_id", session.id).maybeSingle();
      if (error) throw error;
      if (!data) return jsonResponse({ ok: false, error: "Document introuvable" }, 404);
      if (action === "get_job" || data.status === "ready" || ["cancelled", "error"].includes(data.status)) {
        return jsonResponse({ ok: true, heartbeat: false, job: data, server_time: now });
      }
      if (Date.now() - started >= maxWait) {
        return jsonResponse({ ok: true, heartbeat: true, job: { id: jobId, status: "pending", fill_plan: null, error_message: "", app_last_seen_at: now }, server_time: now });
      }
      await new Promise((resolve) => setTimeout(resolve, 750));
    }
  }

  if (action === "ack_applied") {
    const jobId = String(body.job_id ?? "").trim();
    const commandId = String(body.command_id ?? "").trim();
    const job = await getJobOwned(session.id, jobId);
    const expected = String(job.fill_plan?.command_id ?? "");
    if (expected && commandId && expected !== commandId) {
      return jsonResponse({ ok: false, error: "Commande MCP remplacée par une commande plus récente" }, 409);
    }
    const document = safeObject(job.document);
    const registry = registryFromDocument(document);
    const current = Array.isArray(body.current_overlays) ? body.current_overlays : [];
    const mergedCurrent = current.map((p: any, i: number) => {
      const r = registry[i];
      return { ...p, overlay_id: r?.overlay_id ?? p?.overlay_id ?? `overlay_${i + 1}`,
        data_state: r?.data_state ?? p?.data_state ?? "known" };
    });
    document.current_overlays = mergedCurrent;
    document.overlay_registry = registry;
    document.last_applied_command_id = commandId;
    document.last_applied_at = new Date().toISOString();
    const now = new Date().toISOString();
    const { error } = await supabase.from("remplissage_mcp_jobs").update({
      status: "pending", document, profile: safeObject(body.profile), fill_plan: null,
      error_message: null, updated_at: now, app_last_seen_at: now, is_active: true,
    }).eq("id", jobId).eq("session_id", session.id);
    if (error) throw error;
    return jsonResponse({ ok: true, job_id: jobId, status: "pending", command_id: commandId, current_overlays: mergedCurrent.length });
  }

  if (action === "list_jobs") {
    const { data, error } = await supabase.from("remplissage_mcp_jobs")
      .select("id,status,document,created_at,updated_at,expires_at").eq("session_id", session.id)
      .order("created_at", { ascending: false }).limit(50);
    if (error) throw error;
    return jsonResponse({ ok: true, jobs: data ?? [] });
  }

  if (action === "cancel_job") {
    const jobId = String(body.job_id ?? "");
    const { error } = await supabase.from("remplissage_mcp_jobs")
      .update({ status: "cancelled", updated_at: new Date().toISOString() })
      .eq("id", jobId).eq("session_id", session.id);
    if (error) throw error;
    return jsonResponse({ ok: true, job_id: jobId, status: "cancelled" });
  }

  return jsonResponse({ ok: false, error: "Action inconnue" }, 400);
}

async function handleMcp(req: Request, body: Record<string, any>) {
  const id = body.id ?? null;
  const method = String(body.method ?? "");
  const session = await getSession(req);
  if (!session) return rpcError(id, -32001, "Session Remplissage Papier invalide ou expirée");
  void supabase.from("remplissage_mcp_sessions")
    .update({ chatgpt_last_seen_at: new Date().toISOString() }).eq("id", session.id);

  if (method === "initialize") {
    return rpcResult(id, {
      protocolVersion: PROTOCOL_VERSION,
      capabilities: { tools: { listChanged: true } },
      serverInfo: { name: "Remplissage Papier MCP", version: SERVER_VERSION },
      instructions: "Flux recommandé: paper_open_active_document → inspection visuelle → paper_submit_fill_plan → preview → paper_update_overlay répété jusqu'à validation → paper_get_filled_document. Connecteur ancien: paper_get_document_context renvoie aussi l'image réelle et paper_submit_fill_plan permet de remplacer le plan complet après chaque preview. Les coordonnées ChatGPT sont autoritaires, sans snap. y texte = baseline; checkbox = centre.",
    }, session.id);
  }
  if (method === "ping") return rpcResult(id, {}, session.id);
  if (method === "tools/list") return rpcResult(id, { tools }, session.id);
  if (method === "tools/call") {
    const params = safeObject(body.params);
    try {
      const result = await handleToolCall(session.id, String(params.name ?? ""), safeObject(params.arguments));
      if (result && typeof result === "object" && (result as any).__mcp_image_result) {
        const r = result as any;
        return rpcResult(id, { content: r.content, structuredContent: r.structuredContent, isError: false }, session.id);
      }
      return rpcResult(id, toolResult(result), session.id);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Erreur outil";
      return rpcResult(id, toolResult({ error: message }, true), session.id);
    }
  }
  if (method === "notifications/initialized" || method.startsWith("notifications/")) {
    return new Response(null, { status: 202, headers: corsHeaders });
  }
  return rpcError(id, -32601, `Méthode MCP inconnue: ${method}`, undefined, session.id);
}

async function handleChatUploadPage(uploadToken: string) {
  const tokenHash = await sha256Hex(uploadToken);
  const { data, error } = await supabase.from("remplissage_mcp_jobs")
    .select("id,document,upload_token_expires_at,file_uploaded_at")
    .eq("upload_token_hash", tokenHash).maybeSingle();
  if (error || !data) return htmlResponse("<h2>Lien invalide</h2>", 404);
  if (data.file_uploaded_at) return htmlResponse("<h2>PDF déjà envoyé</h2><p>Vous pouvez revenir dans ChatGPT.</p>");
  if (!data.upload_token_expires_at || new Date(data.upload_token_expires_at).getTime() <= Date.now()) {
    return htmlResponse("<h2>Lien expiré</h2>", 410);
  }
  const name = String(data.document?.name ?? "document.pdf").replace(/[<>&\"']/g, "");
  return htmlResponse(`<!doctype html><html lang="fr"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Remplissage Papier</title><style>body{font-family:system-ui;background:#090D0F;color:#FFF1D7;padding:24px}main{max-width:520px;margin:auto}.card{background:#151A1D;border:1px solid #333;border-radius:18px;padding:22px}input,button{width:100%;box-sizing:border-box;margin-top:14px;padding:15px;border-radius:12px}button{border:0;background:#FF5A00;color:#fff;font-weight:800}</style></head><body><main><div class="card"><h2>Envoyer vers Remplissage Papier</h2><p>${name}</p><input id="f" type="file" accept="application/pdf"><button id="b">ENVOYER LE PDF</button><div id="s"></div></div></main><script>const f=document.getElementById('f'),b=document.getElementById('b'),s=document.getElementById('s');b.onclick=async()=>{const x=f.files&&f.files[0];if(!x){s.textContent='Choisissez un PDF.';return;}b.disabled=true;s.textContent='Envoi…';try{const r=await fetch(location.href,{method:'POST',headers:{'Content-Type':'application/pdf'},body:x});const j=await r.json();if(!r.ok||!j.ok)throw new Error(j.error||'Échec');s.textContent='PDF envoyé. Ouvrez l’application.';}catch(e){s.textContent='Erreur: '+e.message;b.disabled=false;}};</script></body></html>`);
}

async function handleChatUploadBytes(uploadToken: string, req: Request) {
  const tokenHash = await sha256Hex(uploadToken);
  const { data, error } = await supabase.from("remplissage_mcp_jobs")
    .select("id,session_id,upload_token_expires_at").eq("upload_token_hash", tokenHash).maybeSingle();
  if (error || !data) return jsonResponse({ ok: false, error: "Lien invalide" }, 404);
  if (!data.upload_token_expires_at || new Date(data.upload_token_expires_at).getTime() <= Date.now()) {
    return jsonResponse({ ok: false, error: "Lien expiré" }, 410);
  }
  const bytes = new Uint8Array(await req.arrayBuffer());
  if (bytes.length < 5 || bytes.length > MAX_PDF_BYTES || bytes[0] !== 0x25 || bytes[1] !== 0x50 || bytes[2] !== 0x44 || bytes[3] !== 0x46) {
    return jsonResponse({ ok: false, error: "PDF invalide" }, 400);
  }
  const path = `${data.session_id}/${data.id}/original.pdf`;
  const { error: up } = await supabase.storage.from(PDF_BUCKET).upload(path, bytes, { contentType: "application/pdf", upsert: true });
  if (up) throw up;
  const now = new Date().toISOString();
  const { error: update } = await supabase.from("remplissage_mcp_jobs").update({
    status: "pending", original_pdf_path: path, file_uploaded_at: now,
    upload_token_hash: null, upload_token_expires_at: null, updated_at: now, is_active: true,
  }).eq("id", data.id);
  if (update) throw update;
  return jsonResponse({ ok: true, job_id: data.id, status: "pending", bytes: bytes.length });
}

async function handleBinaryUpload(req: Request, session: { id: string }, url: URL, kind: "page" | "preview" | "pdf") {
  const jobId = String(url.searchParams.get("job_id") ?? "").trim();
  const job = await getJobOwned(session.id, jobId);
  const bytes = new Uint8Array(await req.arrayBuffer());
  const now = new Date().toISOString();

  if (kind === "pdf") {
    if (bytes.length < 5 || bytes.length > MAX_PDF_BYTES || bytes[0] !== 0x25 || bytes[1] !== 0x50 || bytes[2] !== 0x44 || bytes[3] !== 0x46) {
      return jsonResponse({ ok: false, error: "PDF final invalide" }, 400);
    }
    const path = `${session.id}/${jobId}/filled.pdf`;
    const { error } = await supabase.storage.from(PDF_BUCKET).upload(path, bytes, { contentType: "application/pdf", upsert: true });
    if (error) throw error;
    await supabase.from("remplissage_mcp_jobs").update({ filled_pdf_path: path, filled_pdf_uploaded_at: now, updated_at: now, app_last_seen_at: now })
      .eq("id", jobId).eq("session_id", session.id);
    return jsonResponse({ ok: true, job_id: jobId, filled_pdf_available: true, bytes: bytes.length });
  }

  const pageIndex = Number(url.searchParams.get("page_index") ?? "-1");
  if (!Number.isInteger(pageIndex) || pageIndex < 0 || pageIndex > 999) return jsonResponse({ ok: false, error: "page_index invalide" }, 400);
  if (bytes.length < 100 || bytes.length > MAX_PAGE_IMAGE_BYTES || bytes[0] !== 0xFF || bytes[1] !== 0xD8) {
    return jsonResponse({ ok: false, error: "JPEG invalide" }, 400);
  }
  const filename = kind === "page"
    ? `page-${String(pageIndex).padStart(4, "0")}.jpg`
    : `preview-${String(pageIndex).padStart(4, "0")}.jpg`;
  const path = `${session.id}/${jobId}/${filename}`;
  const { error } = await supabase.storage.from(PAGE_BUCKET).upload(path, bytes, { contentType: "image/jpeg", upsert: true });
  if (error) throw error;

  if (kind === "page") {
    const document = safeObject(job.document);
    const count = Number(document.page_count ?? document.pageCount ?? 0);
    document.page_images_ready = true;
    if (!Array.isArray(document.pages)) document.pages = Array.from({ length: Math.max(count, pageIndex + 1) }, () => ({}));
    await supabase.from("remplissage_mcp_jobs").update({ document, app_last_seen_at: now, is_active: true, updated_at: now })
      .eq("id", jobId).eq("session_id", session.id);
  } else {
    const pages = Array.isArray(job.preview_pages) ? job.preview_pages.map(Number) : [];
    if (!pages.includes(pageIndex)) pages.push(pageIndex);
    pages.sort((a, b) => a - b);
    await supabase.from("remplissage_mcp_jobs").update({ preview_pages: pages, preview_updated_at: now, app_last_seen_at: now, is_active: true, updated_at: now })
      .eq("id", jobId).eq("session_id", session.id);
  }
  return jsonResponse({ ok: true, job_id: jobId, page_index: pageIndex, bytes: bytes.length });
}

Deno.serve(async (req: Request) => {
  try {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
    const url = new URL(req.url);
    const chatUpload = String(url.searchParams.get("chat_upload") ?? "").trim();
    if (chatUpload) {
      if (req.method === "GET") return await handleChatUploadPage(chatUpload);
      if (req.method === "POST") return await handleChatUploadBytes(chatUpload, req);
      return jsonResponse({ ok: false, error: "Méthode non supportée" }, 405);
    }

    const appAction = url.searchParams.get("app_action");
    if (req.method === "POST" && appAction) {
      const session = await getSession(req);
      if (!session) return jsonResponse({ ok: false, error: "Session invalide" }, 401);
      if (appAction === "upload_page") return await handleBinaryUpload(req, session, url, "page");
      if (appAction === "upload_preview_page") return await handleBinaryUpload(req, session, url, "preview");
      if (appAction === "upload_filled_pdf") return await handleBinaryUpload(req, session, url, "pdf");
    }

    if (req.method === "DELETE") return new Response(null, { status: 204, headers: corsHeaders });
    if (req.method === "GET") {
      const session = await getSession(req);
      return jsonResponse({ ok: true, service: "Remplissage Papier MCP", version: SERVER_VERSION,
        protocol: PROTOCOL_VERSION, tool_count: tools.length, session_valid: Boolean(session), endpoint: url.origin + url.pathname });
    }
    if (req.method !== "POST") return jsonResponse({ ok: false, error: "Méthode HTTP non supportée" }, 405);
    const body = safeObject(await req.json());
    if (body.jsonrpc === "2.0") return await handleMcp(req, body);
    return await handleAppApi(req, body);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Erreur interne";
    return jsonResponse({ ok: false, error: message }, 500);
  }
});
