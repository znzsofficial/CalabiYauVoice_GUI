<script lang="ts">
  import BulkDownloadBar from '../BulkDownloadBar.svelte';
  import SearchResults from '../SearchResults.svelte';
  import type { ProfileValue, SearchResult, Status } from '../searchTypes';

  type CategoryProfileValue = Extract<ProfileValue, 'voiceCategory' | 'categoryDownload'>;

  let {
    activeProfile = 'categoryDownload' as CategoryProfileValue,
    status = 'idle' as Status,
    query = '',
    errorMessage = '',
    results = [] as SearchResult[],
    categoryResults = [] as SearchResult[],
    categoryResultsCountStr = '0',
    categoryAllResultsCountStr = '0',
    categoryShowAllResults = false,
    categoryIncludeSubcats = false,
    categorySelectionEnabled = false,
    selectedCategoryResults = new Set<string>(),
    selectedCategoryResultItems = [] as SearchResult[],
    selectedCategoriesTotal = 0,
    categoryStatusText = '',
    categoryDownloading = false,
    selectionDisabled = false,
    downloadConcurrency = 4,
    expandedCategories = new Set<string>(),
    collapsedRootCategories = new Set<string>(),
    categorySubcats = {} as Record<string, string[]>,
    categorySubcatLoading = new Set<string>(),
    categorySubcatErrors = {} as Record<string, string>,
    pages = [] as Array<number | '...'>,
    currentPage = 1,
    totalPages = 0,
    onRetry = () => {},
    onToggleCategory = (title: string) => {},
    onToggleCategoryExpanded = (title: string) => {},
    onToggleRootCollapsed = (title: string) => {},
    onOpenCategoryFiles = (title: string) => {},
    onRetryCategorySubcats = (title: string) => {},
    onToggleAllCategories = () => {},
    onClearAllSelections = () => {},
    onDownloadCategories = () => {},
    onCancelCategories = () => {},
    onConcurrencyChange = (value: number) => {},
    onSetCategoryShowAllResults = (value: boolean) => {},
    onSetCategoryIncludeSubcats = (value: boolean) => {},
    onGoPage = (page: number) => {},
  }: {
    activeProfile?: CategoryProfileValue;
    status?: Status;
    query?: string;
    errorMessage?: string;
    results?: SearchResult[];
    categoryResults?: SearchResult[];
    categoryResultsCountStr?: string;
    categoryAllResultsCountStr?: string;
    categoryShowAllResults?: boolean;
    categoryIncludeSubcats?: boolean;
    categorySelectionEnabled?: boolean;
    selectedCategoryResults?: Set<string>;
    selectedCategoryResultItems?: SearchResult[];
    selectedCategoriesTotal?: number;
    categoryStatusText?: string;
    categoryDownloading?: boolean;
    selectionDisabled?: boolean;
    downloadConcurrency?: number;
    expandedCategories?: Set<string>;
    collapsedRootCategories?: Set<string>;
    categorySubcats?: Record<string, string[]>;
    categorySubcatLoading?: Set<string>;
    categorySubcatErrors?: Record<string, string>;
    pages?: Array<number | '...'>;
    currentPage?: number;
    totalPages?: number;
    onRetry?: () => void;
    onToggleCategory?: (title: string) => void;
    onToggleCategoryExpanded?: (title: string) => void;
    onToggleRootCollapsed?: (title: string) => void;
    onOpenCategoryFiles?: (title: string) => void;
    onRetryCategorySubcats?: (title: string) => void;
    onToggleAllCategories?: () => void;
    onClearAllSelections?: () => void;
    onDownloadCategories?: () => void;
    onCancelCategories?: () => void;
    onConcurrencyChange?: (value: number) => void;
    onSetCategoryShowAllResults?: (value: boolean) => void;
    onSetCategoryIncludeSubcats?: (value: boolean) => void;
    onGoPage?: (page: number) => void;
  } = $props();

  let pageAllSelected = $derived(categoryResults.length > 0 && selectedCategoryResultItems.length === categoryResults.length);
  let selectionInfo = $derived(
    selectedCategoriesTotal > 0
      ? `已选 ${selectedCategoriesTotal} 个分类${selectedCategoryResultItems.length > 0 && selectedCategoryResultItems.length !== selectedCategoriesTotal ? ` · 本页 ${selectedCategoryResultItems.length}` : ''}${categoryIncludeSubcats ? ' · 含子分类' : ''}`
      : `本页 ${categoryResults.length} 个分类可选`
  );
