import { HttpError, json, readJson, integer, text } from "./http.js";

// Shared-password authentication identifies a credential role, not an individual person.
// Record only action metadata: never original content, cookies or credential values.
export function auditStatement(db, action, target, reason = "") {
  return db.prepare(`INSERT INTO admin_audit(actor,action,target,reason)
    SELECT 'admin',?,?,? WHERE changes()>0`).bind(action, String(target), reason);
}

export async function moderate(request, env) {
  const body = await readJson(request);
  const id = integer(String(body.id));
  const action = body.action;
  const reason = text(body.reason, "reason", 200) || "";
  const updates = {
    hide: "hidden_at=unixepoch(),pinned_at=NULL",
    restore: "hidden_at=NULL",
    pin: "pinned_at=unixepoch()",
    unpin: "pinned_at=NULL",
  };
  if (!Object.hasOwn(updates, action)) throw new HttpError(400, "管理动作无效");
  const conditions = {
    hide: "hidden_at IS NULL",
    restore: "hidden_at IS NOT NULL",
    pin: `root_id IS NULL AND hidden_at IS NULL AND pinned_at IS NULL
      AND (SELECT COUNT(*) FROM profile_comments p WHERE p.target_bid=profile_comments.target_bid
        AND p.pinned_at IS NOT NULL AND p.hidden_at IS NULL AND p.deleted_at IS NULL)<3`,
    unpin: "root_id IS NULL AND pinned_at IS NOT NULL",
  };
  // Visibility of a deleted root still controls its surviving discussion.
  // Restore changes visibility ONLY; it never restores deleted content.
  const visibilityAction = action === "hide" || action === "restore";
  const results = await env.DB.batch([
    env.DB.prepare(`UPDATE profile_comments SET ${updates[action]} WHERE id=?
      AND (deleted_at IS NULL ${visibilityAction ? "OR root_id IS NULL" : ""}) AND ${conditions[action]}`).bind(id),
    auditStatement(env.DB, action, id, reason),
    env.DB.prepare("SELECT id,root_id,hidden_at,pinned_at,deleted_at FROM profile_comments WHERE id=?").bind(id),
  ]);
  const row = results[2].results[0];
  if (!row) throw new HttpError(404, "留言不存在");
  if (row.deleted_at != null && !(visibilityAction && row.root_id == null)) throw new HttpError(409, "已删除内容不能恢复或置顶");
  if (["pin", "unpin"].includes(action) && row.root_id != null) throw new HttpError(400, "只能置顶主留言");
  if (action === "pin" && row.pinned_at == null) throw new HttpError(409, "请先恢复留言，且每个留言板最多置顶 3 条");
  return json({ success: true, changed: results[0].meta.changes > 0 });
}

export async function auditList(env, url) {
  const before = integer(url.searchParams.get("before"), null);
  const size = 20;
  const [count, rows] = await env.DB.batch([
    env.DB.prepare("SELECT COUNT(*) AS count FROM admin_audit"),
    env.DB.prepare(`SELECT * FROM admin_audit ${before ? "WHERE id<?" : ""} ORDER BY id DESC LIMIT ?`)
      .bind(...(before ? [before] : []), size + 1),
  ]);
  const records = rows.results.slice(0, size);
  return json({ total: count.results[0].count, size, records,
    nextCursor: rows.results.length > size ? String(records.at(-1).id) : null });
}
