/**
 * 角色头像批量获取服务
 * 基于 MediaWiki imageinfo API 批量加载超弦体头像
 */

const WIKI_API = '/api/wiki';
const AVATAR_CACHE_KEY = 'calabiyau.nav.avatars.map';
const CACHE_TTL = 7 * 24 * 60 * 60 * 1000;

export interface CharacterCandidate {
  title: string;
  url?: string | null;
}

export async function fetchCharacterAvatars(candidates: (string | CharacterCandidate)[]): Promise<Record<string, string>> {
  const map: Record<string, string> = {};
  if (candidates.length === 0) return map;

  const normalizedItems: Array<{ displayTitle: string; queryNames: string[] }> = [];

  for (const item of candidates) {
    const title = typeof item === 'string' ? item : item.title;
    const url = typeof item === 'string' ? '' : item.url || '';
    const queryNames = new Set<string>();
    queryNames.add(title);

    // 从 url 中提取 Wiki 页面真实目标名（如 .../%E5%8A%A0%E6%8B%89%E8%92%82%E4%BA%9A%C2%B7%E5%88%A9%E9%87%8C -> 加拉蒂亚·利里）
    if (url) {
      try {
        const pathPart = url.split('/').pop() || '';
        const decoded = decodeURIComponent(pathPart).trim();
        if (decoded && decoded !== title) {
          queryNames.add(decoded);
        }
      } catch {}
    }

    // 常见全名与简称兼容别名
    if (title === '加拉蒂亚') queryNames.add('加拉蒂亚·利里');
    if (title === '奥黛丽') queryNames.add('奥黛丽·格罗夫');
    if (title === '玛德蕾娜') queryNames.add('玛德蕾娜·利里');
    if (title.includes('·')) queryNames.add(title.split('·')[0]);

    normalizedItems.push({ displayTitle: title, queryNames: [...queryNames] });
  }

  if (typeof localStorage !== 'undefined') {
    try {
      const cached = localStorage.getItem(AVATAR_CACHE_KEY);
      if (cached) {
        const parsed = JSON.parse(cached) as { time: number; map: Record<string, string> };
        if (parsed.map && Date.now() - parsed.time < CACHE_TTL) {
          Object.assign(map, parsed.map);
          const allFound = normalizedItems.every(item => !!map[item.displayTitle]);
          if (allFound) return map;
        }
      }
    } catch {}
  }

  // 构造查询清单
  const queryTitles: string[] = [];
  const titleToDisplayMap = new Map<string, string>();

  for (const item of normalizedItems) {
    if (map[item.displayTitle]) continue;
    for (const name of item.queryNames) {
      const fileName = `文件:${name}头像.png`;
      queryTitles.push(fileName);
      titleToDisplayMap.set(fileName, item.displayTitle);
    }
  }

  if (queryTitles.length === 0) return map;

  try {
    // 每次最多查 50 个
    for (let i = 0; i < queryTitles.length; i += 50) {
      const chunk = queryTitles.slice(i, i + 50);
      const res = await fetch(`${WIKI_API}?action=query&titles=${encodeURIComponent(chunk.join('|'))}&prop=imageinfo&iiprop=url&format=json`);
      if (!res.ok) continue;
      const data = await res.json() as { query?: { pages?: Record<string, { title: string; imageinfo?: Array<{ url?: string }> }> } };
      const pages = Object.values(data.query?.pages || {});
      for (const p of pages) {
        const url = p.imageinfo?.[0]?.url;
        if (url) {
          const displayTitle = titleToDisplayMap.get(p.title);
          if (displayTitle && !map[displayTitle]) {
            map[displayTitle] = url;
          }
        }
      }
    }

    if (typeof localStorage !== 'undefined') {
      try {
        localStorage.setItem(AVATAR_CACHE_KEY, JSON.stringify({ time: Date.now(), map }));
      } catch {}
    }
  } catch {}

  return map;
}