</script>

<div class={`category-workbench ${categorySelectionEnabled && status === 'ready' ? 'has-rail' : ''}`}>
  <section class="category-results-pane">
    {#if status === 'ready'}
      <div class="category-result-toolbar">
        <div class="result-meta">
          找到 <strong>{categoryResultsCountStr}</strong> 个分类
          {#if !categoryShowAllResults && categoryAllResultsCountStr !== categoryResultsCountStr}
            <span class="category-meta-muted">全部 {categoryAllResultsCountStr}</span>
          {/if}
        </div>
        <div class="category-view-toggle" role="group" aria-label="分类显示范围">
          <button class:active={!categoryShowAllResults} type="button" onclick={() => onSetCategoryShowAllResults(false)}>仅主分类</button>
          <button class:active={categoryShowAllResults} type="button" onclick={() => onSetCategoryShowAllResults(true)}>全部显示</button>
        </div>
      </div>
    {/if}

    {#if status === 'ready' || status === 'loading' || status === 'empty' || status === 'error'}
      <SearchResults {status} {query} {errorMessage} {results} categorySelectionEnabled={categorySelectionEnabled} categorySearchActive {selectionDisabled} selectedCategoryResults={selectedCategoryResults} expandedCategories={expandedCategories} collapsedRootCategories={collapsedRootCategories} categorySubcats={categorySubcats} categorySubcatLoading={categorySubcatLoading} categorySubcatErrors={categorySubcatErrors} onRetry={onRetry} onToggleCategory={onToggleCategory} onToggleCategoryExpanded={onToggleCategoryExpanded} onToggleRootCollapsed={onToggleRootCollapsed} onOpenCategoryFiles={onOpenCategoryFiles} onRetryCategorySubcats={onRetryCategorySubcats} />
    {/if}

    {#if pages.length > 0 && status === 'ready'}
      <div class="pagination"><button class="page-btn" disabled={currentPage <= 1} onclick={() => onGoPage(currentPage - 1)}>‹</button>{#each pages as page}<button class:active={page === currentPage} class="page-btn" disabled={page === '...'} onclick={() => typeof page === 'number' && onGoPage(page)}>{page}</button>{/each}<button class="page-btn" disabled={currentPage >= totalPages} onclick={() => onGoPage(currentPage + 1)}>›</button></div>
    {/if}
  </section>

  {#if categorySelectionEnabled && status === 'ready'}
    <aside class="category-download-rail">
      <BulkDownloadBar
        variant="rail"
        title={activeProfile === 'voiceCategory' ? '语音分类打包' : '分类打包'}
        info={selectionInfo}
        progress={categoryStatusText}
        allSelected={pageAllSelected}
        disabled={selectedCategoriesTotal === 0}
        downloading={categoryDownloading}
        concurrency={downloadConcurrency}
        downloadingLabel="打包中…"
        includeSubcats={categoryIncludeSubcats}
        showIncludeSubcats={true}
        clearAllEnabled={selectedCategoriesTotal > 0}
        onToggleAll={onToggleAllCategories}
        onClearAll={onClearAllSelections}
        onDownload={onDownloadCategories}
        onCancel={onCancelCategories}
        onConcurrencyChange={onConcurrencyChange}
        onToggleIncludeSubcats={() => onSetCategoryIncludeSubcats(!categoryIncludeSubcats)}
      />
    </aside>
  {/if}
</div>

<style>
  .category-result-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
  .category-result-toolbar .result-meta { margin: 0; }
  .category-meta-muted { color: var(--muted-foreground); font-weight: 400; margin-left: 6px; }
  .category-view-toggle { display: inline-flex; align-items: center; gap: 2px; padding: 2px; border: 1px solid var(--border); border-radius: 8px; background: var(--muted); flex-shrink: 0; }
  .category-view-toggle button { border: 0; border-radius: 6px; padding: 5px 10px; background: transparent; color: var(--muted-foreground); font: inherit; font-size: 12px; cursor: pointer; transition: background-color 0.15s, color 0.15s, box-shadow 0.15s; }
  .category-view-toggle button:hover { color: var(--foreground); }
  .category-view-toggle button.active { background: var(--card); color: var(--foreground); box-shadow: 0 1px 2px color-mix(in srgb, var(--foreground) 8%, transparent); }

  @media (max-width: 640px) {
    .category-result-toolbar { align-items: flex-start; flex-direction: column; }
    .category-view-toggle { width: 100%; }
    .category-view-toggle button { flex: 1; }
  }
</style>
