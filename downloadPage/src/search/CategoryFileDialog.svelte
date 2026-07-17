<script lang="ts">
  import { onMount } from 'svelte';
  import { ensureProxyDownloadUrl, proxyMediaUrl } from './searchApi';
  import { downloadBlob, downloadUrlWithRetry } from './download';
  import { formatFileSize } from './utils';

  type CategoryFile = { name: string; url: string; mime?: string; size?: number };
  type FileFilter = 'all' | 'image' | 'audio' | 'other';

  let {
    title = '',
    subtitle = '',
    emptyMessage = '分类内没有可显示文件',
    files = [],
    loading = false,
    error = '',
    onClose = () => {},
    onPreview = (_url: string) => {},
    onRetry = () => {},
  }: {
    title?: string;
    subtitle?: string;
    emptyMessage?: string;
    files?: CategoryFile[];
    loading?: boolean;
    error?: string;
    onClose?: () => void;
    onPreview?: (url: string) => void;
    onRetry?: () => void;
  } = $props();

  let filter = $state('all' as FileFilter);
  let query = $state('');
  let downloadingUrl = $state('');
  let activeAudioUrl = $state('');
  let audioPlaying = $state(false);
  let audioCurrentTime = $state(0);
  let audioDuration = $state(0);
  let dialogRoot: HTMLDivElement | null = $state(null);
  let audioPlayer: HTMLAudioElement | null = null;

  const audioHandlers = {
    timeupdate: () => { audioCurrentTime = audioPlayer?.currentTime || 0; },
    durationchange: () => { audioDuration = Number.isFinite(audioPlayer?.duration || 0) ? audioPlayer?.duration || 0 : 0; },
    ended: () => { audioPlaying = false; },
    pause: () => { audioPlaying = false; },
    play: () => { audioPlaying = true; },
  } as const;

  onMount(() => {
    dialogRoot?.focus();
    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        close();
      }
    };
    window.addEventListener('keydown', onKeydown);
    return () => {
      window.removeEventListener('keydown', onKeydown);
      if (audioPlayer) {
        for (const [event, handler] of Object.entries(audioHandlers)) audioPlayer.removeEventListener(event, handler as EventListener);
        audioPlayer.pause();
        audioPlayer.src = '';
        audioPlayer = null;
      }
    };
  });

  $effect(() => {
    // Reset local UI when dialog switches category.
    void title;
    filter = 'all';
    query = '';
    downloadingUrl = '';
    stopAudioPreview();
  });

  function isAudioFile(url: string, mime?: string): boolean {
    const clean = url.split('?')[0];
    return mime?.startsWith('audio/') === true || /\.(wav|mp3|ogg)$/i.test(clean);
  }

  function isImageFile(url: string, mime?: string): boolean {
    const clean = url.split('?')[0];
    return mime?.startsWith('image/') === true || /\.(png|jpe?g|gif|webp|avif|svg)$/i.test(clean);
  }

  function fileKind(file: CategoryFile): FileFilter {
    if (isImageFile(file.url, file.mime)) return 'image';
    if (isAudioFile(file.url, file.mime)) return 'audio';
    return 'other';
  }

  let counts = $derived.by(() => {
    let image = 0;
    let audio = 0;
    let other = 0;
    for (const file of files) {
      const kind = fileKind(file);
      if (kind === 'image') image += 1;
      else if (kind === 'audio') audio += 1;
      else other += 1;
    }
    return { all: files.length, image, audio, other };
  });

  let filteredFiles = $derived.by(() => {
    const q = query.trim().toLowerCase();
    return files.filter(file => {
      if (filter !== 'all' && fileKind(file) !== filter) return false;
      if (!q) return true;
      return file.name.toLowerCase().includes(q) || (file.mime || '').toLowerCase().includes(q);
    });
  });

  function ensureAudioPlayer(): HTMLAudioElement {
    if (audioPlayer) return audioPlayer;
    audioPlayer = new Audio();
    audioPlayer.preload = 'metadata';
    for (const [event, handler] of Object.entries(audioHandlers)) audioPlayer.addEventListener(event, handler as EventListener);
    return audioPlayer;
  }

  async function toggleAudioPreview(url: string): Promise<void> {
    const player = ensureAudioPlayer();
    if (activeAudioUrl === url && !player.paused) {
      player.pause();
      return;
    }
    if (activeAudioUrl !== url) {
      activeAudioUrl = url;
      audioCurrentTime = 0;
      audioDuration = 0;
      player.src = url;
      player.load();
    }
    try {
      await player.play();
    } catch {
      audioPlaying = false;
    }
  }

  function stopAudioPreview(): void {
    if (!audioPlayer) return;
    audioPlayer.pause();
    audioPlayer.src = '';
    activeAudioUrl = '';
    audioPlaying = false;
    audioCurrentTime = 0;
    audioDuration = 0;
  }

  function close(): void {
    stopAudioPreview();
    onClose();
  }

  async function downloadOne(file: CategoryFile): Promise<void> {
    if (downloadingUrl) return;
    downloadingUrl = file.url;
    try {
      const blob = await downloadUrlWithRetry(ensureProxyDownloadUrl(file.url));
      downloadBlob(blob, file.name || 'download');
    } catch {
      open(proxyMediaUrl(file.url), '_blank', 'noopener,noreferrer');
    } finally {
      downloadingUrl = '';
    }
  }
