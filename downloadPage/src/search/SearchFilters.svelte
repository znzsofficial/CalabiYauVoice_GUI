<script lang="ts">
  import type { NamespaceOption, ProfileValue, SortValue } from './searchTypes';

  type Intent = 'search' | 'download' | 'voice';

  let {
    activeProfile = 'default' as ProfileValue,
    activeSort = 'relevance' as SortValue,
    selectedNS = [0],
    nsList = [] as NamespaceOption[],
    nsExpanded = false,
    disabled = false,
    voiceSubtitleActive = false,
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
    disabled?: boolean;
    /** 语音字幕模式下排序无意义，整行隐藏 */
    voiceSubtitleActive?: boolean;
    onSetProfile?: (v: ProfileValue) => void;
    onSetSort?: (v: SortValue) => void;
    onToggleNS?: (id: number) => void;
    onToggleAllNS?: () => void;
    onToggleNSExpanded?: () => void;
  } = $props();

  /** 第一级：意图 */
  const INTENTS: Array<{ value: Intent; name: string; icon: string; desc: string }> = [
    { value: 'search', name: '搜索', icon: 'lucide:search', desc: '按命名空间检索 Wiki 内容' },
    { value: 'download', name: '批量下载', icon: 'lucide:folder-down', desc: '按分类勾选并打包下载' },
    { value: 'voice', name: '语音字幕', icon: 'lucide:subtitles', desc: '浏览角色语音与字幕' }
  ];

  /** 第二级：意图内的子项（voice 只有一个，不渲染第二级） */
  const PROFILES: Array<{ value: ProfileValue; name: string; desc: string; icon: string; intent: Intent }> = [
    { value: 'default', name: '默认', desc: '主命名空间', icon: 'lucide:file-text', intent: 'search' },
    { value: 'all', name: '全部', desc: '所有命名空间', icon: 'lucide:layers', intent: 'search' },
    { value: 'advanced', name: '高级', desc: '自选命名空间', icon: 'lucide:sliders-horizontal', intent: 'search' },
    { value: 'images', name: '文件', desc: '文件命名空间', icon: 'lucide:image', intent: 'search' },
    { value: 'voiceCategory', name: '语音分类', desc: '按语音分类打包下载', icon: 'lucide:mic', intent: 'download' },
    { value: 'categoryDownload', name: '分类下载', desc: '搜索分类命名空间', icon: 'lucide:folder-tree', intent: 'download' }
  ];

  /** 切换到某个意图时进入的默认子项 */
  const INTENT_DEFAULT: Record<Intent, ProfileValue> = {
    search: 'default',
    download: 'categoryDownload',
    voice: 'voiceSubtitle'
  };

  const SORT_OPTIONS: Array<{ value: SortValue; name: string }> = [
    { value: 'relevance', name: '相关度' },
    { value: 'last_edit_desc', name: '最近编辑' },
    { value: 'last_edit_asc', name: '最早编辑' },
    { value: 'create_timestamp_desc', name: '最新创建' },
    { value: 'incoming_links_desc', name: '最多链接' }
  ];

  let activeIntent = $derived<Intent>(
    activeProfile === 'voiceSubtitle'
      ? 'voice'
      : activeProfile === 'voiceCategory' || activeProfile === 'categoryDownload'
        ? 'download'
        : 'search'
  );
  let subProfiles = $derived(PROFILES.filter(profile => profile.intent === activeIntent));

  function selectIntent(intent: Intent): void {
    if (disabled || activeIntent === intent) return;
    onSetProfile(INTENT_DEFAULT[intent]);
  }
</script>

<div class="filters">
  <div class="filter-group">
    <span class="filter-label">模式</span>
    <div class="mode-segment" role="tablist" aria-label="功能模式">
      {#each INTENTS as intent (intent.value)}
        <button
          class="mode-seg-btn"
          class:active={activeIntent === intent.value}
          type="button"
          role="tab"
          aria-selected={activeIntent === intent.value}
          title={intent.desc}
          disabled={disabled}
          onclick={() => selectIntent(intent.value)}
        >
          <iconify-icon icon={intent.icon}></iconify-icon>
          <span>{intent.name}</span>
        </button>
      {/each}
    </div>
  </div>

  {#if subProfiles.length > 0}
    <div class="filter-group">
      <span class="filter-label">范围</span>
      <div class="chip-group">
        {#each subProfiles as profile (profile.value)}
          <button
            class:active={profile.value === activeProfile}
            class="chip chip-sub"
            title={profile.desc}
            disabled={disabled}
            onclick={() => { if (profile.value !== activeProfile) onSetProfile(profile.value); }}
          >
            <iconify-icon icon={profile.icon}></iconify-icon>{profile.name}
          </button>
        {/each}
      </div>
    </div>
  {/if}

  {#if !voiceSubtitleActive}
    <div class="filter-group"><span class="filter-label">排序</span><div class="chip-group">{#each SORT_OPTIONS as option (option.value)}<button class:active={option.value === activeSort} class="chip" disabled={disabled} onclick={() => onSetSort(option.value)}>{option.name}</button>{/each}</div></div>
  {/if}

  {#if activeProfile === 'advanced'}
    <div class="filter-group namespace-filter"><span class="filter-label">命名空间</span><div class="chip-group"><button class:active={nsList.every(ns => selectedNS.includes(ns.id))} class="chip" disabled={disabled} onclick={onToggleAllNS}>全选</button>{#each nsList as ns (ns.id)}<button class:active={selectedNS.includes(ns.id)} class="chip" disabled={disabled} onclick={() => onToggleNS(ns.id)}>{ns.name}</button>{/each}</div><button class="ns-toggle" title="展开全部命名空间" disabled={disabled} onclick={onToggleNSExpanded}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={nsExpanded ? 'm18 15-6-6-6 6' : 'm6 9 6 6 6-6'}/></svg></button></div>
  {/if}
</div>
