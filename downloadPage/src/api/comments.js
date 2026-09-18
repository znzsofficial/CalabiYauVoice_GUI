import { HttpError, json, text, integer, readJson } from "./http.js";
import { wikiUser, guestIdentity, sha256 } from "./auth.js";
import { writeGate, gateCondition, requireClaim } from "./writes.js";
import { auditStatement } from "./moderation.js";
import { replyNotificationStatement } from "./notifications.js";

const COMMENT_COLUMNS = `c.id,c.target_bid AS targetBid,c.author_bid AS authorBid,c.author_wiki_user_id AS authorWikiUserId,c.author_tag AS authorTag,
  COALESCE(NULLIF((SELECT up.custom_name FROM user_profiles up
    WHERE (c.author_wiki_user_id IS NOT NULL AND up.wiki_user_id=c.author_wiki_user_id)
      OR (c.author_wiki_user_id IS NULL AND up.bid=c.author_bid)
    ORDER BY up.updated_at DESC LIMIT 1),''),c.author_name,c.author_bid) AS authorName,
  (SELECT up.avatar_url FROM user_profiles up
    WHERE (c.author_wiki_user_id IS NOT NULL AND up.wiki_user_id=c.author_wiki_user_id)
      OR (c.author_wiki_user_id IS NULL AND up.bid=c.author_bid)
    ORDER BY up.updated_at DESC LIMIT 1) AS authorAvatarUrl,c.content,c.created_at AS createdAt,
  c.root_id AS rootId,c.reply_to_id AS replyToId,c.deleted_at AS deletedAt,c.hidden_at AS hiddenAt,c.pinned_at AS pinnedAt,
  CASE WHEN c.root_id IS NULL THEN (SELECT COUNT(*) FROM profile_comments r WHERE r.root_id=c.id AND r.deleted_at IS NULL AND r.hidden_at IS NULL) ELSE 0 END AS replyCount,
  CASE WHEN parent.hidden_at IS NOT NULL THEN '已隐藏留言' WHEN parent.deleted_at IS NULL THEN COALESCE(NULLIF((SELECT up.custom_name FROM user_profiles up
    WHERE (parent.author_wiki_user_id IS NOT NULL AND up.wiki_user_id=parent.author_wiki_user_id)
      OR (parent.author_wiki_user_id IS NULL AND up.bid=parent.author_bid)
    ORDER BY up.updated_at DESC LIMIT 1),''),parent.author_name,parent.author_bid) ELSE '已删除留言' END AS replyToName,
  CASE WHEN parent.hidden_at IS NULL AND parent.deleted_at IS NULL AND parent.author_bid='anon' THEN parent.author_tag ELSE NULL END AS replyToTag`;
const JOIN = `FROM profile_comments c LEFT JOIN profile_comments parent ON parent.id=c.reply_to_id`;
function absoluteComment(row, origin) {
  if (!row) return row;
  const { deletedAt, hiddenAt, ...comment } = row;
  if (deletedAt != null) return { ...comment, deleted: true, authorBid: "", authorWikiUserId: null, authorName: "已删除留言", authorTag: null,
    authorAvatarUrl: null, content: "该留言已删除", replyToName: null, replyToTag: null };
  return { ...comment, deleted: false, authorAvatarUrl: row.authorAvatarUrl?.startsWith("/") ? origin + row.authorAvatarUrl : row.authorAvatarUrl };
}
const VISIBLE_ROOT = `c.root_id IS NULL AND c.hidden_at IS NULL AND (c.deleted_at IS NULL OR EXISTS(
  SELECT 1 FROM profile_comments r WHERE r.root_id=c.id AND r.deleted_at IS NULL AND r.hidden_at IS NULL))`;

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
  const [count, rows, pins] = await env.DB.batch([
    env.DB.prepare(`SELECT COUNT(*) AS count FROM profile_comments c WHERE target_bid=? AND ${VISIBLE_ROOT}`).bind(bid),
    env.DB.prepare(`SELECT ${COMMENT_COLUMNS} ${JOIN} WHERE c.target_bid=? AND ${VISIBLE_ROOT} ${condition}
      ORDER BY c.created_at DESC,c.id DESC LIMIT ? OFFSET ?`).bind(...bindings, size + 1, cursor ? 0 : (page - 1) * size),
    env.DB.prepare(`SELECT ${COMMENT_COLUMNS} ${JOIN} WHERE c.target_bid=? AND ${VISIBLE_ROOT}
      AND c.deleted_at IS NULL AND c.pinned_at IS NOT NULL ORDER BY c.pinned_at DESC,c.id DESC LIMIT 3`).bind(bid),
  ]);
  const hasMore = rows.results.length > size;
  const comments = rows.results.slice(0, size).map(row => absoluteComment(row, url.origin));
  const last = comments.at(-1);
  return json({ total: count.results[0].count, page, size, comments, pinnedComments: pins.results.map(row => absoluteComment(row, url.origin)), hasMore, nextCursor: hasMore && last ? `${last.createdAt}:${last.id}` : null });
}

