import { defineConfig, type Connect, type Plugin } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { resolve } from 'node:path';
import type { IncomingMessage, ServerResponse } from 'node:http';

const upstream = 'https://klbq-prod-www.idreamsky.com';
const allowedImageHosts = new Set(['wiki.biligame.com', 'patchwiki.biligame.com']);

function balanceApiProxy(): Plugin {
  return {
    name: 'balance-api-proxy',
    configureServer(server) {
      server.middlewares.use(handleBalanceApiProxy);
    },
    configurePreviewServer(server) {
      server.middlewares.use(handleBalanceApiProxy);
    }
  };
}

async function handleBalanceApiProxy(
  req: IncomingMessage,
  res: ServerResponse,
  next: Connect.NextFunction
): Promise<void> {
  if (!req.url) {
    next();
    return;
  }

  const url = new URL(req.url, 'http://localhost');

  if (req.method === 'OPTIONS' && url.pathname.startsWith('/api/balance/')) {
    writeCorsHeaders(res);
    res.statusCode = 204;
    res.end();
    return;
  }

  if (req.method === 'GET' && (url.pathname === '/api/image-download' || url.pathname === '/api/file-download')) {
    await handleFileDownloadProxy(url, res);
    return;
  }

  const target = url.pathname === '/api/balance/settings'
    ? `${upstream}/api/pages/KLBQ_BALANCE/index`
    : url.pathname === '/api/balance/data'
      ? `${upstream}/api/common/ide`
      : null;

  if (!target) {
    next();
    return;
  }

  try {
    const body = req.method === 'POST' ? await readRequestBody(req) : undefined;
    const response = await fetch(target, {
      method: req.method,
      headers: sanitizeProxyHeaders(req.headers, target),
      body: body ? new Uint8Array(body) : undefined
    });

    writeCorsHeaders(res);
    response.headers.forEach((value, key) => {
      if (!['content-encoding', 'content-length', 'transfer-encoding'].includes(key.toLowerCase())) {
        res.setHeader(key, value);
      }
    });
    res.statusCode = response.status;
    res.end(Buffer.from(await response.arrayBuffer()));
  } catch (error) {
    writeCorsHeaders(res);
    res.statusCode = 502;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify({ error: error instanceof Error ? error.message : 'Proxy request failed' }));
  }
}

async function handleFileDownloadProxy(url: URL, res: ServerResponse): Promise<void> {
  const rawTarget = url.searchParams.get('url');
  const target = rawTarget ? new URL(rawTarget) : null;

  if (!target || !allowedImageHosts.has(target.hostname)) {
    res.statusCode = 400;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify({ error: 'Unsupported image URL' }));
    return;
  }

  try {
    const response = await fetch(target, { headers: { accept: '*/*' } });
    if (!response.ok) {
      throw new Error(`Image request failed: ${response.status}`);
    }

    res.statusCode = response.status;
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Content-Type', response.headers.get('content-type') || 'application/octet-stream');
    res.setHeader('Cache-Control', 'public, max-age=86400');
    res.end(Buffer.from(await response.arrayBuffer()));
  } catch (error) {
    res.statusCode = 502;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify({ error: error instanceof Error ? error.message : 'File proxy failed' }));
  }
}

function sanitizeProxyHeaders(headers: IncomingMessage['headers'], target: string): Headers {
  const nextHeaders = new Headers();
  for (const [key, value] of Object.entries(headers)) {
    if (!value || key.startsWith('sec-')) continue;
    if (['host', 'connection', 'content-length'].includes(key.toLowerCase())) continue;
    nextHeaders.set(key, Array.isArray(value) ? value.join(', ') : value);
  }
  nextHeaders.set('host', new URL(target).host);
  return nextHeaders;
}

function writeCorsHeaders(res: ServerResponse): void {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

function readRequestBody(req: IncomingMessage): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on('data', chunk => chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)));
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

export default defineConfig({
  plugins: [balanceApiProxy(), svelte()],
  build: {
    cssTarget: 'chrome111',
    cssMinify: 'esbuild',
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        main: resolve(import.meta.dirname, 'index.html'),
        search: resolve(import.meta.dirname, 'search/index.html')
      }
    }
  }
});
