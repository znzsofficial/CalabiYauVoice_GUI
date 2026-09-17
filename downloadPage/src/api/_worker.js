import { CORS, HttpError, json, requireDb } from "./http.js";
import { serveRelease } from "./releases.js";
import { proxyApi } from "./proxy.js";
import { serveAvatar, uploadAvatar } from "./avatars.js";
import { getProfile, saveProfile } from "./profiles.js";
import { listComments, postComment, deleteComment } from "./comments.js";
import { likes } from "./likes.js";
import { admin } from "./admin.js";

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    try {
      if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
      if (url.pathname.startsWith("/api/user/avatar/")) return await serveAvatar(request, env, url);
      if (url.pathname.startsWith("/api/user/") || url.pathname.startsWith("/api/admin/")) {
        requireDb(env);
        if (url.pathname.startsWith("/api/admin/")) return await admin(request, env, url, ctx);
        switch (`${request.method} ${url.pathname}`) {
          case "GET /api/user/profile": return await getProfile(request, env, url);
          case "PUT /api/user/profile": return await saveProfile(request, env, url, false, ctx);
          case "POST /api/user/avatar": return await uploadAvatar(request, env);
          case "GET /api/user/comments": return await listComments(request, env, url);
          case "POST /api/user/comments": return await postComment(request, env, url);
          case "DELETE /api/user/comments": return await deleteComment(request, env, url);
          case "GET /api/user/likes":
          case "POST /api/user/likes":
          case "PUT /api/user/likes":
          case "DELETE /api/user/likes": return await likes(request, env, url);
          default: throw new HttpError(404, "接口不存在");
        }
      }
      const release = await serveRelease(request, env, url);
      if (release) return release;
      const proxy = await proxyApi(request, env, url);
      if (proxy) return proxy;
      if (url.pathname.startsWith("/api/")) throw new HttpError(404, "接口不存在");
      return await env.ASSETS.fetch(request);
    } catch (error) {
      if (error instanceof HttpError) return json({ error: error.message }, error.status, error.headers);
      const requestId = crypto.randomUUID();
      console.error(JSON.stringify({ event: "request_failed", requestId, method: request.method, path: url.pathname, error: String(error) }));
      return json({ error: "服务暂不可用，请稍后重试", requestId }, 500);
    }
  },
};
