<script lang="ts">
  import './balance-dialog.css';
  import BalanceAvatar from './BalanceAvatar.svelte';
  import { supportsBalanceScore } from './balanceSeasons';
  import { balanceMetrics, formatBalanceMetric, balanceDelta, filterBalanceHeroes } from './balanceView';
  import type { BalanceOption, BalanceHero, BalanceStatus, SortField, BalanceSettings, BalanceResult, CompareData } from './balanceView';

  let {
    dialogRef = $bindable(null), settings, characterMap = {}, positionMap = {},
    selectedModeCode = '', selectedMapCode = '', selectedSeasonCode = '', selectedSeason2Code = '', selectedRankCodes = [],
    balanceResult = null, compareData = null, showAttackers = false, sortField = 'winRate', sortDesc = true,
    balanceStatus = 'idle', balanceError = '',
    onSelectMode, onSelectSeason, onSelectSeason2, onToggleMap, onToggleRank, onResetFilters, onClearRanks, onSwapSeasons,
    onSetSort, onToggleSide, onRetry
  }: {
    dialogRef?: HTMLDialogElement | null;
    settings: BalanceSettings | null;
    characterMap?: Record<number, BalanceOption>;
    positionMap?: Record<string, BalanceOption>;
    selectedModeCode?: string; selectedMapCode?: string; selectedSeasonCode?: string; selectedSeason2Code?: string;
    selectedRankCodes?: string[];
    balanceResult?: BalanceResult | null; compareData?: CompareData | null;
    showAttackers?: boolean; sortField?: SortField; sortDesc?: boolean;
    balanceStatus?: BalanceStatus; balanceError?: string;
    onSelectMode: (code: string) => void; onSelectSeason: (code: string) => void; onSelectSeason2: (code: string) => void;
    onToggleMap: (code: string) => void; onToggleRank: (code: string) => void;
    onResetFilters: () => void; onClearRanks: () => void; onSwapSeasons: () => void;
    onSetSort: (field: SortField) => void; onToggleSide: (attackers: boolean) => void; onRetry: () => void;
  } = $props();

  let filtersOpen = $state(false);
  let query = $state('');
  let position = $state('');
  let listElement: HTMLDivElement | undefined = $state();
  const selectedSeason = $derived(settings?.seasons.find(item => item.code === selectedSeasonCode));
  const selectedSeason2 = $derived(settings?.seasons.find(item => item.code === selectedSeason2Code));
  const selectedMap = $derived(settings?.maps.find(item => item.code === selectedMapCode));
  const hasCompare = $derived(!!selectedSeason2Code);
  const scoreAvailable = $derived(supportsBalanceScore(selectedSeasonCode));
  const effectiveSort = $derived(sortField === 'score' && !scoreAvailable ? 'winRate' : sortField);
  const side = $derived(showAttackers ? 'attackers' : 'defenders');
  const sideList = $derived(balanceResult?.[side] || []);
  const validCount = $derived(sideList.filter(hero => hero.winRate > 0).length);
  const activeList = $derived(filterBalanceHeroes(sideList, characterMap, query, position, effectiveSort, sortDesc));
  const filterCount = $derived((selectedMapCode ? 1 : 0) + selectedRankCodes.length);
  const rankSummary = $derived(selectedRankCodes.length ? `已选 ${selectedRankCodes.length} 个段位` : '全部段位');

  // A new result/filter starts at the top; do not carry a previous season's scroll offset.
  $effect(() => {
    void balanceResult; void showAttackers; void effectiveSort; void sortDesc; void query; void position;
    listElement?.scrollTo({ top: 0, behavior: 'instant' });
  });

  function closeOnBackdrop(event: MouseEvent): void {
    if (event.target === dialogRef) dialogRef?.close();
  }

  function clearLocalFilters(): void { query = ''; position = ''; }

  function metricValue(hero: BalanceHero, field: SortField): number | undefined {
    return field === 'score' && !scoreAvailable ? undefined : hero[field];
  }

  function previousValue(hero: BalanceHero, field: SortField): number | undefined {
    const previous = compareData?.[side]?.[String(hero.id)];
    if (!previous || !previous.winRate || (field === 'score' && !supportsBalanceScore(selectedSeason2Code))) return undefined;
    return previous[field];
  }
