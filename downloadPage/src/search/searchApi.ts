export type FileAsset = { mime?: string; size?: number; url: string };
export type CategoryFile = { name: string; url: string; mime?: string; size?: number };
export type ResultImage = { thumb: string; full: string; size?: number };
export type WikiSearchItem = {
  title: string;
  ns: number;
  timestamp: string;
  size?: number;
  snippet?: string;
  wordcount?: number;
  redirecttitle?: string;
  sectiontitle?: string;
};
export type Suggestion = { title: string; desc: string; url: string; ns: number; pageid?: number };

import { formatFileSize, fileNameFromTitle } from './utils';
export type CategoryPage = { title: string; pageid: number; thumbnail?: string; url: string };
export type VoiceLine = {
  category: string;
  cnAudio: string;
  cnText: string;
  jpAudio: string;
  jpText: string;
  enAudio: string;
  enText: string;
};
export { formatFileSize, fileNameFromTitle };

type WikiPage = {
  title: string;
  thumbnail?: { source?: string };
  imageinfo?: Array<{ mime?: string; thumburl?: string; url: string; size?: number }>;
  revisions?: Array<{ slots?: { main?: { '*': string } } }>;
  categories?: Array<{ title: string }>;
};
type PrefixSearchResponse = { query?: { prefixsearch?: Array<{ title: string; ns: number; pageid?: number }> } };
export type SearchResponse = {
  errors?: Array<{ code?: string; info?: string }>;
  query?: {
    search?: WikiSearchItem[];
    searchinfo?: { totalhits?: number; suggestion?: string };
  };
};
type CategoryMembersResponse = { continue?: { cmcontinue?: string }; query?: { categorymembers?: Array<{ ns: number; title: string }> } };
type PagesResponse = { continue?: { gcmcontinue?: string }; query?: { pages?: Record<string, WikiPage> } };

export const WIKI_BASE = 'https://wiki.biligame.com/klbq/';
const API = 'https://wiki.biligame.com/klbq/api.php';
const CACHE_TTL = 60_000;
const MAX_CACHE = 100;
const requestCache = new Map<string, { response: Response; time: number }>();

function setCache(url: string, response: Response): void {
  if (requestCache.size >= MAX_CACHE) {
    let oldestKey = '';
    let oldestTime = Infinity;
    for (const [k, v] of requestCache) {
      if (v.time < oldestTime) { oldestTime = v.time; oldestKey = k; }
    }
    requestCache.delete(oldestKey);
  }
  requestCache.set(url, { response: response.clone(), time: Date.now() });
}

export function apiErrorMessage(data: SearchResponse): string | null {
  const err = data?.errors?.[0];
  if (!err) return null;
  const codeMap: Record<string, string> = { 'search-title-disabled': '当前命名空间不支持搜索', 'search-text-disabled': '全文搜索未启用', toomanyconcurrent: '并发请求过多，请稍后再试', ratelimited: '请求频率受限，请稍后再试' };
  return (err.code ? codeMap[err.code] : undefined) || err.info || err.code || '接口返回错误';
}

export function httpErrorMessage(statusCode: number): string {
  const map: Record<number, string> = { 400: '请求参数有误', 403: '访问被拒绝，可能被反爬限制', 404: 'Wiki API 接口不存在', 429: '请求过于频繁，请稍后再试', 500: 'Wiki 服务器内部错误', 502: 'Wiki 服务器网关异常', 503: 'Wiki 服务暂时不可用', 504: 'Wiki 服务器响应超时' };
  return map[statusCode] || `HTTP ${statusCode} 错误`;
}

export async function fetchWithTimeout(url: string, timeoutMs: number): Promise<Response> {
  const cached = requestCache.get(url);
  if (cached && Date.now() - cached.time < CACHE_TTL) return cached.response.clone();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { signal: controller.signal, headers: { Accept: 'application/json' } });
    if (!response.ok) throw new Error(httpErrorMessage(response.status));
    setCache(url, response);
    return response;
  } finally {
    clearTimeout(timer);
  }
}

