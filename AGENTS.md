# Agent notes

Kotlin Multiplatform app (`androidApp`, `desktopApp`, `shared`, `webApp`) plus a separate Vite/Svelte 5 site in `downloadPage/` (not a Gradle module).

## Commands

Windows: `.\gradlew.bat`. macOS/Linux: `./gradlew`.

- Android compile: `.\gradlew.bat :androidApp:compileDebugKotlin`
- Desktop run: `.\gradlew.bat run`
- Web typecheck: `cd downloadPage; pnpm check`
- Web local: `cd downloadPage; pnpm dev` (Vite proxies `/api/wiki`, `/api/balance/*`, image/file download; it does **not** serve R2 APKs)
- Release site: `.\gradlew.bat webDeploy` → `webDist` (assembleRelease, rewrite `latest.json`, upload APK to R2) then `webPush` (build + `wrangler pages deploy`)

`downloadPage/dist/` is generated. `pnpm build` builds the site and bundles `src/api/_worker.js` with esbuild into `dist/_worker.js`. `webStatic` copies `_headers`, `_redirects`, `downloads/latest.json`, and `icon.svg`; never overwrite the bundled Worker with its source entrypoint. Worker integration tests: `pnpm test:worker` in `downloadPage/`.

## Version / release

Keep these in the same change: `androidApp/build.gradle.kts` (`versionName`, `versionCode`), `desktopApp` `packageVersion`, About fallback string, `downloadPage/downloads/latest.json`.

`webDist` **reuses the current `changelog` in `latest.json`**. Edit changelog for the new version **before** `webDeploy`, or the site ships the previous notes.

APKs are **not** git-tracked (`*.apk` in `.gitignore`). Production files live in R2 bucket `calabiyau-releases` as `android/CalabiYauVoice-<version>.apk` plus `android/CalabiYauVoice-latest.apk`. The Pages Worker streams `/downloads/CalabiYauVoice-(latest|x.y.z).apk` from that bucket. `apkUrl` in JSON stays a same-origin path (`/downloads/CalabiYauVoice-2.1.7.apk`). Android `UpdateApi` already resolves that; do not switch it to an in-app WebView.

Do not delete `2.1.6` from `releases`. Full checklist: `docs/release-version-checklist.md`.

## downloadPage Worker

`downloadPage/src/api/proxy.js`, `src/api/auth.js`, and `downloadPage/vite.config.ts` share upstream constants; change both production and development paths.

Before deploying the backend consistency update, apply migration `0005_backend_consistency.sql` and configure a dedicated random `GUEST_SECRET` (at least 32 characters) in Pages. Do not reuse `ADMIN_PASSWORD`. `POST /api/admin/maintenance` performs bounded orphan-avatar and expired-metadata cleanup; it needs an external schedule or manual invocation.

Two-level replies additionally require `0006_comment_replies.sql`. `/api/user/comments` lists roots only for old-client compatibility; `/api/user/replies` lists/posts replies. Comment deletion clears content and retains a tombstone; a deleted root closes its discussion to new replies.

Content moderation requires `0007_comment_moderation.sql`. Hidden roots hide their discussion; hiding is reversible, deletion is not. Pins are returned separately as `pinnedComments` (maximum 3 per board); the chronological feed still includes pinned rows for stable pagination and old clients. New clients deduplicate pins for display. Admin mutations and `admin_audit` writes share a transaction.

Reply notifications additionally require `0008_reply_notifications.sql`. Notifications target logged-in users only, are created in the reply D1 batch, and expose no content after the referenced reply/root is hidden or deleted. `GET/PUT /api/user/notifications` supports unread counts, cursor pages, single-read and through-read.

Stable identity additionally requires `0009_stable_user_identity.sql`. Comments store `author_wiki_user_id` and notifications `recipient_wiki_user_id`; ownership, self-reply detection and notification routing match the immutable MediaWiki user ID first with BID only as legacy fallback. `GET /api/user/session` returns the authoritative identity. Business errors may carry a structured `errorCode`; clients invalidate caches only on those.

