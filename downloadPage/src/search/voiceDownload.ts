import type { VoiceLine } from './searchApi';
import type { generateZip } from './downloadZip';

type LangKey = 'cn' | 'jp' | 'en';
type SubtitleMode = 'merged' | 'perLine' | 'none';
type SubtitleFormat = 'full' | 'plain';
type FolderMode = 'none' | 'lang' | 'category' | 'both';

const langLabels: Record<LangKey, string> = { cn: '中文', jp: '日文', en: '英文' };

function safeCategory(text: string): string {
  if (!text) return 'other';
  return text.replace(/[<>:"/\\|?*]/g, '').replace(/\s+/g, '_').slice(0, 80) || 'other';
}

export function buildFolderPath(folderMode: FolderMode, langKey: LangKey, category: string, filename: string): string {
  const parts: string[] = [];
  if (folderMode === 'lang' || folderMode === 'both') parts.push(langLabels[langKey]);
  if (folderMode === 'category' || folderMode === 'both') parts.push(safeCategory(category));
  parts.push(filename);
  return parts.join('/');
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
    let txt = '';
    for (const line of lines) {
      if (!plain) txt += `[${line.category}]\n`;
      for (const lang of downloadLangs) {
        const text = getText(line, lang);
        if (text) txt += plain ? `${text}\n` : `  ${langLabels[lang]}: ${text}\n`;
      }
      if (!plain) txt += '\n';
    }
    zip.file('subtitles.txt', txt);
  } else {
    const usedTxtNames = new Set<string>();
    let idx = 0;
    for (const line of lines) {
      for (const lang of downloadLangs) {
        const text = getText(line, lang);
        if (text) {
          idx++;
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
