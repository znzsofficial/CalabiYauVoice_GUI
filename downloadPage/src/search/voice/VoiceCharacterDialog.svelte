<script lang="ts">
  import { onMount, tick } from 'svelte';
  import { fetchVoicePageParsetree, resolveAudioUrl, type CategoryPage, type VoiceLine } from '../searchApi';
  import { parseVoiceSections, type VoiceSectionGroup } from './voiceParser';
  import { toError } from '../utils';
  import { downloadBlob, downloadFailuresText, downloadUrlWithRetry, generateZip, uniqueFileName } from '../download';
  import { mergeMp3Buffers, mergeMp3BuffersToWav, mp3BufferToWavBlob, buildFolderPath, addSubtitlesToZip, type AudioExportFormat, type WavBitDepth } from './voiceDownload';
  import AudioPlayButton from './AudioPlayButton.svelte';

  type LangKey = 'cn' | 'jp' | 'en';
  type Status = 'loading' | 'ready' | 'error';

  let {
    dialogRef = $bindable(null),
    character,
    initialSection = 0,
    initialLineIndex,
    highlightQuery = '',
    onClose = () => {}
  }: {
    dialogRef?: HTMLDialogElement | null;
    character: CategoryPage;
    initialSection?: number;
    initialLineIndex?: number;
    highlightQuery?: string;
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
  let folderMode = $state('none' as 'none' | 'lang' | 'category' | 'chapter' | 'both');
  let mergeMode = $state('none' as 'none' | 'byLang' | 'all');
  let exportFormat = $state('mp3' as AudioExportFormat);
  let wavBitDepth = $state(16 as WavBitDepth);
  let selectedIndices = $state(new Set<number>());
  let sectionSelections = $state({} as Record<number, Set<number>>);
  let sectionSelectionCounts = $derived(
    Object.fromEntries(sectionGroups.map((_, i) => [i, (sectionSelections[i]?.size || 0)] as const))
  );
  let downloadLangs = $state(new Set<LangKey>(['cn', 'jp', 'en']));
  let namingMode = $state('both' as 'both' | 'category' | 'subtitle' | 'original' | 'template');
  let namingTemplate = $state('{category}_{text}');
  let exportDetailFiles = $state(false);
  let activeTab = $state({} as Record<number, LangKey>);
  let downloading = $state(false);
  let downloadProgress = $state('');
  let downloadConcurrency = $state(4);
  let downloadAbortController = $state(null) as AbortController | null;
  let highlightedLineIndex = $state(undefined) as number | undefined;

  const langLabels: Record<LangKey, string> = { cn: '中文', jp: '日文', en: '英文' };
  const langShort: Record<LangKey, string> = { cn: '中', jp: '日', en: '英' };

  let totalSelectedLines = $derived(
    Object.values(sectionSelections).reduce((sum, s) => sum + s.size, 0)
  );

  let totalAudioFileCount = $derived.by(() => {
    let count = 0;
    for (let si = 0; si < sectionGroups.length; si++) {
      const sel = sectionSelections[si];
      if (!sel) continue;
      for (const i of sel) {
        const line = sectionGroups[si].lines[i];
        if (line) for (const lang of downloadLangs) if (getAudio(line, lang)) count++;
      }
    }
    return count;
  });

  function closeOnBackdrop(event: MouseEvent): void {
    if (event.target === dialogRef) { dialogRef?.close(); onClose(); }
  }
  function handleClose(): void { dialogRef?.close(); onClose(); }
  function getVoicePageTitle(charName: string): string { return `${charName}/语音台词`; }

  function getTabLang(index: number): LangKey {
    return activeTab[index] || defaultLangForLine(voiceLines[index]);
  }
  function setTabLang(index: number, lang: LangKey): void { activeTab = { ...activeTab, [index]: lang }; }
  function defaultLangForLine(line: VoiceLine): LangKey {
    if (getText(line, 'cn')) return 'cn';
    if (getText(line, 'jp')) return 'jp';
    if (getText(line, 'en')) return 'en';
    return 'cn';
  }

  function getAudio(line: VoiceLine, lang: LangKey): string {
    return (lang === 'cn' ? line.cnAudio : lang === 'jp' ? line.jpAudio : line.enAudio) || '';
  }
  function getText(line: VoiceLine, lang: LangKey): string {
    return (lang === 'cn' ? line.cnText : lang === 'jp' ? line.jpText : line.enText) || '';
  }
  function resolvePlaySrc(line: VoiceLine, lang: LangKey): string {
    const fn = getAudio(line, lang);
    return fn ? resolveAudioUrl(fn) : '';
  }
  function syncSectionSelection(): void {
    sectionSelections = { ...sectionSelections, [activeSectionIdx]: selectedIndices };
  }

  function toggleLine(index: number): void {
    if (downloading) return;
    if (!hasAnyAudio(voiceLines[index])) return;
    const next = new Set(selectedIndices);
    next.has(index) ? next.delete(index) : next.add(index);
    selectedIndices = next;
    syncSectionSelection();
  }
  function toggleGroup(indices: number[]): void {
    if (downloading) return;
    const selectable = indices.filter(i => hasAnyAudio(voiceLines[i]));
    if (selectable.length === 0) return;
    const allSelected = selectable.every(i => selectedIndices.has(i));
    const next = new Set(selectedIndices);
    if (allSelected) { for (const i of selectable) next.delete(i); }
    else { for (const i of selectable) next.add(i); }
    selectedIndices = next;
    syncSectionSelection();
  }
  function toggleAll(): void {
    if (downloading) return;
    const visible = groupedVoiceLines.flatMap(g => g.lines.map(l => l.globalIndex)).filter(i => hasAnyAudio(voiceLines[i]));
    const allVisible = visible.length > 0 && visible.every(i => selectedIndices.has(i));
    selectedIndices = allVisible ? new Set() : new Set(visible);
    syncSectionSelection();
  }
  function toggleLang(lang: LangKey): void {
    if (downloading) return;
    const next = new Set(downloadLangs);
    if (next.has(lang)) {
      if (next.size <= 1) return;
      next.delete(lang);
    } else {
      next.add(lang);
    }
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

  function audioExt(): string {
    return exportFormat === 'wav' ? 'wav' : 'mp3';
  }

  function withAudioExt(name: string): string {
    const base = name.replace(/\.(mp3|wav|ogg|flac)$/i, '');
    return `${base}.${audioExt()}`;
  }

  function resolveNameTemplate(line: VoiceLine, lang: LangKey, audioName: string, index: number): string {
    const replacements: Record<string, string> = {
      category: safeFileName(line.category, 'voice'),
      text: safeFileName(getText(line, lang), index.toString()),
      lang,
      index: String(index + 1),
      original: audioName.replace(/\.(mp3|wav|ogg|flac)$/i, ''),
    };
    return withAudioExt(namingTemplate.replace(/\{(\w+)\}/g, (_, key: string) => replacements[key] ?? _));
  }

  async function toExportBlob(source: Blob | ArrayBuffer): Promise<Blob> {
    if (exportFormat !== 'wav') {
      return source instanceof Blob ? source : new Blob([source], { type: 'audio/mpeg' });
    }
    const buf = source instanceof Blob ? await source.arrayBuffer() : source;
    return await mp3BufferToWavBlob(buf, wavBitDepth);
  }

  async function downloadOneAudio(
    audioName: string,
    signal?: AbortSignal,
    retries = 2,
  ): Promise<{ ok: true; blob: Blob } | { ok: false; error: string }> {
    // resolveAudioUrl already rewrites to same-origin /api/file-download
    const url = resolveAudioUrl(audioName);
    try {
      const blob = await downloadUrlWithRetry(url, { retries, signal });
      return { ok: true, blob };
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return { ok: false, error: '已取消' };
      return { ok: false, error: toError(err).message || '下载失败' };
    }
  }

  async function downloadOneAudioBuf(
    audioName: string,
    signal?: AbortSignal,
    retries = 2,
  ): Promise<{ ok: true; buf: ArrayBuffer } | { ok: false; error: string }> {
    const result = await downloadOneAudio(audioName, signal, retries);
    if (!result.ok) return result;
    return { ok: true, buf: await result.blob.arrayBuffer() };
  }

  function cancelDownload(): void {
    downloadAbortController?.abort();
    downloadProgress = '正在取消...';
  }

  function chapterName(sectionIdx: number): string {
    return sectionGroups[sectionIdx]?.title || '';
  }

  async function handleDownload(): Promise<void> {
    const allSelectedLines: Array<{ line: VoiceLine; sectionIdx: number }> = [];
    for (let si = 0; si < sectionGroups.length; si++) {
      const sel = sectionSelections[si];
      if (!sel || sel.size === 0) continue;
      for (const i of sel) {
        const line = sectionGroups[si].lines[i];
        if (line && hasAnyAudio(line)) {
          allSelectedLines.push({ line, sectionIdx: si });
        }
      }
    }

    if (allSelectedLines.length === 0 || downloading) return;
    downloading = true;
    downloadProgress = '准备下载...';
    const controller = new AbortController();
    downloadAbortController = controller;
    const signal = controller.signal;
    const lines = allSelectedLines.map(l => l.line);
    try {
      const tasks: Array<{ line: VoiceLine; lang: LangKey; audioName: string; sectionIdx: number }> = [];
      for (const { line, sectionIdx } of allSelectedLines) {
        for (const lang of downloadLangs) {
          const audioName = getAudio(line, lang);
          if (audioName) tasks.push({ line, lang, audioName, sectionIdx });
        }
      }
      if (tasks.length === 0) return;

      const limit = Math.max(1, Math.min(12, downloadConcurrency));

      if (mergeMode !== 'none') {
        const chunksByLang: Record<LangKey, ArrayBuffer[]> = { cn: [], jp: [], en: [] };
        const allChunks: ArrayBuffer[] = [];
        const failures: Array<{ name: string; error: string; category?: string }> = [];
        for (let i = 0; i < tasks.length; i += limit) {
          if (signal.aborted) throw new Error('已取消下载');
          const batch = tasks.slice(i, i + limit);
          const results = await Promise.all(batch.map(async task => {
            const result = await downloadOneAudioBuf(task.audioName, signal);
            return { task, result };
          }));
          if (signal.aborted) throw new Error('已取消下载');
          for (const { task, result } of results) {
            if (result.ok) {
              if (mergeMode === 'byLang') chunksByLang[task.lang].push(result.buf);
              else allChunks.push(result.buf);
            } else {
              failures.push({ name: task.audioName, error: result.error, category: task.line.category });
            }
          }
          downloadProgress = failures.length > 0
            ? `正在下载 ${Math.min(i + limit, tasks.length)}/${tasks.length}，失败 ${failures.length}`
            : `正在下载 ${Math.min(i + limit, tasks.length)}/${tasks.length}`;
        }

        const zip = await generateZip();
        let mergedFileCount = 0;
        let mergedClipCount = 0;

        if (mergeMode === 'all') {
          if (allChunks.length === 0) {
            downloadProgress = `下载失败：${failures.length} 个文件均无法获取`;
            return;
          }
          mergedClipCount = allChunks.length;
          const fileBase = `${character.title}-合并`;
          if (exportFormat === 'wav') {
            downloadProgress = '正在解码并合并为 WAV...';
            const mergedWav = await mergeMp3BuffersToWav(allChunks, wavBitDepth);
            allChunks.length = 0;
            zip.file(`${fileBase}.wav`, mergedWav);
          } else {
            downloadProgress = '正在合并 MP3...';
            const merged = mergeMp3Buffers(allChunks);
            allChunks.length = 0;
            zip.file(`${fileBase}.mp3`, new Blob([merged as BlobPart], { type: 'audio/mpeg' }));
          }
          mergedFileCount = 1;
        } else {
          // byLang: each selected language becomes its own merged track
          const langsToMerge = (['cn', 'jp', 'en'] as LangKey[]).filter(lang => chunksByLang[lang].length > 0);
          if (langsToMerge.length === 0) {
            downloadProgress = `下载失败：${failures.length} 个文件均无法获取`;
            return;
          }
          for (const lang of langsToMerge) {
            if (signal.aborted) throw new Error('已取消下载');
            const chunks = chunksByLang[lang];
            mergedClipCount += chunks.length;
            const fileBase = `${character.title}-${langLabels[lang]}-合并`;
            if (exportFormat === 'wav') {
              downloadProgress = `正在解码并合并 ${langLabels[lang]} WAV...`;
              const mergedWav = await mergeMp3BuffersToWav(chunks, wavBitDepth);
              chunks.length = 0;
              const filename = `${fileBase}.wav`;
              if (folderMode === 'lang' || folderMode === 'both') {
                zip.file(buildFolderPath(folderMode, lang, 'merged', filename), mergedWav);
              } else {
                zip.file(filename, mergedWav);
              }
            } else {
              downloadProgress = `正在合并 ${langLabels[lang]} MP3...`;
              const merged = mergeMp3Buffers(chunks);
              chunks.length = 0;
              const filename = `${fileBase}.mp3`;
              const blob = new Blob([merged as BlobPart], { type: 'audio/mpeg' });
              if (folderMode === 'lang' || folderMode === 'both') {
                zip.file(buildFolderPath(folderMode, lang, 'merged', filename), blob);
              } else {
                zip.file(filename, blob);
              }
            }
            mergedFileCount += 1;
          }
        }

        addSubtitlesToZip(zip, lines, downloadLangs, getText, { subtitleMode, subtitleFormat, folderMode }, uniqueFileName);
        if (exportDetailFiles) addDetailFiles(zip, lines, downloadLangs, uniqueFileName);
        if (failures.length > 0) zip.file('_download_failed.txt', downloadFailuresText(failures));
        if (signal.aborted) throw new Error('已取消下载');
        downloadProgress = '正在生成 ZIP...';
        const content = await zip.generateAsync({ type: 'blob' });
        downloadBlob(content, `${character.title}-语音-${new Date().toISOString().slice(0, 10)}.zip`);
        const modeLabel = mergeMode === 'all' ? '全部合并' : '按语言合并';
        downloadProgress = failures.length > 0
          ? `${modeLabel} ${mergedFileCount} 个文件（${mergedClipCount} 段），${failures.length} 个失败`
          : `${modeLabel} ${mergedFileCount} 个文件（${mergedClipCount} 段）`;
      } else {
        const zip = await generateZip();
        const usedNames = new Set<string>();
        const catCounters: Record<string, number> = {};
        let finished = 0;
        let failed = 0;
        const failures: Array<{ name: string; error: string; category?: string }> = [];

        for (let i = 0; i < tasks.length; i += limit) {
          if (signal.aborted) throw new Error('已取消下载');
          const batch = tasks.slice(i, i + limit);
          await Promise.all(batch.map(async task => {
            const result = await downloadOneAudio(task.audioName, signal);
            if (!result.ok) {
              failed++;
              failures.push({ name: task.audioName, error: result.error, category: task.line.category });
              return;
            }
            let blob: Blob;
            try {
              if (exportFormat === 'wav') downloadProgress = `正在转码 WAV ${finished + failed + 1}/${tasks.length}`;
              blob = await toExportBlob(result.blob);
            } catch (err) {
              failed++;
              failures.push({ name: task.audioName, error: toError(err).message || 'WAV 转码失败', category: task.line.category });
              return;
            }
            let name: string;
            if (namingMode === 'both') {
              name = withAudioExt(`${safeFileName(task.line.category, '')}_${safeFileName(getText(task.line, task.lang), task.audioName)}`);
            } else if (namingMode === 'category') {
              const key = task.line.category || 'voice';
              catCounters[key] = (catCounters[key] || 0) + 1;
              name = withAudioExt(`${safeFileName(key, '')}_${String(catCounters[key]).padStart(3, '0')}`);
            } else if (namingMode === 'subtitle') {
              name = withAudioExt(safeFileName(getText(task.line, task.lang), task.audioName));
            } else if (namingMode === 'template') {
              name = resolveNameTemplate(task.line, task.lang, task.audioName, finished + failed);
            } else {
              name = withAudioExt(task.audioName);
            }
            zip.file(buildFolderPath(folderMode, task.lang, task.line.category, uniqueFileName(name, usedNames), chapterName(task.sectionIdx)), blob);
            finished++;
          }));
          downloadProgress = failed > 0
            ? `正在下载 ${Math.min(i + limit, tasks.length)}/${tasks.length}，失败 ${failed}`
            : `正在下载 ${Math.min(i + limit, tasks.length)}/${tasks.length}`;
        }

        if (signal.aborted) throw new Error('已取消下载');
        if (finished === 0) {
          downloadProgress = `下载失败：${failed} 个文件均无法获取`;
          return;
        }

        addSubtitlesToZip(zip, lines, downloadLangs, getText, { subtitleMode, subtitleFormat, folderMode }, uniqueFileName);
        if (exportDetailFiles) addDetailFiles(zip, lines, downloadLangs, uniqueFileName);
        if (failures.length > 0) zip.file('_download_failed.txt', downloadFailuresText(failures));
        downloadProgress = '正在生成 ZIP...';
        const content = await zip.generateAsync({ type: 'blob' });
        downloadBlob(content, `${character.title}-语音-${new Date().toISOString().slice(0, 10)}.zip`);
        downloadProgress = failed > 0 ? `已打包 ${finished} 个文件，${failed} 个失败` : `已打包 ${finished} 个文件`;
      }
    } catch (err) {
      downloadProgress = toError(err).message || '下载失败';
    } finally {
      downloading = false;
      downloadAbortController = null;
    }
  }

  function addDetailFiles(
    zip: Awaited<ReturnType<typeof generateZip>>,
    lines: VoiceLine[],
    downloadLangs: Set<LangKey>,
    uniqueFileName: (name: string, usedNames: Set<string>) => string,
  ): void {
    const used = new Set<string>();
    let idx = 0;
    for (const line of lines) {
      idx++;
      let txt = '';
      for (const lang of downloadLangs) {
        const text = getText(line, lang);
        if (text) txt += `[${langLabels[lang]}] ${text}\n`;
      }
      if (txt) {
        const cat = line.category || 'other';
        const prefix = safeFileName(cat, String(idx));
        zip.file(buildFolderPath(folderMode, 'cn' as LangKey, cat, uniqueFileName(`${prefix}_${String(idx).padStart(3, '0')}.txt`, used)), txt.trim());
      }
    }
  }

  function selectSection(index: number, force = false): void {
    if (!force && index === activeSectionIdx) return;
    sectionSelections = { ...sectionSelections, [activeSectionIdx]: selectedIndices };
    activeSectionIdx = index;
    selectedIndices = sectionSelections[index] || new Set();
    const group = sectionGroups[index];
    voiceLines = group ? group.lines : [];
  }

  async function scrollToLine(index: number | undefined): Promise<void> {
    if (index == null) return;
    highlightedLineIndex = index;
    // Ensure target row is visible even if highlightQuery filters it out.
    if (searchQuery.trim()) {
      const line = voiceLines[index];
      if (line && !lineMatchesSearch(line, searchQuery.trim().toLowerCase())) {
        searchQuery = '';
      }
    }
    await tick();
    let target = dialogRef?.querySelector(`[data-voice-line="${index}"]`);
    if (!target) {
      await tick();
      target = dialogRef?.querySelector(`[data-voice-line="${index}"]`);
    }
    if (!target && searchQuery) {
      searchQuery = '';
      await tick();
      target = dialogRef?.querySelector(`[data-voice-line="${index}"]`);
    }
    target?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    setTimeout(() => {
      if (highlightedLineIndex === index) highlightedLineIndex = undefined;
    }, 1800);
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

  let groupedVoiceLines = $derived.by(() => {
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
  });

  let voiceMatchedCount = $derived(
    groupedVoiceLines.reduce((sum, g) => sum + g.lines.length, 0)
  );
  let visibleSelectable = $derived(
    groupedVoiceLines.flatMap(g => g.lines.map(l => l.globalIndex)).filter(i => hasAnyAudio(voiceLines[i]))
  );
  let allVisibleSelected = $derived(
    visibleSelectable.length > 0 && visibleSelectable.every(i => selectedIndices.has(i))
  );

  onMount(async () => {
    dialogRef?.showModal();
    try {
      const pageTitle = getVoicePageTitle(character.title);
      const parsetree = await fetchVoicePageParsetree(pageTitle);
      sectionGroups = parseVoiceSections(parsetree);
      if (sectionGroups.length > 0) {
        const secIdx = Math.max(0, Math.min(initialSection, sectionGroups.length - 1));
        selectSection(secIdx, true);
        if (highlightQuery) searchQuery = highlightQuery;
        status = 'ready';
        await scrollToLine(initialLineIndex);
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
            <div class="voice-sidebar-heading">导出</div>
            <div class="voice-sidebar-row">
              <span class="voice-sidebar-label">语言</span>
              <div class="chip-group">
                {#each ['cn', 'jp', 'en'] as lang (lang)}
                  <button class:active={downloadLangs.has(lang as LangKey)} class="chip chip-sm" disabled={downloading} onclick={() => toggleLang(lang as LangKey)}>
                    {langLabels[lang as LangKey]}
                  </button>
                {/each}
              </div>
            </div>
            <div class="voice-sidebar-row">
              <span class="voice-sidebar-label">合并</span>
              <div class="chip-group voice-sidebar-chips-vert">
                <button class:active={mergeMode === 'none'} class="chip chip-sm" disabled={downloading} onclick={() => mergeMode = 'none'}>不合并</button>
                <button class:active={mergeMode === 'byLang'} class="chip chip-sm" disabled={downloading} onclick={() => mergeMode = 'byLang'}>按语言合并</button>
                <button class:active={mergeMode === 'all'} class="chip chip-sm" disabled={downloading} onclick={() => mergeMode = 'all'}>全部合并</button>
              </div>
            </div>
            <div class="voice-sidebar-row">
              <span class="voice-sidebar-label">格式</span>
              <div class="chip-group">
                <button class:active={exportFormat === 'mp3'} class="chip chip-sm" disabled={downloading} onclick={() => exportFormat = 'mp3'}>MP3</button>
                <button class:active={exportFormat === 'wav'} class="chip chip-sm" disabled={downloading} onclick={() => exportFormat = 'wav'}>WAV</button>
              </div>
            </div>
            {#if exportFormat === 'wav'}
              <div class="voice-sidebar-row">
                <span class="voice-sidebar-label">位深</span>
                <div class="chip-group">
                  <button class:active={wavBitDepth === 16} class="chip chip-sm" disabled={downloading} onclick={() => wavBitDepth = 16}>16-bit</button>
                  <button class:active={wavBitDepth === 24} class="chip chip-sm" disabled={downloading} onclick={() => wavBitDepth = 24}>24-bit</button>
                </div>
              </div>
            {/if}
            <div class="voice-sidebar-row">
              <span class="voice-sidebar-label">并发 {downloadConcurrency}</span>
              <input class="voice-concurrency" type="range" min="1" max="12" bind:value={downloadConcurrency} disabled={downloading}>
            </div>
          </div>

          <div class="voice-sidebar-section">
            <div class="voice-sidebar-heading">字幕</div>
            <div class="voice-sidebar-row">
              <span class="voice-sidebar-label">模式</span>
              <div class="chip-group voice-sidebar-chips-vert">
                <button class:active={subtitleMode === 'merged'} class="chip chip-sm" disabled={downloading} onclick={() => subtitleMode = 'merged'}>按语言汇总</button>
                <button class:active={subtitleMode === 'perLine'} class="chip chip-sm" disabled={downloading} onclick={() => subtitleMode = 'perLine'}>按台词拆分</button>
                <button class:active={subtitleMode === 'none'} class="chip chip-sm" disabled={downloading} onclick={() => subtitleMode = 'none'}>不导出</button>
              </div>
            </div>
            {#if subtitleMode !== 'none'}
              <div class="voice-sidebar-row">
                <span class="voice-sidebar-label">内容</span>
                <div class="chip-group">
                  <button class:active={subtitleFormat === 'full'} class="chip chip-sm" disabled={downloading} onclick={() => subtitleFormat = 'full'}>含分类</button>
                  <button class:active={subtitleFormat === 'plain'} class="chip chip-sm" disabled={downloading} onclick={() => subtitleFormat = 'plain'}>纯文本</button>
                </div>
              </div>
              <label class="voice-toggle">
                <input type="checkbox" bind:checked={exportDetailFiles} disabled={downloading}>
                <span class="voice-toggle-track"><span class="voice-toggle-thumb"></span></span>
                <span class="voice-toggle-label">导出详情文件</span>
              </label>
            {/if}
          </div>

          {#if mergeMode === 'none'}
            <div class="voice-sidebar-section">
              <div class="voice-sidebar-heading">文件</div>
              <div class="voice-sidebar-row">
                <span class="voice-sidebar-label">命名</span>
                <div class="chip-group voice-sidebar-chips-vert">
                  <button class:active={namingMode === 'both'} class="chip chip-sm" disabled={downloading} onclick={() => namingMode = 'both'}>分类 + 字幕</button>
                  <button class:active={namingMode === 'category'} class="chip chip-sm" disabled={downloading} onclick={() => namingMode = 'category'}>分类</button>
                  <button class:active={namingMode === 'subtitle'} class="chip chip-sm" disabled={downloading} onclick={() => namingMode = 'subtitle'}>字幕</button>
                  <button class:active={namingMode === 'original'} class="chip chip-sm" disabled={downloading} onclick={() => namingMode = 'original'}>原始文件名</button>
                  <button class:active={namingMode === 'template'} class="chip chip-sm" disabled={downloading} onclick={() => namingMode = 'template'}>模板</button>
                </div>
              </div>
              {#if namingMode === 'template'}
                <div class="voice-sidebar-row">
                  <input class="voice-template-input" type="text" bind:value={namingTemplate} spellcheck="false" autocomplete="off" placeholder={'{category}_{text}'} disabled={downloading}>
                  <p class="voice-sidebar-hint">{'{category} · {text} · {lang} · {index} · {original}'}</p>
                </div>
              {/if}
              <div class="voice-sidebar-row">
                <span class="voice-sidebar-label">目录</span>
                <div class="chip-group voice-sidebar-chips-vert">
                  <button class:active={folderMode === 'none'} class="chip chip-sm" disabled={downloading} onclick={() => folderMode = 'none'}>无</button>
                  <button class:active={folderMode === 'lang'} class="chip chip-sm" disabled={downloading} onclick={() => folderMode = 'lang'}>语言</button>
                  <button class:active={folderMode === 'category'} class="chip chip-sm" disabled={downloading} onclick={() => folderMode = 'category'}>分类</button>
                  <button class:active={folderMode === 'chapter'} class="chip chip-sm" disabled={downloading} onclick={() => folderMode = 'chapter'}>章节</button>
                  <button class:active={folderMode === 'both'} class="chip chip-sm" disabled={downloading} onclick={() => folderMode = 'both'}>语言 / 分类</button>
                </div>
              </div>
            </div>
          {/if}
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
                {#if (sectionSelectionCounts[i] || 0) > 0}
                  <span class="voice-tab-badge">{sectionSelectionCounts[i]}</span>
                {/if}
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
                <span class="voice-search-info">                匹配 {voiceMatchedCount} / {voiceLines.length} 条</span>
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
          {:else if groupedVoiceLines.length === 0}
            <div class="voice-placeholder">
              <iconify-icon icon="lucide:search-x"></iconify-icon>
              <p>无匹配结果</p>
            </div>
          {:else}
            {#each groupedVoiceLines as group, i (i)}
              {@const groupSelectable = group.lines.map(l => l.globalIndex).filter(gi => hasAnyAudio(voiceLines[gi]))}
              {@const groupAllSelected = groupSelectable.length > 0 && groupSelectable.every(gi => selectedIndices.has(gi))}
              <div class="voice-group">
                <button class="voice-group-label" disabled={downloading || groupSelectable.length === 0} onclick={() => toggleGroup(groupSelectable)}>
                  <iconify-icon icon={groupAllSelected ? 'lucide:folder-check' : 'lucide:folder'}></iconify-icon>
                  {group.category}
                  <span class="voice-group-count">{group.lines.length}</span>
                </button>
                {#each group.lines as { line, globalIndex } (globalIndex)}
                  {@const activeLang = getTabLang(globalIndex)}
                  {@const lineHasAudio = hasAnyAudio(line)}
                  <div class:selected={selectedIndices.has(globalIndex)} class:voice-line-muted={!lineHasAudio} class:voice-line-highlighted={highlightedLineIndex === globalIndex} class="voice-line" data-voice-line={globalIndex}>
                    {#if lineHasAudio}
                      <button class="voice-line-check" disabled={downloading} onclick={() => toggleLine(globalIndex)} aria-label="选择">
                        <iconify-icon icon={selectedIndices.has(globalIndex) ? 'lucide:check-square' : 'lucide:square'}></iconify-icon>
                      </button>
                    {:else}
                      <span class="voice-line-check voice-line-check-disabled" title="无可下载音频">
                        <iconify-icon icon="lucide:circle-slash"></iconify-icon>
                      </span>
                    {/if}
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
            <button class="btn outline" disabled={downloading || visibleSelectable.length === 0} onclick={toggleAll}>
              <iconify-icon icon={allVisibleSelected ? 'lucide:square' : 'lucide:check-square'} style="margin-right:4px;font-size:0.9rem;"></iconify-icon>
              {allVisibleSelected ? '取消全选' : '全选'}
            </button>
            <span class="voice-footer-info">
              {#if mergeMode === 'byLang'}
                {totalSelectedLines} 条 · 按语言合并 · {totalAudioFileCount} 段 · {exportFormat === 'wav' ? `WAV ${wavBitDepth}-bit` : 'MP3'}
              {:else if mergeMode === 'all'}
                {totalSelectedLines} 条 · 全部合并 · {totalAudioFileCount} 段 · {exportFormat === 'wav' ? `WAV ${wavBitDepth}-bit` : 'MP3'}
              {:else}
                {totalSelectedLines} 条 · {totalAudioFileCount} 个{exportFormat === 'wav' ? ` WAV ${wavBitDepth}-bit` : ' 文件'}
              {/if}
              {#if downloadProgress}<span class="voice-footer-progress"> · {downloadProgress}</span>{/if}
            </span>
            {#if downloading}
              <button class="btn outline" type="button" onclick={cancelDownload}>取消</button>
            {:else}
              <button class="btn primary" disabled={totalSelectedLines === 0} onclick={handleDownload}>
                <iconify-icon icon="lucide:download" style="margin-right:6px;"></iconify-icon>
                下载
              </button>
            {/if}
          </div>
        {/if}
      </div>
    </div>
  </div>
</dialog>
