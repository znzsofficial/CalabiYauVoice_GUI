/**
 * 随机壁纸服务（移植自移动端 WallpaperApi）
 * 从 BWiki 壁纸页面动态解析官方壁纸列表，随机抽取并提供全屏背景与预览
 */

export interface WallpaperInfo {
  fileName: string;
  title: string;
  url: string;
  totalCount?: number;
}

const WIKI_API = '/api/wiki';
const CACHE_LIST_KEY = 'calabiyau.nav.wallpaper.list';
const CACHE_URL_PREFIX = 'calabiyau.nav.wallpaper.url.';
const SESSION_CURRENT_KEY = 'calabiyau.nav.wallpaper.current';
const PREF_ENABLED_KEY = 'calabiyau.nav.wallpaper.enabled';
const LIST_TTL = 24 * 60 * 60 * 1000;

// 内置预选壁纸池：确保离线或首次进入瞬间无需等待即可渲染
export const FALLBACK_WALLPAPERS: WallpaperInfo[] = [
  {
    fileName: '壁纸-海风邀约的夏日.png',
    title: '海风邀约的夏日',
    url: 'https://patchwiki.biligame.com/images/klbq/6/6d/87ihbk49p8ie822vug23v04ctnj0tri.png'
  },
  {
    fileName: '壁纸-潮汐所见的绮梦.png',
    title: '潮汐所见的绮梦',
    url: 'https://patchwiki.biligame.com/images/klbq/5/55/fhy5lpufa6tzjiods116z8cqr49pqqk.png'
  },
  {
    fileName: '壁纸-奇趣游乐园.png',
    title: '奇趣游乐园',
    url: 'https://patchwiki.biligame.com/images/klbq/a/a9/cwwqmnvc9aj6ugf0qt00end2qbdy92j.png'
  },
  {
    fileName: '壁纸-学园庆典开放日.png',
    title: '学园庆典开放日',
    url: 'https://patchwiki.biligame.com/images/klbq/3/34/sngfl5mplogiuqp5q0mxthpoexddcei.png'
  },
  {
    fileName: '壁纸-田间野趣.png',
    title: '田间野趣',
    url: 'https://patchwiki.biligame.com/images/klbq/b/b0/r15idtia66zh86oxu8yfnu7atjrk433.png'
  },
  {
    fileName: '壁纸-两周年活动.png',
    title: '两周年活动',
    url: 'https://patchwiki.biligame.com/images/klbq/d/d5/bgu6ebcokzom7otcdckb4dpksn2cnff.png'
  },
  {
    fileName: '壁纸-3周年.png',
    title: '3周年',
    url: 'https://patchwiki.biligame.com/images/klbq/6/6d/2qrs4ew4c9yf9hlaticw2c8bgj851ys.png'
  },
  {
    fileName: '壁纸-正佳广场联动.png',
    title: '正佳广场联动',
    url: 'https://patchwiki.biligame.com/images/klbq/4/4a/qd6ijvd6ineokp3o2sb7eukv61pbqtj.png'
  }
];

export function isWallpaperEnabled(): boolean {
  if (typeof localStorage === 'undefined') return true;
  return localStorage.getItem(PREF_ENABLED_KEY) !== 'false';
}

export function setWallpaperEnabled(enabled: boolean): void {
  if (typeof localStorage === 'undefined') return;
  localStorage.setItem(PREF_ENABLED_KEY, enabled ? 'true' : 'false');
}

export function cleanWallpaperTitle(fileName: string): string {
  return fileName
    .replace(/^文件:/, '')
    .replace(/^壁纸-/, '')
    .replace(/\.(png|jpg|jpeg|webp)$/i, '');
}

