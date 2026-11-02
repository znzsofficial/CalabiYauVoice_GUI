/** One metadata lane for this search app. Media/CDN downloads never enter it. */
export const WIKI_REQUEST_GAP_MS = 6_500;

export class WikiBackoffError extends Error {
  constructor(public status: number, public retryAt: number) {
    super(status === 429 ? 'Wiki 请求过于频繁，请稍后再试' : 'Wiki 访问被风控拦截，请稍后再试');
    this.name = 'WikiBackoffError';
  }
}

export function abortError(): DOMException {
  return new DOMException('已取消', 'AbortError');
}

type Clock = {
  now: () => number;
  setTimeout: (callback: () => void, ms: number) => ReturnType<typeof setTimeout>;
  clearTimeout: (timer: ReturnType<typeof setTimeout>) => void;
};

type Job = { start: () => void; reject: (error: unknown) => void; cleanup: () => void; serveCached: () => boolean };

export function createWikiRequestScheduler(clock: Clock = {
  now: () => Date.now(),
  setTimeout: (callback, ms) => setTimeout(callback, ms),
  clearTimeout: timer => clearTimeout(timer),
}) {
  const queue: Job[] = [];
  let active = false;
  let nextAllowed = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let backoff: WikiBackoffError | undefined;

  function pump(): void {
    if (timer !== undefined) clock.clearTimeout(timer);
    timer = undefined;
    // Requests queued for the same URL can become cache hits while another
    // transport completes. Resolve them immediately without consuming a slot.
    for (let i = 0; i < queue.length;) {
      if (queue[i].serveCached()) {
        queue[i].cleanup();
        queue.splice(i, 1);
      } else i++;
    }
    if (active || queue.length === 0) return;
    const delay = nextAllowed - clock.now();
    if (delay > 0) {
      timer = clock.setTimeout(pump, delay);
      return;
    }
    queue.shift()!.start();
  }

  return {
    /** Fail fast during WAF cooldown, including queued work: never fan out fallbacks. */
    backoff(status: number, retryAfterMs = 0): WikiBackoffError {
      const retryAt = clock.now() + Math.max(status === 567 ? 300_000 : 60_000, retryAfterMs);
      backoff = new WikiBackoffError(status, Math.max(backoff?.retryAt || 0, retryAt));
      for (const job of queue.splice(0)) {
        job.cleanup();
        job.reject(backoff);
      }
      if (timer !== undefined) clock.clearTimeout(timer);
      timer = undefined;
      return backoff;
    },
    schedule<T>(run: () => Promise<T>, signal?: AbortSignal, readCache?: () => T | undefined): Promise<T> {
      if (signal?.aborted) return Promise.reject(abortError());
      if (backoff && clock.now() < backoff.retryAt) return Promise.reject(backoff);
      return new Promise<T>((resolve, reject) => {
        const cancel = () => {
          const index = queue.indexOf(job);
          if (index < 0) return; // In-flight cancellation belongs to the transport.
          queue.splice(index, 1);
          job.cleanup();
          reject(abortError());
          pump();
        };
        const job: Job = {
          reject,
          cleanup: () => signal?.removeEventListener('abort', cancel),
          serveCached: () => {
            const cached = readCache?.();
            if (cached === undefined) return false;
            resolve(cached);
            return true;
          },
          start: () => {
            job.cleanup();
            active = true;
            // A rejected/aborted request still gets a full quiet period after settling.
            void (async () => {
              try {
                if (signal?.aborted) throw abortError();
                resolve(await run());
              } catch (error) {
                reject(error);
              } finally {
                nextAllowed = clock.now() + WIKI_REQUEST_GAP_MS;
                active = false;
                pump();
              }
            })();
          },
        };
        signal?.addEventListener('abort', cancel, { once: true });
        queue.push(job);
        pump();
      });
    },
  };
}