Account/board audit fixes additionally require **`0010_account_identity.sql` before deploying the Worker**. It adds verified BID aliases/current names, stable avatar/like owner IDs, archives superseded duplicate profiles, and enforces one profile per Wiki ID. Profile storage BID stays stable; public responses expose the latest verified name. Never claim anonymous `author_bid='anon'` rows via BID fallback. Profile editors send `expectedWikiUserId`; prefer `wiki_id` over BID in reads. Alias collisions fail closed (`IDENTITY_CONFLICT`), not automatic account merges. See `docs/user-system-backend.md` for backup/migration constraints.

Board drafts: explicit anonymous and authenticated drafts have separate owners. Pending posts persist both local owner and `submissionActor`/`targetBid`; unresolved sends cannot switch identity, and pre-fix uncertain Wiki-owner requests without a transport identity fail closed. Disk prefetch must never override ANY successful network response (including empty). Drawer account/profile results are guarded by cookie + generation; saved profiles invalidate pending reads.

R2 binding is `RELEASES` in `downloadPage/wrangler.jsonc` (Pages project `calabiyauwiki`). Do not commit `downloadPage/wrangler.toml` (gitignored; dashboard download can contain secrets). `GITHUB_TOKEN` is a Pages dashboard secret, not in `wrangler.jsonc`.

## Wiki structure golden fixtures & EdgeOne rate limits

BWiki HTML is not a stable API: 2026-09 drift broke items/announcements/activities/weapon-detail/map-list parsers. Real-page snapshots live under `androidApp/src/test/resources/fixtures/pages/` (gitignored, local-only) — `WikiRealPageGoldenTest` runs real-structure assertions when files exist and skips on fresh clones. Refresh via `action=parse&page=<名>&prop=text`; update anchor assertions after refresh. `WikiGoldenFixtureTest` holds captured API JSON (announcement ask, activity/weapon parses) — tracked, keep small.

Batch-fetching BWiki triggers Tencent EdgeOne blocks (HTTP 567) — blocks persist long after the burst; diagnostics can use the browser session (same IP gets blocked too) or wait tens of minutes. Live e2e tests (`LIVE_WIKI_TEST=1`) cover announcements ask, activity cards, weapon detail for 静风/北极星/大剑/小蜜蜂 (per-weapon key structure: 北极星 mobile rows / 大剑 melee nested rows / 小蜜蜂 upper column), **map list template** and **costume filter Lua module**. `fetchBody` throttles 6.5s after every request (inside try — failure paths also back off); keep that if you add requests.

Local snapshots: fetch writes AND `parsesLocalLiveSnapshotsWhenPresent` reads `androidApp/build/live-wiki/` (paths unified). `CharacterDetailLiveAuditTest` audits full character parsing from `androidApp/build/char-audit/` captures; `CharacterDetailApi.parseCharacterWikitext` is internal for that. Both skip when their dirs are absent.

Account-chain caches invalidate on mutation: `updateProfile` clears `profileCache` (60s profile memo), `syncResponseCookies` clears `cookieMemo` (500ms cookie memo). New caches must follow the same invalidate-on-write pattern.

## MediaWiki image queries

All 8 imageinfo call sites pass `redirects=1` (files like 武器-大剑.png redirect to 武器外观图鉴 <id>.png). **Batch sites must also map `query.redirects[].from` → target URL** — title-keyed misses silently drop redirect files: `fetchBatchImageUrls` (FetchImageUrls.kt; weapon list / portraits / 壁纸) and `CharacterDetailApi.fetchImageUrls` (skill icons / story covers) both do; single-title sites are transparent. Weapon-detail images try candidate filenames in order (`武器-<名>.png` for secondary/melee/tactical, `<名>-weapon.png` for primary, each other as fallback) and backfill base damage / body multipliers from the coefficient table (武器部位伤害系数) when the template lacks them.

## Android Wiki pages

When adding/refactoring `androidApp/.../feature/wiki`, follow `docs/android-wiki-feature-guide.md`: split `model` / `source` / `parser` / `api` / `Screen`. BWiki HTML is not a stable API; keep parse failures diagnosable.

## Do not

- Commit signing material (`local.properties`, keystores) or APKs
- Edit `androidApp/build` outputs
- Treat `.agents/` as repo source of truth (gitignored). `.github/*` is also ignored **except** `.github/workflows/` (tracked — the daily live-smoke CI lives there)
