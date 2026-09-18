export const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, HEAD, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, X-Wiki-Cookie, X-Admin-Password, Idempotency-Key, X-Guest-Id",
  "Access-Control-Expose-Headers": "Retry-After, Content-Range, Accept-Ranges, ETag",
};
export class HttpError extends Error {
  constructor(status, message, headers = {}, code = null) {
    super(message); this.status = status; this.headers = headers; this.code = code;
  }
}
export function json(value, status = 200, headers = {}) {
  return Response.json(value, { status, headers: { ...CORS, "Cache-Control": "no-store", ...headers } });
}
// Bound actual bytes too: Content-Length is not trustworthy or always present.
export async function readLimited(source, limit) {
  if (Number(source.headers.get("Content-Length")) > limit) throw new HttpError(413, "请求内容过大");
  const reader = source.body?.getReader();
  if (!reader) return new Uint8Array();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) { await reader.cancel(); throw new HttpError(413, "请求内容过大"); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return bytes;
}
export async function readJson(request) {
  const bytes = await readLimited(request, 16 * 1024);
  let body;
  try { body = JSON.parse(new TextDecoder().decode(bytes)); }
  catch { throw new HttpError(400, "JSON 格式无效"); }
  if (!body || Array.isArray(body) || typeof body !== "object") throw new HttpError(400, "请求体必须为对象");
  return body;
}
export function text(value, name, max, required = false) {
  if (value == null && !required) return null;
  if (typeof value !== "string") throw new HttpError(400, `${name} 必须为字符串`);
  const result = value.trim();
  if (result.length > max || (required && !result)) throw new HttpError(400, `${name} 长度无效`);
  return result || null;
}
export function integer(value, fallback, max = Number.MAX_SAFE_INTEGER) {
  if (value == null) return fallback;
  if (!/^\d+$/.test(value)) throw new HttpError(400, "整数参数无效");
  const result = Number(value);
  if (!Number.isSafeInteger(result) || result < 1 || result > max) throw new HttpError(400, "整数参数超出范围");
  return result;
}
export function requireDb(env) {
  if (!env.DB) throw new HttpError(503, "D1 database not configured");
  return env.DB;
}
export const escapeLike = (value) => `%${value.replace(/[\\%_]/g, "\\$&")}%`;
export function absoluteProfile(profile, origin) {
  return profile?.avatarUrl?.startsWith("/") ? { ...profile, avatarUrl: origin + profile.avatarUrl } : profile;
}
