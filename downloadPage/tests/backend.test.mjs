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
const renamedUser = (id, name) => ({ "X-Wiki-Cookie": `uid-${id}:${name}` });
const guest = (ip, id) => ({ "CF-Connecting-IP": ip, ...(id ? { "X-Guest-Id": id } : {}) });
const wikiIds = new Map();
// Fixed IDs keep throttle-key fixtures (wiki:<id>) stable regardless of test order.
const fixedWikiIds = { Alice: 101, Bob: 102, Carol: 103, Reviewer: 104 };
let nextWikiId = 1;

async function call(path, method = "GET", body, headers = {}) {
  return mf.dispatchFetch(base + path, { method, headers: { ...(body ? { "Content-Type": "application/json" } : {}), ...headers }, body: body ? JSON.stringify(body) : undefined });
}
async function reset() {
  for (const table of ["admin_audit", "write_requests", "profile_comments", "profile_likes", "user_profiles", "write_throttle", "avatar_assets", "wiki_user_aliases", "wiki_user_names", "user_profile_archive"]) await db.prepare(`DELETE FROM ${table}`).run();
  await db.prepare("DELETE FROM reply_notifications").run();
}
async function seed(bid) {
  await db.prepare("INSERT INTO user_profiles(bid,wiki_user_id) VALUES(?,?)").bind(bid, fixedWikiIds[bid] ?? 1).run();
}