export async function searchWiki(params: {
  search: string;
  namespace: string;
  limit: number;
  offset: number;
  sort: string;
}): Promise<SearchResponse> {
  const query = new URLSearchParams({
    action: 'query', list: 'search', srsearch: params.search, srlimit: String(params.limit),
    sroffset: String(params.offset), srnamespace: params.namespace, srinfo: 'totalhits|suggestion',
    srprop: 'snippet|timestamp|wordcount|sectiontitle|redirecttitle|size', srsort: params.sort, format: 'json', origin: '*'
  });
  return await (await fetchWithTimeout(`${API}?${query}`, 10000)).json() as SearchResponse;
}

export async function fetchPrefixSuggestions(params: {
  search: string;
  namespace: string;
  limit: number;
  nsNameMap: Record<number, string>;
}): Promise<Suggestion[]> {
  const query = new URLSearchParams({ action: 'query', list: 'prefixsearch', pssearch: params.search, pslimit: String(params.limit), psnamespace: params.namespace || '0', format: 'json', origin: '*' });
  const data = await (await fetchWithTimeout(`${API}?${query}`, 5000)).json() as PrefixSearchResponse;
  return (data.query?.prefixsearch || []).map(item => ({
    title: item.title,
    desc: params.nsNameMap[item.ns] || '页面',
    url: WIKI_BASE + encodeURIComponent(item.title.replace(/ /g, '_')),
    ns: item.ns,
    pageid: item.pageid
  }));
}

export async function fetchFileAssets(titles: string[], nsMap: Record<string, number>): Promise<{ images: Record<string, ResultImage>; files: Record<string, FileAsset> }> {
  const images: Record<string, ResultImage> = {};
  const files: Record<string, FileAsset> = {};
  const piThumbs: Record<string, string> = {};
  try {
    const params = new URLSearchParams({ action: 'query', prop: 'pageimages', piprop: 'thumbnail', pithumbnail: '120', pilicense: 'any', titles: titles.join('|'), format: 'json', origin: '*' });
    const data = await (await fetchWithTimeout(`${API}?${params}`, 8000)).json() as PagesResponse;
    for (const page of Object.values(data.query?.pages || {})) if (page.thumbnail?.source) piThumbs[page.title] = page.thumbnail.source;
  } catch (err) { console.warn('[searchApi] fetchPageImages failed:', err); }

  const fileTitles = titles.filter(title => nsMap[title] === 6);
  if (fileTitles.length > 0) {
    try {
      const params = new URLSearchParams({ action: 'query', prop: 'imageinfo', iiprop: 'url|size|mime', iiurlwidth: '120', titles: fileTitles.map(title => 'File:' + title.replace(/^文件:/, '')).join('|'), format: 'json', origin: '*' });
      const data = await (await fetchWithTimeout(`${API}?${params}`, 8000)).json() as PagesResponse;
      for (const page of Object.values(data.query?.pages || {})) {
        const info = page.imageinfo?.[0];
        const origTitle = page.title.replace(/^文件:/, '');
        if (info?.url) {
          files[origTitle] = { mime: info.mime, size: info.size, url: info.url };
          files[page.title] = files[origTitle];
        }
        if (info?.mime?.startsWith('image/')) {
          images[origTitle] = { thumb: info.thumburl || info.url, full: info.url, size: info.size };
          images[page.title] = images[origTitle];
        }
      }
    } catch (err) { console.warn('[searchApi] fetchImageInfo failed:', err); }
  }

  const missingTitles = titles.filter(title => !images[title] && (nsMap[title] === 0 || nsMap[title] === 14));
  if (missingTitles.length > 0) {
    try {
      const params = new URLSearchParams({ action: 'query', prop: 'revisions', rvprop: 'content', rvslots: 'main', titles: missingTitles.slice(0, 10).join('|'), format: 'json', origin: '*' });
      const data = await (await fetchWithTimeout(`${API}?${params}`, 8000)).json() as PagesResponse;
      for (const page of Object.values(data.query?.pages || {})) {
        const content = page.revisions?.[0]?.slots?.main?.['*'] || '';
        const match = content.match(/\[\[(?:文件|File):([^|\]\]]+)/i);
        if (match) {
          const fullUrl = WIKI_BASE + 'Special:Redirect/file/' + encodeURIComponent(match[1].trim());
          images[page.title] = { thumb: fullUrl, full: fullUrl };
        }
      }
    } catch (err) { console.warn('[searchApi] fetchRevisions failed:', err); }
  }

  for (const title of titles) if (!images[title] && piThumbs[title]) images[title] = { thumb: piThumbs[title], full: piThumbs[title] };
  return { images, files };
}

