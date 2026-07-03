<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchAllCharacters, fetchVoicePageParsetree, type CategoryPage } from './searchApi';
  import { parseVoiceSections } from './voiceParser';
  import { toError } from './utils';
  import VoiceCharacterDialog from './VoiceCharacterDialog.svelte';

  let { query = '' }: { query?: string } = $props();

  let voiceCharacters = $state([]) as CategoryPage[];
  let voiceCharsLoading = $state(false);
  let voiceCharsError = $state('');
  let voiceIndexReady = $state(false);
  let voiceIndexLoading = $state(false);
  let voiceIndexVersion = $state(0);
  let voiceSearchIndex: Map<string, Array<{ title: string; lines: Array<{ category: string; cnText: string; jpText: string; enText: string }> }>> = new Map();
  let voiceSearchQuery = $state('');
  let voiceDialogOpen = $state(false);
  let voiceDialogCharacter = $state(null) as CategoryPage | null;
  let voiceDialogRef = $state(null) as HTMLDialogElement | null;
  let voiceDialogNavSection = $state(0);
  let voiceDialogNavQuery = $state('');

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
  } => {
    void voiceIndexVersion;
    const q = voiceSearchQuery.toLowerCase();
    if (!q) return { characters: voiceCharacters, hits: null };
    if (!voiceIndexReady) {
      return { characters: voiceCharacters.filter(c => c.title.toLowerCase().includes(q)), hits: null };
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
    return { characters: [...matchedChars], hits: hits.slice(0, 30) };
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

  async function buildVoiceSearchIndex(chars: CategoryPage[]): Promise<void> {
    if (voiceIndexReady || voiceIndexLoading) return;
    voiceIndexLoading = true;
    try {
      const idx = new Map<string, Array<{ title: string; lines: Array<{ category: string; cnText: string; jpText: string; enText: string }> }>>();
      const concurrency = 4;
      for (let i = 0; i < chars.length; i += concurrency) {
        const batch = chars.slice(i, i + concurrency);
        await Promise.all(batch.map(async c => {
          try {
            const pt = await fetchVoicePageParsetree(`${c.title}/语音台词`);
            const groups = parseVoiceSections(pt);
            idx.set(c.title, groups.map(g => ({
              title: g.title,
              lines: g.lines.map(l => ({ category: l.category, cnText: l.cnText, jpText: l.jpText, enText: l.enText }))
            })));
          } catch { /* skip character */ }
        }));
      }
      voiceSearchIndex = idx;
      voiceIndexVersion++;
      voiceIndexReady = true;
    } finally {
      voiceIndexLoading = false;
    }
  }

  function openVoiceDialog(character: CategoryPage, sectionIdx?: number, query?: string): void {
    voiceDialogCharacter = character;
    voiceDialogNavSection = sectionIdx ?? 0;
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
    {#if voiceFilterResult.hits && voiceFilterResult.hits.length > 0}
      <div class="voice-search-hits">
        {#each voiceFilterResult.hits as hit (hit.character.pageid + '-' + hit.sectionIdx + '-' + hit.lineIdx)}
          <button class="voice-hit-item" onclick={() => openVoiceDialog(hit.character, hit.sectionIdx, query.trim())}>
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
    {/if}
    {#if voiceFilterResult.characters.length === 0 && (!voiceFilterResult.hits || voiceFilterResult.hits.length === 0)}
      <div class="balance-placeholder text-muted" style="margin-top:16px;">
        <iconify-icon icon="lucide:search-x"></iconify-icon>
        <p>无匹配角色</p>
      </div>
    {/if}
  {:else}
    <div class="balance-placeholder text-muted">
      <iconify-icon icon="lucide:users"></iconify-icon>
      <p>暂无角色数据</p>
    </div>
  {/if}
  {#if voiceIndexLoading}
    <div style="text-align:center;padding:8px;font-size:0.75rem;color:var(--muted-foreground);">
      <span class="suggest-spinner" style="display:inline-block;vertical-align:middle;margin-right:6px;"></span>正在索引章节与字幕…
    </div>
  {/if}
</div>

{#if voiceDialogOpen && voiceDialogCharacter}
  <VoiceCharacterDialog bind:dialogRef={voiceDialogRef} character={voiceDialogCharacter} initialSection={voiceDialogNavSection} highlightQuery={voiceDialogNavQuery} onClose={() => voiceDialogOpen = false} />
{/if}