export async function listReplies(request, env, url) {
  const rootId = integer(url.searchParams.get("rootId"));
  if (!rootId) throw new HttpError(400, "缺少讨论 id");
  const size = integer(url.searchParams.get("size"), 20, 50);
  const cursor = url.searchParams.get("before");
  const focusId = integer(url.searchParams.get("focusId"), null);
  if (focusId && cursor) throw new HttpError(400, "定位和翻页不能同时使用");
  let condition = "", bindings = [rootId];
  if (cursor) {
    const parts = /^(\d+):(\d+)$/.exec(cursor);
    if (!parts) throw new HttpError(400, "分页游标无效");
    const time = integer(parts[1]), id = integer(parts[2]);
    condition = "AND (c.created_at < ? OR (c.created_at = ? AND c.id < ?))";
    bindings.push(time, time, id);
  }
  if (focusId) {
    const focus = await env.DB.prepare("SELECT id,created_at FROM profile_comments WHERE id=? AND root_id=? AND hidden_at IS NULL").bind(focusId, rootId).first();
    if (!focus) throw new HttpError(404, "回复已不可用", {}, "FOCUS_UNAVAILABLE");
    condition = "AND (c.created_at < ? OR (c.created_at = ? AND c.id <= ?))";
    bindings.push(focus.created_at, focus.created_at, focus.id);
  }
  const [roots, count, rows] = await env.DB.batch([
    env.DB.prepare(`SELECT ${COMMENT_COLUMNS} ${JOIN} WHERE c.id=? AND c.root_id IS NULL AND c.hidden_at IS NULL`).bind(rootId),
    env.DB.prepare("SELECT COUNT(*) AS count FROM profile_comments WHERE root_id=? AND hidden_at IS NULL").bind(rootId),
    env.DB.prepare(`SELECT ${COMMENT_COLUMNS} ${JOIN} WHERE c.root_id=? AND c.hidden_at IS NULL ${condition}
      ORDER BY c.created_at DESC,c.id DESC LIMIT ?`).bind(...bindings, size + 1),
  ]);
  const root = roots.results[0];
  if (!root) throw new HttpError(404, "讨论不存在", {}, "DISCUSSION_UNAVAILABLE");
  const hasMore = rows.results.length > size;
  const comments = rows.results.slice(0, size).map(row => absoluteComment(row, url.origin));
  if (focusId && comments[0]?.id !== focusId) throw new HttpError(404, "回复已不可用", {}, "FOCUS_UNAVAILABLE");
  const last = comments.at(-1);
  return json({ root: absoluteComment(root, url.origin), total: count.results[0].count, size, comments, hasMore,
    nextCursor: hasMore && last ? `${last.createdAt}:${last.id}` : null });
}

async function replay(db, actor, key, fingerprint, origin) {
  const saved = await db.prepare("SELECT fingerprint,comment_id FROM write_requests WHERE actor=? AND request_key=?").bind(actor, key).first();
  if (!saved) return null;
  if (saved.fingerprint !== fingerprint) throw new HttpError(409, "请求编号已用于不同留言");
  const comment = await db.prepare(`SELECT ${COMMENT_COLUMNS} ${JOIN} WHERE c.id=?`).bind(saved.comment_id).first();
  if (!comment || comment.deletedAt != null) throw new HttpError(410, "该请求的留言已删除", {}, "IDEMPOTENT_RESULT_DELETED");
  if (comment.hiddenAt != null || (comment.rootId != null && !(await db.prepare("SELECT 1 FROM profile_comments WHERE id=? AND hidden_at IS NULL").bind(comment.rootId).first()))) {
    throw new HttpError(410, "该请求的留言已隐藏", {}, "IDEMPOTENT_RESULT_HIDDEN");
  }
  return json({ success: true, comment: absoluteComment(comment, origin) });
}

