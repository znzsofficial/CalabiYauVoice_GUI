// S1–S9: https://klbq.qq.com/web202305/balance-date.html
// S10: 敕魂命途 (2025-03-25 season update). Keep in sync with data.BalanceSeasons.
const historicalNames = [
  '赤刃行动', '低语暗云', '暮光幻蝶', '日珥鸣火', '纵弦震音',
  '虚像艺匠', '绯息醒世', '蚀刻迷局', '隙间重构', '敕魂命途'
];

export function mergeBalanceSeasons(upstream: { code: string; name: string }[]): { code: string; name: string }[] {
  const byCode = new Map(upstream.map(item => [item.code, item]));
  historicalNames.forEach((name, index) => {
    const code = String(index + 1);
    if (!byCode.has(code)) byCode.set(code, { code, name });
  });
  return [...byCode.values()].sort((a, b) => (Number(b.code) || 0) - (Number(a.code) || 0));
}

export function supportsBalanceScore(seasonCode: string): boolean {
  return Number(seasonCode) >= 8;
}
