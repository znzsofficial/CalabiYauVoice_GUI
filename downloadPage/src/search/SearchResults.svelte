<script lang="ts">
  import CategoryTreeNode from './CategoryTreeNode.svelte';
  import { highlightMatch, categoryDisplayName } from './utils';
  import type { SearchResult, Status } from './searchTypes';

  let {
    status = 'idle' as Status,
    query = '',
    resultSuggestion = '',
    errorMessage = '',
    results = [] as SearchResult[],
    fileSelectionEnabled = false,
    categorySelectionEnabled = false,
    categorySearchActive = false,
    selectionDisabled = false,
    selectedFiles = new Set<string>(),
    selectedCategoryResults = new Set<string>(),
    expandedCategories = new Set<string>(),
    collapsedRootCategories = new Set<string>(),
    categorySubcats = {} as Record<string, string[]>,
    categorySubcatLoading = new Set<string>(),
    categorySubcatErrors = {} as Record<string, string>,
    onRetry = () => {},
    onSuggestion = (_value: string) => {},
    onToggleFile = (_title: string) => {},
    onToggleCategory = (_title: string) => {},
    onOpenLightbox = (_src: string) => {},
    onToggleCategoryExpanded = (_title: string) => {},
    onToggleRootCollapsed = (_title: string) => {},
    onOpenCategoryFiles = (_title: string) => {},
    onRetryCategorySubcats = (_title: string) => {},
  }: {
    status?: Status;
    query?: string;
    resultSuggestion?: string;
    errorMessage?: string;
    results?: SearchResult[];
    fileSelectionEnabled?: boolean;
    categorySelectionEnabled?: boolean;
    categorySearchActive?: boolean;
    selectionDisabled?: boolean;
    selectedFiles?: Set<string>;
    selectedCategoryResults?: Set<string>;
    expandedCategories?: Set<string>;
    collapsedRootCategories?: Set<string>;
    categorySubcats?: Record<string, string[]>;
    categorySubcatLoading?: Set<string>;
    categorySubcatErrors?: Record<string, string>;
    onRetry?: () => void;
    onSuggestion?: (value: string) => void;
    onToggleFile?: (title: string) => void;
    onToggleCategory?: (title: string) => void;
    onOpenLightbox?: (src: string) => void;
    onToggleCategoryExpanded?: (title: string) => void;
    onToggleRootCollapsed?: (title: string) => void;
    onOpenCategoryFiles?: (title: string) => void;
    onRetryCategorySubcats?: (title: string) => void;
  } = $props();

  function cleanSnippet(html: string): string {
    const div = document.createElement('div');
    div.innerHTML = html || '';
    div.querySelectorAll('.searchmatch').forEach(element => {
      const mark = document.createElement('mark');
      mark.innerHTML = element.innerHTML;
      element.replaceWith(mark);
    });
    return div.innerHTML;
  }

  function imageFull(image: SearchResult['image']): string {
    return image?.full || '';
  }

  function resultSnippet(result: SearchResult): string {
    return result.snippet || '';
  }

  function handleToggleFileClick(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    if (selectionDisabled) return;
    const title = (e.currentTarget as HTMLElement).dataset.title;
    if (title) onToggleFile(title);
  }

  function handleToggleFileKeydown(e: KeyboardEvent): void {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault(); e.stopPropagation();
      if (selectionDisabled) return;
      const title = (e.currentTarget as HTMLElement).dataset.title;
      if (title) onToggleFile(title);
    }
  }

  function handleToggleCategoryClick(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    if (selectionDisabled) return;
    const title = (e.currentTarget as HTMLElement).dataset.title;
    if (title) onToggleCategory(title);
  }

  function handleToggleCategoryKeydown(e: KeyboardEvent): void {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault(); e.stopPropagation();
      if (selectionDisabled) return;
      const title = (e.currentTarget as HTMLElement).dataset.title;
      if (title) onToggleCategory(title);
    }
  }

  function handleLightboxClick(e: MouseEvent, image: { thumb: string; full: string }): void {
    e.preventDefault(); e.stopPropagation();
    onOpenLightbox(imageFull(image));
  }

  function handleToggleRootCollapsed(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    const title = (e.currentTarget as HTMLElement).dataset.title;
    if (title) onToggleRootCollapsed(title);
  }

  function handleOpenCategoryFiles(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    const title = (e.currentTarget as HTMLElement).dataset.title;
    if (title) onOpenCategoryFiles(title);
  }

  function handleRetryRootSubcats(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    const title = (e.currentTarget as HTMLElement).dataset.title;
    if (title) onRetryCategorySubcats(title);
  }

  function rootChildCount(title: string): number | null {
    const children = categorySubcats[title];
    return Array.isArray(children) ? children.length : null;
  }

  function rootKnownEmpty(title: string): boolean {
    const children = categorySubcats[title];
    return Array.isArray(children) && children.length === 0;
  }

  function rootCollapsed(title: string): boolean {
    return collapsedRootCategories.has(title);
  }
