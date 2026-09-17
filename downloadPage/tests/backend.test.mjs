import assert from "node:assert/strict";
import { before, after, test } from "node:test";
import { readFile, readdir } from "node:fs/promises";
import { build } from "esbuild";
import { Miniflare } from "miniflare";
import { guestIdentity } from "../src/api/auth.js";

let mf, db, assets, releases;
const base = "https://example.test";
const secret = "integration-test-guest-secret-not-production";
const png = Uint8Array.from([137,80,78,71,13,10,26,10,0,0,0,0]);
const user = (name) => ({ "X-Wiki-Cookie": name });
const guest = (ip, id) => ({ "CF-Connecting-IP": ip, ...(id ? { "X-Guest-Id": id } : {}) });

async function call(path, method = "GET", body, headers = {}) {
  return mf.dispatchFetch(base + path, { method, headers: { ...(body ? { "Content-Type": "application/json" } : {}), ...headers }, body: body ? JSON.stringify(body) : undefined });
}
async function reset() {
  for (const table of ["write_requests", "profile_comments", "profile_likes", "user_profiles", "write_throttle", "avatar_assets"]) await db.prepare(`DELETE FROM ${table}`).run();
}
async function seed(bid) {
  await db.prepare("INSERT INTO user_profiles(bid,wiki_user_id) VALUES(?,1)").bind(bid).run();
}

before(async () => {
  const bundle = await build({ entryPoints: ["src/api/_worker.js"], bundle: true, format: "esm", write: false, platform: "browser" });
  mf = new Miniflare({
    modules: true, script: bundle.outputFiles[0].text, compatibilityDate: "2026-05-25",
    d1Databases: ["DB"], r2Buckets: ["USER_ASSETS", "RELEASES"],
    bindings: { ADMIN_PASSWORD: "test-admin", GUEST_SECRET: secret },
    outboundService: async (request) => {
      const cookie = request.headers.get("Cookie");
      if (cookie === "upstream-fails") return new Response("blocked", { status: 567 });
      if (cookie === "malformed") return new Response("<html>blocked</html>");
      return Response.json({ query: { userinfo: cookie === "expired" ? { id: 0, anon: true } : { id: 1, name: cookie } } });
    },
  });
  db = await mf.getD1Database("DB"); assets = await mf.getR2Bucket("USER_ASSETS"); releases = await mf.getR2Bucket("RELEASES");
  for (const file of (await readdir("migrations")).filter(f => f.endsWith(".sql")).sort()) {
    const sql = await readFile(`migrations/${file}`, "utf8");
    for (const stmt of sql.replace(/^--.*$/gm, "").split(";").map(s => s.trim()).filter(Boolean)) await db.prepare(stmt).run();
  }
});
after(async () => { await mf?.dispose(); });

test("credential failures never silently publish anonymous comments", async () => {
  await reset();
  for (const [cookie, status] of [["expired",401],["upstream-fails",503],["malformed",503]]) {
    const response = await call("/api/user/comments", "POST", { targetBid: "__public__", content: "hello" }, user(cookie));
    assert.equal(response.status, status, await response.text());
  }
  assert.equal((await db.prepare("SELECT COUNT(*) AS n FROM profile_comments").first()).n, 0);
});

test("atomic guest throttling and idempotent retries preserve a single row", async () => {
  await reset();
  const payload = { targetBid: "__public__", content: "hello" };
  const headers = { ...guest("203.0.113.10"), "Idempotency-Key": "request-00000001" };
  const responses = await Promise.all(Array.from({ length: 8 }, () => call("/api/user/comments", "POST", payload, headers)));
  for (const response of responses) assert.equal(response.status, 200, await response.text());
  assert.equal((await db.prepare("SELECT COUNT(*) AS n FROM profile_comments").first()).n, 1);
  assert.equal((await call("/api/user/comments", "POST", { ...payload, content: "changed" }, headers)).status, 409);
  const blocked = await call("/api/user/comments", "POST", payload, guest("203.0.113.10"));
  assert.equal(blocked.status, 429); assert.equal(blocked.headers.get("Retry-After"), "30");
  await reset();
  const distinct = await Promise.all(Array.from({ length: 8 }, (_, i) => call("/api/user/comments", "POST", { ...payload, content: `hello ${i}` }, guest("203.0.113.10"))));
  assert.equal(distinct.filter(r => r.status === 200).length, 1);
  assert.equal(distinct.filter(r => r.status === 429).length, 7);
});

