<script lang="ts">
  import { categoryDisplayName } from './utils';
  import CategoryTreeNode from './CategoryTreeNode.svelte';

  let {
    title = '',
    depth = 0,
    selectionDisabled = false,
    selectedCategoryResults = new Set<string>(),
    expandedCategories = new Set<string>(),
    categorySubcats = {} as Record<string, string[]>,
    categorySubcatLoading = new Set<string>(),
    categorySubcatErrors = {} as Record<string, string>,
    onToggleCategory = (_title: string) => {},
    onToggleCategoryExpanded = (_title: string) => {},
    onOpenCategoryFiles = (_title: string) => {},
  }: {
    title?: string;
    depth?: number;
    selectionDisabled?: boolean;
    selectedCategoryResults?: Set<string>;
    expandedCategories?: Set<string>;
    categorySubcats?: Record<string, string[]>;
    categorySubcatLoading?: Set<string>;
    categorySubcatErrors?: Record<string, string>;
    onToggleCategory?: (title: string) => void;
    onToggleCategoryExpanded?: (title: string) => void;
    onOpenCategoryFiles?: (title: string) => void;
  } = $props();

  let displayName = $derived(categoryDisplayName(title));
  let selected = $derived(selectedCategoryResults.has(title));
  let expanded = $derived(expandedCategories.has(title));
  let loading = $derived(categorySubcatLoading.has(title));
  let error = $derived(categorySubcatErrors[title] || '');
  let children = $derived(categorySubcats[title]);
  let knownEmpty = $derived(Array.isArray(children) && children.length === 0);
  let canExpand = $derived(!knownEmpty);
  let childCount = $derived(Array.isArray(children) ? children.length : null);

  function handleExpand(e: MouseEvent): void {
    e.preventDefault();
    e.stopPropagation();
    onToggleCategoryExpanded(title);
  }

  function handleSelect(e: MouseEvent): void {
    e.preventDefault();
    e.stopPropagation();
    if (selectionDisabled) return;
    onToggleCategory(title);
  }

  function handleOpenFiles(e: MouseEvent): void {
    e.preventDefault();
    e.stopPropagation();
    onOpenCategoryFiles(title);
  }

  function handleRowKeydown(e: KeyboardEvent): void {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      e.stopPropagation();
      if (!selectionDisabled) onToggleCategory(title);
    } else if (e.key === 'ArrowRight' && canExpand && !expanded) {
      e.preventDefault();
      onToggleCategoryExpanded(title);
    } else if (e.key === 'ArrowLeft' && expanded) {
      e.preventDefault();
      onToggleCategoryExpanded(title);
    }
  }
</script>

<div
  class="tree-node"
  class:checked={selected}
  class:expanded
  class:child={depth > 0}
  style={`--tree-depth: ${depth}`}
  role="treeitem"
  aria-selected={selected}
  aria-expanded={canExpand ? expanded : undefined}
