<script lang="ts">
  let { src = '', onToggle = (playing: boolean) => {} }: {
    src?: string;
    onToggle?: (playing: boolean) => void;
  } = $props();

  let activeSrc = $state('');
  let playing = $state(false);
  let currentTime = $state(0);
  let duration = $state(0);
  let audioPlayer: HTMLAudioElement | null = null;

  const handlers = {
    timeupdate: () => { currentTime = audioPlayer?.currentTime || 0; },
    durationchange: () => { duration = Number.isFinite(audioPlayer?.duration || 0) ? audioPlayer!.duration : 0; },
    ended: () => { playing = false; onToggle(false); },
    pause: () => { playing = false; },
    play: () => { playing = true; },
  };

  $effect(() => {
    return () => {
      if (audioPlayer) {
        for (const [event, handler] of Object.entries(handlers)) audioPlayer.removeEventListener(event, handler as EventListener);
        audioPlayer.pause();
        audioPlayer.src = '';
        audioPlayer = null;
      }
    };
  });

  function ensurePlayer(): HTMLAudioElement {
    if (audioPlayer) return audioPlayer;
    audioPlayer = new Audio();
    audioPlayer.preload = 'metadata';
    for (const [event, handler] of Object.entries(handlers)) audioPlayer.addEventListener(event, handler as EventListener);
    return audioPlayer;
  }

  async function toggle(): Promise<void> {
    if (!src) return;
    const player = ensurePlayer();
    if (activeSrc === src && !player.paused) {
      player.pause();
      return;
    }
    if (activeSrc !== src) {
      activeSrc = src;
      currentTime = 0;
      duration = 0;
      player.src = src;
      player.load();
    }
    try {
      await player.play();
      onToggle(true);
    } catch {
      // autoplay blocked
    }
  }

  const pct = $derived(activeSrc === src && duration > 0 ? Math.max(0, Math.min(100, (currentTime / duration) * 100)) : 0);
  const isActive = $derived(activeSrc === src && playing);
  const C = 2 * Math.PI * 11;
</script>

<button class="audio-ring" class:playing={isActive} type="button" aria-label={isActive ? '暂停' : '播放'} onclick={toggle} disabled={!src}>
  <svg class="audio-ring-svg" viewBox="0 0 26 26" fill="none">
    <circle cx="13" cy="13" r="11" stroke="var(--border)" stroke-width="2" fill="none" />
    <circle cx="13" cy="13" r="11" stroke="var(--muted-foreground)" stroke-width="2" fill="none" stroke-linecap="round" stroke-dasharray={C} stroke-dashoffset={C * (1 - pct / 100)} />
  </svg>
  <span class="audio-ring-core">
    {#if isActive}
      <svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>
    {:else}
      <svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
    {/if}
  </span>
</button>

<style>
  .audio-ring {
    position: relative;
    width: 30px;
    height: 30px;
    border: 0;
    border-radius: 999px;
    background: transparent;
    color: inherit;
    display: flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    cursor: pointer;
    padding: 0;
  }
  .audio-ring:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }
  .audio-ring svg { display: block; }
  .audio-ring-svg {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    transform: rotate(-90deg);
    pointer-events: none;
  }
  .audio-ring-core {
    position: relative;
    z-index: 1;
    width: 22px;
    height: 22px;
    border-radius: 999px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--background);
    color: var(--foreground);
    transition: background 0.2s, transform 0.15s;
  }
  .audio-ring-core svg {
    width: 11px;
    height: 11px;
  }
  .audio-ring:hover .audio-ring-core {
    background: var(--accent);
    transform: scale(1.08);
  }
  .audio-ring:active .audio-ring-core {
    transform: scale(0.92);
  }
  .audio-ring.playing .audio-ring-core {
    background: var(--primary);
    color: var(--primary-foreground);
  }
</style>
