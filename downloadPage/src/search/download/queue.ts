import { ensureProxyDownloadUrl, httpErrorMessage, type CategoryFile } from '../searchApi';

export type DownloadProgress = { finished: number; total: number; failed: number; currentName?: string };
export type DownloadedFile = { ok: true; index: number; name: string; blob: Blob } | { ok: false; index: number; name: string; error: string };
export type DownloadRetryOptions = { retries?: number; signal?: AbortSignal };

function shouldRetryStatus(status: number): boolean {
  return status === 429 || status >= 500;
}

function delay(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('已取消', 'AbortError'));
      return;
    }
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener('abort', () => {
      clearTimeout(timer);
      reject(new DOMException('已取消', 'AbortError'));
    }, { once: true });
  });
}

export async function downloadUrlWithRetry(url: string, options: DownloadRetryOptions = {}): Promise<Blob> {
  const retries = options.retries ?? 2;
  let lastError = '';
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      if (options.signal?.aborted) throw new DOMException('已取消', 'AbortError');
      if (attempt > 0) await delay(300 * attempt, options.signal);
      const response = await fetch(url, { signal: options.signal });
      if (!response.ok) {
        lastError = httpErrorMessage(response.status);
        if (attempt < retries && shouldRetryStatus(response.status)) continue;
        throw new Error(lastError);
      }
      const blob = await response.blob();
      if (blob.size === 0) {
        lastError = '空文件';
        if (attempt < retries) continue;
        throw new Error(lastError);
      }
      return blob;
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw error;
      lastError = error instanceof Error ? error.message : '下载失败';
      if (attempt < retries) continue;
    }
  }
  throw new Error(lastError || '下载失败');
}

async function downloadOneFile(file: CategoryFile, index: number, options: DownloadRetryOptions): Promise<DownloadedFile> {
  const url = ensureProxyDownloadUrl(file.url);
  try {
    const blob = await downloadUrlWithRetry(url, options);
    return { ok: true, index, name: file.name, blob };
  } catch (error) {
    const message = error instanceof DOMException && error.name === 'AbortError'
      ? '已取消'
      : error instanceof Error ? error.message : '下载失败';
    return { ok: false, index, name: file.name, error: message };
  }
}

export async function downloadFilesInParallel(
  files: CategoryFile[],
  concurrency: number,
  onProgress: (progress: DownloadProgress) => void,
  options: DownloadRetryOptions = {}
): Promise<DownloadedFile[]> {
  const output: DownloadedFile[] = new Array(files.length);
  let nextIndex = 0;
  let finished = 0;
  let failed = 0;

  async function worker(): Promise<void> {
    while (nextIndex < files.length && !options.signal?.aborted) {
      const index = nextIndex++;
      const file = files[index];
      const result = await downloadOneFile(file, index, options);
      output[index] = result;
      finished += 1;
      if (!result.ok) failed += 1;
      onProgress({ finished, total: files.length, failed, currentName: file.name });
    }
  }

  await Promise.all(Array.from({ length: Math.min(concurrency, files.length) }, () => worker()));
  return output.filter(Boolean);
}

export function downloadFailuresText(failures: Array<{ name: string; error: string; category?: string }>): string {
  return [
    '以下文件下载失败：',
    '',
    ...failures.map(file => `- ${file.category ? `[${file.category}] ` : ''}${file.name}: ${file.error}`),
    ''
  ].join('\n');
}
