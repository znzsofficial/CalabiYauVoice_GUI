import { HttpError, json, text, readJson, integer, absoluteProfile } from "./http.js";
import { wikiUser } from "./auth.js";
import { avatarPath, avatarKey, validateAvatar, retireAvatar } from "./avatars.js";
import { writeGate, gateCondition, requireClaim } from "./writes.js";
import { auditStatement } from "./moderation.js";
import { storedProfileBid } from "./identity.js";

const STORED_PROFILE_COLUMNS = "bid,wiki_user_id AS wikiUserId,custom_name AS customName,avatar_url AS avatarUrl,bio,badge,updated_at AS updatedAt";
export const PROFILE_COLUMNS = "COALESCE((SELECT current_bid FROM wiki_user_names n WHERE n.wiki_user_id=user_profiles.wiki_user_id),bid) AS bid,wiki_user_id AS wikiUserId,custom_name AS customName,avatar_url AS avatarUrl,bio,badge,updated_at AS updatedAt";

export async function getSession(request, env) {
  const user = await wikiUser(request, true, env.DB);
  return json({ user: { bid: user.bid, wikiUserId: user.wikiUserId } });
}

export async function getProfile(request, env, url) {
  const bid = text(url.searchParams.get("bid"), "bid", 128);
  const id = url.searchParams.get("wiki_id");
  if (!bid && !id) throw new HttpError(400, "缺少 bid 或 wiki_id");
  const profile = await env.DB.prepare(`SELECT ${PROFILE_COLUMNS} FROM user_profiles WHERE ${id ? "wiki_user_id" : "bid"}=?`)
    .bind(id ? integer(id) : (await storedProfileBid(env.DB, bid) ?? bid)).first();
  return json({ profile: absoluteProfile(profile, url.origin) });
}

export async function saveProfile(request, env, url, admin = false, ctx) {
  const user = admin ? null : await wikiUser(request, true, env.DB);
  const body = await readJson(request);
  if (user && body.expectedWikiUserId != null && body.expectedWikiUserId !== user.wikiUserId) {
    throw new HttpError(409, "登录账号已变化，请重新打开资料编辑", {}, "IDENTITY_CHANGED");
  }
  const requestedBid = user?.bid || text(body.bid, "bid", 128, true);
  const bid = await storedProfileBid(env.DB, requestedBid) ?? requestedBid;
  const old = await env.DB.prepare(`SELECT ${STORED_PROFILE_COLUMNS} FROM user_profiles WHERE bid=?`).bind(bid).first();
  if (admin && !old) throw new HttpError(404, "档案不存在");
  // User PUT remains a full replacement for released clients; admin PUT is a patch.
  const field = (name, max) => admin && !(name in body) ? old[name] : text(body[name], name, max);
  const name = field("customName", 30), bio = field("bio", 200), badge = field("badge", 20);
  const avatar = admin && !("avatarUrl" in body) ? old.avatarUrl : avatarPath(body.avatarUrl, url.origin);
  const normalizedAvatar = avatarPath(avatar, url.origin);
  // Validate unchanged references too: a concurrent clear/cleanup must not allow a
  // stale admin patch to resurrect an object already marked for deletion.
  const wikiUserId = user?.wikiUserId ?? old?.wikiUserId ?? null;
  if (normalizedAvatar) await validateAvatar(env, normalizedAvatar, bid, wikiUserId);
  const now = Math.floor(Date.now() / 1000);
  const key = avatarKey(normalizedAvatar);
  const gate = admin ? null : writeGate(env.DB, `uprof:wiki:${user.wikiUserId}`, 10);
  const assetCheck = key
    ? "EXISTS(SELECT 1 FROM avatar_assets WHERE object_key=? AND (owner_wiki_user_id=? OR (owner_wiki_user_id IS NULL AND owner_bid=?)) AND state='active')" : "1";
  const values = [bid, wikiUserId, name, normalizedAvatar, bio, badge, now];
  const conditionBindings = [];
  if (assetCheck !== "1") conditionBindings.push(key, wikiUserId, bid);
  if (gate) conditionBindings.push(gate.key, gate.token);
  const statement = env.DB.prepare(`INSERT INTO user_profiles(bid,wiki_user_id,custom_name,avatar_url,bio,badge,updated_at)
    SELECT ?,?,?,?,?,?,? WHERE ${assetCheck} ${gate ? `AND ${gateCondition}` : ""}
    ON CONFLICT(bid) DO UPDATE SET wiki_user_id=excluded.wiki_user_id,custom_name=excluded.custom_name,
      avatar_url=excluded.avatar_url,bio=excluded.bio,badge=excluded.badge,updated_at=excluded.updated_at
      WHERE user_profiles.wiki_user_id IS NULL OR user_profiles.wiki_user_id=excluded.wiki_user_id`)
    .bind(...values, ...conditionBindings);
  const statements = gate ? [gate.claim, statement] : [statement];
  if (admin) statements.push(auditStatement(env.DB, "edit_profile", bid));
  // Release a claimed window if the asset was concurrently retired and no write happened.
  if (gate) statements.push(env.DB.prepare("DELETE FROM write_throttle WHERE key=? AND token=? AND changes()=0").bind(gate.key, gate.token));
  const results = await env.DB.batch(statements);
  if (gate) requireClaim(results[0], 10);
  if (!results[gate ? 1 : 0].meta?.changes) throw new HttpError(409, "头像已过期，请重新上传");
  if (old?.avatarUrl && avatarPath(old.avatarUrl, url.origin) !== normalizedAvatar) {
    const cleanup = retireAvatar(env, avatarPath(old.avatarUrl, url.origin), bid, url.origin)
      .catch(error => console.error(JSON.stringify({ event: "avatar_cleanup_failed", error: String(error) })));
    if (ctx) ctx.waitUntil(cleanup); else await cleanup;
  }
  const profile = await env.DB.prepare(`SELECT ${PROFILE_COLUMNS} FROM user_profiles WHERE bid=?`).bind(bid).first();
  return json({ success: true, profile: absoluteProfile(profile, url.origin) });
}
