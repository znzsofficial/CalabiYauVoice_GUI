import { HttpError, CORS } from "./http.js";

function parseRange(value, size) {
  const match = /^bytes=(\d*)-(\d*)$/.exec(value);
  if (!match || (!match[1] && !match[2])) return null; // Ignore unsupported/malformed ranges.
  let start, end;
  if (!match[1]) {
    const suffix = Number(match[2]);
    if (!Number.isSafeInteger(suffix) || suffix === 0 || size === 0) return false;
    start = Math.max(0, size - suffix); end = size - 1;
  } else {
    start = Number(match[1]);
    end = match[2] ? Number(match[2]) : size - 1;
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start >= size || start > end) return false;
    end = Math.min(end, size - 1);
  }
  return { offset: start, length: end - start + 1 };
}

export async function serveRelease(request, env, url) {
  const match = /^\/downloads\/(CalabiYauVoice-(?:latest|\d+\.\d+\.\d+)\.apk)$/.exec(url.pathname);
  if (!match) return null;
  if (!["GET", "HEAD"].includes(request.method)) throw new HttpError(405, "Method Not Allowed", { Allow: "GET, HEAD" });
  if (!env.RELEASES) throw new HttpError(503, "发布存储未配置");
  const key = `android/${match[1]}`;
  const head = await env.RELEASES.head(key);
  if (!head) throw new HttpError(404, "安装包不存在");
  const headers = new Headers({ ...CORS,
    "Content-Type": "application/vnd.android.package-archive",
    "Content-Disposition": `attachment; filename="${match[1]}"`,
    "Accept-Ranges": "bytes", ETag: head.httpEtag,
    "Last-Modified": head.uploaded.toUTCString(),
    "Cache-Control": match[1].endsWith("-latest.apk") ? "public, max-age=0, must-revalidate" : "public, max-age=31536000, immutable",
    "Content-Length": String(head.size),
  });
  if (request.headers.get("If-None-Match")?.split(/\s*,\s*/).some(tag => tag === "*" || tag.replace(/^W\//, "") === head.httpEtag)) {
    headers.delete("Content-Length"); return new Response(null, { status: 304, headers });
  }
  if (request.method === "HEAD") return new Response(null, { headers });
  const ifRange = request.headers.get("If-Range");
  const rangeAllowed = !ifRange || ifRange === head.httpEtag || (Number.isFinite(Date.parse(ifRange)) && Math.floor(head.uploaded.getTime() / 1000) <= Date.parse(ifRange) / 1000);
  const range = rangeAllowed && request.headers.has("Range") ? parseRange(request.headers.get("Range"), head.size) : null;
  if (range === false) {
    headers.set("Content-Range", `bytes */${head.size}`); headers.set("Content-Length", "0");
    return new Response(null, { status: 416, headers });
  }
  // Conditional read avoids returning headers for one version and bytes for another
  // if the mutable latest key is replaced between HEAD and GET.
  const object = await env.RELEASES.get(key, { onlyIf: { etagMatches: head.etag }, ...(range ? { range } : {}) });
  if (!object || !("body" in object)) throw new HttpError(503, "安装包更新中，请重试", { "Retry-After": "1" });
  if (range) {
    headers.set("Content-Range", `bytes ${range.offset}-${range.offset + range.length - 1}/${head.size}`);
    headers.set("Content-Length", String(range.length));
  }
  return new Response(object.body, { status: range ? 206 : 200, headers });
}
