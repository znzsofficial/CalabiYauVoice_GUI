<script lang="ts">
  import type { Snippet } from 'svelte';

  let {
    icon = '',
    tone = '' as '' | 'success' | 'error' | 'warn' | 'loading' | 'muted',
    title = '',
    desc = '',
    center = false,
    role = 'status' as 'status' | 'alert',
    iconSnippet,
    actions,
    children,
  }: {
    /** iconify 图标名；与 iconSnippet 二选一 */
    icon?: string;
    tone?: '' | 'success' | 'error' | 'warn' | 'loading' | 'muted';
    title?: string;
    desc?: string;
    center?: boolean;
    role?: 'status' | 'alert';
    /** 自定义图标内容（如加载 spinner），优先于 icon */
    iconSnippet?: Snippet;
    /** 底部操作区 */
    actions?: Snippet;
    /** 追加在操作区之后的内容（如失败列表） */
    children?: Snippet;
  } = $props();
</script>

<div
  class="notice-card"
  class:notice-card-center={center}
  class:tone-success={tone === 'success'}
  class:tone-error={tone === 'error'}
  class:tone-warn={tone === 'warn'}
  role={role}
>
  <div class="notice-card-glow"></div>
  <div class="notice-card-head">
    <span class="notice-card-icon {tone}">
      {#if iconSnippet}{@render iconSnippet()}{:else}<iconify-icon {icon}></iconify-icon>{/if}
    </span>
    <span>
      <strong class="notice-card-title">{title}</strong>
      {#if desc}<small class="notice-card-desc">{desc}</small>{/if}
    </span>
  </div>
  {#if actions}
    <div class="notice-card-actions">{@render actions()}</div>
  {/if}
  {#if children}{@render children()}{/if}
</div>
