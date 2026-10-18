// Optimized snapshots from the official balance settings, served from our origin.
// See public/balance-avatars/README.md. Unknown/new characters still use official metadata.
export const balanceAvatarIds = [
  101, 105, 107, 108, 109, 110, 112, 115, 119, 120, 121, 122,
  123, 124, 125, 126, 128, 130, 131, 132, 133, 137, 146, 205
] as const;
const localIds: ReadonlySet<number> = new Set(balanceAvatarIds);

export function balanceAvatarSources(id: number, officialUrl?: string): string[] {
  const sources = localIds.has(id) ? [`/balance-avatars/${id}.webp`] : [];
  if (officialUrl?.startsWith('https://')) sources.push(officialUrl);
  return sources;
}