export async function fetchPageExtra(titles: string[]): Promise<Record<string, { categories: string[] }>> {
  try {
    const params = new URLSearchParams({ action: 'query', prop: 'categories', cllimit: '5', clshow: '!hidden', titles: titles.join('|'), format: 'json', origin: '*' });
    const data = await (await fetchWithTimeout(`${API}?${params}`, 8000)).json() as PagesResponse;
    return Object.fromEntries(Object.values(data.query?.pages || {}).map(page => [page.title, { categories: (page.categories || []).map(category => category.title.replace(/^分类:/, '')) }]));
  } catch {
    return {};
  }
}

export async function fetchCategoryMembers(category: string, namespace: number, type: 'subcat' | 'file' | 'page'): Promise<string[]> {
  const output: string[] = [];
  let cmcontinue = '';
  do {
    const params = new URLSearchParams({ action: 'query', list: 'categorymembers', cmtitle: category, cmnamespace: String(namespace), cmtype: type, cmlimit: '500', format: 'json', origin: '*' });
    if (cmcontinue) params.set('cmcontinue', cmcontinue);
    const data = await (await fetchWithTimeout(`${API}?${params}`, 10000)).json() as CategoryMembersResponse;
    output.push(...(data.query?.categorymembers || []).map(item => item.title));
    cmcontinue = data.continue?.cmcontinue || '';
  } while (cmcontinue);
  return output;
}

export async function fetchCategoryFiles(category: string, audioOnly: boolean): Promise<CategoryFile[]> {
  const files: CategoryFile[] = [];
  const seenUrls = new Set<string>();
  let gcmcontinue = '';
  do {
    const params = new URLSearchParams({ action: 'query', generator: 'categorymembers', gcmtitle: category, gcmnamespace: '6', gcmlimit: '500', prop: 'imageinfo', iiprop: 'url|mime|size', format: 'json', origin: '*' });
    if (gcmcontinue) params.set('gcmcontinue', gcmcontinue);
    const data = await (await fetchWithTimeout(`${API}?${params}`, 10000)).json() as PagesResponse;
    for (const page of Object.values(data.query?.pages || {})) {
      const info = page.imageinfo?.[0];
      if (!info?.url || seenUrls.has(info.url)) continue;
      if (audioOnly && !isAudioFile(info.url, info.mime)) continue;
      seenUrls.add(info.url);
      files.push({ name: fileNameFromTitle(page.title), url: info.url, mime: info.mime, size: info.size });
    }
    gcmcontinue = data.continue?.gcmcontinue || '';
  } while (gcmcontinue);
  return files;
}

function isAudioFile(url: string, mime?: string): boolean {
  const clean = url.split('?')[0];
  return mime?.startsWith('audio/') === true || /\.(wav|mp3|ogg)$/i.test(clean);
}