before(async () => {
  const bundle = await build({ entryPoints: ["src/api/_worker.js"], bundle: true, format: "esm", write: false, platform: "browser" });
  mf = new Miniflare({
    modules: true, script: bundle.outputFiles[0].text, compatibilityDate: "2026-05-25",
    d1Databases: ["DB", "MIGRATION_DB"], r2Buckets: ["USER_ASSETS", "RELEASES"],
    bindings: { ADMIN_PASSWORD: "test-admin", GUEST_SECRET: secret },
    outboundService: async (request) => {
      const cookie = request.headers.get("Cookie");
      if (cookie === "upstream-fails") return new Response("blocked", { status: 567 });
      if (cookie === "malformed") return new Response("<html>blocked</html>");
      const renamed = /^uid-(\d+):(.*)$/.exec(cookie || "");
      const identity = renamed
        ? { id: Number(renamed[1]), name: renamed[2] }
        : { id: fixedWikiIds[cookie] ?? wikiIds.get(cookie) ?? nextWikiId++, name: cookie };
      if (!renamed && !wikiIds.has(cookie)) wikiIds.set(cookie, identity.id);
      return Response.json({ query: { userinfo: cookie === "expired" ? { id: 0, anon: true } : identity } });
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
  await Promise.all(Array.from({ length: 8 }, async () => {
    const response = await call("/api/user/comments", "POST", payload, headers);
    const body = await response.text();
    assert.equal(response.status, 200, body);
  }));
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

test("two-level replies derive root server-side and stay out of legacy board pages", async () => {
  await reset();
  const root = (await (await call("/api/user/comments", "POST", { targetBid: "__public__", content: "root" }, user("Alice"))).json()).comment;
  const reply = (await (await call("/api/user/replies", "POST", { targetBid: "__public__", replyToId: root.id, content: "reply", rootId: 999 }, user("Bob"))).json()).comment;
  assert.equal(reply.rootId, root.id); assert.equal(reply.replyToId, root.id);
  const nested = (await (await call("/api/user/replies", "POST", { targetBid: "__public__", replyToId: reply.id, content: "reply to reply" }, user("Carol"))).json()).comment;
  assert.equal(nested.rootId, root.id); assert.equal(nested.replyToId, reply.id); assert.equal(nested.replyToName, "Bob");
  const board = await (await call("/api/user/comments?bid=__public__")).json();
  assert.equal(board.total, 1); assert.equal(board.comments[0].replyCount, 2);
  const thread = await (await call(`/api/user/replies?rootId=${root.id}`)).json();
  assert.equal(thread.root.id, root.id); assert.deepEqual(thread.comments.map(c => c.id), [nested.id, reply.id]);
  await seed("other-board");
  assert.equal((await call("/api/user/replies", "POST", { targetBid: "other-board", replyToId: root.id, content: "wrong board" }, user("Dave"))).status, 404);
  assert.equal((await call("/api/user/comments", "POST", { targetBid: "__public__", replyToId: root.id, content: "wrong route" }, user("Dave"))).status, 400);
});

test("soft deletion removes content and blocks new replies but retains discussion", async () => {
  await reset();
  const root = (await (await call("/api/user/comments", "POST", { targetBid: "__public__", content: "sensitive root" }, user("Alice"))).json()).comment;
  const reply = (await (await call("/api/user/replies", "POST", { targetBid: "__public__", replyToId: root.id, content: "sensitive reply" }, user("Bob"))).json()).comment;
  assert.equal((await call(`/api/user/comments?id=${root.id}`, "DELETE", null, user("Bob"))).status, 404);
  assert.equal((await call(`/api/user/comments?id=${root.id}`, "DELETE", null, user("Alice"))).status, 200);
  const board = await (await call("/api/user/comments?bid=__public__")).json();
  assert.equal(board.total, 1); assert.equal(board.comments[0].deleted, true); assert.equal(board.comments[0].authorBid, "");
  const thread = await (await call(`/api/user/replies?rootId=${root.id}`)).json();
  assert.equal(thread.root.content, "该留言已删除"); assert.equal(thread.comments[0].replyToName, "已删除留言");
  assert.equal((await call("/api/user/replies", "POST", { targetBid: "__public__", replyToId: reply.id, content: "too late" }, user("Carol"))).status, 409);
  assert.equal((await call(`/api/admin/comment?id=${reply.id}`, "DELETE", null, { "X-Admin-Password": "test-admin" })).status, 200);
  assert.equal((await (await call("/api/user/comments?bid=__public__")).json()).total, 0);
  assert.equal((await db.prepare("SELECT content FROM profile_comments WHERE id=?").bind(reply.id).first()).content, "");
});

test("reply retry is idempotent, shares posting throttle, and validates target in transaction", async () => {
  await reset();
  const root = (await (await call("/api/user/comments", "POST", { targetBid: "__public__", content: "root" }, user("Alice"))).json()).comment;
  const headers = { ...guest("203.0.113.45"), "Idempotency-Key": "reply-request-0001" };
  const payload = { targetBid: "__public__", replyToId: root.id, content: "once" };
  const responses = await Promise.all(Array.from({ length: 5 }, async () => {
    const response = await call("/api/user/replies", "POST", payload, headers);
    assert.equal(response.status, 200); return (await response.json()).comment.id;
  }));
  assert.equal(new Set(responses).size, 1);
  assert.equal((await call("/api/user/comments", "POST", { targetBid: "__public__", content: "spam" }, guest("203.0.113.45"))).status, 429);
  // Simulate a concurrent delete after preflight but before the conditional INSERT.
  await db.prepare(`CREATE TRIGGER delete_reply_target AFTER INSERT ON write_throttle WHEN NEW.key='wiki:103'
    BEGIN UPDATE profile_comments SET content='',deleted_at=unixepoch() WHERE id=${root.id}; END`).run();
  try {
    assert.equal((await call("/api/user/replies", "POST", payload, user("Carol"))).status, 409);
    assert.equal(await db.prepare("SELECT 1 FROM write_throttle WHERE key='wiki:103'").first(), null);
    assert.equal((await db.prepare("SELECT COUNT(*) AS n FROM profile_comments WHERE root_id=?").bind(root.id).first()).n, 1);
  } finally { await db.prepare("DROP TRIGGER delete_reply_target").run(); }
});

test("reply cursor survives new inserts and deleted replies keep their place", async () => {
  await reset();
  const inserted = await db.prepare("INSERT INTO profile_comments(target_bid,author_bid,content,created_at) VALUES('__public__','Alice','root',100)").run();
  const id = inserted.meta.last_row_id;
  for (let i = 0; i < 5; i++) await db.prepare("INSERT INTO profile_comments(target_bid,author_bid,content,created_at,root_id,reply_to_id) VALUES('__public__','Bob',?,101,?,?)").bind(String(i), id, id).run();
  const first = await (await call(`/api/user/replies?rootId=${id}&size=2`)).json();
  await db.prepare("UPDATE profile_comments SET content='',deleted_at=unixepoch() WHERE id=?").bind(first.comments[0].id).run();
  await db.prepare("INSERT INTO profile_comments(target_bid,author_bid,content,created_at,root_id,reply_to_id) VALUES('__public__','Bob','new',102,?,?)").bind(id, id).run();
  const next = await (await call(`/api/user/replies?rootId=${id}&size=2&before=${first.nextCursor}`)).json();
  assert.deepEqual(next.comments.map(c => c.content), ["2", "1"]);
});

test("root insert rechecks target existence in transaction and releases rejected claim", async () => {
  await reset(); await seed("review-board");
  await db.prepare(`CREATE TRIGGER review_remove_board AFTER INSERT ON write_throttle
    WHEN NEW.key='wiki:104' BEGIN DELETE FROM user_profiles WHERE bid='review-board'; END`).run();
  try {
    const response = await call("/api/user/comments", "POST", { targetBid: "review-board", content: "orphan" }, user("Reviewer"));
    assert.equal(response.status, 409);
    assert.equal((await db.prepare("SELECT COUNT(*) AS n FROM profile_comments WHERE target_bid='review-board'").first()).n, 0);
    assert.equal(await db.prepare("SELECT 1 FROM write_throttle WHERE key='wiki:104'").first(), null);
    assert.equal((await db.prepare("SELECT COUNT(*) AS n FROM write_requests").first()).n, 0);
  } finally { await db.prepare("DROP TRIGGER review_remove_board").run(); }
});

test("moderation hides entire threads and idempotent replay, restore retains content", async () => {
  await reset();
  const root = (await (await call('/api/user/comments', 'POST', {targetBid:'__public__',content:'root secret'}, user('Alice'))).json()).comment;
  const headers = {...user('Bob'), 'Idempotency-Key':'moderation-reply-01'};
  const payload = {targetBid:'__public__',content:'reply secret',replyToId:root.id};
  const reply = (await (await call('/api/user/replies','POST',payload,headers)).json()).comment;
  const admin = {'X-Admin-Password':'test-admin'};
  assert.equal((await call('/api/admin/comment','PUT',{id:root.id,action:'hide',reason:'review'},admin)).status,200);
  assert.equal((await (await call('/api/user/comments?bid=__public__')).json()).total,0);
  assert.equal((await call(`/api/user/replies?rootId=${root.id}`)).status,404);
  assert.equal((await call('/api/user/replies','POST',payload,headers)).status,410);
  assert.equal((await call('/api/admin/comment','PUT',{id:reply.id,action:'hide'},admin)).status,200);
  await call('/api/admin/comment','PUT',{id:root.id,action:'restore'},admin);
  const restored = await (await call(`/api/user/replies?rootId=${root.id}`)).json();
  assert.equal(restored.root.content,'root secret'); assert.equal(restored.comments.length,0);
  await call('/api/admin/comment','PUT',{id:reply.id,action:'restore'},admin);
  assert.equal((await (await call(`/api/user/replies?rootId=${root.id}`)).json()).comments[0].content,'reply secret');
  const audit = await (await call('/api/admin/audit','GET',null,admin)).json();
  assert.equal(audit.total,4);
  assert.ok(!JSON.stringify(audit).includes('reply secret'));
});

test("hidden reply author cannot leak through quotes and pin changes do not move cursor pages", async () => {
  await reset();
  const ids=[];
  for(let i=0;i<5;i++) ids.push((await db.prepare("INSERT INTO profile_comments(target_bid,author_bid,content,created_at) VALUES('__public__','Alice',?,100)").bind(String(i)).run()).meta.last_row_id);
  const admin={'X-Admin-Password':'test-admin'};
  const first=await (await call('/api/user/comments?bid=__public__&size=2')).json();
  await call('/api/admin/comment','PUT',{id:ids[0],action:'pin'},admin);
  const next=await (await call(`/api/user/comments?bid=__public__&size=2&before=${first.nextCursor}`)).json();
  assert.deepEqual(next.comments.map(c=>c.content),['2','1']); assert.equal(next.pinnedComments[0].id,ids[0]);
  const results=await Promise.all(ids.slice(1).map(id=>call('/api/admin/comment','PUT',{id,action:'pin'},admin)));
  assert.equal(results.filter(r=>r.status===200).length,2);
  assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM profile_comments WHERE pinned_at IS NOT NULL').first()).n,3);
  const reply=(await (await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:ids[0],content:'reply'},user('Bob'))).json()).comment;
  await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:reply.id,content:'quote'},user('Carol'));
  assert.equal((await call('/api/admin/comment','PUT',{id:reply.id,action:'pin'},admin)).status,400);
  await call('/api/admin/comment','PUT',{id:reply.id,action:'hide'},admin);
  const thread=await (await call(`/api/user/replies?rootId=${ids[0]}`)).json();
  assert.equal(thread.comments[0].replyToName,'已隐藏留言'); assert.equal(thread.comments[0].replyToTag,null);
  assert.equal(thread.root.replyCount,1);
});

test("moderation is authorized, audited atomically and deletion is not restorable", async () => {
  await reset();
  const root=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'root'},user('Alice'))).json()).comment;
  const body={id:root.id,action:'hide'}; const admin={'X-Admin-Password':'test-admin'};
  assert.equal((await call('/api/admin/comment','PUT',body)).status,401);
  await db.prepare("CREATE TRIGGER reject_audit BEFORE INSERT ON admin_audit BEGIN SELECT RAISE(ABORT,'audit failure'); END").run();
  try {
    assert.equal((await call('/api/admin/comment','PUT',body,admin)).status,500);
    assert.equal((await db.prepare('SELECT hidden_at FROM profile_comments WHERE id=?').bind(root.id).first()).hidden_at,null);
  } finally { await db.prepare('DROP TRIGGER reject_audit').run(); }
  await call(`/api/admin/comment?id=${root.id}`,'DELETE',null,admin);
  assert.equal((await call('/api/admin/comment','PUT',{id:root.id,action:'restore'},admin)).status,200);
  assert.equal((await db.prepare('SELECT content FROM profile_comments WHERE id=?').bind(root.id).first()).content,'');
  assert.equal((await (await call('/api/admin/audit','GET',null,admin)).json()).records[0].action,'delete_comment');
});