>
  <div class="tree-row">
    {#if canExpand}
      <button
        class="tree-toggle"
        class:active={expanded}
        type="button"
        onclick={handleExpand}
        aria-label={`${expanded ? '收起' : '展开'} ${displayName}`}
      >
        {#if loading}
          <span class="suggest-spinner tree-spinner"></span>
        {:else}
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d={expanded ? 'm18 15-6-6-6 6' : 'm9 18 6-6-6-6'} />
          </svg>
        {/if}
      </button>
    {:else}
      <span class="tree-spacer" aria-hidden="true"></span>
    {/if}

    <button class="tree-select" type="button" disabled={selectionDisabled} onclick={handleSelect} onkeydown={handleRowKeydown}>
      <iconify-icon class="tree-check" icon={selected ? 'lucide:check-square' : 'lucide:square'} aria-hidden="true"></iconify-icon>
      <iconify-icon class="tree-folder" icon={expanded ? 'lucide:folder-open' : knownEmpty ? 'lucide:folder' : 'lucide:folder'} aria-hidden="true"></iconify-icon>
      <span class="tree-name">{displayName}</span>
      {#if childCount != null && childCount > 0}
        <span class="tree-count">{childCount}</span>
      {/if}
    </button>

    <button class="tree-files" type="button" onclick={handleOpenFiles} title="查看文件" aria-label={`查看 ${displayName} 文件`}>
      <iconify-icon icon="lucide:files"></iconify-icon>
      <span>文件</span>
    </button>
  </div>

  {#if expanded}
    <div class="tree-children" role="group">
      {#if loading && !children}
        <div class="tree-note"><span class="suggest-spinner"></span><span>加载子分类…</span></div>
      {:else if error}
        <div class="tree-note error">
          <span>{error}</span>
          <button class="tree-retry" type="button" onclick={handleExpand}>重试</button>
        </div>
      {:else if children && children.length > 0}
        {#each children as child (child)}
          <CategoryTreeNode
            title={child}
            depth={depth + 1}
            {selectionDisabled}
            {selectedCategoryResults}
            {expandedCategories}
            {categorySubcats}
            {categorySubcatLoading}
            {categorySubcatErrors}
            {onToggleCategory}
            {onToggleCategoryExpanded}
            {onOpenCategoryFiles}
          />
        {/each}
      {:else}
        <div class="tree-note">没有子分类</div>
      {/if}
    </div>
  {/if}
</div>

<style>
  .tree-node {
    --indent: calc(var(--tree-depth) * 14px);
  }

  .tree-row {
    display: flex;
    align-items: center;
    gap: 4px;
    min-height: 36px;
    padding: 2px 4px 2px calc(4px + var(--indent));
    border-radius: 8px;
    transition: background-color 0.15s;
  }

  .tree-node.checked > .tree-row {
    background: color-mix(in srgb, var(--primary) 7%, transparent);
  }

  .tree-row:hover {
    background: color-mix(in srgb, var(--accent) 80%, transparent);
  }

  .tree-toggle,
  .tree-files,
  .tree-select,
  .tree-retry {
    border: 0;
    background: transparent;
    color: inherit;
    font: inherit;
    cursor: pointer;
  }

  .tree-toggle {
    width: 24px;
    height: 24px;
    border-radius: 6px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: var(--muted-foreground);
    flex-shrink: 0;
  }

  .tree-toggle svg {
    width: 14px;
    height: 14px;
  }

  .tree-toggle:hover,
  .tree-toggle.active {
    color: var(--foreground);
    background: var(--background);
  }

  .tree-spinner {
    width: 12px;
    height: 12px;
  }

  .tree-spacer {
    width: 24px;
    height: 24px;
    flex-shrink: 0;
  }

  .tree-select {
    min-width: 0;
    flex: 1 1 auto;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 6px;
    border-radius: 6px;
    text-align: left;
  }

  .tree-select:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }

  .tree-check {
    font-size: 15px;
    color: var(--muted-foreground);
    flex-shrink: 0;
  }

  .tree-node.checked > .tree-row .tree-check {
    color: var(--foreground);
  }

  .tree-folder {
    font-size: 15px;
    color: var(--muted-foreground);
    flex-shrink: 0;
  }

  .tree-name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
    color: var(--foreground);
  }

  .tree-count {
    margin-left: 2px;
    padding: 0 6px;
    border-radius: 999px;
    background: var(--muted);
    color: var(--muted-foreground);
    font-size: 11px;
    line-height: 18px;
    flex-shrink: 0;
  }

  .tree-files {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 28px;
    padding: 0 8px;
    border: 1px solid transparent;
    border-radius: 6px;
    color: var(--muted-foreground);
    font-size: 12px;
    flex-shrink: 0;
  }

  .tree-files iconify-icon {
    font-size: 14px;
  }

  .tree-files:hover {
    color: var(--foreground);
    border-color: var(--border);
    background: var(--background);
  }

  .tree-children {
    position: relative;
  }

  .tree-children::before {
    content: '';
    position: absolute;
    left: calc(15px + var(--indent));
    top: 0;
    bottom: 8px;
    width: 1px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
  }

  .tree-note {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 30px;
    margin-left: calc(28px + var(--indent));
    padding: 4px 8px;
    color: var(--muted-foreground);
    font-size: 12px;
  }

  .tree-note.error {
    color: var(--destructive, #dc2626);
  }

  .tree-retry {
    height: 24px;
    padding: 0 8px;
    border: 1px solid var(--border);
    border-radius: 6px;
    background: var(--background);
    color: var(--foreground);
    font-size: 11px;
  }

  .tree-retry:hover {
    background: var(--accent);
  }

  @media (max-width: 640px) {
    .tree-files span {
      display: none;
    }

    .tree-files {
      width: 28px;
      padding: 0;
      justify-content: center;
    }
  }
</style>
