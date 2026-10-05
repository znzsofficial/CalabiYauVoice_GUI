import { HttpError } from "./http.js";

/** Only call with identity verified by MediaWiki, never with a client-supplied ID. */
export async function recordWikiIdentity(db, user) {
  const collision = await db.prepare(`SELECT wiki_user_id FROM wiki_user_aliases WHERE bid=?
    UNION ALL SELECT wiki_user_id FROM user_profiles WHERE bid=? AND wiki_user_id IS NOT NULL`)
    .bind(user.bid, user.bid).all();
  if (collision.results.some(row => row.wiki_user_id !== user.wikiUserId)) {
    throw new HttpError(409, "账号名称与历史档案冲突，请联系管理员核对", {}, "IDENTITY_CONFLICT");
  }
  const results = await db.batch([
    db.prepare("INSERT INTO wiki_user_aliases(bid,wiki_user_id) VALUES(?,?) ON CONFLICT DO NOTHING").bind(user.bid, user.wikiUserId),
    db.prepare(`INSERT INTO wiki_user_names(wiki_user_id,current_bid) SELECT ?,? WHERE EXISTS(
      SELECT 1 FROM wiki_user_aliases WHERE bid=? AND wiki_user_id=?)
      ON CONFLICT(wiki_user_id) DO UPDATE SET current_bid=excluded.current_bid`).bind(user.wikiUserId, user.bid, user.bid, user.wikiUserId),
    db.prepare(`UPDATE user_profiles SET wiki_user_id=? WHERE bid=? AND wiki_user_id IS NULL
      AND EXISTS(SELECT 1 FROM wiki_user_aliases WHERE bid=? AND wiki_user_id=?)
      AND NOT EXISTS(SELECT 1 FROM user_profiles WHERE wiki_user_id=?)`)
      .bind(user.wikiUserId, user.bid, user.bid, user.wikiUserId, user.wikiUserId),
    // Legacy anonymous sentinels must NEVER be claimed by a real user named anon.
    db.prepare(`UPDATE profile_comments SET author_wiki_user_id=? WHERE author_wiki_user_id IS NULL
      AND author_bid<>'anon' AND author_bid IN (SELECT bid FROM wiki_user_aliases WHERE wiki_user_id=?)`)
      .bind(user.wikiUserId, user.wikiUserId),
    db.prepare(`UPDATE reply_notifications SET recipient_wiki_user_id=? WHERE recipient_wiki_user_id IS NULL
      AND recipient_bid<>'anon' AND recipient_bid IN (SELECT bid FROM wiki_user_aliases WHERE wiki_user_id=?)`)
      .bind(user.wikiUserId, user.wikiUserId),
    db.prepare(`UPDATE profile_likes SET author_wiki_user_id=? WHERE author_wiki_user_id IS NULL
      AND author_bid IN (SELECT bid FROM wiki_user_aliases WHERE wiki_user_id=?)`).bind(user.wikiUserId, user.wikiUserId),
    db.prepare("SELECT wiki_user_id FROM wiki_user_aliases WHERE bid=?").bind(user.bid),
  ]);
  if (results.at(-1).results[0]?.wiki_user_id !== user.wikiUserId) {
    throw new HttpError(409, "账号名称与历史档案冲突，请联系管理员核对", {}, "IDENTITY_CONFLICT");
  }
}

export async function storedProfileBid(db, bid) {
  const row = await db.prepare(`SELECT bid FROM user_profiles WHERE
    wiki_user_id=(SELECT wiki_user_id FROM wiki_user_aliases WHERE bid=?) OR bid=?
    ORDER BY CASE WHEN wiki_user_id=(SELECT wiki_user_id FROM wiki_user_aliases WHERE bid=?) THEN 0 ELSE 1 END LIMIT 1`)
    .bind(bid, bid, bid).first();
  return row?.bid ?? null;
}
