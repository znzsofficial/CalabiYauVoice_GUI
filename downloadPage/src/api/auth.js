import { HttpError, readLimited } from "./http.js";
export const WIKI_API = "https://wiki.biligame.com/klbq/api.php";
export const WIKI_HEADERS = {
  Accept: "application/json",
  "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
  Referer: "https://wiki.biligame.com/klbq/", Origin: "https://wiki.biligame.com",
};
export async function wikiUser(request, required = false) {
  // Do not forward ambient site cookies to a third party.
  const cookie = request.headers.get("X-Wiki-Cookie");
  if (!cookie?.trim()) {
    if (required) throw new HttpError(401, "请先登录 Wiki");
    return null;
  }
  if (cookie.length > 8192) throw new HttpError(400, "登录凭据过长");
  let user;
  try {
    const response = await fetch(`${WIKI_API}?action=query&meta=userinfo&format=json`, {
      headers: { ...WIKI_HEADERS, Cookie: cookie }, redirect: "manual", signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) throw new Error(`upstream ${response.status}`);
    user = JSON.parse(new TextDecoder().decode(await readLimited(response, 64 * 1024)))?.query?.userinfo;
    if (!user || !Number.isSafeInteger(user.id)) throw new Error("invalid userinfo");
  } catch { throw new HttpError(503, "Wiki 身份验证暂不可用，请稍后重试"); }
  if (user.id === 0 || user.anon !== undefined) throw new HttpError(401, "Wiki 登录已失效，请重新登录");
  if (user.id < 1 || typeof user.name !== "string" || !user.name.trim()) throw new HttpError(503, "Wiki 身份数据无效");
  return { bid: user.name.trim(), wikiUserId: user.id };
}
function hex(buffer) { return Array.from(new Uint8Array(buffer), b => b.toString(16).padStart(2, "0")).join(""); }
export async function sha256(value) {
  return hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
}
export async function hmac(secret, value) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return hex(await crypto.subtle.sign("HMAC", key, encoder.encode(value)));
}
export async function guestIdentity(request, env) {
  if (!env.GUEST_SECRET || env.GUEST_SECRET.length < 32) throw new HttpError(503, "访客标识服务未配置");
  const ip = request.headers.get("CF-Connecting-IP");
  if (!ip) throw new HttpError(503, "无法识别请求来源");
  const guestId = request.headers.get("X-Guest-Id");
  if (guestId && !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(guestId)) throw new HttpError(400, "访客标识无效");
  const ipHash = await hmac(env.GUEST_SECRET, `rate:${ip}`);
  const identity = await hmac(env.GUEST_SECRET, guestId ? `device:${guestId}` : `ip:${ip}`);
  return { tag: identity.slice(0, 10).toUpperCase(), actor: `guest:${identity}`, throttleKey: `ip:${ipHash}` };
}
export async function requireAdmin(request, env) {
  const expected = env.ADMIN_PASSWORD?.trim();
  const provided = request.headers.get("X-Admin-Password");
  if (!expected || !provided) throw new HttpError(401, "管理密码错误或未配置");
  const encoder = new TextEncoder();
  const [a, b] = await Promise.all([expected, provided].map(v => crypto.subtle.digest("SHA-256", encoder.encode(v))));
  if (!crypto.subtle.timingSafeEqual(a, b)) throw new HttpError(401, "管理密码错误或未配置");
}
