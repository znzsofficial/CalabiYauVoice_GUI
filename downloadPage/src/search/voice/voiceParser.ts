import type { VoiceLine } from '../searchApi';

export type VoiceSectionGroup = { title: string; lines: VoiceLine[] };

export function parseVoiceSections(parsetree: string): VoiceSectionGroup[] {
  let root: Element;
  try {
    const dom = new DOMParser().parseFromString(parsetree, 'text/xml');
    root = dom.documentElement;
  } catch {
    return [];
  }
  if (!root || root.querySelector('parsererror')) return [];

  const groups: VoiceSectionGroup[] = [];
  let currentTitle = '';
  let currentLines: VoiceLine[] = [];

  for (const node of Array.from(root.childNodes)) {
    if (node.nodeType !== 1) continue;
    const child = node as Element;

    if (child.tagName === 'h' && child.getAttribute('level') === '2') {
      if (currentLines.length > 0 || currentTitle) {
        groups.push({ title: currentTitle, lines: currentLines });
      }
      const m = /^==(.+?)==$/s.exec(child.textContent || '');
      currentTitle = m ? m[1].trim() : '';
      currentLines = [];
      continue;
    }

    if (child.tagName === 'template') {
      const extracted = extractTemplateVoiceLines(child);
      for (const line of extracted) currentLines.push(line);
    }
  }

  if (currentLines.length > 0 || currentTitle) {
    groups.push({ title: currentTitle, lines: currentLines });
  }

  return groups.filter(g => g.lines.length > 0 || g.title);
}

function extractTemplateVoiceLines(template: Element): VoiceLine[] {
  let titleText = '';
  const parts: Element[] = [];

  for (const node of Array.from(template.childNodes)) {
    if (node.nodeType !== 1) continue;
    const el = node as Element;
    if (el.tagName === 'title') {
      titleText = (el.textContent || '').trim();
    } else if (el.tagName === 'part') {
      parts.push(el);
    }
  }

  if (titleText !== '语音台词') return [];

  const values: string[] = [];
  let hasJa = false;
  let hasEn = false;
  let maxIndex = 0;

  for (const part of parts) {
    let nameVal = '';
    let hasIndex = false;
    let index = 0;
    let valueStr = '';

    for (const node of Array.from(part.childNodes)) {
      if (node.nodeType !== 1) continue;
      const el = node as Element;
      if (el.tagName === 'name') {
        if (el.hasAttribute('index')) {
          hasIndex = true;
          index = parseInt(el.getAttribute('index') || '0', 10);
        } else {
          nameVal = (el.textContent || '').trim();
        }
      } else if (el.tagName === 'value') {
        valueStr = (el.textContent || '').trim();
      }
    }

    if (hasIndex) {
      if (index > maxIndex) maxIndex = index;
      values[index - 1] = valueStr;
    } else if (nameVal === 'ja') {
      hasJa = valueStr === '1';
    } else if (nameVal === 'en') {
      hasEn = valueStr === '1';
    }
  }

  const lines: VoiceLine[] = [];
  const entrySize = hasEn ? 7 : hasJa ? 5 : 3;

  for (let i = 0; i < maxIndex; i += entrySize) {
    const category = values[i] || '';
    const cnAudio = cleanFilename(values[i + 1] || '');
    const cnText = cleanText(values[i + 2] || '');

    let jpAudio = '';
    let jpText = '';
    let enAudio = '';
    let enText = '';

    if (entrySize >= 7) {
      jpAudio = cleanFilename(values[i + 3] || '');
      jpText = cleanText(values[i + 4] || '');
      enAudio = cleanFilename(values[i + 5] || '');
      enText = cleanText(values[i + 6] || '');
    } else if (entrySize >= 5) {
      jpAudio = cleanFilename(values[i + 3] || '');
      jpText = cleanText(values[i + 4] || '');
    }

    if (cnAudio || cnText) {
      lines.push({ category, cnAudio, cnText, jpAudio, jpText, enAudio, enText });
    }
  }

  return lines;
}

function cleanFilename(raw: string): string {
  return raw.trim().replace(/^文件[:：]|^File[:：]/i, '');
}

function cleanText(raw: string): string {
  let text = raw.trim()
    .replace(/<u>/g, '').replace(/<\/u>/g, '')
    .replace(/<br\s*\/?>/gi, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
  return text.trim();
}
