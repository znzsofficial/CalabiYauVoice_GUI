<script lang="ts">
  import { onMount } from 'svelte';
  import BulkDownloadBar from './BulkDownloadBar.svelte';
  import CategoryFileDialog from './CategoryFileDialog.svelte';
  import Lightbox from './Lightbox.svelte';
  import SearchResults from './SearchResults.svelte';
  import { downloadBlob, downloadFilesInParallel, fileNameFromTitle, generateZip, uniqueFileName } from './downloadZip';
  import { apiErrorMessage, fetchCategoryFiles, fetchCategoryMembers, fetchFileAssets, fetchPageExtra, fetchPrefixSuggestions, formatFileSize, httpErrorMessage, searchWiki, WIKI_BASE, type CategoryFile, type FileAsset, type ResultImage, type Suggestion, type WikiSearchItem } from './searchApi';
  import { toError, highlightMatch, esc, categoryDisplayName } from './utils';
  import SearchFilters from './SearchFilters.svelte';

  type ProfileValue = 'default' | 'images' | 'all' | 'advanced' | 'voiceCategory' | 'categoryDownload';
  type Status = 'idle' | 'loading' | 'empty' | 'error' | 'ready';
  type SortValue = 'relevance' | 'last_edit_desc' | 'last_edit_asc' | 'create_timestamp_desc' | 'incoming_links_desc';
  type NamespaceOption = { id: number; name: string };
  type SearchResult = WikiSearchItem & {
    title: string;
    ns: number;
    image?: ResultImage;
    file?: FileAsset;
    categories: string[];
    url: string;
    nsName: string;
    dateStr: string;
    pageSizeKB: string;
    fileSize: string;
    wordCountStr: string;
    delay: string;
  };

  const PAGE_SIZE = 20;
  const SUGGEST_LIMIT = 8;
  const DEBOUNCE_MS = 300;

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
  const PROFILE_NS_MAP: Record<'default' | 'images' | 'all', number[]> = { default: [0], images: [6], all: NS_EXTENDED.map(ns => ns.id) };

  const nsNameMap: Record<number, string> = Object.fromEntries(NS_EXTENDED.map(ns => [ns.id, ns.name]));
  let query = $state('');
  let inputValue = $state('');
  let activeProfile = $state('default' as ProfileValue);
  let selectedNS = $state([0]) as number[];
  let nsExpanded = $state(false);
  let activeSort = $state('relevance' as SortValue);
  let currentPage = $state(1);
  let totalHits = $state(0);
  let searchModePrefix = $state('');
  let searchModeLabel = $state('内容');
  let modeOpen = $state(false);
  let suggestions: Suggestion[] = $state([]);
  let suggestionsLoading = $state(false);
  let suggestionsReady = $state(false);
  let suggestionsOpen = $state(false);
  let suggestIdx = $state(-1);
  let savedQuery = $state('');
  let searchTimer: ReturnType<typeof setTimeout> | undefined;
  let suggestionRequestId = $state(0);
  let status = $state('idle' as Status);
  let errorMessage = $state('');
  let results = $state([]) as SearchResult[];
  let resultSuggestion = $state('');
  let selectedFiles = $state(new Set<string>());
  let selectedCategoryResults = $state(new Set<string>());
  let zipDownloading = $state(false);
  let zipProgress = $state('');
  let downloadConcurrency = $state(4);
  let lightboxSrc = $state('');
  let lightboxOpen = $state(false);
  let lightboxDownloading = $state(false);
  let categoryDownloading = $state(false);
  let categoryStatusText = $state('');
  let expandedCategories = $state(new Set<string>());
  let categorySubcats = $state({}) as Record<string, string[]>;
  let categorySubcatLoading = $state(new Set<string>());
  let categorySubcatErrors = $state({}) as Record<string, string>;
  let categoryFilesCache = $state({}) as Record<string, CategoryFile[]>;
  let categoryFileDialogOpen = $state(false);
  let categoryFileDialogTitle = $state('');
  let categoryFileDialogFiles = $state([]) as CategoryFile[];
  let categoryFileDialogLoading = $state(false);
  let categoryFileDialogError = $state('');

  let nsList = $derived(nsExpanded ? NS_EXTENDED : NS_PRIMARY);
  let totalPages = $derived(Math.ceil(totalHits / PAGE_SIZE));
  let pages = $derived(paginationPages(currentPage, totalPages));
  let showSuggestDropdown = $derived(suggestionsOpen && !!query.trim() && (suggestionsLoading || suggestionsReady || suggestions.length > 0));
  let fileResults = $derived(results.filter(result => result.ns === 6 && result.file));
  let selectedFileResults = $derived(fileResults.filter(result => selectedFiles.has(result.title)));
  let fileSelectionEnabled = $derived(activeProfile === 'images' && fileResults.length > 0);
  let categorySearchActive = $derived(activeProfile === 'voiceCategory' || activeProfile === 'categoryDownload');
  let categoryResults = $derived(results.filter(result => result.ns === 14));
  let selectedCategoryResultItems = $derived(categoryResults.filter(result => selectedCategoryResults.has(result.title)));
  let categorySelectionEnabled = $derived(categorySearchActive && categoryResults.length > 0);
  let totalHitsStr = $derived(totalHits.toLocaleString());
  let categoryResultsCountStr = $derived(categoryResults.length.toLocaleString());

  onMount(() => {
    const urlQ = new URLSearchParams(location.search).get('q');
    if (urlQ) {
      inputValue = urlQ;
      query = urlQ;
      doSearch();
    }
    document.addEventListener('click', handleDocumentClick);
    return () => document.removeEventListener('click', handleDocumentClick);
  });

  function setProfile(value: ProfileValue): void {
    activeProfile = value;
    if (value !== 'advanced') {
      selectedNS = isCategorySearchProfile(value) ? [14] : [...PROFILE_NS_MAP[value]];
      nsExpanded = false;
    }
    selectedFiles = new Set();
    selectedCategoryResults = new Set();
    expandedCategories = new Set();
    categorySubcatErrors = {};
    currentPage = 1;
    categoryStatusText = '';
    if (query.trim()) doSearch();
  }

  function isCategorySearchProfile(value: ProfileValue): value is 'voiceCategory' | 'categoryDownload' {
    return value === 'voiceCategory' || value === 'categoryDownload';
  }

  function isBuiltinSearchProfile(value: ProfileValue): value is 'default' | 'images' | 'all' {
    return value === 'default' || value === 'images' || value === 'all';
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

  function normalizeDownloadConcurrency(): void {
    downloadConcurrency = Math.max(1, Math.min(16, Math.floor(Number(downloadConcurrency) || 1)));
  }

  function setDownloadConcurrency(value: number): void {
    downloadConcurrency = value;
    normalizeDownloadConcurrency();
  }

  function categoryFilesCacheKey(category: string, audioOnly: boolean): string {
    return `${audioOnly ? 'audio' : 'all'}:${category}`;
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
    if (categorySearchActive) return '14';
    return isBuiltinSearchProfile(activeProfile) ? PROFILE_NS_MAP[activeProfile].join('|') : '0';
  }

  function handleInput(): void {
    query = inputValue;
    savedQuery = inputValue;
    if (categorySearchActive) {
      selectedCategoryResults = new Set();
      expandedCategories = new Set();
      categorySubcatErrors = {};
    }
    categoryStatusText = '';
    clearTimeout(searchTimer);
    suggestionsReady = false;
    suggestionsOpen = true;
    if (!query.trim()) {
      suggestions = [];
      suggestIdx = -1;
      suggestionsLoading = false;
      return;
    }
    searchTimer = setTimeout(fetchSuggestions, DEBOUNCE_MS);
  }

  function handleInputFocus(): void {
    if (suggestions.length > 0 || suggestionsLoading) suggestionsOpen = true;
    else if (inputValue.trim() && status === 'idle') fetchSuggestions();
  }

  function handleKeydown(event: KeyboardEvent): void {
    if (suggestions.length > 0 && event.key === 'ArrowDown') {
      event.preventDefault();
      if (suggestIdx === -1) savedQuery = inputValue;
      suggestIdx = Math.min(suggestIdx + 1, suggestions.length - 1);
      inputValue = suggestions[suggestIdx].title;
      return;
    }
    if (suggestions.length > 0 && event.key === 'ArrowUp') {
      event.preventDefault();
      if (suggestIdx <= 0) {
        suggestIdx = -1;
        inputValue = savedQuery;
      } else {
        suggestIdx -= 1;
        inputValue = suggestions[suggestIdx].title;
      }
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      query = inputValue;
      closeSuggestions();
      currentPage = 1;
      doSearch();
    }
    if (event.key === 'Escape') {
      if (suggestionsOpen) {
        closeSuggestions();
        inputValue = savedQuery || query;
      } else if (query) {
        clearSearch();
      }
    }
    if (event.key === 'Tab') {
      closeSuggestions();
    }
  }

  function closeSuggestions(): void {
    suggestions = [];
    suggestionsOpen = false;
    suggestIdx = -1;
    savedQuery = '';
  }

  function handleDocumentClick(event: MouseEvent): void {
    if (!suggestionsOpen) return;
    const target = event.target as HTMLElement;
    if (target.closest('.search-box')) return;
    closeSuggestions();
  }

  function handleSuggestMousedown(event: MouseEvent): void {
    event.preventDefault();
  }

  function clearSearch(): void {
    inputValue = '';
    query = '';
    closeSuggestions();
    suggestionsReady = false;
    suggestionsLoading = false;
    status = 'idle';
    totalHits = 0;
    results = [];
    resultSuggestion = '';
    selectedCategoryResults = new Set();
    expandedCategories = new Set();
    categorySubcatErrors = {};
    categoryFileDialogOpen = false;
    categoryStatusText = '';
  }

  async function fetchSuggestions(): Promise<void> {
    const requestId = ++suggestionRequestId;
    suggestionsLoading = true;
    try {
      const nextSuggestions = await fetchPrefixSuggestions({ search: getSearchQuery(), limit: SUGGEST_LIMIT, namespace: getActiveNSParam() || '0', nsNameMap });
      if (requestId !== suggestionRequestId) return;
      suggestions = nextSuggestions;
      suggestIdx = -1;
    } catch {
      if (requestId !== suggestionRequestId) return;
      suggestions = [];
    } finally {
      if (requestId === suggestionRequestId) {
        suggestionsLoading = false;
        suggestionsReady = true;
      }
    }
  }

  function selectSuggestion(suggestion: Suggestion): void {
    inputValue = suggestion.title;
    query = suggestion.title;
    closeSuggestions();
    currentPage = 1;
    doSearch();
  }

  function searchSuggestion(value: string): void {
    inputValue = value;
    query = value;
    currentPage = 1;
    doSearch();
  }

  async function doSearch(): Promise<void> {
    if (!query.trim()) return;
    status = 'loading';
    errorMessage = '';
    resultSuggestion = '';
    suggestions = [];
    suggestionsOpen = false;

    try {
      const data = await searchWiki({ search: getSearchQuery(), limit: PAGE_SIZE, offset: (currentPage - 1) * PAGE_SIZE, namespace: getActiveNSParam(), sort: activeSort });
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
      let nextResults = search.map((item, index) => normalizeResult(item, fileAssets.images[item.title], fileAssets.files[item.title], extra[item.title], index));
      if (categorySearchActive) nextResults = await organizeCategoryResults(nextResults);
      results = nextResults;
      selectedFiles = new Set([...selectedFiles].filter(title => results.some(result => result.title === title && result.file)));
      selectedCategoryResults = new Set([...selectedCategoryResults].filter(title => results.some(result => result.title === title && result.ns === 14)));
      expandedCategories = new Set([...expandedCategories].filter(title => results.some(result => result.title === title && result.ns === 14)));
      status = 'ready';
    } catch (err) {
      const error = toError(err);
      status = 'error';
      errorMessage = error.name === 'AbortError' ? '搜索请求超时，请检查网络或换个关键词重试' : error.message || '搜索出错，请稍后重试';
    }
  }

  function normalizeResult(item: WikiSearchItem, image: ResultImage | undefined, file: FileAsset | undefined, extra: { categories?: string[] } | undefined, index: number): SearchResult {
    const date = new Date(item.timestamp);
    return {
      ...item,
      image,
      file,
      categories: extra?.categories || [],
      url: WIKI_BASE + encodeURIComponent(item.title.replace(/ /g, '_')),
      nsName: nsNameMap[item.ns] || '',
      dateStr: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`,
      pageSizeKB: item.size ? formatFileSize(item.size) : '',
      fileSize: file?.size ? formatFileSize(file.size) : '',
      wordCountStr: item.wordcount ? item.wordcount.toLocaleString() : '',
      delay: `${index * 0.03}s`
    };
  }

  async function organizeCategoryResults(items: SearchResult[]): Promise<SearchResult[]> {
    const categoryItems = items.filter(item => item.ns === 14);
    const audioOnly = activeProfile === 'voiceCategory';
    const nextSubcats: Record<string, string[]> = { ...categorySubcats };
    const nextFiles: Record<string, CategoryFile[]> = { ...categoryFilesCache };

    await Promise.all(categoryItems.map(async item => {
      if (!nextSubcats[item.title]) {
        try {
          nextSubcats[item.title] = await fetchCategoryMembers(item.title, 14, 'subcat');
        } catch {
          nextSubcats[item.title] = [];
        }
      }
      const cacheKey = categoryFilesCacheKey(item.title, true);
      if (audioOnly && !nextFiles[cacheKey]) {
        try {
          nextFiles[cacheKey] = await fetchCategoryFiles(item.title, true);
        } catch {
          nextFiles[cacheKey] = [];
        }
      }
    }));

    categorySubcats = nextSubcats;
    categoryFilesCache = nextFiles;
    return audioOnly ? items.filter(item => item.ns !== 14 || (nextFiles[categoryFilesCacheKey(item.title, true)]?.length || 0) > 0) : items;
  }

  function toggleFileSelection(title: string): void {
    const next = new Set(selectedFiles);
    if (next.has(title)) next.delete(title);
    else next.add(title);
    selectedFiles = next;
  }

  function setAllFileSelection(selected: boolean): void {
    selectedFiles = selected ? new Set(fileResults.map(result => result.title)) : new Set();
  }

  function toggleCategoryResultSelection(title: string): void {
    const next = new Set(selectedCategoryResults);
    if (next.has(title)) next.delete(title);
    else next.add(title);
    selectedCategoryResults = next;
  }

  function setAllCategoryResultSelection(selected: boolean): void {
    selectedCategoryResults = selected ? new Set(categoryResults.map(result => result.title)) : new Set();
  }

  async function toggleCategoryExpanded(category: string): Promise<void> {
    const next = new Set(expandedCategories);
    if (next.has(category)) {
      next.delete(category);
      expandedCategories = next;
      return;
    }

    next.add(category);
    expandedCategories = next;
    if (categorySubcats[category] || categorySubcatLoading.has(category)) return;

    const loading = new Set(categorySubcatLoading);
    loading.add(category);
    categorySubcatLoading = loading;
    categorySubcatErrors = { ...categorySubcatErrors, [category]: '' };
    try {
      const subcats = await fetchCategoryMembers(category, 14, 'subcat');
      categorySubcats = { ...categorySubcats, [category]: subcats };
    } catch (error) {
      categorySubcatErrors = { ...categorySubcatErrors, [category]: toError(error).message || '加载子分类失败' };
    } finally {
      const done = new Set(categorySubcatLoading);
      done.delete(category);
      categorySubcatLoading = done;
    }
  }

  async function openCategoryFileDialog(category: string): Promise<void> {
    categoryFileDialogOpen = true;
    categoryFileDialogTitle = category;
    const audioOnly = activeProfile === 'voiceCategory';
    const cacheKey = categoryFilesCacheKey(category, audioOnly);
    categoryFileDialogFiles = categoryFilesCache[cacheKey] || [];
    categoryFileDialogError = '';
    if (categoryFilesCache[cacheKey]) return;
    categoryFileDialogLoading = true;
    try {
      const files = await fetchCategoryFiles(category, audioOnly);
      categoryFileDialogFiles = files;
      categoryFilesCache = { ...categoryFilesCache, [cacheKey]: files };
    } catch (error) {
      categoryFileDialogError = toError(error).message || '加载分类文件失败';
    } finally {
      categoryFileDialogLoading = false;
    }
  }

  function closeCategoryFileDialog(): void {
    categoryFileDialogOpen = false;
    categoryFileDialogTitle = '';
    categoryFileDialogFiles = [];
    categoryFileDialogError = '';
    categoryFileDialogLoading = false;
  }

  async function downloadSelectedFilesZip(): Promise<void> {
    if (selectedFileResults.length === 0 || zipDownloading) return;

    zipDownloading = true;
    zipProgress = `准备打包 0/${selectedFileResults.length}`;
    try {
      const zip = await generateZip();
      const usedNames = new Set<string>();
      const files = selectedFileResults.flatMap(result => result.file ? [{ name: fileNameFromTitle(result.title), url: result.file.url, mime: result.file.mime, size: result.file.size }] : []);
      const downloaded = await downloadFilesInParallel(files, downloadConcurrency, current => {
        zipProgress = `正在下载 ${current}/${files.length}`;
      });
      for (const file of downloaded) zip.file(uniqueFileName(file.name, usedNames), file.blob);

      zipProgress = '正在生成 ZIP';
      const content = await zip.generateAsync({ type: 'blob' });
      downloadBlob(content, `klbq-files-${new Date().toISOString().slice(0, 10)}.zip`);
      zipProgress = '';
    } catch (error) {
      zipProgress = toError(error).message || '打包失败';
    } finally {
      zipDownloading = false;
    }
  }

  function normalizeCategoryTitle(value: string): string {
    const trimmed = value.trim();
    if (!trimmed) return '';
    return /^(Category:|分类:)/.test(trimmed) ? trimmed : `Category:${trimmed}`;
  }

  async function downloadSelectedCategoriesZip(): Promise<void> {
    const resultTitles = new Set(categoryResults.map(result => result.title));
    const selectedResultCategories = selectedCategoryResultItems.map(result => result.title);
    const selectedSubcategories = [...selectedCategoryResults].filter(category => !resultTitles.has(category));
    const categories = [...selectedResultCategories, ...selectedSubcategories];
    if (categories.length === 0 || categoryDownloading) return;

    categoryDownloading = true;
    categoryStatusText = `准备分类 0/${categories.length}`;
    const audioOnly = activeProfile === 'voiceCategory';
    try {
      const zip = await generateZip();
      let totalFiles = 0;
      for (const [index, category] of categories.entries()) {
        const displayName = categoryDisplayName(category);
        categoryStatusText = `正在读取 ${displayName} (${index + 1}/${categories.length})`;
        const files = await fetchCategoryFiles(category, audioOnly);
        if (files.length === 0) continue;
        const folder = zip.folder(fileNameFromTitle(displayName));
        let finished = 0;
        const downloaded = await downloadFilesInParallel(files, downloadConcurrency, current => {
          finished = current;
          categoryStatusText = `正在下载 ${displayName} ${finished}/${files.length}`;
        });
        const usedNames = new Set<string>();
        for (const file of downloaded) {
          folder?.file(uniqueFileName(file.name, usedNames), file.blob);
          totalFiles += 1;
        }
      }
      if (totalFiles === 0) throw new Error('选中分类里没有可下载文件');
      categoryStatusText = '正在生成 ZIP';
      const content = await zip.generateAsync({ type: 'blob' });
      downloadBlob(content, `${activeProfile === 'voiceCategory' ? 'klbq-voice-categories' : 'klbq-categories'}-${new Date().toISOString().slice(0, 10)}.zip`);
      categoryStatusText = `已打包 ${totalFiles} 个文件`;
    } catch (error) {
      categoryStatusText = toError(error).message || '分类打包失败';
    } finally {
      categoryDownloading = false;
    }
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
    document.body.style.overflow = 'hidden';
  }

  function closeLightbox(): void {
    lightboxOpen = false;
    lightboxSrc = '';
    document.body.style.overflow = '';
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
      downloadBlob(blob, lightboxDownloadName(lightboxSrc));
    } catch {
      open(lightboxSrc, '_blank', 'noopener,noreferrer');
    } finally {
      lightboxDownloading = false;
    }
  }

  function suggestionPath(title: string): string {
    return `/${title.replace(/ /g, '_')}`;
  }
</script>

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
        <button class="mode-trigger" type="button" aria-expanded={modeOpen} aria-haspopup="listbox" onclick={() => modeOpen = !modeOpen}><span class="mode-value">{searchModeLabel}</span><svg class="mode-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg></button>
        <div class="mode-menu" role="listbox">
          {#each [['', '内容'], ['intitle:', '标题'], ['insource:', '源码']] as [prefix, label]}
            <button class:selected={searchModePrefix === prefix} class="mode-option" type="button" role="option" aria-selected={searchModePrefix === prefix} onclick={() => setMode(prefix, label)}>{label}</button>
          {/each}
        </div>
      </div>
      <input bind:value={inputValue} oninput={handleInput} onfocus={handleInputFocus} onkeydown={handleKeydown} type="text" class="search-input" placeholder="搜索角色、武器、地图、技能…" autocomplete="off">
      {#if inputValue}<button class="search-clear" aria-label="清空搜索" onclick={clearSearch}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg></button>{/if}
    </div>
    {#if showSuggestDropdown}
      <div class="suggest-dropdown" role="listbox" tabindex="-1" onmousedown={handleSuggestMousedown}>
        {#if suggestionsLoading}
          <div class="suggest-state"><span class="suggest-spinner"></span><span>正在查找建议…</span></div>
        {:else if suggestions.length > 0}
          {#each suggestions as suggestion, index (suggestion.title)}
            <button class:highlighted={suggestIdx === index} class="suggest-item" type="button" role="option" aria-selected={suggestIdx === index} onmouseenter={() => suggestIdx = index} onclick={() => selectSuggestion(suggestion)}>
              <svg class="suggest-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
              <span class="suggest-text"><span class="suggest-title">{@html highlightMatch(suggestion.title, query)}</span><span class="suggest-meta"><span class="suggest-ns">{suggestion.desc}</span><span>{suggestionPath(suggestion.title)}</span>{#if suggestion.pageid}<span>#{suggestion.pageid}</span>{/if}</span></span>
            </button>
          {/each}
        {:else}
          <div class="suggest-state">暂无实时建议，按 Enter 搜索</div>
        {/if}
      </div>
    {/if}
  </div>

  <SearchFilters {activeProfile} {activeSort} {selectedNS} {nsList} {nsExpanded} onSetProfile={setProfile} onSetSort={setSort} onToggleNS={toggleNamespace} onToggleAllNS={toggleAllNamespaces} onToggleNSExpanded={() => nsExpanded = !nsExpanded} />

  {#if status === 'ready'}
    <div class="result-meta">
      {#if categorySearchActive}
        找到 <strong>{categoryResultsCountStr}</strong> 个分类
      {:else}
        找到 <strong>{totalHitsStr}</strong> 条结果
      {/if}
      {#if resultSuggestion && !categorySearchActive}
        · 你是不是要搜：<button class="suggestion-link" onclick={() => { inputValue = resultSuggestion; query = resultSuggestion; currentPage = 1; doSearch(); }}>{resultSuggestion}</button>
      {/if}
    </div>
  {/if}

  {#if categorySelectionEnabled && status === 'ready'}
    <BulkDownloadBar title={activeProfile === 'voiceCategory' ? '语音分类打包' : '分类打包'} info={selectedCategoryResults.size > 0 ? `已选择 ${selectedCategoryResults.size} 个分类` : `本页 ${categoryResults.length} 个分类可选`} progress={categoryStatusText} allSelected={selectedCategoryResultItems.length === categoryResults.length} disabled={selectedCategoryResults.size === 0} downloading={categoryDownloading} concurrency={downloadConcurrency} downloadingLabel="打包中…" onToggleAll={() => setAllCategoryResultSelection(selectedCategoryResultItems.length !== categoryResults.length)} onDownload={downloadSelectedCategoriesZip} onConcurrencyChange={setDownloadConcurrency} />
  {/if}

  {#if !categorySearchActive && status === 'ready' && fileSelectionEnabled}
    <BulkDownloadBar title="批量下载" info={selectedFileResults.length > 0 ? `已选择 ${selectedFileResults.length} 个文件` : `本页 ${fileResults.length} 个文件可选`} progress={zipProgress} allSelected={selectedFileResults.length === fileResults.length} disabled={selectedFileResults.length === 0} downloading={zipDownloading} concurrency={downloadConcurrency} downloadingLabel="打包中…" onToggleAll={() => setAllFileSelection(selectedFileResults.length !== fileResults.length)} onDownload={downloadSelectedFilesZip} onConcurrencyChange={setDownloadConcurrency} />
  {/if}

  {#if status === 'ready' || status === 'loading' || status === 'empty' || status === 'error' || !categorySearchActive}
    <SearchResults {status} {query} {resultSuggestion} {errorMessage} {results} {fileSelectionEnabled} {categorySelectionEnabled} {categorySearchActive} {selectedFiles} {selectedCategoryResults} {expandedCategories} {categorySubcats} {categorySubcatLoading} {categorySubcatErrors} onRetry={doSearch} onSuggestion={searchSuggestion} onToggleFile={toggleFileSelection} onToggleCategory={toggleCategoryResultSelection} onOpenLightbox={openLightbox} onToggleCategoryExpanded={toggleCategoryExpanded} onOpenCategoryFiles={openCategoryFileDialog} />
  {/if}

  {#if pages.length > 0 && status === 'ready'}<div class="pagination"><button class="page-btn" disabled={currentPage <= 1} onclick={() => goPage(currentPage - 1)}>‹</button>{#each pages as page}<button class:active={page === currentPage} class="page-btn" disabled={page === '...'} onclick={() => typeof page === 'number' && goPage(page)}>{page}</button>{/each}<button class="page-btn" disabled={currentPage >= totalPages} onclick={() => goPage(currentPage + 1)}>›</button></div>{/if}
</main>

<footer class="footer"><p>数据来源：<a href="https://wiki.biligame.com/klbq/" target="_blank" rel="noopener noreferrer">卡拉彼丘 Wiki</a> · Powered by MediaWiki API</p></footer>

{#if lightboxOpen}
  <Lightbox src={lightboxSrc} downloading={lightboxDownloading} onClose={closeLightbox} onDownload={downloadLightboxImage} />
{/if}

{#if categoryFileDialogOpen}
  <CategoryFileDialog title={categoryDisplayName(categoryFileDialogTitle)} subtitle={activeProfile === 'voiceCategory' ? '音频文件' : '分类文件'} files={categoryFileDialogFiles} loading={categoryFileDialogLoading} error={categoryFileDialogError} onClose={closeCategoryFileDialog} onPreview={openLightbox} />
{/if}

<style>
  button.mode-trigger,
  button.mode-option,
  button.suggest-item {
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

  }
</style>
