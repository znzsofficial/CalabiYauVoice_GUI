<script lang="ts">
  import { onDestroy } from 'svelte';
  import CustomSelect from '../CustomSelect.svelte';
  import { batchCutSegments, detectEncoderSupport, experimentalH265Enabled, exportAnimatedImage, extractAudioForWaveform, muxBilibiliCache, splitMediaTracks, terminateFfmpeg, transcodeVideo, type AnimatedExportOptions, type ClipSegment, type CutEncodingOptions, type CutMode, type EncoderSupport, type FfmpegProgress, type ProcessedFile } from './ffmpegTools';
  import { formatFileSize, toError } from '../search/utils';

  type TaskStatus = 'idle' | 'loading' | 'done' | 'error';
  type SelectOption = { code: string; name: string; disabled?: boolean };

  const defaultEncoding: CutEncodingOptions = { format: 'mp4', video: 'h264', preset: 'veryfast', crf: 20, videoBitrate: 'auto', audioBitrate: '160k', scale: 'source', fps: 'source', audio: 'aac' };

  let frameFile = $state<File | null>(null);
  let frameUrl = $state('');
  let frameDuration = $state(0);
  let framePosition = $state(0);
  let framePreviewUrl = $state('');
  let frameStatus = $state<TaskStatus>('idle');
  let frameMessage = $state('');
  let videoEl = $state<HTMLVideoElement | null>(null);
  let canvasEl = $state<HTMLCanvasElement | null>(null);
  let frameTimer: ReturnType<typeof setTimeout> | null = null;

  let biliVideoFile = $state<File | null>(null);
  let biliAudioFile = $state<File | null>(null);
  let muxStatus = $state<TaskStatus>('idle');
  let muxMessage = $state('');
  let muxProgress = $state(0);
  let muxOutput = $state<ProcessedFile | null>(null);

  let splitFile = $state<File | null>(null);
  let splitStatus = $state<TaskStatus>('idle');
  let splitMessage = $state('');
  let splitProgress = $state(0);
  let splitOutputs: ProcessedFile[] = $state([]);

  let encodeFile = $state<File | null>(null);
  let encodeStatus = $state<TaskStatus>('idle');
  let encodeMessage = $state('');
  let encodeProgress = $state(0);
  let encodeOutput = $state<ProcessedFile | null>(null);
  let encodeDialogOpen = $state(false);

  let animatedFile = $state<File | null>(null);
  let animatedUrl = $state('');
  let animatedDuration = $state(0);
  let animatedStart = $state(0);
  let animatedEnd = $state(3);
  let animatedStatus = $state<TaskStatus>('idle');
  let animatedMessage = $state('');
  let animatedProgress = $state(0);
  let animatedOutput = $state<ProcessedFile | null>(null);
  let animatedDialogOpen = $state(false);
  let animatedVideoEl = $state<HTMLVideoElement | null>(null);
  let animatedOptions = $state<AnimatedExportOptions>({ format: 'webp', width: '540', fps: '12', quality: 80, loop: true, dither: 'sierra2_4a', statsMode: 'diff' });

  let cutFile = $state<File | null>(null);
  let cutUrl = $state('');
  let cutDuration = $state(0);
  let cutStart = $state(0);
  let cutEnd = $state(0);
  let cutSegments: ClipSegment[] = $state([]);
  let cutStatus = $state<TaskStatus>('idle');
  let cutMessage = $state('');
  let cutProgress = $state(0);
  let cutOutputs: ProcessedFile[] = $state([]);
  let cutVideoEl = $state<HTMLVideoElement | null>(null);
  let cutTimelineCanvas = $state<HTMLCanvasElement | null>(null);
  let cutMode = $state<CutMode>('fast');
  let cutEncoding = $state<CutEncodingOptions>({ ...defaultEncoding });
  let encodeEncoding = $state<CutEncodingOptions>({ ...defaultEncoding });
  let cutExportDialogOpen = $state(false);
  let waveformPeaks: number[] = $state([]);
  let waveformStatus = $state('');
  let encoderSupport = $state<EncoderSupport | null>(null);
  let encoderStatus = $state<TaskStatus>('idle');
  let encoderMessage = $state('');
  let coreProgress = $state(0);
  let waveformExtracting = $state(false);
  let formatHint = $state('');
  let activeRun: AbortController | null = null;
  let cutCurrentTime = $state(0);
  let timelineDragMode = $state('' as '' | 'start' | 'end' | 'playhead');
  let openSelect = $state('');
  let timelineFrame = 0;
  let dragTarget = $state('');

  type ToolTab = 'cut' | 'encode' | 'animated' | 'frame' | 'track';
  let activeTab = $state<ToolTab>('cut');

  const formatOptions: SelectOption[] = [
    { code: 'mp4', name: 'MP4' },
    { code: 'webm', name: 'WebM' },
    { code: 'mkv', name: 'MKV' }
  ];
  const presetOptions: SelectOption[] = ['ultrafast', 'superfast', 'veryfast', 'faster', 'fast', 'medium', 'slow'].map(preset => ({ code: preset, name: preset }));
  const videoBitrateOptions: SelectOption[] = ['auto', '1500k', '2500k', '4000k', '6000k', '9000k'].map(rate => ({ code: rate, name: rate === 'auto' ? 'CRF' : rate }));
  const scaleOptions: SelectOption[] = [{ code: 'source', name: '原始' }, { code: '1080', name: '1080p' }, { code: '720', name: '720p' }, { code: '480', name: '480p' }];
  const fpsOptions: SelectOption[] = [{ code: 'source', name: '原始' }, { code: '60', name: '60 fps' }, { code: '30', name: '30 fps' }, { code: '24', name: '24 fps' }];
  const audioOptions: SelectOption[] = [{ code: 'aac', name: 'AAC' }, { code: 'opus', name: 'Opus' }, { code: 'copy', name: '复制' }, { code: 'mute', name: '静音' }];
  const audioBitrateOptions: SelectOption[] = ['96k', '128k', '160k', '192k', '256k'].map(rate => ({ code: rate, name: rate }));
  const animatedFormatOptions: SelectOption[] = [{ code: 'webp', name: 'WebP 动图' }, { code: 'gif', name: 'GIF' }];
  const animatedWidthOptions: SelectOption[] = [{ code: 'source', name: '原始' }, { code: '720', name: '720px' }, { code: '540', name: '540px' }, { code: '360', name: '360px' }, { code: '240', name: '240px' }];
  const animatedFpsOptions: SelectOption[] = [{ code: '24', name: '24 fps' }, { code: '15', name: '15 fps' }, { code: '12', name: '12 fps' }, { code: '10', name: '10 fps' }, { code: '8', name: '8 fps' }];
  const ditherOptions: SelectOption[] = [{ code: 'sierra2_4a', name: 'Sierra' }, { code: 'floyd_steinberg', name: 'Floyd' }, { code: 'bayer', name: 'Bayer' }];
  const statsModeOptions: SelectOption[] = [{ code: 'diff', name: '动态' }, { code: 'full', name: '完整' }];

  const busy = $derived(frameStatus === 'loading' || muxStatus === 'loading' || splitStatus === 'loading' || cutStatus === 'loading' || encodeStatus === 'loading' || animatedStatus === 'loading' || encoderStatus === 'loading');

  function startRun(): AbortController {
    activeRun?.abort();
    activeRun = new AbortController();
    return activeRun;
  }

  function finishRun(controller: AbortController): void {
    if (activeRun === controller) activeRun = null;
  }

  function cancelRun(): void {
    activeRun?.abort();
    terminateFfmpeg();
    encoderSupport = null;
    encoderStatus = 'idle';
    encoderMessage = '';
    coreProgress = 0;
  }

  function toggleSelect(id: string): void {
    openSelect = openSelect === id ? '' : id;
  }

  function selectValue<T extends string>(id: string, setValue: (value: T) => void): (value: string) => void {
    return value => {
      setValue(value as T);
      openSelect = '';
    };
  }

  function videoOptions(): SelectOption[] {
    const options: SelectOption[] = [
      { code: 'h264', name: 'H.264', disabled: !isEncoderAvailable('h264') },
      { code: 'vp9', name: 'VP9', disabled: !isEncoderAvailable('vp9') },
      { code: 'copy', name: '复制' },
      { code: 'none', name: '无' }
    ];
    if (experimentalH265Enabled) options.splice(1, 0, { code: 'h265', name: 'H.265 实验', disabled: !isEncoderAvailable('h265') });
    return options;
  }

  function fileLabel(file: File | null, fallback: string): string {
    return file ? `${file.name} · ${formatFileSize(file.size)}` : fallback;
  }

  function largeFileHint(file: File | null): string {
    return file && file.size >= 500 * 1024 * 1024 ? '移动端建议选择短片段或低分辨率' : '';
  }

  function fileTitle(file: File | null, fallback: string): string {
    const label = fileLabel(file, fallback);
    const hint = largeFileHint(file);
    return hint ? `${label} · ${hint}` : label;
  }

  function chooseFile(event: Event, setFile: (file: File | null) => void): void {
    const file = (event.currentTarget as HTMLInputElement).files?.[0] || null;
    setFile(file);
  }

  function handleDragOver(event: DragEvent, target: string): void {
    if (busy || !event.dataTransfer?.types.includes('Files')) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';
    dragTarget = target;
  }

  function handleDragLeave(event: DragEvent, target: string): void {
    const next = event.relatedTarget as Node | null;
    if (next && event.currentTarget instanceof Node && event.currentTarget.contains(next)) return;
    if (dragTarget === target) dragTarget = '';
  }

  function dropFile(event: DragEvent, target: string, setFile: (file: File | null) => void): void {
    if (busy) return;
    event.preventDefault();
    dragTarget = '';
    const file = event.dataTransfer?.files?.[0] || null;
    if (file) setFile(file);
  }

  function revokeOutput(file: ProcessedFile | null): void {
    if (file) URL.revokeObjectURL(file.url);
  }

  function clearSplitOutputs(): void {
    for (const file of splitOutputs) URL.revokeObjectURL(file.url);
    splitOutputs = [];
  }

  function clearCutOutputs(): void {
    for (const file of cutOutputs) URL.revokeObjectURL(file.url);
    cutOutputs = [];
  }

  function clearSingleOutput(target: 'mux' | 'encode' | 'animated'): void {
    if (target === 'mux') {
      revokeOutput(muxOutput);
      muxOutput = null;
      muxStatus = 'idle';
      muxMessage = '';
    } else if (target === 'encode') {
      revokeOutput(encodeOutput);
      encodeOutput = null;
      encodeStatus = 'idle';
      encodeMessage = encodeFile ? '准备就绪' : '';
    } else {
      revokeOutput(animatedOutput);
      animatedOutput = null;
      animatedStatus = 'idle';
      animatedMessage = animatedFile ? '选择时间范围' : '';
    }
  }

  function clearOutputs(target: 'cut' | 'split'): void {
    if (target === 'cut') {
      clearCutOutputs();
      cutStatus = 'idle';
      cutMessage = cutFile ? '选择片段范围' : '';
    } else {
      clearSplitOutputs();
      splitStatus = 'idle';
      splitMessage = '';
    }
  }

  function setEncodeFile(file: File | null): void {
    encodeFile = file;
    encodeStatus = 'idle';
    encodeMessage = file ? '准备就绪' : '';
    encodeProgress = 0;
    revokeOutput(encodeOutput);
    encodeOutput = null;
  }

  function setAnimatedFile(file: File | null): void {
    animatedFile = file;
    animatedStatus = 'idle';
    animatedMessage = file ? '选择时间范围' : '';
    animatedDuration = 0;
    animatedStart = 0;
    animatedEnd = 3;
    revokeOutput(animatedOutput);
    animatedOutput = null;
    if (animatedUrl) URL.revokeObjectURL(animatedUrl);
    animatedUrl = file ? URL.createObjectURL(file) : '';
  }

  function updateProgress(target: 'mux' | 'split' | 'cut' | 'encode' | 'animated', progress: FfmpegProgress): void {
    const value = progress.progress > 1 ? Math.round(progress.progress) : progress.progress > 0 ? Math.round(progress.progress * 100) : 0;
    if (target === 'mux') {
      muxProgress = value;
      muxMessage = progress.message || muxMessage;
    } else if (target === 'split') {
      splitProgress = value;
      splitMessage = progress.message || splitMessage;
    } else if (target === 'cut') {
      cutProgress = value;
      cutMessage = progress.message || cutMessage;
    } else if (target === 'encode') {
      encodeProgress = value;
      encodeMessage = progress.message || encodeMessage;
    } else {
      animatedProgress = value;
      animatedMessage = progress.message || animatedMessage;
    }
  }

  function isEncoderAvailable(video: CutEncodingOptions['video']): boolean {
    if (video === 'copy' || video === 'none' || !encoderSupport) return true;
    return encoderSupport[video];
  }

  function chooseSupportedVideo(current: CutEncodingOptions['video']): CutEncodingOptions['video'] {
    if (isEncoderAvailable(current)) return current;
    if (encoderSupport?.h264) return 'h264';
    if (experimentalH265Enabled && encoderSupport?.h265) return 'h265';
    if (encoderSupport?.vp9) return 'vp9';
    return 'copy';
  }

  async function ensureEncoderSupport(): Promise<void> {
    if (encoderSupport || encoderStatus === 'loading') return;
    encoderStatus = 'loading';
    encoderMessage = '检测编码器';
    coreProgress = 0;
    try {
      encoderSupport = await detectEncoderSupport(progress => {
        encoderMessage = progress.message || encoderMessage;
        coreProgress = progress.progress > 1 ? Math.round(progress.progress) : Math.round(Math.max(0, Math.min(1, progress.progress)) * 100);
      });
      cutEncoding = normalizeEncoding({ ...cutEncoding, video: chooseSupportedVideo(cutEncoding.video) }, 'video', chooseSupportedVideo(cutEncoding.video));
      encodeEncoding = normalizeEncoding({ ...encodeEncoding, video: chooseSupportedVideo(encodeEncoding.video) }, 'video', chooseSupportedVideo(encodeEncoding.video));
      encoderStatus = 'done';
      encoderMessage = '编码器就绪';
      coreProgress = 100;
    } catch (error) {
      encoderStatus = 'error';
      encoderMessage = toError(error).message || '编码器检测失败';
    }
  }

  function handleAnimatedLoaded(): void {
    animatedDuration = Number.isFinite(animatedVideoEl?.duration || 0) ? (animatedVideoEl?.duration || 0) : 0;
    animatedStart = 0;
    animatedEnd = Math.min(animatedDuration || 0, 3);
    animatedMessage = animatedDuration > 0 ? '选择时间范围' : '无法读取视频时长';
  }

  function setAnimatedStart(value: number): void {
    animatedStart = Math.max(0, Math.min(value, Math.max(0, animatedEnd - 0.1)));
    if (animatedVideoEl) animatedVideoEl.currentTime = animatedStart;
  }

  function setAnimatedEnd(value: number): void {
    animatedEnd = Math.min(animatedDuration, Math.max(value, animatedStart + 0.1));
    if (animatedVideoEl) animatedVideoEl.currentTime = animatedEnd;
  }

  function setFrameFile(file: File | null): void {
    frameFile = file;
    frameStatus = file ? 'loading' : 'idle';
    frameMessage = file ? '读取中' : '';
    frameDuration = 0;
    framePosition = 0;
    if (frameUrl) URL.revokeObjectURL(frameUrl);
    if (framePreviewUrl) URL.revokeObjectURL(framePreviewUrl);
    framePreviewUrl = '';
    frameUrl = file ? URL.createObjectURL(file) : '';
  }

  function handleVideoLoaded(): void {
    frameDuration = Number.isFinite(videoEl?.duration || 0) ? (videoEl?.duration || 0) : 0;
    frameStatus = 'idle';
    frameMessage = frameDuration > 0 ? '拖动选择画面' : '无法读取视频时长';
    seekFrame(0, 0);
  }

  function seekFrame(value: number, delay = 120): void {
    framePosition = Math.max(0, Math.min(value, frameDuration || value));
    if (frameTimer) clearTimeout(frameTimer);
    frameTimer = setTimeout(() => {
      if (videoEl) videoEl.currentTime = framePosition;
    }, delay);
  }

  function captureFrame(): Blob | null {
    if (!videoEl || !canvasEl || videoEl.readyState < 2) return null;
    const width = videoEl.videoWidth;
    const height = videoEl.videoHeight;
    if (!width || !height) return null;
    canvasEl.width = width;
    canvasEl.height = height;
    canvasEl.getContext('2d')?.drawImage(videoEl, 0, 0, width, height);
    const dataUrl = canvasEl.toDataURL('image/png');
    const [meta, data] = dataUrl.split(',');
    const mime = meta.match(/data:(.*);base64/)?.[1] || 'image/png';
    const bytes = Uint8Array.from(atob(data), char => char.charCodeAt(0));
    return new Blob([bytes], { type: mime });
  }

  function updateFramePreview(): void {
    const blob = captureFrame();
    if (!blob) return;
    if (framePreviewUrl) URL.revokeObjectURL(framePreviewUrl);
    framePreviewUrl = URL.createObjectURL(blob);
  }

  function exportFrame(): void {
    if (!frameFile) return;
    const blob = captureFrame();
    if (!blob) {
      frameStatus = 'error';
      frameMessage = '无法读取当前画面';
      return;
    }
    const name = `${frameFile.name.replace(/\.[^.]+$/, '') || 'video'}_${Math.round(framePosition * 1000)}ms.png`;
    downloadBlob(blob, name);
    frameStatus = 'done';
    frameMessage = `已导出 ${name}`;
  }

  async function runMux(): Promise<void> {
    if (!biliVideoFile || !biliAudioFile || muxStatus === 'loading') return;
    muxStatus = 'loading';
    muxProgress = 0;
    muxMessage = '合成中';
    revokeOutput(muxOutput);
    muxOutput = null;
    const run = startRun();
    try {
      muxOutput = await muxBilibiliCache(biliVideoFile, biliAudioFile, progress => updateProgress('mux', progress), { signal: run.signal });
      muxStatus = 'done';
      muxProgress = 100;
      muxMessage = `合成完成：${muxOutput.name}`;
    } catch (error) {
      muxStatus = 'error';
      muxMessage = run.signal.aborted ? '已取消' : toError(error).message || '合成失败';
    } finally {
      finishRun(run);
    }
  }

  async function runSplit(): Promise<void> {
    if (!splitFile || splitStatus === 'loading') return;
    splitStatus = 'loading';
    splitProgress = 0;
    splitMessage = '拆分中';
    clearSplitOutputs();
    const run = startRun();
    try {
      const result = await splitMediaTracks(splitFile, progress => updateProgress('split', progress), { signal: run.signal });
      splitOutputs = result.files;
      splitStatus = 'done';
      splitProgress = 100;
      splitMessage = `拆分完成：${result.files.length} 个文件`;
    } catch (error) {
      splitStatus = 'error';
      splitMessage = run.signal.aborted ? '已取消' : toError(error).message || '拆分失败';
    } finally {
      finishRun(run);
    }
  }

  function setCutFile(file: File | null): void {
    cutFile = file;
    cutStatus = file ? 'idle' : 'idle';
    cutMessage = file ? '读取中' : '';
    cutDuration = 0;
    cutStart = 0;
    cutEnd = 0;
    cutSegments = [];
    cutCurrentTime = 0;
    waveformPeaks = [];
    waveformStatus = file ? '分析波形中' : '';
    clearCutOutputs();
    if (cutUrl) URL.revokeObjectURL(cutUrl);
    cutUrl = file ? URL.createObjectURL(file) : '';
    if (file) loadWaveform(file);
    scheduleTimelineDraw();
  }

  function handleCutVideoLoaded(): void {
    cutDuration = Number.isFinite(cutVideoEl?.duration || 0) ? (cutVideoEl?.duration || 0) : 0;
    cutStart = 0;
    cutEnd = Math.min(cutDuration || 0, 10);
    cutMessage = cutDuration > 0 ? '选择片段范围' : '无法读取视频时长';
    scheduleTimelineDraw();
  }

  function setCutStart(value: number): void {
    cutStart = Math.max(0, Math.min(value, Math.max(0, cutEnd - 0.1)));
    if (cutVideoEl) cutVideoEl.currentTime = cutStart;
    cutCurrentTime = cutStart;
    scheduleTimelineDraw();
  }

  function setCutEnd(value: number): void {
    cutEnd = Math.min(cutDuration, Math.max(value, cutStart + 0.1));
    if (cutVideoEl) cutVideoEl.currentTime = cutEnd;
    cutCurrentTime = cutEnd;
    scheduleTimelineDraw();
  }

  function addCutSegment(): void {
    if (!cutFile || cutEnd - cutStart <= 0.05) return;
    const id = Date.now();
    cutSegments = [...cutSegments, { id, start: cutStart, end: cutEnd }].sort((a, b) => a.start - b.start);
    cutMessage = `已添加 ${cutSegments.length} 段`;
    scheduleTimelineDraw();
  }

  function removeCutSegment(id: number): void {
    cutSegments = cutSegments.filter(segment => segment.id !== id);
    scheduleTimelineDraw();
  }

  function seekCutSegment(segment: ClipSegment): void {
    cutStart = segment.start;
    cutEnd = segment.end;
    if (cutVideoEl) cutVideoEl.currentTime = segment.start;
    cutCurrentTime = segment.start;
    scheduleTimelineDraw();
  }

  function updateCutPlayhead(): void {
    cutCurrentTime = cutVideoEl?.currentTime || 0;
    scheduleTimelineDraw();
  }

  async function loadWaveform(file: File): Promise<void> {
    try {
      waveformStatus = '正在解码波形';
      scheduleTimelineDraw();
      const context = new AudioContext();
      let audioBuffer: AudioBuffer | null = null;
      try {
        audioBuffer = await context.decodeAudioData(await file.arrayBuffer());
      } catch {
        waveformStatus = '正在提取音频';
        waveformExtracting = true;
        scheduleTimelineDraw();
        try {
          const wavBuffer = await extractAudioForWaveform(file);
          if (wavBuffer) audioBuffer = await context.decodeAudioData(wavBuffer);
        } finally {
          waveformExtracting = false;
        }
      }
      if (!audioBuffer) throw new Error('无法解码音频');
      const channel = audioBuffer.getChannelData(0);
      const bins = 960;
      const block = Math.max(1, Math.floor(channel.length / bins));
      const peaks: number[] = [];
      for (let i = 0; i < bins; i++) {
        let max = 0;
        const start = i * block;
        const end = Math.min(channel.length, start + block);
        for (let j = start; j < end; j++) max = Math.max(max, Math.abs(channel[j]));
        peaks.push(max);
      }
      await context.close();
      if (file !== cutFile) return;
      waveformPeaks = peaks;
      waveformStatus = '';
    } catch {
      if (file !== cutFile) return;
      waveformPeaks = [];
      waveformStatus = '无法读取波形';
    } finally {
      scheduleTimelineDraw();
    }
  }

  function scheduleTimelineDraw(): void {
    if (timelineFrame) cancelAnimationFrame(timelineFrame);
    timelineFrame = requestAnimationFrame(drawTimeline);
  }

  function drawTimeline(): void {
    timelineFrame = 0;
    const canvas = cutTimelineCanvas;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const width = Math.max(1, Math.floor(rect.width * dpr));
    const height = Math.max(1, Math.floor(rect.height * dpr));
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const styles = getComputedStyle(document.documentElement);
    const fg = styles.getPropertyValue('--foreground').trim() || '#111';
    const muted = styles.getPropertyValue('--muted-foreground').trim() || '#777';
    const border = styles.getPropertyValue('--border').trim() || '#ddd';
    const accent = styles.getPropertyValue('--accent').trim() || '#eee';
    const primary = styles.getPropertyValue('--primary').trim() || '#111';
    ctx.clearRect(0, 0, width, height);
    ctx.fillStyle = accent;
    ctx.fillRect(0, 0, width, height);

    const rulerH = 24 * dpr;
    const waveTop = rulerH + 8 * dpr;
    const waveH = height - waveTop - 10 * dpr;
    const duration = Math.max(cutDuration, 1);
    ctx.strokeStyle = border;
    ctx.lineWidth = 1 * dpr;
    ctx.font = `${10 * dpr}px system-ui`;
    ctx.fillStyle = muted;
    const ticks = Math.min(10, Math.max(2, Math.ceil(duration / 10)));
    for (let i = 0; i <= ticks; i++) {
      const x = (i / ticks) * width;
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, height);
      ctx.stroke();
      ctx.fillText(formatTime((i / ticks) * duration).replace(/^00:/, ''), x + 4 * dpr, 15 * dpr);
    }

    for (const segment of cutSegments) {
      const x = (segment.start / duration) * width;
      const w = Math.max(2 * dpr, ((segment.end - segment.start) / duration) * width);
      ctx.fillStyle = colorMix(primary, 0.18);
      ctx.fillRect(x, waveTop, w, waveH);
    }

    const startX = (cutStart / duration) * width;
    const endX = (cutEnd / duration) * width;
    ctx.fillStyle = colorMix(primary, 0.28);
    ctx.fillRect(startX, waveTop, Math.max(2 * dpr, endX - startX), waveH);

    if (waveformPeaks.length > 0) {
      ctx.strokeStyle = primary;
      ctx.lineWidth = Math.max(1, 1.5 * dpr);
      const mid = waveTop + waveH / 2;
      for (let x = 0; x < width; x += 2 * dpr) {
        const peak = waveformPeaks[Math.min(waveformPeaks.length - 1, Math.floor((x / width) * waveformPeaks.length))] || 0;
        const amp = Math.max(1 * dpr, peak * waveH * 0.48);
        ctx.beginPath();
        ctx.moveTo(x, mid - amp);
        ctx.lineTo(x, mid + amp);
        ctx.stroke();
      }
    } else {
      ctx.fillStyle = muted;
      ctx.fillText(waveformStatus || '波形', 12 * dpr, waveTop + waveH / 2);
    }

    drawHandle(ctx, startX, waveTop, waveH, primary, dpr);
    drawHandle(ctx, endX, waveTop, waveH, primary, dpr);
    const playX = (Math.max(0, Math.min(cutCurrentTime, duration)) / duration) * width;
    ctx.strokeStyle = fg;
    ctx.lineWidth = 2 * dpr;
    ctx.beginPath();
    ctx.moveTo(playX, rulerH);
    ctx.lineTo(playX, height);
    ctx.stroke();
  }

  function drawHandle(ctx: CanvasRenderingContext2D, x: number, y: number, h: number, color: string, dpr: number): void {
    ctx.fillStyle = color;
    ctx.fillRect(x - 2 * dpr, y - 4 * dpr, 4 * dpr, h + 8 * dpr);
    ctx.beginPath();
    ctx.arc(x, y - 1 * dpr, 5 * dpr, 0, Math.PI * 2);
    ctx.fill();
  }

  function colorMix(color: string, alpha: number): string {
    return `color-mix(in srgb, ${color} ${Math.round(alpha * 100)}%, transparent)`;
  }

  function timelineTime(event: PointerEvent): number {
    const canvas = cutTimelineCanvas;
    if (!canvas) return 0;
    const rect = canvas.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (event.clientX - rect.left) / Math.max(1, rect.width)));
    return ratio * Math.max(cutDuration, 0);
  }

  function startTimelineDrag(event: PointerEvent): void {
    if (!cutDuration || !cutTimelineCanvas) return;
    cutTimelineCanvas.setPointerCapture(event.pointerId);
    const time = timelineTime(event);
    const threshold = Math.max(0.25, cutDuration * 0.015);
    timelineDragMode = Math.abs(time - cutStart) <= threshold ? 'start' : Math.abs(time - cutEnd) <= threshold ? 'end' : 'playhead';
    moveTimelineDrag(event);
  }

  function moveTimelineDrag(event: PointerEvent): void {
    if (!timelineDragMode) return;
    const time = timelineTime(event);
    if (timelineDragMode === 'start') setCutStart(time);
    else if (timelineDragMode === 'end') setCutEnd(time);
    else {
      cutCurrentTime = time;
      if (cutVideoEl) cutVideoEl.currentTime = time;
      scheduleTimelineDraw();
    }
  }

  function endTimelineDrag(): void {
    timelineDragMode = '';
  }

  async function runBatchCut(): Promise<void> {
    if (!cutFile || cutSegments.length === 0 || cutStatus === 'loading') return;
    cutExportDialogOpen = false;
    cutStatus = 'loading';
    cutProgress = 0;
    cutMessage = '准备裁切';
    clearCutOutputs();
    const run = startRun();
    try {
      cutOutputs = await batchCutSegments(cutFile, cutSegments, cutMode, cutEncoding, progress => updateProgress('cut', progress), { signal: run.signal });
      cutStatus = 'done';
      cutProgress = 100;
      cutMessage = `${cutMode === 'precise' ? '精确' : '快速'}裁切完成：${cutOutputs.length} 段`;
    } catch (error) {
      cutStatus = 'error';
      cutMessage = run.signal.aborted ? '已取消' : toError(error).message || '裁切失败';
    } finally {
      finishRun(run);
    }
  }

  function normalizeEncoding<K extends keyof CutEncodingOptions>(encoding: CutEncodingOptions, key: K, value: CutEncodingOptions[K]): CutEncodingOptions {
    const next = { ...encoding, [key]: value };
    const beforeFormat = next.format;
    if (key === 'format') {
      if (value === 'webm') {
        next.video = next.video === 'copy' || next.video === 'none' ? next.video : 'vp9';
        next.audio = next.audio === 'copy' || next.audio === 'mute' ? next.audio : 'opus';
      }
      if (value === 'mp4') {
        next.video = next.video === 'copy' || next.video === 'none' ? next.video : 'h264';
        next.audio = next.audio === 'copy' || next.audio === 'mute' ? next.audio : 'aac';
      }
      if (value === 'mkv') {
        next.audio = next.audio === 'copy' || next.audio === 'mute' ? next.audio : 'aac';
      }
    }
    if (key === 'video' && value === 'vp9' && next.format === 'mp4') next.format = 'webm';
    if (key === 'video' && value === 'h265') {
      if (next.format === 'webm') next.format = 'mkv';
      next.preset = 'ultrafast';
    }
    if (!experimentalH265Enabled && next.video === 'h265') next.video = 'h264';
    if (key === 'audio' && value === 'opus' && next.format === 'mp4') {
      next.format = next.video === 'vp9' ? 'webm' : 'mkv';
    }
    if (next.video === 'h265' && next.format === 'webm') next.format = 'mkv';
    if (next.format === 'webm' && next.video !== 'vp9' && next.video !== 'copy' && next.video !== 'none') next.video = 'vp9';
    if (next.format === 'webm' && next.audio !== 'opus' && next.audio !== 'copy' && next.audio !== 'mute') next.audio = 'opus';
    if (beforeFormat !== next.format) {
      formatHint = `格式兼容：已切换为 ${next.format.toUpperCase()}`;
      const hint = formatHint;
      setTimeout(() => { if (formatHint === hint) formatHint = ''; }, 2400);
    }
    return next;
  }

  function setCutEncoding<K extends keyof CutEncodingOptions>(key: K, value: CutEncodingOptions[K]): void {
    cutEncoding = normalizeEncoding(cutEncoding, key, value);
  }

  function setEncodeEncoding<K extends keyof CutEncodingOptions>(key: K, value: CutEncodingOptions[K]): void {
    encodeEncoding = normalizeEncoding(encodeEncoding, key, value);
  }

  async function openCutExportDialog(): Promise<void> {
    if (!cutFile || cutSegments.length === 0 || busy) return;
    await ensureEncoderSupport();
    cutExportDialogOpen = true;
  }

  function closeCutExportDialog(): void {
    if (cutStatus === 'loading') return;
    cutExportDialogOpen = false;
  }

  async function openEncodeDialog(): Promise<void> {
    if (!encodeFile || busy) return;
    await ensureEncoderSupport();
    encodeDialogOpen = true;
  }

  function closeEncodeDialog(): void {
    if (encodeStatus === 'loading') return;
    encodeDialogOpen = false;
  }

  async function runEncode(): Promise<void> {
    if (!encodeFile || encodeStatus === 'loading') return;
    encodeDialogOpen = false;
    encodeStatus = 'loading';
    encodeProgress = 0;
    encodeMessage = '准备压制';
    revokeOutput(encodeOutput);
    encodeOutput = null;
    const run = startRun();
    try {
      encodeOutput = await transcodeVideo(encodeFile, encodeEncoding, progress => updateProgress('encode', progress), { signal: run.signal });
      encodeStatus = 'done';
      encodeProgress = 100;
      encodeMessage = `压制完成：${encodeOutput.name}`;
    } catch (error) {
      encodeStatus = 'error';
      encodeMessage = run.signal.aborted ? '已取消' : toError(error).message || '压制失败';
    } finally {
      finishRun(run);
    }
  }

  function openAnimatedDialog(): void {
    if (!animatedFile || animatedEnd - animatedStart <= 0.05 || busy) return;
    animatedDialogOpen = true;
  }

  function closeAnimatedDialog(): void {
    if (animatedStatus === 'loading') return;
    animatedDialogOpen = false;
  }

  function setAnimatedOption<K extends keyof AnimatedExportOptions>(key: K, value: AnimatedExportOptions[K]): void {
    animatedOptions = { ...animatedOptions, [key]: value };
  }

  async function runAnimatedExport(): Promise<void> {
    if (!animatedFile || animatedStatus === 'loading') return;
    animatedDialogOpen = false;
    animatedStatus = 'loading';
    animatedProgress = 0;
    animatedMessage = '准备导出';
    revokeOutput(animatedOutput);
    animatedOutput = null;
    const run = startRun();
    try {
      animatedOutput = await exportAnimatedImage(animatedFile, animatedStart, animatedEnd, animatedOptions, progress => updateProgress('animated', progress), { signal: run.signal });
      animatedStatus = 'done';
      animatedProgress = 100;
      animatedMessage = `动图导出完成：${animatedOutput.name}`;
    } catch (error) {
      animatedStatus = 'error';
      animatedMessage = run.signal.aborted ? '已取消' : toError(error).message || '导出失败';
    } finally {
      finishRun(run);
    }
  }

  function downloadBlob(blob: Blob, name: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    link.click();
    URL.revokeObjectURL(url);
  }

  async function downloadZip(files: ProcessedFile[], name: string): Promise<void> {
    if (files.length === 0) return;
    const { default: JSZip } = await import('jszip');
    const zip = new JSZip();
    for (const file of files) zip.file(file.name, file.blob);
    const blob = await zip.generateAsync({ type: 'blob' });
    downloadBlob(blob, name);
  }

  function formatTime(seconds: number): string {
    const safe = Math.max(0, seconds || 0);
    const minutes = Math.floor(safe / 60);
    const secs = Math.floor(safe % 60);
    const millis = Math.floor((safe % 1) * 1000);
    return `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}.${String(millis).padStart(3, '0')}`;
  }

  onDestroy(() => {
    if (frameTimer) clearTimeout(frameTimer);
    if (timelineFrame) cancelAnimationFrame(timelineFrame);
    activeRun?.abort();
    if (frameUrl) URL.revokeObjectURL(frameUrl);
    if (framePreviewUrl) URL.revokeObjectURL(framePreviewUrl);
    if (cutUrl) URL.revokeObjectURL(cutUrl);
    if (animatedUrl) URL.revokeObjectURL(animatedUrl);
    revokeOutput(muxOutput);
    revokeOutput(encodeOutput);
    revokeOutput(animatedOutput);
    clearSplitOutputs();
    clearCutOutputs();
  });
