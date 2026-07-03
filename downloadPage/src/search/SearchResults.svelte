<script lang="ts">
  import { highlightMatch, categoryDisplayName } from './utils';
  import type { SearchResult, Status } from './searchTypes';

  let { status = 'idle', query = '', resultSuggestion = '', errorMessage = '', results = [], fileSelectionEnabled = false, categorySelectionEnabled = false, categorySearchActive = false, selectionDisabled = false, selectedFiles = new Set<string>(), selectedCategoryResults = new Set<string>(), expandedCategories = new Set<string>(), categorySubcats = {}, categorySubcatLoading = new Set<string>(), categorySubcatErrors = {}, onRetry = () => {}, onSuggestion = (value: string) => {}, onToggleFile = (title: string) => {}, onToggleCategory = (title: string) => {}, onOpenLightbox = (src: string) => {}, onToggleCategoryExpanded = (title: string) => {}, onOpenCategoryFiles = (title: string) => {} }: {
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
    categorySubcats?: Record<string, string[]>;
    categorySubcatLoading?: Set<string>;
    categorySubcatErrors?: Record<string, string>;
    onRetry?: () => void;
    onSuggestion?: (value: string) => void;
    onToggleFile?: (title: string) => void;
    onToggleCategory?: (title: string) => void;
    onOpenLightbox?: (src: string) => void;
    onToggleCategoryExpanded?: (title: string) => void;
    onOpenCategoryFiles?: (title: string) => void;
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
    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); if (selectionDisabled) return; const title = (e.currentTarget as HTMLElement).dataset.title; if (title) onToggleFile(title); }
  }
  function handleToggleCategoryClick(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    if (selectionDisabled) return;
    const title = (e.currentTarget as HTMLElement).dataset.title;
    if (title) onToggleCategory(title);
  }
  function handleToggleCategoryKeydown(e: KeyboardEvent): void {
    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); if (selectionDisabled) return; const title = (e.currentTarget as HTMLElement).dataset.title; if (title) onToggleCategory(title); }
  }
  function handleLightboxClick(e: MouseEvent, image: { thumb: string; full: string }): void {
    e.preventDefault(); e.stopPropagation(); onOpenLightbox(imageFull(image));
  }
  function handleToggleCategoryExpanded(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    if (selectionDisabled) return;
    const title = (e.currentTarget as HTMLElement).dataset.title;
    if (title) onToggleCategoryExpanded(title);
  }
  function handleOpenCategoryFiles(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    const title = (e.currentTarget as HTMLElement).dataset.title;
    if (title) onOpenCategoryFiles(title);
  }
  function handleSubcatClick(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    if (selectionDisabled) return;
    const subcat = (e.currentTarget as HTMLElement).dataset.subcat;
    if (subcat) onToggleCategory(subcat);
  }
  function handleSubcatExpandClick(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    if (selectionDisabled) return;
    const subcat = (e.currentTarget as HTMLElement).dataset.subcat;
    if (subcat) onToggleCategoryExpanded(subcat);
  }
  function handleOpenSubcatFiles(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    const subcat = (e.currentTarget as HTMLElement).dataset.subcat;
    if (subcat) onOpenCategoryFiles(subcat);
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
    <div class="empty-state"><div class="empty-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 8v4"/><path d="M12 16h.01"/></svg></div><p>{errorMessage}</p><button class="btn outline" style="margin-top: 12px;" onclick={onRetry}>重试</button></div>
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
          <div class="result-title-row">{#if result.nsName}<span class="result-ns">{result.nsName}</span>{/if}<h3 class="result-title"><a class="result-title-link" href={result.url} target="_blank" rel="noopener noreferrer">{@html highlightMatch(result.title, query)}</a></h3><a class="result-open-link" href={result.url} target="_blank" rel="noopener noreferrer" aria-label={`打开 ${result.title}`}><iconify-icon icon="lucide:external-link"></iconify-icon></a></div>
          {#if result.redirecttitle}<div class="result-redirect">重定向自：<span>{@html highlightMatch(result.redirecttitle, query)}</span></div>{/if}
          {#if result.sectiontitle}<span class="result-section">§ {result.sectiontitle}</span>{/if}
          {#if !categorySearchActive}
            <p class="result-snippet">{@html cleanSnippet(resultSnippet(result))}</p>
            <div class="result-meta-row"><span title="最后编辑">{result.dateStr}</span>{#if result.wordCountStr}<span title="字数">{result.wordCountStr} 字</span>{/if}{#if result.fileSize}<span title="文件大小">{result.fileSize}</span>{:else if result.pageSizeKB}<span title="页面大小">{result.pageSizeKB}</span>{/if}</div>
          {/if}
          {#if categorySearchActive && result.ns === 14}
            <div class="category-tree-actions">
              {#if !categorySubcats[result.title] || categorySubcats[result.title].length > 0}
                <button class="category-tree-action" class:active={expandedCategories.has(result.title)} type="button" data-title={result.title} onclick={handleToggleCategoryExpanded}>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={expandedCategories.has(result.title) ? 'm18 15-6-6-6 6' : 'm6 9 6 6 6-6'}/></svg>
                  <span>{expandedCategories.has(result.title) ? '收起' : '子分类'}</span>
                  {#if categorySubcats[result.title]}<small>{categorySubcats[result.title].length}</small>{/if}
                </button>
              {/if}
              <button class="category-tree-action" type="button" data-title={result.title} onclick={handleOpenCategoryFiles}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/></svg>
                <span>查看文件</span>
              </button>
            </div>
            {#if expandedCategories.has(result.title)}
              <div class="subcategory-panel category-tree" role="tree" aria-label={`${categoryDisplayName(result.title)} 子分类`}>
                <div class="subcategory-tree-head">
                  <span class="subcategory-tree-head-icon"><iconify-icon icon="lucide:git-branch"></iconify-icon></span>
                  <span class="subcategory-tree-head-title">子分类树</span>
                  {#if categorySubcats[result.title]}
                    <span class="subcategory-tree-head-count">{categorySubcats[result.title].length} 项</span>
                  {/if}
                </div>
                {#if categorySubcatLoading.has(result.title)}
                  <div class="subcategory-loading"><span class="suggest-spinner"></span><span>加载子分类…</span></div>
                {:else if categorySubcatErrors[result.title]}
                  <span class="subcategory-error">{categorySubcatErrors[result.title]}</span>
                {:else if categorySubcats[result.title]?.length > 0}
                  <div class="subcategory-tree-list">
                    {#each categorySubcats[result.title] as subcat (subcat)}
                      <div class:checked={selectedCategoryResults.has(subcat)} class="subcategory-tree-row" role="treeitem" aria-selected={selectedCategoryResults.has(subcat)}>
                        <span class="subcategory-tree-branch" aria-hidden="true"></span>
                        {#if !categorySubcats[subcat] || categorySubcats[subcat].length > 0}
                          <button class="subcategory-tree-toggle" class:active={expandedCategories.has(subcat)} type="button" disabled={selectionDisabled} data-subcat={subcat} onclick={handleSubcatExpandClick} aria-label={`${expandedCategories.has(subcat) ? '收起' : '展开'} ${categoryDisplayName(subcat)}`}>
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={expandedCategories.has(subcat) ? 'm18 15-6-6-6 6' : 'm9 18 6-6-6-6'}/></svg>
                          </button>
                        {:else}
                          <span class="subcategory-tree-spacer" aria-hidden="true"></span>
                        {/if}
                        <button class="subcategory-tree-select" type="button" disabled={selectionDisabled} data-subcat={subcat} onclick={handleSubcatClick}>
                          <iconify-icon class="subcategory-tree-check" icon={selectedCategoryResults.has(subcat) ? 'lucide:check-square' : 'lucide:square'} aria-hidden="true"></iconify-icon>
                          <iconify-icon class="subcategory-tree-folder" icon={expandedCategories.has(subcat) ? 'lucide:folder-open' : 'lucide:folder'} aria-hidden="true"></iconify-icon>
                          <span class="subcategory-tree-name">{categoryDisplayName(subcat)}</span>
                        </button>
                        <button class="subcategory-tree-file" type="button" data-subcat={subcat} onclick={handleOpenSubcatFiles} aria-label={`查看 ${categoryDisplayName(subcat)} 文件`}>
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/></svg>
                          <span>文件</span>
                        </button>
                      </div>
                      {#if expandedCategories.has(subcat)}
                        <div class="subcategory-tree-children" role="group">
                          {#if categorySubcatLoading.has(subcat)}
                            <div class="subcategory-tree-note"><span class="suggest-spinner"></span><span>加载子分类…</span></div>
                          {:else if categorySubcatErrors[subcat]}
                            <span class="subcategory-tree-note error">{categorySubcatErrors[subcat]}</span>
                          {:else if categorySubcats[subcat]?.length > 0}
                            {#each categorySubcats[subcat] as child (child)}
                              <div class:checked={selectedCategoryResults.has(child)} class="subcategory-tree-row child" role="treeitem" aria-selected={selectedCategoryResults.has(child)}>
                                <span class="subcategory-tree-branch" aria-hidden="true"></span>
                                <span class="subcategory-tree-spacer" aria-hidden="true"></span>
                                <button class="subcategory-tree-select" type="button" disabled={selectionDisabled} data-subcat={child} onclick={handleSubcatClick}>
                                  <iconify-icon class="subcategory-tree-check" icon={selectedCategoryResults.has(child) ? 'lucide:check-square' : 'lucide:square'} aria-hidden="true"></iconify-icon>
                                  <iconify-icon class="subcategory-tree-folder" icon="lucide:folder" aria-hidden="true"></iconify-icon>
                                  <span class="subcategory-tree-name">{categoryDisplayName(child)}</span>
                                </button>
                                <button class="subcategory-tree-file" type="button" data-subcat={child} onclick={handleOpenSubcatFiles} aria-label={`查看 ${categoryDisplayName(child)} 文件`}>
                                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/></svg>
                                  <span>文件</span>
                                </button>
                              </div>
                            {/each}
                          {:else}
                            <span class="subcategory-tree-note">没有子分类</span>
                          {/if}
                        </div>
                      {/if}
                    {/each}
                  </div>
                {:else}
                  <span class="subcategory-empty">没有子分类</span>
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
  .category-tree-card::before { content: ''; position: absolute; left: 28px; top: 42px; bottom: 18px; width: 1px; background: linear-gradient(var(--border), color-mix(in srgb, var(--border) 20%, transparent)); }
  .category-tree-actions { display: flex; align-items: center; gap: 6px; margin-top: 10px; flex-wrap: wrap; }
  .category-tree-action { display: inline-flex; align-items: center; gap: 5px; padding: 5px 10px; border: 1px solid var(--border); border-radius: 6px; background: var(--background); color: var(--muted-foreground); font: inherit; font-size: 12px; cursor: pointer; transition: background-color 0.15s, border-color 0.15s, color 0.15s, transform 0.1s, box-shadow 0.2s; }
  .category-tree-action svg { width: 14px; height: 14px; flex-shrink: 0; }
  .category-tree-action small { font-size: 11px; opacity: 0.6; }
  .category-tree-action:hover { border-color: color-mix(in srgb, var(--border) 60%, var(--foreground)); color: var(--foreground); background: var(--accent); box-shadow: 0 8px 18px -14px color-mix(in srgb, var(--foreground) 18%, transparent); }
  .category-tree-action:active { transform: scale(0.98); }
  .category-tree-action:focus-visible { outline: none; box-shadow: 0 0 0 2px var(--background), 0 0 0 4px var(--ring); }
  .category-tree-action.active { border-color: var(--primary); color: var(--foreground); background: var(--accent); }
  .subcategory-panel { margin-top: 12px; padding: 10px; border: 1px solid var(--border); border-radius: 12px; background: linear-gradient(180deg, color-mix(in srgb, var(--card) 88%, var(--muted)), color-mix(in srgb, var(--muted) 86%, transparent)); box-shadow: inset 0 1px 0 color-mix(in srgb, var(--foreground) 3%, transparent); }
  .subcategory-tree-head { display: flex; align-items: center; gap: 8px; min-height: 34px; margin-bottom: 8px; padding: 0 4px 8px; border-bottom: 1px solid color-mix(in srgb, var(--border) 72%, transparent); color: var(--muted-foreground); }
  .subcategory-tree-head-icon { display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px; border: 1px solid var(--border); border-radius: 7px; background: var(--background); color: var(--foreground); }
  .subcategory-tree-head-icon iconify-icon { font-size: 14px; }
  .subcategory-tree-head-title { color: var(--foreground); font-size: 13px; font-weight: 600; }
  .subcategory-tree-head-count { margin-left: auto; font-size: 12px; }
  .subcategory-loading, .subcategory-empty, .subcategory-error { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--muted-foreground); padding: 4px 0; }
  .subcategory-loading, .subcategory-empty, .subcategory-error { margin-left: 14px; }
  .subcategory-tree-list { position: relative; display: flex; flex-direction: column; gap: 4px; }
  .subcategory-tree-list::before { content: ''; position: absolute; left: 22px; top: 3px; bottom: 20px; width: 1px; background: color-mix(in srgb, var(--border) 86%, transparent); }
  .subcategory-tree-row { position: relative; display: grid; grid-template-columns: 34px 34px minmax(0, 1fr) auto; align-items: center; gap: 6px; min-height: 44px; padding: 3px 8px 3px 0; border: 1px solid transparent; border-radius: 10px; color: var(--muted-foreground); transition: background-color 0.15s, border-color 0.15s, color 0.15s, transform 0.15s; }
  .subcategory-tree-row.child { margin-left: 36px; grid-template-columns: 34px 34px minmax(0, 1fr) auto; }
  .subcategory-tree-row:hover { background: color-mix(in srgb, var(--card) 88%, transparent); border-color: color-mix(in srgb, var(--border) 86%, var(--foreground)); color: var(--foreground); transform: translateX(1px); }
  .subcategory-tree-row.checked { color: var(--foreground); background: color-mix(in srgb, var(--primary) 8%, var(--card)); border-color: color-mix(in srgb, var(--primary) 24%, var(--border)); }
  .subcategory-tree-branch { position: relative; width: 22px; height: 1px; margin-left: 22px; background: color-mix(in srgb, var(--border) 90%, transparent); }
  .subcategory-tree-branch::after { content: ''; position: absolute; right: -2px; top: -2px; width: 5px; height: 5px; border-radius: 999px; background: var(--border); }
  .subcategory-tree-toggle, .subcategory-tree-file, .subcategory-tree-select { appearance: none; border: 0; background: transparent; color: inherit; font: inherit; cursor: pointer; }
  .subcategory-tree-toggle, .subcategory-tree-spacer { display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px; border-radius: 8px; }
  .subcategory-tree-file { display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-width: 58px; height: 30px; padding: 0 9px; border: 1px solid var(--border); border-radius: 8px; background: var(--background); font-size: 12px; }
  .subcategory-tree-toggle svg, .subcategory-tree-file svg { width: 15px; height: 15px; }
  .subcategory-tree-toggle:hover, .subcategory-tree-file:hover { background: var(--card); border-color: color-mix(in srgb, var(--border) 70%, var(--foreground)); color: var(--foreground); }
  .subcategory-tree-toggle.active { color: var(--foreground); background: var(--card); box-shadow: inset 0 0 0 1px var(--border); }
  .subcategory-tree-toggle:disabled, .subcategory-tree-select:disabled { cursor: not-allowed; opacity: 0.55; }
  .subcategory-tree-select { display: inline-flex; align-items: center; gap: 9px; min-width: 0; height: 36px; padding: 4px 6px; text-align: left; border-radius: 8px; }
  .subcategory-tree-select:hover { background: color-mix(in srgb, var(--accent) 58%, transparent); }
  .subcategory-tree-check { flex: 0 0 auto; width: 18px; height: 18px; color: var(--muted-foreground); }
  .subcategory-tree-folder { flex: 0 0 auto; width: 16px; height: 16px; color: color-mix(in srgb, var(--muted-foreground) 78%, var(--foreground)); }
  .subcategory-tree-row.checked .subcategory-tree-check { color: var(--primary); }
  .subcategory-tree-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 500; }
  .subcategory-tree-children { position: relative; display: flex; flex-direction: column; gap: 4px; margin: 2px 0 4px 36px; }
  .subcategory-tree-children::before { content: ''; position: absolute; left: 22px; top: 0; bottom: 20px; width: 1px; background: color-mix(in srgb, var(--border) 82%, transparent); }
  .subcategory-tree-note { display: flex; align-items: center; gap: 6px; min-height: 34px; margin-left: 68px; font-size: 12px; color: var(--muted-foreground); }
  .subcategory-tree-note.error { color: var(--destructive); }

  @media (prefers-color-scheme: dark) {
    .category-tree-action:hover,
    .subcategory-panel {
      border-color: color-mix(in srgb, var(--primary) 30%, var(--border));
    }
    .subcategory-tree-row:hover {
      background: color-mix(in srgb, var(--accent) 72%, transparent);
    }
    .subcategory-tree-row.checked {
      background: color-mix(in srgb, var(--primary) 12%, transparent);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .subcategory-tree-row {
      transition: background-color 0.15s, border-color 0.15s, color 0.15s;
    }
    .subcategory-tree-row:hover {
      transform: none;
    }
  }

  @media (max-width: 640px) {
    .category-tree-card { padding-left: 36px; }
    .category-tree-card::before { left: 22px; }
    .subcategory-panel { padding: 8px 6px 8px 8px; }
    .subcategory-tree-row { grid-template-columns: 24px 30px minmax(0, 1fr) 34px; min-height: 40px; gap: 4px; }
    .subcategory-tree-row.child { margin-left: 20px; grid-template-columns: 24px 30px minmax(0, 1fr) 34px; }
    .subcategory-tree-children { margin-left: 20px; }
    .subcategory-tree-list::before, .subcategory-tree-children::before { left: 16px; }
    .subcategory-tree-branch { width: 16px; margin-left: 16px; }
    .subcategory-tree-file { min-width: 30px; width: 30px; padding: 0; }
    .subcategory-tree-file span { display: none; }
    .subcategory-tree-name { font-size: 12.5px; }
  }
</style>