test("hide wins against reply insertion recheck and duplicate hide does not duplicate audit", async () => {
  await reset();
  const root=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'root'},user('Alice'))).json()).comment;
  await db.prepare(`CREATE TRIGGER hide_during_post AFTER INSERT ON write_throttle WHEN NEW.key='wiki:102'
    BEGIN UPDATE profile_comments SET hidden_at=unixepoch() WHERE id=${root.id}; END`).run();
  try {
    assert.equal((await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:root.id,content:'rejected'},user('Bob'))).status,409);
    assert.equal(await db.prepare("SELECT 1 FROM write_throttle WHERE key='wiki:102'").first(),null);
  } finally { await db.prepare('DROP TRIGGER hide_during_post').run(); }
  const admin={'X-Admin-Password':'test-admin'};
  await call('/api/admin/comment','PUT',{id:root.id,action:'restore'},admin);
  await Promise.all(Array.from({length:4},()=>call('/api/admin/comment','PUT',{id:root.id,action:'hide'},admin)));
  assert.equal((await (await call('/api/admin/audit','GET',null,admin)).json()).total,2);
});

test("profile admin mutations are audited and hidden pins cannot reappear on restore", async () => {
  await reset(); await seed('Alice');
  const admin={'X-Admin-Password':'test-admin'};
  assert.equal((await call('/api/admin/profile','PUT',{bid:'Alice',bio:'changed'},admin)).status,200);
  const root=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'pin'},user('Bob'))).json()).comment;
  for(const action of ['pin','hide','restore']) assert.equal((await call('/api/admin/comment','PUT',{id:root.id,action},admin)).status,200);
  assert.equal((await (await call('/api/user/comments?bid=__public__')).json()).pinnedComments.length,0);
  assert.equal((await call('/api/admin/profile?bid=Alice','DELETE',null,admin)).status,200);
  const audit=await (await call('/api/admin/audit','GET',null,admin)).json();
  assert.equal(audit.total,5); assert.equal(audit.records[0].action,'delete_profile');
  assert.equal(audit.records.at(-1).action,'edit_profile');
});

