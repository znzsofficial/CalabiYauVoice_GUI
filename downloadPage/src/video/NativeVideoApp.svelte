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
  let markersOpen = $state(false);
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

  const nativeIdeas = [
    '按固定间隔生成 PNG/JPEG/WebP 帧图，适合整理镜头素材',
    '加入音频波形预览，用节奏辅助定位截图和动图片段',
    '扩展素材信息面板，集中查看尺寸、时长、类型和文件大小',
    '制作封面套版，把标题、水印和基础图形一起写入画面',
    '保存常用导出预设，快速复用比例、尺寸、帧率和 GIF 参数'
  ];

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
    const settings: SavedSettings = { exportFormat, cropRatio, outputSize, cropAnchor, customRatioWidth, customRatioHeight, customOutputWidth, customOutputHeight, batchMode, batchCount, batchInterval, sheetColumns, gifSeconds, gifFps, gifWidth, gifColors, gifRepeat, webmBitrate, motionVideoFormat, motionDirection, brightness, contrast, saturation, overlayText, overlaySize, overlayBand };
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
    message = next ? '正在整理素材信息' : '';
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
    message = '正在拆解 GIF';
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
      message = `已拆解 ${splitFrames.length} 帧`;
    } catch {
      status = 'error';
      message = 'GIF 拆解没有完成，可以换一个文件或稍后再试';
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
    message = duration > 0 ? '' : '素材信息准备中';
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
    message = `已添加标记 ${formatTime(next)}`;
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
    if (!videoEl?.videoWidth || !videoEl.videoHeight) return `${formatOptions.find(option => option.value === exportFormat)?.label || 'PNG'} / 等待素材`;
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
    context.filter = `brightness(${brightness}%) contrast(${contrast}%) saturate(${saturation}%)`;
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
    revoke(previewUrl);
    previewUrl = URL.createObjectURL(blob);
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

  function getSampleTimes(): number[] {
    const start = Math.max(0, Math.min(rangeStart, duration || 0));
    const end = Math.max(start, Math.min(rangeEnd || duration || start, duration || start));
    if (batchMode === 'interval') {
      const interval = Math.max(0.25, Number(batchInterval) || 5);
      const safeEnd = Math.max(start, end - 0.08);
      const count = Math.max(1, Math.min(Math.floor((safeEnd - start) / interval) + 1, 60));
      return Array.from({ length: count }, (_, index) => Math.min(safeEnd, start + index * interval));
    }
    const safeCount = Math.max(1, Math.min(Math.round(batchCount), 60));
    if (!duration || safeCount === 1) return [Math.min(position, duration || position)];
    const safeEnd = Math.max(start, end - 0.08);
    const step = Math.max(0, safeEnd - start) / safeCount;
    return Array.from({ length: safeCount }, (_, index) => Math.min(safeEnd, start + step * index + step / 2));
  }

  function seekTo(time: number): Promise<void> {
    if (!videoEl) return Promise.resolve();
    if (seekTimer) clearTimeout(seekTimer);
    position = Math.max(0, Math.min(time, duration || time));
    return new Promise((resolve, reject) => {
      if (!videoEl) {
        resolve();
        return;
      }
      const timeout = window.setTimeout(() => {
        cleanup();
        reject(new Error('seek timeout'));
      }, 5000);
      const cleanup = (): void => {
        window.clearTimeout(timeout);
        videoEl?.removeEventListener('seeked', onSeeked);
        videoEl?.removeEventListener('error', onError);
      };
      const onSeeked = (): void => {
        cleanup();
        resolve();
      };
      const onError = (): void => {
        cleanup();
        reject(new Error('seek failed'));
      };
      videoEl.addEventListener('seeked', onSeeked, { once: true });
      videoEl.addEventListener('error', onError, { once: true });
      videoEl.currentTime = position;
    });
  }

  async function exportFrame(): Promise<void> {
    if (!file) return;
    const blob = await captureFrame();
    if (!blob) {
      status = 'error';
      message = '当前画面正在准备';
      return;
    }
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    const extension = getExtension();
    const ratioSuffix = cropRatio === 'source' ? 'source' : cropRatio.replace(':', 'x');
    const name = `${file.name.replace(/\.[^.]+$/, '') || 'video'}_${Math.round(position * 1000)}ms_${ratioSuffix}.${extension}`;
    link.href = url;
    link.download = name;
    link.click();
    URL.revokeObjectURL(url);
    status = 'done';
    message = `已导出 ${name}`;
  }

  async function captureBatch(): Promise<{ blob: Blob; time: number }[]> {
    if (!file || !videoEl) return [];
    const times = sampleTimes;
    const frames: { blob: Blob; time: number }[] = [];
    batchBusy = true;
    status = 'loading';
    try {
      for (const [index, time] of times.entries()) {
        message = `正在抽帧 ${index + 1}/${times.length}`;
        await seekTo(time);
        const blob = await captureFrame();
        if (!blob) throw new Error('capture failed');
        frames.push({ blob, time });
      }
      return frames;
    } finally {
      batchBusy = false;
      void updatePreview();
    }
  }

  async function exportBatchZip(): Promise<void> {
    if (!file) return;
    try {
      const frames = await captureBatch();
      if (!frames.length) throw new Error('empty batch');
      const zip = new JSZip();
      const extension = getExtension();
      frames.forEach(({ blob, time }, index) => {
        zip.file(getFrameName(index, time, extension), blob);
      });
      message = '正在生成 ZIP';
      const zipBlob = await zip.generateAsync({ type: 'blob' });
      const name = `${file.name.replace(/\.[^.]+$/, '') || 'video'}_${frames.length}_frames.zip`;
      downloadBlob(zipBlob, name);
      status = 'done';
      message = `已导出 ${name}`;
    } catch {
      status = 'error';
      message = '批量抽帧没有完成，可以调整数量后再次生成';
      batchBusy = false;
    }
  }

  async function exportMarkerZip(): Promise<void> {
    if (!file || !timelineMarkers.length) return;
    batchBusy = true;
    status = 'loading';
    try {
      const zip = new JSZip();
      const extension = getExtension();
      for (const [index, time] of timelineMarkers.entries()) {
        message = `正在导出标记 ${index + 1}/${timelineMarkers.length}`;
        await seekTo(time);
        const blob = await captureFrame();
        if (!blob) throw new Error('marker capture failed');
        zip.file(getFrameName(index, time, extension), blob);
      }
      message = '正在生成 ZIP';
      const zipBlob = await zip.generateAsync({ type: 'blob' });
      const name = `${file.name.replace(/\.[^.]+$/, '') || 'video'}_${timelineMarkers.length}_markers.zip`;
      downloadBlob(zipBlob, name);
      status = 'done';
      message = `已导出 ${name}`;
    } catch {
      status = 'error';
      message = '标记帧导出没有完成，可以重新开始一次';
    } finally {
      batchBusy = false;
      void updatePreview();
    }
  }

  async function exportSplitZip(): Promise<void> {
    if (!splitFrames.length) return;
    status = 'loading';
    try {
      const zip = new JSZip();
      splitFrames.forEach((frame, index) => {
        zip.file(`gif_frame_${String(index + 1).padStart(3, '0')}_${Math.round(frame.time)}ms.png`, frame.blob);
      });
      message = '正在生成 GIF 帧包';
      const zipBlob = await zip.generateAsync({ type: 'blob' });
      const name = `${splitGifName.replace(/\.[^.]+$/, '') || 'gif'}_${splitFrames.length}_frames.zip`;
      downloadBlob(zipBlob, name);
      status = 'done';
      message = `已导出 ${name}`;
    } catch {
      status = 'error';
      message = 'GIF 帧包没有生成完成，可以重新开始一次';
    }
  }

  async function exportSplitSheet(): Promise<void> {
    if (!splitFrames.length || !canvasEl) return;
    const bitmaps: ImageBitmap[] = [];
    status = 'loading';
    try {
      bitmaps.push(...await Promise.all(splitFrames.map(frame => createImageBitmap(frame.blob))));
      const columns = Math.max(1, Math.min(Math.round(sheetColumns), bitmaps.length));
      const rows = Math.ceil(bitmaps.length / columns);
      const cellWidth = bitmaps[0].width;
      const cellHeight = bitmaps[0].height;
      const sheetCanvas = document.createElement('canvas');
      sheetCanvas.width = cellWidth * columns;
      sheetCanvas.height = cellHeight * rows;
      const context = sheetCanvas.getContext('2d');
      if (!context) throw new Error('no canvas context');
      context.fillStyle = '#09090b';
      context.fillRect(0, 0, sheetCanvas.width, sheetCanvas.height);
      context.font = `${Math.max(12, Math.round(cellWidth * 0.035))}px ui-monospace, SFMono-Regular, Consolas, monospace`;
      context.textBaseline = 'bottom';
      bitmaps.forEach((bitmap, index) => {
        const x = (index % columns) * cellWidth;
        const y = Math.floor(index / columns) * cellHeight;
        context.drawImage(bitmap, x, y, cellWidth, cellHeight);
        const code = formatTime(splitFrames[index].time / 1000);
        const padding = Math.max(8, Math.round(cellWidth * 0.018));
        const textWidth = context.measureText(code).width;
        context.fillStyle = 'rgb(0 0 0 / 0.68)';
        context.fillRect(x + padding, y + cellHeight - padding - 24, textWidth + 14, 24);
        context.fillStyle = '#fff';
        context.fillText(code, x + padding + 7, y + cellHeight - padding - 6);
      });
      const blob = await blobFromCanvas(sheetCanvas, 'image/png');
      if (!blob) throw new Error('sheet export failed');
      const name = `${splitGifName.replace(/\.[^.]+$/, '') || 'gif'}_contact_sheet.png`;
      downloadBlob(blob, name);
      status = 'done';
      message = `已导出 ${name}`;
    } catch {
      status = 'error';
      message = 'GIF 联系表没有生成完成，可以重新开始一次';
    } finally {
      bitmaps.forEach(bitmap => bitmap.close());
    }
  }

  async function exportContactSheet(): Promise<void> {
    if (!file || !canvasEl) return;
    try {
      const frames = await captureBatch();
      if (!frames.length) throw new Error('empty sheet');
      const bitmaps = await Promise.all(frames.map(frame => createImageBitmap(frame.blob)));
      const columns = Math.max(1, Math.min(Math.round(sheetColumns), bitmaps.length));
      const rows = Math.ceil(bitmaps.length / columns);
      const cellWidth = bitmaps[0].width;
      const cellHeight = bitmaps[0].height;
      canvasEl.width = cellWidth * columns;
      canvasEl.height = cellHeight * rows;
      const context = canvasEl.getContext('2d');
      if (!context) throw new Error('no canvas context');
      context.fillStyle = '#09090b';
      context.fillRect(0, 0, canvasEl.width, canvasEl.height);
      context.font = `${Math.max(12, Math.round(cellWidth * 0.035))}px ui-monospace, SFMono-Regular, Consolas, monospace`;
      context.textBaseline = 'bottom';
      bitmaps.forEach((bitmap, index) => {
        const x = (index % columns) * cellWidth;
        const y = Math.floor(index / columns) * cellHeight;
        context.drawImage(bitmap, x, y, cellWidth, cellHeight);
        const code = formatTime(frames[index].time);
        const padding = Math.max(8, Math.round(cellWidth * 0.018));
        const textWidth = context.measureText(code).width;
        context.fillStyle = 'rgb(0 0 0 / 0.68)';
        context.fillRect(x + padding, y + cellHeight - padding - 24, textWidth + 14, 24);
        context.fillStyle = '#fff';
        context.fillText(code, x + padding + 7, y + cellHeight - padding - 6);
        bitmap.close();
      });
      const blob = await blobFromCanvas(canvasEl, exportFormat);
      if (!blob) throw new Error('sheet export failed');
      const name = `${file.name.replace(/\.[^.]+$/, '') || 'video'}_contact_sheet.${getExtension()}`;
      downloadBlob(blob, name);
      status = 'done';
      message = `已导出 ${name}`;
    } catch {
      status = 'error';
      message = '联系表没有生成完成，可以重新开始一次';
      batchBusy = false;
    }
  }

  async function exportGif(): Promise<void> {
    if (!file || !videoEl || !canvasEl) return;
    const fps = Math.max(2, Math.min(Math.round(Number(gifFps) || 8), 15));
    const width = Math.max(96, Math.min(Math.round(Number(gifWidth) || 480), 960));
    const colors = Math.max(16, Math.min(Math.round(Number(gifColors) || 128), 256));
    const repeat = gifRepeat === 'once' ? -1 : gifRepeat === 'twice' ? 1 : 0;
    const requestedSeconds = Math.max(0.5, Math.min(Number(gifSeconds) || 2, 6));
    const frameCount = Math.max(1, Math.min(Math.round(requestedSeconds * fps), 90));
    const { times, start, seconds } = getMotionTimes(frameCount, fps, requestedSeconds);
    const gif = GIFEncoder();
    batchBusy = true;
    status = 'loading';
    try {
      for (const [index, time] of times.entries()) {
        message = `正在编码 GIF ${index + 1}/${times.length}`;
        await seekTo(time);
        const context = drawCurrentFrame(width);
        if (!context) throw new Error('gif frame failed');
        const { data } = context.getImageData(0, 0, canvasEl.width, canvasEl.height);
        const format = 'rgb444';
        const palette = quantize(data, colors, { format });
        const indexedFrame = applyPalette(data, palette, format);
        gif.writeFrame(indexedFrame, canvasEl.width, canvasEl.height, {
          palette,
          delay: Math.round(1000 / fps),
          repeat
        });
        await new Promise(resolve => window.setTimeout(resolve, 0));
      }
      gif.finish();
      const blob = new Blob([gif.bytes()], { type: 'image/gif' });
      revoke(gifPreviewUrl);
      gifPreviewUrl = URL.createObjectURL(blob);
      const name = `${file.name.replace(/\.[^.]+$/, '') || 'video'}_${Math.round(start * 1000)}ms_${seconds.toFixed(1)}s_${fps}fps.gif`;
      downloadBlob(blob, name);
      status = 'done';
      message = `已导出 ${name}`;
    } catch {
      status = 'error';
      message = 'GIF 没有生成完成，可以调整参数后再次生成';
    } finally {
      batchBusy = false;
      void updatePreview();
    }
  }

  function resolveMotionVideoFormat(): { label: string; mime: string; extension: string } | null {
    if (typeof MediaRecorder === 'undefined') return null;
    const selected = motionVideoFormat === 'auto' ? null : motionVideoOptions.find(option => option.value === motionVideoFormat);
    if (selected && MediaRecorder.isTypeSupported(selected.mime)) return selected;
    return motionVideoOptions.find(option => MediaRecorder.isTypeSupported(option.mime)) || null;
  }

  function getMotionTimes(frameCount: number, fps: number, maxSeconds: number): { times: number[]; start: number; seconds: number } {
    const start = Math.max(0, Math.min(rangeStart, duration || 0));
    const selectedEnd = Math.max(start, Math.min(rangeEnd || duration || start, duration || start));
    const clipEnd = Math.min(selectedEnd, start + maxSeconds);
    const seconds = Math.max(0.5, Math.min((clipEnd - start) || maxSeconds, maxSeconds));
    const safeEnd = Math.max(start, Math.min(clipEnd, (duration || clipEnd) - 0.08));
    const forward = Array.from({ length: frameCount }, (_, index) => Math.min(safeEnd, start + index / fps));
    if (motionDirection === 'reverse') return { times: forward.reverse(), start, seconds };
    if (motionDirection === 'pingpong' && forward.length > 2) return { times: [...forward, ...forward.slice(1, -1).reverse()], start, seconds };
    return { times: forward, start, seconds };
  }

  async function exportWebm(): Promise<void> {
    if (!file || !videoEl || !canvasEl || !canvasEl.captureStream || typeof MediaRecorder === 'undefined') {
      status = 'error';
      message = '短视频生成器正在准备';
      return;
    }
    const format = resolveMotionVideoFormat();
    if (!format) {
      status = 'error';
      message = '短视频格式正在准备';
      return;
    }
    const fps = Math.max(2, Math.min(Math.round(Number(gifFps) || 8), 30));
    const width = Math.max(96, Math.min(Math.round(Number(gifWidth) || 480), 1280));
    const bitrate = Math.max(250, Math.min(Math.round(Number(webmBitrate) || 2500), 12000)) * 1000;
    const requestedSeconds = Math.max(0.5, Math.min(Number(gifSeconds) || 2, 10));
    const frameCount = Math.max(1, Math.min(Math.round(requestedSeconds * fps), 300));
    const { times, start, seconds } = getMotionTimes(frameCount, fps, requestedSeconds);
    const bitmaps: ImageBitmap[] = [];
    batchBusy = true;
    status = 'loading';
    try {
      for (const [index, time] of times.entries()) {
        message = `正在准备短视频 ${index + 1}/${times.length}`;
        await seekTo(time);
        if (!drawCurrentFrame(width)) throw new Error('webm frame failed');
        bitmaps.push(await createImageBitmap(canvasEl));
      }
      canvasEl.width = bitmaps[0].width;
      canvasEl.height = bitmaps[0].height;
      const context = canvasEl.getContext('2d');
      if (!context) throw new Error('no canvas context');
      const stream = canvasEl.captureStream(0);
      const [track] = stream.getVideoTracks() as CanvasCaptureMediaStreamTrack[];
      if (!track?.requestFrame) throw new Error('requestFrame unavailable');
      const recorder = new MediaRecorder(stream, { mimeType: format.mime, videoBitsPerSecond: bitrate });
      const chunks: BlobPart[] = [];
      recorder.ondataavailable = event => {
        if (event.data.size > 0) chunks.push(event.data);
      };
      const done = new Promise<void>((resolve, reject) => {
        recorder.onstop = () => resolve();
        recorder.onerror = () => reject(new Error('webm recorder failed'));
      });
      recorder.start();
      for (const [index, bitmap] of bitmaps.entries()) {
        message = `正在编码短视频 ${index + 1}/${bitmaps.length}`;
        context.clearRect(0, 0, canvasEl.width, canvasEl.height);
        context.drawImage(bitmap, 0, 0);
        track.requestFrame();
        await new Promise(resolve => window.setTimeout(resolve, Math.round(1000 / fps)));
      }
      recorder.stop();
      await done;
      const blob = new Blob(chunks, { type: format.mime });
      revoke(webmPreviewUrl);
      webmPreviewUrl = URL.createObjectURL(blob);
      const name = `${file.name.replace(/\.[^.]+$/, '') || 'video'}_${Math.round(start * 1000)}ms_${seconds.toFixed(1)}s_${fps}fps.${format.extension}`;
      downloadBlob(blob, name);
      status = 'done';
      message = `已导出 ${name}`;
    } catch {
      status = 'error';
      message = '短视频没有生成完成，可以调整参数后再次生成';
    } finally {
      bitmaps.forEach(bitmap => bitmap.close());
      batchBusy = false;
      void updatePreview();
    }
  }

  async function copyFrame(): Promise<void> {
    const blob = await captureFrame('image/png');
    if (!blob) {
      status = 'error';
      message = '当前画面正在准备';
      return;
    }
    if (!navigator.clipboard || typeof ClipboardItem === 'undefined') {
      status = 'error';
      message = '剪贴板图片写入由系统接管，可先导出图片';
      return;
    }
    try {
      await navigator.clipboard.write([new ClipboardItem({ [blob.type]: blob })]);
      status = 'done';
      message = '已复制当前帧到剪贴板';
    } catch {
      status = 'error';
      message = '剪贴板授权待确认，可先导出图片';
    }
  }

  function toggleSelect(name: string): void {
    openSelect = openSelect === name ? '' : name;
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
    customRatioWidth = Math.max(1, Math.min(Math.round(Number(customRatioWidth) || 1), 99));
    customRatioHeight = Math.max(1, Math.min(Math.round(Number(customRatioHeight) || 1), 99));
    void updatePreview();
  }

  function updateCustomOutput(): void {
    customOutputWidth = Math.max(64, Math.min(Math.round(Number(customOutputWidth) || 1920), 4096));
    customOutputHeight = Math.max(64, Math.min(Math.round(Number(customOutputHeight) || 1080), 4096));
    void updatePreview();
  }

  function updateVisualAdjustments(): void {
    brightness = Math.max(50, Math.min(Math.round(Number(brightness) || 100), 150));
    contrast = Math.max(50, Math.min(Math.round(Number(contrast) || 100), 150));
    saturation = Math.max(0, Math.min(Math.round(Number(saturation) || 100), 200));
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
    splitFrames.forEach(frame => revoke(frame.url));
  });
