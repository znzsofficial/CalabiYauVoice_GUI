import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { setImmediate as immediate } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';

// In-memory bundles resolve the application's extensionless TS imports without
// writing shared build output. Every test gets fresh module-level caches/queue.
const bundles = await Promise.all(['searchApi.ts', 'voice/voiceIndexBuilder.ts'].map(async entry => {
  const result = await build({ entryPoints: [fileURLToPath(new URL(`../src/search/${entry}`, import.meta.url))], bundle: true, write: false, format: 'esm', platform: 'browser' });
  return result.outputFiles[0].text;
}));
let moduleId = 0;
const load = (index = 0) => import(`data:text/javascript;base64,${Buffer.from(bundles[index]).toString('base64')}#${moduleId++}`);
const json = (body = {}, status = 200, headers = {}) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json', ...headers } });
const flush = async () => { for (let i = 0; i < 6; i++) await immediate(); };
const deferred = () => { let resolve, reject; const promise = new Promise((ok, fail) => { resolve = ok; reject = fail; }); return { promise, resolve, reject }; };
const abortError = () => new DOMException('cancelled', 'AbortError');

function setup(t, fetcher) {
  let now = 0;
  let sequence = 0;
  const timers = new Map();
  const calls = [];
  t.mock.method(Date, 'now', () => now);
  t.mock.method(globalThis, 'setTimeout', (callback, delay) => {
    const id = ++sequence;
    timers.set(id, { at: now + delay, callback });
    return id;
  });
  t.mock.method(globalThis, 'clearTimeout', id => timers.delete(id));
  t.mock.method(globalThis, 'fetch', (url, options) => {
    calls.push({ url, at: now, signal: options?.signal });
    return fetcher(url, options, calls.length);
  });
  return {
    calls, timers,
    async advance(ms) {
      const until = now + ms;
      for (;;) {
        const next = [...timers].filter(([, timer]) => timer.at <= until).sort((a, b) => a[1].at - b[1].at)[0];
        if (!next) break;
        now = next[1].at;
        timers.delete(next[0]);
        next[1].callback();
        await flush();
      }
      now = until;
      await flush();
    },
  };
}

test('Wiki misses are serial, with a 6.5s quiet period after success and failure', async t => {
  const first = deferred();
  const clock = setup(t, (_url, _options, n) => n === 1 ? first.promise : n === 2 ? Promise.reject(new Error('offline')) : Promise.resolve(json()));
  const api = await load();
  const a = api.fetchWithTimeout('/api/wiki?a', 20_000);
  const b = api.fetchWithTimeout('/api/wiki?b', 20_000);
  const failed = assert.rejects(b, /offline/);
  const c = api.fetchWithTimeout('/api/wiki?c', 20_000);
  await clock.advance(1_000);
  assert.equal(clock.calls.length, 1);
  first.resolve(json());
  await a;
  await clock.advance(6_499);
  assert.equal(clock.calls.length, 1);
  await clock.advance(1);
  await failed;
  assert.deepEqual(clock.calls.map(call => call.at), [0, 7_500]);
  await clock.advance(6_500);
  await c;
  assert.deepEqual(clock.calls.map(call => call.at), [0, 7_500, 14_000]);
  assert.equal(clock.timers.size, 0);
});

test('HTTP errors also delay the next actual request', async t => {
  const clock = setup(t, (_url, _options, n) => Promise.resolve(n === 1 ? json({}, 500) : json()));
  const api = await load();
  await assert.rejects(api.fetchWithTimeout('/api/wiki?bad', 1_000), /Wiki/);
  const next = api.fetchWithTimeout('/api/wiki?good', 1_000);
  await clock.advance(6_499);
  assert.equal(clock.calls.length, 1);
  await clock.advance(1);
  await next;
  assert.deepEqual(clock.calls.map(call => call.at), [0, 6_500]);
});

