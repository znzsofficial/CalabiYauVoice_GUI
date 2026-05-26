const UPSTREAM = "https://klbq-prod-www.idreamsky.com";

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
