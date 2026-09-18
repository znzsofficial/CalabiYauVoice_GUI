export type NavItem = { title: string; url: string | null; children: NavItem[] };
export type NavSection = { title: string; items: NavItem[] };

// Keep in sync with androidApp .../wiki/navigation/parser/NavigationMenuParsers.kt
const WIKI_BASE = 'https://wiki.biligame.com/klbq/';
const TARGET_SECTIONS = ['首页', '角色', '武器', '地图', '玩法', '其他'];
const SIDEBAR_API = '/api/wiki?action=query&meta=allmessages&ammessages=sidebar&format=json';
const CACHE_KEY = 'downloadPage.nav.sidebar';
const CACHE_TTL = 60 * 60 * 1000;

type SidebarResponse = { query?: { allmessages?: Array<{ '*'?: string }> } };

type MutableNode = { title: string; url: string | null; children: MutableNode[] };

export function wikiPathEncode(title: string): string {
  return encodeURIComponent(title).replace(/%20/g, '%20').replace(/%2F/g, '/').replace(/%3A/g, ':');
}

export function toWikiUrl(target: string): string {
  if (/^https?:\/\//.test(target)) return target;
  return WIKI_BASE + wikiPathEncode(target);
}

function parseTargetAndDisplay(payload: string): { target: string | null; display: string } {
  const splitIndex = payload.indexOf('|');
  if (splitIndex >= 0) {
    const target = payload.slice(0, splitIndex).trim();
    const display = payload.slice(splitIndex + 1).trim() || target;
    return { target: target || null, display };
  }
  return { target: payload.trim() || null, display: payload.trim() };
}

export function parseSidebar(raw: string): NavSection[] {
  const sectionRoots = new Map<string, MutableNode>();
  const depthStack = new Map<number, MutableNode>();

  for (const line of raw.split(/\r?\n/)) {
    const trimmedEnd = line.replace(/\s+$/, '');
    if (!trimmedEnd.startsWith('*')) continue;
    const depth = trimmedEnd.length - trimmedEnd.replace(/^\*+/, '').length;
    const payload = trimmedEnd.slice(depth).trim();
    if (!payload) continue;

    if (depth === 1) {
      const sectionTitle = payload.trim();
      depthStack.clear();
      if (TARGET_SECTIONS.includes(sectionTitle)) {
        const node: MutableNode = { title: sectionTitle, url: null, children: [] };
        sectionRoots.set(sectionTitle, node);
        depthStack.set(1, node);
      }
      continue;
    }

    const parent = depthStack.get(depth - 1);
    if (!parent) continue;
    const { target, display } = parseTargetAndDisplay(payload);
    const child: MutableNode = { title: display, url: target ? toWikiUrl(target) : null, children: [] };
    parent.children.push(child);
    depthStack.set(depth, child);
    for (const key of [...depthStack.keys()]) {
      if (key > depth) depthStack.delete(key);
    }
  }

  return TARGET_SECTIONS.flatMap(title => {
    const root = sectionRoots.get(title);
    if (!root) return [];
    return [{ title: root.title, items: root.children.map(toImmutable) }];
  });
}

function toImmutable(node: MutableNode): NavItem {
  return { title: node.title, url: node.url, children: node.children.map(toImmutable) };
}

interface CachedSidebar { time: number; sections: NavSection[] }

export async function fetchNavSections(signal?: AbortSignal): Promise<NavSection[]> {
  if (typeof sessionStorage !== 'undefined') {
    try {
      const raw = sessionStorage.getItem(CACHE_KEY);
      if (raw) {
        const cached = JSON.parse(raw) as CachedSidebar;
        if (Array.isArray(cached.sections) && Date.now() - cached.time < CACHE_TTL) {
          return cached.sections;
        }
      }
    } catch {
      // Ignore malformed cache entries.
    }
  }

  const response = await fetch(SIDEBAR_API, { signal, headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`导航数据请求失败（HTTP ${response.status}）`);
  const data = await response.json() as SidebarResponse;
  const rawSidebar = data.query?.allmessages?.[0]?.['*'];
  if (!rawSidebar) throw new Error('导航数据为空');

  const sections = parseSidebar(rawSidebar);
  if (sections.length === 0) throw new Error('导航数据解析失败');

  if (typeof sessionStorage !== 'undefined') {
    try {
      sessionStorage.setItem(CACHE_KEY, JSON.stringify({ time: Date.now(), sections } satisfies CachedSidebar));
    } catch {
      // Storage may be unavailable (quota/private mode).
    }
  }
  return sections;
}