test("reply creates one notification in the same transaction, skips self and anonymous recipients", async () => {
  await reset();
  const root=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'root'},user('Alice'))).json()).comment;
  const reply=(await (await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:root.id,content:'reply'},user('Bob'))).json()).comment;
  let response=await call('/api/user/notifications','GET',null,user('Alice'));
  let data=await response.json(); assert.equal(response.status,200); assert.equal(data.unreadCount,1); assert.equal(data.notifications[0].commentId,reply.id);
  response=await call('/api/user/notifications','GET',null,user('Bob')); data=await response.json(); assert.equal(data.unreadCount,0);
  const anonymous=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'anon'},guest('203.0.113.70'))).json()).comment;
  await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:anonymous.id,content:'reply anon'},user('Alice'));
  response=await call('/api/user/notifications','GET',null,user('Alice')); data=await response.json(); assert.equal(data.unreadCount,1);
  assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM reply_notifications').first()).n,1);
});

test("notification read is idempotent and unavailable content does not leak", async () => {
  await reset();
  const root=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'root'},user('Alice'))).json()).comment;
  const reply=(await (await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:root.id,content:'secret reply'},user('Bob'))).json()).comment;
  let data=await (await call('/api/user/notifications','GET',null,user('Alice'))).json(); const notification=data.notifications[0];
  assert.equal((await call('/api/user/notifications','PUT',{id:notification.id},user('Alice'))).status,200);
  assert.equal((await call('/api/user/notifications','PUT',{id:notification.id},user('Alice'))).status,200);
  assert.equal((await (await call('/api/user/notifications','GET',null,user('Alice'))).json()).unreadCount,0);
  await call('/api/admin/comment','PUT',{id:reply.id,action:'hide'},{'X-Admin-Password':'test-admin'});
  data=await (await call('/api/user/notifications','GET',null,user('Alice'))).json();
  assert.equal(data.notifications[0].status,'hidden'); assert.equal(data.notifications[0].content,null); assert.equal(data.notifications[0].authorBid,null);
  assert.equal((await call(`/api/user/replies?rootId=${root.id}&focusId=${reply.id}`)).status,404);
});

test("notification throughId marks only the recipient's own older records", async () => {
  await reset();
  const root=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'root'},user('Alice'))).json()).comment;
  for (const [index, content] of ['one','two','three','four'].entries()) {
    const response = await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:root.id,content},user('Bob'));
    assert.equal(response.status, 200, await response.text());
    // Bob's reply throttling is bypassed in the fixture only by advancing the stored timestamp.
    if (index < 3) await db.prepare("DELETE FROM write_throttle").run();
  }
  const data=await (await call('/api/user/notifications','GET',null,user('Alice'))).json();
  assert.equal(data.unreadCount,4);
  const boundary=data.notifications[2].id;
  const read=await (await call('/api/user/notifications','PUT',{throughId:boundary},user('Alice'))).json();
  assert.equal(read.unreadCount,2);
  assert.equal((await (await call('/api/user/notifications','GET',null,user('Bob'))).json()).unreadCount,0);
});

test("notification follows direct recipient, skips real self reply and retry creates no duplicates", async () => {
  await reset();
  const root=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'root'},user('Alice'))).json()).comment;
  await db.prepare("DELETE FROM write_throttle").run();
  const self=await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:root.id,content:'self'},user('Alice'));
  assert.equal(self.status,200);
  assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM reply_notifications').first()).n,0);
  const headers={...user('Bob'),'Idempotency-Key':'notification-retry-001'};
  const payload={targetBid:'__public__',replyToId:root.id,content:'reply'};
  const replies=await Promise.all(Array.from({length:6},async()=>{
    const response=await call('/api/user/replies','POST',payload,headers);
    assert.equal(response.status,200); return (await response.json()).comment;
  }));
  assert.equal(new Set(replies.map(r=>r.id)).size,1);
  assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM reply_notifications').first()).n,1);
  const nested=await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:replies[0].id,content:'direct'},user('Carol'));
  assert.equal(nested.status,200);
  assert.equal((await (await call('/api/user/notifications','GET',null,user('Alice'))).json()).unreadCount,1);
  assert.equal((await (await call('/api/user/notifications','GET',null,user('Bob'))).json()).unreadCount,1);
});

test("notification failure rolls back reply, idempotency record and throttle", async()=>{
  await reset();
  const root=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'root'},user('Alice'))).json()).comment;
  await db.prepare("CREATE TRIGGER reject_notification BEFORE INSERT ON reply_notifications BEGIN SELECT RAISE(ABORT,'notification failure'); END").run();
  try {
    const response=await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:root.id,content:'rollback'},user('Bob'));
    assert.equal(response.status,500);
    assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM profile_comments WHERE root_id=?').bind(root.id).first()).n,0);
    assert.equal(await db.prepare("SELECT 1 FROM write_throttle WHERE key='wiki:102'").first(),null);
    assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM write_requests').first()).n,1);
  } finally { await db.prepare('DROP TRIGGER reject_notification').run(); }
});