test('queued duplicate cache misses become immediate hits without taking another request slot', async t => {
  const first = deferred();
  const clock = setup(t, (_url, _options, n) => n === 1 ? first.promise : Promise.resolve(json()));
  const api = await load();
  const a = api.fetchWithTimeout('/api/wiki?same', 10_000);
  const duplicate = api.fetchWithTimeout('/api/wiki?same', 10_000);
  const next = api.fetchWithTimeout('/api/wiki?other', 10_000);
  first.resolve(json());
  await Promise.all([a, duplicate]);
  assert.equal(clock.calls.length, 1);
  await clock.advance(6_500);
  await next;
  assert.deepEqual(clock.calls.map(call => call.at), [0, 6_500]);
});

test('pre-aborted and queued cancellation remove work without shifting the lane', async t => {
  const clock = setup(t, async () => json());
  const api = await load();
  const cancelled = new AbortController();
  cancelled.abort();
  await assert.rejects(api.fetchWithTimeout('/api/wiki?never', 1_000, cancelled.signal), { name: 'AbortError' });
  await api.fetchWithTimeout('/api/wiki?first', 1_000);
  const queued = new AbortController();
  const rejected = assert.rejects(api.fetchWithTimeout('/api/wiki?cancel', 1_000, queued.signal), { name: 'AbortError' });
  const next = api.fetchWithTimeout('/api/wiki?next', 1_000);
  queued.abort();
  await rejected;
  await clock.advance(6_500);
  await next;
  assert.deepEqual(clock.calls.map(call => call.url), ['/api/wiki?first', '/api/wiki?next']);
  assert.deepEqual(clock.calls.map(call => call.at), [0, 6_500]);
  assert.equal(clock.timers.size, 0);
});

test('in-flight abort retains the quiet period, never caches its response, and does not poison later work', async t => {
  const clock = setup(t, (_url, options, n) => n === 1 ? new Promise((_ok, reject) => options.signal.addEventListener('abort', () => reject(abortError()), { once: true })) : Promise.resolve(json()));
  const api = await load();
  const controller = new AbortController();
  const rejected = assert.rejects(api.fetchWithTimeout('/api/wiki?same', 10_000, controller.signal), { name: 'AbortError' });
  await clock.advance(200);
  controller.abort();
  await rejected;
  const next = api.fetchWithTimeout('/api/wiki?same', 1_000);
  await clock.advance(6_499);
  assert.equal(clock.calls.length, 1);
  await clock.advance(1);
  await next;
  assert.deepEqual(clock.calls.map(call => call.at), [0, 6_700]);
  assert.equal(clock.timers.size, 0);
});

test('timeout starts only at transport start, not while queued', async t => {
  const clock = setup(t, (_url, options, n) => n === 1 ? Promise.resolve(json()) : new Promise((_ok, reject) => options.signal.addEventListener('abort', () => reject(abortError()), { once: true })));
  const api = await load();
  await api.fetchWithTimeout('/api/wiki?first', 1_000);
  const rejected = assert.rejects(api.fetchWithTimeout('/api/wiki?timeout', 1_000), { name: 'AbortError' });
  await clock.advance(6_499);
  assert.equal(clock.calls.length, 1);
  await clock.advance(1);
  assert.equal(clock.calls.length, 2);
  await clock.advance(999);
  assert.equal(clock.calls[1].signal.aborted, false);
  await clock.advance(1);
  await rejected;
  assert.equal(clock.calls[1].signal.aborted, true);
  assert.equal(clock.timers.size, 0);
});

test('fresh cache bypasses a busy lane; media downloads do not enter the Wiki scheduler; expired entries re-fetch', async t => {
  const pending = deferred();
  const clock = setup(t, (url) => url === '/api/wiki?busy' ? pending.promise : Promise.resolve(json()));
  const api = await load();
  await api.fetchWithTimeout('/api/wiki?cached', 1_000);
  const busy = api.fetchWithTimeout('/api/wiki?busy', 100_000);
  await clock.advance(6_500);
  await api.fetchWithTimeout('/api/wiki?cached', 1_000);
  await api.fetchWithTimeout('/api/file-download?url=cdn', 1_000);
  assert.deepEqual(clock.calls.map(call => call.url), ['/api/wiki?cached', '/api/wiki?busy', '/api/file-download?url=cdn']);
  pending.resolve(json());
  await busy;
  await clock.advance(60_000);
  await api.fetchWithTimeout('/api/wiki?cached', 1_000);
  assert.equal(clock.calls.length, 4);
});

