import { HttpError, json, text, integer, escapeLike, absoluteProfile } from "./http.js";
import { requireAdmin } from "./auth.js";
import { PROFILE_COLUMNS, saveProfile } from "./profiles.js";
import { deleteComment } from "./comments.js";
import { avatarPath, retireAvatar, cleanupAvatars } from "./avatars.js";
import { moderate, auditList, auditStatement } from "./moderation.js";

export async function admin(request, env, url, ctx) {
  await requireAdmin(request, env);
  if (request.method === "PUT" && url.pathname === "/api/admin/comment") return moderate(request, env);
  if (request.method === "GET" && url.pathname === "/api/admin/audit") return auditList(env, url);
  if (request.method === "PUT" && url.pathname === "/api/admin/profile") return saveProfile(request, env, url, true, ctx);
  if (request.method === "DELETE" && url.pathname === "/api/admin/comment") return deleteComment(request, env, url, true);
  if (request.method === "POST" && url.pathname === "/api/admin/maintenance") {
    await cleanupAvatars(env, url.origin, 100);
    await env.DB.batch([
      env.DB.prepare("DELETE FROM write_throttle WHERE key IN (SELECT key FROM write_throttle WHERE last_at < unixepoch()-86400 LIMIT 1000)"),
      env.DB.prepare("DELETE FROM write_requests WHERE rowid IN (SELECT rowid FROM write_requests WHERE created_at < unixepoch()-604800 LIMIT 1000)"),
      env.DB.prepare("DELETE FROM reply_notifications WHERE rowid IN (SELECT rowid FROM reply_notifications WHERE read_at < unixepoch()-7776000 LIMIT 1000)"),
    ]);
    return json({ success: true });
  }
  if (request.method === "DELETE" && url.pathname === "/api/admin/profile") {
    const bid = text(url.searchParams.get("bid"), "bid", 128, true);
    const results = await env.DB.batch([
      env.DB.prepare("SELECT avatar_url FROM user_profiles WHERE bid=?").bind(bid),
      env.DB.prepare("DELETE FROM user_profiles WHERE bid=?").bind(bid),
      auditStatement(env.DB, "delete_profile", bid),
      env.DB.prepare("DELETE FROM profile_comments WHERE target_bid=?").bind(bid),
      env.DB.prepare("DELETE FROM profile_likes WHERE target_bid=?").bind(bid),
    ]);
    const old = results[0].results[0]?.avatar_url;
    if (old) ctx.waitUntil(retireAvatar(env, avatarPath(old, url.origin), bid, url.origin)
      .catch(error => console.error(JSON.stringify({ event: "avatar_cleanup_failed", error: String(error) }))));
    return json({ success: true, deleted: results[1].meta.changes > 0 });
  }
  if (request.method === "GET" && ["/api/admin/profiles", "/api/admin/comments"].includes(url.pathname)) {
    const profiles = url.pathname.endsWith("profiles");
    const q = text(url.searchParams.get("q"), "q", 200);
    const page = integer(url.searchParams.get("page"), 1, 100000);
    const size = integer(url.searchParams.get("size"), 20, 50);
    let where = "", bindings = [];
    if (q) {
      if (profiles) {
        where = "WHERE bid LIKE ? ESCAPE '\\' OR custom_name LIKE ? ESCAPE '\\' OR wiki_user_id=?";
        bindings = [escapeLike(q), escapeLike(q), /^\d+$/.test(q) && Number.isSafeInteger(Number(q)) ? Number(q) : -1];
      } else {
        where = "WHERE c.content LIKE ? ESCAPE '\\' OR c.author_bid LIKE ? ESCAPE '\\' OR c.target_bid LIKE ? ESCAPE '\\' OR c.author_name LIKE ? ESCAPE '\\' OR p.custom_name LIKE ? ESCAPE '\\' OR c.author_tag LIKE ? ESCAPE '\\'";
        bindings = Array(6).fill(escapeLike(q));
      }
    }
    const from = profiles ? "FROM user_profiles" : "FROM profile_comments c LEFT JOIN user_profiles p ON p.bid=c.author_bid AND c.author_bid<>'anon'";
    const columns = profiles ? PROFILE_COLUMNS : `c.id,c.target_bid AS targetBid,c.author_bid AS authorBid,
      COALESCE(NULLIF(p.custom_name,''),c.author_name,c.author_bid) AS authorName,c.author_tag AS authorTag,
      CASE WHEN c.deleted_at IS NULL THEN c.content ELSE '该留言已删除' END AS content,
      c.root_id AS rootId,c.reply_to_id AS replyToId,c.deleted_at AS deletedAt,c.hidden_at AS hiddenAt,c.pinned_at AS pinnedAt,c.created_at AS createdAt`;
    const [count, rows] = await env.DB.batch([
      env.DB.prepare(`SELECT COUNT(*) AS count ${from} ${where}`).bind(...bindings),
      env.DB.prepare(`SELECT ${columns} ${from} ${where} ORDER BY ${profiles ? "updated_at DESC,bid" : "c.created_at DESC,c.id DESC"} LIMIT ? OFFSET ?`).bind(...bindings, size, (page - 1) * size),
    ]);
    return json({ total: count.results[0].count, page, size, [profiles ? "profiles" : "comments"]: profiles ? rows.results.map(p => absoluteProfile(p, url.origin)) : rows.results });
  }
  throw new HttpError(404, "管理接口不存在");
}