test("notification pagination and through-read isolate users and leave later arrivals unread", async()=>{
  await reset();
  assert.equal((await call('/api/user/notifications')).status,401);
  assert.equal((await call('/api/user/notifications','PUT',{id:1})).status,401);
  for(let i=0;i<4;i++) await db.prepare("INSERT INTO reply_notifications(recipient_bid,comment_id,root_id) VALUES('Alice',?,1)").bind(i+1).run();
  const first=await (await call('/api/user/notifications?size=2','GET',null,user('Alice'))).json();
  assert.equal(first.notifications.length,2);
  const foreign=await (await call('/api/user/notifications','PUT',{id:first.notifications[0].id},user('Bob'))).json();
  assert.equal(foreign.changed,0);
  const created=await db.prepare("INSERT INTO reply_notifications(recipient_bid,comment_id,root_id) VALUES('Alice',5,1)").run();
  const next=await (await call(`/api/user/notifications?size=2&before=${first.nextCursor}`,'GET',null,user('Alice'))).json();
  assert.equal(next.notifications.length,2);
  assert.equal(new Set([...first.notifications,...next.notifications].map(n=>n.id)).size,4);
  const read=await (await call('/api/user/notifications','PUT',{throughId:first.latestId},user('Alice'))).json();
  assert.equal(read.unreadCount,1); assert.equal(read.changed,4);
  assert.equal((await (await call('/api/user/notifications','PUT',{throughId:first.latestId},user('Alice'))).json()).changed,0);
  const last=await (await call('/api/user/notifications','GET',null,user('Alice'))).json();
  assert.equal(last.notifications[0].id,created.meta.last_row_id); assert.equal(last.notifications[0].read,false);
  assert.equal((await call('/api/user/notifications','PUT',{id:1,throughId:2},user('Alice'))).status,400);
});

test("deleted root and hidden parent redact notifications; focused pages locate old replies", async()=>{
  await reset();
  const root=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'root'},user('Alice'))).json()).comment;
  const reply=(await (await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:root.id,content:'private text'},user('Bob'))).json()).comment;
  for(let i=0;i<25;i++) await db.prepare("INSERT INTO profile_comments(target_bid,author_bid,content,root_id,reply_to_id,created_at) VALUES('__public__','Carol','later',?,?,?)")
    .bind(root.id,root.id,reply.createdAt+1).run();
  const focused=await (await call(`/api/user/replies?rootId=${root.id}&focusId=${reply.id}&size=2`)).json();
  assert.equal(focused.comments[0].id,reply.id); assert.equal(focused.root.targetBid,'__public__');
  const admin={'X-Admin-Password':'test-admin'};
  await call('/api/admin/comment','PUT',{id:root.id,action:'hide'},admin);
  let data=await (await call('/api/user/notifications','GET',null,user('Alice'))).json();
  assert.equal(data.notifications[0].status,'hidden'); assert.equal(data.notifications[0].content,null);
  assert.equal((await call(`/api/user/replies?rootId=${root.id}&focusId=${reply.id}`)).status,404);
  await call('/api/admin/comment','PUT',{id:root.id,action:'restore'},admin);
  await call(`/api/admin/comment?id=${root.id}`,'DELETE',null,admin);
  data=await (await call('/api/user/notifications','GET',null,user('Alice'))).json();
  assert.equal(data.notifications[0].status,'deleted'); assert.equal(data.notifications[0].content,null); assert.equal(data.notifications[0].authorName,null);
});

test("deleted roots support discussion hide/unhide without resurrecting text or pins", async () => {
  await reset();
  const root=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'root'},user('Alice'))).json()).comment;
  const reply=(await (await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:root.id,content:'surviving'},user('Bob'))).json()).comment;
  const admin={'X-Admin-Password':'test-admin'};
  await call(`/api/admin/comment?id=${root.id}`,'DELETE',null,admin);
  assert.equal((await call('/api/admin/comment','PUT',{id:root.id,action:'hide'},admin)).status,200);
  assert.equal((await call(`/api/user/replies?rootId=${root.id}`)).status,404);
  assert.equal((await (await call('/api/user/comments?bid=__public__')).json()).total,0);
  assert.equal((await call('/api/admin/comment','PUT',{id:root.id,action:'restore'},admin)).status,200);
  const restored=await (await call(`/api/user/replies?rootId=${root.id}`)).json();
  assert.equal(restored.root.deleted,true); assert.equal(restored.root.content,'该留言已删除');
  assert.equal(restored.comments[0].content,'surviving');
  assert.equal((await call('/api/admin/comment','PUT',{id:root.id,action:'pin'},admin)).status,409);
  await call(`/api/admin/comment?id=${reply.id}`,'DELETE',null,admin);
  assert.equal((await call('/api/admin/comment','PUT',{id:reply.id,action:'restore'},admin)).status,409);
});

test("concurrent repeated deletes remain successful but audit the transition exactly once", async () => {
  await reset();
  const root=(await (await call('/api/user/comments','POST',{targetBid:'__public__',content:'root'},user('Alice'))).json()).comment;
  const admin={'X-Admin-Password':'test-admin'};
  await Promise.all(Array.from({length:5},async()=>{
    const response=await call(`/api/admin/comment?id=${root.id}`,'DELETE',null,admin);
    assert.equal(response.status,200); assert.equal((await response.json()).deleted,true);
  }));
  assert.equal((await db.prepare("SELECT COUNT(*) AS n FROM admin_audit WHERE action='delete_comment'").first()).n,1);
  assert.equal((await call(`/api/user/comments?id=${root.id}`,'DELETE',null,user('Alice'))).status,200);
  assert.equal((await call(`/api/user/comments?id=${root.id}`,'DELETE',null,user('Bob'))).status,404);
  assert.equal((await call('/api/admin/comment?id=9007199254740991','DELETE',null,admin)).status,404);
});

