import type { VoiceLine } from '../searchApi';
import type { generateZip } from '../download';

type LangKey = 'cn' | 'jp' | 'en';
type SubtitleMode = 'merged' | 'perLine' | 'none';
type SubtitleFormat = 'full' | 'plain';
type FolderMode = 'none' | 'lang' | 'category' | 'chapter' | 'both';
export type AudioExportFormat = 'mp3' | 'wav';
export type WavBitDepth = 16 | 24;

const langLabels: Record<LangKey, string> = { cn: '中文', jp: '日文', en: '英文' };

function safeCategory(text: string): string {
  if (!text) return 'other';
  return text.replace(/[<>:"/\\|?*]/g, '').replace(/\s+/g, '_').slice(0, 80) || 'other';
}

export function buildFolderPath(folderMode: FolderMode, langKey: LangKey, category: string, filename: string, chapter?: string): string {
  const parts: string[] = [];
  if (folderMode === 'lang' || folderMode === 'both') parts.push(langLabels[langKey]);
  if (folderMode === 'category' || folderMode === 'both') parts.push(safeCategory(category));
  if (folderMode === 'chapter' && chapter) parts.push(safeCategory(chapter));
  parts.push(filename);
  return parts.join('/');
}

/** Decode MP3 (or other browser-supported audio) to AudioBuffer. Sample rate is the decoded native rate. */
export async function decodeAudioData(data: ArrayBuffer): Promise<AudioBuffer> {
  const AC = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
  const ctx = new AC();
  try {
    // decodeAudioData may detach the buffer; always pass a copy
    return await ctx.decodeAudioData(data.slice(0));
  } finally {
    await ctx.close().catch(() => {});
  }
}

function writeString(view: DataView, offset: number, text: string): void {
  for (let i = 0; i < text.length; i++) view.setUint8(offset + i, text.charCodeAt(i));
}

function floatToIntSample(sample: number, bitDepth: WavBitDepth): number {
  const s = Math.max(-1, Math.min(1, sample));
  if (bitDepth === 16) return s < 0 ? Math.round(s * 0x8000) : Math.round(s * 0x7fff);
  // 24-bit
  return s < 0 ? Math.round(s * 0x800000) : Math.round(s * 0x7fffff);
}

/** Encode AudioBuffer as PCM WAV. Uses buffer.sampleRate (native decoded rate). */
export function audioBufferToWav(buffer: AudioBuffer, bitDepth: WavBitDepth = 16): ArrayBuffer {
  const numChannels = buffer.numberOfChannels;
  const sampleRate = buffer.sampleRate;
  const numSamples = buffer.length;
  const bytesPerSample = bitDepth / 8;
  const blockAlign = numChannels * bytesPerSample;
  const dataSize = numSamples * blockAlign;
  const ab = new ArrayBuffer(44 + dataSize);
  const view = new DataView(ab);

  writeString(view, 0, 'RIFF');
  view.setUint32(4, 36 + dataSize, true);
  writeString(view, 8, 'WAVE');
  writeString(view, 12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, numChannels, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * blockAlign, true);
  view.setUint16(32, blockAlign, true);
  view.setUint16(34, bitDepth, true);
  writeString(view, 36, 'data');
  view.setUint32(40, dataSize, true);

  const channels: Float32Array[] = [];
  for (let c = 0; c < numChannels; c++) channels.push(buffer.getChannelData(c));

  let offset = 44;
  for (let i = 0; i < numSamples; i++) {
    for (let c = 0; c < numChannels; c++) {
      const v = floatToIntSample(channels[c][i] || 0, bitDepth);
      if (bitDepth === 16) {
        view.setInt16(offset, v, true);
        offset += 2;
      } else {
        view.setUint8(offset, v & 0xff);
        view.setUint8(offset + 1, (v >> 8) & 0xff);
        view.setUint8(offset + 2, (v >> 16) & 0xff);
        offset += 3;
      }
    }
  }
  return ab;
}

export async function mp3BufferToWavBlob(data: ArrayBuffer, bitDepth: WavBitDepth = 16): Promise<Blob> {
  const audio = await decodeAudioData(data);
  const wav = audioBufferToWav(audio, bitDepth);
  return new Blob([wav], { type: 'audio/wav' });
}

/** Concatenate AudioBuffers; resamples later buffers if sample rate/channel count differs from the first. */
export async function concatAudioBuffers(buffers: AudioBuffer[]): Promise<AudioBuffer> {
  if (buffers.length === 0) throw new Error('没有可合并的音频');
  if (buffers.length === 1) return buffers[0];

  const targetRate = buffers[0].sampleRate;
  const targetChannels = buffers[0].numberOfChannels;
  const normalized: AudioBuffer[] = [];

  for (const buf of buffers) {
    if (buf.sampleRate === targetRate && buf.numberOfChannels === targetChannels) {
      normalized.push(buf);
      continue;
    }
    const duration = buf.duration;
    const offline = new OfflineAudioContext(targetChannels, Math.ceil(duration * targetRate), targetRate);
    const src = offline.createBufferSource();
    src.buffer = buf;
    src.connect(offline.destination);
    src.start(0);
    normalized.push(await offline.startRendering());
  }

  const totalLength = normalized.reduce((sum, b) => sum + b.length, 0);
  const AC = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
  const ctx = new AC({ sampleRate: targetRate });
  try {
    const out = ctx.createBuffer(targetChannels, totalLength, targetRate);
    let offset = 0;
    for (const buf of normalized) {
      for (let c = 0; c < targetChannels; c++) {
        out.getChannelData(c).set(buf.getChannelData(Math.min(c, buf.numberOfChannels - 1)), offset);
      }
      offset += buf.length;
    }
    return out;
  } finally {
    await ctx.close().catch(() => {});
  }
}

export async function mergeMp3BuffersToWav(buffers: ArrayBuffer[], bitDepth: WavBitDepth = 16): Promise<Blob> {
  const decoded: AudioBuffer[] = [];
  for (const buf of buffers) {
    if (buf.byteLength === 0) continue;
    decoded.push(await decodeAudioData(buf));
  }
  if (decoded.length === 0) throw new Error('没有可合并的音频');
  const merged = await concatAudioBuffers(decoded);
  return new Blob([audioBufferToWav(merged, bitDepth)], { type: 'audio/wav' });
}

export function mergeMp3Buffers(buffers: ArrayBuffer[]): Uint8Array {
  const stripped: Uint8Array[] = [];
  for (const buf of buffers) {
    if (buf.byteLength === 0) continue;
    const bytes = new Uint8Array(buf);
    let start = 0;
    let end = bytes.length;

    if (bytes.length >= 10 && bytes[0] === 0x49 && bytes[1] === 0x44 && bytes[2] === 0x33) {
      const sz = ((bytes[6] & 0x7F) << 21) | ((bytes[7] & 0x7F) << 14) | ((bytes[8] & 0x7F) << 7) | (bytes[9] & 0x7F);
      start = Math.min(10 + sz, bytes.length);
    }
    if (bytes.length > 128 && bytes[bytes.length - 128] === 0x54 && bytes[bytes.length - 127] === 0x41 && bytes[bytes.length - 126] === 0x47) {
      end = Math.max(0, bytes.length - 128);
    }

    let foundSync = false;
    while (start < end - 1) {
      if (bytes[start] === 0xFF && (bytes[start + 1] & 0xE0) === 0xE0) { foundSync = true; break; }
      start++;
    }

    if (foundSync && start < end) {
      stripped.push(bytes.slice(start, end));
    } else if (bytes.length > 0) {
      stripped.push(bytes);
    }
  }

  if (stripped.length === 0) return new Uint8Array(0);

  const totalLen = stripped.reduce((s, a) => s + a.length, 0);
  const out = new Uint8Array(totalLen);
  let offset = 0;
  for (const s of stripped) { out.set(s, offset); offset += s.length; }
  return out;
}

export function addSubtitlesToZip(
  zip: Awaited<ReturnType<typeof generateZip>>,
  lines: VoiceLine[],
  downloadLangs: Set<LangKey>,
  getText: (line: VoiceLine, lang: LangKey) => string,
  opts: { subtitleMode: SubtitleMode; subtitleFormat: SubtitleFormat; folderMode: FolderMode },
  uniqueFileName: (name: string, usedNames: Set<string>) => string,
): void {
  if (opts.subtitleMode === 'none') return;
  const plain = opts.subtitleFormat === 'plain';

  if (opts.subtitleMode === 'merged') {
    // One subtitle file per language (cn/jp/en), not a mixed multilingual file.
    for (const lang of downloadLangs) {
      let txt = '';
      for (const line of lines) {
        const text = getText(line, lang);
        if (!text) continue;
        if (!plain) txt += `[${line.category}]\n`;
        txt += plain ? `${text}\n` : `${text}\n\n`;
      }
      if (!txt.trim()) continue;
      const filename = `subtitles_${langLabels[lang]}.txt`;
      if (opts.folderMode === 'lang' || opts.folderMode === 'both') {
        zip.file(buildFolderPath(opts.folderMode, lang, 'subtitles', filename), txt.trim() + '\n');
      } else {
        zip.file(filename, txt.trim() + '\n');
      }
    }
  } else {
    const usedTxtNames = new Set<string>();
    const langCounters: Record<string, number> = {};
    for (const line of lines) {
      for (const lang of downloadLangs) {
        const text = getText(line, lang);
        if (text) {
          langCounters[lang] = (langCounters[lang] || 0) + 1;
          const idx = langCounters[lang];
          const name = uniqueFileName(
            `${String(idx).padStart(3, '0')}${plain ? '' : `_${safeCategory(line.category)}`}_${lang}.txt`,
            usedTxtNames,
          );
          zip.file(buildFolderPath(opts.folderMode, lang, line.category, name), text);
        }
      }
    }
  }
}
