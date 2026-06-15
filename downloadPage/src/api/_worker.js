// These constants must stay in sync with downloadPage/vite.config.ts
const UPSTREAM = "https://klbq-prod-www.idreamsky.com";
const ALLOWED_IMAGE_HOSTS = new Set(["wiki.biligame.com", "patchwiki.biligame.com"]);

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // OPTIONS 预检
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type",
        },
      });
    }

    // GET /api/balance/settings → 平衡数据设置
    if (url.pathname === "/api/balance/settings") {
      return proxy(`${UPSTREAM}/api/pages/KLBQ_BALANCE/index`, request);
    }

    // POST /api/balance/data → 平衡数据查询
    if (url.pathname === "/api/balance/data") {
      return proxy(`${UPSTREAM}/api/common/ide`, request);
    }

    // GET /api/github-stars → GitHub star count
    if (url.pathname === "/api/github-stars") {
      const corsHeaders = {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, OPTIONS",
      };
      try {
        const headers = { Accept: "application/vnd.github+json", "User-Agent": "CalabiYauWiki/1.0" };
        if (env.GITHUB_TOKEN) headers.Authorization = `Bearer ${env.GITHUB_TOKEN}`;
        const resp = await fetch("https://api.github.com/repos/znzsofficial/CalabiYauVoice_GUI", { headers });
        if (!resp.ok) return Response.json({ stars: 0 }, { headers: { ...corsHeaders, "Cache-Control": "public, max-age=300" } });
        const data = await resp.json();
        return Response.json({ stars: data.stargazers_count ?? 0 }, { headers: { ...corsHeaders, "Cache-Control": "public, max-age=300" } });
      } catch {
        return Response.json({ stars: 0 }, { headers: { ...corsHeaders, "Cache-Control": "public, max-age=60" } });
      }
    }

    if (request.method === "GET" && (url.pathname === "/api/image-download" || url.pathname === "/api/file-download")) {
      return proxyFileDownload(url);
    }

    // 其余请求走静态资源
    return env.ASSETS.fetch(request);
  },
};

async function proxy(target, original) {
  const headers = new Headers(original.headers);
  headers.set("Host", new URL(target).host);
  for (const key of [...headers.keys()]) {
    if (key.startsWith("sec-")) headers.delete(key);
  }

  const resp = await fetch(target, {
    method: original.method,
    headers,
    body: original.method === "POST" ? original.body : undefined,
  });

  const respHeaders = new Headers(resp.headers);
  respHeaders.set("Access-Control-Allow-Origin", "*");
  respHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  respHeaders.set("Access-Control-Allow-Headers", "Content-Type");

  return new Response(resp.body, {
    status: resp.status,
    headers: respHeaders,
  });
}

async function proxyFileDownload(url) {
  const rawTarget = url.searchParams.get("url");
  const target = rawTarget ? new URL(rawTarget) : null;

  if (!target || !ALLOWED_IMAGE_HOSTS.has(target.hostname)) {
    return Response.json({ error: "Unsupported image URL" }, { status: 400 });
  }

  const resp = await fetch(target, { headers: { Accept: "*/*" } });
  const headers = new Headers(resp.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Cache-Control", "public, max-age=86400");

  return new Response(resp.body, {
    status: resp.status,
    headers,
  });
}
