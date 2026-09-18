import { HttpError, integer, json, readJson } from "./http.js";
import { wikiUser } from "./auth.js";

// Created in the SAME transaction as the reply and its idempotency record.
// Only the immediately preceding successful request-record INSERT may notify.
export function replyNotificationStatement(db, actor, requestKey) {
  return db.prepare(`INSERT INTO reply_notifications(recipient_bid,recipient_wiki_user_id,comment_id,root_id,created_at)
    SELECT parent.author_bid,parent.author_wiki_user_id,c.id,c.root_id,c.created_at
    FROM write_requests w JOIN profile_comments c ON c.id=w.comment_id
    JOIN profile_comments parent ON parent.id=c.reply_to_id
    WHERE changes()=1 AND w.actor=? AND w.request_key=? AND c.root_id IS NOT NULL
      AND parent.author_wiki_user_id IS NOT NULL
      AND (c.author_wiki_user_id IS NULL OR parent.author_wiki_user_id<>c.author_wiki_user_id)
    ON CONFLICT(recipient_bid,comment_id) DO NOTHING`).bind(actor, requestKey);
}

export async function notifications(request, env, url) {
  const user = await wikiUser(request, true);
  if (request.method === "PUT") {
    const body = await readJson(request);
    const id = body.id;
    const through = body.throughId;
    if ((id == null) === (through == null)) throw new HttpError(400, "请提供 id 或 throughId");
    const value = id ?? through;
    if (!Number.isSafeInteger(value) || value < 1) throw new HttpError(400, "通知编号无效");
    const [changed, count] = await env.DB.batch([
      env.DB.prepare(`UPDATE reply_notifications SET read_at=unixepoch()
        WHERE (recipient_wiki_user_id=? OR (recipient_wiki_user_id IS NULL AND recipient_bid=?))
        AND read_at IS NULL AND id ${id != null ? "=" : "<="} ?`).bind(user.wikiUserId, user.bid, value),
      env.DB.prepare(`SELECT COUNT(*) AS count FROM reply_notifications
        WHERE (recipient_wiki_user_id=? OR (recipient_wiki_user_id IS NULL AND recipient_bid=?)) AND read_at IS NULL`).bind(user.wikiUserId, user.bid),
    ]);
    // A foreign/nonexistent ID is an idempotent no-op: no existence information leaks.
    return json({ success: true, changed: changed.meta.changes, unreadCount: count.results[0].count });
  }
  const size = integer(url.searchParams.get("size"), 20, 50);
  const before = integer(url.searchParams.get("before"), null);
  const [unread, latest, rows] = await env.DB.batch([
    env.DB.prepare(`SELECT COUNT(*) AS unreadCount FROM reply_notifications
      WHERE (recipient_wiki_user_id=? OR (recipient_wiki_user_id IS NULL AND recipient_bid=?)) AND read_at IS NULL`).bind(user.wikiUserId, user.bid),
    env.DB.prepare(`SELECT MAX(id) AS latestId FROM reply_notifications
      WHERE recipient_wiki_user_id=? OR (recipient_wiki_user_id IS NULL AND recipient_bid=?)`).bind(user.wikiUserId, user.bid),
    env.DB.prepare(`SELECT n.id,n.root_id AS rootId,n.comment_id AS commentId,n.created_at AS createdAt,n.read_at AS readAt,
      CASE WHEN c.id IS NULL OR root.id IS NULL THEN 'unavailable'
        WHEN c.hidden_at IS NOT NULL OR root.hidden_at IS NOT NULL OR parent.hidden_at IS NOT NULL THEN 'hidden'
        WHEN c.deleted_at IS NOT NULL OR root.deleted_at IS NOT NULL OR parent.deleted_at IS NOT NULL THEN 'deleted' ELSE 'available' END AS status,
      c.content,c.author_bid AS authorBid,c.author_tag AS authorTag,
      COALESCE(NULLIF((SELECT up.custom_name FROM user_profiles up WHERE up.wiki_user_id=c.author_wiki_user_id
        ORDER BY up.updated_at DESC LIMIT 1),''),c.author_name,c.author_bid) AS authorName,
      (SELECT up.avatar_url FROM user_profiles up WHERE up.wiki_user_id=c.author_wiki_user_id
        ORDER BY up.updated_at DESC LIMIT 1) AS authorAvatarUrl
      FROM reply_notifications n LEFT JOIN profile_comments c ON c.id=n.comment_id
      LEFT JOIN profile_comments root ON root.id=n.root_id
      LEFT JOIN profile_comments parent ON parent.id=c.reply_to_id
       WHERE (n.recipient_wiki_user_id=? OR (n.recipient_wiki_user_id IS NULL AND n.recipient_bid=?))
       ${before ? "AND n.id<?" : ""} ORDER BY n.id DESC LIMIT ?`)
      .bind(user.wikiUserId, user.bid, ...(before ? [before] : []), size + 1),
  ]);
  const hasMore = rows.results.length > size;
  const items = rows.results.slice(0, size).map(row => {
    const available = row.status === "available";
    return { id: row.id, rootId: row.rootId, commentId: row.commentId, createdAt: row.createdAt,
      read: row.readAt != null, status: row.status,
      content: available ? row.content : null,
      authorBid: available ? row.authorBid : null,
      authorName: available ? row.authorName : null,
      authorTag: available ? row.authorTag : null,
      authorAvatarUrl: available ? (row.authorAvatarUrl?.startsWith("/") ? url.origin + row.authorAvatarUrl : row.authorAvatarUrl) : null };
  });
  return json({ ...unread.results[0], ...latest.results[0], notifications: items, nextCursor: hasMore ? String(items.at(-1).id) : null });
}
