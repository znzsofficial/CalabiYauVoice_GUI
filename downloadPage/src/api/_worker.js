// These constants must stay in sync with downloadPage/vite.config.ts
const UPSTREAM = "https://klbq-prod-www.idreamsky.com";
const WIKI_API = "https://wiki.biligame.com/klbq/api.php";
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

    // GET /api/wiki → MediaWiki API proxy (avoids browser CORS)
    if (request.method === "GET" && url.pathname === "/api/wiki") {
      return proxyWikiApi(url);
    }

    // 其余请求走静态资源
    return env.ASSETS.fetch(request);
  },
};

function wikiProxyErrorMessage(status) {
  if (status === 429) return "请求过于频繁，请稍后再试";
  if (status === 403 || status === 567) return "Wiki 访问被风控拦截，请更换网络/VPN 节点后重试";
  if (status >= 500) return "Wiki 上游暂时不可用，请稍后重试";
  if (status >= 400) return `Wiki 请求失败（HTTP ${status}）`;
  return "Wiki 代理请求失败";
}

async function proxyWikiApi(url) {
  const target = new URL(WIKI_API);
  target.search = url.search;
  target.searchParams.delete("origin");
  const cors = { "Access-Control-Allow-Origin": "*" };

  try {
    const resp = await fetch(target, {
      headers: {
        Accept: "application/json,text/javascript,*/*;q=0.01",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        // EdgeOne WAF blocks bare server-side clients; spoof a normal browser referer.
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        Referer: "https://wiki.biligame.com/klbq/",
        Origin: "https://wiki.biligame.com",
      },
    });
    const contentType = resp.headers.get("content-type") || "";
    const body = await resp.arrayBuffer();
    const head = new TextDecoder().decode(body.slice(0, 32)).trimStart();
    const looksHtml = contentType.includes("text/html") || head.startsWith("<!");
    const wafBlocked = resp.status === 567 || resp.status === 403 || (resp.status >= 500 && looksHtml);

    if (!resp.ok || looksHtml || wafBlocked) {
      const status = resp.status === 429 ? 429 : wafBlocked || looksHtml ? 502 : resp.status >= 400 ? resp.status : 502;
      return Response.json(
        {
          error: wikiProxyErrorMessage(resp.status === 200 && looksHtml ? 567 : resp.status),
          upstreamStatus: resp.status,
        },
        { status, headers: { ...cors, "Cache-Control": "no-store" } },
      );
    }

    const headers = new Headers(resp.headers);
    headers.set("Access-Control-Allow-Origin", "*");
    headers.set("Cache-Control", "public, max-age=30");
    return new Response(body, { status: resp.status, headers });
  } catch (error) {
    return Response.json(
      { error: error instanceof Error ? error.message : "Wiki 代理请求失败" },
      { status: 502, headers: cors },
    );
  }
}

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
