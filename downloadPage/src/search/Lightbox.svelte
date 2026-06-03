<script lang="ts">
  let { src = '', downloading = false, onClose = () => {}, onDownload = () => {} }: {
    src?: string;
    downloading?: boolean;
    onClose?: () => void;
    onDownload?: () => void;
  } = $props();

  let loaded = $state(false);
  let zoomed = $state(false);
  let pinching = $state(false);

  // Bypass Svelte reactivity for high-frequency transform updates
  let imgEl: HTMLImageElement | undefined;
  let scale = 1;
  let translateX = 0;
  let translateY = 0;
  let dragging = $state(false);
  let dragStartX = 0;
  let dragStartY = 0;
  let startTranslateX = 0;
  let startTranslateY = 0;
  let pointers = new Map<number, { x: number; y: number }>();
  let pinchStartDistance = 0;
  let pinchStartScale = 1;
  let pinchCenterX = 0;
  let pinchCenterY = 0;
  let pinchStartTranslateX = 0;
  let pinchStartTranslateY = 0;
  let rafId = 0;
  let pendingTransform = false;

  function applyTransform(): void {
    if (!imgEl) return;
    imgEl.style.transform = `translate(${translateX}px,${translateY}px) scale(${scale})`;
    pendingTransform = false;
  }

  function scheduleTransform(): void {
    if (pendingTransform) return;
    pendingTransform = true;
    rafId = requestAnimationFrame(applyTransform);
  }

  function syncZoomedState(): void {
    zoomed = scale > 1.05;
  }

  function resetTransform(): void {
    scale = 1;
    translateX = 0;
    translateY = 0;
    pointers = new Map();
    syncZoomedState();
    if (imgEl) imgEl.style.transform = '';
  }

  function toggleZoom(): void {
    if (scale > 1.05) {
      resetTransform();
      return;
    }
    scale = 2.5;
    translateX = 0;
    translateY = 0;
    syncZoomedState();
    if (imgEl) imgEl.style.transform = `translate(0px,0px) scale(2.5)`;
  }

  function handleWheel(event: WheelEvent): void {
    event.preventDefault();
    const oldScale = scale;
    const nextScale = Math.max(0.5, Math.min(15, scale * (event.deltaY > 0 ? 0.92 : 1.08)));
    scale = nextScale;
    if (nextScale <= 1.05) {
      resetTransform();
      return;
    }
    const ratio = nextScale / oldScale;
    translateX *= ratio;
    translateY *= ratio;
    syncZoomedState();
    scheduleTransform();
  }

  function startDrag(event: PointerEvent): void {
    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    (event.currentTarget as Element | null)?.setPointerCapture?.(event.pointerId);

    if (pointers.size === 1) {
      if (scale <= 1.05) return;
      dragging = true;
      dragStartX = event.clientX;
      dragStartY = event.clientY;
      startTranslateX = translateX;
      startTranslateY = translateY;
      return;
    }

    if (pointers.size === 2) {
      const points = [...pointers.values()];
      pinchStartDistance = distanceBetween(points[0], points[1]);
      pinchStartScale = scale;
      pinchCenterX = (points[0].x + points[1].x) / 2;
      pinchCenterY = (points[0].y + points[1].y) / 2;
      pinchStartTranslateX = translateX;
      pinchStartTranslateY = translateY;
      dragging = false;
      pinching = true;
    }
  }

  function moveDrag(event: PointerEvent): void {
    if (!pointers.has(event.pointerId)) return;
    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (pointers.size >= 2) {
      const points = [...pointers.values()];
      const nextDistance = distanceBetween(points[0], points[1]);
      if (pinchStartDistance <= 0) return;
      const nextScale = Math.max(0.5, Math.min(15, pinchStartScale * (nextDistance / pinchStartDistance)));
      const ratio = nextScale / pinchStartScale;
      scale = nextScale;
      translateX = pinchStartTranslateX + (pinchCenterX - window.innerWidth / 2) * (1 - ratio);
      translateY = pinchStartTranslateY + (pinchCenterY - window.innerHeight / 2) * (1 - ratio);
      syncZoomedState();
      scheduleTransform();
      return;
    }

    if (!dragging) return;
    translateX = startTranslateX + event.clientX - dragStartX;
    translateY = startTranslateY + event.clientY - dragStartY;
    scheduleTransform();
  }

  function endDrag(event: PointerEvent): void {
    pointers.delete(event.pointerId);
    dragging = false;
    if (pointers.size < 2) {
      pinchStartDistance = 0;
      pinchStartScale = scale;
      pinching = false;
    }
  }

  function distanceBetween(a: { x: number; y: number }, b: { x: number; y: number }): number {
    return Math.hypot(b.x - a.x, b.y - a.y);
  }

  $effect(() => {
    src;
    loaded = false;
    resetTransform();
  });

  $effect(() => {
    return () => cancelAnimationFrame(rafId);
  });
