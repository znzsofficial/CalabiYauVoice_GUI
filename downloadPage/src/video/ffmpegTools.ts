import { FFmpeg } from '@ffmpeg/ffmpeg';
import { fetchFile } from '@ffmpeg/util';

export type FfmpegProgress = { progress: number; message: string };
export type FfmpegRunOptions = { signal?: AbortSignal };
export type ProcessedFile = { name: string; blob: Blob; url: string };
export type SplitResult = { files: ProcessedFile[]; videoSamples: number; audioSamples: number };
export type ClipSegment = { id: number; start: number; end: number };
export type CutMode = 'fast' | 'precise';
export type EncoderSupport = { h264: boolean; h265: boolean; vp9: boolean };
export type CutEncodingOptions = {
  format: 'mp4' | 'webm' | 'mkv';
  video: 'h264' | 'h265' | 'vp9' | 'copy' | 'none';
  preset: 'ultrafast' | 'superfast' | 'veryfast' | 'faster' | 'fast' | 'medium' | 'slow';
  crf: number;
  videoBitrate: string;
  audioBitrate: string;
  scale: 'source' | '1080' | '720' | '480';
  fps: 'source' | '60' | '30' | '24';
  audio: 'aac' | 'opus' | 'copy' | 'mute';
};
export type AnimatedExportOptions = {
  format: 'gif' | 'webp';
  width: 'source' | '720' | '540' | '360' | '240';
  fps: '24' | '15' | '12' | '10' | '8';
  quality: number;
  loop: boolean;
  dither: 'sierra2_4a' | 'bayer' | 'floyd_steinberg';
  statsMode: 'full' | 'diff';
};

const coreBaseUrls = [
  import.meta.env.VITE_FFMPEG_CORE_BASE,
  'https://cdn.jsdelivr.net/npm/@ffmpeg/core@0.12.10/dist/esm',
  'https://fastly.jsdelivr.net/npm/@ffmpeg/core@0.12.10/dist/esm',
  'https://gcore.jsdelivr.net/npm/@ffmpeg/core@0.12.10/dist/esm',
  'https://unpkg.com/@ffmpeg/core@0.12.10/dist/esm',
  'https://unpkg.com/@ffmpeg/core@0.12.9/dist/esm'
].filter(Boolean) as string[];
const MAX_LOAD_RETRIES = 3;
export const experimentalH265Enabled = import.meta.env.VITE_ENABLE_EXPERIMENTAL_H265 === 'true';
let ffmpeg: FFmpeg | null = null;
let loading: Promise<FFmpeg> | null = null;
let operationQueue = Promise.resolve();
let activeProgress: (progress: FfmpegProgress) => void = () => {};
let encoderSupport: EncoderSupport | null = null;