</script>

<div class="category-file-dialog" role="dialog" aria-modal="true" aria-label="分类文件列表" tabindex="-1" bind:this={dialogRoot}>
  <button class="category-file-backdrop" type="button" aria-label="关闭" onclick={close}></button>
  <section class="category-file-panel">
    <div class="category-file-header">
      <div class="category-file-heading">
        <h2>{title}</h2>
        <p>
          {subtitle}
          {#if !loading && !error && files.length > 0}
            · {files.length} 个文件
            {#if filteredFiles.length !== files.length}
              · 显示 {filteredFiles.length}
            {/if}
          {/if}
        </p>
      </div>
      <button class="btn outline" type="button" onclick={close}>关闭</button>
    </div>

    {#if !loading && !error && files.length > 0}
      <div class="category-file-toolbar">
        <div class="category-file-filters" role="group" aria-label="文件类型">
          <button class:active={filter === 'all'} type="button" onclick={() => filter = 'all'}>全部 {counts.all}</button>
          {#if counts.image > 0}
            <button class:active={filter === 'image'} type="button" onclick={() => filter = 'image'}>图片 {counts.image}</button>
          {/if}
          {#if counts.audio > 0}
            <button class:active={filter === 'audio'} type="button" onclick={() => filter = 'audio'}>音频 {counts.audio}</button>
          {/if}
          {#if counts.other > 0}
            <button class:active={filter === 'other'} type="button" onclick={() => filter = 'other'}>其他 {counts.other}</button>
          {/if}
        </div>
        <label class="category-file-search">
          <iconify-icon icon="lucide:search"></iconify-icon>
          <input bind:value={query} type="search" placeholder="筛选文件名…" autocomplete="off">
        </label>
      </div>
    {/if}

    <div class="category-file-list">
      {#if loading}
        <div class="category-file-state">
          <span class="suggest-spinner"></span>
          <span>正在加载文件…</span>
        </div>
      {:else if error}
        <div class="category-file-state error">
          <iconify-icon icon="lucide:alert-circle"></iconify-icon>
          <span>{error}</span>
          <button class="btn outline" type="button" onclick={onRetry}>重试</button>
        </div>
      {:else if files.length === 0}
        <div class="category-file-state">
          <iconify-icon icon="lucide:folder-open"></iconify-icon>
          <span>{emptyMessage}</span>
        </div>
      {:else if filteredFiles.length === 0}
        <div class="category-file-state">
          <iconify-icon icon="lucide:search-x"></iconify-icon>
          <span>没有匹配的文件</span>
          <button class="btn outline" type="button" onclick={() => { filter = 'all'; query = ''; }}>清除筛选</button>
        </div>
      {:else}
        {#each filteredFiles as file (file.url)}
          {@const mediaUrl = proxyMediaUrl(file.url)}
          {@const kind = fileKind(file)}
          <div class="category-file-item">
            {#if kind === 'image'}
              <button class="category-file-preview" type="button" onclick={() => onPreview(mediaUrl)} title="预览">
                <img src={mediaUrl} alt="" loading="lazy">
              </button>
            {:else if kind === 'audio'}
              {@const pct = activeAudioUrl === mediaUrl && audioDuration > 0 ? Math.max(0, Math.min(100, (audioCurrentTime / audioDuration) * 100)) : 0}
              {@const isActive = activeAudioUrl === mediaUrl && audioPlaying}
              {@const C = 2 * Math.PI * 21}
              <button class="category-audio-ring" class:playing={isActive} type="button" aria-label={isActive ? '暂停' : '播放'} onclick={() => toggleAudioPreview(mediaUrl)}>
                <svg class="audio-ring-svg" viewBox="0 0 48 48" fill="none">
                  <circle cx="24" cy="24" r="21" stroke="var(--border)" stroke-width="3" fill="none" />
                  <circle cx="24" cy="24" r="21" stroke="var(--muted-foreground)" stroke-width="3" fill="none" stroke-linecap="round" stroke-dasharray={C} stroke-dashoffset={C * (1 - pct / 100)} />
                </svg>
                <span class="category-audio-ring-core">
                  {#if isActive}
                    <svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>
                  {:else}
                    <svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                  {/if}
                </span>
              </button>
            {:else}
              <span class="category-file-icon">
                <iconify-icon icon="lucide:file"></iconify-icon>
              </span>
            {/if}

            <div class="category-file-meta">
              <span class="category-file-name">{file.name}</span>
              <small>{formatFileSize(file.size || 0) || file.mime || '文件'}</small>
            </div>

            <div class="category-file-actions">
              <a class="category-file-action" href={mediaUrl} target="_blank" rel="noopener noreferrer" title="新标签打开">
                <iconify-icon icon="lucide:external-link"></iconify-icon>
              </a>
              <button
                class="category-file-action primary"
                type="button"
                title="下载"
                disabled={downloadingUrl === file.url}
                onclick={() => downloadOne(file)}
              >
                {#if downloadingUrl === file.url}
                  <span class="suggest-spinner"></span>
                {:else}
                  <iconify-icon icon="lucide:download"></iconify-icon>
                {/if}
              </button>
            </div>
          </div>
        {/each}
      {/if}
    </div>
  </section>
</div>

<style>
  .category-file-dialog { position: fixed; inset: 0; z-index: 70; display: grid; place-items: center; padding: 20px; }
  .category-file-backdrop { position: absolute; inset: 0; border: 0; background: color-mix(in srgb, var(--background) 30%, #000 55%); backdrop-filter: blur(4px); -webkit-backdrop-filter: blur(4px); }
  .category-file-panel {
    position: relative;
    width: min(760px, 100%);
    max-height: min(780px, 90vh);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    border-radius: 12px;
    border: 1px solid var(--border);
    background: var(--background);
    box-shadow: 0 24px 80px color-mix(in srgb, var(--foreground) 22%, transparent);
    animation: dialogIn 0.25s cubic-bezier(0.22, 1, 0.36, 1);
  }
  .category-file-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding: 16px 16px 12px;
    border-bottom: 1px solid var(--border);
  }
  .category-file-heading { min-width: 0; }
  .category-file-header h2 { margin: 0; font-size: 1.0625rem; line-height: 1.3; }
  .category-file-header p { margin: 4px 0 0; color: var(--muted-foreground); font-size: 0.8125rem; }
  .category-file-toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 16px;
    border-bottom: 1px solid var(--border);
    background: color-mix(in srgb, var(--muted) 55%, var(--background));
  }
  .category-file-filters {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    padding: 2px;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--muted);
    flex-wrap: wrap;
  }
  .category-file-filters button {
    border: 0;
    border-radius: 6px;
    padding: 5px 10px;
    background: transparent;
    color: var(--muted-foreground);
    font: inherit;
    font-size: 12px;
    cursor: pointer;
  }
  .category-file-filters button:hover { color: var(--foreground); }
  .category-file-filters button.active {
    background: var(--card);
    color: var(--foreground);
    box-shadow: 0 1px 2px color-mix(in srgb, var(--foreground) 8%, transparent);
  }
  .category-file-search {
    margin-left: auto;
    min-width: 0;
    flex: 1 1 160px;
    max-width: 240px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 34px;
    padding: 0 10px;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--background);
    color: var(--muted-foreground);
  }
  .category-file-search iconify-icon { font-size: 14px; flex-shrink: 0; }
  .category-file-search input {
    min-width: 0;
    flex: 1;
    border: 0;
    outline: none;
    background: transparent;
    color: var(--foreground);
    font: inherit;
    font-size: 13px;
  }
  .category-file-list { overflow: auto; padding: 8px; flex: 1 1 auto; }
  .category-file-state {
    display: grid;
    justify-items: center;
    gap: 10px;
    padding: 40px 16px;
    color: var(--muted-foreground);
    text-align: center;
    font-size: 0.875rem;
  }
  .category-file-state.error { color: var(--destructive, #dc2626); }
  .category-file-state iconify-icon { font-size: 1.25rem; }
  .category-file-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    border-radius: 10px;
    transition: background-color 0.15s;
  }
  .category-file-item:hover { background: var(--accent); }
  .category-file-preview {
    width: 48px;
    height: 48px;
    padding: 0;
    border: 1px solid var(--border);
    border-radius: 8px;
    overflow: hidden;
    background: var(--muted);
    flex: 0 0 auto;
    cursor: pointer;
    transition: transform 0.2s, border-color 0.2s, box-shadow 0.2s;
    appearance: none;
    outline: none;
  }
  .category-file-preview:hover {
    transform: scale(1.06);
    border-color: color-mix(in srgb, var(--border) 60%, var(--foreground));
    box-shadow: 0 8px 18px -12px color-mix(in srgb, var(--foreground) 18%, transparent);
  }
  .category-file-preview img { width: 100%; height: 100%; object-fit: cover; display: block; }
  .category-audio-ring {
    position: relative;
    width: 48px;
    height: 48px;
    border: 0;
    border-radius: 999px;
    background: transparent;
    color: inherit;
    display: flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    cursor: pointer;
  }
  .audio-ring-svg { position: absolute; inset: 0; width: 100%; height: 100%; transform: rotate(-90deg); pointer-events: none; }
  .category-audio-ring-core {
    position: relative;
    z-index: 1;
    width: 36px;
    height: 36px;
    border-radius: 999px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--background);
    color: var(--foreground);
    transition: background 0.2s, transform 0.2s;
  }
  .category-audio-ring-core svg { width: 16px; height: 16px; }
  .category-audio-ring:hover .category-audio-ring-core { background: var(--accent); transform: scale(1.05); }
  .category-audio-ring.playing .category-audio-ring-core { background: var(--primary); color: var(--primary-foreground); }
  .category-file-icon {
    width: 48px;
    height: 48px;
    border-radius: 8px;
    display: grid;
    place-items: center;
    background: var(--muted);
    color: var(--muted-foreground);
    font-size: 1.1rem;
    flex: 0 0 auto;
  }
  .category-file-meta { min-width: 0; display: grid; gap: 3px; flex: 1 1 auto; }
  .category-file-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 0.875rem;
    color: var(--foreground);
  }
  .category-file-meta small { color: var(--muted-foreground); font-size: 0.75rem; }
  .category-file-actions { display: inline-flex; align-items: center; gap: 4px; flex-shrink: 0; }
  .category-file-action {
    width: 34px;
    height: 34px;
    border: 1px solid var(--border);
    border-radius: 8px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: var(--background);
    color: var(--muted-foreground);
    text-decoration: none;
    cursor: pointer;
    transition: background-color 0.15s, color 0.15s, border-color 0.15s;
  }
  .category-file-action:hover {
    color: var(--foreground);
    border-color: color-mix(in srgb, var(--border) 50%, var(--foreground));
  }
  .category-file-action.primary {
    color: var(--primary-foreground);
    background: var(--primary);
    border-color: var(--primary);
  }
  .category-file-action.primary:hover {
    filter: brightness(1.05);
  }
  .category-file-action:disabled {
    opacity: 0.6;
    cursor: wait;
  }
  .category-file-action iconify-icon,
  .category-file-action .suggest-spinner {
    font-size: 15px;
  }

  @keyframes dialogIn {
    from { opacity: 0; transform: scale(0.96) translateY(8px); }
    to { opacity: 1; transform: scale(1) translateY(0); }
  }

  @media (prefers-color-scheme: dark) {
    .category-file-panel {
      border-color: color-mix(in srgb, var(--primary) 30%, var(--border));
      box-shadow: 0 0 20px -5px color-mix(in srgb, var(--primary) 15%, transparent);
    }
  }

  @media (max-width: 640px) {
    .category-file-dialog { padding: 10px; }
    .category-file-toolbar { flex-direction: column; align-items: stretch; }
    .category-file-search { max-width: none; margin-left: 0; }
    .category-file-filters { width: 100%; }
    .category-file-filters button { flex: 1; }
  }
</style>
