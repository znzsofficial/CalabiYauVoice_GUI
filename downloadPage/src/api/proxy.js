import { CORS, HttpError, json, readLimited } from "./http.js";
import { WIKI_API, WIKI_HEADERS } from "./auth.js";
// Keep these upstreams synchronized with vite.config.ts.
const UPSTREAM = "https://klbq-prod-www.idreamsky.com";
const ALLOWED = new Set(["wiki.biligame.com", "patchwiki.biligame.com"]);

function allowed(url) { return url.protocol === "https:" && !url.username && !url.password && (!url.port || url.port === "443") && ALLOWED.has(url.hostname); }

export async function proxyApi(request, env, url) {
  if (url.pathname === "/api/github-stars" && request.method === "GET") {
    const headers = { Accept: "application/vnd.github+json", "User-Agent": "CalabiYauWiki/1.0" };
    if (env.GITHUB_TOKEN) headers.Authorization = `Bearer ${env.GITHUB_TOKEN}`;
    const response = await fetch("https://api.github.com/repos/znzsofficial/CalabiYauVoice_GUI", { headers, signal: AbortSignal.timeout(5000) });
    if (!response.ok) throw new HttpError(502, "GitHub 暂不可用");
    const data = JSON.parse(new TextDecoder().decode(await readLimited(response, 256 * 1024)));
    return json({ stars: data.stargazers_count ?? 0 }, 200, { "Cache-Control": "public, max-age=300" });
  }
  if (["/api/image-download", "/api/file-download"].includes(url.pathname) && request.method === "GET") {
    let target;
    try { target = new URL(url.searchParams.get("url")); } catch { throw new HttpError(400, "不支持的文件地址"); }
    if (!allowed(target)) throw new HttpError(400, "不支持的文件地址");
    for (let hop = 0; hop < 5; hop++) {
      const response = await fetch(target, { redirect: "manual", headers: { ...WIKI_HEADERS, Accept: "*/*" }, signal: AbortSignal.timeout(15000) });
      if ([301,302,303,307,308].includes(response.status)) {
        await response.body?.cancel();
        const location = response.headers.get("Location");
        if (!location) throw new HttpError(502, "文件重定向无效");
        target = new URL(location, target);
        if (!allowed(target)) throw new HttpError(502, "文件重定向目标不受支持");
        continue;
      }
      if (!response.ok) throw new HttpError(502, `文件请求失败（HTTP ${response.status}）`);
      const headers = new Headers(response.headers);
      headers.set("Access-Control-Allow-Origin", "*"); headers.set("Cache-Control", "public, max-age=86400");
      return new Response(response.body, { status: response.status, headers });
    }
    throw new HttpError(502, "文件重定向次数过多");
  }
  if (url.pathname === "/api/wiki" && request.method === "GET") {
    const target = new URL(WIKI_API); target.search = url.search; target.searchParams.delete("origin");
    const response = await fetch(target, { headers: WIKI_HEADERS, signal: AbortSignal.timeout(10000) });
    const bytes = await readLimited(response, 8 * 1024 * 1024);
    const html = response.headers.get("Content-Type")?.includes("text/html") || new TextDecoder().decode(bytes.slice(0,32)).trimStart().startsWith("<!");
    if (!response.ok || html) return json({ error: "Wiki 上游暂不可用或被风控拦截", upstreamStatus: response.status }, response.status === 429 ? 429 : 502);
    return new Response(bytes, { headers: { ...CORS, "Content-Type": response.headers.get("Content-Type") || "application/json", "Cache-Control": "public, max-age=30" } });
  }
  const balance = url.pathname === "/api/balance/settings" && request.method === "GET" ? "/api/pages/KLBQ_BALANCE/index"
    : url.pathname === "/api/balance/data" && request.method === "POST" ? "/api/common/ide" : null;
  if (balance) {
    // Only forward protocol headers, never Wiki/admin credentials.
    const headers = new Headers();
    for (const name of ["Content-Type", "Accept", "User-Agent", "Referer", "Origin"]) {
      if (request.headers.has(name)) headers.set(name, request.headers.get(name));
    }
    const response = await fetch(UPSTREAM + balance, { method: request.method, headers,
      body: request.method === "POST" ? await readLimited(request, 64 * 1024) : undefined, signal: AbortSignal.timeout(15000) });
    const out = new Headers(response.headers); out.set("Access-Control-Allow-Origin", "*");
    return new Response(response.body, { status: response.status, headers: out });
  }
  return null;
}