for (const scenario of [
  { name: '429 Retry-After', response: () => json({}, 429, { 'retry-after': '120' }), status: 429, cooldown: 120_000 },
  { name: 'raw EdgeOne 567', response: () => json({}, 567), status: 567, cooldown: 300_000 },
  { name: 'proxy-wrapped 567', response: () => json({ upstreamStatus: 567, error: 'blocked' }, 502), status: 567, cooldown: 300_000 },
  { name: 'proxy-wrapped HTML 200', response: () => json({ upstreamStatus: 200, error: 'blocked' }, 502), status: 567, cooldown: 300_000 },
  { name: 'HTML interstitial', response: () => new Response('<!doctype html>', { headers: { 'content-type': 'text/html' } }), status: 567, cooldown: 300_000 },
  { name: 'MediaWiki ratelimited payload', response: () => json({ error: { code: 'ratelimited' } }), status: 429, cooldown: 60_000 },
]) test(`${scenario.name} rejects queued/fallback requests and backs off without sleeping`, async t => {
  const clock = setup(t, (_url, _options, n) => Promise.resolve(n === 2 ? scenario.response() : json()));
  const api = await load();
  await api.fetchWithTimeout('/api/wiki?cached', 1_000);
  const blocked = assert.rejects(api.fetchVoicePageParsetree('Test/语音台词'), { name: 'WikiBackoffError', status: scenario.status });
  const queued = assert.rejects(api.fetchWithTimeout('/api/wiki?queued', 1_000), { name: 'WikiBackoffError' });
  await clock.advance(6_500);
  await Promise.all([blocked, queued]);
  await api.fetchWithTimeout('/api/wiki?cached', 1_000);
  await assert.rejects(api.fetchWithTimeout('/api/wiki?fallback', 1_000), { name: 'WikiBackoffError' });
  assert.equal(clock.calls.length, 2);
  assert.equal(clock.timers.size, 0);
  await clock.advance(scenario.cooldown - 1);
  await assert.rejects(api.fetchWithTimeout('/api/wiki?fallback', 1_000), { name: 'WikiBackoffError' });
  await clock.advance(1);
  await api.fetchWithTimeout('/api/wiki?fallback', 1_000);
  assert.equal(clock.calls.length, 3);
});

test('voice title fallback carries cancellation into queued page-id lookup', async t => {
  const clock = setup(t, async () => json({ error: { code: 'missingtitle' } }));
  const api = await load();
  const controller = new AbortController();
  const rejected = assert.rejects(api.fetchVoicePageParsetree('Missing/语音台词', controller.signal), { name: 'AbortError' });
  await flush();
  assert.equal(clock.calls.length, 1);
  controller.abort();
  await rejected;
  await clock.advance(20_000);
  assert.equal(clock.calls.length, 1);
  assert.equal(clock.timers.size, 0);
});

test('failed voice catalog lookup is never cached as a successful empty voice page', async t => {
  const clock = setup(t, (_url, _options, n) => Promise.resolve(n === 1 ? json({ error: { code: 'missingtitle' } }) : json({}, 500)));
  const api = await load();
  const rejected = assert.rejects(api.fetchVoicePageParsetree('Broken/语音台词'), /Wiki/);
  await flush();
  await clock.advance(6_500);
  await rejected;
  assert.equal(api.getCachedVoiceParsetree('Broken/语音台词'), undefined);
  assert.equal(clock.calls.length, 2);
});

