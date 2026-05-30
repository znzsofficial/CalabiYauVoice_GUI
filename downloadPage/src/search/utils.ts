export function toError(error: unknown): Error {
  return error instanceof Error ? error : new Error(String(error));
}

export function formatFileSize(bytes: number, fallback = ''): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return fallback;
  const units = ['B', 'KB', 'MB', 'GB'];
  let size = bytes;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return `${size.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

export function fileNameFromTitle(title: string): string {
  return title.replace(/^文件:/, '').replace(/[\\/:*?"<>|]/g, '_') || 'file';
}

export function esc(value: unknown): string {
  return String(value || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
}

export function highlightMatch(text: string, q: string): string {
  const safeText = esc(text);
  const words = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').split(/\s+/).filter(Boolean);
  if (words.length === 0) return safeText;
  return safeText.replace(new RegExp(`(${words.join('|')})`, 'gi'), '<mark class="match-mark">$1</mark>');
}

export function categoryDisplayName(category: string): string {
  return category.replace(/^(Category:|分类:)/, '');
}
