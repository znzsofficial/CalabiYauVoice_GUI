<script lang="ts">
  import CustomSelect from './CustomSelect.svelte';

  type BalanceOption = { code: string; name: string; character_code?: string; character_image?: string; imageUrl?: string; position_code?: string; positionCode?: string; position_name?: string };
  type BalanceHero = { id: number; heroName: string; winRate: number; selectRate: number; kd: number; damageAve: number; score: number };
  type BalanceSide = 'attackers' | 'defenders';
  type BalanceStatus = 'idle' | 'loading' | 'ready' | 'error';
  type SortField = keyof Omit<BalanceHero, 'id' | 'heroName'>;
  type MetricType = 'percent' | 'kd' | 'num';
  type MetricColumn = [SortField, MetricType, number];
  type BalanceSettings = { modes: BalanceOption[]; maps: BalanceOption[]; ranks: BalanceOption[]; seasons: BalanceOption[]; positions: BalanceOption[]; characters: BalanceOption[] };
  type BalanceResult = Record<BalanceSide, BalanceHero[]>;
  type CompareData = Record<BalanceSide, Record<string, Partial<Record<SortField, number>>>>;

  let {
    dialogRef = $bindable(null),
    settings,
    characterMap = {},
    positionMap = {},
    selectedModeCode = '',
    selectedMapCode = '',
    selectedSeasonCode = '',
    selectedSeason2Code = '',
    selectedRankCodes = [],
    balanceResult = null,
    compareData = null,
    showAttackers = false,
    sortField = 'winRate' as SortField,
    sortDesc = true,
    balanceOpenSelect = '' as '' | 'mode' | 'season' | 'season2',
    balanceStatus = 'idle' as BalanceStatus,
    balanceError = '',
    onSelectMode = (code: string) => {},
    onSelectSeason = (code: string) => {},
    onSelectSeason2 = (code: string) => {},
    onToggleSelect = (kind: '' | 'mode' | 'season' | 'season2') => {},
    onToggleMap = (code: string) => {},
    onToggleRank = (code: string) => {},
    onSetSort = (field: SortField) => {},
    onToggleSide = (attackers: boolean) => {},
    onRetry = () => {},
  }: {
    dialogRef?: HTMLDialogElement | null;
    settings: BalanceSettings | null;
    characterMap?: Record<number, BalanceOption>;
    positionMap?: Record<string, BalanceOption>;
    selectedModeCode?: string;
    selectedMapCode?: string;
    selectedSeasonCode?: string;
    selectedSeason2Code?: string;
    selectedRankCodes?: string[];
    balanceResult?: BalanceResult | null;
    compareData?: CompareData | null;
    showAttackers?: boolean;
    sortField?: SortField;
    sortDesc?: boolean;
    balanceOpenSelect?: '' | 'mode' | 'season' | 'season2';
    balanceStatus?: BalanceStatus;
    balanceError?: string;
    onSelectMode?: (code: string) => void;
    onSelectSeason?: (code: string) => void;
    onSelectSeason2?: (code: string) => void;
    onToggleSelect?: (kind: '' | 'mode' | 'season' | 'season2') => void;
    onToggleMap?: (code: string) => void;
    onToggleRank?: (code: string) => void;
    onSetSort?: (field: SortField) => void;
    onToggleSide?: (attackers: boolean) => void;
    onRetry?: () => void;
  } = $props();

  const sortLabels: Record<SortField, string> = {
    winRate: '胜率', selectRate: '选取率', kd: 'KD', damageAve: '场均伤害', score: '场均得分'
  };
  const sortEntries = Object.entries(sortLabels) as Array<[SortField, string]>;
  const metricColumns: MetricColumn[] = [
    ['winRate', 'percent', 15], ['selectRate', 'percent', 25], ['kd', 'kd', 20],
    ['damageAve', 'num', 20], ['score', 'num', 18]
  ];

  let selectedMode = $derived(settings?.modes.find(item => item.code === selectedModeCode) || null);
  let selectedSeason = $derived(settings?.seasons.find(item => item.code === selectedSeasonCode) || null);
  let selectedSeason2 = $derived(settings?.seasons.find(item => item.code === selectedSeason2Code) || null);
  let activeList = $derived(getSortedBalanceList(balanceResult, showAttackers, sortField, sortDesc));
  let hasCompare = $derived(!!(compareData && selectedSeason2));

  function closeOnBackdrop(event: MouseEvent): void {
    if (event.target === dialogRef) dialogRef?.close();
  }

  function getSortedBalanceList(result: BalanceResult | null, attackersVisible: boolean, field: SortField, descending: boolean): BalanceHero[] {
    const side = attackersVisible ? 'attackers' : 'defenders';
    const list = (result?.[side] || []).filter(item => item.winRate > 0);
    return [...list].sort((a, b) => descending ? b[field] - a[field] : a[field] - b[field]);
  }

  function formatMetric(value: number, type: MetricType): string {
    if (type === 'percent') return `${value.toFixed(1)}%`;
    if (type === 'kd') return value.toFixed(2);
    return Math.round(value).toLocaleString();
  }

  function metricDiffClass(current: number, previous: number, threshold: number): string {
    if (!Number.isFinite(previous)) return '';
    const diff = current - previous;
    if (diff === 0) return '';
    const pctDiff = Math.abs(diff / (previous || 1)) * 100;
    if (pctDiff < threshold) return '';
    return diff > 0 ? 'cmp-up' : 'cmp-down';
  }

  function metricDiffArrow(current: number, previous: number): string {
    const diff = current - previous;
    if (diff > 0) return '▲';
    if (diff < 0) return '▼';
    return '';
  }