</script>

<svelte:head>
  <meta property="og:image" content="/icon.svg">
</svelte:head>

<header class="video-header">
  <div class="video-header-inner">
    <a href="/" class="back-link" aria-label="返回下载页"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg></a>
    <div>
      <h1>视频工具</h1>
    <p>裁切、压制、动图、提帧</p>
    </div>
    <a class="search-link" href="/search/">Wiki 搜索</a>
  </div>
</header>

<div class="video-app-layout">
  <aside class="sidebar">
    <nav>
      <button class="tab-btn" class:active={activeTab === 'cut'} onclick={() => activeTab = 'cut'}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3v18"/><path d="M18 3v18"/><path d="M8 7h8"/><path d="M8 17h8"/><path d="m10 12 4-3v6Z"/></svg>
        视频批量裁切
      </button>
      <button class="tab-btn" class:active={activeTab === 'encode'} onclick={() => activeTab = 'encode'}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h9"/><path d="M4 17h9"/><path d="m15 7 3 3-3 3"/><path d="m18 10H8"/><path d="m9 11-3 3 3 3"/><path d="M6 14h10"/></svg>
        格式转换与压制
      </button>
      <button class="tab-btn" class:active={activeTab === 'animated'} onclick={() => activeTab = 'animated'}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M8 9h.01"/><path d="M12 9h.01"/><path d="M16 9h.01"/><path d="M7 14h10"/></svg>
        动图导出
      </button>
      <button class="tab-btn" class:active={activeTab === 'frame'} onclick={() => activeTab = 'frame'}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="10" r="2"/><path d="m21 15-5-5L5 19"/></svg>
        提取视频帧
      </button>
      <button class="tab-btn" class:active={activeTab === 'track'} onclick={() => activeTab = 'track'}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16"/><path d="M4 17h16"/><path d="M8 3v18"/><path d="M16 3v18"/></svg>
        媒体轨道处理
      </button>
    </nav>
  </aside>

  <main class="active-tool-content">
    {#if encoderStatus === 'loading' || encoderStatus === 'error'}
      <section class="core-loader" class:error={encoderStatus === 'error'}>
        <div class="progress-head"><span>{encoderMessage || '加载视频核心'}</span>{#if encoderStatus === 'loading'}<strong>{coreProgress}%</strong>{/if}</div>
        {#if encoderStatus === 'loading'}<progress max="100" value={coreProgress}></progress>{/if}
      </section>
    {/if}

    {#if activeTab === 'cut'}
      <section class="tool-card cut-tool">
        <label class:dragging={dragTarget === 'cut'} class:warning={!!largeFileHint(cutFile)} class="file-picker" ondragover={(event) => handleDragOver(event, 'cut')} ondragleave={(event) => handleDragLeave(event, 'cut')} ondrop={(event) => dropFile(event, 'cut', setCutFile)}>
          <input type="file" accept="video/*,.m4s,.webm,.mp4,.mov" onchange={(event) => chooseFile(event, setCutFile)} disabled={busy}>
          <span>视频文件</span>
          <small title={fileTitle(cutFile, 'MP4 / WebM / M4S / MOV')}>{fileLabel(cutFile, 'MP4 / WebM / M4S / MOV')}</small>
        </label>
        {#if cutUrl}
          <!-- svelte-ignore a11y_media_has_caption -->
          <video class="cut-video" bind:this={cutVideoEl} src={cutUrl} controls preload="metadata" onloadedmetadata={handleCutVideoLoaded} ontimeupdate={updateCutPlayhead} onseeked={updateCutPlayhead}></video>
          <div class="waveform-shell">
            <canvas class="waveform-timeline" bind:this={cutTimelineCanvas} aria-label="波形时间轴" role="slider" aria-valuemin="0" aria-valuemax={Math.round(cutDuration)} aria-valuenow={Math.round(cutCurrentTime)} tabindex="0" onpointerdown={startTimelineDrag} onpointermove={moveTimelineDrag} onpointerup={endTimelineDrag} onpointercancel={endTimelineDrag}></canvas>
            <div class="timeline-readout">
              <span>{waveformStatus || '波形'}</span>
              <strong>{formatTime(cutCurrentTime)} / {formatTime(cutDuration)}</strong>
            </div>
          </div>
          <div class="clip-controls">
            <label><span>开始 {formatTime(cutStart)}</span><input type="range" min="0" max={Math.max(cutDuration, 1)} step="0.04" value={cutStart} oninput={(event) => setCutStart(Number((event.currentTarget as HTMLInputElement).value))}></label>
            <label><span>结束 {formatTime(cutEnd)}</span><input type="range" min="0" max={Math.max(cutDuration, 1)} step="0.04" value={cutEnd} oninput={(event) => setCutEnd(Number((event.currentTarget as HTMLInputElement).value))}></label>
          </div>
          <div class="clip-actions">
            <button class="secondary-action" type="button" onclick={addCutSegment} disabled={busy || cutEnd - cutStart <= 0.05}>添加片段</button>
            <button class="primary-action" type="button" onclick={openCutExportDialog} disabled={busy || cutSegments.length === 0}>导出片段</button>
          </div>
          {#if cutSegments.length > 0}
            <div class="clip-list">
              {#each cutSegments as segment, index (segment.id)}
                <div class="clip-item">
                  <button type="button" onclick={() => seekCutSegment(segment)}><strong>片段 {index + 1}</strong><span>{formatTime(segment.start)} - {formatTime(segment.end)} · {formatTime(segment.end - segment.start)}</span></button>
                  <button class="clip-remove" type="button" aria-label="删除片段" onclick={() => removeCutSegment(segment.id)}>×</button>
                </div>
              {/each}
            </div>
          {/if}
          {#if cutStatus !== 'idle' || cutMessage}{@render ProgressPanel(cutStatus, cutMessage, cutProgress, null, cutOutputs, () => clearOutputs('cut'))}{/if}
        {/if}
      </section>
    {:else if activeTab === 'encode'}
      <section class="tool-card encode-tool">
        <label class:dragging={dragTarget === 'encode'} class:warning={!!largeFileHint(encodeFile)} class="file-picker" ondragover={(event) => handleDragOver(event, 'encode')} ondragleave={(event) => handleDragLeave(event, 'encode')} ondrop={(event) => dropFile(event, 'encode', setEncodeFile)}>
          <input type="file" accept="video/*,.m4s,.webm,.mp4,.mov,.mkv" onchange={(event) => chooseFile(event, setEncodeFile)} disabled={busy}>
          <span>视频文件</span>
          <small title={fileTitle(encodeFile, 'MP4 / WebM / MKV / MOV / M4S')}>{fileLabel(encodeFile, 'MP4 / WebM / MKV / MOV / M4S')}</small>
        </label>
        <div class="tool-summary">
          <span>{encodeEncoding.format.toUpperCase()}</span>
          <span>{encodeEncoding.video === 'h264' ? 'H.264' : encodeEncoding.video === 'h265' ? 'H.265' : encodeEncoding.video === 'vp9' ? 'VP9' : encodeEncoding.video === 'copy' ? '复制' : '无视频'}</span>
          <span>{encodeEncoding.audio === 'aac' ? 'AAC' : encodeEncoding.audio === 'opus' ? 'Opus' : encodeEncoding.audio === 'copy' ? '复制' : '静音'}</span>
        </div>
        <button class="primary-action" type="button" onclick={openEncodeDialog} disabled={busy || !encodeFile}>导出设置</button>
        {#if encodeStatus !== 'idle' || encodeMessage}{@render ProgressPanel(encodeStatus, encodeMessage, encodeProgress, encodeOutput, [], () => clearSingleOutput('encode'))}{/if}
      </section>
    {:else if activeTab === 'animated'}
      <section class="tool-card animated-tool">
        <label class:dragging={dragTarget === 'animated'} class:warning={!!largeFileHint(animatedFile)} class="file-picker" ondragover={(event) => handleDragOver(event, 'animated')} ondragleave={(event) => handleDragLeave(event, 'animated')} ondrop={(event) => dropFile(event, 'animated', setAnimatedFile)}>
          <input type="file" accept="video/*,.m4s,.webm,.mp4,.mov,.mkv" onchange={(event) => chooseFile(event, setAnimatedFile)} disabled={busy}>
          <span>视频文件</span>
          <small title={fileTitle(animatedFile, 'MP4 / WebM / MKV / MOV / M4S')}>{fileLabel(animatedFile, 'MP4 / WebM / MKV / MOV / M4S')}</small>
        </label>
        {#if animatedUrl}
          <!-- svelte-ignore a11y_media_has_caption -->
          <video class="mini-video" bind:this={animatedVideoEl} src={animatedUrl} controls preload="metadata" onloadedmetadata={handleAnimatedLoaded}></video>
          <div class="clip-controls">
            <label><span>开始 {formatTime(animatedStart)}</span><input type="range" min="0" max={Math.max(animatedDuration, 1)} step="0.04" value={animatedStart} oninput={(event) => setAnimatedStart(Number((event.currentTarget as HTMLInputElement).value))}></label>
            <label><span>结束 {formatTime(animatedEnd)}</span><input type="range" min="0" max={Math.max(animatedDuration, 1)} step="0.04" value={animatedEnd} oninput={(event) => setAnimatedEnd(Number((event.currentTarget as HTMLInputElement).value))}></label>
          </div>
          <div class="tool-summary"><span>{animatedOptions.format.toUpperCase()}</span><span>{animatedOptions.width === 'source' ? '原始宽度' : `${animatedOptions.width}px`}</span><span>{animatedOptions.fps} fps</span></div>
          <button class="primary-action" type="button" onclick={openAnimatedDialog} disabled={busy || animatedEnd - animatedStart <= 0.05}>导出设置</button>
        {/if}
        {#if animatedStatus !== 'idle' || animatedMessage}{@render ProgressPanel(animatedStatus, animatedMessage, animatedProgress, animatedOutput, [], () => clearSingleOutput('animated'))}{/if}
      </section>
    {:else if activeTab === 'frame'}
      <section class="tool-card frame-tool">
        <label class:dragging={dragTarget === 'frame'} class="file-picker" ondragover={(event) => handleDragOver(event, 'frame')} ondragleave={(event) => handleDragLeave(event, 'frame')} ondrop={(event) => dropFile(event, 'frame', setFrameFile)}>
          <input type="file" accept="video/*,.m4s,.webm,.mp4,.mov" onchange={(event) => chooseFile(event, setFrameFile)} disabled={busy}>
          <span>视频文件</span>
          <small>{fileLabel(frameFile, 'MP4 / WebM / M4S / MOV')}</small>
        </label>
        {#if frameUrl}
          <div class="frame-preview">
            <video bind:this={videoEl} src={frameUrl} preload="metadata" muted playsinline onloadedmetadata={handleVideoLoaded} onseeked={updateFramePreview}></video>
            {#if framePreviewUrl}<img src={framePreviewUrl} alt="当前帧预览">{:else}<span>读取中</span>{/if}
          </div>
          <div class="timeline-row">
            <span>{formatTime(framePosition)}</span>
            <input type="range" min="0" max={Math.max(frameDuration, 1)} step="0.04" value={framePosition} oninput={(event) => seekFrame(Number((event.currentTarget as HTMLInputElement).value))}>
            <span>{formatTime(frameDuration)}</span>
          </div>
          <button class="primary-action" type="button" onclick={exportFrame} disabled={busy}>导出当前画面</button>
          <p class:danger={frameStatus === 'error'} class="status-line">{frameMessage}</p>
        {/if}
        <canvas bind:this={canvasEl} class="hidden-canvas" style="display: none;"></canvas>
      </section>
    {:else if activeTab === 'track'}
      <div class="track-panels-layout">
        <section class="tool-card track-card" aria-label="B站缓存音画合成">
          <div class="track-panel-header">
            <div class="track-panel-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            </div>
            <div class="track-panel-title">
              <h2>B站缓存合成</h2>
              <p>将独立的 video.m4s 和 audio.m4s 合并为完整视频</p>
            </div>
          </div>
          <div class="track-inputs">
            <label class:dragging={dragTarget === 'bili-video'} class="file-picker" ondragover={(event) => handleDragOver(event, 'bili-video')} ondragleave={(event) => handleDragLeave(event, 'bili-video')} ondrop={(event) => dropFile(event, 'bili-video', file => biliVideoFile = file)}>
              <input type="file" accept="video/*,.m4s" onchange={(event) => chooseFile(event, file => biliVideoFile = file)} disabled={busy}>
              <span>视频轨 (video.m4s)</span>
              <small>{fileLabel(biliVideoFile, '选择视频轨文件')}</small>
            </label>
            <label class:dragging={dragTarget === 'bili-audio'} class="file-picker" ondragover={(event) => handleDragOver(event, 'bili-audio')} ondragleave={(event) => handleDragLeave(event, 'bili-audio')} ondrop={(event) => dropFile(event, 'bili-audio', file => biliAudioFile = file)}>
              <input type="file" accept="audio/*,.m4s" onchange={(event) => chooseFile(event, file => biliAudioFile = file)} disabled={busy}>
              <span>音频轨 (audio.m4s)</span>
              <small>{fileLabel(biliAudioFile, '选择音频轨文件')}</small>
            </label>
          </div>
          <button class="primary-action w-full" type="button" onclick={runMux} disabled={busy || !biliVideoFile || !biliAudioFile}>开始合成</button>
          {#if muxStatus !== 'idle'}{@render ProgressPanel(muxStatus, muxMessage, muxProgress, muxOutput, [], () => clearSingleOutput('mux'))}{/if}
        </section>

        <section class="tool-card track-card" aria-label="音视频拆分">
          <div class="track-panel-header">
            <div class="track-panel-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12h8"/><path d="m8 8-4 4 4 4"/><path d="M20 12h-8"/><path d="m16 8 4 4-4 4"/></svg>
            </div>
            <div class="track-panel-title">
              <h2>音视频提取拆分</h2>
              <p>从媒体文件中分离提取独立的视频轨与音频轨</p>
            </div>
          </div>
          <div class="track-inputs">
            <label class:dragging={dragTarget === 'split'} class="file-picker" ondragover={(event) => handleDragOver(event, 'split')} ondragleave={(event) => handleDragLeave(event, 'split')} ondrop={(event) => dropFile(event, 'split', file => splitFile = file)}>
              <input type="file" accept="video/*,audio/*,.m4s,.webm,.mp4,.mov" onchange={(event) => chooseFile(event, file => splitFile = file)} disabled={busy}>
              <span>媒体文件</span>
              <small>{fileLabel(splitFile, 'MP4 / WebM / M4S / MOV')}</small>
            </label>
          </div>
          <button class="primary-action w-full" type="button" onclick={runSplit} disabled={busy || !splitFile}>开始拆分</button>
          {#if splitStatus !== 'idle'}{@render ProgressPanel(splitStatus, splitMessage, splitProgress, null, splitOutputs, () => clearOutputs('split'))}{/if}
        </section>
      </div>
    {/if}
  </main>
</div>

{#if cutExportDialogOpen}
  <div class="export-dialog" role="dialog" aria-modal="true" aria-label="导出设置">
    <button class="export-backdrop" type="button" aria-label="关闭导出设置" onclick={closeCutExportDialog}></button>
    <section class="export-panel">
      <div class="export-header">
        <div>
          <h2>导出设置</h2>
          <p>{cutSegments.length} 段 · {cutMode === 'precise' ? '精确' : '快速'}</p>
        </div>
        <button class="export-close" type="button" aria-label="关闭" onclick={closeCutExportDialog}>×</button>
      </div>
      {#if encoderMessage}<p class:danger={encoderStatus === 'error'} class="status-line">{encoderMessage}</p>{/if}
      <div class="mode-toggle" role="radiogroup" aria-label="裁切模式">
        <button class:active={cutMode === 'fast'} type="button" role="radio" aria-checked={cutMode === 'fast'} onclick={() => cutMode = 'fast'} disabled={busy}>
          <strong>快速裁切</strong>
          <span>关键帧切点</span>
        </button>
        <button class:active={cutMode === 'precise'} type="button" role="radio" aria-checked={cutMode === 'precise'} onclick={() => cutMode = 'precise'} disabled={busy}>
          <strong>精确裁切</strong>
          <span>按时间重编码</span>
        </button>
      </div>
      {#if cutMode === 'precise'}
        <div class="encoding-panel">
          <label>
            <span>输出格式</span>
            <CustomSelect value={cutEncoding.format} options={formatOptions} open={openSelect === 'cut-format'} disabled={busy} onToggle={() => toggleSelect('cut-format')} onSelect={selectValue<CutEncodingOptions['format']>('cut-format', value => setCutEncoding('format', value))} />
          </label>
          <label>
            <span>视频压制</span>
            <CustomSelect value={cutEncoding.video} options={videoOptions()} open={openSelect === 'cut-video'} disabled={busy} onToggle={() => toggleSelect('cut-video')} onSelect={selectValue<CutEncodingOptions['video']>('cut-video', value => setCutEncoding('video', value))} />
          </label>
          <label>
            <span>编码速度</span>
            <CustomSelect value={cutEncoding.preset} options={presetOptions} open={openSelect === 'cut-preset'} disabled={busy || cutEncoding.video === 'vp9' || cutEncoding.video === 'copy' || cutEncoding.video === 'none'} onToggle={() => toggleSelect('cut-preset')} onSelect={selectValue<CutEncodingOptions['preset']>('cut-preset', value => setCutEncoding('preset', value))} />
          </label>
          <label>
            <span>质量 CRF {cutEncoding.crf}</span>
            <input type="range" min="0" max="35" step="1" value={cutEncoding.crf} oninput={(event) => setCutEncoding('crf', Number((event.currentTarget as HTMLInputElement).value))} disabled={busy || cutEncoding.video === 'copy' || cutEncoding.video === 'none' || cutEncoding.videoBitrate !== 'auto'}>
          </label>
          <label>
            <span>视频码率</span>
            <CustomSelect value={cutEncoding.videoBitrate} options={videoBitrateOptions} open={openSelect === 'cut-video-bitrate'} disabled={busy || cutEncoding.video === 'copy' || cutEncoding.video === 'none'} onToggle={() => toggleSelect('cut-video-bitrate')} onSelect={selectValue('cut-video-bitrate', value => setCutEncoding('videoBitrate', value))} />
          </label>
          <label>
            <span>分辨率</span>
            <CustomSelect value={cutEncoding.scale} options={scaleOptions} open={openSelect === 'cut-scale'} disabled={busy || cutEncoding.video === 'copy' || cutEncoding.video === 'none'} onToggle={() => toggleSelect('cut-scale')} onSelect={selectValue<CutEncodingOptions['scale']>('cut-scale', value => setCutEncoding('scale', value))} />
          </label>
          <label>
            <span>帧率</span>
            <CustomSelect value={cutEncoding.fps} options={fpsOptions} open={openSelect === 'cut-fps'} disabled={busy || cutEncoding.video === 'copy' || cutEncoding.video === 'none'} onToggle={() => toggleSelect('cut-fps')} onSelect={selectValue<CutEncodingOptions['fps']>('cut-fps', value => setCutEncoding('fps', value))} />
          </label>
          <label>
            <span>音频处理</span>
            <CustomSelect value={cutEncoding.audio} options={audioOptions} open={openSelect === 'cut-audio'} disabled={busy} onToggle={() => toggleSelect('cut-audio')} onSelect={selectValue<CutEncodingOptions['audio']>('cut-audio', value => setCutEncoding('audio', value))} />
          </label>
          <label>
            <span>音频码率</span>
            <CustomSelect value={cutEncoding.audioBitrate} options={audioBitrateOptions} open={openSelect === 'cut-audio-bitrate'} disabled={busy || cutEncoding.audio === 'copy' || cutEncoding.audio === 'mute'} onToggle={() => toggleSelect('cut-audio-bitrate')} onSelect={selectValue('cut-audio-bitrate', value => setCutEncoding('audioBitrate', value))} />
          </label>
        </div>
      {/if}
      <div class="export-actions">
        <button class="secondary-action" type="button" onclick={closeCutExportDialog} disabled={busy}>取消</button>
        <button class="primary-action" type="button" onclick={runBatchCut} disabled={busy || cutSegments.length === 0}>开始导出</button>
      </div>
    </section>
  </div>
{/if}

{#if encodeDialogOpen}
  <div class="export-dialog" role="dialog" aria-modal="true" aria-label="压制设置">
    <button class="export-backdrop" type="button" aria-label="关闭压制设置" onclick={closeEncodeDialog}></button>
    <section class="export-panel">
      <div class="export-header">
        <div>
          <h2>压制设置</h2>
          <p>{encodeFile?.name || '未选择'}</p>
        </div>
        <button class="export-close" type="button" aria-label="关闭" onclick={closeEncodeDialog}>×</button>
      </div>
      {#if encoderMessage}<p class:danger={encoderStatus === 'error'} class="status-line">{encoderMessage}</p>{/if}
      <div class="encoding-panel">
        <label>
          <span>输出格式</span>
          <CustomSelect value={encodeEncoding.format} options={formatOptions} open={openSelect === 'encode-format'} disabled={busy} onToggle={() => toggleSelect('encode-format')} onSelect={selectValue<CutEncodingOptions['format']>('encode-format', value => setEncodeEncoding('format', value))} />
        </label>
        <label>
        <span>视频压制</span>
        <CustomSelect value={encodeEncoding.video} options={videoOptions()} open={openSelect === 'encode-video'} disabled={busy} onToggle={() => toggleSelect('encode-video')} onSelect={selectValue<CutEncodingOptions['video']>('encode-video', value => setEncodeEncoding('video', value))} />
      </label>
      <label>
        <span>编码速度</span>
        <CustomSelect value={encodeEncoding.preset} options={presetOptions} open={openSelect === 'encode-preset'} disabled={busy || encodeEncoding.video === 'vp9' || encodeEncoding.video === 'copy' || encodeEncoding.video === 'none'} onToggle={() => toggleSelect('encode-preset')} onSelect={selectValue<CutEncodingOptions['preset']>('encode-preset', value => setEncodeEncoding('preset', value))} />
        </label>
        <label>
          <span>质量 CRF {encodeEncoding.crf}</span>
          <input type="range" min="0" max="35" step="1" value={encodeEncoding.crf} oninput={(event) => setEncodeEncoding('crf', Number((event.currentTarget as HTMLInputElement).value))} disabled={busy || encodeEncoding.video === 'copy' || encodeEncoding.video === 'none' || encodeEncoding.videoBitrate !== 'auto'}>
        </label>
        <label>
          <span>视频码率</span>
          <CustomSelect value={encodeEncoding.videoBitrate} options={videoBitrateOptions} open={openSelect === 'encode-video-bitrate'} disabled={busy || encodeEncoding.video === 'copy' || encodeEncoding.video === 'none'} onToggle={() => toggleSelect('encode-video-bitrate')} onSelect={selectValue('encode-video-bitrate', value => setEncodeEncoding('videoBitrate', value))} />
        </label>
        <label>
          <span>分辨率</span>
          <CustomSelect value={encodeEncoding.scale} options={scaleOptions} open={openSelect === 'encode-scale'} disabled={busy || encodeEncoding.video === 'copy' || encodeEncoding.video === 'none'} onToggle={() => toggleSelect('encode-scale')} onSelect={selectValue<CutEncodingOptions['scale']>('encode-scale', value => setEncodeEncoding('scale', value))} />
        </label>
        <label>
          <span>帧率</span>
          <CustomSelect value={encodeEncoding.fps} options={fpsOptions} open={openSelect === 'encode-fps'} disabled={busy || encodeEncoding.video === 'copy' || encodeEncoding.video === 'none'} onToggle={() => toggleSelect('encode-fps')} onSelect={selectValue<CutEncodingOptions['fps']>('encode-fps', value => setEncodeEncoding('fps', value))} />
        </label>
        <label>
          <span>音频处理</span>
          <CustomSelect value={encodeEncoding.audio} options={audioOptions} open={openSelect === 'encode-audio'} disabled={busy} onToggle={() => toggleSelect('encode-audio')} onSelect={selectValue<CutEncodingOptions['audio']>('encode-audio', value => setEncodeEncoding('audio', value))} />
        </label>
        <label>
          <span>音频码率</span>
          <CustomSelect value={encodeEncoding.audioBitrate} options={audioBitrateOptions} open={openSelect === 'encode-audio-bitrate'} disabled={busy || encodeEncoding.audio === 'copy' || encodeEncoding.audio === 'mute'} onToggle={() => toggleSelect('encode-audio-bitrate')} onSelect={selectValue('encode-audio-bitrate', value => setEncodeEncoding('audioBitrate', value))} />
        </label>
      </div>
      <div class="export-actions">
        <button class="secondary-action" type="button" onclick={closeEncodeDialog} disabled={busy}>取消</button>
        <button class="primary-action" type="button" onclick={runEncode} disabled={busy || !encodeFile}>开始压制</button>
      </div>
    </section>
  </div>
{/if}

{#if animatedDialogOpen}
  <div class="export-dialog" role="dialog" aria-modal="true" aria-label="动图设置">
    <button class="export-backdrop" type="button" aria-label="关闭动图设置" onclick={closeAnimatedDialog}></button>
    <section class="export-panel">
      <div class="export-header">
        <div>
          <h2>动图设置</h2>
          <p>{formatTime(animatedStart)} - {formatTime(animatedEnd)} · {formatTime(animatedEnd - animatedStart)}</p>
        </div>
        <button class="export-close" type="button" aria-label="关闭" onclick={closeAnimatedDialog}>×</button>
      </div>
      <div class="encoding-panel animated-options">
        <label>
          <span>输出格式</span>
          <CustomSelect value={animatedOptions.format} options={animatedFormatOptions} open={openSelect === 'animated-format'} disabled={busy} onToggle={() => toggleSelect('animated-format')} onSelect={selectValue<AnimatedExportOptions['format']>('animated-format', value => setAnimatedOption('format', value))} />
        </label>
        <label>
          <span>宽度</span>
          <CustomSelect value={animatedOptions.width} options={animatedWidthOptions} open={openSelect === 'animated-width'} disabled={busy} onToggle={() => toggleSelect('animated-width')} onSelect={selectValue<AnimatedExportOptions['width']>('animated-width', value => setAnimatedOption('width', value))} />
        </label>
        <label>
          <span>帧率</span>
          <CustomSelect value={animatedOptions.fps} options={animatedFpsOptions} open={openSelect === 'animated-fps'} disabled={busy} onToggle={() => toggleSelect('animated-fps')} onSelect={selectValue<AnimatedExportOptions['fps']>('animated-fps', value => setAnimatedOption('fps', value))} />
        </label>
        <label>
          <span>质量 {animatedOptions.quality}</span>
          <input type="range" min="1" max="100" step="1" value={animatedOptions.quality} oninput={(event) => setAnimatedOption('quality', Number((event.currentTarget as HTMLInputElement).value))} disabled={busy || animatedOptions.format === 'gif'}>
        </label>
        <label>
          <span>抖动</span>
          <CustomSelect value={animatedOptions.dither} options={ditherOptions} open={openSelect === 'animated-dither'} disabled={busy || animatedOptions.format !== 'gif'} onToggle={() => toggleSelect('animated-dither')} onSelect={selectValue<AnimatedExportOptions['dither']>('animated-dither', value => setAnimatedOption('dither', value))} />
        </label>
        <label>
          <span>调色板</span>
          <CustomSelect value={animatedOptions.statsMode} options={statsModeOptions} open={openSelect === 'animated-stats'} disabled={busy || animatedOptions.format !== 'gif'} onToggle={() => toggleSelect('animated-stats')} onSelect={selectValue<AnimatedExportOptions['statsMode']>('animated-stats', value => setAnimatedOption('statsMode', value))} />
        </label>
        <label class="checkbox-option">
          <span>循环播放</span>
          <input type="checkbox" checked={animatedOptions.loop} onchange={(event) => setAnimatedOption('loop', (event.currentTarget as HTMLInputElement).checked)} disabled={busy}>
        </label>
      </div>
      <div class="export-actions">
        <button class="secondary-action" type="button" onclick={closeAnimatedDialog} disabled={busy}>取消</button>
        <button class="primary-action" type="button" onclick={runAnimatedExport} disabled={busy || !animatedFile}>开始导出</button>
      </div>
    </section>
  </div>
{/if}

{#if formatHint}
  <div class="format-toast" role="status" aria-live="polite">{formatHint}</div>
{/if}

{#snippet ProgressPanel(status: TaskStatus, message: string, progress: number, file: ProcessedFile | null = null, files: ProcessedFile[] = [], onClear: (() => void) | null = null)}
  <div class="progress-panel" class:error={status === 'error'} class:done={status === 'done'}>
    <div class="progress-head"><span>{message}</span>{#if status === 'loading'}<strong>{progress}%</strong>{/if}</div>
    {#if status === 'loading'}<progress max="100" value={progress}></progress><button class="clear-result" type="button" onclick={cancelRun}>取消</button>{/if}
    {#if file}<a class="download-link" href={file.url} download={file.name}>下载 {file.name}<small>{formatFileSize(file.blob.size)}</small></a>{/if}
    {#each files as item (item.url)}<a class="download-link" href={item.url} download={item.name}>下载 {item.name}<small>{formatFileSize(item.blob.size)}</small></a>{/each}
    {#if files.length > 1}<button class="clear-result" type="button" onclick={() => downloadZip(files, 'video-results.zip')}>打包 ZIP</button>{/if}
    {#if onClear && (file || files.length > 0 || status === 'error')}<button class="clear-result" type="button" onclick={onClear}>清除结果</button>{/if}
  </div>
{/snippet}