function safeName(name: string, fallback: string): string {
  const cleaned = name.replace(/[\\/:*?"<>|]+/g, '_').trim();
  return cleaned || fallback;
}

function baseName(file: File): string {
  return safeName(file.name.replace(/\.[^.]+$/, ''), 'video');
}

function encodingLabel(options: CutEncodingOptions): string {
  if (options.video === 'copy') return 'copy';
  if (options.video === 'none') return 'audio';
  return options.video;
}

function scaleLabel(scale: CutEncodingOptions['scale'] | AnimatedExportOptions['width']): string {
  return scale === 'source' ? 'source' : `${scale}p`;
}

function timeLabel(seconds: number): string {
  return `${Math.floor(seconds / 60)}m${String(Math.floor(seconds % 60)).padStart(2, '0')}s`;
}

function countSamples(log: string, pattern: RegExp): number {
  return [...log.matchAll(pattern)].reduce((total, match) => total + Number(match[1] || 0), 0);
}

function makeFile(name: string, data: Awaited<ReturnType<FFmpeg['readFile']>>, type: string): ProcessedFile {
  const blobPart = typeof data === 'string' ? data : (() => {
    const copy = new Uint8Array(data.byteLength);
    copy.set(data);
    return copy.buffer;
  })();
  const blob = new Blob([blobPart], { type });
  return { name, blob, url: URL.createObjectURL(blob) };
}

async function removeIfExists(instance: FFmpeg, name: string): Promise<void> {
  try {
    await instance.deleteFile(name);
  } catch {
    // Ignore stale file cleanup failures inside ffmpeg's virtual FS.
  }
}

async function runFfmpegOperation<T>(task: () => Promise<T>): Promise<T> {
  const run = operationQueue.then(task, task);
  operationQueue = run.then(() => undefined, () => undefined);
  return run;
}

function attachFfmpegEvents(instance: FFmpeg): void {
  instance.on('progress', ({ progress }) => {
    if (Number.isFinite(progress)) activeProgress({ progress: Math.max(0, Math.min(1, progress)), message: '处理中' });
  });
  instance.on('log', ({ message }) => {
    if (message) activeProgress({ progress: 0, message });
  });
}

function sourceLabel(baseUrl: string): string {
  try {
    return new URL(baseUrl).hostname;
  } catch {
    return baseUrl;
  }
}

async function loadCoreWithRetry(onProgress: (progress: FfmpegProgress) => void): Promise<FFmpeg> {
  let lastError: unknown;
  const failures: string[] = [];
  for (let attempt = 1; attempt <= MAX_LOAD_RETRIES; attempt++) {
    for (const baseUrl of coreBaseUrls) {
      const candidate = new FFmpeg();
      attachFfmpegEvents(candidate);
      try {
        const source = sourceLabel(baseUrl);
        onProgress({ progress: 0, message: attempt > 1 ? `重试加载 ${source} (${attempt}/${MAX_LOAD_RETRIES})` : `加载视频核心 ${source}` });
        const loaded = { js: 0, wasm: 0 };
        const updateLoadProgress = (kind: 'js' | 'wasm', received: number, total: number): void => {
          loaded[kind] = total > 0 ? Math.min(1, received / total) : loaded[kind];
          onProgress({ progress: (loaded.js * 0.15) + (loaded.wasm * 0.75), message: `加载视频核心 ${source}` });
        };
        const [coreURL, wasmURL] = await Promise.all([
          toBlobUrlWithProgress(`${baseUrl}/ffmpeg-core.js`, 'text/javascript', (received, total) => updateLoadProgress('js', received, total)),
          toBlobUrlWithProgress(`${baseUrl}/ffmpeg-core.wasm`, 'application/wasm', (received, total) => updateLoadProgress('wasm', received, total))
        ]);
        onProgress({ progress: 0.92, message: `初始化视频核心 ${source}` });
        await candidate.load({ coreURL, wasmURL });
        return candidate;
      } catch (error) {
        lastError = error;
        const message = error instanceof Error ? error.message : String(error);
        failures.push(`${sourceLabel(baseUrl)}: ${message}`);
        onProgress({ progress: 0, message: `核心加载失败 ${sourceLabel(baseUrl)}` });
        candidate.terminate();
      }
    }
      if (attempt < MAX_LOAD_RETRIES) {
        onProgress({ progress: 0, message: `加载失败，${attempt * 2} 秒后重试` });
        await new Promise(resolve => setTimeout(resolve, attempt * 2000));
      }
  }
  const detail = failures.slice(-3).join(' / ');
  throw lastError instanceof Error ? new Error(`核心加载失败：${detail || lastError.message}`) : new Error(`核心加载失败：${detail || '未知错误'}`);
}

async function toBlobUrlWithProgress(url: string, mimeType: string, onDownload: (received: number, total: number) => void): Promise<string> {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText || 'request failed'}`);
  const total = Number(response.headers.get('content-length') || 0);
  const reader = response.body?.getReader();
  if (!reader) {
    const buffer = await response.arrayBuffer();
    onDownload(buffer.byteLength, buffer.byteLength);
    return URL.createObjectURL(new Blob([buffer], { type: mimeType }));
  }

  const chunks: Uint8Array[] = [];
  let received = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    if (!value) continue;
    chunks.push(value);
    received += value.byteLength;
    onDownload(received, total);
  }
  const data = new Uint8Array(received);
  let offset = 0;
  for (const chunk of chunks) {
    data.set(chunk, offset);
    offset += chunk.byteLength;
  }
  onDownload(received, total || received);
  return URL.createObjectURL(new Blob([data], { type: mimeType }));
}

export async function getFfmpeg(onProgress: (progress: FfmpegProgress) => void): Promise<FFmpeg> {
  activeProgress = onProgress;
  if (ffmpeg?.loaded) return ffmpeg;
  if (loading) return loading;

  loading = loadCoreWithRetry(onProgress).then(instance => {
    ffmpeg = instance;
    onProgress({ progress: 1, message: '核心就绪' });
    return instance;
  });

  try {
    return await loading;
  } finally {
    loading = null;
  }
}

export function terminateFfmpeg(): void {
  ffmpeg?.terminate();
  ffmpeg = null;
  loading = null;
  encoderSupport = null;
  operationQueue = Promise.resolve();
}

export async function detectEncoderSupport(onProgress: (progress: FfmpegProgress) => void): Promise<EncoderSupport> {
  if (encoderSupport) return encoderSupport;
  return runFfmpegOperation(async () => {
    if (encoderSupport) return encoderSupport;
    const instance = await getFfmpeg(onProgress);
    let logs = '';
    const logHandler = ({ message }: { message: string }) => {
      logs += `${message}\n`;
    };
    instance.on('log', logHandler);
    try {
      onProgress({ progress: 0, message: '检测编码器' });
      await instance.exec(['-hide_banner', '-encoders']);
      encoderSupport = {
        h264: /libx264/i.test(logs),
        h265: experimentalH265Enabled && /libx265/i.test(logs),
        vp9: /libvpx-vp9/i.test(logs)
      };
      return encoderSupport;
    } finally {
      instance.off('log', logHandler);
    }
  });
}

export async function extractAudioForWaveform(file: File): Promise<ArrayBuffer | null> {
  return runFfmpegOperation(async () => {
    let instance: FFmpeg | null = null;
    const input = 'waveform-input';
    const output = 'waveform-output.wav';
    try {
      instance = await getFfmpeg(() => {});
      await removeIfExists(instance, input);
      await removeIfExists(instance, output);
      await instance.writeFile(input, await fetchFile(file));
      const code = await instance.exec(['-i', input, '-vn', '-ac', '1', '-ar', '16000', '-f', 'wav', output]);
      if (code !== 0) return null;
      const data = await instance.readFile(output);
      if (typeof data === 'string') return null;
      const copy = new Uint8Array(data.byteLength);
      copy.set(data);
      return copy.buffer;
    } catch {
      return null;
    } finally {
      if (instance) {
        await removeIfExists(instance, input);
        await removeIfExists(instance, output);
      }
    }
  });
}

export async function muxBilibiliCache(video: File, audio: File, onProgress: (progress: FfmpegProgress) => void, options: FfmpegRunOptions = {}): Promise<ProcessedFile> {
  return runFfmpegOperation(async () => {
  const instance = await getFfmpeg(onProgress);
  const inputVideo = 'input-video.m4s';
  const inputAudio = 'input-audio.m4s';
  const output = `${baseName(video).replace(/(^|[_-])video($|[_-])/i, '_') || 'bilibili-cache'}.mp4`;

  try {
    await removeIfExists(instance, inputVideo);
    await removeIfExists(instance, inputAudio);
    await removeIfExists(instance, output);
    onProgress({ progress: 0, message: '正在读取文件' });
    await instance.writeFile(inputVideo, await fetchFile(video));
    await instance.writeFile(inputAudio, await fetchFile(audio));
    await instance.exec(['-i', inputVideo, '-i', inputAudio, '-map', '0:v:0', '-map', '1:a:0', '-c', 'copy', '-movflags', '+faststart', output], undefined, { signal: options.signal });
    const data = await instance.readFile(output);
    return makeFile(output, data, 'video/mp4');
  } finally {
    await removeIfExists(instance, inputVideo);
    await removeIfExists(instance, inputAudio);
    await removeIfExists(instance, output);
  }
  });
}

export async function splitMediaTracks(file: File, onProgress: (progress: FfmpegProgress) => void, options: FfmpegRunOptions = {}): Promise<SplitResult> {
  return runFfmpegOperation(async () => {
  const instance = await getFfmpeg(onProgress);
  const input = 'split-input';
  const videoOut = `${baseName(file)}_video.mp4`;
  const audioOut = `${baseName(file)}_audio.m4a`;
  let logs = '';

  const logHandler = ({ message }: { message: string }) => {
    logs += `${message}\n`;
  };
  instance.on('log', logHandler);

  try {
    await removeIfExists(instance, input);
    await removeIfExists(instance, videoOut);
    await removeIfExists(instance, audioOut);
    onProgress({ progress: 0, message: '正在读取文件' });
    await instance.writeFile(input, await fetchFile(file));

    const files: ProcessedFile[] = [];
    const videoCode = await instance.exec(['-i', input, '-map', '0:v:0', '-c', 'copy', '-an', videoOut], undefined, { signal: options.signal });
    if (videoCode === 0) {
      const videoData = await instance.readFile(videoOut);
      files.push(makeFile(videoOut, videoData, 'video/mp4'));
    }

    const audioCode = await instance.exec(['-i', input, '-map', '0:a:0', '-c', 'copy', '-vn', audioOut], undefined, { signal: options.signal });
    if (audioCode === 0) {
      const audioData = await instance.readFile(audioOut);
      files.push(makeFile(audioOut, audioData, 'audio/mp4'));
    }

    if (files.length === 0) throw new Error('未找到可拆分的视频或音频轨道');
    return {
      files,
      videoSamples: countSamples(logs, /video:[^\n]*?(\d+)\s+packets/gi),
      audioSamples: countSamples(logs, /audio:[^\n]*?(\d+)\s+packets/gi)
    };
  } finally {
    instance.off('log', logHandler);
    await removeIfExists(instance, input);
    await removeIfExists(instance, videoOut);
    await removeIfExists(instance, audioOut);
  }
  });
}

export async function batchCutSegments(file: File, segments: ClipSegment[], mode: CutMode, options: CutEncodingOptions, onProgress: (progress: FfmpegProgress) => void, runOptions: FfmpegRunOptions = {}): Promise<ProcessedFile[]> {
  return runFfmpegOperation(async () => {
  const instance = await getFfmpeg(onProgress);
  const input = 'cut-input';
  const outputFiles: ProcessedFile[] = [];
  const cleanSegments = segments
    .map(segment => ({ ...segment, start: Math.max(0, segment.start), end: Math.max(0, segment.end) }))
    .filter(segment => segment.end - segment.start > 0.05)
    .sort((a, b) => a.start - b.start);

  if (cleanSegments.length === 0) throw new Error('没有可裁切的片段');

  try {
    await removeIfExists(instance, input);
    onProgress({ progress: 0, message: '正在读取文件' });
    await instance.writeFile(input, await fetchFile(file));

    for (const [index, segment] of cleanSegments.entries()) {
      const extension = mode === 'precise' ? options.format : 'mp4';
      const output = `${baseName(file)}_${mode === 'precise' ? encodingLabel(options) : 'fast'}_${timeLabel(segment.start)}-${timeLabel(segment.end)}_clip_${String(index + 1).padStart(2, '0')}.${extension}`;
      await removeIfExists(instance, output);
      onProgress({ progress: index / cleanSegments.length, message: `${mode === 'precise' ? '精确裁切' : '快速裁切'} ${index + 1}/${cleanSegments.length}` });
      const copyArgs = [
        '-ss', segment.start.toFixed(3),
        '-to', segment.end.toFixed(3),
        '-i', input,
        '-map', '0',
        '-c', 'copy',
        '-avoid_negative_ts', 'make_zero',
        output
      ];
      const preciseArgs = [
        '-ss', segment.start.toFixed(3),
        '-to', segment.end.toFixed(3),
        '-i', input,
        '-map', '0',
        ...videoArgs(options),
        ...videoFilterArgs(options),
        ...audioArgs(options),
        ...containerArgs(options),
        output
      ];
      try {
        const code = await instance.exec(mode === 'precise' ? preciseArgs : copyArgs, undefined, { signal: runOptions.signal });
        if (code !== 0) throw new Error(`片段 ${index + 1} 失败`);
        const data = await instance.readFile(output);
        outputFiles.push(makeFile(output, data, mode === 'precise' ? outputMime(options.format) : 'video/mp4'));
      } finally {
        await removeIfExists(instance, output);
      }
    }
  } finally {
    await removeIfExists(instance, input);
  }

    onProgress({ progress: 1, message: '裁切完成' });
  return outputFiles;
  });
}

export async function transcodeVideo(file: File, options: CutEncodingOptions, onProgress: (progress: FfmpegProgress) => void, runOptions: FfmpegRunOptions = {}): Promise<ProcessedFile> {
  return runFfmpegOperation(async () => {
  const instance = await getFfmpeg(onProgress);
  const input = 'transcode-input';
  const output = `${baseName(file)}_${encodingLabel(options)}_${scaleLabel(options.scale)}.${options.format}`;
  try {
    await removeIfExists(instance, input);
    await removeIfExists(instance, output);
    onProgress({ progress: 0, message: '正在读取文件' });
    await instance.writeFile(input, await fetchFile(file));

    const code = await instance.exec([
      '-i', input,
      '-map', '0',
        ...videoArgs(options),
        ...videoFilterArgs(options),
        ...audioArgs(options),
        ...containerArgs(options),
      output
    ], undefined, { signal: runOptions.signal });
    if (code !== 0) throw new Error('压制失败');
    const data = await instance.readFile(output);
    return makeFile(output, data, outputMime(options.format));
  } finally {
    await removeIfExists(instance, input);
    await removeIfExists(instance, output);
  }
  });
}

export async function exportAnimatedImage(file: File, start: number, end: number, options: AnimatedExportOptions, onProgress: (progress: FfmpegProgress) => void, runOptions: FfmpegRunOptions = {}): Promise<ProcessedFile> {
  return runFfmpegOperation(async () => {
  const instance = await getFfmpeg(onProgress);
  const input = 'animated-input';
  const output = `${baseName(file)}_${options.width === 'source' ? 'source' : `${options.width}px`}_${options.fps}fps_${timeLabel(start)}-${timeLabel(end)}.${options.format}`;
  const duration = Math.max(0.1, end - start);
  try {
    await removeIfExists(instance, input);
    await removeIfExists(instance, output);
    onProgress({ progress: 0, message: '读取文件' });
    await instance.writeFile(input, await fetchFile(file));

    if (options.format === 'gif') {
      const palette = 'animated-palette.png';
      try {
        await removeIfExists(instance, palette);
        onProgress({ progress: 0.1, message: '生成调色板' });
        const paletteCode = await instance.exec([
          '-ss', start.toFixed(3),
          '-t', duration.toFixed(3),
          '-i', input,
          '-vf', gifFilterChain(options, 'palettegen'),
          palette
        ], undefined, { signal: runOptions.signal });
        if (paletteCode !== 0) throw new Error('调色板生成失败');
        onProgress({ progress: 0.5, message: '合成 GIF' });
        const gifCode = await instance.exec([
          '-ss', start.toFixed(3),
          '-t', duration.toFixed(3),
          '-i', input,
          '-i', palette,
          '-lavfi', `${gifFilterChain(options, 'scale')}[x];[x][1:v]paletteuse=dither=${options.dither}`,
          '-loop', options.loop ? '0' : '1',
          output
        ], undefined, { signal: runOptions.signal });
        if (gifCode !== 0) throw new Error('GIF 合成失败');
      } finally {
        await removeIfExists(instance, palette);
      }
    } else {
      onProgress({ progress: 0.2, message: '编码 WebP' });
      const code = await instance.exec([
        '-ss', start.toFixed(3),
        '-t', duration.toFixed(3),
        '-i', input,
        ...webpArgs(options),
        output
      ], undefined, { signal: runOptions.signal });
      if (code !== 0) throw new Error('导出失败');
    }
    onProgress({ progress: 0.9, message: '读取输出' });
    const data = await instance.readFile(output);
    return makeFile(output, data, options.format === 'gif' ? 'image/gif' : 'image/webp');
  } finally {
    await removeIfExists(instance, input);
    await removeIfExists(instance, output);
  }
  });
}

function gifFilterChain(options: AnimatedExportOptions, mode: 'palettegen' | 'scale'): string {
  const scale = options.width === 'source' ? 'scale=iw:ih' : `scale=${options.width}:-2:flags=lanczos`;
  if (mode === 'palettegen') return `fps=${options.fps},${scale},palettegen=stats_mode=${options.statsMode}`;
  return `fps=${options.fps},${scale}`;
}

function webpArgs(options: AnimatedExportOptions): string[] {
  const scale = options.width === 'source' ? 'scale=iw:ih' : `scale=${options.width}:-2`;
  return [
    '-vf', `fps=${options.fps},${scale}`,
    '-vsync', '0',
    '-an',
    '-c:v', 'libwebp',
    '-quality', String(Math.max(1, Math.min(100, Math.round(options.quality)))),
    '-loop', options.loop ? '0' : '1',
    '-f', 'webp'
  ];
}

function videoArgs(options: CutEncodingOptions): string[] {
  if (options.video === 'none') return ['-vn'];
  if (options.video === 'copy') return ['-c:v', 'copy'];
  const quality = options.videoBitrate === 'auto'
    ? ['-crf', String(Math.max(0, Math.min(51, Math.round(options.crf))))]
    : ['-b:v', options.videoBitrate];
  if (options.video === 'vp9') return ['-c:v', 'libvpx-vp9', ...quality];
  if (options.video === 'h265') return ['-c:v', 'libx265', '-preset', options.preset, '-threads', '1', '-x265-params', 'pools=none:wpp=0', ...quality];
  return ['-c:v', 'libx264', '-preset', options.preset, ...quality];
}

function videoFilterArgs(options: CutEncodingOptions): string[] {
  if (options.video === 'copy' || options.video === 'none') return [];
  const filters: string[] = [];
  if (options.scale !== 'source') filters.push(`scale=-2:${options.scale}`);
  if (options.fps !== 'source') filters.push(`fps=${options.fps}`);
  return filters.length > 0 ? ['-vf', filters.join(',')] : [];
}

function audioArgs(options: CutEncodingOptions): string[] {
  if (options.audio === 'mute') return ['-an'];
  if (options.audio === 'copy') return ['-c:a', 'copy'];
  if (options.audio === 'opus') return ['-c:a', 'libopus', '-b:a', options.audioBitrate];
  return ['-c:a', 'aac', '-b:a', options.audioBitrate];
}

function containerArgs(options: CutEncodingOptions): string[] {
  if (options.format !== 'mp4') return [];
  return options.video === 'h265' ? ['-tag:v', 'hvc1', '-movflags', '+faststart'] : ['-movflags', '+faststart'];
}

function outputMime(format: CutEncodingOptions['format']): string {
  if (format === 'webm') return 'video/webm';
  if (format === 'mkv') return 'video/x-matroska';
  return 'video/mp4';
}