</script>

<svelte:window onkeydown={(event) => event.key === 'Escape' && onClose()} />

<div class="lightbox open">
  <button class="lightbox-backdrop" aria-label="关闭" onclick={onClose}></button>
    <button class:zoomed class:dragging class:pinching class="lightbox-container" type="button" ondblclick={toggleZoom} onwheel={handleWheel} onpointerdown={startDrag} onpointermove={moveDrag} onpointerup={endDrag} onpointercancel={endDrag}>
    <img bind:this={imgEl} class="lightbox-img" {src} alt="" onload={() => loaded = true}>
    {#if !loaded}<div class="lightbox-loading"><div class="lightbox-spinner"></div></div>{/if}
  </button>
  <button class="lightbox-action lightbox-download" type="button" aria-label="下载图片" title="下载图片" disabled={downloading} onclick={(e) => { e.stopPropagation(); onDownload(); }}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/></svg></button>
  <button class="lightbox-action lightbox-close" aria-label="关闭" onclick={onClose}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg></button>
</div>

<style>
  .lightbox {
    position: fixed;
    inset: 0;
    z-index: 1000;
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity 0.25s ease;
  }

  .lightbox.open {
    opacity: 1;
  }

  button.lightbox-backdrop,
  button.lightbox-container {
    border: 0;
    background: transparent;
    color: inherit;
    font: inherit;
    padding: 0;
    position: absolute;
    appearance: none;
    outline: none;
  }

  button.lightbox-backdrop {
    inset: 0;
    z-index: 0;
    background-color: color-mix(in srgb, var(--background) 85%, transparent);
    backdrop-filter: blur(16px) saturate(180%);
    -webkit-backdrop-filter: blur(16px) saturate(180%);
  }

  button.lightbox-container {
    inset: 0;
    z-index: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    overflow: hidden;
    touch-action: none;
    user-select: none;
    cursor: zoom-in;
  }

  button.lightbox-container.zoomed {
    cursor: grab;
  }

  button.lightbox-container.dragging {
    cursor: grabbing;
  }

  .lightbox-img {
    max-width: 90vw;
    max-height: 85vh;
    object-fit: contain;
    border-radius: var(--radius);
    box-shadow: 0 24px 80px color-mix(in srgb, var(--foreground) 22%, transparent);
    user-select: none;
    -webkit-user-drag: none;
    transform-origin: center center;
    will-change: transform;
    transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  }

  .lightbox-container.dragging .lightbox-img,
  .lightbox-container.pinching .lightbox-img {
    transition: none;
  }

  .lightbox-loading {
    position: absolute;
    display: flex;
    align-items: center;
    justify-content: center;
    pointer-events: none;
  }

  .lightbox-spinner {
    width: 32px;
    height: 32px;
    border: 3px solid color-mix(in srgb, var(--foreground) 15%, transparent);
    border-top-color: var(--foreground);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }

  @keyframes spin {
    to { transform: rotate(360deg); }
  }

  .lightbox-action {
    position: absolute;
    top: 16px;
    z-index: 2;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border: 1px solid color-mix(in srgb, var(--border) 70%, var(--foreground));
    border-radius: 50%;
    background-color: color-mix(in srgb, var(--card) 92%, transparent);
    color: var(--foreground);
    cursor: pointer;
    box-shadow: 0 8px 24px -12px color-mix(in srgb, var(--foreground) 28%, transparent);
    backdrop-filter: blur(12px) saturate(180%);
    -webkit-backdrop-filter: blur(12px) saturate(180%);
    transition: background-color 0.15s, transform 0.15s, border-color 0.15s, box-shadow 0.2s;
  }

  .lightbox-download { right: 68px; }
  .lightbox-close { right: 16px; }

  .lightbox-action:hover {
    background-color: var(--accent);
    border-color: color-mix(in srgb, var(--border) 60%, var(--foreground));
    transform: scale(1.05);
  }

  .lightbox-action:focus-visible {
    outline: none;
    box-shadow: 0 0 0 2px var(--background), 0 0 0 4px var(--ring);
  }

  .lightbox-action:active {
    transform: scale(0.95);
  }

  .lightbox-action:disabled {
    cursor: wait;
    opacity: 0.65;
  }

  .lightbox-action svg {
    width: 20px;
    height: 20px;
  }

  @media (prefers-color-scheme: dark) {
    .lightbox-action:hover {
      border-color: color-mix(in srgb, var(--primary) 40%, var(--border));
      box-shadow: 0 0 20px -5px color-mix(in srgb, var(--primary) 15%, transparent);
    }
  }

  @media (max-width: 640px) {
    .lightbox-action {
      top: 12px;
      width: 36px;
      height: 36px;
    }

    .lightbox-download { right: 60px; }
    .lightbox-close { right: 12px; }

    .lightbox-action svg {
      width: 18px;
      height: 18px;
    }
  }
</style>