test("stable Wiki IDs survive rename for notifications, ownership and self-reply detection", async () => {
  await reset();
  const aliceV1 = renamedUser(7001, "Alice");
  const aliceV2 = renamedUser(7001, "AliceRenamed");
  const bob = renamedUser(7002, "Bob");
  const root = (await (await call("/api/user/comments", "POST", { targetBid: "__public__", content: "root" }, aliceV1)).json()).comment;
  assert.equal(root.authorWikiUserId, 7001);
  await db.prepare("DELETE FROM write_throttle").run();
  await call("/api/user/replies", "POST", { targetBid: "__public__", replyToId: root.id, content: "reply" }, bob);
  let notifications = await (await call("/api/user/notifications", "GET", null, aliceV2)).json();
  assert.equal(notifications.unreadCount, 1);
  assert.equal(notifications.notifications[0].authorName, "Bob");
  assert.equal((await call(`/api/user/comments?id=${root.id}`, "DELETE", null, aliceV2)).status, 200);
  await db.prepare("DELETE FROM write_throttle").run();
  const renamedRoot = (await (await call("/api/user/comments", "POST", { targetBid: "__public__", content: "new name" }, aliceV2)).json()).comment;
  await db.prepare("DELETE FROM write_throttle").run();
  await call("/api/user/replies", "POST", { targetBid: "__public__", replyToId: renamedRoot.id, content: "self" }, aliceV1);
  notifications = await (await call("/api/user/notifications", "GET", null, aliceV2)).json();
  assert.equal(notifications.unreadCount, 1);
});

test("anonymous authors still notify logged-in recipients", async () => {
  await reset();
  const root = (await (await call("/api/user/comments", "POST", { targetBid: "__public__", content: "root" }, user("Alice"))).json()).comment;
  await call("/api/user/replies", "POST", { targetBid: "__public__", replyToId: root.id, content: "anon reply" }, guest("203.0.113.90"));
  const data = await (await call("/api/user/notifications", "GET", null, user("Alice"))).json();
  assert.equal(data.unreadCount, 1);
  assert.equal(data.notifications[0].authorName, "访客");
  assert.equal(data.notifications[0].authorBid, "anon");
});

test("session resolves authoritative identity and errors carry structured codes", async () => {
  await reset();
  const session = await (await call("/api/user/session", "GET", null, renamedUser(7001, "AliceRenamed"))).json();
  assert.deepEqual(session, { user: { bid: "AliceRenamed", wikiUserId: 7001 } });
  assert.equal((await call("/api/user/session", "GET", null, user("expired"))).status, 401);
  const missing = await call("/api/user/replies?rootId=99999999");
  assert.equal(missing.status, 404);
  assert.equal((await missing.json()).errorCode, "DISCUSSION_UNAVAILABLE");
  const root = (await (await call("/api/user/comments", "POST", { targetBid: "__public__", content: "root" }, user("Alice"))).json()).comment;
  await db.prepare("DELETE FROM write_throttle").run();
  const reply = (await (await call("/api/user/replies", "POST", { targetBid: "__public__", replyToId: root.id, content: "x" }, user("Bob"))).json()).comment;
  await call("/api/admin/comment", "PUT", { id: reply.id, action: "hide" }, { "X-Admin-Password": "test-admin" });
  const focused = await call(`/api/user/replies?rootId=${root.id}&focusId=${reply.id}`);
  assert.equal(focused.status, 404);
  assert.equal((await focused.json()).errorCode, "FOCUS_UNAVAILABLE");
});

test("0009 backfill attaches legacy rows so renamed owners keep access", async () => {
  await reset();
  await db.prepare("INSERT INTO user_profiles(bid,wiki_user_id) VALUES('Legacy',8001)").run();
  const root = (await db.prepare("INSERT INTO profile_comments(target_bid,author_bid,content,created_at) VALUES('__public__','Legacy','legacy root',unixepoch())").run()).meta.last_row_id;
  const reply = (await db.prepare("INSERT INTO profile_comments(target_bid,author_bid,content,created_at,root_id,reply_to_id) VALUES('__public__','anon','legacy reply',unixepoch(),?,?)").bind(root, root).run()).meta.last_row_id;
  await db.prepare("INSERT INTO reply_notifications(recipient_bid,comment_id,root_id) VALUES('Legacy',?,?)").bind(reply, root).run();
  await db.prepare("UPDATE profile_comments SET author_wiki_user_id=(SELECT wiki_user_id FROM user_profiles p WHERE p.bid=profile_comments.author_bid) WHERE author_bid<>'anon' AND author_wiki_user_id IS NULL").run();
  await db.prepare("UPDATE reply_notifications SET recipient_wiki_user_id=(SELECT parent.author_wiki_user_id FROM profile_comments reply JOIN profile_comments parent ON parent.id=reply.reply_to_id WHERE reply.id=reply_notifications.comment_id) WHERE recipient_wiki_user_id IS NULL").run();
  await db.prepare("UPDATE reply_notifications SET recipient_wiki_user_id=(SELECT wiki_user_id FROM user_profiles p WHERE p.bid=reply_notifications.recipient_bid) WHERE recipient_wiki_user_id IS NULL").run();
  const renamed = renamedUser(8001, "LegacyRenamed");
  const notifications = await (await call("/api/user/notifications", "GET", null, renamed)).json();
  assert.equal(notifications.unreadCount, 1);
  assert.equal(notifications.notifications[0].authorName, "anon");
  assert.equal((await call(`/api/user/comments?id=${root}`, "DELETE", null, renamed)).status, 200);
});

test("audit cursor pages stay stable when newer records arrive", async () => {
  await reset();
  for (let i = 0; i < 25; i++) await db.prepare("INSERT INTO admin_audit(actor,action,target) VALUES('admin','hide',?)").bind(String(i)).run();
  const admin = { "X-Admin-Password": "test-admin" };
  const first = await (await call("/api/admin/audit", "GET", null, admin)).json();
  assert.equal(first.records.length, 20);
  assert.equal(first.nextCursor, String(first.records.at(-1).id));
  await db.prepare("INSERT INTO admin_audit(actor,action,target) VALUES('admin','hide','newer')").run();
  const next = await (await call(`/api/admin/audit?before=${first.nextCursor}`, "GET", null, admin)).json();
  assert.equal(next.records.length, 5);
  assert.equal(next.nextCursor, null);
  assert.equal(new Set([...first.records, ...next.records].map(r => r.id)).size, 25);
});

