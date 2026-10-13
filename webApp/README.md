# Archived Compose Web UI prototype

**Status: archived / unmaintained as of 2026-10-13. Not a released Web client.**

The active website, Wiki navigation, search, voice playback and bulk-download tools
live in [`../downloadPage/`](../downloadPage/) and are deployed with `webPush` / `webDeploy`.
This directory is retained for reference and optional Kotlin JS/Wasm experiments.

## What this prototype actually does

- Renders a standalone Compose UI with hard-coded `sampleItems()` data.
- Filters those examples locally; it does not query BWiki.
- Simulates a download by setting progress to 42%; it does not download files,
  manage a real queue, or generate ZIP archives.
- Opens the production website when the download-page button is clicked.
- Does not depend on `shared`, Android or Desktop screens. Those cannot simply
  be copied here: the shared layer currently uses JVM/Android dependencies.

Both JS and Wasm Kotlin compilation passed when archived. This is not a promise
of production bundling, browser compatibility or future dependency compatibility.
There is no dedicated Web prototype test suite or release pipeline.

## Optional local experimentation

The module is **excluded by default** from Gradle builds and IDE project sync.
From the repository root, explicitly opt in:

```powershell
.\gradlew.bat -PincludeArchivedWebApp=true :webApp:compileKotlinJs :webApp:compileKotlinWasmJs
.\gradlew.bat -PincludeArchivedWebApp=true :webApp:wasmJsBrowserDevelopmentRun
```

For macOS/Linux replace `.\gradlew.bat` with `./gradlew`.
Do not put `includeArchivedWebApp=true` in the project's `gradle.properties`;
that would re-enable this unmaintained module for every normal build.
Do not deploy its output over the Svelte site or use it as the release download page.

The approximately 16.4 MB CJK font is intentionally retained for the existing
demo's Chinese rendering. It is not a production asset recommendation; any future
experiment should evaluate font subsetting and actual browser startup cost.

## If revisited in the future

Start with a concrete requirement that the Svelte tools do not already meet,
such as a browser-native Wiki client sharing maintained app UI. First design a
browser-compatible data/platform layer, then implement a small real feature and
validate its loading cost, downloads, accessibility and browser behavior.
Do not treat this UI mock-up as an almost-finished downloader.

---

## 中文说明

此模块已归档、停止维护，保留源码用于参考或实验；默认不参与 Gradle 构建和 IDE 同步。
界面使用假数据，搜索只是本地筛选，下载进度是模拟的，不能下载资源。
正式网页工具位于 `downloadPage/`。需要研究时使用上面的显式启用命令，
不要把此原型重新接入默认构建或替换正式站点。
