<script lang="ts">
  import { highlightMatch, categoryDisplayName } from './utils';

  type Status = 'idle' | 'loading' | 'empty' | 'error' | 'ready';
  type FileAsset = { mime?: string; size?: number; url: string };
  type ResultImage = { thumb: string; full: string; size?: number };
  type SearchResult = {
    title: string;
    ns: number;
    image?: ResultImage;
    file?: FileAsset;
    categories: string[];
    url: string;
    nsName: string;
    dateStr: string;
    pageSizeKB: string;
    fileSize: string;
    wordCountStr: string;
    delay: string;
    snippet?: string;
    wordcount?: number;
    redirecttitle?: string;
    sectiontitle?: string;
  };

  let { status = 'idle', query = '', resultSuggestion = '', errorMessage = '', results = [], fileSelectionEnabled = false, categorySelectionEnabled = false, categorySearchActive = false, selectedFiles = new Set<string>(), selectedCategoryResults = new Set<string>(), expandedCategories = new Set<string>(), categorySubcats = {}, categorySubcatLoading = new Set<string>(), categorySubcatErrors = {}, onRetry = () => {}, onSuggestion = (value: string) => {}, onToggleFile = (title: string) => {}, onToggleCategory = (title: string) => {}, onOpenLightbox = (src: string) => {}, onToggleCategoryExpanded = (title: string) => {}, onOpenCategoryFiles = (title: string) => {} }: {
    status?: Status;
    query?: string;
    resultSuggestion?: string;
    errorMessage?: string;
    results?: SearchResult[];
    fileSelectionEnabled?: boolean;
    categorySelectionEnabled?: boolean;
    categorySearchActive?: boolean;
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

  function imageFull(image: ResultImage | undefined): string {
    return image?.full || '';
  }

  function resultSnippet(result: SearchResult): string {
    return result.snippet || '';
  }
  function handleToggleFileClick(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    const title = (e.currentTarget as HTMLElement).dataset.title;
    if (title) onToggleFile(title);
  }
  function handleToggleFileKeydown(e: KeyboardEvent): void {
    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); const title = (e.currentTarget as HTMLElement).dataset.title; if (title) onToggleFile(title); }
  }
  function handleToggleCategoryClick(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
    const title = (e.currentTarget as HTMLElement).dataset.title;
    if (title) onToggleCategory(title);
  }
  function handleToggleCategoryKeydown(e: KeyboardEvent): void {
    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); const title = (e.currentTarget as HTMLElement).dataset.title; if (title) onToggleCategory(title); }
  }
  function handleLightboxClick(e: MouseEvent, image: { thumb: string; full: string }): void {
    e.preventDefault(); e.stopPropagation(); onOpenLightbox(imageFull(image));
  }
  function handleToggleCategoryExpanded(e: MouseEvent): void {
    e.preventDefault(); e.stopPropagation();
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
    const subcat = (e.currentTarget as HTMLElement).dataset.subcat;
    if (subcat) onToggleCategory(subcat);
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
      <a class="result-card" href={result.url} target="_blank" rel="noopener noreferrer" style={`animation-delay: ${result.delay}`}>
        {#if fileSelectionEnabled && result.file}
          <div class="checkbox-container result-select" role="checkbox" aria-checked={selectedFiles.has(result.title)} aria-label={`选择 ${result.title}`} tabindex="0" data-title={result.title} onclick={handleToggleFileClick} onkeydown={handleToggleFileKeydown}>
            <input type="checkbox" checked={selectedFiles.has(result.title)} tabindex="-1" />
            <span class="checkmark"></span>
          </div>
        {/if}
        {#if categorySelectionEnabled && result.ns === 14}
          <div class="checkbox-container result-select" role="checkbox" aria-checked={selectedCategoryResults.has(result.title)} aria-label={`选择分类 ${result.title}`} tabindex="0" data-title={result.title} onclick={handleToggleCategoryClick} onkeydown={handleToggleCategoryKeydown}>
            <input type="checkbox" checked={selectedCategoryResults.has(result.title)} tabindex="-1" />
            <span class="checkmark"></span>
          </div>
        {/if}
        {#if result.image}<button type="button" class="result-thumb" onclick={(e) => handleLightboxClick(e, result.image!)}><img src={result.image.thumb} alt="" loading="lazy"></button>{/if}
        <div class="result-body">
          <div class="result-title-row">{#if result.nsName}<span class="result-ns">{result.nsName}</span>{/if}<h3 class="result-title">{@html highlightMatch(result.title, query)}</h3></div>
          {#if result.redirecttitle}<div class="result-redirect">重定向自：<span>{@html highlightMatch(result.redirecttitle, query)}</span></div>{/if}
          {#if result.sectiontitle}<span class="result-section">§ {result.sectiontitle}</span>{/if}
          {#if !categorySearchActive}
            <p class="result-snippet">{@html cleanSnippet(resultSnippet(result))}</p>
            <div class="result-meta-row"><span title="最后编辑">{result.dateStr}</span>{#if result.wordCountStr}<span title="字数">{result.wordCountStr} 字</span>{/if}{#if result.fileSize}<span title="文件大小">{result.fileSize}</span>{:else if result.pageSizeKB}<span title="页面大小">{result.pageSizeKB}</span>{/if}</div>
          {/if}
          {#if categorySearchActive && result.ns === 14}
            <div class="category-result-actions">
              {#if categorySubcats[result.title]?.length > 0}
                <button class="category-action-btn" class:active={expandedCategories.has(result.title)} type="button" data-title={result.title} onclick={handleToggleCategoryExpanded}>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={expandedCategories.has(result.title) ? 'm18 15-6-6-6 6' : 'm6 9 6 6 6-6'}/></svg>
                  <span>{expandedCategories.has(result.title) ? '收起' : '子分类'}</span>
                  <small>{categorySubcats[result.title].length}</small>
                </button>
              {/if}
              <button class="category-action-btn" type="button" data-title={result.title} onclick={handleOpenCategoryFiles}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/></svg>
                <span>查看文件</span>
              </button>
            </div>
            {#if expandedCategories.has(result.title)}
              <div class="subcategory-panel">
                {#if categorySubcatLoading.has(result.title)}
                  <div class="subcategory-loading"><span class="suggest-spinner"></span><span>加载子分类…</span></div>
                {:else if categorySubcatErrors[result.title]}
                  <span class="subcategory-error">{categorySubcatErrors[result.title]}</span>
                {:else if categorySubcats[result.title]?.length > 0}
                  <div class="subcategory-chips">
                    {#each categorySubcats[result.title] as subcat (subcat)}
                      <button class:checked={selectedCategoryResults.has(subcat)} class="subcategory-chip" type="button" data-subcat={subcat} onclick={handleSubcatClick}>
                        <span>{categoryDisplayName(subcat)}</span>
                        {#if selectedCategoryResults.has(subcat)}<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>{/if}
                      </button>
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
      </a>
    {/each}
  {/if}
</div>

<style>
  .category-result-actions { display: flex; align-items: center; gap: 6px; margin-top: 10px; flex-wrap: wrap; }
  .category-action-btn { display: inline-flex; align-items: center; gap: 5px; padding: 5px 10px; border: 1px solid var(--border); border-radius: 6px; background: var(--background); color: var(--muted-foreground); font: inherit; font-size: 12px; cursor: pointer; transition: all 0.15s; }
  .category-action-btn svg { width: 14px; height: 14px; flex-shrink: 0; }
  .category-action-btn small { font-size: 11px; opacity: 0.6; }
  .category-action-btn:hover { border-color: var(--muted-foreground); color: var(--foreground); background: var(--accent); }
  .category-action-btn.active { border-color: var(--primary); color: var(--foreground); background: var(--accent); }
  .subcategory-panel { margin-top: 8px; padding: 8px; border: 1px solid var(--border); border-radius: 8px; background: var(--muted); }
  .subcategory-loading, .subcategory-empty, .subcategory-error { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--muted-foreground); padding: 4px 0; }
  .subcategory-chips { display: flex; flex-wrap: wrap; gap: 6px; }
  .subcategory-chip { display: inline-flex; align-items: center; gap: 4px; border: 1px solid var(--border); border-radius: 999px; background: var(--background); color: inherit; padding: 4px 10px; font: inherit; font-size: 12px; cursor: pointer; transition: all 0.15s; }
  .subcategory-chip svg { width: 12px; height: 12px; flex-shrink: 0; }
  .subcategory-chip:hover { border-color: var(--muted-foreground); }
  .subcategory-chip.checked { border-color: var(--primary); background: var(--primary); color: var(--primary-foreground); }
</style>
