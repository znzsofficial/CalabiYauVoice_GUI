# Agent notes

Kotlin Multiplatform app (`androidApp`, `desktopApp`, `shared`) plus a separate Vite/Svelte 5 site in `downloadPage/` (not a Gradle module). `webApp/` is an archived, unmaintained Compose JS/Wasm UI prototype (sample data and simulated downloads, not a working client). Excluded from default Gradle settings; opt in with `-PincludeArchivedWebApp=true` only for experiments. See `webApp/README.md`; never use it for production web deployments or duplicate the active Svelte tools there.

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

Release verification is lightweight by user preference (2026-11-06): run change-scoped checks, reuse passing unchanged-code results, let webDist/webPush perform their required builds, then confirm primary-domain metadata and download availability. Do not routinely force all tasks, rerun unaffected platform/web suites, re-download APKs from every domain/R2 alias, or sweep historical releases. Expand only for relevant signing/upload/download/routing changes, failures, or explicit requests. Documentation-only closeout needs diff review, not another build/deploy. Past exhaustive release reports are not future mandatory checklists.

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

WebView/auth changes: see `docs/android-webview-auth.md`. Native Set-Cookie writes require an original CookieSessionGuard ticket and a fresh store check; logout must invalidate tickets and caches. Use the unified WikiNavigationPolicy for all main-document entry points (including POST interception); resolve intent fallback before loading. File-picker cancellation keeps an in-flight tombstone until ActivityResult arrives. WikiWebViewLifetime owns main/popups; release popups and timers on navigation, close, crash, parent release or timeout. Do not mistake anonymous-client Lint warnings for missing render-exit overrides without reading the code.

Android infrastructure and file management: see `docs/android-core-file-manager.md`. Memory-cache clears invalidate pending writers; offline fallbacks must not become permanent memory hits. Force refresh retains the last good disk copy; validate parse payloads before publishing. Auto-retry GET/HEAD only, never mutations. File-manager operations stay within canonical root boundaries; use native lstat for links, no chmod/rm fallback, and keep directory-picking controls available for empty directories. Both native download adapters use shared `downloadResourceAtomically`; never skip nonzero files in an adapter. Update APK cleanup is gated by a persisted target version and exact app-owned path; never delete before PackageManager reports the target installed or sweep files while DownloadManager state is uncertain.

Resource download changes: see `docs/resource-download-implementation.md`. Dialog reads have category/mode generations; search errors must not become empty results. Scan every search-family seed, not just root. Persisted `ResourceDownloadRequest` fixes retry payload/path/options and enables explicit resume after process death (not guaranteed background execution). Android manifests live in internal filesDir/download_manifests keyed by canonical save-directory SHA-256; import legacy public sidecars read-only, never require their creation/replacement. Desktop keeps directory sidecars. Manifests include length + SHA-256; legacy two-column entries re-download once to establish integrity. Network status and resume-record persistence are hints, not download gates. Category fetch failures must not discard already resolved files or look like an empty result. Do not delete a target before the atomic download adapter runs, and do not republish a manifest for files that already verified. Never skip arbitrary nonzero tracked files or use repeated log text as a LazyColumn key.

Item catalogs were split in 2026-10: `功能道具图鉴PC端` and `功能道具图鉴移动端` (mobile-exclusive items only). `ItemCatalogPlatform` owns page names/cache keys; never fetch the retired combined page or reuse its `item_catalog` cache. `WikiRoute.Items` keeps old saved routes/shortcuts as PC; `MobileItems` is separate. Current card-only parser and tracked `fixtures/items/` replace retired table tests; live smoke validates both pages independently with 6.5s request spacing.

Extraction mode (弦溯战线): battle modes show the introduction/settings; its three native catalogs belong in **外观与图鉴**, not the battle-mode card. `feature/wiki/extraction` owns page names and separate `extraction_*_v1` cache keys. Loot/attachment loading uses image-grid skeletons, drops a text-list skeleton. Keep source item rarity labels (1 优秀 / 5 臻藏), do not treat drop `data-tier` as rarity, and do not relabel pool weights as match drop probabilities. See `docs/extraction-mode-implementation.md`.