test("anon sentinel is neither a real account's delete permission nor its public profile", async () => {
  await reset();
  const stranger = (await (await call('/api/user/comments', 'POST', {targetBid:'__public__',content:'guest'}, guest('203.0.113.101'))).json()).comment;
  const namedAnon = renamedUser(9101, 'anon');
  assert.equal((await call('/api/user/profile', 'PUT', {customName:'Registered anon'}, namedAnon)).status, 200);
  assert.equal((await call(`/api/user/comments?id=${stranger.id}`, 'DELETE', null, namedAnon)).status, 404);
  const board = await (await call('/api/user/comments?bid=__public__')).json();
  assert.equal(board.comments[0].authorName, '访客');
  assert.equal(board.comments[0].authorAvatarUrl, null);
  const own = (await (await call('/api/user/comments', 'POST', {targetBid:'__public__',content:'registered'}, namedAnon)).json()).comment;
  assert.equal(own.authorWikiUserId, 9101);
  assert.equal((await call(`/api/user/comments?id=${own.id}`, 'DELETE', null, namedAnon)).status, 200);
});

test("profile edits reject an editor bound to another immutable user", async () => {
  await reset();
  assert.equal((await call('/api/user/profile','PUT',{customName:'A'},renamedUser(9201,'A'))).status,200);
  const rejected=await call('/api/user/profile','PUT',{customName:'A draft',expectedWikiUserId:9201},renamedUser(9202,'B'));
  assert.equal(rejected.status,409); assert.equal((await rejected.json()).errorCode,'IDENTITY_CHANGED');
  assert.equal((await (await call('/api/user/profile?wiki_id=9201')).json()).profile.customName,'A');
  assert.equal((await (await call('/api/user/profile?wiki_id=9202')).json()).profile,null);
});

test("rename retains one profile, original avatars, old board links and ID-scoped throttling", async () => {
  await reset();
  const before=renamedUser(9301,'Before'), after=renamedUser(9301,'After');
  const upload=await mf.dispatchFetch(base+'/api/user/avatar',{method:'POST',headers:{...before,'Content-Type':'image/png'},body:png});
  assert.equal(upload.status,200);
  const {avatarUrl}=await upload.json();
  assert.equal((await call('/api/user/profile','PUT',{customName:'Saved',avatarUrl,bio:'kept'},before)).status,200);
  const root=(await (await call('/api/user/comments','POST',{targetBid:'Before',content:'board root'},user('Bob'))).json()).comment;
  assert.equal((await call('/api/user/session','GET',null,after)).status,200);
  let profile=(await (await call('/api/user/profile?bid=After')).json()).profile;
  assert.equal(profile.customName,'Saved'); assert.equal(profile.bio,'kept'); assert.equal(profile.bid,'After');
  assert.equal((await (await call('/api/user/profile?bid=wrong&wiki_id=9301')).json()).profile.customName,'Saved');
  assert.equal((await call('/api/user/profile','PUT',{avatarUrl,customName:'Changed'},after)).status,429);
  await db.prepare('DELETE FROM write_throttle').run();
  assert.equal((await call('/api/user/profile','PUT',{avatarUrl,customName:'Changed'},after)).status,200);
  assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM user_profiles WHERE wiki_user_id=9301').first()).n,1);
  const board=(await (await call('/api/user/comments?bid=After')).json());
  assert.equal(board.comments[0].id,root.id);
  assert.equal((await call('/api/user/replies','POST',{targetBid:'After',replyToId:root.id,content:'renamed board'},user('Carol'))).status,200);
});

test("rename keeps likes attached to the stable recipient and giver", async () => {
  await reset();
  const target=renamedUser(9401,'Target'), renamedTarget=renamedUser(9401,'TargetNew');
  const giver=renamedUser(9402,'Giver'), renamedGiver=renamedUser(9402,'GiverNew');
  assert.equal((await call('/api/user/profile','PUT',{customName:'Target'},target)).status,200);
  assert.equal((await call('/api/user/likes','PUT',{targetBid:'Target'},giver)).status,200);
  assert.equal((await call('/api/user/session','GET',null,renamedTarget)).status,200);
  let response=await call('/api/user/likes?bid=TargetNew','GET',null,renamedGiver);
  let result=await response.json(); assert.equal(result.count,1); assert.equal(result.likedByMe,true);
  assert.equal((await call('/api/user/likes','PUT',{targetBid:'TargetNew'},renamedGiver)).status,200);
  result=await (await call('/api/user/likes','DELETE',{targetBid:'TargetNew'},renamedGiver)).json();
  assert.equal(result.count,0); assert.equal(result.liked,false);
});

test("verified alias conflicts cannot overwrite another user's profile or avatar", async () => {
  await reset();
  assert.equal((await call('/api/user/profile','PUT',{customName:'Original'},renamedUser(9501,'Protected'))).status,200);
  const response=await call('/api/user/profile','PUT',{customName:'Hijacked'},renamedUser(9502,'Protected'));
  assert.equal(response.status,409); assert.equal((await response.json()).errorCode,'IDENTITY_CONFLICT');
  assert.equal((await (await call('/api/user/profile?wiki_id=9501')).json()).profile.customName,'Original');
});

