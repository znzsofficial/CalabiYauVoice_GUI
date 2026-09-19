/**
 * 武器与地图官方图片资产加载器
 * 批量获取全部 42 款武器立绘切片与全部 28 张对战地图实景缩略图
 * 具备 localStorage 7天长效缓存
 */

const WIKI_API = '/api/wiki';
const ASSETS_CACHE_KEY = 'calabiyau.nav.game_assets.v1';
const CACHE_TTL = 7 * 24 * 60 * 60 * 1000;

export const ALL_WEAPON_NAMES = [
  '北极星', '警探', '卫冕', '夜镰', '审判官', '幻霜', '彩绘', '影袭', '枫鸣', '校准仪',
  '欺诈师', '潮音', '独舞', '破晓', '空境', '绝对执行', '绽放', '自由意志', '谢幕曲',
  '逆焰', '隼', '雨晦', '鸣火', '齿锋', '静风',
  '忍锋', '战镰', '大剑',
  '小蜜蜂', '焚焰者', '雪鸮', '重焰',
  '破片手雷', '治疗雷', '风场雷', '减速雷', '闪光弹', '烟雾弹', '拦截者', '警报器', '雪球', '防弹屏障'
];

export const ALL_MAP_NAMES = [
  '404基地', '88区', '风曳镇', '欧拉港口', '空间实验室', '柯西街区', '科斯迷特', '奥卡努斯', '莱布伦城',
  '2号仓库', '花明弓道馆', '鹿鹿潮玩区', '水下发电厂', '莱特园区', '码头小镇',
  '离境区', '亚克萨工厂', '簌雪东区', '泰芙绿洲', '厄瑞洞窟', '熔岩工厂',
  '洛希街区', '迦纳古城', '极地研究所', '欧拉夜港', '猩红废墟', '虚空残岸', '狄拉克矿区'
];

export interface GameAssets {
  weapons: Record<string, string>;
  maps: Record<string, string>;
}

// 常见未配备图片词条的语义图标字典
export const ITEM_ICON_MAP: Record<string, string> = {
  // 首页
  '首页': 'lucide:home',
  '投稿作品': 'lucide:clapperboard',
  '往期活动': 'lucide:calendar-range',
  '往期公告': 'lucide:megaphone',
  'WIKI反馈版': 'lucide:message-square-warning',

  // 角色系统
  '角色筛选': 'lucide:filter',
  '角色时装投票': 'lucide:heart-handshake',
  '角色阵营': 'lucide:shield',
  '超弦体定位': 'lucide:crosshair',
  '超弦体设定': 'lucide:book-open',
  '宣传车': 'lucide:truck',
  '官博娘': 'lucide:tv-2',
  '卡璐酱': 'lucide:sparkles',

  // 武器直达
  '武器筛选': 'lucide:filter',
  '主武器理论数据表': 'lucide:file-bar-chart',

  // 地图直达
  '地图一览': 'lucide:map-pin',

  // 玩法与系统
  '战斗模式': 'lucide:swords',
  '弦化': 'lucide:fold-horizontal',
  '弦能增幅网络': 'lucide:network',
  '特别行动': 'lucide:shield-alert',
  '晶源战备': 'lucide:backpack',
  '赫尔墨斯': 'lucide:orbit',
  '赛事系统': 'lucide:trophy',
  '誓约': 'lucide:heart',
  '印迹': 'lucide:fingerprint',
  '超弦体天赋': 'lucide:sparkles',
  '超弦推进模式': 'lucide:flag',
  '载具外观': 'lucide:car',
  '头像框': 'lucide:circle-dot',
  '玩家等级': 'lucide:award',
  '好友': 'lucide:user-plus',
  '信用分': 'lucide:badge-check',
  '成就': 'lucide:medal',
  '晶源感染卡组分享': 'lucide:share-2',
  '自定义玩法共享': 'lucide:sliders',

  // 其他
  '剧情故事': 'lucide:book-marked',
  '游戏历史': 'lucide:history',
  '官方视频': 'lucide:video',
  'WIKI大事记': 'lucide:scroll',
  'BGM': 'lucide:music',
  '壁纸': 'lucide:image',
  '表情包': 'lucide:smile',
  '四格漫画': 'lucide:book-image',
  '联动': 'lucide:handshake',
  '相关周边': 'lucide:shopping-bag',
  '兑换码': 'lucide:gift',
  '喵言喵语': 'lucide:paw-print',
  '梗百科': 'lucide:laugh',
  '游戏Tips': 'lucide:lightbulb',
  '人气投票': 'lucide:flame',
  '基板': 'lucide:rectangle-horizontal',
  '封装': 'lucide:box',
  '勋章': 'lucide:medal',
  '喷漆': 'lucide:spray-can',
  '聊天气泡': 'lucide:message-square',
  '头套': 'lucide:smile',
  '超弦体动作': 'lucide:activity',
  '休息室手办': 'lucide:toy-brick',
  '房间外观': 'lucide:bed',
  '星弦杯': 'lucide:star',
  '高校赛': 'lucide:graduation-cap',
  '魔卡杯': 'lucide:trophy'
};

