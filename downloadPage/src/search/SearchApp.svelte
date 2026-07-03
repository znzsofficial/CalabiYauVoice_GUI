<script lang="ts">
  import { onMount } from 'svelte';
  import CategoryFileDialog from './CategoryFileDialog.svelte';
  import Lightbox from './Lightbox.svelte';
  import { downloadBlob, downloadFailuresText, downloadFilesInParallel, fileNameFromTitle, generateZip, uniqueFileName } from './download';
  import { apiErrorMessage, fetchCategoryFiles, fetchCategoryMembers, fetchFileAssets, fetchPageExtra, fetchPrefixSuggestions, formatFileSize, httpErrorMessage, searchWiki, WIKI_BASE, type CategoryFile, type FileAsset, type ResultImage, type Suggestion, type WikiSearchItem } from './searchApi';
  import type { NamespaceOption, ProfileValue, SearchResult, SortValue, Status } from './searchTypes';
  import { toError, categoryDisplayName } from './utils';
  import SearchFilters from './SearchFilters.svelte';
  import SearchBox from './SearchBox.svelte';
  import VoiceSubtitlePanel from './panels/VoiceSubtitlePanel.svelte';
  import WikiSearchPanel from './panels/WikiSearchPanel.svelte';
  import CategoryDownloadPanel from './panels/CategoryDownloadPanel.svelte';

  const PAGE_SIZE = 20;
  const SUGGEST_LIMIT = 8;
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
  let searchRequestId = $state(0);
  let status = $state('idle' as Status);
  let errorMessage = $state('');
  let results = $state([]) as SearchResult[];
  let resultSuggestion = $state('');
  let selectedFiles = $state(new Set<string>());
  let selectedCategoryResults = $state(new Set<string>());
  let zipDownloading = $state(false);
  let zipProgress = $state('');
  let zipAbortController = $state(null) as AbortController | null;
  let downloadConcurrency = $state(4);
  let lightboxSrc = $state('');
  let lightboxOpen = $state(false);
  let lightboxDownloading = $state(false);
  let categoryDownloading = $state(false);
  let categoryAbortController = $state(null) as AbortController | null;
  let categoryStatusText = $state('');
  let categoryShowAllResults = $state(false);
  let categoryAllResults = $state([]) as SearchResult[];
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
  let categoryFilesInFlight = new Map<string, Promise<CategoryFile[]>>();
  let nsList = $derived(nsExpanded ? NS_EXTENDED : NS_PRIMARY);
  let totalPages = $derived(Math.ceil(totalHits / PAGE_SIZE));
  let pages = $derived(paginationPages(currentPage, totalPages));
  let fileResults = $derived(results.filter(result => result.ns === 6 && result.file));
  let selectedFileResults = $derived(fileResults.filter(result => selectedFiles.has(result.title)));
  let fileSelectionEnabled = $derived(activeProfile === 'images' && fileResults.length > 0);
  let categorySearchActive = $derived(activeProfile === 'voiceCategory' || activeProfile === 'categoryDownload');
  let categoryResults = $derived(results.filter(result => result.ns === 14));
  let selectedCategoryResultItems = $derived(categoryResults.filter(result => selectedCategoryResults.has(result.title)));
  let categorySelectionEnabled = $derived(categorySearchActive && categoryResults.length > 0);
  let voiceSubtitleActive = $derived(activeProfile === 'voiceSubtitle');
  let downloadBusy = $derived(zipDownloading || categoryDownloading);
  let totalHitsStr = $derived(totalHits.toLocaleString());
  let categoryResultsCountStr = $derived(categoryResults.length.toLocaleString());
  let categoryAllResultsCountStr = $derived((categoryAllResults.length || categoryResults.length).toLocaleString());

  onMount(() => {
    const urlQ = new URLSearchParams(location.search).get('q');
    if (urlQ) {
      inputValue = urlQ;
      query = urlQ;
      doSearch(urlQ);
    }
  });

  function setProfile(value: ProfileValue): void {
    if (downloadBusy) return;
    if (activeProfile !== value) searchRequestId++;
    activeProfile = value;
    if (value === 'voiceSubtitle') {
      selectedNS = [0];
      nsExpanded = false;
      selectedFiles = new Set();
      selectedCategoryResults = new Set();
      categoryAllResults = [];
      expandedCategories = new Set();
      categorySubcatErrors = {};
      currentPage = 1;
      totalHits = 0;
      resultSuggestion = '';
      errorMessage = '';
      categoryStatusText = '';
      results = [];
      status = 'idle';
      return;
    }
    if (value !== 'advanced') {
      selectedNS = isCategorySearchProfile(value) ? [14] : [...PROFILE_NS_MAP[value]];
      nsExpanded = false;
    }
    selectedFiles = new Set();
    selectedCategoryResults = new Set();
    categoryAllResults = [];
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
    if (downloadBusy) return;
    activeSort = value;
    currentPage = 1;
    if (query.trim()) doSearch();
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
    if (downloadBusy) return;
    selectedNS = selectedNS.includes(id) ? selectedNS.filter(item => item !== id) : [...selectedNS, id];
    currentPage = 1;
    if (query.trim()) doSearch();
  }

  function toggleAllNamespaces(): void {
    if (downloadBusy) return;
    const allChecked = nsList.every(ns => selectedNS.includes(ns.id));
    selectedNS = allChecked
      ? selectedNS.filter(id => !nsList.some(ns => ns.id === id))
      : Array.from(new Set([...selectedNS, ...nsList.map(ns => ns.id)]));
    currentPage = 1;
    if (query.trim()) doSearch();
  }

  function getSearchQuery(value = query): string {
    return buildSearchQuery(value, searchModePrefix);
  }

  function buildSearchQuery(value: string, prefix: string): string {
    const q = value.trim();
    if (!q) return '';
    if (q.startsWith('intitle:') || q.startsWith('insource:')) return q;
    return prefix + q;
  }

  function getActiveNSParam(): string {
    if (activeProfile === 'advanced') {
      const ns = selectedNS.filter(id => id >= 0);
      return ns.length > 0 ? ns.join('|') : '0';
    }
    if (categorySearchActive) return '14';
    return isBuiltinSearchProfile(activeProfile) ? PROFILE_NS_MAP[activeProfile].join('|') : '0';
  }

  function handleSearchInputChange(value: string): void {
    if (voiceSubtitleActive) return;
    query = value;
    if (categorySearchActive) {
      selectedCategoryResults = new Set();
      categoryAllResults = [];
      expandedCategories = new Set();
      categorySubcatErrors = {};
    }
    categoryStatusText = '';
  }

  function clearSearch(): void {
    if (downloadBusy) return;
    inputValue = '';
    query = '';
    status = 'idle';
    totalHits = 0;
    results = [];
    resultSuggestion = '';
    selectedCategoryResults = new Set();
    categoryAllResults = [];
    expandedCategories = new Set();
    categorySubcatErrors = {};
    categoryFileDialogOpen = false;
    categoryStatusText = '';
  }

  async function fetchSearchSuggestions(value: string, modePrefix: string): Promise<Suggestion[]> {
    return await fetchPrefixSuggestions({ search: buildSearchQuery(value, modePrefix), limit: SUGGEST_LIMIT, namespace: getActiveNSParam() || '0', nsNameMap });
  }

  function submitSearch(value: string): void {
    if (voiceSubtitleActive || downloadBusy) return;
    inputValue = value;
    query = value;
    currentPage = 1;
    doSearch(value);
  }

  function searchSuggestion(value: string): void {
    if (downloadBusy) return;
    inputValue = value;
    query = value;
    currentPage = 1;
    doSearch(value);
  }

  async function doSearch(searchValue = query): Promise<void> {
    if (!searchValue.trim()) return;
    query = searchValue;
    const requestProfile = activeProfile;
    const requestCategorySearchActive = requestProfile === 'voiceCategory' || requestProfile === 'categoryDownload';
    const requestNamespace = getActiveNSParam();
    const requestSort = activeSort;
    const requestPage = currentPage;
    const requestSearch = getSearchQuery(searchValue);
    const requestId = ++searchRequestId;
    status = 'loading';
    errorMessage = '';
    resultSuggestion = '';

    try {
      const data = await searchWiki({ search: requestSearch, limit: PAGE_SIZE, offset: (requestPage - 1) * PAGE_SIZE, namespace: requestNamespace, sort: requestSort });
      if (requestId !== searchRequestId || activeProfile !== requestProfile) return;
      const apiErr = apiErrorMessage(data);
      if (apiErr) throw new Error(apiErr);

      const search: WikiSearchItem[] = data.query?.search || [];
      const info = data.query?.searchinfo || {};
      totalHits = info.totalhits || 0;
      resultSuggestion = info.suggestion || '';
      if (search.length === 0) {
        if (requestId !== searchRequestId || activeProfile !== requestProfile) return;
        results = [];
        categoryAllResults = [];
        status = 'empty';
        return;
      }

      let nextResults: SearchResult[];
      if (requestCategorySearchActive) {
        nextResults = search.map((item, index) => normalizeResult(item, undefined, undefined, undefined, index));
        await hydrateCategoryHierarchy(nextResults);
      } else {
        const titles = search.map(item => item.title);
        const nsMap = Object.fromEntries(search.map(item => [item.title, item.ns]));
        const [fileAssets, extra] = await Promise.all([fetchFileAssets(titles, nsMap), fetchPageExtra(titles)]);
        nextResults = search.map((item, index) => normalizeResult(item, fileAssets.images[item.title], fileAssets.files[item.title], extra[item.title], index));
      }
      if (requestId !== searchRequestId || activeProfile !== requestProfile) return;
      if (nextResults.length === 0) {
        results = [];
        categoryAllResults = [];
        status = 'empty';
        return;
      }
      categoryAllResults = requestCategorySearchActive ? nextResults : [];
      results = requestCategorySearchActive ? displayCategoryResults(nextResults) : nextResults;
      const resultTitles = new Set(nextResults.map(r => r.title));
      selectedFiles = new Set([...selectedFiles].filter(t => resultTitles.has(t)));
      selectedCategoryResults = new Set([...selectedCategoryResults].filter(t => resultTitles.has(t)));
      expandedCategories = new Set([...expandedCategories].filter(t => resultTitles.has(t)));
      status = 'ready';
    } catch (err) {
      if (requestId !== searchRequestId || activeProfile !== requestProfile) return;
      const error = toError(err);
      status = 'error';
      errorMessage = error.name === 'AbortError' ? '搜索请求超时，请检查网络或换个关键词重试' : error.message || '搜索出错，请稍后重试';
    }
  }

  async function hydrateCategoryHierarchy(items: SearchResult[]): Promise<void> {
    const categories = items.filter(item => item.ns === 14).map(item => item.title);
    if (categories.length === 0) return;
    const loadedSubcats: Record<string, string[]> = {};
    await Promise.all(categories.map(async category => {
      if (categorySubcats[category]) return;
      try {
        loadedSubcats[category] = await fetchCategoryMembers(category, 14, 'subcat');
      } catch {
        loadedSubcats[category] = [];
      }
    }));
    categorySubcats = { ...categorySubcats, ...loadedSubcats };
  }

  function displayCategoryResults(items: SearchResult[]): SearchResult[] {
    if (categoryShowAllResults) return items;
    const resultTitles = new Set(items.filter(item => item.ns === 14).map(item => item.title));
    const childTitles = new Set<string>();
    for (const result of items) {
      if (result.ns !== 14) continue;
      for (const subcat of categorySubcats[result.title] || []) {
        if (resultTitles.has(subcat)) childTitles.add(subcat);
      }
    }
    return items.filter(item => item.ns !== 14 || !childTitles.has(item.title));
  }

  function setCategoryShowAllResults(value: boolean): void {
    if (downloadBusy) return;
    categoryShowAllResults = value;
    results = displayCategoryResults(categoryAllResults.length > 0 ? categoryAllResults : results);
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

  function toggleFileSelection(title: string): void {
    if (downloadBusy) return;
    const next = new Set(selectedFiles);
    if (next.has(title)) next.delete(title);
    else next.add(title);
    selectedFiles = next;
  }

  function setAllFileSelection(selected: boolean): void {
    if (downloadBusy) return;
    selectedFiles = selected ? new Set(fileResults.map(result => result.title)) : new Set();
  }

  function toggleCategoryResultSelection(title: string): void {
    if (downloadBusy) return;
    const next = new Set(selectedCategoryResults);
    if (next.has(title)) next.delete(title);
    else next.add(title);
    selectedCategoryResults = next;
  }

  function setAllCategoryResultSelection(selected: boolean): void {
    if (downloadBusy) return;
    selectedCategoryResults = selected ? new Set(categoryResults.map(result => result.title)) : new Set();
  }

  async function toggleCategoryExpanded(category: string): Promise<void> {
    if (downloadBusy) return;
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
      if (subcats.length === 0) {
        const collapsed = new Set(expandedCategories);
        collapsed.delete(category);
        expandedCategories = collapsed;
      }
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
      const files = await getCategoryFilesCached(category, audioOnly);
      if (!categoryFileDialogOpen || categoryFileDialogTitle !== category || (activeProfile === 'voiceCategory') !== audioOnly) return;
      categoryFileDialogFiles = files;
    } catch (error) {
      if (!categoryFileDialogOpen || categoryFileDialogTitle !== category || (activeProfile === 'voiceCategory') !== audioOnly) return;
      categoryFileDialogError = toError(error).message || '加载分类文件失败';
    } finally {
      if (categoryFileDialogOpen && categoryFileDialogTitle === category && (activeProfile === 'voiceCategory') === audioOnly) {
        categoryFileDialogLoading = false;
      }
    }
  }

  async function getCategoryFilesCached(category: string, audioOnly: boolean, signal?: AbortSignal): Promise<CategoryFile[]> {
    const cacheKey = categoryFilesCacheKey(category, audioOnly);
    const cached = categoryFilesCache[cacheKey];
    if (cached) return cached;
    let request = categoryFilesInFlight.get(cacheKey);
    if (!request) {
      request = fetchCategoryFiles(category, audioOnly, signal);
      categoryFilesInFlight.set(cacheKey, request);
    }
    try {
      const files = await request;
      categoryFilesCache = { ...categoryFilesCache, [cacheKey]: files };
      return files;
    } finally {
      categoryFilesInFlight.delete(cacheKey);
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
    const controller = new AbortController();
    zipAbortController = controller;
    zipProgress = `准备打包 0/${selectedFileResults.length}`;
    try {
      const zip = await generateZip();
      const usedNames = new Set<string>();
      const files = selectedFileResults.flatMap(result => result.file ? [{ name: fileNameFromTitle(result.title), url: result.file.url, mime: result.file.mime, size: result.file.size }] : []);
      const downloaded = await downloadFilesInParallel(files, downloadConcurrency, progress => {
        zipProgress = progress.failed > 0 ? `正在下载 ${progress.finished}/${progress.total}，失败 ${progress.failed}` : `正在下载 ${progress.finished}/${progress.total}`;
      }, {
        signal: controller.signal
      });
      if (controller.signal.aborted) throw new Error('已取消下载');
      const failures: Array<{ name: string; error: string }> = [];
      let successCount = 0;
      for (const file of downloaded) {
        if (file.ok) {
          zip.file(uniqueFileName(file.name, usedNames), file.blob);
          successCount++;
        } else {
          failures.push({ name: file.name, error: file.error });
        }
      }
      if (successCount === 0) throw new Error(failures.length > 0 ? `下载失败：${failures.length} 个文件均无法获取` : '没有可下载文件');
      if (failures.length > 0) zip.file('_download_failed.txt', downloadFailuresText(failures));

      zipProgress = '正在生成 ZIP';
      const content = await zip.generateAsync({ type: 'blob' });
      downloadBlob(content, `klbq-files-${new Date().toISOString().slice(0, 10)}.zip`);
      zipProgress = failures.length > 0 ? `已打包 ${successCount} 个文件，${failures.length} 个失败` : '';
    } catch (error) {
      zipProgress = toError(error).message || '打包失败';
    } finally {
      zipDownloading = false;
      zipAbortController = null;
    }
  }

  function cancelSelectedFilesZip(): void {
    zipAbortController?.abort();
    zipProgress = '正在取消...';
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
    const controller = new AbortController();
    categoryAbortController = controller;
    categoryStatusText = `准备分类 0/${categories.length}`;
    const audioOnly = activeProfile === 'voiceCategory';
    try {
      const zip = await generateZip();
      let totalFiles = 0;
      const allFailures: Array<{ name: string; error: string; category?: string }> = [];
      for (const [index, category] of categories.entries()) {
        if (controller.signal.aborted) throw new Error('已取消下载');
        const displayName = categoryDisplayName(category);
        categoryStatusText = `正在读取 ${displayName} (${index + 1}/${categories.length})`;
        const files = await getCategoryFilesCached(category, audioOnly, controller.signal);
        if (controller.signal.aborted) throw new Error('已取消下载');
        if (files.length === 0) continue;
        const folder = zip.folder(fileNameFromTitle(displayName));
        const downloaded = await downloadFilesInParallel(files, downloadConcurrency, progress => {
          categoryStatusText = progress.failed > 0
            ? `正在下载 ${displayName} ${progress.finished}/${progress.total}，失败 ${progress.failed}`
            : `正在下载 ${displayName} ${progress.finished}/${progress.total}`;
        }, {
          signal: controller.signal
        });
        if (controller.signal.aborted) throw new Error('已取消下载');
        const usedNames = new Set<string>();
        for (const file of downloaded) {
          if (file.ok) {
            folder?.file(uniqueFileName(file.name, usedNames), file.blob);
            totalFiles += 1;
          } else {
            allFailures.push({ name: file.name, error: file.error, category: displayName });
          }
        }
      }
      if (totalFiles === 0) throw new Error(allFailures.length > 0 ? `下载失败：${allFailures.length} 个文件均无法获取` : '选中分类里没有可下载文件');
      if (allFailures.length > 0) zip.file('_download_failed.txt', downloadFailuresText(allFailures));
      categoryStatusText = '正在生成 ZIP';
      const content = await zip.generateAsync({ type: 'blob' });
      downloadBlob(content, `${activeProfile === 'voiceCategory' ? 'klbq-voice-categories' : 'klbq-categories'}-${new Date().toISOString().slice(0, 10)}.zip`);
      categoryStatusText = allFailures.length > 0 ? `已打包 ${totalFiles} 个文件，${allFailures.length} 个失败` : `已打包 ${totalFiles} 个文件`;
    } catch (error) {
      categoryStatusText = toError(error).message || '分类打包失败';
    } finally {
      categoryDownloading = false;
      categoryAbortController = null;
    }
  }

  function cancelSelectedCategoriesZip(): void {
    categoryAbortController?.abort();
    categoryStatusText = '正在取消...';
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
    if (downloadBusy) return;
    const nextPage = Math.max(1, Math.min(page, totalPages || 1));
    if (nextPage === currentPage) return;
    currentPage = nextPage;
    selectedFiles = new Set();
    selectedCategoryResults = new Set();
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

<main class:main-wide={activeProfile === 'images' || categorySearchActive || voiceSubtitleActive} class="main">
  <div class="search-controls-shell">
    <SearchBox bind:value={inputValue} bind:modePrefix={searchModePrefix} bind:modeLabel={searchModeLabel} {voiceSubtitleActive} disabled={downloadBusy} {status} fetchSuggestions={fetchSearchSuggestions} onInputChange={handleSearchInputChange} onSubmit={submitSearch} onClear={clearSearch} />

    <SearchFilters {activeProfile} {activeSort} {selectedNS} {nsList} {nsExpanded} disabled={downloadBusy} onSetProfile={setProfile} onSetSort={setSort} onToggleNS={toggleNamespace} onToggleAllNS={toggleAllNamespaces} onToggleNSExpanded={() => { if (!downloadBusy) nsExpanded = !nsExpanded; }} />
  </div>

  {#if voiceSubtitleActive}
    <VoiceSubtitlePanel query={inputValue} />
  {/if}

  {#if !voiceSubtitleActive && categorySearchActive}
    <CategoryDownloadPanel activeProfile={activeProfile === 'voiceCategory' ? 'voiceCategory' : 'categoryDownload'} {status} {query} {errorMessage} {results} {categoryResults} {categoryResultsCountStr} {categoryAllResultsCountStr} categoryShowAllResults={categoryShowAllResults} {categorySelectionEnabled} {selectedCategoryResults} {selectedCategoryResultItems} {categoryStatusText} {categoryDownloading} selectionDisabled={downloadBusy} {downloadConcurrency} {expandedCategories} {categorySubcats} {categorySubcatLoading} {categorySubcatErrors} {pages} {currentPage} {totalPages} onRetry={doSearch} onToggleCategory={toggleCategoryResultSelection} onToggleCategoryExpanded={toggleCategoryExpanded} onOpenCategoryFiles={openCategoryFileDialog} onToggleAllCategories={() => setAllCategoryResultSelection(selectedCategoryResultItems.length !== categoryResults.length)} onDownloadCategories={downloadSelectedCategoriesZip} onCancelCategories={cancelSelectedCategoriesZip} onConcurrencyChange={setDownloadConcurrency} onSetCategoryShowAllResults={setCategoryShowAllResults} onGoPage={goPage} />
  {:else if !voiceSubtitleActive}
    <WikiSearchPanel {status} {query} {resultSuggestion} {errorMessage} {results} {totalHitsStr} {fileSelectionEnabled} {fileResults} {selectedFileResults} {selectedFiles} {zipProgress} {zipDownloading} selectionDisabled={downloadBusy} {downloadConcurrency} {pages} {currentPage} {totalPages} onRetry={doSearch} onSuggestion={searchSuggestion} onToggleFile={toggleFileSelection} onOpenLightbox={openLightbox} onToggleAllFiles={() => setAllFileSelection(selectedFileResults.length !== fileResults.length)} onDownloadFiles={downloadSelectedFilesZip} onCancelFiles={cancelSelectedFilesZip} onConcurrencyChange={setDownloadConcurrency} onGoPage={goPage} />
  {/if}
</main>

<footer class="footer"><p>数据来源：<a href="https://wiki.biligame.com/klbq/" target="_blank" rel="noopener noreferrer">卡拉彼丘 Wiki</a> · Powered by MediaWiki API</p></footer>

{#if lightboxOpen}
  <Lightbox src={lightboxSrc} downloading={lightboxDownloading} onClose={closeLightbox} onDownload={downloadLightboxImage} />
{/if}

{#if categoryFileDialogOpen}
  <CategoryFileDialog title={categoryDisplayName(categoryFileDialogTitle)} subtitle={activeProfile === 'voiceCategory' ? '音频文件' : '分类文件'} files={categoryFileDialogFiles} loading={categoryFileDialogLoading} error={categoryFileDialogError} onClose={closeCategoryFileDialog} onPreview={openLightbox} />
{/if}