export async function postComment(request, env, url, reply = false) {
  const user = await wikiUser(request);
  const body = await readJson(request);
  const target = text(body.targetBid, "targetBid", 128, true);
  const content = text(body.content, "content", 200, true);
  const name = user ? null : text(body.authorName, "authorName", 20) || "访客";
  let replyToId = null;
  if (reply) {
    if (!Number.isSafeInteger(body.replyToId) || body.replyToId < 1) throw new HttpError(400, "缺少有效的回复目标");
    replyToId = body.replyToId;
  } else if (body.replyToId != null || body.rootId != null) {
    throw new HttpError(400, "请使用回复接口");
  }
  const guest = user ? null : await guestIdentity(request, env);
  const actor = user ? `wiki:${user.wikiUserId}` : guest.actor;
  const requestKey = request.headers.get("Idempotency-Key") || crypto.randomUUID();
  if (!/^[a-zA-Z0-9_-]{8,128}$/.test(requestKey)) throw new HttpError(400, "请求编号无效");
  const fingerprint = await sha256(JSON.stringify(reply ? [target, content, name, replyToId] : [target, content, name]));
  const previous = await replay(env.DB, actor, requestKey, fingerprint, url.origin);
  if (previous) return previous;
  await targetExists(env.DB, target);
  let rootId = null;
  if (reply) {
    const parent = await env.DB.prepare("SELECT id,root_id,target_bid,deleted_at,hidden_at FROM profile_comments WHERE id=?").bind(replyToId).first();
    if (!parent || parent.target_bid !== target) throw new HttpError(404, "回复目标不存在于该留言板", {}, "REPLY_TARGET_UNAVAILABLE");
    rootId = parent.root_id ?? parent.id;
    const root = await env.DB.prepare("SELECT target_bid,deleted_at,root_id,hidden_at FROM profile_comments WHERE id=?").bind(rootId).first();
    if (!root || root.root_id != null || root.target_bid !== target) throw new HttpError(404, "讨论不存在", {}, "DISCUSSION_UNAVAILABLE");
    if (parent.deleted_at != null || root.deleted_at != null) throw new HttpError(409, "留言已删除，不能继续回复", {}, "REPLY_TARGET_UNAVAILABLE");
    if (parent.hidden_at != null || root.hidden_at != null) throw new HttpError(404, "留言不可用", {}, "REPLY_TARGET_UNAVAILABLE");
  }
  const gate = writeGate(env.DB, user ? `wiki:${user.wikiUserId}` : guest.throttleKey, user ? 10 : 30);
  const results = await env.DB.batch([
    gate.claim,
    env.DB.prepare(`INSERT INTO profile_comments(target_bid,author_bid,author_wiki_user_id,author_name,author_tag,author_ip_hash,content,created_at,root_id,reply_to_id)
      SELECT ?,?,?,?,?,?,?,?,?,? WHERE ${gateCondition}
      AND NOT EXISTS(SELECT 1 FROM write_requests WHERE actor=? AND request_key=?)
      AND (?='__public__' OR EXISTS(SELECT 1 FROM user_profiles WHERE bid=?))
      ${reply ? `AND EXISTS(SELECT 1 FROM profile_comments parent JOIN profile_comments root ON root.id=?
        WHERE parent.id=? AND parent.target_bid=? AND root.target_bid=parent.target_bid
        AND root.root_id IS NULL AND COALESCE(parent.root_id,parent.id)=root.id
        AND parent.deleted_at IS NULL AND root.deleted_at IS NULL AND parent.hidden_at IS NULL AND root.hidden_at IS NULL)` : ""}`)
      .bind(target, user?.bid || "anon", user?.wikiUserId || null, name, guest?.tag || null, guest?.throttleKey || null, content, gate.now, rootId, replyToId,
        gate.key, gate.token, actor, requestKey, target, target, ...(reply ? [rootId, replyToId, target] : [])),
    env.DB.prepare(`INSERT INTO write_requests(actor,request_key,fingerprint,comment_id,created_at)
      SELECT ?,?,?,last_insert_rowid(),? WHERE changes()=1`).bind(actor, requestKey, fingerprint, gate.now),
    replyNotificationStatement(env.DB, actor, requestKey),
    env.DB.prepare(`DELETE FROM write_throttle WHERE key=? AND token=? AND NOT EXISTS(
      SELECT 1 FROM write_requests WHERE actor=? AND request_key=?)`).bind(gate.key, gate.token, actor, requestKey),
  ]);
  // A concurrent retry may have won: return its stored result before reporting 429.
  const response = await replay(env.DB, actor, requestKey, fingerprint, url.origin);
  if (response) return response;
  requireClaim(results[0], gate.seconds);
  throw new HttpError(409, "留言目标已变化，请刷新后重试", {}, "REPLY_TARGET_UNAVAILABLE");
}

export async function deleteComment(request, env, url, admin = false) {
  const user = admin ? null : await wikiUser(request, true);
  const id = integer(url.searchParams.get("id"));
  if (!id) throw new HttpError(400, "缺少留言 id");
  const owner = admin ? "" : "AND (author_wiki_user_id=? OR (author_wiki_user_id IS NULL AND author_bid=?))";
  const statement = env.DB.prepare(`UPDATE profile_comments SET content='',pinned_at=NULL,deleted_at=COALESCE(deleted_at,unixepoch())
    WHERE id=? AND deleted_at IS NULL ${owner}`)
    .bind(...(admin ? [id] : [id, user.wikiUserId, user.bid]));
  const statements = [statement];
  if (admin) statements.push(auditStatement(env.DB, "delete_comment", id));
  statements.push(env.DB.prepare(`SELECT deleted_at FROM profile_comments WHERE id=? ${owner}`)
    .bind(...(admin ? [id] : [id, user.wikiUserId, user.bid])));
  const results = await env.DB.batch(statements);
  const row = results.at(-1).results[0];
  if (!row) throw new HttpError(404, "留言不存在或无权删除");
  return json({ success: true, deleted: row.deleted_at != null, changed: results[0].meta.changes > 0 });
}