test("failed business write rolls back the throttle claim", async () => {
  await reset();
  await db.prepare("CREATE TRIGGER reject_test_comment BEFORE INSERT ON profile_comments WHEN NEW.content='reject-me' BEGIN SELECT RAISE(ABORT,'test failure'); END").run();
  try {
    assert.equal((await call("/api/user/comments", "POST", { targetBid: "__public__", content: "reject-me" }, guest("203.0.113.11"))).status, 500);
    assert.equal((await db.prepare("SELECT COUNT(*) AS n FROM write_throttle").first()).n, 0);
    assert.equal((await call("/api/user/comments", "POST", { targetBid: "__public__", content: "works" }, guest("203.0.113.11"))).status, 200);
  } finally { await db.prepare("DROP TRIGGER reject_test_comment").run(); }
});

test("cursor pages neither repeat nor skip when new posts and deletions shift offsets", async () => {
  await reset();
  for (let i = 0; i < 6; i++) await db.prepare("INSERT INTO profile_comments(target_bid,author_bid,content,created_at) VALUES('__public__','anon',?,100)").bind(String(i)).run();
  const first = await (await call("/api/user/comments?bid=__public__&size=3")).json();
  await db.prepare("INSERT INTO profile_comments(target_bid,author_bid,content,created_at) VALUES('__public__','anon','new',101)").run();
  await db.prepare("DELETE FROM profile_comments WHERE id=?").bind(first.comments[0].id).run();
  const next = await (await call(`/api/user/comments?bid=__public__&size=3&before=${first.nextCursor}`)).json();
  assert.deepEqual(next.comments.map(c => c.content), ["2","1","0"]);
  assert.equal(next.hasMore, false);
});

test("avatar ownership rejects copying another user's URL and admin patch preserves omitted fields", async () => {
  await reset();
  const upload = await mf.dispatchFetch(base + "/api/user/avatar", { method: "POST", headers: { ...user("Alice"), "Content-Type": "image/png" }, body: png });
  assert.equal(upload.status, 200);
  const { avatarUrl, objectKey } = await upload.json();
  assert.equal((await call("/api/user/profile", "PUT", { customName: "Alice", avatarUrl, badge: "badge", bio: "old" }, user("Alice"))).status, 200);
  assert.equal((await call("/api/user/profile", "PUT", { avatarUrl }, user("Bob"))).status, 403);
  assert.ok(await assets.head(objectKey));
  const response = await call("/api/admin/profile", "PUT", { bid: "Alice", bio: "new" }, { "X-Admin-Password": "test-admin" });
  assert.equal(response.status, 200);
  const profile = (await response.json()).profile;
  assert.equal(profile.customName, "Alice"); assert.equal(profile.badge, "badge"); assert.equal(profile.avatarUrl, base + avatarUrl);
  assert.equal((await call("/api/admin/profile", "PUT", { bid: "Alice", bio: null }, { "X-Admin-Password": "test-admin" })).status, 200);
  assert.equal((await db.prepare("SELECT bio FROM user_profiles WHERE bid='Alice'").first()).bio, null);
});

test("legacy ownership uses exact BID and failed avatar save does not consume window", async () => {
  await reset();
  const key = "avatars/Alice_extra_0123456789abcdef.png";
  await assets.put(key, png);
  assert.equal((await call("/api/user/profile", "PUT", { avatarUrl: `/api/user/avatar/${key}` }, user("Alice"))).status, 403);
  assert.equal((await call("/api/user/profile", "PUT", { customName: "Alice" }, user("Alice"))).status, 200);
  assert.ok(await assets.head(key));
});