export async function fetchCategoryPages(category: string): Promise<CategoryPage[]> {
  const pages: CategoryPage[] = [];
  let cmcontinue = '';

  do {
    const params = new URLSearchParams({ action: 'query', list: 'categorymembers', cmtitle: category, cmnamespace: '0', cmtype: 'page', cmlimit: '500', format: 'json', origin: '*' });
    if (cmcontinue) params.set('cmcontinue', cmcontinue);
    const data = await (await fetchWithTimeout(`${API}?${params}`, 10000)).json() as { continue?: { cmcontinue?: string }; query?: { categorymembers?: Array<{ ns: number; title: string; pageid: number }> } };
    const members = data.query?.categorymembers || [];
    for (const member of members) {
      pages.push({ title: member.title, pageid: member.pageid, url: WIKI_BASE + encodeURIComponent(member.title.replace(/ /g, '_')) });
    }
    cmcontinue = data.continue?.cmcontinue || '';
  } while (cmcontinue);

  return pages.filter(p => !p.title.startsWith('文件:') && !p.title.startsWith('File:'));
}

async function fillPageThumbnails(pages: CategoryPage[]): Promise<void> {
  for (const page of pages) {
    const filename = encodeURIComponent(`${page.title}头像.png`);
    page.thumbnail = `${WIKI_BASE}Special:Redirect/file/${filename}`;
  }
}

const CHARACTER_CATEGORIES = ['超弦体', '晶源体'];

export async function fetchAllCharacters(): Promise<CategoryPage[]> {
  const pages: CategoryPage[] = [];
  for (const cat of CHARACTER_CATEGORIES) {
    const categoryPages = await fetchCategoryPages(`Category:${cat}`);
    for (const page of categoryPages) pages.push(page);
  }
  if (pages.length > 0) await fillPageThumbnails(pages);
  return pages;
}

export async function fetchVoicePageParsetree(pageTitle: string): Promise<string> {
  let url = `${API}?action=parse&page=${encodeURIComponent(pageTitle)}&prop=parsetree&format=json&origin=*`;
  let resp = await fetchWithTimeout(url, 15000);
  if (!resp.ok) {
    const pageid = await resolveVoicePageId(pageTitle);
    if (!pageid) return '';
    url = `${API}?action=parse&pageid=${pageid}&prop=parsetree&format=json&origin=*`;
    resp = await fetchWithTimeout(url, 15000);
  }
  const data = await resp.json() as { parse?: { parsetree?: { '*': string } } };
  return data.parse?.parsetree?.['*'] || '';
}

const voicePageIdCache = new Map<string, number | null>();

async function resolveVoicePageId(pageTitle: string): Promise<number | null> {
  if (voicePageIdCache.has(pageTitle)) return voicePageIdCache.get(pageTitle)!;

  let cmcontinue = '';
  do {
    const params = new URLSearchParams({ action: 'query', list: 'categorymembers', cmtitle: 'Category:语音台词', cmnamespace: '0', cmtype: 'page', cmlimit: '500', format: 'json', origin: '*' });
    if (cmcontinue) params.set('cmcontinue', cmcontinue);
    const data = await (await fetchWithTimeout(`${API}?${params}`, 10000)).json() as { continue?: { cmcontinue?: string }; query?: { categorymembers?: Array<{ pageid: number; title: string }> } };
    const members = data.query?.categorymembers || [];
    for (const m of members) {
      const charName = m.title.replace(/\/语音台词$/, '');
      voicePageIdCache.set(m.title, m.pageid);
      voicePageIdCache.set(charName, m.pageid);
    }
    const found = voicePageIdCache.get(pageTitle);
    if (found != null) return found;
    cmcontinue = data.continue?.cmcontinue || '';
  } while (cmcontinue);

  const pid = voicePageIdCache.get(pageTitle);
  if (pid != null) return pid;

  const charName = pageTitle.replace(/\/语音台词$/, '');
  return voicePageIdCache.get(charName) ?? null;
}

export function resolveAudioUrl(filename: string): string {
  if (!filename) return '';
  const clean = filename.trim().replace(/^文件:|^File:/, '');
  return WIKI_BASE + 'Special:Redirect/file/' + encodeURIComponent(clean);
}
