# Agent notes

Kotlin Multiplatform app (`androidApp`, `desktopApp`, `shared`, `webApp`) plus a separate Vite/Svelte 5 site in `downloadPage/` (not a Gradle module).

## Commands

Windows: `.\gradlew.bat`. macOS/Linux: `./gradlew`.

- Android compile: `.\gradlew.bat :androidApp:compileDebugKotlin`
- Desktop run: `.\gradlew.bat run`
- Web typecheck: `cd downloadPage; npm run check`
- Web local: `cd downloadPage; npm run dev` (Vite proxies `/api/wiki`, `/api/balance/*`, image/file download; it does **not** serve R2 APKs)
- Release site: `.\gradlew.bat webDeploy` → `webDist` (assembleRelease, rewrite `latest.json`, upload APK to R2) then `webPush` (build + `wrangler pages deploy`)

`downloadPage/dist/` is generated. `npm run build` builds the site and bundles `src/api/_worker.js` with esbuild into `dist/_worker.js`. `webStatic` copies `_headers`, `_redirects`, `downloads/latest.json`, and `icon.svg`; never overwrite the bundled Worker with its source entrypoint. Worker integration tests: `npm run test:worker` in `downloadPage/`.

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

R2 binding is `RELEASES` in `downloadPage/wrangler.jsonc` (Pages project `calabiyauwiki`). Do not commit `downloadPage/wrangler.toml` (gitignored; dashboard download can contain secrets). `GITHUB_TOKEN` is a Pages dashboard secret, not in `wrangler.jsonc`.

## Android Wiki pages

When adding/refactoring `androidApp/.../feature/wiki`, follow `docs/android-wiki-feature-guide.md`: split `model` / `source` / `parser` / `api` / `Screen`. BWiki HTML is not a stable API; keep parse failures diagnosable.

## Do not

- Commit signing material (`local.properties`, keystores) or APKs
- Edit `androidApp/build` outputs
- Treat `.github/` or `.agents/` as repo source of truth (gitignored)