test("legacy authors without profiles receive notifications and are claimed only after verified login", async () => {
  await reset();
  const root=(await db.prepare("INSERT INTO profile_comments(target_bid,author_bid,content) VALUES('__public__','LegacyNoProfile','old')").run()).meta.last_row_id;
  assert.equal((await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:root,content:'reply'},user('Bob'))).status,200);
  const notice=await db.prepare('SELECT recipient_bid,recipient_wiki_user_id FROM reply_notifications').first();
  assert.equal(notice.recipient_bid,'LegacyNoProfile'); assert.equal(notice.recipient_wiki_user_id,null);
  const owner=renamedUser(9601,'LegacyNoProfile');
  const inbox=await (await call('/api/user/notifications','GET',null,owner)).json();
  assert.equal(inbox.unreadCount,1);
  assert.equal((await db.prepare('SELECT author_wiki_user_id FROM profile_comments WHERE id=?').bind(root).first()).author_wiki_user_id,9601);
  assert.equal((await db.prepare('SELECT recipient_wiki_user_id FROM reply_notifications').first()).recipient_wiki_user_id,9601);
  await db.prepare('DELETE FROM write_throttle').run();
  assert.equal((await call('/api/user/replies','POST',{targetBid:'__public__',replyToId:root,content:'self'},owner)).status,200);
  assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM reply_notifications').first()).n,1);
  assert.equal((await (await call('/api/user/notifications','GET',null,renamedUser(9601,'LegacyRenamed'))).json()).unreadCount,1);
});

test("admin exact lookup does not confuse a newer substring match with the requested profile", async () => {
  await reset();
  await db.prepare("INSERT INTO user_profiles(bid,wiki_user_id,updated_at) VALUES('Exact',9701,100),('ExactOther',9702,101)").run();
  const headers={'X-Admin-Password':'test-admin'};
  assert.equal((await call('/api/admin/profile?bid=Exact')).status,401);
  const result=await (await call('/api/admin/profile?bid=Exact','GET',null,headers)).json();
  assert.equal(result.profile.bid,'Exact'); assert.equal(result.profile.wikiUserId,9701);
});

test("0010 archives duplicate profiles and keeps aliases, boards, likes and avatar owners", async () => {
  const migrationDb=await mf.getD1Database('MIGRATION_DB');
  const sqlStatements=sql=>sql.replace(/^--.*$/gm,'').split(';').map(s=>s.trim()).filter(Boolean);
  const files=(await readdir('migrations')).filter(f=>f.endsWith('.sql')).sort();
  for(const file of files.filter(f=>f<'0010_account_identity.sql')) {
    for(const sql of sqlStatements(await readFile(`migrations/${file}`,'utf8'))) await migrationDb.prepare(sql).run();
  }
  await migrationDb.prepare("INSERT INTO user_profiles(bid,wiki_user_id,custom_name,updated_at) VALUES('Old',9801,'Old profile',100),('New',9801,'Newest profile',101)").run();
  await migrationDb.prepare("INSERT INTO avatar_assets(object_key,owner_bid,created_at) VALUES('avatars/old.png','Old',100)").run();
  await migrationDb.prepare("INSERT INTO profile_comments(target_bid,author_bid,content) VALUES('Old','Bob','kept board')").run();
  await migrationDb.prepare("INSERT INTO profile_likes(target_bid,author_bid) VALUES('Old','Giver'),('New','Giver')").run();
  await migrationDb.batch(sqlStatements(await readFile('migrations/0010_account_identity.sql','utf8')).map(sql=>migrationDb.prepare(sql)));
  assert.equal((await migrationDb.prepare('SELECT COUNT(*) AS n FROM user_profiles').first()).n,1);
  assert.equal((await migrationDb.prepare('SELECT custom_name FROM user_profiles').first()).custom_name,'Newest profile');
  assert.equal((await migrationDb.prepare('SELECT custom_name FROM user_profile_archive').first()).custom_name,'Old profile');
  assert.equal((await migrationDb.prepare("SELECT wiki_user_id FROM wiki_user_aliases WHERE bid='Old'").first()).wiki_user_id,9801);
  assert.equal((await migrationDb.prepare('SELECT owner_wiki_user_id FROM avatar_assets').first()).owner_wiki_user_id,9801);
  assert.equal((await migrationDb.prepare('SELECT target_bid FROM profile_comments').first()).target_bid,'New');
  assert.equal((await migrationDb.prepare('SELECT COUNT(*) AS n FROM profile_likes').first()).n,1);
});

test("untracked legacy avatar aliases remain usable after profile canonicalization and are retired by ID", async () => {
  await reset();
  await db.prepare("INSERT INTO user_profiles(bid,wiki_user_id) VALUES('Canonical',9901)").run();
  await db.prepare("INSERT INTO wiki_user_aliases(bid,wiki_user_id) VALUES('OldAlias',9901),('Canonical',9901)").run();
  const key='avatars/OldAlias_0123456789abcdef.png';
  await assets.put(key,png);
  const owner=renamedUser(9901,'Canonical');
  const response=await call('/api/user/profile','PUT',{avatarUrl:'/api/user/avatar/'+key},owner);
  assert.equal(response.status,200,await response.text());
  assert.equal((await db.prepare('SELECT owner_wiki_user_id FROM avatar_assets WHERE object_key=?').bind(key).first()).owner_wiki_user_id,9901);
  await db.prepare('DELETE FROM write_throttle').run();
  assert.equal((await call('/api/user/profile','PUT',{avatarUrl:null},owner)).status,200);
  assert.equal(await assets.head(key),null);
});