</script>

<svelte:head>
  <meta property="og:image" content="/icon.svg">
</svelte:head>

<header class="header">
  <div class="header-content">
    <a href="/" class="header-back" aria-label="返回首页"><iconify-icon icon="lucide:chevron-left"></iconify-icon></a>
    <h1 class="header-title"><iconify-icon class="header-logo" icon="lucide:film"></iconify-icon>视频素材工坊</h1>
    <a class="header-link" href="/search/">Wiki 搜索</a>
  </div>
</header>

<main class="main native-video-page">
  <section class="workbench-head">
    <div class="workbench-copy">
      <strong><iconify-icon icon={activeMode === 'video' ? 'lucide:clapperboard' : 'lucide:layers-3'}></iconify-icon>{activeMode === 'video' ? '视频素材' : 'GIF 工具'}</strong>
      <span>{activeMode === 'video' ? '导入本地视频后，可以整理截图、批量帧图、联系表、GIF 和短视频片段。' : '把 GIF 拆成可下载的 PNG 帧，也可以生成带时间码的联系表。'}</span>
    </div>
    <div class:video={activeMode === 'video'} class:gif={activeMode === 'gif'} class="mode-switch" role="tablist" aria-label="工作模式">
      <button type="button" class:active={activeMode === 'video'} onclick={() => activeMode = 'video'}><iconify-icon icon="lucide:video"></iconify-icon>视频素材</button>
      <button type="button" class:active={activeMode === 'gif'} onclick={() => activeMode = 'gif'}><iconify-icon icon="lucide:image-play"></iconify-icon>GIF 工具</button>
    </div>
    {#if message}<div class:error={status === 'error'} class:done={status === 'done'} class:loading={status === 'loading'} class="status-bar"><span>{message}</span></div>{/if}
  </section>

  {#if activeMode === 'video' && !videoUrl}
    <section class="empty-workbench">
      <div class="empty-copy">
        <strong>把视频整理成可直接使用的素材</strong>
        <span>适合从录屏、PV 或演示视频里提取画面，顺手生成封面、短动图、帧图包和联系表。</span>
      </div>
      <label class="file-drop hero-drop" ondragover={(event) => event.preventDefault()} ondrop={onDrop}>
        <input type="file" accept="video/*,.mp4,.webm,.mov,.m4v,.ogv" onchange={chooseFile}>
        <strong><iconify-icon icon="lucide:upload-cloud"></iconify-icon>拖入或选择视频素材</strong>
        <span>载入后在本地浏览器内预览、定位时间点并导出需要的画面</span>
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

          <section class="shot-preview-card">
            <div class="section-head"><strong><iconify-icon icon="lucide:image"></iconify-icon>截图预览</strong><span>{outputInfo}</span></div>
            <div class="shot-preview-stage">
              {#if previewUrl}
                <img src={previewUrl} alt="截图预览">
              {:else}
                <div class="shot-preview-empty"><iconify-icon icon="lucide:image"></iconify-icon><span>定位时间点后可刷新预览</span></div>
              {/if}
            </div>
            <div class="actions preview-actions">
              <button type="button" onclick={updatePreview} disabled={batchBusy}><iconify-icon icon="lucide:refresh-cw"></iconify-icon>刷新预览</button>
              <button type="button" onclick={copyFrame} disabled={status === 'loading' || batchBusy}><iconify-icon icon="lucide:copy"></iconify-icon>复制</button>
              <button class="primary" type="button" onclick={exportFrame} disabled={status === 'loading' || batchBusy}><iconify-icon icon="lucide:download"></iconify-icon>导出</button>
            </div>
          </section>

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
        </section>

        <aside class="toolbox-area">
          <label class="file-drop compact-drop" ondragover={(event) => event.preventDefault()} ondrop={onDrop}>
            <input type="file" accept="video/*,.mp4,.webm,.mov,.m4v,.ogv" onchange={chooseFile}>
            <strong><iconify-icon icon="lucide:file-video"></iconify-icon>{file?.name || '视频素材'}</strong>
            <span>点击可更换当前视频</span>
          </label>

          {#if file}
            <div class="file-meta">
              <span><iconify-icon icon="lucide:hard-drive"></iconify-icon>{formatFileSize(file.size)}</span>
              <span><iconify-icon icon="lucide:file-type"></iconify-icon>{file.type || '素材类型'}</span>
              <span><iconify-icon icon="lucide:clock-3"></iconify-icon>{duration > 0 ? formatTime(duration) : '读取时长'}</span>
            </div>
          {/if}

          <section class="global-settings">
            <div class="section-head"><strong><iconify-icon icon="lucide:sliders-horizontal"></iconify-icon>输出设置</strong><span>这些参数会用于截图、批量帧图、联系表、GIF 和短视频导出。</span></div>
            <div class="settings-stack">
              <div class="settings-group">
                <div class="settings-group-title"><iconify-icon icon="lucide:file-output"></iconify-icon><span>文件与尺寸</span></div>
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
              </div>

              <div class="settings-group">
                <div class="settings-group-title"><iconify-icon icon="lucide:crop"></iconify-icon><span>裁切</span></div>
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
              </div>

              <div class="settings-group">
                <div class="settings-group-title"><iconify-icon icon="lucide:sliders-horizontal"></iconify-icon><span>画面</span></div>
                <div class="options-grid compact triple">
                  <label>
                    <span>亮度</span>
                    <input type="number" min="50" max="150" step="5" bind:value={brightness} oninput={updateVisualAdjustments}>
                  </label>
                  <label>
                    <span>对比度</span>
                    <input type="number" min="50" max="150" step="5" bind:value={contrast} oninput={updateVisualAdjustments}>
                  </label>
                  <label>
                    <span>饱和度</span>
                    <input type="number" min="0" max="200" step="5" bind:value={saturation} oninput={updateVisualAdjustments}>
                  </label>
                </div>
              </div>

              <div class="settings-group">
                <div class="settings-group-title"><iconify-icon icon="lucide:type"></iconify-icon><span>文字</span></div>
                <div class="options-grid compact">
                  <label class="wide-field">
                    <span>文字叠加</span>
                    <textarea maxlength="90" rows="2" placeholder="留空则不叠加文字" bind:value={overlayText}></textarea>
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
              </div>
            </div>
          </section>

          <div class:single={activeTool === 'single'} class:batch={activeTool === 'batch'} class:gif={activeTool === 'gif'} class="tabs-header" role="tablist" aria-label="视频素材工具">
            <button type="button" class:active={activeTool === 'single'} onclick={() => activeTool = 'single'}><iconify-icon icon="lucide:camera"></iconify-icon>截图</button>
            <button type="button" class:active={activeTool === 'batch'} onclick={() => activeTool = 'batch'}><iconify-icon icon="lucide:grid-3x3"></iconify-icon>批量</button>
            <button type="button" class:active={activeTool === 'gif'} onclick={() => activeTool = 'gif'}><iconify-icon icon="lucide:sparkles"></iconify-icon>动图</button>
          </div>

          <div class="tab-content">
            {#if activeTool === 'single'}
              <div class="section-head"><strong><iconify-icon icon="lucide:camera"></iconify-icon>单帧素材</strong><span>导出当前时间点的画面，可叠加文字、调整比例和尺寸。</span></div>
              <div class="shot-note"><iconify-icon icon="lucide:info"></iconify-icon><span>左侧预览卡展示最终截图效果。调整比例、位置、滤镜或文字后，可刷新预览再导出。</span></div>
            {:else if activeTool === 'batch'}
              <div class="section-head"><strong><iconify-icon icon="lucide:grid-3x3"></iconify-icon>批量素材</strong><span>在当前区间内抽取多张画面，适合做帧图包或缩略图联系表。</span></div>
              <div class="options-grid">
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
              <div class="actions sticky-footer-actions">
                <button type="button" onclick={exportContactSheet} disabled={status === 'loading' || batchBusy}><iconify-icon icon="lucide:layout-grid"></iconify-icon>导出联系表 · {sampleTimes.length}</button>
                <button class="primary" type="button" onclick={exportBatchZip} disabled={status === 'loading' || batchBusy}><iconify-icon icon="lucide:package-down"></iconify-icon>导出 ZIP · {sampleTimes.length}</button>
              </div>
            {:else if activeTool === 'gif'}
              <div class="section-head"><strong><iconify-icon icon="lucide:sparkles"></iconify-icon>短动图</strong><span>从当前区间生成 GIF 或短视频片段，可选择正放、倒放和乒乓循环。</span></div>
              <div class="options-grid">
                <label>
                  <span>时长（秒）</span>
                  <input type="number" min="0.5" max="6" step="0.5" bind:value={gifSeconds}>
                </label>
                <label>
                  <span>帧率（FPS）</span>
                  <input type="number" min="2" max="15" bind:value={gifFps}>
                </label>
                <label>
                  <span>宽度（px）</span>
                  <input type="number" min="96" max="1280" step="16" bind:value={gifWidth}>
                </label>
                <label>
                  <span>短视频格式</span>
                  <CustomSelect value={motionVideoFormat} options={motionVideoSelectOptions} open={openSelect === 'motionFormat'} onSelect={selectMotionVideoFormat} onToggle={() => toggleSelect('motionFormat')} />
                </label>
                <label>
                  <span>播放方向</span>
                  <CustomSelect value={motionDirection} options={[{ code: 'forward', name: '正放' }, { code: 'reverse', name: '倒放' }, { code: 'pingpong', name: '乒乓' }]} open={openSelect === 'motionDirection'} onSelect={selectMotionDirection} onToggle={() => toggleSelect('motionDirection')} />
                </label>
                <label>
                  <span>GIF 色彩数</span>
                  <input type="number" min="16" max="256" step="16" bind:value={gifColors}>
                </label>
                <label>
                  <span>GIF 循环</span>
                  <CustomSelect value={gifRepeat} options={[{ code: 'forever', name: '无限循环' }, { code: 'once', name: '播放一次' }, { code: 'twice', name: '播放两次' }]} open={openSelect === 'gifRepeat'} onSelect={selectGifRepeat} onToggle={() => toggleSelect('gifRepeat')} />
                </label>
                <label>
                  <span>WebM 码率（kbps）</span>
                  <input type="number" min="250" max="12000" step="250" bind:value={webmBitrate}>
                </label>
              </div>
              <div class="actions sticky-footer-actions">
                <button type="button" onclick={exportWebm} disabled={status === 'loading' || batchBusy || !supportedMotionVideoOptions.length}><iconify-icon icon="lucide:file-video"></iconify-icon>导出短视频 · {Math.max(1, Math.min(Math.round((Number(gifSeconds) || 2) * (Number(gifFps) || 8)), 300))} 帧</button>
                <button class="primary" type="button" onclick={exportGif} disabled={status === 'loading' || batchBusy}><iconify-icon icon="lucide:image-play"></iconify-icon>导出 GIF · {Math.max(1, Math.min(Math.round((Number(gifSeconds) || 2) * (Number(gifFps) || 8)), 90))} 帧</button>
              </div>
              <div class="motion-previews">
                {#if gifPreviewUrl}
                  <div class="gif-preview">
                    <span>最近 GIF</span>
                    <img src={gifPreviewUrl} alt="GIF 预览">
                  </div>
                {/if}
                {#if webmPreviewUrl}
                  <div class="gif-preview">
                    <span>最近短视频</span>
                    <!-- svelte-ignore a11y_media_has_caption -->
                    <video src={webmPreviewUrl} controls loop muted playsinline></video>
                  </div>
                {/if}
              </div>
            {/if}
          </div>
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
            <span>查看帧序列，下载单帧，也可以导出 PNG 帧包和时间码联系表。</span>
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
          <div class="section-head"><strong><iconify-icon icon="lucide:layers-3"></iconify-icon>GIF 拆解</strong><span>载入 GIF 后会在本页解析帧信息并生成导出项。</span></div>
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
            <div class="gif-empty-note"><iconify-icon icon="lucide:info"></iconify-icon>选择 GIF 后会显示帧网格、尺寸、时长和导出操作。</div>
          {/if}
        </aside>
      </div>
    </section>
  {/if}

  <section class="ideas-card">
    <div class="section-head"><strong><iconify-icon icon="lucide:wand-sparkles"></iconify-icon>更多素材玩法</strong><span>这些方向可以继续往工作台里加，保持轻量，同时覆盖常见素材整理流程。</span></div>
    <div class="idea-grid">
      {#each nativeIdeas as idea}
        <span><iconify-icon icon="lucide:circle-dot"></iconify-icon>{idea}</span>
      {/each}
    </div>
  </section>

  <canvas bind:this={canvasEl} class="hidden-canvas"></canvas>
</main>
