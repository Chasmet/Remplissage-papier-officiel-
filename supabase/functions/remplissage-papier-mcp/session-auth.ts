// A session is a capability: never resolve a user from a public request without it.
export function sessionTokenFromRequest(req: Request): string {
  const url = new URL(req.url);
  const queryToken = url.searchParams.get("session")?.trim() ?? "";
  const headerToken = req.headers.get("x-remplissage-session")?.trim() ?? "";
  const authorization = req.headers.get("authorization")?.trim() ?? "";
  const bearer = /^Bearer\s+(\S+)$/i.exec(authorization)?.[1] ?? "";

  // Reject conflicting credentials instead of silently choosing another user's session.
  const credentials = [queryToken, headerToken, bearer].filter(Boolean);
  if (!credentials.length || (authorization && !bearer)) return "";
  if (credentials.some((credential) => credential !== credentials[0])) return "";
  const token = credentials[0];
  return token.length >= 32 && token.length <= 256 ? token : "";
}
