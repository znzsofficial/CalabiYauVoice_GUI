# Agent notes

Kotlin Multiplatform app (`androidApp`, `desktopApp`, `shared`, `webApp`) plus a separate Vite/Svelte 5 site in `downloadPage/` (not a Gradle module).

## Commands

Windows: `.\gradlew.bat`. macOS/Linux: `./gradlew`.

- Android compile: `.\gradlew.bat :androidApp:compileDebugKotlin`
- Desktop run: `.\gradlew.bat run`
- Web typecheck: `cd downloadPage; npm run check`
- Web local: `cd downloadPage; npm run dev` (Vite proxies `/api/wiki`, `/api/balance/*`, image/file download; it does **not** serve R2 APKs)
- Release site: `.\gradlew.bat webDeploy` → `webDist` (assembleRelease, rewrite `latest.json`, upload APK to R2) then `webPush` (build + `wrangler pages deploy`)

`downloadPage/dist/` is generated. After `npm run build`, `webStatic` copies `_headers`, `_redirects`, `downloads/latest.json`, `icon.svg`, and `src/api/_worker.js` into `dist`.

## Version / release

Keep these in the same change: `androidApp/build.gradle.kts` (`versionName`, `versionCode`), `desktopApp` `packageVersion`, About fallback string, `downloadPage/downloads/latest.json`.

`webDist` **reuses the current `changelog` in `latest.json`**. Edit changelog for the new version **before** `webDeploy`, or the site ships the previous notes.

APKs are **not** git-tracked (`*.apk` in `.gitignore`). Production files live in R2 bucket `calabiyau-releases` as `android/CalabiYauVoice-<version>.apk` plus `android/CalabiYauVoice-latest.apk`. The Pages Worker streams `/downloads/CalabiYauVoice-(latest|x.y.z).apk` from that bucket. `apkUrl` in JSON stays a same-origin path (`/downloads/CalabiYauVoice-2.1.7.apk`). Android `UpdateApi` already resolves that; do not switch it to an in-app WebView.

Do not delete `2.1.6` from `releases`. Full checklist: `docs/release-version-checklist.md`.

## downloadPage Worker

`downloadPage/src/api/_worker.js` and `downloadPage/vite.config.ts` share the same upstream constants; change both.

R2 binding is `RELEASES` in `downloadPage/wrangler.jsonc` (Pages project `calabiyauwiki`). Do not commit `downloadPage/wrangler.toml` (gitignored; dashboard download can contain secrets). `GITHUB_TOKEN` is a Pages dashboard secret, not in `wrangler.jsonc`.

## Android Wiki pages

When adding/refactoring `androidApp/.../feature/wiki`, follow `docs/android-wiki-feature-guide.md`: split `model` / `source` / `parser` / `api` / `Screen`. BWiki HTML is not a stable API; keep parse failures diagnosable.

## Do not

- Commit signing material (`local.properties`, keystores) or APKs
- Edit `androidApp/build` outputs
- Treat `.github/` or `.agents/` as repo source of truth (gitignored)
