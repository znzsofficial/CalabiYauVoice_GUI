<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchNavSections, type NavItem, type NavSection } from './sidebar';
  import {
    getRandomWallpaper,
    isWallpaperEnabled,
    setWallpaperEnabled,
    type WallpaperInfo
  } from './wallpaper';
  import { fetchCharacterAvatars } from './avatars';

  let sections = $state<NavSection[]>([]);
  let loading = $state(true);
  let errorMessage = $state('');
  let filter = $state('');
  let activeTab = $state<'all' | string>('all');
  let searchInputEl = $state<HTMLInputElement | null>(null);

  // 壁纸状态
  let currentWallpaper = $state<WallpaperInfo | null>(null);
  let wallpaperActive = $state(true);
  let changingWallpaper = $state(false);
  let wallpaperModalOpen = $state(false);

  // 角色头像映射
  let avatarMap = $state<Record<string, string>>({});
  let avatarsLoading = $state(false);

  // 阵营分类主题配置
  const factionThemes: Record<string, { color: string; badge: string; icon: string }> = {
    '欧泊': { color: '#3b82f6', badge: '欧泊阵营', icon: 'lucide:shield' },
    '剪刀手': { color: '#ef4444', badge: '剪刀手阵营', icon: 'lucide:scissors' },
    '乌尔比诺': { color: '#f59e0b', badge: '乌尔比诺商会', icon: 'lucide:crown' },
    '晶源体': { color: '#a855f7', badge: '晶源感染生物', icon: 'lucide:biohazard' }
  };

  // 分区主题配置
  const sectionThemes: Record<string, { icon: string; color: string; tag: string }> = {
    '首页': { icon: 'lucide:compass', color: '#d97706', tag: '门户与常用' },
    '角色': { icon: 'lucide:users', color: '#2563eb', tag: '超弦体与阵营' },
    '武器': { icon: 'lucide:crosshair', color: '#dc2626', tag: '枪械与战术' },
    '地图': { icon: 'lucide:map', color: '#059669', tag: '对战场景' },
    '玩法': { icon: 'lucide:gamepad-2', color: '#7c3aed', tag: '模式与系统' },
    '其他': { icon: 'lucide:sparkles', color: '#0891b2', tag: '社区与资料' }
  };

  // 高频精选工具卡片 (Featured Spotlight)
  const featuredTools = [
    {
      title: '角色时装投票',
      desc: '超弦体时装人气投票与排行榜',
      url: 'https://wiki.biligame.com/klbq/%E8%A7%92%E8%89%B2%E6%97%B6%E8%A3%85%E6%8A%95%E7%A5%A8',
      icon: 'lucide:heart-handshake',
      color: '#ec4899',
      tag: '热门投票'
    },
    {
      title: '主武器理论数据表',
      desc: '全武器伤害、射速与衰减倍率比对',
      url: 'https://wiki.biligame.com/klbq/%E4%B8%BB%E6%AD%A6%E5%99%A8%E7%90%86%E8%AE%BA%E6%95%B0%E6%8D%AE%E8%A1%A8',
      icon: 'lucide:file-bar-chart',
      color: '#dc2626',
      tag: '硬核数据'
    },
    {
      title: '武器外观筛选',
      desc: '各枪械金色/紫色皮肤展示图鉴',
      url: 'https://wiki.biligame.com/klbq/%E6%AD%A6%E5%99%A8%E5%A4%96%E8%A7%82%E7%AD%9B%E9%80%89',
      icon: 'lucide:palette',
      color: '#f59e0b',
      tag: '皮肤图鉴'
    },
    {
      title: '对战地图一览',
      desc: '爆破、乱斗、团竞等全模式作战场景',
      url: 'https://wiki.biligame.com/klbq/%E5%9C%B0%E5%9B%BE',
      icon: 'lucide:map-pin',
      color: '#10b981',
      tag: '全图透视'
    },
    {
      title: '福利兑换码',
      desc: '官方最新晶核、基板与礼包兑换码',
      url: 'https://wiki.biligame.com/klbq/%E5%85%91%E6%8D%A2%E7%A0%81',
      icon: 'lucide:gift',
      color: '#8b5cf6',
      tag: '实时福利'
    },
    {
      title: '猫娘百宝箱',
      desc: '生化卡牌、贴纸生成器与重构模拟器',
      url: 'https://wiki.biligame.com/klbq/WIKI_APP',
      icon: 'lucide:box',
      color: '#06b6d4',
      tag: '实用工具'
    }
  ];

  const quickSearchTags = [
    '角色时装投票',
    '星绘',
    '奥黛丽',
    '北极星',
    '主武器理论数据表',
    '爆破模式',
    '兑换码',
    '壁纸',
    '生化卡牌生成器'
  ];

  const filterActive = $derived(filter.trim().length > 0);
  const normalizedFilter = $derived(filter.trim().toLowerCase());

  function matchesQuery(title: string): boolean {
    if (!normalizedFilter) return true;
    return title.toLowerCase().includes(normalizedFilter);
  }

  // 统计分区总条目
  function countSectionTotal(section: NavSection): number {
    return section.items.reduce((acc, item) => acc + (item.children.length > 0 ? item.children.length : 1), 0);
  }

  const allEntriesCount = $derived(
    sections.reduce((acc, s) => acc + countSectionTotal(s), 0)
  );

  // 过滤后的分区列表
  const displaySections = $derived.by(() => {
    let base = sections;
    if (activeTab !== 'all' && !filterActive) {
      base = sections.filter(s => s.title === activeTab);
    }

    if (!filterActive) return base;

    const results: NavSection[] = [];
    for (const section of sections) {
      const matchedItems: NavItem[] = [];
      for (const item of section.items) {
        if (item.children.length > 0) {
          const matchedChildren = item.children.filter(c => matchesQuery(c.title));
          if (matchesQuery(item.title) || matchedChildren.length > 0) {
            matchedItems.push({
              ...item,
              children: matchesQuery(item.title) ? item.children : matchedChildren
            });
          }
        } else if (matchesQuery(item.title)) {
          matchedItems.push(item);
        }
      }
      if (matchedItems.length > 0) {
        results.push({ ...section, items: matchedItems });
      }
    }
    return results;
  });

  const totalMatchesCount = $derived.by(() => {
    if (!filterActive) return allEntriesCount;
    return displaySections.reduce((acc, s) => acc + countSectionTotal(s), 0);
  });

  // 换一张壁纸
  async function rollWallpaper(): Promise<void> {
    if (changingWallpaper) return;
    changingWallpaper = true;
    try {
      currentWallpaper = await getRandomWallpaper(true);
    } catch {
      // 保持当前壁纸
    } finally {
      setTimeout(() => {
        changingWallpaper = false;
      }, 400);
    }
  }

  // 切换壁纸开启状态
  function toggleWallpaper(): void {
    wallpaperActive = !wallpaperActive;
    setWallpaperEnabled(wallpaperActive);
  }

  function handleKeydown(e: KeyboardEvent): void {
    if (e.key === '/' && document.activeElement !== searchInputEl) {
      e.preventDefault();
      searchInputEl?.focus();
    } else if (e.key === 'Escape') {
      if (wallpaperModalOpen) {
        wallpaperModalOpen = false;
      } else if (filterActive) {
        filter = '';
      }
    }
  }

  function applyTag(tag: string): void {
    filter = tag;
    activeTab = 'all';
    searchInputEl?.focus();
  }

  async function loadData(): Promise<void> {
    loading = true;
    errorMessage = '';
    try {
      sections = await fetchNavSections();

      // 自动提取角色候选并预载官方头像
      const charSection = sections.find(s => s.title === '角色');
      if (charSection) {
        const candidates: Array<{ title: string; url?: string | null }> = [];
        for (const item of charSection.items) {
          if (item.children.length > 0) {
            candidates.push(...item.children.map(c => ({ title: c.title, url: c.url })));
          } else {
            candidates.push({ title: item.title, url: item.url });
          }
        }
        avatarsLoading = true;
        fetchCharacterAvatars(candidates)
          .then(map => {
            avatarMap = map;
          })
          .finally(() => {
            avatarsLoading = false;
          });
      }
    } catch (error) {
      sections = [];
      errorMessage = error instanceof Error ? error.message : String(error);
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    wallpaperActive = isWallpaperEnabled();
    getRandomWallpaper(false).then(wp => {
      currentWallpaper = wp;
    });
    void loadData();
  });
