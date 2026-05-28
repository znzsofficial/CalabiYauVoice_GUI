<script lang="ts">
  import { onMount } from 'svelte';

  type ProfileValue = 'default' | 'images' | 'all' | 'advanced';
  type Status = 'idle' | 'loading' | 'empty' | 'error' | 'ready';
  type SortValue = 'relevance' | 'last_edit_desc' | 'last_edit_asc' | 'create_timestamp_desc' | 'incoming_links_desc';
  type NamespaceOption = { id: number; name: string };
  type Suggestion = { title: string; desc: string; url: string };
  type ResultImage = { thumb: string; full: string; size?: number };
  type WikiSearchItem = {
    title: string;
    ns: number;
    timestamp: string;
    size?: number;
    snippet?: string;
    wordcount?: number;
    redirecttitle?: string;
    sectiontitle?: string;
  };
  type SearchResult = WikiSearchItem & {
    title: string;
    ns: number;
    image?: ResultImage;
    categories: string[];
    url: string;
    nsName: string;
    dateStr: string;
    pageSizeKB: string;
    fileSize: string;
    delay: string;
  };
  type WikiPage = {
    title: string;
    thumbnail?: { source?: string };
    imageinfo?: Array<{ mime?: string; thumburl?: string; url: string; size?: number }>;
    revisions?: Array<{ slots?: { main?: { '*': string } } }>;
    categories?: Array<{ title: string }>;
  };
  type OpenSearchResponse = [string, string[], string[], string[]];
  type SearchResponse = {
    errors?: Array<{ code?: string; info?: string }>;
    query?: {
      search?: WikiSearchItem[];
      searchinfo?: { totalhits?: number; suggestion?: string };
    };
  };
  type PagesResponse = { query?: { pages?: Record<string, WikiPage> } };

  function toError(error: unknown): Error {
    return error instanceof Error ? error : new Error(String(error));
  }

  const API = 'https://wiki.biligame.com/klbq/api.php';
  const WIKI_BASE = 'https://wiki.biligame.com/klbq/';
  const PAGE_SIZE = 20;
  const SUGGEST_LIMIT = 8;
  const DEBOUNCE_MS = 300;

  const PROFILES: Array<{ value: ProfileValue; name: string; desc: string }> = [
    { value: 'default', name: '默认', desc: '主命名空间' },
    { value: 'images', name: '文件', desc: '文件命名空间' },
    { value: 'all', name: '全部', desc: '所有命名空间' },
    { value: 'advanced', name: '高级', desc: '自选命名空间' }
  ];
  const NS_PRIMARY: NamespaceOption[] = [
    { id: 0, name: '条目' }, { id: 6, name: '文件' }, { id: 14, name: '分类' },
    { id: 10, name: '模板' }, { id: 828, name: '模块' }, { id: 4, name: '项目' }, { id: 2, name: '用户' }
  ];
  const NS_EXTENDED: NamespaceOption[] = [
    { id: 0, name: '条目' }, { id: 1, name: '讨论' }, { id: 2, name: '用户' }, { id: 3, name: '用户讨论' },
    { id: 4, name: '卡拉彼丘' }, { id: 5, name: '卡拉彼丘讨论' }, { id: 6, name: '文件' }, { id: 7, name: '文件讨论' },
    { id: 8, name: 'MediaWiki' }, { id: 9, name: 'MediaWiki讨论' }, { id: 10, name: '模板' }, { id: 11, name: '模板讨论' },
    { id: 12, name: '帮助' }, { id: 13, name: '帮助讨论' }, { id: 14, name: '分类' }, { id: 15, name: '分类讨论' },
    { id: 102, name: '属性' }, { id: 103, name: '属性讨论' }, { id: 106, name: '表单' }, { id: 107, name: '表单讨论' },
    { id: 108, name: '概念' }, { id: 109, name: '概念讨论' }, { id: 112, name: 'smw/schema' }, { id: 113, name: 'smw/schema talk' },
    { id: 114, name: 'Rule' }, { id: 115, name: 'Rule talk' }, { id: 274, name: 'Widget' }, { id: 275, name: 'Widget talk' },
    { id: 828, name: '模块' }, { id: 829, name: '模块讨论' }, { id: 2300, name: 'Topic' }
  ];
  const PROFILE_NS_MAP: Record<Exclude<ProfileValue, 'advanced'>, number[]> = { default: [0], images: [6], all: NS_EXTENDED.map(ns => ns.id) };
  const SORT_OPTIONS: Array<{ value: SortValue; name: string }> = [
    { value: 'relevance', name: '相关度' },
    { value: 'last_edit_desc', name: '最近编辑' },
    { value: 'last_edit_asc', name: '最早编辑' },
    { value: 'create_timestamp_desc', name: '最新创建' },
    { value: 'incoming_links_desc', name: '最多链接' }
  ];

  const nsNameMap: Record<number, string> = Object.fromEntries(NS_EXTENDED.map(ns => [ns.id, ns.name]));
  const requestCache = new Map<string, { response: Response; time: number }>();
  let query = '';
  let inputValue = '';
  let activeProfile: ProfileValue = 'default';
  let selectedNS: number[] = [0];
  let nsExpanded = false;
  let activeSort: SortValue = 'relevance';
  let currentPage = 1;
  let totalHits = 0;
  let searchModePrefix = '';
  let searchModeLabel = '内容';
  let modeOpen = false;
  let suggestions: Suggestion[] = [];
  let suggestIdx = -1;
  let searchTimer: ReturnType<typeof setTimeout> | undefined;
  let status: Status = 'idle';
  let errorMessage = '';
  let results: SearchResult[] = [];
  let resultSuggestion = '';
  let lightboxSrc = '';
  let lightboxOpen = false;
  let lightboxLoaded = false;
  let lightboxScale = 1;
  let lightboxTranslateX = 0;
  let lightboxTranslateY = 0;
  let lightboxDragging = false;
  let lightboxDragStartX = 0;
  let lightboxDragStartY = 0;
  let lightboxStartTranslateX = 0;
  let lightboxStartTranslateY = 0;
  let lightboxDownloading = false;

  $: nsList = nsExpanded ? NS_EXTENDED : NS_PRIMARY;
  $: totalPages = Math.ceil(totalHits / PAGE_SIZE);
  $: pages = paginationPages(currentPage, totalPages);

  onMount(() => {
    const urlQ = new URLSearchParams(location.search).get('q');
    if (urlQ) {
      inputValue = urlQ;
      query = urlQ;
      doSearch();
    }
  });

  function setProfile(value: ProfileValue): void {
    activeProfile = value;
    if (value !== 'advanced') {
      selectedNS = [...PROFILE_NS_MAP[value]];
      nsExpanded = false;
    }
    currentPage = 1;
    if (query.trim()) doSearch();
  }

  function setSort(value: SortValue): void {
    activeSort = value;
    currentPage = 1;
    if (query.trim()) doSearch();
  }

  function setMode(prefix: string, label: string): void {
    searchModePrefix = prefix;
    searchModeLabel = label;
    modeOpen = false;
  }

  function toggleNamespace(id: number): void {
    selectedNS = selectedNS.includes(id) ? selectedNS.filter(item => item !== id) : [...selectedNS, id];
    currentPage = 1;
    if (query.trim()) doSearch();
  }

  function toggleAllNamespaces(): void {
    const allChecked = nsList.every(ns => selectedNS.includes(ns.id));
    selectedNS = allChecked
      ? selectedNS.filter(id => !nsList.some(ns => ns.id === id))
      : Array.from(new Set([...selectedNS, ...nsList.map(ns => ns.id)]));
    currentPage = 1;
    if (query.trim()) doSearch();
  }

  function getSearchQuery(): string {
    const q = query.trim();
    if (!q) return '';
    if (q.startsWith('intitle:') || q.startsWith('insource:')) return q;
    return searchModePrefix + q;
  }

  function getActiveNSParam(): string {
    if (activeProfile === 'advanced') {
      const ns = selectedNS.filter(id => id >= 0);
      return ns.length > 0 ? ns.join('|') : '0';
    }
    return PROFILE_NS_MAP[activeProfile].join('|');
  }

  function handleInput(): void {
    query = inputValue;
    clearTimeout(searchTimer);
    if (!query.trim()) {
      suggestions = [];
      suggestIdx = -1;
      return;
    }
    searchTimer = setTimeout(fetchSuggestions, DEBOUNCE_MS);
  }

  function handleKeydown(event: KeyboardEvent): void {
    if (suggestions.length > 0 && event.key === 'ArrowDown') {
      event.preventDefault();
      suggestIdx = Math.min(suggestIdx + 1, suggestions.length - 1);
      inputValue = suggestions[suggestIdx].title;
      return;
    }
    if (suggestions.length > 0 && event.key === 'ArrowUp') {
      event.preventDefault();
      if (suggestIdx <= 0) {
        suggestIdx = -1;
        inputValue = query;
      } else {
        suggestIdx -= 1;
        inputValue = suggestions[suggestIdx].title;
      }
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      query = inputValue;
      suggestions = [];
      currentPage = 1;
      doSearch();
    }
    if (event.key === 'Escape') {
      if (suggestions.length > 0) {
        suggestions = [];
        inputValue = query;
      } else if (query) {
        clearSearch();
      }
    }
  }

  function clearSearch(): void {
    inputValue = '';
    query = '';
    suggestions = [];
    status = 'idle';
    totalHits = 0;
    results = [];
    resultSuggestion = '';
  }

  async function fetchSuggestions(): Promise<void> {
    try {
      const params = new URLSearchParams({ action: 'opensearch', search: getSearchQuery(), limit: String(SUGGEST_LIMIT), namespace: getActiveNSParam() || '0', format: 'json' });
      const resp = await fetchWithTimeout(`${API}?${params}`, 5000);
      const data = await resp.json() as OpenSearchResponse;
      suggestions = (data[1] || []).map((title: string, index: number) => ({ title, desc: data[2]?.[index] || '', url: data[3]?.[index] || '' }));
      suggestIdx = -1;
    } catch {
      suggestions = [];
    }
  }

  async function doSearch(): Promise<void> {
    if (!query.trim()) return;
    status = 'loading';
    errorMessage = '';
    resultSuggestion = '';
    suggestions = [];

    try {
      const params = new URLSearchParams({
        action: 'query', list: 'search', srsearch: getSearchQuery(), srlimit: String(PAGE_SIZE),
        sroffset: String((currentPage - 1) * PAGE_SIZE), srnamespace: getActiveNSParam(), srinfo: 'totalhits|suggestion',
        srprop: 'snippet|timestamp|wordcount|sectiontitle|redirecttitle|size', srsort: activeSort, format: 'json', origin: '*'
      });
      const resp = await fetchWithTimeout(`${API}?${params}`, 10000);
      const data = await resp.json() as SearchResponse;
      const apiErr = apiErrorMessage(data);
      if (apiErr) throw new Error(apiErr);

      const search: WikiSearchItem[] = data.query?.search || [];
      const info = data.query?.searchinfo || {};
      totalHits = info.totalhits || 0;
      resultSuggestion = info.suggestion || '';
      if (search.length === 0) {
        results = [];
        status = 'empty';
        return;
      }

      const titles = search.map(item => item.title);
      const nsMap = Object.fromEntries(search.map(item => [item.title, item.ns]));
      const [fileAssets, extra] = await Promise.all([fetchFileAssets(titles, nsMap), fetchPageExtra(titles)]);
      results = search.map((item, index) => normalizeResult(item, fileAssets.images[item.title], fileAssets.sizes[item.title], extra[item.title], index));
      status = 'ready';
    } catch (err) {
      const error = toError(err);
      status = 'error';
      errorMessage = error.name === 'AbortError' ? '搜索请求超时，请检查网络或换个关键词重试' : error.message || '搜索出错，请稍后重试';
    }
  }

  function normalizeResult(item: WikiSearchItem, image: ResultImage | undefined, fileSize: number | undefined, extra: { categories?: string[] } | undefined, index: number): SearchResult {
    const date = new Date(item.timestamp);
    return {
      ...item,
      image,
      categories: extra?.categories || [],
      url: WIKI_BASE + encodeURIComponent(item.title.replace(/ /g, '_')),
      nsName: nsNameMap[item.ns] || '',
      dateStr: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`,
      pageSizeKB: item.size ? formatFileSize(item.size) : '',
      fileSize: fileSize ? formatFileSize(fileSize) : '',
      delay: `${index * 0.03}s`
    };
  }

  async function fetchFileAssets(titles: string[], nsMap: Record<string, number>): Promise<{ images: Record<string, ResultImage>; sizes: Record<string, number> }> {
    const images: Record<string, ResultImage> = {};
    const sizes: Record<string, number> = {};
    const piThumbs: Record<string, string> = {};
    try {
      const params = new URLSearchParams({ action: 'query', prop: 'pageimages', piprop: 'thumbnail', pithumbnail: '120', pilicense: 'any', titles: titles.join('|'), format: 'json', origin: '*' });
      const data = await (await fetchWithTimeout(`${API}?${params}`, 8000)).json() as PagesResponse;
      for (const page of Object.values(data.query?.pages || {})) if (page.thumbnail?.source) piThumbs[page.title] = page.thumbnail.source;
    } catch {}

    const fileTitles = titles.filter(title => nsMap[title] === 6);
    if (fileTitles.length > 0) {
      try {
        const params = new URLSearchParams({ action: 'query', prop: 'imageinfo', iiprop: 'url|size|mime', iiurlwidth: '120', titles: fileTitles.map(title => 'File:' + title.replace(/^文件:/, '')).join('|'), format: 'json', origin: '*' });
        const data = await (await fetchWithTimeout(`${API}?${params}`, 8000)).json() as PagesResponse;
        for (const page of Object.values(data.query?.pages || {})) {
          const info = page.imageinfo?.[0];
          const origTitle = page.title.replace(/^文件:/, '');
          if (info?.size) {
            sizes[origTitle] = info.size;
            sizes[page.title] = info.size;
          }
          if (info?.mime?.startsWith('image/')) {
            images[origTitle] = { thumb: info.thumburl || info.url, full: info.url, size: info.size };
            images[page.title] = images[origTitle];
          }
        }
      } catch {}
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
      } catch {}
    }

    for (const title of titles) if (!images[title] && piThumbs[title]) images[title] = { thumb: piThumbs[title], full: piThumbs[title] };
    return { images, sizes };
  }

  function formatFileSize(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes <= 0) return '';
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let unitIndex = 0;

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex += 1;
    }

    return `${size.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
  }

  async function fetchPageExtra(titles: string[]): Promise<Record<string, { categories: string[] }>> {
    try {
      const params = new URLSearchParams({ action: 'query', prop: 'categories', cllimit: '5', clshow: '!hidden', titles: titles.join('|'), format: 'json', origin: '*' });
      const data = await (await fetchWithTimeout(`${API}?${params}`, 8000)).json() as PagesResponse;
      return Object.fromEntries(Object.values(data.query?.pages || {}).map(page => [page.title, { categories: (page.categories || []).map(category => category.title.replace(/^分类:/, '')) }]));
    } catch {
      return {};
    }
  }

  async function fetchWithTimeout(url: string, timeoutMs: number): Promise<Response> {
    const cached = requestCache.get(url);
    if (cached && Date.now() - cached.time < 60000) return cached.response.clone();
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetch(url, { signal: controller.signal, headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error(httpErrorMessage(response.status));
      requestCache.set(url, { response: response.clone(), time: Date.now() });
      return response;
    } finally {
      clearTimeout(timer);
    }
  }

  function apiErrorMessage(data: SearchResponse): string | null {
    const err = data?.errors?.[0];
    if (!err) return null;
    const codeMap: Record<string, string> = { 'search-title-disabled': '当前命名空间不支持搜索', 'search-text-disabled': '全文搜索未启用', toomanyconcurrent: '并发请求过多，请稍后再试', ratelimited: '请求频率受限，请稍后再试' };
    return (err.code ? codeMap[err.code] : undefined) || err.info || err.code || '接口返回错误';
  }

  function httpErrorMessage(statusCode: number): string {
    const map: Record<number, string> = { 400: '请求参数有误', 403: '访问被拒绝，可能被反爬限制', 404: 'Wiki API 接口不存在', 429: '请求过于频繁，请稍后再试', 500: 'Wiki 服务器内部错误', 502: 'Wiki 服务器网关异常', 503: 'Wiki 服务暂时不可用', 504: 'Wiki 服务器响应超时' };
    return map[statusCode] || `HTTP ${statusCode} 错误`;
  }

  function paginationPages(page: number, total: number): Array<number | '...'> {
    if (total <= 1) return [];
    const output: Array<number | '...'> = [];
    const start = Math.max(1, page - 2);
    const end = Math.min(total, page + 2);
    if (start > 1) {
      output.push(1);
      if (start > 2) output.push('...');
    }
    for (let item = start; item <= end; item++) output.push(item);
    if (end < total) {
      if (end < total - 1) output.push('...');
      output.push(total);
    }
    return output;
  }

  function goPage(page: number): void {
    currentPage = page;
    doSearch();
    document.getElementById('results')?.scrollIntoView({ behavior: 'smooth' });
  }

  function openLightbox(src: string): void {
    lightboxSrc = src;
    lightboxOpen = true;
    lightboxLoaded = false;
    lightboxScale = 1;
    lightboxTranslateX = 0;
    lightboxTranslateY = 0;
    document.body.style.overflow = 'hidden';
  }

  function closeLightbox(): void {
    lightboxOpen = false;
    lightboxSrc = '';
    document.body.style.overflow = '';
  }

  function resetLightboxTransform(): void {
    lightboxScale = 1;
    lightboxTranslateX = 0;
    lightboxTranslateY = 0;
  }

  function toggleLightboxZoom(): void {
    if (lightboxScale > 1.05) {
      resetLightboxTransform();
      return;
    }

    lightboxScale = 2.5;
    lightboxTranslateX = 0;
    lightboxTranslateY = 0;
  }

  function handleLightboxWheel(event: WheelEvent): void {
    const oldScale = lightboxScale;
    const nextScale = Math.max(0.5, Math.min(15, lightboxScale * (event.deltaY > 0 ? 0.92 : 1.08)));
    lightboxScale = nextScale;

    if (nextScale <= 1.05) {
      resetLightboxTransform();
      return;
    }

    const ratio = nextScale / oldScale;
    lightboxTranslateX *= ratio;
    lightboxTranslateY *= ratio;
  }

  function startLightboxDrag(event: PointerEvent): void {
    if (lightboxScale <= 1.05) return;
    lightboxDragging = true;
    lightboxDragStartX = event.clientX;
    lightboxDragStartY = event.clientY;
    lightboxStartTranslateX = lightboxTranslateX;
    lightboxStartTranslateY = lightboxTranslateY;
    (event.currentTarget as Element | null)?.setPointerCapture?.(event.pointerId);
  }

  function moveLightboxDrag(event: PointerEvent): void {
    if (!lightboxDragging) return;
    lightboxTranslateX = lightboxStartTranslateX + event.clientX - lightboxDragStartX;
    lightboxTranslateY = lightboxStartTranslateY + event.clientY - lightboxDragStartY;
  }

  function endLightboxDrag(): void {
    lightboxDragging = false;
  }

  function cleanSnippet(html: string): string {
    const div = document.createElement('div');
    div.innerHTML = html || '';
    div.querySelectorAll('.searchmatch').forEach(element => {
      const mark = document.createElement('mark');
      mark.innerHTML = element.innerHTML;
      element.replaceWith(mark);
    });
    return div.innerHTML;
  }

  function highlightMatch(text: string, q: string): string {
    const safeText = esc(text);
    const words = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').split(/\s+/).filter(Boolean);
    if (words.length === 0) return safeText;
    return safeText.replace(new RegExp(`(${words.join('|')})`, 'gi'), '<mark>$1</mark>');
  }

  function esc(value: unknown): string {
    return String(value || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
  }

  function truncate(value: string, length: number): string {
    return value.length > length ? value.slice(0, length) + '…' : value;
  }

  function imageFull(image: ResultImage | undefined): string {
    return image?.full || '';
  }

  function lightboxDownloadName(src: string): string {
    const fallback = 'wiki-image';
    try {
      const pathname = new URL(src, location.href).pathname;
      return decodeURIComponent(pathname.split('/').filter(Boolean).pop() || fallback);
    } catch {
      return fallback;
    }
  }

  async function downloadLightboxImage(): Promise<void> {
    if (!lightboxSrc || lightboxDownloading) return;

    lightboxDownloading = true;
    try {
      const response = await fetch(`/api/image-download?url=${encodeURIComponent(lightboxSrc)}`);
      if (!response.ok) throw new Error(httpErrorMessage(response.status));

      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = lightboxDownloadName(lightboxSrc);
      document.body.append(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(objectUrl);
    } catch {
      open(lightboxSrc, '_blank', 'noopener,noreferrer');
    } finally {
      lightboxDownloading = false;
    }
  }

  function resultSnippet(result: SearchResult): string {
    return result.snippet || '';
  }
</script>

<svelte:window on:keydown={(event) => event.key === 'Escape' && lightboxOpen && closeLightbox()} />

<a class="skip-link" href="#results">跳转到搜索结果</a>

<header class="header">
  <div class="header-content">
    <a href="/" class="header-back" aria-label="返回下载页"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg></a>
    <h1 class="header-title"><img src="/icon.svg" alt="" class="header-logo">卡拉彼丘 Wiki 搜索</h1>
    <a class="header-link" href="https://wiki.biligame.com/klbq/" target="_blank" rel="noopener noreferrer">访问原站</a>
  </div>
</header>

<main class="main">
  <div class="search-box">
    <div class="search-input-wrap">
      <div class:open={modeOpen} class="mode-select">
        <button class="mode-trigger" type="button" aria-expanded={modeOpen} aria-haspopup="listbox" on:click={() => modeOpen = !modeOpen}><span class="mode-value">{searchModeLabel}</span><svg class="mode-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg></button>
        <div class="mode-menu" role="listbox">
          {#each [['', '内容'], ['intitle:', '标题'], ['insource:', '源码']] as [prefix, label]}
            <button class:selected={searchModePrefix === prefix} class="mode-option" type="button" role="option" aria-selected={searchModePrefix === prefix} on:click={() => setMode(prefix, label)}>{label}</button>
          {/each}
        </div>
      </div>
      <input bind:value={inputValue} on:input={handleInput} on:keydown={handleKeydown} type="text" class="search-input" placeholder="搜索角色、武器、地图、技能…" autocomplete="off">
      {#if inputValue}<button class="search-clear" aria-label="清空搜索" on:click={clearSearch}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg></button>{/if}
    </div>
    {#if suggestions.length > 0}
      <div class="suggest-dropdown" role="listbox">
        {#each suggestions as suggestion, index}
          <button class:highlighted={suggestIdx === index} class="suggest-item" type="button" role="option" aria-selected={suggestIdx === index} on:mouseenter={() => suggestIdx = index} on:click={() => { inputValue = suggestion.title; query = suggestion.title; suggestions = []; currentPage = 1; doSearch(); }}>
            <svg class="suggest-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
            <span class="suggest-text"><span class="suggest-title">{@html highlightMatch(suggestion.title, query)}</span>{#if suggestion.desc}<span class="suggest-desc">{truncate(suggestion.desc, 60)}</span>{/if}</span>
          </button>
        {/each}
      </div>
    {/if}
  </div>

  <div class="filters">
    <div class="filter-group"><span class="filter-label">范围</span><div class="chip-group">{#each PROFILES as profile}<button class:active={profile.value === activeProfile} class="chip" title={profile.desc} on:click={() => setProfile(profile.value)}>{profile.name}</button>{/each}</div></div>
    <div class="filter-group"><span class="filter-label">排序</span><div class="chip-group">{#each SORT_OPTIONS as option}<button class:active={option.value === activeSort} class="chip" on:click={() => setSort(option.value)}>{option.name}</button>{/each}</div></div>
    {#if activeProfile === 'advanced'}
      <div class="filter-group namespace-filter"><span class="filter-label">命名空间</span><div class="chip-group"><button class:active={nsList.every(ns => selectedNS.includes(ns.id))} class="chip" on:click={toggleAllNamespaces}>全选</button>{#each nsList as ns}<button class:active={selectedNS.includes(ns.id)} class="chip" on:click={() => toggleNamespace(ns.id)}>{ns.name}</button>{/each}</div><button class="ns-toggle" title="展开全部命名空间" on:click={() => nsExpanded = !nsExpanded}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={nsExpanded ? 'm18 15-6-6-6 6' : 'm6 9 6 6 6-6'}/></svg></button></div>
    {/if}
  </div>

  {#if status === 'ready'}<div class="result-meta">找到 <strong>{totalHits.toLocaleString()}</strong> 条结果{#if resultSuggestion} · 你是不是要搜：<button class="suggestion-link" on:click={() => { inputValue = resultSuggestion; query = resultSuggestion; currentPage = 1; doSearch(); }}>{resultSuggestion}</button>{/if}</div>{/if}

  <div class="results" id="results">
    {#if status === 'idle'}
      <div class="placeholder"><div class="placeholder-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg></div><p>输入关键词开始搜索卡拉彼丘 Wiki</p></div>
    {:else if status === 'loading'}
      {#each Array(5) as _}<div class="result-card skeleton-card"><div class="result-body"><div class="skeleton-line" style="width: 40%; height: 18px;"></div><div class="skeleton-line" style="width: 100%; height: 14px; margin-top: 8px;"></div><div class="skeleton-line" style="width: 80%; height: 14px; margin-top: 4px;"></div><div class="skeleton-line" style="width: 30%; height: 12px; margin-top: 8px;"></div></div></div>{/each}
    {:else if status === 'empty'}
      <div class="empty-state"><div class="empty-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/><path d="m8 11 6 0"/></svg></div><p>未找到「{query}」相关结果</p>{#if resultSuggestion}<p class="empty-hint">你是不是要搜：<button class="suggestion-link" on:click={() => { inputValue = resultSuggestion; query = resultSuggestion; currentPage = 1; doSearch(); }}>{resultSuggestion}</button></p>{:else}<p class="empty-hint">试试换个关键词，或检查拼写</p>{/if}</div>
    {:else if status === 'error'}
      <div class="empty-state"><div class="empty-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 8v4"/><path d="M12 16h.01"/></svg></div><p>{errorMessage}</p><button class="btn outline" style="margin-top: 12px;" on:click={doSearch}>重试</button></div>
    {:else}
      {#each results as result}
        <a class="result-card" href={result.url} target="_blank" rel="noopener noreferrer" style={`animation-delay: ${result.delay}`}>
          {#if result.image}<button type="button" class="result-thumb" on:click|preventDefault|stopPropagation={() => openLightbox(imageFull(result.image))}><img src={result.image.thumb} alt="" loading="lazy"></button>{/if}
          <div class="result-body"><div class="result-title-row">{#if result.nsName}<span class="result-ns">{result.nsName}</span>{/if}<h3 class="result-title">{@html highlightMatch(result.title, query)}</h3></div>{#if result.redirecttitle}<div class="result-redirect">重定向自：<span>{@html highlightMatch(result.redirecttitle, query)}</span></div>{/if}{#if result.sectiontitle}<span class="result-section">§ {result.sectiontitle}</span>{/if}<p class="result-snippet">{@html cleanSnippet(resultSnippet(result))}</p><div class="result-meta-row"><span title="最后编辑">{result.dateStr}</span>{#if result.wordcount}<span title="字数">{result.wordcount.toLocaleString()} 字</span>{/if}{#if result.fileSize}<span title="文件大小">{result.fileSize}</span>{:else if result.pageSizeKB}<span title="页面大小">{result.pageSizeKB}</span>{/if}</div>{#if result.categories.length > 0}<div class="result-cats">{#each result.categories.slice(0, 3) as category}<span class="cat-tag">{category}</span>{/each}</div>{/if}</div>
        </a>
      {/each}
    {/if}
  </div>

  {#if pages.length > 0 && status === 'ready'}<div class="pagination"><button class="page-btn" disabled={currentPage <= 1} on:click={() => goPage(currentPage - 1)}>‹</button>{#each pages as page}<button class:active={page === currentPage} class="page-btn" disabled={page === '...'} on:click={() => typeof page === 'number' && goPage(page)}>{page}</button>{/each}<button class="page-btn" disabled={currentPage >= totalPages} on:click={() => goPage(currentPage + 1)}>›</button></div>{/if}
</main>

<footer class="footer"><p>数据来源：<a href="https://wiki.biligame.com/klbq/" target="_blank" rel="noopener noreferrer">卡拉彼丘 Wiki</a> · Powered by MediaWiki API</p></footer>

{#if lightboxOpen}
  <div class:open={lightboxOpen} class="lightbox"><button class="lightbox-backdrop" aria-label="关闭" on:click={closeLightbox}></button><button class:zoomed={lightboxScale > 1.05} class:dragging={lightboxDragging} class="lightbox-container" type="button" on:dblclick={toggleLightboxZoom} on:wheel|preventDefault={handleLightboxWheel} on:pointerdown={startLightboxDrag} on:pointermove={moveLightboxDrag} on:pointerup={endLightboxDrag} on:pointercancel={endLightboxDrag} on:pointerleave={endLightboxDrag}><img class="lightbox-img" src={lightboxSrc} alt="" style={`transform: translate(${lightboxTranslateX}px, ${lightboxTranslateY}px) scale(${lightboxScale})`} on:load={() => lightboxLoaded = true}>{#if !lightboxLoaded}<div class="lightbox-loading"><div class="lightbox-spinner"></div></div>{/if}</button><button class="lightbox-action lightbox-download" type="button" aria-label="下载图片" title="下载图片" disabled={lightboxDownloading} on:click|stopPropagation={downloadLightboxImage}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/></svg></button><button class="lightbox-action lightbox-close" aria-label="关闭" on:click={closeLightbox}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg></button></div>
{/if}

<style>
  button.mode-trigger,
  button.mode-option,
  button.suggest-item,
  button.result-thumb,
  button.lightbox-backdrop,
  button.lightbox-container {
    border: 0;
    background: transparent;
    color: inherit;
    font: inherit;
    padding: 0;
  }

  button.mode-trigger {
    padding: 0 12px 0 20px;
    border-right: 1px solid var(--border);
  }

  button.mode-option {
    display: block;
    width: 100%;
    text-align: left;
    padding: 8px 12px;
  }

  button.mode-option:hover {
    background-color: var(--accent);
  }

  button.mode-option.selected {
    background-color: var(--primary);
    color: var(--primary-foreground);
    font-weight: 600;
  }

  button.suggest-item {
    width: 100%;
    text-align: left;
    padding: 10px 14px;
  }

  button.suggest-item:hover,
  button.suggest-item.highlighted {
    background-color: var(--accent);
  }

  button.result-thumb {
    display: block;
  }

  button.lightbox-backdrop,
  button.lightbox-container,
  button.lightbox-action {
    position: absolute;
  }

  .lightbox-download {
    right: 68px;
  }

  button.lightbox-backdrop {
    inset: 0;
    z-index: 0;
  }

  button.lightbox-container {
    inset: 0;
    z-index: 1;
  }

  .lightbox-action {
    z-index: 2;
  }

  .lightbox-close {
    right: 16px;
  }

  @media (max-width: 640px) {
    button.mode-trigger {
      padding: 0 8px 0 12px;
    }

    button.mode-option {
      padding: 6px 10px;
    }

    button.suggest-item {
      padding: 9px 12px;
    }

    .lightbox-download {
      right: 60px;
    }

    .lightbox-close {
      right: 12px;
    }
  }

  .lightbox.open {
    background-color: transparent;
  }
</style>