test("oversized JSON and streamed image bodies return 413, malformed fields return 400", async () => {
  assert.equal((await call("/api/user/profile", "PUT", { bio: "x".repeat(17000) }, user("Alice"))).status, 413);
  assert.equal((await call("/api/user/profile", "PUT", { bio: 123 }, user("Alice"))).status, 400);
  const stream = new ReadableStream({ start(controller) { for (let i = 0; i < 34; i++) controller.enqueue(new Uint8Array(65536)); controller.close(); } });
  const response = await mf.dispatchFetch(base + "/api/user/avatar", { method: "POST", headers: { ...user("Alice"), "Content-Type": "image/png" }, body: stream, duplex: "half" });
  assert.equal(response.status, 413);
});

test("guest UUID separates shared networks; changing IP retains the display tag", async () => {
  await reset();
  const payload = { targetBid: "__public__", content: "hello" };
  const a = "00000000-0000-4000-8000-000000000001", b = "00000000-0000-4000-8000-000000000002";
  const first = await (await call("/api/user/comments", "POST", payload, guest("203.0.113.20", a))).json();
  await db.prepare("DELETE FROM write_throttle").run();
  const second = await (await call("/api/user/comments", "POST", payload, guest("203.0.113.20", b))).json();
  const moved = await (await call("/api/user/comments", "POST", payload, guest("203.0.113.21", a))).json();
  assert.notEqual(first.comment.authorTag, second.comment.authorTag);
  assert.equal(first.comment.authorTag, moved.comment.authorTag);
  assert.match(first.comment.authorTag, /^[A-F0-9]{10}$/);
});

test("like PUT and DELETE are idempotent under concurrent retries; legacy toggle remains available", async () => {
  await reset(); await seed("Alice");
  for (const [method, expected] of [["PUT",1],["PUT",1],["DELETE",0],["DELETE",0]]) {
    await Promise.all(Array.from({ length: 5 }, async () => {
      const response = await call("/api/user/likes", method, { targetBid: "Alice" }, user("Bob"));
      assert.equal(response.status, 200);
      assert.equal((await response.json()).count, expected);
    }));
  }
  assert.equal((await (await call("/api/user/likes", "POST", { targetBid: "Alice" }, user("Bob"))).json()).liked, true);
  assert.equal((await (await call("/api/user/likes", "POST", { targetBid: "Alice" }, user("Bob"))).json()).liked, false);
});

test("APK responses implement full, HEAD, range, suffix, If-Range and unsatisfiable range", async () => {
  await releases.put("android/CalabiYauVoice-2.1.10.apk", new TextEncoder().encode("0123456789"));
  const path = "/downloads/CalabiYauVoice-2.1.10.apk";
  const full = await call(path); assert.equal(full.status, 200); assert.equal(await full.text(), "0123456789");
  const head = await call(path, "HEAD"); assert.equal(head.headers.get("Content-Length"), "10"); assert.equal(await head.text(), "");
  for (const [range, body, contentRange] of [["bytes=2-5","2345","bytes 2-5/10"],["bytes=7-","789","bytes 7-9/10"],["bytes=-2","89","bytes 8-9/10"]]) {
    const response = await call(path, "GET", null, { Range: range });
    assert.equal(response.status, 206); assert.equal(response.headers.get("Content-Range"), contentRange); assert.equal(await response.text(), body);
  }
  const invalid = await call(path, "GET", null, { Range: "bytes=10-" }); assert.equal(invalid.status, 416); assert.equal(invalid.headers.get("Content-Range"), "bytes */10");
  const changed = await call(path, "GET", null, { Range: "bytes=2-", "If-Range": '"old"' }); assert.equal(changed.status, 200); assert.equal(await changed.text(), "0123456789");
  assert.equal((await call(path, "GET", null, { "If-None-Match": head.headers.get("ETag") })).status, 304);
});

test("maintenance collects aged unreferenced avatars, keeps live references and expires request metadata", async () => {
  await reset();
  for (const key of ["avatars/orphan.png", "avatars/live.png"]) {
    await assets.put(key, png);
    await db.prepare("INSERT INTO avatar_assets(object_key,owner_bid,created_at) VALUES(?,'Alice',0)").bind(key).run();
  }
  await db.prepare("INSERT INTO user_profiles(bid,avatar_url) VALUES('Alice','/api/user/avatar/avatars/live.png')").run();
  assert.equal((await call("/api/admin/maintenance", "POST", null, { "X-Admin-Password": "test-admin" })).status, 200);
  assert.equal(await assets.head("avatars/orphan.png"), null); assert.ok(await assets.head("avatars/live.png"));
});