</script>

<svelte:window onkeydown={handleKeydown} />

<!-- ── 随机全屏壁纸背景层 (移自 Android 端的 WikiHubWallpaperBackground) ── -->
{#if wallpaperActive && currentWallpaper}
  <div class="wallpaper-background-layer" aria-hidden="true">
    <img
      class="wallpaper-img"
      class:changing={changingWallpaper}
      src={currentWallpaper.url}
      alt=""
      loading="eager"
      referrerpolicy="no-referrer"
    >
    <div class="wallpaper-scrim-gradient"></div>
  </div>
{/if}

<!-- ── 顶部 Sticky Header ── -->
<header class="header" class:has-wallpaper={wallpaperActive && !!currentWallpaper}>
  <div class="header-content">
    <a href="/" class="header-back" aria-label="返回下载页" title="返回卡丘下载主页">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
    </a>

    <h1 class="header-title">
      <img src="/icon.svg" alt="" class="header-logo">
      <span>Wiki 导航</span>
      <span class="header-hub-pill">HUB</span>
    </h1>

    <div class="header-actions">
      <!-- 壁纸小组件按钮 -->
      {#if currentWallpaper}
        <div class="wallpaper-controls-pill">
          <button
            class="wp-ctrl-btn roll-btn"
            class:spinning={changingWallpaper}
            type="button"
            onclick={rollWallpaper}
            title="换一张随机壁纸"
            aria-label="换一张随机壁纸"
          >
            <iconify-icon icon="lucide:dice-5"></iconify-icon>
            <span class="btn-text-desktop">换壁纸</span>
          </button>

          <button
            class="wp-ctrl-btn info-btn"
            type="button"
            onclick={() => wallpaperModalOpen = true}
            title={`当前壁纸：${currentWallpaper.title} (点击全屏预览)`}
            aria-label="预览当前壁纸"
          >
            <iconify-icon icon="lucide:image"></iconify-icon>
            <span class="wp-title-truncate">{currentWallpaper.title}</span>
          </button>

          <button
            class="wp-ctrl-btn toggle-btn"
            class:active={wallpaperActive}
            type="button"
            onclick={toggleWallpaper}
            title={wallpaperActive ? '隐藏背景壁纸' : '显示背景壁纸'}
            aria-label={wallpaperActive ? '隐藏背景壁纸' : '显示背景壁纸'}
          >
            <iconify-icon icon={wallpaperActive ? 'lucide:eye' : 'lucide:eye-off'}></iconify-icon>
          </button>
        </div>
      {/if}

      <a class="header-link search-jump" href="/search/" title="前往 Wiki 全文搜索与素材下载">
        <iconify-icon icon="lucide:search" class="ext-icon"></iconify-icon>
        <span class="btn-text-desktop">搜索</span>
      </a>

      <a class="header-link" href="https://wiki.biligame.com/klbq/" target="_blank" rel="noopener noreferrer" title="前往哔哩哔哩卡拉彼丘官方BWiki">
        <span class="btn-text-desktop">BWiki</span>
        <iconify-icon icon="lucide:external-link" class="ext-icon"></iconify-icon>
      </a>
    </div>
  </div>
</header>

<main class="nav-main">
  <!-- ── 顶部搜索区 ── -->
  <section class="nav-hero-search">
    <div class="search-bar-wrap">
      <iconify-icon icon="lucide:search" class="search-bar-icon"></iconify-icon>
      <input
        bind:this={searchInputEl}
        class="search-bar-input"
        type="search"
        placeholder="快速搜索 Wiki 词条、超弦体、枪械、地图（按 / 快速聚焦）…"
        bind:value={filter}
        aria-label="搜索 Wiki 入口"
      >
      {#if filterActive}
        <button class="search-bar-clear" type="button" onclick={() => filter = ''} aria-label="清空搜索">
          <iconify-icon icon="lucide:x"></iconify-icon>
        </button>
      {:else}
        <span class="search-shortcut" title="按斜杠键聚焦">/</span >
      {/if}
    </div>

    <!-- 热搜推荐快速点击标签 -->
    <div class="quick-tags-wrap" aria-label="快捷搜索建议">
      <span class="quick-tags-label"><iconify-icon icon="lucide:sparkle"></iconify-icon>热搜直达：</span>
      <div class="quick-tags-list">
        {#each quickSearchTags as tag (tag)}
          <button
            class="quick-tag-chip"
            class:active={filter.trim() === tag}
            type="button"
            onclick={() => applyTag(tag)}
          >
            {tag}
          </button>
        {/each}
      </div>
    </div>
  </section>

  <!-- ── 高频热门功能卡片矩阵 (未激活搜索时常驻展示) ── -->
  {#if !filterActive}
    <section class="featured-spotlight-section" aria-label="常用推荐入口">
      <div class="spotlight-section-title">
        <iconify-icon icon="lucide:flame" class="spotlight-title-icon"></iconify-icon>
        <span>高频常用工具</span>
      </div>
      <div class="featured-grid">
        {#each featuredTools as tool (tool.title)}
          <a
            class="featured-tool-card"
            href={tool.url}
            target="_blank"
            rel="noopener noreferrer"
            style="--tool-accent: {tool.color};"
          >
            <div class="tool-card-icon-box" style="background: color-mix(in srgb, {tool.color} 14%, transparent); color: {tool.color};">
              <iconify-icon icon={tool.icon}></iconify-icon>
            </div>
            <div class="tool-card-info">
              <div class="tool-card-title-row">
                <strong class="tool-card-title">{tool.title}</strong>
                <span class="tool-card-tag" style="background: color-mix(in srgb, {tool.color} 10%, transparent); color: {tool.color};">{tool.tag}</span>
              </div>
              <p class="tool-card-desc">{tool.desc}</p>
            </div>
            <iconify-icon icon="lucide:arrow-up-right" class="tool-card-arrow"></iconify-icon>
          </a>
        {/each}
      </div>
    </section>
  {/if}

  <!-- ── 分区切换标签栏 (Segmented Scroller) ── -->
  {#if !loading && !errorMessage && sections.length > 0}
    <nav class="category-tabs-bar" aria-label="分类切换">
      <div class="category-tabs-scroller">
        <button
          class="category-tab"
          class:active={activeTab === 'all' && !filterActive}
          type="button"
          onclick={() => { activeTab = 'all'; filter = ''; }}
        >
          <iconify-icon icon="lucide:layout-grid" class="tab-icon"></iconify-icon>
          <span class="tab-title">全部词条</span>
          <span class="tab-count">{allEntriesCount}</span>
        </button>

        {#each sections as section (section.title)}
          {@const theme = sectionThemes[section.title] || { icon: 'lucide:folder', color: '#6b7280', tag: '' }}
          {@const count = countSectionTotal(section)}
          <button
            class="category-tab"
            class:active={activeTab === section.title && !filterActive}
            type="button"
            onclick={() => { activeTab = section.title; filter = ''; }}
          >
            <iconify-icon icon={theme.icon} class="tab-icon" style="color: {theme.color};"></iconify-icon>
            <span class="tab-title">{section.title}</span>
            <span class="tab-count">{count}</span>
          </button>
        {/each}
      </div>

      {#if filterActive}
        <div class="filter-status-banner">
          <div class="filter-status-left">
            <span>找到 <strong>{totalMatchesCount}</strong> 个匹配入口</span>
            <a class="filter-search-link" href={`/search/?q=${encodeURIComponent(filter.trim())}`}>
              <iconify-icon icon="lucide:search"></iconify-icon>
              <span>在全文搜索中检索「{filter.trim()}」</span>
              <iconify-icon icon="lucide:arrow-right" class="link-arrow"></iconify-icon>
            </a>
          </div>
          <button class="filter-reset-btn" type="button" onclick={() => filter = ''}>清除筛选</button>
        </div>
      {/if}
    </nav>
  {/if}

  <!-- ── 核心内容状态展现 ── -->
  {#if loading}
    <div class="nav-skeleton-list" aria-hidden="true">
      {#each [0, 1, 2] as idx (idx)}
        <div class="section-card skeleton-card">
          <div class="skeleton-header">
            <div class="skeleton-box" style="width: 44px; height: 44px; border-radius: 12px;"></div>
            <div class="skeleton-box" style="width: 140px; height: 24px;"></div>
          </div>
          <div class="skeleton-chips">
            {#each [0, 1, 2, 3, 4, 5, 6, 7] as chip (chip)}
              <div class="skeleton-box" style="width: 120px; height: 42px; border-radius: 12px;"></div>
            {/each}
          </div>
        </div>
      {/each}
    </div>
  {:else if errorMessage}
    <div class="nav-state-card error-state">
      <div class="state-icon-wrap error-icon">
        <iconify-icon icon="lucide:alert-triangle"></iconify-icon>
      </div>
      <h2>加载导航目录失败</h2>
      <p>{errorMessage}</p>
      <button class="state-action-btn" type="button" onclick={loadData}>
        <iconify-icon icon="lucide:refresh-cw"></iconify-icon>
        <span>重新加载</span>
      </button>
    </div>
  {:else if displaySections.length === 0}
    <div class="nav-state-card empty-state">
      <div class="state-icon-wrap empty-icon">
        <iconify-icon icon="lucide:search-x"></iconify-icon>
      </div>
      <h2>未找到匹配的导航项</h2>
      <p>没有找到标题包含「<strong>{filter.trim()}</strong>」的目录，可能是游戏内的技能名、语音台词或详细正文</p>
      <div class="empty-state-actions">
        <a class="state-action-btn primary" href={`/search/?q=${encodeURIComponent(filter.trim())}`}>
          <iconify-icon icon="lucide:search"></iconify-icon>
          <span>在全文搜索中检索「{filter.trim()}」</span>
          <iconify-icon icon="lucide:arrow-right"></iconify-icon>
        </a>
        <button class="state-action-btn" type="button" onclick={() => filter = ''}>
          <iconify-icon icon="lucide:rotate-ccw"></iconify-icon>
          <span>清除搜索词</span>
        </button>
      </div>
    </div>
  {:else}
    <!-- 各大主题分区卡片 -->
    <div class="sections-container">
      {#each displaySections as section (section.title)}
        {@const theme = sectionThemes[section.title] || { icon: 'lucide:folder', color: '#4b5563', tag: '资料' }}
        {@const isCharacterSection = section.title === '角色'}
        {@const directItems = section.items.filter(item => item.children.length === 0)}
        {@const groupItems = section.items.filter(item => item.children.length > 0)}
        {@const sectionCount = countSectionTotal(section)}

        <section class="section-card" style="--section-theme: {theme.color};">
          <!-- 分区卡片头部 -->
          <div class="section-card-header">
            <div class="section-badge-icon" style="background-color: color-mix(in srgb, {theme.color} 14%, transparent); color: {theme.color};">
              <iconify-icon icon={theme.icon}></iconify-icon>
            </div>
            <div class="section-title-wrap">
              <div class="section-title-row">
                <h2 class="section-title">{section.title}</h2>
                <span class="section-total-badge">{sectionCount} 条目</span>
              </div>
              <span class="section-subtitle">{theme.tag}</span>
            </div>
          </div>

          <div class="section-card-body">
            <!-- ── 角色分区专属的阵营图鉴卡片展示 (含官方头像) ── -->
            {#if isCharacterSection}
              <!-- 阵营角色展示 -->
              {#each groupItems as factionGroup (factionGroup.title)}
                {@const factionInfo = factionThemes[factionGroup.title] || { color: '#6b7280', badge: factionGroup.title, icon: 'lucide:users' }}
                <div class="faction-gallery-block" style="--faction-color: {factionInfo.color};">
                  <div class="faction-header-row">
                    <div class="faction-title-pill" style="background: color-mix(in srgb, {factionInfo.color} 12%, transparent); color: {factionInfo.color}; border: 1px solid color-mix(in srgb, {factionInfo.color} 25%, transparent);">
                      <iconify-icon icon={factionInfo.icon}></iconify-icon>
                      <span>{factionGroup.title}</span>
                      <span class="faction-count-badge">{factionGroup.children.length}</span>
                    </div>
                  </div>

                  <div class="character-avatars-grid">
                    {#each factionGroup.children as charItem (charItem.title)}
                      {@const avatarUrl = avatarMap[charItem.title]}
                      <a
                        class="char-avatar-card"
                        href={charItem.url || `https://wiki.biligame.com/klbq/${encodeURIComponent(charItem.title)}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        title={`查看 ${charItem.title} 角色资料`}
                      >
                        <div class="char-avatar-frame">
                          {#if avatarUrl}
                            <img
                              class="char-avatar-img"
                              src={avatarUrl}
                              alt={charItem.title}
                              loading="lazy"
                              referrerpolicy="no-referrer"
                            >
                          {:else}
                            <div class="char-avatar-placeholder">
                              <span>{charItem.title.slice(0, 1)}</span>
                            </div>
                          {/if}
                        </div>
                        <span class="char-name-label">{charItem.title}</span>
                      </a>
                    {/each}
                  </div>
                </div>
              {/each}

              <!-- 角色分区的常规快捷工具 (如角色筛选、时装投票等) -->
              {#if directItems.length > 0}
                <div class="tier-block direct-links-block" style="margin-top: 14px;">
                  <div class="tier-label">
                    <iconify-icon icon="lucide:sparkles" class="tier-icon"></iconify-icon>
                    <span>角色系统与筛选</span>
                  </div>
                  <div class="direct-chips-grid">
                    {#each directItems as item (item.title)}
                      {#if item.url}
                        <a
                          class="nav-link-pill core-pill"
                          href={item.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          title={item.title}
                        >
                          <span class="pill-label">{item.title}</span>
                          <iconify-icon icon="lucide:arrow-up-right" class="pill-arrow"></iconify-icon>
                        </a>
                      {:else}
                        <span class="nav-link-pill pill-plain">
                          <span class="pill-label">{item.title}</span>
                        </span>
                      {/if}
                    {/each}
                  </div>
                </div>
              {/if}

            {:else}
              <!-- ── 非角色分区的通用展示 ── -->

              <!-- 核心直达入口 (Core Direct Links) -->
              {#if directItems.length > 0}
                <div class="tier-block direct-links-block">
                  <div class="tier-label">
                    <iconify-icon icon="lucide:compass" class="tier-icon"></iconify-icon>
                    <span>快捷直达</span>
                  </div>
                  <div class="direct-chips-grid">
                    {#each directItems as item (item.title)}
                      {#if item.url}
                        <a
                          class="nav-link-pill core-pill"
                          href={item.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          title={item.title}
                        >
                          <span class="pill-label">{item.title}</span>
                          <iconify-icon icon="lucide:arrow-up-right" class="pill-arrow"></iconify-icon>
                        </a>
                      {:else}
                        <span class="nav-link-pill pill-plain">
                          <span class="pill-label">{item.title}</span>
                        </span>
                      {/if}
                    {/each}
                  </div>
                </div>
              {/if}

              <!-- 分类子卡片合集 (Sub-groups) -->
              {#if groupItems.length > 0}
                <div class="tier-block groups-block">
                  <div class="tier-label">
                    <iconify-icon icon="lucide:layers" class="tier-icon"></iconify-icon>
                    <span>分类集合</span>
                  </div>

                  <div class="subgroups-grid">
                    {#each groupItems as group (group.title)}
                      <div class="subgroup-card">
                        <!-- 子卡片标题 -->
                        <div class="subgroup-card-header">
                          <div class="subgroup-card-title-wrap">
                            <span class="subgroup-bullet"></span>
                            <strong class="subgroup-card-title">{group.title}</strong>
                            <span class="subgroup-count-badge">{group.children.length}</span>
                          </div>
                          {#if group.url}
                            <a
                              class="subgroup-external-link"
                              href={group.url}
                              target="_blank"
                              rel="noopener noreferrer"
                              title="前往分类原页面"
                            >
                              <iconify-icon icon="lucide:arrow-up-right"></iconify-icon>
                            </a>
                          {/if}
                        </div>

                        <!-- 子条目标签集 -->
                        <div class="subgroup-tag-cloud">
                          {#each group.children as child (child.title)}
                            {#if child.url}
                              <a
                                class="subgroup-tag-chip"
                                href={child.url}
                                target="_blank"
                                rel="noopener noreferrer"
                                title={child.title}
                              >
                                {child.title}
                              </a>
                            {:else}
                              <span class="subgroup-tag-chip tag-plain">
                                {child.title}
                              </span>
                            {/if}
                          {/each}
                        </div>
                      </div>
                    {/each}
                  </div>
                </div>
              {/if}
            {/if}
          </div>
        </section>
      {/each}
    </div>
  {/if}
</main>

<footer class="footer">
  <p>数据实时同步自 <a href="https://wiki.biligame.com/klbq/" target="_blank" rel="noopener noreferrer">卡拉彼丘 BWiki</a> 侧边栏导航 · Powered by MediaWiki API</p>
</footer>

<!-- ── 壁纸全屏大图预览 Modal ── -->
{#if wallpaperModalOpen && currentWallpaper}
  <div
    class="wallpaper-modal-backdrop"
    onclick={(e) => { if (e.target === e.currentTarget) wallpaperModalOpen = false; }}
    onkeydown={(e) => { if (e.key === 'Escape') wallpaperModalOpen = false; }}
    role="dialog"
    aria-modal="true"
    aria-label="壁纸大图预览"
    tabindex="-1"
  >
    <div class="wallpaper-modal-content">
      <div class="wp-modal-header">
        <div class="wp-modal-title-row">
          <iconify-icon icon="lucide:image"></iconify-icon>
          <h3>{currentWallpaper.title}</h3>
        </div>
        <button
          class="wp-modal-close-btn"
          type="button"
          onclick={() => wallpaperModalOpen = false}
          aria-label="关闭预览"
        >
          <iconify-icon icon="lucide:x"></iconify-icon>
        </button>
      </div>

      <div class="wp-modal-img-wrap">
        <img
          src={currentWallpaper.url}
          alt={currentWallpaper.title}
          referrerpolicy="no-referrer"
        >
      </div>

      <div class="wp-modal-footer">
        <span class="wp-modal-tip">170+ 官方壁纸库随机抽取</span>
        <div class="wp-modal-actions">
          <button
            class="wp-action-btn"
            type="button"
            onclick={() => { void rollWallpaper(); }}
          >
            <iconify-icon icon="lucide:dice-5"></iconify-icon>
            <span>换下一张</span>
          </button>
          <a
            class="wp-action-btn primary"
            href={currentWallpaper.url}
            target="_blank"
            rel="noopener noreferrer"
            download={`${currentWallpaper.title}.png`}
          >
            <iconify-icon icon="lucide:download"></iconify-icon>
            <span>下载高清原图</span>
          </a>
        </div>
      </div>
    </div>
  </div>
{/if}
