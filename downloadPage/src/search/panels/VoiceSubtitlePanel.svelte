<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import { fetchAllCharacters, type CategoryPage } from '../searchApi';
  import { buildVoiceSearchIndexMap } from '../voice/voiceIndexBuilder';
  import { isVoiceIndexFresh, loadVoiceIndexCache, saveVoiceIndexCache } from '../voice/voiceIndexStore';
  import type { VoiceIndexFailure, VoiceIndexSections } from '../voice/voiceIndexTypes';
  import { toError } from '../utils';
  import VoiceCharacterDialog from '../voice/VoiceCharacterDialog.svelte';

  let { query = '' }: { query?: string } = $props();

  let voiceCharacters = $state([]) as CategoryPage[];
  let voiceCharsLoading = $state(false);
  let voiceCharsError = $state('');
  let voiceIndexReady = $state(false);
  let voiceIndexLoading = $state(false);
  let voiceIndexFromCache = $state(false);
  let voiceIndexBuiltAt = $state(0);
  let voiceIndexDone = $state(0);
  let voiceIndexTotal = $state(0);
  let voiceSearchIndex = $state(new Map<string, VoiceIndexSections>());
  let voiceIndexFailures = $state([]) as VoiceIndexFailure[];
  let voiceFailedListOpen = $state(false);
  let voiceSearchQuery = $state('');
  let voiceDialogOpen = $state(false);
  let voiceDialogCharacter = $state(null) as CategoryPage | null;
  let voiceDialogRef = $state(null) as HTMLDialogElement | null;
  let voiceDialogNavSection = $state(0);
  let voiceDialogNavLine = $state(undefined) as number | undefined;
  let voiceDialogNavQuery = $state('');
  let indexAbortController: AbortController | null = null;
  const VOICE_HIT_LIMIT = 30;

  let voiceIndexFailed = $derived(voiceIndexFailures.length);
  let voiceFailedTitles = $derived(new Set(voiceIndexFailures.map(item => item.title)));
  let voiceIndexNoVoice = $derived.by(() => {
    let count = 0;
    for (const sections of voiceSearchIndex.values()) {
      if (sections.length === 0) count += 1;
    }
    return count;
  });
  let voiceIndexPending = $derived.by(() => {
    if (voiceCharacters.length === 0) return 0;
    let count = 0;
    for (const c of voiceCharacters) {
      if (!voiceSearchIndex.has(c.title) && !voiceFailedTitles.has(c.title)) count += 1;
    }
    return count;
  });
  let voiceIndexBuiltLabel = $derived(formatBuiltAt(voiceIndexBuiltAt));

  let voiceDebounceTimer: ReturnType<typeof setTimeout>;
  $effect(() => {
    const q = query.trim();
    clearTimeout(voiceDebounceTimer);
    voiceDebounceTimer = setTimeout(() => { voiceSearchQuery = q; }, 150);
    return () => clearTimeout(voiceDebounceTimer);
  });

  let voiceFilterResult = $derived.by((): {
    characters: CategoryPage[];
    hits: Array<{ character: CategoryPage; sectionIdx: number; sectionTitle: string; lineIdx: number; lineText: string }> | null;
    hitTotal: number;
  } => {
    const q = voiceSearchQuery.toLowerCase();
    if (!q) return { characters: voiceCharacters, hits: null, hitTotal: 0 };
    if (!voiceIndexReady && voiceSearchIndex.size === 0) {
      return { characters: voiceCharacters.filter(c => c.title.toLowerCase().includes(q)), hits: null, hitTotal: 0 };
    }

    const matchedChars = new Set<CategoryPage>();
    const hits: Array<{ character: CategoryPage; sectionIdx: number; sectionTitle: string; lineIdx: number; lineText: string }> = [];
    for (const c of voiceCharacters) {
      if (c.title.toLowerCase().includes(q)) matchedChars.add(c);
      const sections = voiceSearchIndex.get(c.title);
      if (!sections) continue;
      for (let si = 0; si < sections.length; si++) {
        const sec = sections[si];
        if (sec.title.toLowerCase().includes(q)) matchedChars.add(c);
        for (let li = 0; li < sec.lines.length; li++) {
          const line = sec.lines[li];
          const haystack = [line.cnText, line.jpText, line.enText, line.category].join(' ').toLowerCase();
          if (haystack.includes(q)) {
            matchedChars.add(c);
            hits.push({ character: c, sectionIdx: si, sectionTitle: sec.title, lineIdx: li, lineText: line.cnText || line.jpText || line.enText || line.category });
          }
        }
      }
    }
    return { characters: [...matchedChars], hits: hits.slice(0, VOICE_HIT_LIMIT), hitTotal: hits.length };
  });

  onMount(() => {
    loadVoiceCharactersAndIndex();
  });

  onDestroy(() => {
    indexAbortController?.abort();
    indexAbortController = null;
  });

  function formatBuiltAt(ts: number): string {
    if (!ts) return '';
    try {
      return new Date(ts).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    } catch {
      return '';
    }
  }

  function publishIndex(
    idx: Map<string, VoiceIndexSections>,
    failures: VoiceIndexFailure[],
    options: { openFailedList?: boolean } = {},
  ): void {
    voiceSearchIndex = new Map(idx);
    voiceIndexFailures = failures.map(item => ({ ...item }));
    if (options.openFailedList && failures.length > 0 && failures.length <= 8) {
      voiceFailedListOpen = true;
    }
  }

  async function persistIndex(idx: Map<string, VoiceIndexSections>, failures: VoiceIndexFailure[], builtAt = Date.now()): Promise<void> {
    voiceIndexBuiltAt = builtAt;
    await saveVoiceIndexCache({
      characterTitles: voiceCharacters.map(c => c.title),
      index: idx,
      failed: failures,
      builtAt,
    });
  }

  function missingTitles(chars: CategoryPage[], idx: Map<string, VoiceIndexSections>, failures: VoiceIndexFailure[]): string[] {
    const failed = new Set(failures.map(item => item.title));
    return chars.filter(c => !idx.has(c.title) && !failed.has(c.title)).map(c => c.title);
  }

  function applyCachedIndex(chars: CategoryPage[], record: NonNullable<Awaited<ReturnType<typeof loadVoiceIndexCache>>>): boolean {
    const titleSet = new Set(chars.map(c => c.title));
    const cachedTitles = new Set(record.characterTitles);
    let overlap = 0;
    for (const title of cachedTitles) if (titleSet.has(title)) overlap += 1;
    if (overlap === 0 || overlap < Math.min(cachedTitles.size, titleSet.size) * 0.5) return false;

    const idx = new Map(record.index.filter(([title]) => titleSet.has(title)));
    const failures = record.failed
      .filter(item => titleSet.has(item.title))
      .map(item => ({ ...item }));
    publishIndex(idx, failures);
    voiceIndexBuiltAt = record.builtAt;
    voiceIndexFromCache = true;
    voiceIndexReady = true;
    return true;
  }

  async function loadVoiceCharactersAndIndex(forceRebuild = false): Promise<void> {
    if (voiceCharsLoading) return;
    voiceCharsError = '';
    let chars = voiceCharacters;
    if (chars.length === 0) {
      voiceCharsLoading = true;
      try {
        chars = await fetchAllCharacters();
        voiceCharacters = chars;
      } catch (err) {
        voiceCharsError = toError(err).message || '加载角色列表失败';
        return;
      } finally {
        voiceCharsLoading = false;
      }
    }

    if (!forceRebuild) {
      const cached = await loadVoiceIndexCache();
      if (cached && applyCachedIndex(chars, cached)) {
        const missing = missingTitles(chars, voiceSearchIndex, voiceIndexFailures);
        const failedTitles = voiceIndexFailures.map(item => item.title);
        if (failedTitles.length > 0 || missing.length > 0) {
          void runIndexBuild(chars, {
            keepExisting: true,
            onlyTitles: [...new Set([...failedTitles, ...missing])],
            background: true,
          });
        } else if (!isVoiceIndexFresh(cached.builtAt)) {
          void runIndexBuild(chars, { keepExisting: true, revalidateAll: true, background: true });
        }
        return;
      }
    }

    await runIndexBuild(chars, { keepExisting: false, background: false });
  }

  async function runIndexBuild(
    chars: CategoryPage[],
    options: {
      keepExisting?: boolean;
      revalidateAll?: boolean;
      onlyTitles?: string[];
      background?: boolean;
    },
  ): Promise<void> {
    // Supersede any in-flight build instead of rejecting while loading.
    indexAbortController?.abort();
    const controller = new AbortController();
    indexAbortController = controller;
    voiceIndexLoading = true;
    if (!options.background) voiceIndexFromCache = false;
    voiceIndexDone = 0;
    voiceIndexTotal = 0;
    try {
      const result = await buildVoiceSearchIndexMap(chars, {
        baseIndex: options.keepExisting ? voiceSearchIndex : undefined,
        baseFailures: options.keepExisting ? voiceIndexFailures : undefined,
        onlyTitles: options.onlyTitles,
        revalidateAll: options.revalidateAll,
        signal: controller.signal,
        onProgress: progress => {
          if (controller.signal.aborted || indexAbortController !== controller) return;
          voiceIndexDone = progress.done;
          voiceIndexTotal = progress.total;
          publishIndex(progress.index, progress.failures);
        },
      });
      if (controller.signal.aborted || indexAbortController !== controller) return;
      publishIndex(result.index, result.failures, { openFailedList: true });
      voiceIndexReady = true;
      await persistIndex(result.index, result.failures);
      if (indexAbortController !== controller) return;
      voiceIndexFromCache = false;
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      if (indexAbortController !== controller) return;
      if (!voiceIndexReady) {
        voiceCharsError = toError(err).message || '建立索引失败';
      }
    } finally {
      // Only the active controller may clear loading (avoids racing a newer build).
      if (indexAbortController === controller) {
        indexAbortController = null;
        voiceIndexLoading = false;
      }
    }
  }

  function cancelIndexBuild(): void {
    const controller = indexAbortController;
    indexAbortController = null;
    controller?.abort();
    voiceIndexLoading = false;
  }

  function retryFailedVoiceIndex(): void {
    if (voiceCharacters.length === 0 || voiceIndexFailures.length === 0) return;
    runIndexBuild(voiceCharacters, {
      keepExisting: true,
      onlyTitles: voiceIndexFailures.map(item => item.title),
    });
  }

  function retryOneFailedVoiceIndex(title: string): void {
    if (voiceCharacters.length === 0) return;
    if (!voiceFailedTitles.has(title)) return;
    runIndexBuild(voiceCharacters, { keepExisting: true, onlyTitles: [title] });
  }

  function indexMissingCharacters(): void {
    if (voiceCharacters.length === 0 || voiceIndexPending === 0) return;
    const titles = missingTitles(voiceCharacters, voiceSearchIndex, voiceIndexFailures);
    if (titles.length === 0) return;
    runIndexBuild(voiceCharacters, { keepExisting: true, onlyTitles: titles });
  }

  function rebuildVoiceIndex(): void {
    if (voiceCharacters.length === 0) return;
    voiceIndexReady = false;
    runIndexBuild(voiceCharacters, { keepExisting: false });
  }

  function openVoiceDialog(character: CategoryPage, sectionIdx?: number, query?: string, lineIdx?: number): void {
    voiceDialogCharacter = character;
    voiceDialogNavSection = sectionIdx ?? 0;
    voiceDialogNavLine = lineIdx;
    voiceDialogNavQuery = query ?? '';
    voiceDialogOpen = true;
  }

  function characterIndexState(title: string): 'failed' | 'no-voice' | 'ready' | 'pending' {
    if (voiceFailedTitles.has(title)) return 'failed';
    const sections = voiceSearchIndex.get(title);
    if (sections === undefined) return 'pending';
    if (sections.length === 0) return 'no-voice';
    return 'ready';
  }
