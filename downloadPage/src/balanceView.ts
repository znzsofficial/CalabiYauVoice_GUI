export type BalanceHero = { id: number; heroName: string; winRate: number; selectRate: number; kd: number; damageAve: number; score: number };
export type SortField = keyof Omit<BalanceHero, 'id' | 'heroName'>;
export type BalanceOption = { code: string; name: string; character_code?: string; character_image?: string; imageUrl?: string; position_code?: string; positionCode?: string; position_name?: string };
export type BalanceSide = 'attackers' | 'defenders';
export type BalanceStatus = 'idle' | 'loading' | 'ready' | 'error';
export type BalanceSettings = { modes: BalanceOption[]; maps: BalanceOption[]; ranks: BalanceOption[]; seasons: BalanceOption[]; positions: BalanceOption[]; characters: BalanceOption[] };
export type BalanceResult = Record<BalanceSide, BalanceHero[]>;
export type CompareData = Record<BalanceSide, Record<string, Partial<Record<SortField, number>>>>;

export const balanceMetrics: { field: SortField; label: string }[] = [
  { field: 'winRate', label: '胜率' }, { field: 'selectRate', label: '选取率' },
  { field: 'kd', label: 'K/D' }, { field: 'damageAve', label: '回合均伤' }, { field: 'score', label: '表现分' }
];

export function formatBalanceMetric(value: number | undefined, field: SortField): string {
  if (value === undefined || !Number.isFinite(value)) return '—';
  if (field === 'winRate' || field === 'selectRate') return `${value.toFixed(2)}%`;
  return value.toFixed(field === 'kd' ? 2 : 1);
}

export function balanceDelta(current: number | undefined, previous: number | undefined, field: SortField): string {
  if (current === undefined || previous === undefined || !Number.isFinite(current) || !Number.isFinite(previous)) return '';
  const precision = field === 'damageAve' || field === 'score' ? 1 : 2;
  const difference = Number((current - previous).toFixed(precision));
  if (difference === 0) return '持平';
  return `${difference > 0 ? '+' : '−'}${Math.abs(difference).toFixed(precision)}${field === 'winRate' || field === 'selectRate' ? ' pp' : ''}`;
}

export function filterBalanceHeroes(
  heroes: BalanceHero[], characters: Record<number, BalanceOption>, query: string,
  position: string, field: SortField, descending: boolean
): BalanceHero[] {
  const search = query.trim().toLocaleLowerCase();
  return heroes.filter(hero => {
    const meta = characters[hero.id];
    return hero.winRate > 0 && (!search || hero.heroName.toLocaleLowerCase().includes(search)) &&
      (!position || (meta?.position_code || meta?.positionCode) === position);
  }).sort((a, b) => (descending ? b[field] - a[field] : a[field] - b[field]) || a.id - b.id);
}
