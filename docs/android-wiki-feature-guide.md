# Android Wiki Feature Guide

This document records the conventions used by native Android Wiki pages. Use it when adding or refactoring pages under `androidApp/src/main/kotlin/com/nekolaska/calabiyau/feature/wiki`.

## Goals

- Keep native Wiki pages predictable to maintain.
- Separate network/cache, parsing, data models, API orchestration, and UI.
- Make parser failures diagnosable because BWiki HTML is not a stable API.
- Prefer small page-specific implementations over a new generic abstraction unless at least two pages benefit immediately.

## Standard Package Layout

Most native Wiki features should use this layout:

```text
feature/wiki/<feature>/
├── <Feature>Screen.kt
├── api/
│   └── <Feature>Api.kt
├── model/
│   └── <Feature>Models.kt
├── parser/
│   └── <Feature>Parsers.kt
└── source/
    └── <Feature>RemoteSource.kt
```

Use shorter layouts only for genuinely tiny pages. If a file starts mixing network requests, parsing, models, and UI, split it before adding more behavior.

## Responsibilities

### `model`

- Contains immutable data classes and page constants.
- Keep UI-only state out of model files.
- Prefer explicit page objects such as `AchievementPage` or `PlayerLevelPage` over raw lists when the page has metadata, source URL, notes, or sections.
- If the screen uses `ApiResourceContent`, define what "empty" means in the screen with `isDataEmpty` when the model is not a `Collection` or `Map`.

### `source`

- Owns remote access and disk cache keys.
- Returns raw source data plus cache metadata.
- Prefer `WikiParseSource.fetchHtml`, `fetchWikitext`, or `fetchHtmlAndWikitext` for normal MediaWiki page parse requests.
- Do not use `WikiParseSource` for non-standard requests such as `action=ask`, `allmessages`, custom voting APIs, or `parse&text=...` unless its contract is expanded deliberately.
- Cache keys must be stable and versioned when parser behavior changes in a way that can make old cached payloads invalid.

Typical source result:

```kotlin
typealias ExampleSourceResult = WikiHtmlPageSourceResult
```

For simple HTML-only parse pages, use `fetchWikiHtmlPage(...)` instead of repeating the `WikiParseSource.fetchHtml(...)` mapping by hand.

### `parser`

- Converts raw HTML/wikitext into model objects.
- Must not perform network requests.
- Should tolerate missing optional fields.
- Should not throw for normal content drift; return partial data when possible.
- Use parser diagnostics for list-like pages where missing content is likely.
- Prefer small extraction helpers over one long selector-heavy function.
- Avoid relying on a single CSS class when the same content can appear as gallery boxes, plain `a.image`, tabs, or tables.

Parser robustness checklist:

- Skip page chrome or intro images that appear before the first content heading when appropriate.
- Deduplicate by stable IDs such as file name, page title, item name plus section, or URL.
- Normalize whitespace in captions and text fields.
- Treat `srcset`, `data-src`, and `src` carefully. Do not store thumbnail URLs as original image URLs unless thumbnails are the intended data.
- Support protocol-relative URLs (`//...`) and site-relative URLs (`/...`) when extracting direct links.
- For image pages, prefer direct original URLs from HTML only when they are not `/thumb/`; otherwise fall back to `imageinfo`.

### `api`

- Orchestrates memory cache, source fetch, parsing, image URL resolution, and `ApiResult` mapping.
- Runs network and parse work on `Dispatchers.IO`.
- Registers memory caches with `MemoryCacheRegistry` when local API-level caches are used.
- Returns `ApiResult.Success` with `isOffline` and `cacheAgeMs` from the source result.
- Returns `ApiResult.Error` with meaningful `ErrorKind` values.

Typical API flow:

```kotlin
object ExampleApi {
    init { MemoryCacheRegistry.register("ExampleApi", ::clearMemoryCache) }

    private var cachedPage: ExamplePage? = null

    fun clearMemoryCache() { cachedPage = null }

    suspend fun fetch(
        forceRefresh: Boolean = false,
        cacheOnly: Boolean = false,
        allowMemoryCache: Boolean = true
    ): ApiResult<ExamplePage> {
        if (!forceRefresh && allowMemoryCache) cachedPage?.let { return ApiResult.Success(it) }
        if (cacheOnly) return fetchFromCache()
        return fetchFromNetwork(forceRefresh).also {
            if (it is ApiResult.Success) cachedPage = it.value
        }
    }
}
```

