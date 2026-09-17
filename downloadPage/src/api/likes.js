import { json, text, readJson } from "./http.js";
import { wikiUser } from "./auth.js";
import { targetExists } from "./comments.js";

export async function likes(request, env, url) {
  const read = request.method === "GET";
  const user = await wikiUser(request, !read);
  const body = read ? null : await readJson(request);
  const bid = text(read ? url.searchParams.get("bid") : body.targetBid, "bid", 128, true);
  const statements = [];
  if (!read) {
    await targetExists(env.DB, bid);
    if (request.method === "POST") {
      // Compatibility for 2.1.10: legacy toggle in a single SQL statement.
      // A temporary marker lets a transaction flip both directions atomically.
      statements.push(env.DB.prepare(`INSERT INTO profile_likes(target_bid,author_bid,created_at) VALUES(?,?,unixepoch())
        ON CONFLICT(target_bid,author_bid) DO UPDATE SET created_at=-1`).bind(bid, user.bid));
      statements.push(env.DB.prepare("DELETE FROM profile_likes WHERE target_bid=? AND author_bid=? AND created_at=-1").bind(bid, user.bid));
    } else if (request.method === "PUT") {
      statements.push(env.DB.prepare("INSERT INTO profile_likes(target_bid,author_bid,created_at) VALUES(?,?,unixepoch()) ON CONFLICT DO NOTHING").bind(bid, user.bid));
    } else {
      statements.push(env.DB.prepare("DELETE FROM profile_likes WHERE target_bid=? AND author_bid=?").bind(bid, user.bid));
    }
  }
  statements.push(env.DB.prepare(`SELECT COUNT(*) AS count,
    EXISTS(SELECT 1 FROM profile_likes WHERE target_bid=? AND author_bid=?) AS liked
    FROM profile_likes WHERE target_bid=?`).bind(bid, user?.bid || "", bid));
  const results = await env.DB.batch(statements);
  const row = results.at(-1).results[0];
  return json(read ? { count: row.count, likedByMe: !!row.liked } : { success: true, count: row.count, liked: !!row.liked });
}