export function getItemIcon(title: string): string {
  if (ITEM_ICON_MAP[title]) return ITEM_ICON_MAP[title];
  for (const [key, icon] of Object.entries(ITEM_ICON_MAP)) {
    if (title.includes(key) || key.includes(title)) return icon;
  }
  return 'lucide:file-text';
}

/** 异步加载全部武器和地图图片 */
export async function fetchGameAssets(): Promise<GameAssets> {
  if (typeof localStorage !== 'undefined') {
    try {
      const cached = localStorage.getItem(ASSETS_CACHE_KEY);
      if (cached) {
        const parsed = JSON.parse(cached) as { time: number; data: GameAssets };
        if (parsed.data && Date.now() - parsed.time < CACHE_TTL) {
          return parsed.data;
        }
      }
    } catch {}
  }

  const weaponTitles = ALL_WEAPON_NAMES.flatMap(w => [`文件:${w}-weapon.png`, `文件:武器-${w}.png`]);
  const mapTitles = ALL_MAP_NAMES.flatMap(m => [`文件:地图-${m}.png`, `文件:地图-${m}.jpg`, `文件:${m}.png`]);
  const queryAll = [...weaponTitles, ...mapTitles];

  const result: GameAssets = {
    weapons: {},
    maps: {}
  };

  const chunks: string[][] = [];
  for (let i = 0; i < queryAll.length; i += 40) {
    chunks.push(queryAll.slice(i, i + 40));
  }

  try {
    await Promise.all(
      chunks.map(async chunk => {
        const res = await fetch(`${WIKI_API}?action=query&titles=${encodeURIComponent(chunk.join('|'))}&prop=imageinfo&iiprop=url&format=json`);
        if (!res.ok) return;
        const d = await res.json() as { query?: { pages?: Record<string, { title: string; imageinfo?: Array<{ url?: string }> }> } };
        for (const p of Object.values(d.query?.pages || {})) {
          const url = p.imageinfo?.[0]?.url;
          if (url) {
            const raw = p.title.replace(/^(File:|文件:)/, '');
            const cleanWeapon = raw.replace(/-weapon\.png$/, '').replace(/^武器-/, '').replace(/\.png$/, '');
            const cleanMap = raw.replace(/^地图-/, '').replace(/\.(png|jpg)$/, '');

            if (ALL_WEAPON_NAMES.includes(cleanWeapon) && !result.weapons[cleanWeapon]) {
              result.weapons[cleanWeapon] = url;
            }
            if (ALL_MAP_NAMES.includes(cleanMap) && !result.maps[cleanMap]) {
              result.maps[cleanMap] = url;
            }
          }
        }
      })
    );

    if (typeof localStorage !== 'undefined') {
      try {
        localStorage.setItem(ASSETS_CACHE_KEY, JSON.stringify({ time: Date.now(), data: result }));
      } catch {}
    }
  } catch {}

  return result;
}