</script>

<div class="voice-char-section">
  {#if voiceCharsLoading}
    <div class="voice-char-skeleton">
      {#each Array(18) as _, i (i)}
        <div class="voice-char-item"><span class="skeleton-line" style="width:48px;height:48px;border-radius:50%;margin:0 auto;"></span><span class="skeleton-line" style="width:56px;"></span></div>
      {/each}
    </div>
  {:else if voiceCharsError && voiceCharacters.length === 0}
    <div class="balance-placeholder text-muted">
      <iconify-icon icon="lucide:alert-circle"></iconify-icon>
      <p>{voiceCharsError}</p>
      <button class="btn outline" style="margin-top:8px;" onclick={() => loadVoiceCharactersAndIndex(true)}>重试</button>
    </div>
  {:else if voiceCharacters.length > 0}
    <div class="voice-workbench">
      <section class="voice-character-pane">
        <div class="voice-pane-header">
          <strong>角色</strong>
          <span>{voiceFilterResult.characters.length} / {voiceCharacters.length}</span>
        </div>
        {#if voiceFilterResult.characters.length > 0}
          <div class="voice-char-grid">
            {#each voiceFilterResult.characters as character (character.pageid)}
              {@const state = characterIndexState(character.title)}
              <button class:is-failed={state === 'failed'} class:is-no-voice={state === 'no-voice'} class:is-pending={state === 'pending'} class="voice-char-item" onclick={() => openVoiceDialog(character)}>
                <div class="voice-char-avatar">
                  {#if character.thumbnail}
                    <img src={character.thumbnail} alt="" loading="lazy">
                  {:else}
                    <span class="hero-avatar-fallback">{character.title.charAt(0)}</span>
                  {/if}
                  {#if state === 'failed'}
                    <span class="voice-char-badge failed" title="索引失败">!</span>
                  {:else if state === 'no-voice'}
                    <span class="voice-char-badge muted" title="暂无语音">–</span>
                  {:else if state === 'pending'}
                    <span class="voice-char-badge pending" title="未索引">…</span>
                  {/if}
                </div>
                <span class="voice-char-name">{character.title}</span>
              </button>
            {/each}
          </div>
        {:else}
          <div class="voice-pane-empty">
            <iconify-icon icon="lucide:users-round"></iconify-icon>
            <span>无匹配角色</span>
          </div>
        {/if}
      </section>

      <aside class={`voice-hit-pane ${query.trim() ? 'has-query' : ''}`}>
        <div class="voice-pane-header">
          <strong>台词命中</strong>
          <span>
            {#if voiceFilterResult.hits}
              {voiceFilterResult.hitTotal > VOICE_HIT_LIMIT
                ? `前 ${voiceFilterResult.hits.length} / ${voiceFilterResult.hitTotal}`
                : voiceFilterResult.hits.length}
            {:else}
              0
            {/if}
          </span>
        </div>
        {#if voiceFilterResult.hits && voiceFilterResult.hits.length > 0}
          <div class="voice-search-hits">
            {#each voiceFilterResult.hits as hit (hit.character.pageid + '-' + hit.sectionIdx + '-' + hit.lineIdx)}
              <button class="voice-hit-item" onclick={() => openVoiceDialog(hit.character, hit.sectionIdx, query.trim(), hit.lineIdx)}>
                <div class="voice-hit-avatar">
                  {#if hit.character.thumbnail}
                    <img src={hit.character.thumbnail} alt="" loading="lazy">
                  {:else}
                    <span class="hero-avatar-fallback">{hit.character.title.charAt(0)}</span>
                  {/if}
                </div>
                <div class="voice-hit-body">
                  <span class="voice-hit-name">{hit.character.title}</span>
                  {#if hit.sectionTitle}
                    <span class="voice-hit-section">{hit.sectionTitle}</span>
                  {/if}
                  <span class="voice-hit-text">{hit.lineText}</span>
                </div>
                <iconify-icon icon="lucide:chevron-right" class="voice-hit-arrow"></iconify-icon>
              </button>
            {/each}
          </div>
          {#if voiceFilterResult.hitTotal > VOICE_HIT_LIMIT}
            <div class="notice-card" role="status">
              <div class="notice-card-glow"></div>
              <div class="notice-card-head">
                <span class="notice-card-icon muted"><iconify-icon icon="lucide:list-filter"></iconify-icon></span>
                <span>
                  <strong class="notice-card-title">仅显示前 {VOICE_HIT_LIMIT} 条</strong>
                  <small class="notice-card-desc">结果较多，可缩小关键词继续筛选</small>
                </span>
              </div>
            </div>
          {/if}
        {:else if query.trim()}
          <div class="voice-pane-empty">
            <iconify-icon icon="lucide:search-x"></iconify-icon>
            <span>{voiceIndexReady || voiceIndexDone > 0 ? '没有匹配台词' : '索引完成后显示台词命中'}</span>
          </div>
        {:else}
          <div class="voice-pane-empty">
            <iconify-icon icon="lucide:message-square-search"></iconify-icon>
            <span>输入台词、章节或角色名以检索</span>
          </div>
        {/if}

        {#if voiceIndexLoading}
          <div class="notice-card" role="status">
            <div class="notice-card-glow"></div>
            <div class="notice-card-head">
              <span class="notice-card-icon loading"><span class="suggest-spinner"></span></span>
              <span>
                <strong class="notice-card-title">{voiceIndexFromCache || voiceIndexReady ? '正在更新索引' : '正在建立索引'}</strong>
                <small class="notice-card-desc">章节与字幕 {voiceIndexDone}/{voiceIndexTotal || '…'}</small>
              </span>
            </div>
            <div class="notice-card-actions">
              <button class="btn outline" type="button" onclick={cancelIndexBuild}>取消</button>
            </div>
          </div>
        {:else if voiceIndexReady}
          <button class="notice-card tone-success notice-card-action" type="button" onclick={rebuildVoiceIndex} title="点击重新建立索引">
            <div class="notice-card-glow"></div>
            <div class="notice-card-head notice-card-head-default">
              <span class="notice-card-icon success"><iconify-icon icon={voiceIndexFromCache ? 'lucide:database' : 'lucide:check-circle-2'}></iconify-icon></span>
              <span>
                <strong class="notice-card-title">{voiceIndexFromCache ? '本地缓存索引' : '索引已就绪'}</strong>
                <small class="notice-card-desc">
                  {voiceSearchIndex.size} 角色已索引
                  {#if voiceIndexBuiltLabel} · {voiceIndexBuiltLabel}{/if}
                  {#if voiceIndexPending > 0} · 未索引 {voiceIndexPending}{/if}
                </small>
              </span>
            </div>
            <div class="notice-card-head notice-card-head-hover" aria-hidden="true">
              <span class="notice-card-icon success"><iconify-icon icon="lucide:refresh-cw"></iconify-icon></span>
              <span>
                <strong class="notice-card-title">重新建立索引</strong>
                <small class="notice-card-desc">点击刷新全部角色台词索引</small>
              </span>
            </div>
          </button>

          {#if voiceIndexFailed > 0}
            <div class="notice-card tone-error" role="alert">
              <div class="notice-card-glow"></div>
              <div class="notice-card-head">
                <span class="notice-card-icon error"><iconify-icon icon="lucide:alert-circle"></iconify-icon></span>
                <span>
                  <strong class="notice-card-title">{voiceIndexFailed} 个角色索引失败</strong>
                  <small class="notice-card-desc">网络异常或上游限制，可逐个或批量重试</small>
                </span>
              </div>
              <div class="notice-card-actions">
                <button class="btn outline" type="button" onclick={retryFailedVoiceIndex}>重试全部失败</button>
                <button class="btn outline" type="button" onclick={() => voiceFailedListOpen = !voiceFailedListOpen}>
                  {voiceFailedListOpen ? '收起列表' : '查看失败项'}
                </button>
              </div>
              {#if voiceFailedListOpen}
                <ul class="voice-failed-list">
                  {#each voiceIndexFailures as item (item.title)}
                    <li class="voice-failed-item">
                      <div class="voice-failed-meta">
                        <strong>{item.title}</strong>
                        <small>{item.error}{item.attempts > 1 ? ` · 已试 ${item.attempts} 次` : ''}</small>
                      </div>
                      <button class="btn outline" type="button" onclick={() => retryOneFailedVoiceIndex(item.title)}>重试</button>
                    </li>
                  {/each}
                </ul>
              {/if}
            </div>
          {/if}

          {#if voiceIndexNoVoice > 0}
            <div class="notice-card tone-warn" role="status">
              <div class="notice-card-glow"></div>
              <div class="notice-card-head">
                <span class="notice-card-icon warn"><iconify-icon icon="lucide:mic-off"></iconify-icon></span>
                <span>
                  <strong class="notice-card-title">{voiceIndexNoVoice} 个角色暂无语音</strong>
                  <small class="notice-card-desc">这些角色目前没有可检索的台词页</small>
                </span>
              </div>
            </div>
          {/if}
        {/if}
      </aside>
    </div>
  {:else}
    <div class="balance-placeholder text-muted">
      <iconify-icon icon="lucide:users"></iconify-icon>
      <p>暂无角色数据</p>
    </div>
  {/if}
</div>

{#if voiceDialogOpen && voiceDialogCharacter}
  <VoiceCharacterDialog bind:dialogRef={voiceDialogRef} character={voiceDialogCharacter} initialSection={voiceDialogNavSection} initialLineIndex={voiceDialogNavLine} highlightQuery={voiceDialogNavQuery} onClose={() => voiceDialogOpen = false} />
{/if}
