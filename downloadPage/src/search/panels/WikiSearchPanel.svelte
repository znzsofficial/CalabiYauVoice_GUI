<script lang="ts">
  import BulkDownloadBar from '../BulkDownloadBar.svelte';
  import SearchResults from '../SearchResults.svelte';
  import type { SearchResult, Status } from '../searchTypes';

  let {
    status = 'idle' as Status,
    query = '',
    resultSuggestion = '',
    errorMessage = '',
    results = [] as SearchResult[],
    totalHitsStr = '0',
    fileSelectionEnabled = false,
    fileResults = [] as SearchResult[],
    selectedFileResults = [] as SearchResult[],
    selectedFiles = new Set<string>(),
    zipProgress = '',
    zipDownloading = false,
    selectionDisabled = false,
    downloadConcurrency = 4,
    pages = [] as Array<number | '...'>,
    currentPage = 1,
    totalPages = 0,
    onRetry = () => {},
    onSuggestion = (value: string) => {},
    onToggleFile = (title: string) => {},
    onOpenLightbox = (src: string) => {},
    onToggleAllFiles = () => {},
    onDownloadFiles = () => {},
    onCancelFiles = () => {},
    onConcurrencyChange = (value: number) => {},
    onGoPage = (page: number) => {},
  }: {
    status?: Status;
    query?: string;
    resultSuggestion?: string;
    errorMessage?: string;
    results?: SearchResult[];
    totalHitsStr?: string;
    fileSelectionEnabled?: boolean;
    fileResults?: SearchResult[];
    selectedFileResults?: SearchResult[];
    selectedFiles?: Set<string>;
    zipProgress?: string;
    zipDownloading?: boolean;
    selectionDisabled?: boolean;
    downloadConcurrency?: number;
    pages?: Array<number | '...'>;
    currentPage?: number;
    totalPages?: number;
    onRetry?: () => void;
    onSuggestion?: (value: string) => void;
    onToggleFile?: (title: string) => void;
    onOpenLightbox?: (src: string) => void;
    onToggleAllFiles?: () => void;
    onDownloadFiles?: () => void;
    onCancelFiles?: () => void;
    onConcurrencyChange?: (value: number) => void;
    onGoPage?: (page: number) => void;
  } = $props();
</script>

<div class={`wiki-workbench ${status === 'ready' && fileSelectionEnabled ? 'has-rail' : ''}`}>
  <section class="wiki-results-pane">
    {#if status === 'ready'}
      <div class="result-meta">
        找到 <strong>{totalHitsStr}</strong> 条结果
        {#if resultSuggestion}
          · 你是不是要搜：<button class="suggestion-link" onclick={() => onSuggestion(resultSuggestion)}>{resultSuggestion}</button>
        {/if}
      </div>
    {/if}

    {#if status === 'ready' || status === 'loading' || status === 'empty' || status === 'error'}
      <SearchResults {status} {query} {resultSuggestion} {errorMessage} {results} {fileSelectionEnabled} {selectionDisabled} {selectedFiles} onRetry={onRetry} onSuggestion={onSuggestion} onToggleFile={onToggleFile} onOpenLightbox={onOpenLightbox} />
    {/if}

    {#if pages.length > 0 && status === 'ready'}
      <div class="pagination"><button class="page-btn" disabled={currentPage <= 1} onclick={() => onGoPage(currentPage - 1)}>‹</button>{#each pages as page}<button class:active={page === currentPage} class="page-btn" disabled={page === '...'} onclick={() => typeof page === 'number' && onGoPage(page)}>{page}</button>{/each}<button class="page-btn" disabled={currentPage >= totalPages} onclick={() => onGoPage(currentPage + 1)}>›</button></div>
    {/if}
  </section>

  {#if status === 'ready' && fileSelectionEnabled}
    <aside class="wiki-download-rail">
      <BulkDownloadBar variant="rail" title="批量下载" info={selectedFileResults.length > 0 ? `已选择 ${selectedFileResults.length} 个文件` : `本页 ${fileResults.length} 个文件可选`} progress={zipProgress} allSelected={selectedFileResults.length === fileResults.length} disabled={selectedFileResults.length === 0} downloading={zipDownloading} concurrency={downloadConcurrency} downloadingLabel="打包中…" onToggleAll={onToggleAllFiles} onDownload={onDownloadFiles} onCancel={onCancelFiles} onConcurrencyChange={onConcurrencyChange} />
    </aside>
  {/if}
</div>
