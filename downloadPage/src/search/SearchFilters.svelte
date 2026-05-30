<script lang="ts">
  type ProfileValue = 'default' | 'images' | 'all' | 'advanced' | 'voiceCategory' | 'categoryDownload';
  type SortValue = 'relevance' | 'last_edit_desc' | 'last_edit_asc' | 'create_timestamp_desc' | 'incoming_links_desc';
  type NamespaceOption = { id: number; name: string };

  let {
    activeProfile = 'default' as ProfileValue,
    activeSort = 'relevance' as SortValue,
    selectedNS = [0],
    nsList = [] as NamespaceOption[],
    nsExpanded = false,
    onSetProfile = (v: ProfileValue) => {},
    onSetSort = (v: SortValue) => {},
    onToggleNS = (id: number) => {},
    onToggleAllNS = () => {},
    onToggleNSExpanded = () => {},
  }: {
    activeProfile?: ProfileValue;
    activeSort?: SortValue;
    selectedNS?: number[];
    nsList?: NamespaceOption[];
    nsExpanded?: boolean;
    onSetProfile?: (v: ProfileValue) => void;
    onSetSort?: (v: SortValue) => void;
    onToggleNS?: (id: number) => void;
    onToggleAllNS?: () => void;
    onToggleNSExpanded?: () => void;
  } = $props();

  const PROFILES: Array<{ value: ProfileValue; name: string; desc: string }> = [
    { value: 'default', name: '默认', desc: '主命名空间' },
    { value: 'images', name: '文件', desc: '文件命名空间' },
    { value: 'all', name: '全部', desc: '所有命名空间' },
    { value: 'advanced', name: '高级', desc: '自选命名空间' },
    { value: 'voiceCategory', name: '语音分类', desc: '按语音分类打包下载' },
    { value: 'categoryDownload', name: '分类下载', desc: '搜索分类命名空间' }
  ];
  const SORT_OPTIONS: Array<{ value: SortValue; name: string }> = [
    { value: 'relevance', name: '相关度' },
    { value: 'last_edit_desc', name: '最近编辑' },
    { value: 'last_edit_asc', name: '最早编辑' },
    { value: 'create_timestamp_desc', name: '最新创建' },
    { value: 'incoming_links_desc', name: '最多链接' }
  ];
</script>

<div class="filters">
  <div class="filter-group"><span class="filter-label">范围</span><div class="chip-group">{#each PROFILES as profile (profile.value)}<button class:active={profile.value === activeProfile} class="chip" title={profile.desc} onclick={() => onSetProfile(profile.value)}>{profile.name}</button>{/each}</div></div>
  <div class="filter-group"><span class="filter-label">排序</span><div class="chip-group">{#each SORT_OPTIONS as option (option.value)}<button class:active={option.value === activeSort} class="chip" onclick={() => onSetSort(option.value)}>{option.name}</button>{/each}</div></div>
  {#if activeProfile === 'advanced'}
    <div class="filter-group namespace-filter"><span class="filter-label">命名空间</span><div class="chip-group"><button class:active={nsList.every(ns => selectedNS.includes(ns.id))} class="chip" onclick={onToggleAllNS}>全选</button>{#each nsList as ns (ns.id)}<button class:active={selectedNS.includes(ns.id)} class="chip" onclick={() => onToggleNS(ns.id)}>{ns.name}</button>{/each}</div><button class="ns-toggle" title="展开全部命名空间" onclick={onToggleNSExpanded}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={nsExpanded ? 'm18 15-6-6-6 6' : 'm6 9 6 6 6-6'}/></svg></button></div>
  {/if}
</div>
