<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchAllCharacters, fetchVoicePageParsetree, type CategoryPage } from '../searchApi';
  import { parseVoiceSections } from '../voice/voiceParser';
  import { toError } from '../utils';
  import VoiceCharacterDialog from '../voice/VoiceCharacterDialog.svelte';

  let { query = '' }: { query?: string } = $props();

  let voiceCharacters = $state([]) as CategoryPage[];
  let voiceCharsLoading = $state(false);
  let voiceCharsError = $state('');
  let voiceIndexReady = $state(false);
  let voiceIndexLoading = $state(false);
  let voiceIndexVersion = $state(0);
  let voiceIndexFailed = $state(0);
  let voiceIndexNoVoice = $state(0);
  let voiceIndexDone = $state(0);
  let voiceIndexTotal = $state(0);
  let voiceSearchIndex: Map<string, Array<{ title: string; lines: Array<{ category: string; cnText: string; jpText: string; enText: string }> }>> = new Map();
  let voiceIndexFailedTitles = $state(new Set<string>());
  let voiceSearchQuery = $state('');
  let voiceDialogOpen = $state(false);
  let voiceDialogCharacter = $state(null) as CategoryPage | null;
  let voiceDialogRef = $state(null) as HTMLDialogElement | null;
  let voiceDialogNavSection = $state(0);
  let voiceDialogNavLine = $state(undefined) as number | undefined;
  let voiceDialogNavQuery = $state('');
  const VOICE_HIT_LIMIT = 30;

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
    void voiceIndexVersion;
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

  async function loadVoiceCharactersAndIndex(): Promise<void> {
    if (voiceCharsLoading || voiceIndexLoading) return;
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
    await buildVoiceSearchIndex(chars);
  }

  async function buildVoiceSearchIndex(chars: CategoryPage[], force = false): Promise<void> {
    if (!force && (voiceIndexReady || voiceIndexLoading)) return;
    if (voiceIndexLoading) return;
    voiceIndexLoading = true;
    try {
      const idx = force
        ? new Map(voiceSearchIndex)
        : new Map<string, Array<{ title: string; lines: Array<{ category: string; cnText: string; jpText: string; enText: string }> }>>();
      const failedTitles = force ? new Set(voiceIndexFailedTitles) : new Set<string>();
      const targets = force
        ? chars.filter(c => failedTitles.has(c.title) || !voiceSearchIndex.has(c.title))
        : chars;
      voiceIndexTotal = targets.length;
      voiceIndexDone = 0;
      const concurrency = 4;
      for (let i = 0; i < targets.length; i += concurrency) {
        const batch = targets.slice(i, i + concurrency);
        await Promise.all(batch.map(async c => {
          try {
            const pt = await fetchVoicePageParsetree(`${c.title}/语音台词`);
            // Empty parsetree means the character currently has no voice page / lines.
            // Count it as indexed (not a network failure) so the UI doesn't keep showing "失败".
            if (!pt) {
              idx.set(c.title, []);
              failedTitles.delete(c.title);
              return;
            }
            const groups = parseVoiceSections(pt);
            idx.set(c.title, groups.map(g => ({
              title: g.title,
              lines: g.lines.map(l => ({ category: l.category, cnText: l.cnText, jpText: l.jpText, enText: l.enText }))
            })));
            failedTitles.delete(c.title);
          } catch {
            failedTitles.add(c.title);
            idx.delete(c.title);
          } finally {
            voiceIndexDone += 1;
          }
        }));
        // publish partial index so hits appear while indexing continues
        voiceSearchIndex = new Map(idx);
        voiceIndexVersion++;
      }
      voiceSearchIndex = idx;
      voiceIndexFailedTitles = failedTitles;
      voiceIndexFailed = failedTitles.size;
      voiceIndexNoVoice = [...idx.values()].filter(sections => sections.length === 0).length;
      voiceIndexVersion++;
      voiceIndexReady = true;
    } finally {
      voiceIndexLoading = false;
    }
  }

  function retryFailedVoiceIndex(): void {
    if (voiceCharacters.length === 0 || voiceIndexFailedTitles.size === 0) return;
    buildVoiceSearchIndex(voiceCharacters, true);
  }

  function openVoiceDialog(character: CategoryPage, sectionIdx?: number, query?: string, lineIdx?: number): void {
    voiceDialogCharacter = character;
    voiceDialogNavSection = sectionIdx ?? 0;
    voiceDialogNavLine = lineIdx;
    voiceDialogNavQuery = query ?? '';
    voiceDialogOpen = true;
  }
</script>

<div class="voice-char-section">
  {#if voiceCharsLoading}
    <div class="voice-char-skeleton">
      {#each Array(18) as _, i (i)}
        <div class="voice-char-item"><span class="skeleton-line" style="width:48px;height:48px;border-radius:50%;margin:0 auto;"></span><span class="skeleton-line" style="width:56px;"></span></div>
      {/each}
    </div>
  {:else if voiceCharsError}
    <div class="balance-placeholder text-muted">
      <iconify-icon icon="lucide:alert-circle"></iconify-icon>
      <p>{voiceCharsError}</p>
      <button class="btn outline" style="margin-top:8px;" onclick={loadVoiceCharactersAndIndex}>重试</button>
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
              <button class="voice-char-item" onclick={() => openVoiceDialog(character)}>
                <div class="voice-char-avatar">
                  {#if character.thumbnail}
                    <img src={character.thumbnail} alt="" loading="lazy">
                  {:else}
                    <span class="hero-avatar-fallback">{character.title.charAt(0)}</span>
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
            <div class="voice-index-status">仅显示前 {VOICE_HIT_LIMIT} 条命中，请缩小关键词</div>
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
          <div class="voice-index-status">
            <span class="suggest-spinner"></span>
            正在索引章节与字幕… {voiceIndexDone}/{voiceIndexTotal || '…'}
          </div>
        {:else if voiceIndexReady && voiceIndexFailed > 0}
          <div class="voice-index-status">
            {voiceIndexFailed} 个角色索引失败
            <button class="suggestion-link" type="button" onclick={retryFailedVoiceIndex}>重试失败项</button>
          </div>
        {:else if voiceIndexReady && voiceIndexNoVoice > 0}
          <div class="voice-index-status">
            {voiceIndexNoVoice} 个角色暂无语音台词
          </div>
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
