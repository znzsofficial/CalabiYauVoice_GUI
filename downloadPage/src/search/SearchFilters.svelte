<script lang="ts">
  import { searchTask } from './searchExperience';
  import type { NamespaceOption, ProfileValue, SortValue } from './searchTypes';
  let {
    activeProfile = 'voiceSubtitle', activeSort = 'relevance', selectedNS = [0], nsList = [], nsExpanded = false,
    disabled = false, voiceSubtitleActive = false, onSetProfile, onSetSort, onToggleNS, onToggleAllNS, onToggleNSExpanded
  }: {
    activeProfile?: ProfileValue; activeSort?: SortValue; selectedNS?: number[]; nsList?: NamespaceOption[];
    nsExpanded?: boolean; disabled?: boolean; voiceSubtitleActive?: boolean;
    onSetProfile: (v: ProfileValue) => void; onSetSort: (v: SortValue) => void; onToggleNS: (id: number) => void;
    onToggleAllNS: () => void; onToggleNSExpanded: () => void;
  } = $props();
</script>

{#if !voiceSubtitleActive}
  <div class="task-search-options">
    {#if searchTask(activeProfile) === 'assets'}
      <div class="task-subnav" role="group" aria-label="素材查找方式">
        <button class:active={activeProfile === 'images'} aria-pressed={activeProfile === 'images'} disabled={disabled} onclick={() => onSetProfile('images')}>文件搜索</button>
        <button class:active={activeProfile === 'categoryDownload'} aria-pressed={activeProfile === 'categoryDownload'} disabled={disabled} onclick={() => onSetProfile('categoryDownload')}>分类打包</button>
      </div>
    {/if}
    <details class="task-advanced" open={activeProfile === 'advanced' || activeProfile === 'voiceCategory'}>
      <summary>高级选项与排序</summary>
      <div class="task-advanced-body">
        {#if searchTask(activeProfile) === 'wiki'}
          <div class="task-subnav" role="group" aria-label="Wiki 搜索范围">
            {#each [{ value: 'default', label: '条目' }, { value: 'all', label: '全部页面' }, { value: 'advanced', label: '指定命名空间' }] as item (item.value)}
              <button class:active={activeProfile === item.value} disabled={disabled} onclick={() => onSetProfile(item.value as ProfileValue)}>{item.label}</button>
            {/each}
          </div>
        {:else}
          <button class="task-legacy-link" disabled={disabled} onclick={() => onSetProfile('voiceCategory')}>切换至语音分类源（仅限音频无字幕）</button>
        {/if}
        <label class="task-sort-label">结果排序<select aria-label="结果排序" value={activeSort} disabled={disabled} onchange={(e) => onSetSort(e.currentTarget.value as SortValue)}>
          <option value="relevance">相关度</option><option value="last_edit_desc">最近编辑</option><option value="last_edit_asc">最早编辑</option><option value="create_timestamp_desc">最新创建</option><option value="incoming_links_desc">最多链接</option>
        </select></label>
        {#if activeProfile === 'advanced'}
          <p class="task-advanced-note">高级选项：限定检索命名空间范围，配合标题与源码前缀语法使用。</p>
          <div class="chip-group"><button class="chip" disabled={disabled} onclick={onToggleAllNS}>全选 / 清空</button>
            {#each nsList as ns (ns.id)}<button class:active={selectedNS.includes(ns.id)} aria-pressed={selectedNS.includes(ns.id)} class="chip" disabled={disabled} onclick={() => onToggleNS(ns.id)}>{ns.name}</button>{/each}
          </div>
          <button class="task-legacy-link" disabled={disabled} onclick={onToggleNSExpanded}>{nsExpanded ? '收起扩展命名空间' : '展开全部命名空间'}</button>
        {/if}
      </div>
    </details>
  </div>
{/if}
