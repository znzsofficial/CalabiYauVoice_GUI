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
  }
</style>
