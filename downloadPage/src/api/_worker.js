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
          "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, X-Wiki-Cookie, Cookie, X-Admin-Password",
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

    // ───── 自定义用户档案 API ─────────────────────────────────────────
    if (url.pathname.startsWith("/api/user/")) {
      const userResponse = await handleUserApi(request, env, url);
      if (userResponse) return userResponse;
    }

    // ───── 管理后台 API（密码保护）────────────────────────────────────
    if (url.pathname.startsWith("/api/admin/")) {
      const adminResponse = await handleAdminApi(request, env, url);
      if (adminResponse) return adminResponse;
    }

    const apkResponse = await serveReleaseApk(request, env, url.pathname);
    if (apkResponse) return apkResponse;

    // 其余请求走静态资源
    return env.ASSETS.fetch(request);
  },
};

function apkObjectKey(pathname) {
  const match = pathname.match(/^\/downloads\/(CalabiYauVoice-(?:latest|\d+\.\d+\.\d+)\.apk)$/);
  return match ? `android/${match[1]}` : null;
}

async function serveReleaseApk(request, env, pathname) {
  const key = apkObjectKey(pathname);
  if (!key || !env.RELEASES) return null;
  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response("Method Not Allowed", { status: 405, headers: { Allow: "GET, HEAD" } });
  }

  const object = request.method === "HEAD"
    ? await env.RELEASES.head(key)
    : await env.RELEASES.get(key, { range: request.headers });

  if (!object) return new Response("Not Found", { status: 404 });

  const filename = key.slice(key.lastIndexOf("/") + 1);
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("etag", object.httpEtag);
  headers.set("Content-Type", "application/vnd.android.package-archive");
  headers.set("Content-Disposition", `attachment; filename="${filename}"`);
  if (filename.endsWith("-latest.apk")) {
    headers.set("Cache-Control", "public, max-age=0, must-revalidate");
  } else {
    headers.set("Cache-Control", "public, max-age=31536000, immutable");
  }
  headers.set("Access-Control-Allow-Origin", "*");

  if (request.method === "HEAD") {
    return new Response(null, { status: 200, headers });
  }
  if (!("body" in object) || !object.body) {
    return new Response("Not Found", { status: 404 });
  }
  return new Response(object.body, { status: 200, headers });
}

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
  let target = null;
  try {
    target = rawTarget ? new URL(rawTarget) : null;
  } catch {
    target = null;
  }

  if (!target || !ALLOWED_IMAGE_HOSTS.has(target.hostname)) {
    return Response.json({ error: "不支持的文件地址" }, { status: 400 });
  }

  try {
    // Follow redirects manually so Special:Redirect and CDN hops work under WAF-friendly headers.
    let current = target;
    let resp = null;
    for (let hop = 0; hop < 5; hop++) {
      resp = await fetch(current, {
        method: "GET",
        redirect: "manual",
        headers: {
          Accept: "*/*",
          "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
          Referer: "https://wiki.biligame.com/klbq/",
          Origin: "https://wiki.biligame.com",
        },
      });
      if (![301, 302, 303, 307, 308].includes(resp.status)) break;
      const location = resp.headers.get("location");
      if (!location) break;
      const next = new URL(location, current);
      if (!ALLOWED_IMAGE_HOSTS.has(next.hostname) && next.hostname !== current.hostname) {
        return Response.json({ error: "文件重定向目标不受支持" }, { status: 502 });
      }
      current = next;
    }
    if (!resp || !resp.ok) {
      return Response.json({ error: `文件请求失败（HTTP ${resp?.status || 0}）` }, { status: 502 });
    }

    const headers = new Headers(resp.headers);
    headers.set("Access-Control-Allow-Origin", "*");
    headers.set("Cache-Control", "public, max-age=86400");
    return new Response(resp.body, { status: resp.status, headers });
  } catch (error) {
    return Response.json(
      { error: error instanceof Error ? error.message : "文件代理失败" },
      { status: 502 },
    );
  }
}

// ───── 用户档案与头像处理 ─────────────────────────────────────────────

const CORS_USER_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, X-Wiki-Cookie, Cookie",
};

