<script lang="ts">
import { onMount, tick } from 'svelte';
  import { fetchNavSections, type NavItem, type NavSection } from './sidebar';
  import {
    getRandomWallpaper,
    isWallpaperEnabled,
    setWallpaperEnabled,
    type WallpaperInfo
  } from './wallpaper';
  import { fetchCharacterAvatars } from './avatars';
  import {
    CATGIRL_TOOLBOX_MODAL,
    OFFICIAL_CHANNELS_MODAL,
    FILTER_TOOLS_MODAL,
    GAME_EXTENSIONS_MODAL,
    type CollectionModalData
  } from './toolboxes';
  import { matchNavTitle } from './searchAliases';
  import {
    CHARACTER_BIRTHDAYS,
    BIRTHDAY_MAP,
    getDaysUntilBirthday,
    getUpcomingBirthdays,
    type CharacterBirthday
  } from './birthdays';
  import {
    fetchGameAssets,
    getItemIcon,
    type GameAssets
  } from './gameAssets';

  let sections = $state<NavSection[]>([]);
  let loading = $state(true);
  let errorMessage = $state('');
  let filter = $state('');
  let activeTab = $state<'all' | string>('all');
  let searchInputEl = $state<HTMLInputElement | null>(null);
  let modalCloseEl = $state<HTMLButtonElement | null>(null);
  let modalTriggerEl = $state<HTMLElement | null>(null);
  let navReloading = $state(false);

  // 游戏资产映射（武器立绘图、地图缩略图）
  let gameAssets = $state<GameAssets>({ weapons: {}, maps: {} });

  // 弹窗状态
  let activeCollectionModal = $state<CollectionModalData | null>(null);
  let birthdayModalOpen = $state(false);

  // 角色生日计算
  const upcomingBirthdays = $derived(getUpcomingBirthdays(3));
  const nearestBirthday = $derived(upcomingBirthdays[0] || null);

  function getCharacterBirthdayInfo(charName: string): { birthday: CharacterBirthday; days: number; isToday: boolean } | null {
    const b = BIRTHDAY_MAP[charName] || BIRTHDAY_MAP[charName.split('·')[0]];
    if (!b) return null;
    const days = getDaysUntilBirthday(b);
    return { birthday: b, days, isToday: days === 0 };
  }

  // 滚动位置
  let scrollY = $state(0);
  const showScrollTop = $derived(scrollY > 350);

  // 壁纸与提示
  let currentWallpaper = $state<WallpaperInfo | null>(null);
  let wallpaperActive = $state(true);
  let changingWallpaper = $state(false);
  let wallpaperModalOpen = $state(false);
  let wallpaperToast = $state<string | null>(null);
  let toastTimer: number | null = null;

  const modalOpen = $derived(activeCollectionModal !== null || wallpaperModalOpen || birthdayModalOpen);

  function openCollectionModal(data: CollectionModalData, trigger?: HTMLElement): void {
    modalTriggerEl = trigger || (document.activeElement instanceof HTMLElement ? document.activeElement : null);
    activeCollectionModal = data;
  }

  function closeCollectionModal(): void {
    activeCollectionModal = null;
    requestAnimationFrame(() => modalTriggerEl?.focus());
  }

  function openBirthdayModal(trigger?: HTMLElement): void {
    modalTriggerEl = trigger || (document.activeElement instanceof HTMLElement ? document.activeElement : null);
    birthdayModalOpen = true;
  }

  function closeBirthdayModal(): void {
    birthdayModalOpen = false;
    requestAnimationFrame(() => modalTriggerEl?.focus());
  }

  function openWallpaperModal(trigger?: HTMLElement): void {
    modalTriggerEl = trigger || (document.activeElement instanceof HTMLElement ? document.activeElement : null);
    wallpaperModalOpen = true;
  }

  function closeWallpaperModal(): void {
    wallpaperModalOpen = false;
    requestAnimationFrame(() => modalTriggerEl?.focus());
  }

  function handleModalTrap(e: KeyboardEvent): void {
    if (e.key === 'Tab') {
      const modalEl = e.currentTarget as HTMLElement | null;
      if (!modalEl) return;
      const focusables = modalEl.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), [tabindex]:not([tabindex="-1"])'
      );
      if (focusables.length === 0) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (e.shiftKey) {
        if (document.activeElement === first) {
          e.preventDefault();
          last.focus();
        }
      } else {
        if (document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
  }

  function showWallpaperToast(msg: string): void {
    wallpaperToast = msg;
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => {
      wallpaperToast = null;
    }, 2200);
  }

  // 角色头像映射
  let avatarMap = $state<Record<string, string>>({});
  let avatarsLoading = $state(false);

  // 阵营分类主题配置
  const factionThemes: Record<string, { color: string; badge: string; icon: string; slogan: string }> = {
    '欧泊': { color: '#2563eb', badge: '欧泊阵营', icon: 'lucide:shield', slogan: '维和治安 · 官方秩序防卫军' },
    '剪刀手': { color: '#dc2626', badge: '剪刀手阵营', icon: 'lucide:scissors', slogan: '自由潜行 · 追求平权的超弦同盟' },
    '乌尔比诺': { color: '#d97706', badge: '乌尔比诺商会', icon: 'lucide:crown', slogan: '商业财阀 · 掌控巴布洛核心科技' },
    '晶源体': { color: '#9333ea', badge: '晶源感染生物', icon: 'lucide:biohazard', slogan: '超弦感染 · 生化感染模式母体与变异体' }
  };

  // 专属主武器与使用者对应表（根据 Wiki 权威 SMW 数据）
  const PRIMARY_WEAPON_OWNERS: Record<string, string> = {
    '北极星': '星绘',
    '警探': '米雪儿·李',
    '卫冕': '奥黛丽·格罗夫',
    '夜镰': '玛拉',
    '审判官': '信',
    '幻霜': '伊薇特',
    '彩绘': '玛德蕾娜·利里',
    '影袭': '拉薇',
    '枫鸣': '千代',
    '校准仪': '蕾欧娜',
    '欺诈师': '加拉蒂亚·利里',
    '潮音': '汐',
    '独舞': '芙拉薇娅',
    '破晓': '令',
    '空境': '心夏',
    '绝对执行': '忧雾',
    '绽放': '珐格兰丝',
    '自由意志': '白墨',
    '谢幕曲': '香奈美',
    '逆焰': '明',
    '隼': '梅瑞狄斯',
    '雨晦': '诺诺',
    '鸣火': '艾卡',
    '齿锋': '绯莎',
    '静风': '全员通用'
  };

  function getWeaponOwnerDisplay(weaponName: string): { name: string; avatarKey: string } | null {
    const full = PRIMARY_WEAPON_OWNERS[weaponName];
    if (!full) return null;
    if (full === '全员通用') return { name: '通用', avatarKey: '' };
    return { name: full.split('·')[0], avatarKey: full };
  }

  function getSubgroupIcon(title: string): string {
    if (title.includes('步枪') || title.includes('狙击') || title.includes('枪')) return 'lucide:crosshair';
    if (title.includes('近战') || title.includes('刀')) return 'lucide:sword';
    if (title.includes('战术') || title.includes('道具') || title.includes('雷')) return 'lucide:bomb';
    if (title.includes('爆破')) return 'lucide:flame';
    if (title.includes('乱斗') || title.includes('冲突')) return 'lucide:swords';
    if (title.includes('团竞') || title.includes('推进')) return 'lucide:flag';
    if (title.includes('感染')) return 'lucide:biohazard';
    if (title.includes('争夺')) return 'lucide:target';
    if (title.includes('筛选') || title.includes('表')) return 'lucide:filter';
    if (title.includes('链接') || title.includes('官网')) return 'lucide:link-2';
    if (title.includes('宝箱') || title.includes('工具') || title.includes('模拟器')) return 'lucide:box';
    if (title.includes('移动端')) return 'lucide:smartphone';
    if (title.includes('培养') || title.includes('誓约') || title.includes('印迹')) return 'lucide:heart';
    if (title.includes('账号') || title.includes('好友') || title.includes('成就')) return 'lucide:user';
    if (title.includes('分享') || title.includes('卡组')) return 'lucide:share-2';
    if (title.includes('延伸') || title.includes('BGM') || title.includes('壁纸') || title.includes('表情')) return 'lucide:sparkles';
    if (title.includes('随笔') || title.includes('梗') || title.includes('Tips')) return 'lucide:book-open';
    if (title.includes('装饰') || title.includes('基板') || title.includes('勋章') || title.includes('外观')) return 'lucide:gem';
    if (title.includes('赛事') || title.includes('杯')) return 'lucide:trophy';
    return 'lucide:folder-git-2';
  }

  // 子分类主题色彩配置 (赋予玩法与其它丰富的业务语义色彩)
  const subgroupColors: Record<string, string> = {
    // 玩法分区
    '移动端内容': '#06b6d4', // 科技青
    '角色培养': '#ec4899',   // 羁绊粉
    '账号系统': '#3b82f6',   // 档案蓝
    '分享工具': '#8b5cf6',   // 社区紫
    // 其他分区
    '游戏延伸': '#f59e0b',   // 灵感橙
    '随笔条目': '#10b981',   // 探索绿
    '玩家装饰': '#e11d48',   // 潮流红
    '官方赛事': '#8b5cf6',   // 竞技紫
    '民间赛事': '#6366f1',   // 社区蓝紫
    // 地图与武器
    '爆破模式': '#ef4444',
    '团队乱斗模式': '#f97316',
    '无限团竞模式': '#06b6d4',
    '极限推进模式': '#8b5cf6',
    '大头乱斗模式': '#ec4899',
    '晶源感染模式': '#a855f7',
    '极限刀战模式': '#14b8a6',
    '弦区争夺模式': '#f59e0b',
    '枪王乱斗模式': '#3b82f6',
    '晶能冲突模式': '#10b981',
    '自动步枪': '#ef4444',
    '微型冲锋枪': '#f97316',
    '狙击步枪': '#8b5cf6',
    '精确射手步枪': '#3b82f6',
    '轻机枪': '#d97706',
    '霰弹枪': '#06b6d4',
    '近战武器': '#10b981',
    '副武器': '#64748b',
    '战术道具': '#e11d48'
  };

  function getSubgroupColor(title: string): string {
    return subgroupColors[title] || 'var(--foreground)';
  }
  const sectionThemes: Record<string, { icon: string; color: string; tag: string }> = {
    '首页': { icon: 'lucide:compass', color: '#0284c7', tag: '门户与常用' },
    '角色': { icon: 'lucide:users', color: '#2563eb', tag: '超弦体与阵营' },
    '武器': { icon: 'lucide:crosshair', color: '#dc2626', tag: '枪械与战术' },
    '地图': { icon: 'lucide:map', color: '#059669', tag: '对战场景' },
    '玩法': { icon: 'lucide:gamepad-2', color: '#7c3aed', tag: '模式与系统' },
    '其他': { icon: 'lucide:sparkles', color: '#d97706', tag: '社区与资料' }
  };

  function getPortalSub(title: string): string {
    if (title === '首页') return '官方百科主站';
    if (title === '往期公告') return '版本维护更新';
    if (title === '往期活动') return '限时主题归档';
    if (title === '投稿作品') return '同人创作者馆';
    if (title === 'WIKI反馈版') return '勘误纠错反馈';
    return '官方直达';
  }

  function getCollectionIcon(title: string): string {
    if (title === '猫娘的百宝箱') return 'lucide:box';
    if (title === '常用链接') return 'lucide:globe';
    if (title === '常用筛选表') return 'lucide:filter';
    return 'lucide:layers';
  }

  function getCollectionDesc(title: string): string {
    if (title === '猫娘的百宝箱') return 'GuGuTalk、卡牌生成器、重构模拟等 8 款同人工具';
    if (title === '常用链接') return 'PC国服/国际服官网、移动端预约与官方B站';
    if (title === '常用筛选表') return '角色时装外观、武器皮肤与生化卡牌速查';
    return '分类快捷合集';
  }

  function getCollectionColor(title: string): string {
    if (title === '猫娘的百宝箱') return '#06b6d4';
    if (title === '常用链接') return '#2563eb';
    if (title === '常用筛选表') return '#f59e0b';
    return '#6b7280';
  }

  function getModalDataForGroup(groupTitle: string): CollectionModalData | null {
    if (groupTitle === '猫娘的百宝箱') return CATGIRL_TOOLBOX_MODAL;
    if (groupTitle === '常用链接') return OFFICIAL_CHANNELS_MODAL;
    if (groupTitle === '常用筛选表') return FILTER_TOOLS_MODAL;
    if (groupTitle === '游戏延伸') return GAME_EXTENSIONS_MODAL;
    return null;
  }

  interface FeaturedTool {
    title: string;
    desc: string;
    url?: string;
    action?: (trigger?: HTMLElement) => void;
    icon: string;
    color: string;
    tag: string;
    isModal?: boolean;
  }

  // 高频精选工具卡片 (Featured Spotlight - 精致微色调，告别单调平庸)
  const featuredTools: FeaturedTool[] = [
    {
      title: '猫娘百宝箱',
      desc: '卡牌制作、贴纸生成、抽卡模拟等 8 款社区工具',
      action: (el) => openCollectionModal(CATGIRL_TOOLBOX_MODAL, el),
      icon: 'lucide:box',
      color: '#06b6d4',
      tag: '工具',
      isModal: true
    },
    {
      title: '官方渠道矩阵',
      desc: 'PC国服、国际服官网、手游预约与官方B站',
      action: (el) => openCollectionModal(OFFICIAL_CHANNELS_MODAL, el),
      icon: 'lucide:globe',
      color: '#2563eb',
      tag: '官方',
      isModal: true
    },
    {
      title: '全站筛选图鉴',
      desc: '时装外观、武器皮肤、功能道具与生化卡牌',
      action: (el) => openCollectionModal(FILTER_TOOLS_MODAL, el),
      icon: 'lucide:filter',
      color: '#f59e0b',
      tag: '筛选',
      isModal: true
    },
    {
      title: '角色时装投票',
      desc: '超弦体时装人气投票与排行榜',
      url: 'https://wiki.biligame.com/klbq/%E8%A7%92%E8%89%B2%E6%97%B6%E8%A3%85%E6%8A%95%E7%A5%A8',
      icon: 'lucide:heart-handshake',
      color: '#ec4899',
      tag: '热门'
    },
    {
      title: '主武器理论数据表',
      desc: '全武器伤害、射速与衰减倍率比对',
      url: 'https://wiki.biligame.com/klbq/%E4%B8%BB%E6%AD%A6%E5%99%A8%E7%90%86%E8%AE%BA%E6%95%B0%E6%8D%AE%E8%A1%A8',
      icon: 'lucide:file-bar-chart',
      color: '#dc2626',
      tag: '数据'
    },
    {
      title: '对战地图一览',
      desc: '爆破、乱斗、团竞等全模式作战场景',
      url: 'https://wiki.biligame.com/klbq/%E5%9C%B0%E5%9B%BE',
      icon: 'lucide:map-pin',
      color: '#10b981',
      tag: '地图'
    },
    {
      title: '福利兑换码',
      desc: '官方最新晶核、基板与礼包兑换码',
      url: 'https://wiki.biligame.com/klbq/%E5%85%91%E6%8D%A2%E7%A0%81',
      icon: 'lucide:gift',
      color: '#8b5cf6',
      tag: '福利'
    },
    {
      title: '武器外观筛选',
      desc: '各枪械金色/紫色皮肤展示图鉴',
      url: 'https://wiki.biligame.com/klbq/%E6%AD%A6%E5%99%A8%E5%A4%96%E8%A7%82%E7%AD%9B%E9%80%89',
      icon: 'lucide:palette',
      color: '#f97316',
      tag: '外观'
    }
  ];

  const quickSearchTags = [
    '角色时装投票',
    '🎂 角色生日',
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
    if (!filterActive) return true;
    return matchNavTitle(title, filter);
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
      if (currentWallpaper?.title) {
        showWallpaperToast(`已换壁纸：${currentWallpaper.title}`);
      }
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

  async function refreshNavigation(): Promise<void> {
    if (navReloading) return;
    navReloading = true;
    try {
      sections = await fetchNavSections(undefined, true);
      showWallpaperToast('导航目录已刷新');
    } catch (error) {
      errorMessage = error instanceof Error ? error.message : String(error);
    } finally {
      navReloading = false;
    }
  }

  function handleKeydown(e: KeyboardEvent): void {
    if (e.key === '/' && document.activeElement !== searchInputEl) {
      e.preventDefault();
      searchInputEl?.focus();
    } else if (e.key === 'Escape') {
      if (activeCollectionModal) {
        closeCollectionModal();
      } else if (wallpaperModalOpen) {
        closeWallpaperModal();
      } else if (birthdayModalOpen) {
        closeBirthdayModal();
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

      // 异步预载全部武器立绘与地图实景缩略图
      fetchGameAssets().then(assets => {
        gameAssets = assets;
      });
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
    return () => {
      if (toastTimer) clearTimeout(toastTimer);
      document.body.style.overflow = '';
    };
  });

  $effect(() => {
    document.body.style.overflow = modalOpen ? 'hidden' : '';
    if (modalOpen) {
      void tick().then(() => modalCloseEl?.focus());
    }
  });
</script>

<svelte:window onkeydown={handleKeydown} onscroll={() => scrollY = window.scrollY} />

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

          <div class="wallpaper-info-container">
            <button
              class="wp-ctrl-btn info-btn"
              type="button"
              onclick={(e) => openWallpaperModal(e.currentTarget as HTMLElement)}
              title={`当前壁纸：${currentWallpaper.title} (点击全屏预览)`}
              aria-label="预览当前壁纸"
            >
              <iconify-icon icon="lucide:image"></iconify-icon>
              <span class="wp-title-truncate">{currentWallpaper.title}</span>
            </button>
            <div class="wp-hover-preview-card" role="tooltip">
              <div class="wp-hover-preview-thumb">
                <img src={currentWallpaper.url} alt="" loading="lazy" referrerpolicy="no-referrer" />
              </div>
              <div class="wp-hover-preview-meta">
                <span class="wp-hover-title">{currentWallpaper.title}</span>
                <span class="wp-hover-hint">
                  <iconify-icon icon="lucide:maximize-2"></iconify-icon>
                  <span>点击进入全屏画廊</span>
                </span>
              </div>
            </div>
          </div>

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

      {#if !loading && !errorMessage}
        <button
          class="header-link refresh-nav-btn"
          class:refreshing={navReloading}
          type="button"
          onclick={refreshNavigation}
          title="强制刷新 Wiki 侧边栏导航数据"
          aria-label="刷新 Wiki 侧边栏导航数据"
        >
          <iconify-icon icon="lucide:refresh-cw" class="ext-icon" class:spinning={navReloading}></iconify-icon>
          <span class="btn-text-desktop">刷新</span>
        </button>
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
          {#if tool.action}
            <button
              class="featured-tool-card as-btn"
              type="button"
              onclick={(e) => tool.action?.(e.currentTarget as HTMLElement)}
              style="--tool-color: {tool.color};"
            >
              <div class="tool-card-icon-box">
                <iconify-icon icon={tool.icon}></iconify-icon>
              </div>
              <div class="tool-card-info">
                <div class="tool-card-title-row">
                  <strong class="tool-card-title">{tool.title}</strong>
                  <span class="tool-card-tag">{tool.tag}</span>
                </div>
                <p class="tool-card-desc">{tool.desc}</p>
              </div>
              <iconify-icon icon="lucide:layout-grid" class="tool-card-arrow"></iconify-icon>
            </button>
          {:else}
            <a
              class="featured-tool-card"
              href={tool.url}
              target="_blank"
              rel="noopener noreferrer"
              style="--tool-color: {tool.color};"
            >
              <div class="tool-card-icon-box">
                <iconify-icon icon={tool.icon}></iconify-icon>
              </div>
              <div class="tool-card-info">
                <div class="tool-card-title-row">
                  <strong class="tool-card-title">{tool.title}</strong>
                  <span class="tool-card-tag">{tool.tag}</span>
                </div>
                <p class="tool-card-desc">{tool.desc}</p>
              </div>
              <iconify-icon icon="lucide:arrow-up-right" class="tool-card-arrow"></iconify-icon>
            </a>
          {/if}
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
      {#each displaySections as section, sectionIdx (section.title)}
        {@const theme = sectionThemes[section.title] || { icon: 'lucide:folder', color: '#0284c7', tag: '资料' }}
        {@const isHomeSection = section.title === '首页'}
        {@const isCharacterSection = section.title === '角色'}
        {@const directItems = section.items.filter(item => item.children.length === 0)}
        {@const groupItems = section.items.filter(item => item.children.length > 0)}
        {@const sectionCount = countSectionTotal(section)}

        <section class="section-card" style="--section-color: {theme.color}; --card-index: {sectionIdx};">
          <!-- 分区卡片头部 -->
          <div class="section-card-header">
            <div class="section-badge-icon">
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
            <!-- ── 首页分区专属：轻量化门户枢纽与精选合集 ── -->
            {#if isHomeSection}
              <!-- 1. 官方门户核心入口 -->
              <div class="tier-block home-portal-block">
                <div class="tier-label">
                  <iconify-icon icon="lucide:compass" class="tier-icon"></iconify-icon>
                  <span>官方门户枢纽</span>
                </div>
                <div class="home-portal-grid">
                  {#each directItems as item (item.title)}
                    <a
                      class="home-portal-card"
                      href={item.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      title={item.title}
                    >
                      <div class="home-portal-icon-box">
                        <iconify-icon icon={getItemIcon(item.title)}></iconify-icon>
                      </div>
                      <div class="home-portal-texts">
                        <strong class="home-portal-title">{item.title}</strong>
                        <span class="home-portal-sub">{getPortalSub(item.title)}</span>
                      </div>
                      <iconify-icon icon="lucide:arrow-up-right" class="home-portal-arrow"></iconify-icon>
                    </a>
                  {/each}
                </div>
              </div>

              <!-- 2. 精选分类合集矩阵 (告别繁重标签云) -->
              <div class="tier-block home-collections-block">
                <div class="tier-label">
                  <iconify-icon icon="lucide:layout-grid" class="tier-icon"></iconify-icon>
                  <span>精选分类集合</span>
                </div>
                <div class="home-collections-grid">
                  {#each groupItems as group (group.title)}
                    {@const modalData = getModalDataForGroup(group.title)}
                    {@const collColor = getCollectionColor(group.title)}
                    <div class="home-collection-card" style="--coll-color: {collColor};">
                      <div class="home-coll-header">
                        <div class="home-coll-title-row">
                          <div class="home-coll-icon-box">
                            <iconify-icon icon={getCollectionIcon(group.title)}></iconify-icon>
                          </div>
                          <div class="home-coll-title-wrap">
                            <div class="home-coll-title-line">
                              <strong class="home-coll-title">{group.title}</strong>
                              <span class="home-coll-count">{group.children.length} 项</span>
                            </div>
                            <span class="home-coll-desc">{getCollectionDesc(group.title)}</span>
                          </div>
                        </div>
                        {#if modalData}
                          <button
                            class="home-coll-expand-btn"
                            type="button"
                            onclick={(e) => openCollectionModal(modalData, e.currentTarget as HTMLElement)}
                            title={`以弹窗全览 ${group.title}`}
                          >
                            <iconify-icon icon="lucide:layout-grid"></iconify-icon>
                            <span>全览</span>
                          </button>
                        {/if}
                      </div>

                      <div class="home-coll-pills">
                        {#each group.children.slice(0, 4) as child (child.title)}
                          <a
                            class="home-coll-pill"
                            href={child.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            title={child.title}
                          >
                            <span>{child.title}</span>
                            <iconify-icon icon="lucide:arrow-up-right" class="pill-mini-arrow"></iconify-icon>
                          </a>
                        {/each}
                        {#if group.children.length > 4 && modalData}
                          <button
                            class="home-coll-more-btn"
                            type="button"
                            onclick={(e) => openCollectionModal(modalData, e.currentTarget as HTMLElement)}
                          >
                            <span>更多 ({group.children.length - 4})</span>
                            <iconify-icon icon="lucide:chevron-right"></iconify-icon>
                          </button>
                        {/if}
                      </div>
                    </div>
                  {/each}
                </div>
              </div>

            <!-- ── 角色分区专属的阵营图鉴卡片展示 (含官方头像) ── -->
            {:else if isCharacterSection}
              <!-- 角色生日速递横幅 -->
              {#if nearestBirthday}
                <div class="birthday-spotlight-banner">
                  <div class="bday-banner-left">
                    <div class="bday-cake-icon-box">
                      <iconify-icon icon="lucide:cake"></iconify-icon>
                    </div>
                    <div class="bday-banner-text">
                      <div class="bday-title-row">
                        <strong class="bday-banner-title">
                          {#if nearestBirthday.isToday}
                            🎉 今天是 {nearestBirthday.birthday.name} 的生日！
                          {:else}
                            🎂 近期寿星：{nearestBirthday.birthday.name} · {nearestBirthday.birthday.dateText}
                            <span class="bday-countdown-badge">{nearestBirthday.statusText}</span>
                          {/if}
                        </strong>
                      </div>
                      <span class="bday-banner-sub">
                        {#if nearestBirthday.isToday}
                          祝 {nearestBirthday.birthday.name} 生日快乐！去游戏内或 Wiki 查看看生日特别剧情吧~
                        {:else}
                          全角色生日档案与倒计时一览
                        {/if}
                      </span>
                    </div>
                  </div>
                  <button
                    class="bday-view-all-btn"
                    type="button"
                    onclick={(e) => openBirthdayModal(e.currentTarget as HTMLElement)}
                  >
                    <iconify-icon icon="lucide:calendar-days"></iconify-icon>
                    <span>生日日历</span>
                  </button>
                </div>
              {/if}

              <!-- 阵营角色展示 -->
              {#each groupItems as factionGroup (factionGroup.title)}
                {@const factionInfo = factionThemes[factionGroup.title] || { color: '#6b7280', badge: factionGroup.title, icon: 'lucide:users', slogan: '' }}
                <div class="faction-gallery-block" style="--faction-color: {factionInfo.color};">
                  <div class="faction-header-row">
                    <div class="faction-title-pill" style="background: color-mix(in srgb, {factionInfo.color} 12%, transparent); color: {factionInfo.color}; border: 1px solid color-mix(in srgb, {factionInfo.color} 25%, transparent);">
                      <iconify-icon icon={factionInfo.icon}></iconify-icon>
                      <span>{factionGroup.title}</span>
                      <span class="faction-count-badge">{factionGroup.children.length}</span>
                    </div>
                    {#if factionInfo.slogan}
                      <span class="faction-slogan-text">{factionInfo.slogan}</span>
                    {/if}
                  </div>

                  <div class="character-avatars-grid">
                    {#each factionGroup.children as charItem (charItem.title)}
                      {@const avatarUrl = avatarMap[charItem.title]}
                      {@const bInfo = getCharacterBirthdayInfo(charItem.title)}
                      <a
                        class="char-avatar-card"
                        class:birthday-today={bInfo?.isToday}
                        class:birthday-near={bInfo && !bInfo.isToday && bInfo.days <= 7}
                        href={charItem.url || `https://wiki.biligame.com/klbq/${encodeURIComponent(charItem.title)}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        title={bInfo ? `${charItem.title}（生日：${bInfo.birthday.dateText}${bInfo.isToday ? ' · 今天生日！' : ` · 还有${bInfo.days}天`}）` : `查看 ${charItem.title} 角色资料`}
                      >
                        {#if bInfo?.isToday}
                          <span class="char-bday-flag today" title="今天生日！">🎂 生日!</span>
                        {:else if bInfo && bInfo.days <= 7}
                          <span class="char-bday-flag near" title={`${bInfo.days}天后生日`}>🎂 {bInfo.days}天</span>
                        {/if}

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
                            <div class="char-avatar-placeholder" aria-hidden="true">
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
                            <span class="core-pill-icon-box">
                              <iconify-icon icon={getItemIcon(item.title)} class="chip-item-icon"></iconify-icon>
                            </span>
                            <span class="pill-label">{item.title}</span>
                            <iconify-icon icon="lucide:arrow-up-right" class="pill-arrow"></iconify-icon>
                          </a>
                        {:else}
                          <span class="nav-link-pill pill-plain">
                            <span class="core-pill-icon-box">
                              <iconify-icon icon={getItemIcon(item.title)} class="chip-item-icon"></iconify-icon>
                            </span>
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
                            <span class="core-pill-icon-box">
                              <iconify-icon icon={getItemIcon(item.title)} class="chip-item-icon"></iconify-icon>
                            </span>
                            <span class="pill-label">{item.title}</span>
                            <iconify-icon icon="lucide:arrow-up-right" class="pill-arrow"></iconify-icon>
                          </a>
                        {:else}
                          <span class="nav-link-pill pill-plain">
                            <span class="core-pill-icon-box">
                              <iconify-icon icon={getItemIcon(item.title)} class="chip-item-icon"></iconify-icon>
                            </span>
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
                      {@const groupColor = getSubgroupColor(group.title)}
                      <div class="subgroup-card" style="--subgroup-color: {groupColor};">
                        <!-- 子卡片标题 -->
                        <div class="subgroup-card-header">
                          <div class="subgroup-card-title-wrap">
                            <span class="subgroup-title-indicator">
                              <iconify-icon icon={getSubgroupIcon(group.title)} class="subgroup-dot-icon"></iconify-icon>
                            </span>
                            <strong class="subgroup-card-title">{group.title}</strong>
                            <span class="subgroup-count-badge">{group.children.length}</span>
                          </div>

                          <div class="subgroup-header-actions">
                            {#if getModalDataForGroup(group.title)}
                              {@const modalData = getModalDataForGroup(group.title)!}
                              <button
                                class="subgroup-popup-pill-btn"
                                type="button"
                                onclick={(e) => openCollectionModal(modalData, e.currentTarget as HTMLElement)}
                                title={`以弹窗全览 ${group.title}`}
                                aria-label={`以弹窗全览 ${group.title}`}
                              >
                                <iconify-icon icon="lucide:layout-grid"></iconify-icon>
                              </button>
                            {/if}

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
                        </div>

                        <!-- 子条目标签集 -->
                        <div class="subgroup-tag-cloud">
                          {#each group.children as child (child.title)}
                            {@const isWeaponSection = section.title === '武器'}
                            {@const isMapSection = section.title === '地图'}
                            {@const weaponOwner = isWeaponSection ? getWeaponOwnerDisplay(child.title) : null}
                            {@const weaponImg = isWeaponSection ? gameAssets.weapons[child.title] : null}
                            {@const mapImg = isMapSection ? gameAssets.maps[child.title] : null}
                            {#if child.url}
                              <a
                                class="subgroup-tag-chip"
                                class:is-weapon-chip={!!weaponOwner}
                                href={child.url}
                                target="_blank"
                                rel="noopener noreferrer"
                                title={weaponOwner ? `${child.title}（使用者：${weaponOwner.name}）` : child.title}
                              >
                                {#if weaponImg}
                                  <img class="weapon-icon-render" src={weaponImg} alt="" loading="lazy" referrerpolicy="no-referrer">
                                {:else if mapImg}
                                  <img class="map-thumb-render" src={mapImg} alt="" loading="lazy" referrerpolicy="no-referrer">
                                {:else}
                                  <iconify-icon icon={getItemIcon(child.title)} class="chip-prefix-icon"></iconify-icon>
                                {/if}
                                <span class="tag-chip-title">{child.title}</span>
                                {#if weaponOwner}
                                  <span class="weapon-owner-badge">
                                    {#if weaponOwner.avatarKey && (avatarMap[weaponOwner.avatarKey] || avatarMap[weaponOwner.name])}
                                      <img
                                        class="owner-avatar-tiny"
                                        src={avatarMap[weaponOwner.avatarKey] || avatarMap[weaponOwner.name]}
                                        alt=""
                                        loading="lazy"
                                        referrerpolicy="no-referrer"
                                      >
                                    {/if}
                                    <span class="owner-name">{weaponOwner.name}</span>
                                  </span>
                                {/if}
                              </a>
                            {:else}
                              <span class="subgroup-tag-chip tag-plain">
                                {#if weaponImg}
                                  <img class="weapon-icon-render" src={weaponImg} alt="" loading="lazy" referrerpolicy="no-referrer">
                                {:else if mapImg}
                                  <img class="map-thumb-render" src={mapImg} alt="" loading="lazy" referrerpolicy="no-referrer">
                                {:else}
                                  <iconify-icon icon={getItemIcon(child.title)} class="chip-prefix-icon"></iconify-icon>
                                {/if}
                                <span class="tag-chip-title">{child.title}</span>
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
    onclick={(e) => { if (e.target === e.currentTarget) closeWallpaperModal(); }}
    onkeydown={(e) => { if (e.key === 'Escape') closeWallpaperModal(); handleModalTrap(e); }}
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
          bind:this={modalCloseEl}
          class="wp-modal-close-btn"
          type="button"
          onclick={closeWallpaperModal}
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
        <span class="wp-modal-tip">
          {#if currentWallpaper.totalCount && currentWallpaper.totalCount > 0}
            官方壁纸库随机抽取 · 库藏 {currentWallpaper.totalCount} 张
          {:else}
            官方壁纸库随机抽取
          {/if}
        </span>
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

<!-- ── 聚合合集弹窗 (猫娘百宝箱 / 官方渠道 / 全站筛选表 / 游戏延伸) ── -->
{#if activeCollectionModal}
  <div
    class="collection-modal-backdrop"
    onclick={(e) => { if (e.target === e.currentTarget) closeCollectionModal(); }}
    onkeydown={(e) => { if (e.key === 'Escape') closeCollectionModal(); handleModalTrap(e); }}
    role="dialog"
    aria-modal="true"
    aria-label={activeCollectionModal.title}
    tabindex="-1"
  >
    <div class="collection-modal-content">
      <div class="coll-modal-header" style="--modal-accent: {activeCollectionModal.color};">
        <div class="coll-modal-title-wrap">
          <div class="coll-modal-icon-badge" style="background: color-mix(in srgb, {activeCollectionModal.color} 14%, transparent); color: {activeCollectionModal.color};">
            <iconify-icon icon={activeCollectionModal.icon}></iconify-icon>
          </div>
          <div class="coll-modal-text-group">
            <div class="coll-modal-title-row">
              <h3 class="coll-modal-title">{activeCollectionModal.title}</h3>
              <span class="coll-modal-count-badge">{activeCollectionModal.items.length} 个入口</span>
            </div>
            <p class="coll-modal-subtitle">{activeCollectionModal.subtitle}</p>
          </div>
        </div>
        <button
          bind:this={modalCloseEl}
          class="coll-modal-close-btn"
          type="button"
          onclick={closeCollectionModal}
          aria-label="关闭弹窗"
        >
          <iconify-icon icon="lucide:x"></iconify-icon>
        </button>
      </div>

      <div class="coll-modal-body">
        <div class="coll-tools-grid">
          {#each activeCollectionModal.items as tool, toolIdx (tool.title)}
            <a
              class="coll-tool-card"
              href={tool.url}
              target="_blank"
              rel="noopener noreferrer"
              style="--item-color: {tool.color}; --tool-index: {toolIdx};"
            >
              <div class="coll-tool-icon-box" style="background: color-mix(in srgb, {tool.color} 14%, transparent); color: {tool.color};">
                <iconify-icon icon={tool.icon}></iconify-icon>
              </div>
              <div class="coll-tool-info">
                <div class="coll-tool-title-row">
                  <strong class="coll-tool-title">{tool.title}</strong>
                  <span class="coll-tool-tag" style="background: color-mix(in srgb, {tool.color} 10%, transparent); color: {tool.color};">{tool.tag}</span>
                </div>
                <p class="coll-tool-desc">{tool.desc}</p>
              </div>
              <div class="coll-tool-arrow-wrap">
                <iconify-icon icon="lucide:arrow-up-right"></iconify-icon>
              </div>
            </a>
          {/each}
        </div>
      </div>

      {#if activeCollectionModal.footerTip || activeCollectionModal.wikiUrl}
        <div class="coll-modal-footer">
          <span class="coll-footer-tip">{activeCollectionModal.footerTip || ''}</span>
          {#if activeCollectionModal.wikiUrl}
            <a
              class="coll-wiki-link"
              href={activeCollectionModal.wikiUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              <span>查看 Wiki 说明页面</span>
              <iconify-icon icon="lucide:external-link"></iconify-icon>
            </a>
          {/if}
        </div>
      {/if}
    </div>
  </div>
{/if}

<!-- ── 全角色生日日历弹窗 ── -->
{#if birthdayModalOpen}
  <div
    class="collection-modal-backdrop"
    onclick={(e) => { if (e.target === e.currentTarget) closeBirthdayModal(); }}
    onkeydown={(e) => { if (e.key === 'Escape') closeBirthdayModal(); handleModalTrap(e); }}
    role="dialog"
    aria-modal="true"
    aria-label="超弦体生日日历"
    tabindex="-1"
  >
    <div class="collection-modal-content bday-modal-content">
      <div class="coll-modal-header" style="--modal-accent: #f59e0b;">
        <div class="coll-modal-title-wrap">
          <div class="coll-modal-icon-badge" style="background: color-mix(in srgb, #f59e0b 14%, transparent); color: #f59e0b;">
            <iconify-icon icon="lucide:cake"></iconify-icon>
          </div>
          <div class="coll-modal-text-group">
            <div class="coll-modal-title-row">
              <h3 class="coll-modal-title">超弦体生日日历</h3>
              <span class="coll-modal-count-badge">27 位角色</span>
            </div>
            <p class="coll-modal-subtitle">记录引航者与每位超弦体的诞辰纪念日</p>
          </div>
        </div>
        <button
          bind:this={modalCloseEl}
          class="coll-modal-close-btn"
          type="button"
          onclick={closeBirthdayModal}
          aria-label="关闭弹窗"
        >
          <iconify-icon icon="lucide:x"></iconify-icon>
        </button>
      </div>

      <div class="coll-modal-body bday-modal-body">
        {#if nearestBirthday}
          <div class="bday-highlight-card">
            <div class="bday-hl-badge">
              <iconify-icon icon="lucide:sparkles"></iconify-icon>
              <span>下一位寿星</span>
            </div>
            <div class="bday-hl-content">
              <div class="bday-hl-avatar-wrap">
                {#if avatarMap[nearestBirthday.birthday.name] || avatarMap[`${nearestBirthday.birthday.name}·李`] || avatarMap[`${nearestBirthday.birthday.name}·格罗夫`] || avatarMap[`${nearestBirthday.birthday.name}·利里`]}
                  <img
                    class="bday-hl-avatar"
                    src={avatarMap[nearestBirthday.birthday.name] || avatarMap[`${nearestBirthday.birthday.name}·李`] || avatarMap[`${nearestBirthday.birthday.name}·格罗夫`] || avatarMap[`${nearestBirthday.birthday.name}·利里`]}
                    alt={nearestBirthday.birthday.name}
                  >
                {:else}
                  <div class="bday-hl-avatar placeholder">
                    {nearestBirthday.birthday.name.slice(0, 1)}
                  </div>
                {/if}
              </div>
              <div class="bday-hl-info">
                <div class="bday-hl-name-row">
                  <span class="bday-hl-name">{nearestBirthday.birthday.name}</span>
                  <span class="bday-hl-date">{nearestBirthday.birthday.dateText}</span>
                </div>
                <div class="bday-hl-status">
                  {#if nearestBirthday.isToday}
                    🎉 <strong>今天就是生日！</strong> 快去游戏内收听专属特别语音吧！
                  {:else}
                    距离生日还有 <strong>{nearestBirthday.daysRemaining}</strong> 天 ({nearestBirthday.statusText})
                  {/if}
                </div>
              </div>
            </div>
          </div>
        {/if}

        <div class="bday-months-grid">
          {#each Array.from({ length: 12 }, (_, i) => i + 1) as month (month)}
            {@const monthBirthdays = CHARACTER_BIRTHDAYS.filter(b => b.month === month)}
            <div class="bday-month-card" class:has-birthdays={monthBirthdays.length > 0}>
              <div class="bday-month-header">
                <span class="month-num">{month} 月</span>
                <span class="month-count">{monthBirthdays.length} 位</span>
              </div>
              <div class="bday-month-list">
                {#if monthBirthdays.length === 0}
                  <span class="month-empty-hint">本月暂无超弦体生日</span>
                {:else}
                  {#each monthBirthdays as b (b.name)}
                    {@const days = getDaysUntilBirthday(b)}
                    {@const isToday = days === 0}
                    {@const isNear = days > 0 && days <= 14}
                    {@const avatar = avatarMap[b.name] || avatarMap[`${b.name}·李`] || avatarMap[`${b.name}·格罗夫`] || avatarMap[`${b.name}·利里`]}
                    <div class="bday-item-pill" class:is-today={isToday} class:is-near={isNear}>
                      {#if avatar}
                        <img class="bday-item-avatar" src={avatar} alt="" loading="lazy">
                      {:else}
                        <span class="bday-item-avatar placeholder">{b.name.slice(0, 1)}</span>
                      {/if}
                      <span class="bday-item-name">{b.name}</span>
                      <span class="bday-item-date">{b.month}月{b.day}日</span>
                      {#if isToday}
                        <span class="bday-status-pill today">今天!</span>
                      {:else if isNear}
                        <span class="bday-status-pill near">{days}天后</span>
                      {/if}
                    </div>
                  {/each}
                {/if}
              </div>
            </div>
          {/each}
        </div>
      </div>
    </div>
  </div>
{/if}

<!-- ── 浮动返回顶部按钮 ── -->
{#if showScrollTop}
  <button
    class="floating-top-btn"
    type="button"
    onclick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
    title="返回顶部"
    aria-label="返回顶部"
  >
    <iconify-icon icon="lucide:arrow-up"></iconify-icon>
  </button>
{/if}

<!-- ── 壁纸切换浮动微提示 ── -->
{#if wallpaperToast}
  <div class="wallpaper-toast-pill" role="status" aria-live="polite">
    <iconify-icon icon="lucide:sparkles" class="toast-sparkle"></iconify-icon>
    <span>{wallpaperToast}</span>
  </div>
{/if}
