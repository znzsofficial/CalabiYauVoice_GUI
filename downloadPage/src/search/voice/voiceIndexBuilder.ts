import { fetchVoicePageParsetree, type CategoryPage } from '../searchApi';
import { toError } from '../utils';
import { parseVoiceSections } from './voiceParser';
import type { VoiceIndexFailure, VoiceIndexSections } from './voiceIndexTypes';

export type VoiceIndexBuildProgress = {
  done: number;
  total: number;
  index: Map<string, VoiceIndexSections>;
  failures: VoiceIndexFailure[];
};

export type VoiceIndexBuildOptions = {
  /** Start from this map (retry/revalidate) instead of empty. */
  baseIndex?: Map<string, VoiceIndexSections>;
  /** Carry prior failure metadata (attempt counts). */
  baseFailures?: VoiceIndexFailure[];
  /** Limit work to these titles. */
  onlyTitles?: string[];
  /** When true with baseIndex, re-fetch every character in `chars`. */
  revalidateAll?: boolean;
  concurrency?: number;
  signal?: AbortSignal;
  onProgress?: (progress: VoiceIndexBuildProgress) => void;
};

function sortFailures(failures: VoiceIndexFailure[]): VoiceIndexFailure[] {
  return [...failures].sort((a, b) => a.title.localeCompare(b.title, 'zh-CN'));
}

export function resolveIndexTargets(
  chars: CategoryPage[],
  options: {
    baseIndex?: Map<string, VoiceIndexSections>;
    baseFailures?: VoiceIndexFailure[];
    onlyTitles?: string[];
    revalidateAll?: boolean;
    rebuild?: boolean;
  },
): CategoryPage[] {
  if (options.rebuild || !options.baseIndex) return chars;
  const failureSet = new Set((options.baseFailures || []).map(item => item.title));
  const onlySet = options.onlyTitles ? new Set(options.onlyTitles) : null;
  return chars.filter(c => {
    if (options.revalidateAll) return true;
    if (onlySet) return onlySet.has(c.title);
    return failureSet.has(c.title) || !options.baseIndex!.has(c.title);
  });
}

export async function buildVoiceSearchIndexMap(
  chars: CategoryPage[],
  options: VoiceIndexBuildOptions = {},
): Promise<{ index: Map<string, VoiceIndexSections>; failures: VoiceIndexFailure[] }> {
  const signal = options.signal;
  if (signal?.aborted) throw new DOMException('已取消', 'AbortError');

  const idx = options.baseIndex
    ? new Map(options.baseIndex)
    : new Map<string, VoiceIndexSections>();
  const failureMap = new Map<string, VoiceIndexFailure>(
    (options.baseFailures || []).map(item => [item.title, { ...item }]),
  );
  const targets = resolveIndexTargets(chars, {
    baseIndex: options.baseIndex,
    baseFailures: options.baseFailures,
    onlyTitles: options.onlyTitles,
    revalidateAll: options.revalidateAll,
    rebuild: !options.baseIndex,
  });

  if (targets.length === 0) {
    const failures = sortFailures([...failureMap.values()]);
    options.onProgress?.({ done: 0, total: 0, index: idx, failures });
    return { index: idx, failures };
  }

  const concurrency = Math.max(1, Math.min(8, options.concurrency ?? 4));
  let done = 0;
  const total = targets.length;

  for (let i = 0; i < targets.length; i += concurrency) {
    if (signal?.aborted) throw new DOMException('已取消', 'AbortError');
    const batch = targets.slice(i, i + concurrency);
    await Promise.all(batch.map(async c => {
      if (signal?.aborted) throw new DOMException('已取消', 'AbortError');
      try {
        const pt = await fetchVoicePageParsetree(`${c.title}/语音台词`, signal);
        if (!pt) {
          idx.set(c.title, []);
          failureMap.delete(c.title);
          return;
        }
        const groups = parseVoiceSections(pt);
        idx.set(c.title, groups.map(g => ({
          title: g.title,
          lines: g.lines.map(l => ({
            category: l.category,
            cnText: l.cnText,
            jpText: l.jpText,
            enText: l.enText,
          })),
        })));
        failureMap.delete(c.title);
      } catch (err) {
        if (err instanceof DOMException && err.name === 'AbortError') throw err;
        const prev = failureMap.get(c.title);
        failureMap.set(c.title, {
          title: c.title,
          error: toError(err).message || '索引失败',
          at: Date.now(),
          attempts: (prev?.attempts || 0) + 1,
        });
        idx.delete(c.title);
      } finally {
        done += 1;
      }
    }));
    options.onProgress?.({
      done,
      total,
      index: new Map(idx),
      failures: sortFailures([...failureMap.values()]),
    });
  }

  const failures = sortFailures([...failureMap.values()]);
  return { index: idx, failures };
}