</script>

<div class="results" id="results">
  {#if status === 'idle'}
    <div class="placeholder"><div class="placeholder-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg></div><p>输入关键词开始搜索卡拉彼丘 Wiki</p></div>
  {:else if status === 'loading'}
    {#each Array(5) as _}<div class="result-card skeleton-card"><div class="result-body"><div class="skeleton-line" style="width: 40%; height: 18px;"></div><div class="skeleton-line" style="width: 100%; height: 14px; margin-top: 8px;"></div><div class="skeleton-line" style="width: 80%; height: 14px; margin-top: 4px;"></div><div class="skeleton-line" style="width: 30%; height: 12px; margin-top: 8px;"></div></div></div>{/each}
  {:else if status === 'empty'}
    <div class="empty-state"><div class="empty-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/><path d="m8 11 6 0"/></svg></div><p>未找到「{query}」相关结果</p>{#if resultSuggestion}<p class="empty-hint">你是不是要搜：<button class="suggestion-link" onclick={() => onSuggestion(resultSuggestion)}>{resultSuggestion}</button></p>{:else}<p class="empty-hint">试试换个关键词，或检查拼写</p>{/if}</div>
  {:else if status === 'error'}
    <div class="notice-card notice-card-center" role="alert">
      <div class="notice-card-glow"></div>
      <div class="notice-card-head">
        <span class="notice-card-icon error"><iconify-icon icon="lucide:alert-circle"></iconify-icon></span>
        <span>
          <strong class="notice-card-title">搜索失败</strong>
          <small class="notice-card-desc">{errorMessage}</small>
        </span>
      </div>
      <div class="notice-card-actions">
        <button class="btn outline" type="button" onclick={onRetry}>重试</button>
      </div>
    </div>
  {:else}
    {#each results as result (result.title)}
      <article class:category-tree-card={categorySearchActive && result.ns === 14} class="result-card" style={`animation-delay: ${result.delay}`}>
        {#if fileSelectionEnabled && result.file}
          <div class:disabled={selectionDisabled} class="checkbox-container result-select" role="checkbox" aria-disabled={selectionDisabled} aria-checked={selectedFiles.has(result.title)} aria-label={`选择 ${result.title}`} tabindex={selectionDisabled ? -1 : 0} data-title={result.title} onclick={handleToggleFileClick} onkeydown={handleToggleFileKeydown}>
            <iconify-icon icon={selectedFiles.has(result.title) ? 'lucide:check-square' : 'lucide:square'}></iconify-icon>
          </div>
        {/if}
        {#if categorySelectionEnabled && result.ns === 14}
          <div class:disabled={selectionDisabled} class="checkbox-container result-select" role="checkbox" aria-disabled={selectionDisabled} aria-checked={selectedCategoryResults.has(result.title)} aria-label={`选择分类 ${result.title}`} tabindex={selectionDisabled ? -1 : 0} data-title={result.title} onclick={handleToggleCategoryClick} onkeydown={handleToggleCategoryKeydown}>
            <iconify-icon icon={selectedCategoryResults.has(result.title) ? 'lucide:check-square' : 'lucide:square'}></iconify-icon>
          </div>
        {/if}
        {#if result.image}<button type="button" class="result-thumb" onclick={(e) => handleLightboxClick(e, result.image!)}><img src={result.image.thumb} alt="" loading="lazy"></button>{/if}
        <div class="result-body">
          <div class="result-title-row">
            {#if result.nsName}<span class="result-ns">{result.nsName}</span>{/if}
            <h3 class="result-title"><a class="result-title-link" href={result.url} target="_blank" rel="noopener noreferrer">{@html highlightMatch(result.title, query)}</a></h3>
            <a class="result-open-link" href={result.url} target="_blank" rel="noopener noreferrer" aria-label={`打开 ${result.title}`}><iconify-icon icon="lucide:external-link"></iconify-icon></a>
            {#if categorySearchActive && result.ns === 14}
              <button class="category-item-files" type="button" data-title={result.title} onclick={handleOpenCategoryFiles} title="查看文件">
                <iconify-icon icon="lucide:files"></iconify-icon>
                <span>查看文件</span>
              </button>
            {/if}
          </div>
          {#if result.redirecttitle}<div class="result-redirect">重定向自：<span>{@html highlightMatch(result.redirecttitle, query)}</span></div>{/if}
          {#if result.sectiontitle}<span class="result-section">§ {result.sectiontitle}</span>{/if}
          {#if !categorySearchActive}
            <p class="result-snippet">{@html cleanSnippet(resultSnippet(result))}</p>
            <div class="result-meta-row"><span title="最后编辑">{result.dateStr}</span>{#if result.wordCountStr}<span title="字数">{result.wordCountStr} 字</span>{/if}{#if result.fileSize}<span title="文件大小">{result.fileSize}</span>{:else if result.pageSizeKB}<span title="页面大小">{result.pageSizeKB}</span>{/if}</div>
          {/if}
          {#if categorySearchActive && result.ns === 14}
            {@const isCollapsed = rootCollapsed(result.title)}
            {@const childCount = rootChildCount(result.title)}
            {@const knownEmpty = rootKnownEmpty(result.title)}
            {#if knownEmpty}
              <div class="category-tree-hint-row">无子分类</div>
            {:else}
              <div class="subcategory-panel" class:collapsed={isCollapsed} role="tree" aria-label={`${categoryDisplayName(result.title)} 子分类`}>
                <div class="subcategory-tree-head">
                  <span class="subcategory-tree-head-icon"><iconify-icon icon="lucide:folder-tree"></iconify-icon></span>
                  <span class="subcategory-tree-head-title">直接子分类</span>
                  {#if childCount != null}
                    <span class="subcategory-tree-head-count">{childCount} 项</span>
                  {:else if categorySubcatLoading.has(result.title)}
                    <span class="subcategory-tree-head-count">加载中…</span>
                  {/if}
                  <button
                    class="subcategory-collapse-btn"
                    type="button"
                    data-title={result.title}
                    onclick={handleToggleRootCollapsed}
                    aria-expanded={!isCollapsed}
                    title={isCollapsed ? '展开子分类' : '折叠子分类'}
                  >
                    <iconify-icon icon={isCollapsed ? 'lucide:chevron-down' : 'lucide:chevron-up'}></iconify-icon>
                    <span>{isCollapsed ? '展开' : '折叠'}</span>
                  </button>
                </div>
                {#if !isCollapsed}
                  {#if categorySubcatLoading.has(result.title) && !categorySubcats[result.title]}
                    <div class="subcategory-loading"><span class="suggest-spinner"></span><span>加载子分类…</span></div>
                  {:else if categorySubcatErrors[result.title]}
                    <div class="subcategory-error">
                      <span>{categorySubcatErrors[result.title]}</span>
                      <button class="category-tree-action" type="button" data-title={result.title} onclick={handleRetryRootSubcats}>重试</button>
                    </div>
                  {:else if categorySubcats[result.title]?.length > 0}
                    <div class="subcategory-tree-list">
                      {#each categorySubcats[result.title] as subcat (subcat)}
                        <CategoryTreeNode
                          title={subcat}
                          depth={0}
                          {selectionDisabled}
                          {selectedCategoryResults}
                          {expandedCategories}
                          {categorySubcats}
                          {categorySubcatLoading}
                          {categorySubcatErrors}
                          {onToggleCategory}
                          {onToggleCategoryExpanded}
                          {onOpenCategoryFiles}
                        />
                      {/each}
                    </div>
                  {:else}
                    <span class="subcategory-empty">没有子分类</span>
                  {/if}
                {/if}
              </div>
            {/if}
          {/if}
          {#if result.categories.length > 0}<div class="result-cats">{#each result.categories.slice(0, 3) as category}<span class="cat-tag">{category}</span>{/each}</div>{/if}
        </div>
      </article>
    {/each}
  {/if}
</div>

<style>
  .category-tree-card { padding-left: 44px; }
  .category-tree-card::before {
    content: '';
    position: absolute;
    left: 28px;
    top: 42px;
    bottom: 18px;
    width: 1px;
    background: linear-gradient(var(--border), color-mix(in srgb, var(--border) 20%, transparent));
  }
  .category-tree-card .result-title-row {
    align-items: center;
    gap: 8px;
    flex-wrap: nowrap;
  }
  .category-tree-card .result-title {
    min-width: 0;
    flex: 1 1 auto;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .category-tree-card .result-title-link {
    display: inline;
  }
  .category-item-files {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    margin-left: auto;
    height: 30px;
    padding: 0 10px;
    border: 1px solid var(--border);
    border-radius: 7px;
    background: var(--primary);
    color: var(--primary-foreground);
    font: inherit;
    font-size: 12px;
    cursor: pointer;
    flex-shrink: 0;
    transition: filter 0.15s, transform 0.1s;
  }
  .category-item-files iconify-icon { font-size: 14px; }
  .category-item-files:hover { filter: brightness(1.05); }
  .category-item-files:active { transform: scale(0.98); }
  .category-item-files:focus-visible { outline: none; box-shadow: 0 0 0 2px var(--background), 0 0 0 4px var(--ring); }
  .category-tree-hint-row {
    margin-top: 8px;
    font-size: 12px;
    color: var(--muted-foreground);
  }
  .category-tree-action {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 5px 10px;
    border: 1px solid var(--border);
    border-radius: 6px;
    background: var(--background);
    color: var(--muted-foreground);
    font: inherit;
    font-size: 12px;
    cursor: pointer;
    transition: background-color 0.15s, border-color 0.15s, color 0.15s;
  }
  .category-tree-action:hover {
    border-color: color-mix(in srgb, var(--border) 60%, var(--foreground));
    color: var(--foreground);
    background: var(--accent);
  }
  .subcategory-panel {
    margin-top: 10px;
    padding: 8px 10px;
    border: 1px solid var(--border);
    border-radius: 12px;
    background: linear-gradient(180deg, color-mix(in srgb, var(--card) 88%, var(--muted)), color-mix(in srgb, var(--muted) 86%, transparent));
    box-shadow: inset 0 1px 0 color-mix(in srgb, var(--foreground) 3%, transparent);
  }
  .subcategory-panel.collapsed {
    padding-bottom: 8px;
  }
  .subcategory-tree-head {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 32px;
    margin-bottom: 0;
    padding: 0 2px;
    color: var(--muted-foreground);
  }
  .subcategory-panel:not(.collapsed) .subcategory-tree-head {
    margin-bottom: 8px;
    padding-bottom: 8px;
    border-bottom: 1px solid color-mix(in srgb, var(--border) 72%, transparent);
  }
  .subcategory-tree-head-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border: 1px solid var(--border);
    border-radius: 7px;
    background: var(--background);
    color: var(--foreground);
    flex-shrink: 0;
  }
  .subcategory-tree-head-icon iconify-icon { font-size: 14px; }
  .subcategory-tree-head-title { color: var(--foreground); font-size: 13px; font-weight: 600; }
  .subcategory-tree-head-count { font-size: 12px; color: var(--muted-foreground); }
  .subcategory-collapse-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-left: auto;
    height: 28px;
    padding: 0 8px;
    border: 1px solid var(--border);
    border-radius: 7px;
    background: var(--background);
    color: var(--muted-foreground);
    font: inherit;
    font-size: 12px;
    cursor: pointer;
    flex-shrink: 0;
    transition: background-color 0.15s, border-color 0.15s, color 0.15s;
  }
  .subcategory-collapse-btn iconify-icon { font-size: 14px; }
  .subcategory-collapse-btn:hover {
    color: var(--foreground);
    border-color: color-mix(in srgb, var(--border) 55%, var(--foreground));
    background: var(--accent);
  }
  .subcategory-collapse-btn:focus-visible {
    outline: none;
    box-shadow: 0 0 0 2px var(--background), 0 0 0 4px var(--ring);
  }
  .subcategory-loading,
  .subcategory-empty,
  .subcategory-error {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: var(--muted-foreground);
    padding: 6px 4px;
  }
  .subcategory-error { color: var(--destructive, #dc2626); flex-wrap: wrap; }
  .subcategory-tree-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
    max-height: min(420px, 50vh);
    overflow: auto;
    padding-right: 2px;
  }

  @media (prefers-color-scheme: dark) {
    .category-tree-action:hover,
    .subcategory-panel {
      border-color: color-mix(in srgb, var(--primary) 30%, var(--border));
    }
  }

  @media (max-width: 640px) {
    .category-tree-card { padding-left: 36px; }
    .category-tree-card::before { left: 22px; }
    .subcategory-panel { padding: 8px; }
    .category-item-files span,
    .subcategory-collapse-btn span { display: none; }
    .category-item-files,
    .subcategory-collapse-btn {
      width: 30px;
      padding: 0;
      justify-content: center;
    }
  }
</style>