/** 从 Wiki "壁纸" 页面提取全部壁纸文件名 */
export async function fetchWallpaperFileList(forceRefresh = false): Promise<string[]> {
  if (!forceRefresh && typeof localStorage !== 'undefined') {
    try {
      const cached = localStorage.getItem(CACHE_LIST_KEY);
      if (cached) {
        const parsed = JSON.parse(cached) as { time: number; list: string[] };
        if (Array.isArray(parsed.list) && Date.now() - parsed.time < LIST_TTL) {
          return parsed.list;
        }
      }
    } catch {}
  }

  try {
    const res = await fetch(`${WIKI_API}?action=parse&page=${encodeURIComponent('壁纸')}&prop=wikitext&format=json`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json() as { parse?: { wikitext?: { '*'?: string } } };
    const wikitext = data.parse?.wikitext?.['*'] || '';

    const fileNames = new Set<string>();
    const matches = wikitext.matchAll(/(?:文件:|\[\[文件:)(壁纸-[^|\n\]]+\.(?:jpg|png|jpeg|webp))/gi);
    for (const match of matches) {
      if (match[1]) fileNames.add(match[1].trim());
    }

    const bareMatches = wikitext.matchAll(/(?:^|\n)(壁纸-[^\n|]+\.(?:jpg|png|jpeg|webp))/gi);
    for (const match of bareMatches) {
      if (match[1]) fileNames.add(match[1].trim());
    }

    const list = [...fileNames];
    if (list.length > 0 && typeof localStorage !== 'undefined') {
      try {
        localStorage.setItem(CACHE_LIST_KEY, JSON.stringify({ time: Date.now(), list }));
      } catch {}
    }
    return list.length > 0 ? list : FALLBACK_WALLPAPERS.map(w => w.fileName);
  } catch {
    return FALLBACK_WALLPAPERS.map(w => w.fileName);
  }
}

/** 获取单张壁纸的 CDN 真实 URL */
export async function fetchWallpaperUrl(fileName: string): Promise<string | null> {
  const fallback = FALLBACK_WALLPAPERS.find(w => w.fileName === fileName);
  if (fallback) return fallback.url;

  const cacheKey = CACHE_URL_PREFIX + fileName;
  if (typeof localStorage !== 'undefined') {
    const cached = localStorage.getItem(cacheKey);
    if (cached) return cached;
  }

  try {
    const res = await fetch(`${WIKI_API}?action=query&titles=${encodeURIComponent('文件:' + fileName)}&prop=imageinfo&iiprop=url&format=json`);
    if (!res.ok) return null;
    const data = await res.json() as { query?: { pages?: Record<string, { imageinfo?: Array<{ url?: string }> }> } };
    const pages = Object.values(data.query?.pages || {});
    const url = pages[0]?.imageinfo?.[0]?.url || null;
    if (url && typeof localStorage !== 'undefined') {
      try {
        localStorage.setItem(cacheKey, url);
      } catch {}
    }
    return url;
  } catch {
    return null;
  }
}

/** 随机抽取一张壁纸 */
export async function getRandomWallpaper(forceRefresh = false): Promise<WallpaperInfo> {
  if (!forceRefresh && typeof sessionStorage !== 'undefined') {
    try {
      const stored = sessionStorage.getItem(SESSION_CURRENT_KEY);
      if (stored) {
        const parsed = JSON.parse(stored) as WallpaperInfo;
        if (parsed?.url) return parsed;
      }
    } catch {}
  }

  const list = await fetchWallpaperFileList(forceRefresh);
  if (list.length === 0) {
    const fallback = FALLBACK_WALLPAPERS[Math.floor(Math.random() * FALLBACK_WALLPAPERS.length)];
    return fallback;
  }

  // 随机取一张
  const randomIndex = Math.floor(Math.random() * list.length);
  const pickedFileName = list[randomIndex];
  const url = await fetchWallpaperUrl(pickedFileName);

  const result: WallpaperInfo = {
    fileName: pickedFileName,
    title: cleanWallpaperTitle(pickedFileName),
    url: url || FALLBACK_WALLPAPERS[0].url,
    totalCount: list.length
  };

  if (typeof sessionStorage !== 'undefined') {
    try {
      sessionStorage.setItem(SESSION_CURRENT_KEY, JSON.stringify(result));
    } catch {}
  }

  return result;
}
