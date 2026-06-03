<script lang="ts">
  import { onMount } from 'svelte';
  import { toError, formatFileSize as _formatFileSize } from './search/utils';
  import BalanceDialog from './BalanceDialog.svelte';

  const formatFileSize = (bytes: number) => _formatFileSize(bytes, '未知');

  type RawContentItem = { content: string };
  type BalanceOption = { code: string; name: string; character_code?: string; character_image?: string; imageUrl?: string; position_code?: string; positionCode?: string; position_name?: string };
  type BalanceHero = { id: number; heroName: string; winRate: number; selectRate: number; kd: number; damageAve: number; score: number };
  type BalanceSide = 'attackers' | 'defenders';
  type BalanceStatus = 'idle' | 'loading' | 'ready' | 'error';
  type SortField = keyof Omit<BalanceHero, 'id' | 'heroName'>;
  type BalanceSettings = { modes: BalanceOption[]; maps: BalanceOption[]; ranks: BalanceOption[]; seasons: BalanceOption[]; positions: BalanceOption[]; characters: BalanceOption[] };
  type BalanceResult = Record<BalanceSide, BalanceHero[]>;
  type CompareData = Record<BalanceSide, Record<string, Partial<Record<SortField, number>>>>;
  type LatestInfo = { apkUrl?: string; versionName?: string; version?: string; publishedAt?: string; changelog?: string[]; body?: string; apkSize?: number | string };
  type BalanceSettingsPayload = { setting: Record<'mode' | 'map' | 'rank' | 'season', RawContentItem[]>; role_list: { position: RawContentItem[]; role_list: RawContentItem[] } };
  type BalanceSettingsResponse = { code: number; msg?: string; data: { value: BalanceSettingsPayload } };
  type RawHero = Partial<Record<SortField, number>> & { id?: number | string; heroName?: string };
  type BalanceDataResponse = { jData?: { iRet?: number | string; sMsg?: string; data1?: { side1?: RawHero[]; side2?: RawHero[] }; data2?: { side1?: CompareData['attackers']; side2?: CompareData['defenders'] } } };

  const fallbackApk = '/downloads/CalabiYauVoice-latest.apk';
  const chartId = '338985';
  const ideToken = 'b7FM3m';

  let versionName = $state('正在读取...');
  let publishedAt = $state('-');
  let apkSize = $state('正在测量...');
  let apkUrl = $state(fallbackApk);
  let statusText = $state('如果版本信息加载失败，下载按钮仍会使用默认 APK 地址。');
  let changelog: string[] | null = $state(null);
  let copied = $state(false);

  let balanceDialogRef: HTMLDialogElement | null = $state(null);
  let balanceStatus: BalanceStatus = $state('idle');
  let balanceError = $state('');
  let settings = $state(null) as BalanceSettings | null;
  let characterMap = $state({}) as Record<number, BalanceOption>;
  let positionMap = $state({}) as Record<string, BalanceOption>;
  let selectedModeCode = $state('');
  let selectedMapCode = $state('');
  let selectedSeasonCode = $state('');
  let selectedSeason2Code = $state('');
  let selectedRankCodes = $state([]) as string[];
  let balanceResult = $state(null) as BalanceResult | null;
  let compareData = $state(null) as CompareData | null;
  let showAttackers = $state(false);
  let sortField: SortField = $state('winRate');
  let sortDesc = $state(true);
  let balanceOpenSelect = $state('' as '' | 'mode' | 'season' | 'season2');
  let activeNoticeTab = $state('notice');

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

  async function measureApkSize(url: string, knownSize?: number): Promise<void> {
    if (typeof knownSize === 'number' && Number.isFinite(knownSize) && knownSize > 0) {
      apkSize = formatFileSize(knownSize);
      return;
    }
    try {
      const response = await fetchWithTimeout(url, { method: 'HEAD', cache: 'no-store' }, 8000);
      apkSize = formatFileSize(Number(response.headers.get('content-length')));
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
      changelog = Array.isArray(info.changelog) ? info.changelog : String(info.body || '暂无更新日志').split('\n');
      await measureApkSize(nextApkUrl, Number(info.apkSize));
    } catch (error) {
      const err = toError(error);
      versionName = '最新版本';
      publishedAt = err.name === 'AbortError' ? '加载超时' : '读取失败';
      apkSize = '未知';
      apkUrl = fallbackApk;
      statusText = err.name === 'AbortError' ? '读取 latest.json 超时，已切换到默认 APK 下载地址。' : '暂时无法读取 latest.json，已切换到默认 APK 下载地址。';
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

  function parseBalanceOption(item: RawContentItem): BalanceOption {
    return JSON.parse(item.content) as BalanceOption;
  }

  function parseSettings(value: BalanceSettingsPayload): BalanceSettings {
    const setting = value.setting;
    const roleList = value.role_list;
    const parseOptions = (arr: RawContentItem[]) => arr.map(parseBalanceOption);
    return {
      modes: parseOptions(setting.mode), maps: parseOptions(setting.map), ranks: parseOptions(setting.rank),
      seasons: parseOptions(setting.season), positions: roleList.position.map(parseBalanceOption), characters: roleList.role_list.map(parseBalanceOption)
    };
  }

  function parseHero(obj: RawHero): BalanceHero {
    return { id: Number(obj.id ?? 0), heroName: obj.heroName || '', winRate: obj.winRate ?? 0, selectRate: obj.selectRate ?? 0, kd: obj.kd ?? 0, damageAve: obj.damageAve ?? 0, score: obj.score ?? 0 };
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
    balanceDialogRef?.showModal();
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
      characterMap = Object.fromEntries(nextSettings.characters.map(item => [parseInt(item.character_code || item.code, 10), item] as const).filter(([id]) => !Number.isNaN(id)));
      positionMap = Object.fromEntries(nextSettings.positions.map(item => [item.position_code || item.code, item] as const));
      applyBalanceDefaults(nextSettings);
      await loadBalanceData();
    } catch (err) {
      const error = toError(err);
      balanceStatus = 'error';
      balanceError = error.name === 'AbortError' ? '连接官网接口超时，请检查网络或使用客户端查看。' : error.message || '加载失败，请稍后重试';
    }
  }

  async function loadBalanceData(): Promise<void> {
    if (!settings || !selectedModeCode || !selectedSeasonCode) return;
    balanceStatus = 'loading';
    balanceError = '';
    const payload = {
      iChartId: chartId, iSubChartId: chartId, sIdeToken: ideToken,
      mode: selectedModeCode, map: selectedMapCode || '-255',
      rank: selectedRankCodes.length > 0 ? selectedRankCodes : ['-255'],
      season1: selectedSeasonCode, season2: selectedSeason2Code || '0'
    };
    try {
      const resp = await fetchWithTimeout('/api/balance/data', { method: 'POST', headers: { 'Content-Type': 'application/json; charset=utf-8' }, body: JSON.stringify(payload) }, 10000);
      const json = await resp.json() as BalanceDataResponse;
      const jData = json.jData;
      if (!jData || String(jData.iRet) !== '0') throw new Error(jData?.sMsg || '查询失败');
      balanceResult = { attackers: (jData.data1?.side1 || []).map(parseHero), defenders: (jData.data1?.side2 || []).map(parseHero) };
      compareData = jData.data2 && selectedSeason2Code ? { attackers: jData.data2.side1 || {}, defenders: jData.data2.side2 || {} } : null;
      balanceStatus = 'ready';
    } catch (err) {
      const error = toError(err);
      balanceStatus = 'error';
      balanceError = error.name === 'AbortError' ? '查询官网接口超时，请检查网络或使用客户端查看。' : error.message || '查询失败，请稍后重试';
    }
  }

  function handleSetBalanceSelect(kind: 'mode' | 'season' | 'season2', code: string): void {
    if (kind === 'mode') selectedModeCode = code;
    if (kind === 'season') {
      selectedSeasonCode = code;
      if (selectedSeason2Code === selectedSeasonCode) selectedSeason2Code = '';
    }
    if (kind === 'season2') selectedSeason2Code = code;
    balanceOpenSelect = '';
    loadBalanceData();
  }

  function handleToggleMap(code: string): void {
    selectedMapCode = selectedMapCode === code ? '' : code;
    loadBalanceData();
  }

  function handleToggleRank(code: string): void {
    selectedRankCodes = selectedRankCodes.includes(code) ? selectedRankCodes.filter(item => item !== code) : [...selectedRankCodes, code];
    loadBalanceData();
  }

  function handleSetSort(field: SortField): void {
    if (sortField === field) { sortDesc = !sortDesc; } else { sortField = field; sortDesc = true; }
  }

  function handleRetry(): void {
    (settings ? loadBalanceData : loadBalanceSettings)();
  }

  onMount(loadLatestInfo);
</script>

<svelte:head>
  <meta property="og:image" content="/icon.svg">
</svelte:head>

<svelte:window onclick={() => { if (balanceOpenSelect) balanceOpenSelect = ''; }} />

<div class="layout-wrapper">
  <header class="header">
    <div class="header-content">
      <h1 class="header-title">
        <img src="/icon.svg" alt="Logo" class="header-logo">
        卡丘 Wiki 助手
      </h1>
    </div>
  </header>

  <main class="main-content" id="main-content">
    <!-- Suggestion 1: Hero Section -->
    <section class="card shadow-sm hero-section">
      <div class="hero-bg"></div>
      <div class="hero-content">
        <div class="hero-info-panel">
          <div class="hero-version-info">
            <span class="badge">Android 客户端</span>
            <h2 class:loading-pulse={versionName === '正在读取...'}>{versionName}</h2>
            <div class="hero-version-meta">
              <span><iconify-icon icon="lucide:calendar"></iconify-icon> {publishedAt}</span>
              <span><iconify-icon icon="lucide:hard-drive-download"></iconify-icon> {apkSize}</span>
            </div>
          </div>
          <div class="hero-actions">
            <a class="btn primary hero-download-btn" href={apkUrl} download>
              <iconify-icon icon="lucide:download" style="margin-right: 8px; font-size: 1.2em;"></iconify-icon>
              立即下载 APK
            </a>
            <button class:copied class="btn outline hero-copy-btn" onclick={copyDownloadLink}>
              <iconify-icon icon={copied ? "lucide:check" : "lucide:copy"} style="margin-right: 6px;"></iconify-icon>
              {copied ? '已复制' : '复制直链'}
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Suggestion 2: Core Feature Matrix -->
    <section class="feature-matrix">
      <a href="/search/" class="card shadow-sm feature-card index-card">
        <div class="feature-icon wiki-icon"><iconify-icon icon="lucide:book-open"></iconify-icon></div>
        <div class="feature-text">
          <h3>Wiki 搜索</h3>
          <p>实时检索角色、武器与地图资料</p>
        </div>
        <iconify-icon icon="lucide:chevron-right" class="feature-arrow"></iconify-icon>
      </a>
      <a href="/video/" class="card shadow-sm feature-card index-card">
        <div class="feature-icon video-icon"><iconify-icon icon="lucide:scissors"></iconify-icon></div>
        <div class="feature-text">
          <h3>视频工具</h3>
          <p>裁切提取、压制与 B 站缓存合成</p>
        </div>
        <iconify-icon icon="lucide:chevron-right" class="feature-arrow"></iconify-icon>
      </a>
      <button class="card shadow-sm feature-card index-card" onclick={openBalance}>
        <div class="feature-icon balance-icon"><iconify-icon icon="lucide:bar-chart-3"></iconify-icon></div>
        <div class="feature-text">
          <h3>平衡数据</h3>
          <p>查看全角色赛季胜率与选取率</p>
        </div>
        <iconify-icon icon="lucide:chevron-right" class="feature-arrow"></iconify-icon>
      </button>
    </section>

    <div class="grid-layout">
      <div class="col-left">
        <!-- Suggestion 3: Update Log Timeline Style -->
        <section class="card shadow-sm">
          <div class="card-header">
            <div class="card-title"><iconify-icon icon="lucide:history"></iconify-icon>更新日志</div>
          </div>
          <div class="card-body">
            <div class="timeline-container">
              {#if changelog}
                {#each changelog.filter(Boolean).slice(0, 8) as item, index (index)}
                  <div class="timeline-item" style={`animation: fadeInUp 0.5s ease backwards ${index * 0.05}s`}>
                    <div class="timeline-dot"></div>
                    <div class="timeline-content">{String(item).replace(/^[-*•]\s*/, '')}</div>
                  </div>
                {/each}
              {:else}
                <div class="skeleton-timeline">
                  <div class="skeleton-line" style="width: 80%;"></div>
                  <div class="skeleton-line" style="width: 65%;"></div>
                  <div class="skeleton-line" style="width: 70%;"></div>
                </div>
              {/if}
            </div>
          </div>
        </section>

        <!-- Suggestion 3: Merged About & Notice with Tabs -->
        <section class="card shadow-sm tabbed-card">
          <div class="card-tabs">
            <button class:active={activeNoticeTab === 'notice'} onclick={() => activeNoticeTab = 'notice'}>安装提示</button>
            <button class:active={activeNoticeTab === 'about'} onclick={() => activeNoticeTab = 'about'}>关于项目</button>
          </div>
          <div class="card-body">
            {#if activeNoticeTab === 'notice'}
              <div class="notice-content text-muted animate-in">
                <p>Android 可能会提示"未知来源应用"。如果你信任此应用，请在系统提示中允许本次安装。</p>
                <p>下载文件由本站 Cloudflare Pages 托管。若下载没有开始，请长按下载按钮或复制链接到浏览器打开。</p>
              </div>
            {:else}
              <div class="notice-content text-muted animate-in">
                <p>卡丘 Wiki 助手 (CalabiYauVoice) 是面向《卡拉彼丘》玩家的社区工具客户端，整合了 Wiki 浏览、角色资料、高清语音与美术资源检索等常用功能。</p>
                <p>项目由玩家社区自发维护，基于 Compose Multiplatform 构建。我们始终坚持开源与非营利原则。</p>
              </div>
            {/if}
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
          <div class="card-header"><div class="card-title"><iconify-icon icon="lucide:link"></iconify-icon>相关资源</div></div>
          <div class="card-body">
            <div class="link-grid">
              <a class="resource-link" href="https://wiki.biligame.com/klbq/%E9%A6%96%E9%A1%B5" target="_blank" rel="noopener noreferrer">
                <span class="resource-icon"><iconify-icon icon="lucide:book-open"></iconify-icon></span>
                <span style="flex: 1;"><strong>Wiki 首页</strong><small>角色、武器、活动资料</small></span>
                <iconify-icon icon="lucide:external-link" class="text-muted" style="font-size: 14px; opacity: 0.5;"></iconify-icon>
              </a>
              <a class="resource-link" href="https://klbq.idreamsky.com/?nav=home" target="_blank" rel="noopener noreferrer">
                <span class="resource-icon"><iconify-icon icon="lucide:globe"></iconify-icon></span>
                <span style="flex: 1;"><strong>游戏官网</strong><small>官方最新公告与信息</small></span>
                <iconify-icon icon="lucide:external-link" class="text-muted" style="font-size: 14px; opacity: 0.5;"></iconify-icon>
              </a>
              <a class="resource-link" href="https://kc37ot2vpp.feishu.cn/docx/VWj6dYH37oGEOoxv0xYcU7mcnBh" target="_blank" rel="noopener noreferrer">
                <span class="resource-icon"><iconify-icon icon="lucide:image"></iconify-icon></span>
                <span style="flex: 1;"><strong>高清素材库</strong><small>官方美术素材资源</small></span>
                <iconify-icon icon="lucide:external-link" class="text-muted" style="font-size: 14px; opacity: 0.5;"></iconify-icon>
              </a>
              <div class="creator-tooltip-wrap">
                <a class="resource-link" href="https://creatorcenter.idreamsky.com" target="_blank" rel="noopener noreferrer" aria-describedby="creator-tooltip">
                  <span class="resource-icon"><iconify-icon icon="lucide:palette"></iconify-icon></span>
                  <span style="flex: 1;"><strong>创作者中心</strong><small>官方创作者服务平台，获取创作资源与支持。</small></span>
                  <iconify-icon icon="lucide:external-link" class="text-muted" style="font-size: 14px; opacity: 0.5;"></iconify-icon>
                </a>
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

<BalanceDialog
  bind:dialogRef={balanceDialogRef}
  {settings} {characterMap} {positionMap}
  {selectedModeCode} {selectedMapCode} {selectedSeasonCode} {selectedSeason2Code} {selectedRankCodes}
  {balanceResult} {compareData} {showAttackers} {sortField} {sortDesc}
  {balanceOpenSelect} {balanceStatus} {balanceError}
  onSelectMode={(code) => handleSetBalanceSelect('mode', code)}
  onSelectSeason={(code) => handleSetBalanceSelect('season', code)}
  onSelectSeason2={(code) => handleSetBalanceSelect('season2', code)}
  onToggleSelect={(kind) => balanceOpenSelect = kind}
  onToggleMap={handleToggleMap}
  onToggleRank={handleToggleRank}
  onSetSort={handleSetSort}
  onToggleSide={(v) => showAttackers = v}
  onRetry={handleRetry}
/>
