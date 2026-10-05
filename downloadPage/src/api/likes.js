import { json, text, readJson } from "./http.js";
import { wikiUser } from "./auth.js";
import { targetExists } from "./comments.js";
import { storedProfileBid } from "./identity.js";

export async function likes(request, env, url) {
  const read = request.method === "GET";
  const user = await wikiUser(request, !read, env.DB);
  const body = read ? null : await readJson(request);
  const requestedBid = text(read ? url.searchParams.get("bid") : body.targetBid, "bid", 128, true);
  const bid = read ? (await storedProfileBid(env.DB, requestedBid) ?? requestedBid) : await targetExists(env.DB, requestedBid);
  const owner = "(author_wiki_user_id=? OR (author_wiki_user_id IS NULL AND author_bid IN (SELECT bid FROM wiki_user_aliases WHERE wiki_user_id=?)))";
  const ownerBindings = [user?.wikiUserId ?? -1, user?.wikiUserId ?? -1];
  const statements = [];
  if (!read) {
    if (request.method === "POST") {
      // Compatibility for 2.1.10: toggle atomically. Only delete if INSERT
      // found an existing like; an inserted row must survive this batch.
      statements.push(env.DB.prepare(`INSERT INTO profile_likes(target_bid,author_bid,author_wiki_user_id,created_at)
        SELECT ?,?,?,unixepoch() WHERE NOT EXISTS(SELECT 1 FROM profile_likes WHERE target_bid=? AND ${owner})`)
        .bind(bid, user.bid, user.wikiUserId, bid, ...ownerBindings));
      statements.push(env.DB.prepare(`DELETE FROM profile_likes WHERE target_bid=? AND ${owner} AND changes()=0`).bind(bid, ...ownerBindings));
    } else if (request.method === "PUT") {
      statements.push(env.DB.prepare(`INSERT INTO profile_likes(target_bid,author_bid,author_wiki_user_id,created_at)
        SELECT ?,?,?,unixepoch() WHERE NOT EXISTS(SELECT 1 FROM profile_likes WHERE target_bid=? AND ${owner})`)
        .bind(bid, user.bid, user.wikiUserId, bid, ...ownerBindings));
    } else {
      statements.push(env.DB.prepare(`DELETE FROM profile_likes WHERE target_bid=? AND ${owner}`).bind(bid, ...ownerBindings));
    }
  }
  statements.push(env.DB.prepare(`SELECT COUNT(DISTINCT COALESCE(CAST(author_wiki_user_id AS TEXT),
    'bid:' || author_bid)) AS count, EXISTS(SELECT 1 FROM profile_likes WHERE target_bid=? AND ${owner}) AS liked
    FROM profile_likes WHERE target_bid=?`).bind(bid, ...ownerBindings, bid));
  const results = await env.DB.batch(statements);
  const row = results.at(-1).results[0];
  return json(read ? { count: row.count, likedByMe: !!row.liked } : { success: true, count: row.count, liked: !!row.liked });
}
