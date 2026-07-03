<script lang="ts">
  import { onMount } from 'svelte';
  import { highlightMatch } from './utils';
  import type { Suggestion } from './searchApi';
  import type { Status } from './searchTypes';

  let {
    value = $bindable(''),
    modePrefix = $bindable(''),
    modeLabel = $bindable('内容'),
    voiceSubtitleActive = false,
    disabled = false,
    status = 'idle' as Status,
    onSubmit = (value: string) => {},
    onClear = () => {},
    onInputChange = (value: string) => {},
    fetchSuggestions = async (_value: string, _modePrefix: string) => [] as Suggestion[],
  }: {
    value?: string;
    modePrefix?: string;
    modeLabel?: string;
    voiceSubtitleActive?: boolean;
    disabled?: boolean;
    status?: Status;
    onSubmit?: (value: string) => void;
    onClear?: () => void;
    onInputChange?: (value: string) => void;
    fetchSuggestions?: (value: string, modePrefix: string) => Promise<Suggestion[]>;
  } = $props();

  const DEBOUNCE_MS = 300;
  const modes: Array<[string, string]> = [['', '内容'], ['intitle:', '标题'], ['insource:', '源码']];

  let modeOpen = $state(false);
  let suggestions: Suggestion[] = $state([]);
  let suggestionsLoading = $state(false);
  let suggestionsReady = $state(false);
  let suggestionsOpen = $state(false);
  let suggestIdx = $state(-1);
  let savedValue = $state('');
  let searchTimer: ReturnType<typeof setTimeout> | undefined;
  let requestId = $state(0);

  let showSuggestDropdown = $derived(!voiceSubtitleActive && suggestionsOpen && !!value.trim() && (suggestionsLoading || suggestionsReady || suggestions.length > 0));

  onMount(() => {
    document.addEventListener('click', handleDocumentClick);
    return () => document.removeEventListener('click', handleDocumentClick);
  });

  function setMode(prefix: string, label: string): void {
    if (disabled) return;
    modePrefix = prefix;
    modeLabel = label;
    modeOpen = false;
    requestId++;
    clearTimeout(searchTimer);
    closeSuggestions();
    suggestionsReady = false;
    suggestionsLoading = false;
  }

  function handleInput(): void {
    if (disabled) return;
    requestId++;
    onInputChange(value);
    clearTimeout(searchTimer);
    if (voiceSubtitleActive) {
      closeSuggestions();
      suggestionsLoading = false;
      suggestionsReady = false;
      return;
    }
    savedValue = value;
    suggestionsReady = false;
    suggestionsOpen = true;
    if (!value.trim()) {
      suggestions = [];
      suggestIdx = -1;
      suggestionsLoading = false;
      return;
    }
    searchTimer = setTimeout(loadSuggestions, DEBOUNCE_MS);
  }

  function handleInputFocus(): void {
    if (disabled) return;
    if (voiceSubtitleActive) return;
    if (suggestions.length > 0 || suggestionsLoading) suggestionsOpen = true;
    else if (value.trim() && status === 'idle') loadSuggestions();
  }

  function handleKeydown(event: KeyboardEvent): void {
    if (disabled) return;
    if (voiceSubtitleActive) {
      if (event.key === 'Enter') {
        event.preventDefault();
        closeSuggestions();
      } else if (event.key === 'Escape') {
        if (value) value = '';
        closeSuggestions();
      }
      return;
    }

    if (suggestions.length > 0 && event.key === 'ArrowDown') {
      event.preventDefault();
      if (suggestIdx === -1) savedValue = value;
      suggestIdx = Math.min(suggestIdx + 1, suggestions.length - 1);
      value = suggestions[suggestIdx].title;
      return;
    }
    if (suggestions.length > 0 && event.key === 'ArrowUp') {
      event.preventDefault();
      if (suggestIdx <= 0) {
        suggestIdx = -1;
        value = savedValue;
      } else {
        suggestIdx -= 1;
        value = suggestions[suggestIdx].title;
      }
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      closeSuggestions();
      onSubmit(value);
    }
    if (event.key === 'Escape') {
      if (suggestionsOpen) {
        closeSuggestions();
        value = savedValue || value;
      } else if (value) {
        onClear();
      }
    }
    if (event.key === 'Tab') closeSuggestions();
  }

  function clear(): void {
    if (disabled) return;
    value = '';
    closeSuggestions();
    suggestionsReady = false;
    suggestionsLoading = false;
    onClear();
  }

  function closeSuggestions(): void {
    suggestions = [];
    suggestionsOpen = false;
    suggestIdx = -1;
    savedValue = '';
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

  async function loadSuggestions(): Promise<void> {
    const id = ++requestId;
    const requestValue = value;
    const requestModePrefix = modePrefix;
    suggestionsLoading = true;
    try {
      const nextSuggestions = await fetchSuggestions(requestValue, requestModePrefix);
      if (id !== requestId || requestValue !== value || requestModePrefix !== modePrefix) return;
      suggestions = nextSuggestions;
      suggestIdx = -1;
    } catch {
      if (id !== requestId || requestValue !== value || requestModePrefix !== modePrefix) return;
      suggestions = [];
    } finally {
      if (id === requestId) {
        suggestionsLoading = false;
        suggestionsReady = true;
      }
    }
  }

  function selectSuggestion(suggestion: Suggestion): void {
    value = suggestion.title;
    closeSuggestions();
    onSubmit(suggestion.title);
  }

  function suggestionPath(title: string): string {
    return `/${title.replace(/ /g, '_')}`;
  }
</script>

<div class="search-box">
  <div class="search-input-wrap">
    {#if !voiceSubtitleActive}
      <div class:open={modeOpen} class="mode-select">
        <button class="mode-trigger" type="button" aria-expanded={modeOpen} aria-haspopup="listbox" disabled={disabled} onclick={() => modeOpen = !modeOpen}><span class="mode-value">{modeLabel}</span><svg class="mode-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg></button>
        <div class="mode-menu" role="listbox">
          {#each modes as [prefix, label]}
            <button class:selected={modePrefix === prefix} class="mode-option" type="button" role="option" aria-selected={modePrefix === prefix} disabled={disabled} onclick={() => setMode(prefix, label)}>{label}</button>
          {/each}
        </div>
      </div>
    {:else}
      <span class="search-mode-badge">
        <iconify-icon icon="lucide:volume-2" style="font-size:0.85rem;"></iconify-icon>
      </span>
    {/if}
    <input bind:value oninput={handleInput} onfocus={handleInputFocus} onkeydown={handleKeydown} type="text" class="search-input" placeholder={voiceSubtitleActive ? '筛选角色名…' : '搜索角色、武器、地图、技能…'} autocomplete="off" disabled={disabled}>
    {#if value}<button class="search-clear" aria-label="清空搜索" disabled={disabled} onclick={clear}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg></button>{/if}
  </div>
  {#if showSuggestDropdown}
    <div class="suggest-dropdown" role="listbox" tabindex="-1" onmousedown={handleSuggestMousedown}>
      {#if suggestionsLoading}
        <div class="suggest-state"><span class="suggest-spinner"></span><span>正在查找建议…</span></div>
      {:else if suggestions.length > 0}
        {#each suggestions as suggestion, index (suggestion.title)}
          <button class:highlighted={suggestIdx === index} class="suggest-item" type="button" role="option" aria-selected={suggestIdx === index} onmouseenter={() => suggestIdx = index} onclick={() => selectSuggestion(suggestion)}>
            <svg class="suggest-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
            <span class="suggest-text"><span class="suggest-title">{@html highlightMatch(suggestion.title, value)}</span><span class="suggest-meta"><span class="suggest-ns">{suggestion.desc}</span><span>{suggestionPath(suggestion.title)}</span>{#if suggestion.pageid}<span>#{suggestion.pageid}</span>{/if}</span></span>
          </button>
        {/each}
      {:else}
        <div class="suggest-state">暂无实时建议，按 Enter 搜索</div>
      {/if}
    </div>
  {/if}
</div>

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
