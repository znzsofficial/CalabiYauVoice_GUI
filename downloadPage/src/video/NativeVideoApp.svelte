<script lang="ts">
  import { GIFEncoder, applyPalette, quantize } from 'gifenc';
  import { decompressFrames, parseGIF, type ParsedFrame } from 'gifuct-js';
  import JSZip from 'jszip';
  import { onDestroy, onMount } from 'svelte';
  import CustomSelect from '../CustomSelect.svelte';

  type Status = 'idle' | 'loading' | 'done' | 'error';
  type ExportFormat = 'image/png' | 'image/jpeg' | 'image/webp';
  type CropRatio = 'source' | '16:9' | '4:3' | '3:2' | '1:1' | '4:5' | '9:16' | 'custom';
  type OutputSize = 'source' | '1920x1080' | '1280x720' | '1080x1080' | '1080x1920' | '720x1280' | '1600x900' | '1200x1500' | 'custom';
  type CropAnchor = 'top-left' | 'top' | 'top-right' | 'left' | 'center' | 'right' | 'bottom-left' | 'bottom' | 'bottom-right';
  type BatchMode = 'count' | 'interval';
  type GifRepeat = 'forever' | 'once' | 'twice';
  type MotionVideoFormat = 'auto' | 'webm-vp9' | 'webm-vp8' | 'webm' | 'mp4-h264' | 'mp4';
  type MotionDirection = 'forward' | 'reverse' | 'pingpong';
  type WorkMode = 'video' | 'gif';
  type ToolTab = 'single' | 'batch' | 'gif';
  type GifFrameItem = { blob: Blob; delay: number; time: number; url: string };
  type SavedSettings = Partial<{
    exportFormat: ExportFormat;
    cropRatio: CropRatio;
    outputSize: OutputSize;
    cropAnchor: CropAnchor;
    customRatioWidth: number;
    customRatioHeight: number;
    customOutputWidth: number;
    customOutputHeight: number;
    batchMode: BatchMode;
    batchCount: number;
    batchInterval: number;
    sheetColumns: number;
    gifSeconds: number;
    gifFps: number;
    gifWidth: number;
    gifColors: number;
    gifRepeat: GifRepeat;
    webmBitrate: number;
    motionVideoFormat: MotionVideoFormat;
    motionDirection: MotionDirection;
    brightness: number;
    contrast: number;
    saturation: number;
    grayscale: number;
    hueRotate: number;
    blur: number;
    overlayText: string;
    overlaySize: number;
    overlayBand: boolean;
  }>;

  let file = $state<File | null>(null);
  let videoUrl = $state('');
  let previewUrl = $state('');
  let duration = $state(0);
  let position = $state(0);
  let rangeStart = $state(0);
  let rangeEnd = $state(0);
  let exportFormat = $state<ExportFormat>('image/png');
  let cropRatio = $state<CropRatio>('source');
  let outputSize = $state<OutputSize>('source');
  let cropAnchor = $state<CropAnchor>('center');
  let customRatioWidth = $state(21);
  let customRatioHeight = $state(9);
  let customOutputWidth = $state(1920);
  let customOutputHeight = $state(1080);
  let batchMode = $state<BatchMode>('count');
  let batchCount = $state(8);
  let batchInterval = $state(5);
  let sheetColumns = $state(4);
  let gifSeconds = $state(2);
  let gifFps = $state(8);
  let gifWidth = $state(480);
  let gifColors = $state(128);
  let gifRepeat = $state<GifRepeat>('forever');
  let webmBitrate = $state(2500);
  let motionVideoFormat = $state<MotionVideoFormat>('auto');
  let motionDirection = $state<MotionDirection>('forward');
  let brightness = $state(100);
  let contrast = $state(100);
  let saturation = $state(100);
  let grayscale = $state(0);
  let hueRotate = $state(0);
  let blur = $state(0);
  let overlayText = $state('');
  let overlaySize = $state(42);
  let overlayBand = $state(true);
  let gifPreviewUrl = $state('');
  let webmPreviewUrl = $state('');
  let splitGifName = $state('');
  let splitGifWidth = $state(0);
  let splitGifHeight = $state(0);
  let splitFrames = $state<GifFrameItem[]>([]);
  let timelineMarkers = $state<number[]>([]);
  let previewHistory = $state<{ url: string; info: string }[]>([]);
  let historyTimer: ReturnType<typeof setTimeout> | null = null;

  let markersOpen = $state(false);
  let fileInfoOpen = $state(false);
  let batchBusy = $state(false);
  let activeMode = $state<WorkMode>('video');
  let activeTool = $state<ToolTab>('single');
  let seekerHoverTime = $state(0);
  let seekerHoverPercent = $state(0);
  let seekerHovering = $state(false);
  let openSelect = $state('');
  let settingsReady = $state(false);
  let status = $state<Status>('idle');
  let message = $state('');
  let videoEl = $state<HTMLVideoElement | null>(null);
  let canvasEl = $state<HTMLCanvasElement | null>(null);
  let seekTimer: ReturnType<typeof setTimeout> | null = null;

  const settingsKey = 'downloadPage.video.settings';
  const maxSplitFrames = 240;
  const maxSplitPixels = 80_000_000;

  const formatOptions: { value: ExportFormat; label: string; extension: string }[] = [
    { value: 'image/png', label: 'PNG', extension: 'png' },
    { value: 'image/jpeg', label: 'JPEG', extension: 'jpg' },
    { value: 'image/webp', label: 'WebP', extension: 'webp' }
  ];

  const ratioOptions: { value: CropRatio; label: string }[] = [
    { value: 'custom', label: '自定义比例' },
    { value: 'source', label: '原始比例' },
    { value: '16:9', label: '16:9 封面' },
    { value: '4:3', label: '4:3 标准' },
    { value: '3:2', label: '3:2 横图' },
    { value: '1:1', label: '1:1 方图' },
    { value: '4:5', label: '4:5 竖图' },
    { value: '9:16', label: '9:16 竖屏' }
  ];

  const outputOptions: { value: OutputSize; label: string }[] = [
    { value: 'custom', label: '自定义尺寸' },
    { value: 'source', label: '素材尺寸' },
    { value: '1920x1080', label: '1920 x 1080' },
    { value: '1280x720', label: '1280 x 720' },
    { value: '1080x1080', label: '1080 x 1080' },
    { value: '1080x1920', label: '1080 x 1920' },
    { value: '720x1280', label: '720 x 1280' },
    { value: '1600x900', label: '1600 x 900' },
    { value: '1200x1500', label: '1200 x 1500' }
  ];

  const cropAnchors: { value: CropAnchor; label: string }[] = [
    { value: 'top-left', label: '左上' },
    { value: 'top', label: '顶部' },
    { value: 'top-right', label: '右上' },
    { value: 'left', label: '左侧' },
    { value: 'center', label: '居中' },
    { value: 'right', label: '右侧' },
    { value: 'bottom-left', label: '左下' },
    { value: 'bottom', label: '底部' },
    { value: 'bottom-right', label: '右下' }
  ];

  const motionVideoOptions: { value: MotionVideoFormat; label: string; mime: string; extension: string }[] = [
    { value: 'webm-vp9', label: 'WebM VP9', mime: 'video/webm;codecs=vp9', extension: 'webm' },
    { value: 'webm-vp8', label: 'WebM VP8', mime: 'video/webm;codecs=vp8', extension: 'webm' },
    { value: 'webm', label: 'WebM', mime: 'video/webm', extension: 'webm' },
    { value: 'mp4-h264', label: 'MP4 H.264', mime: 'video/mp4;codecs=avc1.42E01E', extension: 'mp4' },
    { value: 'mp4', label: 'MP4', mime: 'video/mp4', extension: 'mp4' }
  ];

  const progressPercent = $derived(duration > 0 ? Math.max(0, Math.min(position / duration * 100, 100)) : 0);
  const sampleTimes = $derived(getSampleTimes());
  const batchMarkers = $derived(duration > 0 && activeTool === 'batch' ? sampleTimes.map(time => time / duration * 100) : []);
  const savedMarkers = $derived(duration > 0 ? timelineMarkers.map(time => time / duration * 100) : []);
  const rangeStartPercent = $derived(duration > 0 ? Math.max(0, Math.min(rangeStart / duration * 100, 100)) : 0);
  const rangeEndPercent = $derived(duration > 0 ? Math.max(0, Math.min((rangeEnd || duration) / duration * 100, 100)) : 100);
  const videoAspectStyle = $derived(videoEl?.videoWidth && videoEl.videoHeight ? `aspect-ratio: ${videoEl.videoWidth} / ${videoEl.videoHeight};` : '');
  const cropOverlayStyle = $derived(getCropOverlayStyle());
  const outputInfo = $derived(getOutputInfo());
  const supportedMotionVideoOptions = $derived(typeof MediaRecorder === 'undefined' ? [] : motionVideoOptions.filter(option => MediaRecorder.isTypeSupported(option.mime)));
  const formatSelectOptions = $derived(formatOptions.map(option => ({ code: option.value, name: option.label })));
  const ratioSelectOptions = $derived(ratioOptions.map(option => ({ code: option.value, name: option.label })));
  const outputSelectOptions = $derived(outputOptions.map(option => ({ code: option.value, name: option.label })));
  const motionVideoSelectOptions = $derived([{ code: 'auto', name: '自动' }, ...motionVideoOptions.filter(option => supportedMotionVideoOptions.some(item => item.value === option.value)).map(option => ({ code: option.value, name: option.label }))]);

  $effect(() => {
    if (typeof localStorage === 'undefined') return;
    if (!settingsReady) return;
    const settings: SavedSettings = { exportFormat, cropRatio, outputSize, cropAnchor, customRatioWidth, customRatioHeight, customOutputWidth, customOutputHeight, batchMode, batchCount, batchInterval, sheetColumns, gifSeconds, gifFps, gifWidth, gifColors, gifRepeat, webmBitrate, motionVideoFormat, motionDirection, brightness, contrast, saturation, grayscale, hueRotate, blur, overlayText, overlaySize, overlayBand };
    localStorage.setItem(settingsKey, JSON.stringify(settings));
  });

  function revoke(url: string): void {
    if (url) URL.revokeObjectURL(url);
  }

  function clearSplitFrames(): void {
    splitFrames.forEach(frame => revoke(frame.url));
    splitFrames = [];
    splitGifName = '';
    splitGifWidth = 0;
    splitGifHeight = 0;
  }

  function clearPreviewHistory(): void {
    previewHistory.forEach(item => revoke(item.url));
    previewHistory = [];
  }

  function chooseFile(event: Event): void {
    const next = (event.currentTarget as HTMLInputElement).files?.[0] || null;
    setFile(next);
    (event.currentTarget as HTMLInputElement).value = '';
  }

  function setFile(next: File | null): void {
    file = next;
    duration = 0;
    position = 0;
    rangeStart = 0;
    rangeEnd = 0;
    timelineMarkers = [];
    status = next ? 'loading' : 'idle';
    message = next ? '读取中…' : '';
    if (historyTimer) clearTimeout(historyTimer);
    historyTimer = null;
    clearPreviewHistory();
    revoke(videoUrl);
    revoke(previewUrl);
    revoke(gifPreviewUrl);
    revoke(webmPreviewUrl);
    previewUrl = '';
    gifPreviewUrl = '';
    webmPreviewUrl = '';
    videoUrl = next ? URL.createObjectURL(next) : '';
  }

  async function chooseSplitGif(event: Event): Promise<void> {
    const next = (event.currentTarget as HTMLInputElement).files?.[0] || null;
    (event.currentTarget as HTMLInputElement).value = '';
    if (!next) return;
    await parseSplitGif(next);
  }

  async function parseSplitGif(next: File): Promise<void> {
    clearSplitFrames();
    status = 'loading';
    message = '拆解中…';
    try {
      const parsed = parseGIF(await next.arrayBuffer());
      const frames = decompressFrames(parsed, true) as ParsedFrame[];
      const pixelBudget = parsed.lsd.width * parsed.lsd.height * frames.length;
      if (frames.length > maxSplitFrames || pixelBudget > maxSplitPixels) throw new Error('gif budget exceeded');
      splitGifName = next.name;
      splitGifWidth = parsed.lsd.width;
      splitGifHeight = parsed.lsd.height;
      splitFrames = await renderGifFrames(frames, parsed.lsd.width, parsed.lsd.height);
      status = 'done';
      message = `${splitFrames.length} 帧`;
    } catch {
      status = 'error';
      message = '拆解失败';
    }
  }

  async function renderGifFrames(frames: ParsedFrame[], width: number, height: number): Promise<GifFrameItem[]> {
    const fullCanvas = document.createElement('canvas');
    const patchCanvas = document.createElement('canvas');
    fullCanvas.width = width;
    fullCanvas.height = height;
    const full = fullCanvas.getContext('2d');
    const patch = patchCanvas.getContext('2d');
    if (!full || !patch) return [];
    let previous: { dims: ParsedFrame['dims']; disposalType: number; restore?: ImageData } | null = null;
    let elapsed = 0;
    const output: GifFrameItem[] = [];
    try {
      for (const frame of frames) {
        if (previous?.disposalType === 2) full.clearRect(previous.dims.left, previous.dims.top, previous.dims.width, previous.dims.height);
        if (previous?.disposalType === 3 && previous.restore) full.putImageData(previous.restore, 0, 0);
        const restore = frame.disposalType === 3 ? full.getImageData(0, 0, width, height) : undefined;
        patchCanvas.width = frame.dims.width;
        patchCanvas.height = frame.dims.height;
        patch.putImageData(new ImageData(new Uint8ClampedArray(frame.patch), frame.dims.width, frame.dims.height), 0, 0);
        full.drawImage(patchCanvas, frame.dims.left, frame.dims.top);
        const blob = await blobFromCanvas(fullCanvas, 'image/png');
        const delay = Math.max(frame.delay * 10, 20);
        if (blob) output.push({ blob, delay, time: elapsed, url: URL.createObjectURL(blob) });
        elapsed += delay;
        previous = { dims: frame.dims, disposalType: frame.disposalType, restore };
      }
    } catch (error) {
      output.forEach(frame => revoke(frame.url));
      throw error;
    }
    return output;
  }

  function onDrop(event: DragEvent): void {
    event.preventDefault();
    const next = event.dataTransfer?.files?.[0] || null;
    if (next) setFile(next);
  }

  function onVideoLoaded(): void {
    duration = Number.isFinite(videoEl?.duration || 0) ? videoEl?.duration || 0 : 0;
    rangeStart = 0;
    rangeEnd = duration;
    status = duration > 0 ? 'idle' : 'error';
    message = duration > 0 ? '' : '读取中…';
    seekFrame(0, 0);
  }

  function seekFrame(value: number, delay = 100): void {
    position = Math.max(0, Math.min(value, duration || value));
    if (seekTimer) clearTimeout(seekTimer);
    seekTimer = setTimeout(() => {
      if (videoEl) videoEl.currentTime = position;
    }, delay);
  }

  function stepFrame(delta: number): void {
    seekFrame(position + delta, 0);
  }

  function addMarker(): void {
    const next = Math.max(0, Math.min(position, duration || position));
    if (timelineMarkers.some(time => Math.abs(time - next) < 0.04)) return;
    timelineMarkers = [...timelineMarkers, next].sort((a, b) => a - b);
    status = 'done';
    message = `标记 ${formatTime(next)}`;
  }

  function removeMarker(marker: number): void {
    timelineMarkers = timelineMarkers.filter(time => time !== marker);
  }

  function clearMarkers(): void {
    timelineMarkers = [];
    markersOpen = false;
  }

  function setRangeStart(): void {
    rangeStart = Math.max(0, Math.min(position, rangeEnd || duration || position));
  }

  function setRangeEnd(): void {
    rangeEnd = Math.max(rangeStart, Math.min(position, duration || position));
  }

  function updateRangeStart(event: Event): void {
    const value = Number((event.currentTarget as HTMLInputElement).value);
    rangeStart = Math.max(0, Math.min(Number.isFinite(value) ? value : 0, rangeEnd || duration || 0));
  }

  function updateRangeEnd(event: Event): void {
    const value = Number((event.currentTarget as HTMLInputElement).value);
    rangeEnd = Math.max(rangeStart, Math.min(Number.isFinite(value) ? value : duration, duration || 0));
  }

  function resetRange(): void {
    rangeStart = 0;
    rangeEnd = duration;
  }

  function updateSeekerHover(event: PointerEvent): void {
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const percent = Math.max(0, Math.min((event.clientX - rect.left) / rect.width, 1));
    seekerHoverPercent = percent * 100;
    seekerHoverTime = percent * (duration || 0);
    seekerHovering = true;
  }

  function onVideoSeeked(): void {
    if (!batchBusy) void updatePreview();
  }

  function parseSize(value: OutputSize): { width: number; height: number } | null {
    if (value === 'source') return null;
    if (value === 'custom') {
      const width = Math.max(64, Math.min(Math.round(Number(customOutputWidth) || 1920), 4096));
      const height = Math.max(64, Math.min(Math.round(Number(customOutputHeight) || 1080), 4096));
      return { width, height };
    }
    const [width, height] = value.split('x').map(Number);
    return Number.isFinite(width) && Number.isFinite(height) ? { width, height } : null;
  }

  function getRatioValue(ratio = cropRatio): string {
    if (ratio !== 'custom') return ratio;
    const width = Math.max(1, Number(customRatioWidth) || 1);
    const height = Math.max(1, Number(customRatioHeight) || 1);
    return `${width}:${height}`;
  }

  function getTargetSize(sourceWidth: number, sourceHeight: number, ratio = getRatioValue()): { width: number; height: number } {
    if (ratio === 'source') return { width: sourceWidth, height: sourceHeight };
    const [ratioWidth, ratioHeight] = ratio.split(':').map(Number);
    if (!ratioWidth || !ratioHeight) return { width: sourceWidth, height: sourceHeight };
    const sourceRatio = sourceWidth / sourceHeight;
    const targetRatio = ratioWidth / ratioHeight;
    if (sourceRatio > targetRatio) {
      return { width: Math.round(sourceHeight * targetRatio), height: sourceHeight };
    }
    return { width: sourceWidth, height: Math.round(sourceWidth / targetRatio) };
  }

  function getCropSource(sourceWidth: number, sourceHeight: number): { x: number; y: number; width: number; height: number } {
    const { width, height } = getTargetSize(sourceWidth, sourceHeight);
    const maxX = Math.max(0, sourceWidth - width);
    const maxY = Math.max(0, sourceHeight - height);
    const x = cropAnchor.endsWith('right') || cropAnchor === 'right' ? maxX : cropAnchor.endsWith('left') || cropAnchor === 'left' ? 0 : maxX / 2;
    const y = cropAnchor.startsWith('bottom') || cropAnchor === 'bottom' ? maxY : cropAnchor.startsWith('top') || cropAnchor === 'top' ? 0 : maxY / 2;
    return { x: Math.round(x), y: Math.round(y), width, height };
  }

  function getOutputSize(cropWidth: number, cropHeight: number, outputWidth?: number): { width: number; height: number } {
    if (outputWidth) {
      const width = Math.max(64, Math.min(Math.round(outputWidth), cropWidth));
      return { width, height: Math.max(1, Math.round(width * cropHeight / cropWidth)) };
    }
    return parseSize(outputSize) || { width: cropWidth, height: cropHeight };
  }

  function getOutputInfo(): string {
    if (!videoEl?.videoWidth || !videoEl.videoHeight) return `${formatOptions.find(option => option.value === exportFormat)?.label || 'PNG'} / —`;
    const crop = getCropSource(videoEl.videoWidth, videoEl.videoHeight);
    const size = getOutputSize(crop.width, crop.height);
    const ratio = cropRatio === 'custom' ? `${customRatioWidth}:${customRatioHeight}` : ratioOptions.find(option => option.value === cropRatio)?.label || '原始比例';
    const format = formatOptions.find(option => option.value === exportFormat)?.label || 'PNG';
    return `${format} / ${size.width} x ${size.height} / ${ratio}`;
  }

  function getCropOverlayStyle(): string {
    if (!videoEl?.videoWidth || !videoEl.videoHeight) return 'display: none;';
    const crop = getCropSource(videoEl.videoWidth, videoEl.videoHeight);
    const left = crop.x / videoEl.videoWidth * 100;
    const top = crop.y / videoEl.videoHeight * 100;
    const width = crop.width / videoEl.videoWidth * 100;
    const height = crop.height / videoEl.videoHeight * 100;
    return `left: ${left}%; top: ${top}%; width: ${width}%; height: ${height}%;`;
  }

  function blobFromCanvas(canvas: HTMLCanvasElement, type: ExportFormat): Promise<Blob | null> {
    const quality = type === 'image/png' ? undefined : 0.92;
    return new Promise(resolve => canvas.toBlob(resolve, type, quality));
  }

  function drawTextOverlay(context: CanvasRenderingContext2D, width: number, height: number): void {
    const text = overlayText.trim();
    if (!text) return;
    const lines = text.split(/\r?\n/).map(line => line.trim()).filter(Boolean).slice(0, 3);
    if (!lines.length) return;
    const baseSize = Math.max(18, Math.min(Number(overlaySize) || 42, 120));
    const fontSize = Math.max(12, Math.round(width / 1280 * baseSize));
    const padding = Math.round(fontSize * 0.75);
    const lineHeight = Math.round(fontSize * 1.18);
    const bandHeight = padding * 2 + lineHeight * lines.length;
    const y = height - bandHeight;
    context.save();
    if (overlayBand) {
      const gradient = context.createLinearGradient(0, y - padding, 0, height);
      gradient.addColorStop(0, 'rgba(0, 0, 0, 0)');
      gradient.addColorStop(0.28, 'rgba(0, 0, 0, 0.58)');
      gradient.addColorStop(1, 'rgba(0, 0, 0, 0.78)');
      context.fillStyle = gradient;
      context.fillRect(0, Math.max(0, y - padding), width, Math.min(height, bandHeight + padding));
    }
    context.font = `700 ${fontSize}px system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`;
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.fillStyle = '#fff';
    context.strokeStyle = 'rgba(0, 0, 0, 0.62)';
    context.lineWidth = Math.max(3, Math.round(fontSize * 0.12));
    lines.forEach((line, index) => {
      const lineY = y + padding + lineHeight * index + lineHeight / 2;
      context.strokeText(line, width / 2, lineY);
      context.fillText(line, width / 2, lineY);
    });
    context.restore();
  }

  function drawCurrentFrame(outputWidth?: number): CanvasRenderingContext2D | null {
    if (!videoEl || !canvasEl || videoEl.readyState < 2) return null;
    const sourceWidth = videoEl.videoWidth;
    const sourceHeight = videoEl.videoHeight;
    if (!sourceWidth || !sourceHeight) return null;
    const { x: sourceX, y: sourceY, width, height } = getCropSource(sourceWidth, sourceHeight);
    const { width: targetWidth, height: targetHeight } = getOutputSize(width, height, outputWidth);
    canvasEl.width = targetWidth;
    canvasEl.height = targetHeight;
    const context = canvasEl.getContext('2d');
    if (!context) return null;
    context.filter = `brightness(${brightness}%) contrast(${contrast}%) saturate(${saturation}%) grayscale(${grayscale}%) hue-rotate(${hueRotate}deg) blur(${blur}px)`;
    context.drawImage(videoEl, sourceX, sourceY, width, height, 0, 0, targetWidth, targetHeight);
    context.filter = 'none';
    drawTextOverlay(context, targetWidth, targetHeight);
    return context;
  }

  async function captureFrame(type: ExportFormat = exportFormat): Promise<Blob | null> {
    if (!canvasEl || !drawCurrentFrame()) return null;
    return blobFromCanvas(canvasEl, type);
  }

  async function updatePreview(): Promise<void> {
    const blob = await captureFrame();
    if (!blob) return;
    const url = URL.createObjectURL(blob);
    const previousUrl = previewUrl;
    previewUrl = url;
    if (previousUrl && !previewHistory.some(item => item.url === previousUrl)) revoke(previousUrl);
    if (historyTimer) clearTimeout(historyTimer);
    historyTimer = setTimeout(() => {
      const info = getOutputInfo();
      const nextHistory = [{ url, info }, ...previewHistory.filter(item => item.url !== url)];
      previewHistory = nextHistory.slice(0, 6);
      nextHistory.slice(6).forEach(item => {
        if (item.url !== previewUrl) revoke(item.url);
      });
    }, 800);
  }

  function removeHistoryItem(index: number): void {
    const item = previewHistory[index];
    if (item && item.url !== previewUrl) URL.revokeObjectURL(item.url);
    previewHistory = previewHistory.filter((_, i) => i !== index);
  }

  function getExtension(type = exportFormat): string {
    return formatOptions.find(option => option.value === type)?.extension || 'png';
  }

  function getFrameName(index: number, time: number, extension = getExtension()): string {
    const ratioSuffix = cropRatio === 'source' ? 'source' : cropRatio.replace(':', 'x');
    return `frame_${String(index + 1).padStart(3, '0')}_${Math.round(time * 1000)}ms_${ratioSuffix}.${extension}`;
  }

  function downloadBlob(blob: Blob, name: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    link.click();
    URL.revokeObjectURL(url);
  }

  function basename(name: string): string {
    return name.replace(/\.[^.]+$/, '') || 'video';
  }

  function getSampleTimes(): number[] {
    const start = Math.max(0, Math.min(rangeStart, duration || 0));
    const end = Math.max(start, Math.min(rangeEnd || duration || start, duration || start));
    if (batchMode === 'interval') {
      const interval = Math.max(0.25, Number(batchInterval) || 1);
      const times: number[] = [];
      for (let t = start; t <= end; t += interval) {
        times.push(t);
        if (times.length >= 60) break;
      }
      return times;
    }
    const count = Math.max(1, Math.min(Math.round(Number(batchCount) || 8), 60));
    if (count === 1) return [start];
    const step = (end - start) / (count - 1);
    return Array.from({ length: count }, (_, i) => start + i * step);
  }

  async function seekTo(time: number): Promise<void> {
    if (!videoEl) return;
    const target = Math.max(0, Math.min(time, duration || time));
    if (Math.abs(videoEl.currentTime - target) < 0.001 && videoEl.readyState >= 2) return;
    return new Promise(resolve => {
      let settled = false;
      const finish = (): void => {
        if (settled) return;
        settled = true;
        if (timeout) clearTimeout(timeout);
        videoEl?.removeEventListener('seeked', onSeeked);
        resolve();
      };
      const onSeeked = (): void => {
        finish();
      };
      videoEl?.addEventListener('seeked', onSeeked);
      const timeout = setTimeout(finish, 3000);
      videoEl!.currentTime = target;
    });
  }

  async function copyFrame(): Promise<void> {
    if (!canvasEl || !drawCurrentFrame() || typeof ClipboardItem === 'undefined') return;
    status = 'loading';
    message = '复制中…';
    try {
      const blob = await blobFromCanvas(canvasEl, 'image/png');
      if (!blob) throw new Error('blob failed');
      await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
      status = 'done';
      message = '已复制';
    } catch {
      status = 'error';
      message = '复制失败';
    }
  }

  async function exportFrame(): Promise<void> {
    const blob = await captureFrame();
    if (blob) downloadBlob(blob, getFrameName(0, position));
  }

  async function exportBatchZip(): Promise<void> {
    if (!file || !canvasEl) return;
    const times = sampleTimes;
    const zip = new JSZip();
    batchBusy = true;
    status = 'loading';
    try {
      let exported = 0;
      for (const [index, time] of times.entries()) {
        message = `${index + 1}/${times.length}`;
        await seekTo(time);
        const blob = await captureFrame();
        if (blob) {
          zip.file(getFrameName(index, time), blob);
          exported += 1;
        }
      }
      if (exported === 0) throw new Error('empty export');
      message = '打包中…';
      const content = await zip.generateAsync({ type: 'blob' });
      downloadBlob(content, `frames_${basename(file.name)}.zip`);
      status = 'done';
      message = `已导出 ${exported} 张`;
    } catch {
      status = 'error';
      message = '导出失败';
    } finally {
      batchBusy = false;
      seekFrame(position, 0);
    }
  }

  async function exportMarkerZip(): Promise<void> {
    if (!file || !canvasEl || !timelineMarkers.length) return;
    const zip = new JSZip();
    batchBusy = true;
    status = 'loading';
    try {
      let exported = 0;
      for (const [index, time] of timelineMarkers.entries()) {
        message = `标记 ${index + 1}/${timelineMarkers.length}`;
        await seekTo(time);
        const blob = await captureFrame();
        if (blob) {
          zip.file(getFrameName(index, time), blob);
          exported += 1;
        }
      }
      if (exported === 0) throw new Error('empty export');
      message = '打包中…';
      const content = await zip.generateAsync({ type: 'blob' });
      downloadBlob(content, `markers_${basename(file.name)}.zip`);
      status = 'done';
      message = `已导出 ${exported} 张`;
    } catch {
      status = 'error';
      message = '导出失败';
    } finally {
      batchBusy = false;
      seekFrame(position, 0);
    }
  }

  async function exportContactSheet(): Promise<void> {
    if (!file || !canvasEl) return;
    const times = sampleTimes;
    const cols = Math.max(1, Math.min(Math.round(Number(sheetColumns) || 4), 12));
    const rows = Math.ceil(times.length / cols);
    const sourceWidth = videoEl?.videoWidth || 1920;
    const sourceHeight = videoEl?.videoHeight || 1080;
    const { width: thumbWidth, height: thumbHeight } = getOutputSize(sourceWidth, sourceHeight, 480);
    const sheetCanvas = document.createElement('canvas');
    const padding = 20;
    const headerHeight = 60;
    sheetCanvas.width = cols * thumbWidth + (cols + 1) * padding;
    sheetCanvas.height = rows * thumbHeight + (rows + 1) * padding + headerHeight;
    const ctx = sheetCanvas.getContext('2d');
    if (!ctx) return;
    batchBusy = true;
    status = 'loading';
    try {
      let exported = 0;
      ctx.fillStyle = '#18181b';
      ctx.fillRect(0, 0, sheetCanvas.width, sheetCanvas.height);
      ctx.fillStyle = '#fff';
      ctx.font = 'bold 20px system-ui';
      ctx.fillText(`${file.name} · ${times.length} 帧`, padding, 40);
      for (const [index, time] of times.entries()) {
        message = `联系表 ${index + 1}/${times.length}`;
        await seekTo(time);
        const context = drawCurrentFrame(thumbWidth);
        if (context) {
          const x = (index % cols) * thumbWidth + (index % cols + 1) * padding;
          const y = Math.floor(index / cols) * thumbHeight + (Math.floor(index / cols) + 1) * padding + headerHeight;
          ctx.drawImage(context.canvas, x, y);
          ctx.fillStyle = 'rgba(0,0,0,0.6)';
          ctx.fillRect(x + 5, y + 5, 80, 24);
          ctx.fillStyle = '#fff';
          ctx.font = '14px tabular-nums system-ui';
          ctx.fillText(formatTime(time), x + 12, y + 22);
          exported += 1;
        }
      }
      if (exported === 0) throw new Error('empty export');
      const blob = await blobFromCanvas(sheetCanvas, 'image/jpeg');
      if (blob) downloadBlob(blob, `sheet_${basename(file.name)}.jpg`);
      status = 'done';
      message = '联系表已导出';
    } catch {
      status = 'error';
      message = '联系表失败';
    } finally {
      batchBusy = false;
      seekFrame(position, 0);
    }
  }

  function getMotionTimes(frameCount: number, fps: number, requestedSeconds: number): { times: number[]; seconds: number } {
    const start = Math.max(0, Math.min(rangeStart, duration || 0));
    const end = Math.max(start, Math.min(rangeEnd || duration || start, duration || start));
    const availableSeconds = end - start;
    const seconds = Math.min(requestedSeconds, availableSeconds);
    const step = 1 / fps;
    const forward: number[] = [];
    for (let i = 0; i < frameCount; i++) {
      const t = start + i * step;
      if (t > end) break;
      forward.push(t);
    }
    if (motionDirection === 'reverse') return { times: [...forward].reverse(), seconds };
    if (motionDirection === 'pingpong' && forward.length > 2) return { times: [...forward, ...forward.slice(1, -1).reverse()], seconds };
    return { times: forward, seconds };
  }

  async function exportWebm(): Promise<void> {
    if (!file || !videoEl || !canvasEl || !canvasEl.captureStream || typeof MediaRecorder === 'undefined') {
      status = 'error';
      message = '不可用';
      return;
    }
    const format = resolveMotionVideoFormat();
    if (!format) {
      status = 'error';
      message = '格式不可用';
      return;
    }
    const fps = Math.max(2, Math.min(Math.round(Number(gifFps) || 8), 30));
    const width = Math.max(96, Math.min(Math.round(Number(gifWidth) || 480), 1280));
    const bitrate = Math.max(250, Math.min(Math.round(Number(webmBitrate) || 2500), 12000)) * 1000;
    const requestedSeconds = Math.max(0.5, Math.min(Number(gifSeconds) || 2, 10));
    const frameCount = Math.max(1, Math.min(Math.round(requestedSeconds * fps), 300));
    const { times, seconds } = getMotionTimes(frameCount, fps, requestedSeconds);
    const bitmaps: ImageBitmap[] = [];
    batchBusy = true;
    status = 'loading';
    try {
      for (const [index, time] of times.entries()) {
        message = `短视频 ${index + 1}/${times.length}`;
        await seekTo(time);
        if (!drawCurrentFrame(width)) throw new Error('webm frame failed');
        bitmaps.push(await createImageBitmap(canvasEl));
      }
      const stream = canvasEl.captureStream(0);
      const recorder = new MediaRecorder(stream, { mimeType: format.mime, videoBitsPerSecond: bitrate });
      const chunks: Blob[] = [];
      recorder.ondataavailable = (e) => chunks.push(e.data);
      const recordPromise = new Promise<Blob>((resolve) => {
        recorder.onstop = () => resolve(new Blob(chunks, { type: format.mime }));
      });
      recorder.start();
      const track = stream.getVideoTracks()[0] as any;
      for (const bitmap of bitmaps) {
        const ctx = canvasEl.getContext('2d');
        if (ctx) {
          ctx.clearRect(0, 0, canvasEl.width, canvasEl.height);
          ctx.drawImage(bitmap, 0, 0);
          if (track?.requestFrame) track.requestFrame();
        }
        await new Promise(r => setTimeout(r, 1000 / fps));
      }
      recorder.stop();
      const videoBlob = await recordPromise;
      revoke(webmPreviewUrl);
      webmPreviewUrl = URL.createObjectURL(videoBlob);
      downloadBlob(videoBlob, `motion_${basename(file.name)}.${format.extension}`);
      status = 'done';
      message = `短视频 ${seconds.toFixed(1)}s`;
    } catch {
      status = 'error';
      message = '导出失败';
    } finally {
      bitmaps.forEach(b => b.close());
      batchBusy = false;
      seekFrame(position, 0);
    }
  }

  function resolveMotionVideoFormat(): (typeof motionVideoOptions)[0] | null {
    if (motionVideoFormat !== 'auto') return motionVideoOptions.find(o => o.value === motionVideoFormat) || null;
    return supportedMotionVideoOptions[0] || null;
  }

  async function exportGif(): Promise<void> {
    if (!file || !canvasEl) return;
    const fps = Math.max(2, Math.min(Math.round(Number(gifFps) || 8), 15));
    const width = Math.max(96, Math.min(Math.round(Number(gifWidth) || 320), 800));
    const colors = Math.max(16, Math.min(Math.round(Number(gifColors) || 128), 256));
    const requestedSeconds = Math.max(0.5, Math.min(Number(gifSeconds) || 2, 6));
    const frameCount = Math.max(1, Math.min(Math.round(requestedSeconds * fps), 90));
    const { times, seconds } = getMotionTimes(frameCount, fps, requestedSeconds);
    batchBusy = true;
    status = 'loading';
    try {
      const gif = new (GIFEncoder as any)();
      for (const [index, time] of times.entries()) {
        message = `GIF ${index + 1}/${times.length}`;
        await seekTo(time);
        const context = drawCurrentFrame(width);
        if (context) {
          const { data, width: w, height: h } = context.getImageData(0, 0, canvasEl.width, canvasEl.height);
          const palette = quantize(data, colors);
          const indexData = applyPalette(data, palette);
          gif.writeFrame(indexData, w, h, { palette, delay: 1000 / fps, repeat: gifRepeat === 'forever' ? 0 : gifRepeat === 'once' ? -1 : 1 });
        }
      }
      gif.finish();
      const blob = new Blob([gif.bytes()], { type: 'image/gif' });
      revoke(gifPreviewUrl);
      gifPreviewUrl = URL.createObjectURL(blob);
      downloadBlob(blob, `motion_${basename(file.name)}.gif`);
      status = 'done';
      message = `GIF ${seconds.toFixed(1)}s`;
    } catch {
      status = 'error';
      message = '导出失败';
    } finally {
      batchBusy = false;
      seekFrame(position, 0);
    }
  }

  async function exportSplitSheet(): Promise<void> {
    if (!splitFrames.length) return;
    const cols = Math.max(1, Math.min(Math.round(Number(sheetColumns) || 4), 12));
    const rows = Math.ceil(splitFrames.length / cols);
    const thumbWidth = splitGifWidth;
    const thumbHeight = splitGifHeight;
    const sheetCanvas = document.createElement('canvas');
    const padding = 10;
    sheetCanvas.width = cols * thumbWidth + (cols + 1) * padding;
    sheetCanvas.height = rows * thumbHeight + (rows + 1) * padding;
    const ctx = sheetCanvas.getContext('2d');
    if (!ctx) return;
    ctx.fillStyle = '#18181b';
    ctx.fillRect(0, 0, sheetCanvas.width, sheetCanvas.height);
    for (const [index, frame] of splitFrames.entries()) {
      const img = await new Promise<HTMLImageElement>((resolve) => {
        const i = new Image();
        i.onload = () => resolve(i);
        i.src = frame.url;
      });
      const x = (index % cols) * thumbWidth + (index % cols + 1) * padding;
      const y = Math.floor(index / cols) * thumbHeight + (Math.floor(index / cols) + 1) * padding;
      ctx.drawImage(img, x, y);
      ctx.fillStyle = 'rgba(0,0,0,0.5)';
      ctx.fillRect(x + 2, y + 2, 60, 18);
      ctx.fillStyle = '#fff';
      ctx.font = '11px tabular-nums system-ui';
      ctx.fillText(formatTime(frame.time / 1000), x + 6, y + 14);
    }
    const blob = await blobFromCanvas(sheetCanvas, 'image/jpeg');
    if (blob) downloadBlob(blob, `gif_sheet_${basename(splitGifName)}.jpg`);
  }

  async function exportSplitZip(): Promise<void> {
    if (!splitFrames.length) return;
    const zip = new JSZip();
    splitFrames.forEach((frame, index) => {
      zip.file(`frame_${String(index + 1).padStart(3, '0')}.png`, frame.blob);
    });
    const content = await zip.generateAsync({ type: 'blob' });
    downloadBlob(content, `gif_frames_${basename(splitGifName)}.zip`);
  }

  function toggleSelect(key: string): void {
    openSelect = openSelect === key ? '' : key;
  }

  function selectExportFormat(value: string): void {
    exportFormat = value as ExportFormat;
    openSelect = '';
    void updatePreview();
  }

  function selectCropRatio(value: string): void {
    cropRatio = value as CropRatio;
    openSelect = '';
    void updatePreview();
  }

  function selectOutputSize(value: string): void {
    outputSize = value as OutputSize;
    openSelect = '';
    void updatePreview();
  }

  function setCropAnchor(value: CropAnchor): void {
    cropAnchor = value;
    void updatePreview();
  }

  function updateCustomRatio(): void {
    customRatioWidth = Math.max(1, Number(customRatioWidth) || 1);
    customRatioHeight = Math.max(1, Number(customRatioHeight) || 1);
    void updatePreview();
  }

  function updateCustomOutput(): void {
    customOutputWidth = Math.max(64, Math.min(Math.round(Number(customOutputWidth) || 1920), 4096));
    customOutputHeight = Math.max(64, Math.min(Math.round(Number(customOutputHeight) || 1080), 4096));
    void updatePreview();
  }

  function updateVisualAdjustments(): void {
    void updatePreview();
  }

  function selectBatchMode(value: string): void {
    batchMode = value as BatchMode;
    openSelect = '';
  }

  function selectMotionVideoFormat(value: string): void {
    motionVideoFormat = value as MotionVideoFormat;
    openSelect = '';
  }

  function selectMotionDirection(value: string): void {
    motionDirection = value as MotionDirection;
    openSelect = '';
  }

  function selectGifRepeat(value: string): void {
    gifRepeat = value as GifRepeat;
    openSelect = '';
  }

  function formatTime(seconds: number): string {
    const safe = Math.max(0, seconds || 0);
    const minutes = Math.floor(safe / 60);
    const secs = Math.floor(safe % 60);
    const millis = Math.floor((safe % 1) * 1000);
    return `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}.${String(millis).padStart(3, '0')}`;
  }

  function formatFileSize(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let index = 0;
    while (size >= 1024 && index < units.length - 1) {
      size /= 1024;
      index += 1;
    }
    return `${size.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
  }

  onMount(() => {
    const closeSelect = (): void => {
      openSelect = '';
    };
    document.addEventListener('click', closeSelect);
    try {
      const raw = localStorage.getItem(settingsKey);
      if (raw) {
        const settings = JSON.parse(raw) as SavedSettings;
        if (settings.exportFormat && formatOptions.some(option => option.value === settings.exportFormat)) exportFormat = settings.exportFormat;
        if (settings.cropRatio && ratioOptions.some(option => option.value === settings.cropRatio)) cropRatio = settings.cropRatio;
        if (settings.outputSize && outputOptions.some(option => option.value === settings.outputSize)) outputSize = settings.outputSize;
        if (settings.cropAnchor && cropAnchors.some(option => option.value === settings.cropAnchor)) cropAnchor = settings.cropAnchor;
        if (typeof settings.customRatioWidth === 'number') customRatioWidth = settings.customRatioWidth;
        if (typeof settings.customRatioHeight === 'number') customRatioHeight = settings.customRatioHeight;
        if (typeof settings.customOutputWidth === 'number') customOutputWidth = settings.customOutputWidth;
        if (typeof settings.customOutputHeight === 'number') customOutputHeight = settings.customOutputHeight;
        if (settings.batchMode === 'count' || settings.batchMode === 'interval') batchMode = settings.batchMode;
        if (typeof settings.batchCount === 'number') batchCount = settings.batchCount;
        if (typeof settings.batchInterval === 'number') batchInterval = settings.batchInterval;
        if (typeof settings.sheetColumns === 'number') sheetColumns = settings.sheetColumns;
        if (typeof settings.gifSeconds === 'number') gifSeconds = settings.gifSeconds;
        if (typeof settings.gifFps === 'number') gifFps = settings.gifFps;
        if (typeof settings.gifWidth === 'number') gifWidth = settings.gifWidth;
        if (typeof settings.gifColors === 'number') gifColors = settings.gifColors;
        if (settings.gifRepeat === 'forever' || settings.gifRepeat === 'once' || settings.gifRepeat === 'twice') gifRepeat = settings.gifRepeat;
        if (typeof settings.webmBitrate === 'number') webmBitrate = settings.webmBitrate;
        if (settings.motionVideoFormat && (settings.motionVideoFormat === 'auto' || motionVideoOptions.some(option => option.value === settings.motionVideoFormat))) motionVideoFormat = settings.motionVideoFormat;
        if (settings.motionDirection === 'forward' || settings.motionDirection === 'reverse' || settings.motionDirection === 'pingpong') motionDirection = settings.motionDirection;
        if (typeof settings.brightness === 'number') brightness = settings.brightness;
        if (typeof settings.contrast === 'number') contrast = settings.contrast;
        if (typeof settings.saturation === 'number') saturation = settings.saturation;
        if (typeof settings.grayscale === 'number') grayscale = settings.grayscale;
        if (typeof settings.hueRotate === 'number') hueRotate = settings.hueRotate;
        if (typeof settings.blur === 'number') blur = settings.blur;
        if (typeof settings.overlayText === 'string') overlayText = settings.overlayText;
        if (typeof settings.overlaySize === 'number') overlaySize = settings.overlaySize;
        if (typeof settings.overlayBand === 'boolean') overlayBand = settings.overlayBand;
      }
    } catch {
      localStorage.removeItem(settingsKey);
    } finally {
      settingsReady = true;
    }
    return () => document.removeEventListener('click', closeSelect);
  });

  onDestroy(() => {
    if (seekTimer) clearTimeout(seekTimer);
    revoke(videoUrl);
    revoke(previewUrl);
    revoke(gifPreviewUrl);
    revoke(webmPreviewUrl);
    clearPreviewHistory();
    splitFrames.forEach(frame => revoke(frame.url));
  });
</script>

<svelte:head>
  <meta property="og:image" content="/icon.svg">
</svelte:head>

<header class="header">
  <div class="header-content">
    <a href="/" class="header-back" aria-label="返回首页"><iconify-icon icon="lucide:chevron-left"></iconify-icon></a>
    <h1 class="header-title"><iconify-icon class="header-logo" icon="lucide:film"></iconify-icon>视频素材工具台</h1>
    <a class="header-link" href="/search/">Wiki 搜索</a>
  </div>
</header>

<main class="main native-video-page">
  <section class="workbench-head">
    <div class="workbench-copy">
      <strong><iconify-icon icon={activeMode === 'video' ? 'lucide:clapperboard' : 'lucide:layers-3'}></iconify-icon>{activeMode === 'video' ? '视频' : 'GIF'}</strong>
      <span>{activeMode === 'video' ? '截图、批量抽帧、联系表、GIF、短视频片段' : 'GIF 拆帧、PNG 帧包、时间码联系表'}</span>
    </div>
    <div class:video={activeMode === 'video'} class:gif={activeMode === 'gif'} class="mode-switch" role="tablist" aria-label="模式">
      <button type="button" class:active={activeMode === 'video'} onclick={() => activeMode = 'video'}><iconify-icon icon="lucide:video"></iconify-icon>视频</button>
      <button type="button" class:active={activeMode === 'gif'} onclick={() => activeMode = 'gif'}><iconify-icon icon="lucide:image-play"></iconify-icon>GIF</button>
    </div>
    {#if message}<div class:error={status === 'error'} class:done={status === 'done'} class:loading={status === 'loading'} class="status-bar"><span>{message}</span></div>{/if}
  </section>

  {#if activeMode === 'video' && !videoUrl}
    <section class="empty-workbench">
      <div class="empty-copy">
        <strong>视频素材导出</strong>
        <span>定位时间点，裁切画面，调整色彩，导出单帧或批量素材。</span>
      </div>
      <label class="file-drop hero-drop" ondragover={(event) => event.preventDefault()} ondrop={onDrop}>
        <input type="file" accept="video/*,.mp4,.webm,.mov,.m4v,.ogv" onchange={chooseFile}>
        <strong><iconify-icon icon="lucide:upload-cloud"></iconify-icon>选择视频</strong>
        <span>MP4 / WebM / MOV / M4V / OGV</span>
      </label>
    </section>
  {:else if activeMode === 'video'}
    <section class="workspace-shell">
      <div class="workspace-grid">
        <section class="visual-area">
          <div class="video-shell" style={videoAspectStyle}>
            <!-- svelte-ignore a11y_media_has_caption -->
            <video bind:this={videoEl} src={videoUrl} controls preload="metadata" muted playsinline onloadedmetadata={onVideoLoaded} onseeked={onVideoSeeked}></video>
            <div class="crop-shade" aria-hidden="true">
              <span class="crop-box" style={cropOverlayStyle}></span>
            </div>
          </div>

          <div class="timeline-card">
            <div class="timeline-meta">
              <span>{formatTime(position)}</span>
              <span>{formatTime(duration)}</span>
            </div>
            <div
              class="custom-range-container"
              style={`--progress: ${progressPercent}%; --hover-x: ${seekerHoverPercent}%;`}
            >
              {#if seekerHovering}
                <span class="hover-time">{formatTime(seekerHoverTime)}</span>
              {/if}
              <span class="range-window" style={`left: ${rangeStartPercent}%; width: ${Math.max(0, rangeEndPercent - rangeStartPercent)}%;`}></span>
              {#if batchMarkers.length}
                <div class="marker-track" aria-hidden="true">
                  {#each batchMarkers as marker}
                    <span class="batch-dot" style={`left: ${marker}%;`}></span>
                  {/each}
                  {#each savedMarkers as marker}
                    <span class="saved-dot" style={`left: ${marker}%;`}></span>
                  {/each}
                </div>
              {:else if savedMarkers.length}
                <div class="marker-track" aria-hidden="true">
                  {#each savedMarkers as marker}
                    <span class="saved-dot" style={`left: ${marker}%;`}></span>
                  {/each}
                </div>
              {/if}
              <input type="range" min="0" max={Math.max(duration, 1)} step="0.04" value={position} onpointermove={updateSeekerHover} onpointerleave={() => seekerHovering = false} oninput={(event) => seekFrame(Number((event.currentTarget as HTMLInputElement).value))}>
            </div>
            <div class="frame-stepper">
              <button type="button" onclick={() => stepFrame(-0.04)} disabled={batchBusy}><iconify-icon icon="lucide:skip-back"></iconify-icon>-1 帧</button>
              <button type="button" onclick={() => stepFrame(0.04)} disabled={batchBusy}>+1 帧<iconify-icon icon="lucide:skip-forward"></iconify-icon></button>
            </div>
            <div class="marker-actions">
              <button type="button" onclick={setRangeStart} disabled={batchBusy}><iconify-icon icon="lucide:flag"></iconify-icon>设为起点</button>
              <button type="button" onclick={setRangeEnd} disabled={batchBusy}><iconify-icon icon="lucide:square"></iconify-icon>设为终点</button>
              <button type="button" onclick={resetRange} disabled={batchBusy}><iconify-icon icon="lucide:maximize-2"></iconify-icon>全段</button>
              <button type="button" onclick={addMarker} disabled={batchBusy}><iconify-icon icon="lucide:bookmark-plus"></iconify-icon>添加标记</button>
              <button type="button" onclick={exportMarkerZip} disabled={status === 'loading' || batchBusy || !timelineMarkers.length}><iconify-icon icon="lucide:archive"></iconify-icon>导出标记帧</button>
              <button type="button" onclick={clearMarkers} disabled={!timelineMarkers.length || batchBusy}><iconify-icon icon="lucide:trash-2"></iconify-icon>清空标记</button>
            </div>
            <div class="timeline-meta">
              <span>区间 {formatTime(rangeStart)}</span>
              <span>{formatTime(rangeEnd || duration)}</span>
            </div>
            <div class="range-inputs">
              <label>
                <span>起点秒</span>
                <input type="number" min="0" max={Math.max(duration, 0)} step="0.01" value={Number(rangeStart.toFixed(2))} oninput={updateRangeStart} disabled={batchBusy}>
              </label>
              <label>
                <span>终点秒</span>
                <input type="number" min="0" max={Math.max(duration, 0)} step="0.01" value={Number((rangeEnd || duration).toFixed(2))} oninput={updateRangeEnd} disabled={batchBusy}>
              </label>
            </div>
            {#if timelineMarkers.length}
              <details class="marker-drawer" bind:open={markersOpen}>
                <summary>标记点 · {timelineMarkers.length}</summary>
                <div class="marker-list">
                  {#each timelineMarkers as marker}
                    <div class="marker-item">
                      <button type="button" onclick={() => seekFrame(marker, 0)}>{formatTime(marker)}</button>
                      <button type="button" class="remove" aria-label={`移除 ${formatTime(marker)}`} onclick={() => removeMarker(marker)}>×</button>
                    </div>
                  {/each}
                </div>
              </details>
            {/if}
          </div>

          {#if previewHistory.length}
            <section class="history-card">
              <div class="section-head"><strong><iconify-icon icon="lucide:history"></iconify-icon>预览</strong></div>
              <div class="history-grid">
                {#each previewHistory as item, index}
                  <div class="history-item" class:active={item.url === previewUrl}>
                    <button class="history-thumb" type="button" onclick={() => previewUrl = item.url}>
                      <img src={item.url} alt="预览历史">
                    </button>
                    <button class="history-remove" type="button" onclick={() => removeHistoryItem(index)} aria-label="移除历史">×</button>
                  </div>
                {/each}
              </div>
            </section>
          {/if}
        </section>

        <aside class="toolbox-area">
          <section class="inspector-section">
            <div class="shot-preview-mini">
              <div class="shot-preview-stage">
                {#if previewUrl}
                  <img src={previewUrl} alt="截图预览">
                {:else}
                  <div class="shot-preview-empty"><iconify-icon icon="lucide:image"></iconify-icon><span>预览区</span></div>
                {/if}
              </div>
              <div class="preview-info">{outputInfo}</div>
              <div class="actions preview-actions compact">
                <button type="button" onclick={updatePreview} disabled={batchBusy} title="刷新预览"><iconify-icon icon="lucide:refresh-cw"></iconify-icon></button>
                <button type="button" onclick={copyFrame} disabled={status === 'loading' || batchBusy} title="复制图片"><iconify-icon icon="lucide:copy"></iconify-icon></button>
                <button class="primary" type="button" onclick={exportFrame} disabled={status === 'loading' || batchBusy}><iconify-icon icon="lucide:download"></iconify-icon>导出</button>
              </div>
            </div>

            <label class="file-drop compact-drop" ondragover={(event) => event.preventDefault()} ondrop={onDrop}>
              <input type="file" accept="video/*,.mp4,.webm,.mov,.m4v,.ogv" onchange={chooseFile}>
              <strong><iconify-icon icon="lucide:file-video"></iconify-icon>{file?.name || '选择视频'}</strong>
              <span>更换</span>
            </label>

            <div class="file-info-card" class:open={fileInfoOpen}>
              <button type="button" class="file-info-trigger" onclick={() => fileInfoOpen = !fileInfoOpen}>
                <iconify-icon icon="lucide:info"></iconify-icon>
                <span>信息</span>
                <iconify-icon icon="lucide:chevron-down"></iconify-icon>
              </button>
              {#if fileInfoOpen}
                <div class="file-info-body">
                  {#if file}
                    <span><iconify-icon icon="lucide:hard-drive"></iconify-icon>{formatFileSize(file.size)}</span>
                    <span><iconify-icon icon="lucide:file-type"></iconify-icon>{file.type || '—'}</span>
                    <span><iconify-icon icon="lucide:clock-3"></iconify-icon>{duration > 0 ? formatTime(duration) : '—'}</span>
                  {:else}
                    <span>—</span>
                  {/if}
                </div>
              {/if}
            </div>

            <div class:single={activeTool === 'single'} class:batch={activeTool === 'batch'} class:gif={activeTool === 'gif'} class="tabs-header" role="tablist" aria-label="工具">
              <button type="button" class:active={activeTool === 'single'} onclick={() => activeTool = 'single'}><iconify-icon icon="lucide:camera"></iconify-icon>截图</button>
              <button type="button" class:active={activeTool === 'batch'} onclick={() => activeTool = 'batch'}><iconify-icon icon="lucide:grid-3x3"></iconify-icon>批量</button>
              <button type="button" class:active={activeTool === 'gif'} onclick={() => activeTool = 'gif'}><iconify-icon icon="lucide:sparkles"></iconify-icon>动态</button>
            </div>

            <section class="global-settings">
              <div class="settings-stack">
                <details class="settings-group">
                  <summary class="settings-group-title"><iconify-icon icon="lucide:file-output"></iconify-icon><span>文件与尺寸</span></summary>
                  <div class="options-grid compact">
                    <label>
                      <span>导出格式</span>
                      <CustomSelect value={exportFormat} options={formatSelectOptions} open={openSelect === 'format'} onSelect={selectExportFormat} onToggle={() => toggleSelect('format')} />
                    </label>
                    {#if outputSize === 'custom'}
                      <label>
                        <span>输出宽度</span>
                        <input type="number" min="64" max="4096" step="1" bind:value={customOutputWidth} onblur={updateCustomOutput} oninput={updateCustomOutput}>
                      </label>
                      <label>
                        <span>输出高度</span>
                        <input type="number" min="64" max="4096" step="1" bind:value={customOutputHeight} onblur={updateCustomOutput} oninput={updateCustomOutput}>
                      </label>
                    {/if}
                    <label class="wide-field">
                      <span>输出尺寸</span>
                      <CustomSelect value={outputSize} options={outputSelectOptions} open={openSelect === 'output'} onSelect={selectOutputSize} onToggle={() => toggleSelect('output')} />
                    </label>
                  </div>
                </details>

                <details class="settings-group">
                  <summary class="settings-group-title"><iconify-icon icon="lucide:crop"></iconify-icon><span>裁切</span></summary>
                  <div class="options-grid compact">
                    {#if cropRatio === 'custom'}
                      <label>
                        <span>比例宽</span>
                        <input type="number" min="1" max="99" step="1" bind:value={customRatioWidth} onblur={updateCustomRatio} oninput={updateCustomRatio}>
                      </label>
                      <label>
                        <span>比例高</span>
                        <input type="number" min="1" max="99" step="1" bind:value={customRatioHeight} onblur={updateCustomRatio} oninput={updateCustomRatio}>
                      </label>
                    {/if}
                    <label class="wide-field">
                      <span>封面比例</span>
                      <CustomSelect value={cropRatio} options={ratioSelectOptions} open={openSelect === 'ratio'} onSelect={selectCropRatio} onToggle={() => toggleSelect('ratio')} />
                    </label>
                    <div class="wide-field crop-anchor-field">
                      <span>裁切位置</span>
                      <div class="anchor-grid" aria-label="裁切位置">
                        {#each cropAnchors as anchor}
                          <button type="button" class:active={cropAnchor === anchor.value} onclick={() => setCropAnchor(anchor.value)}>{anchor.label}</button>
                        {/each}
                      </div>
                    </div>
                  </div>
                </details>

                <details class="settings-group">
                  <summary class="settings-group-title"><iconify-icon icon="lucide:sliders-horizontal"></iconify-icon><span>画面</span></summary>
                  <div class="settings-sliders">
                    <label class="slider-row">
                      <span>亮度</span>
                      <input type="range" min="0" max="200" step="1" value={brightness} oninput={(event) => { brightness = Number((event.currentTarget as HTMLInputElement).value); updateVisualAdjustments(); }}>
                      <input type="number" class="slider-value" step="1" bind:value={brightness} oninput={updateVisualAdjustments}>
                    </label>
                    <label class="slider-row">
                      <span>对比度</span>
                      <input type="range" min="0" max="200" step="1" value={contrast} oninput={(event) => { contrast = Number((event.currentTarget as HTMLInputElement).value); updateVisualAdjustments(); }}>
                      <input type="number" class="slider-value" step="1" bind:value={contrast} oninput={updateVisualAdjustments}>
                    </label>
                    <label class="slider-row">
                      <span>饱和度</span>
                      <input type="range" min="0" max="300" step="1" value={saturation} oninput={(event) => { saturation = Number((event.currentTarget as HTMLInputElement).value); updateVisualAdjustments(); }}>
                      <input type="number" class="slider-value" step="1" bind:value={saturation} oninput={updateVisualAdjustments}>
                    </label>
                    <label class="slider-row">
                      <span>灰度</span>
                      <input type="range" min="0" max="100" step="1" value={grayscale} oninput={(event) => { grayscale = Number((event.currentTarget as HTMLInputElement).value); updateVisualAdjustments(); }}>
                      <input type="number" class="slider-value" step="1" bind:value={grayscale} oninput={updateVisualAdjustments}>
                    </label>
                    <label class="slider-row">
                      <span>色相</span>
                      <input type="range" min="0" max="360" step="1" value={hueRotate} oninput={(event) => { hueRotate = Number((event.currentTarget as HTMLInputElement).value); updateVisualAdjustments(); }}>
                      <input type="number" class="slider-value" step="1" bind:value={hueRotate} oninput={updateVisualAdjustments}>
                    </label>
                    <label class="slider-row">
                      <span>模糊</span>
                      <input type="range" min="0" max="20" step="0.5" value={blur} oninput={(event) => { blur = Number((event.currentTarget as HTMLInputElement).value); updateVisualAdjustments(); }}>
                      <input type="number" class="slider-value" step="0.5" bind:value={blur} oninput={updateVisualAdjustments}>
                    </label>
                  </div>
                </details>

                <details class="settings-group">
                  <summary class="settings-group-title"><iconify-icon icon="lucide:type"></iconify-icon><span>文字</span></summary>
                  <div class="options-grid compact">
                    <label class="wide-field">
                      <span>叠加文字</span>
                      <textarea maxlength="90" rows="2" placeholder="留空则不叠加" bind:value={overlayText}></textarea>
                    </label>
                    <label>
                      <span>文字大小</span>
                      <input type="number" min="18" max="120" step="2" bind:value={overlaySize}>
                    </label>
                    <label class="check-field checkbox-container">
                      <input type="checkbox" bind:checked={overlayBand}>
                      <span class="checkmark"></span>
                      <span>文字背景条</span>
                    </label>
                  </div>
                </details>
              </div>
            </section>

            <div class="tab-content-inspector">
              {#if activeTool === 'single'}
                <div class="inspector-note"><iconify-icon icon="lucide:info"></iconify-icon><span>单帧截图</span></div>
              {:else if activeTool === 'batch'}
                <div class="inspector-group">
                  <div class="settings-group-title"><iconify-icon icon="lucide:grid-3x3"></iconify-icon><span>批量选项</span></div>
                  <div class="options-grid compact">
                    <label>
                      <span>抽帧模式</span>
                      <CustomSelect value={batchMode} options={[{ code: 'count', name: '均匀数量' }, { code: 'interval', name: '固定间隔' }]} open={openSelect === 'batchMode'} onSelect={selectBatchMode} onToggle={() => toggleSelect('batchMode')} />
                    </label>
                    {#if batchMode === 'count'}
                      <label>
                        <span>抽帧数量</span>
                        <input type="number" min="1" max="60" bind:value={batchCount}>
                      </label>
                    {:else}
                      <label>
                        <span>间隔秒数</span>
                        <input type="number" min="0.25" max="600" step="0.25" bind:value={batchInterval}>
                      </label>
                    {/if}
                    <label>
                      <span>预计帧数</span>
                      <input type="text" value={`${sampleTimes.length} 张`} readonly>
                    </label>
                    <label>
                      <span>联系表列数</span>
                      <input type="number" min="1" max="12" bind:value={sheetColumns}>
                    </label>
                  </div>
                  <div class="actions-stack">
                    <button type="button" onclick={exportContactSheet} disabled={status === 'loading' || batchBusy}><iconify-icon icon="lucide:layout-grid"></iconify-icon>导出联系表 ({sampleTimes.length})</button>
                    <button class="primary" type="button" onclick={exportBatchZip} disabled={status === 'loading' || batchBusy}><iconify-icon icon="lucide:package-down"></iconify-icon>导出 ZIP 包 ({sampleTimes.length})</button>
                  </div>
                </div>
              {:else if activeTool === 'gif'}
                <div class="inspector-group">
                  <div class="settings-group-title"><iconify-icon icon="lucide:sparkles"></iconify-icon><span>动态选项</span></div>
                  <div class="options-grid compact">
                    <label>
                      <span>时长(s)</span>
                      <input type="number" min="0.5" max="6" step="0.5" bind:value={gifSeconds}>
                    </label>
                    <label>
                      <span>帧率(FPS)</span>
                      <input type="number" min="2" max="15" bind:value={gifFps}>
                    </label>
                    <label>
                      <span>宽度(px)</span>
                      <input type="number" min="96" max="1280" step="16" bind:value={gifWidth}>
                    </label>
                    <label>
                      <span>短视频格式</span>
                      <CustomSelect value={motionVideoFormat} options={motionVideoSelectOptions} open={openSelect === 'motionFormat'} onSelect={selectMotionVideoFormat} onToggle={() => toggleSelect('motionFormat')} />
                    </label>
                    <label class="wide-field">
                      <span>播放方向</span>
                      <CustomSelect value={motionDirection} options={[{ code: 'forward', name: '正放' }, { code: 'reverse', name: '倒放' }, { code: 'pingpong', name: '乒乓' }]} open={openSelect === 'motionDirection'} onSelect={selectMotionDirection} onToggle={() => toggleSelect('motionDirection')} />
                    </label>
                  </div>
                  <div class="actions-stack">
                    <button type="button" onclick={exportWebm} disabled={status === 'loading' || batchBusy || !supportedMotionVideoOptions.length}><iconify-icon icon="lucide:file-video"></iconify-icon>导出短视频</button>
                    <button class="primary" type="button" onclick={exportGif} disabled={status === 'loading' || batchBusy}><iconify-icon icon="lucide:image-play"></iconify-icon>导出 GIF</button>
                  </div>
                </div>
              {/if}
            </div>
          </section>
        </aside>
      </div>
    </section>
  {:else}
    <section class="workspace-shell gif-workspace">
      <div class="gif-workspace-grid">
        <section class="gif-visual-area">
          <label class="file-drop hero-drop">
            <input type="file" accept="image/gif,.gif" onchange={chooseSplitGif}>
            <strong><iconify-icon icon="lucide:image-play"></iconify-icon>{splitGifName || '选择 GIF 文件'}</strong>
            <span>帧序列、单帧、帧包、联系表</span>
          </label>
          {#if splitFrames.length}
            <div class="split-preview-grid large">
              {#each splitFrames.slice(0, 24) as frame, index}
                <button type="button" onclick={() => downloadBlob(frame.blob, `gif_frame_${String(index + 1).padStart(3, '0')}.png`)}>
                  <img src={frame.url} alt={`GIF 第 ${index + 1} 帧`}>
                  <span>{formatTime(frame.time / 1000)}</span>
                </button>
              {/each}
            </div>
          {/if}
        </section>
        <aside class="toolbox-area">
          <div class="section-head"><strong><iconify-icon icon="lucide:layers-3"></iconify-icon>GIF 拆解</strong><span>解析 GIF 并导出。</span></div>
          {#if splitFrames.length}
            <div class="file-meta">
              <span><iconify-icon icon="lucide:scan"></iconify-icon>{splitGifWidth} x {splitGifHeight}</span>
              <span><iconify-icon icon="lucide:images"></iconify-icon>{splitFrames.length} 帧</span>
              <span><iconify-icon icon="lucide:clock-3"></iconify-icon>{formatTime(splitFrames.reduce((sum, frame) => sum + frame.delay, 0) / 1000)}</span>
            </div>
            <label>
              <span>联系表列数</span>
              <input type="number" min="1" max="12" bind:value={sheetColumns}>
            </label>
            <div class="actions sticky-footer-actions">
              <button type="button" onclick={exportSplitSheet}><iconify-icon icon="lucide:layout-grid"></iconify-icon>导出联系表</button>
              <button class="primary" type="button" onclick={exportSplitZip}><iconify-icon icon="lucide:package-down"></iconify-icon>导出 PNG 帧包</button>
            </div>
          {:else}
            <div class="gif-empty-note"><iconify-icon icon="lucide:info"></iconify-icon>等待 GIF</div>
          {/if}
        </aside>
      </div>
    </section>
  {/if}

  <canvas bind:this={canvasEl} class="hidden-canvas"></canvas>
</main>