For stale-while-revalidate screens, the cache prefetch may populate API memory cache before the network validation starts. The validation fetch must therefore pass `allowMemoryCache = false`, otherwise it can be short-circuited by the cached prefetch result and never verify fresh data.

### `Screen`

- Owns UI state only: filters, selected tabs, dialogs, preview targets, selected section, etc.
- Use `rememberLoadState` and `ApiResourceContent` for standard API-backed pages.
- For cache-first pages, use `rememberLoadState(initial, cachedFetch, fetch)` and make the validation fetch bypass API memory cache.
- Provide `isDataEmpty` for page model objects; the default only treats `null`, `Collection`, and `Map` as empty.
- Use `BackNavButton`, `RefreshActionButton`, and `OpenWikiActionButton` for top app bar actions.
- Use `WikiListSkeleton` or page-specific skeletons for initial loading, not a spinner-only state, when the page is list-heavy.
- Use `ImagePreviewDialog` and `PreviewImage` for ordinary URL/URI image previews.
- Keep special image preview/save flows local only when the feature has custom confirmation, bitmap data, or non-standard storage behavior.

## UI Conventions

- Top app bar actions should use shared components: `RefreshActionButton` and `OpenWikiActionButton`.
- Do not replace content-level actions with top-bar components. For example, a card's external-link icon or random button has different semantics.
- `Modifier` should be the first parameter for reusable composables when practical.
- Prefer stable Lazy keys: URL, page title, item ID, or a section name plus item name.
- Avoid large top statistic cards unless they add decision-making value.
- Do not put long, flat lists on the Hub home page. Prefer second-level aggregate pages.

## Gallery Pages

Gallery-like pages include wallpapers, stickers, and comics. Their parser must handle several shapes:

- Plain `a.image` links.
- MediaWiki `li.gallerybox` entries.
- `resp-tabs` panes.
- Bootstrap-like tab panes.
- Mixed panes that contain both `gallerybox` entries and standalone images.

Gallery metadata conventions:

- `caption`: primary display title.
- `description`: secondary text such as sticker flavor text.
- `obtainMethod`: acquisition source parsed separately from sticker gallery text.
- `directImageUrl`: optional URL parsed directly from HTML. If it is absent or points to a thumbnail, resolve via `imageinfo` in the API layer.

## Cache Conventions

- Disk cache belongs in `source` through `OfflineCache` or `WikiParseSource`.
- Memory cache belongs in `api` and must be registered with `MemoryCacheRegistry`.
- If parsed output depends on secondary requests, cache those secondary requests too. Gallery image URL resolution is the common example.
- Use versioned cache keys when cached HTML or wikitext can produce invalid output after a parser change.

Cache-first screen pattern:

```kotlin
val state = rememberLoadState(
    initial = ExamplePage.EMPTY,
    cachedFetch = { ExampleApi.fetch(cacheOnly = true) },
    fetch = { force -> ExampleApi.fetch(forceRefresh = force, allowMemoryCache = false) }
)
```

The `cacheOnly` path must not perform network requests, including secondary requests such as `imageinfo` or module wikitext. If secondary data is unavailable in disk cache, return partial data or an empty secondary map and let the validation fetch fill it.

## Error Handling

- Network/cache miss: `ErrorKind.NETWORK`.
- HTML/wikitext found but no expected content: `ErrorKind.NOT_FOUND`.
- Unexpected parser or schema issue: `ErrorKind.PARSE` when identifiable, otherwise `toErrorKind()`.
- Keep user messages specific enough to identify the failing feature.

## Standardization Opportunities

These are good candidates for future cleanup:

- A shared `WikiPageSourceResult` for simple HTML-only page sources.
- A helper for `ApiResult` memory-cache orchestration to reduce repeated `fetch` / `fetchFromNetwork` boilerplate.
- Parser fixture tests for gallery, achievements, player levels, oath, imprints, BGM, and map details.
- A small diagnostics model for parser counts, such as section count, item count, and image count.
- A page model convention for `isEmpty()` so screens can pass `isDataEmpty = Page::isEmpty` consistently.

Do not introduce these abstractions before they remove real duplication. Prefer documenting the pattern first, then extracting after two or three features need the same behavior.
