import { HttpError, json, text, integer, readJson } from "./http.js";
import { wikiUser, guestIdentity, sha256 } from "./auth.js";
import { writeGate, gateCondition, requireClaim } from "./writes.js";

const COMMENT_COLUMNS = `c.id,c.author_bid AS authorBid,c.author_tag AS authorTag,
  COALESCE(NULLIF(p.custom_name,''),c.author_name,c.author_bid) AS authorName,
  p.avatar_url AS authorAvatarUrl,c.content,c.created_at AS createdAt`;
const JOIN = "FROM profile_comments c LEFT JOIN user_profiles p ON p.bid=c.author_bid AND c.author_bid<>'anon'";
const absoluteComment = (row, origin) => row?.authorAvatarUrl?.startsWith("/") ? { ...row, authorAvatarUrl: origin + row.authorAvatarUrl } : row;

export async function targetExists(db, bid) {
  if (bid !== "__public__" && !(await db.prepare("SELECT 1 FROM user_profiles WHERE bid=?").bind(bid).first())) throw new HttpError(404, "目标用户不存在");
}

export async function listComments(request, env, url) {
  const bid = text(url.searchParams.get("bid"), "bid", 128, true);
  const size = integer(url.searchParams.get("size"), 20, 50);
  const page = integer(url.searchParams.get("page"), 1, 100000);
  const cursor = url.searchParams.get("before");
  let condition = "", bindings = [bid];
  if (cursor) {
    const parts = /^(\d+):(\d+)$/.exec(cursor);
    if (!parts) throw new HttpError(400, "分页游标无效");
    const time = integer(parts[1]), id = integer(parts[2]);
    condition = "AND (c.created_at < ? OR (c.created_at = ? AND c.id < ?))";
    bindings.push(time, time, id);
  }
  const [count, rows] = await env.DB.batch([
    env.DB.prepare("SELECT COUNT(*) AS count FROM profile_comments WHERE target_bid=?").bind(bid),
    env.DB.prepare(`SELECT ${COMMENT_COLUMNS} ${JOIN} WHERE c.target_bid=? ${condition}
      ORDER BY c.created_at DESC,c.id DESC LIMIT ? OFFSET ?`).bind(...bindings, size + 1, cursor ? 0 : (page - 1) * size),
  ]);
  const hasMore = rows.results.length > size;
  const comments = rows.results.slice(0, size).map(row => absoluteComment(row, url.origin));
  const last = comments.at(-1);
  return json({ total: count.results[0].count, page, size, comments, hasMore, nextCursor: hasMore && last ? `${last.createdAt}:${last.id}` : null });
}

async function replay(db, actor, key, fingerprint, origin) {
  const saved = await db.prepare("SELECT fingerprint,comment_id FROM write_requests WHERE actor=? AND request_key=?").bind(actor, key).first();
  if (!saved) return null;
  if (saved.fingerprint !== fingerprint) throw new HttpError(409, "请求编号已用于不同留言");
  const comment = await db.prepare(`SELECT ${COMMENT_COLUMNS} ${JOIN} WHERE c.id=?`).bind(saved.comment_id).first();
  if (!comment) throw new HttpError(410, "该请求的留言已删除");
  return json({ success: true, comment: absoluteComment(comment, origin) });
}

export async function postComment(request, env, url) {
  const user = await wikiUser(request);
  const body = await readJson(request);
  const target = text(body.targetBid, "targetBid", 128, true);
  const content = text(body.content, "content", 200, true);
  const name = user ? null : text(body.authorName, "authorName", 20) || "访客";
  const guest = user ? null : await guestIdentity(request, env);
  const actor = user ? `wiki:${user.wikiUserId}` : guest.actor;
  const requestKey = request.headers.get("Idempotency-Key") || crypto.randomUUID();
  if (!/^[a-zA-Z0-9_-]{8,128}$/.test(requestKey)) throw new HttpError(400, "请求编号无效");
  const fingerprint = await sha256(JSON.stringify([target, content, name]));
  const previous = await replay(env.DB, actor, requestKey, fingerprint, url.origin);
  if (previous) return previous;
  await targetExists(env.DB, target);
  const gate = writeGate(env.DB, user ? `bid:${user.bid}` : guest.throttleKey, user ? 10 : 30);
  const results = await env.DB.batch([
    gate.claim,
    env.DB.prepare(`INSERT INTO profile_comments(target_bid,author_bid,author_name,author_tag,author_ip_hash,content,created_at)
      SELECT ?,?,?,?,?,?,? WHERE ${gateCondition}
      AND NOT EXISTS(SELECT 1 FROM write_requests WHERE actor=? AND request_key=?)`)
      .bind(target, user?.bid || "anon", name, guest?.tag || null, guest?.throttleKey || null, content, gate.now, gate.key, gate.token, actor, requestKey),
    env.DB.prepare(`INSERT INTO write_requests(actor,request_key,fingerprint,comment_id,created_at)
      SELECT ?,?,?,last_insert_rowid(),? WHERE changes()=1`).bind(actor, requestKey, fingerprint, gate.now),
  ]);
  // A concurrent retry may have won: return its stored result before reporting 429.
  const response = await replay(env.DB, actor, requestKey, fingerprint, url.origin);
  if (response) return response;
  requireClaim(results[0], gate.seconds);
  throw new HttpError(500, "留言未写入");
}

export async function deleteComment(request, env, url, admin = false) {
  const user = admin ? null : await wikiUser(request, true);
  const id = integer(url.searchParams.get("id"));
  if (!id) throw new HttpError(400, "缺少留言 id");
  const result = await env.DB.prepare(`DELETE FROM profile_comments WHERE id=? ${admin ? "" : "AND author_bid=?"}`)
    .bind(...(admin ? [id] : [id, user.bid])).run();
  if (!admin && !result.meta.changes) throw new HttpError(404, "留言不存在或无权删除");
  return json({ success: true, deleted: result.meta.changes > 0 });
}
