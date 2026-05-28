<script lang="ts">
  import { onMount } from 'svelte';

  type RawContentItem = { content: string };
  type BalanceOption = {
    code: string;
    name: string;
    character_code?: string;
    character_image?: string;
    imageUrl?: string;
    position_code?: string;
    positionCode?: string;
    position_name?: string;
  };
  type BalanceHero = {
    id: number;
    heroName: string;
    winRate: number;
    selectRate: number;
    kd: number;
    damageAve: number;
    score: number;
  };
  type BalanceSide = 'attackers' | 'defenders';
  type BalanceStatus = 'idle' | 'loading' | 'ready' | 'error';
  type BalanceSelectKind = '' | 'mode' | 'season' | 'season2';
  type SortField = keyof Omit<BalanceHero, 'id' | 'heroName'>;
  type MetricType = 'percent' | 'kd' | 'num';
  type MetricColumn = [SortField, MetricType, number];
  type BalanceSettings = {
    modes: BalanceOption[];
    maps: BalanceOption[];
    ranks: BalanceOption[];
    seasons: BalanceOption[];
    positions: BalanceOption[];
    characters: BalanceOption[];
  };
  type BalanceResult = Record<BalanceSide, BalanceHero[]>;
  type CompareData = Record<BalanceSide, Record<string, Partial<Record<SortField, number>>>>;
  type LatestInfo = {
    apkUrl?: string;
    versionName?: string;
    version?: string;
    publishedAt?: string;
    changelog?: string[];
    body?: string;
    apkSize?: number | string;
  };
  type BalanceSettingsPayload = {
    setting: Record<'mode' | 'map' | 'rank' | 'season', RawContentItem[]>;
    role_list: {
      position: RawContentItem[];
      role_list: RawContentItem[];
    };
  };
  type BalanceSettingsResponse = {
    code: number;
    msg?: string;
    data: { value: BalanceSettingsPayload };
  };
  type RawHero = Partial<Record<SortField, number>> & {
    id?: number | string;
    heroName?: string;
  };
  type BalanceDataResponse = {
    jData?: {
      iRet?: number | string;
      sMsg?: string;
      data1?: { side1?: RawHero[]; side2?: RawHero[] };
      data2?: { side1?: CompareData['attackers']; side2?: CompareData['defenders'] };
    };
  };

  function toError(error: unknown): Error {
    return error instanceof Error ? error : new Error(String(error));
  }

  const fallbackApk = '/downloads/CalabiYauVoice-latest.apk';

  let versionName = '正在读取...';
  let publishedAt = '-';
  let apkSize = '正在测量...';
  let apkUrl = fallbackApk;
  let statusText = '如果版本信息加载失败，下载按钮仍会使用默认 APK 地址。';
  let changelog: string[] | null = null;
  let copied = false;
  let balanceDialog: HTMLDialogElement;
  let balanceStatus: BalanceStatus = 'idle';
  let balanceError = '';
  let settings: BalanceSettings | null = null;
  let characterMap: Record<number, BalanceOption> = {};
  let positionMap: Record<string, BalanceOption> = {};
  let selectedModeCode = '';
  let selectedMapCode = '';
  let selectedSeasonCode = '';
  let selectedSeason2Code = '';
  let selectedRankCodes: string[] = [];
  let balanceResult: BalanceResult | null = null;
  let compareData: CompareData | null = null;
  let showAttackers = false;
  let sortField: SortField = 'winRate';
  let sortDesc = true;
  let balanceOpenSelect: BalanceSelectKind = '';

  const chartId = '338985';
  const ideToken = 'b7FM3m';
  const sortLabels: Record<SortField, string> = {
    winRate: '胜率',
    selectRate: '选取率',
    kd: 'KD',
    damageAve: '场均伤害',
    score: '场均得分'
  };
  const sortEntries = Object.entries(sortLabels) as Array<[SortField, string]>;
  const metricColumns: MetricColumn[] = [
    ['winRate', 'percent', 15],
    ['selectRate', 'percent', 25],
    ['kd', 'kd', 20],
    ['damageAve', 'num', 20],
    ['score', 'num', 18]
  ];

  $: selectedMode = settings?.modes.find(item => item.code === selectedModeCode) || null;
  $: selectedMap = settings?.maps.find(item => item.code === selectedMapCode) || null;
  $: selectedSeason = settings?.seasons.find(item => item.code === selectedSeasonCode) || null;
  $: selectedSeason2 = settings?.seasons.find(item => item.code === selectedSeason2Code) || null;
  $: activeList = getSortedBalanceList(balanceResult, showAttackers, sortField, sortDesc);
  $: hasCompare = !!(compareData && selectedSeason2);

  function resolveUrl(url: string | null | undefined): string {
    if (!url) return fallbackApk;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    if (url.startsWith('/')) return url;
    return `/${url}`;
  }

  async function fetchWithTimeout(url: string, options: RequestInit = {}, timeoutMs = 10000): Promise<Response> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const resp = await fetch(url, { ...options, signal: controller.signal });
      if (!resp.ok) throw new Error(`服务器返回 ${resp.status} (${resp.statusText || '错误'})`);
      return resp;
    } catch (err) {
      const error = toError(err);
      if (error.name === 'AbortError') throw error;
      if (error.message.includes('fetch') || error.message.includes('Network') || error.message.includes('Load failed')) {
        if (!navigator.onLine) throw new Error('网络已断开，请检查网络连接');
        throw new Error('无法连接到服务器，可能是网络问题或服务器暂时不可用');
      }
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }

  function formatFileSize(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes <= 0) return '未知';
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let unitIndex = 0;

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex += 1;
    }

    return `${size.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
  }

  async function measureApkSize(url: string, knownSize?: number): Promise<void> {
    if (typeof knownSize === 'number' && Number.isFinite(knownSize) && knownSize > 0) {
      apkSize = formatFileSize(knownSize);
      return;
    }

    try {
      const response = await fetchWithTimeout(url, { method: 'HEAD', cache: 'no-store' }, 8000);
      const contentLength = Number(response.headers.get('content-length'));
      apkSize = formatFileSize(contentLength);
    } catch (error) {
      const err = toError(error);
      apkSize = err.name === 'AbortError' ? '测量超时' : '未知';
    }
  }

  async function loadLatestInfo(): Promise<void> {
    try {
      const response = await fetchWithTimeout('/downloads/latest.json', { cache: 'no-store' }, 8000);
      const info = await response.json() as LatestInfo;
      const nextApkUrl = resolveUrl(info.apkUrl);

      apkUrl = nextApkUrl;
      versionName = info.versionName || info.version || '未知版本';
      publishedAt = info.publishedAt || '未标注';
      statusText = '版本信息已更新，点击按钮下载最新 APK。';
      changelog = Array.isArray(info.changelog)
        ? info.changelog
        : String(info.body || '暂无更新日志').split('\n');

      await measureApkSize(nextApkUrl, Number(info.apkSize));
    } catch (error) {
      const err = toError(error);
      versionName = '最新版本';
      publishedAt = err.name === 'AbortError' ? '加载超时' : '读取失败';
      apkSize = '未知';
      apkUrl = fallbackApk;
      statusText = err.name === 'AbortError'
        ? '读取 latest.json 超时，已切换到默认 APK 下载地址。'
        : '暂时无法读取 latest.json，已切换到默认 APK 下载地址。';
      changelog = ['无法读取在线更新日志，请稍后刷新页面重试。'];
      await measureApkSize(fallbackApk);
    }
  }

  async function copyDownloadLink(): Promise<void> {
    const url = new URL(apkUrl, location.origin).href;

    try {
      await navigator.clipboard.writeText(url);
      copied = true;
      setTimeout(() => copied = false, 1500);
    } catch {
      prompt('请手动复制链接：', url);
    }
  }

  function closeOnBackdrop(event: MouseEvent, dialog: HTMLDialogElement): void {
    if (event.target === dialog) dialog.close();
  }

  function optionName(options: BalanceOption[] | undefined, code: string, placeholder: string): string {
    return options?.find(option => option.code === code)?.name || placeholder;
  }

  function setBalanceSelect(kind: 'mode' | 'season' | 'season2', code: string): void {
    if (kind === 'mode') selectedModeCode = code;
    if (kind === 'season') {
      selectedSeasonCode = code;
      if (selectedSeason2Code === selectedSeasonCode) selectedSeason2Code = '';
    }
    if (kind === 'season2') selectedSeason2Code = code;
    balanceOpenSelect = '';
    loadBalanceData();
  }

  function parseBalanceOption(item: RawContentItem): BalanceOption {
    return JSON.parse(item.content) as BalanceOption;
  }

  function parseSettings(value: BalanceSettingsPayload): BalanceSettings {
    const setting = value.setting;
    const roleList = value.role_list;
    const parseOptions = (arr: RawContentItem[]) => arr.map(parseBalanceOption);

    return {
      modes: parseOptions(setting.mode),
      maps: parseOptions(setting.map),
      ranks: parseOptions(setting.rank),
      seasons: parseOptions(setting.season),
      positions: roleList.position.map(parseBalanceOption),
      characters: roleList.role_list.map(parseBalanceOption)
    };
  }

  function applyBalanceDefaults(nextSettings: BalanceSettings): void {
    selectedModeCode = (nextSettings.modes.find(item => item.name === '排位爆破') || nextSettings.modes[0])?.code || '';
    selectedMapCode = '';
    selectedSeasonCode = nextSettings.seasons[0]?.code || '';
    selectedSeason2Code = '';
    selectedRankCodes = [];
    showAttackers = false;
    sortField = 'winRate';
    sortDesc = true;
  }

  async function openBalance(): Promise<void> {
    balanceDialog?.showModal();
    if (!settings && balanceStatus !== 'loading') await loadBalanceSettings();
  }

  async function loadBalanceSettings(): Promise<void> {
    balanceStatus = 'loading';
    balanceError = '';

    try {
      const resp = await fetchWithTimeout('/api/balance/settings', { cache: 'no-store' }, 10000);
      const json = await resp.json() as BalanceSettingsResponse;
      if (json.code !== 0) throw new Error(json.msg || '接口返回异常');

      const nextSettings = parseSettings(json.data.value);
      settings = nextSettings;
      characterMap = Object.fromEntries(nextSettings.characters
        .map(item => [parseInt(item.character_code || item.code, 10), item] as const)
        .filter(([id]) => !Number.isNaN(id)));
      positionMap = Object.fromEntries(nextSettings.positions.map(item => [item.position_code || item.code, item] as const));
      applyBalanceDefaults(nextSettings);
      await loadBalanceData();
    } catch (err) {
      const error = toError(err);
      balanceStatus = 'error';
      balanceError = error.name === 'AbortError'
        ? '连接官网接口超时，请检查网络或使用客户端查看。'
        : error.message || '加载失败，请稍后重试';
    }
  }

  async function loadBalanceData(): Promise<void> {
    if (!settings || !selectedModeCode || !selectedSeasonCode) return;

    balanceStatus = 'loading';
    balanceError = '';

    const payload = {
      iChartId: chartId,
      iSubChartId: chartId,
      sIdeToken: ideToken,
      mode: selectedModeCode,
      map: selectedMapCode || '-255',
      rank: selectedRankCodes.length > 0 ? selectedRankCodes : ['-255'],
      season1: selectedSeasonCode,
      season2: selectedSeason2Code || '0'
    };

    try {
      const resp = await fetchWithTimeout('/api/balance/data', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
        body: JSON.stringify(payload)
      }, 10000);
      const json = await resp.json() as BalanceDataResponse;
      const jData = json.jData;
      if (!jData || String(jData.iRet) !== '0') throw new Error(jData?.sMsg || '查询失败');

      balanceResult = {
        attackers: (jData.data1?.side1 || []).map(parseHero),
        defenders: (jData.data1?.side2 || []).map(parseHero)
      };
      compareData = jData.data2 && selectedSeason2Code
        ? { attackers: jData.data2.side1 || {}, defenders: jData.data2.side2 || {} }
        : null;
      balanceStatus = 'ready';
    } catch (err) {
      const error = toError(err);
      balanceStatus = 'error';
      balanceError = error.name === 'AbortError'
        ? '查询官网接口超时，请检查网络或使用客户端查看。'
        : error.message || '查询失败，请稍后重试';
    }
  }

  function parseHero(obj: RawHero): BalanceHero {
    return {
      id: Number(obj.id ?? 0),
      heroName: obj.heroName || '',
      winRate: obj.winRate ?? 0,
      selectRate: obj.selectRate ?? 0,
      kd: obj.kd ?? 0,
      damageAve: obj.damageAve ?? 0,
      score: obj.score ?? 0
    };
  }

  function toggleRank(code: string): void {
    selectedRankCodes = selectedRankCodes.includes(code)
      ? selectedRankCodes.filter(item => item !== code)
      : [...selectedRankCodes, code];
    loadBalanceData();
  }

  function setSort(field: SortField): void {
    if (sortField === field) {
      sortDesc = !sortDesc;
    } else {
      sortField = field;
      sortDesc = true;
    }
  }

  function getSortedBalanceList(
    result: BalanceResult | null,
    attackersVisible: boolean,
    field: SortField,
    descending: boolean
  ): BalanceHero[] {
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

  onMount(() => {
    loadLatestInfo();
  });
</script>

<svelte:head>
  <meta property="og:image" content="/icon.svg">
</svelte:head>

<svelte:window on:click={() => balanceOpenSelect = ''} />

<div class="layout-wrapper">
  <header class="header">
    <div class="header-content">
      <h1 class="header-title">
        <img src="/icon.svg" alt="Logo" class="header-logo">
        卡丘 Wiki 助手
      </h1>
      <span class="badge">Android 客户端</span>
    </div>
  </header>

  <main class="main-content" id="main-content">
    <div class="grid-layout">
      <div class="col-left">
        <section class="card shadow-sm">
          <div class="card-header">
            <div class="card-title">
              <iconify-icon icon="lucide:download-cloud"></iconify-icon>
              应用下载
            </div>
            <span class="badge">APK</span>
          </div>
          <div class="card-body">
            <div class="info-grid">
              <div class="info-item version-card">
                <span class="info-label"><iconify-icon icon="lucide:tag"></iconify-icon>最新版本</span>
                <strong class:loading-pulse={versionName === '正在读取...'} class="info-value version-value">{versionName}</strong>
              </div>
              <div class="info-item">
                <span class="info-label"><iconify-icon icon="lucide:calendar"></iconify-icon>发布时间</span>
                <strong class:loading-pulse={publishedAt === '-'} class="info-value">{publishedAt}</strong>
              </div>
              <div class="info-item">
                <span class="info-label"><iconify-icon icon="lucide:hard-drive-download"></iconify-icon>文件大小</span>
                <strong class:loading-pulse={apkSize === '正在测量...'} class="info-value">{apkSize}</strong>
              </div>
            </div>

            <div class="download-row">
              <a class="btn primary download-apk-btn" href={apkUrl} download>
                <iconify-icon icon="lucide:download" style="margin-right: 6px; font-size: 1.1em;"></iconify-icon>
                下载 APK
              </a>
              <button class:copied class="copy-link-btn" title={copied ? '已复制' : '复制下载链接'} aria-label={copied ? '已复制' : '复制下载链接'} on:click={copyDownloadLink}>
                <span class="copy-link-label">
                  <iconify-icon icon="lucide:copy"></iconify-icon>
                  复制链接
                </span>
                <span class="copy-link-success">已复制</span>
              </button>
            </div>
            <p class="status-hint">{statusText}</p>
          </div>
        </section>

        <section class="card shadow-sm">
          <div class="card-header">
            <div class="card-title"><iconify-icon icon="lucide:history"></iconify-icon>更新日志</div>
          </div>
          <div class="card-body">
            <ul class="changelog-list text-muted">
              {#if changelog}
                {#each changelog.filter(Boolean) as item, index}
                  <li style={`animation: fadeInUp 0.5s ease backwards ${Math.min(index, 10) * 0.05}s`}>{String(item).replace(/^[-*•]\s*/, '')}</li>
                {/each}
              {:else}
                <li><span class="skeleton-line" style="width: 80%;"></span></li>
                <li><span class="skeleton-line" style="width: 65%;"></span></li>
                <li><span class="skeleton-line" style="width: 70%;"></span></li>
              {/if}
            </ul>
          </div>
        </section>

        <section class="card shadow-sm">
          <div class="card-header"><div class="card-title"><iconify-icon icon="lucide:shield-alert"></iconify-icon>安装提示</div></div>
          <div class="card-body">
            <div class="notice-content text-muted">
              <p>Android 可能会提示"未知来源应用"。如果你信任此应用，请在系统提示中允许本次安装。</p>
              <p>下载文件由本站 Cloudflare Pages 托管。若下载没有开始，请长按下载按钮或复制链接到浏览器打开。</p>
            </div>
          </div>
        </section>

        <section class="card shadow-sm">
          <div class="card-header"><div class="card-title"><iconify-icon icon="lucide:info"></iconify-icon>关于项目</div></div>
          <div class="card-body">
            <div class="notice-content text-muted">
              <p>卡丘 Wiki 助手 (CalabiYauVoice) 是面向《卡拉彼丘》玩家的社区工具客户端，整合了 Wiki 浏览、角色资料、高清语音与美术资源检索，以及高效的本地缓存管理等常用功能。</p>
              <p>项目由玩家社区自发维护，基于 Compose Multiplatform 构建，力求在不同平台上提供一致且流畅的使用体验。我们始终坚持开源与非营利原则，如果你在查阅资料或使用过程中遇到问题，欢迎提交反馈参与共建。</p>
            </div>
          </div>
        </section>
      </div>

      <div class="col-right">
        <section class="card shadow-sm">
          <div class="card-header">
            <div class="card-title"><iconify-icon icon="lucide:github"></iconify-icon>开源社区</div>
            <span class="badge">Apache 2.0</span>
          </div>
          <div class="card-body">
            <div class="notice-content text-muted">
              <p>项目完全开放源代码，所有代码和资源透明可查。我们坚持自由软件精神，致力于建设一个互助、友好的玩家社区环境。</p>
              <div style="margin-top: 16px; display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px;">
                <a class="btn outline" href="https://github.com/znzsofficial/CalabiYauVoice_GUI" target="_blank" rel="noopener noreferrer"><iconify-icon icon="lucide:code-2" style="margin-right: 6px;"></iconify-icon>GitHub 仓库</a>
                <a class="btn outline" href="https://github.com/znzsofficial/CalabiYauVoice_GUI/issues" target="_blank" rel="noopener noreferrer"><iconify-icon icon="lucide:message-square-plus" style="margin-right: 6px;"></iconify-icon>反馈问题</a>
              </div>
            </div>
          </div>
        </section>

        <section class="card shadow-sm">
          <div class="card-header">
            <div class="card-title"><iconify-icon icon="lucide:search"></iconify-icon>Wiki 搜索</div>
            <span class="badge">MediaWiki</span>
          </div>
          <div class="card-body">
            <div class="notice-content text-muted"><p>搜索卡拉彼丘 Wiki 的角色、武器、地图等资料，支持实时搜索建议与命名空间筛选。</p></div>
            <div style="margin-top: 12px;"><a class="btn primary w-full" href="/search/"><iconify-icon icon="lucide:search" style="margin-right: 6px; font-size: 1.1em;"></iconify-icon>打开 Wiki 搜索</a></div>
          </div>
        </section>

        <section class="card shadow-sm">
          <div class="card-header">
            <div class="card-title"><iconify-icon icon="lucide:bar-chart-3"></iconify-icon>平衡数据</div>
            <span class="badge">官网接口</span>
          </div>
          <div class="card-body">
            <div class="notice-content text-muted"><p>查看角色胜率、选取率、K/D 等赛季数据，支持按模式、地图、段位与赛季筛选。</p></div>
            <div style="margin-top: 12px;"><button class="btn primary w-full" on:click={openBalance}><iconify-icon icon="lucide:bar-chart-3" style="margin-right: 6px; font-size: 1.1em;"></iconify-icon>查看平衡数据</button></div>
          </div>
        </section>

        <section class="card shadow-sm">
          <div class="card-header"><div class="card-title"><iconify-icon icon="lucide:link"></iconify-icon>相关链接</div></div>
          <div class="card-body">
            <div class="link-grid">
              <a class="resource-link" href="https://wiki.biligame.com/klbq/%E9%A6%96%E9%A1%B5" target="_blank" rel="noopener noreferrer"><span class="resource-icon"><iconify-icon icon="lucide:book-open"></iconify-icon></span><span style="flex: 1;"><strong>卡拉彼丘 Wiki 首页</strong><small>查看角色、武器、活动、素材与社区整理资料。</small></span><iconify-icon icon="lucide:external-link" class="text-muted" style="font-size: 14px; opacity: 0.5;"></iconify-icon></a>
              <a class="resource-link" href="https://klbq.idreamsky.com/?nav=home" target="_blank" rel="noopener noreferrer"><span class="resource-icon"><iconify-icon icon="lucide:globe"></iconify-icon></span><span style="flex: 1;"><strong>卡拉彼丘官网</strong><small>前往游戏官网，了解最新公告、活动和官方信息。</small></span><iconify-icon icon="lucide:external-link" class="text-muted" style="font-size: 14px; opacity: 0.5;"></iconify-icon></a>
              <a class="resource-link" href="https://kc37ot2vpp.feishu.cn/docx/VWj6dYH37oGEOoxv0xYcU7mcnBh" target="_blank" rel="noopener noreferrer"><span class="resource-icon"><iconify-icon icon="lucide:image"></iconify-icon></span><span style="flex: 1;"><strong>官方素材库</strong><small>官方提供的高清美术素材与资源文档。</small></span><iconify-icon icon="lucide:external-link" class="text-muted" style="font-size: 14px; opacity: 0.5;"></iconify-icon></a>
              <div class="creator-tooltip-wrap">
                <a class="resource-link" href="https://creatorcenter.idreamsky.com" target="_blank" rel="noopener noreferrer" aria-describedby="creator-tooltip"><span class="resource-icon"><iconify-icon icon="lucide:palette"></iconify-icon></span><span style="flex: 1;"><strong>创作者中心</strong><small>官方创作者服务平台，获取创作资源与支持。</small></span><iconify-icon icon="lucide:external-link" class="text-muted" style="font-size: 14px; opacity: 0.5;"></iconify-icon></a>
                <div id="creator-tooltip" class="creator-tooltip" role="tooltip">
                  <div class="creator-tooltip-inner">
                    <div class="creator-tooltip-glow"></div>
                    <div class="creator-tooltip-head">
                      <span class="creator-tooltip-icon"><iconify-icon icon="lucide:monitor-smartphone"></iconify-icon></span>
                      <span>
                        <strong>访问提示</strong>
                        <small>电脑端原站可能无法提交稿件</small>
                      </span>
                    </div>
                    <p>建议前往移动端反代页面，以获得完整的创作者中心功能。</p>
                    <div class="creator-tooltip-actions">
                      <a class="creator-tooltip-primary" href="https://creator.nekolaska.vip/" target="_blank" rel="noopener noreferrer"><iconify-icon icon="lucide:smartphone"></iconify-icon>访问反代</a>
                      <a class="creator-tooltip-secondary" href="https://creatorcenter.idreamsky.com" target="_blank" rel="noopener noreferrer">仍去原站</a>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </main>

  <footer class="footer">
    <p>© 2026 CalabiYauVoice GUI Contributors. Released under the Apache License 2.0.</p>
    <p>本项目为玩家社区工具，非《卡拉彼丘》官方应用；游戏名称、商标与素材版权归其各自权利方所有。</p>
  </footer>
</div>

<dialog class="balance-dialog" bind:this={balanceDialog} on:click={(event) => closeOnBackdrop(event, balanceDialog)}>
  <div class="balance-dialog-inner">
    <div class="balance-dialog-header">
      <h2 class="balance-dialog-title"><iconify-icon icon="lucide:bar-chart-3"></iconify-icon>平衡数据</h2>
      <button class="btn outline balance-close-btn" aria-label="关闭" on:click={() => balanceDialog.close()}><iconify-icon icon="lucide:x"></iconify-icon></button>
    </div>
    <div class="balance-dialog-body">
      {#if settings}
        <div class="balance-filters">
          <div class="filter-row">
            <span class="filter-label">模式</span>
            <div class:open={balanceOpenSelect === 'mode'} class="custom-select cs">
              <button class="cs-trigger" type="button" aria-expanded={balanceOpenSelect === 'mode'} aria-haspopup="listbox" on:click|stopPropagation={() => balanceOpenSelect = balanceOpenSelect === 'mode' ? '' : 'mode'}>
                <span class="cs-value">{optionName(settings.modes, selectedModeCode, '选择模式')}</span>
                <svg class="cs-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
              </button>
              <div class="cs-menu">
                {#each settings.modes as mode}
                  <button class:selected={selectedModeCode === mode.code} class="cs-option" type="button" role="option" aria-selected={selectedModeCode === mode.code} on:click={() => setBalanceSelect('mode', mode.code)}>{mode.name}</button>
                {/each}
              </div>
            </div>
          </div>
          <div class="filter-row">
            <span class="filter-label">赛季</span>
            <div class:open={balanceOpenSelect === 'season'} class="custom-select cs">
              <button class="cs-trigger" type="button" aria-expanded={balanceOpenSelect === 'season'} aria-haspopup="listbox" on:click|stopPropagation={() => balanceOpenSelect = balanceOpenSelect === 'season' ? '' : 'season'}>
                <span class="cs-value">{optionName(settings.seasons, selectedSeasonCode, '选择赛季')}</span>
                <svg class="cs-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
              </button>
              <div class="cs-menu">
                {#each settings.seasons as season}
                  <button class:selected={selectedSeasonCode === season.code} class="cs-option" type="button" role="option" aria-selected={selectedSeasonCode === season.code} on:click={() => setBalanceSelect('season', season.code)}>{season.name}</button>
                {/each}
              </div>
            </div>
          </div>
          <div class="filter-row">
            <span class="filter-label">对比</span>
            <div class:open={balanceOpenSelect === 'season2'} class="custom-select cs">
              <button class="cs-trigger" type="button" aria-expanded={balanceOpenSelect === 'season2'} aria-haspopup="listbox" on:click|stopPropagation={() => balanceOpenSelect = balanceOpenSelect === 'season2' ? '' : 'season2'}>
                <span class:cs-placeholder={!selectedSeason2Code} class="cs-value">{optionName(settings.seasons, selectedSeason2Code, '无')}</span>
                <svg class="cs-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
              </button>
              <div class="cs-menu">
                <button class:selected={!selectedSeason2Code} class="cs-option" type="button" role="option" aria-selected={!selectedSeason2Code} on:click={() => setBalanceSelect('season2', '')}>无</button>
                {#each settings.seasons.filter(season => season.code !== selectedSeasonCode) as season}
                  <button class:selected={selectedSeason2Code === season.code} class="cs-option" type="button" role="option" aria-selected={selectedSeason2Code === season.code} on:click={() => setBalanceSelect('season2', season.code)}>{season.name}</button>
                {/each}
              </div>
            </div>
          </div>
          <div class="filter-row">
            <span class="filter-label">地图</span>
            <div class="chip-group">
              {#each settings.maps as map}
                <button class:active={selectedMapCode === map.code} class="chip" on:click={() => { selectedMapCode = selectedMapCode === map.code ? '' : map.code; loadBalanceData(); }}>{map.name}</button>
              {/each}
            </div>
          </div>
          <div class="filter-row">
            <span class="filter-label">段位</span>
            <div class="chip-group">
              {#each settings.ranks as rank}
                <button class:active={selectedRankCodes.includes(rank.code)} class="chip" on:click={() => toggleRank(rank.code)}>{rank.name}</button>
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
              {#each Array(6) as _}
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
            <button class="btn outline balance-retry-btn" on:click={settings ? loadBalanceData : loadBalanceSettings}>
              <iconify-icon icon="lucide:refresh-cw"></iconify-icon>
              重试
            </button>
          </div>
        {:else}
          <div class="side-tabs" role="tablist" aria-label="攻防方切换">
            <button class:active={!showAttackers} class="side-tab" role="tab" aria-selected={!showAttackers} on:click={() => showAttackers = false}>
              <iconify-icon icon="lucide:shield" style="font-size:14px;"></iconify-icon>
              防守方
            </button>
            <button class:active={showAttackers} class="side-tab" role="tab" aria-selected={showAttackers} on:click={() => showAttackers = true}>
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
                  {#each sortEntries as [field, label]}
                    <th class:sorted={sortField === field} class="sortable col-num" on:click={() => setSort(field)}>
                      {label}<span class="sort-arrow">{sortField === field ? (sortDesc ? '▼' : '▲') : '↕'}</span>
                    </th>
                  {/each}
                </tr>
              </thead>
              <tbody>
                {#if activeList.length > 0}
                  {#each activeList as hero, index}
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
                      {#each metricColumns as [field, type, threshold]}
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

<style>
  .cs-trigger,
  .cs-option {
    font: inherit;
    text-align: left;
  }

  .cs-trigger {
    width: 100%;
  }

  .cs-option {
    border: 0;
    display: block;
    width: 100%;
    background: transparent;
  }
</style>