async function handleUserApi(request, env, url) {
  // 1. GET /api/user/avatar/:key → 从 R2 读取头像
  if (url.pathname.startsWith("/api/user/avatar/")) {
    const key = url.pathname.replace(/^\/api\/user\/avatar\//, "");
    if (!key || !env.USER_ASSETS) return new Response("Not Found", { status: 404, headers: CORS_USER_HEADERS });

    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method Not Allowed", { status: 405, headers: { ...CORS_USER_HEADERS, Allow: "GET, HEAD" } });
    }

    const object = await env.USER_ASSETS.get(key);
    if (!object) return new Response("Avatar Not Found", { status: 404, headers: CORS_USER_HEADERS });

    const headers = new Headers(CORS_USER_HEADERS);
    object.writeHttpMetadata(headers);
    headers.set("etag", object.httpEtag);
    headers.set("Cache-Control", "public, max-age=86400");
    return new Response(object.body, { status: 200, headers });
  }

  // GET /api/user/avatar（缺少 key）——POST 走下方的上传处理
  if (request.method === "GET" && url.pathname === "/api/user/avatar") {
    return Response.json({ error: "缺少头像 key" }, { status: 404, headers: CORS_USER_HEADERS });
  }

  // 2. GET /api/user/profile?bid=xxx 或 ?wiki_id=xxx
  if (request.method === "GET" && url.pathname === "/api/user/profile") {
    if (!env.DB) {
      return Response.json({ error: "D1 database not configured" }, { status: 500, headers: CORS_USER_HEADERS });
    }
    const bid = url.searchParams.get("bid")?.trim();
    const wikiId = url.searchParams.get("wiki_id")?.trim();
    if (!bid && !wikiId) {
      return Response.json({ error: "缺少 bid 或 wiki_id 参数" }, { status: 400, headers: CORS_USER_HEADERS });
    }
    if (wikiId && !/^\d+$/.test(wikiId)) {
      return Response.json({ error: "wiki_id 必须为纯数字" }, { status: 400, headers: CORS_USER_HEADERS });
    }

    try {
      let query = "SELECT bid, wiki_user_id as wikiUserId, custom_name as customName, avatar_url as avatarUrl, bio, badge, updated_at as updatedAt FROM user_profiles WHERE ";
      let result = null;
      if (bid) {
        result = await env.DB.prepare(query + "bid = ?").bind(bid).first();
      } else {
        result = await env.DB.prepare(query + "wiki_user_id = ?").bind(parseInt(wikiId, 10)).first();
      }
      if (!result) {
        return Response.json({ profile: null }, { headers: { ...CORS_USER_HEADERS, "Cache-Control": "public, max-age=60" } });
      }
      result = withAbsoluteAvatarUrl(result, url.origin);
      return Response.json({ profile: result }, { headers: { ...CORS_USER_HEADERS, "Cache-Control": "public, max-age=60" } });
    } catch (err) {
      console.error("user profile query failed:", err);
      return Response.json({ error: "查询用户档案失败" }, { status: 500, headers: CORS_USER_HEADERS });
    }
  }

  // 3. POST /api/user/profiles → 批量查询 { bids?: string[], wiki_ids?: number[] }
  if (request.method === "POST" && url.pathname === "/api/user/profiles") {
    if (!env.DB) {
      return Response.json({ error: "D1 database not configured" }, { status: 500, headers: CORS_USER_HEADERS });
    }
    try {
      const body = await request.json();
      const bids = Array.isArray(body.bids) ? body.bids.map(b => String(b).trim()).filter(Boolean) : [];
      const wikiIds = Array.isArray(body.wiki_ids) ? body.wiki_ids.map(id => Number(id)).filter(id => !isNaN(id)) : [];

      if (bids.length === 0 && wikiIds.length === 0) {
        return Response.json({ profiles: {} }, { headers: CORS_USER_HEADERS });
      }

      // D1 单条语句最多绑定 100 个参数，各限制 50
      const safeBids = bids.slice(0, 50);
      const safeWikiIds = wikiIds.slice(0, 50);

      if (safeBids.length === 0 && safeWikiIds.length === 0) {
        return Response.json({ profiles: {} }, { headers: CORS_USER_HEADERS });
      }

      const conditions = [];
      const bindings = [];

      if (safeBids.length > 0) {
        const placeholders = safeBids.map(() => "?").join(",");
        conditions.push(`bid IN (${placeholders})`);
        bindings.push(...safeBids);
      }
      if (safeWikiIds.length > 0) {
        const placeholders = safeWikiIds.map(() => "?").join(",");
        conditions.push(`wiki_user_id IN (${placeholders})`);
        bindings.push(...safeWikiIds);
      }

      const sql = `SELECT bid, wiki_user_id as wikiUserId, custom_name as customName, avatar_url as avatarUrl, bio, badge, updated_at as updatedAt FROM user_profiles WHERE ${conditions.join(" OR ")}`;
      const { results } = await env.DB.prepare(sql).bind(...bindings).all();

      const profilesMap = {};
      for (const item of (results || [])) {
        profilesMap[item.bid] = withAbsoluteAvatarUrl(item, url.origin);
      }
      return Response.json({ profiles: profilesMap }, { headers: { ...CORS_USER_HEADERS, "Cache-Control": "public, max-age=60" } });
    } catch (err) {
      console.error("user profiles batch query failed:", err);
      return Response.json({ error: "批量查询用户档案失败" }, { status: 500, headers: CORS_USER_HEADERS });
    }
  }

  // 4. POST /api/user/avatar → 上传头像到 R2
  if (request.method === "POST" && url.pathname === "/api/user/avatar") {
    if (!env.USER_ASSETS) {
      return Response.json({ error: "R2 user bucket not configured" }, { status: 500, headers: CORS_USER_HEADERS });
    }
    const authUser = await authenticateWikiUser(request);
    if (!authUser) {
      return Response.json({ error: "登录已失效或未授权，请检查 Wiki 登录 Cookie" }, { status: 401, headers: CORS_USER_HEADERS });
    }

    try {
      const contentType = request.headers.get("content-type") || "";
      let fileBytes = null;
      let mimeType = "image/png";
      let ext = "png";

      if (contentType.includes("multipart/form-data")) {
        const formData = await request.formData();
        const file = formData.get("file");
        if (!file || typeof file === "string") {
          return Response.json({ error: "未找到上传的文件字段 'file'" }, { status: 400, headers: CORS_USER_HEADERS });
        }
        fileBytes = await file.arrayBuffer();
        mimeType = file.type || "image/png";
      } else {
        fileBytes = await request.arrayBuffer();
        mimeType = contentType.split(";")[0].trim() || "image/png";
      }

      // 大小限制 2MB
      if (!fileBytes || fileBytes.byteLength === 0) {
        return Response.json({ error: "图片文件为空" }, { status: 400, headers: CORS_USER_HEADERS });
      }
      if (fileBytes.byteLength > 2 * 1024 * 1024) {
        return Response.json({ error: "图片大小不能超过 2MB" }, { status: 400, headers: CORS_USER_HEADERS });
      }

      // MIME 检查
      if (mimeType.includes("jpeg") || mimeType.includes("jpg")) ext = "jpg";
      else if (mimeType.includes("webp")) ext = "webp";
      else if (mimeType.includes("png")) ext = "png";
      else {
        return Response.json({ error: "仅支持 PNG、JPEG 或 WEBP 格式图片" }, { status: 400, headers: CORS_USER_HEADERS });
      }

      // 魔数校验：内容必须与声明的图片格式一致
      const sniffed = sniffImageMime(fileBytes);
      if (!sniffed) {
        return Response.json({ error: "文件不是有效的图片" }, { status: 400, headers: CORS_USER_HEADERS });
      }
      if (sniffed !== mimeType) {
        return Response.json({ error: "图片内容与声明的格式不符" }, { status: 400, headers: CORS_USER_HEADERS });
      }

      // 计算内容 Hash，生成唯一文件名
      const hashBuffer = await crypto.subtle.digest("SHA-256", fileBytes);
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      const hashHex = hashArray.map(b => b.toString(16).padStart(2, "0")).join("").slice(0, 16);

      const objectKey = `avatars/${authUser.bid}_${hashHex}.${ext}`;
      await env.USER_ASSETS.put(objectKey, fileBytes, {
        httpMetadata: {
          contentType: mimeType,
          cacheControl: "public, max-age=31536000, immutable",
        },
      });

      const avatarUrl = `/api/user/avatar/${objectKey}`;
      return Response.json({ avatarUrl, objectKey }, { headers: CORS_USER_HEADERS });
    } catch (err) {
      console.error("avatar upload failed:", err);
      return Response.json({ error: "上传头像失败" }, { status: 500, headers: CORS_USER_HEADERS });
    }
  }

  // 5. PUT /api/user/profile → 更新用户自定义信息
  if (request.method === "PUT" && url.pathname === "/api/user/profile") {
    if (!env.DB) {
      return Response.json({ error: "D1 database not configured" }, { status: 500, headers: CORS_USER_HEADERS });
    }
    const authUser = await authenticateWikiUser(request);
    if (!authUser) {
      return Response.json({ error: "登录已失效或未授权，请检查 Wiki 登录 Cookie" }, { status: 401, headers: CORS_USER_HEADERS });
    }

    try {
      const body = await request.json();
      const customName = body.customName?.trim() || null;
      const bio = body.bio?.trim() || null;
      const badge = body.badge?.trim() || null;

      // 头像 URL 只允许本服务的头像路径（相对或绝对均可），仅存相对路径
      let avatarUrl = null;
      if (body.avatarUrl) {
        const raw = String(body.avatarUrl).trim();
        try {
          const parsed = new URL(raw, url.origin);
          if (parsed.origin !== url.origin || !parsed.pathname.startsWith("/api/user/avatar/")) {
            return Response.json({ error: "头像地址不受支持" }, { status: 400, headers: CORS_USER_HEADERS });
          }
          avatarUrl = parsed.pathname;
        } catch {
          return Response.json({ error: "头像地址无效" }, { status: 400, headers: CORS_USER_HEADERS });
        }
      }

      // 字符长度安全保护
      if (customName && customName.length > 30) {
        return Response.json({ error: "自定义名称不能超过 30 字符" }, { status: 400, headers: CORS_USER_HEADERS });
      }
      if (bio && bio.length > 200) {
        return Response.json({ error: "个人简介不能超过 200 字符" }, { status: 400, headers: CORS_USER_HEADERS });
      }
      if (badge && badge.length > 20) {
        return Response.json({ error: "徽章头衔不能超过 20 字符" }, { status: 400, headers: CORS_USER_HEADERS });
      }

      const now = Math.floor(Date.now() / 1000);

      // 更新前读取旧头像，替换后删除旧 R2 对象，避免孤儿文件
      const oldRow = await env.DB.prepare("SELECT avatar_url FROM user_profiles WHERE bid = ?").bind(authUser.bid).first();
      const oldAvatarPath = oldRow?.avatar_url;

      const sql = `
        INSERT INTO user_profiles (bid, wiki_user_id, custom_name, avatar_url, bio, badge, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(bid) DO UPDATE SET
          wiki_user_id = excluded.wiki_user_id,
          custom_name = excluded.custom_name,
          avatar_url = excluded.avatar_url,
          bio = excluded.bio,
          badge = excluded.badge,
          updated_at = excluded.updated_at
      `;
      await env.DB.prepare(sql).bind(
        authUser.bid,
        authUser.wikiUserId,
        customName,
        avatarUrl,
        bio,
        badge,
        now
      ).run();

      if (env.USER_ASSETS && oldAvatarPath && oldAvatarPath !== avatarUrl && oldAvatarPath.startsWith("/api/user/avatar/")) {
        const oldKey = oldAvatarPath.replace(/^\/api\/user\/avatar\//, "");
        if (oldKey) await env.USER_ASSETS.delete(oldKey).catch(() => {});
      }

      return Response.json({
        success: true,
        profile: withAbsoluteAvatarUrl({
          bid: authUser.bid,
          wikiUserId: authUser.wikiUserId,
          customName,
          avatarUrl,
          bio,
          badge,
          updatedAt: now,
        }, url.origin),
      }, { headers: CORS_USER_HEADERS });
    } catch (err) {
      console.error("user profile update failed:", err);
      return Response.json({ error: "更新资料失败" }, { status: 500, headers: CORS_USER_HEADERS });
    }
  }

  return null;
}

/** 通过文件头魔数判断真实图片格式 */
function sniffImageMime(buffer) {
  const b = new Uint8Array(buffer.slice(0, 12));
  if (b.length >= 8 && b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) return "image/png";
  if (b.length >= 3 && b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff) return "image/jpeg";
  if (
    b.length >= 12 &&
    b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46 &&
    b[8] === 0x57 && b[9] === 0x45 && b[10] === 0x42 && b[11] === 0x50
  ) return "image/webp";
  return null;
}

/**
 * 校验请求中的 Wiki Cookie，请求 MediaWiki 验证当前登录用户身份。
 * 返回 { bid: string, wikiUserId: number } 或 null
 */
async function authenticateWikiUser(request) {
  const cookie = request.headers.get("X-Wiki-Cookie") || request.headers.get("Cookie");
  if (!cookie) return null;

  try {
    const url = `${WIKI_API}?action=query&meta=userinfo&uiprop=rights&format=json`;
    const resp = await fetch(url, {
      method: "GET",
      redirect: "manual",
      headers: {
        Accept: "application/json",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        // 与 proxyWikiApi 保持一致，避免被 EdgeOne WAF 风控拦截
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        Referer: "https://wiki.biligame.com/klbq/",
        Origin: "https://wiki.biligame.com",
        Cookie: cookie,
      },
    });
    if (!resp.ok) return null;

    const data = await resp.json();
    const userInfo = data?.query?.userinfo;
    if (!userInfo || userInfo.id === 0 || userInfo.anon !== undefined) {
      return null;
    }

    return {
      bid: String(userInfo.name).trim(),
      wikiUserId: Number(userInfo.id),
    };
  } catch {
    return null;
  }
}

/** 将相对头像路径拼接为绝对 URL，便于客户端直接展示 */
function withAbsoluteAvatarUrl(profile, origin) {
  if (profile && profile.avatarUrl && profile.avatarUrl.startsWith("/")) {
    return { ...profile, avatarUrl: `${origin}${profile.avatarUrl}` };
  }
  return profile;
}

// ───── 管理后台 API ──────────────────────────────────────────────────

async function sha256Hex(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** 管理员鉴权：X-Admin-Password 头与 env.ADMIN_PASSWORD 比对（两侧哈希后比较） */
async function isAdminAuthorized(request, env) {
  const expected = (env.ADMIN_PASSWORD || "").trim();
  if (!expected) return false;
  const provided = request.headers.get("X-Admin-Password");
  if (!provided) return false;
  const [providedHash, expectedHash] = await Promise.all([sha256Hex(provided), sha256Hex(expected)]);
  return providedHash === expectedHash;
}

/**
 * 管理后台接口（全部需要密码）：
 * - GET    /api/admin/profiles?q=&page=&size=  列表/搜索（bid / 昵称模糊，wiki_id 精确）
 * - PUT    /api/admin/profile                  编辑指定档案（bid 必填）
 * - DELETE /api/admin/profile?bid=             删除档案（连带删除 R2 头像）
 */
async function handleAdminApi(request, env, url) {
  if (!(await isAdminAuthorized(request, env))) {
    return Response.json({ error: "密码错误或未配置管理密码" }, { status: 401, headers: CORS_USER_HEADERS });
  }
  if (!env.DB) {
    return Response.json({ error: "D1 database not configured" }, { status: 500, headers: CORS_USER_HEADERS });
  }

  // 1. GET /api/admin/profiles → 列表/搜索
  if (request.method === "GET" && url.pathname === "/api/admin/profiles") {
    const q = url.searchParams.get("q")?.trim() || "";
    const page = Math.max(1, parseInt(url.searchParams.get("page") || "1", 10) || 1);
    const size = Math.min(50, Math.max(1, parseInt(url.searchParams.get("size") || "20", 10) || 20));
    const offset = (page - 1) * size;

    let where = "";
    let bindings = [];
    if (q) {
      where = "WHERE bid LIKE ? OR custom_name LIKE ? OR wiki_user_id = ?";
      const likePattern = `%${q}%`;
      bindings = [likePattern, likePattern, /^\d+$/.test(q) ? parseInt(q, 10) : -1];
    }

    try {
      const totalRow = await env.DB.prepare(`SELECT COUNT(*) as count FROM user_profiles ${where}`)
        .bind(...bindings).first();
      const { results } = await env.DB.prepare(
        `SELECT bid, wiki_user_id as wikiUserId, custom_name as customName, avatar_url as avatarUrl, bio, badge, updated_at as updatedAt
         FROM user_profiles ${where}
         ORDER BY updated_at DESC LIMIT ? OFFSET ?`
      ).bind(...bindings, size, offset).all();

      return Response.json(
        { total: totalRow?.count || 0, page, size, profiles: (results || []).map((p) => withAbsoluteAvatarUrl(p, url.origin)) },
        { headers: { ...CORS_USER_HEADERS, "Cache-Control": "no-store" } },
      );
    } catch (err) {
      console.error("admin profiles query failed:", err);
      return Response.json({ error: "查询失败" }, { status: 500, headers: CORS_USER_HEADERS });
    }
  }

  // 2. PUT /api/admin/profile → 编辑指定档案
  if (request.method === "PUT" && url.pathname === "/api/admin/profile") {
    try {
      const body = await request.json();
      const bid = String(body.bid || "").trim();
      if (!bid) {
        return Response.json({ error: "缺少 bid" }, { status: 400, headers: CORS_USER_HEADERS });
      }
      const oldRow = await env.DB.prepare("SELECT avatar_url FROM user_profiles WHERE bid = ?").bind(bid).first();
      if (!oldRow) {
        return Response.json({ error: "档案不存在" }, { status: 404, headers: CORS_USER_HEADERS });
      }

      // 字段显式传入才更新（允许传 null 清空）
      const customName = "customName" in body ? (body.customName?.trim() || null) : null;
      const bio = "bio" in body ? (body.bio?.trim() || null) : null;
      const badge = "badge" in body ? (body.badge?.trim() || null) : null;
      if (customName && customName.length > 30) {
        return Response.json({ error: "自定义名称不能超过 30 字符" }, { status: 400, headers: CORS_USER_HEADERS });
      }
      if (bio && bio.length > 200) {
        return Response.json({ error: "个人简介不能超过 200 字符" }, { status: 400, headers: CORS_USER_HEADERS });
      }
      if (badge && badge.length > 20) {
        return Response.json({ error: "徽章头衔不能超过 20 字符" }, { status: 400, headers: CORS_USER_HEADERS });
      }

      // 头像：只接受本服务头像路径；null 表示清空
      let avatarUrl = null;
      if (body.avatarUrl) {
        const raw = String(body.avatarUrl).trim();
        try {
          const parsed = new URL(raw, url.origin);
          if (parsed.origin !== url.origin || !parsed.pathname.startsWith("/api/user/avatar/")) {
            return Response.json({ error: "头像地址不受支持" }, { status: 400, headers: CORS_USER_HEADERS });
          }
          avatarUrl = parsed.pathname;
        } catch {
          return Response.json({ error: "头像地址无效" }, { status: 400, headers: CORS_USER_HEADERS });
        }
      }

      const now = Math.floor(Date.now() / 1000);
      await env.DB.prepare(
        `UPDATE user_profiles
         SET custom_name = ?, avatar_url = ?, bio = ?, badge = ?, updated_at = ?
         WHERE bid = ?`
      ).bind(customName, avatarUrl, bio, badge, now, bid).run();

      // 头像被清空或替换时删除旧 R2 对象
      const oldAvatarPath = oldRow.avatar_url;
      if (env.USER_ASSETS && oldAvatarPath && oldAvatarPath !== avatarUrl && oldAvatarPath.startsWith("/api/user/avatar/")) {
        const oldKey = oldAvatarPath.replace(/^\/api\/user\/avatar\//, "");
        if (oldKey) await env.USER_ASSETS.delete(oldKey).catch(() => {});
      }

      return Response.json(
        { success: true, profile: withAbsoluteAvatarUrl({ bid, customName, avatarUrl, bio, badge, updatedAt: now }, url.origin) },
        { headers: CORS_USER_HEADERS },
      );
    } catch (err) {
      console.error("admin profile update failed:", err);
      return Response.json({ error: "更新失败" }, { status: 500, headers: CORS_USER_HEADERS });
    }
  }

  // 3. DELETE /api/admin/profile → 删除档案
  if (request.method === "DELETE" && url.pathname === "/api/admin/profile") {
    const bid = url.searchParams.get("bid")?.trim();
    if (!bid) {
      return Response.json({ error: "缺少 bid" }, { status: 400, headers: CORS_USER_HEADERS });
    }
    try {
      const oldRow = await env.DB.prepare("SELECT avatar_url FROM user_profiles WHERE bid = ?").bind(bid).first();
      const result = await env.DB.prepare("DELETE FROM user_profiles WHERE bid = ?").bind(bid).run();
      if (env.USER_ASSETS && oldRow?.avatar_url?.startsWith("/api/user/avatar/")) {
        const oldKey = oldRow.avatar_url.replace(/^\/api\/user\/avatar\//, "");
        if (oldKey) await env.USER_ASSETS.delete(oldKey).catch(() => {});
      }
      return Response.json({ success: true, deleted: (result.changes || 0) > 0 }, { headers: CORS_USER_HEADERS });
    } catch (err) {
      console.error("admin profile delete failed:", err);
      return Response.json({ error: "删除失败" }, { status: 500, headers: CORS_USER_HEADERS });
    }
  }

  return null;
}
