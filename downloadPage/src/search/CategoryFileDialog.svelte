<script lang="ts">
  import { formatFileSize } from './utils';

  type CategoryFile = { name: string; url: string; mime?: string; size?: number };

  let { title = '', subtitle = '', files = [], loading = false, error = '', onClose = () => {}, onPreview = (url: string) => {} }: {
    title?: string;
    subtitle?: string;
    files?: CategoryFile[];
    loading?: boolean;
    error?: string;
    onClose?: () => void;
    onPreview?: (url: string) => void;
  } = $props();

  let activeAudioUrl = $state('');
  let audioPlaying = $state(false);
  let audioCurrentTime = $state(0);
  let audioDuration = $state(0);
  let audioPlayer: HTMLAudioElement | null = null;
  const audioHandlers = {
    timeupdate: () => { audioCurrentTime = audioPlayer?.currentTime || 0; },
    durationchange: () => { audioDuration = Number.isFinite(audioPlayer?.duration || 0) ? audioPlayer?.duration || 0 : 0; },
    ended: () => { audioPlaying = false; },
    pause: () => { audioPlaying = false; },
    play: () => { audioPlaying = true; },
  } as const;

  $effect(() => {
    return () => {
      if (audioPlayer) {
        for (const [event, handler] of Object.entries(audioHandlers)) audioPlayer.removeEventListener(event, handler as EventListener);
        audioPlayer.pause();
        audioPlayer.src = '';
        audioPlayer = null;
      }
    };
  });

  function isAudioFile(url: string, mime?: string): boolean {
    const clean = url.split('?')[0];
    return mime?.startsWith('audio/') === true || /\.(wav|mp3|ogg)$/i.test(clean);
  }

  function isImageFile(url: string, mime?: string): boolean {
    const clean = url.split('?')[0];
    return mime?.startsWith('image/') === true || /\.(png|jpe?g|gif|webp|avif|svg)$/i.test(clean);
  }

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


</script>

<div class="category-file-dialog" role="dialog" aria-modal="true" aria-label="分类文件列表">
  <button class="category-file-backdrop" type="button" aria-label="关闭" onclick={close}></button>
  <section class="category-file-panel">
    <div class="category-file-header">
      <div>
        <h2>{title}</h2>
        <p>{subtitle}</p>
      </div>
      <button class="btn outline" type="button" onclick={close}>关闭</button>
    </div>
    <div class="category-file-list">
      {#if loading}
        <div class="suggest-state">正在加载文件...</div>
      {:else if error}
        <div class="suggest-state">{error}</div>
      {:else if files.length === 0}
        <div class="suggest-state">分类内没有可显示文件</div>
      {:else}
        {#each files as file (file.url)}
          <div class="category-file-item">
            {#if isImageFile(file.url, file.mime)}
              <button class="category-file-preview" type="button" onclick={() => onPreview(file.url)}><img src={file.url} alt="" loading="lazy"></button>
            {:else if isAudioFile(file.url, file.mime)}
              {@const pct = activeAudioUrl === file.url && audioDuration > 0 ? Math.max(0, Math.min(100, (audioCurrentTime / audioDuration) * 100)) : 0}
              {@const isActive = activeAudioUrl === file.url && audioPlaying}
              {@const C = 2 * Math.PI * 21}
              <button class="category-audio-ring" class:playing={isActive} type="button" aria-label={isActive ? '暂停' : '播放'} onclick={() => toggleAudioPreview(file.url)}>
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
              <span class="category-file-icon">FILE</span>
            {/if}
            <a class="category-file-name" href={file.url} target="_blank" rel="noopener noreferrer">
              <span>{file.name}</span>
              <small>{formatFileSize(file.size || 0) || file.mime || '文件'}</small>
            </a>
          </div>
        {/each}
      {/if}
    </div>
  </section>
</div>

<style>
  .category-file-dialog { position: fixed; inset: 0; z-index: 70; display: grid; place-items: center; padding: 20px; }
  .category-file-backdrop { position: absolute; inset: 0; border: 0; background: rgb(0 0 0 / 0.45); }
  .category-file-panel { position: relative; width: min(720px, 100%); max-height: min(720px, 86vh); display: flex; flex-direction: column; overflow: hidden; border-radius: 8px; border: 1px solid var(--border); background: var(--background); box-shadow: 0 24px 80px rgb(0 0 0 / 0.25); }
  .category-file-header, .category-file-item { display: flex; align-items: center; }
  .category-file-header { justify-content: space-between; gap: 16px; padding: 16px; border-bottom: 1px solid var(--border); }
  .category-file-header h2 { margin: 0; font-size: 18px; }
  .category-file-header p { margin: 4px 0 0; color: var(--muted-foreground); font-size: 13px; }
  .category-file-list { overflow: auto; padding: 10px; }
  .category-file-item { gap: 12px; padding: 10px 12px; border-radius: 8px; color: inherit; }
  .category-file-item:hover { background: var(--accent); }
  .category-file-preview { width: 48px; height: 48px; padding: 0; border: 1px solid var(--border); border-radius: 8px; overflow: hidden; background: var(--muted); flex: 0 0 auto; cursor: pointer; transition: transform 0.2s, border-color 0.2s, box-shadow 0.2s; }
  .category-file-preview:hover { transform: scale(1.08); border-color: var(--muted-foreground); box-shadow: 0 2px 8px rgb(0 0 0 / 0.12); }
  .category-file-preview img { width: 100%; height: 100%; object-fit: cover; display: block; }
  .category-audio-ring { position: relative; width: 48px; height: 48px; border: 0; border-radius: 999px; background: transparent; color: inherit; display: flex; align-items: center; justify-content: center; flex: 0 0 auto; cursor: pointer; }
  .audio-ring-svg { position: absolute; inset: 0; width: 100%; height: 100%; transform: rotate(-90deg); pointer-events: none; }
  .category-audio-ring-core { position: relative; z-index: 1; width: 36px; height: 36px; border-radius: 999px; display: flex; align-items: center; justify-content: center; background: var(--background); color: var(--foreground); transition: background 0.2s, transform 0.2s; }
  .category-audio-ring-core svg { width: 16px; height: 16px; }
  .category-audio-ring:hover .category-audio-ring-core { background: var(--accent); transform: scale(1.05); }
  .category-audio-ring:active .category-audio-ring-core { transform: scale(0.95); }
  .category-audio-ring.playing .category-audio-ring-core { background: var(--primary); color: var(--primary-foreground); }
  .category-file-icon { width: 48px; height: 48px; border-radius: 8px; display: grid; place-items: center; background: var(--muted); color: var(--muted-foreground); font-size: 11px; flex: 0 0 auto; }
  .category-file-name { min-width: 0; display: grid; gap: 4px; color: inherit; text-decoration: none; flex: 1 1 auto; }
  .category-file-name span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .category-file-name small { color: var(--muted-foreground); white-space: nowrap; }
</style>
