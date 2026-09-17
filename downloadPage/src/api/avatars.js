import { HttpError, CORS, json, readLimited } from "./http.js";
import { wikiUser } from "./auth.js";
import { writeGate, requireClaim, releaseGate } from "./writes.js";

const PREFIX = "/api/user/avatar/";
const MAX_IMAGE = 2 * 1024 * 1024;

export function avatarPath(value, origin) {
  if (value == null || value === "") return null;
  if (typeof value !== "string") throw new HttpError(400, "头像地址无效");
  let url;
  try { url = new URL(value, origin); } catch { throw new HttpError(400, "头像地址无效"); }
  if (url.origin !== origin || !url.pathname.startsWith(PREFIX) || url.search || url.hash) throw new HttpError(400, "头像地址不受支持");
  // Canonicalize percent-encoded aliases before saving or comparing references.
  let key;
  try { key = decodeURIComponent(url.pathname.slice(PREFIX.length)); } catch { throw new HttpError(400, "头像地址编码无效"); }
  if (!/^avatars\/[^/\\?#%]+\.(png|jpg|webp)$/.test(key)) throw new HttpError(400, "头像地址无效");
  return PREFIX + key;
}

// Legacy keys encode BID followed by EXACTLY 16 hash digits. Do not use a loose prefix check.
function legacyOwner(key, bid) {
  const match = /^avatars\/(.+)_([a-f0-9]{16})\.(png|jpg|webp)$/.exec(key);
  return match?.[1] === bid;
}

export async function validateAvatar(env, path, bid) {
  if (!path) return;
  if (!env.USER_ASSETS) throw new HttpError(503, "头像存储未配置");
  const key = decodeURIComponent(path.slice(PREFIX.length));
  let asset = await env.DB.prepare("SELECT owner_bid,state FROM avatar_assets WHERE object_key=?").bind(key).first();
  if (!asset) {
    if (!legacyOwner(key, bid)) throw new HttpError(403, "只能使用自己上传的头像");
    if (!(await env.USER_ASSETS.head(key))) throw new HttpError(400, "头像文件不存在，请重新上传");
    await env.DB.prepare("INSERT INTO avatar_assets(object_key,owner_bid,created_at) VALUES(?,?,unixepoch()) ON CONFLICT DO NOTHING").bind(key, bid).run();
    asset = await env.DB.prepare("SELECT owner_bid,state FROM avatar_assets WHERE object_key=?").bind(key).first();
  }
  if (asset?.owner_bid !== bid) throw new HttpError(403, "只能使用自己上传的头像");
  if (asset.state !== "active" || !(await env.USER_ASSETS.head(key))) throw new HttpError(409, "头像已过期，请重新上传");
}

export function avatarKey(path) { return path ? decodeURIComponent(path.slice(PREFIX.length)) : null; }

// Mark unreachable objects before deleting. Profile writes only accept active assets,
// so concurrent save/cleanup cannot resurrect a reference to an object being deleted.
export async function cleanupAvatars(env, origin, limit = 20) {
  if (!env.USER_ASSETS) throw new HttpError(503, "头像存储未配置");
  // Discover old untracked uploads, one bounded R2 page per maintenance call.
  const savedCursor = await env.DB.prepare("SELECT value FROM maintenance_state WHERE key='avatar_scan'").first();
  const page = await env.USER_ASSETS.list({ prefix: "avatars/", limit: 100, ...(savedCursor?.value ? { cursor: savedCursor.value } : {}) });
  for (const object of page.objects) {
    const match = /^avatars\/(.+)_([a-f0-9]{16})\.(png|jpg|webp)$/.exec(object.key);
    if (match) await env.DB.prepare("INSERT INTO avatar_assets(object_key,owner_bid,created_at) VALUES(?,?,?) ON CONFLICT DO NOTHING")
      .bind(object.key, match[1], Math.floor(object.uploaded.getTime() / 1000)).run();
  }
  await env.DB.prepare("INSERT INTO maintenance_state(key,value) VALUES('avatar_scan',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")
    .bind(page.truncated ? page.cursor : null).run();
  const { results } = await env.DB.prepare(`SELECT object_key FROM avatar_assets
    WHERE (state='deleting' OR created_at < unixepoch()-86400)
    AND NOT EXISTS(SELECT 1 FROM user_profiles WHERE avatar_url='/api/user/avatar/' || object_key OR avatar_url=? || '/api/user/avatar/' || object_key)
    ORDER BY created_at LIMIT ?`).bind(origin, limit).all();
  for (const { object_key: key } of results) {
    const path = PREFIX + key;
    await env.DB.prepare(`UPDATE avatar_assets SET state='deleting' WHERE object_key=?
      AND NOT EXISTS(SELECT 1 FROM user_profiles WHERE avatar_url=? OR avatar_url=?)`)
      .bind(key, path, origin + path).run();
    const row = await env.DB.prepare("SELECT state FROM avatar_assets WHERE object_key=?").bind(key).first();
    if (row?.state !== "deleting") continue;
    await env.USER_ASSETS.delete(key);
    await env.DB.prepare("DELETE FROM avatar_assets WHERE object_key=? AND state='deleting'").bind(key).run();
  }
}

export async function retireAvatar(env, path, bid, origin) {
  if (!path || !env.USER_ASSETS) return;
  const key = avatarKey(path);
  // Never delete an unowned object, even if a historic profile referenced another user.
  if (legacyOwner(key, bid)) {
    await env.DB.prepare("INSERT INTO avatar_assets(object_key,owner_bid,created_at) VALUES(?,?,0) ON CONFLICT DO NOTHING").bind(key, bid).run();
  }
  await env.DB.prepare(`UPDATE avatar_assets SET state='deleting' WHERE object_key=? AND owner_bid=?
    AND NOT EXISTS(SELECT 1 FROM user_profiles WHERE avatar_url=? OR avatar_url=?)`)
    .bind(key, bid, path, origin + path).run();
  // Deletion is retriable: retain the tombstone if R2 fails. Do not scan the bucket
  // on every profile save; the maintenance endpoint handles aged orphans in batches.
  const row = await env.DB.prepare("SELECT state FROM avatar_assets WHERE object_key=? AND owner_bid=?").bind(key, bid).first();
  if (row?.state === "deleting") {
    await env.USER_ASSETS.delete(key);
    await env.DB.prepare("DELETE FROM avatar_assets WHERE object_key=? AND state='deleting'").bind(key).run();
  }
}

function sniff(bytes) {
  if (bytes.length >= 8 && [137,80,78,71,13,10,26,10].every((v,i) => bytes[i] === v)) return "image/png";
  if (bytes.length >= 3 && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) return "image/jpeg";
  if (bytes.length >= 12 && String.fromCharCode(...bytes.slice(0,4)) === "RIFF" && String.fromCharCode(...bytes.slice(8,12)) === "WEBP") return "image/webp";
  return null;
}

export async function uploadAvatar(request, env) {
  const user = await wikiUser(request, true);
  if (!env.USER_ASSETS) throw new HttpError(503, "头像存储未配置");
  const type = request.headers.get("Content-Type") || "";
  const raw = await readLimited(request, MAX_IMAGE + 64 * 1024);
  let bytes = raw;
  let mime = type.split(";")[0].trim().toLowerCase();
  if (mime === "multipart/form-data") {
    let form;
    try { form = await new Response(raw, { headers: { "Content-Type": type } }).formData(); }
    catch { throw new HttpError(400, "上传表单无效"); }
    const file = form.get("file");
    if (!file || typeof file === "string") throw new HttpError(400, "缺少头像文件");
    bytes = new Uint8Array(await file.arrayBuffer());
    mime = file.type.toLowerCase();
  }
  if (!bytes.length) throw new HttpError(400, "图片文件为空");
  if (bytes.length > MAX_IMAGE) throw new HttpError(413, "图片大小不能超过 2MB");
  const detected = sniff(bytes);
  if (!detected || detected !== mime) throw new HttpError(400, "图片格式无效或与声明不符");
  const ext = { "image/png": "png", "image/jpeg": "jpg", "image/webp": "webp" }[mime];
  const key = `avatars/${crypto.randomUUID()}.${ext}`;
  // R2 cannot join a D1 transaction. Hold a bounded lease, compensate failures, and
  // record the object BEFORE put so a process interruption remains collectible.
  const gate = writeGate(env.DB, `uav:${user.bid}`, 30);
  requireClaim(await gate.claim.run(), 30);
  try {
    await env.DB.prepare("INSERT INTO avatar_assets(object_key,owner_bid,created_at) VALUES(?,?,?)").bind(key, user.bid, gate.now).run();
    await env.USER_ASSETS.put(key, bytes, { httpMetadata: { contentType: mime, cacheControl: "public, max-age=86400" } });
  } catch (error) {
    try {
      await env.DB.prepare("UPDATE avatar_assets SET state='deleting' WHERE object_key=?").bind(key).run();
    } finally {
      await releaseGate(env.DB, gate);
    }
    throw error;
  }
  return json({ avatarUrl: PREFIX + key, objectKey: key });
}

export async function serveAvatar(request, env, url) {
  if (!["GET", "HEAD"].includes(request.method)) throw new HttpError(405, "Method Not Allowed", { Allow: "GET, HEAD" });
  if (!env.USER_ASSETS) throw new HttpError(503, "头像存储未配置");
  const key = decodeURIComponent(url.pathname.slice(PREFIX.length));
  const object = request.method === "HEAD" ? await env.USER_ASSETS.head(key) : await env.USER_ASSETS.get(key);
  if (!object) throw new HttpError(404, "头像不存在");
  const headers = new Headers({ ...CORS, "Cache-Control": "public, max-age=86400", "X-Content-Type-Options": "nosniff" });
  object.writeHttpMetadata(headers);
  headers.set("ETag", object.httpEtag);
  headers.set("Content-Length", String(object.size));
  return new Response(request.method === "HEAD" ? null : object.body, { headers });
}