</script>

{#snippet identity(hero: BalanceHero, rank: number)}
  {@const meta = characterMap[hero.id]}
  {@const image = meta?.character_image || meta?.imageUrl}
  {@const role = positionMap[meta?.position_code || meta?.positionCode || '']}
  <div class="bd-identity">
    <span class="bd-mobile-rank">{rank}</span>
    <BalanceAvatar id={hero.id} name={hero.heroName} officialUrl={image} />
    <span class="bd-hero-info"><strong>{hero.heroName}</strong><small>{role?.position_name || role?.name || '超弦体'}</small></span>
  </div>
{/snippet}

{#snippet metric(hero: BalanceHero, field: SortField)}
  {@const current = metricValue(hero, field)}
  {@const previous = previousValue(hero, field)}
  {@const delta = balanceDelta(current, previous, field)}
  <span class="bd-metric-value">{formatBalanceMetric(current, field)}</span>
  {#if hasCompare}
    <span class="bd-metric-previous" title={`对比：${selectedSeason2?.name || ''}`}>
      <span>{formatBalanceMetric(previous, field)}</span>
      {#if delta}<span class:bd-up={delta.startsWith('+')} class:bd-down={delta.startsWith('−')} class="bd-delta" title="pp 表示百分点；正负号仅表示数值变化，不代表角色强弱">{delta}</span>{/if}
    </span>
  {/if}
{/snippet}

<dialog class="bd-dialog" bind:this={dialogRef} onclick={closeOnBackdrop} aria-labelledby="bd-title" aria-describedby="bd-description">
  <div class="bd-shell">
    <header class="bd-header">
      <div class="bd-heading">
        <span class="bd-heading-icon" aria-hidden="true"><iconify-icon icon="lucide:chart-no-axes-combined"></iconify-icon></span>
        <div><h2 id="bd-title">平衡数据</h2><p id="bd-description">官方统计，按赛季探索超弦体表现。</p></div>
      </div>
      <div class="bd-header-actions">
        <a href="https://klbq.idreamsky.com/balanceData" target="_blank" rel="noopener noreferrer" class="bd-source">数据来源 <iconify-icon icon="lucide:arrow-up-right"></iconify-icon></a>
        <button class="bd-icon-button" aria-label="关闭平衡数据" onclick={() => dialogRef?.close()}><iconify-icon icon="lucide:x"></iconify-icon></button>
      </div>
    </header>

    {#if settings}
      <section class="bd-controls" aria-label="查询条件">
        <div class="bd-primary-filters">
          <label class="bd-field bd-mode"><span>游戏模式</span><span class="bd-select-wrap">
            <select value={selectedModeCode} onchange={(e) => onSelectMode(e.currentTarget.value)} aria-label="游戏模式">
              {#each settings.modes as mode (mode.code)}<option value={mode.code}>{mode.name}</option>{/each}
            </select><iconify-icon icon="lucide:chevron-down" aria-hidden="true"></iconify-icon>
          </span></label>
          <label class="bd-field bd-season"><span>当前赛季</span><span class="bd-select-wrap">
            <select value={selectedSeasonCode} onchange={(e) => onSelectSeason(e.currentTarget.value)} aria-label="当前赛季">
              {#each settings.seasons as season (season.code)}<option value={season.code}>S{season.code} · {season.name}</option>{/each}
            </select><iconify-icon icon="lucide:chevron-down" aria-hidden="true"></iconify-icon>
          </span></label>
          <label class="bd-field bd-compare"><span>对比赛季</span><span class="bd-select-wrap">
            <select value={selectedSeason2Code} onchange={(e) => onSelectSeason2(e.currentTarget.value)} aria-label="对比赛季">
              <option value="">不对比</option>
              {#each settings.seasons.filter(s => s.code !== selectedSeasonCode) as season (season.code)}<option value={season.code}>S{season.code} · {season.name}</option>{/each}
            </select><iconify-icon icon="lucide:chevron-down" aria-hidden="true"></iconify-icon>
          </span></label>
          <button class:bd-filter-active={filtersOpen || filterCount > 0} class="bd-filter-toggle" aria-expanded={filtersOpen} aria-controls="bd-advanced" onclick={() => filtersOpen = !filtersOpen}>
            <iconify-icon icon="lucide:sliders-horizontal"></iconify-icon>地图 / 段位{#if filterCount}<span class="bd-filter-count">{filterCount}</span>{/if}
          </button>
        </div>
        {#if filtersOpen}
          <div class="bd-advanced" id="bd-advanced">
            <div class="bd-filter-group"><span class="bd-filter-label">地图<small>单选</small></span><div class="bd-chips">
              <button class:bd-selected={!selectedMapCode} aria-pressed={!selectedMapCode} onclick={() => onToggleMap('')}>全部地图</button>
              {#each settings.maps as map (map.code)}<button class:bd-selected={selectedMapCode === map.code} aria-pressed={selectedMapCode === map.code} onclick={() => onToggleMap(map.code)}>{map.name}</button>{/each}
            </div></div>
            <div class="bd-filter-group"><span class="bd-filter-label">段位<small>多选</small></span><div class="bd-chips">
              <button class:bd-selected={!selectedRankCodes.length} aria-pressed={!selectedRankCodes.length} onclick={() => { if (selectedRankCodes.length) onClearRanks(); }}>全部段位</button>
              {#each settings.ranks as rank (rank.code)}<button class:bd-selected={selectedRankCodes.includes(rank.code)} aria-pressed={selectedRankCodes.includes(rank.code)} onclick={() => onToggleRank(rank.code)}>{rank.name}</button>{/each}
            </div></div>
            <div class="bd-filter-foot"><span>修改条件后自动查询</span><button class="bd-text-button" disabled={!filterCount} onclick={onResetFilters}>重置地图与段位</button></div>
          </div>
        {/if}
      </section>
    {/if}

    <section class="bd-results" aria-label="超弦体统计" aria-busy={balanceStatus === 'loading'}>
      <div class="bd-result-toolbar">
        <div class="bd-sides" role="group" aria-label="攻防方">
          <button class:bd-side-active={!showAttackers} aria-pressed={!showAttackers} onclick={() => onToggleSide(false)}><iconify-icon icon="lucide:shield"></iconify-icon>防守方</button>
          <button class:bd-side-active={showAttackers} aria-pressed={showAttackers} onclick={() => onToggleSide(true)}><iconify-icon icon="lucide:zap"></iconify-icon>进攻方</button>
        </div>
        <div class="bd-local-filters">
          <label class="bd-search"><iconify-icon icon="lucide:search" aria-hidden="true"></iconify-icon><input type="search" placeholder="搜索超弦体" aria-label="搜索超弦体" bind:value={query}></label>
          <span class="bd-select-wrap bd-position"><select aria-label="定位筛选" bind:value={position}><option value="">全部定位</option>
            {#each settings?.positions || [] as role (role.position_code || role.code)}<option value={role.position_code || role.code}>{role.position_name || role.name}</option>{/each}
          </select><iconify-icon icon="lucide:chevron-down" aria-hidden="true"></iconify-icon></span>
        </div>
      </div>

      <div class="bd-context">
        <p>{selectedSeason ? `S${selectedSeason.code} ${selectedSeason.name}` : '正在连接官网'}<span class="bd-context-dot">·</span>{selectedMap?.name || '全部地图'}<span class="bd-context-dot">·</span>{rankSummary}</p>
        <span class="bd-result-count" role="status">{balanceStatus === 'loading' ? '查询中…' : balanceStatus === 'ready' ? `${activeList.length} / ${validCount} 位超弦体` : ''}</span>
      </div>

      {#if hasCompare}
        <div class="bd-compare-legend"><span><strong>上排</strong> 当前赛季 <span class="bd-legend-divider">/</span> <strong>下排</strong> {selectedSeason2?.name} <span class="bd-legend-note">· 变化以对比赛季为基准</span></span><button class="bd-text-button" onclick={onSwapSeasons} title="互换当前与对比赛季"><iconify-icon icon="lucide:arrow-left-right"></iconify-icon>互换</button></div>
      {/if}

      <div class="bd-mobile-sort"><span class="bd-select-wrap"><select aria-label="排序指标" value={effectiveSort} onchange={(e) => onSetSort(e.currentTarget.value as SortField)}>
        {#each balanceMetrics as { field, label } (field)}<option value={field} disabled={field === 'score' && !scoreAvailable}>按{label}排序</option>{/each}
      </select><iconify-icon icon="lucide:chevron-down" aria-hidden="true"></iconify-icon></span><button class="bd-icon-button" aria-label={sortDesc ? '当前降序，切换为升序' : '当前升序，切换为降序'} onclick={() => onSetSort(effectiveSort)}><iconify-icon icon={sortDesc ? 'lucide:arrow-down-wide-narrow' : 'lucide:arrow-up-narrow-wide'}></iconify-icon></button></div>

      <div class="bd-data-scroll" bind:this={listElement}>
        {#if balanceStatus === 'idle' || balanceStatus === 'loading'}
          <div class="bd-loading" role="status"><span class="bd-sr-only">正在查询平衡数据</span>
            {#each Array(7) as _, i (i)}<div class="bd-skeleton-row" aria-hidden="true"><span class="bd-skeleton-avatar"></span><span class="bd-skeleton-name"></span>{#each Array(4) as _, j (j)}<span></span>{/each}</div>{/each}
          </div>
        {:else if balanceStatus === 'error'}
          <div class="bd-empty"><iconify-icon icon="lucide:cloud-off"></iconify-icon><h3>暂时无法获取数据</h3><p>{balanceError}</p><button class="bd-action" onclick={onRetry}><iconify-icon icon="lucide:rotate-cw"></iconify-icon>重新查询</button></div>
        {:else if !activeList.length}
          <div class="bd-empty"><iconify-icon icon={validCount ? 'lucide:search-x' : 'lucide:chart-no-axes-column'}></iconify-icon><h3>{validCount ? '没有匹配的超弦体' : '暂无有效统计'}</h3><p>{validCount ? '试试其他角色名，或取消定位筛选。' : '官网未提供当前赛季、地图、段位与攻防方的有效数据。试试其他查询条件。'}</p>{#if query || position}<button class="bd-action" onclick={clearLocalFilters}>清除搜索与定位</button>{:else if filterCount}<button class="bd-action" onclick={onResetFilters}>查看全部地图与段位</button>{/if}</div>
        {:else}
          <table class="bd-table" class:bd-comparing={hasCompare}>
            <caption class="bd-sr-only">{selectedSeason?.name} · {showAttackers ? '进攻方' : '防守方'}。{hasCompare ? `与${selectedSeason2?.name}对比，上排当前，下排对比。` : ''}</caption>
            <thead><tr><th scope="col" class="bd-rank">#</th><th scope="col" class="bd-name-col">超弦体</th>
              {#each balanceMetrics as { field, label } (field)}<th scope="col" class:bd-sorted={effectiveSort === field} aria-sort={effectiveSort === field ? sortDesc ? 'descending' : 'ascending' : 'none'}>
                <button disabled={field === 'score' && !scoreAvailable} onclick={() => onSetSort(field)}>{label}<iconify-icon icon={effectiveSort === field ? sortDesc ? 'lucide:arrow-down' : 'lucide:arrow-up' : 'lucide:arrow-down-up'}></iconify-icon></button>
              </th>{/each}
            </tr></thead>
            <tbody>{#each activeList as hero, index (hero.id)}<tr>
              <td class="bd-rank">{index + 1}</td><th scope="row" class="bd-name-col">{@render identity(hero, index + 1)}</th>
              {#each balanceMetrics as { field, label } (field)}<td class:bd-sorted={effectiveSort === field} class:bd-win-rate={field === 'winRate'}><span class="bd-metric-label">{label}</span>{@render metric(hero, field)}</td>{/each}
            </tr>{/each}</tbody>
          </table>
        {/if}
      </div>
    </section>

    <footer class="bd-footer"><span><iconify-icon icon="lucide:info"></iconify-icon>{!scoreAvailable || (hasCompare && !supportsBalanceScore(selectedSeason2Code)) ? 'S8 以前无表现分，以 — 标示。' : hasCompare ? 'pp = 百分点；数值变化不代表角色强弱。' : '仅展示官网有有效统计的超弦体。'}</span><button class="bd-text-button" disabled={balanceStatus === 'loading' || balanceStatus === 'idle'} onclick={onRetry} aria-label="刷新平衡数据"><iconify-icon icon="lucide:rotate-cw"></iconify-icon>刷新</button></footer>
  </div>
</dialog>