</script>

<dialog class="balance-dialog" bind:this={dialogRef} onclick={closeOnBackdrop}>
  <div class="balance-dialog-inner">
    <div class="balance-dialog-header">
      <h2 class="balance-dialog-title"><iconify-icon icon="lucide:bar-chart-3"></iconify-icon>平衡数据</h2>
      <button class="btn outline balance-close-btn" aria-label="关闭" onclick={() => dialogRef?.close()}><iconify-icon icon="lucide:x"></iconify-icon></button>
    </div>
    <div class="balance-dialog-body">
      {#if settings}
        <div class="balance-filters">
          <div class="filter-row">
            <span class="filter-label">模式</span>
            <CustomSelect value={selectedModeCode} options={settings.modes} open={balanceOpenSelect === 'mode'} onSelect={onSelectMode} onToggle={() => onToggleSelect(balanceOpenSelect === 'mode' ? '' : 'mode')} />
          </div>
          <div class="filter-row">
            <span class="filter-label">赛季</span>
            <CustomSelect value={selectedSeasonCode} options={settings.seasons} open={balanceOpenSelect === 'season'} onSelect={onSelectSeason} onToggle={() => onToggleSelect(balanceOpenSelect === 'season' ? '' : 'season')} />
          </div>
          <div class="filter-row">
            <span class="filter-label">对比</span>
            <CustomSelect value={selectedSeason2Code} options={settings.seasons.filter(s => s.code !== selectedSeasonCode)} placeholder="无" open={balanceOpenSelect === 'season2'} onSelect={onSelectSeason2} onToggle={() => onToggleSelect(balanceOpenSelect === 'season2' ? '' : 'season2')} />
          </div>
          <div class="filter-row">
            <span class="filter-label">地图</span>
            <div class="chip-group">
              {#each settings.maps as map (map.code)}
                <button class:active={selectedMapCode === map.code} class="chip" onclick={() => onToggleMap(map.code)}>{map.name}</button>
              {/each}
            </div>
          </div>
          <div class="filter-row">
            <span class="filter-label">段位</span>
            <div class="chip-group">
              {#each settings.ranks as rank (rank.code)}
                <button class:active={selectedRankCodes.includes(rank.code)} class="chip" onclick={() => onToggleRank(rank.code)}>{rank.name}</button>
              {/each}
            </div>
          </div>
        </div>
      {/if}

      <div class="balance-result-area">
        {#if balanceStatus === 'idle'}
          <div class="balance-placeholder text-muted">
            <div class="balance-placeholder-icon"><iconify-icon icon="lucide:bar-chart-3"></iconify-icon></div>
            <p>正在加载设置…</p>
          </div>
        {:else if balanceStatus === 'loading'}
          <div class="balance-skeleton" aria-label="正在查询平衡数据">
            <div class="balance-skeleton-tabs">
              <span class="skeleton-line skeleton-tab"></span>
              <span class="skeleton-line skeleton-tab"></span>
            </div>
            <div class="balance-skeleton-table">
              {#each Array(6) as _, i (i)}
                <div class="balance-skeleton-row">
                  <span class="skeleton-line skeleton-rank"></span>
                  <span class="skeleton-line skeleton-avatar"></span>
                  <span class="skeleton-line skeleton-name"></span>
                  <span class="skeleton-line skeleton-metric"></span>
                  <span class="skeleton-line skeleton-metric"></span>
                  <span class="skeleton-line skeleton-metric"></span>
                </div>
              {/each}
            </div>
          </div>
        {:else if balanceStatus === 'error'}
          <div class="balance-error">
            <iconify-icon icon="lucide:alert-circle"></iconify-icon>
            <p>{balanceError}</p>
            <button class="btn outline balance-retry-btn" onclick={onRetry}>
              <iconify-icon icon="lucide:refresh-cw"></iconify-icon>
              重试
            </button>
          </div>
        {:else}
          <div class="side-tabs" role="tablist" aria-label="攻防方切换">
            <button class:active={!showAttackers} class="side-tab" role="tab" aria-selected={!showAttackers} onclick={() => onToggleSide(false)}>
              <iconify-icon icon="lucide:shield" style="font-size:14px;"></iconify-icon>
              防守方
            </button>
            <button class:active={showAttackers} class="side-tab" role="tab" aria-selected={showAttackers} onclick={() => onToggleSide(true)}>
              <iconify-icon icon="lucide:zap" style="font-size:14px;"></iconify-icon>
              进攻方
            </button>
          </div>

          {#if hasCompare}
            <div class="cmp-season-label">
              <span class="cmp-tag current">{selectedSeason?.name}</span>
              <span class="cmp-tag compare">{selectedSeason2?.name}</span>
            </div>
          {/if}

          <div class="balance-table-wrap">
            <table class:has-compare={hasCompare} class="balance-table">
              <thead>
                <tr>
                  <th class="col-rank">#</th>
                  <th>角色</th>
                  {#each sortEntries as [field, label] (field)}
                    <th class:sorted={sortField === field} class="sortable col-num" onclick={() => onSetSort(field)}>
                      {label}<span class="sort-arrow">{sortField === field ? (sortDesc ? '▼' : '▲') : '↕'}</span>
                    </th>
                  {/each}
                </tr>
              </thead>
              <tbody>
                {#if activeList.length > 0}
                  {#each activeList as hero, index (hero.id)}
                    {@const meta = characterMap[hero.id]}
                    {@const imgUrl = meta && (meta.character_image || meta.imageUrl)}
                    {@const posCode = meta && (meta.position_code || meta.positionCode)}
                    {@const pos = posCode && positionMap[posCode]}
                    {@const cmp = compareData?.[showAttackers ? 'attackers' : 'defenders']?.[String(hero.id)]}
                    <tr>
                      <td class="col-rank">{index + 1}</td>
                      <td class="col-name">
                        <span class="hero-cell">
                          {#if imgUrl}
                            <img class="hero-avatar" src={imgUrl} alt="" loading="lazy">
                          {:else}
                            <span class="hero-avatar-fallback">{hero.heroName.charAt(0)}</span>
                          {/if}
                          <span class="hero-info">
                            <span class="hero-name">{hero.heroName}</span>
                            {#if pos}
                              <span class="hero-pos">{pos.position_name || pos.name}</span>
                            {/if}
                          </span>
                        </span>
                      </td>
                      {#each metricColumns as [field, type, threshold] (field)}
                        <td class="col-num">
                          {#if hasCompare && cmp}
                            {@const cls = metricDiffClass(hero[field], cmp[field] ?? 0, threshold)}
                            <div class="cell-cmp">
                              <span class={`cell-main ${cls}`}>{formatMetric(hero[field], type)}{#if metricDiffArrow(hero[field], cmp[field] ?? 0)}<span class={`cmp-arrow ${hero[field] > (cmp[field] ?? 0) ? 'up' : 'down'}`}>{metricDiffArrow(hero[field], cmp[field] ?? 0)}</span>{/if}</span>
                              <span class="cell-sub">{formatMetric(cmp[field] ?? 0, type)}</span>
                            </div>
                          {:else}
                            {formatMetric(hero[field], type)}
                          {/if}
                        </td>
                      {/each}
                    </tr>
                  {/each}
                {:else}
                  <tr><td colspan="7" style="text-align:center;padding:24px;color:var(--muted-foreground);">暂无数据</td></tr>
                {/if}
              </tbody>
            </table>
          </div>
        {/if}
      </div>
    </div>
  </div>
</dialog>