test("guest service fails closed without an independent secret", async () => {
  const request = new Request(base, { headers: guest("203.0.113.30") });
  await assert.rejects(() => guestIdentity(request, { ADMIN_PASSWORD: "not-a-guest-secret" }), error => error.status === 503);
});

test("profile concurrency permits only one successful write in its window", async () => {
  await reset();
  const responses = await Promise.all(Array.from({ length: 6 }, (_, i) => call("/api/user/profile", "PUT", { bio: `bio ${i}` }, user("Alice"))));
  assert.equal(responses.filter(r => r.status === 200).length, 1);
  assert.equal(responses.filter(r => r.status === 429).length, 5);
});

test("an old cross-owner reference never causes deletion of the owner's avatar", async () => {
  await reset();
  const key = "avatars/Bob_0123456789abcdef.png";
  await assets.put(key, png);
  await db.prepare("INSERT INTO user_profiles(bid,avatar_url) VALUES('Alice',?)").bind(`/api/user/avatar/${key}`).run();
  const response = await call("/api/admin/profile", "PUT", { bid: "Alice", avatarUrl: null }, { "X-Admin-Password": "test-admin" });
  assert.equal(response.status, 200);
  assert.ok(await assets.head(key));
});

test("SQL failure during profile update rolls back gate and retains original data", async () => {
  await reset(); await seed("Alice");
  await db.prepare("CREATE TRIGGER reject_test_profile BEFORE UPDATE ON user_profiles BEGIN SELECT RAISE(ABORT,'test profile failure'); END").run();
  try {
    assert.equal((await call("/api/user/profile", "PUT", { bio: "no" }, user("Alice"))).status, 500);
    assert.equal((await db.prepare("SELECT COUNT(*) AS n FROM write_throttle").first()).n, 0);
    assert.equal((await db.prepare("SELECT bio FROM user_profiles WHERE bid='Alice'").first()).bio, null);
  } finally { await db.prepare("DROP TRIGGER reject_test_profile").run(); }
});

test("admin search treats percent literally and unknown API never falls through to HTML", async () => {
  await reset();
  for (const content of ["100%", "1000"]) await db.prepare("INSERT INTO profile_comments(target_bid,author_bid,content) VALUES('__public__','anon',?)").bind(content).run();
  const response = await call("/api/admin/comments?q=%25", "GET", null, { "X-Admin-Password": "test-admin" });
  assert.equal(response.status, 200); assert.equal((await response.json()).total, 1);
  assert.equal((await call("/api/missing")).status, 404);
  assert.equal((await call("/api/admin/comments")).status, 401);
});

test("retired avatar cannot be reattached and deleted idempotent comment cannot be recreated", async () => {
  await reset();
  const key = "avatars/retired.png";
  await assets.put(key, png);
  await db.prepare("INSERT INTO avatar_assets(object_key,owner_bid,created_at,state) VALUES(?,'Alice',0,'deleting')").bind(key).run();
  assert.equal((await call("/api/user/profile", "PUT", { avatarUrl: `/api/user/avatar/${key}` }, user("Alice"))).status, 409);
  assert.equal((await db.prepare("SELECT COUNT(*) AS n FROM write_throttle").first()).n, 0);
  const payload = { targetBid: "__public__", content: "once" };
  const headers = { ...guest("203.0.113.31"), "Idempotency-Key": "deleted-request-01" };
  const post = await (await call("/api/user/comments", "POST", payload, headers)).json();
  await db.prepare("DELETE FROM profile_comments WHERE id=?").bind(post.comment.id).run();
  assert.equal((await call("/api/user/comments", "POST", payload, headers)).status, 410);
  assert.equal((await db.prepare("SELECT COUNT(*) AS n FROM profile_comments").first()).n, 0);
});