test('voice category fallback is scoped to its caller, including in-flight abort', async t => {
  const clock = setup(t, (_url, options, n) => n === 1
    ? Promise.resolve(json({ error: { code: 'missingtitle' } }))
    : n === 2 ? Promise.resolve(json({ query: { pages: {} } }))
      : new Promise((_ok, reject) => options.signal.addEventListener('abort', () => reject(abortError()), { once: true })));
  const api = await load();
  const controller = new AbortController();
  const rejected = assert.rejects(api.fetchVoicePageParsetree('Catalog/语音台词', controller.signal), { name: 'AbortError' });
  await flush();
  await clock.advance(13_000);
  assert.equal(clock.calls.length, 3);
  assert.match(clock.calls[2].url, /categorymembers/);
  controller.abort();
  await rejected;
  assert.equal(clock.calls[2].signal.aborted, true);
  assert.equal(clock.timers.size, 0);
});

test('character catalog and suggestions accept lifecycle cancellation while queued', async t => {
  const clock = setup(t, async () => json({ query: { categorymembers: [], prefixsearch: [] } }));
  const api = await load();
  await api.fetchWithTimeout('/api/wiki?first', 1_000);
  const controller = new AbortController();
  const characters = assert.rejects(api.fetchAllCharacters(controller.signal), { name: 'AbortError' });
  const suggestions = assert.rejects(api.fetchPrefixSuggestions({ search: 'x', namespace: '0', limit: 8, nsNameMap: {}, signal: controller.signal }), { name: 'AbortError' });
  controller.abort();
  await Promise.all([characters, suggestions]);
  await clock.advance(20_000);
  assert.equal(clock.calls.length, 1);
  assert.equal(clock.timers.size, 0);
});

test('voice index stops on WAF, preserves last good data, and leaves later titles pending', async t => {
  const clock = setup(t, async () => json({ upstreamStatus: 567 }, 502));
  const builder = await load(1);
  const previous = [{ title: 'old', lines: [] }];
  const updates = [];
  const result = await builder.buildVoiceSearchIndexMap([
    { title: 'A' }, { title: 'B' }, { title: 'C' },
  ], { baseIndex: new Map([['A', previous]]), revalidateAll: true, concurrency: 8, onProgress: progress => updates.push(progress) });
  assert.equal(clock.calls.length, 1);
  assert.deepEqual(result.index.get('A'), previous);
  assert.deepEqual(result.failures.map(item => item.title), ['A']);
  assert.equal(result.index.has('B'), false);
  assert.equal(updates.length, 1);
  assert.equal(updates[0].done, 1);
  assert.equal(updates[0].total, 3);
});

test('unparseable voice data is a failure, not a successful empty index', async t => {
  setup(t, async () => json({ parse: { parsetree: { '*': '<invalid-voice-data />' } } }));
  const builder = await load(1);
  const result = await builder.buildVoiceSearchIndexMap([{ title: 'Broken' }]);
  assert.equal(result.index.has('Broken'), false);
  assert.deepEqual(result.failures.map(item => item.title), ['Broken']);
});

test('voice index fills only old-cache gaps, serializes even requested concurrency, and batches publications', async t => {
  const clock = setup(t, async url => {
    const params = new URL(url, 'https://local.test').searchParams;
    return params.get('action') === 'parse'
      ? json({ error: { code: 'missingtitle' } })
      : json({ query: { pages: { '-1': { title: params.get('titles').split('|')[0], missing: '' } } } });
  });
  const builder = await load(1);
  const updates = [];
  const promise = builder.buildVoiceSearchIndexMap(['Old', 'A', 'B', 'C', 'D', 'E'].map(title => ({ title })), {
    baseIndex: new Map([['Old', []]]), concurrency: 4, onProgress: progress => updates.push(progress),
  });
  await flush();
  assert.equal(clock.calls.length, 1);
  // Five missing titles each need parse + verified missing title lookup.
  for (let i = 0; i < 9; i++) await clock.advance(6_500);
  const result = await promise;
  assert.equal(clock.calls.length, 10);
  assert.ok(clock.calls.every(call => !decodeURIComponent(call.url).includes('Old')));
  assert.equal(result.index.size, 6);
  assert.deepEqual(result.failures, []);
  assert.deepEqual(updates.map(progress => progress.done), [4, 5]);
  assert.deepEqual(clock.calls.map(call => call.at), Array.from({ length: 10 }, (_, i) => i * 6_500));
});
