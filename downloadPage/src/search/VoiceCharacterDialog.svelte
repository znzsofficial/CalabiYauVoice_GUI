<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchVoicePageParsetree, resolveAudioUrl, type CategoryPage, type VoiceLine } from './searchApi';
  import { parseVoiceSections, type VoiceSectionGroup } from './voiceParser';
  import { toError } from './utils';
  import { downloadBlob, generateZip, uniqueFileName } from './downloadZip';
  import { mergeMp3Buffers, buildFolderPath, addSubtitlesToZip } from './voiceDownload';
  import AudioPlayButton from './AudioPlayButton.svelte';

  type LangKey = 'cn' | 'jp' | 'en';
  type Status = 'loading' | 'ready' | 'error';

  let {
    dialogRef = $bindable(null),
    character,
    onClose = () => {}
  }: {
    dialogRef?: HTMLDialogElement | null;
    character: CategoryPage;
    onClose?: () => void;
  } = $props();

  let status = $state('loading' as Status);
  let errorMessage = $state('');
  let sectionGroups = $state([]) as VoiceSectionGroup[];
  let activeSectionIdx = $state(0);
  let voiceLines = $state([]) as VoiceLine[];
  let searchQuery = $state('');
  let filterHasAudio = $state('all' as 'all' | LangKey);
  let subtitleMode = $state('merged' as 'merged' | 'perLine' | 'none');
  let subtitleFormat = $state('full' as 'full' | 'plain');
  let folderMode = $state('none' as 'none' | 'lang' | 'category' | 'both');
  let mergeAudio = $state(false);
  let selectedIndices = $state(new Set<number>());
  let downloadLangs = $state(new Set<LangKey>(['cn', 'jp', 'en']));
  let namingMode = $state('both' as 'both' | 'category' | 'subtitle' | 'original');
  let activeTab = $state(new Map<number, LangKey>());
  let downloading = $state(false);
  let downloadProgress = $state('');
  let downloadConcurrency = $state(4);

  const langLabels: Record<LangKey, string> = { cn: '中文', jp: '日文', en: '英文' };
  const langShort: Record<LangKey, string> = { cn: '中', jp: '日', en: '英' };

  let mergeFileCount = $derived.by(() => {
    let count = 0;
    for (const i of selectedIndices) {
      const line = voiceLines[i];
      if (line) for (const lang of downloadLangs) if (getAudio(line, lang)) count++;
    }
    return count;
  });

  let downloadFileCount = $derived.by(() => {
    let count = 0;
    for (const i of selectedIndices) {
      const line = voiceLines[i];
      if (line) for (const lang of downloadLangs) if (getAudio(line, lang)) count++;
    }
    return count;
  });

  function closeOnBackdrop(event: MouseEvent): void {
    if (event.target === dialogRef) { dialogRef?.close(); onClose(); }
  }
  function handleClose(): void { dialogRef?.close(); onClose(); }
  function getVoicePageTitle(charName: string): string { return `${charName}/语音台词`; }

  function getTabLang(index: number): LangKey {
    if (!activeTab.has(index)) {
      for (const lang of ['cn', 'jp', 'en'] as LangKey[]) if (getText(voiceLines[index], lang)) return lang;
      return 'cn';
    }
    return activeTab.get(index)!;
  }
  function setTabLang(index: number, lang: LangKey): void { activeTab = new Map(activeTab.set(index, lang)); }

  function getAudio(line: VoiceLine, lang: LangKey): string {
    const key = lang === 'cn' ? 'cnAudio' : lang === 'jp' ? 'jpAudio' : 'enAudio';
    return (line as Record<string, string>)[key] || '';
  }
  function getText(line: VoiceLine, lang: LangKey): string {
    const key = lang === 'cn' ? 'cnText' : lang === 'jp' ? 'jpText' : 'enText';
    return (line as Record<string, string>)[key] || '';
  }
  function resolvePlaySrc(line: VoiceLine, lang: LangKey): string {
    const fn = getAudio(line, lang);
    return fn ? resolveAudioUrl(fn) : '';
  }
  function toggleLine(index: number): void {
    const next = new Set(selectedIndices);
    next.has(index) ? next.delete(index) : next.add(index);
    selectedIndices = next;
  }
  function toggleGroup(indices: number[]): void {
    if (indices.length === 0) return;
    const allSelected = indices.every(i => selectedIndices.has(i));
    const next = new Set(selectedIndices);
    if (allSelected) { for (const i of indices) next.delete(i); }
    else { for (const i of indices) next.add(i); }
    selectedIndices = next;
  }
  function toggleAll(): void {
    selectedIndices = selectedIndices.size === voiceLines.length ? new Set() : new Set(voiceLines.map((_, i) => i));
  }
  function toggleLang(lang: LangKey): void {
    const next = new Set(downloadLangs);
    next.has(lang) ? next.delete(lang) : next.add(lang);
    downloadLangs = next;
  }
  function hasAnyAudio(line: VoiceLine): boolean {
    for (const lang of downloadLangs) if (getAudio(line, lang)) return true;
    return false;
  }
  function safeFileName(text: string, fallback: string): string {
    if (!text) return fallback;
    return text.replace(/[<>:"/\\|?*]/g, '').replace(/\s+/g, '_').slice(0, 80) || fallback;
  }

  async function handleDownload(): Promise<void> {
    const lines = voiceLines.filter((_, i) => selectedIndices.has(i) && hasAnyAudio(_));
    if (lines.length === 0 || downloading) return;
    downloading = true; downloadProgress = '准备下载...';
    try {
      const tasks: Array<{ line: VoiceLine; lang: LangKey; audioName: string }> = [];
      for (const line of lines) {
        for (const lang of downloadLangs) {
          const audioName = getAudio(line, lang);
          if (audioName) tasks.push({ line, lang, audioName });
        }
      }
      if (tasks.length === 0) { downloading = false; return; }

      const limit = Math.max(1, Math.min(12, downloadConcurrency));

      if (mergeAudio) {
        const chunks: ArrayBuffer[] = [];
        let failed = 0;
        for (let i = 0; i < tasks.length; i += limit) {
          const batch = tasks.slice(i, i + limit);
          const results = await Promise.all(batch.map(async task => {
            try {
              const resp = await fetch(`/api/file-download?url=${encodeURIComponent(resolveAudioUrl(task.audioName))}`);
              if (!resp.ok) return null;
              return await resp.arrayBuffer();
            } catch { return null; }
          }));
          for (const buf of results) {
            if (buf) chunks.push(buf);
            else failed++;
          }
          downloadProgress = `正在下载 ${Math.min(i + limit, tasks.length)}/${tasks.length}`;
        }
        if (chunks.length === 0) {
          downloadProgress = `下载失败：${failed} 个文件均无法获取`;
          return;
        }
        downloadProgress = '正在合并 MP3...';
        const merged = mergeMp3Buffers(chunks);
        const zip = await generateZip();
        zip.file(`${character.title}-合并.mp3`, new Blob([merged as BlobPart], { type: 'audio/mpeg' }));
        addSubtitlesToZip(zip, lines, downloadLangs, getText, { subtitleMode, subtitleFormat, folderMode }, uniqueFileName);
        downloadProgress = '正在生成 ZIP...';
        const content = await zip.generateAsync({ type: 'blob' });
        downloadBlob(content, `${character.title}-语音-${new Date().toISOString().slice(0, 10)}.zip`);
        downloadProgress = `已合并 ${chunks.length} 个文件`;
      } else {
        const zip = await generateZip(); const usedNames = new Set<string>();
        const catCounters: Record<string, number> = {};
        let finished = 0;

        for (let i = 0; i < tasks.length; i += limit) {
          const batch = tasks.slice(i, i + limit);
          await Promise.all(batch.map(async task => {
            try {
              const resp = await fetch(`/api/file-download?url=${encodeURIComponent(resolveAudioUrl(task.audioName))}`);
              if (!resp.ok) return;
              const blob = await resp.blob();
              if (blob.size === 0) return;
              let name: string;
              if (namingMode === 'both') {
                name = `${safeFileName(task.line.category, '')}_${safeFileName(getText(task.line, task.lang), task.audioName)}.mp3`;
              } else if (namingMode === 'category') {
                const key = task.line.category || 'voice';
                catCounters[key] = (catCounters[key] || 0) + 1;
                name = `${safeFileName(key, '')}_${String(catCounters[key]).padStart(3, '0')}.mp3`;
              } else if (namingMode === 'subtitle') {
                name = `${safeFileName(getText(task.line, task.lang), task.audioName)}.mp3`;
              } else {
                name = task.audioName;
              }
              zip.file(buildFolderPath(folderMode, task.lang, task.line.category, uniqueFileName(name, usedNames)), blob);
              finished++;
            } catch { /* skip */ }
          }));
          downloadProgress = `正在下载 ${Math.min(i + limit, tasks.length)}/${tasks.length}`;
        }

        if (finished === 0) {
          downloadProgress = '下载失败：所有音频文件均无法获取';
          return;
        }

        addSubtitlesToZip(zip, lines, downloadLangs, getText, { subtitleMode, subtitleFormat, folderMode }, uniqueFileName);
        downloadProgress = '正在生成 ZIP...';
        const content = await zip.generateAsync({ type: 'blob' });
        downloadBlob(content, `${character.title}-语音-${new Date().toISOString().slice(0, 10)}.zip`);
        downloadProgress = `已打包 ${finished} 个文件`;
      }
    } catch (err) {
      downloadProgress = toError(err).message || '下载失败';
    } finally { downloading = false; }
  }

  function selectSection(index: number): void {
    activeSectionIdx = index;
    selectedIndices = new Set();
    searchQuery = '';
    filterHasAudio = 'all';
    const group = sectionGroups[index];
    voiceLines = group ? group.lines : [];
  }

  function lineSearchText(line: VoiceLine): string {
    return [
      line.category,
      getText(line, 'cn'), getText(line, 'jp'), getText(line, 'en'),
      getAudio(line, 'cn'), getAudio(line, 'jp'), getAudio(line, 'en')
    ].join('\n').toLowerCase();
  }

  function lineMatchesSearch(line: VoiceLine, q: string): boolean {
    if (filterHasAudio !== 'all' && !getAudio(line, filterHasAudio)) return false;
    if (q) return lineSearchText(line).includes(q);
    return true;
  }

  function groupedLines(): Array<{ category: string; lines: Array<{ line: VoiceLine; globalIndex: number }> }> {
    const q = searchQuery.trim().toLowerCase();
    const groups: Array<{ category: string; lines: Array<{ line: VoiceLine; globalIndex: number }> }> = [];
    let current: typeof groups[0] | null = null;
    for (const [i, line] of voiceLines.entries()) {
      if (!lineMatchesSearch(line, q)) continue;
      if (!current || current.category !== line.category) {
        current = { category: line.category, lines: [] };
        groups.push(current);
      }
      current.lines.push({ line, globalIndex: i });
    }
    return groups;
  }

  function matchedCount(): number {
    const q = searchQuery.trim().toLowerCase();
    let count = 0;
    for (const line of voiceLines) { if (lineMatchesSearch(line, q)) count++; }
    return count;
  }

  onMount(async () => {
    dialogRef?.showModal();
    try {
      const pageTitle = getVoicePageTitle(character.title);
      const parsetree = await fetchVoicePageParsetree(pageTitle);
      sectionGroups = parseVoiceSections(parsetree);
      if (sectionGroups.length > 0) {
        selectSection(0);
        status = 'ready';
      } else {
        status = 'error';
        errorMessage = '未解析到语音台词数据';
      }
    } catch (err) {
      status = 'error';
      errorMessage = toError(err).message || '加载语音台词失败';
    }
  });
</script>

<dialog class="voice-dialog" bind:this={dialogRef} onclick={closeOnBackdrop}>
  <div class="voice-dialog-inner">
    <div class="voice-dialog-header">
      <h2 class="voice-dialog-title">
        {#if character.thumbnail}
          <img class="voice-dialog-avatar" src={character.thumbnail} alt="">
        {/if}
        <iconify-icon icon="lucide:volume-2"></iconify-icon>
        {character.title}
        <span class="badge voice-dialog-badge">语音字幕</span>
      </h2>
      <button class="btn outline" onclick={handleClose}>
        <iconify-icon icon="lucide:x" style="margin-right:4px;font-size:1rem;"></iconify-icon>返回
      </button>
    </div>

    {#if status === 'loading'}
      <div class="voice-section-tabs voice-section-tabs-loading">
        {#each Array(4) as _, i (i)}
          <span class="skeleton-line voice-section-tab-skel"></span>
        {/each}
      </div>
    {/if}

    <div class="voice-dialog-content">
      <!-- Sidebar -->
      {#if status === 'ready'}
        <aside class="voice-sidebar">
          <div class="voice-sidebar-section">
            <div class="voice-sidebar-heading">
              <iconify-icon icon="lucide:download"></iconify-icon>下载
            </div>
            <div class="voice-sidebar-row">
              <span class="voice-sidebar-label">语言</span>
              <div class="chip-group">
                {#each ['cn', 'jp', 'en'] as lang (lang)}
                  <button class:active={downloadLangs.has(lang as LangKey)} class="chip chip-sm" onclick={() => toggleLang(lang as LangKey)}>
                    {langLabels[lang as LangKey]}
                  </button>
                {/each}
              </div>
            </div>
            <div class="voice-sidebar-row">
              <label class="voice-toggle">
                <input type="checkbox" bind:checked={mergeAudio}>
                <span class="voice-toggle-track"><span class="voice-toggle-thumb"></span></span>
                <span class="voice-toggle-label">合并为单文件</span>
              </label>
            </div>
            <div class="voice-sidebar-row">
              <span class="voice-sidebar-label">线程数</span>
              <div class="voice-sidebar-inline">
                <input class="voice-concurrency" type="range" min="1" max="12" bind:value={downloadConcurrency}>
                <span class="voice-concurrency-val">{downloadConcurrency}</span>
              </div>
            </div>
          </div>

          <div class="voice-sidebar-section">
            <div class="voice-sidebar-heading">
              <iconify-icon icon="lucide:file-text"></iconify-icon>字幕设置
            </div>
            <div class="voice-sidebar-row">
              <span class="voice-sidebar-label">导出</span>
              <div class="chip-group">
                <button class:active={subtitleMode === 'merged'} class="chip chip-sm" onclick={() => subtitleMode = 'merged'}>合并</button>
                <button class:active={subtitleMode === 'perLine'} class="chip chip-sm" onclick={() => subtitleMode = 'perLine'}>分离</button>
                <button class:active={subtitleMode === 'none'} class="chip chip-sm" onclick={() => subtitleMode = 'none'}>不导出</button>
              </div>
            </div>
            <div class="voice-sidebar-row">
              <span class="voice-sidebar-label">格式</span>
              <div class="chip-group">
                <button class:active={subtitleFormat === 'full'} class="chip chip-sm" onclick={() => subtitleFormat = 'full'}>含标签</button>
                <button class:active={subtitleFormat === 'plain'} class="chip chip-sm" onclick={() => subtitleFormat = 'plain'}>纯文本</button>
              </div>
            </div>
          </div>

          <div class="voice-sidebar-section">
            <div class="voice-sidebar-heading">
              <iconify-icon icon="lucide:folder-tree"></iconify-icon>文件设置
            </div>
            <div class="voice-sidebar-row">
              <span class="voice-sidebar-label">命名</span>
              <div class="chip-group voice-sidebar-chips-vert">
                <button class:active={namingMode === 'both'} class="chip chip-sm" onclick={() => namingMode = 'both'}>
                  <iconify-icon icon="lucide:tag"></iconify-icon>类型+字幕
                </button>
                <button class:active={namingMode === 'category'} class="chip chip-sm" onclick={() => namingMode = 'category'}>
                  <iconify-icon icon="lucide:folder"></iconify-icon>仅类型
                </button>
                <button class:active={namingMode === 'subtitle'} class="chip chip-sm" onclick={() => namingMode = 'subtitle'}>
                  <iconify-icon icon="lucide:message-square-text"></iconify-icon>仅字幕
                </button>
                <button class:active={namingMode === 'original'} class="chip chip-sm" onclick={() => namingMode = 'original'}>
                  <iconify-icon icon="lucide:file"></iconify-icon>原名
                </button>
              </div>
            </div>
            <div class="voice-sidebar-row">
              <span class="voice-sidebar-label">目录</span>
              <div class="chip-group voice-sidebar-chips-vert">
                <button class:active={folderMode === 'none'} class="chip chip-sm" onclick={() => folderMode = 'none'}>
                  <iconify-icon icon="lucide:layers"></iconify-icon>平铺
                </button>
                <button class:active={folderMode === 'lang'} class="chip chip-sm" onclick={() => folderMode = 'lang'}>
                  <iconify-icon icon="lucide:languages"></iconify-icon>按语言
                </button>
                <button class:active={folderMode === 'category'} class="chip chip-sm" onclick={() => folderMode = 'category'}>
                  <iconify-icon icon="lucide:folder-open"></iconify-icon>按分类
                </button>
                <button class:active={folderMode === 'both'} class="chip chip-sm" onclick={() => folderMode = 'both'}>
                  <iconify-icon icon="lucide:folder-tree"></iconify-icon>语言+分类
                </button>
              </div>
            </div>
          </div>
        </aside>
      {/if}

      <!-- Main -->
      <div class="voice-main">
        {#if sectionGroups.length > 1 && status === 'ready'}
          <div class="voice-section-tabs">
            {#each sectionGroups as group, i (group.title)}
              <button class:active={activeSectionIdx === i} class="voice-section-tab" onclick={() => selectSection(i)}>
                <iconify-icon icon="lucide:hash" style="font-size:0.7rem;opacity:0.5;"></iconify-icon>
                {group.title}
              </button>
            {/each}
          </div>
        {/if}

        <div class="voice-dialog-body">
          {#if status === 'ready' && voiceLines.length > 0}
            <div class="voice-search-bar">
              <div class="voice-search-input-wrap">
                <iconify-icon icon="lucide:search" class="voice-search-icon"></iconify-icon>
                <input class="voice-search-input" type="text" placeholder="搜索台词、分类或文件名..." bind:value={searchQuery} />
                {#if searchQuery}
                  <button class="voice-search-clear" onclick={() => searchQuery = ''} aria-label="清除">
                    <iconify-icon icon="lucide:x"></iconify-icon>
                  </button>
                {/if}
              </div>
              <div class="voice-filter-chips">
                <iconify-icon icon="lucide:filter" style="font-size:0.7rem;color:var(--muted-foreground);margin-right:2px;"></iconify-icon>
                <button class:active={filterHasAudio === 'all'} class="chip chip-sm" onclick={() => filterHasAudio = 'all'}>全部</button>
                {#each ['cn', 'jp', 'en'] as lang (lang)}
                  <button class:active={filterHasAudio === lang} class="chip chip-sm" onclick={() => filterHasAudio = filterHasAudio === lang ? 'all' : lang as LangKey}>
                    {langLabels[lang as LangKey]}
                  </button>
                {/each}
              </div>
              {#if searchQuery || filterHasAudio !== 'all'}
                <span class="voice-search-info">匹配 {matchedCount()} / {voiceLines.length} 条</span>
              {/if}
            </div>
          {/if}

          {#if status === 'loading'}
            <div class="voice-loading">
              {#each Array(8) as _, i (i)}
                <div class="voice-line-skeleton">
                  <span class="skeleton-line" style="width:16px;"></span>
                  <span class="skeleton-line" style="width:48px;"></span>
                  <span class="skeleton-line" style="width:100%;"></span>
                </div>
              {/each}
            </div>
          {:else if status === 'error'}
            <div class="voice-placeholder">
              <iconify-icon icon="lucide:alert-circle"></iconify-icon>
              <p>{errorMessage}</p>
            </div>
          {:else if voiceLines.length === 0}
            <div class="voice-placeholder">
              <iconify-icon icon="lucide:mic-off"></iconify-icon>
              <p>该章节暂无语音台词数据</p>
            </div>
          {:else if groupedLines().length === 0}
            <div class="voice-placeholder">
              <iconify-icon icon="lucide:search-x"></iconify-icon>
              <p>无匹配结果</p>
            </div>
          {:else}
            {#each groupedLines() as group, i (i)}
              <div class="voice-group">
                <button class="voice-group-label" onclick={() => toggleGroup(group.lines.map(l => l.globalIndex))}>
                  <iconify-icon icon={selectedIndices.size > 0 && group.lines.every(l => selectedIndices.has(l.globalIndex)) ? 'lucide:folder-check' : 'lucide:folder'}></iconify-icon>
                  {group.category}
                  <span class="voice-group-count">{group.lines.length}</span>
                </button>
                {#each group.lines as { line, globalIndex } (globalIndex)}
                  {@const activeLang = getTabLang(globalIndex)}
                  <div class:selected={selectedIndices.has(globalIndex)} class="voice-line">
                    <button class="voice-line-check" onclick={() => toggleLine(globalIndex)} aria-label="选择">
                      <iconify-icon icon={selectedIndices.has(globalIndex) ? 'lucide:check-square' : 'lucide:square'}></iconify-icon>
                    </button>
                    <div class="voice-line-tabs">
                      {#each ['cn', 'jp', 'en'] as lang (lang)}
                        {@const text = getText(line, lang as LangKey)}
                        {#if text || lang === 'cn'}
                          <button class:active={activeLang === lang} class="voice-tab" onclick={() => setTabLang(globalIndex, lang as LangKey)} title={langLabels[lang as LangKey]}>
                            {langShort[lang as LangKey]}
                          </button>
                        {:else}
                          <span class="voice-tab voice-tab-empty">{langShort[lang as LangKey]}</span>
                        {/if}
                      {/each}
                    </div>
                    <div class="voice-line-text">
                      {#if getText(line, activeLang)}
                        {getText(line, activeLang)}
                      {:else}
                        <span class="text-muted">（无文本）</span>
                      {/if}
                    </div>
                    <AudioPlayButton src={resolvePlaySrc(line, activeLang)} />
                  </div>
                {/each}
              </div>
            {/each}
          {/if}
        </div>

        {#if status === 'ready' && voiceLines.length > 0}
          <div class="voice-footer">
            <button class="btn outline" onclick={toggleAll}>
              <iconify-icon icon={selectedIndices.size === voiceLines.length ? 'lucide:square' : 'lucide:check-square'} style="margin-right:4px;font-size:0.9rem;"></iconify-icon>
              {selectedIndices.size === voiceLines.length ? '取消全选' : '全选'}
            </button>
            <span class="voice-footer-info">
              {#if mergeAudio}
                已选 {selectedIndices.size} 条 · 合并 {mergeFileCount} 个文件为 MP3
              {:else}
                已选 {selectedIndices.size} 条 · {downloadFileCount} 个文件
              {/if}
              {#if downloadProgress}<span class="voice-footer-progress"> · {downloadProgress}</span>{/if}
            </span>
            <button class="btn primary" disabled={selectedIndices.size === 0 || downloading} onclick={handleDownload}>
              <iconify-icon icon="lucide:download" style="margin-right:6px;"></iconify-icon>
              {downloading ? '打包中...' : '下载'}
            </button>
          </div>
        {/if}
      </div>
    </div>
  </div>
</dialog>