Drop summary tables support both legacy `掉落池条目（完整）` and `掉落池条目（按稀有度分行）` with `rowspan` metadata. Group continuation rows by their original name cell, never name/tier alone (Boss cores share both). Preserve source labels such as Q8 without inventing a rarity name. Source cards and complete pools are separate views; do not infer joins from similar names or row order.

The 2026-11-04 drop page uses `.sec-drop-pool` shared pools (including collapsed HTML chips), section `.sec-drop-note` metadata, and a Boss stage candidate-pool table. Keep `ITEM / DROP_SOURCE / DROP_POOL / DROP_STAGE` explicit; never infer type from display field labels. `.sec-drop-card__must` can mean **weighted** Boss materials: read its nearest preceding source label, not CSS alone. Preserve shared-pool/group declared counts and reject structurally partial responses before disk/memory publication even when cards still parse. Table adapters handle semantic headers, column order and bounded row-group spans separately from cards/pools. `EXTRACTION_DROPS_AUDIT_FILE` audits a modern full response offline; retain old `EXTRACTION_AUDIT_DIR` coverage. See `docs/extraction-mode-implementation.md`.

The 2026-11-06 container table is a separate on-demand resource (`extraction_drop_containers_v1`): join toggle/panel by exact `sec-c-<containerId>`, validate declared row/group/item totals, keep explicit unpublished pools distinct from missing panels. Card links require an explicit 晶骸容器/晶骸核心 ID; never convert a 掉落组 ID. Reverse lookup preserves source quality labels and excludes proven zero-count sources by default (15001); chips expose no per-item weight or ID, so pool membership is not a probability or farming recommendation. `EXTRACTION_CONTAINERS_AUDIT_DIR` audits full local captures. Keep tab/filter/detail state isolated and old caches readable.

Bio deck pools combine the official share index table with verified PC card IDs. Missing indices stay `-1`; never infer them from card order, cardId or Lua keys. “盗取” (20351047) has no share index in the 2026-11-02 source snapshot. Publishing a complete card list without a code requires explicit `allowMissingShareCode`; never emit a partial-code Widget. Empty/invalid share codes hide the copy UI. See `docs/rift-bio-pool-followup.md`. Weapon detailed performance may be explicitly AI-derived reference data: preserve its disclaimer and platform/mode boundaries. Full-page offline audits use `WEAPON_AUDIT_DIR`, `EXTRACTION_AUDIT_DIR`, and `MAP_BIO_AUDIT_DIR`; fresh clones skip them, tracked small fixtures still run.

When adding/refactoring `androidApp/.../feature/wiki`, follow `docs/android-wiki-feature-guide.md`: split `model` / `source` / `parser` / `api` / `Screen`. BWiki HTML is not a stable API; keep parse failures diagnosable.

Wiki UI reuse: see `docs/android-wiki-ui-architecture.md`. Reuse `WikiScrollableSheet` for bounded short details, `WikiContentSheet` for long lazy content, and `WikiSelectionSheet` for searchable choices; never place an entire catalog in one lazy item. Keep section keys across refresh/empty prefetch. Image-save enqueue feedback is “已开始保存”, not completion. Bio reads are tab-scoped (PC never loads deck indexes/mobile); official index + verified PC completion are deck-only dependencies. Preserve editor drafts on same-faction data arrival; missing-index publication still requires explicit consent. Voting/deck writes are single-flight and never automatically retried; reconcile partial vote failures from server reads. Balance draft/side/sort edits must not query.

## Do not

- Commit signing material (`local.properties`, keystores) or APKs
- Edit `androidApp/build` outputs
- Treat `.agents/` as repo source of truth (gitignored). `.github/*` is also ignored **except** `.github/workflows/` (tracked — the daily live-smoke CI lives there)
