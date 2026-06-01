<script lang="ts">
  let { value = '', options = [], placeholder = '', open = false, onSelect = (code: string) => {}, onToggle = () => {} }: {
    value?: string;
    options?: Array<{ code: string; name: string }>;
    placeholder?: string;
    open?: boolean;
    onSelect?: (code: string) => void;
    onToggle?: () => void;
  } = $props();

  let displayText = $derived(options.find(o => o.code === value)?.name || placeholder);
  let isPlaceholder = $derived(!options.find(o => o.code === value));
</script>

<div class:open class="custom-select cs">
  <button class="cs-trigger" type="button" aria-expanded={open} aria-haspopup="listbox" onclick={(e) => { e.stopPropagation(); onToggle(); }}>
    <span class:cs-placeholder={isPlaceholder} class="cs-value">{displayText}</span>
    <svg class="cs-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
  </button>
  <div class="cs-menu">
    {#if placeholder}
      <button class:selected={!value} class="cs-option" type="button" role="option" aria-selected={!value} title={placeholder} onclick={() => onSelect('')}>{placeholder}</button>
    {/if}
    {#each options as option (option.code)}
      <button class:selected={value === option.code} class="cs-option" type="button" role="option" aria-selected={value === option.code} title={option.name} onclick={() => onSelect(option.code)}>{option.name}</button>
    {/each}
  </div>
</div>
