<script lang="ts">
  let { title = '', info = '', progress = '', allSelected = false, disabled = false, downloading = false, concurrency = 4, downloadLabel = '下载 ZIP', downloadingLabel = '打包中...', onToggleAll = () => {}, onDownload = () => {}, onConcurrencyChange = (value: number) => {} }: {
    title?: string;
    info?: string;
    progress?: string;
    allSelected?: boolean;
    disabled?: boolean;
    downloading?: boolean;
    concurrency?: number;
    downloadLabel?: string;
    downloadingLabel?: string;
    onToggleAll?: () => void;
    onDownload?: () => void;
    onConcurrencyChange?: (value: number) => void;
  } = $props();

  function handleConcurrencyChange(event: Event): void {
    onConcurrencyChange(Number((event.currentTarget as HTMLInputElement).value));
  }
</script>

<div class="bulk-download-bar">
  <div class="bulk-download-info">
    <strong>{title}</strong>
    <span>{info}</span>
    {#if progress}<span>{progress}</span>{/if}
  </div>
  <div class="bulk-download-actions">
    <button class="btn outline" type="button" onclick={onToggleAll}>{allSelected ? '取消全选' : '全选本页'}</button>
    <label class="concurrency-control"><span>并发</span><input value={concurrency} type="number" min="1" max="16" step="1" onchange={handleConcurrencyChange}></label>
    <button class="btn primary" type="button" disabled={disabled || downloading} onclick={onDownload}>{downloading ? downloadingLabel : downloadLabel}</button>
  </div>
</div>

<style>
  .bulk-download-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    padding: 12px 16px;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background-color: var(--card);
    color: var(--card-foreground);
    margin-bottom: 12px;
    box-shadow: 0 1px 3px 0 color-mix(in srgb, var(--foreground) 5%, transparent), 0 1px 2px -1px color-mix(in srgb, var(--foreground) 5%, transparent);
    transition: border-color 0.2s, box-shadow 0.2s;
  }

  .bulk-download-bar:hover,
  .bulk-download-bar:focus-within {
    border-color: color-mix(in srgb, var(--border) 60%, var(--foreground));
    box-shadow: 0 12px 24px -10px color-mix(in srgb, var(--foreground) 10%, transparent);
  }

  .bulk-download-info {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    font-size: 0.8125rem;
    min-width: 0;
  }

  .bulk-download-info strong {
    font-size: 0.875rem;
    white-space: nowrap;
  }

  .bulk-download-info span {
    color: var(--muted-foreground);
    white-space: nowrap;
  }

  .bulk-download-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  .concurrency-control {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--muted-foreground);
    font-size: 13px;
  }

  .concurrency-control input {
    width: 58px;
    height: 34px;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--background);
    color: inherit;
    padding: 0 8px;
    font: inherit;
    transition: border-color 0.15s, box-shadow 0.2s;
  }

  .concurrency-control input:focus {
    outline: none;
    border-color: var(--ring);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--ring) 15%, transparent);
  }

  @media (prefers-color-scheme: dark) {
    .bulk-download-bar:hover,
    .bulk-download-bar:focus-within {
      border-color: color-mix(in srgb, var(--primary) 40%, var(--border));
      box-shadow: 0 0 20px -5px color-mix(in srgb, var(--primary) 15%, transparent);
    }
  }

  @media (max-width: 640px) {
    .bulk-download-bar {
      flex-direction: column;
      align-items: stretch;
      gap: 10px;
      padding: 12px;
    }

    .bulk-download-info {
      flex-direction: column;
      align-items: flex-start;
      gap: 4px;
    }

    .bulk-download-actions {
      flex-wrap: wrap;
    }
  }
</style>
